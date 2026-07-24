package com.yindong.music.data

import android.os.Build
import android.util.Log
import com.yindong.music.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 统计数据上报器
 *
 * 将 App 使用数据上报到后台管理系统 (11hotai)
 * - 软件打开事件 (app_open)
 * - 搜索事件 (search)
 * - 设备注册 (device)
 *
 * 上报地址: https://jilu.zh2026.cn/api/record.php
 * 后端已改为 Token 可选校验，未配置 Token 时直接上报
 */
object StatsReporter {

    private const val TAG = "StatsReporter"

    /**
     * 后台统计接口地址
     */
    private const val STATS_API_URL = "https://jilu.zh2026.cn/api/record.php"

    /**
     * API Token — 从后台「API接口」页面获取
     */
    private const val API_TOKEN = "4b7a474944c2b0c1bf727d24ed513ea9629910f9f5319103"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 设备型号，如 "Xiaomi 14" */
    private val deviceModel: String
        get() = "${Build.BRAND} ${Build.MODEL}".trim()

    /** 系统版本，如 "Android 14" */
    private val osVersion: String
        get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /** App 版本号 */
    private val appVersion: String
        get() = BuildConfig.VERSION_NAME

    /**
     * 上报软件打开事件
     * 在 Application.onCreate() 中调用
     */
    fun reportAppOpen() {
        val deviceId = LocalStorage.loadOrCreateDeviceId()
        report("app_open", deviceId, null)
    }

    /**
     * 上报搜索事件
     * @param keyword 搜索关键词
     * @param source 搜索来源平台 (如 "网易云", "QQ", "全部")
     */
    fun reportSearch(keyword: String, source: String? = null) {
        val deviceId = LocalStorage.loadOrCreateDeviceId()
        report("search", deviceId, keyword, source)
    }

    /**
     * 上报设备信息（注册/更新设备）
     */
    fun reportDevice() {
        val deviceId = LocalStorage.loadOrCreateDeviceId()
        report("device", deviceId, null)
    }

    /**
     * 核心上报方法 — 异步发送，不阻塞主线程，失败静默忽略
     */
    private fun report(
        type: String,
        deviceId: String,
        keyword: String? = null,
        source: String? = null,
    ) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", type)
                    put("device_id", deviceId)
                    put("device_model", deviceModel)
                    put("os_version", osVersion)
                    put("app_version", appVersion)
                    if (keyword != null) put("keyword", keyword)
                    if (source != null) put("source", source)
                }

                val requestBuilder = Request.Builder()
                    .url(STATS_API_URL)
                    .header("Content-Type", "application/json")
                    .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))

                // Token 非空时携带，后端可选校验
                if (API_TOKEN.isNotBlank()) {
                    requestBuilder.header("X-API-Token", API_TOKEN)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "统计上报成功: type=$type, deviceId=$deviceId")
                    } else {
                        Log.w(TAG, "统计上报失败: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                // 静默失败，不影响 App 正常使用
                Log.d(TAG, "统计上报异常(已忽略): ${e.message}")
            }
        }
    }
}
