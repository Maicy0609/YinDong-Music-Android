package com.yindong.music.data.lxsdk

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 内置落雪插件原生实现 - 完全复制 cjxz123/lxmusic.js 的 handleGetMusicUrl 逻辑
 *
 * lxmusic.js 核心逻辑：
 *   POST https://c.wwwweb.top/music/url
 *   Headers: Content-Type: application/json, User-Agent: lx-music-mobile/<ver>, X-Api-Key: <key>
 *   Body: { source, musicId, quality }
 *   musicId = musicInfo.hash ?? musicInfo.songmid
 *   Response: { code: 200, url: "..." }
 */
object LxSdkMusicUrl {
    private const val TAG = "LxSdkMusicUrl"

    // 与 lxmusic.js 常量完全一致
    private const val API_URL = "https://c.wwwweb.top"
    private const val API_KEY = "IKM-P83400001-i8if68xzJ80HdPIQ-i4"
    private const val PLUGIN_VERSION = "v26"
    private const val USER_AGENT = "lx-music-mobile/$PLUGIN_VERSION"

    // 与 lxmusic.js MUSIC_QUALITY 完全一致
    private val MUSIC_QUALITY = mapOf(
        "kg" to listOf("128k", "320k", "flac", "flac24bit", "hires", "atmos", "master"),
        "kw" to listOf("128k", "320k", "flac", "flac24bit", "hires"),
        "tx" to listOf("128k", "320k", "flac", "flac24bit", "hires", "atmos", "atmos_plus", "master"),
        "wy" to listOf("128k", "320k", "flac", "flac24bit", "hires", "atmos", "master"),
    )

    data class MusicUrlResult(
        val url: String = "",
        val headers: Map<String, String> = emptyMap(),
        val error: String = "",
    )

    /**
     * 获取播放 URL - 与 lxmusic.js handleGetMusicUrl 完全一致
     *
     * @param source 音源 id（wy/tx/kw/kg/mg）
     * @param songRawJson 歌曲原始 JSON（包含 songmid, hash 等）
     * @param quality 音质（128k/320k/flac/flac24bit/hires/...）
     * @return MusicUrlResult
     */
    suspend fun getMusicUrl(source: String, songRawJson: String, quality: String): MusicUrlResult {
        // 检查音源是否被支持（lxmusic.js 不支持 mg）
        val supportedQualities = MUSIC_QUALITY[source]
        if (supportedQualities == null) {
            return MusicUrlResult(error = "音源 $source 暂不支持")
        }

        // 与 lxmusic.js: const songId = musicInfo.hash ?? musicInfo.songmid;
        val musicInfo = try {
            JSONObject(songRawJson)
        } catch (e: Exception) {
            return MusicUrlResult(error = "musicInfo 解析失败: ${e.message}")
        }

        val hash = musicInfo.optString("hash").orEmpty()
        val songmid = musicInfo.optString("songmid").orEmpty()
        // JS: musicInfo.hash ?? musicInfo.songmid — hash 非空时用 hash，否则用 songmid
        val songId = hash.ifBlank { songmid }
        if (songId.isBlank()) {
            return MusicUrlResult(error = "无法获取 songId（hash 和 songmid 均为空）")
        }

        // 与 lxmusic.js 音质降级一致：如果请求的音质不在支持列表中，降级到 128k
        val actualQuality = if (supportedQualities.contains(quality)) quality else "128k"

        // 构建 request body —— 与 lxmusic.js handleGetMusicUrl body 完全一致
        val requestBody = JSONObject().apply {
            put("source", source)
            put("musicId", songId)
            put("quality", actualQuality)
        }

        // 与 lxmusic.js headers 完全一致
        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to USER_AGENT,
            "X-Api-Key" to API_KEY,
        )

        return withContext(Dispatchers.IO) {
            try {
                val resp = LxSdk.httpFetch(
                    url = "$API_URL/music/url",
                    method = "post",
                    headers = headers,
                    body = requestBody,
                    timeoutMs = 15000L,
                )
                // 与 lxmusic.js: const {body} = request; if (!body || isNaN(Number(body.code))) throw...
                val respBody = resp.body as? JSONObject
                    ?: JSONObject(resp.body as? String ?: "")
                val code = respBody.optInt("code", -1)
                // 与 lxmusic.js switch(body.code) 完全一致
                when (code) {
                    200 -> {
                        val url = respBody.optString("url").orEmpty()
                        if (url.isBlank()) {
                            MusicUrlResult(error = "返回成功但 URL 为空")
                        } else {
                            Log.d(TAG, "getMusicUrl OK: source=$source, songId=$songId, quality=$actualQuality, url=${url.take(80)}")
                            MusicUrlResult(url = url)
                        }
                    }
                    403 -> MusicUrlResult(error = "鉴权失败")
                    429 -> MusicUrlResult(error = "请求过速")
                    500 -> MusicUrlResult(error = "获取URL失败, ${respBody.optString("message").ifBlank { "未知错误" }}")
                    else -> MusicUrlResult(error = respBody.optString("message").ifBlank { "未知错误(code=$code)" })
                }
            } catch (e: Exception) {
                Log.e(TAG, "getMusicUrl failed: source=$source, songId=$songId, quality=$actualQuality", e)
                MusicUrlResult(error = "请求失败: ${e.message}")
            }
        }
    }

    /**
     * 检查音源是否被内置插件支持
     */
    fun isSourceSupported(source: String): Boolean = MUSIC_QUALITY.containsKey(source)
}
