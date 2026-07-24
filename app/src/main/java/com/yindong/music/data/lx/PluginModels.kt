package com.yindong.music.data.lx

data class PluginInfo(
    val name: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val homepage: String = "",
)

data class MusicUrlResult(
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val error: String = "",
)

data class LyricResult(
    val lyric: String = "",
    val tlyric: String = "",
    val error: String = "",
)

enum class PluginFormat { LX, MUSIC_FREE, UNKNOWN }

data class PluginEntry(
    val id: String,
    val uri: String,
    val info: PluginInfo = PluginInfo(),
    val sources: List<String> = emptyList(),
    val format: PluginFormat = PluginFormat.LX,
    val initialized: Boolean = true,
)

data class LxMusicUrlResult(
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
)

data class LuoxueRuntimeOptions(
    val callTimeoutMs: Long = 15000L,
    val allowHttp: Boolean = true,
)
