package com.yindong.music

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.yindong.music.data.CrashLogManager
import com.yindong.music.data.LocalStorage
import com.yindong.music.data.StatsReporter
import com.yindong.music.security.CriticalUiProtector
import com.yindong.music.security.SecurityGuard

/**
 * 自定义 Application — 确保全局初始化在所有组件之前完成
 */
class CloudMusicApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // 1. 最先初始化本地存储
        LocalStorage.init(this)
        LocalStorage.loadOrCreateDeviceId()
        // 2. 初始化崩溃日志管理器
        CrashLogManager.init(this)
        // 3. 安装全局异常捕获 (写入日志 + 前台服务异常兖底)
        CrashLogManager.installCrashHandler(this)
        // 4. 初始化安全加固模块
        SecurityGuard.init(this, BuildConfig.EXPECTED_SIGNATURE)
        CriticalUiProtector.init(this)

        // 5. 在 Application 层做早期快速检测（调试器 / Hook）
        if (BuildConfig.SECURITY_KILL_ON_RISK) {
            if (SecurityGuard.checkDebugger() || SecurityGuard.checkHook() || CriticalUiProtector.isStaticPolicyTampered()) {
                SecurityGuard.killProcess()
            }
        }

        // Debug 模式下输出当前签名哈希，方便首次配置
        if (BuildConfig.DEBUG) {
            Log.d("SecurityGuard", "APK Signature SHA-256: ${SecurityGuard.getCurrentSignatureHash()}")
        }

        // 6. 上报软件打开统计到后台管理系统
        StatsReporter.reportAppOpen()
    }

    /**
     * 全局 Coil ImageLoader 配置
     * - 内存缓存：取应用可用内存的 25%，保证封面图快速命中缓存
     * - 磁盘缓存：100MB，持久化已加载的图片
     * - crossfade 关闭：滑动时交叉淡入动画会导致大量额外绘制帧
     *   （单张图在滑动中反复触发 alpha 动画，严重拖慢帧率）
     * - respectCacheHeaders = false：音乐封面图不变，强制缓存
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
