package com.yindong.music.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.yindong.music.data.db.LocalMusicDao
import com.yindong.music.data.db.LocalSong

/**
 * 本地音乐扫描器 —— 基于 MediaStore 全量扫描 + 文件夹导入 + 增量更新。
 *
 * MediaStore 已索引设备上所有音频文件（含外部存储），无需手动遍历文件系统。
 * 对于指定文件夹导入，使用 MediaStore 的 IS_PENDING / RELATIVE_PATH 过滤。
 */
class MusicScanner(
    private val context: Context,
    private val dao: LocalMusicDao,
    private val metadataParser: MetadataParser,
) {
    companion object {
        private const val TAG = "MusicScanner"

        /** 支持的音频文件扩展名（MediaStore 已过滤大部分，这里用于文件夹导入时手动筛选） */
        val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "aac", "m4a", "ogg", "opus", "wma", "flac", "ape", "wav", "alac",
            "wv", "tta", "tak", "dff", "dsf", "pcm", "caf", "au", "ra", "midi",
            "mod", "xm", "it", "s3m", "m4b", "m4p", "amr", "mp2", "mp1", "voc",
            "raw", "sln", "wmalossless", "mp4a", "3ga", "oga", "ogv", "spx", "m4r",
            "adts", "ac3", "eac3", "dts", "dtshd", "mlp", "thd", "shn", "bwf",
            "rf64", "rf32", "iff", "aiff", "aif", "aifc", "snd", "nist", "ircam",
            "sf", "mat", "gsm", "qcp", "evrc", "smv", "aptx", "aptxhd", "ldac",
            "lhac", "ofr", "ofs", "la", "kxa", "mka", "mid", "rmi", "kar", "mtm",
            "ult", "dmf", "okt", "ptm", "stm", "mdl", "xpk", "ppm", "mmf", "imy",
            "dvf", "vsd", "msp", "nwa", "u8", "s8", "ulaw", "alaw", "f32", "f64",
            "pcm16", "pcm24", "pcm32", "bwav", "fla", "rtp", "auv", "wma10", "mks",
        )
    }

    /** 扫描进度回调 */
    data class ScanProgress(
        val scanned: Int,
        val total: Int,
        val currentFile: String,
    )

    /**
     * 全量扫描 —— 扫描设备上所有音频文件。
     * 增量机制：仅处理数据库中不存在或 dateModified 变化的文件。
     */
    suspend fun scanAll(
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult {
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.GENRE,
            ),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
        ) ?: return ScanResult(0, 0, 0)

        val results = mutableListOf<LocalSong>()
        cursor.use { c ->
            val total = c.count
            while (c.moveToNext()) {
                val filePath = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: continue
                val dateModified = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)) * 1000

                // 增量：跳过未修改的已存在文件
                val existing = dao.getByPath(filePath)
                if (existing != null && existing.dateModified == dateModified) {
                    results.add(existing)
                    continue
                }

                val song = LocalSong(
                    filePath = filePath,
                    fileName = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)) ?: "",
                    title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "",
                    artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "",
                    album = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "",
                    albumArtist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)),
                    duration = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                    fileSize = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
                    mimeType = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
                    track = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)).takeIf { it > 0 },
                    year = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)).takeIf { it > 0 },
                    genre = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)),
                    dateAdded = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) * 1000,
                    dateModified = dateModified,
                )

                // 用 jaudiotagger / MediaMetadataRetriever 补充元数据
                val enriched = metadataParser.enrichMetadata(song)
                results.add(enriched)

                onProgress(ScanProgress(results.size, total, song.fileName))
            }
        }

        // 批量写入数据库
        val newCount = dao.upsertAllAndGetNewCount(results)

        // 清理已删除的文件
        val keepPaths = results.map { it.filePath }
        val deletedCount = if (keepPaths.isNotEmpty()) dao.deleteNotIn(keepPaths) else 0

        return ScanResult(
            totalScanned = results.size,
            newAdded = newCount,
            deleted = deletedCount,
        )
    }

    /**
     * 指定文件夹导入 —— 扫描指定目录下的音频文件。
     */
    suspend fun scanFolder(
        folderPath: String,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult {
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
            ),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?",
            arrayOf("$folderPath%"),
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
        ) ?: return ScanResult(0, 0, 0)

        val results = mutableListOf<LocalSong>()
        cursor.use { c ->
            val total = c.count
            while (c.moveToNext()) {
                val filePath = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: continue
                val dateModified = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)) * 1000

                val existing = dao.getByPath(filePath)
                if (existing != null && existing.dateModified == dateModified) {
                    results.add(existing)
                    continue
                }

                val song = LocalSong(
                    filePath = filePath,
                    fileName = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)) ?: "",
                    title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "",
                    artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "",
                    album = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "",
                    albumArtist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)),
                    duration = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                    fileSize = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
                    mimeType = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
                    track = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)).takeIf { it > 0 },
                    year = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)).takeIf { it > 0 },
                    dateAdded = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) * 1000,
                    dateModified = dateModified,
                )

                val enriched = metadataParser.enrichMetadata(song)
                results.add(enriched)

                onProgress(ScanProgress(results.size, total, song.fileName))
            }
        }

        val newCount = dao.upsertAllAndGetNewCount(results)
        return ScanResult(results.size, newCount, 0)
    }

    data class ScanResult(
        val totalScanned: Int,
        val newAdded: Int,
        val deleted: Int,
    )
}

/** DAO 扩展：upsert 并返回新增数量 */
private suspend fun LocalMusicDao.upsertAllAndGetNewCount(songs: List<LocalSong>): Int {
    var newCount = 0
    for (song in songs) {
        val existing = getByPath(song.filePath)
        if (existing == null) {
            insert(song)
            newCount++
        } else if (existing.dateModified != song.dateModified) {
            update(song.copy(id = existing.id))
        }
    }
    return newCount
}
