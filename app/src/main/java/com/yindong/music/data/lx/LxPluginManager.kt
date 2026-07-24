package com.yindong.music.data.lx

import android.content.ContentResolver
import android.content.Context
import com.yindong.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LxPluginManager(
    private val contentResolver: ContentResolver,
    private val quickJsFactory: () -> QuickJSWrapper,
    private val logCallback: ((String) -> Unit)? = null,
) {
    private val inner = LuoxuePluginManager(contentResolver, quickJsFactory, logCallback)

    fun getPluginEntry(id: String): PluginEntry? {
        val inst = inner.getPlugin(id) ?: return null
        return PluginEntry(inst.id, inst.uri, inst.info, inst.sources, inst.format)
    }

    suspend fun musicUrl(pluginId: String, source: String, song: Song, timeoutMs: Long, quality: String = "320k"): LxMusicUrlResult {
        val plugin = inner.getPlugin(pluginId) ?: return LxMusicUrlResult()
        return withContext(Dispatchers.IO) {
            val result = plugin.musicUrl(source, song, quality, timeoutMs)
            LxMusicUrlResult(url = result.url, headers = result.headers)
        }
    }

    fun isPluginLoaded(id: String): Boolean = inner.isPluginLoaded(id)

    suspend fun lyric(pluginId: String, source: String, song: Song, timeoutMs: Long): LyricResult {
        val plugin = inner.getPlugin(pluginId) ?: return LyricResult()
        return withContext(Dispatchers.IO) { plugin.lyric(source, song, timeoutMs) }
    }

    fun sourceSupportsAction(pluginId: String, source: String, action: String): Boolean {
        return inner.sourceSupportsAction(pluginId, source, action)
    }

    suspend fun search(pluginId: String, source: String, keyword: String, timeoutMs: Long, page: Int = 1, type: String = "music"): LuoxuePluginManager.PluginSearchResult {
        val plugin = inner.getPlugin(pluginId) ?: return LuoxuePluginManager.PluginSearchResult(emptyList(), true)
        return withContext(Dispatchers.IO) { plugin.search(source, keyword, timeoutMs, page, type) }
    }

    suspend fun getAlbumInfo(pluginId: String, source: String, albumRawJson: String, timeoutMs: Long): LuoxuePluginManager.PluginSearchResult {
        val plugin = inner.getPlugin(pluginId) ?: return LuoxuePluginManager.PluginSearchResult(emptyList(), true)
        return withContext(Dispatchers.IO) { plugin.getAlbumInfo(source, albumRawJson, timeoutMs) }
    }

    suspend fun getArtistWorks(pluginId: String, source: String, artistRawJson: String, timeoutMs: Long, page: Int = 1): LuoxuePluginManager.PluginSearchResult {
        val plugin = inner.getPlugin(pluginId) ?: return LuoxuePluginManager.PluginSearchResult(emptyList(), true)
        return withContext(Dispatchers.IO) { plugin.getArtistWorks(source, artistRawJson, timeoutMs, page) }
    }

    suspend fun getMusicSheetInfo(pluginId: String, source: String, sheetRawJson: String, timeoutMs: Long, page: Int = 1): LuoxuePluginManager.PluginSearchResult {
        val plugin = inner.getPlugin(pluginId) ?: return LuoxuePluginManager.PluginSearchResult(emptyList(), true)
        return withContext(Dispatchers.IO) { plugin.getMusicSheetInfo(source, sheetRawJson, timeoutMs, page) }
    }

    suspend fun loadPluginFromScript(script: String, fileUri: String, options: LuoxueRuntimeOptions = LuoxueRuntimeOptions(), context: Context? = null): Result<PluginEntry> {
        return try {
            val inst = inner.loadPluginFromScript(script, fileUri, context)
            if (inst != null) {
                Result.success(PluginEntry(inst.id, inst.uri, inst.info, inst.sources, inst.format))
            } else {
                Result.failure(IllegalStateException("插件加载返回null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun removePlugin(pluginId: String) { inner.removePlugin(pluginId) }

    fun getAllPluginEntries(): List<PluginEntry> = inner.getAllPlugins().map { entry ->
        PluginEntry(entry.id, entry.uri, entry.info, entry.sources, entry.format)
    }

    suspend fun load(uri: String, options: LuoxueRuntimeOptions = LuoxueRuntimeOptions(), context: Context? = null): Result<PluginEntry> {
        return try {
            val inst = inner.loadPluginFromUri(uri, contentResolver, context ?: throw IllegalStateException("需要Context"))
            if (inst != null) {
                Result.success(PluginEntry(inst.id, inst.uri, inst.info, inst.sources, inst.format))
            } else {
                Result.failure(IllegalStateException("URI插件加载返回null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun pluginCount(): Int = inner.pluginCount()
}
