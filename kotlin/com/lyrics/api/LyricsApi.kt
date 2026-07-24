package com.lyrics.api

import com.lyrics.api.provider.KugouProvider
import com.lyrics.api.provider.NeteaseProvider
import com.lyrics.api.provider.QQMusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class LyricsApi {

    private val kugou = KugouProvider()
    private val netease = NeteaseProvider()
    private val qqMusic = QQMusicProvider()

    suspend fun search(
        keyword: String,
        platforms: List<String> = listOf("kg", "ne", "qq"),
        limit: Int = 5
    ): SearchResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val deferreds = platforms.map { platform ->
            async {
                when (platform) {
                    "kg" -> kugou.search(keyword, limit)
                    "ne" -> netease.search(keyword, limit)
                    "qq" -> qqMusic.search(keyword, limit)
                    else -> emptyList()
                }
            }
        }

        val results = deferreds.awaitAll()
        val allSongs = results.flatten()

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f

        SearchResult(
            success = true,
            keyword = keyword,
            total = allSongs.size,
            songs = allSongs,
            elapsedTime = elapsed
        )
    }

    suspend fun getLyrics(
        platform: String,
        name: String,
        artist: String
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val searchResult = when (platform) {
            "kg" -> kugou.search("$name $artist", 5)
            "ne" -> netease.search("$name $artist", 5)
            "qq" -> qqMusic.search("$name $artist", 5)
            else -> return@withContext null
        }

        val targetSong = searchResult.firstOrNull { song ->
            (name.lowercase() in song.name.lowercase() || song.name.lowercase() in name.lowercase()) &&
            (artist.lowercase() in song.artist.lowercase() || song.artist.lowercase() in artist.lowercase())
        } ?: searchResult.firstOrNull() ?: return@withContext null

        val lyricsData = when (platform) {
            "kg" -> kugou.getLyrics(targetSong)
            "ne" -> netease.getLyrics(targetSong)
            "qq" -> qqMusic.getLyrics(targetSong)
            else -> null
        } ?: return@withContext null

        LyricsResult(
            success = true,
            song = targetSong,
            lyrics = lyricsData
        )
    }

    suspend fun getLyrics(song: SongInfo): LyricsResult? = withContext(Dispatchers.IO) {
        val lyricsData = when (song.platform) {
            "kg" -> kugou.getLyrics(song)
            "ne" -> netease.getLyrics(song)
            "qq" -> qqMusic.getLyrics(song)
            else -> null
        } ?: return@withContext null

        LyricsResult(
            success = true,
            song = song,
            lyrics = lyricsData
        )
    }

    fun syncLyrics(
        lyricsData: LyricsData,
        currentTimeMs: Int,
        contextLines: Int = 2
    ): SyncLyricsResult {
        val lines = lyricsData.linesWithWords ?: lyricsData.lines.map { line ->
            LyricsLineWithWords(
                startTime = line.time,
                duration = 0,
                text = line.text,
                words = emptyList()
            )
        }

        if (lines.isEmpty()) {
            return SyncLyricsResult(
                success = true,
                currentLine = null,
                prevLines = emptyList(),
                nextLines = emptyList(),
                progressPercent = 0f
            )
        }

        var currentIdx = 0
        for (i in lines.indices) {
            if (lines[i].startTime <= currentTimeMs) {
                currentIdx = i
            } else {
                break
            }
        }

        val totalDuration = lines.last().startTime + lines.last().duration
        val progressPercent = if (totalDuration > 0) {
            minOf(100f, (currentTimeMs.toFloat() / totalDuration) * 100f)
        } else 0f

        val prevStart = maxOf(0, currentIdx - contextLines)
        val nextEnd = minOf(lines.size, currentIdx + contextLines + 1)

        return SyncLyricsResult(
            success = true,
            currentLine = lines.getOrNull(currentIdx),
            prevLines = lines.subList(prevStart, currentIdx),
            nextLines = lines.subList(currentIdx + 1, nextEnd),
            progressPercent = progressPercent
        )
    }
}
