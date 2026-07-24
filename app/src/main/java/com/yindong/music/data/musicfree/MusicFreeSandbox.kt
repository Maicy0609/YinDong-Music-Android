package com.yindong.music.data.musicfree

import android.content.Context
import android.util.Log
import com.yindong.music.data.lx.QuickJSWrapper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * MusicFree 插件沙箱
 *
 * 参考 MusicFree-master/src/core/pluginManager/plugin.ts 的 mountPlugin 方法：
 *  - 使用 Function() 构造沙箱（在 Kotlin 端通过 QuickJS 的 evaluate 实现）
 *  - 注入 require/module/exports/console/env/URL/process 全局对象
 *  - 插件代码执行后通过 module.exports 获取插件实例
 *
 * HTTP 桥接：MusicFree 插件大量使用 axios 发起 HTTP 请求，本沙箱通过原生
 * __mf_http_request 函数将请求转发到 Kotlin 实现，避免在 JS 中实现完整的
 * axios/HTTP 栈。
 *
 * 注意：每个 MusicFreeSandbox 实例拥有独立的 QuickJSWrapper，插件间互不影响。
 */
class MusicFreeSandbox(
    private val context: Context,
    private val script: String,
    private val pluginId: String,
    private val logCallback: ((String) -> Unit)? = null,
) : AutoCloseable {

    companion object {
        private const val TAG = "MusicFreeSandbox"
    }

    private val quickJs: QuickJSWrapper = QuickJSWrapper()
    private var instanceJson: String = "{}"
    private var mounted = false

    init {
        try {
            setupEnvironment()
            mountPlugin()
            mounted = true
        } catch (e: Exception) {
            Log.e(TAG, "[$pluginId] sandbox init failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * 获取插件元数据
     */
    fun getPluginInfo(): MusicFreePluginInfo {
        ensureMounted()
        return try {
            val json = JSONObject(instanceJson)
            val platform = json.optString("platform", "")
            val version = json.optString("version", "")
            val author = json.optString("author", "")
            val description = json.optString("description", "")
            val searchTypes = json.optJSONArray("supportedSearchType")?.let { arr ->
                (0 until arr.length()).map { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList()
            MusicFreePluginInfo(
                platform = platform,
                version = version,
                author = author,
                description = description,
                supportedSearchType = searchTypes,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[$pluginId] getPluginInfo failed: ${e.message}", e)
            MusicFreePluginInfo()
        }
    }

    /**
     * 调用插件的 search 方法
     */
    fun search(query: String, page: Int, type: String): MusicFreeSearchResult {
        ensureMounted()
        return try {
            val argsJson = JSONObject().apply {
                put("query", query)
                put("page", page)
                put("type", type)
            }.toString()
            val resultJson = callMethod("search", argsJson)
            if (resultJson.isNullOrBlank()) return MusicFreeSearchResult()
            parseSearchResult(resultJson)
        } catch (e: Exception) {
            Log.e(TAG, "[$pluginId] search failed: ${e.message}", e)
            MusicFreeSearchResult()
        }
    }

    /**
     * 调用插件的 getMediaSource 方法
     */
    fun getMediaSource(musicItemJson: String, quality: String): MusicFreeMediaSource {
        ensureMounted()
        return try {
            val argsJson = JSONObject().apply {
                put("musicItem", JSONObject(musicItemJson))
                put("quality", quality)
            }.toString()
            val resultJson = callMethod("getMediaSource", argsJson)
            if (resultJson.isNullOrBlank()) return MusicFreeMediaSource()
            parseMediaSourceResult(resultJson)
        } catch (e: Exception) {
            Log.e(TAG, "[$pluginId] getMediaSource failed: ${e.message}", e)
            MusicFreeMediaSource()
        }
    }

    /**
     * 调用插件的 getLyric 方法
     */
    fun getLyric(musicItemJson: String): String {
        ensureMounted()
        return try {
            val argsJson = JSONObject().apply {
                put("musicItem", JSONObject(musicItemJson))
            }.toString()
            val resultJson = callMethod("getLyric", argsJson)
            if (resultJson.isNullOrBlank()) return ""
            val result = JSONObject(resultJson)
            result.optString("rawLrc", "")
        } catch (e: Exception) {
            Log.e(TAG, "[$pluginId] getLyric failed: ${e.message}", e)
            ""
        }
    }

    override fun close() {
        try {
            quickJs.close()
        } catch (_: Exception) {}
        mounted = false
    }

    // ═════════════════════════════════════════════════════════════════
    //  沙箱初始化
    // ═════════════════════════════════════════════════════════════════

    private fun ensureMounted() {
        if (!mounted) throw IllegalStateException("沙箱未挂载或已关闭")
    }

    /**
     * 设置沙箱环境：注册全局函数、注入 polyfill
     */
    private fun setupEnvironment() {
        // 注册原生 HTTP 桥接函数
        quickJs.registerGlobalFunction("__mf_http_request") { args ->
            try {
                val reqJson = args.firstOrNull()?.toString() ?: "{}"
                val req = JSONObject(reqJson)
                val result = executeHttpRequest(req)
                result
            } catch (e: Exception) {
                Log.e(TAG, "[$pluginId] __mf_http_request error: ${e.message}", e)
                JSONObject().put("error", e.message ?: "http error").toString()
            }
        }

        // 注册 console（日志转发到 logCallback）
        quickJs.registerGlobalFunction("__mf_console_log") { args ->
            val msg = args.joinToString(" ") { it?.toString() ?: "null" }
            logCallback?.invoke("[$pluginId] $msg")
            Log.d(TAG, "[$pluginId] $msg")
            ""
        }

        // 注入 polyfill 脚本
        // 注意：必须调用 getPolyfillScript() 而非直接引用字段，
        // 因为 Kotlin 属性按声明顺序初始化，init 块在字段声明之前执行时字段为 null。
        // 使用函数避免初始化顺序问题。
        quickJs.evaluate(getPolyfillScript(), "musicfree_polyfill.js")
    }

    /**
     * 挂载插件：执行插件代码，提取 module.exports
     *
     * 完全参考 MusicFree plugin.ts 的 mountPlugin：
     *   const _module = { exports: {} };
     *   _instance = Function(`return function(require, module, exports, console, env, URL, process) { ${funcCode} }`)()
     *       (_require, _module, _module.exports, _console, env, URL, _process);
     *   _instance = _module.exports.default || _module.exports;
     */
    private fun mountPlugin() {
        val wrapperScript = """
            (function() {
                var __mf_module = { exports: {} };
                var __mf_exports = __mf_module.exports;
                var __mf_env = {
                    userVariables: {},
                    getUserVariables: function() { return {}; },
                    appVersion: '1.0.0',
                    os: 'android',
                    lang: 'zh-CN'
                };
                var __mf_process = {
                    platform: 'android',
                    version: '1.0.0',
                    env: __mf_env
                };
                var __mf_console = {
                    log: function() { __mf_console_log.apply(null, Array.prototype.slice.call(arguments)); },
                    warn: function() { __mf_console_log.apply(null, Array.prototype.slice.call(arguments)); },
                    info: function() { __mf_console_log.apply(null, Array.prototype.slice.call(arguments)); },
                    error: function() { __mf_console_log.apply(null, Array.prototype.slice.call(arguments)); },
                    debug: function() { __mf_console_log.apply(null, Array.prototype.slice.call(arguments)); }
                };
                try {
                    // 插件代码执行（插件内通过 module.exports 暴露 API）
                    // 完全参照 MusicFree-master plugin.ts line 937:
                    //   function(require, __musicfree_require, module, exports, console, env, URL, process)
                    //   _require 同时作为 require 和 __musicfree_require 注入
                    (function(require, __musicfree_require, module, exports, console, env, URL, process) {
                        $script
                    })(__mf_require, __mf_require, __mf_module, __mf_exports, __mf_console, __mf_env, __mf_URL, __mf_process);
                } catch (e) {
                    __mf_console.error('插件代码执行异常: ' + (e && e.message ? e.message : e));
                }
                // 提取插件实例（兼容 module.exports.default 和 module.exports 两种写法）
                var __mf_instance = __mf_module.exports.default || __mf_module.exports;
                if (!__mf_instance || typeof __mf_instance !== 'object') {
                    throw new Error('插件未通过 module.exports 暴露对象');
                }
                // 序列化插件元数据
                var __mf_meta = {
                    platform: __mf_instance.platform || '',
                    version: __mf_instance.version || '',
                    author: __mf_instance.author || '',
                    description: __mf_instance.description || '',
                    supportedSearchType: __mf_instance.search ? ['music'] : []
                };
                // 缓存插件实例到全局
                globalThis.__mf_plugin_instance = __mf_instance;
                return JSON.stringify(__mf_meta);
            })();
        """.trimIndent()

        instanceJson = quickJs.evaluateForResult(wrapperScript, "musicfree_plugin_${pluginId}.js")
            ?: "{}"
        Log.d(TAG, "[$pluginId] 插件元数据: $instanceJson")
    }

    /**
     * 调用插件实例的方法（异步方法通过 Promise + executePendingJobs 轮询）
     *
     * 由于 QuickJSWrapper 的 evaluate 是同步的，但插件方法是 async 的，
     * 我们采用以下策略：
     * 1. 第一次 evaluate：调用方法，将 Promise 的 then/catch 结果存入 globalThis.__mf_call_result
     * 2. 循环：调用 executePendingJobs() 推进 Promise，然后检查 __mf_call_result 是否已设置
     * 3. 超时（12s）后返回错误
     */
    private fun callMethod(methodName: String, argsJson: String): String? {
        // Step 1: 启动异步调用
        val startScript = """
            (function() {
                var instance = globalThis.__mf_plugin_instance;
                if (!instance || typeof instance.$methodName !== 'function') {
                    __mf_console_log('callMethod $methodName: 方法不存在');
                    globalThis.__mf_call_result = JSON.stringify({ __error: '方法 $methodName 不存在' });
                    return;
                }
                var args = JSON.parse(${jsStringLiteral(argsJson)});
                globalThis.__mf_call_result = null;
                try {
                    var ret;
                    if ('$methodName' === 'search') {
                        __mf_console_log('callMethod search: query=' + (args.query || '') + ' page=' + (args.page || 1) + ' type=' + (args.type || 'music'));
                        ret = instance.$methodName(args.query || '', args.page || 1, args.type || 'music');
                    } else if ('$methodName' === 'getMediaSource') {
                        ret = instance.$methodName(args.musicItem || {}, args.quality || 'standard');
                    } else if ('$methodName' === 'getLyric') {
                        ret = instance.$methodName(args.musicItem || {});
                    } else {
                        ret = instance.$methodName(args);
                    }
                    if (ret && typeof ret.then === 'function') {
                        // Promise: 注册回调，结果存入 __mf_call_result
                        __mf_console_log('callMethod $methodName: 返回 Promise，等待 resolve');
                        ret.then(function(v) {
                            __mf_console_log('callMethod $methodName: Promise resolved, type=' + typeof v + ', keys=' + (v ? Object.keys(v).join(',') : 'null'));
                            globalThis.__mf_call_result = __mf_serialize_result(v);
                        }).catch(function(e) {
                            var errMsg = e && e.message ? e.message : String(e);
                            var errStack = e && e.stack ? e.stack : '';
                            __mf_console_log('callMethod $methodName: Promise rejected: ' + errMsg + (errStack ? ' | stack: ' + errStack : ''));
                            globalThis.__mf_call_result = JSON.stringify({ __error: errMsg, __stack: errStack });
                        });
                    } else {
                        // 同步结果
                        __mf_console_log('callMethod $methodName: 同步返回, type=' + typeof ret);
                        globalThis.__mf_call_result = __mf_serialize_result(ret);
                    }
                } catch (e) {
                    __mf_console_log('callMethod $methodName: 同步异常: ' + (e && e.message ? e.message : String(e)));
                    globalThis.__mf_call_result = JSON.stringify({ __error: (e && e.message) ? e.message : String(e) });
                }
            })();
        """.trimIndent()
        quickJs.evaluate(startScript, "mf_call_start_$methodName.js")

        // Step 2: 轮询等待结果
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline) {
            // 推进 Promise microtasks
            quickJs.executePendingJobs()
            // 检查结果
            val result = quickJs.evaluateForResult("globalThis.__mf_call_result", "mf_check.js")
            if (result != null && result != "null" && result != "undefined" && result.isNotBlank()) {
                // 清理并返回
                quickJs.evaluate("globalThis.__mf_call_result = null;", "mf_clear.js")
                return result
            }
            // 短暂等待避免 busy loop
            Thread.sleep(10)
        }
        // 超时
        quickJs.evaluate("globalThis.__mf_call_result = null;", "mf_clear.js")
        Log.w(TAG, "[$pluginId] callMethod $methodName timeout (12s)")
        return null
    }

    // ═════════════════════════════════════════════════════════════════
    //  HTTP 桥接实现
    // ═════════════════════════════════════════════════════════════════

    /**
     * 执行 HTTP 请求（同步，由 JS 端调用）
     *
     * 入参 JSON 格式：
     *   { url, method, headers, data, timeout, responseType }
     * 返回 JSON 格式：
     *   { statusCode, headers, body, error }
     */
    private fun executeHttpRequest(req: JSONObject): String {
        val url = req.optString("url", "")
        if (url.isBlank()) return JSONObject().put("error", "url is empty").toString()
        val method = req.optString("method", "GET").uppercase()
        val headers = mutableMapOf<String, String>()
        req.optJSONObject("headers")?.let { h ->
            val keys = h.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                headers[k] = h.optString(k)
            }
        }
        val body = req.optString("data", "")
        val timeoutMs = req.optInt("timeout", 15000)

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs.coerceAtMost(30000)
                readTimeout = timeoutMs.coerceAtMost(30000)
                instanceFollowRedirects = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (!headers.containsKey("User-Agent") && !headers.containsKey("user-agent")) {
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                }
                if (method != "GET" && method != "HEAD" && body.isNotBlank()) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val respHeaders = JSONObject()
            // 读取所有响应头（key 不区分大小写）
            val respHeaderMap = ConcurrentHashMap<String, MutableList<String>>()
            for ((k, v) in conn.headerFields) {
                if (k == null) continue
                respHeaderMap.getOrPut(k.lowercase()) { mutableListOf() }.addAll(v)
            }
            respHeaderMap.forEach { (k, v) -> respHeaders.put(k, JSONArray(v)) }
            val responseBody = try {
                val encoding = conn.contentEncoding?.lowercase() ?: ""
                val rawStream = if (code in 200..299) conn.inputStream else conn.errorStream
                val decompressedStream = when {
                    rawStream == null -> null
                    encoding.contains("gzip") -> java.util.zip.GZIPInputStream(rawStream)
                    encoding.contains("deflate") -> java.util.zip.InflaterInputStream(rawStream)
                    else -> rawStream
                }
                decompressedStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }

            JSONObject().apply {
                put("statusCode", code)
                put("headers", respHeaders)
                put("body", responseBody)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "[$pluginId] HTTP $method $url failed: ${e.message}", e)
            JSONObject().put("error", e.message ?: "http error").toString()
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  结果解析
    // ═════════════════════════════════════════════════════════════════

    private fun parseSearchResult(resultJson: String): MusicFreeSearchResult {
        return try {
            val obj = if (resultJson.startsWith("{")) JSONObject(resultJson) else JSONObject().put("__value", resultJson)
            if (obj.has("__error")) {
                Log.w(TAG, "search error: ${obj.optString("__error")}")
                return MusicFreeSearchResult()
            }
            val isEnd = obj.optBoolean("isEnd", true)
            val dataArr = obj.optJSONArray("data") ?: JSONArray()
            val items = (0 until dataArr.length()).mapNotNull { idx ->
                val item = dataArr.optJSONObject(idx) ?: return@mapNotNull null
                val id = item.optString("id", "")
                if (id.isBlank()) return@mapNotNull null
                MusicFreeSearchItem(
                    id = id,
                    platform = item.optString("platform", ""),
                    title = item.optString("title", ""),
                    artist = item.optString("artist", ""),
                    album = item.optString("album", ""),
                    artwork = item.optString("artwork", ""),
                    duration = item.optLong("duration", 0L),
                    rawJson = item.toString(),
                )
            }
            MusicFreeSearchResult(isEnd = isEnd, data = items)
        } catch (e: Exception) {
            Log.e(TAG, "parseSearchResult failed: ${e.message}", e)
            MusicFreeSearchResult()
        }
    }

    private fun parseMediaSourceResult(resultJson: String): MusicFreeMediaSource {
        return try {
            val obj = if (resultJson.startsWith("{")) JSONObject(resultJson) else JSONObject().put("__value", resultJson)
            if (obj.has("__error")) {
                Log.w(TAG, "getMediaSource error: ${obj.optString("__error")}")
                return MusicFreeMediaSource()
            }
            if (obj.has("__empty") || obj.has("__value")) return MusicFreeMediaSource()
            val url = obj.optString("url", "")
            val headers = mutableMapOf<String, String>()
            obj.optJSONObject("headers")?.let { h ->
                val keys = h.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    headers[k] = h.optString(k)
                }
            }
            val ua = obj.optString("userAgent", "").ifBlank { null }
            MusicFreeMediaSource(url = url, headers = headers, userAgent = ua)
        } catch (e: Exception) {
            Log.e(TAG, "parseMediaSourceResult failed: ${e.message}", e)
            MusicFreeMediaSource()
        }
    }

    private fun jsStringLiteral(s: String): String {
        // 简单转义为 JS 字符串字面量（使用 JSON.stringify 风格）
        return JSONObject().put("v", s).toString().let { it.substring(it.indexOf(":") + 1, it.lastIndexOf("}")) }
    }

    /**
     * Polyfill 脚本：提供 require/URL/axios 等 MusicFree 插件依赖的全局对象
     *
     * 使用函数而非 val 字段，避免 Kotlin 属性初始化顺序问题
     * （init 块在字段声明之前执行时字段为 null）
     */
    private fun getPolyfillScript(): String = """
        // ── ES2019+ polyfill（确保 QuickJS 兼容） ──
        if (!String.prototype.replaceAll) {
            String.prototype.replaceAll = function(pattern, replacement) {
                if (pattern instanceof RegExp) {
                    if (!pattern.flags.includes('g')) throw new TypeError('replaceAll must be called with a global RegExp');
                    return this.replace(pattern, replacement);
                }
                return this.split(String(pattern)).join(String(replacement));
            };
        }
        if (!String.prototype.matchAll) {
            String.prototype.matchAll = function(re) {
                if (!(re instanceof RegExp)) throw new TypeError('matchAll requires RegExp');
                if (!re.flags.includes('g')) throw new TypeError('matchAll requires global flag');
                var self = this, results = [], m;
                var re2 = new RegExp(re.source, re.flags);
                while ((m = re2.exec(self)) !== null) { results.push(m); if (m.index === re2.lastIndex) re2.lastIndex++; }
                return results;
            };
        }
        if (!String.prototype.padStart) {
            String.prototype.padStart = function(len, fill) {
                fill = String(fill || ' ');
                var s = String(this);
                while (s.length < len) s = fill + s;
                return s.slice(0, len);
            };
        }
        if (!String.prototype.padEnd) {
            String.prototype.padEnd = function(len, fill) {
                fill = String(fill || ' ');
                var s = String(this);
                while (s.length < len) s = s + fill;
                return s.slice(0, len);
            };
        }
        if (!String.prototype.trimStart) String.prototype.trimStart = function() { return this.replace(/^\s+/, ''); };
        if (!String.prototype.trimEnd) String.prototype.trimEnd = function() { return this.replace(/\s+$/, ''); };
        if (!Array.prototype.flat) {
            Array.prototype.flat = function(depth) {
                depth = depth === undefined ? 1 : depth;
                var result = [];
                function flatten(arr, d) {
                    for (var i = 0; i < arr.length; i++) {
                        if (Array.isArray(arr[i]) && d > 0) flatten(arr[i], d - 1);
                        else result.push(arr[i]);
                    }
                }
                flatten(this, depth);
                return result;
            };
        }
        if (!Array.prototype.flatMap) {
            Array.prototype.flatMap = function(fn) {
                return this.map(fn).flat(1);
            };
        }
        if (!Object.entries) {
            Object.entries = function(obj) {
                var result = [];
                for (var k in obj) { if (obj.hasOwnProperty(k)) result.push([k, obj[k]]); }
                return result;
            };
        }
        if (!Object.fromEntries) {
            Object.fromEntries = function(entries) {
                var obj = {};
                for (var i = 0; i < entries.length; i++) { obj[entries[i][0]] = entries[i][1]; }
                return obj;
            };
        }
        if (!Object.values) {
            Object.values = function(obj) {
                var result = [];
                for (var k in obj) { if (obj.hasOwnProperty(k)) result.push(obj[k]); }
                return result;
            };
        }
        if (!Array.from) {
            Array.from = function(arrLike, mapFn) {
                var result = [];
                for (var i = 0; i < arrLike.length; i++) {
                    result.push(mapFn ? mapFn(arrLike[i], i) : arrLike[i]);
                }
                return result;
            };
        }
        if (!globalThis || typeof globalThis !== 'object') {
            var globalThis = (function() { return this; })() || {};
        }
        if (!globalThis.Error.captureStackTrace) {
            Error.captureStackTrace = function() {};
        }

        // ── 结果序列化辅助函数 ──
        function __mf_serialize_result(v) {
            if (v === null || v === undefined) return JSON.stringify({ __empty: true });
            if (typeof v === 'string') return JSON.stringify({ __value: v });
            if (typeof v === 'number' || typeof v === 'boolean') return JSON.stringify({ __value: String(v) });
            try {
                return JSON.stringify(v);
            } catch (e) {
                return JSON.stringify({ __value: String(v) });
            }
        }

        // ── URL polyfill（基础实现） ──
        function __mf_URL(url, base) {
            this._url = url;
            this._base = base || '';
            this._parsed = __mf_parseUrl(url, base);
            this.protocol = this._parsed.protocol;
            this.host = this._parsed.host;
            this.hostname = this._parsed.hostname;
            this.port = this._parsed.port;
            this.pathname = this._parsed.pathname;
            this.search = this._parsed.search;
            this.hash = this._parsed.hash;
            this.href = this._parsed.href;
            this.searchParams = {
                get: function(k) {
                    var m = (this._parsed && this._parsed.searchParams) || {};
                    return m[k] || null;
                }.bind(this)
            };
        }
        __mf_URL.prototype.toString = function() { return this.href; };
        function __mf_parseUrl(url, base) {
            // 简单 URL 解析（不依赖浏览器 URL 构造器）
            var result = { protocol: '', host: '', hostname: '', port: '', pathname: '/', search: '', hash: '', href: url, searchParams: {} };
            try {
                var rest = url;
                var idx = rest.indexOf('://');
                if (idx > 0) {
                    result.protocol = rest.substring(0, idx);
                    rest = rest.substring(idx + 3);
                }
                var hashIdx = rest.indexOf('#');
                if (hashIdx >= 0) {
                    result.hash = rest.substring(hashIdx);
                    rest = rest.substring(0, hashIdx);
                }
                var queryIdx = rest.indexOf('?');
                if (queryIdx >= 0) {
                    result.search = rest.substring(queryIdx);
                    rest = rest.substring(0, queryIdx);
                    var qs = result.search.substring(1);
                    qs.split('&').forEach(function(pair) {
                        var eq = pair.indexOf('=');
                        if (eq > 0) {
                            result.searchParams[decodeURIComponent(pair.substring(0, eq))] = decodeURIComponent(pair.substring(eq + 1));
                        }
                    });
                }
                var slashIdx = rest.indexOf('/');
                if (slashIdx >= 0) {
                    result.host = rest.substring(0, slashIdx);
                    result.pathname = rest.substring(slashIdx);
                } else {
                    result.host = rest;
                    result.pathname = '/';
                }
                var colonIdx = result.host.indexOf(':');
                if (colonIdx >= 0) {
                    result.hostname = result.host.substring(0, colonIdx);
                    result.port = result.host.substring(colonIdx + 1);
                } else {
                    result.hostname = result.host;
                }
            } catch (e) {}
            return result;
        }

        // ── require 实现（提供 MusicFree 插件常用的 npm 包） ──
        function __mf_require(name) {
            if (__mf_require._cache[name]) return __mf_require._cache[name];
            var pkg = null;
            switch (name) {
                case 'axios':
                    pkg = __mf_createAxios();
                    break;
                case 'crypto-js':
                    pkg = __mf_createCryptoJs();
                    break;
                case 'cheerio':
                    pkg = __mf_createCheerio();
                    break;
                case 'dayjs':
                    pkg = __mf_createDayjs();
                    break;
                case 'qs':
                    pkg = __mf_createQs();
                    break;
                case 'he':
                    pkg = { encode: function(s) { return s; }, decode: function(s) { return s; } };
                    break;
                case 'big-integer':
                    pkg = function(n) { return { toString: function() { return String(n); }, add: function(o) { return pkg(Number(n) + Number(o)); } }; };
                    break;
                default:
                    pkg = {};
            }
            pkg.default = pkg;
            __mf_require._cache[name] = pkg;
            return pkg;
        }
        __mf_require._cache = {};

        // ── axios 实现（基于 __mf_http_request 桥接，返回 Promise） ──
        // 完全参照 MusicFree-master plugin.ts:14 的 axios 导入，
        // 所有方法必须返回 Promise，因为插件使用 await axios.get() 或 .then() 链式调用
        // 关键：1. 自动 JSON 解析响应体  2. 支持 params 选项  3. 返回标准 axios 响应结构
        function __mf_createAxios() {
            function _buildUrl(url, config) {
                if (!config || !config.params) return url;
                var params = config.params;
                var parts = [];
                for (var k in params) {
                    if (params.hasOwnProperty(k) && params[k] !== undefined && params[k] !== null) {
                        parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(params[k]));
                    }
                }
                if (parts.length === 0) return url;
                var qs = parts.join('&');
                return url + (url.indexOf('?') >= 0 ? '&' : '?') + qs;
            }
            function _request(config) {
                return new Promise(function(resolve, reject) {
                    try {
                        var finalUrl = _buildUrl(config.url, config);
                        __mf_console_log('axios: ' + (config.method || 'GET') + ' ' + finalUrl);
                        var reqJson = JSON.stringify({
                            url: finalUrl,
                            method: (config.method || 'GET').toUpperCase(),
                            headers: config.headers || {},
                            data: config.data || '',
                            timeout: config.timeout || 15000
                        });
                        var respStr = __mf_http_request(reqJson);
                        var resp = JSON.parse(respStr);
                        if (resp.error) {
                            __mf_console_log('axios: HTTP error: ' + resp.error);
                            reject(new Error(resp.error));
                            return;
                        }
                        var body = resp.body || '';
                        var headers = resp.headers || {};
                        __mf_console_log('axios: status=' + resp.statusCode + ' bodyLen=' + (body ? body.length : 0));
                        // axios 标准行为：如果响应头是 application/json 或 body 是 JSON 字符串，自动 JSON.parse
                        var data = body;
                        // headers 中的值可能是数组（如 ["application/json"]），先取第一个元素再 toLowerCase
                        var contentType = headers['content-type'] || headers['Content-Type'] || '';
                        if (Array.isArray(contentType)) contentType = contentType[0] || '';
                        contentType = String(contentType).toLowerCase();
                        if (body && typeof body === 'string') {
                            if (contentType.indexOf('application/json') >= 0 || body.trim().startsWith('{') || body.trim().startsWith('[')) {
                                try {
                                    data = JSON.parse(body);
                                    __mf_console_log('axios: response body JSON parsed, type=' + typeof data);
                                } catch (e) {
                                    __mf_console_log('axios: JSON parse failed, keeping string: ' + e.message);
                                    // JSON 解析失败，保留原始字符串
                                }
                            }
                        }
                        resolve({
                            data: data,
                            status: resp.statusCode || 0,
                            statusCode: resp.statusCode || 0,
                            statusText: '',
                            headers: headers,
                            config: config,
                            request: {}
                        });
                    } catch (e) {
                        __mf_console_log('axios: exception: ' + (e && e.message ? e.message : String(e)));
                        reject(e);
                    }
                });
            }
            var axios = function(config) {
                return _request(config);
            };
            axios.get = function(url, config) {
                config = config || {};
                config.url = url;
                config.method = 'GET';
                return _request(config);
            };
            axios.post = function(url, data, config) {
                config = config || {};
                config.url = url;
                config.method = 'POST';
                config.data = data;
                return _request(config);
            };
            axios.put = function(url, data, config) {
                config = config || {};
                config.url = url;
                config.method = 'PUT';
                config.data = data;
                return _request(config);
            };
            axios.delete = function(url, config) {
                config = config || {};
                config.url = url;
                config.method = 'DELETE';
                return _request(config);
            };
            axios.head = function(url, config) {
                config = config || {};
                config.url = url;
                config.method = 'HEAD';
                return _request(config);
            };
            // axios.defaults（MusicFree plugin.ts:36 设置了 axios.defaults.timeout = 2000）
            axios.defaults = { timeout: 2000 };
            // axios.interceptors（MusicFree plugin.ts:37-47 使用了 response interceptor）
            axios.interceptors = {
                response: {
                    use: function() { /* no-op */ }
                },
                request: {
                    use: function() { /* no-op */ }
                }
            };
            return axios;
        }

        // ── crypto-js 桩（提供最常用方法） ──
        function __mf_createCryptoJs() {
            return {
                MD5: function(s) { return { toString: function() { return __mf_md5(String(s)); } }; },
                SHA1: function(s) { return { toString: function() { return __mf_sha1(String(s)); } }; },
                SHA256: function(s) { return { toString: function() { return __mf_sha256(String(s)); } }; },
                enc: { Hex: { stringify: function(o) { return o.toString(); } }, Base64: { stringify: function(o) { return o.toString(); } } },
                HmacMD5: function(s, k) { return { toString: function() { return ''; } }; },
                HmacSHA256: function(s, k) { return { toString: function() { return ''; } }; },
                AES: { encrypt: function(s, k) { return { toString: function() { return String(s); } }; }, decrypt: function(s, k) { return { toString: function() { return String(s); } }; } },
                DES: { encrypt: function(s, k) { return { toString: function() { return String(s); } }; }, decrypt: function(s, k) { return { toString: function() { return String(s); } }; } },
                Base64: { stringify: function(s) { return __mf_base64_encode(String(s)); }, parse: function(s) { return { toString: function() { return __mf_base64_decode(String(s)); } }; } },
                Utf8: { stringify: function(s) { return String(s); }, parse: function(s) { return s; } }
            };
        }
        function __mf_md5(s) {
            // 简单 MD5 占位（实际 MD5 计算较复杂，部分插件可能不工作）
            var h = 0;
            for (var i = 0; i < s.length; i++) { h = ((h << 5) - h + s.charCodeAt(i)) | 0; }
            return (h >>> 0).toString(16).padStart(8, '0').repeat(4);
        }
        function __mf_sha1(s) { return __mf_md5(s); }
        function __mf_sha256(s) { return __mf_md5(s); }
        function __mf_base64_encode(s) {
            // Base64 编码（简化版，仅 ASCII）
            var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            var result = '';
            for (var i = 0; i < s.length; i += 3) {
                var a = s.charCodeAt(i) & 0xff;
                var b = i + 1 < s.length ? s.charCodeAt(i + 1) & 0xff : 0;
                var c = i + 2 < s.length ? s.charCodeAt(i + 2) & 0xff : 0;
                result += chars[a >> 2];
                result += chars[((a & 3) << 4) | (b >> 4)];
                result += i + 1 < s.length ? chars[((b & 15) << 2) | (c >> 6)] : '=';
                result += i + 2 < s.length ? chars[c & 63] : '=';
            }
            return result;
        }
        function __mf_base64_decode(s) {
            // Base64 解码（简化版）
            var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            var result = '';
            s = s.replace(/[^A-Za-z0-9+/]/g, '');
            for (var i = 0; i < s.length; i += 4) {
                var a = chars.indexOf(s.charAt(i));
                var b = chars.indexOf(s.charAt(i + 1));
                var c = chars.indexOf(s.charAt(i + 2));
                var d = chars.indexOf(s.charAt(i + 3));
                result += String.fromCharCode((a << 2) | (b >> 4));
                if (c >= 0) result += String.fromCharCode(((b & 15) << 4) | (c >> 2));
                if (d >= 0) result += String.fromCharCode(((c & 3) << 6) | d);
            }
            return result;
        }

        // ── cheerio 桩（仅提供最基础 API，复杂 HTML 解析可能不完整） ──
        function __mf_createCheerio() {
            function $(html) {
                this._html = typeof html === 'string' ? html : '';
                this._text = this._html.replace(/<[^>]+>/g, '');
            }
            $.prototype.text = function() { return this._text; };
            $.prototype.html = function() { return this._html; };
            $.prototype.attr = function(n) { return ''; };
            $.prototype.find = function(s) { return $(this._html); };
            $.prototype.each = function(fn) { fn(0, this); return this; };
            $.prototype.toArray = function() { return [this]; };
            $.prototype.first = function() { return this; };
            $.prototype.last = function() { return this; };
            $.prototype.next = function() { return this; };
            $.prototype.prev = function() { return this; };
            $.prototype.parent = function() { return this; };
            $.prototype.children = function() { return []; };
            $.prototype.remove = function() { return this; };
            $.prototype.addClass = function(c) { return this; };
            $.prototype.removeClass = function(c) { return this; };
            $.prototype.hasClass = function(c) { return false; };
            var cheerio = function(sel, ctx, root, opts) {
                if (typeof sel === 'string' && sel.indexOf('<') >= 0) return $(sel);
                return $(ctx || sel || '');
            };
            cheerio.load = function(html) {
                var fn = function(sel) {
                    if (sel) {
                        // 简单选择器：仅支持标签名和 .class
                        return $(html || '');
                    }
                    return $(html || '');
                };
                fn.html = function() { return html; };
                fn.xml = fn.html;
                fn.root = function() { return $(html); };
                fn.contains = function() { return false; };
                return fn;
            };
            return cheerio;
        }

        // ── dayjs 桩 ──
        function __mf_createDayjs() {
            function dayjs(d) {
                this._d = d ? new Date(d) : new Date();
            }
            dayjs.prototype.format = function(fmt) {
                var d = this._d;
                if (!fmt || fmt === 'YYYY-MM-DD HH:mm:ss') {
                    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') + ' ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0') + ':' + String(d.getSeconds()).padStart(2, '0');
                }
                return d.toISOString();
            };
            dayjs.prototype.valueOf = function() { return this._d.getTime(); };
            dayjs.prototype.unix = function() { return Math.floor(this._d.getTime() / 1000); };
            return dayjs;
        }

        // ── qs 桩 ──
        function __mf_createQs() {
            return {
                stringify: function(obj) {
                    var parts = [];
                    for (var k in obj) {
                        if (obj[k] !== undefined && obj[k] !== null) {
                            parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(obj[k]));
                        }
                    }
                    return parts.join('&');
                },
                parse: function(str) {
                    var result = {};
                    if (!str) return result;
                    str.split('&').forEach(function(pair) {
                        var eq = pair.indexOf('=');
                        if (eq > 0) {
                            result[decodeURIComponent(pair.substring(0, eq))] = decodeURIComponent(pair.substring(eq + 1));
                        }
                    });
                    return result;
                }
            };
        }
    """.trimIndent()
}
