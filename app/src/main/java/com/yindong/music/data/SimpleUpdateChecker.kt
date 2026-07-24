package com.yindong.music.data

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yindong.music.ui.components.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class SimpleUpdateChecker(private val activity: Activity) {

    companion object {
        private const val TAG = "SimpleUpdateChecker"
        private const val APK_FILENAME = "yd.apk"
        private const val VERSION_FILENAME = "yd_version.txt"
        private const val CHANGELOG_FILENAME = "yd_changelog.txt"
        private const val APK_SAVE_NAME = "simple_update.apk"

        var serverBaseUrl: String = "http://yindong.zh2026.cn"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    private var progressDialog: AlertDialog? = null
    private var progressPercent: TextView? = null
    private var progressBar: ProgressBar? = null

    suspend fun checkUpdate() = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl()
        if (baseUrl.isEmpty()) return@withContext

        try {
            val fileInfo = getServerFileInfo("$baseUrl/$APK_FILENAME") ?: return@withContext

            val serverVersion = fetchServerVersion(baseUrl).ifEmpty { "新版本" }
            val changelog = fetchChangelog(baseUrl)
            val currentVersion = getCurrentVersion()

            if (serverVersion == "新版本") {
                Log.w(TAG, "无法获取远程版本号，跳过更新提示")
                return@withContext
            }

            if (!isNewerVersion(serverVersion, currentVersion)) {
                Log.d(TAG, "已是最新版本: 本地=$currentVersion 远程=$serverVersion")
                return@withContext
            }

            withContext(Dispatchers.Main) {
                showUpdateDialog(
                    info = UpdateInfo(
                        currentVersion = currentVersion,
                        newVersion = serverVersion,
                        changelog = changelog,
                        fileSize = formatFileSize(fileInfo.size),
                    ),
                    downloadUrl = "$baseUrl/$APK_FILENAME",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
        }
    }

    private fun getServerFileInfo(url: String): FileInfo? {
        return try {
            client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val size = response.header("Content-Length")?.toLongOrNull() ?: 0
                if (size > 0) FileInfo(size, response.header("Last-Modified") ?: "") else null
            }
        } catch (_: Exception) { null }
    }

    private fun fetchServerVersion(baseUrl: String): String {
        return try {
            val response = client.newCall(Request.Builder().url("$baseUrl/$VERSION_FILENAME").build()).execute()
            if (!response.isSuccessful) return ""
            val body = response.body?.string()?.trim() ?: return ""
            if (body.startsWith("<") || body.contains("<html", ignoreCase = true) || body.contains("DOCTYPE", ignoreCase = true)) {
                Log.w(TAG, "版本文件返回HTML而非纯文本")
                return ""
            }
            val versionRegex = Regex("""^v?\d+(\.\d+){1,3}(-\w+)?${'$'}""")
            val match = versionRegex.find(body)
            match?.value?.trim()?.ifEmpty { null } ?: run {
                if (body.length > 50) "" else body
            }
        } catch (_: Exception) { "" }
    }

    private fun fetchChangelog(baseUrl: String): String {
        return try {
            val response = client.newCall(Request.Builder().url("$baseUrl/$CHANGELOG_FILENAME").build()).execute()
            if (!response.isSuccessful) return ""
            val body = response.body?.string()?.trim() ?: return ""
            if (body.startsWith("<") || body.contains("<html", ignoreCase = true) || body.contains("DOCTYPE", ignoreCase = true)) {
                Log.w(TAG, "更新日志返回HTML而非纯文本")
                return ""
            }
            if (body.length > 5000) body.substring(0, 5000) else body
        } catch (_: Exception) { "" }
    }

    private fun getCurrentVersion(): String {
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
    }

    private fun getServerUrl(): String {
        if (RemoteConfig.serverUrl.isNotEmpty()) return RemoteConfig.serverUrl.trimEnd('/')
        if (serverBaseUrl.isNotEmpty()) return serverBaseUrl.trimEnd('/')
        return ""
    }

    private fun showUpdateDialog(info: UpdateInfo, downloadUrl: String) {
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
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val titleText = TextView(activity).apply {
                text = "发现新版本"
                textSize = 20f
                setTextColor(textColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleRow.addView(titleText)
            container.addView(titleRow)

            val versionCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(16) }
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    colors = intArrayOf(
                        Color.parseColor("#33FF6B6B"),
                        Color.parseColor("#334ECDC4"),
                    )
                    orientation = GradientDrawable.Orientation.TL_BR
                }
            }
            val versionText = TextView(activity).apply {
                text = "v${info.newVersion}"
                textSize = 26f
                setTextColor(accentColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.02f
                gravity = Gravity.CENTER
            }
            versionCard.addView(versionText)

            val currentText = TextView(activity).apply {
                text = "当前版本 v${info.currentVersion}"
                textSize = 12f
                setTextColor(subTextColor)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            versionCard.addView(currentText)

            if (info.fileSize.isNotEmpty()) {
                val sizeText = TextView(activity).apply {
                    text = "大小 ${info.fileSize}"
                    textSize = 11f
                    setTextColor(Color.argb(80, Color.red(subTextColor), Color.green(subTextColor), Color.blue(subTextColor)))
                    gravity = Gravity.CENTER
                }
                versionCard.addView(sizeText)
            }
            container.addView(versionCard)

            if (info.changelog.isNotEmpty()) {
                val logLabel = TextView(activity).apply {
                    text = "更新内容"
                    textSize = 13f
                    setTextColor(Color.argb(115, Color.red(subTextColor), Color.green(subTextColor), Color.blue(subTextColor)))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(16) }
                }
                container.addView(logLabel)

                val logCard = TextView(activity).apply {
                    text = info.changelog
                    textSize = 13f
                    setTextColor(if (isDark) Color.parseColor("#DDE0E8") else Color.parseColor("#FF444444"))
                    setLineSpacing(dp(6).toFloat(), 1f)
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        val bgColor = if (isDark) Color.parseColor("#1A000000") else Color.parseColor("#0A000000")
                        setColor(bgColor)
                    }
                }
                container.addView(logCard)
            }

            val btnRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(22) }
            }

            val browserBtn = createButton("浏览器下载", Color.TRANSPARENT, subTextColor, false) {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)))
                } catch (_: Exception) {}
            }
            btnRow.addView(browserBtn)

            btnRow.addView(LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12), LinearLayout.LayoutParams.MATCH_PARENT)
            })

            val inAppBtn = createButton("应用内下载", accentColor, Color.WHITE, true) {
                startInAppDownload(downloadUrl)
            }
            btnRow.addView(inAppBtn)
            container.addView(btnRow)

            AlertDialog.Builder(activity)
                .setView(container)
                .setCancelable(true)
                .create()
                .also { dialog ->
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.show()
                }

        } catch (e: Exception) {
            Log.e(TAG, "显示更新对话框失败", e)
            fallbackShowDialog(info, downloadUrl)
        }
    }

    private fun startInAppDownload(downloadUrl: String) {
        try {
            val apkFile = File(
                activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                APK_SAVE_NAME
            )
            if (apkFile.exists()) apkFile.delete()

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("云音乐更新")
                setDescription("正在下载更新包...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE or DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_SAVE_NAME
                )
                setMimeType("application/vnd.android.package-archive")
            }

            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)

            showDownloadProgress()
            registerDownloadReceiver()
            startProgressPolling(dm)
        } catch (e: Exception) {
            Log.e(TAG, "应用内下载失败", e)
            Toast.makeText(activity, "下载失败，尝试浏览器下载", Toast.LENGTH_SHORT).show()
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)))
            } catch (_: Exception) {}
        }
    }

    private fun showDownloadProgress() {
        if (activity.isFinishing || activity.isDestroyed) return

        val isDark = isDarkMode()
        val bgColor = if (isDark) Color.parseColor("#FF1E1E2E") else Color.parseColor("#FFF5F5F5")
        val textColor = if (isDark) Color.WHITE else Color.parseColor("#FF1A1A2E")
        val accentColor = Color.parseColor("#FFE74C3C")

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
        }

        val title = TextView(activity).apply {
            text = "正在下载更新"
            textSize = 18f
            setTextColor(textColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(title)

        progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24)
            ).apply { topMargin = dp(20) }
        }
        container.addView(progressBar)

        progressPercent = TextView(activity).apply {
            text = "0%"
            textSize = 16f
            setTextColor(accentColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        container.addView(progressPercent)

        val tipText = TextView(activity).apply {
            text = "下载完成后将自动弹出安装界面"
            textSize = 12f
            setTextColor(if (isDark) Color.parseColor("#B0B0C0") else Color.parseColor("#FF888888"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        container.addView(tipText)

        progressDialog = AlertDialog.Builder(activity)
            .setView(container)
            .setCancelable(false)
            .create()
        progressDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        progressDialog?.show()
    }

    private fun startProgressPolling(dm: DownloadManager) {
        Thread {
            while (true) {
                try {
                    val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIdx >= 0) {
                            val status = cursor.getInt(statusIdx)
                            if (status == DownloadManager.STATUS_FAILED) {
                                cursor.close()
                                activity.runOnUiThread {
                                    progressDialog?.dismiss()
                                    Toast.makeText(activity, "下载失败，请重试", Toast.LENGTH_SHORT).show()
                                }
                                break
                            }
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                cursor.close()
                                break
                            }
                        }
                        val bytesDoneIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        if (bytesDoneIdx >= 0 && bytesTotalIdx >= 0) {
                            val done = cursor.getLong(bytesDoneIdx)
                            val total = cursor.getLong(bytesTotalIdx)
                            if (total > 0) {
                                val percent = (done * 100f / total).toInt()
                                activity.runOnUiThread {
                                    progressBar?.progress = percent
                                    progressPercent?.text = "$percent%"
                                }
                            }
                        }
                        cursor.close()
                    }
                    Thread.sleep(300)
                } catch (_: Exception) { break }
            }
        }.start()
    }

    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Log.d(TAG, "下载完成: $id")
                    progressDialog?.dismiss()
                    installApk()
                    try { activity.unregisterReceiver(this) } catch (_: Exception) {}
                    downloadReceiver = null
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk() {
        try {
            val apkFile = File(
                activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                APK_SAVE_NAME
            )

            if (!apkFile.exists()) {
                Toast.makeText(activity, "APK文件不存在", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            activity.startActivity(intent)
            Log.d(TAG, "已触发APK安装")
        } catch (e: Exception) {
            Log.e(TAG, "安装APK失败", e)
            Toast.makeText(activity, "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createButton(text: String, bgColor: Int, textColor: Int, isPrimary: Boolean, onClick: () -> Unit): TextView {
        val btn = TextView(activity).apply {
            this.text = text
            textSize = 14f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                if (isPrimary) {
                    setColor(bgColor)
                } else {
                    setColor(bgColor)
                    val strokeColor = if (isDarkMode()) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A000000")
                    setStroke(dp(1), strokeColor)
                }
            }
            setOnClickListener { onClick() }
        }
        return btn
    }

    private fun fallbackShowDialog(info: UpdateInfo, downloadUrl: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            AlertDialog.Builder(activity)
                .setTitle("发现新版本 v${info.newVersion}")
                .setMessage(buildString {
                    append("当前版本: v${info.currentVersion}\n")
                    if (info.fileSize.isNotEmpty()) append("文件大小: ${info.fileSize}\n")
                    if (info.changelog.isNotEmpty()) append("\n${info.changelog}")
                })
                .setPositiveButton("应用内下载") { _, _ ->
                    startInAppDownload(downloadUrl)
                }
                .setNeutralButton("浏览器下载") { _, _ ->
                    try { activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))) } catch (_: Exception) {}
                }
                .setNegativeButton("稍后再说", null)
                .show()
        } catch (_: Exception) {}
    }

    private fun formatFileSize(size: Long): String = when {
        size >= 1024 * 1024 -> "%.2f MB".format(size / (1024.0 * 1024.0))
        size >= 1024 -> "%.2f KB".format(size / 1024.0)
        else -> "$size B"
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun isDarkMode(): Boolean {
        return when (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    private fun isNewerVersion(serverVersion: String, currentVersion: String): Boolean {
        val sParts = parseVersion(serverVersion)
        val cParts = parseVersion(currentVersion)
        val maxLen = maxOf(sParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val s = sParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (s > c) return true
            if (s < c) return false
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> {
        return version.removePrefix("v").removePrefix("V")
            .split(Regex("[.\\-]"))
            .mapNotNull { it.toIntOrNull() }
    }

    fun cleanup() {
        try {
            downloadReceiver?.let { activity.unregisterReceiver(it) }
        } catch (_: Exception) {}
        downloadReceiver = null
        try {
            progressDialog?.dismiss()
        } catch (_: Exception) {}
    }

    private data class FileInfo(val size: Long, val lastModified: String)
}
