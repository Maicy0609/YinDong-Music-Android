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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yindong.music.data.model.Song
import com.yindong.music.ui.components.AddToPlaylistSheet
import com.yindong.music.ui.components.SongItem
import com.yindong.music.ui.theme.CoverGradients
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel

@Composable
fun ExternalPlaylistDetailScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onSaveToMine: () -> Unit,
    onPlayerClick: () -> Unit,
) {
    val playlist = viewModel.externalViewPlaylist
    val isLoading = viewModel.isExternalViewLoading
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accentColor = Color(0xFF007AFF)
    val accent = accentColor
    val surfaceColor = secondaryBackground
    val cardColor = tertiaryBackground
    val subtleBorder = separator
    val accentSoftBg = accent.copy(alpha = 0.08f)

    var songToAdd by remember { mutableStateOf<Song?>(null) }
    songToAdd?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = viewModel.userPlaylists,
            onSelect = { targetId -> viewModel.addSongToPlaylist(targetId, song) },
            onDismiss = { songToAdd = null },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(systemBackground),
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accent, strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("加载歌单中...", color = labelSecondary, fontSize = 13.sp)
                }
            }
        } else if (playlist == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote, null,
                        tint = accent.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("歌单加载失败", color = labelSecondary, fontSize = 14.sp)
                }
            }
        } else {
            val gradients = CoverGradients
            val coverColors = remember(playlist.name, gradients) {
                gradients[(playlist.name.hashCode() and 0x7FFFFFFF) % gradients.size]
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                // Header
                item {
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

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Brush.linearGradient(coverColors))
                                        .border(1.dp, subtleBorder, RoundedCornerShape(16.dp)),
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
                                        Icon(
                                            Icons.Default.MusicNote, null,
                                            tint = accent.copy(alpha = 0.5f),
                                            modifier = Modifier.size(48.dp),
                                        )
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        playlist.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = labelPrimary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "来源: ${playlist.creator}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = labelSecondary,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${playlist.songs.size} 首歌曲",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = labelSecondary,
                                    )
                                }
                            }

                            // Save button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accent)
                                        .clickable { onSaveToMine() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = "保存", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("保存到我的歌单", style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Play all
                item {
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
                                    .clickable {
                                        if (playlist.songs.isNotEmpty()) {
                                            viewModel.playAllFromList(playlist.songs)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.Default.PlayCircleFilled, contentDescription = "播放全部", tint = Color.White, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("播放全部", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(6.dp))
                                Text("(${playlist.songs.size})", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                        HorizontalDivider(color = subtleBorder, thickness = 0.5.dp)
                    }
                }

                // Songs
                itemsIndexed(playlist.songs, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song ->
                    SongItem(
                        song = song,
                        index = index,
                        onSongClick = {
                            viewModel.playPlaylist(playlist.songs, index)
                        },
                        onMoreClick = { songToAdd = song },
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
