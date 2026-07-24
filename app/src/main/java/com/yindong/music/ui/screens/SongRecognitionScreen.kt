package com.yindong.music.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel

@Composable
fun SongRecognitionScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accentColor = Color(0xFF007AFF)
    val accent = accentColor
    val cardColor = tertiaryBackground
    val subtleBorder = separator

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        permissionRequested = true
        if (granted) {
            viewModel.initAudioFingerprintGenerator()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission && !permissionRequested) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            permissionRequested = true
        } else if (hasAudioPermission) {
            viewModel.initAudioFingerprintGenerator()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(systemBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "听歌识曲",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = labelPrimary,
            )
        }

        if (!hasAudioPermission) {
            PermissionDeniedContent(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        } else {
            RecognitionContent(viewModel = viewModel)
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val accent = accentColor
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = accent.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "需要麦克风权限",
                style = MaterialTheme.typography.titleMedium,
                color = labelPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "听歌识曲需要使用麦克风录制音频",
                style = MaterialTheme.typography.bodyMedium,
                color = labelSecondary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .clickable { onRequestPermission() }
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "授权麦克风",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RecognitionContent(viewModel: MusicViewModel) {
    val state = viewModel.recognizeState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is MusicViewModel.RecognizeState.Idle -> {
                IdleContent(onStart = { viewModel.startSongRecognition() })
            }
            is MusicViewModel.RecognizeState.Recording -> {
                RecordingContent()
            }
            is MusicViewModel.RecognizeState.GeneratingFP -> {
                ProcessingContent("正在生成音频指纹...")
            }
            is MusicViewModel.RecognizeState.Recognizing -> {
                ProcessingContent("正在识别歌曲...")
            }
            is MusicViewModel.RecognizeState.Success -> {
                SuccessContent(
                    songs = state.songs,
                    onPlay = { song -> viewModel.playRecognizedSong(song) },
                    onRetry = { viewModel.resetRecognition() },
                    onSearch = { song ->
                        val query = "${song.song.name} ${song.song.artists.joinToString(" ") { it.name }}"
                        viewModel.updateSearchQuery(query)
                        viewModel.resetRecognition()
                    }
                )
            }
            is MusicViewModel.RecognizeState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.resetRecognition() },
                )
            }
        }
    }
}

@Composable
private fun IdleContent(onStart: () -> Unit) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accent = accentColor
    val cardColor = tertiaryBackground
    val subtleBorder = separator
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "听歌识曲",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = labelPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "识别身边正在播放的歌曲",
            style = MaterialTheme.typography.bodyLarge,
            color = labelSecondary,
        )
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(140.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accent, accent.copy(alpha = 0.7f))
                    )
                )
                .clickable { onStart() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "开始识别",
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "点击开始识别",
            style = MaterialTheme.typography.bodyMedium,
            color = labelSecondary,
        )
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(cardColor)
                .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                "使用提示",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = labelPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TipItem("将手机靠近音源，确保声音清晰")
            TipItem("录制 3 秒音频即可识别")
            TipItem("热门歌曲识别率更高")
            TipItem("安静环境下效果更好")
        }
    }
}

@Composable
private fun TipItem(text: String) {
    val accentColor = Color(0xFF007AFF)
    val labelSecondary = if (isDarkTheme()) Color(0xFFAEAEB2) else Color(0xFF636366)
    val accent = accentColor
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("•", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = labelSecondary)
    }
}

@Composable
private fun RecordingContent() {
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDarkTheme()) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDarkTheme()) Color(0xFFAEAEB2) else Color(0xFF636366)
    val accent = accentColor
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.5f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = accent,
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "正在录制音频...",
            style = MaterialTheme.typography.titleMedium,
            color = labelPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "请保持手机靠近音源",
            style = MaterialTheme.typography.bodyMedium,
            color = labelSecondary,
        )
    }
}

@Composable
private fun ProcessingContent(message: String) {
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDarkTheme()) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDarkTheme()) Color(0xFFAEAEB2) else Color(0xFF636366)
    val accent = accentColor
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = accent,
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = labelPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "请稍候...",
            style = MaterialTheme.typography.bodyMedium,
            color = labelSecondary,
        )
    }
}

@Composable
private fun SuccessContent(
    songs: List<MusicViewModel.RecognizedSong>,
    onPlay: (MusicViewModel.RecognizedSong) -> Unit,
    onRetry: () -> Unit,
    onSearch: (MusicViewModel.RecognizedSong) -> Unit,
) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accent = accentColor
    val cardColor = tertiaryBackground
    val surfaceColor = secondaryBackground
    val subtleBorder = separator
    val accentSoftBg = accent.copy(alpha = 0.08f)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "识别成功！找到 ${songs.size} 首可能的歌曲",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(songs, key = { _, it -> it.song.id }, contentType = { _, _ -> "song" }) { index, song ->
                RecognizedSongItem(
                    index = index,
                    song = song,
                    onPlay = { onPlay(song) },
                    onSearch = { onSearch(song) },
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = subtleBorder,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor)
                .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
                .clickable { onRetry() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, null, tint = labelSecondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("再试一次", color = labelSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RecognizedSongItem(
    index: Int,
    song: MusicViewModel.RecognizedSong,
    onPlay: () -> Unit,
    onSearch: () -> Unit,
) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accent = accentColor
    val cardColor = tertiaryBackground
    val surfaceColor = secondaryBackground
    val subtleBorder = separator
    val accentSoftBg = accent.copy(alpha = 0.08f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(surfaceColor)
                .border(1.dp, subtleBorder, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val coverUrl = song.song.album?.picUrl
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = song.song.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.Album,
                    contentDescription = null,
                    tint = labelTertiary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                song.song.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                song.song.artists.joinToString(" / ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    "#${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${(song.score * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (song.score > 0.8) Color.White else if (song.score > 0.5) labelSecondary else labelSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Row {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentSoftBg)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(surfaceColor)
                    .clickable { onSearch() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = labelPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = labelTertiary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "识别失败",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = labelPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = labelSecondary,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(cs.error)
                .clickable { onRetry() }
                .padding(horizontal = 32.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("再试一次", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
