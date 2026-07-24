package com.yindong.music.data.lx

import android.content.Context
import android.util.Log
import com.yindong.music.data.model.Song
import kotlinx.coroutines.runBlocking
import java.io.InputStream

object BuiltinPluginManager {
    private const val TAG = "BuiltinPlugin"

    // ── 内置插件定义 ──
    data class BuiltinPluginDef(
        val assetName: String,       // assets 中的文件名
        val uri: String,             // builtin:// URI
        val defaultSource: String,   // 默认音源 key
    )

    // 旧的内置 QQ 插件
    private val legacyPlugin = BuiltinPluginDef("builtin_qq.js", "builtin://builtin_qq", "qq")

    // cjxz123 四个音源
    private val cjxz123Plugins = listOf(
        BuiltinPluginDef("builtin_wy.js", "builtin://cjxz123_wy", "wy"),
        BuiltinPluginDef("builtin_tx.js", "builtin://cjxz123_tx", "tx"),
        BuiltinPluginDef("builtin_kw.js", "builtin://cjxz123_kw", "kw"),
        BuiltinPluginDef("builtin_kg.js", "builtin://cjxz123_kg", "kg"),
    )

    // ── 旧接口兼容（单插件） ──
    private var _loadedScript: String? = null
    var isLoaded: Boolean = false; private set
    var pluginId: String = "builtin_qq"; private set
    var sources: List<String> = listOf("qq"); private set

    // ── 多插件支持 ──
    data class LoadedBuiltinPlugin(
        val id: String,
        val sources: List<String>,
        val def: BuiltinPluginDef,
    )
    private val _loadedPlugins = mutableListOf<LoadedBuiltinPlugin>()
    val loadedPlugins: List<LoadedBuiltinPlugin> get() = _loadedPlugins

    fun loadBuiltinScript(): String {
        if (_loadedScript != null) return _loadedScript!!
        return try {
            val stream: InputStream? = BuiltinPluginManager::class.java.classLoader?.getResourceAsStream("assets/builtin_qq.js")
            if (stream == null) return ""
            _loadedScript = stream.bufferedReader().use { it.readText() }
            _loadedScript ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /** 使用 Context.assets.open 读取资产文件（Android 标准方式） */
    private fun loadAssetScriptFromContext(context: Context, assetName: String): String {
        return try {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "读取内置插件失败: $assetName", e)
            ""
        }
    }

    suspend fun syncAndLoad(
        pluginManager: LxPluginManager,
        logCallback: ((String) -> Unit)? = null,
    ) {
        try {
            val script = loadBuiltinScript()
            if (script.isBlank()) return
            val opts = LuoxueRuntimeOptions(callTimeoutMs = 15000L, allowHttp = true)
            val result = kotlin.runCatching { pluginManager.loadPluginFromScript(script, "builtin://builtin_qq", opts).getOrThrow() }
            if (result.isSuccess) {
                val entry = result.getOrThrow()
                pluginId = entry.id
                sources = entry.sources.ifEmpty { listOf("qq") }
                isLoaded = true
                _loadedPlugins.add(LoadedBuiltinPlugin(entry.id, entry.sources.ifEmpty { listOf("qq") }, legacyPlugin))
            }
        } catch (_: Exception) {}
    }

    /**
     * 加载 cjxz123 四个内置音源（网易、QQ、酷我、酷狗）
     * 必须传入 Context 以使用 context.assets.open 读取资产文件
     */
    suspend fun loadCjxz123Plugins(
        context: Context,
        pluginManager: LxPluginManager,
        logCallback: ((String) -> Unit)? = null,
    ) {
        Log.d(TAG, "开始加载 cjxz123 内置音源...")
        val opts = LuoxueRuntimeOptions(callTimeoutMs = 15000L, allowHttp = true)
        for (def in cjxz123Plugins) {
            try {
                val script = loadAssetScriptFromContext(context, def.assetName)
                if (script.isBlank()) {
                    Log.w(TAG, "跳过空内置插件: ${def.assetName}")
                    continue
                }
                Log.d(TAG, "正在加载内置插件: ${def.assetName} (URI=${def.uri})")
                val result = kotlin.runCatching {
                    pluginManager.loadPluginFromScript(script, def.uri, opts, context).getOrThrow()
                }
                if (result.isSuccess) {
                    val entry = result.getOrThrow()
                    _loadedPlugins.add(LoadedBuiltinPlugin(entry.id, entry.sources.ifEmpty { listOf(def.defaultSource) }, def))
                    Log.d(TAG, "✅ 内置插件加载成功: ${entry.info.name} (sources=${entry.sources})")
                } else {
                    Log.w(TAG, "❌ 内置插件加载失败: ${def.assetName}, error=${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 内置插件异常: ${def.assetName}", e)
            }
        }
        Log.d(TAG, "cjxz123 内置插件加载完成: ${_loadedPlugins.size} 个")
    }

    fun musicUrl(pluginManager: LxPluginManager, song: Song, quality: String): MusicUrlResult {
        return try {
            val result = runBlocking { pluginManager.musicUrl(pluginId, sources.firstOrNull() ?: "qq", song, 15000L, quality) }
            MusicUrlResult(url = result.url, headers = result.headers)
        } catch (_: Exception) {
            MusicUrlResult(url = "")
        }
    }

    /** 判断某个 pluginId 是否属于内置插件 */
    fun isBuiltinPlugin(pluginId: String): Boolean {
        return _loadedPlugins.any { it.id == pluginId }
    }
}
