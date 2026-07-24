package com.lyrics.api

data class SongInfo(
    val id: String,
    val platform: String,
    val platformName: String,
    val name: String,
    val artist: String,
    val album: String? = null,
    val duration: Int? = null,
    val hasWordLyric: Boolean = false,
    val rawHash: String? = null,
    val rawId: Int? = null,
    val rawMid: String? = null
)

data class LyricsLine(
    val time: Int,
    val text: String
)

data class LyricsWord(
    val startTime: Int,
    val duration: Int,
    val text: String
)

data class LyricsLineWithWords(
    val startTime: Int,
    val duration: Int,
    val text: String,
    val words: List<LyricsWord>
)

data class LyricsData(
    val format: String,
    val isWordByWord: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val lines: List<LyricsLine>,
    val linesWithWords: List<LyricsLineWithWords>? = null,
    val rawContent: String? = null
)

data class SearchResult(
    val success: Boolean,
    val keyword: String,
    val total: Int,
    val songs: List<SongInfo>,
    val elapsedTime: Float
)

data class LyricsResult(
    val success: Boolean,
    val song: SongInfo,
    val lyrics: LyricsData
)

data class SyncLyricsResult(
    val success: Boolean,
    val currentLine: LyricsLineWithWords?,
    val prevLines: List<LyricsLineWithWords>,
    val nextLines: List<LyricsLineWithWords>,
    val progressPercent: Float
)
