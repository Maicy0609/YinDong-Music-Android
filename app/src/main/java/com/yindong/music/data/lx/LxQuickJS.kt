package com.yindong.music.data.lx

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Base64
import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.CompletableDeferred
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * QuickJS 引擎 - 完全移植自 lx-music-mobile-master 的 QuickJS.java + JavaScriptThread.java + JsHandler.java
 *
 * 核心架构（与 lx-music-mobile 完全一致）：
 * 1. 所有 JS 操作在 "JavaScriptThread" (HandlerThread) 上执行
 * 2. HTTP 请求异步执行（不阻塞 JS 线程），响应通过 Handler 消息回到 JS 线程
 * 3. 插件通过 __lx_native_call__ 与 Java 通信（非阻塞）
 * 4. Java 通过 __lx_native__ 调用 JS（通过 Handler post 到 JS 线程）
 * 5. 使用 user-api-preload.js（lx-music-mobile 原版）构造 lx 全局对象
 *
 * 通信流程（与 lx-music-mobile index.ts 一致）：
 *   App → callJS("request", {requestKey, data}) → JS handleRequest → 插件 handler
 *   插件 → lx.request → nativeCall("request", {requestKey, url, options}) → Java HTTP
 *   Java HTTP 完成 → callJS("response", {requestKey, error, response}) → JS handleNativeResponse → callback
 *   插件 handler 完成 → nativeCall("response", {requestKey, status, result}) → Java resolve deferred
 */
class LxQuickJS(
    private val context: Context,
    private val logCallback: ((String) -> Unit)? = null,
) : AutoCloseable {

    companion object {
        private const val TAG = "LxQuickJS"
        private var nativeLoaded = false

        @Synchronized
        private fun ensureNativeLoaded() {
            if (!nativeLoaded) {
                QuickJSLoader.init()
                nativeLoaded = true
                Log.d(TAG, "QuickJSLoader.init() OK")
            }
        }

        // Handler message types (与 lx-music-mobile HandlerWhat.java 一致)
        private const val MSG_ACTION = 1000
        private const val MSG_INIT = 99
        private const val MSG_INIT_FAILED = 500
        private const val MSG_INIT_SUCCESS = 200
        private const val MSG_DESTROY = 98
        private const val MSG_LOG = 1001
    }

    // ── JS 线程（HandlerThread，与 lx-music-mobile JavaScriptThread.java 一致）──
    private val jsThread = HandlerThread("JavaScriptThread").apply { start() }
    private val jsHandler = Handler(jsThread.looper) { msg ->
        when (msg.what) {
            MSG_ACTION -> {
                @Suppress("UNCHECKED_CAST")
                val data = msg.obj as Array<Any?>
                callJSInternal(data[0] as String, data[1] as? String)
                true
            }
            MSG_DESTROY -> {
                destroyInternal()
                true
            }
            else -> false
        }
    }

    // ── 超时 Handler（与 lx-music-mobile QuickJS.java timeoutHandler 一致）──
    private val timeoutHandler = Handler(Looper.getMainLooper())

    // ── HTTP 线程池（异步 HTTP 请求，不阻塞 JS 线程）──
    private val httpExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "LxQuickJS-HTTP").apply { isDaemon = true }
    }

    // ── OkHttp 客户端 ──
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // ── JS 上下文（仅在 JS 线程访问）──
    private var jsContext: QuickJSContext? = null
    private val key = UUID.randomUUID().toString()
    private var inited = false

    // ── 请求映射（与 lx-music-mobile index.ts userApiRequestMap 一致）──
    // requestKey → CompletableDeferred（App 等待插件返回结果）
    private val userApiRequestMap = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    // requestKey → Call（插件 HTTP 请求，支持取消）
    private val scriptRequestMap = ConcurrentHashMap<String, Call>()
    // setTimeout 回调映射
    private val timeoutCallbacks = ConcurrentHashMap<Int, Runnable>()

    // ── 初始化状态 ──
    private val initLatch = CountDownLatch(1)
    @Volatile private var initError: String? = null
    @Volatile private var pluginLoaded = false
    @Volatile private var pluginInited = false
    @Volatile private var initedSources: JSONObject? = null

    init {
        // 在 JS 线程上创建上下文
        jsHandler.post {
            try {
                ensureNativeLoaded()
                val ctx = QuickJSContext.create()
                ctx.setConsole(object : QuickJSContext.Console {
                    override fun log(info: String) { sendLog("log", info) }
                    override fun info(info: String) { sendLog("info", info) }
                    override fun warn(info: String) { sendLog("warn", info) }
                    override fun error(info: String) { sendLog("error", info) }
                })
                jsContext = ctx
                createEnvObj(ctx)
                // 加载 user-api-preload.js（lx-music-mobile 原版）
                val preload = context.assets.open("user-api-preload.js").bufferedReader().use { it.readText() }
                ctx.evaluate(preload, "user-api-preload.js")
                // 调用 lx_setup（与 lx-music-mobile QuickJS.java createJSEnv 一致）
                ctx.getGlobalObject().getJSFunction("lx_setup").call(key, "", "", "", "", "", "", "")
                Log.d(TAG, "QuickJS engine initialized on thread ${Thread.currentThread().name}")
            } catch (e: Throwable) {
                Log.e(TAG, "QuickJS init failed: ${e.message}", e)
                initError = e.message ?: "unknown error"
            } finally {
                initLatch.countDown()
            }
        }
        // 等待初始化完成
        initLatch.await(30, TimeUnit.SECONDS)
    }

    /**
     * 注册原生函数（与 lx-music-mobile QuickJS.java createEnvObj 完全一致）
     */
    private fun createEnvObj(ctx: QuickJSContext) {
        val globalObj = ctx.getGlobalObject()

        // ── 主通信桥 __lx_native_call__（与 lx-music-mobile 完全一致）──
        globalObj.setProperty("__lx_native_call__") { args ->
            try {
                val argKey = args?.get(0)?.toString() ?: ""
                if (key == argKey) {
                    val action = args?.get(1)?.toString() ?: ""
                    val data = args?.get(2)?.toString() ?: ""
                    callNative(action, data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "__lx_native_call__ error: ${e.message}", e)
            }
            null
        }

        // ── setTimeout（与 lx-music-mobile QuickJS.java __lx_native_call__set_timeout 一致）──
        globalObj.setProperty("__lx_native_call__set_timeout") { args ->
            try {
                val id = (args?.get(0) as? Number)?.toInt() ?: 0
                val timeout = (args?.get(1) as? Number)?.toLong() ?: 0L
                val runnable = Runnable {
                    jsHandler.obtainMessage(MSG_ACTION, arrayOf<Any?>("__set_timeout__", id.toString())).sendToTarget()
                }
                timeoutCallbacks[id] = runnable
                timeoutHandler.postDelayed(runnable, timeout)
            } catch (e: Exception) {
                Log.e(TAG, "set_timeout error: ${e.message}", e)
            }
            null
        }

        // ── Base64 编码（与 lx-music-mobile QuickJS.java utils_str2b64 一致）──
        globalObj.setProperty("__lx_native_call__utils_str2b64") { args ->
            try {
                val str = args?.get(0)?.toString() ?: ""
                Base64.encodeToString(str.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "str2b64 error: ${e.message}", e)
                ""
            }
        }

        // ── Base64 解码为字节数组 JSON（与 lx-music-mobile QuickJS.java utils_b642buf 一致）──
        globalObj.setProperty("__lx_native_call__utils_b642buf") { args ->
            try {
                val str = args?.get(0)?.toString() ?: ""
                val bytes = Base64.decode(str.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                val sb = StringBuilder("[")
                for (i in bytes.indices) {
                    sb.append(bytes[i].toInt())
                    if (i < bytes.size - 1) sb.append(",")
                }
                sb.append("]")
                sb.toString()
            } catch (e: Exception) {
                Log.e(TAG, "b642buf error: ${e.message}", e)
                ""
            }
        }

        // ── MD5（与 lx-music-mobile QuickJS.java utils_str2md5 一致）──
        globalObj.setProperty("__lx_native_call__utils_str2md5") { args ->
            try {
                val str = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    URLDecoder.decode(args?.get(0)?.toString() ?: "", StandardCharsets.UTF_8)
                } else {
                    URLDecoder.decode(args?.get(0)?.toString() ?: "", "UTF-8")
                }
                val md = MessageDigest.getInstance("MD5")
                val md5Bytes = md.digest(str.toByteArray(StandardCharsets.UTF_8))
                val sb = StringBuilder()
                for (b in md5Bytes) sb.append(String.format("%02x", b))
                sb.toString()
            } catch (e: Exception) {
                Log.e(TAG, "str2md5 error: ${e.message}", e)
                ""
            }
        }

        // ── AES 加密（与 lx-music-mobile AES.java + CryptoModule 一致）──
        globalObj.setProperty("__lx_native_call__utils_aes_encrypt") { args ->
            try {
                val data = args?.get(0)?.toString() ?: ""
                val keyStr = args?.get(1)?.toString() ?: ""
                val iv = args?.get(2)?.toString() ?: ""
                val mode = args?.get(3)?.toString() ?: ""
                aesEncrypt(data, keyStr, iv, mode)
            } catch (e: Exception) {
                Log.e(TAG, "aes_encrypt error: ${e.message}", e)
                ""
            }
        }

        // ── RSA 加密（与 lx-music-mobile RSA.java + CryptoModule 一致）──
        globalObj.setProperty("__lx_native_call__utils_rsa_encrypt") { args ->
            try {
                val data = args?.get(0)?.toString() ?: ""
                val publicKey = args?.get(1)?.toString() ?: ""
                val padding = args?.get(2)?.toString() ?: ""
                rsaEncrypt(data, publicKey, padding)
            } catch (e: Exception) {
                Log.e(TAG, "rsa_encrypt error: ${e.message}", e)
                ""
            }
        }

        // 不再需要持有 globalObj 引用（QuickJSContext 会管理）
    }

    /**
     * Java → JS 调用（与 lx-music-mobile QuickJS.java callJS 一致）
     * 通过 Handler post 到 JS 线程执行
     */
    fun callJS(action: String, data: Any? = null) {
        val dataStr = when (data) {
            null -> null
            is String -> data
            else -> data.toString()
        }
        jsHandler.obtainMessage(MSG_ACTION, arrayOf<Any?>(action, dataStr)).sendToTarget()
    }

    /**
     * 实际在 JS 线程上调用 __lx_native__（与 lx-music-mobile QuickJS.java callJS 一致）
     */
    private fun callJSInternal(action: String, dataStr: String?) {
        val ctx = jsContext ?: run {
            Log.w(TAG, "callJS: jsContext is null, action=$action")
            return
        }
        try {
            val params: Array<Any?> = if (dataStr != null) {
                arrayOf(key, action, dataStr)
            } else {
                arrayOf(key, action)
            }
            ctx.getGlobalObject().getJSFunction("__lx_native__").call(*params)
        } catch (e: Exception) {
            Log.e(TAG, "callJS error: action=$action, ${e.message}", e)
        }
    }

    /**
     * JS → Java 调用（与 lx-music-mobile QuickJS.java callNative 一致）
     * 在 JS 线程上被调用，必须立即返回，不阻塞
     */
    private fun callNative(action: String, dataStr: String) {
        Log.d(TAG, "callNative: action=$action, data=${dataStr.take(200)}")
        try {
            val data = JSONObject(dataStr)
            when (action) {
                "init" -> handleInitFromPlugin(data)
                "request" -> handleScriptRequest(data)
                "cancelRequest" -> handleCancelRequest(data)
                "response" -> handlePluginResponse(data)
                "showUpdateAlert" -> {
                    // 忽略更新提示（与 lx-music-mobile 行为一致，仅记录日志）
                    Log.d(TAG, "showUpdateAlert: ${data.optString("log")}")
                }
                else -> Log.w(TAG, "callNative: unknown action=$action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "callNative parse error: action=$action, ${e.message}", e)
        }
    }

    /**
     * 处理插件 init 事件（与 lx-music-mobile index.ts handleStateChange 一致）
     */
    private fun handleInitFromPlugin(data: JSONObject) {
        if (inited) return
        inited = true
        val status = data.optBoolean("status", false)
        val errorMessage = data.optString("errorMessage", "")
        val info = data.optJSONObject("info")
        if (status && info != null) {
            initedSources = info.optJSONObject("sources")
            pluginInited = true
            Log.d(TAG, "Plugin inited: sources=${initedSources?.keys()?.asSequence()?.toList()}")
            logCallback?.invoke("插件初始化成功: sources=${initedSources?.keys()?.asSequence()?.toList()}")
        } else {
            Log.w(TAG, "Plugin init failed: $errorMessage")
            logCallback?.invoke("插件初始化失败: $errorMessage")
        }
    }

    /**
     * 处理插件 HTTP 请求（与 lx-music-mobile index.ts sendScriptRequest 一致）
     * 在 JS 线程被调用，dispatch 到 HTTP 线程异步执行
     */
    private fun handleScriptRequest(data: JSONObject) {
        val requestKey = data.optString("requestKey")
        val url = data.optString("url")
        val options = data.optJSONObject("options") ?: JSONObject()
        Log.d(TAG, "handleScriptRequest: requestKey=$requestKey, url=$url, options=${options.toString().take(200)}")

        httpExecutor.execute {
            try {
                val response = executeHttpRequest(url, options)
                // HTTP 完成，通过 Handler 回到 JS 线程处理响应
                val responseData = JSONObject()
                    .put("requestKey", requestKey)
                    .put("error", JSONObject.NULL)
                    .put("response", response)
                jsHandler.obtainMessage(MSG_ACTION, arrayOf<Any?>("response", responseData.toString())).sendToTarget()
            } catch (e: Exception) {
                Log.e(TAG, "handleScriptRequest failed: url=$url, ${e.message}", e)
                val responseData = JSONObject()
                    .put("requestKey", requestKey)
                    .put("error", e.message ?: "request failed")
                    .put("response", JSONObject.NULL)
                jsHandler.obtainMessage(MSG_ACTION, arrayOf<Any?>("response", responseData.toString())).sendToTarget()
            }
        }
    }

    /**
     * 取消 HTTP 请求（与 lx-music-mobile index.ts cancelRequest 一致）
     */
    private fun handleCancelRequest(data: JSONObject) {
        val requestKey = if (data.length() == 1 && !data.has("requestKey")) {
            data.optString("data")
        } else {
            data.optString("requestKey")
        }
        scriptRequestMap.remove(requestKey)?.cancel()
    }

    /**
     * 处理插件返回结果（与 lx-music-mobile index.ts handleUserApiResponse 一致）
     * 插件 handler 完成 → nativeCall("response", {requestKey, status, result/errorMessage})
     */
    private fun handlePluginResponse(data: JSONObject) {
        val requestKey = data.optString("requestKey")
        val status = data.optBoolean("status", false)
        val result = data.optJSONObject("result")
        val errorMessage = data.optString("errorMessage", "")

        val deferred = userApiRequestMap.remove(requestKey) ?: run {
            Log.w(TAG, "handlePluginResponse: no deferred for requestKey=$requestKey")
            return
        }

        if (status && result != null) {
            deferred.complete(result)
        } else {
            deferred.completeExceptionally(Exception(errorMessage.ifBlank { "plugin request failed" }))
        }
    }

    /**
     * 执行 HTTP 请求（与 lx-music-mobile request.js fetchData + handleRequestData 一致）
     */
    private fun executeHttpRequest(url: String, options: JSONObject): JSONObject {
        val method = options.optString("method", "get").lowercase()
        val headers = options.optJSONObject("headers") ?: JSONObject()
        val body = options.opt("body")
        val form = options.optJSONObject("form")
        val timeout = options.optLong("timeout", 13000L)

        // 合并默认 headers（与 request.js defaultHeaders 一致）
        val mergedHeaders = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36",
            "Accept" to "application/json",
        )
        headers.keys().forEach { k -> mergedHeaders[k] = headers.optString(k) }

        val requestBuilder = Request.Builder().url(url)
        var finalBody: RequestBody? = null

        if (method == "post" || method == "put") {
            when {
                form != null -> {
                    if (!mergedHeaders.containsKey("Content-Type")) {
                        mergedHeaders["Content-Type"] = "application/x-www-form-urlencoded"
                    }
                    val sb = StringBuilder()
                    val formKeys = form.keys().asSequence().toList()
                    formKeys.forEachIndexed { i, k ->
                        if (i > 0) sb.append("&")
                        sb.append(URLEncoder.encode(k, "UTF-8"))
                        sb.append("=")
                        sb.append(URLEncoder.encode(form.optString(k), "UTF-8"))
                    }
                    finalBody = sb.toString().toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                }
                body != null && body != JSONObject.NULL -> {
                    if (!mergedHeaders.containsKey("Content-Type")) {
                        mergedHeaders["Content-Type"] = "application/json"
                    }
                    val bodyStr = when (body) {
                        is String -> body
                        is JSONObject -> body.toString()
                        is JSONArray -> body.toString()
                        else -> body.toString()
                    }
                    val ct = mergedHeaders["Content-Type"] ?: "application/json"
                    finalBody = bodyStr.toRequestBody(ct.toMediaTypeOrNull())
                }
                else -> {
                    finalBody = ByteArray(0).toRequestBody(null)
                }
            }
        }

        mergedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        when (method) {
            "get" -> requestBuilder.get()
            "post" -> requestBuilder.post(finalBody!!)
            "put" -> requestBuilder.put(finalBody!!)
            "delete" -> {
                if (finalBody != null) requestBuilder.delete(finalBody) else requestBuilder.delete()
            }
            "head" -> requestBuilder.head()
            else -> requestBuilder.method(method.uppercase(), finalBody)
        }

        val client = okHttpClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .build()

        val call = client.newCall(requestBuilder.build())
        val requestKey = url.hashCode().toString() + System.nanoTime()
        scriptRequestMap[requestKey] = call

        try {
            call.execute().use { resp ->
                scriptRequestMap.remove(requestKey)
                val rawBody = resp.body?.string() ?: ""
                val respHeaders = JSONObject()
                for (i in 0 until resp.headers.size) {
                    respHeaders.put(resp.headers.name(i), resp.headers.value(i))
                }
                // body 自动 JSON 解析（与 request.js 一致）
                val parsedBody: Any = try {
                    JSONObject(rawBody)
                } catch (_: Exception) {
                    try {
                        JSONArray(rawBody)
                    } catch (_: Exception) {
                        rawBody
                    }
                }
                return JSONObject()
                    .put("statusCode", resp.code)
                    .put("statusMessage", resp.message)
                    .put("headers", respHeaders)
                    .put("body", parsedBody)
                    .put("url", resp.request.url.toString())
                    .put("ok", resp.isSuccessful)
            }
        } catch (e: Exception) {
            scriptRequestMap.remove(requestKey)
            throw e
        }
    }

    /**
     * AES 加密（与 lx-music-mobile AES.java 完全一致）
     */
    private fun aesEncrypt(data: String, key: String, iv: String, mode: String): String {
        val dataBytes = Base64.decode(data, Base64.DEFAULT)
        val keyBytes = Base64.decode(key, Base64.DEFAULT)
        val cipherMode = when (mode) {
            "AES/CBC/PKCS7Padding" -> "AES/CBC/PKCS7Padding"
            "AES" -> "AES"  // Java 默认 AES/ECB/PKCS5Padding（与 lx-music-mobile 一致）
            else -> mode
        }
        val cipher = Cipher.getInstance(cipherMode)
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")
        if (iv.isEmpty()) {
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        } else {
            val ivBytes = Base64.decode(iv, Base64.DEFAULT)
            val finalIv = ByteArray(16)
            System.arraycopy(ivBytes, 0, finalIv, 0, minOf(ivBytes.size, 16))
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(finalIv))
        }
        val encrypted = cipher.doFinal(dataBytes)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * RSA 加密（与 lx-music-mobile RSA.java encryptRSAToString 完全一致）
     */
    private fun rsaEncrypt(data: String, publicKey: String, padding: String): String {
        val keyBytes = Base64.decode(publicKey.trim().toByteArray(), Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val publicKeyObj = KeyFactory.getInstance("RSA").generatePublic(keySpec)
        val cipherMode = when (padding) {
            "RSA/ECB/OAEPWithSHA1AndMGF1Padding" -> "RSA/ECB/OAEPWithSHA1AndMGF1Padding"
            "RSA/ECB/NoPadding" -> "RSA/ECB/NoPadding"
            else -> padding
        }
        val cipher = Cipher.getInstance(cipherMode)
        cipher.init(Cipher.ENCRYPT_MODE, publicKeyObj)
        val dataBytes = Base64.decode(data, Base64.DEFAULT)
        val encrypted = cipher.doFinal(dataBytes)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * 发送日志（与 lx-music-mobile JsHandler sendLogEvent 一致）
     */
    private fun sendLog(type: String, msg: String) {
        val log = "[$type] $msg"
        Log.d(TAG, "JS $log")
        logCallback?.invoke(log)
    }

    // ════════════════════════════════════════════════════════════════════
    //  公共 API（高层接口，与 lx-music-mobile index.ts sendUserApiRequest 一致）
    // ════════════════════════════════════════════════════════════════════

    /**
     * 加载插件脚本（与 lx-music-mobile QuickJS.java loadScript 一致）
     * 必须在 JS 线程上调用（内部自动 post）
     */
    fun loadScript(script: String, id: String = "", name: String = "", description: String = "",
                   version: String = "", author: String = "", homepage: String = ""): String {
        if (initError != null) return "QuickJS init failed: $initError"

        val latch = CountDownLatch(1)
        var result = ""
        jsHandler.post {
            try {
                val ctx = jsContext ?: throw IllegalStateException("jsContext is null")
                // 直接执行插件脚本（lx_setup 已在 init 块中调用，环境已就绪）
                ctx.evaluate(script, "plugin.js")
                pluginLoaded = true
                Log.d(TAG, "Plugin script loaded (${script.length} chars), name=$name, version=$version")
                logCallback?.invoke("插件脚本加载成功 (${script.length} chars)")
            } catch (e: Exception) {
                result = e.message ?: "load script error"
                Log.e(TAG, "loadScript error: ${e.message}", e)
                logCallback?.invoke("插件脚本加载失败: ${e.message}")
                // 通知 JS 运行错误
                try {
                    callJSInternal("__run_error__", null)
                } catch (_: Exception) {}
            } finally {
                latch.countDown()
            }
        }
        latch.await(30, TimeUnit.SECONDS)
        return result
    }

    /**
     * 是否已初始化（插件已发送 inited 事件）
     */
    fun isPluginInited(): Boolean = pluginInited

    /**
     * 获取已初始化的音源列表
     */
    fun getInitedSources(): List<String> {
        val sources = initedSources ?: return emptyList()
        val list = mutableListOf<String>()
        sources.keys().forEach { list.add(it) }
        return list
    }

    /**
     * 检查音源是否支持指定操作
     */
    fun isSourceSupported(source: String, action: String = "musicUrl"): Boolean {
        val sources = initedSources ?: return false
        val srcInfo = sources.optJSONObject(source) ?: return false
        val actions = srcInfo.optJSONArray("actions") ?: return false
        (0 until actions.length()).forEach { i ->
            if (actions.optString(i) == action) return true
        }
        return false
    }

    /**
     * 发送 API 请求（与 lx-music-mobile index.ts sendUserApiRequest 一致）
     *
     * @param source 音源（wy/tx/kw/kg/mg）
     * @param action 操作（musicUrl/lyric/pic）
     * @param info 请求信息（{type, musicInfo}）
     * @param timeoutMs 超时
     * @return 插件返回的结果 JSON
     */
    suspend fun sendApiRequest(source: String, action: String, info: JSONObject, timeoutMs: Long = 20000L): JSONObject {
        if (!pluginInited) throw IllegalStateException("插件未初始化")

        val requestKey = "request__${Math.random().toString().substring(2)}"
        val deferred = CompletableDeferred<JSONObject>()
        userApiRequestMap[requestKey] = deferred

        // 构造请求数据（与 lx-music-mobile index.ts sendUserApiRequest 一致）
        val requestData = JSONObject()
            .put("requestKey", requestKey)
            .put("data", JSONObject()
                .put("source", source)
                .put("action", action)
                .put("info", info)
            )

        // 通过 Handler 发送到 JS 线程
        callJS("request", requestData.toString())

        try {
            return kotlinx.coroutines.withTimeout(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            userApiRequestMap.remove(requestKey)
            throw e
        }
    }

    /**
     * 获取音乐播放 URL（高层封装）
     *
     * @param source 音源（wy/tx/kw/kg）
     * @param musicInfo 音乐信息 JSON（与 lx-music-desktop MusicInfo 一致）
     * @param quality 音质（128k/320k/flac/flac24bit/hires/...）
     * @param timeoutMs 超时
     * @return URL 字符串
     */
    suspend fun getMusicUrl(source: String, musicInfo: JSONObject, quality: String, timeoutMs: Long = 20000L): String {
        val info = JSONObject()
            .put("type", quality)
            .put("musicInfo", musicInfo)
        val result = sendApiRequest(source, "musicUrl", info, timeoutMs)
        // result 结构：{source, action, data: {type, url}}
        val data = result.optJSONObject("data") ?: throw Exception("invalid response: no data")
        val url = data.optString("url")
        if (url.isBlank()) throw Exception("plugin returned empty url")
        return url
    }

    /**
     * 获取歌词（高层封装）
     */
    suspend fun getLyric(source: String, musicInfo: JSONObject, timeoutMs: Long = 20000L): JSONObject {
        val info = JSONObject()
            .put("type", "lrc")
            .put("musicInfo", musicInfo)
        val result = sendApiRequest(source, "lyric", info, timeoutMs)
        return result.optJSONObject("data") ?: JSONObject()
    }

    /**
     * 获取封面图（高层封装）
     */
    suspend fun getPicUrl(source: String, musicInfo: JSONObject, timeoutMs: Long = 20000L): String {
        val info = JSONObject()
            .put("type", "pic")
            .put("musicInfo", musicInfo)
        val result = sendApiRequest(source, "pic", info, timeoutMs)
        val data = result.optJSONObject("data")
        return data?.optString("url") ?: result.optString("data")
    }

    private fun destroyInternal() {
        try {
            jsContext?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroy error: ${e.message}")
        }
        jsContext = null
    }

    override fun close() {
        jsHandler.sendEmptyMessage(MSG_DESTROY)
        try {
            jsThread.quitSafely()
            jsThread.join(3000)
        } catch (_: Exception) {}
        try {
            httpExecutor.shutdownNow()
        } catch (_: Exception) {}
        timeoutHandler.looper.let { /* main looper, don't quit */ }
    }
}
