package com.yindong.music.data.lxsdk

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * lx-music-mobile 4 平台热门搜索实现
 *
 * 完全按照以下文件移植：
 * - src/utils/musicSdk/wy/hotSearch.js（eapi 加密请求 /api/search/chart/detail）
 * - src/utils/musicSdk/tx/hotSearch.js（musicu.fcg GetHotkeyForQQMusicPC）
 * - src/utils/musicSdk/kw/hotSearch.js（hotword.kuwo.cn）
 * - src/utils/musicSdk/kg/hotSearch.js（gateway.kugou.com hot_tab）
 *
 * 每个平台返回 [HotSearchResult]，[fetchAll] 并行合并去重。
 */
object LxSdkHotSearch {

    private const val TAG = "LxSdkHotSearch"

    data class HotSearchResult(
        val source: String,        // "wy", "tx", "kw", "kg"
        val list: List<String>,    // 热门搜索关键词列表
    )

    // ===================== 网易云 =====================

    /**
     * 网易云热搜（与 wy/hotSearch.js 一致）
     * 使用 eapi 加密请求 /api/search/chart/detail，参数 { id: 'HOT_SEARCH_SONG#@#' }
     */
    suspend fun searchWy(): HotSearchResult = withContext(Dispatchers.IO) {
        val url = "/api/search/chart/detail"
        val data = JSONObject().apply {
            put("id", "HOT_SEARCH_SONG#@#")
        }

        val params = eapiEncrypt(url, data)

        val resp = LxSdk.httpFetch(
            url = "http://interface.music.163.com/eapi/batch",
            method = "post",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
                "Cookie" to "osver=Linux; version=2.10.6; channel=netease; os=pc; email=; requestId=; MUSIC_U=; __csrf=;",
            ),
            form = mapOf("params" to params),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("wy hotSearch response not JSON: ${resp.body}")
        if (body.optInt("code") != 200) {
            throw RuntimeException("wy hotSearch failed: code=${body.optInt("code")}")
        }

        val dataObj = body.optJSONObject("data") ?: JSONObject()
        // 与 wy/hotSearch.js filterList 一致：data.itemList，兼容 hotSearchList / list
        val rawList = dataObj.optJSONArray("itemList")
            ?: dataObj.optJSONArray("hotSearchList")
            ?: dataObj.optJSONArray("list")
            ?: JSONArray()
        val list = mutableListOf<String>()
        for (i in 0 until rawList.length()) {
            val item = rawList.optJSONObject(i) ?: continue
            val word = item.optString("searchWord")
            if (word.isNotBlank()) list.add(word)
        }
        HotSearchResult("wy", list)
    }

    /**
     * 与 wy/utils/crypto.js eapi(url, object) 完全一致
     * （从 LxSdkSearch.kt 复制，因 LxSdkSearch 中该方法为 private）
     * @return hex 大写字符串
     */
    private fun eapiEncrypt(url: String, obj: Any): String {
        val text = if (obj is JSONObject) obj.toString() else obj.toString()
        val message = "nobody${url}use${text}md5forencrypt"
        val digest = LxSdk.md5(message)
        val data = "$url-36cd479b6b5-$text-36cd479b6b5-$digest"
        // AES-ECB-128-NoPadding
        val eapiKey = LxSdk.b64EncodeStr("e82ckenh8dichen8")
        val dataB64 = LxSdk.b64EncodeStr(data)
        val encrypted = LxSdk.aesEncrypt(dataB64, eapiKey, "", "ECB_128_NoPadding")
        // base64 → hex 大写
        val encryptedBytes = LxSdk.b64Decode(encrypted)
        val sb = StringBuilder()
        for (b in encryptedBytes) sb.append(String.format("%02X", b))
        return sb.toString()
    }

    // ===================== QQ 音乐 =====================

    /**
     * QQ 音乐热搜（与 tx/hotSearch.js 一致）
     * POST https://u.y.qq.com/cgi-bin/musicu.fcg GetHotkeyForQQMusicPC
     */
    suspend fun searchTx(): HotSearchResult = withContext(Dispatchers.IO) {
        val data = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("ct", "19")
                put("cv", "1803")
                put("guid", "0")
                put("patch", "118")
                put("tmeAppID", "qqmusic")
                put("uin", "0")
            })
            put("hotkey", JSONObject().apply {
                put("method", "GetHotkeyForQQMusicPC")
                put("module", "tencent_musicsoso_hotkey.HotkeyService")
                put("param", JSONObject().apply {
                    put("search_id", "")
                    put("uin", 0)
                })
            })
        }

        val resp = LxSdk.httpFetch(
            url = "https://u.y.qq.com/cgi-bin/musicu.fcg",
            method = "post",
            headers = mapOf("Referer" to "https://y.qq.com/portal/player.html"),
            body = data,
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("tx hotSearch response not JSON: ${resp.body}")
        if (body.optInt("code") != 0) {
            throw RuntimeException("tx hotSearch failed: code=${body.optInt("code")}")
        }

        // 与 tx/hotSearch.js filterList 一致：body.hotkey.data.vec_hotkey[].query
        val vecHotkey = body.optJSONObject("hotkey")
            ?.optJSONObject("data")
            ?.optJSONArray("vec_hotkey")
            ?: JSONArray()
        val list = mutableListOf<String>()
        for (i in 0 until vecHotkey.length()) {
            val item = vecHotkey.optJSONObject(i) ?: continue
            val query = item.optString("query")
            if (query.isNotBlank()) list.add(query)
        }
        HotSearchResult("tx", list)
    }

    // ===================== 酷我 =====================

    /**
     * 酷我热搜（与 kw/hotSearch.js 一致）
     * GET http://hotword.kuwo.cn/hotword.s ...
     */
    suspend fun searchKw(): HotSearchResult = withContext(Dispatchers.IO) {
        val url = "http://hotword.kuwo.cn/hotword.s?prod=kwplayer_ar_9.3.0.1&corp=kuwo&newver=2&vipver=9.3.0.1&source=kwplayer_ar_9.3.0.1_40.apk&p2p=1&notrace=0&uid=0&plat=kwplayer_ar&rformat=json&encoding=utf8&tabid=1"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf("User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 9;)"),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kw hotSearch response not JSON: ${resp.body}")
        // 与 kw/hotSearch.js 一致：body.status === 'ok'
        if (body.optString("status") != "ok") {
            throw RuntimeException("kw hotSearch failed: status=${body.optString("status")}")
        }

        // 与 kw/hotSearch.js filterList 一致：body.tagvalue[].key
        val tagvalue = body.optJSONArray("tagvalue") ?: JSONArray()
        val list = mutableListOf<String>()
        for (i in 0 until tagvalue.length()) {
            val item = tagvalue.optJSONObject(i) ?: continue
            val key = item.optString("key")
            if (key.isNotBlank()) list.add(key)
        }
        HotSearchResult("kw", list)
    }

    // ===================== 酷狗 =====================

    /**
     * 酷狗热搜（与 kg/hotSearch.js 一致）
     * GET http://gateway.kugou.com/api/v3/search/hot_tab ...
     */
    suspend fun searchKg(): HotSearchResult = withContext(Dispatchers.IO) {
        val url = "http://gateway.kugou.com/api/v3/search/hot_tab?signature=ee44edb9d7155821412d220bcaf509dd&appid=1005&clientver=10026&plat=0"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf(
                "User-Agent" to "kmplayer_9020&android&android10&4.4.4&HUAWEI&xxx&android.baidumusic&3.1.0.1",
                "kg-rc" to "1",
                "dfid" to "2OQLmE1W8Mba0c1vGa3R0rFD",
                "mid" to "1566798377155319802633948005",
                "clienttime" to "1566798377155",
            ),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kg hotSearch response not JSON: ${resp.body}")
        // 与 kg/hotSearch.js 一致：body.errcode === 0
        if (body.optInt("errcode") != 0) {
            throw RuntimeException("kg hotSearch failed: errcode=${body.optInt("errcode")}")
        }

        // 与 kg/hotSearch.js filterList 一致：data.list[].keywords[].keyword（decodeName 解码）
        val dataObj = body.optJSONObject("data") ?: JSONObject()
        val lists = dataObj.optJSONArray("list") ?: JSONArray()
        val list = mutableListOf<String>()
        for (i in 0 until lists.length()) {
            val item = lists.optJSONObject(i) ?: continue
            val keywords = item.optJSONArray("keywords") ?: continue
            for (j in 0 until keywords.length()) {
                val k = keywords.optJSONObject(j) ?: continue
                val keyword = LxSdk.decodeName(k.optString("keyword"))
                if (keyword.isNotBlank()) list.add(keyword)
            }
        }
        HotSearchResult("kg", list)
    }

    // ===================== 合并 =====================

    /**
     * 并行获取 4 平台热搜，合并去重，返回前 20 个。
     * 单个平台失败不影响其他平台（runCatching 隔离异常）。
     */
    suspend fun fetchAll(): List<String> = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            listOf(
                async { runCatching { searchWy() }.getOrNull() },
                async { runCatching { searchTx() }.getOrNull() },
                async { runCatching { searchKw() }.getOrNull() },
                async { runCatching { searchKg() }.getOrNull() },
            ).awaitAll()
        }

        // 合并去重，保留首次出现顺序（LinkedHashSet）
        val merged = LinkedHashSet<String>()
        for (result in results) {
            if (result == null) continue
            for (word in result.list) {
                if (word.isNotBlank()) merged.add(word)
            }
        }

        // 记录失败平台
        val failedSources = results.mapIndexedNotNull { idx, r ->
            if (r == null) listOf("wy", "tx", "kw", "kg")[idx] else null
        }
        if (failedSources.isNotEmpty()) {
            Log.w(TAG, "fetchAll partial failure: $failedSources")
        }

        merged.toList().take(20)
    }
}
