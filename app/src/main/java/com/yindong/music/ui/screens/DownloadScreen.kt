package com.yindong.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel

// Platform color mapping
private val platformColors = mapOf(
    "QQ音乐" to listOf(Color(0xFF282828), Color(0xFF181818)),
    "网易云" to listOf(Color(0xFF1DB954), Color(0xFF1AA34A)),
    "网易云音乐" to listOf(Color(0xFF1DB954), Color(0xFF1AA34A)),
    "酷我音乐" to listOf(Color(0xFF282828), Color(0xFF181818)),
    "酷狗音乐" to listOf(Color(0xFF282828), Color(0xFF181818)),
    "咪咕音乐" to listOf(Color(0xFF1ED760), Color(0xFF1DB954)),
    "抖音" to listOf(Color(0xFF121212), Color(0xFF282828)),
)

private val defaultColors = listOf(Color(0xFF282828), Color(0xFF181818))

@Composable
fun DownloadScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accentColor = Color(0xFF007AFF)
    val accent = accentColor
    val surfaceColor = secondaryBackground
    val cardColor = tertiaryBackground
    val subtleBorder = separator

    val grouped = viewModel.getDownloadsByPlatform()
    var deleteConfirm = remember { mutableStateOf<MusicViewModel.DownloadedSong?>(null) }
    var showPathDialog = remember { mutableStateOf(false) }
    var editingPath = remember { mutableStateOf(viewModel.downloadDir) }

    // 修改下载路径弹窗
    if (showPathDialog.value) {
        AlertDialog(
            onDismissRequest = { showPathDialog.value = false },
            title = { Text("修改下载路径") },
            text = {
                Column {
                    Text(
                        "输入新的下载保存路径，清空则恢复默认",
                        style = MaterialTheme.typography.bodySmall,
                        color = labelSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingPath.value,
                        onValueChange = { editingPath.value = it },
                        label = { Text("下载路径") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDownloadDir(editingPath.value)
                    showPathDialog.value = false
                }) { Text("保存", color = accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingPath.value = ""
                    viewModel.setDownloadDir("")
                    showPathDialog.value = false
                }) { Text("恢复默认") }
            },
        )
    }

    // Delete confirmation dialog
    deleteConfirm.value?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteConfirm.value = null },
            title = { Text("删除下载") },
            text = { Text("确定删除「${entry.song.title}」？文件将被永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDownloadedSong(entry)
                    deleteConfirm.value = null
                }) { Text("删除", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm.value = null }) { Text("取消") }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(systemBackground)
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(cardColor)
                    .border(1.dp, subtleBorder, CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = labelPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "下载管理",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                    color = labelPrimary,
                )
                Text(
                    "${viewModel.downloadedSongs.size} 首 · ${viewModel.getDownloadTotalSize()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = labelSecondary,
                )
            }
        }

        // 下载路径显示
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cardColor)
                .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
                .clickable {
                    editingPath.value = viewModel.downloadDir
                    showPathDialog.value = true
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Folder, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "保存路径",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelSecondary,
                )
                Text(
                    viewModel.downloadDir,
                    style = MaterialTheme.typography.bodySmall,
                    color = labelPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Edit, null, tint = accent, modifier = Modifier.size(18.dp))
        }

        if (viewModel.downloadedSongs.isEmpty()) {
            // Empty state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        tint = labelTertiary,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂无下载",
                        style = MaterialTheme.typography.bodyMedium,
                        color = labelSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "在播放器中点击下载按钮，仅插件音乐可下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = labelTertiary,
                    )
                }
            }
        } else {
            // Play all button
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .clickable {
                        val allSongs = viewModel.downloadedSongs
                            .sortedBy { it.downloadTime }
                            .map {
                                it.song.copy(directUrl = it.filePath)
                            }
                        if (allSongs.isNotEmpty()) viewModel.playPlaylist(allSongs, 0)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("全部播放", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(8.dp))

            // Grouped by platform
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                grouped.forEach { (platform, songs) ->
                    item(key = "header_$platform") {
                        PlatformHeader(
                            platform = platform,
                            count = songs.size,
                        )
                    }
                    items(songs, key = { "${it.filePath}_${it.downloadTime}" }) { entry ->
                        DownloadedSongRow(
                            entry = entry,
                            onPlay = { viewModel.playDownloadedSong(entry) },
                            onDelete = { deleteConfirm.value = entry },
                        )
                    }
                    item(key = "spacer_$platform") {
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun PlatformHeader(platform: String, count: Int) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val colors = platformColors[platform] ?: defaultColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.linearGradient(colors))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            platform,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            color = labelPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count 首",
            style = MaterialTheme.typography.bodySmall,
            color = labelSecondary,
        )
    }
}

@Composable
private fun DownloadedSongRow(
    entry: MusicViewModel.DownloadedSong,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val subtleBorder = separator
    val fileSizeText = remember(entry.fileSize) {
        when {
            entry.fileSize >= 1024 * 1024 -> String.format("%.1f MB", entry.fileSize / (1024.0 * 1024.0))
            entry.fileSize >= 1024 -> String.format("%.0f KB", entry.fileSize / 1024.0)
            else -> "${entry.fileSize} B"
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tertiaryBackground)
                .border(1.dp, subtleBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.song.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = entry.song.coverUrl,
                    contentDescription = entry.song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = labelTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Song info
        Column(Modifier.weight(1f)) {
            Text(
                entry.song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.song.artist} · $fileSizeText",
                style = MaterialTheme.typography.bodySmall,
                color = labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Delete
        IconButton(onClick = onDelete, Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                "删除",
                tint = labelTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
