package com.yindong.music.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMusicDao {

    // ── 查询 ──

    @Query("SELECT * FROM local_songs ORDER BY title COLLATE NOCASE")
    fun getAllSongs(): Flow<List<LocalSong>>

    @Query("SELECT * FROM local_songs ORDER BY title COLLATE NOCASE")
    suspend fun getAllSongsOnce(): List<LocalSong>

    @Query("SELECT * FROM local_songs WHERE id = :id")
    suspend fun getById(id: Long): LocalSong?

    @Query("SELECT * FROM local_songs WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): LocalSong?

    @Query("SELECT COUNT(*) FROM local_songs")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM local_songs WHERE filePath = :path")
    suspend fun countByPath(path: String): Int

    // ── 插入/更新 ──

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: LocalSong): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<LocalSong>): List<Long>

    @Update
    suspend fun update(song: LocalSong)

    /** 批量 upsert：已存在的跳过，新的插入 */
    suspend fun upsertAll(songs: List<LocalSong>) {
        for (song in songs) {
            val existing = getByPath(song.filePath)
            if (existing == null) {
                insert(song)
            } else if (existing.dateModified != song.dateModified) {
                update(song.copy(id = existing.id))
            }
        }
    }

    // ── 删除 ──

    @Delete
    suspend fun delete(song: LocalSong)

    @Delete
    suspend fun deleteAll(songs: List<LocalSong>)

    @Query("DELETE FROM local_songs WHERE filePath NOT IN (:keepPaths)")
    suspend fun deleteNotIn(keepPaths: List<String>): Int

    @Query("DELETE FROM local_songs WHERE filePath = :path")
    suspend fun deleteByPath(path: String): Int

    @Query("DELETE FROM local_songs")
    suspend fun deleteAll(): Int

    // ── 歌手/专辑分组查询 ──

    @Query("SELECT DISTINCT artist FROM local_songs WHERE artist != '' ORDER BY artist COLLATE NOCASE")
    suspend fun getArtists(): List<String>

    @Query("SELECT DISTINCT album FROM local_songs WHERE album != '' ORDER BY album COLLATE NOCASE")
    suspend fun getAlbums(): List<String>

    @Query("SELECT * FROM local_songs WHERE artist = :artist ORDER BY title COLLATE NOCASE")
    suspend fun getSongsByArtist(artist: String): List<LocalSong>

    @Query("SELECT * FROM local_songs WHERE album = :album ORDER BY track")
    suspend fun getSongsByAlbum(album: String): List<LocalSong>

    @Query("SELECT * FROM local_songs WHERE filePath IN (:paths)")
    suspend fun getByPaths(paths: List<String>): List<LocalSong>
}
