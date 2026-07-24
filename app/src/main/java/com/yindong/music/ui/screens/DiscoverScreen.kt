@file:OptIn(ExperimentalLayoutApi::class)

package com.yindong.music.ui.screens

import com.yindong.music.data.api.RecommendPlaylist
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.yindong.music.data.model.Song
import com.yindong.music.ui.components.HeadsetBanner
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel
import java.util.Calendar

// ──────────────────────── Apple 设计令牌 ────────────────────────
// Apple System Colors
private val AppleSystemBlue = Color(0xFF007AFF)
private val AppleSystemRed = Color(0xFFFF3B30)
private val AppleSystemGreen = Color(0xFF34C759)
private val AppleSystemOrange = Color(0xFFFF9500)
private val AppleSystemPurple = Color(0xFFAF52DE)
private val AppleSystemPink = Color(0xFFFF2D55)
private val AppleSystemTeal = Color(0xFF5AC8FA)
private val AppleSystemIndigo = Color(0xFF5856D6)

// 排名颜色
private val RankGold = Color(0xFFFF9500)
private val RankSilver = Color(0xFF8E8E93)
private val RankBronze = Color(0xFFA2845E)

@Composable
fun DiscoverScreen(
    viewModel: MusicViewModel,
    onSearchClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onPlaylistClick: (Long) -> Unit,
    onHotChartClick: () -> Unit = {},
    onPlaylistSquareClick: () -> Unit = {},
    onNewSongsClick: () -> Unit = {},
    onImportPlaylistClick: () -> Unit = {},
    onExternalPlaylistClick: (platform: String, playlistId: String) -> Unit = { _, _ -> },
    onAiAudioEffectClick: () -> Unit = {},
) {
    fun doQuickSearch(keyword: String) {
        viewModel.quickSearch(keyword)
        onSearchClick()
    }

    LaunchedEffect(Unit) {
        if (viewModel.recommendPlaylists.isEmpty()) {
            viewModel.loadRecommendPlaylists()
        }
        if (viewModel.hotChartSongs.isEmpty()) {
            viewModel.loadHotChart()
        }
    }

    val importState = viewModel.importState
    LaunchedEffect(importState) {
        if (importState is MusicViewModel.ImportState.Success) {
            onPlaylistClick(importState.playlistId)
            viewModel.resetImportState()
        }
    }

    val isDark = isDarkTheme()
    // Apple 系统颜色
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accentColor = AppleSystemBlue

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 6..11 -> "早上好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
    val greetingSub = when (hour) {
        in 6..11 -> "开启活力满满的一天"
        in 12..13 -> "用音乐放松一下"
        in 14..17 -> "让旋律陪你度过午后"
        in 18..22 -> "享受夜晚的音乐时光"
        else -> "在静谧中聆听心声"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(systemBackground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 180.dp),
    ) {
        // ── Apple 风格大标题区 ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    greeting,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = labelPrimary,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 40.sp,
                )
                Text(
                    greetingSub,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = labelSecondary,
                    letterSpacing = (-0.2).sp,
                )
            }
        }

        // ── 搜索栏 (Apple 风格) ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(secondaryBackground)
                    .clickable { onSearchClick() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search, null,
                    tint = labelSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "搜索歌曲、歌手、歌单",
                    fontSize = 16.sp,
                    color = labelTertiary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Mic, null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // ── 耳机横幅 ──
        item {
            HeadsetBanner(
                isVisible = viewModel.showHeadsetBanner,
                deviceName = viewModel.connectedHeadsetName,
                onDismiss = { viewModel.dismissHeadsetBanner() },
                onEnterAudioEffect = onAiAudioEffectClick,
            )
        }

        // ── Apple 风格快捷入口 (圆形图标 + 文字) ──
        item {
            val dayOfMonth = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString() }
            val quickEntries = listOf(
                AppleQuickEntry("每日推荐", AppleSystemRed, Icons.Default.CalendarMonth, dayNumber = dayOfMonth) { onHotChartClick() },
                AppleQuickEntry("排行榜", AppleSystemOrange, Icons.Default.BarChart) { onHotChartClick() },
                AppleQuickEntry("歌单广场", AppleSystemPink, Icons.Default.QueueMusic) { onPlaylistSquareClick() },
                AppleQuickEntry("导入歌单", AppleSystemTeal, Icons.Default.Download) { onImportPlaylistClick() },
                AppleQuickEntry("新歌速递", AppleSystemIndigo, Icons.Default.MusicNote) { onNewSongsClick() },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(quickEntries, key = { it.title }, contentType = { "entry" }) { entry ->
                    AppleQuickEntryItem(entry)
                }
            }
        }

        // ── 推荐歌单 (Apple Music 大封面风格) ──
        item {
            val playlists = viewModel.recommendPlaylists.take(8)
            if (playlists.isNotEmpty()) {
                AppleSectionHeader(
                    title = "推荐歌单",
                    onMoreClick = { onPlaylistSquareClick() },
                    labelPrimary = labelPrimary,
                    accentColor = accentColor,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(playlists, key = { it.playlistId }, contentType = { "playlist" }) { playlist ->
                        ApplePlaylistCard(
                            playlist = playlist,
                            onClick = { onExternalPlaylistClick(playlist.platform, playlist.playlistId) },
                            tertiaryBackground = tertiaryBackground,
                            labelPrimary = labelPrimary,
                            labelSecondary = labelSecondary,
                        )
                    }
                }
            }
        }

        // ── 新歌速递 (Apple Music 横向卡片) ──
        item {
            AppleSectionHeader(
                title = "新歌速递",
                onMoreClick = { onNewSongsClick() },
                labelPrimary = labelPrimary,
                accentColor = accentColor,
            )
        }

        item {
            val hotSongs = viewModel.hotChartSongs.take(10)
            if (hotSongs.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(hotSongs, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song ->
                        AppleSongCard(
                            song = song,
                            accentColor = accentColor,
                            onClick = { viewModel.playPlaylist(viewModel.hotChartSongs, index) },
                            tertiaryBackground = tertiaryBackground,
                            labelPrimary = labelPrimary,
                            labelSecondary = labelSecondary,
                        )
                    }
                }
            } else {
                // 加载占位卡片
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    listOf("热歌推荐" to AppleSystemRed, "新歌速递" to AppleSystemOrange, "流行热歌" to AppleSystemPink).forEach { (title, color) ->
                        AppleLoadingCard(
                            title = title,
                            color = color,
                            modifier = Modifier.weight(1f),
                            secondaryBackground = secondaryBackground,
                            labelPrimary = labelPrimary,
                            labelTertiary = labelTertiary,
                            onClick = { doQuickSearch(title) },
                        )
                    }
                }
            }
        }

        // ── 热搜榜 (Apple 列表分组风格) ──
        item {
            AppleSectionHeader(
                title = "热搜榜",
                onMoreClick = null,
                labelPrimary = labelPrimary,
                accentColor = accentColor,
            )
        }

        item {
            val searches = viewModel.hotSearches
            // Apple 风格分组列表 (Inset Grouped)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(secondaryBackground),
            ) {
                searches.take(8).forEachIndexed { index, keyword ->
                    AppleHotSearchRow(
                        rank = index + 1,
                        keyword = keyword,
                        onClick = { doQuickSearch(keyword) },
                        labelPrimary = labelPrimary,
                        labelSecondary = labelSecondary,
                        separator = separator,
                        isLast = index == minOf(searches.size, 8) - 1,
                    )
                }
            }
        }

        // ── 场景推荐 (Apple 卡片网格) ──
        item {
            AppleSectionHeader(
                title = "场景推荐",
                onMoreClick = null,
                labelPrimary = labelPrimary,
                accentColor = accentColor,
            )
        }

        item {
            val scenePlaylists = viewModel.recommendPlaylists.drop(8).take(3)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (scenePlaylists.size >= 3) {
                    scenePlaylists.forEach { playlist ->
                        AppleSceneCard(
                            playlist = playlist,
                            modifier = Modifier.weight(1f),
                            onClick = { onExternalPlaylistClick(playlist.platform, playlist.playlistId) },
                            tertiaryBackground = tertiaryBackground,
                            labelPrimary = labelPrimary,
                            labelSecondary = labelSecondary,
                        )
                    }
                } else {
                    AppleSceneFallback(
                        icon = Icons.Default.Headphones,
                        title = "睡前",
                        subtitle = "轻柔入梦",
                        color = AppleSystemIndigo,
                        modifier = Modifier.weight(1f),
                        tertiaryBackground = tertiaryBackground,
                        labelPrimary = labelPrimary,
                        labelSecondary = labelSecondary,
                        onClick = { doQuickSearch("睡前轻音乐") },
                    )
                    AppleSceneFallback(
                        icon = Icons.Default.SportsEsports,
                        title = "游戏",
                        subtitle = "燃战BGM",
                        color = AppleSystemRed,
                        modifier = Modifier.weight(1f),
                        tertiaryBackground = tertiaryBackground,
                        labelPrimary = labelPrimary,
                        labelSecondary = labelSecondary,
                        onClick = { doQuickSearch("游戏BGM") },
                    )
                    AppleSceneFallback(
                        icon = Icons.Default.Favorite,
                        title = "恋爱",
                        subtitle = "甜蜜时光",
                        color = AppleSystemPink,
                        modifier = Modifier.weight(1f),
                        tertiaryBackground = tertiaryBackground,
                        labelPrimary = labelPrimary,
                        labelSecondary = labelSecondary,
                        onClick = { doQuickSearch("甜蜜恋爱") },
                    )
                }
            }
        }

        // ── 猜你喜欢 (Apple 胶囊标签) ──
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "猜你喜欢",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = labelPrimary,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight, null,
                    tint = labelTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            val guessKeywords = remember(viewModel.searchHistory, viewModel.hotChartSongs) {
                val fromHistory = viewModel.searchHistory.take(5)
                val fromHotArtists = viewModel.hotChartSongs
                    .map { it.artist }
                    .distinct()
                    .filterNot { a -> fromHistory.any { it.contains(a) } }
                    .take(10)
                (fromHistory + fromHotArtists).distinct().take(12)
            }
            if (guessKeywords.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    guessKeywords.forEach { keyword ->
                        AppleChip(
                            text = keyword,
                            secondaryBackground = secondaryBackground,
                            labelPrimary = labelPrimary,
                            onClick = { doQuickSearch(keyword) },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ══════════════════════════════════════════════════════════
//  Apple 风格子组件
// ══════════════════════════════════════════════════════════

private data class AppleQuickEntry(
    val title: String,
    val color: Color,
    val icon: ImageVector,
    val dayNumber: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun AppleQuickEntryItem(entry: AppleQuickEntry) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable { entry.onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(entry.color),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.dayNumber != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        entry.icon, null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        entry.dayNumber,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp,
                    )
                }
            } else {
                Icon(
                    entry.icon, null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AppleSectionHeader(
    title: String,
    onMoreClick: (() -> Unit)?,
    labelPrimary: Color,
    accentColor: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = labelPrimary,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.weight(1f))
        if (onMoreClick != null) {
            Text(
                "查看全部",
                fontSize = 15.sp,
                color = accentColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onMoreClick() },
            )
        }
    }
}

@Composable
private fun ApplePlaylistCard(
    playlist: RecommendPlaylist,
    onClick: () -> Unit,
    tertiaryBackground: Color,
    labelPrimary: Color,
    labelSecondary: Color,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
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
                Icon(
                    Icons.Default.LibraryMusic, null,
                    tint = labelSecondary.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                )
            }
            // 平台标签
            if (playlist.platform.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        playlist.platform,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            // 播放次数
            if (playlist.playCount > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.PlayArrow, null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        formatPlayCount(playlist.playCount),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            fontSize = 13.sp,
            color = labelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AppleSongCard(
    song: Song,
    accentColor: Color,
    onClick: () -> Unit,
    tertiaryBackground: Color,
    labelPrimary: Color,
    labelSecondary: Color,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(tertiaryBackground),
        ) {
            if (song.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.LibraryMusic, null,
                    tint = labelSecondary.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                )
            }
            // 平台标签
            if (song.platform.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        song.platform,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            // 播放按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow, null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            fontSize = 13.sp,
            color = labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            song.artist,
            fontSize = 12.sp,
            color = labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppleLoadingCard(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    secondaryBackground: Color,
    labelPrimary: Color,
    labelTertiary: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(secondaryBackground)
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MusicNote, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
        ) {
            Text(
                title,
                fontSize = 13.sp,
                color = labelPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "加载中...",
                fontSize = 11.sp,
                color = labelTertiary,
            )
        }
    }
}

@Composable
private fun AppleHotSearchRow(
    rank: Int,
    keyword: String,
    onClick: () -> Unit,
    labelPrimary: Color,
    labelSecondary: Color,
    separator: Color,
    isLast: Boolean,
) {
    val rankColor = when (rank) {
        1 -> RankGold
        2 -> RankSilver
        3 -> RankBronze
        else -> labelSecondary
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 排名
            Text(
                "$rank",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3) rankColor else labelSecondary,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                keyword,
                fontSize = 15.sp,
                color = labelPrimary,
                fontWeight = if (rank <= 3) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rank <= 3) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(rankColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "HOT",
                        fontSize = 9.sp,
                        color = rankColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (!isLast) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 0.dp)
                    .height(0.5.dp)
                    .background(separator),
            )
        }
    }
}

@Composable
private fun AppleSceneCard(
    playlist: RecommendPlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    tertiaryBackground: Color,
    labelPrimary: Color,
    labelSecondary: Color,
) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(tertiaryBackground)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
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
                Icon(
                    Icons.Default.LibraryMusic, null,
                    tint = labelSecondary.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center),
                )
            }
        }
        Text(
            playlist.name,
            fontSize = 13.sp,
            color = labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AppleSceneFallback(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    tertiaryBackground: Color,
    labelPrimary: Color,
    labelSecondary: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(tertiaryBackground)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            fontSize = 14.sp,
            color = labelPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = 11.sp,
            color = labelSecondary,
        )
    }
}

@Composable
private fun AppleChip(
    text: String,
    secondaryBackground: Color,
    labelPrimary: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(secondaryBackground)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = labelPrimary,
            fontWeight = FontWeight.Normal,
        )
    }
}

private fun formatPlayCount(count: Long): String {
    return when {
        count >= 100_000_000 -> "${count / 100_000_000}亿"
        count >= 10_000 -> "${count / 10_000}万"
        count >= 1000 -> "${count / 1000}k"
        else -> "$count"
    }
}
