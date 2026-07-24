package com.yindong.music.data.musicfree

import android.content.Context
import android.util.Log
import com.yindong.music.data.lx.QuickJSWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * MusicFree 插件管理器
 *
 * 完全参考 MusicFree-master/src/core/pluginManager 的实现：
 *  - [plugin.ts] Plugin 类：使用 Function() 构造沙箱，注入 require/module/exports/console/env/URL/process
 *  - [index.ts] PluginManager：installPluginFromLocalFile / uninstallPlugin / getSearchablePlugins
 *
 * 沙箱内可用包（参考 plugin.ts 的 packages）：
 *  - axios: 通过原生 HTTP 桥接实现（__mf_http_request）
 *  - cheerio: 基础桩（仅提供最常用 API，复杂 HTML 解析可能不完整）
 *  - crypto-js: 基础桩（MD5/SHA256/Base64 等常用方法通过原生实现）
 *  - dayjs/qs/he/big-integer: 基础桩
 *
 * 注意：本管理器独立于 LX 插件系统（LxPluginEngine/LxPluginManager），
 * 不会影响落雪插件系统的运行。
 */
class MusicFreePluginManager(
    private val logCallback: ((String) -> Unit)? = null,
) {
    companion object {
        private const val TAG = "MusicFreePluginMgr"
        private const val PLUGINS_DIR = "musicfree_plugins"
        private const val MAX_PLUGIN_SIZE = 2 * 1024 * 1024 // 2MB
        private const val PREFS_NAME = "musicfree_plugins_prefs"
        private const val KEY_PLUGIN_LIST = "plugin_list_json"
    }

    private val plugins = mutableListOf<MusicFreePluginEntry>()
    private val sandboxes = mutableMapOf<String, MusicFreeSandbox>()

    @Synchronized
    fun getAllPlugins(): List<MusicFreePluginEntry> = plugins.toList()

    @Synchronized
    fun getPlugin(id: String): MusicFreePluginEntry? = plugins.find { it.id == id }

    @Synchronized
    fun getEnabledPlugins(): List<MusicFreePluginEntry> = plugins.filter { it.enabled && it.mounted }

    /**
     * 启动时加载已持久化的插件
     */
    @Synchronized
    fun loadPersistedPlugins(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_PLUGIN_LIST, "") ?: ""
            if (json.isBlank()) {
                Log.d(TAG, "无已保存的 MusicFree 插件")
                return
            }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("path")
                val fileName = obj.optString("fileName")
                val enabled = obj.optBoolean("enabled", true)
                if (path.isBlank()) continue
                val file = File(path)
                if (!file.exists()) {
                    Log.w(TAG, "插件文件不存在，跳过: $path")
                    continue
                }
                val script = file.readText(Charsets.UTF_8)
                val hash = sha256(script.toByteArray())
                mountPluginInternal(context, hash, path, fileName, script, enabled)
            }
            Log.d(TAG, "已加载 ${plugins.size} 个 MusicFree 插件")
        } catch (e: Exception) {
            Log.e(TAG, "loadPersistedPlugins failed: ${e.message}", e)
        }
    }

    /**
     * 从 Uri 导入插件（用户通过文件选择器选择 .js 文件）
     *
     * 流程：
     * 1. 读取文件内容（最多 2MB）
     * 2. 计算 SHA-256 哈希作为唯一 id
     * 3. 校验是否为合法 MusicFree 插件（包含 module.exports 且有 platform 字段）
     * 4. 复制到内部存储 musicfree_plugins/ 目录
     * 5. 挂载到 QuickJS 沙箱
     * 6. 持久化插件列表
     */
    suspend fun importFromUri(context: Context, uri: android.net.Uri): Result<MusicFreePluginEntry> = withContext(Dispatchers.IO) {
        try {
            // 1. 读取文件内容
            val scriptBytes = readUriContent(context, uri)
                ?: return@withContext Result.failure(IllegalStateException("无法读取插件文件"))
            if (scriptBytes.size > MAX_PLUGIN_SIZE) {
                return@withContext Result.failure(IllegalArgumentException("插件大小不能超过 2MB"))
            }
            val script = String(scriptBytes, Charsets.UTF_8)
            if (script.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("插件内容为空"))
            }

            // 2. 计算哈希
            val hash = sha256(scriptBytes)

            // 3. 去重检查
            if (plugins.any { it.id == hash }) {
                return@withContext Result.failure(IllegalStateException("插件已安装"))
            }

            // 4. 复制到内部存储
            val pluginsDir = File(context.filesDir, PLUGINS_DIR).apply { if (!exists()) mkdirs() }
            val rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?') ?: ""
            val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val fileName = safeName.takeIf { it.endsWith(".js") && it.isNotBlank() }
                ?: "musicfree_${System.currentTimeMillis()}.js"
            val localFile = File(pluginsDir, fileName)
            localFile.writeText(script, Charsets.UTF_8)

            // 5. 挂载插件
            val entry = mountPluginInternal(context, hash, localFile.absolutePath, fileName, script, enabled = true)
                ?: return@withContext Result.failure(IllegalStateException("插件挂载失败，请检查插件代码是否合法"))

            // 6. 持久化
            persistPlugins(context)
            Log.d(TAG, "✅ MusicFree 插件导入成功: ${entry.info.platform} (id=${entry.id})")
            logCallback?.invoke("MusicFree 插件导入成功: ${entry.info.platform}")
            Result.success(entry)
        } catch (e: Exception) {
            Log.e(TAG, "importFromUri failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 从 URL 导入插件（在线导入）
     */
    suspend fun importFromUrl(context: Context, url: String): Result<MusicFreePluginEntry> = withContext(Dispatchers.IO) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext Result.failure(IllegalArgumentException("仅支持 HTTP/HTTPS URL"))
            }
            // 下载插件
            val scriptBytes = downloadFile(url, MAX_PLUGIN_SIZE)
                ?: return@withContext Result.failure(IllegalStateException("下载失败"))
            val script = String(scriptBytes, Charsets.UTF_8)
            val hash = sha256(scriptBytes)

            if (plugins.any { it.id == hash }) {
                return@withContext Result.failure(IllegalStateException("插件已安装"))
            }

            // 保存到内部存储
            val pluginsDir = File(context.filesDir, PLUGINS_DIR).apply { if (!exists()) mkdirs() }
            val rawName = url.substringAfterLast('/').substringBefore('?')
            val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val fileName = safeName.takeIf { it.endsWith(".js") && it.isNotBlank() }
                ?: "musicfree_${System.currentTimeMillis()}.js"
            val localFile = File(pluginsDir, fileName)
            localFile.writeText(script, Charsets.UTF_8)

            val entry = mountPluginInternal(context, hash, localFile.absolutePath, fileName, script, enabled = true)
                ?: return@withContext Result.failure(IllegalStateException("插件挂载失败"))

            persistPlugins(context)
            Log.d(TAG, "✅ MusicFree 插件在线导入成功: ${entry.info.platform}")
            Result.success(entry)
        } catch (e: Exception) {
            Log.e(TAG, "importFromUrl failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 卸载插件
     */
    @Synchronized
    fun uninstallPlugin(context: Context, pluginId: String) {
        val entry = plugins.find { it.id == pluginId } ?: return
        try {
            sandboxes[pluginId]?.close()
            sandboxes.remove(pluginId)
        } catch (_: Exception) {}
        try {
            File(entry.path).delete()
        } catch (_: Exception) {}
        plugins.removeIf { it.id == pluginId }
        persistPlugins(context)
        Log.d(TAG, "已卸载 MusicFree 插件: ${entry.info.platform}")
    }

    /**
     * 启用/禁用插件
     */
    @Synchronized
    fun setEnabled(context: Context, pluginId: String, enabled: Boolean) {
        val idx = plugins.indexOfFirst { it.id == pluginId }
        if (idx < 0) return
        plugins[idx] = plugins[idx].copy(enabled = enabled)
        persistPlugins(context)
    }

    /**
     * 搜索歌曲
     *
     * 对应 MusicFree plugin.methods.search(query, page, type)
     */
    suspend fun search(pluginId: String, query: String, page: Int = 1, type: String = "music"): MusicFreeSearchResult {
        val sandbox = sandboxes[pluginId] ?: return MusicFreeSearchResult()
        val plugin = plugins.find { it.id == pluginId } ?: return MusicFreeSearchResult()
        if (!plugin.enabled) return MusicFreeSearchResult()

        return withTimeoutOrNull(15_000L) {
            withContext(Dispatchers.IO) {
                try {
                    sandbox.search(query, page, type)
                } catch (e: Exception) {
                    Log.w(TAG, "search failed: ${e.message}")
                    MusicFreeSearchResult()
                }
            }
        } ?: run {
            Log.w(TAG, "search timeout: pluginId=$pluginId, query=$query")
            MusicFreeSearchResult()
        }
    }

    /**
     * 获取播放链接
     *
     * 对应 MusicFree plugin.methods.getMediaSource(musicItem, quality)
     * 音质回退：如果指定音质返回空 URL，自动回退到 standard
     *
     * @param musicItemJson 插件搜索时返回的原始 JSON（MusicFreeSearchItem.rawJson）
     * @param quality 音质 key（standard/high/super_lossless/lossless 等）
     */
    suspend fun getMediaSource(pluginId: String, musicItemJson: String, quality: String = "standard"): MusicFreeMediaSource {
        val sandbox = sandboxes[pluginId] ?: return MusicFreeMediaSource()
        val plugin = plugins.find { it.id == pluginId } ?: return MusicFreeMediaSource()
        if (!plugin.enabled) return MusicFreeMediaSource()

        return withTimeoutOrNull(15_000L) {
            withContext(Dispatchers.IO) {
                try {
                    val result = sandbox.getMediaSource(musicItemJson, quality)
                    // 音质回退：如果指定音质返回空 URL，尝试 standard
                    if (result.url.isBlank() && quality != "standard") {
                        Log.d(TAG, "音质 $quality 返回空 URL，回退到 standard")
                        sandbox.getMediaSource(musicItemJson, "standard")
                    } else {
                        result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "getMediaSource failed: ${e.message}")
                    // 异常时也尝试回退到 standard
                    if (quality != "standard") {
                        try { sandbox.getMediaSource(musicItemJson, "standard") } catch (_: Exception) { MusicFreeMediaSource() }
                    } else {
                        MusicFreeMediaSource()
                    }
                }
            }
        } ?: run {
            Log.w(TAG, "getMediaSource timeout: pluginId=$pluginId")
            MusicFreeMediaSource()
        }
    }

    /**
     * 获取歌词
     */
    suspend fun getLyric(pluginId: String, musicItemJson: String): String {
        val sandbox = sandboxes[pluginId] ?: return ""
        val plugin = plugins.find { it.id == pluginId } ?: return ""
        if (!plugin.enabled) return ""

        return withTimeoutOrNull(10_000L) {
            withContext(Dispatchers.IO) {
                try {
                    sandbox.getLyric(musicItemJson)
                } catch (e: Exception) {
                    Log.w(TAG, "getLyric failed: ${e.message}")
                    ""
                }
            }
        } ?: ""
    }

    // ═════════════════════════════════════════════════════════════════
    //  内部方法
    // ═════════════════════════════════════════════════════════════════

    private fun mountPluginInternal(
        context: Context,
        hash: String,
        path: String,
        fileName: String,
        script: String,
        enabled: Boolean,
    ): MusicFreePluginEntry? {
        return try {
            val sandbox = MusicFreeSandbox(context, script, hash, logCallback)
            val info = sandbox.getPluginInfo()
            if (info.platform.isBlank()) {
                sandbox.close()
                Log.w(TAG, "插件 platform 字段为空，无法挂载: $fileName")
                return null
            }
            val entry = MusicFreePluginEntry(
                id = hash,
                path = path,
                fileName = fileName,
                info = info,
                mounted = true,
                enabled = enabled,
            )
            plugins.add(entry)
            sandboxes[hash] = sandbox
            Log.d(TAG, "✅ 挂载成功: ${info.platform} v${info.version}")
            entry
        } catch (e: Exception) {
            Log.e(TAG, "挂载插件失败: $fileName, ${e.message}", e)
            // 挂载失败直接返回 null，调用方据此返回 failure
            // 不再创建 mounted=false 的占位条目，避免"导入成功但实际未挂载"的误导
            null
        }
    }

    private fun persistPlugins(context: Context) {
        try {
            val arr = JSONArray()
            plugins.forEach { p ->
                arr.put(JSONObject().apply {
                    put("path", p.path)
                    put("fileName", p.fileName)
                    put("enabled", p.enabled)
                })
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PLUGIN_LIST, arr.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "persistPlugins failed: ${e.message}", e)
        }
    }

    private fun readUriContent(context: Context, uri: android.net.Uri): ByteArray? {
        return try {
            val input: InputStream? = context.contentResolver.openInputStream(uri)
            input?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "readUriContent failed: ${e.message}", e)
            null
        }
    }

    private fun downloadFile(url: String, maxSize: Int): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n <= 0) break
                    total += n
                    if (total > maxSize) return null
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadFile failed: ${e.message}", e)
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
