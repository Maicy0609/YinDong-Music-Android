package com.yindong.music.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/**
 * LicenseVerifier — 软件启动远程联网校验器（Kotlin 版，对应 frontend-verify/verify-utils.js）
 *
 * 执行顺序：
 *   1. 检测网络连通性（断网直接拦截）
 *   2. 解密 4 个校验 URL（XOR + Base64，代码内不暴露明文）
 *   3. 并发请求 4 个校验地址（3000ms 超时，全部返回 "yes" 才通过）
 *   4. 失败重试 1 次（重试失败永久拦截）
 *
 * 判定拦截条件（任一即拦截）：
 *   返回非 "yes"、HTTP 4xx/5xx、超时、SSL 异常、连接失败
 *
 * ⚠️ 安全声明：客户端校验无法做到绝对不可绕过，本方案最大化提升逆向门槛。
 */
object LicenseVerifier {

    private const val TAG = "LicenseVerifier"

    /** 校验结果 */
    sealed class Result {
        /** 校验通过 */
        object Pass : Result()
        /** 断网 */
        object Offline : Result()
        /** 服务器校验异常（返回 no / HTTP 错误 / 超时 / SSL 异常） */
        object ServerError : Result()
    }

    // ============================================================
    // 1. URL 混淆加密 / 解密（与 JS 版 verify-utils.js 完全一致）
    // ============================================================

    /** XOR 密钥（与 JS 版 _URL_KEY 一致） */
    private const val URL_KEY = "Yd$2026#Secure*Kv9"

    /**
     * 4 个校验地址密文（与 JS 版 _ENCRYPTED_URLS 一致）
     * 解密后为 https://yindong.zh2026.cn/yy/public/{wyy,kw,kg,qq}.txt
     */
    private val ENCRYPTED_URLS = listOf(
        "MRBQQkMIGQwqDA0RHQtNZQxRa1QWBB5RWAwqHEwFBwdGIhUWLh1dHERKQg==",
        "MRBQQkMIGQwqDA0RHQtNZQxRa1QWBB5RWAwqHEwFBwdGIhUWMhMKRkhG",
        "MRBQQkMIGQwqDA0RHQtNZQxRa1QWBB5RWAwqHEwFBwdGIhUWMgMKRkhG",
        "MRBQQkMIGQwqDA0RHQtNZQxRa1QWBB5RWAwqHEwFBwdGIhUWKBUKRkhG",
    )

    /**
     * XOR + Base64 解密（与 JS decryptUrl 一致）
     */
    private fun decryptUrl(encrypted: String, key: String): String {
        val bytes = Base64.getDecoder().decode(encrypted)
        val sb = StringBuilder()
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF       // 转无符号
            val k = key[i % key.length].code
            sb.append((b xor k).toChar())
        }
        return sb.toString()
    }

    /** 获取解密后的 4 个校验 URL（每次调用动态解密，内存中不长期缓存明文） */
    private fun getVerifyUrls(): List<String> =
        ENCRYPTED_URLS.map { decryptUrl(it, URL_KEY) }

    // ============================================================
    // 2. 网络连通性检测
    // ============================================================

    /**
     * 双重检测网络连通性：
     *   - 第一层：ConnectivityManager 判断有无活动网络
     *   - 第二层：实际 HTTP 请求验证真实连通性（避免 host 劫持误报）
     */
    private suspend fun checkNetwork(context: Context): Boolean = withContext(Dispatchers.IO) {
        // 第一层：系统 API 判断
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return@withContext false
            }
        }
        // 第二层：实际请求验证
        try {
            withTimeout(3000L) {
                val conn = URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.requestMethod = "HEAD"
                conn.useCaches = false
                conn.instanceFollowRedirects = false
                val code = conn.responseCode
                conn.disconnect()
                code in 200..399
            }
        } catch (e: Exception) {
            Log.w(TAG, "network check failed: ${e.message}")
            false
        }
    }

    // ============================================================
    // 3. 设备唯一标识指纹
    // ============================================================

    /**
     * 生成设备指纹（组合 Android ID + 屏幕信息 + 随机 UUID）
     * 每次启动 UUID 变化，但 Android ID 稳定，服务端可识别批量破解设备
     */
    private fun generateFingerprint(context: Context): String {
        val parts = mutableListOf<String>()
        // Android ID（稳定标识）
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank()) parts.add(androidId)
        } catch (_: Exception) { }
        // 机型
        parts.add(Build.MANUFACTURER)
        parts.add(Build.MODEL)
        // 随机 UUID（每次启动变化，避免长期追踪，但服务端可记录批次）
        parts.add(UUID.randomUUID().toString())
        // SHA-256 摘要
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(parts.joinToString("|").toByteArray())
        return "fp_" + hash.joinToString("") { "%02x".format(it) }
    }

    // ============================================================
    // 4. 并发请求校验
    // ============================================================

    /**
     * 并发请求 4 个校验地址，全部返回纯文本 "yes" 才通过
     * @param timeoutMs 单次超时 3000ms
     * @return true=全部通过 false=任一失败
     */
    private suspend fun fetchVerifyUrls(
        context: Context,
        urls: List<String>,
        timeoutMs: Int = 3000
    ): Boolean = coroutineScope {
        val results = urls.map { url ->
            async(Dispatchers.IO) { fetchSingle(url, context, timeoutMs) }
        }.awaitAll()
        results.all { it }
    }

    /**
     * 单个校验请求
     * - 严格 HTTPS（HttpURLConnection 默认校验证书，不主动关闭）
     * - 禁止重定向（instanceFollowRedirects = false，防止劫持）
     * - 禁用缓存
     * - 携带设备指纹请求头
     */
    private fun fetchSingle(url: String, context: Context, timeoutMs: Int): Boolean {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                useCaches = false
                instanceFollowRedirects = false   // 禁止重定向，防止劫持
                setRequestProperty("X-Device-Fingerprint", generateFingerprint(context))
                setRequestProperty("X-Verify-Token", "yd_" + System.currentTimeMillis().toString(16))
                setRequestProperty("Cache-Control", "no-store")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "verify failed: HTTP $code for $url")
                conn.disconnect()
                return false
            }
            val text = conn.inputStream.bufferedReader().use { it.readText().trim() }
            conn.disconnect()
            if (text != "yes") {
                Log.w(TAG, "verify failed: response=\"$text\" for $url")
                return false
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "verify failed: ${e.message} for $url")
            false
        }
    }

    // ============================================================
    // 5. 主校验流程
    // ============================================================

    /**
     * 执行完整校验流程
     * @param context 上下文（用于网络检测和设备指纹）
     * @param maxRetry 重试次数，默认 1 次
     * @return [Result]
     */
    suspend fun verify(context: Context, maxRetry: Int = 1): Result = withContext(Dispatchers.IO) {
        // 1. 网络连通性检测
        val online = checkNetwork(context)
        if (!online) {
            Log.w(TAG, "verify: offline")
            return@withContext Result.Offline
        }
        // 2. 解密 4 个校验 URL
        val urls = getVerifyUrls()
        // 3. 并发请求 + 4. 失败重试 1 次
        for (attempt in 0..maxRetry) {
            val pass = fetchVerifyUrls(context, urls)
            if (pass) {
                Log.d(TAG, "verify: pass")
                return@withContext Result.Pass
            }
            Log.w(TAG, "verify: attempt ${attempt + 1} failed, retrying...")
            if (attempt < maxRetry) {
                kotlinx.coroutines.delay(500L)
            }
        }
        // 5. 重试仍失败 → 服务器校验异常
        Log.w(TAG, "verify: server error (all attempts failed)")
        Result.ServerError
    }
}
