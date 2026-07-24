package com.yindong.music.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import com.yindong.music.data.api.MusicApiConfig
import com.yindong.music.data.CrashLogEntry
import com.yindong.music.data.DeviceStatus
import com.yindong.music.data.LocalStorage
import com.yindong.music.data.PlaylistSyncManager
import com.yindong.music.data.LogLevel
import com.yindong.music.data.RemoteConfig
import com.yindong.music.data.UsbDeviceState
import com.yindong.music.data.UsbLogEntry
import coil.compose.AsyncImage
import com.yindong.music.data.model.Playlist
import com.yindong.music.data.model.Song
import com.yindong.music.security.CriticalUiProtector
import com.yindong.music.ui.components.SleepTimerDialog
import com.yindong.music.ui.components.SongItem
import com.yindong.music.ui.theme.CoverGradients
import com.yindong.music.ui.theme.AllColorSchemes
import com.yindong.music.ui.theme.ThemeManager
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.util.BatteryOptimizationHelper
import com.yindong.music.viewmodel.MusicViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun MineScreen(
    viewModel: MusicViewModel,
    onSearchClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onPlaylistClick: (Long) -> Unit,
    onAiAudioEffectClick: () -> Unit = {},
    onImportPlaylistClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
) {
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var showPluginManager by remember { mutableStateOf(false) }
    var showLxPluginDialog by remember { mutableStateOf(false) }
    var showMusicFreeDialog by remember { mutableStateOf(false) }
    var showApiConfig by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showCarLyricSettings by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var viewingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deleteConfirm by remember { mutableStateOf<Playlist?>(null) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }

    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val themeScope = rememberCoroutineScope()

    var isDark by remember { mutableStateOf(ThemeManager.isDark) }
    var selectedSchemeId by remember { mutableStateOf(ThemeManager.getSavedColorSchemeId(context)) }
    var dynamicColorEnabled by remember { mutableStateOf(ThemeManager.getSavedDynamicColorEnabled(context)) }
    LaunchedEffect(Unit) { ThemeManager.isDarkMode.collectLatest { dark -> isDark = dark } }
    LaunchedEffect(Unit) {
        ThemeManager.colorSchemeState.collectLatest { scheme ->
            selectedSchemeId = scheme.id
        }
    }
    LaunchedEffect(Unit) {
        ThemeManager.dynamicColorEnabled.collectLatest { enabled ->
            dynamicColorEnabled = enabled
        }
    }

    // 从搜索页跳转：自动打开落雪插件对话框
    LaunchedEffect(viewModel.pendingOpenLxPlugin) {
        if (viewModel.pendingOpenLxPlugin) {
            showLxPluginDialog = true
            viewModel.consumePendingOpenLxPlugin()
        }
    }

    if (showSettings) SettingsDialog(viewModel) { showSettings = false }
    if (showCreatePlaylist) CreatePlaylistDialog(viewModel) { showCreatePlaylist = false }
    if (showPluginManager) PluginManagerDialog(viewModel, onBack = { showPluginManager = false })
    if (showLxPluginDialog) LxPluginImportDialog(viewModel, onBack = { showLxPluginDialog = false })
    if (showMusicFreeDialog) MusicFreePluginImportDialog(viewModel, onBack = { showMusicFreeDialog = false })
    if (showApiConfig) ApiConfigDialog(viewModel, onDismiss = { showApiConfig = false }, onOpenPluginManager = { showApiConfig = false; showPluginManager = true })
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
    if (showSleepTimer) SleepTimerDialog(viewModel) { showSleepTimer = false }
    if (showCacheDialog) CacheDialog(viewModel) { showCacheDialog = false }
    if (showOrientationDialog) OrientationDialog(onDismiss = { showOrientationDialog = false })
    if (showCarLyricSettings) CarLyricSettingsDialog(viewModel = viewModel, onDismiss = { showCarLyricSettings = false })
    if (showThemeModeDialog) ThemeModeDialog(onDismiss = { showThemeModeDialog = false })
    if (showSyncDialog) PlaylistSyncDialog(viewModel = viewModel, onDismiss = { showSyncDialog = false })

    deleteConfirm?.let { pl ->
        AppleCenterDialog(
            onDismiss = { deleteConfirm = null },
            title = "删除歌单",
            destructive = true,
            primaryLabel = "删除",
            onPrimary = { viewModel.deletePlaylist(pl.id); deleteConfirm = null; if (viewingPlaylist?.id == pl.id) viewingPlaylist = null },
            secondaryLabel = "取消",
            onSecondary = { deleteConfirm = null },
        ) {
            Text("确定删除「${pl.name}」？")
        }
    }
    songToDelete?.let { song ->
        AppleCenterDialog(
            onDismiss = { songToDelete = null },
            title = "从歌单删除",
            destructive = true,
            primaryLabel = "移除",
            onPrimary = {
                viewingPlaylist?.let { pl -> viewModel.removeSongFromPlaylist(pl.id, song) }
                songToDelete = null
            },
            secondaryLabel = "取消",
            onSecondary = { songToDelete = null },
        ) {
            Text("确定移除歌曲「${song.title}」？")
        }
    }

    if (showFavorites) {
        val clipboardManager = LocalClipboardManager.current
        var showAddFavoriteDialog by remember { mutableStateOf(false) }
        var favTitle by remember { mutableStateOf("") }
        var favArtist by remember { mutableStateOf("") }
        var showImportFavoritesDialog by remember { mutableStateOf(false) }
        var importFavoritesText by remember { mutableStateOf("") }
        var showDeleteFavoriteConfirm by remember { mutableStateOf<Song?>(null) }

        if (showAddFavoriteDialog) {
            AppleCenterDialog(
                onDismiss = { showAddFavoriteDialog = false },
                title = "添加到收藏",
                primaryLabel = "添加",
                onPrimary = { viewModel.addFavoriteManual(favTitle, favArtist); showAddFavoriteDialog = false; favTitle = ""; favArtist = "" },
                secondaryLabel = "取消",
                onSecondary = { showAddFavoriteDialog = false },
            ) {
                Column {
                    OutlinedTextField(value = favTitle, onValueChange = { favTitle = it }, label = { Text("歌名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = favArtist, onValueChange = { favArtist = it }, label = { Text("歌手") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
        }

        if (showImportFavoritesDialog) {
            AppleCenterDialog(
                onDismiss = { showImportFavoritesDialog = false },
                title = "导入收藏",
                primaryLabel = "导入",
                onPrimary = { if (importFavoritesText.isNotBlank()) { viewModel.importFavoritesFromText(importFavoritesText); showImportFavoritesDialog = false; importFavoritesText = "" } },
                secondaryLabel = "取消",
                onSecondary = { showImportFavoritesDialog = false },
            ) {
                Column {
                    Text("粘贴收藏列表内容：", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = importFavoritesText, onValueChange = { importFavoritesText = it }, label = { Text("粘贴收藏内容") }, modifier = Modifier.fillMaxWidth().height(200.dp), maxLines = 20)
                }
            }
        }

        showDeleteFavoriteConfirm?.let { song ->
            AppleCenterDialog(
                onDismiss = { showDeleteFavoriteConfirm = null },
                title = "删除收藏",
                destructive = true,
                primaryLabel = "移除",
                onPrimary = { viewModel.removeFavorite(song); showDeleteFavoriteConfirm = null },
                secondaryLabel = "取消",
                onSecondary = { showDeleteFavoriteConfirm = null },
            ) {
                Text("确定从收藏移除「${song.title}」？")
            }
        }

        LaunchedEffect(Unit) { viewModel.fetchMissingFavCovers() }

        SubPage("我的收藏", "${viewModel.favorites.size} 首", onBack = { showFavorites = false },
            onPlayAll = if (viewModel.favorites.isNotEmpty()) {{ viewModel.playAllFromList(viewModel.favorites) }} else null,
            viewModel = viewModel,
            onAddMusic = { showAddFavoriteDialog = true },
            onCopyPlaylist = {
                val exportText = viewModel.exportFavoritesAsText()
                if (exportText.isBlank()) { viewModel.showToast("没有收藏可导出") }
                else { clipboardManager.setText(AnnotatedString(exportText)); viewModel.showToast("已复制到剪贴板") }
            },
        ) {
            if (viewModel.favorites.isEmpty()) EmptyHint(Icons.Default.FavoriteBorder, "还没有收藏", "播放时点 ♡ 收藏歌曲")
            else {
                LazyColumn {
                    itemsIndexed(viewModel.favorites, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song ->
                        SongItem(song = song, onSongClick = { viewModel.playPlaylist(viewModel.favorites, index) }, onMoreClick = { showDeleteFavoriteConfirm = song })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { showImportFavoritesDialog = true }) {
                        Icon(Icons.Default.Download, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导入", color = cs.primary)
                    }
                }
            }
        }
        return
    }

    if (showHistory) {
        SubPage("最近播放", "${viewModel.playHistory.size} 首", onBack = { showHistory = false },
            onPlayAll = if (viewModel.playHistory.isNotEmpty()) {{ viewModel.playAllFromList(viewModel.playHistory) }} else null,
            viewModel = viewModel,
        ) {
            if (viewModel.playHistory.isEmpty()) EmptyHint(Icons.Default.History, "还没有播放记录", "搜索歌曲开始播放吧")
            else LazyColumn { itemsIndexed(viewModel.playHistory, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song -> SongItem(song = song, onSongClick = { viewModel.playPlaylist(viewModel.playHistory, index) }) } }
        }
        return
    }

    viewingPlaylist?.let { pl ->
        val latest = viewModel.getUserPlaylistById(pl.id) ?: pl
        val clipboardManager = LocalClipboardManager.current
        var showCopyDialog by remember { mutableStateOf(false) }
        if (showCopyDialog) {
            val copyText = remember(latest) {
                buildString {
                    appendLine("🎵 ${latest.name}")
                    appendLine("---")
                    latest.songs.forEachIndexed { index, song -> appendLine("${index + 1}. ${song.title} - ${song.artist}") }
                }
            }
            AppleCenterDialog(
                onDismiss = { showCopyDialog = false },
                title = "复制歌单",
                primaryLabel = "复制",
                onPrimary = { clipboardManager.setText(AnnotatedString(copyText)); viewModel.showToast("歌单已复制到剪贴板"); showCopyDialog = false },
                secondaryLabel = "关闭",
                onSecondary = { showCopyDialog = false },
            ) {
                Column {
                    Text("歌单内容已生成，点击复制：", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)).background(if (isDarkTheme()) Color(0xFF141414) else Color(0xFFF0F1F3)).padding(12.dp)) {
                        Text(copyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        SubPage(latest.name, "${latest.songs.size} 首", onBack = { viewingPlaylist = null },
            onPlayAll = if (latest.songs.isNotEmpty()) {{ viewModel.playAllFromList(latest.songs) }} else null,
            viewModel = viewModel,
            onAddMusic = onSearchClick,
            onCopyPlaylist = { showCopyDialog = true }
        ) {
            if (latest.songs.isEmpty()) EmptyHint(Icons.Default.List, "歌单还是空的", "点击右上角 + 去搜索添加")
            else LazyColumn {
                itemsIndexed(latest.songs, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, song ->
                    SongItem(song = song, onSongClick = { viewModel.playPlaylist(latest.songs, index) }, onMoreClick = { songToDelete = song })
                }
            }
        }
        return
    }

    // ═══ 主页面 — Apple 设计风格 ═══
    val isDarkTheme = isDarkTheme()
    // Apple 系统颜色
    val systemBackground = if (isDarkTheme) Color(0xFF141414) else Color(0xFFF8F9FA)
    val secondaryBackground = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tertiaryBackground = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val cardColor = tertiaryBackground
    val subtleBorder = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val heroSurface = secondaryBackground
    val accentColor = Color(0xFF007AFF) // Apple System Blue
    val labelPrimary = if (isDarkTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    LazyColumn(
        Modifier.fillMaxSize().background(systemBackground).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 180.dp),
    ) {
        // ── 顶部用户卡片 (Apple 风格大标题) ──
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    "设置",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = labelPrimary,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 40.sp,
                )
                Text(
                    "个性化你的音乐体验",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = labelSecondary,
                    letterSpacing = (-0.2).sp,
                )
            }
        }

        // ── Apple 风格统计卡片 (Inset Grouped) ──
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(secondaryBackground),
            ) {
                AppleStatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.History,
                    label = "最近播放",
                    count = "${viewModel.playHistory.size}",
                    accentColor = accentColor,
                    labelPrimary = labelPrimary,
                    labelSecondary = labelSecondary,
                    onClick = { showHistory = true },
                )
                Box(
                    Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .padding(vertical = 16.dp)
                        .background(subtleBorder),
                )
                AppleStatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Favorite,
                    label = "我的收藏",
                    count = "${viewModel.favorites.size}",
                    accentColor = Color(0xFFFF3B30),
                    labelPrimary = labelPrimary,
                    labelSecondary = labelSecondary,
                    onClick = { showFavorites = true },
                )
            }
        }

        // ── 歌单云同步 ──
        item {
            ModernSectionLabel("数据同步")
            ModernSettingsCard {
                val syncUserId = PlaylistSyncManager.getSyncUserId()
                ModernSettingsRow(
                    Icons.Default.CloudSync, "歌单云同步",
                    if (syncUserId.isNotEmpty()) "已登录 · ID: $syncUserId" else "点击登录或注册",
                ) { showSyncDialog = true }
            }
        }

        // ── 主题设置区 ──
        item {
            ModernSectionLabel("外观主题")
            ModernSettingsCard {
                // 主题模式：浅色/深色/跟随系统
                val themeModeLabel = when (ThemeManager.themeMode.value) {
                    com.yindong.music.ui.theme.ThemeMode.LIGHT -> "浅色模式"
                    com.yindong.music.ui.theme.ThemeMode.DARK -> "深色模式"
                    com.yindong.music.ui.theme.ThemeMode.SYSTEM -> "跟随系统"
                }
                ModernSettingsRow(
                    icon = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    title = "界面主题",
                    subtitle = themeModeLabel,
                ) { showThemeModeDialog = true }
                ModernSettingsDivider()
                // 动态主题色：随封面自动调整
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = accentColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "封面动态配色",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = cs.onBackground,
                        )
                        Text(
                            if (dynamicColorEnabled) "已开启 · 主题色随当前封面变化" else "关闭 · 使用固定主题色",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onBackground.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = dynamicColorEnabled,
                        onCheckedChange = { enabled ->
                            ThemeManager.setDynamicColorEnabled(context, enabled)
                            dynamicColorEnabled = enabled
                            // 开启后立即基于当前播放歌曲封面应用一次
                            if (enabled) {
                                viewModel.currentSong?.coverUrl?.takeIf { it.isNotBlank() }
                                    ?.let { url ->
                                        themeScope.launch {
                                            ThemeManager.updateDynamicTheme(context, url)
                                        }
                                    }
                            }
                        },
                    )
                }
                ModernSettingsDivider()
                // 配色方案
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Palette, null, tint = accentColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "配色方案",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = cs.onBackground,
                        )
                        Text(
                            if (dynamicColorEnabled) "已由动态配色接管 · 选择将关闭动态色"
                            else AllColorSchemes.find { it.id == selectedSchemeId }?.name ?: "默认",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dynamicColorEnabled) accentColor.copy(alpha = 0.8f) else cs.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AllColorSchemes.forEach { scheme ->
                        val isSelected = selectedSchemeId == scheme.id
                        // 动态色开启时降低调色板透明度，提示当前非激活态
                        val paletteAlpha = if (dynamicColorEnabled) 0.45f else 1f
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(if (isSelected && !dynamicColorEnabled) accentColor else Color.Transparent)
                                .padding(3.dp).clickable { ThemeManager.setColorScheme(context, scheme.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(scheme.previewColor.copy(alpha = paletteAlpha)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected && !dynamicColorEnabled) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ModernSettingsDivider()
                // 屏幕方向设置
                val orientationLabel = when (LocalStorage.loadScreenOrientation()) {
                    "PORTRAIT" -> "竖屏"
                    "LANDSCAPE" -> "横屏"
                    "REVERSE_LANDSCAPE" -> "反向横屏"
                    else -> "跟随系统"
                }
                ModernSettingsRow(
                    icon = Icons.Default.Settings,
                    title = "屏幕方向",
                    subtitle = orientationLabel,
                ) { showOrientationDialog = true }
                ModernSettingsDivider()
                // 车载歌词开关：开启后通知栏播放器标题位与车机蓝牙显示实时歌词
                ModernSettingsSwitchRow(
                    icon = Icons.Default.MusicNote,
                    title = "车载歌词",
                    subtitle = if (viewModel.carBtLyricsEnabled) "通知栏/车机显示实时歌词" else "关闭",
                    checked = viewModel.carBtLyricsEnabled,
                ) { viewModel.toggleCarBtLyrics() }
                ModernSettingsDivider()
                ModernSettingsRow(
                    icon = Icons.Default.Settings,
                    title = "车载歌词设置",
                    subtitle = "同步偏移 / 字体 / 悬浮窗",
                ) { showCarLyricSettings = true }
            }
        }
        item {
            ModernSectionLabel("音乐")
            ModernSettingsCard {
                ModernSettingsRow(Icons.Default.Equalizer, "AI 音效中心", "空间声场", onClick = onAiAudioEffectClick)
                ModernSettingsDivider()
                ModernSettingsRow(Icons.Default.Download, "导入歌单", "从文本导入歌单", onClick = onImportPlaylistClick)
                ModernSettingsDivider()
                CriticalButtonMarker("mine_download", "下载管理")
                ModernSettingsRow(Icons.Default.Download, "下载管理", "${viewModel.downloadedSongs.size} 首已下载", onClick = onDownloadsClick)
                ModernSettingsDivider()
                val sleepSubtitle = if (viewModel.isSleepTimerRunning) {
                    val totalSec = (viewModel.sleepTimerMs / 1000).toInt()
                    "剩余 ${totalSec / 60}:${"%02d".format(totalSec % 60)}"
                } else "定时停止播放"
                ModernSettingsRow(Icons.Default.Timer, "睡眠定时器", sleepSubtitle) { showSleepTimer = true }
            }
        }

        // ── 音源与插件区 ──
        item {
            ModernSectionLabel("音源与插件")
            ModernSettingsCard {
                CriticalButtonMarker("mine_api", "API设置")
                ModernSettingsRow(
                    Icons.Default.Settings, "API 设置",
                    if (viewModel.apiMode == "lx_plugin") {
                        if (viewModel.lxSelectedSource == MusicViewModel.LX_SOURCE_ALL) "落雪插件 · 全部音源"
                        else "落雪插件 · ${viewModel.lxSelectedSource.ifBlank { "未选择" }}"
                    } else "官方 API 模式",
                ) { showApiConfig = true }
                ModernSettingsDivider()
                ModernSettingsRow(
                    Icons.Default.Code, "插件管理",
                    if (viewModel.lxPlugins.isEmpty()) "未导入插件"
                    else "已加载 ${viewModel.lxPlugins.size} 个 · ${viewModel.lxPlugins.count { viewModel.isPluginEnabled(it.id) }} 启用",
                ) { showPluginManager = true }
                ModernSettingsDivider()
                ModernSettingsRow(
                    Icons.Default.MusicNote,
                    title = {
                        Text(
                            "落雪插件",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
                            style = MaterialTheme.typography.titleMedium.copy(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF6B5B95),
                                        Color(0xFF007AFF),
                                        Color(0xFF34C759),
                                        Color(0xFFFF9500),
                                        Color(0xFFFF2D55),
                                        Color(0xFF6B5B95),
                                    ),
                                ),
                            ),
                        )
                    },
                    if (viewModel.lxPlugins.isEmpty()) "未导入" else "${viewModel.lxPlugins.size} 个已导入",
                ) { showLxPluginDialog = true }
                ModernSettingsDivider()
                // ── MusicFree 插件入口（清晰可见） ──
                CriticalButtonMarker("mine_musicfree", "MusicFree插件")
                ModernSettingsRow(
                    Icons.Default.CloudDownload,
                    title = {
                        Text(
                            "MusicFree 插件",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
                            color = Color(0xFF34C759),
                        )
                    },
                    if (viewModel.musicFreePlugins.isEmpty()) "点击导入插件"
                    else "已加载 ${viewModel.musicFreePlugins.size} 个 · ${viewModel.musicFreePlugins.count { it.enabled }} 启用",
                ) { showMusicFreeDialog = true }
            }
            if (viewModel.musicFreePlugins.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                viewModel.musicFreePlugins.forEach { plugin -> ModernMusicFreePluginListRow(plugin, viewModel) }
            }
        }

        // ── 存储区 ──
        item {
            ModernSectionLabel("存储")
            ModernSettingsCard {
                ModernSettingsRow(Icons.Default.DeleteSweep, "音乐缓存", "查看和清理本地缓存") { showCacheDialog = true }
            }
        }

        // ── 我的歌单区(全新设计) ──
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.List, null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "我的歌单",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = cs.onBackground,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${viewModel.userPlaylists.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onBackground.copy(alpha = 0.6f),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                        .background(cardColor).border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
                        .clickable { onImportPlaylistClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Download, null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                        .background(accentColor).clickable { showCreatePlaylist = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (viewModel.userPlaylists.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
                        .background(heroSurface).border(1.dp, subtleBorder, RoundedCornerShape(24.dp)).padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(accentColor.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.List, null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "点击 + 创建你的第一个歌单",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onBackground.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        } else {
            items(viewModel.userPlaylists, key = { it.id }, contentType = { "playlist" }) { pl ->
                ModernPlaylistRow(pl, onClick = { viewingPlaylist = pl }, onDelete = { deleteConfirm = pl })
            }
        }

        // ── 关于区 ──
        item {
            Spacer(Modifier.height(24.dp))
            ModernSectionLabel("关于")
            ModernSettingsCard {
                ModernSettingsRow(Icons.Default.Info, "版本信息", "v${viewModel.currentVersion}", showChevron = false) {}
                ModernSettingsDivider()
                val activity = context as? android.app.Activity
                ModernSettingsRow(Icons.Default.PhonelinkErase, "后台播放", if (viewModel.isPlaying) "播放中" else "最小化到后台") { activity?.moveTaskToBack(true) }
                ModernSettingsDivider()
                // 电池优化白名单
                val batteryGranted = remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }
                ModernSettingsRow(
                    Icons.Default.BatteryFull,
                    "电池白名单",
                    if (batteryGranted.value) "已加入" else "未加入，点击申请",
                ) {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    batteryGranted.value = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                }
                ModernSettingsDivider()
                ModernSettingsRow(Icons.Default.Settings, "更多设置", "音质、歌词、Cookie 等") { showSettings = true }
                ModernSettingsDivider()
                ModernSettingsRow(Icons.Default.Info, "关于应用", "v3.0.4") { showAbout = true }
            }
            Spacer(Modifier.height(12.dp))
            OfficialCommunityEntryRow(viewModel = viewModel, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), showSubtitle = true)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
                    .background(heroSurface).border(1.dp, subtleBorder, RoundedCornerShape(24.dp)).padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Info, null, tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "免责声明",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onBackground,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "• 本应用音乐资源来自互联网公开接口，不存储任何音频文件\n" +
                        "• 仅供个人学习试听，请支持正版音乐\n" +
                        "• 版权归原作者及平台所有，侵权请联系删除\n" +
                        "• 使用本应用产生的法律责任由用户自行承担",
                        style = MaterialTheme.typography.bodySmall, color = cs.onBackground.copy(alpha = 0.5f), lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// ═══ 全新组件 ═══

@Composable
private fun CriticalButtonMarker(key: String, label: String) {
    LaunchedEffect(key, label) {
        if (key.isNotBlank()) { CriticalUiProtector.markCriticalButtonRendered(key, label) }
    }
}

@Composable
private fun ModernSectionLabel(text: String) {
    val isDark = isDarkTheme()
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = labelSecondary,
        letterSpacing = (-0.1).sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun ModernSettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val isDark = isDarkTheme()
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp)).background(secondaryBackground),
    ) { content() }
}

@Composable
private fun ModernSettingsDivider() {
    val isDark = isDarkTheme()
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    HorizontalDivider(
        Modifier.padding(start = 70.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = separator,
    )
}

@Composable
private fun ModernSettingsRow(icon: ImageVector, title: String, subtitle: String = "", showChevron: Boolean = true, onClick: () -> Unit) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = labelPrimary,
                letterSpacing = (-0.2).sp,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = labelSecondary,
                )
            }
        }
        if (showChevron) {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = labelTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ModernSettingsRow(icon: ImageVector, title: @Composable () -> Unit, subtitle: String = "", showChevron: Boolean = true, onClick: () -> Unit) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            title()
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = labelSecondary,
                )
            }
        }
        if (showChevron) {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = labelTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String = "", onClick: () -> Unit) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = labelPrimary,
                letterSpacing = (-0.2).sp,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = labelSecondary,
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight, null,
            tint = labelTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ModernSettingsSwitchRow(icon: ImageVector, title: String, subtitle: String = "", checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = labelPrimary,
                letterSpacing = (-0.2).sp,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = labelSecondary,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
        )
    }
}

@Composable
private fun AppleStatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    count: String,
    accentColor: Color,
    labelPrimary: Color,
    labelSecondary: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            count,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = labelPrimary,
            letterSpacing = (-0.3).sp,
        )
        Text(
            label,
            fontSize = 12.sp,
            color = labelSecondary,
        )
    }
}

@Composable
private fun ModernStatTile(modifier: Modifier, icon: ImageVector, label: String, count: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accentColor = Color(0xFF007AFF)
    Row(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.background.copy(alpha = if (isDarkTheme()) 0.4f else 0.6f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                count,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ModernPluginListRow(plugin: com.yindong.music.data.lx.PluginEntry, viewModel: MusicViewModel) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val cardColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val subtleBorder = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accentColor = Color(0xFF007AFF)
    val enabled = viewModel.isPluginEnabled(plugin.id)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(cardColor)
            .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(7.dp))
                .background(if (enabled) accentColor else cs.onBackground.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Code, null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plugin.info.name.ifBlank { "未知插件" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) cs.onBackground else cs.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!plugin.initialized) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "初始化失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(cs.error)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                "v${plugin.info.version} · ${plugin.info.author}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { viewModel.togglePluginEnabled(plugin.id) },
            colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
            modifier = Modifier.height(24.dp),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { viewModel.removeLxPluginById(plugin.id) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, "删除", tint = cs.onBackground.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun OfficialCommunityEntryRow(viewModel: MusicViewModel, modifier: Modifier = Modifier, showSubtitle: Boolean = false) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    val joinTitle = RemoteConfig.officialCommunityTitle.ifBlank { CriticalUiProtector.communityEntryTitle() }
    val groupQq = RemoteConfig.officialCommunityQq.ifBlank { CriticalUiProtector.communityQqNumber() }
    val subtitle = RemoteConfig.officialCommunitySubtitle.ifBlank { CriticalUiProtector.communityEntrySubtitle() }
    val joinUrl = RemoteConfig.officialCommunityJoinUrl.ifBlank { CriticalUiProtector.communityJoinUrl() }

    LaunchedEffect(Unit) { CriticalUiProtector.markCommunityEntryRendered() }

    Column(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(accentColor.copy(alpha = 0.12f), accentColor.copy(alpha = 0.04f)),
                ),
            )
            .border(1.dp, accentColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .clickable {
                if (!CriticalUiProtector.canOpenCommunityEntry()) { viewModel.showToast("关键入口完整性校验失败"); return@clickable }
                CriticalUiProtector.markCommunityEntryOpened()
                openQqGroup(context, viewModel, groupQq, joinUrl)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showSubtitle) Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onBackground.copy(alpha = 0.6f),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("👥", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    joinTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                )
                Text(
                    "官方群QQ：$groupQq",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onBackground.copy(alpha = 0.6f),
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = accentColor.copy(0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

private fun openQqGroup(context: Context, viewModel: MusicViewModel, groupQq: String, fallbackUrl: String) {
    val troopUin = CriticalUiProtector.communityQqNumber()
    val joinKey = CriticalUiProtector.communityJoinKey()
    val schemes = listOf(
        "mqqapi://group/join_troop?src_type=internal&version=1&troop_uin=$troopUin&subsource_id=1030&is_need_jump_aio=1",
        CriticalUiProtector.buildJoinQqGroupUrl(joinKey),
        fallbackUrl,
    )
    for ((index, url) in schemes.withIndex()) {
        if (url.isBlank()) continue
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return
        } catch (_: Exception) {
            if (index == schemes.lastIndex) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ群号", groupQq))
                    viewModel.showToast("QQ群号 $groupQq 已复制到剪贴板")
                } catch (_: Exception) { viewModel.showToast("请添加QQ群: $groupQq") }
            }
        }
    }
}

@Composable
private fun ModernPlaylistRow(playlist: Playlist, onClick: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val gradients = CoverGradients
    val colors = remember(playlist.id, gradients) {
        val idx = ((playlist.id % gradients.size).toInt() + gradients.size) % gradients.size
        gradients[idx]
    }
    val songCovers = remember(playlist.songs) { playlist.songs.filter { it.coverUrl.isNotEmpty() }.map { it.coverUrl }.distinct().take(4) }
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist.coverUrl.isNotEmpty()) {
                AsyncImage(model = playlist.coverUrl, contentDescription = playlist.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (songCovers.size >= 4) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(model = songCovers[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(model = songCovers[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(model = songCovers[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(model = songCovers[3], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                }
            } else if (songCovers.isNotEmpty()) {
                AsyncImage(model = songCovers.first(), contentDescription = playlist.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = labelPrimary,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${playlist.songs.size} 首",
                    fontSize = 13.sp,
                    color = labelSecondary,
                )
            }
        }
        IconButton(onClick = onDelete, Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, null, tint = labelTertiary, modifier = Modifier.size(16.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = labelTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SubPage(title: String, subtitle: String, onBack: () -> Unit, onPlayAll: (() -> Unit)? = null, viewModel: MusicViewModel? = null, onAddMusic: (() -> Unit)? = null, onCopyPlaylist: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var showTimer by remember { mutableStateOf(false) }
    if (showTimer && viewModel != null) { SleepTimerDialog(viewModel) { showTimer = false } }
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    Column(Modifier.fillMaxSize().background(systemBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = labelPrimary, letterSpacing = (-0.4).sp)
                if (viewModel?.isSleepTimerRunning == true) {
                    val totalSec = (viewModel.sleepTimerMs / 1000).toInt()
                    Text("定时 ${totalSec / 60}:${"%02d".format(totalSec % 60)}", fontSize = 13.sp, color = accentColor)
                }
                Text(subtitle, fontSize = 13.sp, color = labelSecondary)
            }
            if (viewModel != null) {
                IconButton(onClick = { showTimer = true }) { Icon(Icons.Default.Timer, "定时", tint = if (viewModel.isSleepTimerRunning) accentColor else labelSecondary) }
            }
            if (onCopyPlaylist != null) { IconButton(onClick = onCopyPlaylist) { Icon(Icons.Default.ContentCopy, "复制歌单", tint = labelSecondary) } }
            if (onAddMusic != null) { IconButton(onClick = onAddMusic) { Icon(Icons.Default.Add, "添加音乐", tint = labelSecondary) } }
            TextButton(onClick = onBack) { Text("返回", color = accentColor, fontSize = 16.sp) }
        }
        if (onPlayAll != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp)).background(accentColor).clickable { onPlayAll() }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("全部播放", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun EmptyHint(icon: ImageVector, text: String, sub: String) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(text, fontSize = 16.sp, color = labelSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(sub, fontSize = 13.sp, color = labelTertiary)
        }
    }
}

// ═══ Apple 风格弹窗组件集 ═══

/** Apple 风格底部弹窗：统一替代 Material AlertDialog，与设置页 Inset Grouped 视觉语言一致 */
@Composable
private fun AppleSheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String = "",
    scrollable: Boolean = true,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val accentColor = if (destructive) Color(0xFFFF3B30) else Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onDismiss() },
            )
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(systemBackground),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                        .width(36.dp).height(5.dp).clip(CircleShape).background(separator.copy(alpha = 0.6f)),
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = labelPrimary, letterSpacing = (-0.3).sp)
                    if (subtitle.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, fontSize = 13.sp, color = labelSecondary)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = separator)
                val contentModifier = if (scrollable) {
                    Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
                } else {
                    Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(horizontal = 20.dp, vertical = 16.dp)
                }
                Box(contentModifier) { content() }
                if (primaryLabel != null || secondaryLabel != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = separator)
                    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (secondaryLabel != null) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clickable { onSecondary?.invoke() },
                                contentAlignment = Alignment.Center,
                            ) { Text(secondaryLabel, fontSize = 17.sp, color = labelSecondary) }
                            Box(Modifier.width(0.5.dp).fillMaxHeight().background(separator))
                        }
                        if (primaryLabel != null) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clickable { onPrimary?.invoke() },
                                contentAlignment = Alignment.Center,
                            ) { Text(primaryLabel, fontSize = 17.sp, color = accentColor, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

/** Apple 风格居中弹窗（用于输入类对话框，避免键盘遮挡按钮） */
@Composable
private fun AppleCenterDialog(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String = "",
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val accentColor = if (destructive) Color(0xFFFF3B30) else Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().imePadding()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onDismiss() },
            )
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 40.dp)
                    .clip(RoundedCornerShape(20.dp)).background(systemBackground),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = labelPrimary, letterSpacing = (-0.3).sp)
                    if (subtitle.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, fontSize = 13.sp, color = labelSecondary)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = separator)
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) { content() }
                if (primaryLabel != null || secondaryLabel != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = separator)
                    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (secondaryLabel != null) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clickable { onSecondary?.invoke() },
                                contentAlignment = Alignment.Center,
                            ) { Text(secondaryLabel, fontSize = 17.sp, color = labelSecondary) }
                            Box(Modifier.width(0.5.dp).fillMaxHeight().background(separator))
                        }
                        if (primaryLabel != null) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clickable { onPrimary?.invoke() },
                                contentAlignment = Alignment.Center,
                            ) { Text(primaryLabel, fontSize = 17.sp, color = accentColor, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

/** Apple 风格选项行（带勾选标记），用于弹窗内的单选列表 */
@Composable
private fun AppleSheetOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String = "",
    leading: @Composable (() -> Unit)? = null,
) {
    val isDark = isDarkTheme()
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 16.sp, color = labelPrimary, letterSpacing = (-0.2).sp)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 13.sp, color = labelSecondary)
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
    }
}

/** Apple 风格弹窗内分隔线（全宽） */
@Composable
private fun AppleSheetSeparator() {
    val isDark = isDarkTheme()
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    HorizontalDivider(thickness = 0.5.dp, color = separator)
}

@Composable
private fun CacheDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppleSheet(
        onDismiss = onDismiss,
        title = "音乐缓存管理",
        primaryLabel = "关闭",
        onPrimary = onDismiss,
    ) {
        Column {
            val cacheSize = viewModel.getCacheSize()
            Text("当前缓存大小: $cacheSize", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("缓存包括:", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text("• 歌曲音频文件", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text("• 专辑封面图片", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text("• 歌词文件", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { viewModel.clearCache(); viewModel.showToast("缓存已清理"); onDismiss() }) { Text("清理缓存") }
            }
        }
    }
}

// ═══ 设置对话框 ═══
@Composable
private fun SettingsDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    // 子页面状态
    var showCrashLogs by remember { mutableStateOf(false) }
    var showCrashDetail by remember { mutableStateOf<CrashLogEntry?>(null) }
    var showPluginManager by remember { mutableStateOf(false) }

    // 子对话框状态
    var showQualityDialog by remember { mutableStateOf(false) }
    var showPlayerStyleDialog by remember { mutableStateOf(false) }
    var showLyricColorDialog by remember { mutableStateOf(false) }
    var showCurrentColorDialog by remember { mutableStateOf(false) }
    var showNormalColorDialog by remember { mutableStateOf(false) }
    var showLyricFontSizeDialog by remember { mutableStateOf(false) }
    var showLyricSizeDialog by remember { mutableStateOf(false) }
    var showQQCookieDialog by remember { mutableStateOf(false) }
    var showImportPlaylistDialog by remember { mutableStateOf(false) }
    var showExportAllPlaylistsDialog by remember { mutableStateOf(false) }
    var showExportFavoritesDialog by remember { mutableStateOf(false) }
    var showImportFavoritesDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showUsbExclusiveDialog by remember { mutableStateOf(false) }

    // 文本输入状态
    var qqCookieText by remember { mutableStateOf(viewModel.qqCookie) }
    var importPlaylistText by remember { mutableStateOf("") }
    var exportAllPlaylistsText by remember { mutableStateOf("") }
    var exportFavoritesText by remember { mutableStateOf("") }
    var importFavoritesText by remember { mutableStateOf("") }

    // 子页面优先渲染
    if (showPluginManager) {
        PluginManagerDialog(viewModel, onBack = { showPluginManager = false })
        return
    }
    showCrashDetail?.let { entry ->
        CrashLogDetailDialog(entry) { showCrashDetail = null }
        return
    }
    if (showCrashLogs) {
        CrashLogListDialog(viewModel, onBack = { showCrashLogs = false }, onDetail = { showCrashDetail = it })
        return
    }

    val cs = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    // 颜色选项
    val lyricColorOptions = listOf(
        "白色" to android.graphics.Color.WHITE, "绿色" to android.graphics.Color.parseColor("#4CAF50"),
        "青色" to android.graphics.Color.parseColor("#00BCD4"), "粉色" to android.graphics.Color.parseColor("#FF4081"),
        "黄色" to android.graphics.Color.parseColor("#FFEB3B"), "橙色" to android.graphics.Color.parseColor("#FF9800"),
        "紫色" to android.graphics.Color.parseColor("#AB47BC"), "蓝色" to android.graphics.Color.parseColor("#2196F3"),
        "红色" to android.graphics.Color.parseColor("#F44336"),
    )
    val currentColorName = lyricColorOptions.find { it.second == viewModel.floatingLyricsColor }?.first ?: "自定义"
    val lyricHighlightOptions = listOf(
        "自动" to 0, "绿色" to android.graphics.Color.parseColor("#22C55E"), "青色" to android.graphics.Color.parseColor("#00BCD4"),
        "粉色" to android.graphics.Color.parseColor("#FF4081"), "黄色" to android.graphics.Color.parseColor("#FFEB3B"),
        "橙色" to android.graphics.Color.parseColor("#FF9800"), "紫色" to android.graphics.Color.parseColor("#AB47BC"),
        "蓝色" to android.graphics.Color.parseColor("#2196F3"), "红色" to android.graphics.Color.parseColor("#F44336"),
        "白色" to android.graphics.Color.WHITE,
    )
    val curHighlightName = lyricHighlightOptions.find { it.second == viewModel.lyricCurrentColor }?.first ?: "自定义"
    val lyricNormalOptions = listOf(
        "自动" to 0, "白色" to android.graphics.Color.WHITE, "浅灰" to android.graphics.Color.parseColor("#BDBDBD"),
        "深灰" to android.graphics.Color.parseColor("#616161"), "黑色" to android.graphics.Color.parseColor("#2D2D2D"),
        "米色" to android.graphics.Color.parseColor("#D7CCC8"),
    )
    val curNormalName = lyricNormalOptions.find { it.second == viewModel.lyricNormalColor }?.first ?: "自定义"

    // ── 子对话框 ──
    if (showQualityDialog) {
        AppleSheet(
            onDismiss = { showQualityDialog = false },
            title = "选择播放音质",
            primaryLabel = "关闭",
            onPrimary = { showQualityDialog = false },
        ) {
            Column {
                MusicApiConfig.Quality.entries.forEachIndexed { index, q ->
                    AppleSheetOption(
                        text = q.label,
                        selected = viewModel.selectedQuality == q,
                        subtitle = if (q.bitrate == 0) "无损音质 (FLAC)" else "${q.bitrate}kbps",
                        onClick = { viewModel.setQuality(q); showQualityDialog = false },
                    )
                    if (index != MusicApiConfig.Quality.entries.lastIndex) AppleSheetSeparator()
                }
            }
        }
    }
    if (showPlayerStyleDialog) {
        AppleSheet(
            onDismiss = { showPlayerStyleDialog = false },
            title = "选择播放器样式",
            primaryLabel = "关闭",
            onPrimary = { showPlayerStyleDialog = false },
        ) {
            Column {
                MusicViewModel.PlayerStyle.entries.forEachIndexed { index, style ->
                    AppleSheetOption(
                        text = style.displayName,
                        selected = viewModel.playerStyle == style,
                        onClick = { viewModel.changePlayerStyle(style); showPlayerStyleDialog = false },
                    )
                    if (index != MusicViewModel.PlayerStyle.entries.lastIndex) AppleSheetSeparator()
                }
            }
        }
    }
    if (showLyricColorDialog) {
        AppleSheet(
            onDismiss = { showLyricColorDialog = false },
            title = "悬浮歌词颜色",
            secondaryLabel = "取消",
            onSecondary = { showLyricColorDialog = false },
        ) {
            Column {
                lyricColorOptions.forEachIndexed { index, (name, color) ->
                    AppleSheetOption(
                        text = name,
                        selected = color == viewModel.floatingLyricsColor,
                        onClick = { viewModel.changeFloatingLyricsColor(color); showLyricColorDialog = false },
                        leading = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color))) },
                    )
                    if (index != lyricColorOptions.lastIndex) AppleSheetSeparator()
                }
            }
        }
    }
    if (showCurrentColorDialog) {
        AppleSheet(
            onDismiss = { showCurrentColorDialog = false },
            title = "歌词高亮颜色",
            secondaryLabel = "取消",
            onSecondary = { showCurrentColorDialog = false },
        ) {
            Column {
                lyricHighlightOptions.forEachIndexed { index, (name, color) ->
                    AppleSheetOption(
                        text = name,
                        selected = color == viewModel.lyricCurrentColor,
                        onClick = { viewModel.changeLyricCurrentColor(color); showCurrentColorDialog = false },
                        leading = {
                            if (color != 0) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)))
                            else Box(Modifier.size(20.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A)))))
                        },
                    )
                    if (index != lyricHighlightOptions.lastIndex) AppleSheetSeparator()
                }
            }
        }
    }
    if (showNormalColorDialog) {
        AppleSheet(
            onDismiss = { showNormalColorDialog = false },
            title = "歌词普通颜色",
            secondaryLabel = "取消",
            onSecondary = { showNormalColorDialog = false },
        ) {
            Column {
                lyricNormalOptions.forEachIndexed { index, (name, color) ->
                    AppleSheetOption(
                        text = name,
                        selected = color == viewModel.lyricNormalColor,
                        onClick = { viewModel.changeLyricNormalColor(color); showNormalColorDialog = false },
                        leading = {
                            if (color != 0) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)))
                            else Box(Modifier.size(20.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color.White, Color(0xFF2D2D2D)))))
                        },
                    )
                    if (index != lyricNormalOptions.lastIndex) AppleSheetSeparator()
                }
            }
        }
    }
    if (showLyricFontSizeDialog) {
        var sliderSize by remember { mutableFloatStateOf(if (viewModel.lyricFontSize > 0) viewModel.lyricFontSize.toFloat() else 17f) }
        AppleSheet(
            onDismiss = { showLyricFontSizeDialog = false },
            title = "播放器歌词大小",
            primaryLabel = "确定",
            onPrimary = { viewModel.changeLyricFontSize(sliderSize.toInt()); showLyricFontSizeDialog = false },
            secondaryLabel = "恢复默认",
            onSecondary = { viewModel.changeLyricFontSize(0); showLyricFontSizeDialog = false },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("${sliderSize.toInt()} sp", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Slider(value = sliderSize, onValueChange = { sliderSize = it }, valueRange = 12f..36f, steps = 23)
                Spacer(Modifier.height(4.dp))
                Text("默认: 17 sp", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
    if (showLyricSizeDialog) {
        var sliderSize by remember { mutableFloatStateOf(viewModel.floatingLyricSize.toFloat()) }
        AppleSheet(
            onDismiss = { showLyricSizeDialog = false },
            title = "悬浮歌词大小",
            primaryLabel = "确定",
            onPrimary = { viewModel.changeFloatingLyricSize(sliderSize.toInt()); showLyricSizeDialog = false },
            secondaryLabel = "取消",
            onSecondary = { showLyricSizeDialog = false },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("${sliderSize.toInt()} sp", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("10", style = MaterialTheme.typography.bodySmall)
                    Slider(value = sliderSize, onValueChange = { sliderSize = it }, valueRange = 10f..30f, steps = 19, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("30", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showQQCookieDialog) {
        AppleCenterDialog(
            onDismiss = { showQQCookieDialog = false },
            title = "QQ Cookie 设置",
            primaryLabel = "保存",
            onPrimary = { viewModel.setQQCookie(qqCookieText); showQQCookieDialog = false },
            secondaryLabel = "取消",
            onSecondary = { showQQCookieDialog = false },
        ) {
            Column {
                Text("设置Cookie后可获取QQ音乐完整版播放链接", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = qqCookieText, onValueChange = { qqCookieText = it }, label = { Text("Cookie") }, placeholder = { Text("粘贴QQ音乐的Cookie...") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 10)
                Spacer(Modifier.height(8.dp))
                Text("获取方式：", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("1. 浏览器打开 https://y.qq.com/ 并登录", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("2. F12打开开发者工具 → Network标签", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("3. 刷新页面，点击任意请求", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("4. 右侧Headers → Request Headers", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("5. 复制Cookie整串", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
    if (showImportPlaylistDialog) {
        AppleCenterDialog(
            onDismiss = { showImportPlaylistDialog = false },
            title = "导入歌单",
            primaryLabel = "导入",
            onPrimary = { if (importPlaylistText.isNotBlank()) { viewModel.importPlaylistFromText(importPlaylistText); showImportPlaylistDialog = false; importPlaylistText = "" } },
            secondaryLabel = "取消",
            onSecondary = { showImportPlaylistDialog = false },
        ) {
            Column {
                Text("粘贴复制的歌单内容：", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = importPlaylistText, onValueChange = { importPlaylistText = it }, label = { Text("歌单名称和歌曲列表") }, modifier = Modifier.fillMaxWidth().height(200.dp), maxLines = 20)
            }
        }
    }
    if (showExportAllPlaylistsDialog) {
        AppleCenterDialog(
            onDismiss = { showExportAllPlaylistsDialog = false },
            title = "导出全部歌单",
            primaryLabel = "复制",
            onPrimary = { if (exportAllPlaylistsText.isNotBlank()) { clipboardManager.setText(AnnotatedString(exportAllPlaylistsText)); viewModel.showToast("已复制到剪贴板") }; showExportAllPlaylistsDialog = false },
            secondaryLabel = "关闭",
            onSecondary = { showExportAllPlaylistsDialog = false },
        ) {
            Column {
                Text("已生成全部歌单内容，点击复制：", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant.copy(0.5f)).padding(12.dp)) {
                    Text(exportAllPlaylistsText, style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                }
            }
        }
    }
    if (showExportFavoritesDialog) {
        AppleCenterDialog(
            onDismiss = { showExportFavoritesDialog = false },
            title = "导出收藏",
            primaryLabel = "复制",
            onPrimary = { if (exportFavoritesText.isNotBlank()) { clipboardManager.setText(AnnotatedString(exportFavoritesText)); viewModel.showToast("已复制到剪贴板") }; showExportFavoritesDialog = false },
            secondaryLabel = "关闭",
            onSecondary = { showExportFavoritesDialog = false },
        ) {
            Column {
                Text("已生成收藏列表，点击复制：", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant.copy(0.5f)).padding(12.dp)) {
                    Text(exportFavoritesText, style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                }
            }
        }
    }
    if (showImportFavoritesDialog) {
        AppleCenterDialog(
            onDismiss = { showImportFavoritesDialog = false },
            title = "导入收藏",
            primaryLabel = "导入",
            onPrimary = { if (importFavoritesText.isNotBlank()) { viewModel.importFavoritesFromText(importFavoritesText); showImportFavoritesDialog = false; importFavoritesText = "" } },
            secondaryLabel = "取消",
            onSecondary = { showImportFavoritesDialog = false },
            ) {
                Column {
                    Text("粘贴收藏列表内容：", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = importFavoritesText, onValueChange = { importFavoritesText = it }, label = { Text("粘贴收藏内容") }, modifier = Modifier.fillMaxWidth().height(200.dp), maxLines = 20)
                }
            }
        }
    if (showCacheDialog) {
        AppleSheet(
            onDismiss = { showCacheDialog = false },
            title = "音乐缓存管理",
            primaryLabel = "关闭",
            onPrimary = { showCacheDialog = false },
        ) {
            Column {
                val cacheSize = viewModel.getCacheSize()
                Text("当前缓存大小: $cacheSize", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("缓存包括:", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("• 歌曲音频文件", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("• 专辑封面图片", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("• 歌词文件", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { viewModel.clearCache(); viewModel.showToast("缓存已清理"); showCacheDialog = false }) { Text("清理缓存") }
                }
            }
        }
    }
    if (showUsbExclusiveDialog) {
        UsbExclusiveDialog(viewModel = viewModel, onDismiss = { showUsbExclusiveDialog = false })
    }

    // ── 主页面 (Apple Inset Grouped) ──
    // 用 Dialog 包裹，避免被 MineScreen 的 LazyColumn 覆盖
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Column(Modifier.fillMaxSize().background(systemBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("更多设置", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = labelPrimary, letterSpacing = (-0.4).sp)
                Text("音质、歌词、接口与数据", fontSize = 13.sp, color = labelSecondary)
            }
            TextButton(onClick = onDismiss) { Text("返回", color = accentColor, fontSize = 16.sp) }
        }

        LazyColumn(
            Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // 播放
            item {
                ModernSectionLabel("播放")
                ModernSettingsCard {
                    ModernSettingsRow(Icons.Default.HighQuality, "默认音质", viewModel.selectedQuality.label) { showQualityDialog = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.MusicNote, "播放器样式", viewModel.playerStyle.displayName) { showPlayerStyleDialog = true }
                }
            }
            // 外观
            item {
                ModernSectionLabel("外观")
                ModernSettingsCard {
                    ModernSettingsSwitchRow(
                        icon = if (viewModel.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        title = "深色模式",
                        subtitle = if (viewModel.isDarkMode) "已开启" else "关闭",
                        checked = viewModel.isDarkMode,
                    ) { viewModel.toggleDarkMode() }
                }
            }
            // 歌词
            item {
                ModernSectionLabel("歌词")
                ModernSettingsCard {
                    ModernSettingsRow(Icons.Default.Code, "悬浮歌词颜色", currentColorName) { showLyricColorDialog = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.LightMode, "歌词高亮颜色", curHighlightName) { showCurrentColorDialog = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.DarkMode, "歌词普通颜色", curNormalName) { showNormalColorDialog = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.Settings, "播放器歌词大小", if (viewModel.lyricFontSize > 0) "${viewModel.lyricFontSize} sp" else "默认 (17 sp)") { showLyricFontSizeDialog = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.Settings, "悬浮歌词大小", "${viewModel.floatingLyricSize} sp") { showLyricSizeDialog = true }
                }
            }
            // 音源与接口
            item {
                ModernSectionLabel("音源与接口")
                ModernSettingsCard {
                    ModernSettingsRow(
                        Icons.Default.Settings, "音乐 API 配置",
                        if (viewModel.apiMode == "lx_plugin") {
                            if (viewModel.lxSelectedSource == MusicViewModel.LX_SOURCE_ALL) "落雪插件 · 全部音源"
                            else "落雪插件 · ${viewModel.lxSelectedSource.ifBlank { "未选择" }}"
                        } else "官方 API 模式"
                    ) { showPluginManager = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(
                        Icons.Default.List, "插件管理",
                        if (viewModel.lxPlugins.isEmpty()) "未导入插件"
                        else "已加载 ${viewModel.lxPlugins.size} 个 · ${viewModel.lxPlugins.count { viewModel.isPluginEnabled(it.id) }} 启用"
                    ) { showPluginManager = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.Settings, "QQ Cookie", if (viewModel.qqCookie.isNotEmpty()) "已设置" else "未设置（仅试听）") {
                        qqCookieText = viewModel.qqCookie; showQQCookieDialog = true
                    }
                }
            }
            // 数据管理
            item {
                ModernSectionLabel("数据管理")
                ModernSettingsCard {
                    ModernSettingsRow(Icons.Default.Upload, "导出全部歌单", "一键复制全部歌单内容") {
                        exportAllPlaylistsText = viewModel.exportAllPlaylistsAsText()
                        if (exportAllPlaylistsText.isBlank()) viewModel.showToast("没有歌单可导出") else showExportAllPlaylistsDialog = true
                    }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.Download, "导入歌单", "粘贴歌单内容导入") { showImportPlaylistDialog = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.Favorite, "导出收藏", "一键复制全部收藏内容") {
                        exportFavoritesText = viewModel.exportFavoritesAsText()
                        if (exportFavoritesText.isBlank()) viewModel.showToast("没有收藏可导出") else showExportFavoritesDialog = true
                    }
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.FavoriteBorder, "导入收藏", "粘贴收藏内容导入") { showImportFavoritesDialog = true }
                }
            }
            // 存储
            item {
                ModernSectionLabel("存储")
                ModernSettingsCard {
                    ModernSettingsRow(Icons.Default.DeleteSweep, "音乐缓存管理", "查看和清理本地缓存") { showCacheDialog = true }
                    ModernSettingsDivider()
                    ModernSettingsRow(
                        Icons.Default.Usb, "USB 独占",
                        if (viewModel.isUsbExclusiveEnabled.value) "已启用 · 实时认证中" else "未启用 · 点击查看认证日志"
                    ) { showUsbExclusiveDialog = true }
                }
            }
            // 开发者
            item {
                ModernSectionLabel("开发者")
                ModernSettingsCard {
                    ModernSettingsSwitchRow(
                        Icons.Default.Code, "开发者模式",
                        if (viewModel.isDevMode) "已开启" else "查看崩溃日志等调试信息",
                        checked = viewModel.isDevMode,
                    ) {
                        viewModel.toggleDevMode()
                        viewModel.showToast(if (viewModel.isDevMode) "开发者模式已开启" else "开发者模式已关闭")
                    }
                    if (viewModel.isDevMode) {
                        ModernSettingsDivider()
                        val logCount = viewModel.getCrashLogCount()
                        Row(
                            Modifier.fillMaxWidth().clickable { showCrashLogs = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF34C759)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.BugReport, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("崩溃日志", fontSize = 16.sp, fontWeight = FontWeight.Normal, color = labelPrimary, letterSpacing = (-0.2).sp)
                                Text(if (logCount > 0) "$logCount 条记录 — 点击查看详情" else "暂无崩溃记录", fontSize = 13.sp, color = if (logCount > 0) accentColor else labelSecondary)
                            }
                            if (logCount > 0) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(accentColor), contentAlignment = Alignment.Center) {
                                    Text("$logCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = labelTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            // 关于
            item {
                ModernSectionLabel("关于")
                ModernSettingsCard {
                    ModernSettingsRow(Icons.Default.Info, "版本信息", "v${viewModel.currentVersion}", showChevron = false) {}
                    ModernSettingsDivider()
                    val displayAuthorQq = RemoteConfig.officialAuthorQq.ifBlank { CriticalUiProtector.communityQqNumber() }
                    ModernSettingsRow(Icons.Default.Info, "作者联系 QQ", displayAuthorQq, showChevron = false) {}
                    ModernSettingsDivider()
                    ModernSettingsRow(Icons.Default.Download, "下载最新版", "点击跳转云盘下载页面") { viewModel.openDownloadPage() }
                }
                Spacer(Modifier.height(12.dp))
                OfficialCommunityEntryRow(viewModel = viewModel, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), showSubtitle = true)
            }
            // 免责声明
            item {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)).background(secondaryBackground).padding(16.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = labelSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("免责声明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = labelSecondary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "• 本应用音乐资源来自互联网公开接口，不存储任何音频文件\n" +
                            "• 仅供个人学习试听，请支持正版音乐\n" +
                            "• 版权归原作者及平台所有，侵权请联系删除\n" +
                            "• 使用本应用产生的法律责任由用户自行承担",
                            style = MaterialTheme.typography.bodySmall, color = labelSecondary.copy(alpha = 0.8f), lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
    }
    } // Dialog
}

// ═══ 歌单同步对话框 ═══
@Composable
private fun PlaylistSyncDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val accentColor = Color(0xFF007AFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val errorColor = Color(0xFFFF3B30)
    val successColor = Color(0xFF34C759)

    val syncState by PlaylistSyncManager.syncState.collectAsState()
    var userId by remember { mutableStateOf(PlaylistSyncManager.getSyncUserId()) }
    val isLoggedIn = userId.isNotEmpty()
    val scope = rememberCoroutineScope()

    // 子页面状态：login / register / recover
    var subPage by remember { mutableStateOf("login") }

    // 表单输入
    var inputId by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }
    var inputQq by remember { mutableStateOf("") }
    var recoverQq by remember { mutableStateOf("") }
    var recoverPassword by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(systemBackground).statusBarsPadding()) {
            // 顶栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 返回按钮：已登录或登录页直接关闭；其他子页面返回登录页
                val backAction: () -> Unit = when {
                    isLoggedIn -> onDismiss
                    subPage == "login" -> onDismiss
                    else -> { { subPage = "login" } }
                }
                TextButton(onClick = backAction) {
                    Text(
                        if (isLoggedIn || subPage == "login") "返回" else "返回登录",
                        color = accentColor, fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        isLoggedIn -> "歌单云同步"
                        subPage == "register" -> "注册账号"
                        subPage == "recover" -> "找回密码"
                        else -> "歌单云同步"
                    },
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = labelPrimary,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            LazyColumn(
                Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                // ══════════════════════════════════════
                // 已登录：同步操作区
                // ══════════════════════════════════════
                if (isLoggedIn) {
                    item {
                        ModernSectionLabel("当前账号")
                        ModernSettingsCard {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(accentColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("ID: $userId", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = labelPrimary)
                                    Text("已登录", fontSize = 13.sp, color = successColor)
                                }
                                TextButton(onClick = {
                                    PlaylistSyncManager.logout()
                                    userId = ""
                                    subPage = "login"
                                    PlaylistSyncManager.resetState()
                                }) {
                                    Text("退出", color = errorColor, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    item {
                        ModernSectionLabel("数据同步")
                        ModernSettingsCard {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        PlaylistSyncManager.upload(
                                            playlists = viewModel.userPlaylists,
                                            favorites = viewModel.favorites,
                                            playHistory = viewModel.playHistory,
                                            searchHistory = viewModel.searchHistory,
                                        )
                                    }
                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(accentColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.CloudUpload, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("开始同步", fontSize = 16.sp, color = labelPrimary)
                                    Text("上传歌单、收藏到云端", fontSize = 13.sp, color = labelSecondary)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = labelTertiary, modifier = Modifier.size(18.dp))
                            }
                            ModernSettingsDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        val result = PlaylistSyncManager.download()
                                        if (result != null) {
                                            val (playlists, favorites, playHistory) = result
                                            if (playlists.isNotEmpty()) {
                                                viewModel.importPlaylists(playlists)
                                            }
                                            if (favorites.isNotEmpty()) {
                                                viewModel.importFavorites(favorites)
                                            }
                                            if (playHistory.isNotEmpty()) {
                                                viewModel.importPlayHistory(playHistory)
                                            }
                                            viewModel.showToast("恢复完成")
                                        }
                                    }
                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF34C759)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.CloudDownload, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("恢复数据", fontSize = 16.sp, color = labelPrimary)
                                    Text("从云端拉取歌单和收藏", fontSize = 13.sp, color = labelSecondary)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = labelTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // 同步状态
                    val currentState = syncState
                    if (currentState !is PlaylistSyncManager.SyncState.Idle) {
                        item {
                            ModernSectionLabel("同步状态")
                            ModernSettingsCard {
                                Column(Modifier.padding(16.dp)) {
                                    when (currentState) {
                                        is PlaylistSyncManager.SyncState.Authenticating -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentColor)
                                                Spacer(Modifier.width(8.dp))
                                                Text("正在认证...", fontSize = 14.sp, color = labelSecondary)
                                            }
                                        }
                                        is PlaylistSyncManager.SyncState.Uploading -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentColor)
                                                Spacer(Modifier.width(8.dp))
                                                Text("正在上传...", fontSize = 14.sp, color = labelSecondary)
                                            }
                                        }
                                        is PlaylistSyncManager.SyncState.Downloading -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentColor)
                                                Spacer(Modifier.width(8.dp))
                                                Text("正在下载...", fontSize = 14.sp, color = labelSecondary)
                                            }
                                        }
                                        is PlaylistSyncManager.SyncState.Progress -> {
                                            Column {
                                                Text(currentState.message, fontSize = 14.sp, color = labelSecondary)
                                                Spacer(Modifier.height(8.dp))
                                                androidx.compose.material3.LinearProgressIndicator(
                                                    progress = { currentState.percent / 100f },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = accentColor,
                                                )
                                            }
                                        }
                                        is PlaylistSyncManager.SyncState.Success -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, null, tint = successColor, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(currentState.message, fontSize = 14.sp, color = successColor)
                                            }
                                        }
                                        is PlaylistSyncManager.SyncState.Error -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CloudOff, null, tint = errorColor, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(currentState.message, fontSize = 14.sp, color = errorColor)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }

                // ══════════════════════════════════════
                // 未登录 - 登录界面
                // ══════════════════════════════════════
                else if (subPage == "login") {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text("歌单云同步", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = labelPrimary, letterSpacing = (-0.4).sp, modifier = Modifier.padding(horizontal = 20.dp))
                        Text("同步歌单、收藏到云端", fontSize = 13.sp, color = labelSecondary, modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(24.dp))
                    }
                    item {
                        ModernSettingsCard {
                            Column(Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = inputId,
                                    onValueChange = { v -> inputId = v.filter { it.isDigit() }.take(10) },
                                    label = { Text("ID") },
                                    placeholder = { Text("输入数字ID") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = inputPassword,
                                    onValueChange = { v -> inputPassword = v.filter { it.isDigit() }.take(20) },
                                    label = { Text("密码") },
                                    placeholder = { Text("输入数字密码") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (inputId.isBlank()) {
                                            viewModel.showToast("请输入ID")
                                            return@Button
                                        }
                                        if (inputPassword.length < 4) {
                                            viewModel.showToast("密码至少4位数字")
                                            return@Button
                                        }
                                        scope.launch {
                                            val ok = PlaylistSyncManager.auth(inputId, inputPassword)
                                            if (ok) {
                                                userId = inputId
                                                inputId = ""
                                                inputPassword = ""
                                                viewModel.showToast("登录成功")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                ) {
                                    Text("登录", fontSize = 16.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    TextButton(onClick = {
                                        inputId = ""
                                        inputPassword = ""
                                        inputQq = ""
                                        subPage = "register"
                                    }) {
                                        Text("没有账号？去注册", color = accentColor, fontSize = 13.sp)
                                    }
                                    TextButton(onClick = {
                                        recoverQq = ""
                                        recoverPassword = ""
                                        subPage = "recover"
                                    }) {
                                        Text("忘记密码？", color = labelSecondary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                    // 同步状态（登录中显示）
                    val currentState = syncState
                    if (currentState is PlaylistSyncManager.SyncState.Error) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            ModernSettingsCard {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudOff, null, tint = errorColor, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(currentState.message, fontSize = 14.sp, color = errorColor)
                                }
                            }
                        }
                    }
                }

                // ══════════════════════════════════════
                // 未登录 - 注册界面
                // ══════════════════════════════════════
                else if (subPage == "register") {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text("注册新账号", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = labelPrimary, letterSpacing = (-0.4).sp, modifier = Modifier.padding(horizontal = 20.dp))
                        Text("填写以下信息完成注册", fontSize = 13.sp, color = labelSecondary, modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(24.dp))
                    }
                    item {
                        ModernSettingsCard {
                            Column(Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = inputId,
                                    onValueChange = { v -> inputId = v.filter { it.isDigit() }.take(10) },
                                    label = { Text("ID") },
                                    placeholder = { Text("10位以内数字") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = inputPassword,
                                    onValueChange = { v -> inputPassword = v.filter { it.isDigit() }.take(20) },
                                    label = { Text("密码") },
                                    placeholder = { Text("4-20位数字") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = inputQq,
                                    onValueChange = { v -> inputQq = v.filter { it.isDigit() }.take(20) },
                                    label = { Text("QQ号") },
                                    placeholder = { Text("用于密码找回") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (inputId.isBlank()) {
                                            viewModel.showToast("请输入ID")
                                            return@Button
                                        }
                                        if (inputPassword.length < 4) {
                                            viewModel.showToast("密码至少4位数字")
                                            return@Button
                                        }
                                        if (inputQq.isBlank()) {
                                            viewModel.showToast("请输入QQ号")
                                            return@Button
                                        }
                                        scope.launch {
                                            val ok = PlaylistSyncManager.auth(inputId, inputPassword, inputQq)
                                            if (ok) {
                                                userId = inputId
                                                inputId = ""
                                                inputPassword = ""
                                                inputQq = ""
                                                subPage = "login"
                                                viewModel.showToast("注册成功")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                ) {
                                    Text("注册", fontSize = 16.sp)
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "• ID和密码均为纯数字\n• QQ号用于找回密码，请填写真实QQ号\n• 注册后ID和密码绑定，他人无法登录您的账号",
                                    fontSize = 12.sp, color = labelTertiary, lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                    // 注册错误状态
                    val currentState = syncState
                    if (currentState is PlaylistSyncManager.SyncState.Error) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            ModernSettingsCard {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudOff, null, tint = errorColor, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(currentState.message, fontSize = 14.sp, color = errorColor)
                                }
                            }
                        }
                    }
                }

                // ══════════════════════════════════════
                // 未登录 - 找回密码界面
                // ══════════════════════════════════════
                else if (subPage == "recover") {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text("找回密码", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = labelPrimary, letterSpacing = (-0.4).sp, modifier = Modifier.padding(horizontal = 20.dp))
                        Text("通过注册时填写的QQ号找回", fontSize = 13.sp, color = labelSecondary, modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(24.dp))
                    }
                    item {
                        ModernSettingsCard {
                            Column(Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = recoverQq,
                                    onValueChange = { v -> recoverQq = v.filter { it.isDigit() }.take(20) },
                                    label = { Text("QQ号") },
                                    placeholder = { Text("注册时填写的QQ号") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = recoverPassword,
                                    onValueChange = { v -> recoverPassword = v.filter { it.isDigit() }.take(20) },
                                    label = { Text("新密码") },
                                    placeholder = { Text("4-20位数字") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (recoverQq.isBlank()) {
                                            viewModel.showToast("请输入QQ号")
                                            return@Button
                                        }
                                        if (recoverPassword.length < 4) {
                                            viewModel.showToast("新密码至少4位数字")
                                            return@Button
                                        }
                                        scope.launch {
                                            val foundId = PlaylistSyncManager.recover(recoverQq, recoverPassword)
                                            if (foundId != null) {
                                                recoverQq = ""
                                                recoverPassword = ""
                                                subPage = "login"
                                                viewModel.showToast("密码重置成功，您的ID是 $foundId")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                ) {
                                    Text("重置密码", fontSize = 16.sp)
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "• 输入注册时填写的QQ号\n• 设置新密码后即可用ID+新密码登录\n• 系统会返回您的ID",
                                    fontSize = 12.sp, color = labelTertiary, lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                    // 找回错误/成功状态
                    val currentState = syncState
                    if (currentState is PlaylistSyncManager.SyncState.Error) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            ModernSettingsCard {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudOff, null, tint = errorColor, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(currentState.message, fontSize = 14.sp, color = errorColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══ USB独占模式对话框 ═══
@Composable
private fun UsbExclusiveDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    val enabled by viewModel.isUsbExclusiveEnabled.collectAsState()
    val logEntries by viewModel.usbExclusiveLogEntries.collectAsState()
    val deviceStates by viewModel.usbDeviceStates.collectAsState()
    val logDirPath by viewModel.usbExclusiveLogDir.collectAsState()

    AppleSheet(
        onDismiss = onDismiss,
        title = "USB独占",
        primaryLabel = "关闭",
        onPrimary = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 状态卡片
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (enabled) Color(0xFF4CAF50).copy(0.08f) else cs.surfaceVariant.copy(0.5f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape)
                        .background(if (enabled) Color(0xFF4CAF50) else cs.onSurfaceVariant.copy(0.4f))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (enabled) "USB独占模式 · 已启用" else "USB独占模式 · 未启用",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "认证日志输出至：$logDirPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // 操作按钮
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.toggleUsbExclusive() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (enabled) Color(0xFFE53935) else accentColor
                    )
                ) { Text(if (enabled) "关闭独占" else "启用独占") }
                if (enabled) {
                    OutlinedButton(onClick = { viewModel.rescanUsbDevices() }, modifier = Modifier.weight(1f)) {
                        Text("重新扫描")
                    }
                }
            }

            // 设备列表
            if (enabled && deviceStates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Usb, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("设备独占状态（${deviceStates.size}）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    deviceStates.values.forEach { state ->
                        UsbDeviceRow(
                            state = state,
                            accentColor = accentColor,
                            onRetry = { viewModel.retryUsbDevice(state.deviceName) },
                            onRelease = { viewModel.releaseUsbDevice(state.deviceName) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 日志列表标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("认证日志（${logEntries.size} 条）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))

            // 日志列表
            Box(
                Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.surfaceVariant.copy(0.4f))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                if (logEntries.isEmpty()) {
                    Text(
                        "暂无日志。点击「启用独占」将启动 USB 设备独占访问认证流程，\n所有状态与错误日志将实时输出到上述目录。",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        logEntries.forEach { entry ->
                            UsbLogLine(entry = entry, accentColor = accentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsbDeviceRow(
    state: UsbDeviceState,
    accentColor: Color,
    onRetry: () -> Unit,
    onRelease: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (statusColor, statusText) = deviceStatusVisual(state.status, cs)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceVariant.copy(0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(statusColor)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "VID:${state.vendorId} PID:${state.productId}",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface
            )
            Text(
                "$statusText · 接口 ${state.claimedInterfaceCount}/${state.interfaceCount}" +
                    (state.lastError?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        // 仅在出错/冲突/已连接等可操作状态时显示按钮
        val canRetry = state.status in setOf(
            DeviceStatus.CONFLICT, DeviceStatus.ERROR,
            DeviceStatus.PERMISSION_DENIED, DeviceStatus.RELEASED, DeviceStatus.DETACHED
        )
        val canRelease = state.status == DeviceStatus.CONNECTED
        if (canRetry) {
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("重试", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (canRelease) {
            TextButton(onClick = onRelease, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("释放", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE53935))
            }
        }
    }
}

@Composable
private fun deviceStatusVisual(status: DeviceStatus, cs: androidx.compose.material3.ColorScheme): Pair<Color, String> = when (status) {
    DeviceStatus.IDLE -> cs.onSurfaceVariant.copy(0.5f) to "待机"
    DeviceStatus.REQUESTING_PERMISSION -> Color(0xFFFFA000) to "请求权限中"
    DeviceStatus.PERMISSION_GRANTED -> Color(0xFF2196F3) to "权限已授予"
    DeviceStatus.PERMISSION_DENIED -> Color(0xFFE53935) to "权限被拒绝"
    DeviceStatus.CONNECTED -> Color(0xFF4CAF50) to "已独占"
    DeviceStatus.CONFLICT -> Color(0xFFFFA000) to "设备冲突"
    DeviceStatus.DETACHED -> cs.onSurfaceVariant.copy(0.5f) to "已拔出"
    DeviceStatus.RELEASED -> cs.onSurfaceVariant to "已释放"
    DeviceStatus.ERROR -> Color(0xFFE53935) to "错误"
}

@Composable
private fun UsbLogLine(entry: UsbLogEntry, accentColor: Color) {
    val cs = MaterialTheme.colorScheme
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFE53935)
        LogLevel.WARN -> Color(0xFFFFA000)
        LogLevel.INFO -> cs.onSurfaceVariant
    }
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "[${entry.level.tag}]",
                style = MaterialTheme.typography.bodySmall,
                color = levelColor,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(entry.timestamp, style = MaterialTheme.typography.bodySmall, color = accentColor.copy(0.7f))
                Text(entry.message, style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
            }
        }
    }
}

// ═══ API配置对话框 ═══
@Composable
private fun ApiConfigDialog(viewModel: MusicViewModel, onDismiss: () -> Unit, onOpenPluginManager: () -> Unit) {
    var selectedSourceText by remember { mutableStateOf(viewModel.lxSelectedSource) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testKeyword by remember { mutableStateOf("周杰伦") }
    var testSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary

    AppleSheet(
        onDismiss = onDismiss,
        title = "音乐API配置 / 落雪插件",
        primaryLabel = "保存",
        onPrimary = { if (viewModel.apiMode == "lx_plugin" && selectedSourceText.isNotBlank()) viewModel.updateLxSelectedSource(selectedSourceText); onDismiss(); viewModel.showToast("配置已保存") },
        secondaryLabel = "取消",
        onSecondary = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp)) {
                Column {
                    Text("新手教程", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = accentColor)
                    Spacer(Modifier.height(6.dp))
                    Text("搜索可用，播放需插件。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("步骤：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = cs.onSurface)
                    Text("1. 选择落雪插件模式", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text("2. 导入 .js 文件", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text("3. 选择 source", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text("4. 测试", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text("5. 保存", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("API模式", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("official" to "官方API", "lx_plugin" to "落雪插件").forEach { (mode, label) ->
                    val sel = viewModel.apiMode == mode
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) accentColor.copy(0.08f) else cs.surfaceVariant.copy(0.5f))
                        .then(if (sel) Modifier.border(1.5.dp, accentColor.copy(0.3f), RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { viewModel.updateApiMode(mode) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(if (sel) accentColor else Color.Transparent)
                            .then(if (!sel) Modifier.border(1.5.dp, cs.onSurfaceVariant.copy(0.4f), CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                            if (sel) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) accentColor else cs.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (viewModel.apiMode == "lx_plugin") Text("落雪插件模式：导入本地.js插件并选择source", style = MaterialTheme.typography.bodySmall, color = accentColor)
            else Text("官方API模式：走内置官方逻辑", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (viewModel.apiMode == "lx_plugin") {
                OutlinedButton(onClick = onOpenPluginManager, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.List, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (viewModel.lxPlugins.isEmpty()) "管理插件 · 未导入" else "管理插件 · ${viewModel.lxPlugins.size} 个插件 · ${viewModel.lxPlugins.count { viewModel.isPluginEnabled(it.id) }} 个启用")
                }
                Spacer(Modifier.height(8.dp))

                if (viewModel.lxSources.isNotEmpty()) {
                    Text("选择 Source:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isAllSel = viewModel.lxSelectedSource == MusicViewModel.LX_SOURCE_ALL
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (isAllSel) Color(0xFF4CAF50) else cs.surfaceVariant)
                            .clickable { viewModel.updateLxSelection(viewModel.lxSelectedPluginId, MusicViewModel.LX_SOURCE_ALL); selectedSourceText = MusicViewModel.LX_SOURCE_ALL }
                            .padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("全部", style = MaterialTheme.typography.bodySmall, fontWeight = if (isAllSel) FontWeight.Bold else FontWeight.Normal, color = if (isAllSel) Color.White else cs.onSurface)
                        }
                        viewModel.lxPlugins.forEach { plugin ->
                            plugin.sources.forEach { src ->
                                val isSel = viewModel.lxSelectedPluginId == plugin.id && viewModel.lxSelectedSource == src
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSel) accentColor else cs.surfaceVariant)
                                    .clickable { viewModel.updateLxSelection(plugin.id, src); selectedSourceText = src }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(src, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) Color.White else cs.onSurface)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(value = selectedSourceText, onValueChange = { selectedSourceText = it; viewModel.updateLxSelectedSource(it) }, label = { Text("当前source") }, placeholder = { Text("如 kw/kg/tx/wy/mg") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = testKeyword, onValueChange = { testKeyword = it }, label = { Text("测试搜索关键字") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { isTesting = true; val r = viewModel.testLxSearch(testKeyword); isTesting = false; if (r.isSuccess) { testSongs = r.getOrNull().orEmpty(); testResult = "search成功: ${testSongs.size} 首" } else testResult = "search失败: ${r.exceptionOrNull()?.message}" } }, modifier = Modifier.weight(1f), enabled = !isTesting) { Text("测试Search") }
                    Button(onClick = { val first = testSongs.firstOrNull(); if (first == null) testResult = "请先搜索并选择歌曲" else scope.launch { isTesting = true; val r = viewModel.testLxMusicUrl(first); isTesting = false; testResult = if (r.isSuccess) "play成功: ${r.getOrNull()}" else "play失败: ${r.exceptionOrNull()?.message}" } }, modifier = Modifier.weight(1f), enabled = !isTesting) { Text("测试Play") }
                }
                Spacer(Modifier.height(8.dp))
            }

            testResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                Text(result, style = MaterialTheme.typography.bodySmall, color = if (result.contains("成功") || result.startsWith("✓")) Color(0xFF4CAF50) else Color(0xFFF44336))
            }

            if (viewModel.apiMode == "lx_plugin" && viewModel.lxDebugLogs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Runtime Log", style = MaterialTheme.typography.labelMedium)
                    Row {
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(viewModel.lxDebugLogs.joinToString("\n"))); viewModel.showToast("已复制日志") }) { Text("Copy", style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = { viewModel.clearLxLogs() }) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(max = 200.dp).background(cs.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small).padding(8.dp)) {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(viewModel.lxDebugLogs.size) { scrollState.animateScrollTo(scrollState.maxValue) }
                    Column(Modifier.verticalScroll(scrollState)) {
                        viewModel.lxDebugLogs.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                color = if (line.contains("ERROR") || line.contains("FAILED") || line.contains("WARNING")) Color(0xFFF44336)
                                else if (line.contains("REQUEST HANDLER SET") || line.contains("done")) Color(0xFF4CAF50) else cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ═══ 插件管理 ═══

/**
 * 插件管理对话框
 */
@Composable
private fun PluginManagerDialog(viewModel: MusicViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    val pluginPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = viewModel.importLxPlugin(uri)
                if (result.isSuccess) viewModel.showToast("插件导入成功")
                else viewModel.showToast("导入失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    var showOnlineImport by remember { mutableStateOf(false) }
    var onlineUrl by remember { mutableStateOf("") }
    var isOnlineImporting by remember { mutableStateOf(false) }
    var expandedPluginId by remember { mutableStateOf<String?>(null) }

    if (showOnlineImport) {
        AppleCenterDialog(
            onDismiss = { if (!isOnlineImporting) showOnlineImport = false },
            title = "在线导入JS插件",
            primaryLabel = "导入",
            onPrimary = { if (!isOnlineImporting) { if (onlineUrl.isBlank()) { viewModel.showToast("请输入插件URL") } else { isOnlineImporting = true; scope.launch { val result = viewModel.importLxPluginFromUrl(onlineUrl.trim()); isOnlineImporting = false; if (result.isSuccess) { viewModel.showToast("在线导入成功"); showOnlineImport = false; onlineUrl = "" } else viewModel.showToast("导入失败: ${result.exceptionOrNull()?.message}") } } } },
            secondaryLabel = "取消",
            onSecondary = { if (!isOnlineImporting) showOnlineImport = false },
        ) {
            Column {
                Text("输入JS插件的下载链接，将自动下载并导入。", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = onlineUrl, onValueChange = { onlineUrl = it }, label = { Text("插件URL") }, placeholder = { Text("https://example.com/plugin.js") }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 3, enabled = !isOnlineImporting)
                if (isOnlineImporting) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在下载并导入...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    AppleSheet(
        onDismiss = onBack,
        title = "插件管理",
        primaryLabel = "完成",
        onPrimary = onBack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pluginPicker.launch(arrayOf("text/javascript", "application/javascript", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("导入JS", fontSize = 12.sp, maxLines = 1) }
                Button(onClick = { showOnlineImport = true }, modifier = Modifier.weight(1f)) { Text("在线导入", fontSize = 12.sp, maxLines = 1) }
                OutlinedButton(onClick = { viewModel.removeLxPlugin() }, modifier = Modifier.weight(1f)) { Text("全部移除", fontSize = 12.sp, maxLines = 1) }
            }
            Spacer(Modifier.height(12.dp))

            if (viewModel.lxPlugins.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.List, null, tint = cs.onSurfaceVariant.copy(0.3f), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("暂无插件", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Text("点击上方按钮导入JS插件", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(0.5f))
                    }
                }
            } else {
                Text("已加载 ${viewModel.lxPlugins.size} 个插件", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                viewModel.lxPlugins.forEach { plugin ->
                    val enabled = viewModel.isPluginEnabled(plugin.id)
                    val isExpanded = expandedPluginId == plugin.id
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (enabled) cs.surfaceVariant.copy(0.5f) else cs.surfaceVariant.copy(0.2f))
                        .then(if (enabled) Modifier.border(1.dp, accentColor.copy(0.2f), RoundedCornerShape(12.dp)) else Modifier).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(plugin.info.name.ifBlank { "未知插件" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (enabled) cs.onSurface else cs.onSurface.copy(0.4f))
                                    if (!plugin.initialized) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("初始化失败", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF44336)).padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                                Text("v${plugin.info.version} · ${plugin.info.author}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(if (enabled) 1f else 0.4f))
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    // 单插件独占模式：启用此插件时自动禁用其他全部插件
                                    viewModel.togglePluginEnabled(plugin.id)
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
                                modifier = Modifier.height(24.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { viewModel.removeLxPluginById(plugin.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, "删除", tint = cs.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(16.dp))
                            }
                        }
                        if (plugin.sources.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expandedPluginId = if (isExpanded) null else plugin.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                val enabledCount = plugin.sources.count { viewModel.isSourceEnabled(plugin.id, it) }
                                Text("$enabledCount / ${plugin.sources.size} 个音源启用", style = MaterialTheme.typography.bodySmall, color = if (enabled) accentColor.copy(0.8f) else cs.onSurfaceVariant.copy(0.4f))
                                Spacer(Modifier.weight(1f))
                                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "展开", tint = cs.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(18.dp))
                            }
                        }
                        if (isExpanded && plugin.sources.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            plugin.sources.forEach { src ->
                                val srcEnabled = viewModel.isSourceEnabled(plugin.id, src)
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (srcEnabled) accentColor.copy(0.06f) else Color.Transparent)
                                    .clickable { viewModel.toggleSourceEnabled(plugin.id, src) }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(src, style = MaterialTheme.typography.bodySmall, fontWeight = if (srcEnabled) FontWeight.Medium else FontWeight.Normal, color = if (srcEnabled) cs.onSurface else cs.onSurface.copy(0.4f))
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = srcEnabled, onCheckedChange = { viewModel.toggleSourceEnabled(plugin.id, src) }, colors = SwitchDefaults.colors(checkedTrackColor = accentColor), modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (viewModel.lxPlugins.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant.copy(0.3f)).padding(10.dp)) {
                    Text("提示：启用多个插件后，选择“全部”音源即可跨插件搜索和播放。\n禁用的插件或音源将不会参与搜索和播放。", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(0.7f), lineHeight = 18.sp)
                }
            }
        }
    }
}

// ═══ 落雪插件导入对话框 ═══
@Composable
private fun LxPluginImportDialog(viewModel: MusicViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    val pluginPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = viewModel.importLxPlugin(uri)
                if (result.isSuccess) viewModel.showToast("插件导入成功")
                else viewModel.showToast("导入失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    var showOnlineImport by remember { mutableStateOf(false) }
    var onlineUrl by remember { mutableStateOf("") }
    var isOnlineImporting by remember { mutableStateOf(false) }

    if (showOnlineImport) {
        AppleCenterDialog(
            onDismiss = { if (!isOnlineImporting) showOnlineImport = false },
            title = "在线导入JS插件",
            primaryLabel = "导入",
            onPrimary = { if (!isOnlineImporting) { if (onlineUrl.isBlank()) { viewModel.showToast("请输入插件URL") } else { isOnlineImporting = true; scope.launch { val result = viewModel.importLxPluginFromUrl(onlineUrl.trim()); isOnlineImporting = false; if (result.isSuccess) { viewModel.showToast("在线导入成功"); showOnlineImport = false; onlineUrl = "" } else viewModel.showToast("导入失败: ${result.exceptionOrNull()?.message}") } } } },
            secondaryLabel = "取消",
            onSecondary = { if (!isOnlineImporting) showOnlineImport = false },
        ) {
            Column {
                Text("输入JS插件的下载链接，将自动下载并导入。", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = onlineUrl, onValueChange = { onlineUrl = it }, label = { Text("插件URL") }, placeholder = { Text("https://example.com/plugin.js") }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 3, enabled = !isOnlineImporting)
                if (isOnlineImporting) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在下载并导入...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    AppleSheet(
        onDismiss = onBack,
        title = "落雪插件",
        primaryLabel = "完成",
        onPrimary = onBack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pluginPicker.launch(arrayOf("text/javascript", "application/javascript", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("导入JS", fontSize = 12.sp, maxLines = 1) }
                Button(onClick = { showOnlineImport = true }, modifier = Modifier.weight(1f)) { Text("在线导入", fontSize = 12.sp, maxLines = 1) }
                OutlinedButton(onClick = { viewModel.removeLxPlugin() }, modifier = Modifier.weight(1f)) { Text("全部移除", fontSize = 12.sp, maxLines = 1) }
            }
            Spacer(Modifier.height(12.dp))

            if (viewModel.lxPlugins.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, null, tint = cs.onSurfaceVariant.copy(0.3f), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("暂无插件", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Text("点击上方按钮导入JS插件", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(0.5f))
                    }
                }
            } else {
                Text("已加载 ${viewModel.lxPlugins.size} 个落雪插件", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                viewModel.lxPlugins.forEach { plugin ->
                    val enabled = viewModel.isPluginEnabled(plugin.id)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (enabled) cs.surfaceVariant.copy(0.5f) else cs.surfaceVariant.copy(0.2f))
                        .then(if (enabled) Modifier.border(1.dp, accentColor.copy(0.2f), RoundedCornerShape(12.dp)) else Modifier).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(plugin.info.name.ifBlank { "未知插件" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (enabled) cs.onSurface else cs.onSurface.copy(0.4f))
                                Text("v${plugin.info.version} · ${plugin.info.author}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(if (enabled) 1f else 0.4f))
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    // 单插件独占模式：启用此插件时自动禁用其他全部插件
                                    viewModel.togglePluginEnabled(plugin.id)
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
                                modifier = Modifier.height(24.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { viewModel.removeLxPluginById(plugin.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, "删除", tint = cs.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ═══ MusicFree 插件导入对话框 ═══
@Composable
private fun MusicFreePluginImportDialog(viewModel: MusicViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val accentColor = Color(0xFF34C759)
    val pluginPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = viewModel.importMusicFreePlugin(uri)
                if (result.isSuccess) viewModel.showToast("MusicFree 插件导入成功")
                else viewModel.showToast("导入失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    var showOnlineImport by remember { mutableStateOf(false) }
    var onlineUrl by remember { mutableStateOf("") }
    var isOnlineImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    if (showOnlineImport) {
        AppleCenterDialog(
            onDismiss = { if (!isOnlineImporting) showOnlineImport = false },
            title = "在线导入MusicFree插件",
            primaryLabel = "导入",
            onPrimary = {
                if (!isOnlineImporting) {
                    if (onlineUrl.isBlank()) {
                        viewModel.showToast("请输入插件URL")
                    } else {
                        isOnlineImporting = true
                        scope.launch {
                            val result = viewModel.importMusicFreePluginFromUrl(onlineUrl.trim())
                            isOnlineImporting = false
                            if (result.isSuccess) {
                                viewModel.showToast("在线导入成功")
                                showOnlineImport = false
                                onlineUrl = ""
                            } else {
                                val errMsg = result.exceptionOrNull()?.message ?: "未知错误"
                                importError = errMsg
                                viewModel.showToast("导入失败: $errMsg")
                            }
                        }
                    }
                }
            },
            secondaryLabel = "取消",
            onSecondary = { if (!isOnlineImporting) showOnlineImport = false },
        ) {
            Column {
                Text("输入 MusicFree 插件（.js）的下载链接，将自动下载并导入。", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = onlineUrl,
                    onValueChange = { onlineUrl = it; importError = null },
                    label = { Text("插件URL") },
                    placeholder = { Text("https://example.com/plugin.js") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    enabled = !isOnlineImporting,
                    isError = importError != null,
                )
                if (importError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(importError!!, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
                if (isOnlineImporting) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("正在下载并导入...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    AppleSheet(
        onDismiss = onBack,
        title = "MusicFree 插件",
        primaryLabel = "完成",
        onPrimary = onBack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 说明文本
            Text(
                "MusicFree 插件支持音乐搜索和播放。导入 .js 插件文件后即可在搜索页选择对应平台使用。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pluginPicker.launch(arrayOf("text/javascript", "application/javascript", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                ) { Text("导入JS", fontSize = 12.sp, maxLines = 1) }
                Button(
                    onClick = { showOnlineImport = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                ) { Text("在线导入", fontSize = 12.sp, maxLines = 1) }
                OutlinedButton(
                    onClick = { viewModel.removeAllMusicFreePlugins() },
                    modifier = Modifier.weight(1f),
                ) { Text("全部移除", fontSize = 12.sp, maxLines = 1) }
            }
            Spacer(Modifier.height(12.dp))

            if (viewModel.musicFreePlugins.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudDownload, null, tint = cs.onSurfaceVariant.copy(0.3f), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("暂无 MusicFree 插件", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Text("点击上方按钮导入 .js 插件文件", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(0.5f))
                    }
                }
            } else {
                Text("已加载 ${viewModel.musicFreePlugins.size} 个 MusicFree 插件", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                viewModel.musicFreePlugins.forEach { plugin ->
                    val enabled = plugin.enabled
                    val mounted = plugin.mounted
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (enabled && mounted) cs.surfaceVariant.copy(0.5f) else cs.surfaceVariant.copy(0.2f))
                        .then(if (enabled && mounted) Modifier.border(1.dp, accentColor.copy(0.3f), RoundedCornerShape(12.dp)) else Modifier)
                        .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    plugin.info.platform.ifBlank { plugin.fileName },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled && mounted) cs.onSurface else cs.onSurface.copy(0.4f),
                                )
                                val subtitle = buildString {
                                    if (plugin.info.version.isNotBlank()) append("v${plugin.info.version}")
                                    if (plugin.info.author.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append(plugin.info.author)
                                    }
                                    if (!mounted) {
                                        if (isNotEmpty()) append(" · ")
                                        append("加载失败")
                                    }
                                }
                                if (subtitle.isNotBlank()) {
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(if (enabled && mounted) 1f else 0.4f))
                                }
                                if (!mounted && plugin.errorMsg.isNotBlank()) {
                                    Text(
                                        "错误: ${plugin.errorMsg.take(80)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.error.copy(0.7f),
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { viewModel.toggleMusicFreePlugin(plugin.id, it) },
                                enabled = mounted,
                                colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
                                modifier = Modifier.height(24.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { viewModel.removeMusicFreePlugin(plugin.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, "删除", tint = cs.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** MusicFree 插件列表行（在音源与插件区显示已导入的插件） */
@Composable
private fun ModernMusicFreePluginListRow(
    plugin: com.yindong.music.data.musicfree.MusicFreePluginEntry,
    viewModel: MusicViewModel,
) {
    val cs = MaterialTheme.colorScheme
    val accentColor = Color(0xFF34C759)
    val enabled = plugin.enabled && plugin.mounted
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (enabled) cs.surfaceVariant.copy(0.5f) else cs.surfaceVariant.copy(0.2f))
            .then(if (enabled) Modifier.border(1.dp, accentColor.copy(0.2f), RoundedCornerShape(12.dp)) else Modifier)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CloudDownload, null,
                tint = if (enabled) accentColor else cs.onSurfaceVariant.copy(0.4f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    plugin.info.platform.ifBlank { plugin.fileName },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) cs.onSurface else cs.onSurface.copy(0.4f),
                )
                Text(
                    if (enabled) "已启用" else if (!plugin.mounted) "加载失败" else "已禁用",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant.copy(if (enabled) 1f else 0.4f),
                )
            }
            Switch(
                checked = plugin.enabled,
                onCheckedChange = { viewModel.toggleMusicFreePlugin(plugin.id, it) },
                enabled = plugin.mounted,
                colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
                modifier = Modifier.height(24.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ═══ 崩溃日志列表 ═══
@Composable
private fun CrashLogListDialog(viewModel: MusicViewModel, onBack: () -> Unit, onDetail: (CrashLogEntry) -> Unit) {
    val logs = remember { viewModel.fetchCrashLogs() }
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    AppleSheet(
        onDismiss = onBack,
        title = "崩溃日志",
        scrollable = false,
        primaryLabel = "返回",
        onPrimary = onBack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (logs.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { viewModel.clearCrashLogs(); onBack() }, Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteSweep, "清空", tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BugReport, null, tint = cs.onSurfaceVariant.copy(0.3f), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("暂无崩溃记录", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Text("运行稳定", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(0.5f))
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(logs, key = { it.fileName }, contentType = { "log" }) { entry ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onDetail(entry) }.padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (entry.isCrash) accentColor.copy(0.1f) else Color(0xFFFF9800).copy(0.1f)), contentAlignment = Alignment.Center) {
                                Text(if (entry.isCrash) "💥" else "⚠️", fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (entry.isCrash) "崩溃" else "异常", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (entry.isCrash) accentColor else Color(0xFFFF9800))
                                Text(entry.time, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            Text("${entry.sizeKB} KB", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(0.5f))
                            Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant.copy(0.3f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ═══ 崩溃日志详情 ═══
@Composable
private fun CrashLogDetailDialog(entry: CrashLogEntry, onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    AppleSheet(
        onDismiss = onBack,
        title = if (entry.isCrash) "崩溃详情" else "异常详情",
        subtitle = entry.time,
        primaryLabel = "返回",
        onPrimary = onBack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(entry.content)); android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show() }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, "复制", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant.copy(0.5f)).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Text(entry.content, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp), color = cs.onSurface)
            }
        }
    }
}

// ═══ 创建歌单 ═══
@Composable
private fun CreatePlaylistDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val accentColor = MaterialTheme.colorScheme.primary
    AppleCenterDialog(
        onDismiss = onDismiss,
        title = "创建歌单",
        primaryLabel = "创建",
        onPrimary = { viewModel.createPlaylist(name); onDismiss() },
        secondaryLabel = "取消",
        onSecondary = onDismiss,
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("歌单名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

// ═══ 主题选择 ═══
@Composable
private fun ThemePickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    var selectedSchemeId by remember { mutableStateOf(ThemeManager.getSavedColorSchemeId(context)) }
    var dynamicActive by remember { mutableStateOf(ThemeManager.isDynamicColorActive) }
    LaunchedEffect(Unit) {
        launch { ThemeManager.colorSchemeState.collectLatest { scheme -> selectedSchemeId = scheme.id } }
        launch { ThemeManager.dynamicColorEnabled.collectLatest { dynamicActive = ThemeManager.isDynamicColorActive } }
    }

    AppleSheet(
        onDismiss = onDismiss,
        title = "配色方案",
        scrollable = false,
        primaryLabel = "完成",
        onPrimary = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 动态色激活提示横幅
            if (dynamicActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "动态配色已开启，选择任意配色将关闭动态色",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(AllColorSchemes, key = { it.id }, contentType = { "scheme" }) { scheme ->
                    val isSelected = selectedSchemeId == scheme.id
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.08f) else cs.surfaceVariant)
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) accentColor else cs.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable { ThemeManager.setColorScheme(context, scheme.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(scheme.previewColor), contentAlignment = Alignment.Center) {
                            if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            else Icon(Icons.Default.Palette, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(scheme.name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) accentColor else cs.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(scheme.id, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = accentColor, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

// ═══ 关于应用 ═══
private const val OFFICIAL_WEBSITE_URL = "https://yindong-music.github.io"
private const val GITHUB_REPO_URL = "https://github.com/yindong-music/yindong-music"

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    AppleSheet(
        onDismiss = onDismiss,
        title = "关于应用",
        primaryLabel = "关闭",
        onPrimary = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.5f)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("音动音乐", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("v3.0.4", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("一款基于 Jetpack Compose 的现代音乐播放器", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_WEBSITE_URL))) } }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("官网", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL))) } }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("GitHub", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            val displayAuthorQq = RemoteConfig.officialAuthorQq.ifBlank { CriticalUiProtector.communityQqNumber() }
            Text("作者QQ: $displayAuthorQq", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("音乐资源来自互联网公开接口，仅供个人学习试听，请支持正版音乐。", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

// ═══ 屏幕方向设置 ═══
@Composable
private fun OrientationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf(LocalStorage.loadScreenOrientation()) }

    val options = listOf(
        "SYSTEM" to "跟随系统",
        "PORTRAIT" to "竖屏",
        "LANDSCAPE" to "横屏（向左）",
        "REVERSE_LANDSCAPE" to "横屏（向右）",
    )

    AppleSheet(
        onDismiss = onDismiss,
        title = "屏幕方向",
        primaryLabel = "完成",
        onPrimary = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                AppleSheetOption(
                    text = label,
                    selected = selected == value,
                    onClick = {
                        selected = value
                        LocalStorage.saveScreenOrientation(value)
                        // 立即应用方向
                        val activity = context as? android.app.Activity
                        activity?.requestedOrientation = when (value) {
                            "PORTRAIT" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            "LANDSCAPE" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            "REVERSE_LANDSCAPE" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    },
                )
                if (index != options.lastIndex) AppleSheetSeparator()
            }
        }
    }
}

// ═══ 界面主题模式设置 ═══
@Composable
private fun ThemeModeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf(ThemeManager.themeMode.value) }

    val options = listOf(
        com.yindong.music.ui.theme.ThemeMode.LIGHT to "浅色模式",
        com.yindong.music.ui.theme.ThemeMode.DARK to "深色模式",
        com.yindong.music.ui.theme.ThemeMode.SYSTEM to "跟随系统",
    )

    AppleSheet(
        onDismiss = onDismiss,
        title = "界面主题",
        primaryLabel = "完成",
        onPrimary = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                AppleSheetOption(
                    text = label,
                    selected = selected == mode,
                    onClick = {
                        selected = mode
                        ThemeManager.setThemeMode(context, mode)
                    },
                )
                if (index != options.lastIndex) AppleSheetSeparator()
            }
        }
    }
}

// ═══ 车载歌词设置 ═══
@Composable
internal fun CarLyricSettingsDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    // 悬浮窗歌词颜色预设
    val overlayColors = remember {
        listOf(
            "白色" to android.graphics.Color.WHITE, "绿色" to android.graphics.Color.parseColor("#4CAF50"),
            "青色" to android.graphics.Color.parseColor("#00BCD4"), "粉色" to android.graphics.Color.parseColor("#FF4081"),
            "黄色" to android.graphics.Color.parseColor("#FFEB3B"), "橙色" to android.graphics.Color.parseColor("#FF9800"),
            "紫色" to android.graphics.Color.parseColor("#AB47BC"), "蓝色" to android.graphics.Color.parseColor("#2196F3"),
            "红色" to android.graphics.Color.parseColor("#F44336"),
        )
    }
    // 蓝牙连接状态（仅展示，不驱动开关）
    val btConnected = viewModel.isHeadsetConnected &&
        viewModel.headsetType == com.yindong.music.data.BluetoothHeadsetManager.HeadsetType.BLUETOOTH
    val btName = viewModel.connectedHeadsetName
    // 悬浮窗权限
    val hasOverlayPerm = remember { android.provider.Settings.canDrawOverlays(context) }

    AppleSheet(
        onDismiss = onDismiss,
        title = "车载蓝牙歌词",
        primaryLabel = "完成",
        onPrimary = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ① 总开关
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Bluetooth, null, tint = cs.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("车载蓝牙歌词", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text("前台横屏大字 · 后台悬浮窗", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.5f))
                }
                Switch(
                    checked = viewModel.carBtLyricsEnabled,
                    onCheckedChange = { viewModel.toggleCarBtLyrics() },
                )
            }

            AppleSheetSeparator()

            // ② 蓝牙状态
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("蓝牙状态", fontSize = 14.sp, color = cs.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Text(
                    if (btConnected) (btName?.takeIf { it.isNotBlank() } ?: "已连接") else "未连接",
                    fontSize = 14.sp,
                    color = if (btConnected) Color(0xFF34C759) else cs.onSurface.copy(alpha = 0.5f),
                )
            }
            Text(
                "蓝牙仅作状态展示，连接/断开不会自动开关歌词",
                fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.45f),
            )

            AppleSheetSeparator()

            // ③ 歌词同步偏移
            Text("歌词同步偏移: ${viewModel.lyricSyncOffsetMs}ms", fontSize = 14.sp, color = cs.onSurface)
            Slider(
                value = viewModel.lyricSyncOffsetMs.toFloat(),
                onValueChange = { viewModel.updateLyricSyncOffsetMs(it.toInt()) },
                valueRange = -5000f..5000f,
                steps = 99,
            )
            Text(
                "正值延后，负值提前，对横屏与悬浮窗同时生效",
                fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(12.dp))
            AppleSheetSeparator()

            // ④ 横屏字体大小
            Text("横屏字体大小: ${viewModel.carLyricFontSize}sp", fontSize = 14.sp, color = cs.onSurface)
            Slider(
                value = viewModel.carLyricFontSize.toFloat(),
                onValueChange = { viewModel.updateCarLyricFontSize(it.toInt()) },
                valueRange = 18f..56f,
                steps = 18,
            )

            Spacer(Modifier.height(12.dp))
            // ⑤ 横屏背景透明度
            Text("横屏背景透明度: ${viewModel.carLyricBgOpacity}%", fontSize = 14.sp, color = cs.onSurface)
            Slider(
                value = viewModel.carLyricBgOpacity.toFloat(),
                onValueChange = { viewModel.updateCarLyricBgOpacity(it.toInt()) },
                valueRange = 0f..100f,
                steps = 20,
            )

            Spacer(Modifier.height(12.dp))
            AppleSheetSeparator()

            // ⑥ 悬浮窗歌词颜色
            Text("悬浮窗歌词颜色", fontSize = 14.sp, color = cs.onSurface)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                overlayColors.forEach { (_, color) ->
                    val selected = color == viewModel.floatingLyricsColor
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .then(if (selected) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier)
                            .clickable { viewModel.changeFloatingLyricsColor(color) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // ⑦ 悬浮窗字体大小
            Text("悬浮窗字体大小: ${viewModel.floatingLyricSize}sp", fontSize = 14.sp, color = cs.onSurface)
            Slider(
                value = viewModel.floatingLyricSize.toFloat(),
                onValueChange = { viewModel.changeFloatingLyricSize(it.toInt()) },
                valueRange = 10f..30f,
                steps = 19,
            )

            // ⑧ 悬浮窗权限
            if (!hasOverlayPerm) {
                Spacer(Modifier.height(12.dp))
                AppleSheetSeparator()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "未授予悬浮窗权限，后台将无法显示悬浮歌词",
                        fontSize = 12.sp, color = Color(0xFFFF3B30), modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.requestOverlayPermission() }) { Text("去授权") }
                }
            }
        }
    }
}
