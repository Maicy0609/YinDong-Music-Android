package com.lyrics.api.provider

import com.lyrics.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.Inflater

class KugouProvider {

    companion object {
        private const val SECRET = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        private val KRC_KEY = "@Gaw^2tGQ61-\u00CE\u00D2ni".toByteArray(Charsets.ISO_8859_1)
    }

    private val client = java.net.HttpURLConnection::class.java
    private val userAgent = "Mozilla/5.0"

    private fun sign(params: Map<String, String>): String {
        val body = params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
        val raw = "${SECRET}${body}${SECRET}"
        return md5(raw)
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", userAgent)
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn.inputStream.bufferedReader().readText()
    }

    suspend fun search(keyword: String, limit: Int = 5): List<SongInfo> = withContext(Dispatchers.IO) {
        try {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://mobileservice.kugou.com/api/v3/search/song?keyword=$encodedKeyword&page=1&pagesize=$limit&userid=-1&clientver=20000&platform=WebFilter"
            val json = JSONObject(httpGet(url))
            val info = json.optJSONObject("data")?.optJSONArray("info") ?: return@withContext emptyList()

            val songs = mutableListOf<SongInfo>()
            for (i in 0 until minOf(info.length(), limit)) {
                val s = info.getJSONObject(i)
                val hash = s.optString("hash", "")
                val albumAudioId = s.optString("album_audio_id", "0")
                val duration = s.optInt("duration", 0)
                val songName = s.optString("songname", "")
                val singerName = s.optString("singername", "")

                val hasWord = checkKrc(hash, albumAudioId, duration, songName, singerName)

                songs.add(SongInfo(
                    id = "kg_${hash}",
                    platform = "kg",
                    platformName = "酷狗音乐",
                    name = songName,
                    artist = singerName,
                    duration = duration * 1000,
                    hasWordLyric = hasWord,
                    rawHash = hash,
                    rawId = albumAudioId.toIntOrNull()
                ))
            }
            songs
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun checkKrc(hash: String, albumAudioId: String, duration: Int, songName: String, singerName: String): Boolean {
        return try {
            val params = mutableMapOf(
                "appid" to "3116",
                "clientver" to "11070",
                "album_audio_id" to albumAudioId,
                "duration" to (duration * 1000).toString(),
                "hash" to hash,
                "keyword" to "$singerName - $songName",
                "lrctxt" to "1",
                "man" to "no"
            )
            params["signature"] = sign(params)

            val query = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
            val url = "https://lyrics.kugou.com/v1/search?$query"
            val json = JSONObject(httpGet(url, mapOf("User-Agent" to "Android14-1070-11070-201-0-Lyric-wifi")))
            val cands = json.optJSONArray("candidates")
            if (cands != null && cands.length() > 0) {
                cands.getJSONObject(0).optInt("krctype", 0) == 1
            } else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getLyrics(song: SongInfo): LyricsData? = withContext(Dispatchers.IO) {
        try {
            val hash = song.rawHash ?: return@withContext null
            val albumAudioId = song.rawId?.toString() ?: "0"
            val duration = (song.duration ?: 0) / 1000

            val params = mutableMapOf(
                "appid" to "3116",
                "clientver" to "11070",
                "album_audio_id" to albumAudioId,
                "duration" to (duration * 1000).toString(),
                "hash" to hash,
                "keyword" to "${song.artist} - ${song.name}",
                "lrctxt" to "1",
                "man" to "no"
            )
            params["signature"] = sign(params)

            val query = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
            val searchUrl = "https://lyrics.kugou.com/v1/search?$query"
            val searchJson = JSONObject(httpGet(searchUrl, mapOf("User-Agent" to "Android14-1070-11070-201-0-Lyric-wifi")))
            val cands = searchJson.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) return@withContext null

            val c = cands.getJSONObject(0)
            val dlParams = mutableMapOf(
                "appid" to "3116",
                "clientver" to "11070",
                "accesskey" to c.getString("accesskey"),
                "charset" to "utf8",
                "client" to "mobi",
                "fmt" to "krc",
                "id" to c.getString("id"),
                "ver" to "1"
            )
            dlParams["signature"] = sign(dlParams)

            val dlQuery = dlParams.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
            val dlUrl = "http://lyrics.kugou.com/download?$dlQuery"
            val dlJson = JSONObject(httpGet(dlUrl, mapOf("User-Agent" to "Android14-1070-11070-201-0-Lyric-wifi")))
            val contentB64 = dlJson.optString("content", "")
            if (contentB64.isEmpty()) return@withContext null

            val contentType = dlJson.optInt("contenttype", 1)
            val contentBytes = java.util.Base64.getDecoder().decode(contentB64)

            if (contentType == 2) {
                val lyricText = String(contentBytes, Charsets.UTF_8)
                LyricsParser.parseLyrics(lyricText, "lrc")
            } else {
                val lyricText = decryptKrc(contentBytes)
                val krcTitle = Regex("""\[ti:([^\]]*)\]""").find(lyricText)?.groupValues?.get(1)?.trim() ?: ""
                val songTitle = song.name.trim()

                val wrongSong = krcTitle.isNotEmpty() &&
                    krcTitle.lowercase() !in songTitle.lowercase() &&
                    songTitle.lowercase() !in krcTitle.lowercase()

                if (wrongSong) {
                    val lrcParams = dlParams.toMutableMap()
                    lrcParams["fmt"] = "lrc"
                    lrcParams.remove("signature")
                    lrcParams["signature"] = sign(lrcParams.filterKeys { it != "signature" })

                    val lrcQuery = lrcParams.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
                    val lrcUrl = "http://lyrics.kugou.com/download?$lrcQuery"
                    val lrcJson = JSONObject(httpGet(lrcUrl, mapOf("User-Agent" to "Android14-1070-11070-201-0-Lyric-wifi")))
                    val lrcB64 = lrcJson.optString("content", "")
                    if (lrcB64.isEmpty()) return@withContext null
                    val lrcText = String(java.util.Base64.getDecoder().decode(lrcB64), Charsets.UTF_8)
                    LyricsParser.parseLyrics(lrcText, "lrc")
                } else {
                    LyricsParser.parseLyrics(lyricText, "krc")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decryptKrc(data: ByteArray): String {
        val enc = data.copyOfRange(4, data.size)
        val dec = ByteArray(enc.size)
        for (i in enc.indices) {
            dec[i] = (enc[i].toInt() xor KRC_KEY[i % KRC_KEY.size].toInt()).toByte()
        }
        val inflater = Inflater()
        inflater.setInput(dec)
        val output = ByteArray(enc.size * 10)
        val resultLen = inflater.inflate(output)
        inflater.end()
        return String(output, 0, resultLen, Charsets.UTF_8)
    }
}
