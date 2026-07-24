package com.yindong.music.data.lxsdk

import android.util.Base64
import android.util.Log
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * lx-music-mobile 搜索 SDK 工具集
 *
 * 完全按照 lx-music-mobile-master 项目的实现移植：
 * - MD5 / SHA1 / AES / RSA 加密
 * - httpFetch 网络请求（与 src/utils/request.js 一致）
 * - sizeFormate / formatPlayTime / decodeName / formatSingerName 工具
 */
object LxSdk {

    private const val TAG = "LxSdk"

    /** 与 src/utils/request.js defaultHeaders 一致 */
    const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"

    /** 共享 OkHttp 客户端（默认 15s 超时，与 options.js 一致） */
    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** 取消中的 HTTP 调用映射（与 request.js 的 cancelHttp 一致） */
    private val httpCalls = ConcurrentHashMap<String, Call>()

    // ===================== 加密工具 =====================

    /** 与 utils.js toMD5 一致：返回 32 位小写 hex */
    fun md5(str: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(str.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder()
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** 与 tx/utils/crypto.js hashSHA1 一致：返回 40 位小写 hex */
    fun sha1Hex(str: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(str.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder()
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** Base64 编码（NO_WRAP） */
    fun b64Encode(bytes: ByteArray): String =
        String(Base64.encode(bytes, Base64.NO_WRAP), StandardCharsets.UTF_8)

    /** Base64 编码字符串 */
    fun b64EncodeStr(str: String): String =
        b64Encode(str.toByteArray(StandardCharsets.UTF_8))

    /** Base64 解码 */
    fun b64Decode(str: String): ByteArray =
        Base64.decode(str.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)

    /**
     * 网易云 weapi 加密（与 lx-music-mobile weapi(obj) 一致）
     * 两轮 AES-CBC-128-PKCS7Padding + RSA NoPadding
     * @return mapOf("params" to ..., "encSecKey" to ...)
     */
    fun weapiEncrypt(obj: JSONObject): Map<String, String> {
        val text = obj.toString()
        val presetKey = b64EncodeStr("0CoJUm6Qyw8W8jud")
        val iv = b64EncodeStr("0102030405060708")

        // 随机 secretKey（16 字符）
        val secretKey = buildString { repeat(16) { append(('0'..'9').random()) } }

        // 第一轮 AES
        val textB64 = b64EncodeStr(text)
        val firstEncrypted = aesEncrypt(textB64, presetKey, iv, "CBC_128_PKCS7Padding")

        // 第二轮 AES
        val firstEncryptedB64 = b64EncodeStr(firstEncrypted)
        val secretKeyB64 = b64EncodeStr(secretKey)
        val params = aesEncrypt(firstEncryptedB64, secretKeyB64, iv, "CBC_128_PKCS7Padding")

        // RSA NoPadding
        val publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
        val reversedSecretKeyBytes = secretKey.reversed().toByteArray(StandardCharsets.UTF_8)
        val reversedSecretKeyB64 = b64Encode(reversedSecretKeyBytes)
        val encSecKeyB64 = rsaEncrypt(reversedSecretKeyB64, publicKey, "NoPadding")
        val encSecKeyBytes = b64Decode(encSecKeyB64)
        val sb = StringBuilder()
        for (b in encSecKeyBytes) sb.append(String.format("%02x", b))

        return mapOf("params" to params, "encSecKey" to sb.toString())
    }

    /**
     * QQ音乐 zzcSign 签名（与 lx-music-desktop tx/utils/crypto.js zzcSign 完全一致）
     * SHA1 → 索引取字符 → XOR + base64 → zzc{part1}{b64Part}{part2} 小写
     */
    fun zzcSign(text: String): String {
        val part1Indexes = intArrayOf(23, 14, 6, 36, 16, 40, 7, 19)
        val part2Indexes = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
        val scrambleValues = intArrayOf(89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179)

        val hash = sha1Hex(text)

        val part1 = StringBuilder()
        for (idx in part1Indexes) { if (idx < hash.length) part1.append(hash[idx]) }

        val part2 = StringBuilder()
        for (idx in part2Indexes) { if (idx < hash.length) part2.append(hash[idx]) }

        val part3 = ByteArray(20)
        for (i in 0 until 20) {
            val byteVal = scrambleValues[i] xor hash.substring(i * 2, i * 2 + 2).toInt(16)
            part3[i] = byteVal.toByte()
        }
        val b64Part = b64Encode(part3).replace("\\", "").replace("/", "").replace("+", "").replace("=", "")

        return "zzc${part1}${b64Part}${part2}".lowercase()
    }

    /**
     * AES 加密（与 lx-music-mobile nativeModules/crypto.ts + AES.java 一致）
     *
     * 注意：lx-music-mobile 原版中 `AES_MODE.ECB_128_NoPadding = 'AES'`，
     * 而 Java 的 `Cipher.getInstance("AES")` 默认是 `AES/ECB/PKCS5Padding`，
     * 因此原版实际使用 PKCS5Padding（不是真正的 NoPadding）。
     *
     * @param data base64 编码的数据
     * @param key  base64 编码的密钥
     * @param iv   base64 编码的初始向量，空字符串表示 ECB
     * @param mode CBC_128_PKCS7Padding / ECB_128_NoPadding
     * @return base64 编码的密文
     */
    fun aesEncrypt(data: String, key: String, iv: String, mode: String): String {
        val dataBytes = b64Decode(data)
        val keyBytes = b64Decode(key)
        // 与 lx-music-mobile AES.java + crypto.ts 完全一致：'AES' → Cipher.getInstance("AES")
        val cipherMode = when (mode) {
            "CBC_128_PKCS7Padding" -> "AES/CBC/PKCS7Padding"
            "ECB_128_NoPadding" -> "AES"  // 注意：原版用 'AES'，Java 默认 PKCS5Padding
            else -> mode
        }
        val cipher = Cipher.getInstance(cipherMode)
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")
        if (iv.isEmpty()) {
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        } else {
            val ivBytes = b64Decode(iv)
            val finalIv = ByteArray(16)
            System.arraycopy(ivBytes, 0, finalIv, 0, minOf(ivBytes.size, 16))
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(finalIv))
        }
        val encrypted = cipher.doFinal(dataBytes)
        return b64Encode(encrypted)
    }

    /**
     * AES 解密
     * @param data base64 编码的密文
     * @param key  base64 编码的密钥
     * @param iv   base64 编码的初始向量，空字符串表示 ECB
     * @param mode CBC_128_PKCS7Padding / ECB_128_NoPadding
     * @return base64 编码的明文
     */
    fun aesDecrypt(data: String, key: String, iv: String, mode: String): String {
        val dataBytes = b64Decode(data)
        val keyBytes = b64Decode(key)
        val cipherMode = when (mode) {
            "CBC_128_PKCS7Padding" -> "AES/CBC/PKCS7Padding"
            "ECB_128_NoPadding" -> "AES"
            else -> mode
        }
        val cipher = Cipher.getInstance(cipherMode)
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")
        if (iv.isEmpty()) {
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
        } else {
            val ivBytes = b64Decode(iv)
            val finalIv = ByteArray(16)
            System.arraycopy(ivBytes, 0, finalIv, 0, minOf(ivBytes.size, 16))
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(finalIv))
        }
        val decrypted = cipher.doFinal(dataBytes)
        return b64Encode(decrypted)
    }

    /**
     * RSA 加密（与 nativeModules/crypto.ts rsaEncryptSync 一致）
     * - NoPadding 模式需要手动 pad 到 128 字节（与 JS Buffer.concat([Buffer.alloc(128 - buffer.length), buffer]) 一致）
     * - OAEPWithSHA1AndMGF1Padding 模式直接加密
     * @param dataBase64 base64 编码的明文
     * @param publicKeyBase64 base64 编码的公钥（不含 PEM 头尾）
     * @param padding NoPadding / OAEPWithSHA1AndMGF1Padding
     * @return base64 编码的密文
     */
    fun rsaEncrypt(dataBase64: String, publicKeyBase64: String, padding: String): String {
        val keyBytes = b64Decode(publicKeyBase64)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
        val cipherMode = when (padding) {
            "NoPadding" -> "RSA/ECB/NoPadding"
            "OAEPWithSHA1AndMGF1Padding" -> "RSA/ECB/OAEPWithSHA1AndMGF1Padding"
            else -> padding
        }
        val cipher = Cipher.getInstance(cipherMode)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val dataBytes = b64Decode(dataBase64)
        val finalData = if (padding == "NoPadding") {
            // 与 JS Buffer.concat([Buffer.alloc(128 - buffer.length), buffer]) 一致
            val padded = ByteArray(128)
            System.arraycopy(dataBytes, 0, padded, 128 - dataBytes.size, dataBytes.size)
            padded
        } else {
            dataBytes
        }
        val encrypted = cipher.doFinal(finalData)
        return b64Encode(encrypted)
    }

    // ===================== httpFetch =====================

    /**
     * HTTP 请求结果（与 src/utils/request.js fetchData 返回结构一致）
     */
    data class Response(
        val statusCode: Int,
        val body: Any?,  // String 或 JSONObject / JSONArray
        val headers: Map<String, String>,
    )

    /**
     * 与 src/utils/request.js httpFetch 一致
     * @param url 完整 URL
     * @param method get/post/put
     * @param headers 自定义 headers（会与默认 UA + Accept 合并）
     * @param body 请求体（对象会 JSON.stringify，字符串原样发送）
     * @param form 表单数据（自动 urlencoded）
     * @param timeoutMs 超时（默认 15000ms）
     * @return Response
     */
    fun httpFetch(
        url: String,
        method: String = "get",
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
        form: Map<String, String>? = null,
        timeoutMs: Long = 15000L,
    ): Response {
        val requestKey = "${System.nanoTime()}_${url.hashCode()}"

        val mergedHeaders = mutableMapOf(
            "User-Agent" to DEFAULT_UA,
            "Accept" to "application/json",
        )
        mergedHeaders.putAll(headers)

        val requestBuilder = Request.Builder().url(url)

        // 构造 body
        var finalBody: RequestBody? = null
        val m = method.lowercase()
        if (m == "post" || m == "put") {
            when {
                form != null -> {
                    mergedHeaders["Content-Type"] = "application/x-www-form-urlencoded"
                    val sb = StringBuilder()
                    form.entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) sb.append("&")
                        sb.append(URLEncoder.encode(k, "UTF-8"))
                        sb.append("=")
                        sb.append(URLEncoder.encode(v, "UTF-8"))
                    }
                    finalBody = sb.toString().toRequestBody(
                        "application/x-www-form-urlencoded".toMediaTypeOrNull()
                    )
                }
                body != null -> {
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

        when (m) {
            "get" -> requestBuilder.get()
            "post" -> requestBuilder.post(finalBody!!)
            "put" -> requestBuilder.put(finalBody!!)
            "delete" -> {
                if (finalBody != null) requestBuilder.delete(finalBody!!) else requestBuilder.delete()
            }
            "head" -> requestBuilder.head()
            else -> requestBuilder.method(method.uppercase(), finalBody)
        }

        val client = sharedClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val call = client.newCall(requestBuilder.build())
        httpCalls[requestKey] = call

        try {
            call.execute().use { resp ->
                httpCalls.remove(requestKey)
                val rawBody = resp.body?.string() ?: ""
                val respHeaders = mutableMapOf<String, String>()
                for (i in 0 until resp.headers.size) {
                    respHeaders[resp.headers.name(i)] = resp.headers.value(i)
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
                return Response(resp.code, parsedBody, respHeaders)
            }
        } catch (e: Exception) {
            httpCalls.remove(requestKey)
            Log.w(TAG, "httpFetch failed: $url — ${e.message}")
            throw e
        }
    }

    // ===================== 工具函数 =====================

    /** 与 src/utils/common.ts sizeFormate 一致 */
    fun sizeFormate(size: Long): String {
        if (size == 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val n = Math.floor(Math.log(size.toDouble()) / Math.log(1024.0)).toInt()
        val idx = n.coerceIn(0, units.size - 1)
        val v = size / Math.pow(1024.0, idx.toDouble())
        return String.format("%.2f %s", v, units[idx])
    }

    /** 与 src/utils/common.ts formatPlayTime 一致 */
    fun formatPlayTime(timeSec: Long): String {
        val m = timeSec / 60
        val s = timeSec % 60
        return if (m == 0L && s == 0L) "--/--" else numFix(m) + ":" + numFix(s)
    }

    /** 与 src/utils/common.ts formatPlayTime 一致（字符串输入） */
    fun formatPlayTime(interval: String?): String {
        if (interval.isNullOrBlank()) return "--/--"
        // "mm:ss" → seconds
        val parts = interval.split(":")
        var sec = 0L
        var unit = 1L
        for (i in parts.indices.reversed()) {
            sec += (parts[i].trim().toLongOrNull() ?: 0L) * unit
            unit *= 60
        }
        return formatPlayTime(sec)
    }

    private fun numFix(n: Long): String = if (n < 10) "0$n" else n.toString()

    /**
     * 与 src/utils/index.ts decodeName 一致：HTML 实体解码
     * 使用 he.decode 的简化实现，覆盖常见 HTML 实体
     */
    fun decodeName(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '&') {
                val semi = str.indexOf(';', i)
                if (semi > i && semi - i < 12) {
                    val entity = str.substring(i + 1, semi)
                    val decoded = decodeEntity(entity)
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun decodeEntity(entity: String): String? {
        return when {
            entity.startsWith("#x") || entity.startsWith("#X") -> {
                entity.substring(2).toIntOrNull(16)?.let { Character.toChars(it).concatToString() }
            }
            entity.startsWith("#") -> {
                entity.substring(1).toIntOrNull()?.let { Character.toChars(it).concatToString() }
            }
            else -> when (entity) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                "copy" -> "©"
                "reg" -> "®"
                "trade" -> "™"
                "hellip" -> "…"
                "mdash" -> "—"
                "ndash" -> "–"
                "ldquo" -> "\u201C"
                "rdquo" -> "\u201D"
                "lsquo" -> "\u2018"
                "rsquo" -> "\u2019"
                else -> null
            }
        }
    }

    /**
     * 与 musicSdk/utils.js formatSingerName 一致
     * @param singers 歌手数组（每个元素是 JSONObject，含 nameKey 字段）
     * @param nameKey 取值的键名（默认 "name"）
     * @param join 连接字符（默认 "、"）
     */
    fun formatSingerName(singers: JSONArray?, nameKey: String = "name", join: String = "、"): String {
        if (singers == null) return ""
        val sb = StringBuilder()
        for (i in 0 until singers.length()) {
            val item = singers.optJSONObject(i) ?: continue
            val name = item.optString(nameKey)
            if (name.isBlank()) continue
            if (sb.isNotEmpty()) sb.append(join)
            sb.append(decodeName(name))
        }
        return sb.toString()
    }

    /** formatSingerName 的字符串/数组字符串输入版本 */
    fun formatSingerName(singers: Any?): String {
        return when (singers) {
            is JSONArray -> formatSingerName(singers)
            is String -> decodeName(singers)
            null -> ""
            else -> decodeName(singers.toString())
        }
    }

    /** URL 解码（与 URLDecoder.decode 一致，UTF-8） */
    fun urlDecode(str: String): String = URLDecoder.decode(str, "UTF-8")

    /** URL 编码（UTF-8） */
    fun urlEncode(str: String): String = URLEncoder.encode(str, "UTF-8")
}
