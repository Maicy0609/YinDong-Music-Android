package com.yindong.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yindong.music.data.model.Song
import com.yindong.music.ui.theme.GlassGradients

/**
 * 高性能歌曲列表项
 *
 * 优化点：
 * 1. 移除 crossfade — 滑动时 alpha 动画会导致大量额外绘制帧
 * 2. 移除 drawBehind 每帧渐变绘制 — 改用纯色背景，降低 GPU 负担
 * 3. ImageRequest 不再每次重组重建 — 直接传 URL 给 AsyncImage
 * 4. contentDescription 设为 null — 减少无障碍树构建开销
 * 5. 预计算 fallbackGradient 并 remember — 避免重复计算
 */
@Composable
fun SongItem(
    song: Song,
    index: Int? = null,
    onSongClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    val artworkUrl = remember(song.coverUrl, song.artistPicUrl) {
        if (song.coverUrl.isNotBlank()) song.coverUrl else song.artistPicUrl
    }

    val glassGradients = GlassGradients
    val fallbackGradient = remember(song.id, glassGradients) {
        val idx = ((song.id % glassGradients.size).toInt() + glassGradients.size) % glassGradients.size
        glassGradients[idx]
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSongClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index < 3) accentColor else cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        // 封面容器：纯色背景替代渐变绘制，降低每帧 GPU 负担
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (artworkUrl.isEmpty()) Modifier.background(Brush.linearGradient(fallbackGradient))
                    else Modifier.background(cs.surfaceVariant)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkUrl.isNotEmpty()) {
                // 直接传 URL，不构建 ImageRequest，不使用 crossfade
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${song.artist} - ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onMoreClick != null) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = cs.onSurfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMoreClick() }
                    .padding(4.dp),
            )
        } else {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = cs.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
