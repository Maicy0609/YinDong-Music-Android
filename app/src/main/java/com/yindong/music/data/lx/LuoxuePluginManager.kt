package com.yindong.music.data.lx

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import com.yindong.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class LuoxuePluginManager(
    private val contentResolver: ContentResolver,
    private val quickJsFactory: () -> QuickJSWrapper,
    logCallbackParam: ((String) -> Unit)? = null,
) {
    private val okHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val plugins = mutableMapOf<String, PluginInstance>()

    var logCallback: ((String) -> Unit)? = logCallbackParam

    private fun log(msg: String) {
        Log.d(TAG, msg)
        try { logCallback?.invoke(msg) } catch (_: Exception) {}
    }

    fun createPluginSandbox(context: Context): QuickJSWrapper {
        val runtime = QuickJSWrapper()
        initSafeContext(runtime)
        injectFetchProxy(runtime)
        injectLxBridge(runtime)
        loadPreludeFromAssets(runtime, context)
        return runtime
    }

    private fun loadPreludeFromAssets(runtime: QuickJSWrapper, context: Context) {
        try {
            val script = cachedPreludeScript ?: context.assets.open("prelude.js").bufferedReader().use { it.readText() }
            cachedPreludeScript = script
            runtime.evaluate(script, "prelude.js")
            log("prelude.js loaded from assets (${script.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load prelude.js from assets", e)
            throw IllegalStateException("prelude.js 加载失败: ${e.message}", e)
        }
    }

    private fun initSafeContext(runtime: QuickJSWrapper) {
        runtime.registerGlobalFunction("__nativeLog") { args ->
            val msg = args.firstOrNull()?.toString().orEmpty()
            log("[JS] $msg")
            null
        }
    }

    private fun injectFetchProxy(runtime: QuickJSWrapper) {
        runtime.registerGlobalFunction("__nativeRequest") { args ->
            val method = args.getOrElse(0) { "GET" }.toString()
            val url = args.getOrElse(1) { "" }.toString()
            val headersJson = args.getOrElse(2) { "{}" }.toString()
            val bodyStr = args.getOrElse(3) { "" }.toString()

// 输出请求基本信息，用于诊断 lxmusic API 参数错误
log("nativeRequest SYNC: $method $url, bodyLen=${bodyStr.length}")

            try {
                val result = executeNativeRequest(method, url, headersJson, bodyStr)
                log("nativeRequest SYNC done: $method $url, result len=${result.length}")
                result
            } catch (e: Exception) {
                log("nativeRequest SYNC failed: $method $url, error=${e.message}")
                JSONObject()
                    .put("statusCode", 500)
                    .put("body", "{\"error\":\"${e.message?.replace("\"", "\\\"")}\"}")
                    .put("headers", JSONObject())
                    .toString()
            }
        }
    }

    private fun injectLxBridge(runtime: QuickJSWrapper) {
        runtime.registerGlobalFunction("__nativeLxOn") { args ->
            val event = args.getOrElse(0) { "" }.toString()
            log("lx.on: event=$event")
            if (event == "request") {
                runtime.setLxHandlerRegistered(true)
            }
            null
        }

        runtime.registerGlobalFunction("__nativeLxSend") { args ->
            val event = args.getOrElse(0) { "" }.toString()
            val dataStr = args.getOrElse(1) { "{}" }.toString()
            log("lx.send: event=$event, data=${dataStr.take(200)}")
            if (event == "inited") {
                try {
                    runtime.setInitedPayload(dataStr)
                } catch (e: Exception) {
                    log("parse inited payload error: ${e.message}")
                }
            }
            null
        }
    }

    fun loadLocalPlugin(pluginFile: File, context: android.content.Context): PluginInstance? {
        return try {
            val script = pluginFile.readText()
            val uri = android.net.Uri.fromFile(pluginFile).toString()
            loadPluginFromScript(script, uri, context)
        } catch (e: Exception) {
            Log.e(TAG, "本地插件加载失败", e)
            null
        }
    }

    suspend fun loadOnlinePlugin(pluginUrl: String, context: android.content.Context): PluginInstance? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(pluginUrl).build()
                val response = okHttpClient.newCall(request).execute()
                val jsCode = response.body?.string() ?: throw Exception("插件下载失败")
                loadPluginFromScript(jsCode, pluginUrl, context)
            } catch (e: Exception) {
                Log.e(TAG, "在线插件加载失败", e)
                null
            }
        }
    }

    fun loadPluginFromScript(
        script: String,
        uri: String,
        context: android.content.Context? = null,
    ): PluginInstance? {
        return try {
            val bytes = script.toByteArray(Charsets.UTF_8)
            if (bytes.size > 2 * 1024 * 1024) throw IllegalArgumentException("插件大小不能超过2MB (当前: ${bytes.size} bytes)")
            if (script.isBlank()) throw IllegalArgumentException("插件内容为空")
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { b -> "%02x".format(b) }

            plugins[hash]?.destroy()

            log("=== 开始加载插件: $uri (${script.length} chars, hash=${hash.take(12)}) ===")

            val ctx = context ?: throw IllegalStateException("需要 Context 来加载插件")
            val runtime = createPluginSandbox(ctx)
            if (runtime.initError != null) {
                throw IllegalStateException("QuickJS引擎初始化失败: ${runtime.initError}")
            }

            log("Step1: prelude.js 已加载 ✓")

            val cleanCode = script.trim().trimStart('\uFEFF')
            log("Step2: 执行插件脚本 (${cleanCode.length} chars)...")
            try {
                // 与 lx-music-mobile 一致：不做格式预检，直接执行脚本
                // 合法性由运行时 lx.send('inited') 判断
                val info = parsePluginInfo(cleanCode)
                val setupCode = """
                    if (typeof globalThis.lx !== 'undefined' && globalThis.lx.currentScriptInfo) {
                        globalThis.lx.currentScriptInfo.name = ${escapeJsString(info.name)};
                        globalThis.lx.currentScriptInfo.version = ${escapeJsString(info.version)};
                        globalThis.lx.currentScriptInfo.author = ${escapeJsString(info.author)};
                        globalThis.lx.currentScriptInfo.description = ${escapeJsString(info.description)};
                        globalThis.lx.currentScriptInfo.rawScript = '';
                    }
                """.trimIndent()
                runtime.evaluate(setupCode, "lx_setup.js")
                runtime.evaluate(cleanCode, "plugin.js")
                log("Step2: 插件脚本执行OK ✓")
            } catch (e: Exception) {
                // 与 lx-music-mobile 一致：如果插件已发送 inited 事件，说明核心功能已就绪
                // 脚本执行中的后续错误（如未知事件、未处理的 Promise 拒绝）不应中断加载
                if (runtime.getInitedPayload() != null) {
                    log("Step2 WARNING: 脚本执行有错误但 inited 已收到，继续加载: ${e.message?.take(200)}")
                } else {
                    log("Step2 FAILED: ${e.message}")
                    throw IllegalStateException("插件脚本执行失败: ${e.message?.take(300)}", e)
                }
            }

            log("Step3: 等待异步任务完成...")
            runtime.executePendingJobs()
            Thread.sleep(100)
            runtime.executePendingJobs()

            // 从注释解析插件元信息（不依赖运行时，更可靠）
            val info = parsePluginInfo(cleanCode)
            log("Step4: LX 注释解析 → name=${info.name}, version=${info.version}, author=${info.author}")

            val format = detectFormat(runtime)
            log("Step4: 插件格式 → $format")

            val sources = extractSources(runtime, format, info)
            log("Step4: 音源列表 → $sources")

            val instance = PluginInstance(
                id = hash,
                uri = uri,
                info = info,
                sources = sources,
                format = format,
                runtime = runtime,
            )
            plugins[hash] = instance
            log("=== 插件加载成功: name=${info.name}, version=${info.version}, sources=$sources, format=$format ===")
            instance
        } catch (e: Exception) {
            Log.e(TAG, "插件加载失败: $uri", e)
            null
        }
    }

    fun loadPluginFromUri(
        pluginUri: String,
        contentResolver: android.content.ContentResolver,
        context: android.content.Context,
    ): PluginInstance? {
        return try {
            val maxPluginSize = 2 * 1024 * 1024
            val byteArray = contentResolver.openInputStream(android.net.Uri.parse(pluginUri))?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    total += n
                    if (total > maxPluginSize) throw IllegalArgumentException("插件大小不能超过2MB")
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            } ?: throw IllegalStateException("读取插件文件失败")
            val script = String(byteArray, Charsets.UTF_8)
            loadPluginFromScript(script, pluginUri, context)
        } catch (e: Exception) {
            Log.e(TAG, "URI插件加载失败", e)
            null
        }
    }

    fun getPlugin(id: String): PluginInstance? = plugins[id]
    fun getAllPlugins(): List<PluginInstance> = plugins.values.toList()
    fun removePlugin(id: String) { plugins.remove(id)?.destroy() }
    fun pluginCount(): Int = plugins.size
    fun isPluginLoaded(id: String): Boolean = plugins.containsKey(id)

    fun sourceSupportsAction(pluginId: String, source: String, action: String): Boolean {
        val plugin = plugins[pluginId] ?: return true
        return plugin.sourceSupportsAction(source, action)
    }

    private fun detectLxFromSource(script: String): Boolean {
        val head = script.take(2000)
        val hasLxOn = head.contains("lx.on(") || head.contains("lx.on (")
        val hasLxSend = head.contains("lx.send(") || head.contains("lx.send (")
        val hasLxComment = head.contains("@name") && head.contains("@version")
        // 放宽检测：只要包含 lx.on 或 lx.send 调用，就认为是 lx 格式
        // （即使包含 module.exports，也允许加载，因为某些 lx 插件可能包含兼容代码）
        if (hasLxOn || hasLxSend) return true
        if (hasLxComment) return true
        log("detectLxFromSource: not lx format, hasLxOn=$hasLxOn, hasLxSend=$hasLxSend, hasLxComment=$hasLxComment, head=${head.take(200)}")
        return false
    }

    private fun parsePluginInfo(script: String): PluginInfo {
        val tagRegex = Regex("@(name|version|author|description|homepage)\\s+(.+)")
        val lines = script.lineSequence().take(120).toList()
        
        // 直接在前 120 行中查找所有匹配，不管是不是在注释块里
        val allMatches = mutableListOf<MatchResult>()
        lines.forEachIndexed { index, line ->
            tagRegex.findAll(line).forEach { match ->
                log("Direct match at line ${index + 1}: ${match.groupValues}")
                allMatches.add(match)
            }
        }
        
        // 如果直接搜索能找到，说明注释块解析有问题
        if (allMatches.isEmpty()) {
            log("No direct matches found in first 120 lines!")
        } else {
            log("Found ${allMatches.size} direct matches in first 120 lines")
        }
        
        val header = lines
            .takeWhile {
                val t = it.trim()
                val keep = t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") || t.startsWith("*/") || t.isBlank()
                keep
            }
            .toList()
            .joinToString("\n")

        log("LX header preview: ${header.take(150)}")
        val map = mutableMapOf<String, String>()
        tagRegex.findAll(header).forEach { m ->
            log("Match from header: ${m.groupValues}")
            map[m.groupValues[1].lowercase()] = m.groupValues[2].trim()
        }
        log("LX parsed map: $map")
        
        val info = PluginInfo(
            name = map["name"].orEmpty(),
            version = map["version"].orEmpty(),
            author = map["author"].orEmpty(),
            description = map["description"].orEmpty(),
            homepage = map["homepage"].orEmpty(),
        )
        log("LX plugin info: name='${info.name}', version='${info.version}', author='${info.author}'")
        return info
    }

    private fun detectFormat(runtime: QuickJSWrapper): PluginFormat {
        if (runtime.isLxHandlerRegistered()) return PluginFormat.LX
        return PluginFormat.LX
    }

    private fun extractSources(runtime: QuickJSWrapper, format: PluginFormat, info: PluginInfo): List<String> {
        val initedPayloadStr = runtime.getInitedPayload()
        val srcList = mutableListOf<String>()

        if (initedPayloadStr != null) {
            try {
                val initedPayload = JSONObject(initedPayloadStr)
                val srcObj = initedPayload.optJSONObject("sources") ?: JSONObject()
                srcObj.keys().forEach { key ->
                    srcList.add(key)
                }
                if (srcList.isEmpty()) {
                    val srcArr = initedPayload.optJSONArray("sources")
                    if (srcArr != null) {
                        (0 until srcArr.length()).forEach { i ->
                            val item = srcArr.optJSONObject(i)
                            val key = item?.keys()?.let { if (it.hasNext()) it.next() else null } ?: srcArr.optString(i)
                            if (key.isNotBlank()) srcList.add(key)
                        }
                    }
                }
            } catch (e: Exception) {
                log("extractSources: parse inited payload failed: ${e.message}")
            }
        }

        if (srcList.isEmpty() && format == PluginFormat.LX) {
            val name = info.name.lowercase()
            val key = when {
                name.contains("酷我") || name.contains("kuwo") -> "kw"
                name.contains("酷狗") || name.contains("kugou") -> "kg"
                name.contains("网易云") || name.contains("netease") || name.contains("163") -> "wy"
                name.contains("qq") || name.contains("腾讯") -> "tx"
                name.contains("咪咕") || name.contains("migu") -> "mg"
                name.contains("抖音") || name.contains("douyin") -> "dy"
                else -> ""
            }
            if (key.isNotBlank()) srcList.add(key)
        }

        if (srcList.isEmpty()) {
            srcList.add("kw")
        }

        return srcList
    }

    private val okHttpClientWithCookie: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .cookieJar(object : okhttp3.CookieJar {
                private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                    cookieStore[url.host] = cookies.toMutableList()
                    val domain = url.topPrivateDomain()
                    if (domain != url.host) {
                        val existing = cookieStore.getOrPut(domain) { mutableListOf() }
                        for (c in cookies) {
                            if (c.domain == domain || c.domain == "." + domain) {
                                existing.removeAll { it.name == c.name }
                                existing.add(c)
                            }
                        }
                    }
                }
                override fun loadForRequest(url: okhttp3.HttpUrl): MutableList<okhttp3.Cookie> {
                    val result = mutableListOf<okhttp3.Cookie>()
                    cookieStore[url.host]?.let { result.addAll(it) }
                    val domain = url.topPrivateDomain()
                    if (domain != url.host) {
                        cookieStore[domain]?.let { result.addAll(it) }
                    }
                    return result
                }
            })
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private var bilibiliCookieFetched = false
    private suspend fun ensureBilibiliCookie() {
        if (bilibiliCookieFetched) return
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://www.bilibili.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                okHttpClientWithCookie.newCall(request).execute().close()
                bilibiliCookieFetched = true
                log("bilibili cookie prefetched")
            }
        } catch (e: Exception) {
            log("bilibili cookie prefetch failed: ${e.message}")
        }
    }

    private fun executeNativeRequest(method: String, url: String, headersJson: String, bodyStr: String): String {
        val requestBuilder = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

        if (url.contains("bilibili.com") || url.contains("biligame.com")) {
            requestBuilder.header("Referer", "https://www.bilibili.com")
            requestBuilder.header("Origin", "https://www.bilibili.com")
        }

        try {
            val headers = JSONObject(headersJson)
            headers.keys().forEach { key ->
                val value = headers.getString(key)
                if (key.equals("Accept-Encoding", ignoreCase = true)) {
                    return@forEach
                }
                requestBuilder.header(key, value)
            }
        } catch (_: Exception) {}

        if (method.equals("POST", ignoreCase = true) && bodyStr.isNotBlank()) {
            val contentType = try {
                val hdrs = JSONObject(headersJson)
                val ct = hdrs.optString("Content-Type", "").ifBlank { hdrs.optString("content-type", "") }
                if (ct.contains("x-www-form-urlencoded")) "application/x-www-form-urlencoded; charset=utf-8"
                else if (ct.contains("multipart")) "multipart/form-data"
                else "application/json; charset=utf-8"
            } catch (_: Exception) { "application/json; charset=utf-8" }
            val mediaType = contentType.toMediaTypeOrNull()
            requestBuilder.post(RequestBody.create(mediaType, bodyStr))
        } else if (method.equals("PUT", ignoreCase = true) && bodyStr.isNotBlank()) {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            requestBuilder.put(RequestBody.create(mediaType, bodyStr))
        } else {
            requestBuilder.get()
        }

        val response = okHttpClientWithCookie.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""

        val result = JSONObject()
        result.put("statusCode", response.code)
        result.put("body", responseBody)
        val respHeaders = JSONObject()
        response.headers.toMap().forEach { (k, v) -> respHeaders.put(k, v) }
        result.put("headers", respHeaders)

        return result.toString()
    }

    private fun escapeJsString(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\u0000", "\\0")
        return "'$escaped'"
    }

    data class PluginSearchResult(val songs: List<Song>, val isEnd: Boolean)

    inner class PluginInstance(
        val id: String,
        val uri: String,
        val info: PluginInfo,
        val sources: List<String>,
        val format: PluginFormat,
        private val runtime: QuickJSWrapper,
    ) {
        fun sourceSupportsAction(source: String, action: String): Boolean = true

        suspend fun search(source: String, keyword: String, timeoutMs: Long, page: Int = 1, type: String = "music"): PluginSearchResult {
            return withContext(Dispatchers.IO) {
                doLxSearch(source, keyword, timeoutMs, page)
            }
        }

        suspend fun musicUrl(source: String, song: Song, quality: String, timeoutMs: Long): MusicUrlResult {
            return withContext(Dispatchers.IO) {
                doLxMusicUrl(source, song, quality, timeoutMs)
            }
        }

        suspend fun lyric(source: String, song: Song, timeoutMs: Long): LyricResult {
            return withContext(Dispatchers.IO) {
                doLxLyric(source, song, timeoutMs)
            }
        }

        suspend fun getAlbumInfo(source: String, albumRawJson: String, timeoutMs: Long): PluginSearchResult {
            return withContext(Dispatchers.IO) {
                PluginSearchResult(emptyList(), true)
            }
        }

        suspend fun getArtistWorks(source: String, artistRawJson: String, timeoutMs: Long, page: Int = 1): PluginSearchResult {
            return withContext(Dispatchers.IO) {
                PluginSearchResult(emptyList(), true)
            }
        }

        suspend fun getMusicSheetInfo(source: String, sheetRawJson: String, timeoutMs: Long, page: Int = 1): PluginSearchResult {
            return withContext(Dispatchers.IO) {
                PluginSearchResult(emptyList(), true)
            }
        }

        fun destroy() {
            try { runtime.close() } catch (_: Exception) {}
        }

        private suspend fun doLxSearch(source: String, keyword: String, timeoutMs: Long, page: Int = 1): PluginSearchResult {
            val info = JSONObject()
                .put("keyword", keyword)
                .put("page", page)
                .put("limit", 30)
            val payload = JSONObject()
                .put("source", source)
                .put("action", "search")
                .put("info", info)
            val ret = callLxAction(source, "search", info, timeoutMs)
            val songs = parseSearchResult(ret, source)
            val isEnd = ret.optBoolean("isEnd", true).let { end ->
                if (end) true else songs.size < 30
            }
            return PluginSearchResult(songs, isEnd)
        }

        private suspend fun doLxMusicUrl(source: String, song: Song, quality: String, timeoutMs: Long): MusicUrlResult {
            val musicInfo = buildMusicInfo(source, song, quality)
            // 兼容 lxmusic API 服务器要求：source/musicId/quality 在顶层（不只 musicInfo 内部）
            // musicId = musicInfo.hash ?? musicInfo.songmid ?? platformId（与 lxmusic.js 一致）
            val musicId = musicInfo.optString("hash").ifBlank {
                musicInfo.optString("songmid").ifBlank {
                    musicInfo.optString("musicId").ifBlank { song.platformId }
                }
            }
            // lx quality → lxmusic quality 映射（兼容 exhigh 等扩展值）
            val lxQuality = when (quality) {
                "128k", "128" -> "128k"
                "320k", "exhigh" -> "320k"
                "flac", "lossless" -> "flac"
                "flac24bit", "hires" -> "flac24bit"
                else -> quality
            }
            val info = JSONObject()
                .put("type", quality)
                .put("musicInfo", musicInfo)
                // 顶层兼容字段（lxmusic API 服务器期望）
                .put("source", source)
                .put("musicId", musicId)
                .put("quality", lxQuality)
            var ret = callLxAction(source, "musicUrl", info, timeoutMs)
            var result = parseMusicUrlResult(ret)
            // 如果请求的音质不可用，自动降级到128k重试
            if (result.url.isBlank() && quality != "128k" && quality != "128") {
                log("LX musicUrl: quality $quality returned empty url, retrying with 128k")
                val retryInfo = JSONObject()
                    .put("type", "128k")
                    .put("musicInfo", musicInfo)
                    .put("source", source)
                    .put("musicId", musicId)
                    .put("quality", "128k")
                ret = callLxAction(source, "musicUrl", retryInfo, timeoutMs)
                result = parseMusicUrlResult(ret)
            }
            return result
        }

        private suspend fun doLxLyric(source: String, song: Song, timeoutMs: Long): LyricResult {
            val musicInfo = buildMusicInfo(source, song, "lrc")
            // 顶层兼容字段
            val musicId = musicInfo.optString("hash").ifBlank {
                musicInfo.optString("songmid").ifBlank {
                    musicInfo.optString("musicId").ifBlank { song.platformId }
                }
            }
            val info = JSONObject()
                .put("type", "lrc")
                .put("musicInfo", musicInfo)
                .put("source", source)
                .put("musicId", musicId)
                .put("quality", "lrc")
            val ret = callLxAction(source, "lyric", info, timeoutMs)
            return parseLyricResult(ret)
        }

        private suspend fun callLxAction(source: String, action: String, info: JSONObject, timeoutMs: Long): JSONObject {
            return withTimeoutOrNull(timeoutMs) {
                try {
                    val payloadJson = JSONObject()
                        .put("source", source)
                        .put("action", action)
                        .put("info", info)

                    // 检查 handler 是否已注册
                    val checkHandler = runtime.evaluateForResult(
                        "(function(){ var h = globalThis.__lxRequestHandler; return typeof h === 'function' ? 'ok' : 'none'; })()",
                        "check_handler.js"
                    )
                    if (checkHandler != "ok") {
                        return@withTimeoutOrNull JSONObject().put("error", "no lx request handler")
                    }

                    // 统一使用异步路径，避免 handler 被重复调用两次
                    runtime.evaluate("globalThis.__lxCallResult = undefined;", "reset_lx.js")

                    val asyncJs = """
                        (async function(){
                            try {
                                var handler = globalThis.__lxRequestHandler;
                                var payload = JSON.parse(${escapeJsString(payloadJson.toString())});
                                var result = await handler(payload);
                                // 完全照搬 lx-music-desktop preload.js handleRequest 校验逻辑
                                switch (payload.action) {
                                    case 'musicUrl':
                                        // 校验：必须是 string、长度<2048、以 http: 或 https: 开头
                                        if (typeof result != 'string' || result.length > 2048 || !/^https?:/.test(result)) {
                                            var preview = (typeof result) + ' ' + String(result || '').substring(0, 200);
                                            globalThis.__lxCallResult = JSON.stringify({error: 'invalid musicUrl response: ' + preview});
                                        } else {
                                            globalThis.__lxCallResult = JSON.stringify({url: result, type: payload.info.type});
                                        }
                                        break;
                                    case 'lyric':
                                        globalThis.__lxCallResult = JSON.stringify(result || {});
                                        break;
                                    case 'pic':
                                        if (typeof result != 'string' || result.length > 2048 || !/^https?:/.test(result)) {
                                            globalThis.__lxCallResult = JSON.stringify({error: 'invalid pic response'});
                                        } else {
                                            globalThis.__lxCallResult = JSON.stringify({url: result});
                                        }
                                        break;
                                    default:
                                        globalThis.__lxCallResult = JSON.stringify(result || {});
                                }
                            } catch(e) {
                                globalThis.__lxCallResult = JSON.stringify({error: e.message || 'async error'});
                            }
                        })();
                    """.trimIndent()
                    runtime.evaluate(asyncJs, "call_lx_async.js")

                    val startMs = System.currentTimeMillis()
                    var finalResult: String? = null
                    while (System.currentTimeMillis() - startMs < timeoutMs) {
                        runtime.executePendingJobs()
                        Thread.sleep(50)
                        runtime.executePendingJobs()
                        try {
                            val check = runtime.evaluateForResult(
                                "typeof globalThis.__lxCallResult!=='undefined'?globalThis.__lxCallResult:'__PENDING__'", "poll_lx.js")
                            if (check != null && check != "__PENDING__") {
                                finalResult = check
                                break
                            }
                        } catch (_: Exception) {}
                    }
                    if (finalResult == null) finalResult = "{\"error\":\"timeout ${timeoutMs}ms\"}"
                    // JS 端已按 lx-music-desktop handleRequest 校验逻辑包装为 {url, type} 或 {error}
                    // 兼容旧逻辑：如果 finalResult 是纯 URL 字符串（带引号），也包装为 {url}
                    var trimmed = finalResult.trim()
                    repeat(5) {
                        val before = trimmed
                        when {
                            trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2 ->
                                trimmed = trimmed.substring(1, trimmed.length - 1)
                            trimmed.startsWith("\\\"") && trimmed.endsWith("\\\"") && trimmed.length >= 4 ->
                                trimmed = trimmed.substring(2, trimmed.length - 2)
                            else -> return@repeat
                        }
                        if (trimmed == before) return@repeat
                    }
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        log("LX callLxAction: detected direct URL string, len=${trimmed.length}")
                        JSONObject().put("url", trimmed)
                    } else {
                        try { JSONObject(finalResult) } catch (e: Exception) {
                            log("LX callLxAction: finalResult parse failed, len=${finalResult.length}, preview=${finalResult.take(200)}")
                            JSONObject().put("error", "parse failed: ${finalResult.take(100)}")
                        }
                    }
                } catch (e: Exception) {
                    JSONObject().put("error", "evaluate error: ${e.message}")
                }
            } ?: JSONObject().put("error", "timeout after ${timeoutMs}ms")
        }

        private fun buildMusicInfo(source: String, song: Song, quality: String): JSONObject {
            val musicInfo = if (song.pluginRawJson.isNotBlank()) {
                try {
                    val raw = JSONObject(song.pluginRawJson)
                    if (!raw.has("name")) raw.put("name", song.title)
                    if (!raw.has("singer")) raw.put("singer", song.artist)
                    if (!raw.has("types")) {
                        val typesArr = JSONArray()
                        listOf("128k", "320k", "flac").forEach { typesArr.put(JSONObject().put("type", it)) }
                        raw.put("types", typesArr)
                    }
                    ensurePlatformIdAliases(raw, source, song.platformId)
                    raw
                } catch (_: Exception) {
                    buildFallbackMusicInfo(source, song)
                }
            } else {
                buildFallbackMusicInfo(source, song)
            }
            musicInfo.put("type", quality)
            return musicInfo
        }

        private fun buildFallbackMusicInfo(source: String, song: Song): JSONObject {
            val obj = JSONObject()
                .put("id", song.platformId)
                .put("name", song.title)
                .put("singer", song.artist)
                .put("artists", JSONArray().put(JSONObject().put("name", song.artist)))
            val typesArr = JSONArray()
            listOf("128k", "320k", "flac").forEach { typesArr.put(JSONObject().put("type", it)) }
            obj.put("types", typesArr)
            ensurePlatformIdAliases(obj, source, song.platformId)
            return obj
        }

        private fun ensurePlatformIdAliases(obj: JSONObject, source: String, platformId: String) {
            val lower = source.lowercase()
            // 辅助函数：字段不存在或为空字符串/null 时设置
            fun ensureField(key: String, value: String) {
                val existing = obj.optString(key, "")
                if (existing.isBlank() || existing == "null") obj.put(key, value)
            }
            when {
                lower == "tx" || lower.startsWith("qq") -> {
                    ensureField("songmid", platformId)
                    ensureField("strMediaMid", platformId)
                    ensureField("mid", platformId)
                }
                lower == "kg" || lower.startsWith("kugou") -> {
                    ensureField("hash", platformId)
                    ensureField("FileHash", platformId)
                }
                lower == "kw" || lower.startsWith("kuwo") -> {
                    ensureField("rid", platformId)
                    ensureField("musicrid", "MUSIC_$platformId")
                }
                lower == "mg" || lower.startsWith("migu") -> {
                    ensureField("copyrightId", platformId)
                }
                lower == "qsvip" || lower == "dy" || lower.startsWith("douyin") -> {
                    ensureField("id", platformId)
                }
            }
            // 确保通用字段 musicId/songId/source 存在且非空（lx-music-desktop 插件要求）
            ensureField("musicId", platformId)
            ensureField("songId", platformId)
            ensureField("source", source)
        }

        private fun parseSearchResult(ret: JSONObject, source: String): List<Song> {
            val error = ret.optString("error")
            if (error.isNotBlank()) throw IllegalStateException(error)
            val dataObj = ret.optJSONObject("data")
            val list = ret.optJSONArray("list")
                ?: ret.optJSONArray("songs")
                ?: dataObj?.optJSONArray("list")
                ?: dataObj?.optJSONArray("songs")
                ?: JSONArray()
            return parseSongList(list, source)
        }

        private fun parseSongList(list: JSONArray, source: String): List<Song> {
            return (0 until list.length()).mapNotNull { i ->
                val o = list.optJSONObject(i) ?: return@mapNotNull null
                val songId = o.optString("songmid").ifBlank {
                    o.optString("id").ifBlank { o.optString("hash").ifBlank { o.optString("musicId").ifBlank { o.optString("songId").ifBlank { o.optString("FileHash") } } } }
                }
                // 标题：兼容酷狗 SongName（大写驼峰）
                val title = o.optString("title").ifBlank {
                    o.optString("name").ifBlank { o.optString("songname").ifBlank { o.optString("SongName") } }
                }
                // 歌手：兼容酷狗 Singers（JSON数组字符串）和标准 artists 数组
                val artist = o.optString("singer").ifBlank {
                    o.optString("Singers").ifBlank {
                        val artistArr = o.optJSONArray("artists")
                        if (artistArr != null) {
                            (0 until artistArr.length()).mapNotNull { idx ->
                                val item = artistArr.opt(idx)
                                when (item) {
                                    is String -> item.takeIf { it.isNotBlank() }
                                    is JSONObject -> item.optString("name").takeIf { it.isNotBlank() }
                                    else -> item?.toString()?.takeIf { it.isNotBlank() }
                                }
                            }.joinToString("/")
                        } else o.optString("artist")
                    }
                }
                // 解析 Singers 字段（酷狗返回 JSON 数组字符串如 [{"name":"林俊杰"}]）
                val resolvedArtist = if (artist.startsWith("[") && artist.contains("name")) {
                    try {
                        val arr = JSONArray(artist)
                        (0 until arr.length()).mapNotNull { idx ->
                            arr.optJSONObject(idx)?.optString("name")?.takeIf { it.isNotBlank() }
                        }.joinToString("/")
                    } catch (_: Exception) { artist }
                } else artist
                // 专辑：兼容酷狗 AlbumName（大写驼峰）
                val album = o.optString("album").ifBlank {
                    o.optString("albumName").ifBlank {
                        o.optString("AlbumName").ifBlank {
                            val albumObj = o.opt("album")
                            when (albumObj) {
                                is String -> albumObj
                                is JSONObject -> albumObj.optString("name")
                                else -> albumObj?.toString().orEmpty()
                            }
                        }
                    }
                }
                // 封面：兼容酷狗 ImgUrl（含{size}占位符）和 MF 插件设置的 coverUrl
                var coverUrl = o.optString("img").ifBlank {
                    o.optString("pic").ifBlank {
                        o.optString("cover").ifBlank {
                            o.optString("artwork").ifBlank {
                                o.optString("albumPic").ifBlank {
                                    o.optString("picture").ifBlank {
                                        o.optString("ImgUrl").ifBlank {
                                            o.optString("coverUrl").ifBlank {
                                                val albumObj = o.optJSONObject("album")
                                                albumObj?.optString("pic").orEmpty().ifBlank {
                                                    albumObj?.optString("img").orEmpty()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // 替换酷狗 ImgUrl 中的 {size} 占位符
                if (coverUrl.contains("{size}")) {
                    coverUrl = coverUrl.replace("{size}", "240")
                }
                val durationMs = o.optLong("interval", 0L).let { interval ->
                    if (interval > 0) interval * 1000L
                    else o.optLong("duration", 0L).let { dur ->
                        if (dur in 1..9999) dur * 1000L else dur
                    }
                }
                val lrcText = o.optString("lrc").ifBlank {
                    o.optString("lyric").ifBlank {
                        o.optString("lrcText")
                    }
                }
                if (songId.isBlank() && title.isBlank()) return@mapNotNull null
                Song(
                    id = songId.hashCode().toLong(),
                    title = title,
                    artist = resolvedArtist,
                    album = album,
                    duration = durationMs,
                    coverUrl = coverUrl,
                    lrcText = lrcText,
                    platform = sourceDisplayName(source),
                    platformId = songId,
                    pluginRawJson = o.toString(),
                    lxSourceKey = source,
                    lxPluginId = id,
                )
            }
        }

        private fun sourceDisplayName(sourceKey: String): String {
            val lower = sourceKey.lowercase()
            return when {
                lower == "tx" || lower.startsWith("qq") || lower.contains("qqmusic") -> "QQ音乐"
                lower == "wy" || lower == "netease" || lower.startsWith("163") -> "网易云"
                lower == "kw" || lower.startsWith("kuwo") -> "酷我音乐"
                lower == "kg" || lower.startsWith("kugou") -> "酷狗音乐"
                lower == "mg" || lower.startsWith("migu") -> "咪咕音乐"
                lower == "qsvip" || lower == "dy" || lower.startsWith("douyin") -> "汽水音乐"
                else -> sourceKey
            }
        }

        private fun parseMusicUrlResult(ret: JSONObject): MusicUrlResult {
            val url = ret.optString("url").ifBlank {
                ret.optString("musicUrl").ifBlank {
                    ret.optJSONObject("data")?.optString("url").orEmpty()
                }
            }
            val headers = mutableMapOf<String, String>()
            val headersObj = ret.optJSONObject("headers")
                ?: ret.optJSONObject("data")?.optJSONObject("headers")
                ?: JSONObject()
            headersObj.keys().forEach { k ->
                headers[k] = headersObj.optString(k)
            }
            // 修复：不抛致命异常，记录错误后返回空结果让上层降级重试
            // 上层 doLxMusicUrl 会根据 url 是否为空决定是否重试 128k
            // 上层 tryPlugin 会根据 url 是否为空决定是否走下一个插件
            if (url.isBlank()) {
                val err = ret.optString("error", "plugin return empty url")
                if (err.isNotBlank() && err != "null") {
                    Log.w(TAG, "parseMusicUrlResult: empty url, error=$err")
                }
                return MusicUrlResult("", headers)
            }
            return MusicUrlResult(url, headers)
        }

        private fun parseLyricResult(ret: JSONObject): LyricResult {
            val error = ret.optString("error")
            if (error.isNotBlank()) {
                Log.w(TAG, "歌词获取错误: $error")
                return LyricResult()
            }
            val lyric = ret.optString("lyricText").ifBlank {
                ret.optString("lyric").ifBlank {
                    ret.optString("lrcText").ifBlank {
                        ret.optString("url").ifBlank {
                            ret.optJSONObject("data")?.optString("lyric").orEmpty()
                        }
                    }
                }
            }
            val tlyric = ret.optString("tlyricText").ifBlank {
                ret.optString("tlyric").ifBlank {
                    ret.optJSONObject("data")?.optString("tlyric").orEmpty()
                }
            }
            return LyricResult(lyric = lyric, tlyric = tlyric)
        }

        private fun escapeJsString(s: String): String {
            val escaped = s
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\u0000", "\\0")
            return "'$escaped'"
        }
    }

    companion object {
        private val TAG = "LuoxuePlugin"
        private var cachedPreludeScript: String? = null
        private var _builtinEntry: PluginEntry? = null
        @JvmStatic var isLoaded: Boolean = false; private set
        @JvmStatic var pluginId: String = ""; private set
        @JvmStatic var sources: List<String> = emptyList(); private set

        @JvmStatic
        suspend fun syncAndLoad(
            pluginManager: LxPluginManager,
            logCallback: ((String) -> Unit)? = null,
        ) {
            try {
                val script = BuiltinPluginManager.loadBuiltinScript()
                if (script.isBlank()) return
                val opts = LuoxueRuntimeOptions(callTimeoutMs = 15000L, allowHttp = true)
                val result = kotlin.runCatching { pluginManager.loadPluginFromScript(script, "builtin://default", opts).getOrThrow() }
                if (result.isSuccess) {
                    val entry = result.getOrThrow()
                    _builtinEntry = entry
                    isLoaded = true
                    pluginId = entry.id
                    sources = entry.sources.ifEmpty { listOf("qq") }
                }
            } catch (_: Exception) {}
        }

        @JvmStatic
        suspend fun musicUrl(pluginManager: LxPluginManager, song: Song, quality: String): LxMusicUrlResult? {
            return try {
                if (!isLoaded || pluginId.isBlank()) return null
                pluginManager.musicUrl(pluginId, sources.firstOrNull() ?: "qq", song, 15000L, quality)
            } catch (_: Exception) { null }
        }
    }
}
