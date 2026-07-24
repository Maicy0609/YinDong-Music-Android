package com.yindong.music.data

import android.app.AlertDialog
import android.app.Activity
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
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yindong.music.ui.components.UpdateInfo
import java.io.File
import java.security.MessageDigest

class UpdateManager(private val activity: Activity) {

    companion object {
        private const val TAG = "UpdateManager"
        private val SHA256_REGEX = Regex("^[a-f0-9]{64}$")
    }

    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null

    fun cleanup() {
        try {
            downloadReceiver?.let { activity.unregisterReceiver(it) }
        } catch (_: Exception) {}
        downloadReceiver = null
    }

    fun checkUpdate() {
        if (!RemoteConfig.isLoaded) return

        val serverVersion = RemoteConfig.latestVersion
        if (serverVersion.isEmpty()) return

        val currentVersion = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.0.0"
        } catch (_: Exception) { "0.0.0" }

        Log.d(TAG, "版本对比: 当前=$currentVersion, 服务器=$serverVersion")

        if (compareVersion(currentVersion, serverVersion) >= 0) return

        if (!hasSecureUpdateConfig()) {
            Log.e(TAG, "更新配置不安全")
            activity.runOnUiThread {
                if (RemoteConfig.forceUpdate) {
                    showGlassDialog(UpdateInfo(
                        currentVersion = try { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" },
                        newVersion = RemoteConfig.latestVersion,
                        changelog = "当前版本需要更新，但更新包校验信息缺失或非法，请联系管理员修复后再试。",
                        isForceUpdate = true,
                    ))
                } else {
                    Toast.makeText(activity, "更新配置不安全，已自动跳过", Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        val info = UpdateInfo(
            currentVersion = currentVersion,
            newVersion = serverVersion,
            changelog = RemoteConfig.updateLog,
            isForceUpdate = RemoteConfig.forceUpdate,
        )

        activity.runOnUiThread { showGlassDialog(info) }
    }

    private fun showGlassDialog(info: UpdateInfo) {
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

            // 标题行
            val titleRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            titleRow.addView(TextView(activity).apply {
                text = "发现新版本"; textSize = 20f; setTextColor(textColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!info.isForceUpdate) {
                titleRow.addView(TextView(activity).apply {
                    text = "✕"; textSize = 18f; setTextColor(subTextColor); gravity = Gravity.CENTER
                    setPadding(dp(12), 0, dp(4), 0)
                    setOnClickListener { /* dismiss handled by dialog cancelable */ }
                })
            }
            container.addView(titleRow)

            // 版本号卡片
            val versionCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    colors = intArrayOf(Color.parseColor("#33FF6B6B"), Color.parseColor("#334ECDC4"))
                    orientation = GradientDrawable.Orientation.TL_BR
                }
            }
            versionCard.addView(TextView(activity).apply {
                text = "v${info.newVersion}"; textSize = 26f; setTextColor(accentColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            })
            versionCard.addView(TextView(activity).apply {
                text = "当前版本 v${info.currentVersion}"; textSize = 12f
                setTextColor(subTextColor); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
            })
            container.addView(versionCard)

            // 更新日志
            if (RemoteConfig.updateLog.isNotEmpty()) {
                container.addView(TextView(activity).apply {
                    text = "更新内容"; textSize = 13f; setTextColor(Color.argb(115, Color.red(subTextColor), Color.green(subTextColor), Color.blue(subTextColor)))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
                })
                container.addView(TextView(activity).apply {
                    text = RemoteConfig.updateLog; textSize = 13f; setLineSpacing(dp(6).toFloat(), 1f)
                    setTextColor(if (isDark) Color.parseColor("#DDE0E8") else Color.parseColor("#FF444444"))
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        val logBgColor = if (isDark) Color.parseColor("#1A000000") else Color.parseColor("#0A000000")
                        setColor(logBgColor)
                    }
                })
            }

            // 强制更新提示
            if (info.isForceUpdate) {
                container.addView(TextView(activity).apply {
                    text = "⚠ 此为强制更新版本，请更新后继续使用"; textSize = 11f; setTextColor(accentColor)
                    gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#33FF6B6B"))
                    }
                })
            }

            // 按钮行
            val btnRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22) }
            }

            if (!info.isForceUpdate) {
                btnRow.addView(createButton("稍后再说", Color.TRANSPARENT, subTextColor, false) {})
                btnRow.addView(LinearLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(12), LinearLayout.LayoutParams.MATCH_PARENT)
                })
            }

            btnRow.addView(createButton("浏览器下载", Color.TRANSPARENT, subTextColor, false) {
                try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RemoteConfig.updateUrl))) } catch (_: Exception) {}
            })
            btnRow.addView(LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12), LinearLayout.LayoutParams.MATCH_PARENT)
            })

            btnRow.addView(createButton("应用内下载", accentColor, Color.WHITE, true) { startDownload() })
            container.addView(btnRow)

            AlertDialog.Builder(activity)
                .setView(container)
                .setCancelable(!info.isForceUpdate)
                .also { builder ->
                    if (info.isForceUpdate) builder.setOnCancelListener { activity.finish() }
                }
                .create()
                .also { dialog ->
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.show()
                }

        } catch (e: Exception) {
            Log.e(TAG, "显示更新对话框失败，使用备用方案", e)
            fallbackDialog(info)
        }
    }

    private fun createButton(text: String, bgColor: Int, textColor: Int, isPrimary: Boolean, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            this.text = text; textSize = 14f; setTextColor(textColor); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                if (isPrimary) setColor(bgColor) else {
                    setColor(bgColor)
                    val strokeColor = if (isDarkMode()) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A000000")
                    setStroke(dp(1), strokeColor)
                }
            }
            setOnClickListener { onClick() }
        }

    private fun fallbackDialog(info: UpdateInfo) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 v${info.newVersion}")
            .setMessage(buildString {
                append("当前版本: v${info.currentVersion}\n")
                if (RemoteConfig.updateLog.isNotEmpty()) append("\n${RemoteConfig.updateLog}")
                if (info.isForceUpdate) append("\n\n⚠ 此为强制更新版本")
            })
            .setPositiveButton("应用内下载") { _, _ -> startDownload() }
            .setNeutralButton("浏览器下载") { _, _ ->
                try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RemoteConfig.updateUrl))) } catch (_: Exception) {}
            }
            .also { builder ->
                if (!info.isForceUpdate) builder.setNegativeButton("稍后再说", null)
                else builder.setNegativeButton("退出应用") { _, _ -> activity.finish() }
            }
            .show()
    }

    private fun startDownload() {
        val url = RemoteConfig.updateUrl
        if (url.isEmpty()) {
            Toast.makeText(activity, "下载地址未配置", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.startsWith("https://")) {
            Toast.makeText(activity, "更新地址必须为HTTPS", Toast.LENGTH_LONG).show()
            Log.e(TAG, "拒绝非HTTPS更新地址: $url")
            return
        }
        if (!hasSecureUpdateConfig()) {
            Toast.makeText(activity, "更新配置不安全，已阻止下载", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val apkFile = File(
                activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "cloud_music_update.apk"
            )
            if (apkFile.exists()) apkFile.delete()

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("新众和夜雨音乐")
                setDescription("正在下载 v${RemoteConfig.latestVersion}...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE or DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    "cloud_music_update.apk"
                )
                setMimeType("application/vnd.android.package-archive")
            }

            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)

            Toast.makeText(activity, "开始下载更新...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "下载已开始: $downloadId")

            registerDownloadReceiver()
            startProgressPolling(dm)
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            Toast.makeText(activity, "下载失败，尝试浏览器下载", Toast.LENGTH_SHORT).show()
            try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
        }
    }

    private fun startProgressPolling(dm: DownloadManager) {
        Thread {
            var lastPercent = -1
            while (true) {
                try {
                    val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIdx >= 0) {
                            val status = cursor.getInt(statusIdx)
                            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
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
                                val percent = ((done * 100f / total).toInt())
                                if (percent != lastPercent && percent % 5 == 0) {
                                    lastPercent = percent
                                    Log.d(TAG, "下载进度: $percent%")
                                }
                            }
                        }
                        cursor.close()
                    }
                    Thread.sleep(500)
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
                    installApk()
                    try { activity.unregisterReceiver(this) } catch (_: Exception) {}
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
                "cloud_music_update.apk"
            )

            if (!apkFile.exists()) {
                Toast.makeText(activity, "APK文件不存在", Toast.LENGTH_SHORT).show()
                return
            }

            val verifyError = verifyDownloadedApk(apkFile)
            if (verifyError != null) {
                Log.e(TAG, "APK校验失败: $verifyError")
                Toast.makeText(activity, "安装已阻止: $verifyError", Toast.LENGTH_LONG).show()
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

    private fun verifyDownloadedApk(apkFile: File): String? {
        val archiveInfo = activity.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: return "无法解析安装包"
        if (archiveInfo.packageName != activity.packageName) return "安装包包名不匹配"

        val expectedSha = RemoteConfig.updateApkSha256.trim().lowercase()
        if (!SHA256_REGEX.matches(expectedSha)) return "更新配置缺少合法SHA256"
        val actualSha = sha256Of(apkFile)
        if (actualSha != expectedSha) return "SHA256校验失败"
        return null
    }

    private fun hasSecureUpdateConfig(): Boolean {
        val url = RemoteConfig.updateUrl.trim()
        val expectedSha = RemoteConfig.updateApkSha256.trim().lowercase()
        if (url.isEmpty() || !url.startsWith("https://")) return false
        return SHA256_REGEX.matches(expectedSha)
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun isDarkMode(): Boolean {
        val nightMode = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun compareVersion(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(parts1.size, parts2.size)
        for (i in 0 until len) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
