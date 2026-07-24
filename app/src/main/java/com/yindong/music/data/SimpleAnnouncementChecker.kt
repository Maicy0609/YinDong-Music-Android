package com.yindong.music.data

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yindong.music.security.CriticalUiProtector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 简单公告检查器 — 直接检测服务器上的公告文件
 *
 * 服务器配置:
 * 1. 在宝塔面板网站目录下放 announcement.txt (公告内容)
 * 2. 可选: 放 announcement_title.txt (公告标题，默认"公告")
 *
 * APP 检测流程:
 * 1. 获取服务器上公告文件内容
 * 2. 与本地已显示的公告ID对比（用内容hash或日期作为ID）
 * 3. 如果是新公告则显示弹窗
 */
class SimpleAnnouncementChecker(private val activity: Activity) {

    companion object {
        private const val TAG = "SimpleAnnouncementChecker"
        private const val ANNOUNCEMENT_FILENAME = "announcement.txt"
        private const val ANNOUNCEMENT_TITLE_FILENAME = "announcement_title.txt"
        private const val PREFS_NAME = "announcement_prefs"
        private const val KEY_LAST_ANNOUNCEMENT = "last_announcement_hash"
        private const val KEY_DISMISS_UNTIL = "dismiss_until_timestamp"
        private const val KEY_PLUGIN_DISMISS_UNTIL = "plugin_dismiss_until_timestamp"
        private val DISMISS_DURATION_MS = 60 * 60 * 1000L

        private const val PLUGIN_ANNOUNCEMENT_FILENAME = "cajian.txt"

        // 服务器基础URL
        var serverBaseUrl: String = "https://yindong.zh2026.cn"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
    }

    /**
     * 检查公告 - 检测服务器上的公告文件
     */
    suspend fun checkAnnouncement() = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl()
        if (baseUrl.isEmpty()) {
            Log.d(TAG, "服务器URL未配置，跳过公告检查")
            return@withContext
        }

        val announcementUrl = "$baseUrl/$ANNOUNCEMENT_FILENAME"
        Log.d(TAG, "检查公告: $announcementUrl")

        try {
            // 获取公告内容
            val announcementContent = fetchAnnouncementContent(baseUrl) ?: run {
                Log.d(TAG, "公告文件为空或不存在")
                return@withContext
            }

            // 获取标题
            val title = fetchAnnouncementTitle(baseUrl)

            if (isDismissed()) {
                Log.d(TAG, "公告在屏蔽期内，跳过显示")
                return@withContext
            }

            // 每次启动都显示公告
            withContext(Dispatchers.Main) {
                showAnnouncementDialog(title, announcementContent)
            }

        } catch (e: Exception) {
            Log.e(TAG, "检查公告失败: ${e.message}", e)
        }
    }

    /**
     * 获取公告内容
     */
    private fun fetchAnnouncementContent(baseUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/$ANNOUNCEMENT_FILENAME")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string()?.trim() ?: ""
                    if (content.isNotEmpty()) content else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取公告内容失败: ${e.message}")
            null
        }
    }

    /**
     * 获取公告标题
     */
    private fun fetchAnnouncementTitle(baseUrl: String): String {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/$ANNOUNCEMENT_TITLE_FILENAME")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()?.takeIf { it.isNotEmpty() } ?: "公告"
                } else "公告"
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取公告标题失败，使用默认")
            "公告"
        }
    }

    /**
     * 获取服务器URL
     */
    private fun getServerUrl(): String {
        // 优先使用 RemoteConfig 中配置的
        if (RemoteConfig.serverUrl.isNotEmpty()) {
            return RemoteConfig.serverUrl.trimEnd('/')
        }
        // 其次使用本类中设置的
        if (serverBaseUrl.isNotEmpty()) {
            return serverBaseUrl.trimEnd('/')
        }
        return ""
    }

    /**
     * 显示公告对话框
     */
    private fun showAnnouncementDialog(title: String, content: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            val isDark = isDarkMode()
            val bgColor = if (isDark) Color.parseColor("#FF1E1E2E") else Color.parseColor("#FFF5F5F5")
            val textColor = if (isDark) Color.WHITE else Color.parseColor("#FF1A1A2E")
            val subTextColor = if (isDark) Color.parseColor("#B0B0C0") else Color.parseColor("#FF888888")
            val accentColor = Color.parseColor("#FFE74C3C")

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(20), dp(24), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(28).toFloat()
                    setColor(bgColor)
                }
            }

            val titleRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val titleText = TextView(activity).apply {
                text = title
                textSize = 20f
                setTextColor(textColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.02f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleRow.addView(titleText)

            val qqBtn = TextView(activity).apply {
                text = "👥 加官方入QQ群"
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.03f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    colors = intArrayOf(
                        Color.parseColor("#FFFF6B35"),
                        Color.parseColor("#FFFF2E63"),
                    )
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                }
            }
            titleRow.addView(qqBtn)
            container.addView(titleRow)

            val dividerTop = TextView(activity).apply {
                background = GradientDrawable().apply {
                    colors = intArrayOf(
                        Color.argb(60, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                        Color.argb(10, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    )
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply { topMargin = dp(14) }
            }
            container.addView(dividerTop)

            val scrollView = object : ScrollView(activity) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val maxH = dp(450)
                    val spec = MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
                    super.onMeasure(widthMeasureSpec, spec)
                }
            }.apply {
                isVerticalScrollBarEnabled = true
                verticalScrollbarPosition = android.view.View.SCROLLBAR_POSITION_RIGHT
                scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(16) }
                clipToPadding = false
            }

            val contentCard = TextView(activity).apply {
                text = parseAnnouncementContent(content)
                textSize = 14f
                setTextColor(if (isDark) Color.parseColor("#DDE0E8") else Color.parseColor("#FF444444"))
                setLineSpacing(dp(7).toFloat(), 1f)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    val cardBg = if (isDark) Color.parseColor("#12000000") else Color.parseColor("#08000000")
                    setColor(cardBg)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            scrollView.addView(contentCard)
            container.addView(scrollView)

            val btnRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(22) }
            }

            val dismissBtn = TextView(activity).apply {
                text = "1小时内不再显示"
                textSize = 14f
                setTextColor(subTextColor)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (isDark) Color.parseColor("#20FFFFFF") else Color.parseColor("#10000000"))
                }
            }

            val confirmBtn = TextView(activity).apply {
                text = "我知道了"
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    colors = intArrayOf(accentColor, Color.parseColor("#FFFF6B6B"))
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                }
            }
            btnRow.addView(dismissBtn)
            btnRow.addView(confirmBtn)
            container.addView(btnRow)

            AlertDialog.Builder(activity)
                .setView(container)
                .setCancelable(true)
                .create()
                .also { dialog ->
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.show()
                    qqBtn.setOnClickListener { joinQQGroup() }
                    dismissBtn.setOnClickListener {
                        dismissFor12Hours()
                        dialog.dismiss()
                    }
                    confirmBtn.setOnClickListener { dialog.dismiss() }
                }

        } catch (e: Exception) {
            Log.e(TAG, "显示公告对话框失败，使用备用方案", e)
            fallbackShowDialog(title, content)
        }
    }

    private fun parseAnnouncementContent(raw: String): String {
        return raw.lines().mapIndexed { index, line ->
            when {
                line.startsWith("【") && line.endsWith("】") -> "\u200B$line"
                line.startsWith("• ") -> "$line"
                line.matches(Regex("^\\d+\\..*")) -> "$line"
                line.isBlank() -> ""
                else -> line
            }.let { if (index > 0 && it.isNotEmpty()) it else it }
        }.joinToString("\n")
    }

    private fun fallbackShowDialog(title: String, content: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(content)
                .setNegativeButton("1小时内不再显示") { _, _ -> dismissFor12Hours() }
                .setPositiveButton("我知道了", null)
                .show()
        } catch (_: Exception) {}
    }

    /**
     * 强制显示公告（用于测试）
     */
    suspend fun forceShowAnnouncement() = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl()
        if (baseUrl.isEmpty()) return@withContext

        val content = fetchAnnouncementContent(baseUrl) ?: "暂无公告"
        val title = fetchAnnouncementTitle(baseUrl)

        withContext(Dispatchers.Main) {
            showAnnouncementDialog(title, content)
        }
    }

    suspend fun checkPluginAnnouncement() = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl()
        if (baseUrl.isEmpty()) {
            Log.d(TAG, "服务器URL未配置，跳过插件公告检查")
            return@withContext
        }

        val pluginUrl = "$baseUrl/$PLUGIN_ANNOUNCEMENT_FILENAME"
        Log.d(TAG, "检查插件公告: $pluginUrl")

        try {
            val pluginContent = fetchPluginAnnouncementContent(baseUrl) ?: run {
                Log.d(TAG, "插件公告文件为空或不存在")
                return@withContext
            }

            if (isPluginDismissed()) {
                Log.d(TAG, "插件公告在屏蔽期内，跳过显示")
                return@withContext
            }

            withContext(Dispatchers.Main) {
                showPluginAnnouncementDialog(pluginContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查插件公告失败: ${e.message}", e)
        }
    }

    private fun fetchPluginAnnouncementContent(baseUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/$PLUGIN_ANNOUNCEMENT_FILENAME")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string()?.trim() ?: ""
                    if (content.isNotEmpty()) content else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取插件公告内容失败: ${e.message}")
            null
        }
    }

    private fun showPluginAnnouncementDialog(content: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            val isDark = isDarkMode()
            val bgColor = if (isDark) Color.parseColor("#FF1E1E2E") else Color.parseColor("#FFF5F5F5")
            val textColor = if (isDark) Color.WHITE else Color.parseColor("#FF1A1A2E")
            val subTextColor = if (isDark) Color.parseColor("#B0B0C0") else Color.parseColor("#FF888888")
            val accentColor = Color.parseColor("#FFE74C3C")

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(20), dp(24), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(28).toFloat()
                    setColor(bgColor)
                }
            }

            val titleText = TextView(activity).apply {
                text = "插件公告「点击可以复制」"
                textSize = 20f
                setTextColor(textColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.02f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(titleText)

            val dividerTop = TextView(activity).apply {
                background = GradientDrawable().apply {
                    colors = intArrayOf(
                        Color.argb(60, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                        Color.argb(10, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    )
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply { topMargin = dp(14) }
            }
            container.addView(dividerTop)

            val scrollView = object : ScrollView(activity) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val maxH = dp(450)
                    val spec = MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
                    super.onMeasure(widthMeasureSpec, spec)
                }
            }.apply {
                isVerticalScrollBarEnabled = true
                verticalScrollbarPosition = android.view.View.SCROLLBAR_POSITION_RIGHT
                scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(16) }
                clipToPadding = false
            }

            val contentLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    val cardBg = if (isDark) Color.parseColor("#12000000") else Color.parseColor("#08000000")
                    setColor(cardBg)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val urlPattern = Regex("""https?://[^\s]+""")
            val lines = content.lines()
            Log.d(TAG, "插件公告原始内容行数: ${lines.size}")
            for ((lineIndex, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                Log.d(TAG, "处理第${lineIndex + 1}行: $trimmed")

                val urls = urlPattern.findAll(trimmed).map { it.value }.toList()
                Log.d(TAG, "第${lineIndex + 1}行找到${urls.size}个URL: $urls")

                if (urls.isEmpty()) {
                    val lineView = TextView(activity).apply {
                        text = trimmed
                        textSize = 14f
                        setTextColor(if (isDark) Color.parseColor("#DDE0E8") else Color.parseColor("#FF444444"))
                        setLineSpacing(dp(7).toFloat(), 1f)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (contentLayout.childCount > 0) topMargin = dp(8)
                        }
                    }
                    contentLayout.addView(lineView)
                } else {
                    for (url in urls) {
                        Log.d(TAG, "添加链接视图: $url")
                        val linkView = TextView(activity).apply {
                            text = url
                            textSize = 14f
                            setTextColor(accentColor)
                            setLineSpacing(dp(7).toFloat(), 1f)
                            paint.isUnderlineText = true
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                if (contentLayout.childCount > 0) topMargin = dp(8)
                            }
                        }
                        linkView.setOnClickListener {
                            copyToClipboard(url, "插件链接")
                        }
                        contentLayout.addView(linkView)
                    }
                }
            }
            Log.d(TAG, "插件公告总共添加了${contentLayout.childCount}个视图")

            scrollView.addView(contentLayout)
            container.addView(scrollView)

            val btnRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(22) }
            }

            val dismissBtn = TextView(activity).apply {
                text = "今天不显示"
                textSize = 14f
                setTextColor(subTextColor)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (isDark) Color.parseColor("#20FFFFFF") else Color.parseColor("#10000000"))
                }
            }

            val confirmBtn = TextView(activity).apply {
                text = "我知道了"
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    colors = intArrayOf(accentColor, Color.parseColor("#FFFF6B6B"))
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                }
            }
            btnRow.addView(dismissBtn)
            btnRow.addView(confirmBtn)
            container.addView(btnRow)

            AlertDialog.Builder(activity)
                .setView(container)
                .setCancelable(true)
                .create()
                .also { dialog ->
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.show()
                    dismissBtn.setOnClickListener {
                        dismissPluginFor12Hours()
                        dialog.dismiss()
                    }
                    confirmBtn.setOnClickListener { dialog.dismiss() }
                }

        } catch (e: Exception) {
            Log.e(TAG, "显示插件公告对话框失败，使用备用方案", e)
            fallbackShowPluginDialog(content)
        }
    }

    private fun fallbackShowPluginDialog(content: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            AlertDialog.Builder(activity)
                .setTitle("插件公告")
                .setMessage(content)
                .setNegativeButton("1小时内不再显示") { _, _ -> dismissPluginFor12Hours() }
                .setPositiveButton("我知道了", null)
                .show()
        } catch (_: Exception) {}
    }

    suspend fun forceShowPluginAnnouncement() = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl()
        if (baseUrl.isEmpty()) return@withContext

        val content = fetchPluginAnnouncementContent(baseUrl) ?: "暂无插件公告"

        withContext(Dispatchers.Main) {
            showPluginAnnouncementDialog(content)
        }
    }

    private fun isPluginDismissed(): Boolean {
        val until = prefs.getLong(KEY_PLUGIN_DISMISS_UNTIL, 0)
        return if (until > 0) System.currentTimeMillis() < until else false
    }

    private fun dismissPluginFor12Hours() {
        prefs.edit().putLong(KEY_PLUGIN_DISMISS_UNTIL, System.currentTimeMillis() + DISMISS_DURATION_MS).apply()
        Log.d(TAG, "公告已屏蔽1小时，直到 ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(System.currentTimeMillis() + DISMISS_DURATION_MS))}")
    }

    private fun copyToClipboard(text: String, label: String) {
        try {
            val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
            android.widget.Toast.makeText(activity, "$label 已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            android.widget.Toast.makeText(activity, "复制失败，请手动复制: $text", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun isDarkMode(): Boolean {
        return when (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    private fun isDismissed(): Boolean {
        val until = prefs.getLong(KEY_DISMISS_UNTIL, 0)
        return if (until > 0) System.currentTimeMillis() < until else false
    }

    private fun dismissFor12Hours() {
        prefs.edit().putLong(KEY_DISMISS_UNTIL, System.currentTimeMillis() + DISMISS_DURATION_MS).apply()
        Log.d(TAG, "公告已屏蔽12小时，直到 ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(System.currentTimeMillis() + DISMISS_DURATION_MS))}")
    }

    private fun joinQQGroup() {
        val troopUin = CriticalUiProtector.communityQqNumber()
        val joinKey = CriticalUiProtector.communityJoinKey()
        val fallbackUrl = RemoteConfig.officialCommunityJoinUrl.ifBlank { CriticalUiProtector.communityJoinUrl() }

        val schemes = listOf(
            "mqqapi://group/join_troop?src_type=internal&version=1&troop_uin=$troopUin&subsource_id=1030&is_need_jump_aio=1",
            CriticalUiProtector.buildJoinQqGroupUrl(joinKey),
            fallbackUrl,
        )

        for ((index, url) in schemes.withIndex()) {
            if (url.isBlank()) continue
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                return
            } catch (_: Exception) {
                if (index == schemes.lastIndex) {
                    Log.w(TAG, "所有QQ群跳转方式均失败，尝试复制群号")
                    try {
                        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ群号", troopUin))
                        android.widget.Toast.makeText(activity, "QQ群号 $troopUin 已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(activity, "请添加QQ群: $troopUin", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun cleanup() {}
}
