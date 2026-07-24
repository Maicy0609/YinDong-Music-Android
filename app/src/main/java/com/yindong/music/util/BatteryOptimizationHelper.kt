package com.yindong.music.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 电池优化白名单工具类
 *
 * Android 6.0+ 引入了 Doze 模式，未加入白名单的应用在后台可能被系统限制网络访问。
 * 音乐播放器需要加入电池优化白名单，以确保后台播放不被中断。
 *
 * 华为/荣耀设备有独立的电源管理，需要额外适配。
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOpt"

    /** 是否已被加入电池优化白名单 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val result = pm.isIgnoringBatteryOptimizations(context.packageName)
        Log.d(TAG, "isIgnoringBatteryOptimizations: $result (packageName=${context.packageName})")
        return result
    }

    /**
     * 请求加入电池优化白名单，弹出系统授权对话框。
     * 仅在 Android 6.0+ 且未加入白名单时有效。
     *
     * @return true 表示成功启动了系统授权界面
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        Log.d(TAG, "requestIgnoreBatteryOptimizations: 开始请求电池优化白名单")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG, "requestIgnoreBatteryOptimizations: Android < 6.0，无需申请")
            return false
        }
        if (isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "requestIgnoreBatteryOptimizations: 已在白名单中，跳过")
            return false
        }

        // 尝试 1：标准 Android API — 直接弹窗询问
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "requestIgnoreBatteryOptimizations: 标准弹窗已启动")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "requestIgnoreBatteryOptimizations: 标准弹窗 SecurityException (缺少权限?)", e)
            // 尝试 2：华为/荣耀专用电源管理页面
            if (tryHuaweiBatterySettings(context)) return true
            // 尝试 3：标准电池优化设置列表页
            tryBatteryOptimizationSettings(context)
        } catch (e: Exception) {
            Log.w(TAG, "requestIgnoreBatteryOptimizations: 标准弹窗失败", e)
            // 尝试 2：华为/荣耀专用电源管理页面
            if (tryHuaweiBatterySettings(context)) return true
            // 尝试 3：标准电池优化设置列表页
            tryBatteryOptimizationSettings(context)
        }
    }

    /**
     * 打开电池优化设置页面（列表页），让用户手动找到应用并设置。
     * 作为 requestIgnoreBatteryOptimizations 的备用方案。
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        Log.d(TAG, "openBatteryOptimizationSettings: 打开电池优化设置页")
        // 华为/荣耀设备优先使用专用页面
        if (tryHuaweiBatterySettings(context)) return true
        return tryBatteryOptimizationSettings(context)
    }

    /**
     * 尝试打开华为/荣耀设备的电源管理设置页面。
     * 华为 EMUI/HarmonyOS 有独立的电源管理，标准 Android Intent 不生效。
     *
     * @return true 表示成功打开了华为电源管理页面
     */
    private fun tryHuaweiBatterySettings(context: Context): Boolean {
        if (!isHuaweiDevice()) {
            Log.d(TAG, "tryHuaweiBatterySettings: 非华为设备，跳过")
            return false
        }

        Log.d(TAG, "tryHuaweiBatterySettings: 检测到华为设备，尝试打开华为电源管理")

        // 华为/荣耀电源管理相关的 Intent 列表（按优先级排序）
        val huaweiIntents = listOf(
            // 华为应用启动管理（直接定位到本应用）
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                putExtra("packageName", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 华为应用启动管理（旧版 EMUI）
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.activity.StartUpOptimizeActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 荣耀应用启动管理
            Intent().apply {
                component = ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                putExtra("packageName", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 华为电池优化设置
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // 华为系统管家主页面（兜底）
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.mainactivity.MainActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )

        for ((index, intent) in huaweiIntents.withIndex()) {
            try {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "tryHuaweiBatterySettings: 成功打开华为电源管理 (第${index + 1}个Intent)")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "tryHuaweiBatterySettings: 第${index + 1}个Intent失败: ${e.message}")
            }
        }

        Log.w(TAG, "tryHuaweiBatterySettings: 所有华为Intent均失败")
        return false
    }

    /**
     * 尝试打开标准 Android 电池优化设置列表页。
     */
    private fun tryBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "tryBatteryOptimizationSettings: 成功打开标准电池优化设置页")
            true
        } catch (e: Exception) {
            Log.w(TAG, "tryBatteryOptimizationSettings: 打开失败", e)
            // 最后兜底：打开应用详情页，让用户手动修改电池设置
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "tryBatteryOptimizationSettings: 兜底打开应用详情页")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "tryBatteryOptimizationSettings: 所有方式均失败", e2)
                false
            }
        }
    }

    /**
     * 判断是否为华为/荣耀设备。
     * 通过 Build.MANUFACTURER 和 Build.BRAND 判断。
     */
    private fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
        val brand = Build.BRAND?.lowercase().orEmpty()
        return manufacturer.contains("huawei") || brand.contains("huawei") ||
               manufacturer.contains("honor") || brand.contains("honor")
    }
}
