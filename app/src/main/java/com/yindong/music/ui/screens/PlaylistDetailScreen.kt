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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yindong.music.data.model.Playlist
import com.yindong.music.data.model.Song
import com.yindong.music.ui.components.AddToPlaylistSheet
import com.yindong.music.ui.components.SongItem
import com.yindong.music.ui.theme.CoverGradients
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onPlayerClick: () -> Unit,
) {
    val playlist = viewModel.getUserPlaylistById(playlistId) ?: return
    val songs = playlist.songs
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
    val accentSoftBg = accent.copy(alpha = 0.08f)

    // 添加到歌单弹窗
    var songToAdd by remember { mutableStateOf<Song?>(null) }
    songToAdd?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = viewModel.userPlaylists,
            onSelect = { targetId -> viewModel.addSongToPlaylist(targetId, song) },
            onDismiss = { songToAdd = null },
        )
    }

    // 自动为没有封面的歌曲搜索匹配封面
    LaunchedEffect(playlistId) {
        viewModel.fetchMissingCovers(playlistId)
    }

    val gradients = CoverGradients
    val coverColors = remember(playlist.id, gradients) {
        val idx = ((playlist.id % gradients.size).toInt() + gradients.size) % gradients.size
        gradients[idx]
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(systemBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // Header
            item {
                PlaylistHeader(
                    playlist = playlist,
                    coverColors = coverColors,
                    onBack = onBack,
                    viewModel = viewModel,
                    onImport = { viewModel.exportPlaylistCopy(playlistId) },
                    accent = accent,
                    cardColor = cardColor,
                    surfaceColor = surfaceColor,
                    subtleBorder = subtleBorder,
                    accentSoftBg = accentSoftBg,
                    isDark = isDark,
                )
            }

            // Play all
            item {
                PlayAllButton(
                    songCount = songs.size,
                    isLoading = false,
                    onClick = {
                        if (songs.isNotEmpty()) {
                            viewModel.playAllFromList(songs)
                        }
                    },
                    accent = accent,
                    surfaceColor = surfaceColor,
                    subtleBorder = subtleBorder,
                    isDark = isDark,
                )
            }

            // Songs
            itemsIndexed(songs, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song ->
                SongItem(
                    song = song,
                    index = index,
                    onSongClick = {
                        viewModel.playPlaylist(songs, index)
                    },
                    onMoreClick = { songToAdd = song },
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    coverColors: List<Color>,
    onBack: () -> Unit,
    viewModel: MusicViewModel,
    onImport: () -> Unit,
    accent: Color,
    cardColor: Color,
    surfaceColor: Color,
    subtleBorder: Color,
    accentSoftBg: Color,
    isDark: Boolean,
) {
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.06f),
                        accent.copy(alpha = 0.02f),
                        Color.Transparent,
                    )
                )
            )
            .statusBarsPadding(),
    ) {
        Column {
            // Back button: circular white with shadow, accent tint icon
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp)
                    .size(40.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(cardColor)
                    .border(1.dp, subtleBorder, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(coverColors))
                        .border(1.dp, subtleBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (playlist.coverUrl.isNotEmpty()) {
                        AsyncImage(
                            model = playlist.coverUrl,
                            contentDescription = playlist.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        val songCovers = playlist.songs.filter { it.coverUrl.isNotEmpty() }.map { it.coverUrl }.distinct().take(4)
                        if (songCovers.size >= 4) {
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                    AsyncImage(model = songCovers[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                    AsyncImage(model = songCovers[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                }
                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                    AsyncImage(model = songCovers[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                    AsyncImage(model = songCovers[3], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                }
                            }
                        } else if (songCovers.isNotEmpty()) {
                            AsyncImage(
                                model = songCovers.first(),
                                contentDescription = playlist.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        fontSize = 28.sp,
                        color = labelPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentSoftBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(playlist.creator, style = MaterialTheme.typography.bodySmall, color = labelSecondary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (playlist.description.isNotEmpty()) {
                        Text(
                            playlist.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = labelTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        playlist.tags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentSoftBg)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, color = accent)
                            }
                        }
                    }
                }
            }

            // Action chips (functional)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                ActionChip(Icons.Default.Input, "导出", surfaceColor, subtleBorder) { onImport() }
                ActionChip(Icons.Default.Share, "分享", surfaceColor, subtleBorder) { viewModel.showToast("分享功能即将上线") }
                ActionChip(Icons.Default.Download, "下载", surfaceColor, subtleBorder) { viewModel.showToast("下载功能即将上线") }
                ActionChip(Icons.Default.CheckCircle, "已收藏", surfaceColor, subtleBorder) { viewModel.showToast("已收藏") }
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    surfaceColor: Color,
    subtleBorder: Color,
    onClick: () -> Unit,
) {
    val isDark = isDarkTheme()
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceColor)
            .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = label, tint = labelSecondary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = labelSecondary)
    }
}

@Composable
private fun PlayAllButton(
    songCount: Int,
    isLoading: Boolean,
    onClick: () -> Unit,
    accent: Color,
    surfaceColor: Color,
    subtleBorder: Color,
    isDark: Boolean,
) {
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .clickable { onClick() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.PlayCircleFilled, contentDescription = "播放全部", tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("播放全部", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(6.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("($songCount)", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(Icons.Default.Sort, contentDescription = "排序", tint = labelTertiary, modifier = Modifier.size(20.dp))
        }

        HorizontalDivider(color = subtleBorder, thickness = 0.5.dp)
    }
}
