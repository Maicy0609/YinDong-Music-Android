package com.yindong.music.viewmodel

import android.app.Application
import com.yindong.music.data.model.Banner
import com.yindong.music.data.CrashLogEntry
import com.yindong.music.data.CrashLogManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import android.os.PowerManager
import com.yindong.music.FloatingLyricsService
import com.yindong.music.MediaSessionHolder
import com.yindong.music.MusicPlaybackService
import com.yindong.music.CarLyricHolder
import com.yindong.music.data.BluetoothHeadsetManager
import com.yindong.music.data.LocalStorage
import com.yindong.music.data.UsbExclusiveManager
import com.yindong.music.data.UsbLogEntry
import com.yindong.music.data.UsbDeviceState
import com.yindong.music.ui.theme.ThemeManager
import com.yindong.music.data.MockData
import com.yindong.music.data.RemoteConfig
import com.yindong.music.data.StatsReporter
import com.yindong.music.data.api.MusicApiConfig
import com.yindong.music.data.api.MusicApiService
import com.yindong.music.data.api.RecommendPlaylist
import com.yindong.music.data.lx.LuoxuePluginManager
import com.yindong.music.data.lx.LxPluginManager
import com.yindong.music.data.lx.LxPluginEngine
import com.yindong.music.data.lx.PluginEntry
import com.yindong.music.data.lx.PluginFormat
import com.yindong.music.data.lx.PluginInfo
import com.yindong.music.data.lx.LuoxueRuntimeOptions
import com.yindong.music.data.lx.QuickJSWrapper
import com.yindong.music.security.MusicPlaybackGate
import com.yindong.music.security.SecurityGuard
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import com.yindong.music.data.lx.LocalAudioProxy
import com.yindong.music.data.lx.BuiltinPluginManager
import com.yindong.music.data.lxsdk.LxSdkHotSearch
import com.yindong.music.data.lxsdk.LxSdkLeaderboard
import com.yindong.music.data.lxsdk.LxSdkSongList
import com.yindong.music.data.lxsdk.LxSdkSearchManager
import com.yindong.music.data.lxsdk.LxSdkTipSearch
import okhttp3.Request
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.yindong.music.MusicPlaybackServiceHelper
import com.yindong.music.data.model.LyricLine
import com.yindong.music.data.model.Playlist
import com.yindong.music.data.model.Song
import com.lyrics.api.LyricsApi
import com.lyrics.api.LyricsData
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(UnstableApi::class)
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MusicVM"
        /** Special source value meaning "search all sources and merge results". */
        const val LX_SOURCE_ALL = "__all__"
        /** 内置 lxmusic 插件 ID（仅用于解析播放，无搜索能力，不应显示在搜索平台列表中） */
        const val BUILTIN_LXMUSIC_ID = "builtin_lxmusic"
    }

    // ── HTTP DataSource (supports per-play custom headers for LX plugins) ──
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)

    /** 包装 HTTP + 本地文件 DataSource，支持 file:/// 和 content:// URI */
    private val dataSourceFactory = DefaultDataSource.Factory(getApplication(), httpDataSourceFactory)

    private val audioProxy = LocalAudioProxy(
        run {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            })
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    )

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            250,      // bufferForPlaybackMs - ExoPlayer最小值，收到数据立即播放
            500       // bufferForPlaybackAfterRebufferMs - 重新缓冲0.5秒恢复
        )
        .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
        .build()

    private val musicAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    // ── 实时音频振幅处理器（必须在player之前初始化）──
    private val amplitudeProcessor = com.yindong.music.audio.AmplitudeAudioProcessor()

    private val player: ExoPlayer = ExoPlayer.Builder(
        application,
        com.yindong.music.audio.AmplitudeRenderersFactory(application, amplitudeProcessor)
    )
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setLoadControl(loadControl)
        .setWakeMode(PowerManager.PARTIAL_WAKE_LOCK)
        .setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ false)
        .build()

    private var bassBoostEffect: BassBoost? = null
    private var virtualizerEffect: Virtualizer? = null
    private var equalizerEffect: Equalizer? = null
    private var reverbEffect: EnvironmentalReverb? = null
    private var loudnessEnhancerEffect: LoudnessEnhancer? = null
    // private var dynamicsEffect: DynamicsProcessing? = null

    // ── MediaSession (通知栏/灵动岛/锁屏媒体控制) ──
    private val sessionPlayer = object : ForwardingPlayer(player) {
        override fun seekToNext() { this@MusicViewModel.playNext() }
        override fun seekToPrevious() { this@MusicViewModel.playPrevious() }
        override fun seekToNextMediaItem() { this@MusicViewModel.playNext() }
        override fun seekToPreviousMediaItem() { this@MusicViewModel.playPrevious() }
        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true
        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }
    }

    private val mediaSession: MediaSession? = try {
        MediaSession.Builder(application, sessionPlayer).build().also {
            MediaSessionHolder.session = it
        }
    } catch (e: Exception) {
        Log.e(TAG, "MediaSession创建失败", e)
        null
    }

    private var serviceStarted = false
    private var playerReleased = false
    private var playRequestToken = 0L
    private var playJob: Job? = null
    private var lyricsRequestToken = 0L
    private var intendedPlatformId: String? = null
    private var lastRetryPlatformId: String? = null

    // ── 预取URL缓存：避免切歌时重复请求 ──
    private data class PrefetchEntry(
        val url: String,
        val headers: Map<String, String>,
        val timestamp: Long,
    )
    private val prefetchUrlCache = mutableMapOf<String, PrefetchEntry>()
    private val PREFETCH_CACHE_TTL = 5 * 60 * 1000L // 5分钟过期

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasAudioFocus = false
    private var isDucking = false
    /** 记录因音频焦点丢失而被暂停，焦点恢复时应自动继续播放 */
    private var pausedByFocusLoss = false
    private val audioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
        } else {
            @Suppress("DEPRECATION")
            null
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "requestAudioFocus: granted=$hasAudioFocus")
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        hasAudioFocus = false
        Log.d(TAG, "abandonAudioFocus")
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "handleAudioFocusChange: focusChange=$focusChange, isDucking=$isDucking, pausedByFocus=$pausedByFocusLoss")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                isDucking = false
                if (player.isPlaying) {
                    pausedByFocusLoss = true
                    player.pause()
                    Log.d(TAG, "audioFocus: AUDIOFOCUS_LOSS, paused")
                }
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (player.isPlaying) {
                    pausedByFocusLoss = true
                    player.pause()
                    Log.d(TAG, "audioFocus: AUDIOFOCUS_LOSS_TRANSIENT, paused")
                } else if (!isDucking) {
                    isDucking = true
                    player.volume = 0.2f
                    Log.d(TAG, "audioFocus: AUDIOFOCUS_LOSS_TRANSIENT, ducking")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!isDucking) {
                    isDucking = true
                    player.volume = 0.2f
                    Log.d(TAG, "audioFocus: AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, ducking")
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (isDucking) {
                    isDucking = false
                    player.volume = 1.0f
                    Log.d(TAG, "audioFocus: AUDIOFOCUS_GAIN, unducking")
                }
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    if (!player.isPlaying && player.playbackState == Player.STATE_READY) {
                        player.play()
                        Log.d(TAG, "audioFocus: AUDIOFOCUS_GAIN, resuming after focus loss")
                    }
                }
            }
        }
    }

    // ── 播放状态 ──
    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var isSeeking by mutableStateOf(false)
    var totalDuration by mutableLongStateOf(0L)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var isLoadingLyrics by mutableStateOf(false)
        private set
    var playError by mutableStateOf<String?>(null)
        private set

    // ── 音质 (持久化) ──
    var selectedQuality by mutableStateOf(MusicApiConfig.Quality.STANDARD)
        private set

    val lxQualityKey: String
        get() = when (selectedQuality) {
            MusicApiConfig.Quality.STANDARD -> "128k"
            MusicApiConfig.Quality.EXHIGH -> "320k"
            MusicApiConfig.Quality.LOSSLESS -> "flac"
            MusicApiConfig.Quality.HIRES -> "flac24bit"
            MusicApiConfig.Quality.JYMASTER -> "master"
            MusicApiConfig.Quality.SKY -> "atmos"
            MusicApiConfig.Quality.JYEFFECT -> "atmos_plus"
        }

    val serverQualityKey: String
        get() = selectedQuality.key

    val isDarkMode: Boolean
        get() = ThemeManager.isDark

    // ── 开发者模式 (持久化) ──
    var isDevMode by mutableStateOf(false)
        private set

    // ── 播放模式 (持久化) ──
    enum class PlayMode { LOOP, SINGLE, SHUFFLE }
    var playMode by mutableStateOf(PlayMode.LOOP)
        private set

    // ── 播放器样式 (持久化) ──
    enum class PlayerStyle(val displayName: String, val description: String) {
        MODERN("标准模式", "大封面+简洁控件"),
        // 模糊背景模式：全屏高斯模糊封面背景+居中清晰封面
        BLUR_BG("模糊背景", "高斯模糊封面背景+居中清晰封面"),
        // 沉浸式封面模式：全屏封面+渐变遮罩+歌词+底部控件
        IMMERSIVE_COVER("沉浸式封面", "全屏封面+渐变遮罩+歌词+底部控件"),
        // 独立样式：汽水风（移植自 qduan/眸音推荐流）
        QDUAN("汽水风", "眸音推荐流卡片样式，居中封面+卡拉OK歌词+贴底进度条"),
    }
    var playerStyle by mutableStateOf(PlayerStyle.MODERN)
        private set

    // ── 本地音乐网络补全开关：歌曲没有封面/歌词时从网络获取 ──
    var onlineFallbackEnabled by mutableStateOf(
        getApplication<Application>().getSharedPreferences("cloud_music_data", android.content.Context.MODE_PRIVATE)
            .getBoolean("online_fallback_enabled", true)
    )

    fun toggleOnlineFallback() {
        onlineFallbackEnabled = !onlineFallbackEnabled
        getApplication<Application>().getSharedPreferences("cloud_music_data", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("online_fallback_enabled", onlineFallbackEnabled).apply()
    }

    /** 切换到 QDUAN 前记录的原样式，供切回时还原（双向切换）。 */
    private var previousPlayerStyle: PlayerStyle? = null

    fun changePlayerStyle(style: PlayerStyle) {
        playerStyle = style
        LocalStorage.savePlayerStyle(style.name)
    }

    /** 循环切换到下一个播放器样式 */
    fun togglePlayerStyle() {
        val styles = PlayerStyle.entries
        val nextIndex = (styles.indexOf(playerStyle) + 1) % styles.size
        playerStyle = styles[nextIndex]
        LocalStorage.savePlayerStyle(playerStyle.name)
    }

    /**
     * 在「原样式」与「汽水风(QDUAN)」之间双向切换：
     *  - 当前非 QDUAN → 记录当前样式，切到 QDUAN
     *  - 当前为 QDUAN → 切回先前记录的原样式（无记录则回 MODERN）
     * 仅切换 UI，不打断播放状态/进度/音量。
     */
    fun toggleQduanStyle() {
        if (playerStyle != PlayerStyle.QDUAN) {
            previousPlayerStyle = playerStyle
            changePlayerStyle(PlayerStyle.QDUAN)
        } else {
            val restore = previousPlayerStyle ?: PlayerStyle.MODERN
            previousPlayerStyle = null
            changePlayerStyle(restore)
        }
    }

    // ── 歌词 ──
    private val lyricsApi = LyricsApi()
    private var cachedLyricsData: LyricsData? = null
    var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
        private set
    var currentLyricIndex by mutableIntStateOf(0)
        private set
    var currentPlaybackTimeMs by mutableStateOf(0L)
        private set
    var floatingLyricsEnabled by mutableStateOf(LocalStorage.loadFloatingLyricEnabled())
        private set
    var floatingLyricsText by mutableStateOf("")
        private set
    var floatingLyricsColor by mutableStateOf(android.graphics.Color.WHITE)
        private set
    var highlightLyricColor by mutableStateOf(android.graphics.Color.GREEN)
        private set
    var normalLyricColor by mutableStateOf(android.graphics.Color.WHITE)
        private set
    var playerLyricSize by mutableIntStateOf(18)
        private set
    var floatingLyricSize by mutableIntStateOf(16)
        private set

    // ── 车载蓝牙歌词 ──
    /** 车载蓝牙歌词总开关（手动控制；不随蓝牙连接/断开自动切换） */
    var carBtLyricsEnabled by mutableStateOf(LocalStorage.loadCarBtLyricsEnabled())
        private set
    /** 歌词同步偏移(ms)：正值延后，负值提前，对横屏与悬浮窗同时生效 */
    var lyricSyncOffsetMs by mutableIntStateOf(LocalStorage.loadLyricSyncOffset())
        private set
    /** 应用是否处于前台（ProcessLifecycleOwner 驱动） */
    var isAppForegrounded by mutableStateOf(true)
        private set
    /** 横屏车载歌词字体大小（响应式，供设置面板实时调整） */
    var carLyricFontSize by mutableIntStateOf(LocalStorage.loadCarLyricFontSize())
        private set
    /** 横屏车载歌词背景透明度（响应式） */
    var carLyricBgOpacity by mutableIntStateOf(LocalStorage.loadCarLyricBgOpacity())
        private set

    // ── 播放列表 ──
    var playlist by mutableStateOf<List<Song>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set

    // ── 歌单 ──
    var myPlaylists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var favoriteSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var recentSongs by mutableStateOf<List<Song>>(emptyList())
        private set

    // ── 发现页 ──
    var banners by mutableStateOf<List<Banner>>(emptyList())
        private set
    var recommendPlaylists by mutableStateOf<List<RecommendPlaylist>>(emptyList())
        private set

    // ── 搜索 ──
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var hotSearches by mutableStateOf<List<String>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<Song>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var hasSearched by mutableStateOf(false)
        private set
    var hasMoreResults by mutableStateOf(true)
        private set
    private var currentSearchPage = 1
    private val searchPageSize = 20

    // ── 排行榜 ──
    var hotChart by mutableStateOf<List<Song>>(emptyList())
        private set
    var risingChart by mutableStateOf<List<Song>>(emptyList())
        private set
    var newChart by mutableStateOf<List<Song>>(emptyList())
        private set
    var originalChart by mutableStateOf<List<Song>>(emptyList())
        private set

    // ── 歌单广场 ──
    var playlistSquare by mutableStateOf<List<Playlist>>(emptyList())
        private set

    // ── 导入外部歌单 ──
    sealed class ImportState {
        object Idle : ImportState()
        object Loading : ImportState()
        data class Success(val playlistId: Long, val playlistName: String = "", val songCount: Int = 0) : ImportState()
        data class Error(val message: String) : ImportState()
    }
    var importState by mutableStateOf<ImportState>(ImportState.Idle)
        private set
    var isRecommendLoading by mutableStateOf(false)
        private set
    var recommendLoadError by mutableStateOf("")
        private set
    private var recommendLoadToken = 0L

    // ── 热歌榜（UI 用） ──
    var hotChartSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var isHotChartLoading by mutableStateOf(false)
        private set

    // ── 新歌速递（UI 用） ──
    var newSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var isNewSongsLoading by mutableStateOf(false)
        private set

    // ── 听歌识曲 ──
    // 本地数据类，替代已删除的 NeteaseApi 类型
    data class RecognizedArtist(val name: String = "")
    data class RecognizedAlbum(val picUrl: String = "", val name: String = "")
    data class RecognizedSongData(
        val id: Long = 0,
        val name: String = "",
        val artists: List<RecognizedArtist> = emptyList(),
        val album: RecognizedAlbum? = null,
    )
    data class RecognizedSong(val song: RecognizedSongData = RecognizedSongData(), val score: Float = 0f)

    sealed class RecognizeState {
        data object Idle : RecognizeState()
        data object Recording : RecognizeState()
        data object GeneratingFP : RecognizeState()
        data object Recognizing : RecognizeState()
        data class Success(val songs: List<RecognizedSong>) : RecognizeState()
        data class Error(val message: String) : RecognizeState()
    }
    var recognizeState by mutableStateOf<RecognizeState>(RecognizeState.Idle)
        private set
    var recognizedSongs by mutableStateOf<List<RecognizedSong>>(emptyList())
        private set

    // ── 在线搜索结果（UI 可写） ──
    var onlineResults by mutableStateOf<List<Song>>(emptyList())
    var searchQuery by mutableStateOf("")
        private set
    var isParsing by mutableStateOf(false)
        private set
    var parseError by mutableStateOf<String?>(null)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    var albumSearchResults by mutableStateOf<List<com.yindong.music.data.model.AlbumSearchResult>>(emptyList())
        private set
    var artistSearchResults by mutableStateOf<List<com.yindong.music.data.model.ArtistSearchResult>>(emptyList())
        private set
    var sheetSearchResults by mutableStateOf<List<com.yindong.music.data.model.MusicSheetSearchResult>>(emptyList())
        private set
    var isSearchingAlbum by mutableStateOf(false)
        private set
    var isSearchingArtist by mutableStateOf(false)
        private set
    var isSearchingSheet by mutableStateOf(false)
        private set

    var detailSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var isLoadingDetail by mutableStateOf(false)
        private set
    var detailTitle by mutableStateOf("")
        private set
    var detailCoverUrl by mutableStateOf("")
        private set
    var detailSubtitle by mutableStateOf("")
        private set

    // ── 搜索建议 ──
    data class SearchSuggestion(val keyword: String = "")
    var searchSuggestions by mutableStateOf<List<SearchSuggestion>>(emptyList())
        private set
    private var searchSuggestionJob: kotlinx.coroutines.Job? = null

    // ── 搜索分页相关（用于在线搜索） ──
    var isLoadingMore by mutableStateOf(false)
        private set
    private var currentSearchOffset = 0
    private val searchLimit = 30
    private var currentSearchPlatform = ""
    private var currentPluginPage = 1
    // "全部"搜索时各平台当前页码
    private var allPlatformPages = mutableMapOf("网易云" to 0, "QQ" to 0, "酷我" to 0, "酷狗" to 0)

    // ── 分类/播客搜索 ──
    var isCategoryLoading by mutableStateOf(false)
        private set
    var categorySongs by mutableStateOf<List<Song>>(emptyList())
        private set

    // ── 均衡器预设 ──
    enum class EqPreset(val displayName: String) {
        FLAT("纯净"),
        BASS("低音"),
        VOCAL("人声"),
        POP("流行"),
        ROCK("摇滚"),
        JAZZ("爵士"),
        CLASSICAL("古典"),
        ELECTRONIC("电音"),
        HIPHOP("说唱"),
        LIVE("现场"),
        NIGHT("夜间"),
        ACG("次元"),
        // ── 以下预设来自 lx-music-desktop-master ──
        DANCE("舞曲"),
        SLOW("慢歌"),
        SUBWOOFER("重低音"),
        SOFT("柔和"),
    }
    var currentPreset by mutableStateOf(EqPreset.FLAT)
        private set

    // ── 混响参数 ──
    var reverbRoomSize by mutableIntStateOf(0)
        private set
    var reverbDamping by mutableIntStateOf(0)
        private set
    var reverbLevel by mutableIntStateOf(0)
        private set
    var loudnessGain by mutableIntStateOf(0)
        private set

    // ── 登录状态 ──
    var isLoggedIn by mutableStateOf(false)
        private set
    var userName by mutableStateOf("")
        private set
    var userId by mutableStateOf("")
        private set
    var userToken by mutableStateOf("")
        private set

    // ── 下载地址 ──
    var downloadPageUrl by mutableStateOf("")
        private set

    // ── API配置 ──
    var apiMode by mutableStateOf("official")
        private set
    var apiHost by mutableStateOf("")
        private set
    var shareBaseUrl by mutableStateOf(LocalStorage.loadShareBaseUrl())
        private set
    var qqMusicApi by mutableStateOf("")
        private set
    var neteaseApi by mutableStateOf("")
        private set
    var kuwoApi by mutableStateOf("")
        private set
    var miguApi by mutableStateOf("")
        private set
    var kugouApi by mutableStateOf("")
        private set
    var douyinApi by mutableStateOf("")
        private set
    var qqCookie by mutableStateOf("")
        private set
    var lxPluginUri by mutableStateOf("")
        private set
    var lxPluginHash by mutableStateOf("")
        private set
    var lxPluginInfo by mutableStateOf(PluginInfo())
        private set
    var lxSelectedSource by mutableStateOf("")
        private set
    var lxSources by mutableStateOf<List<String>>(emptyList())
        private set
    var lxAllowHttp by mutableStateOf(true)
        private set
    var lxTimeoutMs by mutableLongStateOf(15000L)
        private set
    var lxDebugLogs by mutableStateOf<List<String>>(emptyList())
        private set

    // ── 多插件状态 ──
    var lxPlugins by mutableStateOf<List<PluginEntry>>(emptyList())
        private set
    // ── MusicFree 插件状态 ──
    var musicFreePlugins by mutableStateOf<List<com.yindong.music.data.musicfree.MusicFreePluginEntry>>(emptyList())
        private set
    var lxSelectedPluginId by mutableStateOf("")
        private set
    var playbackSourcePriority by mutableStateOf("netease_first")
        private set

    // ── 插件/音源启用控制（lx-music-mobile-master 单一 activeId 模式） ──
    /** key 格式: "pluginId:sourceKey" */
    var disabledSourceKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 判断插件是否为当前激活插件（与 lx-music-mobile-master 的 common.apiSource 机制一致）。
     * 同一时间只有一个插件处于激活状态，由 [lxSelectedPluginId] 唯一标识。
     */
    fun isPluginEnabled(pluginId: String): Boolean =
        lxSelectedPluginId.isNotBlank() && pluginId == lxSelectedPluginId

    fun isSourceEnabled(pluginId: String, source: String): Boolean = "$pluginId:$source" !in disabledSourceKeys

    /** 没有可用的插件：既无选中的 LX 插件，也无已启用的 MusicFree 插件 */
    val hasNoAvailablePlugin: Boolean
        get() {
            val noLx = lxSelectedPluginId.isBlank() || lxPlugins.none { it.id == lxSelectedPluginId }
            val noMf = musicFreePlugins.none { it.enabled && it.mounted }
            return noLx && noMf
        }

    /** 请求打开落雪插件对话框（从搜索页跳转） */
    var pendingOpenLxPlugin by mutableStateOf(false)
        private set

    fun requestOpenLxPlugin() { pendingOpenLxPlugin = true }
    fun consumePendingOpenLxPlugin() { pendingOpenLxPlugin = false }

    /** 当前唯一激活的插件（与 lx-music-mobile-master 的 activeApi 概念一致） */
    val activePlugin: PluginEntry? get() = lxPlugins.firstOrNull { it.id == lxSelectedPluginId }

    /**
     * 切换插件激活状态（lx-music-mobile-master 模式）。
     * - 若当前未激活该插件 → 激活它（同时自动取消其他插件激活），并设置 apiMode/lxSelectedSource
     * - 若当前已激活该插件 → 取消激活（无选中插件），清空 lxSelectedSource
     */
    fun togglePluginEnabled(pluginId: String) {
        if (lxSelectedPluginId == pluginId) {
            // 取消激活
            lxSelectedPluginId = ""
            lxSelectedSource = ""
            LocalStorage.saveLxSelectedSource("")
        } else {
            // 激活新插件
            lxSelectedPluginId = pluginId
            val plugin = lxPlugins.firstOrNull { it.id == pluginId }
            val source = plugin?.sources?.firstOrNull().orEmpty()
            lxSelectedSource = source
            if (source.isNotBlank()) {
                apiMode = "lx_plugin"
                LocalStorage.saveApiMode(apiMode)
            }
            LocalStorage.saveLxSelectedSource(lxSelectedSource)
        }
        LocalStorage.saveLxSelectedPluginId(lxSelectedPluginId)
        refreshActivePluginSupportedQualities()
    }

    /** 显式激活指定插件（用于外部代码需要"切换到某插件"的场景） */
    fun enablePluginExclusively(pluginId: String) {
        if (lxPlugins.none { it.id == pluginId }) return
        lxSelectedPluginId = pluginId
        val plugin = lxPlugins.firstOrNull { it.id == pluginId }
        val source = plugin?.sources?.firstOrNull().orEmpty()
        lxSelectedSource = source
        if (source.isNotBlank()) {
            apiMode = "lx_plugin"
            LocalStorage.saveApiMode(apiMode)
        }
        LocalStorage.saveLxSelectedPluginId(lxSelectedPluginId)
        LocalStorage.saveLxSelectedSource(lxSelectedSource)
        refreshActivePluginSupportedQualities()
    }

    // ── 插件音质检测（按平台分组，含文件大小） ──
    // LX 音质 key → 应用 Quality 枚举 的映射（与 downloadSongWithQuality 的映射保持一致）
    private val LX_QUALITY_TO_APP_QUALITY: Map<String, MusicApiConfig.Quality> = mapOf(
        "128k" to MusicApiConfig.Quality.STANDARD,
        "320k" to MusicApiConfig.Quality.EXHIGH,
        "flac" to MusicApiConfig.Quality.LOSSLESS,
        "flac24bit" to MusicApiConfig.Quality.HIRES,
        "hires" to MusicApiConfig.Quality.HIRES,
        "master" to MusicApiConfig.Quality.JYMASTER,
        "atmos" to MusicApiConfig.Quality.SKY,
        "atmos_plus" to MusicApiConfig.Quality.JYEFFECT,
        "sky" to MusicApiConfig.Quality.SKY,
        "jyeffect" to MusicApiConfig.Quality.JYEFFECT,
    )

    /**
     * 当前生效插件每个平台实际检测到的音质列表（含文件大小信息）。
     * - key: 平台 source id（wy/tx/kw/kg）
     * - value: 该平台检测通过的音质列表（按 [LxPluginEngine.PLATFORM_QUALITY_SPECS] 顺序）
     *
     * UI 通过 [getSupportedQualitiesForSource] 按当前歌曲所属平台查询，仅展示该平台支持的音质。
     */
    var activePluginSupportedQualitiesBySource by mutableStateOf<Map<String, List<LxPluginEngine.DetectedQuality>>>(emptyMap())
        private set

    /** 兼容旧 API：将各平台检测到的音质 key 并集映射为应用 Quality 枚举集合 */
    val activePluginSupportedQualities: Set<MusicApiConfig.Quality>
        get() = activePluginSupportedQualitiesBySource.values
            .flatten()
            .mapNotNull { LX_QUALITY_TO_APP_QUALITY[it.lxKey] }
            .toSet()

    /** 是否正在执行真实音质检测（搜索+试解析+HEAD 取文件大小，用于 UI 提示） */
    var isQualityDetecting by mutableStateOf(false)
        private set

    /** 音质检测任务（支持取消上一次未完成的检测） */
    private var qualityDetectionJob: Job? = null

    /** 音质检测使用的测试关键词（覆盖 4 大平台均有结果） */
    private val QUALITY_DETECT_KEYWORD = "周杰伦"

    /** 参与音质检测的 4 大平台 source id（与搜索平台一致，咪咕不参与） */
    private val QUALITY_DETECT_SOURCES = listOf("wy", "tx", "kw", "kg")

    /**
     * 依据当前生效插件刷新支持音质集合（按平台分组）。
     *
     * 分两步：
     * 1. 立即用静态映射表 [LxPluginEngine.PLATFORM_QUALITY_SPECS] 填充（保证 UI 即时响应）
     *    - 仅展示 [PlatformQualitySpec.lxKey] 非空且属于该平台 lxmusic.js 支持列表的音质
     *    - 文件大小暂为 0（待真实检测填充）
     * 2. 异步执行真实音质检测 [performActualQualityDetection]
     *    （搜索 4 平台前 3 首 → 逐档位试解析 → HEAD 取文件大小）
     *
     * @param runRealDetection 是否在静态映射后执行真实音质检测。
     *        - 用户显式操作（启用/导入/重载插件）时传 true
     *        - App 启动初始化时传 false，避免启动期网络请求
     */
    private fun refreshActivePluginSupportedQualities(runRealDetection: Boolean = true) {
        // 1. 立即用静态映射表填充（保证 UI 即时响应，不阻塞用户操作）
        val initialMap = mutableMapOf<String, List<LxPluginEngine.DetectedQuality>>()
        val plugin = activePlugin
        if (plugin != null && plugin.sources.isNotEmpty()) {
            for (source in QUALITY_DETECT_SOURCES) {
                if (source !in plugin.sources) continue
                val specs = LxPluginEngine.PLATFORM_QUALITY_SPECS[source] ?: continue
                val lxSupported = LxPluginEngine.MUSIC_QUALITY[source] ?: emptyList()
                // 静态阶段：lxKey 非空且属于该平台 lxmusic.js 支持列表的音质先展示，文件大小留空
                val detected = specs
                    .filter { spec -> spec.lxKey != null && spec.lxKey in lxSupported }
                    .map { spec ->
                        LxPluginEngine.DetectedQuality(
                            source = source,
                            displayName = spec.displayName,
                            lxKey = spec.lxKey!!,
                            description = spec.description,
                            fileSizeBytes = 0L,
                            fileSizeText = "",
                        )
                    }
                if (detected.isNotEmpty()) initialMap[source] = detected
            }
        }
        activePluginSupportedQualitiesBySource = initialMap
        Log.d(TAG, "静态音质检测完成: ${initialMap.mapValues { (_, v) -> v.size }}")

        // 2. 异步执行真实音质检测（搜索 → 试解析 URL → HEAD 取文件大小）
        if (runRealDetection) {
            performActualQualityDetection()
        }
    }

    /**
     * 真实音质检测：每次启用插件后自动搜索网易云/QQ/酷我/酷狗，
     * 取每个平台搜索结果前 3 首歌，逐个音质档位尝试解析播放链接，
     * 成功解析到有效 URL 的档位视为"支持"，并通过 HEAD 请求获取文件大小。
     *
     * - 检测期间不阻塞 UI（静态映射表已先填充）
     * - 检测全部失败时保留静态映射表结果，不影响用户使用
     * - 单线程顺序执行，避免 QuickJS 并发问题
     * - 仅测试该音源声明支持的音质，避免引擎自动降级到 128k 导致误判
     * - 检测结果按平台分组写入 [activePluginSupportedQualitiesBySource]
     */
    private fun performActualQualityDetection() {
        val plugin = activePlugin ?: run {
            qualityDetectionJob?.cancel()
            isQualityDetecting = false
            return
        }
        if (plugin.sources.isEmpty()) {
            qualityDetectionJob?.cancel()
            isQualityDetecting = false
            return
        }
        // 取消上一次未完成的检测
        qualityDetectionJob?.cancel()
        qualityDetectionJob = viewModelScope.launch(Dispatchers.IO) {
            isQualityDetecting = true
            try {
                // 仅检测插件声明支持且在 4 大平台范围内的音源
                val sourcesToTest = QUALITY_DETECT_SOURCES.filter { it in plugin.sources }
                if (sourcesToTest.isEmpty()) {
                    Log.w(TAG, "音质检测: 当前插件不含 4 大平台音源，跳过")
                    return@launch
                }

                // Step 1: 搜索每个平台，取前 4 首歌
                val testSongsBySource = mutableMapOf<String, List<Song>>()
                for (source in sourcesToTest) {
                    try {
                        val result = withTimeoutOrNull(10_000L) {
                            LxSdkSearchManager.search(source, QUALITY_DETECT_KEYWORD, 1, lxTimeoutMs)
                        }
                        val songs = result?.songs.orEmpty().take(3)
                        if (songs.isNotEmpty()) {
                            testSongsBySource[source] = songs
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "音质检测搜索失败: source=$source, ${e.message}")
                    }
                }
                if (testSongsBySource.isEmpty()) {
                    Log.w(TAG, "音质检测: 所有平台搜索均无结果，保留静态映射结果")
                    return@launch
                }

                // Step 2: 逐平台逐档位试解析 + HEAD 取文件大小
                // 以静态映射为初始值，逐个验证并补充 fileSize
                val resultMap = activePluginSupportedQualitiesBySource.toMutableMap()
                for ((source, songs) in testSongsBySource) {
                    val specs = LxPluginEngine.PLATFORM_QUALITY_SPECS[source] ?: continue
                    val lxSupported = LxPluginEngine.MUSIC_QUALITY[source] ?: emptyList()
                    val detectedList = mutableListOf<LxPluginEngine.DetectedQuality>()
                    for (spec in specs) {
                        val lxKey = spec.lxKey
                        // 1) 静态不支持（lxKey=null 或不在 lxmusic.js 支持列表）→ 直接跳过，不展示
                        if (lxKey == null || lxKey !in lxSupported) continue
                        // 2) 真实试解析：从搜索结果中按顺序尝试，命中一首即视为支持
                        var resolvedUrl: String? = null
                        var resolvedHeaders: Map<String, String> = emptyMap()
                        for (song in songs) {
                            try {
                                val urlResult = withTimeoutOrNull(8_000L) {
                                    lxPluginManager.musicUrl(plugin.id, source, song, 8_000L, lxKey)
                                }
                                if (urlResult != null &&
                                    urlResult.url.isNotBlank() &&
                                    urlResult.url.startsWith("http")
                                ) {
                                    resolvedUrl = urlResult.url
                                    resolvedHeaders = urlResult.headers
                                    Log.d(TAG, "音质检测命中: $lxKey (source=$source, song=${song.title})")
                                    break
                                }
                            } catch (_: Exception) {
                                // 继续尝试下一首
                            }
                        }
                        // 3) 解析失败 → 不展示该音质
                        if (resolvedUrl == null) continue
                        // 4) HEAD 请求获取文件大小
                        val sizeBytes = withTimeoutOrNull(6_000L) {
                            LxPluginEngine.fetchFileSize(resolvedUrl, resolvedHeaders)
                        } ?: 0L
                        val sizeText = LxPluginEngine.formatFileSize(sizeBytes)
                        detectedList.add(
                            LxPluginEngine.DetectedQuality(
                                source = source,
                                displayName = spec.displayName,
                                lxKey = lxKey,
                                description = spec.description,
                                fileSizeBytes = sizeBytes,
                                fileSizeText = sizeText,
                            )
                        )
                    }
                    if (detectedList.isNotEmpty()) {
                        resultMap[source] = detectedList
                        Log.d(TAG, "音质检测完成: source=$source, 共 ${detectedList.size} 档支持")
                    } else {
                        // 该平台所有档位真实检测均失败 → 从结果中移除该平台
                        resultMap.remove(source)
                        Log.w(TAG, "音质检测: source=$source 所有档位解析失败，移除")
                    }
                }

                // Step 3: 更新状态（仅当至少一个平台有结果时）
                if (resultMap.isNotEmpty()) {
                    activePluginSupportedQualitiesBySource = resultMap
                    Log.d(TAG, "音质检测全部完成: ${resultMap.mapValues { (_, v) -> v.size }}")
                } else {
                    Log.w(TAG, "音质检测全部失败，保留静态映射结果")
                }
            } catch (e: Exception) {
                Log.w(TAG, "音质检测异常: ${e.message}")
            } finally {
                isQualityDetecting = false
            }
        }
    }

    /**
     * 获取指定平台当前检测到的音质列表（含文件大小）。
     * - 用于播放页/下载页根据当前歌曲所属平台展示音质选项
     * - 若该平台未检测到，返回空列表（UI 应提示用户先启用插件）
     */
    fun getSupportedQualitiesForSource(source: String): List<LxPluginEngine.DetectedQuality> {
        return activePluginSupportedQualitiesBySource[source] ?: emptyList()
    }

    /** 判断指定音质是否被当前生效插件支持（兼容旧 API，用于过滤全局音质集合） */
    fun isQualitySupportedByActivePlugin(quality: MusicApiConfig.Quality): Boolean {
        val supported = activePluginSupportedQualities
        // 若无生效插件或检测为空，则不过滤（避免用户无法操作）
        return supported.isEmpty() || quality in supported
    }

    fun toggleSourceEnabled(pluginId: String, source: String) {
        val key = "$pluginId:$source"
        disabledSourceKeys = if (key in disabledSourceKeys) {
            disabledSourceKeys - key
        } else {
            disabledSourceKeys + key
        }
        LocalStorage.saveDisabledSources(disabledSourceKeys)
    }

    fun clearLxLogs() { lxDebugLogs = emptyList() }
    fun addLxLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        lxDebugLogs = (lxDebugLogs + "[$ts] $msg").takeLast(200)
    }

    private val lxPluginManager by lazy {
        LxPluginManager(
            contentResolver = getApplication<Application>().contentResolver,
            quickJsFactory = { QuickJSWrapper() },
            logCallback = { msg -> addLxLog(msg) },
        )
    }

    /** MusicFree 插件管理器（独立于 LX 插件系统，互不影响） */
    private val musicFreePluginManager by lazy {
        com.yindong.music.data.musicfree.MusicFreePluginManager(
            logCallback = { msg -> addLxLog(msg) },
        )
    }

    // ── 崩溃日志 ──
    var crashLogs by mutableStateOf<List<CrashLogEntry>>(emptyList())
        private set

    // ── 均衡器 ──
    var equalizerEnabled by mutableStateOf(true)
        private set
    var equalizerBands by mutableStateOf<List<Pair<Int, Int>>>(emptyList())
        private set
    var bassBoostEnabled by mutableStateOf(false)
        private set
    var bassBoostStrength by mutableIntStateOf(0)
        private set
    var virtualizerEnabled by mutableStateOf(false)
        private set
    var virtualizerStrength by mutableIntStateOf(0)
        private set

    // ── 定时停止 ──
    var sleepTimerMinutes by mutableIntStateOf(0)
        private set
    var sleepTimerEnabled by mutableStateOf(false)
        private set
    private var sleepTimerJob: Job? = null

    // ── 下载状态 (必须在 init 之前声明, 否则 loadDownloadedSongs() 会 NPE) ──
    var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set
    var downloadedSongs by mutableStateOf<List<DownloadedSong>>(emptyList())
        private set

    // ── 蓝牙耳机状态 ──
    var isHeadsetConnected by mutableStateOf(false)
        private set
    var connectedHeadsetName by mutableStateOf<String?>(null)
        private set
    var headsetType by mutableStateOf(BluetoothHeadsetManager.HeadsetType.UNKNOWN)
        private set
    var showHeadsetBanner by mutableStateOf(false)
        private set
    private var headsetBannerDismissed by mutableStateOf(false)

    var showBluetoothWelcomeAnimation by mutableStateOf(false)
        private set
    private var bluetoothWelcomeAnimationShown by mutableStateOf(false)

    // ── 实时音频振幅（用于封面跳动等视觉效果）──
    var audioAmplitude by mutableFloatStateOf(0f)
        private set

    private val headsetManager: BluetoothHeadsetManager by lazy {
        BluetoothHeadsetManager(getApplication())
    }

    init {
        // 设置振幅处理器回调（音频处理线程调用，切换到主线程更新UI）
        amplitudeProcessor.onAmplitudeUpdate = { amplitude ->
            // 在主线程更新，确保Compose能观察到状态变化
            viewModelScope.launch(Dispatchers.Main) {
                audioAmplitude = amplitude
            }
        }
        
        // 监听播放状态变化，同步给振幅处理器（主线程）
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                amplitudeProcessor.isPlayerPlaying = isPlaying
                if (!isPlaying) {
                    audioAmplitude = 0f
                }
            }
        })

        // 1. 立即预热TCP连接 (异步，不阻塞)
        MusicApiService.warmup()
        // 2. 本地数据加载 (无网络, 合并到单个协程减少开销)
        viewModelScope.launch {
            loadSettingsSync()
            loadUserAuthSync()
            loadLocalDataSync()
        }
        loadApiConfig() // 单独协程: 可能触发插件加载
        // 同步初始化内置插件（避免异步加载期间 UI 显示"未导入"）
        // 这样清除数据后首次启动，内置插件立即可见，不会"丢失"
        runCatching {
            ensureBuiltinLxmusicPlugin()
            ensurePluginNames()
            // App 启动时仅做静态映射检测，不触发网络请求（真实检测在用户显式启用插件时执行）
            refreshActivePluginSupportedQualities(runRealDetection = false)
        }
        // 异步加载用户导入的 MusicFree 插件（避免阻塞 init）
        loadBuiltinPlugin()
        initPlayer()
        initEqualizer()
        // 3. 网络数据: 批量接口 + 推荐歌单并行加载
        loadInitData()
        loadRecommendPlaylists()
        // 4. 内置插件已删除（改用 LxSdk 原生 5 平台搜索）
        // 5. 初始化蓝牙耳机状态监测
        initHeadsetMonitoring()
        // 6. 注册应用前后台生命周期监听（车载蓝牙歌词：前台横屏 / 后台悬浮窗切换）
        registerAppLifecycleObserver()
        // 7. 同步车载歌词开关到共享状态（app 重启后恢复通知栏歌词显示）
        CarLyricHolder.enabled = carBtLyricsEnabled
    }

    /**
     * 初始化蓝牙耳机状态监测
     */
    private fun initHeadsetMonitoring() {
        headsetManager.startMonitoring()
        // 收集耳机状态变化
        viewModelScope.launch {
            headsetManager.headsetState.collect { state ->
                val wasConnected = isHeadsetConnected
                isHeadsetConnected = state.isConnected
                connectedHeadsetName = state.deviceName
                headsetType = state.type

                // 当耳机新连接时显示提示（且用户未手动关闭）
                if (state.isConnected && !wasConnected && !headsetBannerDismissed) {
                    showHeadsetBanner = true
                }
                
                // 应用启动时，如果蓝牙耳机已连接且未显示过欢迎动画，则显示
                if (state.isConnected && 
                    state.type == BluetoothHeadsetManager.HeadsetType.BLUETOOTH && 
                    !bluetoothWelcomeAnimationShown) {
                    showBluetoothWelcomeAnimation = true
                    bluetoothWelcomeAnimationShown = true
                }
            }
        }
    }

    /** 应用前后台生命周期观察者（持有引用以便 onCleared 时移除） */
    private var appLifecycleObserver: DefaultLifecycleObserver? = null

    /**
     * 注册应用前后台生命周期监听（ProcessLifecycleOwner）。
     * 车载蓝牙歌词依赖此回调：前台 → 横屏大字歌词接管；后台 → 悬浮窗接管。
     */
    private fun registerAppLifecycleObserver() {
        if (appLifecycleObserver != null) return
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppForegrounded = true
                reevaluateOverlayState()
            }
            override fun onStop(owner: LifecycleOwner) {
                isAppForegrounded = false
                reevaluateOverlayState()
            }
        }
        appLifecycleObserver = observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    /**
     * 关闭耳机连接提示横幅
     */
    fun dismissHeadsetBanner() {
        showHeadsetBanner = false
        headsetBannerDismissed = true
    }
    
    /**
     * 欢迎动画完成回调
     */
    fun onBluetoothWelcomeAnimationComplete() {
        showBluetoothWelcomeAnimation = false
    }

    /**
     * 重新显示耳机连接提示（用于测试或用户主动触发）
     */
    fun showHeadsetBannerAgain() {
        if (isHeadsetConnected) {
            showHeadsetBanner = true
            headsetBannerDismissed = false
        }
    }

    private fun initPlayer() {
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // 播放器分配到真实 audioSessionId 后重新初始化音效，
                // 否则音效绑定到 session 0（全局混音），不会作用于播放器输出。
                reinitEqualizer(audioSessionId)
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    totalDuration = player.duration.coerceAtLeast(0L)
                    playError = null
                }
                if (state == Player.STATE_ENDED) {
                    autoPlayNext()
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                updateMediaSessionPlaybackState()
            }
            override fun onPositionDiscontinuity(reason: Int) {
                val mediaId = player.currentMediaItem?.mediaId
                // 只在 mediaId 与当前意图播放的歌曲匹配时才更新，防止旧歌曲事件覆盖新歌曲
                if (!mediaId.isNullOrEmpty() && mediaId == intendedPlatformId) {
                    val idx = playlist.indexOfFirst { it.platformId == mediaId }
                    if (idx >= 0) {
                        currentIndex = idx
                        currentSong = playlist[idx]
                        
                    }
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "播放错误: ${error.errorCodeName}", error)
                // IO 类错误自动重试一次 (清除缓存重新获取URL，解决QQ音乐等CDN链接过期问题)
                val song = currentSong
                if (song != null && song.platformId != lastRetryPlatformId && isRetryableError(error)) {
                    lastRetryPlatformId = song.platformId
                    Log.d(TAG, "播放失败, 自动重试: ${song.title}")
                    MusicApiService.invalidatePlayUrlCache(song.platform, song.platformId)
                    playSong(song)
                    return
                }
                playError = "播放失败: ${getErrorMessage(error.errorCode)}"
                isBuffering = false

            }
        })

        viewModelScope.launch {
            while (true) {
                delay(50)
                // 不播放时跳过进度更新，但歌词仍需更新（暂停时也可能切歌）
                if (player.duration > 0 && player.isPlaying && !isSeeking) {
                    progress = player.currentPosition.toFloat() / player.duration.toFloat()
                }
                updateLyricIndex()
                updateFloatingLyrics()
            }
        }
    }

    /** 判断是否为可重试的播放错误 (网络/IO相关) */
    private fun isRetryableError(error: androidx.media3.common.PlaybackException): Boolean {
        return error.errorCode in setOf(
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        )
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败"
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接超时"
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "音频链接已失效"
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "无效的音频格式"
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "解码器初始化失败"
            else -> "未知错误 (代码: $errorCode)"
        }
    }

    private fun updateMediaSessionPlaybackState() {
        // MediaSession 通过 Player 自动同步元数据
    }

    /** 同步加载设置 (在已有协程内调用, 避免额外协程开销) */
    private fun loadSettingsSync() {
        selectedQuality = MusicApiConfig.Quality.fromStoredValue(LocalStorage.loadQuality())
        isDevMode = LocalStorage.loadDevMode()
        playMode = try { PlayMode.valueOf(LocalStorage.loadPlayMode()) } catch (_: Exception) { PlayMode.LOOP }
        playerStyle = try { PlayerStyle.valueOf(LocalStorage.loadPlayerStyle()) } catch (_: Exception) { PlayerStyle.MODERN }
        currentPreset = try { EqPreset.valueOf(LocalStorage.loadEqPreset()) } catch (_: Exception) { EqPreset.FLAT }
        floatingLyricsColor = LocalStorage.loadFloatingLyricColor()
        highlightLyricColor = LocalStorage.loadLyricCurrentColor()
        normalLyricColor = LocalStorage.loadLyricNormalColor()
        playerLyricSize = LocalStorage.loadLyricFontSize()
        floatingLyricSize = LocalStorage.loadFloatingLyricSize()
        // 应用重启后按当前开关状态评估悬浮歌词（通用悬浮歌词 + 车载蓝牙歌词）
        reevaluateOverlayState()
    }

    /** 同步加载用户认证 */
    private fun loadUserAuthSync() {
        userToken = LocalStorage.loadUserToken()
        userName = LocalStorage.loadUserName()
        userId = LocalStorage.loadUserId().toString()
        isLoggedIn = userToken.isNotEmpty()
    }

    /** 同步加载所有本地数据 (歌单/收藏/历史等) */
    private fun migrateNetease2Platform() {
        var changed = false
        val migrated = myPlaylists.map { playlist ->
            val migratedSongs = playlist.songs.map { song ->
                if (song.platform == "网易云2") {
                    changed = true
                    song.copy(platform = "网易云")
                } else song
            }
            if (migratedSongs !== playlist.songs) playlist.copy(songs = migratedSongs) else playlist
        }
        if (changed) {
            myPlaylists = migrated
            LocalStorage.savePlaylists(myPlaylists)
            Log.d(TAG, "migrateNetease2Platform: migrated saved playlists")
        }
    }

    private fun loadLocalDataSync() {
        myPlaylists = LocalStorage.loadPlaylists()
        migrateNetease2Platform()
        val rawFavs = LocalStorage.loadFavorites()
        val rawRecent = LocalStorage.loadPlayHistory()
        val migratedFavs = rawFavs.map { if (it.platform == "网易云2") it.copy(platform = "网易云") else it }
        val migratedRecent = rawRecent.map { if (it.platform == "网易云2") it.copy(platform = "网易云") else it }
        favoriteSongs = migratedFavs
        recentSongs = migratedRecent
        if (rawFavs.any { it.platform == "网易云2" }) LocalStorage.saveFavorites(migratedFavs)
        if (rawRecent.any { it.platform == "网易云2" }) LocalStorage.savePlayHistory(migratedRecent)
        searchHistory = LocalStorage.loadSearchHistory()
        crashLogs = CrashLogManager.getLogFiles()
        banners = MockData.banners
        // 热门搜索改为异步加载 lx-music-mobile 4 平台热搜（替换硬编码）
        hotSearches = listOf("周杰伦", "林俊杰", "薛之谦", "陈奕迅", "邓紫棋") // 兜底
        loadHotSearches()
        downloadPageUrl = LocalStorage.loadServerUrl()
        loadDownloadedSongs()
    }

    private fun loadApiConfig() {
        viewModelScope.launch {
            apiMode = LocalStorage.loadApiMode()
            apiHost = LocalStorage.loadApiHost()
            qqMusicApi = LocalStorage.loadQQMusicApiKey()
            neteaseApi = LocalStorage.loadNeteaseApiKey()
            kuwoApi = LocalStorage.loadKuwoApiKey()
            miguApi = LocalStorage.loadMiguApiKey()
            kugouApi = LocalStorage.loadKugouApiKey()
            douyinApi = LocalStorage.loadDouyinApiKey()
            qqCookie = LocalStorage.loadQQCookie()
            lxPluginUri = LocalStorage.loadLxPluginUri()
            lxPluginHash = LocalStorage.loadLxPluginHash()
            lxPluginInfo = LocalStorage.loadLxPluginInfo()
            lxSelectedSource = LocalStorage.loadLxSelectedSource()
            lxSelectedPluginId = LocalStorage.loadLxSelectedPluginId()
            // 同步修复：如果已有选中的插件但 apiMode 不是 lx_plugin，说明是旧版本数据，需修正
            if (lxSelectedPluginId.isNotBlank() && apiMode != "lx_plugin") {
                apiMode = "lx_plugin"
                LocalStorage.saveApiMode(apiMode)
            }
            playbackSourcePriority = LocalStorage.loadPlaybackSourcePriority()
            lxAllowHttp = LocalStorage.loadLxAllowHttp()
            lxTimeoutMs = LocalStorage.loadLxTimeoutMs()
            disabledSourceKeys = LocalStorage.loadDisabledSources()
            if (apiMode == "lx_plugin") {
                ensurePluginPolicyLoaded()
                val savedPlugins = LocalStorage.loadLxPlugins()
                if (savedPlugins.isNotEmpty()) {
                    reloadAllPlugins(savedPlugins)
                } else if (lxPluginUri.isNotBlank()) {
                    val result = reloadLxPlugin()
                    if (result.isFailure) {
                        Log.e(TAG, "reloadLxPlugin failed on startup", result.exceptionOrNull())
                    } else {
                        Log.d(TAG, "reloadLxPlugin ok: sources=${lxSources}, selectedSource=$lxSelectedSource")
                    }
                }
            }
            // 无论何种 API 模式，都确保内置 lxmusic 插件可见
            ensureBuiltinLxmusicPlugin()
        }
    }


    /**
     * 静默加载内置远程 JS 插件
     * 从服务器拉取最新 JS 音源脚本，用户完全无感知。
     * 管理员替换服务器上的 JS 文件即可热更新。
     *
     * 注意：内置落雪音源（lxmusic.js）已按用户要求移除，不再自动加载。
     * 落雪插件引擎（LxPluginEngine）基础设施保留，可用于用户导入的 LX 插件。
     * 音乐播放改为依赖用户导入的 MusicFree 插件或 LX 插件。
     */
    private fun loadBuiltinPlugin() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                // ── 落雪插件引擎日志回调（保留引擎基础设施，但不自动加载 lxmusic.js） ──
                LxPluginEngine.setLogCallback { msg -> addLxLog(msg) }

                // ── 加载用户导入的 LX 插件（不加载内置 lxmusic.js） ──
                lxPlugins = lxPluginManager.getAllPluginEntries()
                ensurePluginNames()
                rebuildSources()

                // ── 加载用户导入的 MusicFree 插件 ──
                musicFreePluginManager.loadPersistedPlugins(context)
                musicFreePlugins = musicFreePluginManager.getAllPlugins()

                refreshActivePluginSupportedQualities(runRealDetection = false)
            } catch (e: Exception) {
                Log.w(TAG, "内置插件加载失败: ${e.message}")
            }
        }
    }

    private val builtinPluginId: String get() = ""

    /** 判断是否为内置插件（包括 cjxz123 系列和 lxmusic 内置） */
    private fun isBuiltinPlugin(pluginId: String): Boolean =
        pluginId == BUILTIN_LXMUSIC_ID || BuiltinPluginManager.isBuiltinPlugin(pluginId)

    /** 内置 lxmusic 插件条目（已移除内置落雪音源，保留方法以兼容旧调用） */
    @Suppress("UNUSED")
    private fun builtinLxmusicEntry(): PluginEntry? = null

    /** 内置 lxmusic 插件已移除，此方法保留为空操作以兼容旧调用点。 */
    private fun ensureBuiltinLxmusicPlugin() {
        // 内置落雪音源（lxmusic.js）已按用户要求移除，不再自动添加到插件列表。
        // 用户应通过 MusicFree 插件导入或 LX 插件导入功能自行添加音源。
    }

    /** 音源 key → 默认显示名（用于插件名称为空时的回退，避免显示"未知插件"） */
    private val sourceDisplayName: Map<String, String> = mapOf(
        "wy" to "网易云音源",
        "tx" to "QQ音源",
        "kw" to "酷我音源",
        "kg" to "酷狗音源",
        "mg" to "咪咕音源",
        "qq" to "QQ音源",
    )

    /**
     * 为名称为空或为"未知"的插件条目填充回退名称，避免 UI 显示"未知插件"。
     * 依据插件的音源列表推断默认名称。
     */
    private fun ensurePluginNames() {
        var changed = false
        lxPlugins = lxPlugins.map { plugin ->
            val name = plugin.info.name.trim()
            if (name.isBlank() || name == "未知" || name == "未知插件") {
                changed = true
                val fallback = plugin.sources.firstOrNull()?.let { sourceDisplayName[it] }
                    ?: if (plugin.id == BUILTIN_LXMUSIC_ID) "野花音源[内置]" else "音源插件"
                plugin.copy(info = plugin.info.copy(name = fallback))
            } else plugin
        }
        if (changed) Log.d(TAG, "ensurePluginNames: 已为名称为空的插件填充回退名称")
    }

    private fun mergeRecommendPlaylists(vararg groups: List<RecommendPlaylist>?): List<RecommendPlaylist> {
        val merged = mutableListOf<RecommendPlaylist>()
        val seen = LinkedHashSet<String>()
        groups.forEach { group ->
            group?.forEach { item ->
                val platform = item.platform.trim()
                val playlistId = item.playlistId.trim()
                val name = item.name.trim()
                if (platform.isBlank() || playlistId.isBlank() || name.isBlank()) return@forEach
                val key = "$platform::$playlistId"
                if (!seen.add(key)) return@forEach
                merged.add(
                    item.copy(
                        name = name,
                        platform = platform,
                        playlistId = playlistId,
                        coverUrl = item.coverUrl.trim(),
                    )
                )
            }
        }
        return merged
    }

    private fun preferRecommendPlaylistsWithCover(playlists: List<RecommendPlaylist>, minCoveredOnly: Int = 24): List<RecommendPlaylist> {
        if (playlists.isEmpty()) return playlists
        val withCover = playlists.filter { it.coverUrl.isNotBlank() }
        if (withCover.isEmpty()) return playlists
        return if (withCover.size >= minCoveredOnly) withCover else withCover + playlists.filter { it.coverUrl.isBlank() }
    }

    fun loadRecommendPlaylists() {
        val requestToken = ++recommendLoadToken
        Log.d(TAG, "loadRecommendPlaylists: start, token=$requestToken")
        viewModelScope.launch {
            isRecommendLoading = true
            recommendLoadError = ""
            val serverPlaylists = try {
                // 替换为 lx-music-mobile 4 平台歌单广场实现
                val result = LxSdkSongList.fetchAllFeatured()
                Log.d(TAG, "loadRecommendPlaylists: fetchAllFeatured 返回 ${result.size} 个歌单")
                result
            } catch (e: Exception) {
                Log.w(TAG, "lx 歌单广场获取失败", e)
                emptyList()
            }

            val neteaseHighQuality = try {
                MusicApiService.fetchNeteaseHighQualityPlaylists("全部", 30)
            } catch (e: Exception) {
                Log.w(TAG, "网易云精品歌单拉取失败", e)
                emptyList<com.yindong.music.data.model.Playlist>()
            }
            val neteaseTopPlaylists = try {
                MusicApiService.fetchNeteaseTopPlaylists()
            } catch (e: Exception) {
                Log.w(TAG, "网易云排行榜拉取失败", e)
                emptyList<com.yindong.music.data.model.Playlist>()
            }
            val neteaseHotPlaylists = try {
                MusicApiService.fetchNeteaseHotPlaylists("全部", 20)
            } catch (e: Exception) {
                Log.w(TAG, "网易云热门歌单拉取失败", e)
                emptyList<com.yindong.music.data.model.Playlist>()
            }

            val allServerPlaylists = serverPlaylists.toMutableList()
            if (neteaseHighQuality.isNotEmpty()) {
                allServerPlaylists.addAll(neteaseHighQuality.map { pl ->
                    com.yindong.music.data.api.RecommendPlaylist(
                        name = pl.name,
                        coverUrl = pl.coverUrl,
                        playCount = pl.playCount,
                        platform = pl.platform.ifEmpty { "netease" },
                        playlistId = pl.platformId.ifEmpty { "${pl.id}" },
                    )
                })
                Log.d(TAG, "添加 ${neteaseHighQuality.size} 个网易云精品歌单")
            }
            if (neteaseTopPlaylists.isNotEmpty()) {
                allServerPlaylists.addAll(neteaseTopPlaylists.map { pl ->
                    com.yindong.music.data.api.RecommendPlaylist(
                        name = pl.name,
                        coverUrl = pl.coverUrl,
                        playCount = pl.playCount,
                        platform = pl.platform.ifEmpty { "netease" },
                        playlistId = pl.platformId.ifEmpty { "${pl.id}" },
                    )
                })
                Log.d(TAG, "添加 ${neteaseTopPlaylists.size} 个网易云排行榜")
            }
            if (neteaseHotPlaylists.isNotEmpty()) {
                allServerPlaylists.addAll(neteaseHotPlaylists.map { pl ->
                    com.yindong.music.data.api.RecommendPlaylist(
                        name = pl.name,
                        coverUrl = pl.coverUrl,
                        playCount = pl.playCount,
                        platform = pl.platform.ifEmpty { "netease" },
                        playlistId = pl.platformId.ifEmpty { "${pl.id}" },
                    )
                })
                Log.d(TAG, "添加 ${neteaseHotPlaylists.size} 个网易云热门歌单")
            }

            val quickBase = mergeRecommendPlaylists(allServerPlaylists, builtInPlaylists)
            Log.d(TAG, "loadRecommendPlaylists: quickBase=${quickBase.size} (serverPlaylists=${serverPlaylists.size}, builtIn=${builtInPlaylists.size})")
            recommendPlaylists = quickBase
            if (quickBase.isEmpty()) {
                recommendLoadError = "网络连接失败，请检查网络后重试"
            }
            buildBannersFromRecommend(recommendPlaylists)
            loadBannerCovers()
            try {
                var working = quickBase
                val officialPlaylists = try {
                    MusicApiService.fetchOfficialFeaturedPlaylists(
                        totalLimit = 160,
                        perPlatformLimit = 48,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "官方精选歌单拉取失败", e)
                    emptyList()
                }
                if (requestToken != recommendLoadToken) return@launch
                if (officialPlaylists.isNotEmpty()) {
                    val mergedOfficial = mergeRecommendPlaylists(officialPlaylists, working)
                    if (mergedOfficial != recommendPlaylists) {
                        recommendPlaylists = mergedOfficial
                        buildBannersFromRecommend(recommendPlaylists)
                    }
                    working = mergedOfficial
                }

                val enriched = MusicApiService.enrichRecommendPlaylistCovers(
                    playlists = working,
                    maxLookup = 480,
                    parallelism = 16,
                )
                if (requestToken != recommendLoadToken) return@launch
                val preferred = preferRecommendPlaylistsWithCover(enriched, minCoveredOnly = 24)
                if (preferred != recommendPlaylists) {
                    recommendPlaylists = preferred
                    buildBannersFromRecommend(recommendPlaylists)
                }
            } catch (e: Exception) {
                Log.w(TAG, "推荐歌单加载失败", e)
            } finally {
                if (requestToken == recommendLoadToken) {
                    isRecommendLoading = false
                }
            }
        }
    }

    /** Banner 固定 4 张卡片，从推荐列表中查找封面 */
    private data class BannerSpec(
        val title: String,
        val subtitle: String,
        val platform: String,
        val playlistId: String,
    )

    private val fixedBannerSpecs by lazy {
        listOf(
            BannerSpec("每日30首", "QQ音乐 \u00b7 每日推荐", "QQ音乐", "6117630062"),
            BannerSpec("热歌排行榜", "QQ音乐 \u00b7 飙升榜", "QQ音乐", "62"),
            BannerSpec("新歌推荐", "QQ音乐 \u00b7 新歌榜", "QQ音乐", "27"),
            BannerSpec("猜你喜欢", "QQ音乐 \u00b7 精选推荐", "QQ音乐", "8995383783"),
            BannerSpec("粤语老歌", "网易云 \u00b7 经典粤语", "网易云", "13938849799"),
            BannerSpec("好听的音乐", "网易云 \u00b7 精选好歌", "网易云", "12449928929"),
            BannerSpec("抖音热歌", "QQ音乐 \u00b7 热门抖音", "QQ音乐", "7993862959"),
        )
    }

    private fun buildBannersFromRecommend(playlists: List<RecommendPlaylist>) {
        val current = banners
        banners = fixedBannerSpecs.mapIndexed { i, spec ->
            val matched = playlists.find { it.platform == spec.platform && it.playlistId == spec.playlistId }
            val existingCover = current.getOrNull(i)?.imageUrl.orEmpty()
            val cover = matched?.coverUrl?.takeIf { it.isNotBlank() } ?: existingCover
            Banner(
                id = i.toLong() + 1,
                title = spec.title,
                subtitle = spec.subtitle,
                imageUrl = cover,
                platform = spec.platform,
                playlistId = spec.playlistId,
            )
        }
    }

    /** 异步获取 Banner 封面（从服务器 API 拉取歌单详情提取 coverUrl） */
    private fun loadBannerCovers() {
        viewModelScope.launch {
            fixedBannerSpecs.forEachIndexed { i, spec ->
                launch {
                    try {
                        val result = fetchExternalPlaylist(spec.platform, spec.playlistId)
                        val coverUrl = result?.coverUrl.orEmpty()
                        if (coverUrl.isNotBlank()) {
                            val current = banners.toMutableList()
                            if (i < current.size && current[i].imageUrl.isBlank()) {
                                current[i] = current[i].copy(imageUrl = coverUrl)
                                banners = current
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Banner封面加载失败[${spec.title}]: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 平台显示名 → LX 源 id（"wy"/"tx"/"kw"/"kg"）。
     * 输入已是 LX 源 id 时原样返回，便于 [LinkParser.parse] 返回值与旧调用点共用同一接口。
     */
    private fun toLxSource(platform: String): String = when (platform) {
        "wy", "tx", "kw", "kg" -> platform
        "QQ音乐", "QQ", "qq" -> "tx"
        "网易云", "网易云音乐", "netease" -> "wy"
        "酷我", "酷我音乐", "kuwo" -> "kw"
        "酷狗", "酷狗音乐", "kugou" -> "kg"
        else -> platform
    }

    /**
     * 通过 lx-music-mobile 实现（[LxSdkSongList.getListDetail]）获取外部歌单详情。
     * 替代原 [MusicApiService.fetchPlaylistImport] 的功能：
     * - 严格遵循"来源是哪个就用哪个来源的音源播放"原则
     * - 返回的 [Song] 已包含 lxSourceKey / pluginRawJson，可直接用于播放
     * @param platform 平台显示名或 LX 源 id
     * @param playlistId 歌单 id 或歌单 URL
     * @return [MusicApiService.ImportedPlaylistData] 或 null（失败时）
     */
    private suspend fun fetchExternalPlaylist(
        platform: String,
        playlistId: String,
    ): MusicApiService.ImportedPlaylistData? = try {
        val lxSource = toLxSource(platform)
        val result = LxSdkSongList.getListDetail(lxSource, playlistId)
        if (result.list.isEmpty() && result.info.name.isBlank()) {
            Log.w(TAG, "fetchExternalPlaylist: empty result for $lxSource/$playlistId")
            null
        } else {
            MusicApiService.ImportedPlaylistData(
                name = result.info.name.ifBlank { "导入歌单" },
                coverUrl = result.info.img,
                songs = result.list,
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "fetchExternalPlaylist failed: $platform/$playlistId — ${e.message}")
        null
    }

    /** 内置精选歌单——服务器无数据时兜底 (均为各平台真实歌单ID) */
    private val builtInPlaylists = listOf(
        // ── 网易云音乐 (官方榜单 + 热门用户歌单) ──
        RecommendPlaylist("云音乐热歌榜",       "", 3200_0000, "网易云", "3778678"),
        RecommendPlaylist("云音乐飙升榜",       "", 2100_0000, "网易云", "19723756"),
        RecommendPlaylist("云音乐新歌榜",       "", 1800_0000, "网易云", "3779629"),
        RecommendPlaylist("云音乐原创榜",       "", 680_0000,  "网易云", "2884035"),
        // ── QQ音乐 (热门歌单) ──
        RecommendPlaylist("抖音最热DJ合集",     "", 860_0000,  "QQ音乐", "7526065605"),
        RecommendPlaylist("经典老歌500首",     "", 1500_0000, "QQ音乐", "7446068798"),
        RecommendPlaylist("华语流行热歌",       "", 920_0000,  "QQ音乐", "8690683167"),
        RecommendPlaylist("抖音热歌精选",       "", 1100_0000, "QQ音乐", "7603617752"),
        // ── 酷我音乐 ──
        RecommendPlaylist("酷我热歌榜",         "", 780_0000,  "酷我音乐", "236682355"),
        RecommendPlaylist("酷我飙升榜",         "", 560_0000,  "酷我音乐", "244441035"),
        RecommendPlaylist("酷我新歌榜",         "", 430_0000,  "酷我音乐", "279521540"),
        // ── 酷狗音乐 ──
        RecommendPlaylist("酷狗TOP500",       "", 2500_0000, "酷狗音乐", "320496"),
        RecommendPlaylist("酷狗飙升榜",         "", 520_0000,  "酷狗音乐", "31308"),
        RecommendPlaylist("酷狗新歌榜",         "", 390_0000,  "酷狗音乐", "279203"),
        // ── 跨平台热门 ──
        RecommendPlaylist("2025热搜热歌",      "", 1600_0000, "网易云", "2892128872"),
        RecommendPlaylist("2025流行音乐集锦",   "", 890_0000,  "网易云", "13834199288"),
        )


    /**
     * 加载热门搜索（lx-music-mobile 4 平台热搜合并去重）
     * 替换原有硬编码热搜词，参考 lx-music-mobile-master/src/core/hotSearch.ts
     */
    private fun loadHotSearches() {
        viewModelScope.launch {
            try {
                val list = LxSdkHotSearch.fetchAll()
                if (list.isNotEmpty()) {
                    hotSearches = list
                    Log.d(TAG, "loadHotSearches: 获取 ${list.size} 个热搜词")
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadHotSearches failed: ${e.message}")
            }
        }
    }

    /**
     * 启动时用 lx-music-mobile 4 平台排行榜获取数据
     * 替换原服务器 /api/init 批量接口
     */
    private fun loadInitData() {
        viewModelScope.launch {
            try {
                coroutineScope {
                    val hotDeferred = async { fetchLxLeaderboardSongs(listOf(
                        Triple("wy", "3778678", "网易云"), Triple("tx", "26", "QQ音乐"),
                        Triple("kg", "8888", "酷狗"), Triple("kw", "16", "酷我"),
                    )) }
                    val risingDeferred = async { fetchLxLeaderboardSongs(listOf(
                        Triple("wy", "19723756", "网易云"), Triple("tx", "62", "QQ音乐"),
                        Triple("kg", "6666", "酷狗"), Triple("kw", "93", "酷我"),
                    )) }
                    val newDeferred = async { fetchLxLeaderboardSongs(listOf(
                        Triple("wy", "3779629", "网易云"), Triple("tx", "27", "QQ音乐"),
                        Triple("kg", "22650", "酷狗"), Triple("kw", "17", "酷我"),
                    )) }
                    val originalDeferred = async { fetchLxLeaderboardSongs(listOf(
                        Triple("wy", "3779652", "网易云"), Triple("tx", "36", "QQ音乐"),
                        Triple("kg", "23784", "酷狗"), Triple("kw", "158", "酷我"),
                    )) }
                    hotChart = hotDeferred.await()
                    risingChart = risingDeferred.await()
                    newChart = newDeferred.await()
                    originalChart = originalDeferred.await()
                }
                Log.d(TAG, "lx 排行榜初始化成功: hot=${hotChart.size}, rising=${risingChart.size}")
            } catch (e: Exception) {
                Log.w(TAG, "lx 排行榜初始化失败", e)
            }
        }
    }

    /**
     * 并发4个热歌榜请求 (以前是串行的，现在并发)
     */
    private fun loadChartsConcurrently() {
        viewModelScope.launch {
            try {
                coroutineScope {
                    val hotDeferred = async { MusicApiService.fetchAllHotChart() }
                    val risingDeferred = async { MusicApiService.fetchNeteaseHotChart() }
                    val newDeferred = async { MusicApiService.fetchKuwoHotChart() }
                    val originalDeferred = async { MusicApiService.fetchKugouHotChart() }
                    hotChart = hotDeferred.await()
                    risingChart = risingDeferred.await()
                    newChart = newDeferred.await()
                    originalChart = originalDeferred.await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "并发加载排行榜失败", e)
            }
        }
    }

    // ── 浏览外部歌单（不保存到本地） ──
    var externalViewPlaylist by mutableStateOf<Playlist?>(null)
        private set
    var isExternalViewLoading by mutableStateOf(false)
        private set
    private var externalViewRequestToken = 0L

    fun loadExternalPlaylistForView(platform: String, playlistId: String) {
        viewModelScope.launch {
            val requestToken = ++externalViewRequestToken
            isExternalViewLoading = true
            externalViewPlaylist = null
            try {
                val result = fetchExternalPlaylist(platform, playlistId)
                if (result != null) {
                    val initialSongs = result.songs
                    val initialCover = if (result.coverUrl.isNotBlank()) {
                        result.coverUrl
                    } else {
                        initialSongs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty()
                    }
                    externalViewPlaylist = Playlist(
                        id = -1L,
                        name = result.name,
                        coverUrl = initialCover,
                        songCount = initialSongs.size,
                        playCount = 0,
                        creator = platform,
                        songs = initialSongs,
                    )
                    val needsEnrich = initialSongs.any {
                        it.coverUrl.isBlank() &&
                            it.artistPicUrl.isBlank() &&
                            it.platform.isNotBlank() &&
                            it.platformId.isNotBlank()
                    }
                    if (needsEnrich) {
                        launch {
                            fun publishIfLatest(enrichedSongs: List<Song>) {
                                if (requestToken != externalViewRequestToken) return
                                val current = externalViewPlaylist ?: return
                                val mergedCover = if (current.coverUrl.isNotBlank()) {
                                    current.coverUrl
                                } else {
                                    enrichedSongs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty()
                                }
                                val updated = current.copy(
                                    coverUrl = mergedCover,
                                    songCount = enrichedSongs.size,
                                    songs = enrichedSongs,
                                )
                                if (updated != current) {
                                    externalViewPlaylist = updated
                                }
                            }
                            try {
                                val fast = MusicApiService.enrichMissingSongCovers(
                                    songs = initialSongs,
                                    maxLookup = 24,
                                    parallelism = 12,
                                )
                                publishIfLatest(fast)
                                val full = MusicApiService.enrichMissingSongCovers(
                                    songs = fast,
                                    maxLookup = 160,
                                    parallelism = 10,
                                )
                                publishIfLatest(full)
                            } catch (e: Exception) {
                                Log.w(TAG, "外部歌单歌曲补图失败", e)
                            }
                        }
                    }
                } else {
                    showToast("获取歌单失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载外部歌单失败", e)
                showToast("加载失败: ${e.message}")
            } finally {
                isExternalViewLoading = false
            }
        }
    }

    fun saveExternalPlaylistToMine(): Long? {
        val ext = externalViewPlaylist ?: return null
        val newId = System.currentTimeMillis()
        val newPlaylist = ext.copy(id = newId, creator = userName)
        myPlaylists = myPlaylists + newPlaylist
        LocalStorage.savePlaylists(myPlaylists)
        showToast("已保存到我的歌单")
        return newId
    }

    // ── 导入外部歌单 ──
    private var importJob: Job? = null

    fun importExternalPlaylist(platform: String, playlistId: String) {
        importJob?.cancel()
        importState = ImportState.Loading
        importJob = viewModelScope.launch {
            try {
                val result = fetchExternalPlaylist(platform, playlistId)
                if (result == null) {
                    importState = ImportState.Error("获取歌单失败，请检查链接是否正确")
                    return@launch
                }
                val enrichedSongs = try {
                    MusicApiService.enrichMissingSongCovers(
                        songs = result.songs,
                        maxLookup = 160,
                        parallelism = 10,
                    )
                } catch (_: Exception) {
                    result.songs
                }
                val mergedCover = if (result.coverUrl.isNotBlank()) {
                    result.coverUrl
                } else {
                    enrichedSongs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty()
                }
                val newPlaylist = Playlist(
                    id = System.currentTimeMillis(),
                    name = result.name,
                    coverUrl = mergedCover,
                    songCount = enrichedSongs.size,
                    playCount = 0,
                    creator = userName,
                    songs = enrichedSongs,
                )
                myPlaylists = myPlaylists + newPlaylist
                LocalStorage.savePlaylists(myPlaylists)
                importState = ImportState.Success(newPlaylist.id, result.name, enrichedSongs.size)
            } catch (e: Exception) {
                Log.e(TAG, "导入外部歌单失败", e)
                importState = ImportState.Error("导入失败: ${e.message}")
            }
        }
    }

    fun resetImportState() {
        importState = ImportState.Idle
    }

    /** 浏览外部歌单：获取歌曲并直接播放，不保存到本地歌单 */
    fun viewExternalPlaylist(platform: String, playlistId: String) {
        viewModelScope.launch {
            isBuffering = true
            try {
                val result = fetchExternalPlaylist(platform, playlistId)
                if (result != null && result.songs.isNotEmpty()) {
                    playPlaylist(result.songs, 0)
                } else {
                    showToast("获取歌单失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "浏览外部歌单失败", e)
                showToast("加载失败: ${e.message}")
            } finally {
                isBuffering = false
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importState = ImportState.Idle
    }


    /**
     * Try to get a music URL from plugins.
     * Priority: 1) origin plugin (found the song, pluginRawJson is its format)
     *           2) selected plugin (only if it supports source)
     *           3) fallback: other plugins supporting source (up to 3)
     */
    /** Reject plugin URLs that contain unresolved template variables or are clearly invalid. */
    private val trustedCleartextPlaybackHosts = setOf(
        "127.0.0.1",
        "localhost",
        // 常见音乐 CDN HTTP 域名
        "antiserver.kuwo.cn",
        "nmobi.kuwo.cn",
        "other.web.rh01.sycdn.kuwo.cn",
        "other.web.nf01.sycdn.kuwo.cn",
        "trackercdn.kugou.com",
        "txcdn.kugou.com",
    )

    private fun isTrustedPlaybackHost(host: String): Boolean {
        if (host in trustedCleartextPlaybackHosts) return true
        // 支持子域名匹配（如 *.kuwo.cn, *.kugou.com, *.126.net）
        if (host.endsWith(".music.126.net")) return true
        if (host.endsWith(".126.net")) return true
        if (host.endsWith(".kuwo.cn")) return true
        if (host.endsWith(".kugou.com")) return true
        if (host.endsWith(".migu.cn")) return true
        return false
    }

    private fun isValidPluginUrl(url: String): Boolean {
        // 本地文件路径直接通过（本地音乐播放）
        if (url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")) {
            return true
        }
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = parsed.host?.trim()?.lowercase().orEmpty()
        if (host.isBlank()) return false
        // 放宽限制：插件返回的 URL（包括明文 HTTP）视为有效
        // 用户主动安装插件，意味着信任该插件返回的 URL
        // 仅排除明显无效的 URL
        if (url.contains("=undefined") || url.contains("=null") || url.contains("=NaN")) return false
        if (url.contains("/undefined") || url.contains("/null")) return false
        // URL 必须有路径部分（域名后至少还有一级路径），排除只有域名的无效URL如 "https://wx.music.tc.qq.com/"
        val path = parsed.path?.trim().orEmpty()
        if (path.isBlank() || path == "/") return false
        return true
    }

    private suspend fun tryNeteaseFirst(song: Song, quality: String): com.yindong.music.data.api.MusicUrlResult? {
        // 内置网易云 API 已移除，统一由落雪插件获取播放链接
        return null
    }

    /**
     * 将插件音质 key 映射为服务端 API 音质级别
     */
    private fun mapQualityToApiLevel(quality: String): String {
        return when (quality.lowercase()) {
            "standard", "128k", "128" -> "standard"
            "exhigh", "320k", "320" -> "exhigh"
            "lossless", "flac" -> "lossless"
            "hires" -> "jyeffect"
            "master", "jymaster" -> "jymaster"
            else -> "standard"
        }
    }

    /**
     * 判断平台是否是网易云相关平台
     * netease_first 策略只应作用于网易云平台的歌曲
     */
    private fun isNeteasePlatform(platform: String): Boolean {
        return platform == "网易云" || platform == "网易云2" || platform == "网易云音乐" ||
               platform.equals("netease", ignoreCase = true)
    }

    private fun passSecurityGate(
        action: MusicPlaybackGate.Action,
        defaultMessage: String,
        parseScope: Boolean = false,
    ): Boolean {
        val decision = MusicPlaybackGate.evaluate(action)
        if (decision.allowed) return true
        val message = decision.reason ?: defaultMessage
        Log.w(TAG, "安全策略拦截[$action]: $message")
        if (parseScope) parseError = message else playError = message
        showToast(message)
        if (decision.shouldKillProcess) {
            SecurityGuard.killProcess()
        }
        return false
    }

    // ── 播放控制 ──
    /**
     * 播放歌曲
     * 参考 MusicFree TrackPlayer.play() 的架构：
     * 1. 通过 song.lxPluginId 路由到搜索该歌曲的原始插件
     * 2. 调用 plugin.methods.getMediaSource(musicItem, quality) 获取音源
     * 3. 失败时按优先级链尝试其他插件/服务端API
     * 4. 获取到音源后设置到 ExoPlayer 播放
     */
    fun playSong(song: Song) {
        if (!passSecurityGate(MusicPlaybackGate.Action.START_PLAYBACK, "当前环境存在风险，已阻止播放")) return
        if (playerReleased) return
        requestAudioFocus()
        val requestToken = ++playRequestToken
        // 用户主动切歌时重置重试标记
        if (song.platformId != currentSong?.platformId) lastRetryPlatformId = null
        playJob?.cancel()
        Log.d(TAG, "playSong start: song=${song.title}, platform=${song.platform}, pid=${song.platformId}, lxPluginId=${song.lxPluginId.take(8)}, lxSourceKey=${song.lxSourceKey}")
        currentSong = song
        intendedPlatformId = song.platformId
        // 标记歌词正在加载，但不立即清空旧歌词，避免界面闪烁
        isLoadingLyrics = true
        currentLyricIndex = 0
        // 立即停止旧播放，防止旧歌曲的回调事件覆盖新歌曲状态
        player.stop()
        val existingIndex = playlist.indexOfFirst { it.platformId == song.platformId && it.platform == song.platform }
        if (existingIndex >= 0) {
            currentIndex = existingIndex
        } else {
            if (playlist.isEmpty()) {
                playlist = listOf(song)
                currentIndex = 0
            } else {
                playlist = playlist + song
                currentIndex = playlist.lastIndex
            }
        }
        Log.d(TAG, "playSong resolved: queueSize=${playlist.size}, currentIndex=$currentIndex")
        // 歌词与播放并行加载——不等URL拿到就开始获取歌词
        loadLyrics(song, requestToken)

        // 本地歌曲：若开启网络补全且缺少封面/歌词，从网络获取（音源顺序 QQ → 网易云 → 酷狗 → 酷我）
        if (song.platform == "本地" && onlineFallbackEnabled) {
            fetchOnlineCoverAndLyrics(song, requestToken)
        }

        playJob = viewModelScope.launch {
            isBuffering = true
            playError = null
            try {
                val result = resolveMusicUrl(song)
                if (requestToken != playRequestToken) {
                    Log.d(TAG, "playSong ignored stale url result: token=$requestToken latest=$playRequestToken")
                    return@launch
                }
                if (result.url.isNullOrEmpty()) {
                    Log.w(TAG, "playSong url empty: song=${song.title}, err=${result.error}")
                    playError = result.error ?: "获取播放链接失败"
                    isBuffering = false
                    return@launch
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(result.url)
                    .setMediaId(song.platformId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.coverUrl.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) })
                            .build()
                    )
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                Log.d(TAG, "playSong player started: title=${song.title}, idx=$currentIndex, queueSize=${playlist.size}")

                addToRecent(song)
                prefetchNextSongUrl()
                loadCoverToService(song.coverUrl)
                if (!serviceStarted) {
                    startPlaybackService()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "播放失败", e)
                playError = "播放失败: ${e.message}"
                isBuffering = false
            }
        }
    }

    /**
     * 解析歌曲播放URL
     *
     * 严格遵循 lx-music-mobile-master / lx-music-desktop-master 插件系统设计规范：
     * 每个音源仅使用其对应的插件系统解析方法（运行在 [LxPluginEngine] 中），
     * 完全禁用任何形式的回退机制或备用解析方案（weapi / zzcSign / 跨音源 / 官方 API / LxSdkMusicUrl 均已移除）。
     *
     * 流程：
     * 1. 直接URL（搜索时已返回播放链接，如抖音/汽水音乐解析）→ 直接返回
     * 2. 通过 [LxPluginEngine] 调用 lxmusic.js 插件解析（与 lx-music-mobile 的 sendUserApiRequest 完全一致）
     * 3. 解析失败返回错误（不进行任何兜底）
     */
    /**
     * 跨音源换源备选列表（与 lx-music-desktop getOnlineOtherSourceMusicUrl 一致）
     * 当原音源失败时，按此顺序尝试其他音源的同名歌曲
     */
    private val CROSS_SOURCE_CANDIDATES = listOf("wy", "tx", "kw", "kg", "mg")

    private suspend fun resolveMusicUrl(song: Song): com.yindong.music.data.api.MusicUrlResult {
        // 0. 直接URL（搜索时已返回播放链接，如抖音/汽水音乐解析）
        if (song.directUrl.isNotBlank() && isValidPluginUrl(song.directUrl)) {
            // 本地文件路径转 file:// URI，ExoPlayer 需要完整 URI 才能用 FileDataSource
            val finalUrl = if (song.directUrl.startsWith("/")) "file://${song.directUrl}" else song.directUrl
            Log.d(TAG, "resolveMusicUrl: directUrl=${finalUrl.take(80)}")
            httpDataSourceFactory.setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0"))
            return com.yindong.music.data.api.MusicUrlResult(url = finalUrl)
        }

        val context = getApplication<Application>()
        val quality = lxQualityKey

        // 1. MusicFree 插件优先：如果歌曲来自 MusicFree 插件搜索，直接走 getMediaSource
        if (song.musicFreePluginId.isNotBlank()) {
            Log.d(TAG, "resolveMusicUrl: MusicFree plugin=${song.musicFreePluginId}, songId=${song.platformId}")
            val mfQuality = mapLxQualityToMusicFree(quality)
            val mfResult = musicFreePluginManager.getMediaSource(song.musicFreePluginId, song.pluginRawJson, mfQuality)
            if (mfResult.url.isNotBlank()) {
                Log.d(TAG, "resolveMusicUrl: MusicFree OK url=${mfResult.url.take(80)}")
                val headers = mfResult.headers.toMutableMap()
                mfResult.userAgent?.let { headers.putIfAbsent("user-agent", it) }
                return applyPluginResult(com.yindong.music.data.api.MusicUrlResult(url = mfResult.url, headers = headers))
            }
            Log.w(TAG, "resolveMusicUrl: MusicFree failed, trying fallback")
        }

        // 2. 用户导入的 LX 插件解析
        // 优先用歌曲自带的 lxPluginId（来自插件搜索），否则回退到用户选中的插件
        // 这样首页歌单等非插件搜索来源的歌曲也能用选中插件解析
        val pluginIdForSong = song.lxPluginId.ifBlank { lxSelectedPluginId }
        if (pluginIdForSong.isNotBlank()) {
            val loadedIds = lxPluginManager.getAllPluginEntries().map { it.id }.toSet()
            if (pluginIdForSong in loadedIds && isPluginEnabled(pluginIdForSong)) {
                val source = effectiveLxSource(song)
                if (source.isNotBlank()) {
                    // 2a. 优先使用预取缓存
                    val cacheKey = song.platformId + "|" + pluginIdForSong
                    val cached = prefetchUrlCache[cacheKey]
                    if (cached != null && System.currentTimeMillis() - cached.timestamp < PREFETCH_CACHE_TTL) {
                        prefetchUrlCache.remove(cacheKey)
                        return applyPluginResult(com.yindong.music.data.api.MusicUrlResult(url = cached.url, headers = cached.headers))
                    }
                    // 2b. 缓存未命中，通过 LX 插件实时解析
                    val lxResult = lxPluginManager.musicUrl(pluginIdForSong, source, song, 15000L, quality)
                    if (lxResult.url.isNotBlank()) {
                        return applyPluginResult(com.yindong.music.data.api.MusicUrlResult(url = lxResult.url, headers = lxResult.headers))
                    }
                    Log.w(TAG, "resolveMusicUrl: LX plugin failed: ${lxResult.url.ifBlank { "empty" }}")
                }
            }
        }

        // 3. 跨音源换源：尝试其他 MusicFree 插件或 LX 插件搜索同名歌曲
        if (song.title.isNotBlank()) {
            // 3a. 尝试其他 MusicFree 插件搜索同名歌曲
            for (mfPlugin in musicFreePlugins.filter { it.enabled && it.mounted }) {
                try {
                    val mfSearch = musicFreePluginManager.search(mfPlugin.id, "${song.title} ${song.artist}".trim(), 1, "music")
                    val match = mfSearch.data.firstOrNull { s ->
                        s.title.equals(song.title, ignoreCase = true) &&
                        (s.artist.isBlank() || song.artist.isBlank() ||
                         s.artist.contains(song.artist, ignoreCase = true) ||
                         song.artist.contains(s.artist, ignoreCase = true))
                    }
                    if (match != null) {
                        Log.d(TAG, "resolveMusicUrl: cross-source MusicFree found ${match.title} on ${mfPlugin.info.platform}")
                        val mfResult = musicFreePluginManager.getMediaSource(mfPlugin.id, match.rawJson, mapLxQualityToMusicFree(quality))
                        if (mfResult.url.isNotBlank()) {
                            Log.d(TAG, "resolveMusicUrl: cross-source MusicFree OK")
                            val headers = mfResult.headers.toMutableMap()
                            mfResult.userAgent?.let { headers.putIfAbsent("user-agent", it) }
                            return applyPluginResult(com.yindong.music.data.api.MusicUrlResult(url = mfResult.url, headers = headers))
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "resolveMusicUrl: cross-source MusicFree ${mfPlugin.info.platform} error: ${e.message}")
                }
            }

            // 3b. 尝试 LX 跨音源换源（与 lx-music-desktop getOtherSource 一致）
            val source = effectiveLxSource(song)
            val triedSources = mutableSetOf(source)
            for (candidateSource in CROSS_SOURCE_CANDIDATES) {
                if (candidateSource in triedSources) continue
                if (!LxPluginEngine.isSourceSupported(candidateSource)) continue

                try {
                    val searchResult = LxSdkSearchManager.search(
                        sourceId = candidateSource,
                        keyword = "${song.title} ${song.artist}".trim(),
                        page = 1,
                        timeoutMs = 8000L,
                    )
                    val match = searchResult.songs.firstOrNull { s ->
                        s.title.equals(song.title, ignoreCase = true) &&
                        (s.artist.isBlank() || song.artist.isBlank() ||
                         s.artist.contains(song.artist, ignoreCase = true) ||
                         song.artist.contains(s.artist, ignoreCase = true))
                    }
                    if (match != null) {
                        triedSources.add(candidateSource)
                        Log.d(TAG, "resolveMusicUrl: crossSource found ${match.title} on $candidateSource, id=${match.platformId}")
                        // 3b-1: 优先用选中的 LX 插件解析，其次查找其他已加载插件中支持该音源的
                        val matchedLxPlugin = lxPlugins.firstOrNull { p ->
                            p.id == lxSelectedPluginId && candidateSource in p.sources
                        } ?: lxPlugins.firstOrNull { p ->
                            candidateSource in p.sources
                        }
                        if (matchedLxPlugin != null) {
                            val crossResult = lxPluginManager.musicUrl(matchedLxPlugin.id, candidateSource, match, 15000L, quality)
                            if (crossResult.url.isNotBlank()) {
                                Log.d(TAG, "resolveMusicUrl: crossSource LX OK source=$candidateSource")
                                return applyPluginResult(com.yindong.music.data.api.MusicUrlResult(url = crossResult.url, headers = crossResult.headers))
                            }
                        }
                        // 3b-2: 兜底 LxPluginEngine（lxmusic.js 已移除，会返回错误，但不影响流程）
                        val crossResult = LxPluginEngine.getMusicUrl(context, candidateSource, match, quality)
                        if (crossResult.url.isNotBlank()) {
                            Log.d(TAG, "resolveMusicUrl: crossSource OK source=$candidateSource")
                            return applyPluginResult(com.yindong.music.data.api.MusicUrlResult(url = crossResult.url, headers = crossResult.headers))
                        }
                        Log.d(TAG, "resolveMusicUrl: crossSource $candidateSource failed: ${crossResult.error}")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "resolveMusicUrl: crossSource $candidateSource search error: ${e.message}")
                }
            }
        }

        return com.yindong.music.data.api.MusicUrlResult(error = "无可用插件解析播放链接，请导入 MusicFree 插件或 LX 插件后重试")
    }

    /** 将 LX 音质 key 映射为 MusicFree 音质 key */
    private fun mapLxQualityToMusicFree(lxQuality: String): String {
        return when (lxQuality) {
            "128k" -> "standard"
            "320k" -> "high"
            "flac" -> "lossless"
            "flac24bit" -> "lossless"
            "hires" -> "lossless"
            else -> "standard"
        }
    }

    /**
     * 处理插件返回的音源结果：设置 headers、代理等
     */
    private fun applyPluginResult(pluginResult: com.yindong.music.data.api.MusicUrlResult): com.yindong.music.data.api.MusicUrlResult {
        val pluginHeaders = mutableMapOf<String, String>()
        val allowedHeaders = setOf("referer", "user-agent", "cookie", "origin")
        for ((k, v) in pluginResult.headers) {
            if (k.lowercase() in allowedHeaders) {
                pluginHeaders[k.lowercase()] = v
            }
        }
        if (!pluginHeaders.containsKey("user-agent")) {
            pluginHeaders["user-agent"] = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        val finalUrl: String
        if (pluginHeaders.isNotEmpty() && pluginHeaders.keys.any { it != "user-agent" }) {
            if (!audioProxy.isRunning()) audioProxy.start()
            finalUrl = audioProxy.register(pluginResult.url ?: "", pluginHeaders)
            Log.d(TAG, "applyPluginResult: PROXY url=${finalUrl.take(60)}")
        } else {
            finalUrl = pluginResult.url ?: ""
            httpDataSourceFactory.setDefaultRequestProperties(pluginHeaders)
            Log.d(TAG, "applyPluginResult: DIRECT url=${finalUrl.take(60)}")
        }
        return com.yindong.music.data.api.MusicUrlResult(url = finalUrl, headers = pluginResult.headers)
    }

    /** 预取多首歌曲的播放URL (后台预热缓存, 切歌时零等待) */
    private fun prefetchNextSongUrl() {
        if (playlist.size <= 1) return
        val indices = mutableListOf<Int>()
        val nextIdx = (currentIndex + 1) % playlist.size
        indices.add(nextIdx)
        if (playlist.size > 2) {
            val prevIdx = if (currentIndex == 0) playlist.size - 1 else currentIndex - 1
            indices.add(prevIdx)
        }
        if (playlist.size > 3) {
            val nextNextIdx = (currentIndex + 2) % playlist.size
            indices.add(nextNextIdx)
        }
        indices.forEach { idx ->
            val song = playlist.getOrNull(idx) ?: return@forEach
            // 跳过已缓存的歌曲
            val cacheKey = song.platformId + "|" + (song.lxPluginId.ifBlank { lxSelectedPluginId })
            if (prefetchUrlCache[cacheKey]?.let { System.currentTimeMillis() - it.timestamp < PREFETCH_CACHE_TTL } == true) return@forEach
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val src = song.lxSourceKey.ifBlank { effectiveLxSource(song) }
                    val prefetchPluginId = song.lxPluginId.ifBlank { lxSelectedPluginId }
                    if (src.isNotBlank() && prefetchPluginId.isNotBlank() && LxPluginEngine.isSourceSupported(src)) {
                        val result = lxPluginManager.musicUrl(prefetchPluginId, src, song, 15000L, lxQualityKey)
                        if (result.url.isNotBlank()) {
                            prefetchUrlCache[cacheKey] = PrefetchEntry(
                                url = result.url,
                                headers = result.headers,
                                timestamp = System.currentTimeMillis(),
                            )
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(getApplication(), MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
            serviceStarted = true
        } catch (e: Exception) {
            Log.e(TAG, "启动播放服务失败", e)
        }
    }

    private fun loadCoverToService(coverUrl: String) {
        if (coverUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (coverUrl.startsWith("/") || coverUrl.startsWith("file://")) {
                    // 本地文件封面
                    val path = if (coverUrl.startsWith("file://")) coverUrl.removePrefix("file://") else coverUrl
                    val file = java.io.File(path)
                    if (!file.exists()) return@launch
                    file.inputStream().use { BitmapFactory.decodeStream(it) }
                } else {
                    // 网络封面
                    val request = Request.Builder().url(coverUrl).build()
                    val response = OkHttpClient().newCall(request).execute()
                    val body = response.body?.byteStream() ?: return@launch
                    val bmp = BitmapFactory.decodeStream(body)
                    body.close()
                    bmp
                }?.let { if (it.width > 512 || it.height > 512) Bitmap.createScaledBitmap(it, 512, 512, true) else it }
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        try {
                            val service = getApplication<Application>()
                            (service.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)
                        } catch (_: Exception) {}
                        MusicPlaybackServiceHelper.updateCover(getApplication(), bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "封面加载失败: ${e.message}")
            }
        }
    }

    /** 歌曲播放完毕后自动切下一首（根据播放模式） */
    private fun autoPlayNext() {
        if (playlist.isEmpty()) return
        when (playMode) {
            PlayMode.SINGLE -> {
                // 单曲循环：重新播放当前歌曲
                currentSong?.let { playSong(it) }
            }
            PlayMode.LOOP -> {
                currentIndex = (currentIndex + 1) % playlist.size
                playlist.getOrNull(currentIndex)?.let { playSong(it) }
            }
            PlayMode.SHUFFLE -> {
                if (playlist.size <= 1) {
                    currentSong?.let { playSong(it) }
                } else {
                    var next = currentIndex
                    while (next == currentIndex) {
                        next = (0 until playlist.size).random()
                    }
                    currentIndex = next
                    playlist.getOrNull(currentIndex)?.let { playSong(it) }
                }
            }
        }
    }

    /** 将歌曲插入到当前播放位置的下一首 (不立即播放) */
    fun playNextInQueue(song: Song) {
        // 如果已在队列中，先移除再插入
        val existing = playlist.indexOfFirst { it.platformId == song.platformId && it.platform == song.platform }
        val mutable = playlist.toMutableList()
        if (existing >= 0) {
            mutable.removeAt(existing)
            if (existing <= currentIndex && currentIndex > 0) currentIndex--
        }
        val insertAt = (currentIndex + 1).coerceAtMost(mutable.size)
        mutable.add(insertAt, song)
        playlist = mutable
        showToast("\u5df2\u6dfb\u52a0\u5230\u4e0b\u4e00\u9996\u64ad\u653e")
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int = 0) {
        playlist = songs
        currentIndex = startIndex
        songs.getOrNull(startIndex)?.let { playSong(it) }
    }

    /**
     * 本地歌曲网络补全：当本地歌曲缺少封面或歌词时，按音源顺序
     * QQ → 网易云 → 酷狗 → 酷我 搜索匹配歌曲，取第一个匹配项的封面和歌词。
     *
     * 仅在 [onlineFallbackEnabled] 开启、且 [song] 为本地歌曲时调用。
     * 更新 [currentSong] 的 coverUrl 与 lrcText，并刷新通知栏封面与歌词显示。
     */
    private fun fetchOnlineCoverAndLyrics(song: Song, playToken: Long) {
        if (!onlineFallbackEnabled) return
        if (song.platform != "本地") return
        // 仅当缺少封面或歌词时才发起网络请求
        val needCover = song.coverUrl.isBlank()
        val needLyrics = song.lrcText.isBlank()
        if (!needCover && !needLyrics) return

        viewModelScope.launch {
            val query = "${song.title} ${song.artist}".trim()
            if (query.isBlank()) return@launch
            Log.d(TAG, "fetchOnlineCoverAndLyrics: query='$query', needCover=$needCover, needLyrics=$needLyrics")

            // 音源顺序：QQ → 网易云 → 酷狗 → 酷我
            val sourceOrder = listOf("tx", "wy", "kg", "kw")
            var matchedSong: Song? = null
            var matchedSource: String = ""

            for (source in sourceOrder) {
                if (isStale(playToken)) return@launch
                try {
                    val result = withTimeoutOrNull(8000L) {
                        LxSdkSearchManager.search(source, query, page = 1, timeoutMs = 8000L)
                    }
                    if (result == null || result.songs.isEmpty()) {
                        Log.d(TAG, "fetchOnlineCoverAndLyrics: source=$source no results")
                        continue
                    }
                    // 取第一条结果作为匹配（搜索结果已按相关度排序）
                    val first = result.songs.first()
                    Log.d(TAG, "fetchOnlineCoverAndLyrics: source=$source matched '${first.title}' - ${first.artist}")
                    matchedSong = first
                    matchedSource = source
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "fetchOnlineCoverAndLyrics: source=$source search failed: ${e.message}")
                }
            }

            if (matchedSong == null) {
                Log.d(TAG, "fetchOnlineCoverAndLyrics: no match found for '$query'")
                return@launch
            }
            if (isStale(playToken)) return@launch

            val match = matchedSong
            // ① 用搜索结果的封面补全
            var newCoverUrl = song.coverUrl
            if (needCover && match.coverUrl.isNotBlank()) {
                newCoverUrl = match.coverUrl
                Log.d(TAG, "fetchOnlineCoverAndLyrics: cover from $matchedSource")
            }

            // ② 用插件系统获取歌词（失败时回退到直接平台 API）
            var newLrcText = song.lrcText
            var directLyricLines: List<LyricLine>? = null
            if (needLyrics) {
                // ②-a 插件系统获取歌词
                try {
                    val lyricResult = withTimeoutOrNull(8000L) {
                        LxPluginEngine.getLyric(getApplication(), matchedSource, match, 8000L)
                    }
                    if (lyricResult != null && lyricResult.lyric.isNotBlank()) {
                        newLrcText = lyricResult.lyric
                        Log.d(TAG, "fetchOnlineCoverAndLyrics: lyric(plugin) from $matchedSource, len=${lyricResult.lyric.length}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "fetchOnlineCoverAndLyrics: getLyric(plugin) failed: ${e.message}")
                }
                // ②-b 插件未返回歌词时，回退到直接平台 API（lxmusic.js 可能不支持 lyric action）
                if (newLrcText.isBlank() && match.platformId.isNotBlank()) {
                    try {
                        val directLyrics = withTimeoutOrNull(8000L) {
                            MusicApiService.fetchLyricsDirect(
                                match.platform, match.platformId, match.title,
                                (match.duration / 1000).toInt(),
                            )
                        }
                        if (directLyrics != null && directLyrics.isNotEmpty()) {
                            // fetchLyricsDirect 返回已解析的 LyricLine 列表，需转回 LRC 文本
                            // 直接复用解析结果，无需转文本再解析
                            Log.d(TAG, "fetchOnlineCoverAndLyrics: lyric(direct) from ${match.platform}, lines=${directLyrics.size}")
                            // 用已解析结果直接更新（在 ③ 中统一处理）
                            directLyricLines = directLyrics
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "fetchOnlineCoverAndLyrics: getLyric(direct) failed: ${e.message}")
                    }
                }
            }

            if (isStale(playToken)) return@launch

            // ③ 更新 currentSong 并刷新通知栏封面与歌词
            val hasCoverUpdate = newCoverUrl != song.coverUrl
            val hasLrcUpdate = newLrcText != song.lrcText || directLyricLines != null
            if (hasCoverUpdate || hasLrcUpdate) {
                val updated = song.copy(coverUrl = newCoverUrl, lrcText = newLrcText)
                currentSong = updated
                // 同步更新 playlist 中的对应项
                val idx = playlist.indexOfFirst { it.platformId == song.platformId && it.platform == song.platform }
                if (idx >= 0) {
                    playlist = playlist.toMutableList().also { it[idx] = updated }
                }
                // 刷新通知栏封面
                if (hasCoverUpdate) {
                    loadCoverToService(newCoverUrl)
                }
                // 刷新歌词显示
                if (hasLrcUpdate) {
                    if (directLyricLines != null && directLyricLines.isNotEmpty()) {
                        // 直接用已解析结果（fetchLyricsDirect 返回的）
                        lyrics = directLyricLines
                        currentLyricIndex = 0
                        cachedLyricsData = MusicApiService.lastParsedLyricsData
                        Log.d(TAG, "fetchOnlineCoverAndLyrics: lyrics updated(direct), ${directLyricLines.size} lines")
                    } else if (newLrcText != song.lrcText) {
                        try {
                            val parsed = MusicApiService.parseLrc(newLrcText)
                            if (parsed.isNotEmpty()) {
                                lyrics = parsed
                                currentLyricIndex = 0
                                cachedLyricsData = MusicApiService.lastParsedLyricsData
                                Log.d(TAG, "fetchOnlineCoverAndLyrics: lyrics updated, ${parsed.size} lines")
                            }
                        } catch (_: Exception) { /* ignore */ }
                    }
                }
                Log.d(TAG, "fetchOnlineCoverAndLyrics: song '${song.title}' updated")
            }
        }
    }

    /** 判断播放 token 是否已过期（用户切歌） */
    private fun isStale(token: Long): Boolean = token != playRequestToken

    fun togglePlayPause() {
        if (playerReleased) return
        if (player.isPlaying) {
            player.pause()
        } else {
            when (player.playbackState) {
                Player.STATE_ENDED -> {
                    // 播放完毕，回到起点重新播放
                    player.seekTo(0)
                    player.play()
                }
                Player.STATE_IDLE -> {
                    // 播放器已停止/未准备，重新播放当前歌曲
                    currentSong?.let { playSong(it) }
                }
                else -> {
                    player.play()
                }
            }
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        if (playlist.size == 1) {
            showToast("当前只有一首歌")
            return
        }
        val oldIndex = currentIndex
        currentIndex = when (playMode) {
            // 手动点击“下一首”时，单曲循环也应切换到下一首
            PlayMode.LOOP, PlayMode.SINGLE -> (currentIndex + 1) % playlist.size
            PlayMode.SHUFFLE -> {
                var next = currentIndex
                while (next == currentIndex && playlist.size > 1) {
                    next = (0 until playlist.size).random()
                }
                next
            }
        }
        Log.d(TAG, "playNext: mode=$playMode, oldIndex=$oldIndex, newIndex=$currentIndex, queueSize=${playlist.size}")
        playlist.getOrNull(currentIndex)?.let { playSong(it) }
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        if (playlist.size == 1) {
            showToast("当前只有一首歌")
            return
        }
        val oldIndex = currentIndex
        currentIndex = when (playMode) {
            // 手动点击“上一首”时，单曲循环也应切换到上一首
            PlayMode.LOOP, PlayMode.SINGLE -> (currentIndex - 1 + playlist.size) % playlist.size
            PlayMode.SHUFFLE -> {
                var prev = currentIndex
                while (prev == currentIndex && playlist.size > 1) {
                    prev = (0 until playlist.size).random()
                }
                prev
            }
        }
        Log.d(TAG, "playPrevious: mode=$playMode, oldIndex=$oldIndex, newIndex=$currentIndex, queueSize=${playlist.size}")
        playlist.getOrNull(currentIndex)?.let { playSong(it) }
    }

    fun seekTo(progress: Float) {
        if (playerReleased) return
        this.progress = progress
        val position = (progress * player.duration).toLong()
        player.seekTo(position)
    }

    fun changePlayMode() {
        playMode = when (playMode) {
            PlayMode.LOOP -> PlayMode.SINGLE
            PlayMode.SINGLE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LOOP
        }
        LocalStorage.savePlayMode(playMode.name)
    }

    // ── 音质 ──
    fun changeQuality(quality: MusicApiConfig.Quality) {
        selectedQuality = quality
        LocalStorage.saveQuality(quality.name)
        currentSong?.let { playSong(it) }
    }

    fun toggleDarkMode() {
        val context = getApplication<Application>().applicationContext
        ThemeManager.toggleDarkMode(context)
    }

    // ── 开发者模式 ──
    fun toggleDevMode() {
        isDevMode = !isDevMode
        LocalStorage.saveDevMode(isDevMode)
    }

    // ── 歌词 ──
    private fun loadLyrics(song: Song, playToken: Long? = null) {
        val token = playToken ?: ++lyricsRequestToken
        if (playToken != null) {
            lyricsRequestToken = playToken
        }
        val platform = song.platform
        // 酷狗歌词 API 需要 FileHash 而非数字 ID（与 lx-music-mobile kg/lyric.js 一致）
        val songId = if (platform == "酷狗" || platform == "酷狗音乐" || platform == "kugou" || platform == "kg") {
            extractKgHash(song) ?: song.platformId
        } else {
            song.platformId
        }

        viewModelScope.launch {
            Log.d(TAG, "loadLyrics start: song=${song.title}, platform=$platform, songId=$songId, token=$token")
            var fetchedLyrics: List<LyricLine> = emptyList()
            var tlyricRaw: String = ""

            fun isStale() = token != lyricsRequestToken

            try {
                // ① 本地最快: 搜索结果自带的 lrcText (零网络)
                if (song.lrcText.isNotBlank()) {
                    try {
                        val parsed = MusicApiService.parseLrc(song.lrcText)
                        if (parsed.isNotEmpty()) {
                            fetchedLyrics = parsed
                            lyrics = fetchedLyrics
                            currentLyricIndex = 0
                            Log.d(TAG, "loadLyrics ① lrcText: ${parsed.size} lines")
                        }
                    } catch (_: Exception) { /* ignore */ }
                }
                if (isStale()) return@launch

                // ② 插件模式: 通过 LxPluginEngine 调用插件获取歌词+翻译 (8s超时)
                if (apiMode == "lx_plugin" && lxSelectedSource.isNotBlank()) {
                    try {
                        val source = effectiveLxSource(song)
                        if (LxPluginEngine.isSourceSupported(source)) {
                            val pluginResult = withTimeoutOrNull(8000L) {
                                LxPluginEngine.getLyric(getApplication(), source, song, 8000L)
                            }
                            if (isStale()) return@launch
                            if (pluginResult != null && pluginResult.lyric.isNotBlank()) {
                                val parsed = MusicApiService.parseLrc(pluginResult.lyric)
                                if (parsed.isNotEmpty()) {
                                    fetchedLyrics = parsed
                                    tlyricRaw = pluginResult.tlyric
                                    lyrics = fetchedLyrics
                                    currentLyricIndex = 0
                                    Log.d(TAG, "loadLyrics ② plugin: ${parsed.size} lines")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "loadLyrics ② plugin failed: ${e.message}")
                    }
                }
                if (isStale()) return@launch

                // ③ 直接调用平台API获取歌词 (不经服务器, 更快更稳, 8s超时)
                if (fetchedLyrics.isEmpty() && songId.isNotBlank()) {
                    try {
                        
                        val directLyrics = withTimeoutOrNull(8000L) {
                            MusicApiService.fetchLyricsDirect(platform, songId, song.title, (song.duration / 1000).toInt())
                        } ?: emptyList()
                        if (isStale()) return@launch
                        if (directLyrics.isNotEmpty()) {
                            fetchedLyrics = directLyrics
                            lyrics = fetchedLyrics
                            currentLyricIndex = 0
                            Log.d(TAG, "loadLyrics ③ direct: ${directLyrics.size} lines")
                        } else {
                            Log.d(TAG, "loadLyrics ③ direct returned empty")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "loadLyrics ③ direct failed: ${e.message}")
                    }
                }
                if (isStale()) return@launch

                // ④ 服务器歌词API备用 (5s超时, 服务器可能慢)
                if (fetchedLyrics.isEmpty() && songId.isNotBlank()) {
                    try {
                        Log.d(TAG, "loadLyrics ④ server API: platform=$platform")
                        val serverLyrics = withTimeoutOrNull(5000L) {
                            MusicApiService.fetchLyrics(platform, songId)
                        } ?: emptyList()
                        if (isStale()) return@launch
                        if (serverLyrics.isNotEmpty()) {
                            fetchedLyrics = serverLyrics
                            lyrics = fetchedLyrics
                            currentLyricIndex = 0
                            Log.d(TAG, "loadLyrics ④ server: ${serverLyrics.size} lines")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "loadLyrics ④ server failed: ${e.message}")
                    }
                }
                if (isStale()) return@launch

                // ⑤ 搜索回退已移除（内置搜索API已删除），歌词仅依赖服务器API与直接拉取
                if (isStale()) return@launch

                // 合并翻译歌词
                if (tlyricRaw.isNotBlank() && fetchedLyrics.isNotEmpty()) {
                    try {
                        val tmap = MusicApiService.parseLrcToMap(tlyricRaw)
                        if (tmap.isNotEmpty()) {
                            fetchedLyrics = MusicApiService.mergeLyricTranslation(fetchedLyrics, tmap)
                            Log.d(TAG, "合并翻译歌词: ${tmap.size} 行")
                        }
                    } catch (_: Exception) { /* ignore */ }
                }

                if (isStale()) {
                    Log.d(TAG, "loadLyrics stale at end: token=$token latest=$lyricsRequestToken")
                    return@launch
                }
                lyrics = fetchedLyrics
                cachedLyricsData = MusicApiService.lastParsedLyricsData
                currentLyricIndex = 0
                isLoadingLyrics = false
                Log.d(TAG, "loadLyrics done: song=${song.title}, lines=${fetchedLyrics.size}")

            } catch (e: Exception) {
                Log.e(TAG, "loadLyrics unexpected error: ${e.message}", e)
                isLoadingLyrics = false
            }
        }
    }

    private fun updateLyricIndex() {
        if (lyrics.isEmpty()) return
        // 应用歌词同步偏移：正值延后，负值提前（对逐字与逐行、横屏与悬浮窗同时生效）
        val currentTime = (player.currentPosition + lyricSyncOffsetMs.toLong()).coerceAtLeast(0L)
        currentPlaybackTimeMs = currentTime

        val ld = cachedLyricsData
        if (ld != null && ld.isWordByWord && ld.linesWithWords != null) {
            val syncResult = lyricsApi.syncLyrics(ld, currentTime.toInt(), 3)
            val currentLine = syncResult.currentLine
            if (currentLine != null) {
                val idx = ld.linesWithWords.indexOf(currentLine)
                if (idx >= 0) currentLyricIndex = idx
            }
        } else {
            currentLyricIndex = lyrics.indexOfLast { it.timeMs <= currentTime }.coerceAtLeast(0)
        }
    }

    private fun updateFloatingLyrics() {
        val curText = lyrics.getOrNull(currentLyricIndex)?.text ?: ""
        val nxtText = lyrics.getOrNull(currentLyricIndex + 1)?.text ?: ""
        floatingLyricsText = curText
        // 始终同步当前歌词到车载歌词共享状态（供通知栏标题位/车机蓝牙 AVRCP 读取），不依赖悬浮窗是否激活
        CarLyricHolder.currentLine = curText
        // 悬浮窗未激活时无需推送 overlay
        if (!FloatingLyricsService.isActive) return
        // 同步到悬浮歌词服务
        FloatingLyricsService.currentLyricText = curText
        FloatingLyricsService.nextLyricText = nxtText
        FloatingLyricsService.isPlaying = isPlaying
        FloatingLyricsService.lyricColor = floatingLyricsColor
        FloatingLyricsService.lyricSize = floatingLyricSize.toFloat()
    }

    fun toggleFloatingLyrics() {
        Log.d(TAG, "toggleFloatingLyrics: current=$floatingLyricsEnabled, canDraw=${canDrawOverlays()}, isActive=${FloatingLyricsService.isActive}")
        showToast("悬浮歌词: enabled=$floatingLyricsEnabled, active=${FloatingLyricsService.isActive}")
        if (!floatingLyricsEnabled) {
            // 开启
            if (!canDrawOverlays()) {
                showToast("请先授予悬浮窗权限，授权后返回应用将自动开启")
                openOverlayPermissionSettings()
                floatingLyricsEnabled = true
                LocalStorage.saveFloatingLyricEnabled(true)
                return
            }
            floatingLyricsEnabled = true
            LocalStorage.saveFloatingLyricEnabled(true)
        } else {
            // 关闭
            floatingLyricsEnabled = false
            LocalStorage.saveFloatingLyricEnabled(false)
        }
        reevaluateOverlayState()
    }

    /** 从 onResume 调用：检查权限并按当前开关状态评估悬浮歌词 */
    fun tryStartFloatingLyricsIfNeeded() {
        if (floatingLyricsEnabled && !canDrawOverlays()) {
            // 仍无权限，关闭通用开关
            floatingLyricsEnabled = false
            LocalStorage.saveFloatingLyricEnabled(false)
        }
        reevaluateOverlayState()
    }

    /**
     * 车载歌词总开关：手动切换。不随蓝牙连接/断开自动变化。
     * 开启时：通知栏播放器标题位与车机蓝牙（AVRCP）显示实时歌词；
     * 后台另以悬浮窗显示（需悬浮窗权限，可在设置面板内授权）。
     */
    fun toggleCarBtLyrics() {
        carBtLyricsEnabled = !carBtLyricsEnabled
        LocalStorage.saveCarBtLyricsEnabled(carBtLyricsEnabled)
        CarLyricHolder.enabled = carBtLyricsEnabled
        Log.d(TAG, "toggleCarBtLyrics: enabled=$carBtLyricsEnabled")
        // 立即刷新通知栏标题位：开启时显示当前歌词行，关闭时恢复歌名
        MusicPlaybackService.instance?.refreshNotification()
        reevaluateOverlayState()
    }

    /** 设置歌词同步偏移(ms)，自动钳制到 [-5000, 5000] */
    fun updateLyricSyncOffsetMs(ms: Int) {
        val clamped = ms.coerceIn(-5000, 5000)
        lyricSyncOffsetMs = clamped
        LocalStorage.saveLyricSyncOffset(clamped)
    }

    /** 横屏车载歌词字体大小（响应式） */
    fun updateCarLyricFontSize(value: Int) {
        carLyricFontSize = value
        LocalStorage.saveCarLyricFontSize(value)
    }

    /** 横屏车载歌词背景透明度（响应式） */
    fun updateCarLyricBgOpacity(value: Int) {
        carLyricBgOpacity = value
        LocalStorage.saveCarLyricBgOpacity(value)
    }

    /** 公开入口：打开悬浮窗权限授权页（供设置面板"去授权"按钮调用） */
    fun requestOverlayPermission() = openOverlayPermissionSettings()

    /**
     * 悬浮窗显示状态的唯一仲裁者：综合"通用悬浮歌词开关"、"车载蓝牙歌词开关+前后台"、
     * "悬浮窗权限"三者决定是否启动/停止 FloatingLyricsService。
     * 所有开关变化与前后台切换都应调用本函数，避免多处直接 start/stop 导致冲突。
     *
     * 优先级：车载开启+前台 → 横屏大字歌词接管，抑制悬浮窗（即便通用开关也开着）；
     * 后台 → 通用或车载任一开启即显示悬浮窗。
     */
    private fun reevaluateOverlayState() {
        val hasPerm = canDrawOverlays()
        val carWantsOverlay = carBtLyricsEnabled && !isAppForegrounded
        val genericWantsOverlay = floatingLyricsEnabled
        val suppressByCarForeground = carBtLyricsEnabled && isAppForegrounded
        val desired = hasPerm && (genericWantsOverlay || carWantsOverlay) && !suppressByCarForeground
        if (desired && !FloatingLyricsService.isActive) {
            startOverlayServiceInternal()
        } else if (!desired && FloatingLyricsService.isActive) {
            stopFloatingLyricsService()
        }
    }

    /**
     * 启动悬浮歌词服务（内部）：仅负责绑定回调与启停服务，不修改任何开关状态/持久化。
     * 开关状态由各 toggle 函数自行维护，避免车载场景误写通用开关。
     */
    private fun startOverlayServiceInternal() {
        FloatingLyricsService.onPlayPause = { togglePlayPause() }
        FloatingLyricsService.onPrevious = { playPrevious() }
        FloatingLyricsService.onNext = { playNext() }
        try {
            val intent = Intent(getApplication(), FloatingLyricsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动悬浮歌词失败", e)
            showToast("悬浮歌词启动失败: ${e.message}")
        }
    }

    private fun stopFloatingLyricsService() {
        FloatingLyricsService.onPlayPause = null
        FloatingLyricsService.onPrevious = null
        FloatingLyricsService.onNext = null
        try {
            getApplication<Application>().stopService(
                Intent(getApplication(), FloatingLyricsService::class.java)
            )
        } catch (e: Exception) {
            Log.e(TAG, "停止悬浮歌词失败", e)
        }
    }

    private fun canDrawOverlays(): Boolean {
        val app = getApplication<Application>()
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(app)
    }

    private fun openOverlayPermissionSettings() {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${app.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开悬浮窗权限设置失败", e)
        }
    }

    fun changeFloatingLyricsColor(color: Int) {
        floatingLyricsColor = color
        LocalStorage.saveFloatingLyricColor(color)
    }

    fun changeHighlightLyricColor(color: Int) {
        highlightLyricColor = color
        LocalStorage.saveLyricCurrentColor(color)
    }

    fun changeNormalLyricColor(color: Int) {
        normalLyricColor = color
        LocalStorage.saveLyricNormalColor(color)
    }

    fun changePlayerLyricSize(size: Int) {
        playerLyricSize = size
        LocalStorage.saveLyricFontSize(size)
    }

    fun changeFloatingLyricSize(size: Int) {
        floatingLyricSize = size
        LocalStorage.saveFloatingLyricSize(size)
    }

    /** Check if the currently selected plugin+source supports the given action. */
    private fun lxSupportsAction(action: String): Boolean {
        if (lxSelectedSource.isBlank()) return false
        if (lxSelectedSource == LX_SOURCE_ALL) {
            return lxPlugins.any { plugin ->
                isPluginEnabled(plugin.id) && plugin.sources.any { src ->
                    isSourceEnabled(plugin.id, src) &&
                        lxPluginManager.sourceSupportsAction(plugin.id, src, action)
                }
            }
        }
        // 单插件模式下，仅当前启用的插件参与判定，规避禁用的内置插件被后台调用
        if (lxSelectedPluginId.isBlank()) return false
        if (!isPluginEnabled(lxSelectedPluginId)) return false
        return lxPluginManager.sourceSupportsAction(lxSelectedPluginId, lxSelectedSource, action)
    }

    /**
     * Map a display-name platform (e.g. "QQ音乐") back to a server API platform key (e.g. "qq").
     * If the value is already an API key it is returned as-is.
     */
    private fun resolveApiPlatform(platform: String): String {
        return when (platform) {
            "QQ音乐", "qq" -> "qq"
            "网易云", "网易云2", "网易云音乐", "netease" -> "netease"
            "酷我音乐", "kuwo" -> "kuwo"
            "酷狗音乐", "kugou" -> "kugou"
            "咪咕音乐" -> "migu"
            "抖音", "汽水音乐" -> "douyin"
            else -> platform
        }
    }

    /**
     * 从 pluginRawJson 提取酷狗 FileHash（与 lx-music-mobile kg/lyric.js 一致）
     * 酷狗歌词 API 搜索需要 hash 而非数字 ID
     */
    private fun extractKgHash(song: Song): String? {
        if (song.pluginRawJson.isBlank()) return null
        return try {
            val hash = org.json.JSONObject(song.pluginRawJson).optString("hash", null)
            Log.d(TAG, "extractKgHash: pluginRawJson hash=${hash?.take(8)}..., songId=${song.platformId}")
            hash
        } catch (_: Exception) { null }
    }

    /**
     * Map a display-name platform (e.g. "QQ音乐") to a LX plugin source key (e.g. "tx").
     * Returns empty string if no mapping found.
     */
    private fun platformToLxSource(platform: String): String {
        return when (platform) {
            "QQ音乐", "QQ", "qq" -> "tx"
            "网易云", "网易云2", "网易云音乐", "netease" -> "wy"
            "酷我音乐", "酷我", "kuwo" -> "kw"
            "酷狗音乐", "酷狗", "kugou" -> "kg"
            "咪咕音乐", "咪咕" -> "mg"
            "汽水音乐", "抖音", "douyin" -> "qsvip"
            else -> ""
        }
    }

    /** Resolve the effective LX source key for a song: prefer stored key, then infer from platform name, finally fallback to global selection. */
    private fun effectiveLxSource(song: Song): String {
        return song.lxSourceKey.ifBlank { platformToLxSource(song.platform).ifBlank { lxSelectedSource } }
    }

    private fun proxyOrDirectUrl(url: String, headers: Map<String, String>): String {
        val allowedHeaders = setOf("referer", "user-agent", "cookie", "origin")
        val filteredHeaders = headers.filterKeys { it.lowercase() in allowedHeaders }
        if (filteredHeaders.isNotEmpty() && filteredHeaders.keys.any { it.lowercase() != "user-agent" }) {
            if (!audioProxy.isRunning()) audioProxy.start()
            val proxyUrl = audioProxy.register(url, filteredHeaders)
            Log.d(TAG, "proxyOrDirectUrl: PROXY realUrl=${url.take(60)}, headers=$filteredHeaders")
            return proxyUrl
        }
        httpDataSourceFactory.setDefaultRequestProperties(filteredHeaders.ifEmpty {
            mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        })
        return url
    }

    // ── 搜索 ──
    fun search(keyword: String, page: Int = 1) {
        if (keyword.isBlank()) return
        // "全部"平台走多平台合并搜索
        if (page == 1 && (currentSearchPlatform == "全部" || currentSearchPlatform.isBlank())) {
            performAllPlatformSearch(keyword)
            return
        }
        viewModelScope.launch {
            isSearching = true
            try {
                if (page == 1) {
                    searchResults = emptyList()
                    currentSearchPage = 1
                }
                // 使用内置 lx-music-mobile 搜索算法（落雪插件仅用于解析播放链接，不支持搜索）
                val platform = if (currentSearchPlatform.isNotBlank()) currentSearchPlatform else "网易云"
                val results = withTimeoutOrNull(15_000L) {
                    searchByPlugin(platform, keyword, page)
                } ?: emptyList()
                searchResults = if (page == 1) results else searchResults + results
                hasMoreResults = results.size >= searchPageSize
                currentSearchPage = page
                hasSearched = true
                addSearchHistory(keyword)
            } catch (e: Exception) {
                Log.e(TAG, "搜索失败", e)
            } finally {
                isSearching = false
            }
        }
    }

    /**
     * 搜索音乐（供UI调用）
     */
    fun searchMusic(keyword: String) {
        search(keyword, page = 1)
    }

    /**
     * Search all enabled plugins' enabled sources in parallel and merge/interleave results.
     * Each song retains its own lxSourceKey so playback uses the correct source.
     * Uses per-source timeout (8s) so one slow source doesn't block everything.
     */
    private suspend fun searchAllSources(keyword: String): List<Song> = coroutineScope {
        val perSourceTimeout = 8000L
        val pluginSourcePairs = lxPlugins
            .filter { isPluginEnabled(it.id) }
            .flatMap { plugin -> plugin.sources.filter { isSourceEnabled(plugin.id, it) }.map { plugin.id to it } }
        Log.d(TAG, "searchAllSources: keyword=$keyword, pairs=${pluginSourcePairs.map { "${it.first.take(8)}:${it.second}" }}")
        val jobs = pluginSourcePairs.map { (pluginId, source) ->
            async(Dispatchers.IO) {
                try {
                    if (!lxPluginManager.sourceSupportsAction(pluginId, source, "search")) {
                        return@async emptyList<Song>()
                    }
                    withTimeoutOrNull(perSourceTimeout) {
                        lxPluginManager.search(pluginId, source, keyword, perSourceTimeout).songs
                    } ?: run {
                        Log.w(TAG, "searchAllSources: plugin=${pluginId.take(8)} source=$source TIMEOUT")
                        emptyList<Song>()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "searchAllSources: plugin=${pluginId.take(8)} source=$source failed: ${e.message}")
                    emptyList<Song>()
                }
            }
        }
        val allResults = jobs.awaitAll()
        // Interleave results: take turns from each source for variety
        val iterators = allResults.filter { it.isNotEmpty() }.map { it.iterator() }.toMutableList()
        val merged = mutableListOf<Song>()
        while (iterators.isNotEmpty()) {
            val it = iterators.iterator()
            while (it.hasNext()) {
                val iter = it.next()
                if (iter.hasNext()) {
                    merged.add(iter.next())
                } else {
                    it.remove()
                }
            }
        }
        Log.d(TAG, "searchAllSources: merged ${merged.size} results from ${pluginSourcePairs.size} plugin-source pairs")
        merged
    }

    fun loadMoreSearchResults(keyword: String) {
        if (!isSearching && hasMoreResults) {
            search(keyword, currentSearchPage + 1)
        }
    }

    fun clearSearchResults() {
        searchResults = emptyList()
        currentSearchPage = 1
        hasMoreResults = true
        hasSearched = false
    }

    private fun addSearchHistory(keyword: String) {
        if (keyword.isBlank()) return
        searchHistory = listOf(keyword) + searchHistory.filter { it != keyword }.take(19)
        LocalStorage.saveSearchHistory(searchHistory)
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
        LocalStorage.saveSearchHistory(emptyList())
    }

    fun getSearchSuggestionsFromHistory(keyword: String): List<String> {
        if (keyword.isBlank()) return emptyList()
        // 使用本地搜索历史做简单联想
        return searchHistory.filter {
            it.contains(keyword, ignoreCase = true)
        }.take(10)
    }

    // ── 歌单 ──
    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        val newPlaylist = Playlist(
            id = System.currentTimeMillis(),
            name = name,
            coverUrl = "",
            songCount = 0,
            playCount = 0,
            creator = userName
        )
        myPlaylists = myPlaylists + newPlaylist
        LocalStorage.savePlaylists(myPlaylists)
    }

    fun deletePlaylist(playlistId: Long) {
        myPlaylists = myPlaylists.filter { it.id != playlistId }
        LocalStorage.savePlaylists(myPlaylists)
    }

    fun addToPlaylist(playlistId: Long, song: Song) {
        val playlist = myPlaylists.find { it.id == playlistId } ?: return
        val mergedSongs = (playlist.songs + song).distinctBy { "${it.platformId}:${it.platform}" }
        val updatedPlaylist = playlist.copy(
            songs = mergedSongs,
            songCount = mergedSongs.size,
            coverUrl = playlist.coverUrl.ifBlank { mergedSongs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty() },
        )
        myPlaylists = myPlaylists.map { if (it.id == playlistId) updatedPlaylist else it }
        LocalStorage.savePlaylists(myPlaylists)
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        val playlist = myPlaylists.find { it.id == playlistId } ?: return
        val remainingSongs = playlist.songs.filter { it.id != songId }
        val updatedPlaylist = playlist.copy(
            songs = remainingSongs,
            songCount = remainingSongs.size,
            coverUrl = remainingSongs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty(),
        )
        myPlaylists = myPlaylists.map { if (it.id == playlistId) updatedPlaylist else it }
        LocalStorage.savePlaylists(myPlaylists)
    }
    private fun normalizeCoverUrl(raw: String?): String {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        if (value.equals("null", ignoreCase = true) || value.equals("undefined", ignoreCase = true)) return ""
        value = value.replace("\\/", "/").replace("&amp;", "&").trim()
        if (value.startsWith("//")) value = "https:$value"
        return if (
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("data:image", ignoreCase = true)
        ) value else ""
    }

    private fun firstCoverUrl(vararg candidates: String?): String {
        for (candidate in candidates) {
            val normalized = normalizeCoverUrl(candidate)
            if (normalized.isNotEmpty()) return normalized
        }
        return ""
    }

    fun importPlaylist(json: String): Boolean {
        return try {
            val jsonObj = org.json.JSONObject(json)
            val songsArr = jsonObj.optJSONArray("songs")
            val songs = mutableListOf<Song>()
            if (songsArr != null) {
                for (i in 0 until songsArr.length()) {
                    val s = songsArr.getJSONObject(i)
                    songs.add(Song(
                        id = s.optLong("id", System.currentTimeMillis() + i),
                        title = s.optString("title", ""),
                        artist = s.optString("artist", "未知"),
                        album = s.optString("album", ""),
                        coverUrl = firstCoverUrl(
                            s.optString("coverUrl", ""),
                            s.optString("cover", ""),
                            s.optString("picUrl", ""),
                            s.optString("pic", ""),
                            s.optString("imgUrl", ""),
                            s.optString("img", ""),
                        ),
                        platform = s.optString("platform", ""),
                        platformId = s.optString("platformId", ""),
                    ))
                }
            }
            val playlist = Playlist(
                id = jsonObj.optLong("id", System.currentTimeMillis()),
                name = jsonObj.optString("name", "未知歌单"),
                coverUrl = firstCoverUrl(
                    jsonObj.optString("coverUrl", ""),
                    jsonObj.optString("cover", ""),
                    jsonObj.optString("picUrl", ""),
                    songs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl,
                ),
                songCount = songs.size,
                playCount = jsonObj.optLong("playCount", 0),
                creator = jsonObj.optString("creator", ""),
                songs = songs,
            )
            myPlaylists = myPlaylists + playlist
            LocalStorage.savePlaylists(myPlaylists)
            true
        } catch (e: Exception) {
            Log.e(TAG, "导入歌单失败", e)
            false
        }
    }

    fun exportAllPlaylists(): String {
        return try {
            val arr = org.json.JSONArray()
            myPlaylists.forEach { pl ->
                arr.put(org.json.JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("coverUrl", pl.coverUrl)
                    put("songCount", pl.songCount)
                    val songsArr = org.json.JSONArray()
                    pl.songs.forEach { s ->
                        songsArr.put(org.json.JSONObject().apply {
                            put("id", s.id)
                            put("title", s.title)
                            put("artist", s.artist)
                            put("album", s.album)
                            put("coverUrl", s.coverUrl)
                            put("platform", s.platform)
                            put("platformId", s.platformId)
                        })
                    }
                    put("songs", songsArr)
                })
            }
            arr.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "导出歌单失败", e)
            "[]"
        }
    }

    // ── 收藏 ──
    fun toggleFavorite(song: Song) {
        if (favoriteSongs.any { it.id == song.id }) {
            favoriteSongs = favoriteSongs.filter { it.id != song.id }
        } else {
            favoriteSongs = favoriteSongs + song
        }
        LocalStorage.saveFavorites(favoriteSongs)
    }

    fun isFavorite(songId: Long): Boolean {
        return favoriteSongs.any { it.id == songId }
    }

    private fun addToRecent(song: Song) {
        recentSongs = listOf(song) + recentSongs.filter { it.id != song.id }.take(99)
        LocalStorage.savePlayHistory(recentSongs)
    }

    fun clearRecentSongs() {
        recentSongs = emptyList()
        LocalStorage.savePlayHistory(recentSongs)
    }

    fun exportFavorites(): String {
        return try {
            val arr = org.json.JSONArray()
            favoriteSongs.forEach { s ->
                arr.put(org.json.JSONObject().apply {
                    put("id", s.id); put("title", s.title); put("artist", s.artist)
                    put("album", s.album); put("coverUrl", s.coverUrl)
                })
            }
            arr.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "导出收藏失败", e)
            "[]"
        }
    }

    fun importFavorites(json: String): Boolean {
        return try {
            val arr = org.json.JSONArray(json)
            val songs = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Song(
                    id = obj.optLong("id", System.currentTimeMillis() + i),
                    title = obj.optString("title", ""),
                    artist = obj.optString("artist", ""),
                    album = obj.optString("album", ""),
                    coverUrl = firstCoverUrl(
                        obj.optString("coverUrl", ""),
                        obj.optString("cover", ""),
                        obj.optString("picUrl", ""),
                        obj.optString("pic", ""),
                    ),
                )
            }
            favoriteSongs = (favoriteSongs + songs).distinctBy { it.id }
            LocalStorage.saveFavorites(favoriteSongs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "导入收藏失败", e)
            false
        }
    }

    // ── 云同步: 批量导入 ──

    /** 从云端恢复歌单列表 */
    fun importPlaylists(imported: List<Playlist>) {
        val existingIds = myPlaylists.map { it.id }.toSet()
        val newPlaylists = imported.filter { it.id !in existingIds }
        myPlaylists = myPlaylists + newPlaylists
        LocalStorage.savePlaylists(myPlaylists)
        Log.d(TAG, "云同步: 导入 ${newPlaylists.size} 个歌单")
    }

    /** 从云端恢复收藏歌曲 */
    fun importFavorites(songs: List<Song>) {
        favoriteSongs = (favoriteSongs + songs).distinctBy { it.id }
        LocalStorage.saveFavorites(favoriteSongs)
        Log.d(TAG, "云同步: 导入 ${songs.size} 首收藏")
    }

    /** 从云端恢复播放历史 */
    fun importPlayHistory(songs: List<Song>) {
        val existingIds = recentSongs.map { it.id }.toSet()
        val newSongs = songs.filter { it.id !in existingIds }
        recentSongs = (newSongs + recentSongs).take(200)
        LocalStorage.savePlayHistory(recentSongs)
        Log.d(TAG, "云同步: 导入 ${newSongs.size} 首播放历史")
    }

    // ── 登录 ──
    fun login(token: String, name: String, id: String) {
        userToken = token
        userName = name
        userId = id
        isLoggedIn = true
        LocalStorage.saveUserAuth(token, name, id.toIntOrNull() ?: 0)
    }

    fun logout() {
        userToken = ""
        userName = ""
        userId = ""
        isLoggedIn = false
        LocalStorage.clearUserAuth()
    }

    // ── API配置 ──
    fun updateApiMode(mode: String) {
        apiMode = mode
        LocalStorage.saveApiMode(mode)
    }

    fun updateApiHost(host: String) {
        apiHost = host
        LocalStorage.saveApiHost(host)
    }

    fun updateShareBaseUrl(url: String) {
        shareBaseUrl = url
        LocalStorage.saveShareBaseUrl(url)
    }

    fun updateApiKey(platform: String, key: String) {
        when (platform) {
            "qq_music" -> { qqMusicApi = key; LocalStorage.saveQQMusicApiKey(key) }
            "netease" -> { neteaseApi = key; LocalStorage.saveNeteaseApiKey(key) }
            "kuwo" -> { kuwoApi = key; LocalStorage.saveKuwoApiKey(key) }
            "migu" -> { miguApi = key; LocalStorage.saveMiguApiKey(key) }
            "kugou" -> { kugouApi = key; LocalStorage.saveKugouApiKey(key) }
            "douyin" -> { douyinApi = key; LocalStorage.saveDouyinApiKey(key) }
        }
    }

    fun updateQQCookie(cookie: String) {
        qqCookie = cookie
        LocalStorage.saveQQCookie(cookie)
    }



    // ── 崩溃日志 ──
    fun addCrashLog(tag: String, throwable: Throwable) {
        CrashLogManager.logException(tag, throwable)
        crashLogs = CrashLogManager.getLogFiles()
    }

    // ── 均衡器 ──
    private fun initEqualizer() {
        try {
            val sessionId = player.audioSessionId
            equalizerEffect = Equalizer(0, sessionId)
            bassBoostEffect = BassBoost(0, sessionId)
            virtualizerEffect = Virtualizer(0, sessionId)
            reverbEffect = EnvironmentalReverb(0, sessionId)
            loudnessEnhancerEffect = LoudnessEnhancer(sessionId)

            equalizerBands = (0 until (equalizerEffect?.numberOfBands ?: 0)).map { i ->
                val freq = equalizerEffect?.getCenterFreq(i.toShort())?.div(1000) ?: 0
                freq to 0
            }
            applyPreset(currentPreset)
            toggleEqualizer(equalizerEnabled)

        } catch (e: Exception) {
            Log.e(TAG, "初始化均衡器失败", e)
        }
    }

    /**
     * 播放器 audioSessionId 变化时重新创建音效实例。
     * ExoPlayer 首次播放前 audioSessionId=0，此时创建的音效不会作用于播放器输出；
     * 播放后系统分配真实 sessionId，需要释放旧实例并重新绑定。
     */
    private fun reinitEqualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            // 释放旧实例
            equalizerEffect?.enabled = false
            bassBoostEffect?.enabled = false
            virtualizerEffect?.enabled = false
            reverbEffect?.enabled = false
            loudnessEnhancerEffect?.enabled = false
            equalizerEffect?.release()
            bassBoostEffect?.release()
            virtualizerEffect?.release()
            reverbEffect?.release()
            loudnessEnhancerEffect?.release()

            // 用真实 sessionId 重建
            equalizerEffect = Equalizer(0, audioSessionId)
            bassBoostEffect = BassBoost(0, audioSessionId)
            virtualizerEffect = Virtualizer(0, audioSessionId)
            reverbEffect = EnvironmentalReverb(0, audioSessionId)
            loudnessEnhancerEffect = LoudnessEnhancer(audioSessionId)

            equalizerBands = (0 until (equalizerEffect?.numberOfBands ?: 0)).map { i ->
                val freq = equalizerEffect?.getCenterFreq(i.toShort())?.div(1000) ?: 0
                freq to 0
            }
            // 重新应用当前预设和音效参数
            applyPreset(currentPreset)
            toggleEqualizer(equalizerEnabled)
            Log.d(TAG, "音效已重新绑定到 audioSessionId=$audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "重新初始化音效失败", e)
        }
    }

    fun setEqualizerBand(band: Int, level: Int) {
        try {
            val clamped = level.coerceIn(-15, 15)
            equalizerEffect?.setBandLevel(band.toShort(), (clamped * 100).toShort())
            equalizerBands = equalizerBands.mapIndexed { index, pair ->
                if (index == band) pair.first to clamped else pair
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置均衡器失败", e)
        }
    }

    fun toggleEqualizer(enabled: Boolean) {
        equalizerEnabled = enabled
        try {
            equalizerEffect?.enabled = enabled
            bassBoostEffect?.enabled = enabled && bassBoostStrength > 0
            virtualizerEffect?.enabled = enabled && virtualizerStrength > 0
            reverbEffect?.enabled = enabled && reverbLevel > 0
            loudnessEnhancerEffect?.enabled = enabled && loudnessGain > 0
        } catch (e: Exception) {
            Log.e(TAG, "切换均衡器失败", e)
        }
    }

    fun toggleBassBoost(enabled: Boolean) {
        bassBoostEnabled = enabled
        if (!enabled) updateBassBoost(0)
        else updateBassBoost(bassBoostStrength.coerceAtLeast(1))
    }

    fun applyBassBoostStrength(strength: Int) {
        val normalized = if (strength > 100) strength / 10 else strength
        updateBassBoost(normalized)
    }

    fun toggleVirtualizer(enabled: Boolean) {
        virtualizerEnabled = enabled
        if (!enabled) updateVirtualizer(0)
        else updateVirtualizer(virtualizerStrength.coerceAtLeast(1))
    }

    fun applyVirtualizerStrength(strength: Int) {
        val normalized = if (strength > 100) strength / 10 else strength
        updateVirtualizer(normalized)
    }

    // ── 定时停止 ──
    fun setSleepTimer(minutes: Int) {
        sleepTimerMinutes = minutes
        sleepTimerEnabled = minutes > 0
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                delay(minutes * 60 * 1000L)
                player.pause()
                sleepTimerEnabled = false
                sleepTimerMinutes = 0
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerEnabled = false
        sleepTimerMinutes = 0
    }

    // ── 播放队列管理 ──
    fun getQueue(): List<Song> = playlist

    fun removeFromQueue(song: Song) {
        val removedIndex = playlist.indexOfFirst { it.platformId == song.platformId && it.platform == song.platform }
        if (removedIndex < 0) return
        playlist = playlist.filterIndexed { idx, _ -> idx != removedIndex }
        // 调整 currentIndex 保持指向正确的歌曲
        if (playlist.isEmpty()) {
            currentIndex = 0
        } else if (removedIndex < currentIndex) {
            currentIndex = (currentIndex - 1).coerceAtLeast(0)
        } else if (removedIndex == currentIndex) {
            currentIndex = currentIndex.coerceAtMost(playlist.lastIndex)
        }
    }

    fun clearQueue() {
        player.stop()
        playlist = emptyList()
        currentIndex = 0
        currentSong = null
        isPlaying = false
    }

    // ── 播放历史 ──
    // playHistory 直接引用 recentSongs，避免两个字段不同步
    val playHistory: List<Song> get() = recentSongs

    fun removeFromHistory(song: Song) {
        recentSongs = recentSongs.filter { it.id != song.id }
        LocalStorage.savePlayHistory(recentSongs)
    }

    fun playAllFromList(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            playPlaylist(songs, 0)
        }
    }

    // ── 收藏/喜欢 ──
    fun toggleLike(song: Song) {
        toggleFavorite(song)
    }

    // ── 定时停止（兼容旧接口）──
    val isSleepTimerRunning: Boolean
        get() = sleepTimerEnabled

    val sleepTimerMs: Long
        get() = if (sleepTimerEnabled) sleepTimerMinutes * 60 * 1000L else 0L

    fun startSleepTimer(minutes: Int) {
        setSleepTimer(minutes)
    }

    // ── 下载页面 ──
    fun openDownloadPage() {
        val url = downloadPageUrl.trim()
        if (url.isEmpty()) { showToast("暂未配置下载地址"); return }
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            showToast("下载地址格式不正确")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Log.e("MusicViewModel", "打开下载页面失败", e)
            showToast("打开链接失败: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ═══ 插件音质定义 ═══
    // ═══════════════════════════════════════════════════════════

    /** LX plugin quality levels — ordered from lowest to highest. */
    enum class LxQuality(val key: String, val displayName: String) {
        Q128K("128k", "128K"),
        Q320K("320k", "320K"),
        FLAC("flac", "FLAC"),
        FLAC24BIT("flac24bit", "Hires 无损24-Bit"),
        WAV("wav", "臻品音质"),
        FLAC32BIT("flac32bit", "臻品音质 2.0"),
        MASTER("master", "臻品母带"),
        ATMOS("atmos", "沉浸环绕声"),
        ATMOS_PLUS("atmos_plus", "高清环绕声"),
        ;
        companion object {
            fun fromKey(key: String): LxQuality? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            /** For unknown keys from the plugin, create a display name from the key itself. */
            fun displayFor(key: String): String = fromKey(key)?.displayName ?: key.uppercase()
        }
    }

    /**
     * Get available download qualities for a song, extracted from its pluginRawJson types array.
     * Returns a list of (key, displayName) pairs.
     */
    fun getAvailableQualities(song: Song): List<Pair<String, String>> {
        if (song.pluginRawJson.isBlank()) {
            // No raw JSON — return default qualities
            return listOf(
                "128k" to "128K",
                "320k" to "320K",
                "flac" to "FLAC",
            )
        }
        return try {
            val raw = org.json.JSONObject(song.pluginRawJson)
            val typesArr = raw.optJSONArray("types") ?: return listOf(
                "128k" to "128K",
                "320k" to "320K",
                "flac" to "FLAC",
            )
            val result = mutableListOf<Pair<String, String>>()
            for (i in 0 until typesArr.length()) {
                val typeObj = typesArr.optJSONObject(i)
                val key = typeObj?.optString("type")?.takeIf { it.isNotBlank() }
                    ?: typesArr.optString(i).takeIf { it.isNotBlank() }
                    ?: continue
                result.add(key to LxQuality.displayFor(key))
            }
            // Sort by our defined order
            val order = LxQuality.entries.map { it.key }
            result.sortedBy { (k, _) -> order.indexOf(k).let { if (it < 0) 999 else it } }
        } catch (e: Exception) {
            listOf("128k" to "128K", "320k" to "320K", "flac" to "FLAC")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ═══ 插件音乐下载 ═══
    // ═══════════════════════════════════════════════════════════

    data class DownloadedSong(
        val song: Song,
        val filePath: String,
        val fileSize: Long,
        val platform: String,
        val downloadTime: Long = System.currentTimeMillis(),
    )

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val songId: String, val progress: Float = 0f) : DownloadState()
        data class Success(val songTitle: String) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    /** Whether a specific song has been downloaded. */
    fun isDownloaded(song: Song): Boolean {
        return downloadedSongs.any { it.song.platformId == song.platformId && it.song.platform == song.platform }
    }

    /**
     * Download a song using the LX plugin.
     * @param song The song to download (defaults to currently playing song)
     * @param qualityKey The LX quality key to download (e.g. "128k", "320k", "flac", "flac24bit").
     *                   If null, uses the currently selected global quality.
     */
    fun downloadSong(song: Song? = null, qualityKey: String? = null) {
        val target = song ?: currentSong
        if (target == null) { showToast("没有可下载的歌曲"); return }
        if (isDownloaded(target)) {
            showToast("已下载过该歌曲")
            return
        }
        if (downloadState is DownloadState.Downloading) {
            showToast("正在下载中，请稍候")
            return
        }
        val downloadDecision = MusicPlaybackGate.evaluate(MusicPlaybackGate.Action.DOWNLOAD)
        if (!downloadDecision.allowed) {
            val message = downloadDecision.reason ?: "当前环境存在风险，已阻止下载"
            Log.w(TAG, "安全策略拦截[DOWNLOAD]: $message")
            downloadState = DownloadState.Error(message)
            showToast(message)
            if (downloadDecision.shouldKillProcess) {
                SecurityGuard.killProcess()
            }
            return
        }
        val isNetease = target.platform.contains("网易")
        if (isNetease) {
            downloadNeteaseSong(target, qualityKey)
        } else {
            downloadPluginSong(target, qualityKey)
        }
    }

    private fun downloadNeteaseSong(target: Song, qualityKey: String? = null) {
        // 内置网易云API已移除，统一走插件下载
        downloadPluginSong(target, qualityKey)
    }

    private fun downloadPluginSong(target: Song, qualityKey: String? = null) {
        // 优先检查能否确定歌曲的音源（lxSelectedSource 或歌曲自带的 lxSourceKey）
        val dlSource = effectiveLxSource(target)
        if (apiMode != "lx_plugin" || dlSource.isBlank()) {
            showToast("仅支持下载插件音乐，请先在插件管理中启用插件")
            return
        }
        val lxQuality = qualityKey ?: lxQualityKey
        val effectiveSource = dlSource

        // 音质一致性检查：确保该音源支持所请求的音质，避免引擎内部降级到 128k 导致音质不符
        val sourceSupportedQualities = LxPluginEngine.MUSIC_QUALITY[effectiveSource] ?: emptyList()
        if (lxQuality !in sourceSupportedQualities) {
            val qualityLabel = MusicViewModel.LxQuality.displayFor(lxQuality)
            val sourceName = LxSdkSearchManager.sourceIdToName(effectiveSource)
            showToast("$sourceName 不支持 $qualityLabel 音质，请选择其他音质")
            downloadState = DownloadState.Error("$sourceName 不支持 $qualityLabel 音质")
            return
        }

        downloadState = DownloadState.Downloading(target.platformId)
        viewModelScope.launch {
            try {
                var downloadUrl = ""
                var downloadHeaders = mapOf<String, String>()

                // 通过 LX 插件管理器解析下载链接（与播放使用相同路径，确保兼容性）
                val pluginResult = try {
                    val dlPluginId = target.lxPluginId.ifBlank { lxSelectedPluginId }
                    if (dlPluginId.isNotBlank()) {
                        lxPluginManager.musicUrl(dlPluginId, effectiveSource, target, 15000L, lxQuality)
                    } else null
                } catch (_: Exception) { null }
                if (pluginResult != null && pluginResult.url.isNotBlank()) {
                    downloadUrl = pluginResult.url
                    downloadHeaders = pluginResult.headers
                }

                if (downloadUrl.isBlank()) {
                    downloadState = DownloadState.Error("获取下载链接失败")
                    showToast("获取下载链接失败（插件系统无法解析此音质）")
                    return@launch
                }

                val platformDir = target.platform.ifBlank { "其他" }
                val dir = java.io.File(java.io.File(downloadDir), platformDir)
                if (!dir.exists() && !dir.mkdirs()) {
                    downloadState = DownloadState.Error("创建下载目录失败")
                    showToast("创建下载目录失败: ${dir.absolutePath}")
                    return@launch
                }
                Log.d(TAG, "插件下载目录: ${dir.absolutePath}")

                val ext = when {
                    downloadUrl.contains(".flac", true) -> "flac"
                    downloadUrl.contains(".m4a", true) -> "m4a"
                    downloadUrl.contains(".wav", true) -> "wav"
                    lxQuality in listOf("flac", "flac24bit", "flac32bit", "master", "atmos", "atmos_plus") -> "flac"
                    else -> "mp3"
                }
                val safeTitle = target.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                val safeArtist = target.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40)
                val fileName = "${safeArtist} - ${safeTitle}.$ext"
                val outputFile = java.io.File(dir, fileName)

                withContext(Dispatchers.IO) {
                    val reqBuilder = Request.Builder().url(downloadUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    downloadHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                    val downloadClient = MusicApiService.sharedClient().newBuilder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()
                    val response = downloadClient.newCall(reqBuilder.build()).execute()
                    if (!response.isSuccessful) throw IllegalStateException("下载失败: HTTP ${response.code}")
                    val body = response.body ?: throw IllegalStateException("下载内容为空")
                    val totalBytes = body.contentLength()
                    outputFile.outputStream().buffered().use { out ->
                        body.byteStream().buffered().use { input ->
                            val buf = ByteArray(8192)
                            var downloaded = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                out.write(buf, 0, n)
                                downloaded += n
                                if (totalBytes > 0) {
                                    val prog = (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) {
                                        downloadState = DownloadState.Downloading(target.platformId, prog)
                                    }
                                }
                            }
                        }
                    }
                    response.close()
                }

                val entry = DownloadedSong(
                    song = target,
                    filePath = outputFile.absolutePath,
                    fileSize = outputFile.length(),
                    platform = platformDir,
                )
                downloadedSongs = downloadedSongs + entry
                saveDownloadedSongs()
                downloadState = DownloadState.Success(target.title)
                showToast("下载完成: ${target.title}")

                withContext(Dispatchers.IO) {
                    saveLyricAndCover(target, outputFile, safeArtist, safeTitle)
                }
            } catch (e: Exception) {
                Log.e(TAG, "插件下载失败", e)
                downloadState = DownloadState.Error("下载失败: ${e.message}")
                showToast("下载失败: ${e.message}")
            }
        }
    }

    private suspend fun saveLyricAndCover(song: Song, audioFile: java.io.File, safeArtist: String, safeTitle: String) {
        var lrcText = ""
        try {
            lrcText = song.lrcText
            if (lrcText.isBlank()) {
                try {
                    val lyricResult = withTimeoutOrNull(8000L) {
                        val effectiveSource = effectiveLxSource(song)
                        val pluginIdForLyric = song.lxPluginId.ifBlank { lxSelectedPluginId }
                        if (effectiveSource.isNotBlank() && pluginIdForLyric.isNotBlank()) {
                            lxPluginManager.lyric(pluginIdForLyric, effectiveSource, song, lxTimeoutMs)
                        } else null
                    }
                    if (lyricResult != null && lyricResult.lyric.isNotBlank()) {
                        lrcText = lyricResult.lyric
                    }
                } catch (_: Exception) {}
            }
            if (lrcText.isBlank()) {
                try {
                    val lyrics = MusicApiService.fetchLyricsDirect(song.platform, song.platformId)
                    if (lyrics.isNotEmpty()) {
                        val sb = StringBuilder()
                        for (line in lyrics) {
                            if (line.timeMs >= 0) {
                                val m = line.timeMs / 60000
                                val s = (line.timeMs % 60000) / 1000
                                val ms = line.timeMs % 1000
                                sb.append("[%02d:%02d.%03d]%s\n".format(m, s, ms, line.text))
                            } else {
                                sb.append("${line.text}\n")
                            }
                        }
                        lrcText = sb.toString()
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取歌词失败: ${e.message}")
        }

        var coverData: ByteArray? = null
        try {
            val coverUrl = song.coverUrl
            if (coverUrl.isNotBlank()) {
                val client = MusicApiService.sharedClient().newBuilder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                val request = Request.Builder().url(coverUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null && body.contentLength() != 0L) {
                        coverData = body.bytes()
                    }
                }
                response.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "下载封面失败: ${e.message}")
        }

        if (lrcText.isBlank() && coverData == null) return

        try {
            val audioFileObj = org.jaudiotagger.audio.AudioFileIO.read(audioFile)
            val tag = audioFileObj.tagOrCreateAndSetDefault
            if (lrcText.isNotBlank()) {
                tag.setField(org.jaudiotagger.tag.FieldKey.LYRICS, lrcText)
            }
            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, song.title)
            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, song.artist)
            if (song.album.isNotBlank()) {
                tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, song.album)
            }
            if (coverData != null) {
                try {
                    tag.deleteArtworkField()
                    when (tag) {
                        is org.jaudiotagger.tag.flac.FlacTag -> {
                            val opts = android.graphics.BitmapFactory.Options()
                            opts.inJustDecodeBounds = true
                            android.graphics.BitmapFactory.decodeByteArray(coverData, 0, coverData.size, opts)
                            val picture = org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture(
                                coverData, 3, "image/jpeg", "", opts.outWidth, opts.outHeight, 0, 0
                            )
                            val imagesField = tag.javaClass.getDeclaredField("images")
                            imagesField.isAccessible = true
                            @Suppress("UNCHECKED_CAST")
                            val images = imagesField.get(tag) as MutableList<org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture>
                            images.add(picture)
                        }
                        else -> {
                            val coverFile = java.io.File(audioFile.parent, audioFile.nameWithoutExtension + ".jpg")
                            coverFile.writeBytes(coverData)
                            val artwork = org.jaudiotagger.tag.images.ArtworkFactory.createArtworkFromFile(coverFile)
                            tag.setField(artwork as org.jaudiotagger.tag.images.Artwork)
                            coverFile.delete()
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "嵌入封面失败: ${e.message}")
                }
            }
            audioFileObj.commit()
            Log.d(TAG, "元数据已写入: ${audioFile.name}")
        } catch (e: Exception) {
            Log.w(TAG, "写入音频元数据失败: ${e.message}")
        }
    }

    fun downloadSongWithQuality(song: Song, quality: MusicApiConfig.Quality) {
        // 串行保护：与 downloadSong() 一致，避免并发下载导致完成顺序错乱（播放顺序与下载顺序不一致）
        if (downloadState is DownloadState.Downloading) {
            showToast("正在下载中，请稍候")
            return
        }
        // 所有平台统一走插件下载，使用与 lxQualityKey 一致的 LX 音质 key 映射
        val qualityKey = when (quality) {
            MusicApiConfig.Quality.STANDARD -> "128k"
            MusicApiConfig.Quality.EXHIGH -> "320k"
            MusicApiConfig.Quality.LOSSLESS -> "flac"
            MusicApiConfig.Quality.HIRES -> "flac24bit"
            MusicApiConfig.Quality.JYMASTER -> "master"
            MusicApiConfig.Quality.SKY -> "atmos"
            MusicApiConfig.Quality.JYEFFECT -> "atmos_plus"
        }
        downloadPluginSong(song, qualityKey)
    }

    fun batchDownloadCurrentPlaylist() {
        val songs = playlist
        if (songs.isEmpty()) {
            showToast("当前播放列表为空")
            return
        }
        val toDownload = songs.filter { !isDownloaded(it) }
        if (toDownload.isEmpty()) {
            showToast("所有歌曲已下载")
            return
        }
        showToast("开始批量下载 ${toDownload.size} 首歌曲")
        viewModelScope.launch {
            for ((index, song) in toDownload.withIndex()) {
                // 统一走 downloadSongWithQuality，保证音质 key 映射与单首下载一致
                downloadSongWithQuality(song, selectedQuality)
                var waitCount = 0
                while (downloadState is DownloadState.Downloading && waitCount < 120) {
                    kotlinx.coroutines.delay(500)
                    waitCount++
                }
                Log.d(TAG, "批量下载进度: ${index + 1}/${toDownload.size}")
            }
            withContext(Dispatchers.Main) {
                downloadState = DownloadState.Success("批量下载完成")
                showToast("批量下载完成，共下载 ${toDownload.size} 首")
            }
        }
    }

    /** Play a downloaded song directly from its local file. */
    fun playDownloadedSong(entry: DownloadedSong) {
        val file = java.io.File(entry.filePath)
        if (!file.exists()) {
            showToast("文件已被删除")
            downloadedSongs = downloadedSongs.filter { it.filePath != entry.filePath }
            saveDownloadedSongs()
            return
        }
        val localSong = entry.song.copy(directUrl = entry.filePath)
        playSong(localSong)
    }

    /** Delete a downloaded song file and remove from list. */
    fun deleteDownloadedSong(entry: DownloadedSong) {
        try { java.io.File(entry.filePath).delete() } catch (_: Exception) {}
        downloadedSongs = downloadedSongs.filter { it.filePath != entry.filePath }
        saveDownloadedSongs()
        showToast("已删除")
    }

    /** Get downloaded songs grouped by platform for the UI. */
    fun getDownloadsByPlatform(): Map<String, List<DownloadedSong>> {
        return downloadedSongs.groupBy { it.platform }
    }

    /** Get total download size. */
    fun getDownloadTotalSize(): String {
        val total = downloadedSongs.sumOf { it.fileSize }
        return formatFileSize(total)
    }

    fun resetDownloadState() { downloadState = DownloadState.Idle }

    private fun saveDownloadedSongs() {
        try {
            val arr = org.json.JSONArray()
            downloadedSongs.forEach { d ->
                arr.put(org.json.JSONObject().apply {
                    put("title", d.song.title)
                    put("artist", d.song.artist)
                    put("album", d.song.album)
                    put("coverUrl", d.song.coverUrl)
                    put("platform", d.song.platform)
                    put("platformId", d.song.platformId)
                    put("pluginRawJson", d.song.pluginRawJson)
                    put("lxSourceKey", d.song.lxSourceKey)
                    put("filePath", d.filePath)
                    put("fileSize", d.fileSize)
                    put("platformDir", d.platform)
                    put("downloadTime", d.downloadTime)
                })
            }
            LocalStorage.saveString("downloaded_songs", arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "保存下载列表失败", e)
        }
    }

    private fun loadDownloadedSongs() {
        try {
            val json = LocalStorage.loadString("downloaded_songs")
            if (json.isBlank()) return
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<DownloadedSong>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val filePath = obj.optString("filePath", "")
                if (filePath.isBlank() || !java.io.File(filePath).exists()) continue
                list.add(DownloadedSong(
                    song = Song(
                        id = obj.optString("platformId", "").hashCode().toLong(),
                        title = obj.optString("title", ""),
                        artist = obj.optString("artist", ""),
                        album = obj.optString("album", ""),
                        coverUrl = firstCoverUrl(
                            obj.optString("coverUrl", ""),
                            obj.optString("cover", ""),
                            obj.optString("picUrl", ""),
                            obj.optString("pic", ""),
                            obj.optString("imgUrl", ""),
                            obj.optString("img", ""),
                        ),
                        platform = obj.optString("platform", ""),
                        platformId = obj.optString("platformId", ""),
                        pluginRawJson = obj.optString("pluginRawJson", ""),
                        lxSourceKey = obj.optString("lxSourceKey", ""),
                    ),
                    filePath = filePath,
                    fileSize = obj.optLong("fileSize", java.io.File(filePath).length()),
                    platform = obj.optString("platformDir", obj.optString("platform", "其他")),
                    downloadTime = obj.optLong("downloadTime", 0L),
                ))
            }
            downloadedSongs = list
        } catch (e: Exception) {
            Log.e(TAG, "加载下载列表失败", e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ═══ 新增设置功能：缓存管理、下载目录、自动更新、音源管理 ═══
    // ═══════════════════════════════════════════════════════════

    // ── 音乐缓存管理 ──
    fun getCacheSize(): String {
        return try {
            val cacheDir = getApplication<Application>().cacheDir
            val externalCacheDir = getApplication<Application>().externalCacheDir
            var size = cacheDir?.let { calculateDirSize(it) } ?: 0L
            size += externalCacheDir?.let { calculateDirSize(it) } ?: 0L
            formatFileSize(size)
        } catch (e: Exception) {
            "未知"
        }
    }

    private fun calculateDirSize(dir: java.io.File): Long {
        var size = 0L
        try {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) calculateDirSize(file) else file.length()
            }
        } catch (_: Exception) {}
        return size
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 * 1024 -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
            size >= 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            size >= 1024 -> String.format("%.2f KB", size / 1024.0)
            else -> "$size B"
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().cacheDir?.let { deleteDir(it) }
                getApplication<Application>().externalCacheDir?.let { deleteDir(it) }
                withContext(Dispatchers.Main) {
                    showToast("缓存已清理")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("清理缓存失败: ${e.message}")
                }
            }
        }
    }

    private fun deleteDir(dir: java.io.File) {
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) deleteDir(file)
                else file.delete()
            }
        } catch (_: Exception) {}
    }

    // ── 下载目录 ──
    var customDownloadDir by mutableStateOf(LocalStorage.loadString("custom_download_dir"))
        private set

    val downloadDir: String
        get() {
            val custom = customDownloadDir
            if (custom.isNotBlank()) return custom
            val publicMusicDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Music/音动音乐")
            if (android.os.Environment.isExternalStorageManager() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return publicMusicDir.absolutePath
            }
            return try {
                val appDir = getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                appDir?.absolutePath ?: getApplication<Application>().filesDir.resolve("Music").absolutePath
            } catch (_: Exception) {
                getApplication<Application>().filesDir.resolve("Music").absolutePath
            }
        }

    fun setDownloadDir(path: String) {
        val trimmed = path.trim()
        customDownloadDir = trimmed
        LocalStorage.saveString("custom_download_dir", trimmed)
        if (trimmed.isNotBlank()) {
            try { java.io.File(trimmed).mkdirs() } catch (_: Exception) {}
            showToast("下载路径已更新")
        } else {
            showToast("已恢复默认下载路径")
        }
    }

    // ── USB 独占模式 ──
    private val usbExclusiveManager: UsbExclusiveManager by lazy {
        UsbExclusiveManager(getApplication(), downloadDirProvider = { downloadDir })
    }

    /** USB 独占模式是否已启用 */
    val isUsbExclusiveEnabled: StateFlow<Boolean> get() = usbExclusiveManager.enabled

    /** USB 独占模式实时日志条目 */
    val usbExclusiveLogEntries: StateFlow<List<UsbLogEntry>> get() = usbExclusiveManager.logEntries

    /** 每台 USB 设备的独占访问状态 */
    val usbDeviceStates: StateFlow<Map<String, UsbDeviceState>> get() = usbExclusiveManager.deviceStates

    /** 实际日志目录路径（含降级逻辑） */
    val usbExclusiveLogDir: StateFlow<String> get() = usbExclusiveManager.logDirPath

    /** 切换 USB 独占模式开关 */
    fun toggleUsbExclusive() {
        if (isUsbExclusiveEnabled.value) {
            usbExclusiveManager.disable()
            showToast("USB独占模式已关闭")
        } else {
            usbExclusiveManager.enable()
            showToast("USB独占模式已启动，认证日志已写入「日志」文件夹")
        }
    }

    /** 重新扫描并认证已连接的 USB 设备 */
    fun rescanUsbDevices() {
        usbExclusiveManager.rescan()
    }

    /** 对指定设备手动重试独占访问认证 */
    fun retryUsbDevice(deviceName: String) {
        usbExclusiveManager.retryDevice(deviceName)
    }

    /** 主动释放指定设备的独占访问 */
    fun releaseUsbDevice(deviceName: String) {
        usbExclusiveManager.releaseDevice(deviceName)
        showToast("已释放设备独占：$deviceName")
    }



    // ── 音源插件管理 ──
    var qqMusicEnabled by mutableStateOf(LocalStorage.loadSourceEnabled("qq_music"))
        private set
    var neteaseEnabled by mutableStateOf(LocalStorage.loadSourceEnabled("netease"))
        private set
    var kuwoEnabled by mutableStateOf(LocalStorage.loadSourceEnabled("kuwo"))
        private set
    var miguEnabled by mutableStateOf(LocalStorage.loadSourceEnabled("migu"))
        private set
    var kugouEnabled by mutableStateOf(LocalStorage.loadSourceEnabled("kugou"))
        private set
    var douyinEnabled by mutableStateOf(LocalStorage.loadSourceEnabled("douyin"))
        private set

    fun toggleQqMusic() {
        qqMusicEnabled = !qqMusicEnabled
        LocalStorage.saveSourceEnabled("qq_music", qqMusicEnabled)
        showToast("QQ音乐 ${if (qqMusicEnabled) "已启用" else "已禁用"}")
    }

    fun toggleNetease() {
        neteaseEnabled = !neteaseEnabled
        LocalStorage.saveSourceEnabled("netease", neteaseEnabled)
        showToast("网易云音乐 ${if (neteaseEnabled) "已启用" else "已禁用"}")
    }

    fun toggleKuwo() {
        kuwoEnabled = !kuwoEnabled
        LocalStorage.saveSourceEnabled("kuwo", kuwoEnabled)
        showToast("酷我音乐 ${if (kuwoEnabled) "已启用" else "已禁用"}")
    }

    fun toggleMigu() {
        miguEnabled = !miguEnabled
        LocalStorage.saveSourceEnabled("migu", miguEnabled)
        showToast("咪咕音乐 ${if (miguEnabled) "已启用" else "已禁用"}")
    }

    fun toggleKugou() {
        kugouEnabled = !kugouEnabled
        LocalStorage.saveSourceEnabled("kugou", kugouEnabled)
        showToast("酷狗音乐 ${if (kugouEnabled) "已启用" else "已禁用"}")
    }

    fun toggleDouyin() {
        douyinEnabled = !douyinEnabled
        LocalStorage.saveSourceEnabled("douyin", douyinEnabled)
        showToast("抖音 ${if (douyinEnabled) "已启用" else "已禁用"}")
    }

    // ═══════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════
    // ═══ UI 屏幕所需的别名属性和辅助方法 ═══
    // ═══════════════════════════════════════════════════════════

    // ── 属性别名 ──
    val favorites: List<Song> get() = favoriteSongs
    val userPlaylists: List<Playlist> get() = myPlaylists
    val isFloatingLyricsEnabled: Boolean get() = floatingLyricsEnabled
    val lyricCurrentColor: Int get() = highlightLyricColor
    val lyricNormalColor: Int get() = normalLyricColor
    val lyricFontSize: Int get() = playerLyricSize
    val bassBoost: Int get() = bassBoostStrength
    val virtualizer: Int get() = virtualizerStrength
    val reverbWet: Int get() = reverbLevel
    val currentVersion: String get() = "3.0.4"
    var qqPlayApiValue by mutableStateOf(LocalStorage.loadQQPlayApi())
        private set
    var douyinParseApiValue by mutableStateOf(LocalStorage.loadDouyinParseApi())
        private set
    val qqPlayApi: String get() = qqPlayApiValue
    val douyinParseApi: String get() = douyinParseApiValue
    val douyinApiKey: String get() = douyinApi
    val qqMusicApiKey: String get() = qqMusicApi
    val neteaseApiKey: String get() = neteaseApi
    val kuwoApiKey: String get() = kuwoApi
    val miguApiKey: String get() = miguApi
    val kugouApiKey: String get() = kugouApi
    val equalizerFreqs: List<Int>
        get() = equalizerBands.map { it.first }

    // ── 热歌榜 ──

    /**
     * 并行获取 lx-music-mobile 4 平台排行榜歌曲并合并
     * 参考 lx-music-mobile-master/src/core/leaderboard.ts
     * @param boards Triple<source, bangid, platformName>
     */
    private suspend fun fetchLxLeaderboardSongs(boards: List<Triple<String, String, String>>): List<Song> {
        return coroutineScope {
            boards.map { (source, bangid, platformName) ->
                async(Dispatchers.IO) {
                    try {
                        val result = LxSdkLeaderboard.getList(source, bangid, page = 1, limit = 50)
                        result.list.take(30)  // 每平台取前 30 首
                    } catch (e: Exception) {
                        Log.w(TAG, "fetchLxLeaderboardSongs: $source/$bangid failed: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
    }

    fun loadHotChart() {
        viewModelScope.launch {
            isHotChartLoading = true
            try {
                // 替换为 lx-music-mobile 4 平台排行榜实现
                val fetched = fetchLxLeaderboardSongs(
                    listOf(
                        Triple("wy", "3778678", "网易云"),  // 热歌榜
                        Triple("tx", "26", "QQ音乐"),       // 热歌榜
                        Triple("kg", "8888", "酷狗"),        // TOP500
                        Triple("kw", "16", "酷我"),          // 热歌榜
                    )
                )
                hotChartSongs = fetched
                if (fetched.isNotEmpty()) {
                    val snapshotKey = fetched.joinToString("|") { "${it.platform}:${it.platformId}:${it.id}" }
                    launch {
                        try {
                            val fastPass = MusicApiService.enrichMissingSongCovers(
                                songs = fetched,
                                maxLookup = 24,
                                parallelism = 12,
                            )
                            val keyAfterFastPass = hotChartSongs.joinToString("|") { "${it.platform}:${it.platformId}:${it.id}" }
                            if (fastPass != fetched && keyAfterFastPass == snapshotKey) {
                                hotChartSongs = fastPass
                            }
                            val fullPass = MusicApiService.enrichMissingSongCovers(
                                songs = if (fastPass.isNotEmpty()) fastPass else fetched,
                                maxLookup = 120,
                                parallelism = 8,
                            )
                            val keyBeforeFullPass = hotChartSongs.joinToString("|") { "${it.platform}:${it.platformId}:${it.id}" }
                            if (fullPass != hotChartSongs && keyBeforeFullPass == snapshotKey) {
                                hotChartSongs = fullPass
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "热歌榜封面补全失败", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载热歌榜失败", e)
            }
            isHotChartLoading = false
        }
    }

    // ── 新歌速递 ──
    fun loadNewSongs() {
        viewModelScope.launch {
            isNewSongsLoading = true
            try {
                // 替换为 lx-music-mobile 4 平台新歌榜实现
                val fetched = fetchLxLeaderboardSongs(
                    listOf(
                        Triple("wy", "3779629", "网易云"),  // 新歌榜
                        Triple("tx", "27", "QQ音乐"),       // 新歌榜
                        Triple("kg", "22650", "酷狗"),      // 华语新歌榜
                        Triple("kw", "17", "酷我"),          // 新歌榜
                    )
                )
                newSongs = fetched
                if (fetched.isNotEmpty()) {
                    val snapshotKey = fetched.joinToString("|") { "${it.platform}:${it.platformId}:${it.id}" }
                    launch {
                        try {
                            val fastPass = MusicApiService.enrichMissingSongCovers(
                                songs = fetched,
                                maxLookup = 24,
                                parallelism = 12,
                            )
                            val keyAfterFastPass = newSongs.joinToString("|") { "${it.platform}:${it.platformId}:${it.id}" }
                            if (fastPass != fetched && keyAfterFastPass == snapshotKey) {
                                newSongs = fastPass
                            }
                            val fullPass = MusicApiService.enrichMissingSongCovers(
                                songs = if (fastPass.isNotEmpty()) fastPass else fetched,
                                maxLookup = 120,
                                parallelism = 8,
                            )
                            val keyBeforeFullPass = newSongs.joinToString("|") { "${it.platform}:${it.platformId}:${it.id}" }
                            if (fullPass != newSongs && keyBeforeFullPass == snapshotKey) {
                                newSongs = fullPass
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "新歌速递封面补全失败", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载新歌速递失败", e)
            }
            isNewSongsLoading = false
        }
    }

    // ── 搜索相关 ──
    fun quickSearch(keyword: String) {
        searchQuery = keyword
        performOnlineSearch(keyword)
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (query.isEmpty()) {
            onlineResults = emptyList()
            searchSuggestions = emptyList()
            hasSearched = false
            searchSuggestionJob?.cancel()
        } else {
            // 获取搜索建议
            fetchSearchSuggestions(query)
        }
    }

    private fun fetchSearchSuggestions(query: String) {
        searchSuggestionJob?.cancel()
        searchSuggestionJob = viewModelScope.launch {
            // 与 lx-music-mobile TipList 一致：延迟 300ms 防抖
            delay(300)
            if (query.isBlank() || query != searchQuery) {
                searchSuggestions = emptyList()
                return@launch
            }
            // 使用 lx-music-mobile 4 平台 tipSearch 合并结果
            val suggestions = try {
                LxSdkTipSearch.search(query)
            } catch (e: Exception) {
                Log.w(TAG, "fetchSearchSuggestions failed: ${e.message}")
                emptyList()
            }
            // 用户在请求期间可能已修改输入，校验后再写入
            if (query != searchQuery) return@launch
            searchSuggestions = suggestions.map { SearchSuggestion(it) }
        }
    }

    fun initAudioFingerprintGenerator() {
        try {
            com.yindong.music.audio.AudioFingerprintGenerator.init(getApplication())
        } catch (e: Exception) {
            Log.e(TAG, "AFP初始化失败", e)
        }
    }

    fun startSongRecognition() {
        if (recognizeState is RecognizeState.Recording ||
            recognizeState is RecognizeState.GeneratingFP ||
            recognizeState is RecognizeState.Recognizing) {
            return
        }

        viewModelScope.launch {
            try {
                recognizeState = RecognizeState.Recording

                val recordResult = withContext(Dispatchers.IO) {
                    com.yindong.music.audio.AudioRecorder.record()
                }

                if (recordResult.rms < 0.01f) {
                    recognizeState = RecognizeState.Error("环境太安静，请靠近音源再试")
                    return@launch
                }

                recognizeState = RecognizeState.GeneratingFP

                val fingerprint = try {
                    com.yindong.music.audio.AudioFingerprintGenerator.generateFingerprint(recordResult.samples)
                } catch (e: Exception) {
                    Log.e(TAG, "指纹生成失败", e)
                    recognizeState = RecognizeState.Error("指纹生成失败: ${e.message}")
                    return@launch
                }

                recognizeState = RecognizeState.Recognizing

                // 内置识曲API已移除，无法识别
                recognizeState = RecognizeState.Error("听歌识曲功能暂不可用")
            } catch (e: Exception) {
                Log.e(TAG, "识曲失败", e)
                recognizeState = RecognizeState.Error("识别失败: ${e.message}")
            }
        }
    }

    fun resetRecognition() {
        recognizeState = RecognizeState.Idle
        recognizedSongs = emptyList()
        com.yindong.music.audio.AudioFingerprintGenerator.reset()
    }

    fun playRecognizedSong(song: RecognizedSong) {
        viewModelScope.launch {
            val artistName = song.song.artists.joinToString("/") { it.name }
            val coverUrl = song.song.album?.picUrl ?: ""
            val newSong = Song(
                id = song.song.id,
                title = song.song.name,
                artist = artistName,
                album = song.song.album?.name ?: "",
                coverUrl = coverUrl,
                platform = "网易云",
                platformId = song.song.id.toString()
            )
            playSong(newSong)
        }
    }

    fun performOnlineSearch(query: String, isNewSearch: Boolean = true) {
        if (query.isBlank()) return
        // 上报搜索统计
        if (isNewSearch) StatsReporter.reportSearch(query, currentSearchPlatform.ifBlank { "全部" })
        // 直接设置搜索词，避免触发搜索建议导致 UI 闪烁
        searchQuery = query
        // 清空搜索建议
        searchSuggestions = emptyList()
        searchSuggestionJob?.cancel()
        // "全部"平台走多平台合并搜索（空初始状态也默认"全部"）
        if (isNewSearch && (currentSearchPlatform == "全部" || currentSearchPlatform.isBlank())) {
            performAllPlatformSearch(query)
            return
        }
        viewModelScope.launch {
            if (isNewSearch) {
                isSearching = true
                hasSearched = true
                currentSearchOffset = 0
                currentPluginPage = 1
                hasMoreResults = true
                onlineResults = emptyList()
            } else {
                isLoadingMore = true
                currentPluginPage++
            }
            searchError = null
            try {
                // 使用内置 lx-music-mobile 搜索算法（落雪插件仅用于解析播放链接，不支持搜索）
                val platform = if (currentSearchPlatform.isNotBlank()) currentSearchPlatform else "网易云"
                if (isNewSearch) currentSearchPlatform = platform

                val results: List<Song> = withTimeoutOrNull(15_000L) {
                    searchByPlugin(platform, query, currentPluginPage)
                } ?: run {
                    if (isNewSearch) searchError = "搜索超时，请稍后重试"
                    emptyList()
                }

                if (isNewSearch) {
                    onlineResults = results
                } else {
                    // 加载更多：追加结果并去重
                    val existingIds = onlineResults.map { "${it.platform}:${it.platformId}" }.toSet()
                    val newSongs = results.filter { "${it.platform}:${it.platformId}" !in existingIds }
                    onlineResults = onlineResults + newSongs
                }

                // 更新分页状态
                currentSearchOffset += results.size
                hasMoreResults = results.size >= searchLimit

                // 有结果时清除错误
                if (onlineResults.isNotEmpty()) {
                    searchError = null
                    if (isNewSearch) addSearchHistory(query)
                    // 延迟刷新列表，让异步获取的网易云封面能够显示
                    viewModelScope.launch {
                        delay(1500)
                        refreshSongCovers()
                        delay(1500)
                        refreshSongCovers()
                    }
                } else if (isNewSearch && searchError == null) {
                    searchError = "未找到相关结果"
                }
            } catch (e: Exception) {
                Log.e(TAG, "在线搜索失败", e)
                searchError = "搜索失败: ${e.message}"
            } finally {
                isSearching = false
                isLoadingMore = false
            }
        }
    }

    /**
     * 全部平台搜索：并行搜索网易云、QQ、酷我、酷狗，每个平台取前4条，按平台顺序拼接
     * 顺序：网易云4首 → QQ4首 → 酷我4首 → 酷狗4首
     * 支持下滑加载更多：每次加载各平台下一页的4首
     */
    fun performAllPlatformSearch(query: String) {
        if (query.isBlank()) return
        // 上报搜索统计
        StatsReporter.reportSearch(query, "全部")
        searchQuery = query
        searchSuggestions = emptyList()
        searchSuggestionJob?.cancel()
        // 重置各平台页码
        allPlatformPages = mutableMapOf("网易云" to 1, "QQ" to 1, "酷我" to 1, "酷狗" to 1)
        viewModelScope.launch {
            isSearching = true
            hasSearched = true
            currentSearchOffset = 0
            currentPluginPage = 1
            hasMoreResults = false
            onlineResults = emptyList()
            currentSearchPlatform = "全部"
            searchError = null
            try {
                val platforms = listOf("网易云", "QQ", "酷我", "酷狗")
                val perPlatformLimit = 4
                // 内置 LX 平台并行搜索
                val lxDeferredList = platforms.map { platform ->
                    async(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(15_000L) {
                                searchByPlugin(platform, query, 1)
                            } ?: emptyList()
                        } catch (e: Exception) {
                            Log.w(TAG, "全部搜索: platform=$platform failed: ${e.message}")
                            emptyList()
                        }
                    }
                }
                // MusicFree 插件并行搜索（每个启用的插件取前 4 条）
                val mfPlugins = musicFreePlugins.filter { it.enabled && it.mounted }
                val mfPerPluginLimit = 4
                val mfDeferredList = mfPlugins.map { mf ->
                    async(Dispatchers.IO) {
                        try {
                            val mfResult = withTimeoutOrNull(15_000L) {
                                musicFreePluginManager.search(mf.id, query, 1, "music")
                            }
                            mfResult?.data?.take(mfPerPluginLimit)?.mapIndexed { idx, item ->
                                Song(
                                    id = System.currentTimeMillis() + idx,
                                    title = item.title,
                                    artist = item.artist,
                                    album = item.album,
                                    duration = item.duration,
                                    coverUrl = item.artwork,
                                    platform = mf.info.platform.ifBlank { mf.fileName },
                                    platformId = item.id,
                                    pluginRawJson = item.rawJson,
                                    musicFreePluginId = mf.id,
                                )
                            } ?: emptyList()
                        } catch (e: Exception) {
                            Log.w(TAG, "全部搜索: MusicFree ${mf.info.platform} failed: ${e.message}")
                            emptyList()
                        }
                    }
                }
                val allLxResults = lxDeferredList.awaitAll()
                val allMfResults = mfDeferredList.awaitAll()
                // 按顺序拼接：网易云/QQ/酷我/酷狗 各4首 + 各 MusicFree 插件各4首
                Log.d(TAG, "全部搜索完成: LX 各平台结果数=${allLxResults.map { it.size }}, MF 各插件结果数=${allMfResults.map { it.size }}")
                val merged = allLxResults.flatMap { it.take(perPlatformLimit) } +
                    allMfResults.flatMap { it.take(mfPerPluginLimit) }
                Log.d(TAG, "全部搜索合并: 总数=${merged.size}, 各歌曲平台=${merged.map { it.platform }.distinct()}")
                onlineResults = merged
                // 任一平台/插件有更多结果就允许加载更多
                hasMoreResults = allLxResults.any { it.size >= perPlatformLimit } ||
                    allMfResults.any { it.size >= mfPerPluginLimit }
                if (merged.isNotEmpty()) {
                    searchError = null  // 清除个别平台失败导致的错误
                    addSearchHistory(query)
                    viewModelScope.launch {
                        delay(1500)
                        refreshSongCovers()
                        delay(1500)
                        refreshSongCovers()
                    }
                } else if (searchError == null) {
                    searchError = "未找到相关结果"
                }
            } catch (e: Exception) {
                Log.e(TAG, "全部搜索失败", e)
                searchError = "搜索失败: ${e.message}"
            } finally {
                isSearching = false
                isLoadingMore = false
            }
        }
    }

    /**
     * "全部"平台加载更多：各平台下一页各取4首，按顺序拼接
     */
    private fun loadMoreAllPlatform() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        val platforms = listOf("网易云", "QQ", "酷我", "酷狗")
        val perPlatformLimit = 4
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val deferredList = platforms.map { platform ->
                    val nextPage = (allPlatformPages[platform] ?: 1) + 1
                    async(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(15_000L) {
                                searchByPlugin(platform, query, nextPage)
                            } ?: emptyList()
                        } catch (e: Exception) {
                            Log.w(TAG, "全部加载更多: platform=$platform failed: ${e.message}")
                            emptyList()
                        }
                    }
                }
                val allResults = deferredList.awaitAll()
                val newSongs = allResults.flatMap { it.take(perPlatformLimit) }
                Log.d(TAG, "全部加载更多: 各平台结果数=${allResults.map { it.size }}, 新增=${newSongs.size}")
                if (newSongs.isNotEmpty()) {
                    // 去重后追加
                    val existingIds = onlineResults.map { "${it.platform}:${it.platformId}" }.toSet()
                    val filtered = newSongs.filter { "${it.platform}:${it.platformId}" !in existingIds }
                    onlineResults = onlineResults + filtered
                    // 更新各平台页码
                    platforms.forEachIndexed { index, platform ->
                        if (allResults[index].isNotEmpty()) {
                            allPlatformPages[platform] = (allPlatformPages[platform] ?: 1) + 1
                        }
                    }
                }
                // 任一平台还有结果就允许继续加载
                hasMoreResults = allResults.any { it.size >= perPlatformLimit }
            } catch (e: Exception) {
                Log.e(TAG, "全部加载更多失败", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    /**
     * 单平台搜索
     * 参考 MusicFree 的搜索架构：每个插件独立搜索，结果携带插件身份(lxPluginId/lxSourceKey)
     * 播放时通过 lxPluginId 路由回原插件获取音源
     *
     * @param query 搜索关键词
     * @param platform 平台名称（网易云、酷我音乐、酷狗音乐、QQ音乐、或插件名）
     */
    fun performSinglePlatformSearch(query: String, platform: String, isLoadMore: Boolean = false) {
        if (query.isBlank()) return
        // 上报搜索统计（仅首次搜索时上报，不包含加载更多）
        if (!isLoadMore) StatsReporter.reportSearch(query, platform)
        viewModelScope.launch {
            if (!isLoadMore) {
                isSearching = true
                hasSearched = true
                currentSearchOffset = 0
                currentPluginPage = 1
                hasMoreResults = true
                searchError = null
                onlineResults = emptyList()
                currentSearchPlatform = platform
            } else {
                isLoadingMore = true
                currentPluginPage++
            }

            try {
                Log.d(TAG, "performSinglePlatformSearch: platform='$platform', page=$currentPluginPage")

                // 优先检查是否为 MusicFree 插件平台
                val mfPlugin = findMusicFreePluginByPlatform(platform)
                val results: List<Song> = if (mfPlugin != null) {
                    // MusicFree 插件搜索
                    withTimeoutOrNull(15_000L) {
                        searchByMusicFreePlugin(mfPlugin.id, query, currentPluginPage, "music")
                    }?.let { mfResult ->
                        hasMoreResults = !mfResult.isEnd
                        mfResult.data.mapIndexed { idx, item ->
                            Song(
                                id = System.currentTimeMillis() + idx,
                                title = item.title,
                                artist = item.artist,
                                album = item.album,
                                duration = item.duration,
                                coverUrl = item.artwork,
                                platform = mfPlugin.info.platform.ifBlank { platform },
                                platformId = item.id,
                                pluginRawJson = item.rawJson,
                                musicFreePluginId = mfPlugin.id,
                            )
                        }
                    } ?: run {
                        searchError = "搜索超时，请稍后重试"
                        emptyList()
                    }
                } else {
                    // 内置平台搜索（LX 原生搜索算法）
                    withTimeoutOrNull(15_000L) {
                        searchByPlugin(platform, query, currentPluginPage)
                    } ?: run {
                        searchError = "搜索超时，请稍后重试"
                        emptyList()
                    }
                }

                if (isLoadMore) {
                    val existingIds = onlineResults.map { "${it.platform}:${it.platformId}" }.toSet()
                    val newSongs = results.filter { "${it.platform}:${it.platformId}" !in existingIds }
                    onlineResults = onlineResults + newSongs
                } else {
                    onlineResults = results
                }
                Log.d(TAG, "performSinglePlatformSearch: got ${results.size} results, onlineResults=${onlineResults.size}, platforms=${onlineResults.map { it.platform }.distinct()}")
                // 为没有 lxPluginId 的搜索结果补全插件信息，确保播放时能路由到正确的插件
                // 仅从已真正加载到 QuickJS 运行时、且当前启用的插件中匹配，
                // 避免幻影插件（builtin_lxmusic 未真正加载）与禁用插件被后台调用的残留激活问题
                val loadedPluginIds = lxPluginManager.getAllPluginEntries().map { it.id }.toSet()
                if (loadedPluginIds.isNotEmpty()) {
                    val patchedResults = onlineResults.map { song ->
                        if (song.lxPluginId.isBlank() && song.lxSourceKey.isNotBlank()) {
                            val plugin = lxPlugins.find { it.id in loadedPluginIds && isPluginEnabled(it.id) && song.lxSourceKey in it.sources }
                            if (plugin != null) song.copy(lxPluginId = plugin.id) else song
                        } else song
                    }
                    onlineResults = patchedResults
                }
                currentSearchOffset += results.size
                // 插件搜索时 hasMoreResults 由 searchByPlugin 内部设置

                if (onlineResults.isNotEmpty() && !isLoadMore) {
                    addSearchHistory(query)
                }
                if (onlineResults.isEmpty() && !isLoadMore) {
                    searchError = "未找到相关结果"
                }
            } catch (e: Exception) {
                Log.e(TAG, "单平台搜索失败", e)
                searchError = "搜索失败: ${e.message}"
            } finally {
                isSearching = false
                isLoadingMore = false
            }
        }
    }

    /**
     * 通过 lx-music-mobile 搜索算法搜索歌曲
     *
     * 完全使用 lx-music-mobile-master 项目的搜索方式（Kotlin 原生移植），
     * 不再依赖 QuickJS 桥接的落雪插件搜索路径。
     * 支持 5 个内置平台：wy/tx/kw/kg/mg
     */
    private suspend fun searchByPlugin(platformName: String, query: String, page: Int): List<Song> {
        val sourceId = LxSdkSearchManager.resolveSourceId(platformName)
        Log.d(TAG, "searchByPlugin: platform='$platformName', resolved sourceId='$sourceId'")

        if (sourceId !in listOf("wy", "tx", "kw", "kg", "mg")) {
            Log.w(TAG, "searchByPlugin: unsupported platform '$platformName'")
            return emptyList()
        }

        return try {
            val result = LxSdkSearchManager.search(sourceId, query, page, lxTimeoutMs)
            Log.d(TAG, "searchByPlugin: got ${result.songs.size} items, isEnd=${result.isEnd}")
            hasMoreResults = !result.isEnd
            // 统一 platform 字段为显示名，方便 UI 过滤
            result.songs.map { song ->
                val displayName = LxSdkSearchManager.sourceIdToName(sourceId)
                if (song.platform != displayName) song.copy(platform = displayName) else song
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchByPlugin: failed", e)
            emptyList()
        }
    }

    /**
     * 加载更多搜索结果
     */
    fun loadMoreResults() {
        if (!hasMoreResults || isLoadingMore || isSearching) return
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        // "全部"平台：各平台下一页各取4首
        if (currentSearchPlatform == "全部") {
            loadMoreAllPlatform()
            return
        }
        // 统一走插件分页：有平台则单平台分页，否则走在线搜索分页
        if (currentSearchPlatform.isNotBlank()) {
            performSinglePlatformSearch(query, currentSearchPlatform, isLoadMore = true)
            return
        }
        isLoadingMore = true
        performOnlineSearch(query, isNewSearch = false)
    }

    /** 刷新歌曲封面列表（异步获取后调用） */
    fun refreshSongCovers() {
        onlineResults = onlineResults.toList()
    }

    /**
     * 搜索专辑
     * 参考 MusicFree: plugin.methods.search(query, page, "album")
     * MusicFree 插件 search 方法支持 type 参数区分搜索类型
     */
    fun searchAlbums(query: String, platform: String) {
        if (query.isBlank()) return
        StatsReporter.reportSearch(query, platform)
        viewModelScope.launch {
            isSearchingAlbum = true
            albumSearchResults = emptyList()
            try {
                val results = withTimeoutOrNull(15_000L) {
                    val matchedEntry = findPluginForPlatform(platform)
                    if (matchedEntry != null) {
                        val searchSource = matchedEntry.sources.firstOrNull() ?: "search"
                        val searchResult = lxPluginManager.search(matchedEntry.id, searchSource, query, 15000, 1, "album")
                        searchResult.songs.mapNotNull { song ->
                            com.yindong.music.data.model.AlbumSearchResult(
                                id = song.platformId.ifBlank { song.id.toString() },
                                title = song.title,
                                artist = song.artist,
                                coverUrl = song.coverUrl,
                                platform = matchedEntry.info.name,
                                platformId = song.platformId,
                                pluginRawJson = song.pluginRawJson,
                                lxSourceKey = song.lxSourceKey,
                                lxPluginId = song.lxPluginId,
                            )
                        }
                    } else {
                        emptyList()
                    }
                } ?: emptyList()
                albumSearchResults = results
            } catch (e: Exception) {
                Log.e(TAG, "专辑搜索失败", e)
            } finally {
                isSearchingAlbum = false
            }
        }
    }

    /**
     * 搜索歌手
     * 参考 MusicFree: plugin.methods.search(query, page, "artist")
     */
    fun searchArtists(query: String, platform: String) {
        if (query.isBlank()) return
        StatsReporter.reportSearch(query, platform)
        viewModelScope.launch {
            isSearchingArtist = true
            artistSearchResults = emptyList()
            try {
                val results = withTimeoutOrNull(15_000L) {
                    val matchedEntry = findPluginForPlatform(platform)
                    if (matchedEntry != null) {
                        val searchSource = matchedEntry.sources.firstOrNull() ?: "search"
                        val searchResult = lxPluginManager.search(matchedEntry.id, searchSource, query, 15000, 1, "artist")
                        searchResult.songs.mapNotNull { song ->
                            com.yindong.music.data.model.ArtistSearchResult(
                                id = song.platformId.ifBlank { song.id.toString() },
                                name = song.artist.ifBlank { song.title },
                                avatarUrl = song.coverUrl.ifBlank { song.artistPicUrl },
                                platform = matchedEntry.info.name,
                                platformId = song.platformId,
                                pluginRawJson = song.pluginRawJson,
                                lxSourceKey = song.lxSourceKey,
                                lxPluginId = song.lxPluginId,
                            )
                        }
                    } else {
                        emptyList()
                    }
                } ?: emptyList()
                artistSearchResults = results
            } catch (e: Exception) {
                Log.e(TAG, "作者搜索失败", e)
            } finally {
                isSearchingArtist = false
            }
        }
    }

    /**
     * 搜索歌单
     * 参考 MusicFree: plugin.methods.search(query, page, "sheet")
     */
    fun searchSheets(query: String, platform: String) {
        if (query.isBlank()) return
        StatsReporter.reportSearch(query, platform)
        viewModelScope.launch {
            isSearchingSheet = true
            sheetSearchResults = emptyList()
            try {
                val results = withTimeoutOrNull(15_000L) {
                    val matchedEntry = findPluginForPlatform(platform)
                    if (matchedEntry != null) {
                        val searchSource = matchedEntry.sources.firstOrNull() ?: "search"
                        val searchResult = lxPluginManager.search(matchedEntry.id, searchSource, query, 15000, 1, "playlist")
                        searchResult.songs.mapNotNull { song ->
                            com.yindong.music.data.model.MusicSheetSearchResult(
                                id = song.platformId.ifBlank { song.id.toString() },
                                title = song.title,
                                coverUrl = song.coverUrl,
                                creator = song.artist,
                                platform = matchedEntry.info.name,
                                platformId = song.platformId,
                                pluginRawJson = song.pluginRawJson,
                                lxSourceKey = song.lxSourceKey,
                                lxPluginId = song.lxPluginId,
                            )
                        }
                    } else {
                        emptyList()
                    }
                } ?: emptyList()
                sheetSearchResults = results
            } catch (e: Exception) {
                Log.e(TAG, "歌单搜索失败", e)
            } finally {
                isSearchingSheet = false
            }
        }
    }

    /**
     * 查找平台对应的插件（精确→模糊→源key映射）
     */
    private fun findPluginForPlatform(platformName: String): PluginEntry? {
        // 1. 精确匹配
        var entry = lxPlugins.find { it.info.name == platformName }
        // 2. 模糊匹配
        if (entry == null) {
            entry = lxPlugins.find {
                it.info.name.contains(platformName, ignoreCase = true) ||
                platformName.contains(it.info.name, ignoreCase = true)
            }
        }
        // 3. 源 key 映射
        if (entry == null) {
            val lxSource = platformToLxSource(platformName)
            if (lxSource.isNotBlank()) {
                entry = lxPlugins.find { lxSource in it.sources }
            }
        }
        return entry
    }

    /**
     * 将平台名映射到官方API搜索路径
     * 支持模糊匹配：如插件名"酷狗"也能路由到 KugouApi 搜索
     */
    fun resolveSearchPlatform(platformName: String): String {
        // 1. 精确匹配
        when (platformName) {
            "网易云", "酷我音乐", "酷狗音乐", "QQ音乐" -> return platformName
        }
        // 2. 模糊匹配（包含关键词）
        return when {
            platformName.contains("网易", ignoreCase = true) || platformName.contains("云音乐", ignoreCase = true) -> "网易云"
            platformName.contains("酷我", ignoreCase = true) -> "酷我音乐"
            platformName.contains("酷狗", ignoreCase = true) -> "酷狗音乐"
            platformName.contains("QQ", ignoreCase = true) || platformName.contains("腾讯", ignoreCase = true) -> "QQ音乐"
            // 3. 源 key 映射
            else -> {
                val lxSource = platformToLxSource(platformName)
                when (lxSource) {
                    "wy" -> "网易云"
                    "kw" -> "酷我音乐"
                    "kg" -> "酷狗音乐"
                    "tx" -> "QQ音乐"
                    else -> platformName // 返回原名，走插件搜索路径
                }
            }
        }
    }

    fun loadAlbumDetail(album: com.yindong.music.data.model.AlbumSearchResult) {
        viewModelScope.launch {
            isLoadingDetail = true
            detailTitle = album.title
            detailCoverUrl = album.coverUrl
            detailSubtitle = album.artist
            detailSongs = emptyList()
            try {
                val pluginId = album.lxPluginId
                val rawJson = album.pluginRawJson
                if (pluginId.isBlank() || rawJson.isBlank()) {
                    detailSongs = emptyList()
                    return@launch
                }
                val entry = findPluginForPlatform(album.platform) ?: lxPlugins.find { it.id == pluginId }
                val source = entry?.sources?.firstOrNull() ?: album.lxSourceKey.ifBlank { "search" }
                val result = withTimeoutOrNull(15_000L) {
                    lxPluginManager.getAlbumInfo(pluginId, source, rawJson, 15000)
                }
                detailSongs = result?.songs ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "获取专辑详情失败", e)
                detailSongs = emptyList()
            } finally {
                isLoadingDetail = false
            }
        }
    }

    fun loadArtistWorks(artist: com.yindong.music.data.model.ArtistSearchResult) {
        viewModelScope.launch {
            isLoadingDetail = true
            detailTitle = artist.name
            detailCoverUrl = artist.avatarUrl
            detailSubtitle = "${artist.songCount} 首歌曲"
            detailSongs = emptyList()
            try {
                val pluginId = artist.lxPluginId
                val rawJson = artist.pluginRawJson
                if (pluginId.isBlank() || rawJson.isBlank()) {
                    detailSongs = emptyList()
                    return@launch
                }
                val entry = findPluginForPlatform(artist.platform) ?: lxPlugins.find { it.id == pluginId }
                val source = entry?.sources?.firstOrNull() ?: artist.lxSourceKey.ifBlank { "search" }
                val result = withTimeoutOrNull(15_000L) {
                    lxPluginManager.getArtistWorks(pluginId, source, rawJson, 15000)
                }
                detailSongs = result?.songs ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "获取作者作品失败", e)
                detailSongs = emptyList()
            } finally {
                isLoadingDetail = false
            }
        }
    }

    fun loadMusicSheetDetail(sheet: com.yindong.music.data.model.MusicSheetSearchResult) {
        viewModelScope.launch {
            isLoadingDetail = true
            detailTitle = sheet.title
            detailCoverUrl = sheet.coverUrl
            detailSubtitle = buildString {
                if (sheet.creator.isNotBlank()) append(sheet.creator)
                if (sheet.songCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${sheet.songCount} 首")
                }
            }
            detailSongs = emptyList()
            try {
                val pluginId = sheet.lxPluginId
                val rawJson = sheet.pluginRawJson
                if (pluginId.isBlank() || rawJson.isBlank()) {
                    detailSongs = emptyList()
                    return@launch
                }
                val entry = findPluginForPlatform(sheet.platform) ?: lxPlugins.find { it.id == pluginId }
                val source = entry?.sources?.firstOrNull() ?: sheet.lxSourceKey.ifBlank { "search" }
                val result = withTimeoutOrNull(15_000L) {
                    lxPluginManager.getMusicSheetInfo(pluginId, source, rawJson, 15000)
                }
                detailSongs = result?.songs ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "获取歌单详情失败", e)
                detailSongs = emptyList()
            } finally {
                isLoadingDetail = false
            }
        }
    }

    fun playDetailSong(index: Int) {
        if (detailSongs.isNotEmpty() && index in detailSongs.indices) {
            playPlaylist(detailSongs, index)
        }
    }

    fun playAllDetailSongs() {
        if (detailSongs.isNotEmpty()) {
            playPlaylist(detailSongs, 0)
        }
    }

    fun clearDetail() {
        detailSongs = emptyList()
        detailTitle = ""
        detailCoverUrl = ""
        detailSubtitle = ""
        isLoadingDetail = false
    }

    fun isShareLink(url: String): Boolean {
        return url.contains("douyin.com") || url.contains("iesdouyin.com") ||
               url.contains("music.163.com") || url.contains("y.qq.com") ||
               url.contains("kuwo.cn") || url.contains("kugou.com")
    }

    fun parseAndPlay(url: String) {
        viewModelScope.launch {
            isParsing = true
            parseError = null
            if (!passSecurityGate(
                    action = MusicPlaybackGate.Action.PARSE_LINK,
                    defaultMessage = "当前环境存在风险，已阻止解析",
                    parseScope = true,
                )
            ) {
                isParsing = false
                return@launch
            }
            try {
                val rawSong = MusicApiService.parseDouyinLink(url)
                if (rawSong != null) {
                    // 为汽水音乐/抖音歌曲补全 lxPluginId，确保播放时能路由到正确的插件
                    // 仅从已真正加载到 QuickJS 运行时、且当前启用的插件中匹配，避免幻影插件与禁用插件被调用
                    val loadedIds = lxPluginManager.getAllPluginEntries().map { it.id }.toSet()
                    val song = if (rawSong.lxPluginId.isBlank() && rawSong.lxSourceKey.isNotBlank() && loadedIds.isNotEmpty()) {
                        val matchedPlugin = lxPlugins.find { it.id in loadedIds && isPluginEnabled(it.id) && rawSong.lxSourceKey in it.sources }
                        if (matchedPlugin != null) {
                            Log.d(TAG, "parseAndPlay: matched plugin ${matchedPlugin.info.name} for source=${rawSong.lxSourceKey}")
                            rawSong.copy(lxPluginId = matchedPlugin.id)
                        } else rawSong
                    } else rawSong
                    playSong(song)
                } else {
                    parseError = "解析失败，请检查链接"
                }
            } catch (e: Exception) {
                parseError = "解析失败: ${e.message}"
            } finally {
                isParsing = false
            }
        }
    }

    // ── 分类搜索 ──
    fun searchCategory(category: String) {
        viewModelScope.launch {
            isCategoryLoading = true
            try {
                // 使用落雪插件搜索分类关键词
                val results = if (lxSelectedSource == LX_SOURCE_ALL) {
                    searchAllSources(category)
                } else if (lxSelectedSource.isNotBlank() && lxSupportsAction("search")) {
                    withTimeoutOrNull(lxTimeoutMs) {
                        lxPluginManager.search(lxSelectedPluginId, lxSelectedSource, category, lxTimeoutMs).songs ?: emptyList()
                    } ?: emptyList()
                } else {
                    emptyList()
                }
                categorySongs = results
            } catch (e: Exception) {
                Log.e(TAG, "分类搜索失败", e)
            } finally {
                isCategoryLoading = false
            }
        }
    }

    // ── 歌单辅助 ──
    fun getUserPlaylistById(id: Long): Playlist? = myPlaylists.find { it.id == id }

    fun addSongToPlaylist(playlistId: Long, song: Song) = addToPlaylist(playlistId, song)

    fun removeSongFromPlaylist(playlistId: Long, song: Song) {
        removeFromPlaylist(playlistId, song.id)
    }

    fun exportPlaylistCopy(playlistId: Long) {
        val pl = myPlaylists.find { it.id == playlistId } ?: return
        showToast("歌单「${pl.name}」已导出")
    }

    // ── 收藏辅助 ──
    fun isFavorite(song: Song): Boolean = favoriteSongs.any { it.id == song.id }

    fun removeFavorite(song: Song) {
        favoriteSongs = favoriteSongs.filter { it.id != song.id }
        LocalStorage.saveFavorites(favoriteSongs)
    }

    fun addFavoriteManual(title: String, artist: String) {
        if (title.isBlank()) return
        val song = Song(
            id = System.currentTimeMillis(),
            title = title,
            artist = artist.ifBlank { "未知" },
            album = "",
        )
        favoriteSongs = favoriteSongs + song
        LocalStorage.saveFavorites(favoriteSongs)
    }

    fun importFavoritesFromText(text: String) {
        importFavorites(text)
    }

    fun exportFavoritesAsText(): String {
        return favoriteSongs.joinToString("\n") { "${it.title} - ${it.artist}" }
    }

    fun fetchMissingCovers(playlistId: Long) {
        // 异步为歌单中缺失封面的歌曲搜索封面
        viewModelScope.launch {
            // Stub: 未来可对接搜索 API 补全封面
        }
    }

    fun fetchMissingFavCovers() {
        viewModelScope.launch {
            // Stub: 未来可对接搜索 API 补全收藏封面
        }
    }

    // ── 播放控制别名 ──
    fun togglePlay() = togglePlayPause()
    fun togglePlayMode() = changePlayMode()

    // ── 音质别名 ──
    fun setQuality(quality: MusicApiConfig.Quality) = changeQuality(quality)

    // ── 歌词颜色别名 ──
    fun changeLyricCurrentColor(color: Int) = changeHighlightLyricColor(color)
    fun changeLyricNormalColor(color: Int) = changeNormalLyricColor(color)
    fun changeLyricFontSize(size: Int) = changePlayerLyricSize(size)

    // ── QQ Cookie 别名 ──
    fun setQQCookie(cookie: String) = updateQQCookie(cookie)

    // ── 均衡器预设 ──
    fun getCurrentPresetName(): String = currentPreset.displayName
    private data class SceneParams(
        val eq: List<Int>,
        val bass: Int = 0,
        val virt: Int = 0,
        val reverbRoom: Int = 0,
        val reverbDamp: Int = 0,
        val reverbWet: Int = 0,
        val loud: Int = 0,
    )

    private data class ReverbProfile(
        val decayTime: Int,
        val decayHFRatio: Int,
        val roomLevel: Int,
        val reverbLevel: Int,
        val reflectionsLevel: Int,
        val reflectionsDelay: Int,
        val reverbDelay: Int,
        val diffusion: Int,
        val density: Int,
    )

    private fun getSceneParams(preset: EqPreset): SceneParams = when (preset) {
        EqPreset.FLAT -> SceneParams(listOf(0, 0, 0, 0, 0))
        EqPreset.BASS -> SceneParams(listOf(4, 3, 1, 0, 0), bass = 45, virt = 5, reverbRoom = 10, reverbDamp = 50, reverbWet = 8)
        EqPreset.VOCAL -> SceneParams(listOf(-1, 2, 4, 3, 1), bass = 0, virt = 18, reverbRoom = 22, reverbDamp = 55, reverbWet = 15)
        EqPreset.POP -> SceneParams(listOf(1, 3, 3, 2, 2), bass = 18, virt = 16, reverbRoom = 25, reverbDamp = 45, reverbWet = 18)
        EqPreset.ROCK -> SceneParams(listOf(4, 1, 0, 2, 4), bass = 32, virt = 22, reverbRoom = 28, reverbDamp = 42, reverbWet = 18)
        EqPreset.JAZZ -> SceneParams(listOf(2, 0, 1, 3, 2), bass = 12, virt = 50, reverbRoom = 52, reverbDamp = 50, reverbWet = 35)
        EqPreset.CLASSICAL -> SceneParams(listOf(0, 0, 0, 1, 3), bass = 0, virt = 40, reverbRoom = 70, reverbDamp = 40, reverbWet = 35)
        EqPreset.ELECTRONIC -> SceneParams(listOf(4, 1, -1, 1, 4), bass = 35, virt = 55, reverbRoom = 30, reverbDamp = 32, reverbWet = 20)
        EqPreset.HIPHOP -> SceneParams(listOf(4, 3, 1, 2, 3), bass = 38, virt = 12, reverbRoom = 18, reverbDamp = 50, reverbWet = 12)
        EqPreset.LIVE -> SceneParams(listOf(0, 1, 3, 2, 0), bass = 10, virt = 65, reverbRoom = 62, reverbDamp = 38, reverbWet = 42)
        EqPreset.NIGHT -> SceneParams(listOf(-2, 0, 1, 1, -2), bass = 0, virt = 10, reverbRoom = 20, reverbDamp = 65, reverbWet = 12)
        EqPreset.ACG -> SceneParams(listOf(-1, 1, 3, 4, 3), bass = 10, virt = 20, reverbRoom = 22, reverbDamp = 48, reverbWet = 15)
        // ── 以下预设参数来自 lx-music-desktop-master freqsPreset（10 频段映射到 5 频段） ──
        // dance: 4,3,-4,-6,0,0,3,4,4,5 → 低频强、中频凹、高频提升
        EqPreset.DANCE -> SceneParams(listOf(4, -4, 0, 3, 4), bass = 25, virt = 28, reverbRoom = 20, reverbDamp = 40, reverbWet = 15)
        // slow: 5,4,2,0,-2,0,3,6,7,8 → 两侧提升、中频微降，适合慢歌
        EqPreset.SLOW -> SceneParams(listOf(5, 2, -2, 3, 7), bass = 8, virt = 35, reverbRoom = 45, reverbDamp = 50, reverbWet = 28)
        // subwoofer: 8,7,5,4,0,0,0,0,0,0 → 仅低频增强
        EqPreset.SUBWOOFER -> SceneParams(listOf(8, 5, 0, 0, 0), bass = 60, virt = 5, reverbRoom = 5, reverbDamp = 50, reverbWet = 5)
        // soft: -5,-5,-4,-4,3,2,4,4,0,0 → 削高低频、提升中高频
        EqPreset.SOFT -> SceneParams(listOf(-5, -4, 3, 4, 0), bass = 0, virt = 15, reverbRoom = 15, reverbDamp = 60, reverbWet = 10)
    }

    private fun getReverbProfile(preset: EqPreset): ReverbProfile? = when (preset) {
        EqPreset.FLAT -> null
        EqPreset.BASS -> ReverbProfile(300, 900, -1800, -2400, -2000, 5, 8, 800, 900)
        EqPreset.VOCAL -> ReverbProfile(800, 650, -1500, -1800, -1600, 8, 12, 850, 800)
        EqPreset.POP -> ReverbProfile(1000, 850, -1200, -1500, -1400, 10, 15, 880, 850)
        EqPreset.ROCK -> ReverbProfile(1500, 750, -1000, -1200, -1200, 15, 20, 900, 880)
        EqPreset.JAZZ -> ReverbProfile(2600, 580, -600, -800, -800, 18, 25, 950, 940)
        EqPreset.CLASSICAL -> ReverbProfile(3500, 480, -600, -800, -800, 22, 32, 970, 960)
        EqPreset.ELECTRONIC -> ReverbProfile(800, 1000, -1000, -1400, -1200, 8, 12, 820, 880)
        EqPreset.HIPHOP -> ReverbProfile(450, 800, -1800, -2200, -2000, 5, 10, 800, 850)
        EqPreset.LIVE -> ReverbProfile(3200, 620, -500, -600, -800, 20, 30, 985, 980)
        EqPreset.NIGHT -> ReverbProfile(600, 380, -2000, -2500, -2200, 8, 12, 700, 750)
        EqPreset.ACG -> ReverbProfile(900, 750, -1500, -1800, -1500, 10, 14, 860, 820)
        // ── lx-music-desktop-master 预设的混响参数 ──
        EqPreset.DANCE -> ReverbProfile(500, 900, -1500, -2000, -1800, 5, 10, 820, 880)
        EqPreset.SLOW -> ReverbProfile(1800, 600, -800, -1200, -1000, 12, 20, 900, 880)
        EqPreset.SUBWOOFER -> ReverbProfile(200, 1000, -2200, -2800, -2400, 3, 5, 700, 800)
        EqPreset.SOFT -> ReverbProfile(700, 500, -1800, -2200, -2000, 8, 12, 800, 820)
    }

    fun applyPreset(preset: EqPreset) {
        currentPreset = preset
        LocalStorage.saveEqPreset(preset.name)
        val scene = getSceneParams(preset)
        val eqTargets = scene.eq
        val bandCount = equalizerBands.size
        for (i in 0 until bandCount) {
            setEqualizerBand(i, eqTargets.getOrElse(i) { 0 })
        }
        updateBassBoost(scene.bass)
        updateVirtualizer(scene.virt)
        val profile = getReverbProfile(preset)
        if (profile != null) {
            reverbRoomSize = scene.reverbRoom.coerceIn(0, 100)
            reverbDamping = scene.reverbDamp.coerceIn(0, 100)
            reverbLevel = scene.reverbWet.coerceIn(0, 100)
            setReverbDirect(profile)
        } else {
            updateReverbRoomSize(scene.reverbRoom)
            updateReverbDamping(scene.reverbDamp)
            updateReverbWet(scene.reverbWet)
        }
        updateLoudnessGain(scene.loud)
    }

    fun updateBassBoost(value: Int) {
        val clamped = value.coerceIn(0, 100)
        bassBoostStrength = clamped
        bassBoostEnabled = clamped > 0
        try {
            bassBoostEffect?.enabled = equalizerEnabled && clamped > 0
            bassBoostEffect?.setStrength((clamped * 10).coerceIn(0, 1000).toShort())
        } catch (_: Exception) {}
    }

    fun updateVirtualizer(value: Int) {
        val clamped = value.coerceIn(0, 100)
        virtualizerStrength = clamped
        virtualizerEnabled = clamped > 0
        try {
            virtualizerEffect?.enabled = equalizerEnabled && clamped > 0
            virtualizerEffect?.setStrength((clamped * 10).coerceIn(0, 1000).toShort())
        } catch (_: Exception) {}
    }

    fun updateReverbRoomSize(value: Int) {
        reverbRoomSize = value.coerceIn(0, 100)
        applyMappedReverb()
    }

    fun updateReverbDamping(value: Int) {
        reverbDamping = value.coerceIn(0, 100)
        applyMappedReverb()
    }

    fun updateReverbWet(value: Int) {
        reverbLevel = value.coerceIn(0, 100)
        applyMappedReverb()
    }

    fun updateReverbLevel(value: Int) {
        updateReverbWet(value)
    }

    fun updateLoudnessGain(value: Int) {
        val clamped = value.coerceIn(0, 100)
        loudnessGain = clamped
        try {
            if (!equalizerEnabled || clamped <= 0) {
                loudnessEnhancerEffect?.enabled = false
            } else {
                loudnessEnhancerEffect?.enabled = true
                loudnessEnhancerEffect?.setTargetGain((clamped * 20).coerceAtMost(2000))
            }
        } catch (_: Exception) {}
    }

    private fun setReverbDirect(profile: ReverbProfile) {
        try {
            if (!equalizerEnabled || reverbLevel <= 0) {
                reverbEffect?.enabled = false
                return
            }
            reverbEffect?.enabled = true
            reverbEffect?.decayTime = profile.decayTime.coerceIn(100, 20000)
            reverbEffect?.decayHFRatio = profile.decayHFRatio.coerceIn(100, 2000).toShort()
            reverbEffect?.roomLevel = profile.roomLevel.coerceIn(-9000, 0).toShort()
            reverbEffect?.reverbLevel = profile.reverbLevel.coerceIn(-9000, 2000).toShort()
            reverbEffect?.reflectionsLevel = profile.reflectionsLevel.coerceIn(-9000, 1000).toShort()
            reverbEffect?.reflectionsDelay = profile.reflectionsDelay.coerceIn(0, 300)
            reverbEffect?.reverbDelay = profile.reverbDelay.coerceIn(0, 100)
            reverbEffect?.diffusion = profile.diffusion.coerceIn(0, 1000).toShort()
            reverbEffect?.density = profile.density.coerceIn(0, 1000).toShort()
        } catch (_: Exception) {}
    }

    private fun applyMappedReverb() {
        val wet = reverbLevel.coerceIn(0, 100)
        val room = reverbRoomSize.coerceIn(0, 100)
        val damping = reverbDamping.coerceIn(0, 100)
        try {
            if (!equalizerEnabled || wet <= 0) {
                reverbEffect?.enabled = false
                return
            }
            reverbEffect?.enabled = true
            reverbEffect?.decayTime = (100 + room * 49).coerceIn(100, 5000)
            reverbEffect?.decayHFRatio = (1900 - damping * 18).coerceIn(100, 1900).toShort()
            reverbEffect?.roomLevel = (-3000 + wet * 30).coerceIn(-3000, 0).toShort()
            reverbEffect?.reverbLevel = (-6000 + wet * 60).coerceIn(-6000, 0).toShort()
            reverbEffect?.reflectionsDelay = (2 + room * 28 / 100).coerceIn(0, 30)
            reverbEffect?.reflectionsLevel = (-4000 + wet * 30).coerceIn(-4000, -1000).toShort()
            reverbEffect?.reverbDelay = (5 + room * 35 / 100).coerceIn(0, 40)
            reverbEffect?.diffusion = (600 + room * 4).coerceIn(600, 1000).toShort()
            reverbEffect?.density = (600 + room * 4).coerceIn(600, 1000).toShort()
        } catch (_: Exception) {}
    }

    fun formatFrequency(hz: Int): String {
        return if (hz >= 1000) "${hz / 1000}k" else "${hz}Hz"
    }

    // ── API 更新方法（MineScreen 使用）──
    fun updateQQPlayApi(url: String) {
        qqPlayApiValue = url
        LocalStorage.saveQQPlayApi(url)
    }

    fun updateDouyinParseApi(url: String) {
        douyinParseApiValue = url
        LocalStorage.saveDouyinParseApi(url)
    }

    fun updateQQMusicApiKey(key: String) = updateApiKey("qq_music", key)
    fun updateNeteaseApiKey(key: String) = updateApiKey("netease", key)
    fun updateKuwoApiKey(key: String) = updateApiKey("kuwo", key)
    fun updateMiguApiKey(key: String) = updateApiKey("migu", key)
    fun updateKugouApiKey(key: String) = updateApiKey("kugou", key)
    fun updateDouyinApiKey(key: String) = updateApiKey("douyin", key)

    /**
     * Import a JS plugin (add to the multi-plugin list).
     * If the same plugin (by hash) already exists, it is replaced.
     */
    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
    private fun sha256Hex(text: String): String {
        return sha256Hex(text.toByteArray(Charsets.UTF_8))
    }

    private fun normalizePluginScriptForHash(script: String): String {
        return script.replace("\uFEFF", "").trim()
    }

    private suspend fun ensurePluginPolicyLoaded() {
        if (RemoteConfig.isLoaded) return
        val loaded = try {
            RemoteConfig.fetch()
        } catch (e: Exception) {
            Log.w(TAG, "加载插件策略失败: ${e.message}")
            false
        }
        if (!loaded) {
            Log.w(TAG, "插件策略未加载成功，继续使用本地默认策略: ${RemoteConfig.lastError}")
        }
    }

    private suspend fun ensurePluginHashAllowed(pluginHash: String, script: String? = null) {
        // 允许所有插件导入，跳过白名单检查
        return
    }
    suspend fun importLxPlugin(uri: Uri): Result<Unit> {
        return try {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // ignore: fallback to transient read permission
            }

            // ── 将插件复制到内部存储 (解决 content:// URI 过期导致重启后无法重新加载) ──
            val app = getApplication<Application>()
            val maxPluginSize = 2 * 1024 * 1024
            val scriptBytes = app.contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    total += n
                    if (total > maxPluginSize) throw IllegalArgumentException("插件大小不能超过2MB")
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            } ?: throw IllegalStateException("读取插件文件失败")
            val script = String(scriptBytes, Charsets.UTF_8)
            val pluginHash = sha256Hex(scriptBytes)
            ensurePluginHashAllowed(pluginHash, script)

            val pluginsDir = java.io.File(app.filesDir, "lx_plugins")
            if (!pluginsDir.exists()) pluginsDir.mkdirs()
            val rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?') ?: ""
            val safeRawName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val fileName = safeRawName.takeIf { it.endsWith(".js") && it.isNotBlank() }
                ?: "plugin_${System.currentTimeMillis()}.js"
            val localFile = java.io.File(pluginsDir, fileName)
            localFile.writeText(script, Charsets.UTF_8)
            val fileUri = android.net.Uri.fromFile(localFile).toString()

            val entry = lxPluginManager.loadPluginFromScript(script, fileUri, LuoxueRuntimeOptions(), getApplication<Application>()).getOrThrow()

            lxPlugins = (lxPlugins.filter { it.id != entry.id } + entry)
            persistPlugins()

            // Legacy compat: keep single-plugin fields in sync with first plugin
            lxPluginUri = fileUri
            lxPluginHash = entry.id
            lxPluginInfo = entry.info
            LocalStorage.saveLxPluginUri(fileUri)
            LocalStorage.saveLxPluginHash(entry.id)
            LocalStorage.saveLxPluginInfo(entry.info)

            // Rebuild combined sources
            rebuildSources()

            // Auto-select if nothing selected
            if (lxSelectedPluginId.isBlank() || !lxPluginManager.isPluginLoaded(lxSelectedPluginId)) {
                lxSelectedPluginId = entry.id
                LocalStorage.saveLxSelectedPluginId(lxSelectedPluginId)
            }
            if (lxSelectedSource.isBlank() || lxSelectedSource !in lxSources) {
                lxSelectedSource = entry.sources.firstOrNull().orEmpty()
                LocalStorage.saveLxSelectedSource(lxSelectedSource)
            }

            apiMode = "lx_plugin"
            LocalStorage.saveApiMode(apiMode)
            refreshActivePluginSupportedQualities()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import a JS plugin from a remote URL.
     * Downloads the script, saves it to internal storage, then loads it.
     */
    suspend fun importLxPluginFromUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw IllegalArgumentException("仅支持HTTP/HTTPS插件URL")
            }
            val maxPluginBytes = 2L * 1024 * 1024
            
            // 创建带超时配置的OkHttpClient
            val client = OkHttpClient().newBuilder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
                
            Log.d("PluginDownload", "📥 开始下载插件: $url")
            val startTime = System.currentTimeMillis()
            
            val scriptBytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("下载失败: HTTP ${response.code} ${response.message}")
                }
                val body = response.body ?: throw IllegalStateException("下载内容为空")
                val contentLength = body.contentLength()
                Log.d("PluginDownload", "📊 插件大小: ${contentLength} bytes")
                
                if (contentLength > maxPluginBytes) {
                    throw IllegalArgumentException("插件大小不能超过2MB (实际: ${contentLength} bytes)")
                }
                body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        total += n
                        if (total > maxPluginBytes) {
                            throw IllegalArgumentException("插件大小不能超过2MB")
                        }
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                }
            }
            
            val downloadTime = System.currentTimeMillis() - startTime
            Log.d("PluginDownload", "✅ 下载完成，耗时: ${downloadTime}ms，大小: ${scriptBytes.size} bytes")

            val script = String(scriptBytes, Charsets.UTF_8)

            // ── 检测多插件 JSON 格式：{ "plugins": [{ "url": "..." }, ...] } ──
            val trimmed = script.trim()
            if (trimmed.startsWith("{")) {
                try {
                    val json = org.json.JSONObject(trimmed)
                    val pluginsArray = json.optJSONArray("plugins")
                    if (pluginsArray != null && pluginsArray.length() > 0) {
                        Log.d("PluginDownload", "📋 检测到多插件JSON，共 ${pluginsArray.length()} 个插件，开始逐个导入...")
                        var successCount = 0
                        var failCount = 0
                        val errors = mutableListOf<String>()
                        for (i in 0 until pluginsArray.length()) {
                            val pluginObj = pluginsArray.getJSONObject(i)
                            val pluginUrl = pluginObj.optString("url", "")
                            val pluginName = pluginObj.optString("name", "未知")
                            if (pluginUrl.isBlank()) {
                                Log.w("PluginDownload", "⚠️ 第 ${i + 1} 个插件缺少url，跳过: $pluginName")
                                failCount++
                                errors.add("$pluginName: 缺少url")
                                continue
                            }
                            Log.d("PluginDownload", "📥 导入第 ${i + 1}/${pluginsArray.length()} 个插件: $pluginName ($pluginUrl)")
                            try {
                                importLxPluginFromUrl(pluginUrl).getOrThrow()
                                successCount++
                                Log.d("PluginDownload", "✅ 第 ${i + 1} 个插件导入成功: $pluginName")
                            } catch (e: Exception) {
                                failCount++
                                errors.add("$pluginName: ${e.message}")
                                Log.w("PluginDownload", "⚠️ 第 ${i + 1} 个插件导入失败: $pluginName - ${e.message}")
                            }
                        }
                        val msg = "多插件导入完成: 成功 $successCount/${pluginsArray.length()}" +
                            if (errors.isNotEmpty()) "，失败: ${errors.joinToString("; ")}" else ""
                        Log.d("PluginDownload", "🎉 $msg")
                        return@withContext if (successCount > 0) Result.success(Unit)
                            else Result.failure(IllegalStateException(msg))
                    }
                } catch (_: org.json.JSONException) {
                    // 不是合法JSON，按普通JS脚本处理
                    Log.d("PluginDownload", "📄 内容非多插件JSON格式，按JS脚本处理")
                }
            }

            val pluginHash = sha256Hex(scriptBytes)
            Log.d("PluginDownload", "🔒 插件Hash: $pluginHash")
            ensurePluginHashAllowed(pluginHash, script)

            // Save to internal storage for persistence & reload on startup
            val pluginsDir = java.io.File(getApplication<Application>().filesDir, "lx_plugins")
            if (!pluginsDir.exists()) pluginsDir.mkdirs()
            val rawFileName = url.substringAfterLast('/').substringBefore('?')
            val safeFileName = rawFileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val fileName = safeFileName.takeIf { it.endsWith(".js") && it.isNotBlank() }
                ?: "plugin_${System.currentTimeMillis()}.js"
            val localFile = java.io.File(pluginsDir, fileName)
            Log.d("PluginDownload", "💾 保存到本地: ${localFile.absolutePath}")
            localFile.writeText(script, Charsets.UTF_8)
            val fileUri = android.net.Uri.fromFile(localFile).toString()

            Log.d("PluginDownload", "⚙️ 正在加载插件脚本...")
            val entry = lxPluginManager.loadPluginFromScript(script, fileUri, LuoxueRuntimeOptions(), getApplication<Application>()).getOrThrow()
            Log.d("PluginDownload", "✅ 插件加载成功: ${entry.info.name} (ID: ${entry.id})")

            withContext(Dispatchers.Main) {
                // 添加到插件列表（去重）
                val existingIds = lxPlugins.map { it.id }.toSet()
                if (entry.id !in existingIds) {
                    lxPlugins = lxPlugins + entry
                    Log.d("PluginDownload", "📝 已添加插件到列表: ${entry.info.name} (总数: ${lxPlugins.size})")
                } else {
                    Log.d("PluginDownload", "⚠️ 插件已存在，跳过添加: ${entry.info.name}")
                }
                
                persistPlugins()
                rebuildSources()

                // 仅在当前无选中插件时才设置默认值（不覆盖已有选择）
                if (lxSelectedPluginId.isBlank() || !lxPluginManager.isPluginLoaded(lxSelectedPluginId)) {
                    lxSelectedPluginId = entry.id
                    LocalStorage.saveLxSelectedPluginId(lxSelectedPluginId)
                    Log.d("PluginDownload", "🎯 设置默认选中插件: ${entry.info.name}")
                }

                if (lxSelectedSource.isBlank() || lxSelectedSource !in lxSources) {
                    lxSelectedSource = entry.sources.firstOrNull().orEmpty()
                    LocalStorage.saveLxSelectedSource(lxSelectedSource)
                }

                apiMode = "lx_plugin"
                LocalStorage.saveApiMode(apiMode)
            }

            Log.d("PluginDownload", "🎉 插件导入完成: $url")
            refreshActivePluginSupportedQualities()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PluginDownload", "❌ 插件导入失败: $url", e)
            Result.failure(e)
        }
    }

    /** Remove a specific plugin by ID (内置插件不可移除). */
    fun removeLxPluginById(pluginId: String) {
        if (isBuiltinPlugin(pluginId)) return  // 内置插件不可移除
        lxPluginManager.removePlugin(pluginId)
        lxPlugins = lxPlugins.filter { it.id != pluginId }
        persistPlugins()
        rebuildSources()

        // If the removed plugin was selected, reset selection
        if (lxSelectedPluginId == pluginId) {
            val first = lxPlugins.firstOrNull()
            lxSelectedPluginId = first?.id.orEmpty()
            lxSelectedSource = first?.sources?.firstOrNull().orEmpty()
            LocalStorage.saveLxSelectedPluginId(lxSelectedPluginId)
            LocalStorage.saveLxSelectedSource(lxSelectedSource)
        }

        // Legacy compat
        if (lxPlugins.isEmpty()) {
            lxPluginUri = ""
            lxPluginHash = ""
            lxPluginInfo = PluginInfo()
            LocalStorage.saveLxPluginUri("")
            LocalStorage.saveLxPluginHash("")
            LocalStorage.saveLxPluginInfo(PluginInfo())
        }
        refreshActivePluginSupportedQualities()
    }

    fun removeLxPlugin() {
        lxPlugins.forEach { if (!isBuiltinPlugin(it.id)) lxPluginManager.removePlugin(it.id) }
        lxPlugins = emptyList()
        lxPluginUri = ""
        lxPluginHash = ""
        lxPluginInfo = PluginInfo()
        lxSelectedSource = ""
        lxSelectedPluginId = ""
        lxSources = emptyList()
        LocalStorage.saveLxPlugins(emptyList())
        LocalStorage.saveLxPluginUri("")
        LocalStorage.saveLxPluginHash("")
        LocalStorage.saveLxPluginInfo(PluginInfo())
        LocalStorage.saveLxSelectedSource("")
        LocalStorage.saveLxSelectedPluginId("")
        refreshActivePluginSupportedQualities()
    }

    fun updateLxSelectedSource(source: String) {
        lxSelectedSource = source
        val currentSources = lxPluginManager.getPluginEntry(lxSelectedPluginId)?.sources.orEmpty()
        if (source !in currentSources) {
            val matchedPluginId = lxPluginManager.getAllPluginEntries()
                .firstOrNull { source in it.sources }
                ?.id
                .orEmpty()
            if (matchedPluginId.isNotBlank()) {
                lxSelectedPluginId = matchedPluginId
                LocalStorage.saveLxSelectedPluginId(matchedPluginId)
            }
        }
        LocalStorage.saveLxSelectedSource(source)
    }

    /** Select a specific plugin + source combination. */
    fun updateLxSelection(pluginId: String, source: String) {
        lxSelectedPluginId = pluginId
        lxSelectedSource = source
        LocalStorage.saveLxSelectedPluginId(pluginId)
        LocalStorage.saveLxSelectedSource(source)
        // Update legacy fields to match
        val entry = lxPlugins.find { it.id == pluginId }
        if (entry != null) {
            lxPluginInfo = entry.info
            lxPluginUri = entry.uri
            lxPluginHash = entry.id
        }
    }

    fun updateLxTimeoutMs(timeoutMs: Long) {
        lxTimeoutMs = timeoutMs.coerceIn(5000L, 30000L)
        LocalStorage.saveLxTimeoutMs(lxTimeoutMs)
    }
    fun updateLxAllowHttp(allow: Boolean) {
        lxAllowHttp = allow
        LocalStorage.saveLxAllowHttp(allow)
    }

    // ═════════════════════════════════════════════════════════════════
    //  MusicFree 插件管理
    // ═════════════════════════════════════════════════════════════════

    /**
     * 从 Uri 导入 MusicFree 插件（用户通过文件选择器选择 .js 文件）
     *
     * 完整流程：文件选择 → 读取内容 → 哈希计算 → 去重校验 → 复制到内部存储 →
     *           沙箱挂载 → 元数据提取 → 持久化
     *
     * @return Result<Unit> 成功或失败（含错误信息）
     */
    suspend fun importMusicFreePlugin(uri: android.net.Uri): Result<Unit> {
        return try {
            val context = getApplication<Application>()
            val result = musicFreePluginManager.importFromUri(context, uri)
            if (result.isSuccess) {
                musicFreePlugins = musicFreePluginManager.getAllPlugins()
                val entry = result.getOrThrow()
                Log.d(TAG, "MusicFree 插件导入成功: ${entry.info.platform}")
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: IllegalStateException("导入失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "importMusicFreePlugin failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 从 URL 在线导入 MusicFree 插件
     */
    suspend fun importMusicFreePluginFromUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val result = musicFreePluginManager.importFromUrl(context, url)
            if (result.isSuccess) {
                musicFreePlugins = musicFreePluginManager.getAllPlugins()
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: IllegalStateException("导入失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "importMusicFreePluginFromUrl failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 卸载 MusicFree 插件 */
    fun removeMusicFreePlugin(pluginId: String) {
        val context = getApplication<Application>()
        musicFreePluginManager.uninstallPlugin(context, pluginId)
        musicFreePlugins = musicFreePluginManager.getAllPlugins()
    }

    /** 启用/禁用 MusicFree 插件 */
    fun toggleMusicFreePlugin(pluginId: String, enabled: Boolean) {
        val context = getApplication<Application>()
        musicFreePluginManager.setEnabled(context, pluginId, enabled)
        musicFreePlugins = musicFreePluginManager.getAllPlugins()
    }

    /** 卸载全部 MusicFree 插件 */
    fun removeAllMusicFreePlugins() {
        val context = getApplication<Application>()
        musicFreePlugins.forEach { musicFreePluginManager.uninstallPlugin(context, it.id) }
        musicFreePlugins = musicFreePluginManager.getAllPlugins()
    }

    /**
     * 通过 MusicFree 插件搜索
     *
     * @param pluginId MusicFree 插件 id
     * @param query 搜索关键词
     * @param page 页码
     * @param type 搜索类型：music/album/artist/sheet
     */
    suspend fun searchByMusicFreePlugin(pluginId: String, query: String, page: Int = 1, type: String = "music"): com.yindong.music.data.musicfree.MusicFreeSearchResult {
        return musicFreePluginManager.search(pluginId, query, page, type)
    }

    /**
     * 通过 MusicFree 插件获取播放链接
     *
     * @param pluginId MusicFree 插件 id
     * @param musicItemJson 搜索结果中的 rawJson
     * @param quality 音质（standard/high/lossless 等）
     */
    suspend fun getMediaSourceFromMusicFree(pluginId: String, musicItemJson: String, quality: String = "standard"): com.yindong.music.data.musicfree.MusicFreeMediaSource {
        return musicFreePluginManager.getMediaSource(pluginId, musicItemJson, quality)
    }

    /** 判断平台名是否对应已启用的 MusicFree 插件 */
    fun findMusicFreePluginByPlatform(platformName: String): com.yindong.music.data.musicfree.MusicFreePluginEntry? {
        return musicFreePlugins.firstOrNull { p ->
            p.enabled && p.mounted && (
                p.info.platform == platformName ||
                p.info.platform.equals(platformName, ignoreCase = true) ||
                p.fileName.contains(platformName, ignoreCase = true)
            )
        }
    }

    private suspend fun reloadAllPlugins(entries: List<PluginEntry>) {
        val loaded = withContext(Dispatchers.IO) {
            val result = mutableListOf<PluginEntry>()
            for (entry in entries) {
                try {
                    val app = getApplication<Application>()
                    val instance = lxPluginManager.load(entry.uri, LuoxueRuntimeOptions(), app).getOrThrow()
                    var finalInfo = instance.info
                    val isFileUri = entry.uri.startsWith("file://") || entry.uri.startsWith("content://")
                    if (finalInfo.name.isBlank() && isFileUri) {
                        Log.w(TAG, "reloadAllPlugins: ${entry.uri} name still empty, re-parsing from file")
                        try {
                            val script = if (entry.uri.startsWith("file://")) {
                                java.io.File(android.net.Uri.parse(entry.uri).path!!).readText()
                            } else {
                                app.contentResolver.openInputStream(android.net.Uri.parse(entry.uri))?.use {
                                    it.bufferedReader().readText()
                                } ?: ""
                            }
                            if (script.isNotBlank()) {
                                val reparsedInfo = com.yindong.music.data.lx.LuoxuePluginManager::class.java
                                    .getDeclaredMethod("parsePluginInfo", String::class.java)
                                    .let { it.isAccessible = true; it.invoke(null, script) } as com.yindong.music.data.lx.PluginInfo
                                if (reparsedInfo.name.isNotBlank()) {
                                    Log.d(TAG, "Re-parsed name: ${reparsedInfo.name}")
                                    finalInfo = reparsedInfo
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Re-parse failed", e)
                        }
                    }
                        result.add(PluginEntry(instance.id, instance.uri, finalInfo, instance.sources, instance.format, initialized = true))
                        Log.d(TAG, "reloadAllPlugins: loaded ${finalInfo.name}, sources=${instance.sources}")
                } catch (e: Exception) {
                    Log.e(TAG, "reloadAllPlugins: failed to load ${entry.info.name}", e)
                    result.add(entry.copy(initialized = false))
                }
            }
            result
        }
        lxPlugins = loaded
        ensureBuiltinLxmusicPlugin()
        ensurePluginNames()
        persistPlugins()
        rebuildSources()

        val available = lxPluginManager.getAllPluginEntries()
        if (lxSelectedPluginId.isBlank() || available.none { it.id == lxSelectedPluginId }) {
            lxSelectedPluginId = available.firstOrNull()?.id.orEmpty()
            LocalStorage.saveLxSelectedPluginId(lxSelectedPluginId)
        }
        if (lxSelectedSource.isBlank() || lxSelectedSource !in lxSources) {
            lxSelectedSource = lxSources.firstOrNull().orEmpty()
            LocalStorage.saveLxSelectedSource(lxSelectedSource)
        }

        val firstPlugin = loaded.firstOrNull()
        lxPluginInfo = firstPlugin?.info ?: PluginInfo()
        lxPluginUri = firstPlugin?.uri.orEmpty()
        lxPluginHash = firstPlugin?.id.orEmpty()
        refreshActivePluginSupportedQualities()
    }

    suspend fun reloadLxPlugin(): Result<Unit> {
        return try {
            ensurePluginPolicyLoaded()
            if (lxPluginUri.isBlank()) error("请先导入JS插件")
            val app = getApplication<Application>()
            val instance = withContext(Dispatchers.IO) {
                lxPluginManager.load(lxPluginUri, LuoxueRuntimeOptions(), app).getOrThrow()
            }
            lxPluginInfo = instance.info
            rebuildSources()
            if (lxSelectedSource.isBlank() || lxSelectedSource !in lxSources) {
                lxSelectedSource = lxSources.firstOrNull().orEmpty()
                LocalStorage.saveLxSelectedSource(lxSelectedSource)
            }
            LocalStorage.saveLxPluginInfo(lxPluginInfo)
            lxPlugins = lxPluginManager.getAllPluginEntries()
            ensureBuiltinLxmusicPlugin()
            ensurePluginNames()
            persistPlugins()
            refreshActivePluginSupportedQualities()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun rebuildSources() {
        lxSources = lxPluginManager.getAllPluginEntries().flatMap { it.sources }.distinct()
    }

    private fun persistPlugins() {
        LocalStorage.saveLxPlugins(
            lxPlugins.filter { !isBuiltinPlugin(it.id) && !it.uri.startsWith("builtin://") }
        )
    }

    suspend fun testLxSearch(keyword: String): Result<List<Song>> = runCatching {
        if (lxSelectedSource.isBlank()) error("未选择source")
        if (lxSelectedPluginId.isNotBlank() && !isPluginEnabled(lxSelectedPluginId)) {
            error("当前插件已禁用，请先在插件管理中启用")
        }
        if (!lxSupportsAction("search")) error("当前插件source不支持search操作")
        if (lxPluginManager.pluginCount() == 0 && lxPluginUri.isNotBlank()) {
            Log.d(TAG, "testLxSearch: plugin not loaded, reloading...")
            reloadLxPlugin().getOrThrow()
        }
        val plugin = lxPluginManager.getPluginEntry(lxSelectedPluginId) ?: error("插件未加载")
        lxPluginManager.search(lxSelectedPluginId, lxSelectedSource, keyword, lxTimeoutMs).songs
    }

    suspend fun testLxMusicUrl(song: Song): Result<String> = runCatching {
        if (lxSelectedSource.isBlank()) error("未选择source")
        if (lxSelectedPluginId.isNotBlank() && !isPluginEnabled(lxSelectedPluginId)) {
            error("当前插件已禁用，请先在插件管理中启用")
        }
        val lxQuality = lxQualityKey
        val plugin = lxPluginManager.getPluginEntry(lxSelectedPluginId) ?: error("插件未加载")
        lxPluginManager.musicUrl(lxSelectedPluginId, lxSelectedSource, song, lxTimeoutMs, lxQuality).url
    }

    fun testApiConnection(host: String): Boolean {
        // 简单连通性检测
        return try {
            val url = if (host.endsWith("/")) "${host}api/ping" else "$host/api/ping"
            val request = okhttp3.Request.Builder().url(url).build()
            val response = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build().newCall(request).execute()
            response.isSuccessful.also { response.close() }
        } catch (_: Exception) { false }
    }

    // ── 歌单文本导入/导出（MineScreen 使用）──
    fun importPlaylistFromText(text: String) {
        if (text.isBlank()) return
        val lines = text.lines().filter { it.isNotBlank() }
        val name = lines.firstOrNull() ?: "导入歌单"
        val songs = lines.drop(1).mapIndexed { i, line ->
            val parts = line.split(" - ", "—", limit = 2)
            Song(
                id = System.currentTimeMillis() + i,
                title = parts.getOrElse(0) { line }.trim(),
                artist = parts.getOrElse(1) { "" }.trim(),
                album = "",
            )
        }
        val newPlaylist = Playlist(
            id = System.currentTimeMillis(),
            name = name.trim(),
            coverUrl = "",
            songCount = songs.size,
            playCount = 0,
            creator = userName,
            songs = songs,
        )
        myPlaylists = myPlaylists + newPlaylist
        LocalStorage.savePlaylists(myPlaylists)
        showToast("已导入歌单「${name.trim()}」，共 ${songs.size} 首")
    }

    fun exportAllPlaylistsAsText(): String {
        if (myPlaylists.isEmpty()) return ""
        return myPlaylists.joinToString("\n\n") { pl ->
            val header = "歌单: ${pl.name} (${pl.songs.size}首)"
            val body = pl.songs.joinToString("\n") { "  ${it.title} - ${it.artist}" }
            "$header\n$body"
        }
    }

    // ── 崩溃日志（MineScreen 使用）──
    fun getCrashLogCount(): Int = CrashLogManager.getLogCount()

    fun fetchCrashLogs(): List<CrashLogEntry> = CrashLogManager.getLogFiles()

    fun clearCrashLogs() {
        CrashLogManager.clearAllLogs()
        crashLogs = emptyList()
    }

    fun showToast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        super.onCleared()
        abandonAudioFocus()
        if (!playerReleased) {
            playerReleased = true
            player.release()
            mediaSession?.release()
            equalizerEffect?.release()
            bassBoostEffect?.release()
            virtualizerEffect?.release()
            reverbEffect?.release()
            loudnessEnhancerEffect?.release()
            try {
                getApplication<Application>().stopService(
                    Intent(getApplication(), MusicPlaybackService::class.java)
                )
            } catch (_: Exception) {}
        }
        headsetManager.stopMonitoring()
        // 移除应用前后台生命周期观察者，避免泄漏
        appLifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        appLifecycleObserver = null
    }
}