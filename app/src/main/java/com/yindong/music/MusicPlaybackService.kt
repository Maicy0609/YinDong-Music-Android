package com.yindong.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import kotlinx.coroutines.*

@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "music_playback"
        const val NOTIFICATION_ID = 100

        const val ACTION_PREVIOUS = "com.yindong.music.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.yindong.music.ACTION_NEXT"
        const val ACTION_PLAY = "com.yindong.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.yindong.music.ACTION_PAUSE"
        const val ACTION_CLOSE = "com.yindong.music.ACTION_CLOSE"

        @Volatile
        var instance: MusicPlaybackService? = null
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isListening = false
    private var compatSession: MediaSessionCompat? = null
    private var coverBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private var positionUpdateRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "服务已创建")
        MusicPlaybackServiceHelper.attach(this)
        createNotificationChannel()
        initCompatMediaSession()
        startForegroundPlaceholder()
        startPositionSyncJob()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun startForegroundPlaceholder() {
        try {
            updateAll("新众和夜雨音乐", "正在准备播放...", false)
            val notification = buildNotification("新众和夜雨音乐", "正在准备播放...", false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "前台服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败", e)
        }
    }

    /**
     * 构建点击通知正文 / 媒体会话返回的 PendingIntent：回到 MainActivity。
     * 配合 AndroidManifest 中 MainActivity 的 launchMode="singleTask"，
     * 点击通知会复用已有实例（走 onNewIntent）而非重建。
     */
    private fun buildContentPendingIntent(): PendingIntent? {
        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return null
        return PendingIntent.getActivity(
            this, 100,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun initCompatMediaSession() {
        try {
            compatSession = MediaSessionCompat(this, "YinDongMusic").apply {
                buildContentPendingIntent()?.let { setSessionActivity(it) }
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        Log.d(TAG, "MediaSessionCompat onPlay")
                        MediaSessionHolder.session?.player?.playWhenReady = true
                        updateAll()
                    }
                    override fun onPause() {
                        Log.d(TAG, "MediaSessionCompat onPause")
                        MediaSessionHolder.session?.player?.playWhenReady = false
                        updateAll()
                    }
                    override fun onSkipToNext() {
                        Log.d(TAG, "MediaSessionCompat onSkipToNext")
                        MediaSessionHolder.session?.player?.let {
                            it.seekToNext(); it.prepare(); it.playWhenReady = true
                        }
                        updateAll()
                    }
                    override fun onSkipToPrevious() {
                        Log.d(TAG, "MediaSessionCompat onSkipToPrevious")
                        MediaSessionHolder.session?.player?.let {
                            it.seekToPrevious(); it.prepare(); it.playWhenReady = true
                        }
                        updateAll()
                    }
                    override fun onStop() {
                        Log.d(TAG, "MediaSessionCompat onStop")
                        stopSelf()
                    }
                    override fun onSeekTo(pos: Long) {
                        MediaSessionHolder.session?.player?.seekTo(pos)
                        updateAll()
                    }
                })
                isActive = true
            }
            setInitialEmptyState()
            Log.d(TAG, "MediaSessionCompat 初始化成功, token=${compatSession?.sessionToken}")
        } catch (e: Exception) {
            Log.e(TAG, "MediaSessionCompat 初始化失败", e)
        }
    }

    private fun setInitialEmptyState() {
        val session = compatSession ?: return
        val emptyMeta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
            .build()
        session.setMetadata(emptyMeta)

        val stoppedState = PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 1f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_SEEK_TO
            )
            .build()
        session.setPlaybackState(stoppedState)
    }

    private fun startPositionSyncJob() {
        positionUpdateRunnable = object : Runnable {
            override fun run() {
                if (isListening && compatSession != null) {
                    syncPosition()
                    handler.postDelayed(this, 1000L)
                } else {
                    handler.postDelayed(this, 2000L)
                }
            }
        }
        handler.post(positionUpdateRunnable!!)
    }

    private fun syncPosition() {
        val player = MediaSessionHolder.session?.player ?: return
        if (!player.isPlaying || player.duration <= 0) return
        val session = compatSession ?: return
        val state = PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, player.currentPosition.coerceAtLeast(0L), player.playbackParameters.speed)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_SEEK_TO
            )
            .build()
        session.setPlaybackState(state)
        // 车载歌词开启时：每秒刷新通知标题位为当前歌词行（车机蓝牙实时显示）
        if (CarLyricHolder.enabled) {
            updateAll()
        }
    }

    private fun updateAll(title: String? = null, artist: String? = null, isPlaying: Boolean? = null) {
        updateCompatMetadata()
        updateNotification()
    }

    /**
     * 计算通知栏/MediaSession 应显示的标题与副标题。
     * 车载歌词开启时：标题位显示实时歌词行（车机蓝牙经 AVRCP 读取该字段显示），
     * 副标题显示"歌名 - 歌手"以便识别当前曲目。
     * 关闭时：标题为歌名，副标题为歌手（原始行为）。
     */
    private fun resolveDisplayTitleArtist(songTitle: String, artist: String): Pair<String, String> {
        if (!CarLyricHolder.enabled) return songTitle to artist
        val line = CarLyricHolder.currentLine.trim()
        val subtitle = when {
            songTitle.isNotBlank() && artist.isNotBlank() -> "$songTitle - $artist"
            songTitle.isNotBlank() -> songTitle
            else -> artist
        }
        val title = if (line.isNotEmpty()) line else "♪ 纯音乐"
        return title to subtitle
    }

    private fun updateCompatMetadata() {
        val session = compatSession ?: return
        val player = MediaSessionHolder.session?.player

        if (player == null || player.mediaItemCount == 0) {
            setInitialEmptyState()
            return
        }

        val meta = player.currentMediaItem?.mediaMetadata
        val songTitle = meta?.title?.toString()?.takeIf { it.isNotBlank() } ?: return
        val artistStr = meta?.artist?.toString().orEmpty()
        val (displayTitle, displayArtist) = resolveDisplayTitleArtist(songTitle, artistStr)

        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, displayTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, displayArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, meta?.albumTitle?.toString().orEmpty())
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.duration.coerceAtLeast(0L))

        coverBitmap?.let { builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(builder.build())

        val playing = player.isPlaying
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val pbState = PlaybackStateCompat.Builder()
            .setState(state, player.currentPosition.coerceAtLeast(0L), player.playbackParameters.speed)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_SEEK_TO
            )
            .build()
        session.setPlaybackState(pbState)

        session.isActive = true
    }

    fun updateCover(bitmap: Bitmap?) {
        coverBitmap = bitmap
        updateAll()
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): Notification {
        val previousIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicPlaybackService::class.java).setAction(if (isPlaying) ACTION_PAUSE else ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val closeIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setShowWhen(false)
            // 点击通知正文回到 MainActivity（配合 launchMode="singleTask" 走 onNewIntent）。
            // 必须显式设置：仅靠 MediaSession.setSessionActivity() 在 vivo/OPPO/小米/华为/鸿蒙
            // 等 ROM 上不可靠，会导致点击通知栏无法唤起应用。
            .apply { buildContentPendingIntent()?.let { setContentIntent(it) } }
            .addAction(android.R.drawable.ic_media_previous, "上一首", previousIntent)
            .addAction(playPauseIcon, if (isPlaying) "暂停" else "播放", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "下一首", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", closeIntent)

        val mediaStyle = MediaNotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        compatSession?.let { session ->
            mediaStyle.setMediaSession(session.sessionToken)
        }

        builder.setStyle(mediaStyle)

        coverBitmap?.let { builder.setLargeIcon(it) }

        return builder.build()
    }

    private fun updateNotification() {
        val player = MediaSessionHolder.session?.player
        val songTitle = player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "新众和夜雨音乐"
        val artist = player?.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
        val isPlaying = player?.isPlaying == true
        val (displayTitle, displayArtist) = resolveDisplayTitleArtist(songTitle, artist)

        try {
            val notification = buildNotification(displayTitle, displayArtist, isPlaying)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "更新通知失败", e)
        }
    }

    /** 立即刷新通知栏与 MediaSession 元数据（车载歌词开关变化时由 ViewModel 调用） */
    fun refreshNotification() {
        updateAll()
    }

    private fun startListeningPlayer() {
        if (isListening) return
        val session = MediaSessionHolder.session ?: return
        isListening = true

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "Player.Listener onIsPlayingChanged: $isPlaying")
                updateAll()
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                Log.d(TAG, "Player.Listener onMediaItemTransition")
                coverBitmap = null
                updateAll()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "Player.Listener onPlaybackStateChanged")
                updateAll()
            }
        }
        session.player.addListener(listener)
        updateAll()
        Log.d(TAG, "Player 监听已绑定")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "onGetSession: session=${MediaSessionHolder.session != null}")
        return MediaSessionHolder.session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (MediaSessionHolder.session == null) {
            Log.w(TAG, "Session 为空，停止服务")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        intent?.action?.let { action ->
            val session = MediaSessionHolder.session ?: return@let
            when (action) {
                ACTION_PREVIOUS -> {
                    session.player.seekToPrevious(); session.player.prepare(); session.player.playWhenReady = true
                }
                ACTION_NEXT -> {
                    session.player.seekToNext(); session.player.prepare(); session.player.playWhenReady = true
                }
                ACTION_PLAY -> session.player.playWhenReady = true
                ACTION_PAUSE -> session.player.playWhenReady = false
                ACTION_CLOSE -> {
                    session.player.stop(); session.player.clearMediaItems(); stopSelf(); return START_NOT_STICKY
                }
            }
            updateAll()
        }

        startListeningPlayer()

        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand 异常", e)
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val session = MediaSessionHolder.session
            if (session == null || !session.player.playWhenReady || session.player.mediaItemCount == 0) {
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onTaskRemoved 异常", e)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        instance = null
        isListening = false
        MusicPlaybackServiceHelper.detach()
        positionUpdateRunnable?.let { handler.removeCallbacks(it) }
        compatSession?.isActive = false
        compatSession?.release()
        compatSession = null
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "服务已销毁")
    }
}

object MediaSessionHolder {
    @Volatile
    var session: MediaSession? = null
}

/**
 * 车载歌词共享状态：ViewModel 写入当前歌词行，MusicPlaybackService 读取后
 * 将通知栏播放器标题位与 MediaSessionCompat 元数据标题替换为实时歌词，
 * 供车载蓝牙（AVRCP）在车机屏幕上显示。
 */
object CarLyricHolder {
    /** 车载歌词开关（由 ViewModel 同步） */
    @Volatile
    var enabled: Boolean = false

    /** 当前歌词行文本（无歌词时为空串） */
    @Volatile
    var currentLine: String = ""
}
