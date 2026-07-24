package com.yindong.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yindong.music.ui.components.SongItem
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel

@Composable
fun HotChartScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    isNewSongsMode: Boolean = false,
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
    val cardColor = tertiaryBackground
    val subtleBorder = separator
    val accentSoftBg = accent.copy(alpha = 0.08f)

    val screenTitle = if (isNewSongsMode) "新歌速递" else "热歌排行榜"
    val screenSubtitle = if (isNewSongsMode) "最新发布 · 每日更新" else "每日更新 · 热门单曲"
    val displaySongs = if (isNewSongsMode) viewModel.newSongs else viewModel.hotChartSongs
    val isLoading = if (isNewSongsMode) viewModel.isNewSongsLoading else viewModel.isHotChartLoading
    val loadingText = if (isNewSongsMode) "正在加载新歌速递..." else "正在加载热歌榜..."
    val emptyText = if (isNewSongsMode) "暂无新歌数据" else "暂无数据"
    val countText = if (isNewSongsMode) "共 ${displaySongs.size} 首新歌" else "共 ${displaySongs.size} 首热门歌曲"

    LaunchedEffect(Unit) {
        if (isNewSongsMode) {
            viewModel.loadNewSongs()
        } else {
            viewModel.loadHotChart()
        }
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
                    if (isNewSongsMode) Icons.Default.NewReleases else Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    screenTitle,
                    fontSize = 34.sp,
                    color = labelPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    screenSubtitle,
                    fontSize = 13.sp,
                    color = labelSecondary,
                )
            }
        }

        // ── 内容 ──
        if (isLoading && displaySongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = accent,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        loadingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = labelSecondary,
                    )
                }
            }
        } else if (displaySongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = labelTertiary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = labelSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { if (isNewSongsMode) viewModel.loadNewSongs() else viewModel.loadHotChart() }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                // 播放全部按钮
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent)
                                .clickable { viewModel.playAllFromList(displaySongs) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("播放全部", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            countText,
                            style = MaterialTheme.typography.bodySmall,
                            color = labelSecondary,
                        )
                    }
                }

                itemsIndexed(displaySongs, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song ->
                    SongItem(
                        song = song,
                        index = index,
                        onSongClick = {
                            viewModel.playPlaylist(displaySongs, index)
                        },
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
