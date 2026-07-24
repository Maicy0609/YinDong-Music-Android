package com.yindong.music.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.List
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.yindong.music.data.LocalStorage
import com.yindong.music.data.api.MusicApiConfig
import com.yindong.music.data.model.LyricLine
import com.yindong.music.data.model.Playlist
import com.yindong.music.data.model.Song
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.core.view.WindowCompat
import com.yindong.music.ui.theme.AccentGray
import com.yindong.music.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.palette.graphics.Palette
import kotlin.math.cos
import kotlin.math.sin

// 底栏自动隐藏共享状态 —— 在 PlayerScreen/QishuiPlayerScreen 根容器提供，
// UnifiedBottomBar / AutoHideBottomIconRow 消费。
internal val LocalBottomBarVisible = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(true) }
internal val LocalBottomBarShowToken = compositionLocalOf<MutableState<Int>> { mutableStateOf(0) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onOpenAiAudioEffect: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        onDispose {
            val window = activity?.window
            if (window != null && activity != null && !activity.isFinishing && !activity.isDestroyed) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    val song = viewModel.currentSong
    if (song == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("加载中...", color = Color.White.copy(alpha = 0.7f))
            }
        }
        return
    }
    var showLyrics by remember { mutableStateOf(false) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var showQMenu by remember { mutableStateOf(false) }
    val showBottomSheet = remember { mutableStateOf(false) }
    val showNewPlaylist = remember { mutableStateOf(false) }
    val showQueue = remember { mutableStateOf(false) }
    val showTimerSheet = remember { mutableStateOf(false) }
    val showEqualizer = remember { mutableStateOf(false) }
    val showLyricsSheet = remember { mutableStateOf(false) }
    val showAddToPlaylist = remember { mutableStateOf(false) }
    val showDownloadQuality = remember { mutableStateOf(false) }
    val showStylePicker = remember { mutableStateOf(false) }
    LaunchedEffect(showEqualizer.value) {
        if (showEqualizer.value) {
            showEqualizer.value = false
            onOpenAiAudioEffect()
        }
    }

    // ── 下载音质选择弹窗 ──
    if (showDownloadQuality.value && song != null) {
        val dlTextColor = Color(0xFF2D2D2D)
        val dlSecondaryColor = Color(0xFF8E8E93)

        // 解析当前歌曲所属平台 source key（wy/tx/kw/kg）
        val songSource = remember(song) {
            song.lxSourceKey.ifBlank {
                when (song.platform) {
                    "QQ音乐", "QQ", "qq" -> "tx"
                    "网易云", "网易云2", "网易云音乐", "netease" -> "wy"
                    "酷我音乐", "酷我", "kuwo" -> "kw"
                    "酷狗音乐", "酷狗", "kugou" -> "kg"
                    else -> ""
                }
            }
        }
        // 该平台检测到的音质列表（含文件大小），由插件音质检测流程填充
        val detectedDlQualities = remember(songSource, viewModel.activePluginSupportedQualitiesBySource) {
            if (songSource.isNotBlank()) viewModel.getSupportedQualitiesForSource(songSource) else emptyList()
        }
        // 检测中标志（用于显示加载提示）
        val isDlDetecting = viewModel.isQualityDetecting

        ModalBottomSheet(
            onDismissRequest = { showDownloadQuality.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    "选择下载音质",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = dlTextColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    "${song.title} - ${song.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = dlSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                // 显示当前歌曲平台名 + 检测状态
                val platformName = when (songSource) {
                    "wy" -> "网易云音乐"; "tx" -> "QQ音乐"; "kw" -> "酷我音乐"; "kg" -> "酷狗音乐"; else -> "未知平台"
                }
                Text(
                    if (isDlDetecting) "$platformName · 正在检测音质…"
                    else if (detectedDlQualities.isEmpty()) "$platformName · 暂无可用音质"
                    else "$platformName · 共 ${detectedDlQualities.size} 档可选",
                    style = MaterialTheme.typography.bodySmall,
                    color = dlSecondaryColor,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                if (detectedDlQualities.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isDlDetecting) "正在检测该平台支持音质，请稍候…"
                            else "当前插件未检测到该平台支持的音质，请先在插件管理中启用插件",
                            style = MaterialTheme.typography.bodySmall,
                            color = dlSecondaryColor,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // 音质列表：每个音质旁展示文件大小
                    detectedDlQualities.forEachIndexed { index, dq ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Transparent)
                                .clickable {
                                    showDownloadQuality.value = false
                                    // 反查 LX key 对应的 Quality 枚举
                                    val q = when (dq.lxKey) {
                                        "128k" -> MusicApiConfig.Quality.STANDARD
                                        "320k" -> MusicApiConfig.Quality.EXHIGH
                                        "flac" -> MusicApiConfig.Quality.LOSSLESS
                                        "flac24bit", "hires" -> MusicApiConfig.Quality.HIRES
                                        "master" -> MusicApiConfig.Quality.JYMASTER
                                        "atmos" -> MusicApiConfig.Quality.SKY
                                        "atmos_plus" -> MusicApiConfig.Quality.JYEFFECT
                                        else -> MusicApiConfig.Quality.EXHIGH
                                    }
                                    viewModel.downloadSongWithQuality(song, q)
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when (dq.lxKey) {
                                            "atmos", "atmos_plus" -> Color(0xFFFFF4E6)
                                            "flac24bit", "hires" -> Color(0xFFFFF8E1)
                                            "flac" -> Color(0xFFF0F0F0)
                                            "320k" -> Color(0xFFFCE8F0)
                                            "master" -> Color(0xFFFFECD2)
                                            else -> Color(0xFFF5F5F5)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when (dq.lxKey) {
                                    "atmos", "atmos_plus" -> Text("◈", color = Color(0xFFFF9500), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                                    "flac24bit", "hires" -> Text("HR", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9500), fontWeight = FontWeight.Bold)
                                    "flac" -> Text("FLAC", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF2D55), fontWeight = FontWeight.Bold)
                                    "320k" -> Text("320", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF2D55), fontWeight = FontWeight.Bold)
                                    "master" -> Text("母", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9500), fontWeight = FontWeight.Bold)
                                    else -> Text("128", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8E8E93), fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(Modifier.weight(1f)) {
                                Text(dq.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = dlTextColor)
                                Text(dq.description, style = MaterialTheme.typography.bodySmall, color = dlSecondaryColor, maxLines = 1)
                            }

                            // 文件大小信息（右侧）
                            if (dq.fileSizeText.isNotBlank()) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF2F2F7))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        dq.fileSizeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dlSecondaryColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }

                            Icon(Icons.Default.Download, null, tint = dlSecondaryColor.copy(0.5f), modifier = Modifier.size(22.dp))
                        }

                        if (index < detectedDlQualities.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 78.dp), color = Color(0xFFEEEEEE))
                        }
                    }
                }

                // 批量下载按钮
                HorizontalDivider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showDownloadQuality.value = false; viewModel.batchDownloadCurrentPlaylist() }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE3F2FD)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.List, null, tint = Color(0xFF1976D2), modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("批量下载当前列表", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = dlTextColor)
                        Text("下载播放列表中所有未下载歌曲", style = MaterialTheme.typography.bodySmall, color = dlSecondaryColor)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = dlSecondaryColor.copy(0.5f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    LaunchedEffect(viewModel.isPlaying) { while (viewModel.isPlaying) { rotation += 0.3f; if (rotation >= 360f) rotation -= 360f; delay(16L) } }
    LaunchedEffect(viewModel.isPlaying, viewModel.lyrics) { if (viewModel.isPlaying || viewModel.lyrics.isNotEmpty()) showLyrics = true }

    // ── 添加到歌单弹窗 ──
    if (showAddToPlaylist.value) {
        ModalBottomSheet(
            onDismissRequest = { showAddToPlaylist.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E1E1E),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text("添加到歌单", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                
                if (viewModel.userPlaylists.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无歌单，请先创建歌单", color = Color.White.copy(0.6f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(viewModel.userPlaylists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.addSongToPlaylist(playlist.id, song)
                                        showAddToPlaylist.value = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 歌单封面
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF333333)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (playlist.coverUrl.isNotEmpty()) {
                                        AsyncImage(
                                            playlist.coverUrl,
                                            null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(24.dp))
                                    }
                                }
                                
                                Spacer(Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        playlist.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${playlist.songCount}首",
                                        color = Color.White.copy(0.5f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                
                                // 检查歌曲是否已在歌单中
                                val isInPlaylist = playlist.songs.any { it.platformId == song.platformId && it.platform == song.platform }
                                if (isInPlaylist) {
                                    Text("已添加", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Icon(Icons.Default.Add, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // 创建新歌单按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF333333))
                        .clickable {
                            showAddToPlaylist.value = false
                            showNewPlaylist.value = true
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("创建新歌单", color = Color.White, fontWeight = FontWeight.Medium)
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ── 播放队列弹窗 ──
    if (showQueue.value) {
        var selectedTab by remember { mutableStateOf("当前播放") }
        ModalBottomSheet(
            onDismissRequest = { showQueue.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E1E1E),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("播放队列", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                
                // 标签切换
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("当前播放", "历史播放").forEach { tab ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selectedTab == tab) Color.White else Color(0xFF333333))
                                .clickable { selectedTab = tab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tab, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                
                // 操作按钮（仅在当前播放标签显示）
                if (selectedTab == "当前播放") {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF333333))
                                .clickable { 
                                    val queue = viewModel.getQueue()
                                    if (queue.isNotEmpty()) {
                                        queue.forEach { song ->
                                            viewModel.toggleLike(song)
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("添加到收藏", color = Color.White.copy(0.9f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF333333))
                                .clickable { 
                                    val queue = viewModel.getQueue()
                                    if (queue.isNotEmpty()) {
                                        showAddToPlaylist.value = true
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("添加到歌单", color = Color.White.copy(0.9f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // 内容区域 - 使用 LazyColumn 显示所有歌曲
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (selectedTab == "当前播放") {
                        val queue = viewModel.getQueue()
                        if (queue.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("播放队列为空", color = Color.White.copy(0.7f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(queue, key = { _, it -> it.platformId }, contentType = { _, _ -> "queue_song" }) { index, songItem ->
                                    val isCurrent = viewModel.currentSong?.platformId == songItem.platformId && 
                                                   viewModel.currentSong?.platform == songItem.platform
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrent) Color(0xFF333333) else Color.Transparent)
                                            .clickable {
                                                viewModel.playSong(songItem)
                                                showQueue.value = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 序号
                                        Text(
                                            "${index + 1}",
                                            modifier = Modifier.width(36.dp),
                                            color = if (isCurrent) Color.White else Color.White.copy(0.5f),
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 16.sp
                                        )
                                        
                                        // 歌曲信息
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Text(
                                                songItem.title,
                                                color = if (isCurrent) Color.White else Color.White.copy(0.85f),
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                songItem.artist,
                                                color = Color.White.copy(0.5f),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        // 删除按钮
                                        IconButton(
                                            onClick = { viewModel.removeFromQueue(songItem) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                
                                // 清空按钮
                                item {
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFF333333))
                                            .clickable { viewModel.clearQueue() }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("清空队列", color = Color.White.copy(0.7f), fontSize = 14.sp)
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }
                        }
                    } else {
                        // 历史播放
                        val history = viewModel.playHistory
                        if (history.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("暂无历史播放", color = Color.White.copy(0.7f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(history, key = { _, it -> it.platformId }, contentType = { _, _ -> "history_song" }) { index, songItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.playSong(songItem)
                                                showQueue.value = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 序号
                                        Text(
                                            "${index + 1}",
                                            modifier = Modifier.width(36.dp),
                                            color = Color.White.copy(0.5f),
                                            fontSize = 16.sp
                                        )
                                        
                                        // 歌曲信息
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Text(
                                                songItem.title,
                                                color = Color.White.copy(0.85f),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                songItem.artist,
                                                color = Color.White.copy(0.5f),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        // 删除按钮
                                        IconButton(
                                            onClick = { viewModel.removeFromHistory(songItem) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                
                                item {
                                    Spacer(Modifier.height(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // ── 创建歌单对话框 ──
    if (showNewPlaylist.value) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylist.value = false },
            title = { Text("创建歌单", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("歌单名称", color = Color.White.copy(0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(0.8f)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (playlistName.isNotEmpty() && song != null) {
                        val newPlaylist = Playlist(
                            id = System.currentTimeMillis(),
                            name = playlistName,
                            coverUrl = song.coverUrl,
                            songCount = 1,
                            songs = listOf(song)
                        )
                        viewModel.createPlaylist(playlistName)
                        showNewPlaylist.value = false
                    }
                }) {
                    Text("创建", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylist.value = false }) {
                    Text("取消", color = Color.White.copy(0.7f))
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
    
    // 唱针角度动画
    val dur = if (viewModel.totalDuration > 0) viewModel.totalDuration else song.duration

    val coverColors = rememberCoverColors(song.coverUrl)
    // 动画过渡提取色，切歌时平滑过渡
    val animBase     by animateColorAsState(coverColors.base,      tween(800), label = "aBase")
    val animDominant by animateColorAsState(coverColors.dominant,  tween(800), label = "aDom")
    val animMuted    by animateColorAsState(coverColors.muted,     tween(800), label = "aMut")
    val animSecondary by animateColorAsState(coverColors.secondary, tween(800), label = "aSec")
    val baseBg = if (showLyrics) animBase else Color(0xFF1A1A1A)

    if (showLyricsSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showLyricsSheet.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = baseBg,
        ) {
            Box(Modifier.fillMaxWidth().height(520.dp)) {
                if (viewModel.lyrics.isNotEmpty()) {
                    LyricsViewImmersive(viewModel.lyrics, viewModel.currentLyricIndex, baseBg, coverUrl = song.coverUrl, currentLyricColor = if (viewModel.lyricCurrentColor != 0) Color(viewModel.lyricCurrentColor) else null, normalLyricColor = if (viewModel.lyricNormalColor != 0) Color(viewModel.lyricNormalColor) else null, lyricFontSize = viewModel.lyricFontSize, currentTimeMs = viewModel.currentPlaybackTimeMs)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无歌词", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    // ── 更多选项弹窗（分享等功能） ──
    if (showBottomSheet.value) {
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E1E1E),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text("更多选项", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))

                // 分享按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showBottomSheet.value = false
                            val shareText = buildString {
                                append("我正在听「${song.title}」\n")
                                append("歌手：${song.artist}\n")
                                if (song.album.isNotEmpty()) {
                                    append("专辑：${song.album}\n")
                                }
                                append("来源：${song.platform}\n")
                                append("\n在线听歌：")
                                append(buildShareUrl(song))
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                putExtra(Intent.EXTRA_SUBJECT, "我正在听「${song.title}」")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享到"))
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Share,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("分享歌曲", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

                // 复制链接按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showBottomSheet.value = false
                            val shareUrl = buildShareUrl(song)
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("分享链接", shareUrl)
                            clipboardManager.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("复制分享链接", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 状态栏透明，让渐变背景延伸到状态栏区域；离开播放器时自动恢复
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context as? android.app.Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }

        window?.statusBarColor = android.graphics.Color.TRANSPARENT
        controller?.isAppearanceLightStatusBars = true

        onDispose {
            if (window != null && activity != null && !activity.isFinishing && !activity.isDestroyed) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                val ctrl = WindowCompat.getInsetsController(window, window.decorView)
                ctrl.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // ═══ 动态渐变背景 (基于封面多色提取) ═══
    val bgCoverColors = rememberCoverColors(song.coverUrl)

    val gradientColors = remember(bgCoverColors) {
        fun Color.toPastel(): Color {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(
                android.graphics.Color.argb(
                    255,
                    (this.red * 255).toInt(),
                    (this.green * 255).toInt(),
                    (this.blue * 255).toInt()
                ), hsv
            )
            hsv[1] = (hsv[1] * 0.18f).coerceIn(0.04f, 0.20f)
            hsv[2] = (hsv[2] * 1.20f + 0.30f).coerceIn(0.92f, 0.98f)
            return Color(AndroidColor.HSVToColor(hsv))
        }

        listOf(
            bgCoverColors.lightAccent.toPastel(),
            bgCoverColors.muted.toPastel(),
            bgCoverColors.dominant.toPastel(),
            bgCoverColors.secondary.toPastel()
        )
    }

    // 底栏自动隐藏状态 —— 提升到根容器，全屏检测点击，供标准模式和模糊背景模式共用
    val bottomBarVisible = remember { mutableStateOf(true) }
    val bottomBarToken = remember { mutableStateOf(0) }
    LaunchedEffect(bottomBarToken.value, viewModel.playerStyle) {
        bottomBarVisible.value = true
        delay(3000)
        bottomBarVisible.value = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
            .pointerInput(bottomBarVisible.value, viewModel.playerStyle) {
                // 汽水风(QDUAN) 自己管理控件显隐，不在这里拦截点击
                if (!bottomBarVisible.value && viewModel.playerStyle != MusicViewModel.PlayerStyle.QDUAN) {
                    awaitEachGesture {
                        awaitPointerEvent(PointerEventPass.Initial)
                        bottomBarToken.value++
                    }
                }
            }
    ) {
        CompositionLocalProvider(
            LocalBottomBarVisible provides bottomBarVisible,
            LocalBottomBarShowToken provides bottomBarToken,
        ) {
            // qduan / 模糊背景 / 沉浸式封面 风格自己管理状态栏/导航栏内边距，需要占满全屏，不能套在带 insets 的 Column 里
            if (viewModel.playerStyle == MusicViewModel.PlayerStyle.QDUAN || viewModel.playerStyle == MusicViewModel.PlayerStyle.BLUR_BG || viewModel.playerStyle == MusicViewModel.PlayerStyle.IMMERSIVE_COVER) {
                when (viewModel.playerStyle) {
                    MusicViewModel.PlayerStyle.QDUAN -> QishuiPlayerScreen(song, viewModel, onBack, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality)
                    MusicViewModel.PlayerStyle.BLUR_BG -> BlurBgPlayer(song, viewModel, onBack, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality, showTimerSheet)
                    MusicViewModel.PlayerStyle.IMMERSIVE_COVER -> ImmersiveCoverPlayer(song, viewModel, onBack, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality, showTimerSheet)
                    else -> ModernPlayer(song, viewModel, onBack, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality, showTimerSheet)
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // 根据播放器样式显示不同UI
                    when (viewModel.playerStyle) {
                        else -> {
                            // ═══ 标准模式 (默认) ═══
                            ModernPlayer(song, viewModel, onBack, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality, showTimerSheet)
                        }
                    }
                }
            }
        }
    }
}

// ═══ 标准模式播放器 ═══
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ModernPlayer(
    song: Song,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
    showTimerSheet: MutableState<Boolean>,
) {
    val showQualitySheet = remember { mutableStateOf(false) }
    val isImmersiveMode = remember { mutableStateOf(false) }
    val showStylePicker = remember { mutableStateOf(false) }

    val bgCoverColors = rememberCoverColors(song.coverUrl)
    val gradientColors = remember(bgCoverColors) {
        fun Color.toPastel(): Color {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(
                android.graphics.Color.argb(
                    255,
                    (this.red * 255).toInt(),
                    (this.green * 255).toInt(),
                    (this.blue * 255).toInt()
                ), hsv
            )
            hsv[1] = (hsv[1] * 0.18f).coerceIn(0.04f, 0.20f)
            hsv[2] = (hsv[2] * 1.20f + 0.30f).coerceIn(0.92f, 0.98f)
            return Color(AndroidColor.HSVToColor(hsv))
        }

        listOf(
            bgCoverColors.lightAccent.toPastel(),
            bgCoverColors.muted.toPastel(),
            bgCoverColors.dominant.toPastel(),
            bgCoverColors.secondary.toPastel()
        )
    }

    // 基于封面提取一个适合的前景强调色，用于进度条和播放控制按钮
    val accentColor = remember(bgCoverColors) {
        val base = bgCoverColors.vibrant
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(
            android.graphics.Color.argb(
                255,
                (base.red * 255).toInt(),
                (base.green * 255).toInt(),
                (base.blue * 255).toInt()
            ), hsv
        )
        hsv[1] = hsv[1].coerceIn(0.20f, 0.55f)
        hsv[2] = hsv[2].coerceIn(0.25f, 0.42f)
        Color(AndroidColor.HSVToColor(hsv))
    }
    // 文字、图标与底部功能栏统一使用封面强调色
    val textColor = accentColor
    val secondaryTextColor = accentColor.copy(alpha = 0.65f)

    // 始终显示HorizontalPager（包含主界面），即使歌词还在加载中
    // 这样可以确保切换歌曲时界面立即响应，不需要等待歌词加载完成
    if (viewModel.lyrics.isNotEmpty() || viewModel.isLoadingLyrics || true) {
        val pagerState = rememberPagerState(
            initialPage = 1,
            pageCount = { 3 }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    PlayerOptionsPage(song, viewModel, textColor, secondaryTextColor, isImmersiveMode)
                }
                1 -> {
                    if (isImmersiveMode.value) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = gradientColors,
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                                .padding(top = 24.dp, bottom = 16.dp)
                        ) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White),
                                ) {
                                    if (song.coverUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = song.coverUrl,
                                            contentDescription = song.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(Icons.Default.MusicNote, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(80.dp).align(Alignment.Center))
                                    }

                                    if (viewModel.isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.align(Alignment.Center).size(40.dp),
                                            color = textColor.copy(alpha = 0.6f),
                                            strokeWidth = 2.5.dp,
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(56.dp)) {
                                    Icon(Icons.Default.SkipPrevious, null, tint = accentColor, modifier = Modifier.size(36.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.togglePlay() },
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    if (viewModel.isBuffering) {
                                        CircularProgressIndicator(Modifier.size(34.dp), color = accentColor, strokeWidth = 2.5.dp)
                                    } else {
                                        Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = accentColor, modifier = Modifier.size(40.dp))
                                    }
                                }
                                IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(56.dp)) {
                                    Icon(Icons.Default.SkipNext, null, tint = accentColor, modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                    } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        if (isLandscape) {
                            // ═══ 横屏布局：封面左侧 + 标题/歌词右侧 ═══
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF5F5F5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (song.coverUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = song.coverUrl,
                                            contentDescription = song.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(Icons.Default.MusicNote, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(80.dp))
                                    }
                                    if (viewModel.isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                            color = textColor.copy(alpha = 0.6f),
                                            strokeWidth = 2.5.dp,
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f).padding(start = 32.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                song.title,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                song.artist,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = secondaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                                            Icon(Icons.Default.Close, null, tint = secondaryTextColor, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (viewModel.lyrics.isNotEmpty()) {
                                        val idx = viewModel.currentLyricIndex
                                        val line = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx] else null
                                        if (line != null && line.words.isNotEmpty()) {
                                            val annotatedText = buildAnnotatedString {
                                                line.words.forEach { word ->
                                                    val isWordActive = viewModel.currentPlaybackTimeMs >= word.startTimeMs
                                                    withStyle(SpanStyle(color = if (isWordActive) textColor else textColor.copy(alpha = 0.4f))) {
                                                        append(word.text)
                                                    }
                                                }
                                            }
                                            Text(
                                                annotatedText,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        } else if (line != null) {
                                            Text(
                                                line.text,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = textColor,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val nxtLine = if (idx + 1 in viewModel.lyrics.indices) viewModel.lyrics[idx + 1] else null
                                        Text(
                                            nxtLine?.text ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else {
                                        Text(
                                            song.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            song.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        } else {
                        Spacer(modifier = Modifier.height(40.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 关闭按钮
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, null, tint = secondaryTextColor, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF5F5F5)),
            ) {
                if (song.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(80.dp).align(Alignment.Center))
                }

                if (viewModel.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                        color = textColor.copy(alpha = 0.6f),
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (viewModel.lyrics.isNotEmpty()) {
                val idx = viewModel.currentLyricIndex
                val line = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx] else null
                android.util.Log.d("PlayerScreen", "当前歌词: idx=$idx, line?.words数量=${line?.words?.size}, currentPlaybackTimeMs=${viewModel.currentPlaybackTimeMs}")
                if (line != null && line.words.isNotEmpty()) {
                    val annotatedText = buildAnnotatedString {
                        line.words.forEach { word ->
                            val isWordActive = viewModel.currentPlaybackTimeMs >= word.startTimeMs
                            withStyle(SpanStyle(color = if (isWordActive) textColor else textColor.copy(alpha = 0.4f))) {
                                append(word.text)
                            }
                        }
                    }
                    Text(
                        annotatedText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (line != null) {
                    Text(
                        line.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val nxtLine = if (idx + 1 in viewModel.lyrics.indices) viewModel.lyrics[idx + 1] else null
                Text(
                    nxtLine?.text ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
                        }

        // ═══ 统一底部状态功能栏（与其他播放器样式共享同一组件） ═══
        UnifiedBottomBar(
            song = song,
            viewModel = viewModel,
            dur = dur,
            showQueue = showQueue,
            showEqualizer = showEqualizer,
            showAddToPlaylist = showAddToPlaylist,
            showDownloadQuality = showDownloadQuality,
            showStylePicker = showStylePicker,
            tintColor = accentColor,
            playButtonFgColor = accentColor,
            playButtonHasBackground = false,
            accentColor = accentColor,
            qualityText = viewModel.selectedQuality.label,
            onQualityClick = { showQualitySheet.value = true },
        )

        if (showStylePicker.value) {
            PlayerStylePickerDialog(
                currentStyle = viewModel.playerStyle,
                onStyleSelected = { viewModel.changePlayerStyle(it) },
                onDismiss = { showStylePicker.value = false },
            )
        }

        if (showQualitySheet.value) {
            // 解析当前歌曲所属平台 source key（wy/tx/kw/kg）
            val songSource = remember(song) {
                song.lxSourceKey.ifBlank {
                    when (song.platform) {
                        "QQ音乐", "QQ", "qq" -> "tx"
                        "网易云", "网易云2", "网易云音乐", "netease" -> "wy"
                        "酷我音乐", "酷我", "kuwo" -> "kw"
                        "酷狗音乐", "酷狗", "kugou" -> "kg"
                        else -> ""
                    }
                }
            }
            val detectedQualities = remember(songSource, viewModel.activePluginSupportedQualitiesBySource) {
                if (songSource.isNotBlank()) viewModel.getSupportedQualitiesForSource(songSource) else emptyList()
            }
            val isQDetecting = viewModel.isQualityDetecting

            ModalBottomSheet(
                onDismissRequest = { showQualitySheet.value = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 28.dp)
                ) {
                    Text(
                        "当前歌曲音质",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    // 显示当前歌曲平台名 + 检测状态
                    val platformName = when (songSource) {
                        "wy" -> "网易云音乐"; "tx" -> "QQ音乐"; "kw" -> "酷我音乐"; "kg" -> "酷狗音乐"; else -> "未知平台"
                    }
                    Text(
                        if (isQDetecting) "$platformName · 正在检测音质…"
                        else if (detectedQualities.isEmpty()) "$platformName · 暂无可用音质"
                        else "$platformName · 共 ${detectedQualities.size} 档可选",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    if (detectedQualities.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isQDetecting) "正在检测该平台支持音质，请稍候…"
                                else "当前插件未检测到该平台支持的音质，请先在插件管理中启用插件",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        detectedQualities.forEachIndexed { index, dq ->
                            // 反查 LX key 对应的当前 selectedQuality
                            val q = when (dq.lxKey) {
                                "128k" -> MusicApiConfig.Quality.STANDARD
                                "320k" -> MusicApiConfig.Quality.EXHIGH
                                "flac" -> MusicApiConfig.Quality.LOSSLESS
                                "flac24bit", "hires" -> MusicApiConfig.Quality.HIRES
                                "master" -> MusicApiConfig.Quality.JYMASTER
                                "atmos" -> MusicApiConfig.Quality.SKY
                                "atmos_plus" -> MusicApiConfig.Quality.JYEFFECT
                                else -> MusicApiConfig.Quality.EXHIGH
                            }
                            val isSelected = viewModel.selectedQuality == q
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0xFFFFF8F0) else Color.Transparent)
                                    .clickable {
                                        viewModel.setQuality(q)
                                        showQualitySheet.value = false
                                    }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when (dq.lxKey) {
                                                "atmos", "atmos_plus" -> Color(0xFFFFF4E6)
                                                "flac24bit", "hires" -> Color(0xFFFFF8E1)
                                                "flac" -> Color(0xFFF0F0F0)
                                                "320k" -> Color(0xFFFCE8F0)
                                                "master" -> Color(0xFFFFECD2)
                                                else -> Color(0xFFF5F5F5)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (dq.lxKey) {
                                        "atmos", "atmos_plus" -> Text("◈", color = Color(0xFFFF9500), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                                        "flac24bit", "hires" -> Text("HR", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9500), fontWeight = FontWeight.Bold)
                                        "flac" -> Text("FLAC", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF2D55), fontWeight = FontWeight.Bold)
                                        "320k" -> Text("320", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF2D55), fontWeight = FontWeight.Bold)
                                        "master" -> Text("母", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9500), fontWeight = FontWeight.Bold)
                                        else -> Text("128", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8E8E93), fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        dq.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = textColor
                                    )
                                    Text(
                                        dq.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryTextColor,
                                        maxLines = 1
                                    )
                                }

                                // 文件大小信息（右侧）
                                if (dq.fileSizeText.isNotBlank()) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF2F2F7))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            dq.fileSizeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryTextColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF3B30)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "✓",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }

                            if (index < detectedQualities.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 78.dp),
                                    color = Color(0xFFEEEEEE)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
                    }
                }
                2 -> {
                    PlayerLyricsPage(song, viewModel, textColor, secondaryTextColor, isImmersiveMode, gradientColors)
                }
            }
        }
    }
}
// ═══ 音质选择弹窗（公共组件，供多个播放器复用，按当前歌曲平台动态展示） ═══
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualitySelectionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: MusicViewModel,
    textColor: Color = Color(0xFF2D2D2D),
    secondaryTextColor: Color = Color(0xFF8E8E93),
) {
    if (visible) {
        // 解析当前歌曲所属平台 source key（wy/tx/kw/kg）
        val song = viewModel.currentSong
        val songSource = remember(song) {
            song?.lxSourceKey?.ifBlank {
                when (song?.platform) {
                    "QQ音乐", "QQ", "qq" -> "tx"
                    "网易云", "网易云2", "网易云音乐", "netease" -> "wy"
                    "酷我音乐", "酷我", "kuwo" -> "kw"
                    "酷狗音乐", "酷狗", "kugou" -> "kg"
                    else -> ""
                }
            } ?: ""
        }
        val detectedQualities = remember(songSource, viewModel.activePluginSupportedQualitiesBySource) {
            if (songSource.isNotBlank()) viewModel.getSupportedQualitiesForSource(songSource) else emptyList()
        }
        val isQDetecting = viewModel.isQualityDetecting

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    "当前歌曲音质",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                val platformName = when (songSource) {
                    "wy" -> "网易云音乐"; "tx" -> "QQ音乐"; "kw" -> "酷我音乐"; "kg" -> "酷狗音乐"; else -> "未知平台"
                }
                Text(
                    if (isQDetecting) "$platformName · 正在检测音质…"
                    else if (detectedQualities.isEmpty()) "$platformName · 暂无可用音质"
                    else "$platformName · 共 ${detectedQualities.size} 档可选",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                if (detectedQualities.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isQDetecting) "正在检测该平台支持音质，请稍候…"
                            else "当前插件未检测到该平台支持的音质，请先在插件管理中启用插件",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    detectedQualities.forEachIndexed { index, dq ->
                        val q = when (dq.lxKey) {
                            "128k" -> MusicApiConfig.Quality.STANDARD
                            "320k" -> MusicApiConfig.Quality.EXHIGH
                            "flac" -> MusicApiConfig.Quality.LOSSLESS
                            "flac24bit", "hires" -> MusicApiConfig.Quality.HIRES
                            "master" -> MusicApiConfig.Quality.JYMASTER
                            "atmos" -> MusicApiConfig.Quality.SKY
                            "atmos_plus" -> MusicApiConfig.Quality.JYEFFECT
                            else -> MusicApiConfig.Quality.EXHIGH
                        }
                        val isSelected = viewModel.selectedQuality == q
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Color(0xFFFFF8F0) else Color.Transparent)
                                .clickable {
                                    viewModel.setQuality(q)
                                    onDismiss()
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when (dq.lxKey) {
                                            "atmos", "atmos_plus" -> Color(0xFFFFF4E6)
                                            "flac24bit", "hires" -> Color(0xFFFFF8E1)
                                            "flac" -> Color(0xFFF0F0F0)
                                            "320k" -> Color(0xFFFCE8F0)
                                            "master" -> Color(0xFFFFECD2)
                                            else -> Color(0xFFF5F5F5)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when (dq.lxKey) {
                                    "atmos", "atmos_plus" -> Text("◈", color = Color(0xFFFF9500), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                                    "flac24bit", "hires" -> Text("HR", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9500), fontWeight = FontWeight.Bold)
                                    "flac" -> Text("FLAC", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF2D55), fontWeight = FontWeight.Bold)
                                    "320k" -> Text("320", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF2D55), fontWeight = FontWeight.Bold)
                                    "master" -> Text("母", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9500), fontWeight = FontWeight.Bold)
                                    else -> Text("128", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8E8E93), fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    dq.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = textColor
                                )
                                Text(
                                    dq.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor,
                                    maxLines = 1
                                )
                            }

                            // 文件大小信息（右侧）
                            if (dq.fileSizeText.isNotBlank()) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF2F2F7))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        dq.fileSizeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryTextColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3B30)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "✓",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(26.dp))
                            }
                        }

                        if (index < detectedQualities.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 78.dp),
                                color = Color(0xFFEEEEEE)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══ 模糊背景模式播放器 ═══
// 严格按三层结构实现：底层高斯模糊背景 + 中层清晰封面 + 顶层 UI 控件
// 底部功能栏复用标准模式 UnifiedBottomBar 辅助按钮行
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlurBgPlayer(
    song: Song,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
    showTimerSheet: MutableState<Boolean>,
) {
    val showQualitySheet = remember { mutableStateOf(false) }
    val showStylePicker = remember { mutableStateOf(false) }

    // 歌曲切换时 300ms 淡入淡出过渡动画
    val coverAlpha = remember { Animatable(1f) }
    LaunchedEffect(song.platformId) {
        coverAlpha.animateTo(0f, animationSpec = tween(150))
        coverAlpha.animateTo(1f, animationSpec = tween(150))
    }

    // 基于封面提取强调色，让文字、图标与播放控件随封面变化（与标准模式一致）
    val bgCoverColors = rememberCoverColors(song.coverUrl)
    val accentColor = remember(bgCoverColors) {
        val base = bgCoverColors.vibrant
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(
            android.graphics.Color.argb(
                255,
                (base.red * 255).toInt(),
                (base.green * 255).toInt(),
                (base.blue * 255).toInt()
            ), hsv
        )
        hsv[1] = hsv[1].coerceIn(0.20f, 0.55f)
        hsv[2] = hsv[2].coerceIn(0.25f, 0.42f)
        Color(AndroidColor.HSVToColor(hsv))
    }
    val textColor = accentColor
    val labelColor = accentColor

    // 判断是否显示 Hi-Res 标识
    val showHiRes = viewModel.selectedQuality == MusicApiConfig.Quality.HIRES ||
        viewModel.selectedQuality == MusicApiConfig.Quality.LOSSLESS ||
        viewModel.selectedQuality == MusicApiConfig.Quality.JYMASTER ||
        viewModel.selectedQuality == MusicApiConfig.Quality.SKY

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val totalScreenHeight = maxHeight + statusBarHeight
        // 上半屏占位高度：屏幕的 54%，将播放控件整体向上移
        val coverHeight = totalScreenHeight * 0.54f

        // ═══ 背景与控件两层堆叠 ═══
        Box(Modifier.fillMaxSize()) {
            // ── 底层：全屏高斯模糊封面（参考图 2/3/4 下半屏效果）──
            // 高斯核权重卷积 G(x,y) = 1/(2πσ²) * e^(-(x²+y²)/(2σ²))
            if (song.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(coverAlpha.value)
                        .blur(80.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A2E))
                        .alpha(coverAlpha.value)
                )
            }

            // ── 中层：全屏清晰专辑封面，底部用 DstOut 渐变擦掉，露出底层模糊 ──
            if (song.coverUrl.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = coverAlpha.value
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent, // 顶部清晰封面保留
                                        0.25f to Color.Transparent,
                                        0.42f to Color.Black,       // 底部清晰封面擦除，露出模糊
                                        1.0f to Color.Black,
                                    ),
                                    startY = 0f,
                                    endY = size.height
                                ),
                                blendMode = BlendMode.DstOut
                            )
                        }
                ) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // ── 顶层：全部控件 ──
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .alpha(coverAlpha.value)
            ) {
                // 顶部导航栏（仅按钮自身避开系统栏）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowDownward, null, tint = textColor, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { /* 分享功能预留 */ }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Share, null, tint = textColor, modifier = Modifier.size(24.dp))
                    }
                }

                // 上半屏占位（高度 = coverHeight - statusBarHeight，将控件下沉到参考图 2/3/4 的下半屏位置）
                Spacer(Modifier.height(coverHeight - statusBarHeight))

                // ── 下半屏控件区域：标题、歌手、进度条、播放控制、底部功能栏 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                ) {
                    // 歌曲名 + 歌手名 + Hi-Res 标签
                    Text(
                        song.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            song.artist,
                            fontSize = 14.sp,
                            color = labelColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (showHiRes) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2D7D52))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Hi-Res",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // 收藏按钮
                        val isFav = viewModel.isFavorite(song)
                        IconButton(onClick = { viewModel.toggleLike(song) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null,
                                tint = if (isFav) Color(0xFFFF3B30) else textColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        // 更多按钮
                        IconButton(onClick = { showAddToPlaylist.value = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreHoriz, null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 进度条
                    var isDragging by remember { mutableStateOf(false) }
                    var dragValue by remember { mutableFloatStateOf(0f) }

                    LaunchedEffect(Unit) {
                        snapshotFlow { viewModel.progress }
                            .collect { if (!isDragging) dragValue = it }
                    }

                    SeekBar(
                        progress = dragValue,
                        onSeekStart = { isDragging = true; viewModel.isSeeking = true },
                        onSeek = { dragValue = it },
                        onSeekFinished = { viewModel.seekTo(it); isDragging = false; viewModel.isSeeking = false },
                        activeColor = accentColor,
                        inactiveColor = accentColor.copy(alpha = 0.20f),
                        thumbColor = accentColor,
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fmt((dragValue * dur).toLong()),
                            fontSize = 12.sp,
                            color = textColor,
                        )
                        Text(
                            viewModel.selectedQuality.label,
                            fontSize = 12.sp,
                            color = textColor,
                        )
                        Text(
                            fmt(dur),
                            fontSize = 12.sp,
                            color = textColor,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // 主播放控制栏（上一首/播放/下一首使用封面强调色）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.togglePlayMode() }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                when (viewModel.playMode) {
                                    MusicViewModel.PlayMode.LOOP -> Icons.Default.Repeat
                                    MusicViewModel.PlayMode.SINGLE -> Icons.Default.RepeatOne
                                    MusicViewModel.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                                },
                                null,
                                tint = textColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipPrevious, null, tint = accentColor, modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = { viewModel.togglePlay() }, modifier = Modifier.size(48.dp)) {
                            if (viewModel.isBuffering) {
                                CircularProgressIndicator(Modifier.size(28.dp), color = accentColor, strokeWidth = 2.5.dp)
                            } else {
                                Icon(
                                    if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = accentColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipNext, null, tint = accentColor, modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = { showQueue.value = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.List, null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 底部功能栏
                    BlurBgStandardBottomBar(
                        song = song,
                        viewModel = viewModel,
                        showQueue = showQueue,
                        showEqualizer = showEqualizer,
                        showAddToPlaylist = showAddToPlaylist,
                        showDownloadQuality = showDownloadQuality,
                        showStylePicker = showStylePicker,
                        tintColor = accentColor,
                    )
                }

                // ── 对话框 ──
                if (showStylePicker.value) {
                    PlayerStylePickerDialog(
                        currentStyle = viewModel.playerStyle,
                        onStyleSelected = { viewModel.changePlayerStyle(it) },
                        onDismiss = { showStylePicker.value = false },
                    )
                }

                QualitySelectionSheet(
                    visible = showQualitySheet.value,
                    onDismiss = { showQualitySheet.value = false },
                    viewModel = viewModel,
                    textColor = Color(0xFF2D2D2D),
                    secondaryTextColor = Color(0xFF8E8E93),
                )
            }
        }
    }
}

// ═══ 模糊背景模式专用底部功能栏（复用标准模式 UnifiedBottomBar 辅助按钮行） ═══
@Composable
private fun BlurBgStandardBottomBar(
    song: Song,
    viewModel: MusicViewModel,
    showQueue: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
    showStylePicker: MutableState<Boolean>,
    tintColor: Color = Color.White,
) {
    // 状态由根容器通过 CompositionLocal 提供，3 秒自动隐藏，全屏点击重新显示
    val controlsVisible = LocalBottomBarVisible.current.value
    val bottomAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "blurBgBottomBarAlpha",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = bottomAlpha },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 收藏
            val isFav = viewModel.isFavorite(song)
            IconButton(onClick = { viewModel.toggleLike(song) }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFav) tintColor else tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
            }
            // 添加歌单
            IconButton(onClick = { showAddToPlaylist.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(Icons.Default.PlaylistAdd, null, tint = tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
            }
            // 循环模式
            IconButton(onClick = { viewModel.togglePlayMode() }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(
                    when (viewModel.playMode) {
                        MusicViewModel.PlayMode.LOOP -> Icons.Default.Repeat
                        MusicViewModel.PlayMode.SINGLE -> Icons.Default.RepeatOne
                        MusicViewModel.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                    },
                    null, tint = tintColor.copy(0.85f), modifier = Modifier.size(22.dp),
                )
            }
            // 词
            IconButton(onClick = { viewModel.toggleFloatingLyrics() }, Modifier.size(40.dp), enabled = controlsVisible) {
                Text("词", color = if (viewModel.isFloatingLyricsEnabled) tintColor else tintColor.copy(0.8f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            // 播放列表
            IconButton(onClick = { showQueue.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(Icons.Default.List, null, tint = tintColor.copy(0.85f), modifier = Modifier.size(22.dp))
            }
            // 均衡器
            IconButton(onClick = { showEqualizer.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(Icons.Default.Equalizer, null, tint = tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
            }
            // 下载
            IconButton(onClick = { showDownloadQuality.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                val dlState = viewModel.downloadState
                val isThisDownloading = dlState is MusicViewModel.DownloadState.Downloading && dlState.songId == song.platformId
                val isDownloaded = viewModel.isDownloaded(song)
                if (isThisDownloading) {
                    CircularProgressIndicator(
                        progress = { (dlState as MusicViewModel.DownloadState.Downloading).progress },
                        modifier = Modifier.size(22.dp),
                        color = tintColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Download, null, tint = if (isDownloaded) tintColor else tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
                }
            }
            // 播放器样式切换
            IconButton(onClick = { showStylePicker.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(Icons.Default.SwapHoriz, null, tint = tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ═══ 沉浸式封面播放器 ═══
// 全屏封面背景 + 底部渐变模糊遮罩 + 歌词 + 深色控件，底部功能栏复用模糊背景模式的自动隐藏栏
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveCoverPlayer(
    song: Song,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
    showTimerSheet: MutableState<Boolean>,
) {
    val showStylePicker = remember { mutableStateOf(false) }

    // 歌曲切换时 300ms 淡入淡出过渡动画
    val coverAlpha = remember { Animatable(1f) }
    LaunchedEffect(song.platformId) {
        coverAlpha.animateTo(0f, animationSpec = tween(150))
        coverAlpha.animateTo(1f, animationSpec = tween(150))
    }

    // 基于封面提取强调色，让文字、图标与播放控件随封面变化（与标准模式一致）
    val bgCoverColors = rememberCoverColors(song.coverUrl)
    val accentColor = remember(bgCoverColors) {
        val base = bgCoverColors.vibrant
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(
            android.graphics.Color.argb(
                255,
                (base.red * 255).toInt(),
                (base.green * 255).toInt(),
                (base.blue * 255).toInt()
            ), hsv
        )
        hsv[1] = hsv[1].coerceIn(0.20f, 0.55f)
        hsv[2] = hsv[2].coerceIn(0.25f, 0.42f)
        Color(AndroidColor.HSVToColor(hsv))
    }
    val textColor = accentColor
    val secondaryTextColor = accentColor.copy(alpha = 0.65f)
    val labelColor = accentColor

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(Modifier.fillMaxSize()) {
            // ── 底层：全屏清晰封面 ──
            if (song.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(coverAlpha.value),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A2E))
                        .alpha(coverAlpha.value)
                )
            }

            // ── 中层：底部模糊遮罩（参考图片下半屏毛玻璃过渡效果）──
            if (song.coverUrl.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = coverAlpha.value
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent, // 顶部清晰封面保留
                                        0.42f to Color.Transparent,
                                        0.62f to Color.White,       // 底部清晰封面渐隐，露出模糊
                                        1.0f to Color.White,
                                    ),
                                    startY = 0f,
                                    endY = size.height
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(60.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // ── 可读性遮罩：底部叠加半透明白色，让深色控件更清晰 ──
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.70f to Color.White.copy(alpha = 0.55f),
                                1.0f to Color.White.copy(alpha = 0.72f),
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
                    .alpha(coverAlpha.value)
            )

            // ── 顶层：全部控件 ──
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .alpha(coverAlpha.value)
            ) {
                // 顶部状态栏 + 返回/分享
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowDownward, null, tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { /* 分享功能预留 */ }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Share, null, tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(Modifier.weight(1f))

                // 歌词区域（当前行 + 下一行预览）
                val idx = viewModel.currentLyricIndex
                val curLyric = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx].text else null
                val nxtLyric = if (idx + 1 in viewModel.lyrics.indices) viewModel.lyrics[idx + 1].text else null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp)
                        .heightIn(min = 72.dp, max = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (curLyric != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                curLyric,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = labelColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            if (!nxtLyric.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    nxtLyric,
                                    fontSize = 14.sp,
                                    color = secondaryTextColor.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        Text(
                            "暂无歌词",
                            fontSize = 16.sp,
                            color = secondaryTextColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 歌曲名 + 歌手名 + 收藏按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            song.artist,
                            fontSize = 14.sp,
                            color = secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val isFav = viewModel.isFavorite(song)
                    IconButton(onClick = { viewModel.toggleLike(song) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null,
                            tint = if (isFav) Color(0xFFFF3B30) else textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 进度条
                var isDragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableFloatStateOf(0f) }

                LaunchedEffect(Unit) {
                    snapshotFlow { viewModel.progress }
                        .collect { if (!isDragging) dragValue = it }
                }

                SeekBar(
                    progress = dragValue,
                    onSeekStart = { isDragging = true; viewModel.isSeeking = true },
                    onSeek = { dragValue = it },
                    onSeekFinished = { viewModel.seekTo(it); isDragging = false; viewModel.isSeeking = false },
                    activeColor = accentColor,
                    inactiveColor = accentColor.copy(alpha = 0.20f),
                    thumbColor = accentColor,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fmt((dragValue * dur).toLong()), fontSize = 12.sp, color = secondaryTextColor)
                    Text(viewModel.selectedQuality.label, fontSize = 12.sp, color = secondaryTextColor)
                    Text(fmt(dur), fontSize = 12.sp, color = secondaryTextColor)
                }

                Spacer(Modifier.height(16.dp))

                // 主播放控制栏（上一首 / 播放暂停 / 下一首使用封面强调色）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = accentColor, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { viewModel.togglePlay() }, modifier = Modifier.size(64.dp)) {
                        if (viewModel.isBuffering) {
                            CircularProgressIndicator(Modifier.size(36.dp), color = accentColor, strokeWidth = 2.5.dp)
                        } else {
                            Icon(
                                if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null,
                                tint = accentColor,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = accentColor, modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 底部功能栏（复用模糊背景的自动隐藏栏）
                BlurBgStandardBottomBar(
                    song = song,
                    viewModel = viewModel,
                    showQueue = showQueue,
                    showEqualizer = showEqualizer,
                    showAddToPlaylist = showAddToPlaylist,
                    showDownloadQuality = showDownloadQuality,
                    showStylePicker = showStylePicker,
                    tintColor = accentColor,
                )
            }

            // ── 对话框 ──
            if (showStylePicker.value) {
                PlayerStylePickerDialog(
                    currentStyle = viewModel.playerStyle,
                    onStyleSelected = { viewModel.changePlayerStyle(it) },
                    onDismiss = { showStylePicker.value = false },
                )
            }
        }
    }
}

// ═══ 完整歌词界面（点击封面跳转）═══
@Composable
private fun PlayerLyricsPage(
    song: Song,
    viewModel: MusicViewModel,
    textColor: Color,
    secondaryTextColor: Color,
    isImmersiveMode: MutableState<Boolean> = remember { mutableStateOf(false) },
    gradientColors: List<Color> = listOf(Color.White, Color.White, Color.White, Color.White)
) {
    var isControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100L)
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastInteractionTime) > 1000L && isControlsVisible) {
                isControlsVisible = false
            }
        }
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (isControlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "lyricsControlsAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (isControlsVisible) {
                    isControlsVisible = false
                } else {
                    isControlsVisible = true
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶部标题栏 - 自动隐藏
            Box(modifier = Modifier.alpha(controlsAlpha)) {
                if (!isImmersiveMode.value) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 歌词区域 - 始终显示
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LyricsViewImmersive(
                    lyrics = viewModel.lyrics,
                    currentIndex = viewModel.currentLyricIndex,
                    baseBg = Color.White.copy(alpha = 0.1f),
                    coverUrl = song.coverUrl,
                    showBackground = false,
                    topPadding = 8.dp,
                    bottomPadding = 32.dp,
                    currentLyricColor = textColor.copy(alpha = 0.9f),
                    normalLyricColor = secondaryTextColor.copy(alpha = 0.4f),
                    lyricFontSize = 22,
                    currentTimeMs = viewModel.currentPlaybackTimeMs
                )

                if (viewModel.isLoadingLyrics) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = textColor.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("歌词加载中...", style = MaterialTheme.typography.bodyMedium, color = secondaryTextColor.copy(alpha = 0.6f))
                    }
                } else if (viewModel.lyrics.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = secondaryTextColor.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("暂无歌词", style = MaterialTheme.typography.bodyLarge, color = secondaryTextColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
// ═══ 播放器选项页面（右滑显示）═══
@Composable
private fun PlayerOptionsPage(
    song: Song,
    viewModel: MusicViewModel,
    textColor: Color,
    secondaryTextColor: Color,
    isImmersiveMode: MutableState<Boolean>
) {
    var isLandscapeMode by remember { mutableStateOf(false) }

    if (isLandscapeMode) {
        LandscapePlayer(song, viewModel) {
            isLandscapeMode = false
        }
    } else {
        Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 网格布局选项（2列）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionGridItem(
                    icon = { Icon(Icons.Default.Favorite, null, tint = textColor, modifier = Modifier.size(20.dp)) },
                    title = "播放界面保持屏",
                    subtitle = "",
                    backgroundColor = Color.White.copy(alpha = 0.6f),
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )

                OptionGridItem(
                    icon = { Icon(Icons.Default.Waves, null, tint = textColor, modifier = Modifier.size(20.dp)) },
                    title = "沉浸模式",
                    subtitle = "",
                    backgroundColor = Color.White.copy(alpha = 0.6f),
                    textColor = textColor,
                    onClick = { isImmersiveMode.value = !isImmersiveMode.value },
                    modifier = Modifier.weight(1f)
                )
            }

            // 第二行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionGridItem(
                    icon = { Icon(Icons.Default.MusicNote, null, tint = textColor, modifier = Modifier.size(20.dp)) },
                    title = "Original Sound",
                    subtitle = "",
                    backgroundColor = Color.White.copy(alpha = 0.6f),
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )

                OptionGridItem(
                    icon = { Icon(Icons.Default.Cast, null, tint = textColor, modifier = Modifier.size(20.dp)) },
                    title = "DLNA (beta)",
                    subtitle = "",
                    backgroundColor = Color.White.copy(alpha = 0.6f),
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )
            }

            // 第三行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionGridItem(
                    icon = { Icon(Icons.Default.MoreHoriz, null, tint = textColor, modifier = Modifier.size(20.dp)) },
                    title = "横屏模式",
                    subtitle = "",
                    backgroundColor = Color.White.copy(alpha = 0.6f),
                    onClick = { isLandscapeMode = true },
                    modifier = Modifier.weight(1f)
                )

                OptionGridItem(
                    icon = { Icon(Icons.Default.KeyboardArrowDown, null, tint = secondaryTextColor.copy(0.3f), modifier = Modifier.size(20.dp)) },
                    title = "",
                    subtitle = "",
                    backgroundColor = Color.Transparent,
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    }
}

@Composable
private fun OptionGridItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String = "",
    backgroundColor: Color = Color.White.copy(alpha = 0.6f),
    textColor: Color = Color(0xFF2D2D2D),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (backgroundColor == Color(0xFF007AFF)) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
// ═══ 横屏模式播放器 ═══
@Composable
private fun LandscapePlayer(
    song: Song,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    DisposableEffect(Unit) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            // 退出横屏时恢复用户全局方向偏好，而非强制竖屏
            val orientation = com.yindong.music.data.LocalStorage.loadScreenOrientation()
            activity?.requestedOrientation = when (orientation) {
                "PORTRAIT" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "LANDSCAPE" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "REVERSE_LANDSCAPE" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val window = activity?.window
            if (window != null && activity != null && !activity.isFinishing && !activity.isDestroyed) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    val currentLyricText = if (viewModel.lyrics.isNotEmpty() && viewModel.currentLyricIndex in viewModel.lyrics.indices) {
        viewModel.lyrics[viewModel.currentLyricIndex].text
    } else ""

    // 车载歌词设置：字体大小与背景透明度（响应式，由 ViewModel 统一管理，设置面板调整后实时生效）
    val carLyricFontSize = viewModel.carLyricFontSize
    val carLyricBgOpacity = viewModel.carLyricBgOpacity
    // 车载蓝牙歌词总开关 + 蓝牙连接状态
    val carBtLyricsEnabled = viewModel.carBtLyricsEnabled
    val btConnected = viewModel.isHeadsetConnected && viewModel.headsetType == com.yindong.music.data.BluetoothHeadsetManager.HeadsetType.BLUETOOTH
    val btName = viewModel.connectedHeadsetName
    var showCarLyricSettings by remember { mutableStateOf(false) }

    // 封面智能吸色 - 简洁背景（参考第一张图片）
    val coverColors = rememberCoverColors(song.coverUrl)
    val baseBgColor = remember(coverColors.base) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(
            android.graphics.Color.argb(
                255,
                (coverColors.dominant.red * 255).toInt(),
                (coverColors.dominant.green * 255).toInt(),
                (coverColors.dominant.blue * 255).toInt()
            ), hsv
        )
        hsv[1] = hsv[1].coerceIn(0.30f, 0.55f)
        hsv[2] = hsv[2].coerceIn(0.10f, 0.20f)
        Color(AndroidColor.HSVToColor(hsv))
    }
    // 根据用户设置的背景透明度混合背景色
    val animBgColor by animateColorAsState(
        baseBgColor.copy(alpha = carLyricBgOpacity / 100f),
        tween(800), label = "landBg"
    )

    var isControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100L)
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastInteractionTime) > 1000L && isControlsVisible) {
                isControlsVisible = false
            }
        }
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (isControlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "landscapeControlsAlpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(animBgColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (isControlsVisible) {
                    isControlsVisible = false
                } else {
                    isControlsVisible = true
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // 车载蓝牙歌词：常驻顶部开关栏（不随控件自动隐藏，便于行车时盲操）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "车载蓝牙歌词",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Bluetooth, null,
                    tint = if (btConnected) Color(0xFF34C759) else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (btConnected) (btName?.takeIf { it.isNotBlank() } ?: "蓝牙已连接") else "蓝牙未连接",
                    color = if (btConnected) Color(0xFF34C759) else Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "歌词",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium
                )
                Switch(
                    checked = carBtLyricsEnabled,
                    onCheckedChange = { viewModel.toggleCarBtLyrics() }
                )
                IconButton(onClick = { showCarLyricSettings = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Settings, "车载歌词设置", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                }
            }

            if (showCarLyricSettings) {
                CarLyricSettingsDialog(viewModel = viewModel) { showCarLyricSettings = false }
            }

            // 顶部栏 - 自动隐藏
            Box(modifier = Modifier.alpha(controlsAlpha)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(26.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { }) {
                        Icon(Icons.Default.FavoriteBorder, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Share, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(onClick = { viewModel.togglePlayerStyle() }) {
                        Icon(Icons.Default.SwapHoriz, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 车载歌词：多行滚动显示，当前行高亮放大
            val lyricsList = viewModel.lyrics
            val currentIdx = viewModel.currentLyricIndex
            val lyricListState = rememberLazyListState()

            // 歌词自动滚动到当前行
            LaunchedEffect(currentIdx, lyricsList.size) {
                if (lyricsList.isNotEmpty() && currentIdx in lyricsList.indices) {
                    // 滚动到当前行，偏移2行让当前行居中靠上
                    val target = (currentIdx - 1).coerceAtLeast(0)
                    lyricListState.animateScrollToItem(target)
                }
            }

            if (!carBtLyricsEnabled) {
                // 未开启车载蓝牙歌词：居中提示与开关
                Spacer(modifier = Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "开启车载蓝牙歌词",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "前台横屏大字显示 · 后台自动切换悬浮窗",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Switch(
                        checked = carBtLyricsEnabled,
                        onCheckedChange = { viewModel.toggleCarBtLyrics() }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            } else if (lyricsList.isNotEmpty()) {
                LazyColumn(
                    state = lyricListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(lyricsList, key = { _, it -> it.timeMs }, contentType = { _, _ -> "lyric" }) { index, line ->
                        val isCurrent = index == currentIdx
                        val isPast = index < currentIdx
                        val textColor = when {
                            isCurrent -> Color.White
                            isPast -> Color.White.copy(alpha = 0.35f)
                            else -> Color.White.copy(alpha = 0.55f)
                        }
                        val scale = if (isCurrent) 1f else 0.85f
                        Text(
                            line.text.ifBlank { "♪" },
                            fontSize = (carLyricFontSize * scale).sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                }
            } else {
                // 无歌词时显示歌曲信息
                Text(
                    "${song.title} - ${song.artist}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            // 底部控件 - 自动隐藏
            Box(modifier = Modifier.alpha(controlsAlpha)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    IconButton(
                        onClick = { viewModel.togglePlay() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        if (viewModel.isBuffering) {
                            CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.5.dp)
                        } else {
                            Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(onClick = { }) {
                    Icon(Icons.Default.FavoriteBorder, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(24.dp))
                }
                }
            }
        }
    }
}

// ═══ 沉浸式歌词播放器 ═══
@Composable
private fun ImmersiveLyricsPlayer(
    song: Song,
    viewModel: MusicViewModel,
    baseBg: Color,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
) {
    var isControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100L)
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastInteractionTime) > 1000L && isControlsVisible) {
                isControlsVisible = false
            }
        }
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (isControlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "controlsAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (isControlsVisible) {
                    isControlsVisible = false
                } else {
                    isControlsVisible = true
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            // 极简顶部（透明背景）- 第二张图片内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(controlsAlpha)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(song.title, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f), maxLines = 1)
                    }
                    QualityBadge(viewModel)
                    PlayerDownloadButton(viewModel, song)
                    Spacer(Modifier.width(8.dp))
                }
            }

            // 歌词区域（使用外层统一毛玻璃背景）
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LyricsViewImmersive(
                    lyrics = viewModel.lyrics,
                    currentIndex = viewModel.currentLyricIndex,
                    baseBg = baseBg,
                    coverUrl = song.coverUrl,
                    topPadding = 8.dp,
                    bottomPadding = 8.dp,
                    currentLyricColor = if (viewModel.lyricCurrentColor != 0) Color(viewModel.lyricCurrentColor) else null,
                    normalLyricColor = if (viewModel.lyricNormalColor != 0) Color(viewModel.lyricNormalColor) else null,
                    lyricFontSize = viewModel.lyricFontSize,
                    showBackground = false,
                    currentTimeMs = viewModel.currentPlaybackTimeMs
                )
            }

            // 底部控制 - 第三张图片内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(controlsAlpha)
            ) {
                ImmersiveBottomControls(song, viewModel, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality)
            }
        }
    }
}

@Composable
private fun ImmersiveBottomControls(
    song: Song,
    viewModel: MusicViewModel,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
) {
    val showStylePicker = remember { mutableStateOf(false) }
    UnifiedBottomBar(
        song = song,
        viewModel = viewModel,
        dur = dur,
        showQueue = showQueue,
        showEqualizer = showEqualizer,
        showAddToPlaylist = showAddToPlaylist,
        showDownloadQuality = showDownloadQuality,
        showStylePicker = showStylePicker,
    )
    if (showStylePicker.value) {
        PlayerStylePickerDialog(
            currentStyle = viewModel.playerStyle,
            onStyleSelected = { viewModel.changePlayerStyle(it) },
            onDismiss = { showStylePicker.value = false },
        )
    }
}

// ═══ 封面大图模式 ═══
@Composable
private fun CoverArtPlayer(
    song: Song,
    viewModel: MusicViewModel,
    baseBg: Color,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
) {
    var showCoverLyrics by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 顶部栏
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
            }
            QualityBadge(viewModel)
            PlayerDownloadButton(viewModel, song)
        }

        if (showCoverLyrics && viewModel.lyrics.isNotEmpty()) {
            // ── 全屏歌词视图（点击回到封面） ──
            Box(Modifier.weight(1f).fillMaxWidth().clickable { showCoverLyrics = false }) {
                LyricsViewImmersive(
                    lyrics = viewModel.lyrics,
                    currentIndex = viewModel.currentLyricIndex,
                    baseBg = Color.Transparent,
                    coverUrl = song.coverUrl,
                    topPadding = 8.dp,
                    bottomPadding = 8.dp,
                    currentLyricColor = if (viewModel.lyricCurrentColor != 0) Color(viewModel.lyricCurrentColor) else null,
                    normalLyricColor = if (viewModel.lyricNormalColor != 0) Color(viewModel.lyricNormalColor) else null,
                    lyricFontSize = viewModel.lyricFontSize,
                    currentTimeMs = viewModel.currentPlaybackTimeMs
                )
            }
        } else {
            // ── 大封面（点击进入歌词） ──
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .clickable { if (viewModel.lyrics.isNotEmpty()) showCoverLyrics = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF333333)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                song.coverUrl,
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(100.dp))
                        }
                        if (viewModel.isBuffering) {
                            CircularProgressIndicator(Modifier.size(40.dp), color = Color.White.copy(0.7f), strokeWidth = 2.dp)
                        }
                    }
                    // 内嵌歌词
                    if (viewModel.lyrics.isNotEmpty()) {
                        val idx = viewModel.currentLyricIndex
                        Spacer(Modifier.height(12.dp))
                        val cur = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx].text else ""
                        Text(cur, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                        val curT = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx].ttext else ""
                        if (curT.isNotEmpty()) {
                            Text(curT, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.55f), textAlign = TextAlign.Center, maxLines = 1)
                        }
                        val nxt = if (idx + 1 in viewModel.lyrics.indices) viewModel.lyrics[idx + 1].text else ""
                        if (nxt.isNotEmpty()) {
                            Text(nxt, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.4f), textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 底部完整控件
        StandardBottomControls(song, viewModel, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality)
    }
}

// ═══ 极简模式 ═══
@Composable
private fun MinimalPlayer(
    song: Song,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
) {
    var showMinimalLyrics by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 顶部栏
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.weight(1f))
            QualityBadge(viewModel)
            PlayerDownloadButton(viewModel, song)
        }

        if (showMinimalLyrics && viewModel.lyrics.isNotEmpty()) {
            // ── 全屏歌词视图（点击回到封面） ──
            Box(Modifier.weight(1f).fillMaxWidth().clickable { showMinimalLyrics = false }) {
                LyricsViewImmersive(
                    lyrics = viewModel.lyrics,
                    currentIndex = viewModel.currentLyricIndex,
                    baseBg = Color.Transparent,
                    coverUrl = song.coverUrl,
                    topPadding = 8.dp,
                    bottomPadding = 8.dp,
                    currentLyricColor = if (viewModel.lyricCurrentColor != 0) Color(viewModel.lyricCurrentColor) else null,
                    normalLyricColor = if (viewModel.lyricNormalColor != 0) Color(viewModel.lyricNormalColor) else null,
                    lyricFontSize = viewModel.lyricFontSize,
                    currentTimeMs = viewModel.currentPlaybackTimeMs
                )
            }
        } else {
            // ── 封面 + 歌曲信息（点击进入歌词） ──
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { if (viewModel.lyrics.isNotEmpty()) showMinimalLyrics = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 圆形封面
                    Box(
                        Modifier.size(140.dp).clip(RoundedCornerShape(70.dp)).background(Color(0xFF333333)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.coverUrl.isNotEmpty()) {
                            AsyncImage(song.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(60.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(song.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Text(song.artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(0.6f), textAlign = TextAlign.Center)
                    // 内嵌歌词
                    if (viewModel.lyrics.isNotEmpty()) {
                        val idx = viewModel.currentLyricIndex
                        Spacer(Modifier.height(16.dp))
                        val cur = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx].text else ""
                        Text(cur, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp))
                        val curT = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx].ttext else ""
                        if (curT.isNotEmpty()) {
                            Text(curT, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.55f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                        val nxt = if (idx + 1 in viewModel.lyrics.indices) viewModel.lyrics[idx + 1].text else ""
                        if (nxt.isNotEmpty()) {
                            Text(nxt, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.4f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
        }

        // 底部完整控件
        StandardBottomControls(song, viewModel, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality)
    }
}

// ═══ 波形可视化模式 ═══
@Composable
private fun WaveformPlayer(
    song: Song,
    viewModel: MusicViewModel,
    baseBg: Color,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
) {
    // 波形动画
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Column(Modifier.fillMaxSize()) {
        // 顶部栏
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
            }
            QualityBadge(viewModel)
            PlayerDownloadButton(viewModel, song)
        }

        // 波形区域
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 小封面
                Box(
                    Modifier.size(100.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.coverUrl.isNotEmpty()) {
                        AsyncImage(song.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                // 条形波形动画
                Row(Modifier.fillMaxWidth(0.75f).height(100.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    repeat(24) { index ->
                        val animatedHeight by infiniteTransition.animateFloat(
                            initialValue = 0.15f,
                            targetValue = if (viewModel.isPlaying) 0.85f else 0.2f,
                            animationSpec = infiniteRepeatable(
                                tween(250 + (index * 40), easing = FastOutSlowInEasing),
                                RepeatMode.Reverse
                            ),
                            label = "bar$index"
                        )
                        Box(
                            Modifier
                                .width(5.dp)
                                .fillMaxHeight(animatedHeight.coerceIn(0.1f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (viewModel.isPlaying) 0.9f else 0.4f),
                                            Color.White.copy(alpha = if (viewModel.isPlaying) 0.4f else 0.15f),
                                        )
                                    )
                                )
                        )
                    }
                }
                // 内嵌歌词
                if (viewModel.lyrics.isNotEmpty()) {
                    val idx = viewModel.currentLyricIndex
                    Spacer(Modifier.height(12.dp))
                    val cur = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx].text else ""
                    Text(cur, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp))
                    val curT = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx].ttext else ""
                    if (curT.isNotEmpty()) {
                        Text(curT, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.55f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    val nxt = if (idx + 1 in viewModel.lyrics.indices) viewModel.lyrics[idx + 1].text else ""
                    if (nxt.isNotEmpty()) {
                        Text(nxt, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.4f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
            if (viewModel.isBuffering) {
                CircularProgressIndicator(Modifier.size(40.dp), color = Color.White.copy(0.7f), strokeWidth = 2.dp)
            }
        }

        // 底部完整控件
        StandardBottomControls(song, viewModel, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality)
    }
}

// ═══ 唱片歌词模式 ═══
@Composable
private fun DiscLyricsPlayer(
    song: Song,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
) {
    // 使用共享的封面颜色提取
    val coverColors = rememberCoverColors(song.coverUrl)
    // 从真实提取色派生浅色背景
    val bgColor = remember(coverColors) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(
            android.graphics.Color.argb(
                255,
                (coverColors.dominant.red * 255).toInt(),
                (coverColors.dominant.green * 255).toInt(),
                (coverColors.dominant.blue * 255).toInt()
            ), hsv
        )
        hsv[1] = hsv[1].coerceIn(0.04f, 0.20f)
        hsv[2] = hsv[2].coerceAtLeast(0.90f)
        Color(AndroidColor.HSVToColor(hsv))
    }
    // 从真实 vibrant 派生强调色
    val accentColor = remember(coverColors) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(
            android.graphics.Color.argb(
                255,
                (coverColors.vibrant.red * 255).toInt(),
                (coverColors.vibrant.green * 255).toInt(),
                (coverColors.vibrant.blue * 255).toInt()
            ), hsv
        )
        hsv[1] = hsv[1].coerceIn(0.25f, 0.60f)
        hsv[2] = hsv[2].coerceIn(0.25f, 0.45f)
        Color(AndroidColor.HSVToColor(hsv))
    }

    // 动画过渡背景色
    val animBg by animateColorAsState(bgColor, tween(600), label = "discBg")
    val animAccent by animateColorAsState(accentColor, tween(600), label = "discAccent")

    Box(Modifier.fillMaxSize().background(animBg)) {
        var isControlsVisible by remember { mutableStateOf(true) }
        var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(100L)
                val currentTime = System.currentTimeMillis()
                if ((currentTime - lastInteractionTime) > 1000L && isControlsVisible) {
                    isControlsVisible = false
                }
            }
        }

        val controlsAlpha by animateFloatAsState(
            targetValue = if (isControlsVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "discControlsAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (isControlsVisible) {
                        isControlsVisible = false
                    } else {
                        isControlsVisible = true
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                // 顶部栏 - 自动隐藏
                Box(modifier = Modifier.alpha(controlsAlpha)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                // 歌曲信息（左上）
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = animAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = animAccent.copy(0.6f))
                }
                QualityBadge(viewModel)
                PlayerDownloadButton(viewModel, song)
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = animAccent.copy(0.7f), modifier = Modifier.size(26.dp))
                }
                    }
                }

            // ── 水波纹歌词区域（参考第二张图片）──
            val discInfiniteTransition = rememberInfiniteTransition(label = "discWave")
            val discWavePhase by discInfiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 8000, easing = LinearEasing),
                    RepeatMode.Restart
                ),
                label = "discWavePhase"
            )
            val discAmp = viewModel.audioAmplitude

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val phase = discWavePhase * 2f * Math.PI.toFloat()
                    val ampFactor = (discAmp.coerceIn(0.8f, 2.0f) - 0.8f) / 1.2f

                    for (i in 8 downTo 0) {
                        val baseR = (w * 0.08f) + i * (w.coerceAtMost(h) * 0.09f)
                        val pulseR = baseR + kotlin.math.sin(phase * 1.2f - i * 0.4f) * (12f + ampFactor * 25f)
                        val alpha = (0.22f - i * 0.022f).coerceAtLeast(0.03f)
                        drawCircle(
                            animAccent.copy(alpha = alpha * (1f + ampFactor * 0.6f)),
                            radius = pulseR,
                            center = Offset(cx, cy),
                            style = Stroke(width = (1.5f + ampFactor * 3f).coerceIn(1f, 6f))
                        )
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(animAccent.copy(alpha = 0.06f + ampFactor * 0.05f), Color.Transparent),
                            radius = w * 0.35f,
                            center = Offset(cx, cy)
                        ),
                        radius = w * 0.30f + ampFactor * w * 0.04f,
                        center = Offset(cx, cy)
                    )
                }

                if (viewModel.lyrics.isNotEmpty()) {
                    val idx = viewModel.currentLyricIndex
                    val currentText = if (idx in viewModel.lyrics.indices) viewModel.lyrics[idx].text else ""
                    Text(
                        currentText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            lineHeight = 36.sp,
                        ),
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                } else {
                    Text(
                        "纯音乐，请欣赏",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.40f),
                    )
                }
            }

            // 底部控件 - 自动隐藏
            Box(modifier = Modifier.alpha(controlsAlpha)) {
                StandardBottomControls(song, viewModel, dur, showQueue, showBottomSheet, showEqualizer, showLyricsSheet, showNewPlaylist, showAddToPlaylist, showDownloadQuality)
            }
            }
        }
    }
}

// ═══ 自定义进度条（完全自控触摸，绕过 Material3 Slider 手势问题） ═══
@Composable
private fun SeekBar(    progress: Float,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(0.15f),
    thumbColor: Color = Color.White,
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 7.dp,
) {
    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val w = size.width.coerceAtLeast(1).toFloat()
                        val initial = (down.position.x / w).coerceIn(0f, 1f)
                        onSeekStart()
                        onSeek(initial)

                        var current = initial
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    change.consume()
                                    current = (change.position.x / w).coerceIn(0f, 1f)
                                    onSeek(current)
                                } else {
                                    change.consume()
                                    break
                                }
                            }
                        } finally {
                            onSeekFinished(current)
                        }
                    }
                }
            }
    ) {
        val centerY = size.height / 2
        val w = size.width
        val p = progress.coerceIn(0f, 1f)

        // Inactive track
        drawLine(inactiveColor, Offset(0f, centerY), Offset(w, centerY), trackHeightPx, StrokeCap.Round)

        // Active track
        val activeEnd = p * w
        if (activeEnd > 0f) {
            drawLine(activeColor, Offset(0f, centerY), Offset(activeEnd, centerY), trackHeightPx, StrokeCap.Round)
        }

        // Thumb
        drawCircle(thumbColor, thumbRadiusPx, Offset(activeEnd, centerY))
    }
}

// ═══ 播放器样式选择弹窗 ═══
@Composable
private fun PlayerStylePickerDialog(
    currentStyle: MusicViewModel.PlayerStyle,
    onStyleSelected: (MusicViewModel.PlayerStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                "选择播放器样式",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(MusicViewModel.PlayerStyle.entries.toList(), key = { it.name }, contentType = { "style" }) { style ->
                    val isSelected = style == currentStyle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                            .clickable { onStyleSelected(style); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val iconTint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                            when (style) {
                                MusicViewModel.PlayerStyle.MODERN -> Icon(Icons.Default.PlayArrow, null, tint = iconTint, modifier = Modifier.size(22.dp))
                                MusicViewModel.PlayerStyle.BLUR_BG -> Icon(Icons.Default.BlurOn, null, tint = iconTint, modifier = Modifier.size(22.dp))
                                MusicViewModel.PlayerStyle.IMMERSIVE_COVER -> Icon(Icons.Default.Image, null, tint = iconTint, modifier = Modifier.size(22.dp))
                                MusicViewModel.PlayerStyle.QDUAN -> Icon(Icons.Default.ViewAgenda, null, tint = iconTint, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(style.displayName, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal))
                            Text(style.description, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                        }
                        if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Color.White.copy(alpha = 0.6f)) }
        },
    )
}

// ═══ 统一底部状态功能栏 ═══
// 样式规范：
//   - 尺寸：水平 padding 24dp；主控制按钮 52dp（前/后）/ 68dp（播放）；辅助按钮 40dp、图标 22dp
//   - 颜色：tintColor 驱动所有图标与文字；播放按钮背景=tintColor，前景=playButtonFgColor（默认黑）
//   - 交互：点击反馈统一由 IconButton/Box.clickable 提供；进度条拖拽 isDragging 高亮
//   - 响应式：controlsAlpha 适配自动隐藏；fillMaxWidth 适配屏宽
@Composable
private fun UnifiedBottomBar(
    song: Song,
    viewModel: MusicViewModel,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
    showStylePicker: MutableState<Boolean>,
    tintColor: Color = Color.White,
    controlsAlpha: Float = 1f,
    playButtonFgColor: Color = Color.Black,
    playButtonHasBackground: Boolean = true,
    accentColor: Color? = null,
    qualityText: String? = null,
    onQualityClick: (() -> Unit)? = null,
) {
    val controlColor = accentColor ?: tintColor
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .alpha(controlsAlpha),
    ) {
        // ── 进度条 ──
        var isDragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(Unit) {
            snapshotFlow { viewModel.progress }
                .collect { if (!isDragging) dragValue = it }
        }

        SeekBar(
            progress = dragValue,
            onSeekStart = { isDragging = true; viewModel.isSeeking = true },
            onSeek = { dragValue = it },
            onSeekFinished = { viewModel.seekTo(it); isDragging = false; viewModel.isSeeking = false },
            activeColor = controlColor,
            inactiveColor = controlColor.copy(alpha = 0.15f),
            thumbColor = controlColor,
            trackHeight = 2.dp,
            thumbRadius = 4.dp,
        )

        // ── 时间行（可选音质标签居中） ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(fmt((dragValue * dur).toLong()), style = MaterialTheme.typography.labelSmall, color = if (isDragging) tintColor else tintColor.copy(0.4f))
            if (qualityText != null && onQualityClick != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onQualityClick() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        qualityText,
                        style = MaterialTheme.typography.labelSmall,
                        color = tintColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = tintColor.copy(0.4f))
        }

        Spacer(Modifier.height(12.dp))

        // ── 主播放控制 ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.playPrevious() }, Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipPrevious, null, tint = controlColor, modifier = Modifier.size(36.dp))
            }
            if (playButtonHasBackground) {
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(tintColor).clickable { viewModel.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (viewModel.isBuffering) CircularProgressIndicator(Modifier.size(30.dp), color = playButtonFgColor, strokeWidth = 2.5.dp)
                    else Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = playButtonFgColor, modifier = Modifier.size(38.dp))
                }
            } else {
                IconButton(
                    onClick = { viewModel.togglePlay() },
                    modifier = Modifier.size(68.dp)
                ) {
                    if (viewModel.isBuffering) {
                        CircularProgressIndicator(Modifier.size(34.dp), color = controlColor, strokeWidth = 2.5.dp)
                    } else {
                        Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = controlColor, modifier = Modifier.size(40.dp))
                    }
                }
            }
            IconButton(onClick = { viewModel.playNext() }, Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipNext, null, tint = controlColor, modifier = Modifier.size(36.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 辅助功能按钮（统一尺寸40dp、图标22dp）—— 3秒自动隐藏，全屏点击重新显示 ──
        // 状态由根容器通过 CompositionLocal 提供，全屏 pointerInput 检测点击
        val controlsVisible = LocalBottomBarVisible.current.value
        val bottomAlpha by animateFloatAsState(
            targetValue = if (controlsVisible) 1f else 0f,
            animationSpec = tween(200),
            label = "bottomBarAlpha",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.fillMaxWidth().graphicsLayer { alpha = bottomAlpha }, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val isFav = viewModel.isFavorite(song)
            IconButton(onClick = { viewModel.toggleLike(song) }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFav) tintColor else tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = { showAddToPlaylist.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(Icons.Default.PlaylistAdd, null, tint = tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = { viewModel.togglePlayMode() }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(
                    when (viewModel.playMode) {
                        MusicViewModel.PlayMode.LOOP -> Icons.Default.Repeat
                        MusicViewModel.PlayMode.SINGLE -> Icons.Default.RepeatOne
                        MusicViewModel.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                    },
                    null, tint = tintColor.copy(0.85f), modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = { viewModel.toggleFloatingLyrics() }, Modifier.size(40.dp), enabled = controlsVisible) {
                Text("词", color = if (viewModel.isFloatingLyricsEnabled) tintColor else tintColor.copy(0.8f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            IconButton(onClick = { showQueue.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(Icons.Default.List, null, tint = tintColor.copy(0.85f), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = { showEqualizer.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(Icons.Default.Equalizer, null, tint = tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
            }
            // 下载按钮
            IconButton(onClick = { showDownloadQuality.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                val dlState = viewModel.downloadState
                val isThisDownloading = dlState is MusicViewModel.DownloadState.Downloading && dlState.songId == song.platformId
                val isDownloaded = viewModel.isDownloaded(song)
                if (isThisDownloading) {
                    CircularProgressIndicator(
                        progress = { (dlState as MusicViewModel.DownloadState.Downloading).progress },
                        modifier = Modifier.size(22.dp),
                        color = tintColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Download, null, tint = if (isDownloaded) tintColor else tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
                }
            }
            // 播放器样式选择按钮
            IconButton(onClick = { showStylePicker.value = true }, Modifier.size(40.dp), enabled = controlsVisible) {
                Icon(Icons.Default.SwapHoriz, null, tint = tintColor.copy(0.8f), modifier = Modifier.size(22.dp))
            }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

// ═══ 标准底部控件（委托给 UnifiedBottomBar，自包含样式选择弹窗） ═══
@Composable
private fun StandardBottomControls(
    song: Song,
    viewModel: MusicViewModel,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showBottomSheet: MutableState<Boolean>,
    showEqualizer: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>,
    showNewPlaylist: MutableState<Boolean>,
    showAddToPlaylist: MutableState<Boolean>,
    showDownloadQuality: MutableState<Boolean>,
) {
    val showStylePicker = remember { mutableStateOf(false) }
    UnifiedBottomBar(
        song = song,
        viewModel = viewModel,
        dur = dur,
        showQueue = showQueue,
        showEqualizer = showEqualizer,
        showAddToPlaylist = showAddToPlaylist,
        showDownloadQuality = showDownloadQuality,
        showStylePicker = showStylePicker,
    )
    if (showStylePicker.value) {
        PlayerStylePickerDialog(
            currentStyle = viewModel.playerStyle,
            onStyleSelected = { viewModel.changePlayerStyle(it) },
            onDismiss = { showStylePicker.value = false },
        )
    }
}

// ═══ 极简底部控件 ═══
@Composable
private fun MinimalBottomControls(
    viewModel: MusicViewModel,
    dur: Long,
    showQueue: MutableState<Boolean>,
    showLyricsSheet: MutableState<Boolean>? = null,
    showNewPlaylist: MutableState<Boolean>? = null,
    showAddToPlaylist: MutableState<Boolean>? = null,
    song: Song? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // 时间显示
        var isDragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(Unit) {
            snapshotFlow { viewModel.progress }
                .collect { if (!isDragging) dragValue = it }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                fmt((dragValue * dur).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDragging) Color.White else Color.White.copy(0.4f)
            )
            Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
        }
        
        Spacer(Modifier.height(8.dp))
        
        // 进度条
        SeekBar(
            progress = dragValue,
            onSeekStart = {
                isDragging = true
                viewModel.isSeeking = true
            },
            onSeek = { dragValue = it },
            onSeekFinished = {
                viewModel.seekTo(it)
                isDragging = false
                viewModel.isSeeking = false
            },
            inactiveColor = Color.White.copy(0.2f),
        )

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.togglePlayMode() }, Modifier.size(36.dp)) {
                Icon(when (viewModel.playMode) { MusicViewModel.PlayMode.LOOP -> Icons.Default.Repeat; MusicViewModel.PlayMode.SINGLE -> Icons.Default.RepeatOne; MusicViewModel.PlayMode.SHUFFLE -> Icons.Default.Shuffle }, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { viewModel.playPrevious() }, Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { viewModel.togglePlay() }, Modifier.size(56.dp)) {
                if (viewModel.isBuffering) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            IconButton(onClick = { viewModel.playNext() }, Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (showAddToPlaylist != null && song != null) {
                IconButton(onClick = { showAddToPlaylist.value = true }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.PlaylistAdd, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
                }
            }
            if (showLyricsSheet != null) {
                IconButton(onClick = { showLyricsSheet.value = true }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = { showQueue.value = true }, Modifier.size(36.dp)) {
                Icon(Icons.Default.List, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ═══ 黑胶唱片 (正方形封面 + 真实音频律动 + 3D倾斜 + 光泽 + 光晕) ═══
@Composable
private fun VinylDisc(rotation: Float, coverUrl: String, isPlaying: Boolean, audioAmplitude: Float, onClick: (() -> Unit)? = null) {
    var pointerX by remember { mutableFloatStateOf(0.5f) }
    var pointerY by remember { mutableFloatStateOf(0.5f) }
    var isActive by remember { mutableStateOf(false) }

    val tiltX by animateFloatAsState(
        targetValue = if (isActive) (pointerX - 0.5f) * 22f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tiltX",
    )
    val tiltY by animateFloatAsState(
        targetValue = if (isActive) -(pointerY - 0.5f) * 18f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tiltY",
    )

    val shineX by animateFloatAsState(targetValue = pointerX, animationSpec = tween(120), label = "shX")
    val shineY by animateFloatAsState(targetValue = pointerY, animationSpec = tween(120), label = "shY")

    val glowScale by animateFloatAsState(
        targetValue = if (isPlaying) (if (isActive) 1.12f else 1.05f) else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 200f),
        label = "glowScale",
    )
    val shineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(250),
        label = "shAlpha",
    )

    // 🎵 使用真实音频数据驱动封面律动（来自 AudioProcessor）
    val musicPulseScale = if (isPlaying) {
        // 只要有振幅数据就应用缩放（最小1.0，最大1.15）
        audioAmplitude.coerceIn(1.0f, 1.15f)
    } else {
        1f
    }

    // 平滑过渡动画（避免跳动过于突兀）
    val animatedPulse by animateFloatAsState(
        targetValue = musicPulseScale,
        animationSpec = tween(50, easing = LinearEasing),
        label = "pulseAnim"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(280.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClick() }
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                val w = size.width.coerceAtLeast(1)
                val h = size.height.coerceAtLeast(1)
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            isActive = true
                            pointerX = (change.position.x / w).coerceIn(0f, 1f)
                            pointerY = (change.position.y / h).coerceIn(0f, 1f)
                        } else {
                            isActive = false
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .size(270.dp)
                .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.maxDimension * 0.6f,
                        ),
                    )
                },
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .graphicsLayer {
                    rotationY = tiltX
                    rotationX = tiltY
                    cameraDistance = 14f * density
                },
        ) {
            Box(
                Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A1A))
                    .graphicsLayer {
                        scaleX = animatedPulse
                        scaleY = animatedPulse
                    }
            ) {
                if (coverUrl.isNotEmpty()) {
                    AsyncImage(
                        coverUrl,
                        null,
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(80.dp))
                    }
                }
            }

            Box(
                Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer { alpha = shineAlpha }
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.04f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * shineX, size.height * shineY),
                                radius = size.maxDimension * 0.5f,
                            ),
                        )
                    },
            )

            Box(
                Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isActive) 0.07f else 0.02f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = if (isActive) 0.04f else 0.01f),
                                ),
                                start = Offset(size.width * shineX, 0f),
                                end = Offset(size.width * (1f - shineX), size.height),
                            ),
                        )
                    },
            )
        }
        
        // 🎵 正方形音频可视化频谱 - 超大版本
        SquareAudioVisualizer(
            audioAmplitude = audioAmplitude,
            isPlaying = isPlaying,
            modifier = Modifier.size(420.dp)
        )
    }
}

/**
 * 圆形封面 + 环形放射状频谱组件
 * 模仿图片中的设计：圆形封面 + 周围放射状频谱条
 */
/**
 * 圆角正方形封面 + 实时音频跳动效果
 * 封面随着音乐节奏缩放跳动
 */
@Composable
private fun CircularCoverWithSpectrum(
    coverUrl: String,
    isPlaying: Boolean,
    audioAmplitude: Float,
    isBuffering: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    // 封面尺寸 - 圆角正方形
    val coverSizeDp = 320.dp
    val cornerRadius = 24.dp

    // 🎵 封面跳动效果 - 使用真实音频数据
    // audioAmplitude 范围是 1.0f~1.15f（来自AmplitudeAudioProcessor）
    // 需要映射到明显的缩放比例
    val coverPulseScale = if (isPlaying && audioAmplitude > 0f) {
        // 将 1.0~1.15 映射到 1.0~1.25 的缩放比例
        // (audioAmplitude - 1.0) 范围是 0~0.15
        // 乘以 1.67 映射到 0~0.25
        1f + (audioAmplitude - 1f) * 1.67f
    } else {
        1f
    }

    // 平滑过渡动画
    val animatedCoverScale by animateFloatAsState(
        targetValue = coverPulseScale.coerceIn(1.0f, 1.25f),
        animationSpec = tween(50, easing = LinearEasing),
        label = "coverPulse"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 跳动的圆角正方形封面
        Box(
            modifier = Modifier
                .size(coverSizeDp)
                .graphicsLayer {
                    scaleX = animatedCoverScale
                    scaleY = animatedCoverScale
                }
                .clip(RoundedCornerShape(cornerRadius))
                .then(if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop
            )

            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                    color = Color.White.copy(0.7f),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

/**
 * 正方形音频可视化频谱组件 - 霓虹玫瑰风格
 */
@Composable
private fun SquareAudioVisualizer(
    audioAmplitude: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // 每个边的频谱条数 - 33条
    val barsPerSide = 33
    // 动画目标值 - 高潮时放大1.5倍
    val targetScale = if (isPlaying) audioAmplitude else 1f
    val highEnergyScale = if (targetScale > 1.08f) targetScale * 1.5f else targetScale
    
    // 使用 remember 存储每个频谱条的状态
    val barScales = remember { List(barsPerSide * 4) { Animatable(0.05f) } }
    
    // 模拟频谱数据 - 创建呼吸感的波形
    val spectrumData = remember { List(barsPerSide * 4) { index ->
        val position = index % barsPerSide
        val normalizedPos = position.toFloat() / barsPerSide
        // 中间高两边低的弧形分布，模拟自然呼吸
        val archResponse = kotlin.math.sin(normalizedPos * kotlin.math.PI.toFloat())
        // 添加一些随机性
        val randomOffset = (index % 7) * 0.05f
        (archResponse * 0.7f + 0.3f + randomOffset).coerceIn(0.2f, 1f)
    }}
    
    // 更新频谱条动画 - 呼吸感律动
    LaunchedEffect(highEnergyScale, isPlaying) {
        barScales.forEachIndexed { index, animatable ->
            launch {
                val baseResponse = spectrumData[index]
                // 根据位置错开动画，形成波浪效果
                val waveDelay = (index % barsPerSide) * 10L
                val sideDelay = (index / barsPerSide) * 20L
                
                delay(waveDelay + sideDelay)
                
                val target = if (isPlaying) {
                    // 基础呼吸高度 + 振幅响应
                    val breathBase = 0.15f + baseResponse * 0.2f
                    val amplitudeBoost = (highEnergyScale - 1f) * baseResponse * 2.5f
                    val emotionalVariation = kotlin.math.sin(index * 0.5f) * 0.1f
                    (breathBase + amplitudeBoost + emotionalVariation).coerceIn(0.05f, 1f)
                } else {
                    0.05f // 暂停时几乎隐藏
                }
                
                animatable.animateTo(
                    targetValue = target,
                    animationSpec = tween(80, easing = LinearEasing)
                )
            }
        }
    }
    
    val neonColors = listOf(
        Color(0xFF1A1A1A),
        Color(0xFF222222),
        Color(0xFF2A2A2A),
        Color(0xFF333333),
        Color(0xFF3A3A3A),
        Color(0xFF444444)
    )
    
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2
            val centerY = canvasHeight / 2
            
            // 封面尺寸
            val coverSize = 260.dp.toPx()
            val halfCover = coverSize / 2
            
            // 频谱条尺寸 - 超超超大版本
            val barWidth = 10f
            val maxBarHeight = 180f
            val spacing = 2f
            val gap = 6f // 与封面的间隙
            
            // 计算总宽度和起始偏移
            val totalBarSpace = barsPerSide * (barWidth + spacing) - spacing
            val offset = (coverSize - totalBarSpace) / 2
            
            // 绘制四条边的频谱
            for (side in 0 until 4) {
                for (i in 0 until barsPerSide) {
                    val barIndex = side * barsPerSide + i
                    val scale = barScales.getOrNull(barIndex)?.value ?: 0.05f
                    val currentBarHeight = maxBarHeight * scale
                    
                    // 根据高度选择渐变色
                    val colorProgress = scale.coerceIn(0f, 1f)
                    val colorIndex = (colorProgress * (neonColors.size - 1)).toInt()
                        .coerceIn(0, neonColors.size - 2)
                    val nextColorIndex = (colorIndex + 1).coerceAtMost(neonColors.size - 1)
                    val colorFraction = colorProgress * (neonColors.size - 1) - colorIndex
                    
                    val barColor = androidx.compose.ui.graphics.lerp(
                        neonColors[colorIndex],
                        neonColors[nextColorIndex],
                        colorFraction
                    )
                    
                    val x: Float
                    val y: Float
                    val rotation: Float
                    
                    when (side) {
                        0 -> { // 上边 - 向上延伸
                            x = centerX - halfCover + offset + i * (barWidth + spacing) + barWidth / 2
                            y = centerY - halfCover - gap - currentBarHeight / 2
                            rotation = 0f
                        }
                        1 -> { // 右边 - 向右延伸
                            x = centerX + halfCover + gap + currentBarHeight / 2
                            y = centerY - halfCover + offset + i * (barWidth + spacing) + barWidth / 2
                            rotation = 90f
                        }
                        2 -> { // 下边 - 向下延伸
                            x = centerX + halfCover - offset - i * (barWidth + spacing) - barWidth / 2
                            y = centerY + halfCover + gap + currentBarHeight / 2
                            rotation = 180f
                        }
                        else -> { // 左边 - 向左延伸
                            x = centerX - halfCover - gap - currentBarHeight / 2
                            y = centerY + halfCover - offset - i * (barWidth + spacing) - barWidth / 2
                            rotation = 270f
                        }
                    }
                    
                    // 绘制频谱条 - 霓虹光感多层效果
                    rotate(rotation, pivot = Offset(x, y)) {
                        // 💫 外层辉光（10-20%透明度）- 深海中的自发光感
                        if (scale > 0.15f) {
                            val glowAlpha = 0.12f * scale
                            // 第一层外发光
                            drawRoundRect(
                                color = barColor.copy(alpha = glowAlpha * 0.5f),
                                topLeft = Offset(x - barWidth / 2 - 6f, y - currentBarHeight / 2 - 6f),
                                size = Size(barWidth + 12f, currentBarHeight + 12f),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            // 第二层内发光
                            drawRoundRect(
                                color = barColor.copy(alpha = glowAlpha),
                                topLeft = Offset(x - barWidth / 2 - 3f, y - currentBarHeight / 2 - 3f),
                                size = Size(barWidth + 6f, currentBarHeight + 6f),
                                cornerRadius = CornerRadius(3f, 3f)
                            )
                        }
                        
                        // 🌹 主体条 - 柔和圆角（2-4px）
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.75f + scale * 0.15f),
                            topLeft = Offset(x - barWidth / 2, y - currentBarHeight / 2),
                            size = Size(barWidth, currentBarHeight),
                            cornerRadius = CornerRadius(3f, 3f) // 柔和圆角
                        )
                        
                        // ✨ 内层光芯 - 通透感
                        if (scale > 0.2f) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.25f * scale),
                                topLeft = Offset(x - barWidth / 2 + 0.5f, y - currentBarHeight / 2 + 1f),
                                size = Size(barWidth - 1f, currentBarHeight * 0.4f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                        
                        // 🔥 顶部火焰高光
                        if (scale > 0.3f) {
                            val flameHeight = currentBarHeight * 0.15f
                            drawRoundRect(
                                color = neonColors[5].copy(alpha = 0.6f * scale),
                                topLeft = Offset(x - barWidth / 2 + 0.5f, y - currentBarHeight / 2),
                                size = Size(barWidth - 1f, flameHeight),
                                cornerRadius = CornerRadius(1.5f, 1.5f)
                            )
                        }
                    }
                }
            }
            // 四角装饰已删除
        }
    }
}



// ═══ 沉浸式歌词 - 纯色深红背景 + 锁定功能 ═══
@Composable
private fun LyricsViewImmersive(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    baseBg: Color,
    coverUrl: String = "",
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 8.dp,
    currentLyricColor: Color? = null,
    normalLyricColor: Color? = null,
    lyricFontSize: Int = 0,
    showBackground: Boolean = true,
    onSeekTo: ((Long) -> Unit)? = null,
    onBack: () -> Unit = {},
    currentTimeMs: Long = 0L,
) {
    Box(Modifier.fillMaxSize()) {
        if (showBackground && coverUrl.isNotEmpty()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "歌词背景",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.6f,
            )
        }
        if (showBackground) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        if (lyrics.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("\u7eaf\u97f3\u4e50\uff0c\u8bf7\u6b23\u8d4f", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(0.5f), textAlign = TextAlign.Center)
                }
            }
            return
        }

        val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, currentIndex))
        var containerHeight by remember { mutableIntStateOf(0) }
        var userScrolling by remember { mutableStateOf(false) }
        var lastScrolledIndex by remember { mutableIntStateOf(-1) }
        var isFirstLayout by remember { mutableStateOf(true) }

        var isDragging by remember { mutableStateOf(false) }
        var dragTargetIndex by remember { mutableIntStateOf(currentIndex) }

        val density = LocalDensity.current.density

        LaunchedEffect(lyrics) {
            isFirstLayout = true
            lastScrolledIndex = -1
            userScrolling = false
            if (lyrics.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }

        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress && lastScrolledIndex != currentIndex) {
                userScrolling = true
            }
        }
        LaunchedEffect(userScrolling) {
            if (userScrolling) {
                delay(3000L)
                userScrolling = false
            }
        }

        LaunchedEffect(currentIndex) {
            if (containerHeight <= 0 || currentIndex !in lyrics.indices || userScrolling) return@LaunchedEffect
            if (lastScrolledIndex == currentIndex) return@LaunchedEffect
            lastScrolledIndex = currentIndex

            if (isFirstLayout) {
                isFirstLayout = false
                listState.scrollToItem(currentIndex, -containerHeight / 2)
                return@LaunchedEffect
            }

            val visibleInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == currentIndex }
            if (visibleInfo != null) {
                val itemCenter = visibleInfo.offset + visibleInfo.size / 2
                val screenCenter = containerHeight / 2
                val delta = (itemCenter - screenCenter).toFloat()
                if (kotlin.math.abs(delta) > 1f) {
                    listState.animateScrollBy(delta, tween(500, easing = FastOutSlowInEasing))
                }
            } else {
                listState.scrollToItem(maxOf(0, currentIndex - 2))
                delay(16)
                val info = listState.layoutInfo.visibleItemsInfo.find { it.index == currentIndex }
                if (info != null) {
                    val itemCenter = info.offset + info.size / 2
                    val screenCenter = containerHeight / 2
                    val delta = (itemCenter - screenCenter).toFloat()
                    listState.animateScrollBy(delta, tween(300, easing = FastOutSlowInEasing))
                }
            }
        }

        val baseFontSize = if (lyricFontSize > 0) lyricFontSize.toFloat() else 17f
        // 🎨 第二张图片风格：绿色高亮当前行 + 暗红普通歌词
        val currentColor = currentLyricColor ?: Color.White
        val normalColor = normalLyricColor ?: Color(0xFF888888)

        // 点击返回逻辑
        Box(modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBack() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (onSeekTo == null) return@detectVerticalDragGestures
                        isDragging = true
                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                        val targetItem = visibleItems.findLast { it.offset < offset.y }
                        if (targetItem != null) {
                            dragTargetIndex = targetItem.index.coerceIn(0, lyrics.size - 1)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        if (dragTargetIndex in lyrics.indices && onSeekTo != null) {
                            onSeekTo(lyrics[dragTargetIndex].timeMs)
                        }
                    },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (!isDragging || onSeekTo == null) return@detectVerticalDragGestures

                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                        if (visibleItems.isNotEmpty()) {
                            val centerY = containerHeight / 2f
                            val closestItem = visibleItems.minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - centerY) }
                            if (closestItem != null) {
                                val avgItemHeight = if (visibleItems.size > 1) {
                                    kotlin.math.abs(visibleItems[1].offset - visibleItems[0].offset).toFloat()
                                } else {
                                    60f * density
                                }
                                val indexDelta = (-dragAmount / avgItemHeight).toInt()
                                dragTargetIndex = (closestItem.index + indexDelta).coerceIn(0, lyrics.size - 1)
                            }
                        }
                    }
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { containerHeight = it.size.height },
                state = listState,
                contentPadding = PaddingValues(top = topPadding + 56.dp, bottom = bottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            items(lyrics.size, key = { "${it}_${lyrics[it].timeMs}" }) { index ->
                val line = lyrics[index]
                val cur = index == currentIndex
                val isDragTarget = isDragging && index == dragTargetIndex
                val distance = kotlin.math.abs(index - currentIndex)
                val alpha = when {
                    isDragTarget -> 1f
                    cur -> 1f
                    distance == 1 -> 0.6f
                    distance == 2 -> 0.4f
                    distance == 3 -> 0.3f
                    else -> 0.25f
                }

                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.88f)) {
                        if (isDragTarget) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    line.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = baseFontSize.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = (baseFontSize * 1.4f).sp
                                    ),
                                    color = currentColor,
                                    textAlign = TextAlign.Start,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        fmt(line.timeMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = currentColor.copy(alpha = 0.8f),
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.PlayArrow, null, tint = currentColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            if (cur && line.words.isNotEmpty()) {
                                val annotatedText = buildAnnotatedString {
                                    line.words.forEach { word ->
                                        val isWordActive = currentTimeMs >= word.startTimeMs
                                        val style = SpanStyle(
                                            color = if (isWordActive) currentColor else normalColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        withStyle(style) {
                                            append(word.text)
                                        }
                                    }
                                }
                                Text(
                                    annotatedText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = baseFontSize.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = (baseFontSize * 1.4f).sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    line.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = baseFontSize.sp,
                                        fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = (baseFontSize * 1.4f).sp
                                    ),
                                    color = if (cur) currentColor else normalColor.copy(alpha = alpha),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (line.ttext.isNotEmpty() && !isDragging) {
                                Text(
                                    line.ttext,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = (baseFontSize * 0.82f).sp,
                                        fontWeight = FontWeight.Normal,
                                        lineHeight = (baseFontSize * 1.2f).sp
                                    ),
                                    color = if (cur) currentColor.copy(alpha = 0.75f) else normalColor.copy(alpha = alpha * 0.6f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }}
        }
    }
}

private data class CoverColors(
    val base: Color,
    val vibrant: Color,
    val accent: Color,
    val secondary: Color,
    val dominant: Color,
    val muted: Color,
    val lightAccent: Color,
)

private val DefaultCoverColors = CoverColors(
    base = Color(0xFF1A1A1A),
    vibrant = Color(0xFF2A2A2A),
    accent = Color(0xFF3A3A3A),
    secondary = Color(0xFF1A1A1A),
    dominant = Color(0xFF222222),
    muted = Color(0xFF2A2A2A),
    lightAccent = Color(0xFF3A3A3A),
)

/** 轻微降低亮度但保留真实色相和饱和度 */
private fun darkenFaithful(rgb: Int, maxV: Float = 0.35f, minS: Float = 0.20f): Int {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(rgb, hsv)
    hsv[1] = hsv[1].coerceAtLeast(minS)          // 保证最低饱和度
    hsv[2] = hsv[2].coerceAtMost(maxV)            // 降低亮度供背景使用
    return AndroidColor.HSVToColor(hsv)
}

@Composable
private fun rememberCoverColors(coverUrl: String): CoverColors {
    val context = LocalContext.current
    var colors by remember(coverUrl) { mutableStateOf(DefaultCoverColors) }

    LaunchedEffect(coverUrl) {
        if (coverUrl.isBlank()) { colors = DefaultCoverColors; return@LaunchedEffect }

        try {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .allowHardware(false)
                .size(256) // 小尺寸足够提取颜色，更快
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
                val palette = Palette.from(bitmap)
                    .maximumColorCount(24)   // 更多色板槽位 → 更真实
                    .generate()

                val vibrantSw    = palette.vibrantSwatch
                val darkVibrantSw = palette.darkVibrantSwatch
                val mutedSw      = palette.mutedSwatch
                val darkMutedSw  = palette.darkMutedSwatch
                val lightVibSw   = palette.lightVibrantSwatch
                val lightMutSw   = palette.lightMutedSwatch
                val dominantSw   = palette.dominantSwatch

                // ── base: 主背景 ──
                val baseRgb = darkenFaithful(
                    darkVibrantSw?.rgb ?: dominantSw?.rgb ?: 0xFF1A1A1A.toInt(),
                    maxV = 0.30f, minS = 0.22f
                )

                // ── vibrant: 鲜艳色 ──
                val vibRgb = darkenFaithful(
                    vibrantSw?.rgb ?: lightVibSw?.rgb ?: baseRgb,
                    maxV = 0.45f, minS = 0.30f
                )

                // ── accent: lightVibrant / muted ──
                val accentRgb = darkenFaithful(
                    lightVibSw?.rgb ?: mutedSw?.rgb ?: vibrantSw?.rgb ?: baseRgb,
                    maxV = 0.40f, minS = 0.22f
                )

                // ── secondary: muted 真实色 ──
                val secRgb = darkenFaithful(
                    mutedSw?.rgb ?: darkMutedSw?.rgb ?: baseRgb,
                    maxV = 0.28f, minS = 0.18f
                )

                // ── dominant: 封面占比最大色 ──
                val domRgb = darkenFaithful(
                    dominantSw?.rgb ?: darkMutedSw?.rgb ?: baseRgb,
                    maxV = 0.32f, minS = 0.18f
                )

                // ── muted: 柔和色调 ──
                val mutRgb = darkenFaithful(
                    darkMutedSw?.rgb ?: mutedSw?.rgb ?: baseRgb,
                    maxV = 0.25f, minS = 0.12f
                )

                // ── lightAccent: 点缀色 ──
                val laRgb = darkenFaithful(
                    lightVibSw?.rgb ?: lightMutSw?.rgb ?: vibrantSw?.rgb ?: baseRgb,
                    maxV = 0.50f, minS = 0.25f
                )

                colors = CoverColors(
                    base = Color(baseRgb),
                    vibrant = Color(vibRgb),
                    accent = Color(accentRgb),
                    secondary = Color(secRgb),
                    dominant = Color(domRgb),
                    muted = Color(mutRgb),
                    lightAccent = Color(laRgb),
                )
            }
        } catch (_: Exception) {}
    }

    return colors
}

@Composable
private fun PlayerDownloadButton(viewModel: MusicViewModel, song: Song) {
    var showDownloadMenu by remember { mutableStateOf(false) }
    val isDownloading = viewModel.downloadState is MusicViewModel.DownloadState.Downloading &&
        (viewModel.downloadState as MusicViewModel.DownloadState.Downloading).songId == song.platformId
    val isDownloaded = viewModel.isDownloaded(song)

    Box {
        IconButton(
            onClick = { showDownloadMenu = true },
            modifier = Modifier.size(36.dp),
        ) {
            if (isDownloading) {
                val progress = (viewModel.downloadState as MusicViewModel.DownloadState.Downloading).progress
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "下载",
                    tint = if (isDownloaded) Color.White else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        DropdownMenu(
            expanded = showDownloadMenu,
            onDismissRequest = { showDownloadMenu = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            Text(
                "下载: ${song.title}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // 仅展示当前生效插件所支持的音质选项；若检测为空则回退为全部可选
            val supportedDlMenu = viewModel.activePluginSupportedQualities
            val visibleMenuQualities = MusicApiConfig.Quality.entries.filter { q ->
                supportedDlMenu.isEmpty() || q in supportedDlMenu
            }
            visibleMenuQualities.forEach { quality ->
                val qualityColor = when (quality) {
                    MusicApiConfig.Quality.STANDARD -> Color(0xFFAAAAAA)
                    MusicApiConfig.Quality.EXHIGH -> Color(0xFFCCCCCC)
                    MusicApiConfig.Quality.LOSSLESS -> Color.White
                    MusicApiConfig.Quality.HIRES -> Color(0xFFFFD700)
                    MusicApiConfig.Quality.JYMASTER -> Color(0xFFFF6B6B)
                    MusicApiConfig.Quality.SKY -> Color(0xFF00E5FF)
                    MusicApiConfig.Quality.JYEFFECT -> Color(0xFF7C4DFF)
                }
                DropdownMenuItem(
                    onClick = {
                        showDownloadMenu = false
                        viewModel.downloadSongWithQuality(song, quality)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(quality.label, color = qualityColor, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(8.dp))
                            Text("${quality.bitrate}kbps", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
            DropdownMenuItem(
                onClick = {
                    showDownloadMenu = false
                    viewModel.batchDownloadCurrentPlaylist()
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("批量下载当前列表", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
private fun QualityBadge(viewModel: MusicViewModel) {
    var showQualityMenu by remember { mutableStateOf(false) }
    val qColor = when (viewModel.selectedQuality) {
        MusicApiConfig.Quality.STANDARD -> Color(0xFFAAAAAA)
        MusicApiConfig.Quality.EXHIGH -> Color(0xFFCCCCCC)
        MusicApiConfig.Quality.LOSSLESS -> Color.White
        MusicApiConfig.Quality.HIRES -> Color(0xFFFFD700)
        MusicApiConfig.Quality.JYMASTER -> Color(0xFFFF6B6B)
        MusicApiConfig.Quality.SKY -> Color(0xFF00E5FF)
        MusicApiConfig.Quality.JYEFFECT -> Color(0xFF7C4DFF)
    }
    Box {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(qColor.copy(0.15f))
                .clickable { showQualityMenu = true }
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(viewModel.selectedQuality.label, fontSize = 11.sp, color = qColor, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(
            expanded = showQualityMenu,
            onDismissRequest = { showQualityMenu = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            // 仅展示当前生效插件所支持的音质选项；若检测为空则回退为全部可选
            val supported = viewModel.activePluginSupportedQualities
            val visibleQualities = MusicApiConfig.Quality.entries.filter { q ->
                supported.isEmpty() || q in supported
            }
            visibleQualities.forEach { quality ->
                val qualityColor = when (quality) {
                    MusicApiConfig.Quality.STANDARD -> Color(0xFFAAAAAA)
                    MusicApiConfig.Quality.EXHIGH -> Color(0xFFCCCCCC)
                    MusicApiConfig.Quality.LOSSLESS -> Color.White
                    MusicApiConfig.Quality.HIRES -> Color(0xFFFFD700)
                    MusicApiConfig.Quality.JYMASTER -> Color(0xFFFF6B6B)
                    MusicApiConfig.Quality.SKY -> Color(0xFF00E5FF)
                    MusicApiConfig.Quality.JYEFFECT -> Color(0xFF7C4DFF)
                }
                DropdownMenuItem(
                    onClick = { viewModel.setQuality(quality); showQualityMenu = false },
                    text = { Text(quality.label, color = qualityColor) }
                )
            }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 4.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(16.dp)); Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

// ═══ 滚动倒计时 (参考 React Counter 组件的滚动数字动画) ═══

@Composable
private fun SleepTimerCounter(remainingMs: Long, onCancel: () -> Unit) {
    val totalSec = (remainingMs / 1000).toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable { onCancel() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Timer, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))

        val fs = 24.sp
        val col = Color.White

        RollingDigit(targetDigit = minutes / 10, fontSize = fs, color = col)
        RollingDigit(targetDigit = minutes % 10, fontSize = fs, color = col)
        Text(":", fontSize = fs, color = col.copy(0.35f), fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 1.dp))
        RollingDigit(targetDigit = seconds / 10, fontSize = fs, color = col)
        RollingDigit(targetDigit = seconds % 10, fontSize = fs, color = col)

        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Close, null, tint = Color.White.copy(0.25f), modifier = Modifier.size(12.dp))
    }
}

/**
 * 单个滚动数字 — 参考 React Counter 的 Number/Digit 组件
 * 10个数字(0-9)堆叠排列，通过 Y 轴偏移 + 弹性动画实现滚动切换
 * 使用连续累加值 + mod 10 实现自然方向的翻滚 (避免 0→9 穿越中间数字)
 */
@Composable
private fun RollingDigit(targetDigit: Int, fontSize: androidx.compose.ui.unit.TextUnit, color: Color) {
    // 连续累加目标值 (可以 < 0)，用于正确方向的滚动
    val continuousTarget = remember { mutableFloatStateOf(targetDigit.toFloat()) }
    val animValue = remember { Animatable(targetDigit.toFloat()) }
    var prevDigit by remember { mutableIntStateOf(targetDigit) }

    LaunchedEffect(targetDigit) {
        if (targetDigit != prevDigit) {
            // 计算最短滚动路径 (类似 React Counter 的 offset 逻辑)
            val forward = ((targetDigit - prevDigit) + 10) % 10
            val backward = forward - 10
            val step = if (forward < kotlin.math.abs(backward)) forward.toFloat() else backward.toFloat()

            continuousTarget.floatValue += step
            prevDigit = targetDigit
            animValue.animateTo(
                continuousTarget.floatValue,
                spring(dampingRatio = 0.75f, stiffness = 100f),
            )
        }
    }

    val density = LocalDensity.current
    val digitHeightPx = with(density) { fontSize.toPx() * 1.3f }
    val digitHeightDp = with(density) { digitHeightPx.toDp() }
    val charWidthDp = with(density) { (fontSize.toPx() * 0.62f).toDp() }

    Box(
        modifier = Modifier
            .width(charWidthDp)
            .height(digitHeightDp)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val current = animValue.value
        for (number in 0..9) {
            // 与 React 的 Number 组件相同的偏移计算
            val placeDigit = ((current % 10f) + 10f) % 10f
            val rawOffset = ((10f + number - placeDigit) % 10f)
            val offset = if (rawOffset > 5f) rawOffset - 10f else rawOffset
            val yPx = offset * digitHeightPx

            if (kotlin.math.abs(yPx) <= digitHeightPx * 2f) {
                val alpha = (1f - kotlin.math.abs(offset) / 2.5f).coerceIn(0.12f, 1f)
                Text(
                    "$number",
                    fontSize = fontSize,
                    color = color.copy(alpha = alpha),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset { IntOffset(0, yPx.toInt()) },
                )
            }
        }
    }
}

// ═══ 时光模式播放器（日落风景背景）═══
@Composable
private fun TimelapsePlayer(
    song: Song,
    viewModel: MusicViewModel,
    rotation: Float,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>
) {
    // 暖色调渐变背景
    val timeBasedGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2A2A2A).copy(alpha = 0.95f),
            Color(0xFF1E1E1E).copy(alpha = 0.9f),
            Color(0xFF252525).copy(alpha = 0.85f),
            Color(0xFF1A1A1A).copy(alpha = 0.95f),
        ),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY
    )

    Box(Modifier.fillMaxSize()) {
        // 背景渐变
        Box(Modifier.fillMaxSize().background(timeBasedGradient))

        // 装饰性圆形（模拟太阳/月亮）
        Box(
            Modifier
                .size(200.dp)
                .offset(y = (-50).dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3A3A3A).copy(alpha = 0.6f),
                            Color(0xFF2A2A2A).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(Modifier.fillMaxSize()) {
            // 顶部栏
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f))
                }
                Spacer(Modifier.width(48.dp))
            }

            // 唱片区域 - 使用透明边框效果
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 外圈光晕
                Box(
                    Modifier
                        .size(340.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )

                // 旋转唱片
                Box(
                    Modifier
                        .size(300.dp)
                        .graphicsLayer { rotationZ = rotation }
                        .background(Color(0xFF2D3748).copy(alpha = 0.8f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.coverUrl.isNotEmpty()) {
                        AsyncImage(
                            song.coverUrl,
                            null,
                            Modifier
                                .size(200.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(80.dp))
                    }
                }

                // 飞鸟装饰（简单的V形图标模拟）
                Row(Modifier.offset(y = (-120).dp)) {
                    repeat(3) { i ->
                        Text(
                            "V",
                            modifier = Modifier.offset(x = (i * 20 - 20).dp),
                            color = Color(0xFF2D3748).copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 底部控制 - 半透明毛玻璃效果
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(Color(0xFF2D3748).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                MinimalBottomControls(viewModel, dur, showQueue)
            }
        }
    }
}

private fun buildShareUrl(song: Song): String {
    val baseUrl = "${LocalStorage.loadShareBaseUrl()}/aura/share"
    val params = mutableListOf<Pair<String, String>>().apply {
        add("platform" to song.platform)
        add("songId" to song.platformId)
        add("title" to song.title)
        add("artist" to song.artist)
        if (song.album.isNotEmpty()) add("album" to song.album)
        if (song.coverUrl.isNotEmpty()) add("cover" to song.coverUrl)
    }
    val queryString = params.joinToString("&") { "${it.first}=${java.net.URLEncoder.encode(it.second, "UTF-8")}" }
    return "$baseUrl?$queryString"
}

// ═══ 青春彩胶播放器（透明唱片+弥散烟雾）═══
@Composable
private fun ColorVinylPlayer(
    song: Song,
    viewModel: MusicViewModel,
    rotation: Float,
    onBack: () -> Unit,
    dur: Long,
    showQueue: MutableState<Boolean>
) {
    val coverColors = rememberCoverColors(song.coverUrl)

    Box(Modifier.fillMaxSize()) {
        // 基于封面的多色渐变背景
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to coverColors.vibrant.copy(alpha = 0.35f),
                        0.5f to coverColors.base.copy(alpha = 0.25f),
                        1f to coverColors.secondary.copy(alpha = 0.30f),
                    )
                )
        )

        // 弥散烟雾效果（多层模糊圆形）
        val infiniteTransition = rememberInfiniteTransition(label = "smoke")
        val smokeOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
            label = "smoke"
        )

        // 烟雾层1 - 使用封面鲜艳色
        Box(
            Modifier
                .size(400.dp)
                .offset(x = (-100).dp, y = (50 + smokeOffset * 20).dp)
                .alpha(0.3f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coverColors.vibrant.copy(alpha = 0.5f),
                            coverColors.accent.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        // 烟雾层2 - 使用封面次要色
        Box(
            Modifier
                .size(350.dp)
                .offset(x = 150.dp, y = (100 - smokeOffset * 15).dp)
                .alpha(0.25f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coverColors.secondary.copy(alpha = 0.4f),
                            coverColors.base.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(Modifier.fillMaxSize()) {
            // 顶部栏
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f))
                }
                Spacer(Modifier.width(48.dp))
            }

            // 透明唱片区域
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 外圈彩虹光晕
                Box(
                    Modifier
                        .size(320.dp)
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF2A2A2A).copy(alpha = 0.3f),
                                    Color(0xFF333333).copy(alpha = 0.3f),
                                    Color(0xFF3A3A3A).copy(alpha = 0.3f),
                                    Color(0xFF2E2E2E).copy(alpha = 0.3f),
                                    Color(0xFF2A2A2A).copy(alpha = 0.3f),
                                )
                            ),
                            CircleShape
                        )
                        .graphicsLayer { rotationZ = rotation * 0.5f }
                )

                // 透明质感唱片
                Box(
                    Modifier
                        .size(280.dp)
                        .graphicsLayer { rotationZ = rotation }
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // 封面
                    if (song.coverUrl.isNotEmpty()) {
                        AsyncImage(
                            song.coverUrl,
                            null,
                            Modifier
                                .size(180.dp)
                                .clip(CircleShape)
                                .border(4.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // 内圈高光
                    Box(
                        Modifier
                            .size(160.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    ) {}
                }
            }

            // 底部控制 - 半透明毛玻璃效果
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(Color(0xFF2D3748).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                MinimalBottomControls(viewModel, dur, showQueue)
            }
        }
    }
}
