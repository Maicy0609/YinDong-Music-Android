package com.yindong.music.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yindong.music.security.CriticalUiProtector
import com.yindong.music.ui.components.BottomNavBar
import com.yindong.music.ui.components.BluetoothWelcomeAnimation
import com.yindong.music.ui.components.MiniPlayer
import com.yindong.music.ui.components.QueueBottomSheet
import com.yindong.music.ui.screens.AiAudioEffectScreen
import com.yindong.music.ui.screens.DiscoverScreen
import com.yindong.music.ui.screens.DownloadScreen
import com.yindong.music.ui.screens.ExternalPlaylistDetailScreen
import com.yindong.music.ui.screens.HotChartScreen
import com.yindong.music.ui.screens.ImportPlaylistScreen
import com.yindong.music.ui.screens.PlaylistSquareScreen
import com.yindong.music.ui.screens.MineScreen
import com.yindong.music.ui.screens.PlayerScreen
import com.yindong.music.ui.screens.PlaylistDetailScreen
import com.yindong.music.ui.screens.LocalMusicScreen
import com.yindong.music.ui.screens.SearchScreen
import com.yindong.music.ui.screens.SongRecognitionScreen
import com.yindong.music.viewmodel.MusicViewModel

@Composable
fun AppNavigation(viewModel: MusicViewModel) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Mine.route) {
            CriticalUiProtector.onMineRouteEntered()
        } else {
            CriticalUiProtector.onMineRouteLeft()
        }
    }

    val mainTabs = listOf(
        Screen.Discover.route,
        Screen.Playlist.route,
        Screen.Mine.route,
    )
    val isMainTab = currentRoute in mainTabs
    val isPlayerScreen = currentRoute == Screen.Player.route

    val showMiniPlayer = viewModel.currentSong != null && !isPlayerScreen

    val showQueueSheet = remember { mutableStateOf(false) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        QueueBottomSheet(
            viewModel = viewModel,
            showQueue = showQueueSheet.value,
            onDismiss = { showQueueSheet.value = false },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Discover.route,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                // 平滑过渡动画：淡入+轻微位移，避免默认无动画的硬切卡顿
                enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 8 } },
                exitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 8 } },
                popEnterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { -it / 8 } },
                popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 8 } },
            ) {
                composable(Screen.Discover.route) {
                    DiscoverScreen(
                        viewModel = viewModel,
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        onPlaylistClick = { id ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(id))
                        },
                        onHotChartClick = { navController.navigate(Screen.HotChart.route) },
                        onPlaylistSquareClick = { navController.navigate(Screen.PlaylistSquare.route) },
                        onNewSongsClick = { navController.navigate(Screen.NewSongs.route) },
                        onImportPlaylistClick = { navController.navigate(Screen.ImportPlaylist.route) },
                        onExternalPlaylistClick = { platform, playlistId ->
                            viewModel.loadExternalPlaylistForView(platform, playlistId)
                            navController.navigate(Screen.ExternalPlaylistDetail.route)
                        },
                        onAiAudioEffectClick = { navController.navigate(Screen.AiAudioEffect.route) },
                    )
                }

                composable(Screen.Playlist.route) {
                    LocalMusicScreen(
                        musicViewModel = viewModel,
                    )
                }

                composable(Screen.Mine.route) {
                    MineScreen(
                        viewModel = viewModel,
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        onPlaylistClick = { id ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(id))
                        },
                        onAiAudioEffectClick = { navController.navigate(Screen.AiAudioEffect.route) },
                        onImportPlaylistClick = { navController.navigate(Screen.ImportPlaylist.route) },
                        onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onSongRecognition = { navController.navigate(Screen.SongRecognition.route) },
                        onNavigateToPlugins = {
                            viewModel.requestOpenLxPlugin()
                            navController.navigate(Screen.Mine.route) {
                                popUpTo(Screen.Discover.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }

                composable(Screen.HotChart.route) {
                    HotChartScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Screen.NewSongs.route) {
                    HotChartScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        isNewSongsMode = true,
                    )
                }

                composable(
                    Screen.PlaylistDetail.route,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onPlayerClick = { navController.navigate(Screen.Player.route) },
                    )
                }

                composable(Screen.Player.route) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenAiAudioEffect = { navController.navigate(Screen.AiAudioEffect.route) },
                    )
                }
                composable(Screen.AiAudioEffect.route) {
                    AiAudioEffectScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Screen.SongRecognition.route) {
                    SongRecognitionScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Screen.PlaylistSquare.route) {
                    PlaylistSquareScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onPlaylistClick = { platform, playlistId ->
                            viewModel.loadExternalPlaylistForView(platform, playlistId)
                            navController.navigate(Screen.ExternalPlaylistDetail.route)
                        },
                    )
                }

                composable(Screen.ExternalPlaylistDetail.route) {
                    ExternalPlaylistDetailScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onSaveToMine = {
                            val newId = viewModel.saveExternalPlaylistToMine()
                            if (newId != null) {
                                navController.popBackStack()
                                navController.navigate(Screen.PlaylistDetail.createRoute(newId))
                            }
                        },
                        onPlayerClick = { navController.navigate(Screen.Player.route) },
                    )
                }

                composable(Screen.ImportPlaylist.route) {
                    ImportPlaylistScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToPlaylist = { playlistId ->
                            navController.popBackStack()
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                        },
                    )
                }

                composable(Screen.Downloads.route) {
                    DownloadScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // 顶部居中浮动迷你播放器：显示在系统状态栏下方，取代原全宽底播放栏
            if (showMiniPlayer) {
                MiniPlayer(
                    viewModel = viewModel,
                    onPlayerClick = { navController.navigate(Screen.Player.route) },
                    onQueueClick = { showQueueSheet.value = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 6.dp),
                )
            }

            if (isMainTab) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    BottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.Discover.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
            
            BluetoothWelcomeAnimation(
                isVisible = viewModel.showBluetoothWelcomeAnimation,
                deviceName = viewModel.connectedHeadsetName,
                onAnimationComplete = { viewModel.onBluetoothWelcomeAnimationComplete() }
            )
        }
    }
}
