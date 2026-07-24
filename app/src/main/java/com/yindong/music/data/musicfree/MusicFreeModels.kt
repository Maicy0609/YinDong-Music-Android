package com.yindong.music.data.musicfree

/**
 * MusicFree 插件元数据
 *
 * 对应 MusicFree-master/src/core/pluginManager/plugin.ts 的 IPlugin.IPluginDefine
 * 仅保留本项目实际使用到的字段。
 */
data class MusicFreePluginInfo(
    /** 插件平台标识（唯一），等同于 MusicFree 的 instance.platform */
    val platform: String = "",
    /** 插件版本 */
    val version: String = "",
    /** 插件作者 */
    val author: String = "",
    /** 插件描述 */
    val description: String = "",
    /** 插件支持的搜索类型，例如 ["music", "album", "artist", "sheet"] */
    val supportedSearchType: List<String> = emptyList(),
    /** 用户变量定义（暂不使用，保留字段） */
    val userVariables: List<Map<String, String>> = emptyList(),
)

/**
 * MusicFree 插件条目（用于 UI 展示与状态管理）
 */
data class MusicFreePluginEntry(
    /** 插件唯一 id（使用 SHA-256 哈希） */
    val id: String,
    /** 插件文件路径（内部存储） */
    val path: String,
    /** 插件原始文件名 */
    val fileName: String,
    /** 插件元数据 */
    val info: MusicFreePluginInfo = MusicFreePluginInfo(),
    /** 是否已成功挂载到 QuickJS 沙箱 */
    val mounted: Boolean = false,
    /** 是否启用（用户可通过 UI 切换） */
    val enabled: Boolean = true,
    /** 加载错误信息（mounted=false 时有效） */
    val errorMsg: String = "",
)

/**
 * MusicFree 搜索结果项
 * 对应 MusicFree IMusic.IMusicItemBase
 */
data class MusicFreeSearchItem(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artwork: String = "",
    val duration: Long = 0L,
    /** 插件返回的原始 JSON（用于后续 getMediaSource 调用） */
    val rawJson: String = "",
)

/**
 * MusicFree 搜索结果
 */
data class MusicFreeSearchResult(
    val isEnd: Boolean = true,
    val data: List<MusicFreeSearchItem> = emptyList(),
)

/**
 * MusicFree 媒体源结果（播放链接）
 * 对应 MusicFree IPlugin.IMediaSourceResult
 */
data class MusicFreeMediaSource(
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
)
