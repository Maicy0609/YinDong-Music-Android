package com.yindong.music.data.lx

import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class QuickJSWrapper : AutoCloseable {
    companion object {
        private const val TAG = "QuickJSWrapper"
        private var nativeLoaded = false

        @Synchronized
        fun ensureNativeLoaded() {
            if (!nativeLoaded) {
                try {
                    Log.d(TAG, "Loading QuickJS native library...")
                    QuickJSLoader.init()
                    nativeLoaded = true
                    Log.d(TAG, "QuickJSLoader.init() OK")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "QuickJS native library load FAILED: ${e.message}", e)
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "QuickJSLoader.init() exception: ${e.message}", e)
                    throw e
                }
            }
        }
    }

    private val jsThread: Thread
    private val jsExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "QJS-Engine").apply { isDaemon = true }
    }

    @Volatile
    private var context: QuickJSContext? = null
    @Volatile
    var initError: String? = null
        private set
    @Volatile
    private var lxHandlerRegistered = false
    @Volatile
    private var initedPayload: String? = null

    init {
        val callable = Callable {
            try {
                ensureNativeLoaded()
                Log.d(TAG, "Creating QuickJSContext on thread ${Thread.currentThread().name}...")
                val ctx = QuickJSContext.create()
                Log.d(TAG, "QuickJSContext created OK on thread ${ctx.currentThreadId}")
                ctx
            } catch (e: Throwable) {
                Log.e(TAG, "QuickJSContext create FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                null
            }
        }
        val task = FutureTask(callable)
        jsExecutor.submit(task)
        context = task.get(30, TimeUnit.SECONDS)
        if (context == null) {
            initError = "QuickJS context creation returned null"
            Log.e(TAG, initError!!)
        } else {
            Log.d(TAG, "QuickJS engine initialized successfully")
        }
        jsThread = Thread.currentThread()
    }

    fun setLxHandlerRegistered(value: Boolean) { lxHandlerRegistered = value }
    fun isLxHandlerRegistered(): Boolean = lxHandlerRegistered
    fun setInitedPayload(payload: String) { initedPayload = payload }
    fun getInitedPayload(): String? = initedPayload

    private fun <T> runOnJsThread(block: () -> T): T {
        if (context == null) throw IllegalStateException("QuickJS context is null or destroyed")
        val future = jsExecutor.submit(Callable { block() })
        return try {
            future.get(30, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw RuntimeException("QuickJS operation timeout after 30s", e)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    fun evaluate(script: String, fileName: String = "<eval>") {
        val ctx = context ?: throw IllegalStateException("QuickJS context is null")
        if (script.isBlank()) return
        try {
            runOnJsThread {
                ctx.evaluate(script, fileName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "evaluate failed: $fileName (${script.length} chars)", e)
            throw e
        }
    }

    fun evaluateForResult(script: String, fileName: String = "<eval>"): String? {
        val ctx = context ?: return null
        return try {
            val result = runOnJsThread { ctx.evaluate(script, fileName) }
            result?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "evaluateForResult failed: $fileName", e)
            null
        }
    }

    fun registerGlobalFunction(name: String, fn: (Array<Any?>) -> Any?) {
        val ctx = context ?: run {
            Log.e(TAG, "registerGlobalFunction[$name]: context is null")
            return
        }
        try {
            runOnJsThread {
                val globalObj = ctx.getGlobalObject()
                if (globalObj == null) {
                    Log.e(TAG, "registerGlobalFunction[$name]: globalObject is null")
                    return@runOnJsThread
                }
                globalObj.setProperty(name, JSCallFunction { args ->
                    try {
                        val result = fn(args ?: emptyArray())
                        result ?: ""
                    } catch (e: Exception) {
                        Log.e(TAG, "Global function '$name' error: ${e.message}", e)
                        "ERROR: ${e.message}"
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerGlobalFunction failed: $name", e)
        }
    }

    fun executePendingJobs() {
        try {
            runOnJsThread { context?.evaluate("void 0;", "flush_jobs.js") }
        } catch (_: Exception) {}
    }

    override fun close() {
        try {
            runOnJsThread {
                try {
                    context?.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "destroy error: ${e.message}")
                }
                context = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "close failed", e)
        }
        try {
            jsExecutor.shutdown()
            if (!jsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                jsExecutor.shutdownNow()
            }
        } catch (_: Exception) {}
    }
}
