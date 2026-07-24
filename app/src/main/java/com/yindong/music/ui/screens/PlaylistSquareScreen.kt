package com.yindong.music.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yindong.music.data.api.RecommendPlaylist
import com.yindong.music.ui.theme.CoverGradients
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
    "抖音" to Color(0xFF121212),
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

@Composable
fun PlaylistSquareScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onPlaylistClick: (String, String) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (viewModel.recommendPlaylists.isEmpty()) {
            viewModel.loadRecommendPlaylists()
        }
    }

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

    var selectedTab by remember { mutableStateOf("全部") }
    val allPlaylists = viewModel.recommendPlaylists
    val isLoading = viewModel.isRecommendLoading
    val hasError = !isLoading && allPlaylists.isEmpty() && viewModel.recommendLoadError.isNotEmpty()
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
        // ── 顶部标题栏 (Clean White) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回按钮: 圆形白色按钮 + 阴影
            Box(
                modifier = Modifier
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
                    tint = labelPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            // 标题图标: accent 色 + 柔和 accent 背景圆
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentSoftBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "歌单广场",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = labelPrimary,
                )
                Text(
                    "${allPlaylists.size} 个精选歌单 · ${platforms.size - 1} 个平台",
                    style = MaterialTheme.typography.bodySmall,
                    color = labelSecondary,
                )
            }
        }

        // ── 平台筛选 Tab ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            platforms.forEach { tab ->
                val isSelected = selectedTab == tab
                val tabColor = if (tab == "全部") accent else platformColorMap[tab] ?: defaultPlatformColor
                val animBg by animateColorAsState(
                    targetValue = when {
                        isSelected -> tabColor
                        isDark -> surfaceColor
                        else -> cardColor
                    },
                    animationSpec = tween(200), label = "tabBg",
                )
                val animFg by animateColorAsState(
                    targetValue = if (isSelected) Color.White else labelSecondary,
                    animationSpec = tween(200), label = "tabFg",
                )
                val animBorder by animateColorAsState(
                    targetValue = when {
                        isSelected -> Color.Transparent
                        else -> subtleBorder
                    },
                    animationSpec = tween(200), label = "tabBorder",
                )
                val count = if (tab == "全部") allPlaylists.size
                    else allPlaylists.count { it.platform == tab }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(animBg)
                        .border(1.dp, animBorder, RoundedCornerShape(12.dp))
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 平台色小圆点
                    if (tab != "全部") {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White.copy(alpha = 0.8f) else tabColor),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        tab,
                        color = animFg,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                    // 数量角标
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$count",
                        color = animFg.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }

        // ── 内容区域 ──
        if (isLoading && allPlaylists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accent, strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "加载精选歌单中...",
                        color = labelSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        } else if (hasError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = cs.error.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "获取歌单失败",
                        color = labelPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        viewModel.recommendLoadError,
                        color = labelSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.error.copy(alpha = 0.1f))
                            .border(1.dp, cs.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.loadRecommendPlaylists() }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = cs.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新加载", color = cs.error, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else if (filteredPlaylists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = labelTertiary,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂无歌单数据",
                        color = labelSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(filteredPlaylists, key = { it.platform + it.playlistId }, contentType = { "playlist" }) { playlist ->
                    PlaylistGridItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist.platform, playlist.playlistId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridItem(
    playlist: RecommendPlaylist,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val cardColor = tertiaryBackground
    val subtleBorder = separator
    val pColor = platformColorMap[playlist.platform] ?: defaultPlatformColor
    val gradientIndex = (playlist.playlistId.hashCode() and 0x7FFFFFFF) % CoverGradients.size
    val fallbackGradient = CoverGradients[gradientIndex]

    Column(
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(cardColor)
                .border(1.dp, subtleBorder, RoundedCornerShape(12.dp)),
        ) {
            if (playlist.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // 无封面时显示渐变背景 + 装饰元素
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            colors = fallbackGradient,
                            start = androidx.compose.ui.geometry.Offset.Zero,
                            end = androidx.compose.ui.geometry.Offset(300f, 300f),
                        )
                    ),
                ) {
                    // 装饰光圈
                    Box(
                        Modifier
                            .size(50.dp)
                            .offset(x = (-10).dp, y = (-10).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f)),
                    )
                    Box(
                        Modifier
                            .size(30.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 8.dp, y = 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(40.dp).align(Alignment.Center),
                    )
                }
            }

            // 底部渐变遮罩 (让封面上的信息更清晰)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                        )
                    ),
            )

            // 平台彩色标签 (左上)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(pColor.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    platformShort(playlist.platform),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
            }

            // 播放量 (右上)
            if (playlist.playCount > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(formatCount(playlist.playCount), color = Color.White, fontSize = 9.sp)
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        // 歌单名称
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodySmall,
            color = labelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
        count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
        count >= 1000 -> String.format("%.1fk", count / 1000.0)
        else -> "$count"
    }
}
