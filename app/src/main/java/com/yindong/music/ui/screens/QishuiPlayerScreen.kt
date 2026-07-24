package com.yindong.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.yindong.music.R
import com.yindong.music.data.model.Song
import com.yindong.music.viewmodel.MusicViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader

// ══════════════════════════════════════════════════════════════════
// 桥接层 —— 替代 qduan 中缺失的 AudioQualityPrefs / AudioEffectsController
// ══════════════════════════════════════════════════════════════════

private object AudioQualityPrefs {
    data class State(val mode: Int = 0)
    val state: MutableStateFlow<State> = MutableStateFlow(State())
    fun label(mode: Int): String = "标准音质"
}

private object AudioEffectsController {
    const val MODE_NORMAL = 0
    const val MODE_STUDIO = 1
    const val MODE_SPATIAL = 2
    const val MODE_PANORAMIC = 3
    const val MODE_MASTER = 4
    const val MODE_MASTER2 = 5
    const val MODE_LIVE = 6
    const val MODE_HEADPHONE_A3 = 7
    const val MODE_POP = 8
    const val MODE_ROCK = 9
    const val MODE_JAZZ = 10
    const val MODE_CLASSICAL = 11
    const val MODE_BASS = 12
    const val MODE_VOCAL = 13
    const val MODE_ELECTRONIC = 14
    const val MODE_HIPHOP = 15
    data class State(val mode: Int = 0)
    val state: MutableStateFlow<State> = MutableStateFlow(State())
}

data class PlayerProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val effectiveDurationMs: Long = 0,
)

data class Track(
    val id: String,
    val title: String? = null,
    val artist: String? = null,
    val artists: List<String?>? = null,
    val creator: String? = null,
    val pic: String? = null,
    val duration: Long = 0,
    val colorBg: String? = null,
    val lyrics: String? = null,
    val lyricsKrc: String? = null,
    val lyricsLrc: String? = null,
    val nodes: List<TrackNode>? = null,
    val stats: TrackStats? = null,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "未命名"

    val displayArtist: String
        get() = artists?.filterNotNull()?.joinToString(" / ")?.takeIf { it.isNotBlank() }
            ?: artist?.takeIf { it.isNotBlank() }
            ?: creator?.takeIf { it.isNotBlank() }
            ?: "未知"
}

data class TrackNode(
    val type: Int,
    val start: Long,
    val opacity: Float,
)

data class TrackStats(
    val collectCount: Long? = null,
    val commentCount: Long? = null,
    val shareCount: Long? = null,
)

fun Long?.formatCount(): String {
    val v = this ?: 0
    return when {
        v >= 10_000 -> "%.1f万".format(v / 10_000.0)
        v >= 1_000 -> "%.1fk".format(v / 1_000.0)
        v > 0 -> v.toString()
        else -> "—"
    }
}

@Composable
private fun rememberCoverColor(coverUrl: String): String? {
    val context = LocalContext.current
    var colorHex by remember(coverUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(coverUrl) {
        if (coverUrl.isBlank()) { colorHex = null; return@LaunchedEffect }
        try {
            val request = ImageRequest.Builder(context).data(coverUrl).allowHardware(false).build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                // 直接使用 qduan 的方法：Palette 提取封面主色，取原始色值不做任何压暗/偏移。
                val palette = Palette.from(bitmap).generate()
                val rgb = palette.dominantSwatch?.rgb
                if (rgb != null) {
                    colorHex = String.format("#%06X", 0xFFFFFF and rgb)
                }
            }
        } catch (_: Exception) { colorHex = null }
    }
    return colorHex
}

@Composable
fun QishuiPlayerScreen(
    song: Song,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    dur: Long,
    @Suppress("UNUSED_PARAMETER") showQueue: androidx.compose.runtime.MutableState<Boolean>,
    showBottomSheet: androidx.compose.runtime.MutableState<Boolean>,
    showEqualizer: androidx.compose.runtime.MutableState<Boolean>,
    showLyricsSheet: androidx.compose.runtime.MutableState<Boolean>,
    @Suppress("UNUSED_PARAMETER") showNewPlaylist: androidx.compose.runtime.MutableState<Boolean>,
    showAddToPlaylist: androidx.compose.runtime.MutableState<Boolean>,
    showDownloadQuality: androidx.compose.runtime.MutableState<Boolean>,
) {
    val coverColor = rememberCoverColor(song.coverUrl)
    val track = remember(song.id, song.title, song.artist, song.coverUrl, song.duration, song.lrcText, coverColor) {
        Track(
            id = song.id.toString(),
            title = song.title,
            artist = song.artist,
            pic = song.coverUrl.ifEmpty { null },
            duration = song.duration,
            colorBg = coverColor,
            lyrics = song.lrcText.ifEmpty { null },
        )
    }
    // 计算背景色（与 AudioTrackCard 中的 bg 逻辑一致，直接拿到这里用）
    val bg = remember(coverColor) {
        val hex = coverColor?.trim()
        if (hex.isNullOrEmpty()) Color(0xFF303030)
        else runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color(0xFF303030) }
    }

    val playerProgress = PlayerProgress(
        positionMs = viewModel.currentPlaybackTimeMs,
        durationMs = dur,
        bufferedMs = 0,
        effectiveDurationMs = dur,
    )

    // [状态栏] 汽水风背景是封面主色 → 状态栏背景必须同步为 bg，图标白色
    // 用 SideEffect 直接改 window.statusBarColor，而不是只改 isAppearanceLightStatusBars
    // （Theme.kt 的 SideEffect 会在之后执行并覆盖 isAppearanceLightStatusBars，
    //  但 window.statusBarColor 是纯色，不会被 Theme 覆盖）
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val bgArgb = bg.toArgb()
        window.statusBarColor = bgArgb
        window.navigationBarColor = bgArgb
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    // 底栏自动隐藏状态 —— 提升到根容器，全屏检测点击
    val bottomBarVisible = remember { mutableStateOf(true) }
    val bottomBarToken = remember { mutableStateOf(0) }
    LaunchedEffect(bottomBarToken.value) {
        bottomBarVisible.value = true
        delay(3000)
        bottomBarVisible.value = false
    }

    CompositionLocalProvider(
        LocalBottomBarVisible provides bottomBarVisible,
        LocalBottomBarShowToken provides bottomBarToken,
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(bottomBarVisible.value) {
                if (!bottomBarVisible.value) {
                    awaitEachGesture {
                        awaitPointerEvent(PointerEventPass.Initial)
                        bottomBarToken.value++
                    }
                }
            }
    ) {
        // AudioTrackCard 与 qduan 保持一致，占满全屏；底部播放控制作为浮层覆盖，
        // 不压缩内容高度，使封面尺寸、歌词位置与 qduan 完全对齐。
        AudioTrackCard(
            track = track,
            song = song,
            vm = viewModel,
            isCurrent = true,
            playerProgress = playerProgress,
            showQueue = showQueue,
            showEqualizer = showEqualizer,
            showAddToPlaylist = showAddToPlaylist,
            showDownloadQuality = showDownloadQuality,
            onOpenQuality = { showDownloadQuality.value = true },
            onOpenEffects = { showEqualizer.value = true },
            onOpenMore = { showBottomSheet.value = true },
        )
        // 顶栏浮层
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { viewModel.toggleQduanStyle() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SwapHoriz, "切换", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            }
        }
    }
    }  // CompositionLocalProvider
}

@Composable
private fun BottomPlaybackControls(
    vm: MusicViewModel,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.playPrevious() }, Modifier.size(52.dp)) {
            Icon(Icons.Default.SkipPrevious, "上一首", tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Box(
            modifier = Modifier.size(68.dp).clip(CircleShape).background(Color.Transparent)
                .border(width = 2.dp, color = Color.White.copy(alpha = 0.9f), shape = CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (vm.isPlaying) "暂停" else "播放",
                tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(38.dp)
            )
        }
        IconButton(onClick = { vm.playNext() }, Modifier.size(52.dp)) {
            Icon(Icons.Default.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

/**
 * 底部 8 图标功能栏 —— 对齐其他 7 个播放界面的 UnifiedBottomBar 辅助按钮行。
 */
@Composable
private fun QishuiBottomIconRow(
    song: Song,
    vm: MusicViewModel,
    showQueue: androidx.compose.runtime.MutableState<Boolean>,
    showEqualizer: androidx.compose.runtime.MutableState<Boolean>,
    showAddToPlaylist: androidx.compose.runtime.MutableState<Boolean>,
    showDownloadQuality: androidx.compose.runtime.MutableState<Boolean>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tint = Color.White
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isFav = vm.isFavorite(song)
        IconButton(onClick = { vm.toggleLike(song) }, Modifier.size(40.dp), enabled = enabled) {
            Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFav) tint else tint.copy(0.8f), modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = { showAddToPlaylist.value = true }, Modifier.size(40.dp), enabled = enabled) {
            Icon(Icons.Default.PlaylistAdd, null, tint = tint.copy(0.8f), modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = { vm.changePlayMode() }, Modifier.size(40.dp), enabled = enabled) {
            Icon(
                when (vm.playMode) {
                    MusicViewModel.PlayMode.LOOP -> Icons.Default.Repeat
                    MusicViewModel.PlayMode.SINGLE -> Icons.Default.RepeatOne
                    MusicViewModel.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                },
                null, tint = tint.copy(0.85f), modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = { vm.toggleFloatingLyrics() }, Modifier.size(40.dp), enabled = enabled) {
            Text("词", color = if (vm.isFloatingLyricsEnabled) tint else tint.copy(0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = { showQueue.value = true }, Modifier.size(40.dp), enabled = enabled) {
            Icon(Icons.Default.List, null, tint = tint.copy(0.85f), modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = { showEqualizer.value = true }, Modifier.size(40.dp), enabled = enabled) {
            Icon(Icons.Default.Equalizer, null, tint = tint.copy(0.8f), modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = { showDownloadQuality.value = true }, Modifier.size(40.dp), enabled = enabled) {
            val dlState = vm.downloadState
            val isThisDownloading = dlState is MusicViewModel.DownloadState.Downloading && dlState.songId == song.platformId
            val isDownloaded = vm.isDownloaded(song)
            if (isThisDownloading) {
                CircularProgressIndicator(
                    progress = { (dlState as MusicViewModel.DownloadState.Downloading).progress },
                    modifier = Modifier.size(22.dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Default.Download, null, tint = if (isDownloaded) tint else tint.copy(0.8f), modifier = Modifier.size(22.dp))
            }
        }
        IconButton(onClick = { vm.toggleQduanStyle() }, Modifier.size(40.dp), enabled = enabled) {
            Icon(Icons.Default.SwapHoriz, null, tint = tint.copy(0.8f), modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * 底部 8 图标功能栏 —— 状态由根容器 CompositionLocal 提供，全屏点击重新显示。
 */
@Composable
private fun AutoHideBottomIconRow(
    song: Song,
    vm: MusicViewModel,
    showQueue: androidx.compose.runtime.MutableState<Boolean>,
    showEqualizer: androidx.compose.runtime.MutableState<Boolean>,
    showAddToPlaylist: androidx.compose.runtime.MutableState<Boolean>,
    showDownloadQuality: androidx.compose.runtime.MutableState<Boolean>,
) {
    val controlsVisible = LocalBottomBarVisible.current.value
    val alpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "controlsAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        QishuiBottomIconRow(
            song = song,
            vm = vm,
            showQueue = showQueue,
            showEqualizer = showEqualizer,
            showAddToPlaylist = showAddToPlaylist,
            showDownloadQuality = showDownloadQuality,
            enabled = controlsVisible,
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        )
    }
}

// ══════════════════════════════════════════════════════════════════
// 以下完完全全复制自 qduan RecommendScreen.kt L706-1622
// 仅 MouyinPlayer.xxx → vm.xxx 桥接，LyricFontFamily → FontFamily.Default
// ══════════════════════════════════════════════════════════════════

@Composable
private fun AudioTrackCard(
    track: Track,
    song: Song,
    vm: MusicViewModel,
    isCurrent: Boolean,
    playerProgress: PlayerProgress,
    showQueue: androidx.compose.runtime.MutableState<Boolean>,
    showEqualizer: androidx.compose.runtime.MutableState<Boolean>,
    showAddToPlaylist: androidx.compose.runtime.MutableState<Boolean>,
    showDownloadQuality: androidx.compose.runtime.MutableState<Boolean>,
    onOpenQuality: () -> Unit = {},
    onOpenEffects: () -> Unit = {},
    onOpenMore: () -> Unit = {},
) {
    val isPlayingThisCard = isCurrent && vm.isPlaying && vm.currentSong?.id == song.id
    val bg = remember(track.colorBg) {
        val hex = track.colorBg?.trim()
        if (hex.isNullOrEmpty()) Color(0xFF303030)
        else runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color(0xFF303030) }
    }
    var likeAnimVisible by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            // 上滑上一首 / 下滑下一首
            .pointerInput(track.id) {
                val threshold = 80.dp.toPx()
                var totalY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalY = 0f },
                    onVerticalDrag = { _, dragAmount -> totalY += dragAmount },
                    onDragEnd = {
                        if (kotlin.math.abs(totalY) > threshold) {
                            if (totalY < 0) vm.playPrevious() else vm.playNext()
                        }
                    }
                )
            }
            .pointerInput(track.id) {
                detectTapGestures(onDoubleTap = {
                    likeAnimVisible = true
                    vm.toggleFavorite(song)
                })
            },
    ) {
        val windowH = maxHeight
        val windowW = maxWidth
        val wide = windowW >= 600.dp
        val compact = windowH < 600.dp
        val tinyWidth = windowW < 320.dp
        // 封面向左上收：减小顶部 spacer、加大封面左右内边距
        val topSpacer = if (compact) 32.dp else 70.dp
        // 绿框区域整体向上移，为底部 8 图标功能栏留出空间
        val bottomSpacer = if (compact) 130.dp else 124.dp
        val coverMaxSize = if (windowH >= 760.dp) 320.dp else (windowH * 0.60f - 160.dp).coerceIn(150.dp, 320.dp)
        val titleFont = if (wide) 28.sp else if (compact) 14.sp else 19.sp
        val artistFont = if (wide) 18.sp else if (compact) 11.sp else 15.sp
        val titleArtistGap = if (compact) 2.dp else 6.dp
        val actionToBarGap = if (compact) 4.dp else 8.dp
        val hPad = if (tinyWidth) 12.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Spacer(modifier = Modifier.height(topSpacer))
            val wide = windowW >= 600.dp
            if (wide) {
                val wideCover = (windowH * 0.42f).coerceIn(150.dp, 280.dp)
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        AlbumCover(track = track, maxSize = wideCover, isCurrent = isCurrent, isPlayingThisCard = isPlayingThisCard)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp), contentAlignment = Alignment.CenterStart) {
                        LyricStrip(track = track, positionMs = playerProgress.positionMs, wide = true)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 28.dp), contentAlignment = Alignment.Center) {
                    AlbumCover(track = track, maxSize = coverMaxSize, isCurrent = isCurrent, isPlayingThisCard = isPlayingThisCard)
                }
                if (!compact || windowH > 420.dp) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = hPad), contentAlignment = Alignment.CenterStart) {
                        LyricStrip(track = track, positionMs = playerProgress.positionMs)
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = hPad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = track.displayTitle, color = Color.White, fontSize = titleFont, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    val qualityState by AudioQualityPrefs.state.collectAsState()
                    val effectsState by AudioEffectsController.state.collectAsState()
                    val gap = if (compact) 4.dp else 6.dp
                    Spacer(modifier = Modifier.width(gap))
                    InfoChip(text = AudioQualityPrefs.label(qualityState.mode), onClick = onOpenQuality)
                    Spacer(modifier = Modifier.width(gap))
                    InfoChip(text = effectsModeLabel(effectsState.mode), onClick = onOpenEffects)
                }
                Spacer(modifier = Modifier.height(titleArtistGap))
                Text(text = track.displayArtist, color = Color.White.copy(alpha = 0.88f), fontSize = artistFont, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(actionToBarGap))
                ThinProgressBar(playerProgress, track, vm)
                // 时间行：对齐第二张图片，当前时间 / 当前音质 / 总时长
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val qualityState by AudioQualityPrefs.state.collectAsState()
                    Text(fmt(playerProgress.positionMs), color = Color.White.copy(0.5f), fontSize = 11.sp)
                    Text(AudioQualityPrefs.label(qualityState.mode), color = Color.White.copy(0.5f), fontSize = 11.sp)
                    Text(fmt(playerProgress.durationMs), color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                BottomPlaybackControls(
                    vm = vm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AutoHideBottomIconRow(
                song = song,
                vm = vm,
                showQueue = showQueue,
                showEqualizer = showEqualizer,
                showAddToPlaylist = showAddToPlaylist,
                showDownloadQuality = showDownloadQuality,
            )
            Spacer(modifier = Modifier.height(bottomSpacer))
        }
        DoubleTapLikeOverlay(visible = likeAnimVisible) { likeAnimVisible = false }
    }
}

@Composable
private fun AlbumCover(track: Track, maxSize: Dp = 320.dp, isCurrent: Boolean = true, isPlayingThisCard: Boolean = false) {
    val enterScale by animateFloatAsState(targetValue = if (isCurrent) 1f else 0.94f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "albumEnterScale")
    val breath = if (isPlayingThisCard) {
        val infinite = rememberInfiniteTransition(label = "albumBreath")
        infinite.animateFloat(initialValue = 1f, targetValue = 1.015f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "albumBreath")
    } else null
    Box(modifier = Modifier.widthIn(max = maxSize).fillMaxWidth().aspectRatio(1f).graphicsLayer {
        val s = enterScale * (breath?.value ?: 1f)
        scaleX = s; scaleY = s
    }.clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
        val pic = track.pic
        if (!pic.isNullOrBlank()) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(pic).size(540).build(), contentDescription = track.displayTitle, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(text = "♪", color = Color.White.copy(alpha = 0.5f), fontSize = 72.sp)
        }
    }
}

@Composable
private fun InfoChip(text: String, onClick: () -> Unit = {}) {
    Text(text = text, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 3.dp))
}

private fun effectsModeLabel(mode: Int): String = when (mode) {
    AudioEffectsController.MODE_NORMAL -> "原声"
    AudioEffectsController.MODE_STUDIO -> "录音室"
    AudioEffectsController.MODE_SPATIAL -> "空间环绕"
    AudioEffectsController.MODE_PANORAMIC -> "全景声"
    AudioEffectsController.MODE_MASTER -> "母带"
    AudioEffectsController.MODE_MASTER2 -> "母带2"
    AudioEffectsController.MODE_LIVE -> "现场"
    AudioEffectsController.MODE_HEADPHONE_A3 -> "A3耳机"
    AudioEffectsController.MODE_POP -> "流行"
    AudioEffectsController.MODE_ROCK -> "摇滚"
    AudioEffectsController.MODE_JAZZ -> "爵士"
    AudioEffectsController.MODE_CLASSICAL -> "古典"
    AudioEffectsController.MODE_BASS -> "重低音"
    AudioEffectsController.MODE_VOCAL -> "人声"
    AudioEffectsController.MODE_ELECTRONIC -> "电子"
    AudioEffectsController.MODE_HIPHOP -> "Hip-Hop"
    else -> "原声"
}

private val SeekClickEasing = CubicBezierEasing(0.65f, 0f, 0.9f, 1f)

private fun fmt(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${String.format("%02d", s)}"
}

@Composable
private fun ThinProgressBar(p: PlayerProgress, track: Track, vm: MusicViewModel) {
    val seekScope = rememberCoroutineScope()
    val seekAnim = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(1) }
    var dragRatio by remember { mutableStateOf<Float?>(null) }
    val realDurationMs = p.durationMs
    val effDurMs = p.effectiveDurationMs
    val playingRatio = if (effDurMs > 0) (p.positionMs.toFloat() / effDurMs).coerceIn(0f, 1f) else 0f
    val ratio = when {
        dragRatio != null -> dragRatio!!
        seekAnim.isRunning -> seekAnim.value
        else -> playingRatio
    }.coerceIn(0f, 1f)
    val dragging = dragRatio != null
    val nodeTotalMs = if (realDurationMs > 0) realDurationMs else track.duration

    Box(modifier = Modifier.fillMaxWidth().height(28.dp).onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
        .pointerInput(realDurationMs) {
            if (realDurationMs <= 0) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                val slop = viewConfiguration.touchSlop
                val startX = down.position.x
                var lastX = startX
                var moved = false
                val pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = event.changes.firstOrNull { it.id == pointerId } ?: break
                    ch.consume()
                    if (!ch.pressed) break
                    lastX = ch.position.x
                    if (!moved && kotlin.math.abs(lastX - startX) > slop) moved = true
                    if (moved) dragRatio = (lastX / widthPx.toFloat()).coerceIn(0f, 1f)
                }
                val target = (lastX / widthPx.toFloat()).coerceIn(0f, 1f)
                if (moved) {
                    vm.seekTo(target)
                    dragRatio = null
                } else {
                    val curEff = p.effectiveDurationMs
                    val from = if (curEff > 0) (p.positionMs.toFloat() / curEff).coerceIn(0f, 1f) else 0f
                    vm.seekTo(target)
                    seekScope.launch { seekAnim.snapTo(from); seekAnim.animateTo(target, animationSpec = tween(520, easing = SeekClickEasing)) }
                }
            }
        }, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
            if (realDurationMs > 0 && p.bufferedMs > 0) {
                val bufRatio = (p.bufferedMs.toFloat() / realDurationMs).coerceIn(0f, 1f)
                Box(modifier = Modifier.fillMaxWidth(bufRatio).fillMaxHeight().background(Color.White.copy(alpha = 0.35f)))
            }
            Box(modifier = Modifier.fillMaxWidth(ratio).fillMaxHeight().background(Color.White))
        }
        val nodes = track.nodes
        if (!nodes.isNullOrEmpty() && nodeTotalMs > 0) {
            Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                val cy = size.height / 2f
                val ring = 1.dp.toPx()
                nodes.forEach { node ->
                    if (node.start <= 0L || node.start >= nodeTotalMs) return@forEach
                    if (node.opacity <= 0f) return@forEach
                    val x = node.start.toFloat() / nodeTotalMs * size.width
                    val r = if (node.type == 2 || node.type == 3) 3.5.dp.toPx() else 2.5.dp.toPx()
                    val a = node.opacity.coerceIn(0f, 1f)
                    drawCircle(Color.Black.copy(alpha = 0.5f * a), r + ring, Offset(x, cy))
                    drawCircle(Color.White.copy(alpha = a), r, Offset(x, cy))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (ratio > 0f) Spacer(modifier = Modifier.weight(ratio))
            Box(modifier = Modifier.size(if (dragging) 13.dp else 9.dp).clip(CircleShape).background(Color.White))
            if (ratio < 1f) Spacer(modifier = Modifier.weight(1f - ratio))
        }
    }
}

@Composable
private fun LyricStrip(track: Track, positionMs: Long, wide: Boolean = false) {
    val lf = if (wide) 26.sp else 18.sp
    val original = remember(track.lyricsKrc) { parseKrc(track.lyricsKrc) }
    val translation = remember(track.lyricsLrc) { parseLrc(track.lyricsLrc) }
    val fallback = remember(track.lyrics) { parseLrc(track.lyrics) }
    val pos = positionMs - 150L
    val topLine: LyricLine?
    val topNextStart: Long
    val bottomText: String
    val bottomFollowsTop: Boolean
    if (original.isNotEmpty() && translation.isNotEmpty()) {
        val i = lyricIndexAt(original, pos)
        topLine = original.getOrNull(i)
        topNextStart = original.getOrNull(i + 1)?.timeMs ?: ((topLine?.timeMs ?: 0L) + 4000L)
        bottomText = translation.getOrNull(i)?.text.orEmpty()
        bottomFollowsTop = true
    } else {
        val mono = when {
            original.isNotEmpty() -> original
            translation.isNotEmpty() -> translation
            else -> fallback
        }
        if (mono.isEmpty()) { LyricPlaceholder(lf); return }
        val i = lyricIndexAt(mono, pos)
        topLine = mono.getOrNull(i)
        topNextStart = mono.getOrNull(i + 1)?.timeMs ?: ((topLine?.timeMs ?: 0L) + 4000L)
        bottomText = mono.getOrNull(i + 1)?.text.orEmpty()
        bottomFollowsTop = false
    }
    if (topLine == null) { LyricPlaceholder(lf); return }
    val topText = topLine.text
    if (topText.isBlank() && bottomText.isBlank()) { LyricPlaceholder(lf); return }
    val topSung = sungChars(topLine, topNextStart, pos).coerceIn(0, topText.length)
    val topFraction = if (topText.isEmpty()) 0f else topSung.toFloat() / topText.length
    val bottomSung = if (bottomFollowsTop) (topFraction * bottomText.length).toInt() else 0
    AnimatedContent(targetState = LyricSwitchState(lineKey = topLine.timeMs, topText = topText, topSung = topSung, bottomText = bottomText, bottomSung = bottomSung, bottomFollowsTop = bottomFollowsTop), contentKey = { it.lineKey }, transitionSpec = {
        (slideInVertically(animationSpec = lyricTween()) { full -> full } + fadeIn(animationSpec = lyricTween())).togetherWith(slideOutVertically(animationSpec = lyricTween()) { full -> -full } + fadeOut(animationSpec = lyricTween()))
    }, label = "lyricSwitch") { st ->
        Column {
            KaraokeLine(st.topText, st.topSung, lf)
            if (st.bottomText.isNotBlank()) {
                Spacer(modifier = Modifier.height(if (wide) 12.dp else 8.dp))
                if (st.bottomFollowsTop) KaraokeLine(st.bottomText, st.bottomSung, lf) else
                    Text(text = st.bottomText, color = Color.White.copy(alpha = 0.3f), fontSize = lf, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val LyricSwitchEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
private fun <T> lyricTween() = tween<T>(durationMillis = 300, easing = LyricSwitchEasing)

private data class LyricSwitchState(val lineKey: Long, val topText: String, val topSung: Int, val bottomText: String, val bottomSung: Int, val bottomFollowsTop: Boolean)

@Composable
private fun LyricPlaceholder(fontSize: androidx.compose.ui.unit.TextUnit = 18.sp) {
    Text(text = "目前没有歌词，享受音乐...。", color = Color.White.copy(alpha = 0.3f), fontSize = fontSize, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun KaraokeLine(text: String, sung: Int, fontSize: androidx.compose.ui.unit.TextUnit = 18.sp) {
    Text(text = buildAnnotatedString {
        append(text)
        val s = sung.coerceIn(0, text.length)
        if (s > 0) addStyle(SpanStyle(color = Color.White), 0, s)
        if (s < text.length) addStyle(SpanStyle(color = Color.White.copy(alpha = 0.3f)), s, text.length)
    }, fontSize = fontSize, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private class LyricLine(val timeMs: Long, val text: String, val charEndMs: LongArray? = null)

private val LRC_TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val KRC_LINE = Regex("""^\[(\d+),\d+](.*)$""")
private val KRC_TAG = Regex("""<[^>]*>""")

private fun parseLrc(raw: String?): List<LyricLine> {
    if (raw.isNullOrBlank()) return emptyList()
    val out = ArrayList<LyricLine>()
    for (line in raw.split('\n')) {
        val stamps = LRC_TIME.findAll(line).toList()
        if (stamps.isEmpty()) continue
        val text = line.substring(stamps.last().range.last + 1).trim()
        if (text.isEmpty()) continue
        for (m in stamps) {
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val f = m.groupValues[3]
            val frac = when (f.length) { 1 -> (f.toLongOrNull() ?: 0L) * 100; 2 -> (f.toLongOrNull() ?: 0L) * 10; 3 -> f.toLongOrNull() ?: 0L; else -> 0L }
            out.add(LyricLine(min * 60_000 + sec * 1_000 + frac, text))
        }
    }
    return out.sortedBy { it.timeMs }
}

private fun parseKrc(raw: String?): List<LyricLine> {
    if (raw.isNullOrBlank()) return emptyList()
    val out = ArrayList<LyricLine>()
    for (line in raw.split('\n')) {
        val m = KRC_LINE.find(line.trim()) ?: continue
        val start = m.groupValues[1].toLongOrNull() ?: continue
        val body = m.groupValues[2]
        val tags = KRC_TAG.findAll(body).toList()
        if (tags.isEmpty()) { val t = body.trim(); if (t.isNotEmpty()) out.add(LyricLine(start, t)); continue }
        val sb = StringBuilder()
        val charEnds = ArrayList<Long>()
        for ((ti, tag) in tags.withIndex()) {
            val nums = body.substring(tag.range.first + 1, tag.range.last).split(',')
            val off = nums.getOrNull(0)?.toLongOrNull() ?: 0L
            val dur = nums.getOrNull(1)?.toLongOrNull() ?: 0L
            val wordStart = tag.range.last + 1
            val wordEnd = if (ti + 1 < tags.size) tags[ti + 1].range.first else body.length
            val word = body.substring(wordStart, wordEnd)
            if (word.isEmpty()) continue
            val perChar = (dur / word.length).coerceAtLeast(1L)
            var acc = off
            for (ci in word.indices) { acc = if (ci == word.length - 1) off + dur else acc + perChar; sb.append(word[ci]); charEnds.add(acc) }
        }
        val rawText = sb.toString()
        val text = rawText.trim()
        if (text.isEmpty()) continue
        var arr: LongArray? = null
        if (charEnds.size == rawText.length) {
            var ts = 0; while (ts < rawText.length && rawText[ts].isWhitespace()) ts++
            var te = rawText.length; while (te > ts && rawText[te - 1].isWhitespace()) te--
            if (te - ts == text.length) {
                arr = LongArray(text.length) { charEnds[ts + it] }
                for (i in 1 until arr.size) if (arr[i] < arr[i - 1]) arr[i] = arr[i - 1]
            }
        }
        out.add(LyricLine(start, text, arr))
    }
    return out.sortedBy { it.timeMs }
}

private fun lyricIndexAt(lines: List<LyricLine>, posMs: Long): Int {
    var idx = 0
    for (i in lines.indices) { if (lines[i].timeMs <= posMs) idx = i else break }
    return idx
}

private fun sungChars(line: LyricLine, nextStartMs: Long, posMs: Long): Int {
    val n = line.text.length
    if (n == 0) return 0
    val rel = posMs - line.timeMs
    if (rel <= 0L) return 0
    val ce = line.charEndMs
    if (ce != null && ce.size == n) {
        var idx = 0
        while (idx < n) { val charStart = if (idx == 0) 0L else ce[idx - 1]; if (charStart > rel) break; idx++ }
        return idx
    }
    val dur = (nextStartMs - line.timeMs).coerceAtLeast(1L)
    return ((rel.toFloat() / dur) * n).toInt().coerceIn(0, n)
}

@Composable
private fun DoubleTapLikeOverlay(visible: Boolean, onEnd: () -> Unit) {
    if (!visible) return
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/playing_collect_big_double_collect.json"))
    val progress by animateLottieCompositionAsState(composition = composition, isPlaying = true, restartOnPlay = true, iterations = 1)
    LaunchedEffect(progress) { if (composition != null && progress >= 0.99f) onEnd() }
    LottieAnimation(composition = composition, progress = { progress }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}
