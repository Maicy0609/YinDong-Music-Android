package com.yindong.music.data.lxsdk

import android.util.Log
import com.yindong.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * lx-music-mobile 搜索统一入口
 *
 * 提供 5 平台（wy/tx/kw/kg/mg）的内置搜索能力，
 * 完全替代通过 QuickJS 桥接的落雪插件搜索路径。
 *
 * 搜索算法与 lx-music-mobile-master 项目完全一致。
 */
object LxSdkSearchManager {

    private const val TAG = "LxSdkSearchManager"

    /** 支持的平台列表（与 musicSdk/index.js sources.sources 一致） */
    data class Platform(val id: String, val name: String)

    val PLATFORMS: List<Platform> = listOf(
        Platform("wy", "网易云"),
        Platform("tx", "QQ"),
        Platform("kw", "酷我"),
        Platform("kg", "酷狗"),
        Platform("mg", "咪咕"),
    )

    /** 把平台显示名映射到 source id（与 MusicViewModel.platformToLxSource 一致） */
    fun resolveSourceId(platformName: String): String {
        return when (platformName) {
            "QQ音乐", "QQ", "qq" -> "tx"
            "网易云", "网易云2", "网易云音乐", "netease" -> "wy"
            "酷我音乐", "酷我", "kuwo" -> "kw"
            "酷狗音乐", "酷狗", "kugou" -> "kg"
            "咪咕音乐", "咪咕", "migu" -> "mg"
            else -> platformName
        }
    }

    /** 反向映射：source id → 显示名 */
    fun sourceIdToName(sourceId: String): String {
        return when (sourceId) {
            "wy" -> "网易云"
            "tx" -> "QQ"
            "kw" -> "酷我"
            "kg" -> "酷狗"
            "mg" -> "咪咕"
            else -> sourceId
        }
    }

    /** 搜索结果 */
    data class SearchResult(val songs: List<Song>, val isEnd: Boolean, val total: Int)

    /**
     * 执行搜索
     * @param sourceId 平台 id：wy/tx/kw/kg/mg
     * @param keyword 关键词
     * @param page 页码（从 1 开始）
     * @param timeoutMs 超时（仅用于日志，实际超时由 OkHttp 控制）
     */
    suspend fun search(
        sourceId: String,
        keyword: String,
        page: Int = 1,
        timeoutMs: Long = 15000L,
    ): SearchResult = withContext(Dispatchers.IO) {
        val limit = when (sourceId) {
            "tx" -> 50  // 与 tx/musicSearch.js limit 一致
            "mg" -> 20  // 与 mg/musicSearch.js limit 一致
            "wy" -> 20  // 网易云API实际每页最多返回20条
            else -> 30  // kw/kg 默认 30
        }

        val result = try {
            when (sourceId) {
                "wy" -> LxSdkSearch.searchWy(keyword, page, limit)
                "tx" -> LxSdkSearch.searchTx(keyword, page, limit)
                "kw" -> LxSdkSearch.searchKw(keyword, page, limit)
                "kg" -> LxSdkSearch.searchKg(keyword, page, limit)
                "mg" -> LxSdkSearch.searchMg(keyword, page, limit)
                else -> {
                    Log.w(TAG, "unknown source: $sourceId")
                    return@withContext SearchResult(emptyList(), true, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "search[$sourceId] failed: ${e.message}", e)
            return@withContext SearchResult(emptyList(), true, 0)
        }

        Log.d(TAG, "search[$sourceId] '$keyword' page=$page → ${result.list.size}/${result.total}")

        val songs = result.list.mapIndexed { index, sdkSong ->
            sdkSongToSong(sdkSong, sourceId, page, index, limit)
        }
        // isEnd：0条结果→结束；已知总数→按allPage判断；未知总数→有结果就允许下一页
        val isEnd = when {
            songs.isEmpty() -> true
            result.total > 0 -> page >= result.allPage
            else -> false
        }
        SearchResult(songs, isEnd, result.total)
    }

    /**
     * 把 [LxSdkSearch.LxSdkSong] 转换为项目的 [Song] 模型
     * - platform：使用平台显示名（与 UI 显示一致）
     * - platformId：使用 songmid 作为唯一 ID
     * - lxSourceKey：写入 source id（如 "tx"），用于播放路由
     * - pluginRawJson：保存原始 JSON，方便后续 musicUrl 请求
     */
    private fun sdkSongToSong(
        sdkSong: LxSdkSearch.LxSdkSong,
        sourceId: String,
        page: Int,
        indexInPage: Int,
        limit: Int,
    ): Song {
        // 持久化必要的 ID 字段，便于播放 URL 请求时使用
        val rawJson = org.json.JSONObject().apply {
            put("songmid", sdkSong.songmid)
            // 仅写入非空 hash：JS 端 `musicInfo.hash ?? musicInfo.songmid` 的 `??` 仅对
            // null/undefined 触发兜底，空字符串会导致 songId 错误为空。
            sdkSong.ext["hash"]?.takeIf { it.isNotBlank() }?.let { put("hash", it) }
            sdkSong.ext["songId"]?.takeIf { it.isNotBlank() }?.let { put("songId", it) }
            sdkSong.ext["strMediaMid"]?.takeIf { it.isNotBlank() }?.let { put("strMediaMid", it) }
            sdkSong.ext["albumMid"]?.takeIf { it.isNotBlank() }?.let { put("albumMid", it) }
            sdkSong.ext["copyrightId"]?.takeIf { it.isNotBlank() }?.let { put("copyrightId", it) }
            put("name", sdkSong.name)
            put("singer", sdkSong.singer)
            put("source", sourceId)
            // 音质列表
            val types = org.json.JSONArray()
            sdkSong.types.forEach { t ->
                types.put(org.json.JSONObject().put("type", t.type))
            }
            put("types", types)
            // interval
            put("interval", sdkSong.interval)
        }.toString()

        // interval "mm:ss" → 毫秒
        val durationMs = parseIntervalToMs(sdkSong.interval)

        // 唯一 ID（保持 Song.id 是 Long，使用 songmid 的 hash 或时间戳）
        val songIdLong = sdkSong.songmid.hashCode().toLong() and 0xFFFFFFFFL

        return Song(
            id = songIdLong,
            title = sdkSong.name,
            artist = sdkSong.singer,
            album = sdkSong.albumName,
            duration = durationMs,
            coverUrl = sdkSong.img,
            platform = sourceIdToName(sourceId),
            platformId = sdkSong.songmid,
            lxSourceKey = sourceId,
            pluginRawJson = rawJson,
        )
    }

    /** "mm:ss" → 毫秒 */
    private fun parseIntervalToMs(interval: String): Long {
        if (interval.isBlank() || interval == "--/--") return 0L
        val parts = interval.split(":")
        var sec = 0L
        var unit = 1L
        for (i in parts.indices.reversed()) {
            sec += (parts[i].trim().toLongOrNull() ?: 0L) * unit
            unit *= 60
        }
        return sec * 1000L
    }
}
