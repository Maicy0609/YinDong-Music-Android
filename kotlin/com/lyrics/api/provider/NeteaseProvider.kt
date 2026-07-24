package com.lyrics.api.provider

import com.lyrics.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class NeteaseProvider {

    private val userAgent = "Mozilla/5.0"

    private fun httpPost(url: String, params: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

        val body = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        conn.outputStream.write(body.toByteArray())
        return conn.inputStream.bufferedReader().readText()
    }

    suspend fun search(keyword: String, limit: Int = 5): List<SongInfo> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(
                httpPost(
                    "https://music.163.com/api/cloudsearch/pc",
                    mapOf("s" to keyword, "type" to "1", "limit" to limit.toString()),
                    mapOf("Referer" to "https://music.163.com/")
                )
            )
            val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return@withContext emptyList()

            val result = mutableListOf<SongInfo>()
            for (i in 0 until minOf(songs.length(), limit)) {
                val s = songs.getJSONObject(i)
                val songId = s.getInt("id")
                val name = s.optString("name", "")
                val artists = s.optJSONArray("ar")?.let { ar ->
                    (0 until ar.length()).mapNotNull { ar.optJSONObject(it)?.optString("name", "") }.joinToString("/")
                } ?: ""
                val duration = s.optInt("dt", 0)

                val (hasYrc, hasLrc) = checkYrc(songId)

                result.add(SongInfo(
                    id = "ne_$songId",
                    platform = "ne",
                    platformName = "网易云音乐",
                    name = name,
                    artist = artists,
                    duration = duration,
                    hasWordLyric = hasYrc,
                    rawId = songId
                ))
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun checkYrc(songId: Int): Pair<Boolean, Boolean> {
        return try {
            val json = JSONObject(
                httpPost(
                    "https://interface3.music.163.com/api/song/lyric",
                    mapOf(
                        "id" to songId.toString(),
                        "yv" to "1", "lv" to "0", "tv" to "0",
                        "rv" to "0", "kv" to "0", "cp" to "false",
                        "ytv" to "0", "yrv" to "0"
                    ),
                    mapOf("Referer" to "https://music.163.com/")
                )
            )
            val hasYrc = json.optJSONObject("yrc")?.optString("lyric", "")?.trim()?.isNotEmpty() == true
            val hasLrc = json.optJSONObject("lrc")?.optString("lyric", "")?.trim()?.isNotEmpty() == true
            hasYrc to hasLrc
        } catch (e: Exception) {
            false to false
        }
    }

    suspend fun getLyrics(song: SongInfo): LyricsData? = withContext(Dispatchers.IO) {
        try {
            val songId = song.rawId ?: return@withContext null
            val json = JSONObject(
                httpPost(
                    "https://interface3.music.163.com/api/song/lyric",
                    mapOf(
                        "id" to songId.toString(),
                        "yv" to "1", "lv" to "0", "tv" to "0",
                        "rv" to "0", "kv" to "0", "cp" to "false",
                        "ytv" to "0", "yrv" to "0"
                    ),
                    mapOf("Referer" to "https://music.163.com/")
                )
            )

            val yrc = json.optJSONObject("yrc")?.optString("lyric", "")?.trim() ?: ""
            val lrc = json.optJSONObject("lrc")?.optString("lyric", "")?.trim() ?: ""
            val text = yrc.ifEmpty { lrc }
            if (text.isEmpty()) return@withContext null

            val format = if (yrc.isNotEmpty()) "yrc" else "lrc"
            LyricsParser.parseLyrics(text, format)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
