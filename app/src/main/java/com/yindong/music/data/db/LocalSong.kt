package com.yindong.music.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地音乐实体 —— 存储扫描到的本地音频文件元数据。
 */
@Entity(
    tableName = "local_songs",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["artist"]),
        Index(value = ["album"]),
    ],
)
data class LocalSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,          // 文件绝对路径
    val fileName: String,          // 文件名
    val title: String,             // 歌曲标题
    val artist: String,            // 歌手（多位歌手用 / 分隔）
    val album: String,             // 专辑名
    val albumArtist: String? = null, // 专辑艺术家
    val duration: Long = 0,        // 时长(ms)
    val fileSize: Long = 0,        // 文件大小(bytes)
    val mimeType: String? = null,  // MIME 类型
    val coverPath: String? = null, // 封面缓存路径（嵌入式封面提取后保存）
    val lyrics: String? = null,    // 内嵌歌词（LRC 或纯文本）
    val hasKrc: Boolean = false,   // 是否有逐字歌词
    val genre: String? = null,     // 流派
        val year: Int? = null,         // 年份
    val track: Int? = null,        // 音轨号
    val bitRate: Int? = null,      // 比特率(kbps)
    val sampleRate: Int? = null,   // 采样率(Hz)
    val dateAdded: Long = 0,       // 添加时间(unix ms)
    val dateModified: Long = 0,    // 文件最后修改时间(unix ms)
) {
    /** 显示用标题：空则回退到文件名 */
    val displayTitle: String get() = title.ifBlank { fileName }

    /** 显示用歌手：空则显示"未知歌手" */
    val displayArtist: String get() = artist.ifBlank { "未知歌手" }

    /** 显示用专辑：空则显示"未知专辑" */
    val displayAlbum: String get() = album.ifBlank { "未知专辑" }
}

/**
 * 歌手分组（UI 用）
 */
data class ArtistGroup(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val songs: List<LocalSong>,
)

/**
 * 专辑分组（UI 用）
 */
data class AlbumGroup(
    val name: String,
    val artist: String,
    val songCount: Int,
    val songs: List<LocalSong>,
)
