package com.yindong.music

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingLyricsService : Service() {

    companion object {
        private const val TAG = "FloatingLyrics"
        private const val UPDATE_INTERVAL = 150L
        private const val NOTIFICATION_CHANNEL_ID = "floating_lyrics_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile var currentLyricText: String = ""
        @Volatile var nextLyricText: String = ""
        @Volatile var isPlaying: Boolean = false
        @Volatile var isActive: Boolean = false
        @Volatile var lyricColor: Int = Color.WHITE
        @Volatile var lyricSize: Float = 15f

        var onPlayPause: (() -> Unit)? = null
        var onPrevious: (() -> Unit)? = null
        var onNext: (() -> Unit)? = null

        fun start(context: Context) {
            if (isActive) return
            try {
                val intent = Intent(context, FloatingLyricsService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动悬浮歌词失败", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, FloatingLyricsService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "停止悬浮歌词失败", e)
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var lyricTextView: TextView? = null
    private var nextLyricTextView: TextView? = null
    private var playPauseBtn: TextView? = null
    private var prevBtnRef: TextView? = null
    private var nextBtnRef: TextView? = null
    private var lockBtnRef: TextView? = null
    private var closeBtnRef: TextView? = null
    private var playControlRow: LinearLayout? = null
    private var controlRow: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isLocked = false
    private var controlsVisible = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var lastClickTime = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateLyricDisplay()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "未授予悬浮窗权限，停止服务")
            stopSelf()
            return
        }
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        isActive = true
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            createFloatingView()
            handler.post(updateRunnable)
            Log.d(TAG, "悬浮歌词已启动")
        } catch (e: Exception) {
            Log.e(TAG, "悬浮歌词启动失败", e)
            isActive = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        isActive = false
        handler.removeCallbacks(updateRunnable)
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.w(TAG, "移除悬浮窗异常", e)
        }
        floatingView = null
        lyricTextView = null
        nextLyricTextView = null
        Log.d(TAG, "悬浮歌词已停止")
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels

        val rootLayout = object : LinearLayout(this) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                Log.d(TAG, "onIntercept: action=${ev.action}, x=${ev.x}, y=${ev.y}")
                return false
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                Log.d(TAG, "onTouchEvent: action=${ev.action}, isDragging=$isDragging")
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = false
                        initialX = (layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                        initialY = (layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                        initialTouchX = ev.rawX
                        initialTouchY = ev.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (ev.rawX - initialTouchX).toInt()
                        val dy = (ev.rawY - initialTouchY).toInt()
                        if (!isLocked && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                            isDragging = true
                            val lp = layoutParams as? WindowManager.LayoutParams ?: return true
                            lp.x = initialX + dx
                            lp.y = initialY + dy
                            try {
                                windowManager?.updateViewLayout(this, lp)
                            } catch (_: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            val now = System.currentTimeMillis()
                            if (isLocked) {
                                if (now - lastClickTime < 300) {
                                    isLocked = false
                                    lockBtnRef?.text = "🔒"
                                    showControls()
                                }
                            } else {
                                if (now - lastClickTime < 300) {
                                    openApp()
                                } else {
                                    handler.removeCallbacks(toggleRunnable)
                                    handler.postDelayed(toggleRunnable, 320)
                                }
                            }
                            lastClickTime = now
                        }
                        isDragging = false
                        return true
                    }
                }
                return super.onTouchEvent(ev)
            }
        }
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.gravity = Gravity.CENTER
        rootLayout.setPadding(dp(16), dp(6), dp(16), dp(6))

        lyricTextView = TextView(this).apply {
            text = "♪ 等待歌词..."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            maxLines = 2
            paint.isAntiAlias = true
            paint.isSubpixelText = true
            paint.hinting = android.graphics.Paint.HINTING_ON
            paint.flags = paint.flags or android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG
            includeFontPadding = false
            setShadowLayer(3f, 0f, 1.5f, 0xDD000000.toInt())
        }
        rootLayout.addView(lyricTextView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        nextLyricTextView = TextView(this).apply {
            text = ""
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.03f
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(2), 0, 0)
            paint.isAntiAlias = true
            paint.isSubpixelText = true
            paint.flags = paint.flags or android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG
            includeFontPadding = false
            setShadowLayer(2f, 0f, 1f, 0xAA000000.toInt())
        }
        rootLayout.addView(nextLyricTextView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        playControlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            visibility = View.GONE
        }

        prevBtnRef = TextView(this).apply {
            text = "⏮"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                Log.d(TAG, "prevBtn clicked!")
                onPrevious?.invoke()
            }
        }
        playControlRow!!.addView(prevBtnRef, LinearLayout.LayoutParams(dp(40), dp(30)))

        playPauseBtn = TextView(this).apply {
            text = if (isPlaying) "⏸" else "▶"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                Log.d(TAG, "playPauseBtn clicked!")
                onPlayPause?.invoke()
            }
        }
        playControlRow!!.addView(playPauseBtn, LinearLayout.LayoutParams(dp(40), dp(30)))

        nextBtnRef = TextView(this).apply {
            text = "⏭"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                Log.d(TAG, "nextBtn clicked!")
                onNext?.invoke()
            }
        }
        playControlRow!!.addView(nextBtnRef, LinearLayout.LayoutParams(dp(40), dp(30)))

        rootLayout.addView(playControlRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
            visibility = View.GONE
        }

        lockBtnRef = TextView(this).apply {
            text = "🔒"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener {
                Log.d(TAG, "lockBtn clicked!")
                isLocked = !isLocked
                text = if (isLocked) "🔴" else "🔒"
                if (isLocked) hideControls()
            }
        }
        controlRow!!.addView(lockBtnRef, LinearLayout.LayoutParams(dp(36), dp(24)))

        closeBtnRef = TextView(this).apply {
            text = "✕"
            setTextColor(0xAAFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener {
                Log.d(TAG, "closeBtn clicked!")
                stopSelf()
            }
        }
        controlRow!!.addView(closeBtnRef, LinearLayout.LayoutParams(dp(36), dp(24)))

        rootLayout.addView(controlRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            // FLAG_NOT_FOCUSABLE: 悬浮窗不抢占输入法焦点，避免开启桌面歌词后其他 App 无法调出输入法
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(80)
            width = (screenWidth * 0.85).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }

        try {
            windowManager?.addView(rootLayout, params)
            floatingView = rootLayout
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮窗失败", e)
        }
    }

    private val toggleRunnable = Runnable { toggleControls() }

    private fun openApp() {
        handler.removeCallbacks(toggleRunnable)
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (intent != null) startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "打开App失败", e)
        }
    }

    private val autoHideRunnable = Runnable { hideControls() }

    private fun showControls() {
        controlsVisible = true
        playControlRow?.visibility = View.VISIBLE
        controlRow?.visibility = View.VISIBLE
        handler.removeCallbacks(autoHideRunnable)
        handler.postDelayed(autoHideRunnable, 3000)
    }

    private fun hideControls() {
        controlsVisible = false
        playControlRow?.visibility = View.GONE
        controlRow?.visibility = View.GONE
        handler.removeCallbacks(autoHideRunnable)
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private var lastText = ""
    private var lastPlayingState = false
    private var lastColor = Color.WHITE
    private var lastSize = 15f

    private fun updateLyricDisplay() {
        val text = currentLyricText
        val next = nextLyricText
        if (lyricColor != lastColor) {
            lastColor = lyricColor
            lyricTextView?.setTextColor(lyricColor)
            nextLyricTextView?.setTextColor((lyricColor and 0x00FFFFFF) or 0x99000000.toInt())
        }
        if (lyricSize != lastSize) {
            lastSize = lyricSize
            lyricTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, lyricSize)
            nextLyricTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, (lyricSize * 0.73f))
        }
        if (text != lastText) {
            lastText = text
            lyricTextView?.text = text.ifEmpty { "♪ 等待歌词..." }
            lyricTextView?.alpha = 0.5f
            lyricTextView?.animate()?.alpha(1f)?.setDuration(200)?.start()
        }
        nextLyricTextView?.text = next
        nextLyricTextView?.visibility = if (next.isEmpty()) View.GONE else View.VISIBLE
        if (isPlaying != lastPlayingState) {
            lastPlayingState = isPlaying
            playPauseBtn?.text = if (isPlaying) "⏸" else "▶"
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "悬浮歌词",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮歌词显示服务"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // 点击通知正文回到 MainActivity（与播放通知一致，显式设置 PendingIntent）。
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }?.let { launchIntent ->
            PendingIntent.getActivity(
                this, 1001,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("悬浮歌词")
            .setContentText("正在显示悬浮歌词")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()
    }
}
