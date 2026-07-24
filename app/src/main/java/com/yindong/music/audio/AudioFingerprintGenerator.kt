package com.yindong.music.audio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AudioFingerprintGenerator {

    private const val TAG = "AFPGenerator"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var isReady = false
    private var pendingCallback: ((String) -> Unit)? = null
    private var pendingError: ((String) -> Unit)? = null
    private var pendingSamples: String? = null
    private var appContext: Context? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun init(context: Context) {
        appContext = context.applicationContext
        if (webView != null) return
        createWebView()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView() {
        val ctx = appContext ?: return

        mainHandler.post {
            if (webView != null) return@post

            isReady = false
            pendingCallback = null
            pendingError = null
            pendingSamples = null

            webView = WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.domStorageEnabled = true
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                clearCache(true)
                clearHistory()

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "WebView 页面加载完成: $url")
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.e(TAG, "WebView 加载错误: ${error?.description}")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(TAG, "JS[${consoleMessage.messageLevel()}]: ${consoleMessage.message()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}")
                        return true
                    }
                }
                addJavascriptInterface(FPInterface(), "Android")
            }

            webView?.loadUrl("file:///android_asset/afp/index.html")
            Log.d(TAG, "AFP WebView 加载中...")
        }
    }

    fun isReady(): Boolean = isReady

    private class FPInterface {
        @JavascriptInterface
        fun onFPReady() {
            isReady = true
            Log.d(TAG, "AFP WASM 模块就绪")
        }

        @JavascriptInterface
        fun onFPError(error: String) {
            Log.e(TAG, "AFP JS错误: $error")
            pendingError?.invoke(error)
            pendingError = null
        }

        @JavascriptInterface
        fun onFPGGenerated(fingerprint: String) {
            Log.d(TAG, "指纹生成成功, 长度=${fingerprint.length}")
            pendingCallback?.invoke(fingerprint)
            pendingCallback = null
        }

        @JavascriptInterface
        fun getSamples(): String {
            val s = pendingSamples ?: "[]"
            Log.d(TAG, "getSamples 被调用, 数据长度=${s.length}")
            return s
        }

        @JavascriptInterface
        fun onLog(msg: String) {
            Log.d(TAG, "JS日志: $msg")
        }
    }

    suspend fun generateFingerprint(samples: FloatArray): String {
        return suspendCancellableCoroutine { continuation ->
            if (webView == null && appContext != null) {
                createWebView()
            }

            if (webView == null) {
                continuation.resumeWithException(IllegalStateException("AFP 未初始化"))
                return@suspendCancellableCoroutine
            }

            if (!isReady) {
                val maxWait = 15000L
                val latch = CountDownLatch(1)
                var timedOut = false

                val checkThread = Thread {
                    var waited = 0L
                    while (!isReady && waited < maxWait) {
                        Thread.sleep(100)
                        waited += 100
                    }
                    if (!isReady) {
                        timedOut = true
                    }
                    latch.countDown()
                }
                checkThread.start()

                Thread {
                    latch.await(maxWait + 1000, TimeUnit.MILLISECONDS)
                    if (timedOut) {
                        continuation.resumeWithException(IllegalStateException("AFP 初始化超时"))
                    } else {
                        doGenerate(samples, continuation)
                    }
                }.start()
            } else {
                doGenerate(samples, continuation)
            }

            continuation.invokeOnCancellation {
                pendingCallback = null
                pendingError = null
                pendingSamples = null
            }
        }
    }

    private fun doGenerate(
        samples: FloatArray,
        continuation: CancellableContinuation<String>
    ) {
        pendingCallback = { fp -> continuation.resume(fp) }
        pendingError = { err -> continuation.resumeWithException(IllegalStateException(err)) }

        val sb = StringBuilder(samples.size * 8)
        sb.append('[')
        for (i in samples.indices) {
            if (i > 0) sb.append(',')
            sb.append(samples[i].toString())
        }
        sb.append(']')
        pendingSamples = sb.toString()

        Log.d(TAG, "准备调用JS生成指纹, 样本数=${samples.size}, JSON长度=${pendingSamples!!.length}")

        mainHandler.post {
            Log.d(TAG, "执行 evaluateJavascript")
            try {
                webView?.clearCache(true)
                webView?.evaluateJavascript("generateFingerprint(Android.getSamples())", null)
            } catch (e: Exception) {
                Log.e(TAG, "evaluateJavascript 异常", e)
                pendingError?.invoke("evaluateJavascript异常: ${e.message}")
                pendingError = null
            }
        }
    }

    fun reset() {
        Log.d(TAG, "重置 AFP Generator")
        pendingCallback = null
        pendingError = null
        pendingSamples = null

        mainHandler.post {
            try {
                webView?.apply {
                    clearCache(true)
                    clearHistory()
                    loadUrl("about:blank")
                }
                webView?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "销毁 WebView 异常", e)
            }
            webView = null
            isReady = false

            if (appContext != null) {
                createWebView()
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                webView?.apply {
                    clearCache(true)
                    clearHistory()
                    loadUrl("about:blank")
                }
                webView?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "销毁 WebView 异常", e)
            }
            webView = null
        }
        isReady = false
        pendingCallback = null
        pendingError = null
        pendingSamples = null
        appContext = null
    }
}
