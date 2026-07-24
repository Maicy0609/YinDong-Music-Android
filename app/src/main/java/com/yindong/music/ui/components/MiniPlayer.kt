package com.yindong.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel
import kotlinx.coroutines.delay

/**
 * 居中浮动迷你播放器：灰色小长方形卡片，3秒后自动收缩为小灰色圆形
 * 功能与原全局底播放栏一致：封面/进度环、标题/歌手、上一首/播放暂停/下一首
 */
@Composable
fun MiniPlayer(
    viewModel: MusicViewModel,
    onPlayerClick: () -> Unit,
    onQueueClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val song = viewModel.currentSong ?: return

    val progress by animateFloatAsState(
        targetValue = viewModel.progress,
        animationSpec = tween(100),
        label = "progress"
    )

    val isDark = isDarkTheme()

    // —— 3 秒自动收缩逻辑 ——
    var isCollapsed by remember { mutableStateOf(false) }
    var expandNonce by remember { mutableIntStateOf(0) }
    // 切歌或手动展开时重置计时器
    LaunchedEffect(song.id, expandNonce) {
        isCollapsed = false
        delay(3000)
        isCollapsed = true
    }

    // 背景色：浅色模式灰色，深色模式深灰
    val expandedBg = if (isDark) Color(0xF21C1C1E) else Color(0xE6E3E3E8)
    val collapsedBg = if (isDark) Color(0xCC2C2C2E) else Color(0xCCCFD0D6)
    val shadowColor = if (isDark) Color(0x28000000) else Color(0x14000000)
    // 收缩态图标颜色
    val collapsedIconTint = if (isDark) Color.White else Color(0xFF333333)

    AnimatedContent(
        targetState = isCollapsed,
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.85f)) togetherWith
                (fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.85f))
        },
        contentAlignment = Alignment.Center,
        modifier = modifier,
        label = "collapse",
    ) { collapsed ->
        if (collapsed) {
            // —— 收缩态：小灰色圆形，点击展开 ——
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(6.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(CircleShape)
                    .background(collapsedBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expandNonce++ },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (viewModel.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (viewModel.isPlaying) "暂停" else "播放",
                        tint = collapsedIconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else {
            // —— 展开态：灰色小长方形 ——
            Row(
                modifier = Modifier
                    .width(300.dp)
                    .shadow(8.dp, RoundedCornerShape(28.dp), ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(RoundedCornerShape(28.dp))
                    .background(expandedBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayerClick,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniPlayerCoverProgress(
                    coverUrl = song.coverUrl,
                    isPlaying = viewModel.isPlaying,
                    progress = progress,
                    coverSize = 34.dp,
                    ringSize = 40.dp,
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        song.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(32.dp),
                ) {
                    if (viewModel.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (viewModel.isPlaying) "暂停" else "播放",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerCoverProgress(
    coverUrl: String,
    isPlaying: Boolean,
    progress: Float,
    coverSize: androidx.compose.ui.unit.Dp,
    ringSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(coverSize)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(coverSize * 0.5f),
                )
            }
        }
        CircularProgressRing(
            progress = progress,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            modifier = Modifier.size(ringSize),
        )
    }
}

@Composable
private fun CircularProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.graphicsLayer { rotationZ = -90f }) {
        val strokeWidth = 2.dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = size.copy(width = size.width - strokeWidth, height = size.height - strokeWidth)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Square),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Square),
        )
    }
}
