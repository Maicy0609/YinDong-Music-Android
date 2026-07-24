package com.yindong.music

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color as AndroidColorRef
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yindong.music.ui.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.yindong.music.data.LocalStorage
import com.yindong.music.data.RemoteConfig
import com.yindong.music.data.UpdateManager
import com.yindong.music.data.SimpleUpdateChecker
import com.yindong.music.data.SimpleAnnouncementChecker
import com.yindong.music.security.CriticalUiProtector
import com.yindong.music.security.LicenseVerifier
import com.yindong.music.security.SecurityGuard
import com.yindong.music.ui.navigation.AppNavigation
import com.yindong.music.util.BatteryOptimizationHelper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.yindong.music.ui.screens.LockType
import com.yindong.music.ui.screens.VerifyLockScreen
import com.yindong.music.ui.screens.DisclaimerScreen
import com.yindong.music.ui.theme.CloudMusicTheme
import com.yindong.music.ui.theme.ColorSchemeConfig
import com.yindong.music.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 校验状态 */
private sealed class LicenseState {
    object Loading : LicenseState()
    object Passed : LicenseState()
    data class Locked(val type: LockType) : LicenseState()
}

class MainActivity : ComponentActivity() {
    private val musicViewModel: MusicViewModel by viewModels()
    private var lastExternalPluginIntent: String? = null
    private var updateManager: UpdateManager? = null
    private var simpleUpdateChecker: SimpleUpdateChecker? = null
    private var simpleAnnouncementChecker: SimpleAnnouncementChecker? = null
    private var criticalUiWatchdogJob: Job? = null
    private var officialContactFallbackShown = false
    private var lastUiTamperReason: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColorRef.TRANSPARENT, AndroidColorRef.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColorRef.TRANSPARENT, AndroidColorRef.TRANSPARENT),
        )

        // 全屏延伸到系统栏（状态栏叠加显示）
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Android 13+ 请求通知权限（媒体控制通知必须）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Android 12+ 请求蓝牙连接权限（检测蓝牙耳机需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1002)
            }
        }

        requestStoragePermission()

        // 启动时自动检查电池优化白名单，未加入则动态申请
        requestBatteryOptimizationIfNeeded()
        
        // 🛡️ 安全加固检查
        performSecurityCheck()
        startCriticalUiWatchdog()

        // LocalStorage 已在 CloudMusicApplication.onCreate() 中初始化
        // 这里做一次安全兄底，防止极端情况
        LocalStorage.init(this)

        // 应用用户设置的屏幕方向偏好（跟随系统/竖屏/横屏/反向横屏）
        applyOrientationPreference()
        
        // 从服务器拉取远程配置 (控制APP一切行为)
        fetchRemoteConfig()

        // 支持从外部应用直接"打开/分享" .js 插件并自动导入
        handleExternalPluginIntent(intent)
        
        setContent {
            val viewModel = musicViewModel

            // ── 软件启动第一优先级：远程联网校验 ──
            var verifyState by remember {
                mutableStateOf<LicenseState>(LicenseState.Loading)
            }
            LaunchedEffect(Unit) {
                val result = LicenseVerifier.verify(this@MainActivity)
                verifyState = when (result) {
                    LicenseVerifier.Result.Pass -> LicenseState.Passed
                    LicenseVerifier.Result.Offline -> LicenseState.Locked(LockType.OFFLINE)
                    LicenseVerifier.Result.ServerError -> LicenseState.Locked(LockType.SERVER)
                }
            }

            when (val state = verifyState) {
                LicenseState.Loading -> {
                    // 校验中：白屏 + loading，不显示任何主界面
                    Box(
                        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                is LicenseState.Locked -> {
                    // 校验失败：白屏拦截，禁止返回，仅留关闭按钮
                    BackHandler(enabled = true) { /* 拦截返回键 */ }
                    VerifyLockScreen(
                        type = state.type,
                        onClose = { finishAffinity() },
                    )
                }
                LicenseState.Passed -> {
                    // 校验通过：正常渲染主界面
                    var disclaimerAccepted by remember {
                        mutableStateOf(LocalStorage.loadDisclaimerAccepted())
                    }

                    if (!disclaimerAccepted) {
                        DisclaimerScreen(
                            onAccept = {
                                LocalStorage.saveDisclaimerAccepted(true)
                                disclaimerAccepted = true
                            },
                            onDecline = { finish() },
                        )
                    } else {
                        ThemeManager.initTheme(this@MainActivity)
                        var isDark by remember { mutableStateOf(ThemeManager.isDarkMode.value) }
                        var currentColorScheme by remember { mutableStateOf(ThemeManager.colorSchemeState.value) }

                        LaunchedEffect(Unit) {
                            ThemeManager.isDarkMode.collectLatest { dark ->
                                isDark = dark
                            }
                        }

                        LaunchedEffect(Unit) {
                            ThemeManager.colorSchemeState.collectLatest { scheme ->
                                currentColorScheme = scheme
                            }
                        }

                        // ── 动态主题色：监听当前歌曲封面变化，提取主色并应用 ──
                        LaunchedEffect(viewModel) {
                            var lastCoverUrl: String? = null
                            snapshotFlow { viewModel.currentSong?.coverUrl }
                                .collectLatest { coverUrl ->
                                    val url = coverUrl?.takeIf { it.isNotBlank() }
                                    if (url == lastCoverUrl) return@collectLatest
                                    lastCoverUrl = url
                                    if (!url.isNullOrBlank()) {
                                        ThemeManager.updateDynamicTheme(this@MainActivity, url)
                                    }
                                }
                        }

                        CloudMusicTheme(isDark = isDark, colorSchemeConfig = currentColorScheme) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                AppNavigation(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isUiEntryMissingReason(reason: String): Boolean {
        return reason.startsWith("mine_community_entry_missing") ||
            reason.startsWith("mine_critical_button_missing")
    }

    private fun showOfficialContactFallbackDialog(trigger: String) {
        if (officialContactFallbackShown) return
        officialContactFallbackShown = true
        val groupQq = RemoteConfig.officialCommunityQq.ifBlank { CriticalUiProtector.communityQqNumber() }
        val joinUrl = RemoteConfig.officialCommunityJoinUrl.ifBlank { CriticalUiProtector.communityJoinUrl() }
        val title = RemoteConfig.officialCommunityTitle
            .ifBlank { CriticalUiProtector.communityEntryTitle() }
            .ifBlank { "QQ官方群" }
        val subtitle = RemoteConfig.officialCommunitySubtitle
            .ifBlank { CriticalUiProtector.communityEntrySubtitle() }
            .ifBlank { "官方群联系方式" }
        val message = buildString {
            append(subtitle)
            append("\n\n官方群QQ：")
            append(groupQq)
            if (trigger != "remote_config") {
                append("\n\n检测到界面异常，已启用服务器兜底展示。")
            }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
        if (joinUrl.isNotBlank()) {
            builder.setPositiveButton("打开官方群") { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(joinUrl))) }
                    .onFailure { Toast.makeText(this, "打开官方群失败", Toast.LENGTH_SHORT).show() }
            }
            builder.setNegativeButton("关闭", null)
        } else {
            builder.setPositiveButton("知道了", null)
        }
        builder.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalPluginIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 从悬浮窗权限设置页返回时，自动启动悬浮歌词
        musicViewModel.tryStartFloatingLyricsIfNeeded()
        // 跟随系统模式下，恢复时刷新深色状态
        ThemeManager.applySystemDarkMode(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 系统深色模式切换时，跟随系统模式下自动更新
        ThemeManager.applySystemDarkMode(this)
    }

    private fun handleExternalPluginIntent(incoming: Intent?) {
        if (incoming == null) return

        val uri: Uri? = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    incoming.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: ArrayList<Uri>? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                uris?.firstOrNull()
            }
            else -> null
        }

        if (uri == null) return

        val mime = incoming.type?.lowercase().orEmpty()
        val raw = (uri.lastPathSegment ?: uri.toString()).lowercase()
        val looksLikeJs = mime.contains("javascript") || raw.endsWith(".js") || raw.contains(".js?")
        if (!looksLikeJs) return
        val intentFingerprint = "${incoming.action}:${uri}"
        if (intentFingerprint == lastExternalPluginIntent) return
        lastExternalPluginIntent = intentFingerprint

        AlertDialog.Builder(this)
            .setTitle("导入JS插件")
            .setMessage("检测到外部插件文件：\n$uri\n\n仅导入你信任的来源。")
            .setNegativeButton("取消", null)
            .setPositiveButton("导入") { _, _ ->
                lifecycleScope.launch {
                    val result = musicViewModel.importLxPlugin(uri)
                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "JS插件导入成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "导入失败: ${result.exceptionOrNull()?.message ?: "未知错误"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
    }

    /**
     * 检查并申请电池优化白名单权限
     * 如果应用未被加入白名单，延迟3秒后弹出系统授权对话框
     * （延迟3秒确保开屏动画完成、License校验通过后再弹出，避免被遮挡）
     */
    private fun requestBatteryOptimizationIfNeeded() {
        lifecycleScope.launch {
            delay(3000) // 延迟3秒，等UI加载完成且License校验通过
            Log.d("BatteryOpt", "启动电池白名单检查...")
            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@MainActivity)) {
                Log.d("BatteryOpt", "未在白名单中，开始申请...")
                val success = BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this@MainActivity)
                Log.d("BatteryOpt", "申请结果: $success")
                if (!success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "无法自动申请电池白名单，请在设置中手动开启",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                Log.d("BatteryOpt", "已在白名单中，无需申请")
            }
        }
    }

    /**
     * 安全加固检查：反调试 / 反Root / 反模拟器 / 签名校验 / 反Hook / 反抓包
     * 在 IO 线程执行完整检测，根据风险等级决定处置策略
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    1003
                )
            }
        }
    }

    /** 根据用户偏好设置屏幕方向 */
    private fun applyOrientationPreference() {
        val orientation = LocalStorage.loadScreenOrientation()
        requestedOrientation = when (orientation) {
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "REVERSE_LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun performSecurityCheck() {
        lifecycleScope.launch(Dispatchers.IO) {
            val report = SecurityGuard.performAllChecks()
            if (CriticalUiProtector.isStaticPolicyTampered() && BuildConfig.SECURITY_KILL_ON_RISK) {
                SecurityGuard.killProcess()
                return@launch
            }

            if (BuildConfig.DEBUG) {
                // Debug 模式只记录日志，不强制终止
                Log.d("SecurityGuard", "Security Report: $report")
                return@launch
            }

            // ── Release 模式处置策略 ──

            // 高危: 调试器 / 签名篡改 / Hook → 直接终止进程
            if (report.hasHighRisk && BuildConfig.SECURITY_KILL_ON_RISK) {
                SecurityGuard.killProcess()
                return@launch
            }

            // 中危: Root / 模拟器 / 可调试 → 仅记录日志，不打扰用户
            if (report.hasMediumRisk) {
                Log.w("SecurityGuard", "Medium risk detected: $report")
            }
        }
    }

    private fun startCriticalUiWatchdog() {
        criticalUiWatchdogJob?.cancel()
        criticalUiWatchdogJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                val reason = CriticalUiProtector.blockReason()
                if (!reason.isNullOrEmpty()) {
                    Log.e("CriticalUiProtector", "关键界面篡改风险: $reason")
                    // 不再弹出"关键界面篡改风险"提示对话框，保持用户界面简洁无干扰
                    if (!isUiEntryMissingReason(reason)) {
                        if (BuildConfig.SECURITY_KILL_ON_RISK) {
                            SecurityGuard.killProcess()
                        }
                        return@launch
                    }
                    // UI 入口缺失类原因仅记录日志，不弹窗、不终止进程
                }
                delay(2_000)
            }
        }
    }

    override fun onDestroy() {
        criticalUiWatchdogJob?.cancel()
        criticalUiWatchdogJob = null
        updateManager?.cleanup()
        updateManager = null
        simpleUpdateChecker?.cleanup()
        simpleUpdateChecker = null
        super.onDestroy()
    }

    /**
     * 检查启动次数，前3次自动下载酷我和QQ音乐插件
     * 改进：只要插件未完成下载就一直尝试（不受启动次数限制）
     */
    private fun checkAndAutoDownloadPlugins() {
        val launchCount = LocalStorage.incrementAppLaunchCount()
        
        // 检查是否所有必需插件都已下载完成
        val kwDone = LocalStorage.loadString("auto_download_kw_success") == "true"
        val qqDone = LocalStorage.loadString("auto_download_qq_success") == "true"
        
        // 如果两个都已完成，且启动次数超过3次，则跳过
        if (launchCount > 3 && kwDone && qqDone) {
            Log.d("AutoDownload", "⏭️ 所有插件已下载完成，跳过自动下载 (第${launchCount}次启动)")
            return
        }
        
        Log.d("AutoDownload", "🚀 开始检查自动下载 (第${launchCount}次启动, kw=$kwDone, qq=$qqDone)")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 检查是否需要重置自动下载状态（版本升级或修复bug后）
                LocalStorage.checkAndResetAutoDownloadIfNeeded()
                
                val pluginUrls = listOf(
                    "http://music.haitangw.net/cqapi/kw.js" to "kw",
                    "http://music.haitangw.net/cqapi/qq.js" to "qq"
                )

                var allSuccess = true
                val results = mutableMapOf<String, Boolean>()

                for ((url, name) in pluginUrls) {
                    // 检查该插件是否已经成功下载过
                    val alreadyDownloaded = LocalStorage.loadString("auto_download_${name}_success") == "true"
                    
                    if (alreadyDownloaded) {
                        Log.d("AutoDownload", "⏭️ 插件 $name 已下载过，跳过: $url")
                        results[name] = true
                        continue
                    }

                    // 最多重试3次
                    var success = false
                    for (attempt in 1..3) {
                        try {
                            Log.d("AutoDownload", "🔄 [$name] 第${attempt}次尝试下载: $url")
                            val result = musicViewModel.importLxPluginFromUrl(url)
                            
                            if (result.isSuccess) {
                                Log.d("AutoDownload", "✅ [$name] 自动下载成功 (第${attempt}次尝试)")
                                LocalStorage.saveString("auto_download_${name}_success", "true")
                                results[name] = true
                                success = true
                                break
                            } else {
                                val error = result.exceptionOrNull()?.message ?: "未知错误"
                                Log.w("AutoDownload", "❌ [$name] 第${attempt}次失败: $error")
                                
                                if (attempt < 3) {
                                    // 等待2秒后重试
                                    delay(2000L)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AutoDownload", "⚠️ [$name] 第${attempt}次异常: ${e.message}", e)
                            
                            if (attempt < 3) {
                                delay(2000L)
                            }
                        }
                    }

                    if (!success) {
                        Log.e("AutoDownload", "💔 [$name] 3次尝试后仍然失败: $url")
                        results[name] = false
                        allSuccess = false
                    }
                }

                // 只有所有插件都成功才标记为全部完成
                if (allSuccess || results.all { it.value }) {
                    LocalStorage.setAutoDownloadPluginsDone(true)
                    Log.d("AutoDownload", "🎉 第$launchCount 次启动，所有插件自动下载完成")
                } else {
                    val failed = results.filter { !it.value }.keys.joinToString(", ")
                    Log.w("AutoDownload", "⚠️ 第$launchCount 次启动，部分插件下载失败: $failed，下次启动将重试")
                }
            } catch (e: Exception) {
                Log.e("AutoDownload", "自动下载插件流程异常", e)
            }
        }
    }

    /**
     * - 功能开关 (features)
     * - UI控制 (ui_config)
     * - 频率限制 (rate_limits)
     * - API地址 (api_host)
     */
    private fun fetchRemoteConfig() {
        lifecycleScope.launch {
            // ── 0. 先启动简单更新检查和公告检查 (不依赖远程配置) ──
            simpleUpdateChecker = SimpleUpdateChecker(this@MainActivity)
            simpleAnnouncementChecker = SimpleAnnouncementChecker(this@MainActivity)
            lifecycleScope.launch {
                delay(1000) // 延迟1秒，让UI先加载
                simpleUpdateChecker?.checkUpdate()
            }
            lifecycleScope.launch {
                delay(2000) // 延迟2秒显示公告
                simpleAnnouncementChecker?.checkAnnouncement()
                simpleAnnouncementChecker?.checkPluginAnnouncement()
            }

            val success = RemoteConfig.fetch()
            if (!success) {
                Log.w("MainActivity", "远程配置拉取失败，使用默认配置")
                return@launch
            }

            // ── 1. 总开关：服务器关闭则APP不可用 ──
            if (!RemoteConfig.appEnabled) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("服务维护中")
                        .setMessage(RemoteConfig.announcement.ifEmpty { "应用暂时不可用，请稍后再试" })
                        .setCancelable(false)
                        .setPositiveButton("退出") { _, _ -> finish() }
                        .show()
                }
                return@launch
            }

            // ── 2. 版本更新检查 (支持强制/可选更新 + APK下载安装) ──
            updateManager = UpdateManager(this@MainActivity)
            updateManager?.checkUpdate()

            // ── 3. 公告弹窗 ──
            if (RemoteConfig.showAnnouncement && RemoteConfig.announcement.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("公告")
                        .setMessage(RemoteConfig.announcement)
                        .setPositiveButton("知道了", null)
                        .show()
                }
            }

            // ── 4. 顶部通知栏提示 ──
            if (RemoteConfig.noticeBarText.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, RemoteConfig.noticeBarText, Toast.LENGTH_LONG).show()
                }
            }

            // ── 5. 官方联系兜底展示（已移除，保持用户界面简洁无干扰） ──
            // if (RemoteConfig.forceShowOfficialContact) {
            //     withContext(Dispatchers.Main) {
            //         showOfficialContactFallbackDialog(trigger = "remote_config")
            //     }
            // }
        }
    }
}
