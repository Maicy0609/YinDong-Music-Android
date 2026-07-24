@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.yindong.music.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yindong.music.R
import com.yindong.music.data.api.MusicApiConfig
import com.yindong.music.data.model.AlbumSearchResult
import com.yindong.music.data.model.ArtistSearchResult
import com.yindong.music.data.model.MusicSheetSearchResult
import com.yindong.music.data.model.Song
import com.yindong.music.ui.components.AddToPlaylistSheet
import com.yindong.music.ui.components.SongItem
import com.yindong.music.ui.components.liquidGlassEffect
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel

@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onSongRecognition: () -> Unit = {},
    onNavigateToPlugins: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
    val labelSecondary = if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.6f) else Color(0xFF3C3C43).copy(alpha = 0.6f)
    val labelTertiary = if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.3f) else Color(0xFF3C3C43).copy(alpha = 0.3f)
    val separator = if (isDark) Color(0xFF545458).copy(alpha = 0.65f) else Color(0xFF3C3C43).copy(alpha = 0.36f)
    val accentColor = Color(0xFF007AFF)
    val surfaceColor = secondaryBackground
    val cardColor = tertiaryBackground
    val subtleBorder = separator
    val accent = accentColor
    val accentSoft = accent.copy(alpha = 0.08f)
    val primaryText = labelPrimary
    val secondaryText = labelSecondary
    val tertiaryText = labelTertiary

    // ── 平台列表（全部 + 5 个内置平台 + 用户导入的 MusicFree 插件） ──
    // "全部"并行搜索网易云/QQ/酷我/酷狗各前4条；其余为单平台搜索
    // MusicFree 插件显示在 全部/网易云/QQ/酷我/酷狗/咪咕 之后
    val musicFreePlugins = viewModel.musicFreePlugins
    val builtinPlatforms = listOf(
        Triple("全部", R.drawable.ic_launcher_foreground as Any, accent),
        Triple("网易云", R.drawable.netease_logo as Any, accent),
        Triple("QQ", R.drawable.qqmusic_logo as Any, accent),
        Triple("酷我", R.drawable.kuwo_logo as Any, accent),
        Triple("酷狗", R.drawable.kugou_logo as Any, accent),
        Triple("咪咕", R.drawable.ic_launcher_foreground as Any, accent),
    )
    val mfPlatformList = musicFreePlugins
        .filter { it.enabled && it.mounted }
        .map { Triple(it.info.platform.ifBlank { it.fileName }, R.drawable.ic_launcher_foreground as Any, accent) }
    val platforms = builtinPlatforms + mfPlatformList

    var selectedPlatform by rememberSaveable { mutableStateOf("全部") }
    val searchTabs = listOf("单曲", "专辑", "作者", "歌单")
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showDetail by rememberSaveable { mutableStateOf(false) }

    // 更多菜单 / 添加到歌单 / 下载音质 弹窗状态
    var moreMenuSong by remember { mutableStateOf<Song?>(null) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var downloadQualitySong by remember { mutableStateOf<Song?>(null) }

    // 内置拼音输入面板（为没有中文输入法的车载设备提供）
    var showPinyinPanel by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 单曲结果过滤（"全部"平台显示所有结果）
    val filteredResults = if (selectedPlatform == "全部") {
        viewModel.onlineResults
    } else {
        viewModel.onlineResults.filter { song ->
            song.platform == selectedPlatform ||
                song.platform.contains(selectedPlatform, ignoreCase = true) ||
                selectedPlatform.contains(song.platform, ignoreCase = true) ||
                viewModel.resolveSearchPlatform(song.platform) == viewModel.resolveSearchPlatform(selectedPlatform)
        }
    }

    val listState = rememberLazyListState()

    // 滚动到底部自动加载更多（保留 load-more 逻辑）
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3 && viewModel.hasMoreResults && !viewModel.isLoadingMore && !viewModel.isSearching
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreResults()
    }

    val triggerSearch: (String) -> Unit = { q ->
        val query = q.trim()
        if (query.isNotBlank()) {
            if (viewModel.isShareLink(query)) {
                viewModel.parseAndPlay(query)
            } else {
                when (selectedTab) {
                    0 -> {
                        if (selectedPlatform == "全部") {
                            viewModel.performAllPlatformSearch(query)
                        } else {
                            viewModel.performSinglePlatformSearch(query, selectedPlatform)
                        }
                    }
                    1 -> viewModel.searchAlbums(query, selectedPlatform)
                    2 -> viewModel.searchArtists(query, selectedPlatform)
                    3 -> viewModel.searchSheets(query, selectedPlatform)
                }
            }
        }
        // 触发检索的同时回收输入法，避免键盘遮挡搜索结果
        keyboardController?.hide()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(systemBackground)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶部应用栏：返回 + 搜索框 ──
            SearchTopBar(
                query = viewModel.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onBack = onBack,
                onSearch = {
                    val q = viewModel.searchQuery.trim()
                    if (q.isNotBlank()) triggerSearch(q)
                },
                surfaceColor = surfaceColor,
                accent = accent,
                primaryText = primaryText,
                secondaryText = secondaryText,
                isShareLink = viewModel.isShareLink(viewModel.searchQuery.trim()),
                onParseShareLink = { viewModel.parseAndPlay(viewModel.searchQuery.trim()) },
                showPinyinPanel = showPinyinPanel,
                onTogglePinyinPanel = {
                    showPinyinPanel = !showPinyinPanel
                    if (showPinyinPanel) keyboardController?.hide()
                },
            )

            // 链接解析中
            if (viewModel.isParsing) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accent, strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("正在解析链接...", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                }
            }

            // 错误提示（仅在无结果时显示，避免有结果还显示搜索失败）
            viewModel.parseError?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            if (viewModel.onlineResults.isEmpty() && viewModel.searchError != null) {
                viewModel.searchError?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                }
            }

            // 内置搜索算法不需要插件，移除"需要导入插件"引导

            when {
                // ── 没有启用任何插件：显示"没有导入插件"提示，不显示搜索结果 ──
                viewModel.hasNoAvailablePlugin -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceColor)
                            .border(width = 1.dp, color = subtleBorder, shape = RoundedCornerShape(12.dp))
                            .clickable { onNavigateToPlugins() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "没有导入插件，请在「我的 → 插件管理」中启用一个插件后搜索",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // ── 已执行搜索：完整结果 UI ──
                viewModel.onlineResults.isNotEmpty() || viewModel.isSearching ||
                    (viewModel.searchQuery.isNotEmpty() && !viewModel.isSearching && viewModel.hasSearched) ||
                    viewModel.albumSearchResults.isNotEmpty() || viewModel.artistSearchResults.isNotEmpty() ||
                    viewModel.sheetSearchResults.isNotEmpty() ||
                    viewModel.isSearchingAlbum || viewModel.isSearchingArtist || viewModel.isSearchingSheet -> {

                    // 平台选择 + Tab 段控件
                    if (platforms.isEmpty()) {
                        // 无插件提示
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(surfaceColor)
                                .border(width = 1.dp, color = subtleBorder, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "未导入任何插件，请在「我的 → 插件管理」中导入落雪插件后搜索",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        PlatformSelectorRow(
                            platforms = platforms,
                            selectedPlatform = selectedPlatform,
                            onPlatformSelected = { platform ->
                                selectedPlatform = platform
                                if (viewModel.searchQuery.isNotBlank()) {
                                    when (selectedTab) {
                                        0 -> {
                                            if (platform == "全部") {
                                                viewModel.performAllPlatformSearch(viewModel.searchQuery.trim())
                                            } else {
                                                viewModel.performSinglePlatformSearch(viewModel.searchQuery.trim(), platform)
                                            }
                                        }
                                        1 -> viewModel.searchAlbums(viewModel.searchQuery.trim(), platform)
                                        2 -> viewModel.searchArtists(viewModel.searchQuery.trim(), platform)
                                        3 -> viewModel.searchSheets(viewModel.searchQuery.trim(), platform)
                                    }
                                }
                            },
                            surfaceColor = surfaceColor,
                            cardColor = cardColor,
                            subtleBorder = subtleBorder,
                            accent = accent,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SearchSegmentedTabs(
                        tabs = searchTabs,
                        selectedIndex = selectedTab,
                        onTabSelected = { tab ->
                            selectedTab = tab
                            if (viewModel.searchQuery.isNotBlank()) {
                                when (tab) {
                                    0 -> viewModel.performSinglePlatformSearch(viewModel.searchQuery.trim(), selectedPlatform)
                                    1 -> viewModel.searchAlbums(viewModel.searchQuery.trim(), selectedPlatform)
                                    2 -> viewModel.searchArtists(viewModel.searchQuery.trim(), selectedPlatform)
                                    3 -> viewModel.searchSheets(viewModel.searchQuery.trim(), selectedPlatform)
                                }
                            }
                        },
                        accent = accent,
                        surfaceColor = surfaceColor,
                        primaryText = primaryText,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (showDetail && viewModel.detailSongs.isNotEmpty()) {
                        DetailSongList(
                            title = viewModel.detailTitle,
                            coverUrl = viewModel.detailCoverUrl,
                            subtitle = viewModel.detailSubtitle,
                            songs = viewModel.detailSongs,
                            isLoading = viewModel.isLoadingDetail,
                            onBack = {
                                showDetail = false
                                viewModel.clearDetail()
                            },
                            onPlayAll = { viewModel.playAllDetailSongs() },
                            onSongClick = { index -> viewModel.playDetailSong(index) },
                            cardColor = cardColor,
                            subtleBorder = subtleBorder,
                            accent = accent,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            tertiaryText = tertiaryText,
                        )
                    } else {
                        val isSearchingTab = when (selectedTab) {
                            0 -> viewModel.isSearching
                            1 -> viewModel.isSearchingAlbum
                            2 -> viewModel.isSearchingArtist
                            3 -> viewModel.isSearchingSheet
                            else -> false
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 170.dp),
                        ) {
                            if (isSearchingTab) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(modifier = Modifier.size(36.dp), color = accent, strokeWidth = 2.5.dp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("正在搜索中...", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                                        }
                                    }
                                }
                            }

                            when (selectedTab) {
                                0 -> {
                                    if (viewModel.onlineResults.isNotEmpty()) {
                                        if (filteredResults.isEmpty()) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text("该平台暂无结果，换个平台试试", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                                                }
                                            }
                                        } else {
                                            itemsIndexed(filteredResults) { index, song ->
                                                SongItem(
                                                    song = song,
                                                    index = index,
                                                    onSongClick = { viewModel.playPlaylist(filteredResults, index) },
                                                    onMoreClick = { moreMenuSong = song },
                                                    modifier = Modifier.padding(horizontal = 4.dp),
                                                )
                                            }
                                        }

                                        // 加载更多指示
                                        if (viewModel.hasMoreResults) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    if (viewModel.isLoadingMore) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text("加载中...", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                                                        }
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(surfaceColor)
                                                                .clickable { viewModel.loadMoreResults() }
                                                                .padding(vertical = 14.dp),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Text("加载更多", style = MaterialTheme.typography.bodyMedium, color = secondaryText, fontWeight = FontWeight.Medium)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    val albums = viewModel.albumSearchResults
                                    if (albums.isNotEmpty()) {
                                        items(albums) { album ->
                                            ResultCard(
                                                coverUrl = album.coverUrl,
                                                title = album.title,
                                                subtitle = album.artist,
                                                platform = album.platform,
                                                onClick = {
                                                    showDetail = true
                                                    viewModel.loadAlbumDetail(album)
                                                },
                                                cardColor = cardColor,
                                                subtleBorder = subtleBorder,
                                                accent = accent,
                                                primaryText = primaryText,
                                                secondaryText = secondaryText,
                                            )
                                        }
                                    } else if (!isSearchingTab) {
                                        item { EmptyHint("暂无专辑结果", secondaryText) }
                                    }
                                }
                                2 -> {
                                    val artists = viewModel.artistSearchResults
                                    if (artists.isNotEmpty()) {
                                        items(artists) { artist ->
                                            ResultCard(
                                                coverUrl = artist.avatarUrl,
                                                title = artist.name,
                                                subtitle = if (artist.songCount > 0) "${artist.songCount} 首歌曲" else "",
                                                platform = artist.platform,
                                                isCircle = true,
                                                onClick = {
                                                    showDetail = true
                                                    viewModel.loadArtistWorks(artist)
                                                },
                                                cardColor = cardColor,
                                                subtleBorder = subtleBorder,
                                                accent = accent,
                                                primaryText = primaryText,
                                                secondaryText = secondaryText,
                                            )
                                        }
                                    } else if (!isSearchingTab) {
                                        item { EmptyHint("暂无作者结果", secondaryText) }
                                    }
                                }
                                3 -> {
                                    val sheets = viewModel.sheetSearchResults
                                    if (sheets.isNotEmpty()) {
                                        items(sheets) { sheet ->
                                            ResultCard(
                                                coverUrl = sheet.coverUrl,
                                                title = sheet.title,
                                                subtitle = buildString {
                                                    if (sheet.creator.isNotBlank()) append(sheet.creator)
                                                    if (sheet.songCount > 0) {
                                                        if (isNotEmpty()) append(" · ")
                                                        append("${sheet.songCount} 首")
                                                    }
                                                    if (sheet.playCount > 0) {
                                                        if (isNotEmpty()) append(" · ")
                                                        append(formatPlayCount(sheet.playCount))
                                                    }
                                                },
                                                platform = sheet.platform,
                                                onClick = {
                                                    showDetail = true
                                                    viewModel.loadMusicSheetDetail(sheet)
                                                },
                                                cardColor = cardColor,
                                                subtleBorder = subtleBorder,
                                                accent = accent,
                                                primaryText = primaryText,
                                                secondaryText = secondaryText,
                                            )
                                        }
                                    } else if (!isSearchingTab) {
                                        item { EmptyHint("暂无歌单结果", secondaryText) }
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }

                // ── 有输入但无结果：搜索建议 ──
                viewModel.searchQuery.isNotEmpty() -> {
                    if (viewModel.searchSuggestions.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 170.dp),
                        ) {
                            item {
                                Text(
                                    "搜索建议",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = secondaryText,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                            items(viewModel.searchSuggestions) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.performOnlineSearch(suggestion.keyword)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Search, null, tint = tertiaryText, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(suggestion.keyword, style = MaterialTheme.typography.bodyMedium, color = primaryText)
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Search, null, tint = tertiaryText, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("按回车键搜索", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                                Text("搜索已导入的插件音源", style = MaterialTheme.typography.bodySmall, color = tertiaryText)
                            }
                        }
                    }
                }

                // ── 无输入：快捷入口 + 热搜 + 发现 + 历史 ──
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 170.dp),
                    ) {
                        // ── 未启用任何插件时，显示醒目引导 ──
                        if (viewModel.hasNoAvailablePlugin) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 12.dp, bottom = 8.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(accentSoft)
                                        .border(width = 1.dp, color = accent.copy(alpha = 0.3f), shape = RoundedCornerShape(16.dp))
                                        .clickable { onNavigateToPlugins() }
                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("尚未启用任何插件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryText)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("搜索前请先在「我的 → 插件管理」中启用一个插件，点击此处前往", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                                        }
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = accent, modifier = Modifier.size(20.dp).rotate(180f))
                                    }
                                }
                            }
                        }

                        // 快捷入口卡片
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                QuickEntryCard(
                                    icon = Icons.Default.Link,
                                    title = "粘贴链接解析",
                                    subtitle = "支持抖音/汽水音乐分享",
                                    accent = accent,
                                    accentSoft = accentSoft,
                                    cardColor = cardColor,
                                    subtleBorder = subtleBorder,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    modifier = Modifier.weight(1f),
                                    onClick = {},
                                )
                                QuickEntryCard(
                                    icon = Icons.Default.Mic,
                                    title = "听歌识曲",
                                    subtitle = "识别身边播放的歌曲",
                                    accent = accent,
                                    accentSoft = accentSoft,
                                    cardColor = cardColor,
                                    subtleBorder = subtleBorder,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSongRecognition() },
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // 大家都在搜
                        item {
                            SectionHeader(
                                icon = Icons.Default.LocalFireDepartment,
                                title = "大家都在搜",
                                accent = accent,
                                primaryText = primaryText,
                            )
                        }
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            ) {
                                viewModel.hotSearches.forEachIndexed { index, keyword ->
                                    val isTop3 = index < 3
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isTop3) accentSoft else surfaceColor)
                                            .border(
                                                width = 1.dp,
                                                color = if (isTop3) accent.copy(alpha = 0.2f) else subtleBorder,
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .clickable {
                                                viewModel.performOnlineSearch(keyword)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isTop3) {
                                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            keyword,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isTop3) accent else secondaryText,
                                            fontWeight = if (isTop3) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }

                        // 快速发现
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            SectionHeader(
                                icon = Icons.Default.Star,
                                title = "快速发现",
                                accent = accent,
                                primaryText = primaryText,
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                item {
                                    DiscoverChip(icon = Icons.Default.TrendingUp, label = "抖音热歌", accent = accent, onClick = {
                                        viewModel.performOnlineSearch("抖音热歌")
                                    })
                                }
                                item {
                                    DiscoverChip(icon = Icons.Default.Favorite, label = "伤感情歌", accent = accent, onClick = {
                                        viewModel.performOnlineSearch("伤感情歌")
                                    })
                                }
                                item {
                                    DiscoverChip(icon = Icons.Default.MusicNote, label = "怀旧金曲", accent = accent, onClick = {
                                        viewModel.performOnlineSearch("怀旧金曲")
                                    })
                                }
                                item {
                                    DiscoverChip(icon = Icons.Default.MusicNote, label = "车载音乐", accent = accent, onClick = {
                                        viewModel.performOnlineSearch("车载音乐")
                                    })
                                }
                                item {
                                    DiscoverChip(icon = Icons.Default.MusicNote, label = "古风音乐", accent = accent, onClick = {
                                        viewModel.performOnlineSearch("古风音乐")
                                    })
                                }
                            }
                        }

                        // 搜索历史
                        if (viewModel.searchHistory.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.History, null, tint = secondaryText, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("搜索历史", fontSize = 22.sp, color = primaryText, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                                    }
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "清除历史",
                                        tint = secondaryText,
                                        modifier = Modifier.size(20.dp).clickable { viewModel.clearSearchHistory() },
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            item {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                ) {
                                    viewModel.searchHistory.take(10).forEach { keyword ->
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(surfaceColor)
                                                .clickable {
                                                    viewModel.performOnlineSearch(keyword)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Default.History, null, tint = tertiaryText, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(keyword, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 歌曲更多操作弹窗 ──
    moreMenuSong?.let { song ->
        SongMoreSheet(
            song = song,
            isFavorite = viewModel.isFavorite(song),
            onPlay = { viewModel.playPlaylist(listOf(song), 0) },
            onPlayNext = { viewModel.playNextInQueue(song) },
            onToggleFavorite = { viewModel.toggleFavorite(song) },
            onAddToPlaylist = { addToPlaylistSong = song },
            onDownload = { downloadQualitySong = song },
            onDismiss = { moreMenuSong = null },
            cardColor = cardColor,
            subtleBorder = subtleBorder,
            accent = accent,
            primaryText = primaryText,
            secondaryText = secondaryText,
            tertiaryText = tertiaryText,
            errorColor = cs.error,
        )
    }

    // ── 添加到歌单弹窗 ──
    addToPlaylistSong?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = viewModel.myPlaylists,
            onSelect = { playlistId ->
                viewModel.addToPlaylist(playlistId, song)
                viewModel.showToast("已添加到歌单")
            },
            onDismiss = { addToPlaylistSong = null },
        )
    }

    // ── 下载音质选择弹窗 ──
    downloadQualitySong?.let { song ->
        DownloadQualitySheet(
            song = song,
            supportedQualities = viewModel.activePluginSupportedQualities,
            onDownload = { quality ->
                viewModel.downloadSongWithQuality(song, quality)
            },
            onDismiss = { downloadQualitySong = null },
        )
    }

    // 内置拼音输入面板（为没有中文输入法的车载设备提供）
    if (showPinyinPanel) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            com.yindong.music.ui.components.PinyinInputPanel(
                onCharacterSelected = { ch ->
                    viewModel.updateSearchQuery(viewModel.searchQuery + ch)
                },
                onBackspace = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        viewModel.updateSearchQuery(viewModel.searchQuery.dropLast(1))
                    }
                },
                onSearch = {
                    val q = viewModel.searchQuery.trim()
                    if (q.isNotBlank()) triggerSearch(q)
                },
                onDismiss = {
                    showPinyinPanel = false
                },
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
// 子组件
// ────────────────────────────────────────────────────────────

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    surfaceColor: Color,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
    isShareLink: Boolean,
    onParseShareLink: () -> Unit,
    showPinyinPanel: Boolean = false,
    onTogglePinyinPanel: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = primaryText,
                modifier = Modifier.size(22.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .liquidGlassEffect(RoundedCornerShape(12.dp)),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                placeholder = {
                    Text(
                        if (isShareLink) "检测到分享链接，点击解析" else "搜索歌曲、专辑、歌手",
                        color = secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "清除", tint = secondaryText, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Icon(Icons.Default.Mic, contentDescription = "听歌识曲", tint = secondaryText, modifier = Modifier.size(20.dp))
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = accent,
                    focusedTextColor = primaryText,
                    unfocusedTextColor = primaryText,
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }

        // 内置拼音输入面板切换按钮
        IconButton(onClick = onTogglePinyinPanel, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Keyboard,
                contentDescription = "内置输入法",
                tint = if (showPinyinPanel) accent else secondaryText,
                modifier = Modifier.size(22.dp),
            )
        }

        if (isShareLink) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent)
                    .clickable { onParseShareLink() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("解析", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                "搜索",
                color = accent,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp).clickable { onSearch() },
            )
        }
    }
}

@Composable
private fun PlatformSelectorRow(
    platforms: List<Triple<String, Any, Color>>,
    selectedPlatform: String,
    onPlatformSelected: (String) -> Unit,
    surfaceColor: Color,
    cardColor: Color,
    subtleBorder: Color,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(platforms) { (name, icon, _) ->
            val isSelected = selectedPlatform == name
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) accent else surfaceColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.Transparent else subtleBorder,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onPlatformSelected(name) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon is Int) {
                    Image(
                        painter = painterResource(id = icon),
                        contentDescription = name,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) Color.White else primaryText,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SearchSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    accent: Color,
    surfaceColor: Color,
    primaryText: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = selectedIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) accent else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) Color.White else primaryText,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    coverUrl: String,
    title: String,
    subtitle: String,
    platform: String,
    isCircle: Boolean = false,
    onClick: () -> Unit,
    cardColor: Color,
    subtleBorder: Color,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .border(width = 1.dp, color = subtleBorder, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(if (isCircle) CircleShape else RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (platform.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(platform, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun QuickEntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    accentSoft: Color,
    cardColor: Color,
    subtleBorder: Color,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .border(width = 1.dp, color = subtleBorder, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = primaryText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, accent: Color, primaryText: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 22.sp, color = primaryText, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    }
}

@Composable
private fun DiscoverChip(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyHint(text: String, secondaryText: Color) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}

@Composable
private fun SongMoreSheet(
    song: Song,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    cardColor: Color,
    subtleBorder: Color,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
    tertiaryText: Color,
    errorColor: Color,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cardColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // 歌曲信息头
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (song.coverUrl.isNotEmpty()) {
                        AsyncImage(model = song.coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = subtleBorder)

            SongActionItem(icon = Icons.Default.PlayArrow, label = "播放", tint = accent, primaryText = primaryText, onClick = { onPlay(); onDismiss() })
            SongActionItem(icon = Icons.Default.QueuePlayNext, label = "下一首播放", tint = primaryText, primaryText = primaryText, onClick = { onPlayNext(); onDismiss() })
            SongActionItem(icon = Icons.Default.Download, label = "下载", tint = primaryText, primaryText = primaryText, onClick = { onDownload(); onDismiss() })
            SongActionItem(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) errorColor else primaryText,
                primaryText = primaryText,
                onClick = { onToggleFavorite(); onDismiss() },
            )
            SongActionItem(icon = Icons.Default.PlaylistAdd, label = "添加到歌单", tint = primaryText, primaryText = primaryText, onClick = { onAddToPlaylist(); onDismiss() })
        }
    }
}

@Composable
private fun SongActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    primaryText: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun DownloadQualitySheet(
    song: Song,
    supportedQualities: Set<MusicApiConfig.Quality>,
    onDownload: (MusicApiConfig.Quality) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isDarkTheme()
    val dlTextColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
    val dlSecondaryColor = if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.6f) else Color(0xFF3C3C43).copy(alpha = 0.6f)
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val dividerColor = if (isDark) Color(0xFF545458).copy(alpha = 0.65f) else Color(0xFF3C3C43).copy(alpha = 0.36f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cardBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("选择下载音质", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = dlTextColor, modifier = Modifier.padding(bottom = 6.dp))
            Text("${song.title} - ${song.artist}", style = MaterialTheme.typography.bodySmall, color = dlSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 14.dp))

            // 仅展示当前生效插件所支持的音质选项；若检测为空（无生效插件或检测失败）则回退为全部可选
            val qualityFilter: (MusicApiConfig.Quality) -> Boolean = { q ->
                supportedQualities.isEmpty() || q in supportedQualities
            }

            // 高端音质
            val premiumDlQualities = listOf(
                MusicApiConfig.Quality.SKY to Triple("沉浸环绕声", "Surround Audio", "环绕音效 最高5.1声道"),
                MusicApiConfig.Quality.JYMASTER to Triple("超清母带", "Master", "极致细节 192kHz/24bit"),
            ).filter { (quality, _) -> qualityFilter(quality) }

            if (premiumDlQualities.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    premiumDlQualities.forEach { (quality, info) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF8F0))
                            .clickable { onDismiss(); onDownload(quality) }
                            .padding(14.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFFFECD2)), contentAlignment = Alignment.Center) {
                            when (quality) {
                                MusicApiConfig.Quality.SKY -> Text("≋", color = Color(0xFFFF9500), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
                                else -> Icon(Icons.Default.MusicNote, null, tint = Color(0xFFFF9500), modifier = Modifier.size(26.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(info.first, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = dlTextColor)
                            if (quality == MusicApiConfig.Quality.SKY) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFECD2)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("听感升级", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9500), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Text(info.second, style = MaterialTheme.typography.bodyMedium, color = dlTextColor, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(info.third, style = MaterialTheme.typography.bodySmall, color = dlSecondaryColor)
                    }
                }
            }
            }

            val normalDlQualities = listOf(
                MusicApiConfig.Quality.JYEFFECT to Triple("高清环绕声", "Audio Vivid", "沉浸三维空间音频，最高7.1.4声道"),
                MusicApiConfig.Quality.HIRES to Triple("Hi-Res 音质", "Hi-Res", "高解析度无损 24bit"),
                MusicApiConfig.Quality.LOSSLESS to Triple("无损音质", "FLAC", "CD 级无损音质 16bit"),
                MusicApiConfig.Quality.EXHIGH to Triple("极高音质", "320K", "高品质 MP3 320kbps"),
                MusicApiConfig.Quality.STANDARD to Triple("标准音质", "128K", "标准 MP3 128kbps"),
            ).filter { (quality, _) -> qualityFilter(quality) }

            if (premiumDlQualities.isEmpty() && normalDlQualities.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("当前插件未检测到支持的音质，请先在插件管理中启用插件", style = MaterialTheme.typography.bodySmall, color = dlSecondaryColor, textAlign = TextAlign.Center)
                }
            }

            normalDlQualities.forEachIndexed { index, (quality, info) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onDismiss(); onDownload(quality) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(
                            when (quality) {
                                MusicApiConfig.Quality.JYEFFECT -> Color(0xFFFFF4E6)
                                MusicApiConfig.Quality.HIRES -> Color(0xFFFFF8E1)
                                MusicApiConfig.Quality.LOSSLESS -> Color(0xFFF0F0F0)
                                MusicApiConfig.Quality.EXHIGH -> Color(0xFFFCE8F0)
                                else -> Color(0xFFF5F5F5)
                            }
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (quality) {
                            MusicApiConfig.Quality.JYEFFECT -> Text("◈", color = Color(0xFFFF9500), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                            MusicApiConfig.Quality.HIRES -> Text("HR", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9500), fontWeight = FontWeight.Bold)
                            MusicApiConfig.Quality.LOSSLESS -> Text("FLAC", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF2D55), fontWeight = FontWeight.Bold)
                            MusicApiConfig.Quality.EXHIGH -> Text("320", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF2D55), fontWeight = FontWeight.Bold)
                            else -> Text("128", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8E8E93), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${info.first} ${info.second}".trim(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = dlTextColor)
                        Text(info.third, style = MaterialTheme.typography.bodySmall, color = dlSecondaryColor, maxLines = 1)
                    }
                    Icon(Icons.Default.Download, null, tint = dlSecondaryColor.copy(0.5f), modifier = Modifier.size(22.dp))
                }
                if (index < normalDlQualities.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 78.dp), color = dividerColor)
                }
            }
        }
    }
}

@Composable
private fun DetailSongList(
    title: String,
    coverUrl: String,
    subtitle: String,
    songs: List<Song>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit,
    cardColor: Color,
    subtleBorder: Color,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
    tertiaryText: Color,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = primaryText, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl.isNotEmpty()) {
                    AsyncImage(model = coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = primaryText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent)
                    .clickable { onPlayAll() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("播放全部", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        HorizontalDivider(color = subtleBorder)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accent, strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("加载中...", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }
        } else if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                Text("暂无歌曲", style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(songs) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(index) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index < 3) accent else tertiaryText,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = if (index < 3) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.bodyMedium, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (song.platform.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(song.platform, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlayCount(count: Long): String {
    return when {
        count >= 100_000_000 -> "${count / 100_000_000}亿"
        count >= 10_000 -> "${count / 10_000}万"
        else -> count.toString()
    }
}
