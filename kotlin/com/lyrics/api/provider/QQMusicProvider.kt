package com.lyrics.api.provider

import com.lyrics.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class QQMusicProvider {

    private val userAgent = "Mozilla/5.0"

    private fun httpPostJson(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.write(jsonBody.toByteArray(Charsets.UTF_8))
        return conn.inputStream.bufferedReader().readText()
    }

    suspend fun search(keyword: String, limit: Int = 5): List<SongInfo> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("req_0", JSONObject().apply {
                    put("module", "music.search.SearchCgiService")
                    put("method", "DoSearchForQQMusicDesktop")
                    put("param", JSONObject().apply {
                        put("query", keyword)
                        put("num_per_page", limit)
                        put("page_num", 1)
                        put("search_type", 0)
                    })
                })
            }

            val json = JSONObject(
                httpPostJson(
                    "https://u.y.qq.com/cgi-bin/musicu.fcg",
                    body.toString(),
                    mapOf("Referer" to "https://y.qq.com")
                )
            )

            val songs = json.optJSONObject("req_0")
                ?.optJSONObject("data")
                ?.optJSONObject("body")
                ?.optJSONObject("song")
                ?.optJSONArray("list")
                ?: return@withContext emptyList()

            val result = mutableListOf<SongInfo>()
            for (i in 0 until minOf(songs.length(), limit)) {
                val s = songs.getJSONObject(i)
                val mid = s.optString("mid", "")
                val songId = s.optInt("id", 0)
                val title = s.optString("title", s.optString("songname", ""))
                val artists = s.optJSONArray("singer")?.let { ar ->
                    (0 until ar.length()).mapNotNull { ar.optJSONObject(it)?.optString("name", "") }.joinToString("/")
                } ?: ""
                val interval = s.optInt("interval", 0)

                val (hasQrc, hasLrc) = checkQrc(mid, songId)

                result.add(SongInfo(
                    id = "qq_$songId",
                    platform = "qq",
                    platformName = "QQ音乐",
                    name = title,
                    artist = artists,
                    duration = interval * 1000,
                    hasWordLyric = hasQrc,
                    rawId = songId,
                    rawMid = mid
                ))
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun checkQrc(mid: String, songId: Int): Pair<Boolean, Boolean> {
        return try {
            val body = JSONObject().apply {
                put("req_0", JSONObject().apply {
                    put("module", "music.musichallSong.PlayLyricInfo")
                    put("method", "GetPlayLyricInfo")
                    put("param", JSONObject().apply {
                        put("songMid", mid)
                        put("songID", songId)
                        put("musicID", songId)
                    })
                })
            }
            val json = JSONObject(
                httpPostJson(
                    "https://u.y.qq.com/cgi-bin/musicu.fcg",
                    body.toString(),
                    mapOf("Referer" to "https://y.qq.com")
                )
            )
            val data = json.optJSONObject("req_0")?.optJSONObject("data")
            val hasQrc = data?.optInt("qrc", 0) == 1
            val hasLrc = data?.optString("lyric", "")?.isNotEmpty() == true
            hasQrc to hasLrc
        } catch (e: Exception) {
            false to false
        }
    }

    suspend fun getLyrics(song: SongInfo): LyricsData? = withContext(Dispatchers.IO) {
        try {
            val mid = song.rawMid ?: return@withContext null
            val songId = song.rawId ?: return@withContext null

            val body = JSONObject().apply {
                put("req_0", JSONObject().apply {
                    put("module", "music.musichallSong.PlayLyricInfo")
                    put("method", "GetPlayLyricInfo")
                    put("param", JSONObject().apply {
                        put("songMid", mid)
                        put("songID", songId)
                        put("musicID", songId)
                    })
                })
            }

            val json = JSONObject(
                httpPostJson(
                    "https://u.y.qq.com/cgi-bin/musicu.fcg",
                    body.toString(),
                    mapOf("Referer" to "https://y.qq.com")
                )
            )

            val lyricB64 = json.optJSONObject("req_0")
                ?.optJSONObject("data")
                ?.optString("lyric", "")
                ?: return@withContext null

            if (lyricB64.isEmpty()) return@withContext null
            val text = String(java.util.Base64.getDecoder().decode(lyricB64), Charsets.UTF_8)
            LyricsParser.parseLyrics(text, "lrc")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
