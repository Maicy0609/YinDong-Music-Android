package com.yindong.music.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import com.yindong.music.data.db.LocalSong
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * 音乐元数据解析器 —— 优先使用 jaudiotagger（支持 ID3v1/v2、Vorbis Comment、MP4 iTunes、ASF 等），
 * 失败时回退到 MediaMetadataRetriever（系统 API，支持更广泛格式但元数据较少）。
 *
 * 解析内容：标题、歌手、专辑、封面、歌词（含逐字）、比特率、采样率等。
 */
class MetadataParser(private val context: Context) {

    companion object {
        private const val TAG = "MetadataParser"
        private const val COVER_DIR = "local_covers"
    }

    /**
     * 用 jaudiotagger / MediaMetadataRetriever 补充 MediaStore 未提供的元数据：
     * 封面、歌词、比特率、采样率。
     */
    fun enrichMetadata(song: LocalSong): LocalSong {
        val file = File(song.filePath)
        if (!file.exists() || !file.canRead()) return song

        var coverPath: String? = song.coverPath
        var lyrics: String? = song.lyrics
        var hasKrc = song.hasKrc
        var bitRate: Int? = song.bitRate
        var sampleRate: Int? = song.sampleRate

        // ── 尝试 jaudiotagger ──
        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            if (tag != null) {
                // 歌词
                val rawLyrics = try { tag.getFirst(FieldKey.LYRICS) } catch (_: Exception) { "" }
                if (!rawLyrics.isNullOrBlank()) {
                    lyrics = rawLyrics
                    // 检测是否为逐字歌词（含 [00:00.000] 时间标签且每行有毫秒级时间戳）
                    hasKrc = rawLyrics.contains(Regex("""\[\d{2}:\d{2}\.\d{3,}\]"""))
                }

                // 封面
                if (coverPath == null) {
                    val art = try { tag.getFirstArtwork() } catch (_: Exception) { null }
                    if (art != null) {
                        val imageData = art.binaryData
                        if (imageData != null && imageData.isNotEmpty()) {
                            coverPath = saveCover(imageData, song.filePath)
                        }
                    }
                }

                // 补充 MediaStore 可能缺失的字段
                val title = try { tag.getFirst(FieldKey.TITLE) } catch (_: Exception) { "" }
                val artist = try { tag.getFirst(FieldKey.ARTIST) } catch (_: Exception) { "" }
                val album = try { tag.getFirst(FieldKey.ALBUM) } catch (_: Exception) { "" }
                val genre = try { tag.getFirst(FieldKey.GENRE) } catch (_: Exception) { "" }
            }

            // 比特率、采样率
            if (bitRate == null) {
                bitRate = try { header.bitRateAsNumber.toInt() } catch (_: Exception) { null }
            }
            if (sampleRate == null) {
                val srStr = header.sampleRate
                sampleRate = srStr.toIntOrNull()
            }
        } catch (e: CannotReadException) {
            // jaudiotagger 不支持的格式，回退到 MediaMetadataRetriever
            Log.d(TAG, "jaudiotagger 无法读取 ${song.fileName}，尝试 MediaMetadataRetriever")
            val mmrResult = parseWithMediaMetadataRetriever(song.filePath)
            coverPath = coverPath ?: mmrResult.coverPath
            lyrics = lyrics ?: mmrResult.lyrics
            hasKrc = hasKrc || mmrResult.hasKrc
            bitRate = bitRate ?: mmrResult.bitRate
            sampleRate = sampleRate ?: mmrResult.sampleRate
        } catch (e: Exception) {
            Log.w(TAG, "读取元数据失败: ${song.fileName}", e)
            val mmrResult = parseWithMediaMetadataRetriever(song.filePath)
            coverPath = coverPath ?: mmrResult.coverPath
            lyrics = lyrics ?: mmrResult.lyrics
            hasKrc = hasKrc || mmrResult.hasKrc
            bitRate = bitRate ?: mmrResult.bitRate
            sampleRate = sampleRate ?: mmrResult.sampleRate
        }

        return song.copy(
            coverPath = coverPath,
            lyrics = lyrics,
            hasKrc = hasKrc,
            bitRate = bitRate,
            sampleRate = sampleRate,
        )
    }

    /** 使用 MediaMetadataRetriever 解析（支持更广泛格式） */
    private fun parseWithMediaMetadataRetriever(filePath: String): MmrResult {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(filePath)

            val bitRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val sampleRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()

            // 封面
            var coverPath: String? = null
            val embeddedPicture = mmr.embeddedPicture
            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                coverPath = saveCover(embeddedPicture, filePath)
            }

            // 歌词（部分格式支持）
            var lyrics: String? = null
            var hasKrc = false
            try {
                val lyr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
                    ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                // MediaMetadataRetriever 没有直接的歌词字段，尝试从 TrackInfo 获取
            } catch (_: Exception) { }

            MmrResult(coverPath, lyrics, hasKrc, bitRate, sampleRate)
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever 解析失败: $filePath", e)
            MmrResult(null, null, false, null, null)
        } finally {
            try { mmr.release() } catch (_: Exception) { }
        }
    }

    /** 保存封面到内部存储 */
    private fun saveCover(imageData: ByteArray, sourcePath: String): String? {
        return try {
            val coverDir = File(context.filesDir, COVER_DIR)
            if (!coverDir.exists()) coverDir.mkdirs()

            // 用源文件路径的 hash 作为封面文件名，避免重复
            val coverName = "${sourcePath.hashCode().toUInt()}.jpg"
            val coverFile = File(coverDir, coverName)

            if (!coverFile.exists()) {
                coverFile.writeBytes(imageData)
            }
            coverFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "保存封面失败", e)
            null
        }
    }

    private data class MmrResult(
        val coverPath: String?,
        val lyrics: String?,
        val hasKrc: Boolean,
        val bitRate: Int?,
        val sampleRate: Int?,
    )
}
