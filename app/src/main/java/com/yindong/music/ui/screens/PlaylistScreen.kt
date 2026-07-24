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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yindong.music.data.api.RecommendPlaylist
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel

// 平台颜色映射
private val platformColorMap = mapOf(
    "QQ音乐" to Color(0xFF282828),
    "网易云" to Color(0xFF1DB954),
    "网易云音乐" to Color(0xFF1DB954),
    "酷我音乐" to Color(0xFF282828),
    "酷狗音乐" to Color(0xFF282828),
    "咪咕音乐" to Color(0xFF1ED760),
)

private val defaultPlatformColor = Color(0xFF282828)

// 平台简称
private fun platformShort(name: String): String = when (name) {
    "QQ音乐" -> "QQ"
    "网易云", "网易云音乐" -> "网易"
    "酷我音乐" -> "酷我"
    "酷狗音乐" -> "酷狗"
    "咪咕音乐" -> "咪咕"
    else -> name
}

// Apple 设计令牌
private val AppleSystemBlue = Color(0xFF007AFF)

@Composable
fun PlaylistScreen(
    viewModel: MusicViewModel,
    onPlaylistClick: (String, String) -> Unit,
) {
    // 启动时加载歌单数据
    LaunchedEffect(Unit) {
        if (viewModel.recommendPlaylists.isEmpty()) {
            viewModel.loadRecommendPlaylists()
        }
    }

    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accentColor = AppleSystemBlue

    val allPlaylists = viewModel.recommendPlaylists
    val isLoading = viewModel.isRecommendLoading
    val hasError = !isLoading && allPlaylists.isEmpty() && viewModel.recommendLoadError.isNotEmpty()

    // 平台筛选
    var selectedTab by remember { mutableStateOf("全部") }
    val platforms = remember(allPlaylists) {
        listOf("全部") + allPlaylists.map { it.platform }.distinct()
    }
    val filteredPlaylists = remember(allPlaylists, selectedTab) {
        if (selectedTab == "全部") allPlaylists else allPlaylists.filter { it.platform == selectedTab }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(systemBackground)
            .statusBarsPadding(),
    ) {
        // ── Apple 风格大标题 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "音乐库",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = labelPrimary,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.weight(1f))
                // 刷新按钮
                IconButton(
                    onClick = { viewModel.loadRecommendPlaylists() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = if (isLoading) labelTertiary else accentColor,
                    )
                }
            }
            Text(
                "海量歌单，发现好歌",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = labelSecondary,
                letterSpacing = (-0.2).sp,
            )
        }

        // ── 平台筛选标签 (Apple Segmented 风格) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            platforms.take(6).forEach { platform ->
                val selected = selectedTab == platform
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) accentColor else secondaryBackground)
                        .clickable { selectedTab = platform }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (platform == "全部") platform else platformShort(platform),
                        color = if (selected) Color.White else labelSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 内容区域 ──
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = accentColor,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在加载歌单...",
                        fontSize = 14.sp,
                        color = labelSecondary,
                    )
                }
            }
        } else if (hasError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ErrorOutline, null,
                        tint = labelTertiary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        viewModel.recommendLoadError,
                        fontSize = 14.sp,
                        color = labelSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor)
                            .clickable { viewModel.loadRecommendPlaylists() }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text("重新加载", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        } else if (filteredPlaylists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LibraryMusic, null,
                        tint = labelTertiary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂无歌单",
                        fontSize = 16.sp,
                        color = labelSecondary,
                    )
                    Text(
                        "换个平台试试吧",
                        fontSize = 13.sp,
                        color = labelTertiary,
                    )
                }
            }
        } else {
            // 歌单网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 170.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(filteredPlaylists, key = { it.playlistId }, contentType = { "playlist" }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        accentColor = accentColor,
                        tertiaryBackground = tertiaryBackground,
                        labelPrimary = labelPrimary,
                        labelSecondary = labelSecondary,
                        onClick = { onPlaylistClick(playlist.platform, playlist.playlistId) },
                    )
                }
            }
        }
    }
}

/** 歌单卡片 */
@Composable
private fun PlaylistCard(
    playlist: RecommendPlaylist,
    accentColor: Color,
    tertiaryBackground: Color,
    labelPrimary: Color,
    labelSecondary: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(tertiaryBackground),
        ) {
            if (playlist.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(accentColor.copy(alpha = 0.15f), accentColor.copy(alpha = 0.05f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.LibraryMusic, null,
                        tint = accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // 平台标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(platformColorMap[playlist.platform] ?: defaultPlatformColor)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    platformShort(playlist.platform),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // 播放按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow, null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            playlist.name,
            fontSize = 13.sp,
            color = labelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
        )
        Text(
            formatPlayCount(playlist.playCount),
            fontSize = 11.sp,
            color = labelSecondary,
            maxLines = 1,
        )
    }
}

// 格式化播放量
private fun formatPlayCount(count: Long): String {
    return when {
        count >= 100_000_000 -> "${count / 100_000_000}亿"
        count >= 10_000 -> "${count / 10_000}万"
        else -> count.toString()
    }
}