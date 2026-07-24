package com.yindong.music.data.lx

import android.content.Context
import android.util.Log
import com.yindong.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 落雪插件引擎 - 完全基于 lx-music-mobile-master 的插件系统
 *
 * 本类是 [LxQuickJS] 的高层封装，专门用于音频播放 URL 解析。
 *  - 引擎架构（HandlerThread + 异步 HTTP + __lx_native_call__ 通信桥）100% 移植自
 *    lx-music-mobile 的 QuickJS.java + JavaScriptThread.java + JsHandler.java
 *  - 预加载脚本 user-api-preload.js 100% 复制自 lx-music-mobile
 *  - 加载的插件脚本 lxmusic.js 即项目自带的落雪音源插件
 *
 * 与旧版 [LuoxuePluginManager] / [com.yindong.music.data.lxsdk.LxSdkMusicUrl] 的区别：
 *  - 旧版用同步 HTTP 阻塞 JS 线程，且 executePendingJobs 是 no-op，导致 Promise 永远不 resolve
 *  - 新版完全采用 lx-music-mobile 的异步架构，Promise 可正常解析
 *
 * 通信流程（与 lx-music-mobile index.ts 完全一致）：
 *   App → callJS("request", {requestKey, data}) → JS handleRequest → lxmusic.js handler
 *   lxmusic.js → lx.request → nativeCall("request") → Java 异步 HTTP
 *   Java HTTP 完成 → callJS("response", {requestKey, error, response}) → JS callback → Promise resolve
 *   lxmusic.js handler 完成 → nativeCall("response", {requestKey, status, result}) → Java resolve deferred
 */
object LxPluginEngine {

    private const val TAG = "LxPluginEngine"

    /** 插件 ID（与 MusicViewModel.BUILTIN_LXMUSIC_ID 保持一致，仅用于日志/兼容性） */
    const val PLUGIN_ID = "builtin_lxmusic"

    /** 与 lxmusic.js MUSIC_QUALITY 完全一致的音源支持表（用于 isSourceSupported） */
    private val SUPPORTED_SOURCES = setOf("wy", "tx", "kw", "kg", "mg")

    /** 与 lxmusic.js MUSIC_QUALITY 完全一致的音源→支持音质列表（用于音质降级 / 音质检测） */
    val MUSIC_QUALITY: Map<String, List<String>> = mapOf(
        "kg" to listOf("128k", "320k", "flac", "flac24bit", "hires", "atmos", "master"),
        "kw" to listOf("128k", "320k", "flac", "flac24bit", "hires"),
        "tx" to listOf("128k", "320k", "flac", "flac24bit", "hires", "atmos", "atmos_plus", "master"),
        "wy" to listOf("128k", "320k", "flac", "flac24bit", "hires", "atmos", "master"),
        "mg" to listOf("128k", "320k", "flac", "flac24bit", "hires"),
    )

    // ═════════════════════════════════════════════════════════════════
    //  各平台音质规格定义（与各平台官方 App 的音质命名保持一致）
    //  - 仅展示该平台实际支持的音质（lxmusic.js 不支持的 quality 直接隐藏）
    //  - lxKey=null 表示该音质在 lxmusic.js 中无对应实现，需隐藏
    // ═════════════════════════════════════════════════════════════════
    data class PlatformQualitySpec(
        val displayName: String,    // 平台官方音质名（如 "Hi-Res音质"）
        val lxKey: String?,         // 对应的 LX 插件音质 key，null 表示 lxmusic.js 不支持 → 隐藏
        val description: String,    // 简短描述（如 "高解析度无损 24bit"）
    )

    /** 网易云音乐音质规格（按官方 App 排序，共 8 档，实际可见 6 档） */
    val WY_QUALITY_SPECS: List<PlatformQualitySpec> = listOf(
        PlatformQualitySpec("标准音质", "128k", "标准 MP3 128kbps"),
        PlatformQualitySpec("极高音质", "320k", "高品质 MP3 320kbps"),
        PlatformQualitySpec("无损音质", "flac", "FLAC 无损 16bit"),
        PlatformQualitySpec("Hi-Res音质", "flac24bit", "高解析度无损 24bit"),
        PlatformQualitySpec("高清环绕声", "atmos", "环绕声音效"),
        PlatformQualitySpec("沉浸环绕声", null, "沉浸式环绕声（暂不支持）"),
        PlatformQualitySpec("超清母带", "master", "192kHz/24bit 母带"),
        PlatformQualitySpec("杜比全景声", null, "杜比全景声（暂不支持）"),
    )

    /** QQ音乐音质规格（共 9 档，实际可见 7 档） */
    val TX_QUALITY_SPECS: List<PlatformQualitySpec> = listOf(
        PlatformQualitySpec("标准品质", "128k", "标准 MP3 128kbps"),
        PlatformQualitySpec("HQ高品质", "320k", "高品质 MP3 320kbps"),
        PlatformQualitySpec("SQ无损品质", "flac", "FLAC 无损 16bit"),
        PlatformQualitySpec("Hi-Res无损", "flac24bit", "高解析度无损 24bit"),
        PlatformQualitySpec("臻品音质", null, "臻品音质（暂不支持）"),
        PlatformQualitySpec("臻品母带", "master", "192kHz/24bit 母带"),
        PlatformQualitySpec("臻品全景声", "atmos", "QQ 全景声音效"),
        PlatformQualitySpec("杜比全景声", "atmos_plus", "杜比全景声"),
        PlatformQualitySpec("DTS:X音质", null, "DTS:X 音质（暂不支持）"),
    )

    /** 酷我音乐音质规格（共 8 档，实际可见 4 档） */
    val KW_QUALITY_SPECS: List<PlatformQualitySpec> = listOf(
        PlatformQualitySpec("标准音质", "128k", "标准 MP3 128kbps"),
        PlatformQualitySpec("高品质", "320k", "高品质 MP3 320kbps"),
        PlatformQualitySpec("超品音质", "flac", "FLAC 无损 16bit"),
        PlatformQualitySpec("Hi-Res音质", "flac24bit", "高解析度无损 24bit"),
        PlatformQualitySpec("至臻音质2.0", null, "至臻音质 2.0（暂不支持）"),
        PlatformQualitySpec("至臻母带", null, "至臻母带（暂不支持）"),
        PlatformQualitySpec("至臻全景声", null, "至臻全景声（暂不支持）"),
        PlatformQualitySpec("杜比全景声", null, "杜比全景声（暂不支持）"),
    )

    /** 酷狗音乐音质规格（共 7 档，实际可见 6 档） */
    val KG_QUALITY_SPECS: List<PlatformQualitySpec> = listOf(
        PlatformQualitySpec("标准音质", "128k", "标准 MP3 128kbps"),
        PlatformQualitySpec("高品音质", "320k", "高品质 MP3 320kbps"),
        PlatformQualitySpec("无损音质", "flac", "FLAC 无损 16bit"),
        PlatformQualitySpec("Hi-Res音质", "flac24bit", "高解析度无损 24bit"),
        PlatformQualitySpec("黑胶音质", "master", "黑胶母带 192kHz/24bit"),
        PlatformQualitySpec("杜比全景声", null, "杜比全景声（暂不支持）"),
        PlatformQualitySpec("5.1/7.1环绕声", "atmos", "环绕声音效 5.1/7.1 声道"),
    )

    /** 各平台音质规格表（仅 4 大平台，咪咕不参与音质检测） */
    val PLATFORM_QUALITY_SPECS: Map<String, List<PlatformQualitySpec>> = mapOf(
        "wy" to WY_QUALITY_SPECS,
        "tx" to TX_QUALITY_SPECS,
        "kw" to KW_QUALITY_SPECS,
        "kg" to KG_QUALITY_SPECS,
    )

    /**
     * 检测到的音质项（含文件大小信息）。
     * - 用于 [com.yindong.music.viewmodel.MusicViewModel.activePluginSupportedQualitiesBySource]
     * - UI 直接消费 [displayName]、[description]、[fileSizeText]
     */
    data class DetectedQuality(
        val source: String,           // 平台 source key（wy/tx/kw/kg）
        val displayName: String,      // 平台官方音质名
        val lxKey: String,            // LX 插件音质 key
        val description: String,      // 描述
        val fileSizeBytes: Long,      // 文件大小（字节），0 表示未知
        val fileSizeText: String,     // 文件大小展示文本（如 "8.5 MB"），未知时为空
    )

    /**
     * 通过 HEAD 请求获取音频文件大小（字节）。
     * - 部分服务器不支持 HEAD 时回退为 GET + Range:bytes=0-0，读取 Content-Range
     * - 失败时返回 0（UI 显示为"未知大小"）
     * - 总超时 6s，避免阻塞音质检测流程
     */
    suspend fun fetchFileSize(url: String, headers: Map<String, String> = emptyMap()): Long {
        if (url.isBlank() || !url.startsWith("http")) return 0L
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                // 1) 先尝试 HEAD
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 4_000
                    readTimeout = 4_000
                    instanceFollowRedirects = true
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    val len = conn.contentLengthLong
                    if (len > 0) return@withContext len
                }
                // 2) HEAD 失败或不返回长度 → 回退到 Range GET
                conn.disconnect()
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4_000
                    readTimeout = 4_000
                    instanceFollowRedirects = true
                    setRequestProperty("Range", "bytes=0-0")
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
                }
                val code2 = conn.responseCode
                if (code2 in 200..299 || code2 == 206) {
                    // 优先 Content-Range: bytes 0-0/12345
                    val contentRange = conn.getHeaderField("Content-Range")
                    if (!contentRange.isNullOrBlank()) {
                        // 格式: bytes 0-0/12345
                        val slashIdx = contentRange.lastIndexOf('/')
                        if (slashIdx >= 0 && slashIdx < contentRange.length - 1) {
                            val total = contentRange.substring(slashIdx + 1).trim().toLongOrNull() ?: 0L
                            if (total > 0) return@withContext total
                        }
                    }
                    val len = conn.contentLengthLong
                    if (len > 0) return@withContext len
                }
                0L
            } catch (_: Exception) {
                0L
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    /** 将字节数格式化为友好的文件大小展示文本 */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * 检测指定音源集合支持的全部音质档位（取并集）。
     * 用于启用插件时动态识别其支持的音质选项。
     *
     * @param sources 插件支持的音源 key 列表（如 listOf("wy","tx","kw","kg")）
     * @return 该插件支持的全部 LX 音质 key 并集（如 setOf("128k","320k","flac","flac24bit","hires","atmos","master")）
     */
    fun detectSupportedQualities(sources: List<String>): Set<String> {
        val result = linkedSetOf<String>()
        for (src in sources) {
            MUSIC_QUALITY[src]?.let { result.addAll(it) }
        }
        return result
    }

    /** 引擎是否已就绪（已加载脚本并收到 inited 事件） */
    @Volatile private var engineReady = false
    @Volatile private var engineInitError: String? = null

    /** 真正的 QuickJS 引擎实例（懒加载） */
    @Volatile private var engine: LxQuickJS? = null
    private val engineLock = Any()

    /** 上层注入的日志回调（由 MusicViewModel 设置） */
    @Volatile private var logCallback: ((String) -> Unit)? = null

    /** 注入日志回调（必须在 [ensureEngine] 之前调用） */
    fun setLogCallback(callback: ((String) -> Unit)?) {
        logCallback = callback
    }

    /**
     * 初始化引擎（线程安全、幂等）
     *
     * ⚠️ 注意：内置落雪音源（lxmusic.js）已按用户要求移除，不再自动加载。
     * 此方法保留用于未来可能的扩展（例如用户显式导入 lxmusic.js 风格的脚本）。
     * 引擎基础设施（LxQuickJS）保留，但不会自动加载任何脚本。
     *
     * @param context Application Context
     * @return 成功返回 null，失败返回错误信息
     */
    fun ensureEngine(context: Context): String? {
        // 内置 lxmusic.js 已移除，引擎不再自动加载任何脚本。
        // 保留方法签名以兼容现有调用点（getMusicUrl / getLyric 会优雅地返回错误）。
        if (engineReady) return null
        synchronized(engineLock) {
            if (engineReady) return null
            // 标记为"已就绪"但不加载脚本；getMusicUrl 等方法会返回"无插件加载"错误。
            engineReady = true
            Log.i(TAG, "LxPluginEngine ready (无内置脚本加载，等待用户导入 LX/MusicFree 插件)")
            logCallback?.invoke("落雪插件引擎就绪 (无内置脚本)")
            return null
        }
    }

    /** 是否已就绪 */
    fun isReady(): Boolean = engineReady

    /** 音源是否被支持（与 lxmusic.js MUSIC_QUALITY 一致） */
    fun isSourceSupported(source: String): Boolean = source in SUPPORTED_SOURCES

    /** 销毁引擎（一般在 Application 退出时调用） */
    fun destroy() {
        synchronized(engineLock) {
            engine?.close()
            engine = null
            engineReady = false
            engineInitError = null
        }
    }

    /**
     * 获取播放 URL - 完全通过 lx-music-mobile 插件系统解析
     *
     * 与 [com.yindong.music.data.lxsdk.LxSdkMusicUrl.getMusicUrl] 行为等价，
     * 但解析过程完全委托给 lxmusic.js 插件（运行在 LxQuickJS 中），
     * 而非 Kotlin 端直接 HTTP 调用 API。
     *
     * @param context Application Context（用于懒加载引擎）
     * @param source 音源 id（wy/tx/kw/kg）
     * @param song 歌曲对象（必须包含 platformId 或 pluginRawJson）
     * @param quality 音质（128k/320k/flac/flac24bit/hires/...）
     * @param timeoutMs 超时（默认 20s）
     * @return MusicUrlResult（url 为空表示失败，error 包含错误信息）
     */
    suspend fun getMusicUrl(
        context: Context,
        source: String,
        song: Song,
        quality: String,
        timeoutMs: Long = 20_000L,
    ): MusicUrlResult {
        // 1. 引擎就绪检查
        val initErr = ensureEngine(context)
        if (initErr != null) {
            return MusicUrlResult(error = "插件引擎未就绪: $initErr")
        }
        // 内置 lxmusic.js 已移除，engine 可能为 null（无脚本加载）
        val js = engine
        if (js == null) {
            return MusicUrlResult(error = "无内置落雪音源，请导入 MusicFree 插件或 LX 插件后重试")
        }

        // 2. 音源支持检查（与 lxmusic.js MUSIC_QUALITY 一致）
        if (!isSourceSupported(source)) {
            return MusicUrlResult(error = "音源 $source 暂不支持")
        }

        // 2.1 音质降级（与 LxSdkMusicUrl 行为一致：请求音质不在支持列表时降级到 128k）
        val supportedQualities = MUSIC_QUALITY[source] ?: emptyList()
        val actualQuality = if (supportedQualities.contains(quality)) quality else "128k"

        // 3. 构建 musicInfo JSON（与 LuoxuePluginManager.buildMusicInfo 思路一致）
        val musicInfo = buildMusicInfo(source, song)
        val songId = extractSongId(musicInfo, source)
        if (songId.isBlank()) {
            return MusicUrlResult(error = "无法获取 songId（hash/songmid 均为空）")
        }

        // 4. 调用插件系统解析
        return withContext(Dispatchers.IO) {
            try {
                val url = withTimeoutOrNull(timeoutMs) {
                    js.getMusicUrl(source, musicInfo, actualQuality, timeoutMs)
                } ?: return@withContext MusicUrlResult(error = "插件解析超时(${timeoutMs}ms)")

                if (url.isBlank() || !url.startsWith("http")) {
                    return@withContext MusicUrlResult(error = "插件返回无效 URL: ${url.take(120)}")
                }

                Log.d(TAG, "getMusicUrl OK: source=$source, songId=$songId, quality=$actualQuality, url=${url.take(80)}")
                logCallback?.invoke("解析成功: source=$source, songId=$songId, url=${url.take(60)}")
                MusicUrlResult(url = url)
            } catch (e: Exception) {
                Log.w(TAG, "getMusicUrl failed: source=$source, songId=$songId, ${e.message}", e)
                logCallback?.invoke("解析失败: source=$source, songId=$songId, ${e.message}")
                MusicUrlResult(error = e.message ?: "插件解析失败")
            }
        }
    }

    /**
     * 获取歌词（通过 lxmusic.js 插件解析）
     *
     * 与 lx-music-mobile 的 sendUserApiRequest → lyric action 完全一致。
     * 注意：lxmusic.js 插件目前仅声明了 musicUrl action，lyric 可能返回 "action not support"。
     * 歌词获取应优先使用 [MusicApiService.fetchLyricsDirect]，本方法作为插件系统的完整入口保留。
     */
    suspend fun getLyric(
        context: android.content.Context,
        source: String,
        song: Song,
        timeoutMs: Long = 8000L,
    ): LyricResult {
        // 确保引擎已初始化
        ensureEngine(context)

        // 内置 lxmusic.js 已移除，engine 可能为 null
        val js = engine ?: return LyricResult(error = "无内置落雪音源，请导入插件后重试")
        val lower = source.lowercase()

        // 检查音源是否支持
        if (!isSourceSupported(lower)) {
            return LyricResult(error = "音源 $lower 暂不支持")
        }

        val musicInfo = buildMusicInfo(lower, song)
        val songId = extractSongId(musicInfo, lower)
        Log.d(TAG, "getLyric: source=$lower, songId=$songId")

        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    js.getLyric(lower, musicInfo, timeoutMs)
                } ?: return@withContext LyricResult(error = "插件解析超时(${timeoutMs}ms)")

                val lyric = result.optString("lyric", "")
                val tlyric = result.optString("tlyric", "")
                if (lyric.isNotBlank()) {
                    Log.d(TAG, "getLyric OK: source=$lower, songId=$songId, lyricLen=${lyric.length}")
                    LyricResult(lyric = lyric, tlyric = tlyric)
                } else {
                    Log.w(TAG, "getLyric empty: source=$lower, songId=$songId")
                    LyricResult(error = "插件未返回歌词")
                }
            } catch (e: Exception) {
                Log.w(TAG, "getLyric failed: source=$lower, songId=$songId, ${e.message}", e)
                LyricResult(error = e.message ?: "插件解析失败")
            }
        }
    }

    /**
     * 构建 musicInfo JSON（传递给 lxmusic.js 的 info.musicInfo）
     *
     * 与 [LuoxuePluginManager.buildMusicInfo] 思路一致：
     *  - 优先使用 song.pluginRawJson（搜索时已保存的原始 JSON）
     *  - 兜底用 platformId 构造最小 musicInfo
     *
     * 重要：JS 端 `musicInfo.hash ?? musicInfo.songmid` 中 `??` 仅对 null/undefined 触发兜底，
     * 因此对非 kg 音源不应写入空字符串 hash 字段，否则 JS 会把 songId 当成空串。
     */
    private fun buildMusicInfo(source: String, song: Song): JSONObject {
        val lower = source.lowercase()

        // 优先使用搜索时保存的 pluginRawJson
        val base = if (song.pluginRawJson.isNotBlank()) {
            try { JSONObject(song.pluginRawJson) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }

        // 确保基本字段存在
        if (!base.has("name") || base.optString("name").isBlank()) base.put("name", song.title)
        if (!base.has("singer") || base.optString("singer").isBlank()) base.put("singer", song.artist)
        if (!base.has("artists")) {
            base.put("artists", JSONArray().put(JSONObject().put("name", song.artist)))
        }
        if (!base.has("types")) {
            val typesArr = JSONArray()
            listOf("128k", "320k", "flac").forEach { typesArr.put(JSONObject().put("type", it)) }
            base.put("types", typesArr)
        }

        // 按音源补齐 ID 别名（仅当字段缺失或为空时填充，避免覆盖真实值）
        fun ensureField(key: String, value: String) {
            val existing = base.optString(key, "")
            if (existing.isBlank() || existing == "null") base.put(key, value)
        }
        val platformId = song.platformId
        when (lower) {
            "tx" -> {
                ensureField("songmid", platformId)
                ensureField("strMediaMid", platformId)
                ensureField("mid", platformId)
            }
            "kg" -> {
                ensureField("hash", platformId)
                ensureField("FileHash", platformId)
            }
            "kw" -> {
                ensureField("rid", platformId)
                ensureField("musicrid", "MUSIC_$platformId")
            }
            "wy" -> {
                ensureField("songId", platformId)
                ensureField("musicId", platformId)
            }
        }
        ensureField("musicId", platformId)
        ensureField("songId", platformId)
        ensureField("source", source)

        // 关键修复：JS 端 `musicInfo.hash ?? musicInfo.songmid` 的 `??` 仅对 null/undefined
        // 触发兜底，空字符串不会触发。因此对非 kg 音源必须移除空 hash 字段，
        // 否则 songId 会被错误解析为空字符串。
        if (lower != "kg") {
            val hashVal = base.optString("hash", "")
            if (hashVal.isBlank()) base.remove("hash")
        }
        // 同理：kg 音源的 songmid 若为空也移除，避免插件端误用
        if (lower == "kg") {
            val songmidVal = base.optString("songmid", "")
            if (songmidVal.isBlank()) base.remove("songmid")
        }

        return base
    }

    /**
     * 从 musicInfo 中提取 songId - 与 lxmusic.js `musicInfo.hash ?? musicInfo.songmid` 语义一致
     *
     * JS `??` 仅在 null/undefined 时触发兜底，空字符串不会触发；
     * 因此当 hash 字段存在但为空时，必须按 JS 语义返回空字符串（而非兜底到 songmid）。
     * 但本引擎在 [buildMusicInfo] 中已避免为非 kg 音源写入空 hash，所以这里直接用 Kotlin 的 `?:` 即可。
     */
    private fun extractSongId(musicInfo: JSONObject, source: String): String {
        val lower = source.lowercase()
        return if (lower == "kg") {
            // kg 音源优先用 hash
            val hash = musicInfo.optString("hash", "")
            if (hash.isNotBlank()) hash else musicInfo.optString("songmid", "")
        } else {
            // 其他音源用 songmid（不会写入空 hash）
            val songmid = musicInfo.optString("songmid", "")
            if (songmid.isNotBlank()) songmid else musicInfo.optString("hash", "")
        }
    }
}
