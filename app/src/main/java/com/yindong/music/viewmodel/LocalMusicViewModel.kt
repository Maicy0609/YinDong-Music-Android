package com.yindong.music.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yindong.music.data.db.AlbumGroup
import com.yindong.music.data.db.AppDatabase
import com.yindong.music.data.db.ArtistGroup
import com.yindong.music.data.db.LocalSong
import com.yindong.music.data.local.MetadataParser
import com.yindong.music.data.local.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalMusicViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).localMusicDao()
    private val metadataParser = MetadataParser(app)
    private val scanner = MusicScanner(app, dao, metadataParser)

    // ── 歌曲列表（Room Flow 自动更新）──
    val songs: StateFlow<List<LocalSong>> = dao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── 扫描状态 ──
    sealed class ScanState {
        data object Idle : ScanState()
        data class Scanning(val scanned: Int, val total: Int, val currentFile: String) : ScanState()
        data class Completed(val scanned: Int, val added: Int, val deleted: Int) : ScanState()
        data class Error(val message: String) : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // ── 歌手/专辑分组（按需加载）──
    private val _artists = MutableStateFlow<List<ArtistGroup>>(emptyList())
    val artists: StateFlow<List<ArtistGroup>> = _artists.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    val albums: StateFlow<List<AlbumGroup>> = _albums.asStateFlow()

    /** 全量扫描 */
    fun scanAll() {
        if (_scanState.value is ScanState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState.Scanning(0, 0, "")
            try {
                val result = scanner.scanAll { progress ->
                    _scanState.value = ScanState.Scanning(progress.scanned, progress.total, progress.currentFile)
                }
                _scanState.value = ScanState.Completed(result.totalScanned, result.newAdded, result.deleted)
                loadGroups()
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.message ?: "扫描失败")
            }
        }
    }

    /** 文件夹导入（从 SAF URI 转换为文件路径） */
    fun scanFolder(uri: Uri) {
        if (_scanState.value is ScanState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState.Scanning(0, 0, "")
            try {
                val folderPath = uriToFilePath(uri)
                if (folderPath != null) {
                    val result = scanner.scanFolder(folderPath) { progress ->
                        _scanState.value = ScanState.Scanning(progress.scanned, progress.total, progress.currentFile)
                    }
                    _scanState.value = ScanState.Completed(result.totalScanned, result.newAdded, result.deleted)
                    loadGroups()
                } else {
                    // 无法获取文件路径，尝试用 content URI 直接遍历
                    scanWithContentResolver(uri)
                }
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.message ?: "导入失败")
            }
        }
    }

    /** 通过 ContentResolver 遍历 SAF 目录树 */
    private suspend fun scanWithContentResolver(treeUri: Uri) {
        val contentResolver = getApplication<Application>().contentResolver
        val basePath = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, basePath)

        val results = mutableListOf<LocalSong>()
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )

        cursor?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val name = c.getString(1)
                val mime = c.getString(2)

                // 仅处理音频文件
                val ext = name.substringAfterLast('.', "").lowercase()
                if (mime?.startsWith("audio/") == true || ext in MusicScanner.SUPPORTED_EXTENSIONS) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    // 转换为 LocalSong（无法直接获取文件路径，用 URI 播放）
                    val song = LocalSong(
                        filePath = fileUri.toString(),
                        fileName = name,
                        title = name.substringBeforeLast('.'),
                        artist = "",
                        album = "",
                        dateAdded = System.currentTimeMillis(),
                        dateModified = System.currentTimeMillis(),
                    )
                    results.add(song)
                }
            }
        }

        dao.upsertAll(results)
        _scanState.value = ScanState.Completed(results.size, results.size, 0)
        loadGroups()
    }

    /** 加载歌手/专辑分组 */
    private suspend fun loadGroups() {
        withContext(Dispatchers.IO) {
            // 歌手分组
            val artistNames = dao.getArtists()
            _artists.value = artistNames.map { name ->
                val artistSongs = dao.getSongsByArtist(name)
                val albumCount = artistSongs.map { it.album }.distinct().size
                ArtistGroup(name, artistSongs.size, albumCount, artistSongs)
            }

            // 专辑分组
            val albumNames = dao.getAlbums()
            _albums.value = albumNames.map { name ->
                val albumSongs = dao.getSongsByAlbum(name)
                val artist = albumSongs.firstOrNull()?.albumArtist ?: albumSongs.firstOrNull()?.artist ?: "未知歌手"
                AlbumGroup(name, artist, albumSongs.size, albumSongs)
            }
        }
    }

    /** 刷新分组数据（UI 切换 tab 时调用） */
    fun refreshGroups() {
        if (_artists.value.isEmpty() || _albums.value.isEmpty()) {
            viewModelScope.launch { loadGroups() }
        }
    }

    /** 删除本地歌曲记录（仅移除库列表引用，不删除本地文件） */
    fun deleteSong(song: LocalSong) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(song)
            loadGroups()
        }
    }

    /** 批量删除本地歌曲记录（仅移除库列表引用，不删除本地文件） */
    fun deleteSongs(songs: List<LocalSong>) {
        if (songs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll(songs)
            loadGroups()
        }
    }

    /** 清空本地音乐库 */
    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
            _artists.value = emptyList()
            _albums.value = emptyList()
        }
    }

    /** 搜索本地歌曲 */
    fun search(query: String): List<LocalSong> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return songs.value
        return songs.value.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
        }
    }

    // ── URI 转文件路径 ──

    private fun uriToFilePath(uri: Uri): String? {
        return try {
            val contentResolver = getApplication<Application>().contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // 尝试获取 DATA 列
                    val dataIdx = it.getColumnIndex("_data")
                    if (dataIdx >= 0) {
                        return it.getString(dataIdx)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
