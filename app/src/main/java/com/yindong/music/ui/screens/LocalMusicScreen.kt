@file:OptIn(ExperimentalMaterial3Api::class)

package com.yindong.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.yindong.music.data.db.AlbumGroup
import com.yindong.music.data.db.ArtistGroup
import com.yindong.music.data.db.LocalSong
import com.yindong.music.data.model.Song
import com.yindong.music.viewmodel.LocalMusicViewModel
import com.yindong.music.viewmodel.MusicViewModel
import java.io.File

private enum class LocalMusicTab(val label: String) {
    SONGS("歌曲"), ARTISTS("歌手"), ALBUMS("专辑")
}

@Composable
fun LocalMusicScreen(
    musicViewModel: MusicViewModel,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: LocalMusicViewModel = viewModel()
    val songs by vm.songs.collectAsState()
    val scanState by vm.scanState.collectAsState()
    val artists by vm.artists.collectAsState()
    val albums by vm.albums.collectAsState()

    var selectedTab by remember { mutableStateOf(LocalMusicTab.SONGS) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    // 批量删除多选状态
    var inSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 文件夹选择器
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            vm.scanFolder(uri)
        }
    }

    // 搜索结果
    val displaySongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs else vm.search(searchQuery)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── 顶栏 ──
        if (inSelectionMode) {
            // 批量选择模式顶栏
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    inSelectionMode = false
                    selectedIds = emptySet()
                }) {
                    Icon(Icons.Default.Close, "退出选择", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    "已选 ${selectedIds.size} 首",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                // 全选
                val allIds = displaySongs.map { it.id }.toSet()
                val allSelected = selectedIds.containsAll(allIds) && allIds.isNotEmpty()
                TextButton(onClick = {
                    selectedIds = if (allSelected) emptySet() else allIds
                }) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
                // 批量删除
                TextButton(
                    onClick = {
                        val toDelete = songs.filter { it.id in selectedIds }
                        vm.deleteSongs(toDelete)
                        inSelectionMode = false
                        selectedIds = emptySet()
                    },
                    enabled = selectedIds.isNotEmpty(),
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除(${selectedIds.size})")
                }
            }
        } else {
            // 普通模式顶栏
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "本地音乐",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                // 搜索
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                    Icon(Icons.Default.Search, "搜索", tint = MaterialTheme.colorScheme.onBackground)
                }
                // 网络补全开关（图标按钮，点击切换开启/关闭）
                IconButton(onClick = { musicViewModel.toggleOnlineFallback() }) {
                    Icon(
                        Icons.Default.CloudDownload,
                        if (musicViewModel.onlineFallbackEnabled) "网络补全已开启" else "网络补全已关闭",
                        tint = if (musicViewModel.onlineFallbackEnabled)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    )
                }
                // 批量删除入口
                IconButton(
                    onClick = { inSelectionMode = true },
                    enabled = songs.isNotEmpty(),
                ) {
                    Icon(Icons.Default.Checklist, "批量删除", tint = MaterialTheme.colorScheme.onBackground)
                }
                // 扫描
                IconButton(onClick = { vm.scanAll() }, enabled = scanState !is LocalMusicViewModel.ScanState.Scanning) {
                    Icon(Icons.Default.Refresh, "扫描", tint = MaterialTheme.colorScheme.onBackground)
                }
                // 文件夹导入
                IconButton(onClick = { folderPicker.launch(null) }, enabled = scanState !is LocalMusicViewModel.ScanState.Scanning) {
                    Icon(Icons.Default.FolderOpen, "导入文件夹", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // 搜索框
        AnimatedVisibility(visible = showSearch && !inSelectionMode, enter = expandVertically(), exit = shrinkVertically()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("搜索本地歌曲、歌手、专辑") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "清除", modifier = Modifier.size(18.dp))
                        }
                    }
                },
            )
        }

        // ── 扫描进度 ──
        AnimatedVisibility(
            visible = scanState is LocalMusicViewModel.ScanState.Scanning,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            val state = scanState as? LocalMusicViewModel.ScanState.Scanning
            if (state != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "扫描中... ${state.scanned}/${state.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.currentFile,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (state.total > 0) state.scanned.toFloat() / state.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ── 扫描完成提示 ──
        AnimatedVisibility(
            visible = scanState is LocalMusicViewModel.ScanState.Completed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val state = scanState as? LocalMusicViewModel.ScanState.Completed
            if (state != null) {
                Text(
                    "扫描完成：共 ${state.scanned} 首，新增 ${state.added} 首" +
                        if (state.deleted > 0) "，移除 ${state.deleted} 首" else "",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ── Tab 栏 ──
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            LocalMusicTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        if (tab != LocalMusicTab.SONGS) vm.refreshGroups()
                    },
                    text = {
                        val count = when (tab) {
                            LocalMusicTab.SONGS -> songs.size
                            LocalMusicTab.ARTISTS -> artists.size
                            LocalMusicTab.ALBUMS -> albums.size
                        }
                        Text("${tab.label} ($count)")
                    },
                )
            }
        }

        // ── 内容区 ──
        if (songs.isEmpty() && scanState !is LocalMusicViewModel.ScanState.Scanning) {
            // 空状态
            EmptyState(
                onScanAll = { vm.scanAll() },
                onImportFolder = { folderPicker.launch(null) },
            )
        } else {
            when (selectedTab) {
                LocalMusicTab.SONGS -> SongList(
                    songs = displaySongs,
                    onPlay = { song ->
                        playLocalSong(song, musicViewModel, songs)
                    },
                    inSelectionMode = inSelectionMode,
                    selectedIds = selectedIds,
                    onToggleSelect = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onDelete = { song ->
                        vm.deleteSong(song)
                    },
                )
                LocalMusicTab.ARTISTS -> ArtistList(
                    artists = artists,
                    onArtistClick = { artist ->
                        // 展开歌手歌曲（简化：直接播放该歌手第一首）
                        val artistSongs = artist.songs
                        if (artistSongs.isNotEmpty()) {
                            playLocalSong(artistSongs.first(), musicViewModel, artistSongs)
                        }
                    },
                )
                LocalMusicTab.ALBUMS -> AlbumList(
                    albums = albums,
                    onAlbumClick = { album ->
                        val albumSongs = album.songs
                        if (albumSongs.isNotEmpty()) {
                            playLocalSong(albumSongs.first(), musicViewModel, albumSongs)
                        }
                    },
                )
            }
        }
    }
}

// ── 歌曲列表 ──
@Composable
private fun SongList(
    songs: List<LocalSong>,
    onPlay: (LocalSong) -> Unit,
    inSelectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onDelete: (LocalSong) -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                onPlay = { onPlay(song) },
                inSelectionMode = inSelectionMode,
                isSelected = song.id in selectedIds,
                onToggleSelect = { onToggleSelect(song.id) },
                onDelete = { onDelete(song) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun SongRow(
    song: LocalSong,
    onPlay: () -> Unit,
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                if (inSelectionMode) onToggleSelect() else onPlay()
            })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选择模式下显示复选框，否则显示封面
        if (inSelectionMode) {
            Icon(
                if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "已选" else "未选",
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            // 封面
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!song.coverPath.isNullOrEmpty() && File(song.coverPath).exists()) {
                    AsyncImage(
                        model = File(song.coverPath),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        // 标题 + 歌手
        Column(Modifier.weight(1f)) {
            Text(
                song.displayTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${song.displayArtist} · ${song.displayAlbum}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 时长
        if (song.duration > 0) {
            Text(
                formatDuration(song.duration),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            )
        }
        // 单首删除按钮（仅非选择模式显示）
        if (!inSelectionMode) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "从列表移除",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── 歌手列表 ──
@Composable
private fun ArtistList(
    artists: List<ArtistGroup>,
    onArtistClick: (ArtistGroup) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        artist.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${artist.songCount} 首歌曲 · ${artist.albumCount} 张专辑",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        }
    }
}

// ── 专辑列表 ──
@Composable
private fun AlbumList(
    albums: List<AlbumGroup>,
    onAlbumClick: (AlbumGroup) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(albums, key = { it.name }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAlbumClick(album) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        album.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${album.artist} · ${album.songCount} 首",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        }
    }
}

// ── 空状态 ──
@Composable
private fun EmptyState(
    onScanAll: () -> Unit,
    onImportFolder: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.LibraryMusic,
            null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "本地音乐库为空",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "扫描设备上的音乐文件或导入指定文件夹",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScanAll, modifier = Modifier.fillMaxWidth(0.6f)) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("全量扫描")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImportFolder, modifier = Modifier.fillMaxWidth(0.6f)) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("导入文件夹")
        }
    }
}

// ── 工具函数 ──

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/** 将 LocalSong 转换为应用 Song 模型并播放 */
private fun playLocalSong(
    localSong: LocalSong,
    musicViewModel: MusicViewModel,
    playlist: List<LocalSong>,
) {
    val song = Song(
        id = localSong.id,
        title = localSong.displayTitle,
        artist = localSong.displayArtist,
        album = localSong.displayAlbum,
        duration = localSong.duration,
        coverUrl = localSong.coverPath ?: "",
        platform = "本地",
        platformId = "local_${localSong.id}",
        directUrl = localSong.filePath,
        lrcText = localSong.lyrics ?: "",
    )
    // 播放整个列表
    val songs = playlist.mapIndexed { index, ls ->
        Song(
            id = ls.id,
            title = ls.displayTitle,
            artist = ls.displayArtist,
            album = ls.displayAlbum,
            duration = ls.duration,
            coverUrl = ls.coverPath ?: "",
            platform = "本地",
            platformId = "local_${ls.id}",
            directUrl = ls.filePath,
            lrcText = ls.lyrics ?: "",
        )
    }
    val playIndex = playlist.indexOfFirst { it.id == localSong.id }
    musicViewModel.playPlaylist(songs, playIndex.coerceAtLeast(0))
}
