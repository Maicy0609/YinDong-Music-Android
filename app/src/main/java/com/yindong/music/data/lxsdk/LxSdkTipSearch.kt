package com.yindong.music.data.lxsdk

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * lx-music-mobile 4 平台搜索建议（tipSearch）实现
 *
 * 完全按照以下文件移植：
 * - src/utils/musicSdk/wy/tipSearch.js（eapi 加密 /api/search/suggest/web）
 * - src/utils/musicSdk/tx/tipSearch.js（smartbox_new.fcg）
 * - src/utils/musicSdk/kw/tipSearch.js（tips.kuwo.cn）
 * - src/utils/musicSdk/kg/tipSearch.js（searchtip.kugou.com）
 *
 * 用于搜索框实时输入联想，参考 lx-music-mobile-master/src/screens/Home/Views/Search/TipList.tsx
 * debounce 200ms 调用 [search]，单平台失败不影响其他平台。
 */
object LxSdkTipSearch {

    private const val TAG = "LxSdkTipSearch"

    /**
     * 联想搜索：并行调用 4 平台 tipSearch，合并去重后返回前 12 条。
     * 与 lx-music-mobile TipList 的 debounceTipSearch 一致，但本方法本身不实现 debounce，
     * 由调用方自行 debounce。
     *
     * @param keyword 用户当前输入的关键词
     * @return 合并去重后的关键词列表
     */
    suspend fun search(keyword: String): List<String> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val results = coroutineScope {
            listOf(
                async { runCatching { searchWy(keyword) }.getOrNull() },
                async { runCatching { searchTx(keyword) }.getOrNull() },
                async { runCatching { searchKw(keyword) }.getOrNull() },
                async { runCatching { searchKg(keyword) }.getOrNull() },
            ).awaitAll()
        }
        val merged = LinkedHashSet<String>()
        for (list in results) {
            if (list == null) continue
            for (word in list) {
                if (word.isNotBlank()) merged.add(word)
            }
        }
        val failedSources = results.mapIndexedNotNull { idx, r ->
            if (r == null) listOf("wy", "tx", "kw", "kg")[idx] else null
        }
        if (failedSources.isNotEmpty()) {
            Log.w(TAG, "search partial failure: $failedSources for keyword='$keyword'")
        }
        merged.toList().take(12)
    }

    // ===================== 网易云 =====================

    /**
     * 网易云搜索建议（与 wy/tipSearch.js 一致）
     * eapi 加密请求 /api/search/suggest/web，参数 { s }
     * 返回 result.songs，每条格式 "歌名 - 歌手"
     */
    private suspend fun searchWy(keyword: String): List<String> {
        val url = "/api/search/suggest/web"
        val data = JSONObject().apply { put("s", keyword) }
        val params = eapiEncrypt(url, data)

        val resp = LxSdk.httpFetch(
            url = "http://interface.music.163.com/eapi/batch",
            method = "post",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
                "Cookie" to "osver=Linux; version=2.10.6; channel=netease; os=pc; MUSIC_U=; __csrf=;",
            ),
            form = mapOf("params" to params),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("wy tipSearch response not JSON")
        if (body.optInt("code") != 200) {
            throw RuntimeException("wy tipSearch failed: code=${body.optInt("code")}")
        }

        val result = body.optJSONObject("result") ?: return emptyList()
        val songs = result.optJSONArray("songs") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val name = song.optString("name")
            val artists = song.optJSONArray("artists") ?: song.optJSONArray("ar")
            val singer = LxSdk.formatSingerName(artists)
            if (name.isNotBlank()) {
                list.add(if (singer.isNotBlank()) "$name - $singer" else name)
            }
        }
        return list
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
        val eapiKey = LxSdk.b64EncodeStr("e82ckenh8dichen8")
        val dataB64 = LxSdk.b64EncodeStr(data)
        val encrypted = LxSdk.aesEncrypt(dataB64, eapiKey, "", "ECB_128_NoPadding")
        val encryptedBytes = LxSdk.b64Decode(encrypted)
        val sb = StringBuilder()
        for (b in encryptedBytes) sb.append(String.format("%02X", b))
        return sb.toString()
    }

    // ===================== QQ 音乐 =====================

    /**
     * QQ 音乐搜索建议（与 tx/tipSearch.js 一致）
     * GET https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg
     * 返回 data.song.itemlist[]，每条格式 "歌名 - 歌手"
     */
    private suspend fun searchTx(keyword: String): List<String> {
        val url = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg" +
            "?is_xml=0&format=json&key=${java.net.URLEncoder.encode(keyword, "UTF-8")}" +
            "&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8" +
            "&notice=0&platform=yqq&needNewCode=0"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf("Referer" to "https://y.qq.com/portal/player.html"),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("tx tipSearch response not JSON")
        if (body.optInt("code") != 0) {
            throw RuntimeException("tx tipSearch failed: code=${body.optInt("code")}")
        }

        val data = body.optJSONObject("data") ?: return emptyList()
        val song = data.optJSONObject("song") ?: return emptyList()
        val itemList = song.optJSONArray("itemlist") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until itemList.length()) {
            val item = itemList.optJSONObject(i) ?: continue
            val name = item.optString("name")
            val singer = item.optString("singer")
            if (name.isNotBlank()) {
                list.add(if (singer.isNotBlank()) "$name - $singer" else name)
            }
        }
        return list
    }

    // ===================== 酷我 =====================

    /**
     * 酷我搜索建议（与 kw/tipSearch.js 一致）
     * GET https://tips.kuwo.cn/t.s?...&w=<keyword>
     * 返回 WORDITEMS[].RELWORD
     */
    private suspend fun searchKw(keyword: String): List<String> {
        val url = "https://tips.kuwo.cn/t.s?corp=kuwo&newver=3&p2p=1&notrace=0&c=mbox" +
            "&w=${java.net.URLEncoder.encode(keyword, "UTF-8")}&encoding=utf8&rformat=json"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf("Referer" to "http://www.kuwo.cn/"),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kw tipSearch response not JSON")
        val wordItems = body.optJSONArray("WORDITEMS") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until wordItems.length()) {
            val item = wordItems.optJSONObject(i) ?: continue
            val word = item.optString("RELWORD")
            if (word.isNotBlank()) list.add(word)
        }
        return list
    }

    // ===================== 酷狗 =====================

    /**
     * 酷狗搜索建议（与 kg/tipSearch.js 一致）
     * GET https://searchtip.kugou.com/getSearchTip?MusicTipCount=10&keyword=<keyword>
     * 返回 [0].RecordDatas[].HintInfo
     */
    private suspend fun searchKg(keyword: String): List<String> {
        val url = "https://searchtip.kugou.com/getSearchTip?MusicTipCount=10" +
            "&keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf("Referer" to "https://www.kugou.com/"),
        )

        val body = resp.body
        // 酷狗返回 JSONArray
        val arr = when (body) {
            is org.json.JSONArray -> body
            is JSONObject -> {
                // 某些情况下被包在对象里
                val inner = body.optJSONArray("data") ?: return emptyList()
                inner
            }
            else -> return emptyList()
        }
        if (arr.length() == 0) return emptyList()
        val first = arr.optJSONObject(0) ?: return emptyList()
        val recordDatas = first.optJSONArray("RecordDatas") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until recordDatas.length()) {
            val item = recordDatas.optJSONObject(i) ?: continue
            val hint = item.optString("HintInfo")
            if (hint.isNotBlank()) list.add(hint)
        }
        return list
    }
}
