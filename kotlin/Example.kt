package com.lyrics.api

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val api = LyricsApi()

    println("=== 搜索歌曲 ===")
    val searchResult = api.search("稻香", platforms = listOf("kg", "ne"), limit = 3)
    println("搜索关键词: ${searchResult.keyword}")
    println("共找到 ${searchResult.total} 首，耗时 ${searchResult.elapsedTime}s\n")

    searchResult.songs.forEach { song ->
        println("  [${song.platformName}] ${song.name} - ${song.artist} (${song.hasWordLyric})")
    }

    println("\n=== 获取歌词 ===")
    val firstSong = searchResult.songs.first()
    val lyricsResult = api.getLyrics(firstSong)
    if (lyricsResult != null) {
        println("歌曲: ${lyricsResult.song.name} - ${lyricsResult.song.artist}")
        println("歌词格式: ${lyricsResult.lyrics.format}")
        println("是否逐字: ${lyricsResult.lyrics.isWordByWord}")
        println("\n歌词内容:")
        lyricsResult.lyrics.lines.forEach { line ->
            println("  [${line.time}ms] ${line.text}")
        }

        if (lyricsResult.lyrics.linesWithWords != null) {
            println("\n逐字歌词:")
            lyricsResult.lyrics.linesWithWords.take(5).forEach { line ->
                println("  [${line.startTime}ms] ${line.text}")
                line.words.forEach { word ->
                    println("    ${word.text} (${word.startTime}ms +${word.duration}ms)")
                }
            }
        }

        println("\n=== 同步歌词 ===")
        val syncResult = api.syncLyrics(lyricsResult.lyrics, currentTimeMs = 65000, contextLines = 2)
        println("当前进度: ${syncResult.progressPercent}%")
        println("当前行: ${syncResult.currentLine?.text}")
        println("前文: ${syncResult.prevLines.map { it.text }}")
        println("后文: ${syncResult.nextLines.map { it.text }}")
    }

    println("\n=== 直接获取歌词 ===")
    val directResult = api.getLyrics(platform = "kg", name = "晴天", artist = "周杰伦")
    if (directResult != null) {
        println("歌曲: ${directResult.song.name} - ${directResult.song.artist}")
        println("歌词格式: ${directResult.lyrics.format}")
        directResult.lyrics.lines.take(5).forEach { line ->
            println("  [${line.time}ms] ${line.text}")
        }
    }
}
