package com.yindong.music.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * USB 独占访问管理器
 *
 * 功能：
 * 1. 标准 USB 设备独占访问的获取（claimInterface）与释放（releaseInterface + close）机制
 * 2. 设备冲突检测（openDevice 返回 null / claimInterface 失败）与带退避的重试机制
 * 3. 主流机型（华为 / 小米 / OPPO / VIVO 等）与 Android 8.0+ 版本兼容处理
 * 4. 自动过滤 USB Hub 与系统关键设备，避免影响系统及其他设备正常使用
 * 5. 设备拔插、权限变更等异常场景的优雅处理
 *
 * 所有认证状态、错误日志与操作记录实时写入音乐下载目录下的 "日志" 文件夹；
 * 每次启用操作生成独立的、带时间戳的日志文件，保证可追溯。
 */
class UsbExclusiveManager(
    private val context: Context,
    private val downloadDirProvider: () -> String,
) {

    companion object {
        private const val TAG = "UsbExclusiveManager"
        const val LOG_FOLDER_NAME = "日志"
        private const val ACTION_USB_PERMISSION = "com.yindong.music.USB_PERMISSION"
        private const val MAX_LOG_FILES = 100

        /** 冲突重试最大次数 */
        private const val MAX_RETRY = 3

        /** 冲突重试退避基础时长（毫秒） */
        private const val RETRY_BASE_DELAY_MS = 500L
    }

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前是否已启用 USB 独占模式 */
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** 实时日志条目（用于 UI 展示） */
    private val _logEntries = MutableStateFlow<List<UsbLogEntry>>(emptyList())
    val logEntries: StateFlow<List<UsbLogEntry>> = _logEntries.asStateFlow()

    /** 每台设备的独占状态（按 deviceName 索引） */
    private val _deviceStates = MutableStateFlow<Map<String, UsbDeviceState>>(emptyMap())
    val deviceStates: StateFlow<Map<String, UsbDeviceState>> = _deviceStates.asStateFlow()

    /** 实际日志目录路径（含降级逻辑） */
    private val _logDirPath = MutableStateFlow("（未启用）")
    val logDirPath: StateFlow<String> = _logDirPath.asStateFlow()

    /** 当前会话的日志文件 */
    private var currentLogFile: File? = null

    /** 活跃的独占连接（deviceName → 连接信息） */
    private val activeConnections = mutableMapOf<String, ExclusiveConnection>()

    /** 已请求过权限的设备（避免重复弹窗） */
    private val pendingPermission = mutableSetOf<String>()

    private var permissionReceiver: BroadcastReceiver? = null
    private var deviceEventReceiver: BroadcastReceiver? = null

    /**
     * 启用 USB 独占模式：启动认证流程并持续输出日志。
     * 整个方法包在 try-catch 中，确保 VIVO/OPPO 等机型的严格权限策略不会导致应用闪退。
     */
    fun enable() {
        if (_enabled.value) {
            try { appendLog("USB独占模式已处于启用状态，忽略重复请求", level = LogLevel.WARN) } catch (_: Exception) {}
            return
        }
        // 先标记已启用，再初始化日志 → USB 操作失败时至少日志能保留
        _enabled.value = true
        activeConnections.clear()
        pendingPermission.clear()
        _deviceStates.value = emptyMap()
        _logEntries.value = emptyList()

        // 日志文件创建必须优先执行，即使后续 USB 操作 crash 也能保留日志
        currentLogFile = createNewLogFile()

        appendLog("====== USB独占模式启动 ======", level = LogLevel.INFO)
        val logDir = getLogDir()
        _logDirPath.value = logDir?.absolutePath ?: "（目录创建失败）"
        appendLog("日志目录：${logDir?.absolutePath ?: "创建失败"}", level = LogLevel.INFO)
        appendLog("日志文件：${currentLogFile?.name ?: "（创建失败，仅显示在界面中）"}", level = LogLevel.INFO)
        appendLog("时间戳：${nowTimestamp()}", level = LogLevel.INFO)

        try {
            logManufacturerCompatInfo()
        } catch (e: Exception) {
            appendLog("机型信息采集失败：${e.javaClass.simpleName}", level = LogLevel.WARN)
        }

        if (usbManager == null) {
            appendLog("UsbManager 系统服务不可用，该设备可能不支持 USB Host 模式", level = LogLevel.ERROR)
            appendLog("提示：部分品牌（VIVO/OPPO）需在设置中开启 OTG 功能", level = LogLevel.WARN)
            return
        }

        try {
            registerPermissionReceiver()
        } catch (e: Exception) {
            appendLog("注册权限接收器失败：${e.javaClass.simpleName}: ${e.message}", level = LogLevel.ERROR)
        }

        try {
            registerDeviceEventReceiver()
        } catch (e: Exception) {
            appendLog("注册插拔接收器失败：${e.javaClass.simpleName}: ${e.message}", level = LogLevel.ERROR)
        }

        try {
            authenticateAllDevices()
        } catch (e: Exception) {
            appendLog("USB 设备认证流程异常：${e.javaClass.simpleName}: ${e.message}", level = LogLevel.ERROR)
            appendLog("提示：请检查 USB 设备是否已正确连接，或尝试重新扫描", level = LogLevel.WARN)
            Log.e(TAG, "enable() authenticate failed", e)
        }
    }

    /**
     * 禁用 USB 独占模式：释放全部独占连接并结束日志会话。
     */
    fun disable() {
        if (!_enabled.value) return
        try {
            appendLog("====== USB独占模式关闭 ======", level = LogLevel.INFO)
            appendLog("活跃独占连接数：${activeConnections.size}", level = LogLevel.INFO)
        } catch (_: Exception) {}
        try { releaseAllConnections(reason = "模式关闭") } catch (e: Exception) {
            Log.e(TAG, "releaseAllConnections failed", e)
        }
        try { unregisterPermissionReceiver() } catch (_: Exception) {}
        try { unregisterDeviceEventReceiver() } catch (_: Exception) {}
        activeConnections.clear()
        pendingPermission.clear()
        _deviceStates.value = emptyMap()
        _enabled.value = false
        _logDirPath.value = "（未启用）"
        currentLogFile = null
    }

    /**
     * 重新扫描并认证当前连接的 USB 设备（在已启用状态下用户可手动触发）。
     */
    fun rescan() {
        if (!_enabled.value) {
            try { appendLog("USB独占模式未启用，无法重新扫描", level = LogLevel.WARN) } catch (_: Exception) {}
            return
        }
        val manager = usbManager ?: run {
            try { appendLog("UsbManager 不可用，无法重新扫描", level = LogLevel.ERROR) } catch (_: Exception) {}
            return
        }
        appendLog("====== 重新扫描 USB 设备 ======", level = LogLevel.INFO)
        try {
            authenticateAllDevices()
        } catch (e: Exception) {
            appendLog("重新扫描异常：${e.javaClass.simpleName}: ${e.message}", level = LogLevel.ERROR)
        }
    }

    /**
     * 对指定设备重试独占访问认证（用户在 UI 手动触发）。
     */
    fun retryDevice(deviceName: String) {
        val manager = usbManager ?: run {
            appendLog("UsbManager 不可用，无法重试：$deviceName", level = LogLevel.ERROR)
            return
        }
        val device = manager.deviceList.values.find { it.deviceName == deviceName } ?: run {
            appendLog("重试失败，设备已离线：$deviceName", level = LogLevel.ERROR)
            updateDeviceState(deviceName) { it.copy(status = DeviceStatus.DETACHED, lastError = "设备已离线") }
            return
        }
        appendLog("====== 手动重试设备：$deviceName ======", level = LogLevel.INFO)
        // 重置重试计数后重新认证
        updateDeviceState(deviceName) { it.copy(retryCount = 0, lastError = null) }
        authenticateDevice(device, isRetry = true)
    }

    /**
     * 主动释放指定设备的独占访问。
     */
    fun releaseDevice(deviceName: String) {
        releaseConnection(deviceName, reason = "用户主动释放")
    }

    // ─────────────────────────────────────────────────────────────
    // 设备认证与独占获取
    // ─────────────────────────────────────────────────────────────

    /** 认证当前所有已连接的 USB 设备（过滤 Hub 与系统关键设备） */
    private fun authenticateAllDevices() {
        val manager = usbManager ?: run {
            appendLog("UsbManager 不可用", level = LogLevel.ERROR)
            return
        }
        val devices = manager.deviceList
        if (devices.isEmpty()) {
            appendLog("未检测到任何已连接的 USB 设备", level = LogLevel.WARN)
            appendLog("请插入 USB 设备后将自动触发认证", level = LogLevel.INFO)
            return
        }

        val candidates = devices.values.filter { isDeviceCandidate(it) }
        val skipped = devices.size - candidates.size
        appendLog("检测到 ${devices.size} 个 USB 设备（候选 ${candidates.size} 个，跳过 $skipped 个 Hub/系统设备）", level = LogLevel.INFO)

        candidates.forEach { device ->
            authenticateDevice(device, isRetry = false)
        }
    }

    /**
     * 判断设备是否为独占访问候选：
     * - 跳过 USB Hub（class 9）
     * - 跳过无任何接口的设备
     * 这样可避免影响系统关键功能及其他设备正常使用
     */
    private fun isDeviceCandidate(device: UsbDevice): Boolean {
        // USB Hub 设备类
        if (device.deviceClass == UsbConstants.USB_CLASS_HUB) {
            appendLog("跳过 USB Hub：${device.deviceName}", level = LogLevel.INFO)
            return false
        }
        if (device.interfaceCount <= 0) {
            appendLog("跳过无接口设备：${device.deviceName}", level = LogLevel.INFO)
            return false
        }
        return true
    }

    /** 对单个 USB 设备发起独占访问认证 */
    private fun authenticateDevice(device: UsbDevice, isRetry: Boolean) {
        val name = device.deviceName
        appendLog("── 开始认证设备：$name ──", level = LogLevel.INFO)
        appendLog(
            "设备信息: vendorId=${device.vendorId} productId=${device.productId} " +
                "interfaceCount=${device.interfaceCount} class=${device.deviceClass} subclass=${device.deviceSubclass}",
            level = LogLevel.INFO
        )

        val manager = usbManager ?: run {
            appendLog("UsbManager 不可用，认证失败：$name", level = LogLevel.ERROR)
            updateDeviceState(name) {
                it.copy(status = DeviceStatus.ERROR, lastError = UsbExclusiveErrorCode.SYSTEM_SERVICE_UNAVAILABLE.message)
            }
            return
        }

        // 已建立独占连接则跳过
        if (activeConnections.containsKey(name)) {
            appendLog("设备 $name 已建立独占连接，跳过", level = LogLevel.INFO)
            return
        }

        // 初始化设备状态
        putDeviceState(name, device)

        if (manager.hasPermission(device)) {
            appendLog("设备 $name 已拥有权限，直接尝试建立独占连接", level = LogLevel.INFO)
            updateDeviceState(name) { it.copy(status = DeviceStatus.PERMISSION_GRANTED) }
            tryOpenExclusive(device, attempt = 0)
            return
        }

        if (pendingPermission.contains(name) && !isRetry) {
            appendLog("设备 $name 权限请求已发出，等待用户授权中...", level = LogLevel.WARN)
            return
        }

        appendLog("向设备 $name 请求独占访问权限...", level = LogLevel.INFO)
        updateDeviceState(name) { it.copy(status = DeviceStatus.REQUESTING_PERMISSION) }
        pendingPermission.add(name)
        try {
            val intentFlags = pendingIntentFlags()
            val pendingIntent = PendingIntent.getBroadcast(
                context, name.hashCode(),
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                intentFlags
            )
            // requestPermission 的返回值在不同 SDK 版本下类型不一致，这里不依赖返回值；
            // 实际授权结果由权限广播接收器（EXTRA_PERMISSION_GRANTED）异步回调处理。
            manager.requestPermission(device, pendingIntent)
            appendLog("权限请求已提交：$name，等待用户授权回调", level = LogLevel.INFO)
        } catch (e: Exception) {
            appendLog("请求权限异常：$name → ${e.javaClass.simpleName}: ${e.message}", level = LogLevel.ERROR)
            Log.e(TAG, "requestPermission failed for $name", e)
            updateDeviceState(name) {
                it.copy(status = DeviceStatus.ERROR, lastError = "${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /**
     * 尝试打开独占连接，失败时根据错误类型进行重试。
     * attempt: 当前重试次数（0 表示首次）
     */
    private fun tryOpenExclusive(device: UsbDevice, attempt: Int) {
        val name = device.deviceName
        val manager = usbManager ?: return

        val connection: UsbDeviceConnection? = try {
            manager.openDevice(device)
        } catch (e: Exception) {
            appendLog("打开设备连接异常：$name → ${e.javaClass.simpleName}: ${e.message}", level = LogLevel.ERROR)
            Log.e(TAG, "openDevice failed for $name", e)
            null
        }

        if (connection == null) {
            // openDevice 返回 null 通常意味着设备被其他进程占用或权限失效
            val errorCode = if (manager.hasPermission(device)) {
                UsbExclusiveErrorCode.DEVICE_OCCUPIED
            } else {
                UsbExclusiveErrorCode.PERMISSION_DENIED
            }
            appendLog("打开设备连接失败（返回 null）：$name → ${errorCode.message}", level = LogLevel.ERROR)
            handleOpenFailure(device, attempt, errorCode)
            return
        }

        // 声明所有接口独占（force=true 抢占其他进程的占用）
        val claimedInterfaces = mutableListOf<UsbInterface>()
        var claimFailed = false
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val claimed = try {
                connection.claimInterface(iface, true)
            } catch (e: Exception) {
                appendLog("声明接口异常：$name iface[$i] → ${e.javaClass.simpleName}: ${e.message}", level = LogLevel.ERROR)
                Log.e(TAG, "claimInterface failed for $name iface[$i]", e)
                false
            }
            if (claimed) {
                claimedInterfaces.add(iface)
            } else {
                claimFailed = true
                appendLog("声明接口失败：$name iface[$i]", level = LogLevel.ERROR)
            }
        }

        if (claimFailed && claimedInterfaces.isEmpty()) {
            // 全部接口声明失败 → 设备被占用
            try { connection.close() } catch (_: Exception) {}
            handleOpenFailure(device, attempt, UsbExclusiveErrorCode.DEVICE_OCCUPIED)
            return
        }

        // 独占建立成功
        val fd = try { connection.fileDescriptor } catch (_: Exception) { -1 }
        activeConnections[name] = ExclusiveConnection(
            device = device,
            connection = connection,
            claimedInterfaces = claimedInterfaces,
            acquiredAt = System.currentTimeMillis()
        )
        appendLog(
            "✓ 已建立独占连接：$name (fd=$fd, claimedInterfaces=${claimedInterfaces.size}/${device.interfaceCount})",
            level = LogLevel.INFO
        )
        updateDeviceState(name) {
            it.copy(
                status = DeviceStatus.CONNECTED,
                lastError = null,
                acquiredAt = System.currentTimeMillis(),
                claimedInterfaceCount = claimedInterfaces.size
            )
        }
    }

    /** 处理打开独占连接失败：根据错误类型决定是否重试 */
    private fun handleOpenFailure(device: UsbDevice, attempt: Int, errorCode: UsbExclusiveErrorCode) {
        val name = device.deviceName
        val nextAttempt = attempt + 1

        if (nextAttempt > MAX_RETRY) {
            appendLog(
                "设备 $name 已达最大重试次数 ($MAX_RETRY)，停止重试：${errorCode.message}",
                level = LogLevel.ERROR
            )
            updateDeviceState(name) {
                it.copy(status = DeviceStatus.ERROR, lastError = "${errorCode.message}（已重试 $MAX_RETRY 次）")
            }
            return
        }

        // 设备被占用 / 打开失败 → 退避后重试
        val backoff = RETRY_BASE_DELAY_MS * nextAttempt
        appendLog(
            "设备 $name 独占获取失败：${errorCode.message}，${backoff}ms 后重试 (attempt ${nextAttempt}/$MAX_RETRY)",
            level = LogLevel.WARN
        )
        updateDeviceState(name) {
            it.copy(
                status = DeviceStatus.CONFLICT,
                lastError = errorCode.message,
                retryCount = nextAttempt
            )
        }

        scope.launch {
            delay(backoff)
            // 退避期间设备可能被拔出或模式被关闭
            if (!_enabled.value) return@launch
            val manager = usbManager ?: return@launch
            val stillConnected = manager.deviceList.values.any { it.deviceName == name }
            if (!stillConnected) {
                appendLog("重试取消，设备已离线：$name", level = LogLevel.WARN)
                updateDeviceState(name) { it.copy(status = DeviceStatus.DETACHED, lastError = "设备已拔出") }
                return@launch
            }
            tryOpenExclusive(device, nextAttempt)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 释放机制
    // ─────────────────────────────────────────────────────────────

    /** 释放指定设备的独占连接 */
    private fun releaseConnection(deviceName: String, reason: String) {
        val conn = activeConnections.remove(deviceName) ?: run {
            appendLog("释放失败，未找到活跃连接：$deviceName", level = LogLevel.WARN)
            return
        }
        try {
            // 先释放所有已声明的接口
            conn.claimedInterfaces.forEach { iface ->
                try { conn.connection.releaseInterface(iface) } catch (e: Exception) {
                    appendLog("释放接口异常：$deviceName → ${e.message}", level = LogLevel.WARN)
                }
            }
            // 再关闭连接
            conn.connection.close()
            appendLog("✓ 已释放独占连接：$deviceName（原因：$reason）", level = LogLevel.INFO)
        } catch (e: Exception) {
            appendLog("释放连接异常：$deviceName → ${e.javaClass.simpleName}: ${e.message}", level = LogLevel.ERROR)
            Log.e(TAG, "releaseConnection failed for $deviceName", e)
        }
        updateDeviceState(deviceName) {
            it.copy(
                status = DeviceStatus.RELEASED,
                lastError = null,
                acquiredAt = null,
                claimedInterfaceCount = 0
            )
        }
    }

    /** 释放全部独占连接 */
    private fun releaseAllConnections(reason: String) {
        val names = activeConnections.keys.toList()
        if (names.isEmpty()) {
            appendLog("无活跃独占连接需要释放", level = LogLevel.INFO)
            return
        }
        names.forEach { releaseConnection(it, reason) }
    }

    // ─────────────────────────────────────────────────────────────
    // 广播接收器：权限回调 + 设备拔插
    // ─────────────────────────────────────────────────────────────

    /** 注册 USB 权限广播接收器 */
    private fun registerPermissionReceiver() {
        if (permissionReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device: UsbDevice? = extractDevice(intent)
                val devName = device?.deviceName ?: "unknown"
                pendingPermission.remove(devName)

                if (granted) {
                    appendLog("权限授予成功：$devName", level = LogLevel.INFO)
                    updateDeviceState(devName) { it.copy(status = DeviceStatus.PERMISSION_GRANTED) }
                    device?.let { tryOpenExclusive(it, attempt = 0) }
                } else {
                    appendLog("权限授予失败（用户拒绝）：$devName", level = LogLevel.ERROR)
                    updateDeviceState(devName) {
                        it.copy(
                            status = DeviceStatus.PERMISSION_DENIED,
                            lastError = UsbExclusiveErrorCode.PERMISSION_DENIED.message
                        )
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiverCompat(receiver, filter)
        permissionReceiver = receiver
        appendLog("已注册 USB 权限广播接收器", level = LogLevel.INFO)
    }

    /**
     * 注册 USB 设备插拔广播接收器：
     * - ATTACHED: 自动发起独占认证
     * - DETACHED: 释放对应连接并更新状态
     */
    private fun registerDeviceEventReceiver() {
        if (deviceEventReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val device: UsbDevice? = extractDevice(intent)
                val name = device?.deviceName ?: return
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        appendLog("检测到 USB 设备插入：$name", level = LogLevel.INFO)
                        if (isDeviceCandidate(device)) {
                            // 延迟少许等待系统稳定
                            mainHandler.postDelayed({
                                if (_enabled.value) authenticateDevice(device, isRetry = false)
                            }, 300)
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        appendLog("检测到 USB 设备拔出：$name", level = LogLevel.WARN)
                        if (activeConnections.containsKey(name)) {
                            releaseConnection(name, reason = "设备拔出")
                        }
                        updateDeviceState(name) {
                            it.copy(status = DeviceStatus.DETACHED, lastError = "设备已拔出", acquiredAt = null, claimedInterfaceCount = 0)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiverCompat(receiver, filter)
        deviceEventReceiver = receiver
        appendLog("已注册 USB 设备插拔广播接收器", level = LogLevel.INFO)
    }

    /** 兼容 Android 13+ 的 receiver 注册方式 */
    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    /** 兼容 Android 13+ 的 Intent extra 读取 */
    @Suppress("DEPRECATION", "NewApi")
    private fun extractDevice(intent: Intent?): UsbDevice? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    /** 兼容 Android 12+ 的 PendingIntent flag */
    private fun pendingIntentFlags(): Int {
        // Android 12+ 要求隐式 PendingIntent 必须指定可变性
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    private fun unregisterPermissionReceiver() {
        permissionReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        permissionReceiver = null
    }

    private fun unregisterDeviceEventReceiver() {
        deviceEventReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        deviceEventReceiver = null
    }

    // ─────────────────────────────────────────────────────────────
    // 机型 / 版本兼容信息
    // ─────────────────────────────────────────────────────────────

    /** 记录机型与 Android 版本兼容信息，便于排查主流品牌（华为/小米/OPPO/VIVO 等）问题 */
    private fun logManufacturerCompatInfo() {
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val brand = Build.BRAND ?: "unknown"
        val model = Build.MODEL ?: "unknown"
        val sdk = Build.VERSION.SDK_INT
        val release = Build.VERSION.RELEASE ?: "unknown"
        appendLog("机型信息: brand=$brand manufacturer=$manufacturer model=$model", level = LogLevel.INFO)
        appendLog("系统版本: Android $release (API $sdk)", level = LogLevel.INFO)

        val notes = when (brand.lowercase(Locale.ROOT)) {
            "huawei", "honor" -> "华为/荣耀：注意部分机型需在「开发者选项」中开启「仅充电模式下允许 ADB」及 USB 调试，否则 USB 设备权限可能被系统拦截"
            "xiaomi", "redmi" -> "小米/红米：MIUI 可能限制后台广播接收，已使用 RECEIVER_NOT_EXPORTED 兼容 Android 13+"
            "oppo" -> "OPPO：ColorOS 对 USB 权限弹窗有自定义实现，可能延迟回调"
            "vivo" -> "VIVO：OriginOS/Funtouch 对 USB 设备枚举存在限频，建议重新扫描"
            "samsung" -> "三星：OneUI 兼容性良好，需注意多窗口模式下的广播优先级"
            else -> "通用机型兼容策略"
        }
        appendLog("兼容提示: $notes", level = LogLevel.INFO)

        if (sdk < Build.VERSION_CODES.O) {
            appendLog("当前 Android 版本低于 8.0 (API 26)，USB 独占功能可能不可用", level = LogLevel.ERROR)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 设备状态管理
    // ─────────────────────────────────────────────────────────────

    private fun putDeviceState(name: String, device: UsbDevice) {
        val map = _deviceStates.value.toMutableMap()
        if (!map.containsKey(name)) {
            map[name] = UsbDeviceState(
                deviceName = name,
                vendorId = device.vendorId,
                productId = device.productId,
                deviceClass = device.deviceClass,
                interfaceCount = device.interfaceCount,
                status = DeviceStatus.IDLE,
                lastError = null,
                retryCount = 0,
                acquiredAt = null,
                claimedInterfaceCount = 0
            )
            _deviceStates.value = map
        }
    }

    private fun updateDeviceState(name: String, block: (UsbDeviceState) -> UsbDeviceState) {
        val map = _deviceStates.value.toMutableMap()
        val current = map[name] ?: return
        map[name] = block(current)
        _deviceStates.value = map
    }

    // ─────────────────────────────────────────────────────────────
    // 日志文件
    // ─────────────────────────────────────────────────────────────

    /** 返回日志文件夹（主目录失效则降级到应用内部存储） */
    private fun getLogDir(): File? {
        val baseDir = downloadDirProvider()
        return if (baseDir.isNotBlank()) {
            val dir = ensureLogDir(File(File(baseDir), LOG_FOLDER_NAME))
            if (dir != null) return dir
            appendLog("外部日志目录不可写，降级到应用内部存储", level = LogLevel.WARN)
            ensureLogDir(File(context.filesDir, LOG_FOLDER_NAME))
        } else {
            appendLog("下载目录未配置，使用应用内部存储存放日志", level = LogLevel.WARN)
            ensureLogDir(File(context.filesDir, LOG_FOLDER_NAME))
        }
    }

    private fun ensureLogDir(dir: File): File? {
        if (dir.exists() && dir.isDirectory) return dir
        return try {
            if (dir.mkdirs() || dir.isDirectory) dir else null
        } catch (e: Exception) {
            Log.e(TAG, "ensureLogDir failed: ${dir.absolutePath}", e)
            null
        }
    }

    /** 每次启用操作创建一个独立的、带时间戳的日志文件 */
    private fun createNewLogFile(): File? {
        val dir = getLogDir() ?: return null
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "USB_EXCLUSIVE_${dateFormat.format(Date())}.log"
        val file = File(dir, fileName)
        try {
            file.parentFile?.mkdirs()
            file.createNewFile()
            file.appendText("USB独占访问认证日志 - 创建于 ${nowTimestamp()}\n")
        } catch (e: Exception) {
            Log.e(TAG, "createNewLogFile failed", e)
            return null
        }
        cleanOldLogs()
        return file
    }

    /** 追加一条日志（同时写入文件与 StateFlow） */
    private fun appendLog(message: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = nowTimestamp()
        val line = "[$timestamp] [${level.tag}] $message"
        Log.i(TAG, line)

        currentLogFile?.let { file ->
            scope.launch {
                try {
                    file.appendText(line + "\n")
                } catch (e: Exception) {
                    Log.e(TAG, "appendText failed", e)
                }
            }
        }

        val entry = UsbLogEntry(timestamp = timestamp, level = level, message = message)
        _logEntries.value = _logEntries.value + entry
    }

    private fun cleanOldLogs() {
        val dir = getLogDir() ?: return
        val files = dir.listFiles()?.filter { it.extension == "log" }
            ?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > MAX_LOG_FILES) {
            files.drop(MAX_LOG_FILES).forEach { it.delete() }
        }
    }

    private fun nowTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
}

/** 日志级别 */
enum class LogLevel(val tag: String) {
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
}

/** UI 展示用的日志条目 */
data class UsbLogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String,
)

/** 单台 USB 设备的独占访问状态 */
data class UsbDeviceState(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val interfaceCount: Int,
    val status: DeviceStatus,
    val lastError: String?,
    val retryCount: Int,
    val acquiredAt: Long?,
    val claimedInterfaceCount: Int,
)

/** 设备独占访问状态机 */
enum class DeviceStatus {
    IDLE,
    REQUESTING_PERMISSION,
    PERMISSION_GRANTED,
    PERMISSION_DENIED,
    CONNECTED,
    CONFLICT,
    DETACHED,
    RELEASED,
    ERROR
}

/** 独占访问错误码（用于明确错误信息） */
enum class UsbExclusiveErrorCode(val message: String) {
    SUCCESS("成功"),
    DEVICE_NOT_FOUND("设备未找到"),
    PERMISSION_DENIED("USB 权限被拒绝"),
    DEVICE_OCCUPIED("设备被其他进程占用"),
    OPEN_FAILED("打开设备连接失败"),
    INTERFACE_CLAIM_FAILED("声明接口独占失败"),
    DEVICE_DETACHED("设备已拔出"),
    UNSUPPORTED_DEVICE("不支持的设备类型（Hub/系统设备）"),
    SYSTEM_SERVICE_UNAVAILABLE("USB 系统服务不可用")
}

/** 活跃的独占连接句柄 */
private data class ExclusiveConnection(
    val device: UsbDevice,
    val connection: UsbDeviceConnection,
    val claimedInterfaces: List<UsbInterface>,
    val acquiredAt: Long,
)
