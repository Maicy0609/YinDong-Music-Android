package com.yindong.music.data.lxsdk

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * lx-music-mobile-master 5 平台搜索实现
 *
 * 完全按照以下文件移植：
 * - src/utils/musicSdk/wy/musicSearch.js + wy/utils/crypto.js + wy/utils/index.js
 * - src/utils/musicSdk/tx/musicSearch.js + tx/utils/crypto.js + tx/utils/index.js
 * - src/utils/musicSdk/kw/musicSearch.js + kw/util.js
 * - src/utils/musicSdk/kg/musicSearch.js + kg/util.js
 * - src/utils/musicSdk/mg/musicSearch.js + mg/utils/index.js
 *
 * 每个平台的搜索结果统一为 [LxSdkSong] 列表。
 */
object LxSdkSearch {

    private const val TAG = "LxSdkSearch"

    /** 搜索结果数据类（与 lx-music musicSdk 各平台 search() 返回结构一致） */
    data class LxSdkSong(
        val singer: String,
        val name: String,
        val albumName: String,
        val albumId: String,
        val source: String,        // wy/tx/kw/kg/mg
        val interval: String,      // "mm:ss"
        val songmid: String,       // 主 ID
        val img: String,
        val types: List<TypeItem>, // 可用音质
        val _types: Map<String, TypeItem>, // type → item
        /** 额外字段（如 hash/strMediaMid/copyrightId 等，播放 URL 需要） */
        val ext: Map<String, String> = emptyMap(),
    )

    data class TypeItem(val type: String, val size: String?, val hash: String? = null)

    data class SearchResult(
        val list: List<LxSdkSong>,
        val allPage: Int,
        val total: Int,
        val limit: Int,
        val source: String,
    )

    // ===================== 网易云 =====================

    /**
     * 网易云搜索（与 wy/musicSearch.js 完全一致）
     * 使用 eapi 加密
     */
    suspend fun searchWy(str: String, page: Int = 1, limit: Int = 30): SearchResult {
        // 与 wy/utils/index.js eapiRequest(url, data) 一致
        // URL: /api/search/song/list/page
        // body: { keyword, needCorrect:'1', channel:'typing', offset, scene:'normal', total, limit }
        val url = "/api/search/song/list/page"
        val data = JSONObject().apply {
            put("keyword", str)
            put("needCorrect", "1")
            put("channel", "typing")
            put("offset", limit * (page - 1))
            put("scene", "normal")
            put("total", page == 1)
            put("limit", limit)
        }

        val params = eapiEncrypt(url, data)

        val resp = LxSdk.httpFetch(
            url = "http://interface.music.163.com/eapi/batch",
            method = "post",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
                "origin" to "https://music.163.com",
            ),
            form = mapOf("params" to params),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("wy search response not JSON: ${resp.body}")
        if (body.optInt("code") != 200) {
            throw RuntimeException("wy search failed: code=${body.optInt("code")}")
        }

        val dataObj = body.optJSONObject("data") ?: JSONObject()
        val resources = dataObj.optJSONArray("resources") ?: JSONArray()
        val list = mutableListOf<LxSdkSong>()
        for (i in 0 until resources.length()) {
            val item = resources.optJSONObject(i) ?: continue
            val simpleSong = item.optJSONObject("baseInfo")?.optJSONObject("simpleSongData") ?: continue
            val parsed = parseWySong(simpleSong)
            if (parsed != null) list.add(parsed)
        }

        val total = dataObj.optInt("totalCount", 0)
        val allPage = if (limit > 0) (total + limit - 1) / limit else 1
        return SearchResult(list, allPage.coerceAtLeast(1), total, limit, "wy")
    }

    /**
     * 与 wy/musicSearch.js handleResult 一致
     */
    private fun parseWySong(item: JSONObject): LxSdkSong? {
        val privilege = item.optJSONObject("privilege") ?: JSONObject()
        val types = mutableListOf<TypeItem>()
        val _types = mutableMapOf<String, TypeItem>()

        // hires
        if (privilege.optString("maxBrLevel") == "hires") {
            val hr = item.optJSONObject("hr")
            val size = hr?.let { LxSdk.sizeFormate(it.optLong("size")) }
            types.add(TypeItem("flac24bit", size))
            _types["flac24bit"] = TypeItem("flac24bit", size)
        }
        // 根据 maxbr 决定可用音质（与 JS switch fallthrough 一致）
        val maxbr = privilege.optInt("maxbr")
        when (maxbr) {
            999000 -> {
                val sq = item.optJSONObject("sq")
                val size = sq?.let { LxSdk.sizeFormate(it.optLong("size")) }
                types.add(TypeItem("flac", size))
                _types["flac"] = TypeItem("flac", size)
                val h = item.optJSONObject("h")
                val size2 = h?.let { LxSdk.sizeFormate(it.optLong("size")) }
                types.add(TypeItem("320k", size2))
                _types["320k"] = TypeItem("320k", size2)
                val l = item.optJSONObject("l")
                val size3 = l?.let { LxSdk.sizeFormate(it.optLong("size")) }
                types.add(TypeItem("128k", size3))
                _types["128k"] = TypeItem("128k", size3)
            }
            320000 -> {
                val h = item.optJSONObject("h")
                val size = h?.let { LxSdk.sizeFormate(it.optLong("size")) }
                types.add(TypeItem("320k", size))
                _types["320k"] = TypeItem("320k", size)
                val l = item.optJSONObject("l")
                val size2 = l?.let { LxSdk.sizeFormate(it.optLong("size")) }
                types.add(TypeItem("128k", size2))
                _types["128k"] = TypeItem("128k", size2)
            }
            192000, 128000 -> {
                val l = item.optJSONObject("l")
                val size = l?.let { LxSdk.sizeFormate(it.optLong("size")) }
                types.add(TypeItem("128k", size))
                _types["128k"] = TypeItem("128k", size)
            }
        }
        types.reverse()

        val ar = item.optJSONArray("ar") ?: JSONArray()
        val al = item.optJSONObject("al") ?: JSONObject()

        return LxSdkSong(
            singer = LxSdk.formatSingerName(ar),
            name = item.optString("name"),
            albumName = al.optString("name"),
            albumId = al.optInt("id").toString(),
            source = "wy",
            interval = LxSdk.formatPlayTime(item.optLong("dt") / 1000),
            songmid = item.optLong("id").toString(),
            img = al.optString("picUrl"),
            types = types,
            _types = _types,
        )
    }

    /**
     * 与 wy/utils/crypto.js eapi(url, object) 完全一致
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
     * QQ 音乐搜索（与 tx/musicSearch.js 完全一致）
     * 使用 zzcSign 签名
     */
    suspend fun searchTx(str: String, page: Int = 1, limit: Int = 50): SearchResult {
        val data = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("ct", "11")
                put("cv", "14090508")
                put("v", "14090508")
                put("tmeAppID", "qqmusic")
                put("phonetype", "EBG-AN10")
                put("deviceScore", "553.47")
                put("devicelevel", "50")
                put("newdevicelevel", "20")
                put("rom", "HuaWei/EMOTION/EmotionUI_14.2.0")
                put("os_ver", "12")
                put("OpenUDID", "0")
                put("OpenUDID2", "0")
                put("QIMEI36", "0")
                put("udid", "0")
                put("chid", "0")
                put("aid", "0")
                put("oaid", "0")
                put("taid", "0")
                put("tid", "0")
                put("wid", "0")
                put("uid", "0")
                put("sid", "0")
                put("modeSwitch", "6")
                put("teenMode", "0")
                put("ui_mode", "2")
                put("nettype", "1020")
                put("v4ip", "")
            })
            put("req", JSONObject().apply {
                put("module", "music.search.SearchCgiService")
                put("method", "DoSearchForQQMusicMobile")
                put("param", JSONObject().apply {
                    put("search_type", 0)
                    put("searchid", Math.random().toString().substring(2))
                    put("query", str)
                    put("page_num", page)
                    put("num_per_page", limit)
                    put("highlight", 0)
                    put("nqc_flag", 0)
                    put("multi_zhida", 0)
                    put("cat", 2)
                    put("grp", 1)
                    put("sin", 0)
                    put("sem", 0)
                })
            })
        }

        val sign = zzcSign(data.toString())
        val resp = LxSdk.httpFetch(
            url = "https://u.y.qq.com/cgi-bin/musics.fcg?sign=$sign",
            method = "post",
            headers = mapOf("User-Agent" to "QQMusic 14090508(android 12)"),
            body = data,
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("tx search response not JSON: ${resp.body}")

        val reqObj = body.optJSONObject("req")
        if (body.optInt("code") != 0 || reqObj?.optInt("code") != 0) {
            throw RuntimeException("tx search failed: code=${body.optInt("code")}")
        }
        // 原版 musicSearch 返回 body.req.data，search 从中解构 { body, meta }
        // 所以 item_song 在 data.body.item_song，meta 在 data.meta
        val reqData = reqObj?.optJSONObject("data") ?: JSONObject()
        val reqBody = reqData.optJSONObject("body") ?: JSONObject()
        val itemSong = reqBody.optJSONArray("item_song") ?: JSONArray()
        val list = mutableListOf<LxSdkSong>()
        for (i in 0 until itemSong.length()) {
            val item = itemSong.optJSONObject(i) ?: continue
            val parsed = parseTxSong(item)
            if (parsed != null) list.add(parsed)
        }

        val meta = reqData.optJSONObject("meta") ?: JSONObject()
        val total = meta.optInt("estimate_sum", 0)
        val allPage = if (limit > 0) (total + limit - 1) / limit else 1
        return SearchResult(list, allPage.coerceAtLeast(1), total, limit, "tx")
    }

    /**
     * 与 tx/musicSearch.js handleResult 一致
     */
    private fun parseTxSong(item: JSONObject): LxSdkSong? {
        val file = item.optJSONObject("file") ?: return null
        val mediaMid = file.optString("media_mid")
        if (mediaMid.isBlank()) return null

        val types = mutableListOf<TypeItem>()
        val _types = mutableMapOf<String, TypeItem>()

        if (file.optInt("size_128mp3") != 0) {
            val size = LxSdk.sizeFormate(file.optLong("size_128mp3"))
            types.add(TypeItem("128k", size))
            _types["128k"] = TypeItem("128k", size)
        }
        if (file.optInt("size_320mp3") != 0) {
            val size = LxSdk.sizeFormate(file.optLong("size_320mp3"))
            types.add(TypeItem("320k", size))
            _types["320k"] = TypeItem("320k", size)
        }
        if (file.optInt("size_flac") != 0) {
            val size = LxSdk.sizeFormate(file.optLong("size_flac"))
            types.add(TypeItem("flac", size))
            _types["flac"] = TypeItem("flac", size)
        }
        if (file.optInt("size_hires") != 0) {
            val size = LxSdk.sizeFormate(file.optLong("size_hires"))
            types.add(TypeItem("flac24bit", size))
            _types["flac24bit"] = TypeItem("flac24bit", size)
        }

        val album = item.optJSONObject("album")
        val albumName = album?.optString("name") ?: ""
        val albumId = album?.optString("mid") ?: ""
        val singerArr = item.optJSONArray("singer") ?: JSONArray()

        // 与 tx/musicSearch.js img 计算一致
        val img = if (albumId.isBlank() || albumId == "空") {
            if (singerArr.length() > 0) {
                val mid = singerArr.optJSONObject(0)?.optString("mid") ?: ""
                if (mid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T001R500x500M000${mid}.jpg" else ""
            } else ""
        } else {
            "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumId}.jpg"
        }

        return LxSdkSong(
            singer = LxSdk.formatSingerName(singerArr),
            name = item.optString("title"),
            albumName = albumName,
            albumId = albumId,
            source = "tx",
            interval = LxSdk.formatPlayTime(item.optLong("interval")),
            songmid = item.optString("mid"),
            img = img,
            types = types,
            _types = _types,
            ext = mapOf(
                "songId" to item.optString("id"),
                "strMediaMid" to mediaMid,
                "albumMid" to (album?.optString("mid") ?: ""),
            ),
        )
    }

    /**
     * 与 tx/utils/crypto.js zzcSign 完全一致
     * @return zzc${part1}${b64Part}${part2} 小写
     */
    private fun zzcSign(text: String): String {
        val part1Indexes = intArrayOf(23, 14, 6, 36, 16, 40, 7, 19)
        val part2Indexes = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
        val scrambleValues = intArrayOf(89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179)

        val hash = LxSdk.sha1Hex(text)

        // 与 JS 一致：hash[idx] 越界时返回 undefined，join('') 时被当作空字符串
        // part1
        val part1 = StringBuilder()
        for (idx in part1Indexes) {
            if (idx < hash.length) part1.append(hash[idx])
        }

        // part2
        val part2 = StringBuilder()
        for (idx in part2Indexes) {
            if (idx < hash.length) part2.append(hash[idx])
        }

        // part3: 与 hash 前 40 个 hex 字符按位 XOR
        val part3 = ByteArray(20)
        for (i in 0 until 20) {
            val byteVal = scrambleValues[i] xor hash.substring(i * 2, i * 2 + 2).toInt(16)
            part3[i] = byteVal.toByte()
        }
        // base64 编码并移除 \ / + =
        val b64Part = LxSdk.b64Encode(part3).replace("\\", "").replace("/", "").replace("+", "").replace("=", "")

        return "zzc${part1}${b64Part}${part2}".lowercase()
    }

    // ===================== 酷我 =====================

    /**
     * 酷我搜索（与 kw/musicSearch.js 完全一致）
     */
    suspend fun searchKw(str: String, page: Int = 1, limit: Int = 30): SearchResult {
        val url = "http://search.kuwo.cn/r.s?client=kt&all=${LxSdk.urlEncode(str)}&pn=${page - 1}&rn=$limit&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"

        val resp = LxSdk.httpFetch(url = url, method = "get")

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kw search response not JSON: ${resp.body}")
        // 与 JS 一致：result.TOTAL !== '0' && result.SHOW === '0' 时重试
        if (body.optString("TOTAL") != "0" && body.optString("SHOW") == "0") {
            throw RuntimeException("kw search SHOW=0")
        }

        val abslist = body.optJSONArray("abslist") ?: JSONArray()
        val list = mutableListOf<LxSdkSong>()
        for (i in 0 until abslist.length()) {
            val info = abslist.optJSONObject(i) ?: continue
            val parsed = parseKwSong(info) ?: continue
            list.add(parsed)
        }

        // 并行获取封面 URL（与 kw/pic.js getPic 一致）
        fetchKwCovers(list)

        val total = body.optString("TOTAL").toIntOrNull() ?: 0
        val allPage = if (limit > 0) (total + limit - 1) / limit else 1
        return SearchResult(list, allPage.coerceAtLeast(1), total, limit, "kw")
    }

    /**
     * 并行获取酷我歌曲封面 URL
     * 与 lx-music-mobile kw/pic.js 一致：
     *   http://artistpicserver.kuwo.cn/pic.web?corp=kuwo&type=rid_pic&pictype=500&size=500&rid=${songmid}
     * 响应体是图片 URL 文本
     */
    private suspend fun fetchKwCovers(list: MutableList<LxSdkSong>) {
        if (list.isEmpty()) return
        try {
            coroutineScope {
                list.mapIndexed { idx, song ->
                    async {
                        val url = "http://artistpicserver.kuwo.cn/pic.web?corp=kuwo&type=rid_pic&pictype=500&size=500&rid=${song.songmid}"
                        try {
                            val r = LxSdk.httpFetch(url, method = "get", timeoutMs = 5000L)
                            val body = r.body as? String ?: ""
                            // 与 JS /^http/.test(body) 一致：只有以 http 开头才使用
                            if (body.startsWith("http")) {
                                val cleanUrl = body.substringBefore("\n").substringBefore("\r").trim()
                                list[idx] = song.copy(img = cleanUrl)
                            }
                            Unit
                        } catch (e: Exception) {
                            Log.w(TAG, "kw pic fetch failed: ${song.songmid}: ${e.message}")
                        }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchKwCovers failed: ${e.message}")
        }
    }

    /**
     * 与 kw/musicSearch.js handleResult + regExps.mInfo 一致
     */
    private fun parseKwSong(info: JSONObject): LxSdkSong? {
        val musicRid = info.optString("MUSICRID")
        val songId = musicRid.replace("MUSIC_", "")

        val nMinfo = info.optString("N_MINFO")
        if (nMinfo.isBlank()) return null

        val types = mutableListOf<TypeItem>()
        val _types = mutableMapOf<String, TypeItem>()
        val mInfoRegex = Regex("""level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)""")
        nMinfo.split(";").forEach { seg ->
            val m = mInfoRegex.find(seg) ?: return@forEach
            val bitrate = m.groupValues[2]
            val size = m.groupValues[4]
            when (bitrate) {
                "4000" -> {
                    types.add(TypeItem("flac24bit", size))
                    _types["flac24bit"] = TypeItem("flac24bit", size.uppercase())
                }
                "2000" -> {
                    types.add(TypeItem("flac", size))
                    _types["flac"] = TypeItem("flac", size.uppercase())
                }
                "320" -> {
                    types.add(TypeItem("320k", size))
                    _types["320k"] = TypeItem("320k", size.uppercase())
                }
                "128" -> {
                    types.add(TypeItem("128k", size))
                    _types["128k"] = TypeItem("128k", size.uppercase())
                }
            }
        }
        types.reverse()

        val interval = info.optString("DURATION").toIntOrNull() ?: 0
        val artist = LxSdk.decodeName(info.optString("ARTIST")).replace("&", "、")
        return LxSdkSong(
            name = LxSdk.decodeName(info.optString("SONGNAME")),
            singer = artist,
            source = "kw",
            songmid = songId,
            albumId = LxSdk.decodeName(info.optString("ALBUMID")),
            interval = if (interval == 0) "--/--" else LxSdk.formatPlayTime(interval.toLong()),
            albumName = info.optString("ALBUM").let { LxSdk.decodeName(it) },
            img = "",
            types = types,
            _types = _types,
        )
    }

    // ===================== 酷狗 =====================

    /**
     * 酷狗搜索（与 kg/musicSearch.js 完全一致）
     */
    suspend fun searchKg(str: String, page: Int = 1, limit: Int = 30): SearchResult {
        val url = "https://songsearch.kugou.com/song_search_v2?keyword=${LxSdk.urlEncode(str)}&page=$page&pagesize=$limit&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1"

        val resp = LxSdk.httpFetch(url = url, method = "get")
        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kg search response not JSON: ${resp.body}")

        if (body.optInt("error_code") != 0) {
            throw RuntimeException("kg search failed: error_code=${body.optInt("error_code")}")
        }

        val dataObj = body.optJSONObject("data") ?: JSONObject()
        val lists = dataObj.optJSONArray("lists") ?: JSONArray()

        // 与 kg/musicSearch.js handleResult 去重逻辑一致
        val ids = mutableSetOf<String>()
        val list = mutableListOf<LxSdkSong>()
        for (i in 0 until lists.length()) {
            val item = lists.optJSONObject(i) ?: continue
            val key = item.optInt("Audioid").toString() + item.optString("FileHash")
            if (ids.contains(key)) continue
            ids.add(key)
            list.add(filterKgSong(item))
            val grp = item.optJSONArray("Grp")
            if (grp != null) {
                for (j in 0 until grp.length()) {
                    val child = grp.optJSONObject(j) ?: continue
                    val childKey = item.optInt("Audioid").toString() + item.optString("FileHash")
                    if (ids.contains(childKey)) continue
                    ids.add(childKey)
                    list.add(filterKgSong(child))
                }
            }
        }

        val total = dataObj.optInt("total", 0)
        val allPage = if (limit > 0) (total + limit - 1) / limit else 1

        // 并行获取封面 URL（与 kg/pic.js getPic 一致）
        fetchKgCovers(list)

        return SearchResult(list, allPage.coerceAtLeast(1), total, limit, "kg")
    }

    /**
     * 并行获取酷狗歌曲封面 URL
     * 与 lx-music-mobile kg/pic.js getPic 一致：
     *   POST http://media.store.kugou.com/v1/get_res_privilege
     *   返回 JSON 中 info.image 替换 {size} 占位符
     */
    private suspend fun fetchKgCovers(list: MutableList<LxSdkSong>) {
        if (list.isEmpty()) return
        try {
            coroutineScope {
                list.mapIndexed { idx, song ->
                    async {
                        val hash = song.ext["hash"] ?: return@async
                        val albumAudioId = song.songmid
                        val albumId = song.albumId
                        // 与 kg/pic.js 完全一致的请求体
                        val resource = JSONObject().apply {
                            put("album_audio_id", albumAudioId)
                            put("album_id", albumId)
                            put("hash", hash)
                            put("id", 0)
                            put("name", "${song.singer} - ${song.name}.mp3")
                            put("type", "audio")
                        }
                        val bodyObj = JSONObject().apply {
                            put("appid", 1001)
                            put("area_code", "1")
                            put("behavior", "play")
                            put("clientver", "9020")
                            put("need_hash_offset", 1)
                            put("relate", 1)
                            put("resource", JSONArray().put(resource))
                            put("token", "")
                            put("userid", 2626431536)
                            put("vip", 1)
                        }
                        try {
                            val r = LxSdk.httpFetch(
                                url = "http://media.store.kugou.com/v1/get_res_privilege",
                                method = "post",
                                headers = mapOf(
                                    "KG-RC" to "1",
                                    "KG-THash" to "expand_search_manager.cpp:852736169:451",
                                    "User-Agent" to "KuGou2012-9020-ExpandSearchManager",
                                ),
                                body = bodyObj,
                                timeoutMs = 5000L,
                            )
                            val respBody = r.body as? JSONObject ?: return@async
                            if (respBody.optInt("error_code") != 0) return@async
                            val dataArray = respBody.optJSONArray("data") ?: return@async
                            val dataItem = dataArray.optJSONObject(0) ?: return@async
                            val info = dataItem.optJSONObject("info") ?: return@async
                            val imgSizes = info.optJSONArray("imgsize")
                            val rawImg = info.optString("image")
                            if (rawImg.isBlank()) return@async
                            // 与 JS 一致：info.imgsize ? info.image.replace('{size}', info.imgsize[0]) : info.image
                            val finalImg = if (imgSizes != null && imgSizes.length() > 0) {
                                rawImg.replace("{size}", imgSizes.optString(0))
                            } else rawImg
                            if (finalImg.isNotBlank()) {
                                list[idx] = song.copy(img = finalImg)
                            }
                            Unit
                        } catch (e: Exception) {
                            Log.w(TAG, "kg pic fetch failed: ${song.songmid}: ${e.message}")
                        }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchKgCovers failed: ${e.message}")
        }
    }

    /**
     * 与 kg/musicSearch.js filterData 一致
     */
    private fun filterKgSong(rawData: JSONObject): LxSdkSong {
        val types = mutableListOf<TypeItem>()
        val _types = mutableMapOf<String, TypeItem>()

        if (rawData.optLong("FileSize") != 0L) {
            val size = LxSdk.sizeFormate(rawData.optLong("FileSize"))
            types.add(TypeItem("128k", size, rawData.optString("FileHash")))
            _types["128k"] = TypeItem("128k", size, rawData.optString("FileHash"))
        }
        if (rawData.optLong("HQFileSize") != 0L) {
            val size = LxSdk.sizeFormate(rawData.optLong("HQFileSize"))
            types.add(TypeItem("320k", size, rawData.optString("HQFileHash")))
            _types["320k"] = TypeItem("320k", size, rawData.optString("HQFileHash"))
        }
        if (rawData.optLong("SQFileSize") != 0L) {
            val size = LxSdk.sizeFormate(rawData.optLong("SQFileSize"))
            types.add(TypeItem("flac", size, rawData.optString("SQFileHash")))
            _types["flac"] = TypeItem("flac", size, rawData.optString("SQFileHash"))
        }
        if (rawData.optLong("ResFileSize") != 0L) {
            val size = LxSdk.sizeFormate(rawData.optLong("ResFileSize"))
            types.add(TypeItem("flac24bit", size, rawData.optString("ResFileHash")))
            _types["flac24bit"] = TypeItem("flac24bit", size, rawData.optString("ResFileHash"))
        }

        val singers = rawData.optJSONArray("Singers") ?: JSONArray()
        return LxSdkSong(
            singer = LxSdk.decodeName(LxSdk.formatSingerName(singers)),
            name = LxSdk.decodeName(rawData.optString("SongName")),
            albumName = LxSdk.decodeName(rawData.optString("AlbumName")),
            albumId = rawData.optString("AlbumID"),
            songmid = rawData.optInt("Audioid").toString(),
            source = "kg",
            interval = LxSdk.formatPlayTime(rawData.optLong("Duration")),
            img = "",
            types = types,
            _types = _types,
            ext = mapOf("hash" to rawData.optString("FileHash")),
        )
    }

    // ===================== 咪咕 =====================

    /**
     * 咪咕搜索（与 mg/musicSearch.js 完全一致）
     */
    suspend fun searchMg(str: String, page: Int = 1, limit: Int = 20): SearchResult {
        val time = System.currentTimeMillis().toString()
        val signData = createMgSignature(time, str)
        val url = "https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=0&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize=$limit&text=${LxSdk.urlEncode(str)}&pageNo=$page&sort=0&sid=USS"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf(
                "uiVersion" to "A_music_3.6.1",
                "deviceId" to signData.deviceId,
                "timestamp" to time,
                "sign" to signData.sign,
                "channel" to "0146921",
                "User-Agent" to "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30",
            ),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("mg search response not JSON: ${resp.body}")

        if (body.optString("code") != "000000") {
            throw RuntimeException("mg search failed: ${body.optString("info")}")
        }

        val songResultData = body.optJSONObject("songResultData")
            ?: JSONObject().apply { put("resultList", JSONArray()); put("totalCount", 0) }
        val resultList = songResultData.optJSONArray("resultList") ?: JSONArray()
        val list = filterMgData(resultList)

        val total = songResultData.optString("totalCount").toIntOrNull() ?: 0
        val allPage = if (limit > 0) (total + limit - 1) / limit else 1
        return SearchResult(list, allPage.coerceAtLeast(1), total, limit, "mg")
    }

    /**
     * 与 mg/musicSearch.js createSignature 一致
     */
    private data class MgSign(val sign: String, val deviceId: String)

    private fun createMgSignature(time: String, str: String): MgSign {
        val deviceId = "963B7AA0D21511ED807EE5846EC87D20"
        val signatureMd5 = "6cdc72a439cef99a3418d2a78aa28c73"
        val sign = LxSdk.md5("${str}${signatureMd5}yyapp2d16148780a1dcc7408e06336b98cfd50${deviceId}${time}")
        return MgSign(sign, deviceId)
    }

    /**
     * 与 mg/musicSearch.js filterData 一致
     */
    private fun filterMgData(rawData: JSONArray): List<LxSdkSong> {
        val list = mutableListOf<LxSdkSong>()
        val ids = mutableSetOf<String>()
        // 与 JS 一致：rawData.forEach(item => item.forEach(data => {...}))
        for (i in 0 until rawData.length()) {
            val inner = rawData.optJSONArray(i) ?: continue
            for (j in 0 until inner.length()) {
                val data = inner.optJSONObject(j) ?: continue
                val songId = data.optString("songId")
                val copyrightId = data.optString("copyrightId")
                if (songId.isBlank() || copyrightId.isBlank() || ids.contains(copyrightId)) continue
                ids.add(copyrightId)

                val types = mutableListOf<TypeItem>()
                val _types = mutableMapOf<String, TypeItem>()
                val audioFormats = data.optJSONArray("audioFormats")
                if (audioFormats != null) {
                    for (k in 0 until audioFormats.length()) {
                        val type = audioFormats.optJSONObject(k) ?: continue
                        val formatType = type.optString("formatType")
                        // asize 优先，isize 兜底
                        val size = type.optLong("asize").let { if (it != 0L) it else type.optLong("isize") }
                        val sizeStr = if (size != 0L) LxSdk.sizeFormate(size) else null
                        when (formatType) {
                            "PQ" -> {
                                types.add(TypeItem("128k", sizeStr))
                                _types["128k"] = TypeItem("128k", sizeStr)
                            }
                            "HQ" -> {
                                types.add(TypeItem("320k", sizeStr))
                                _types["320k"] = TypeItem("320k", sizeStr)
                            }
                            "SQ" -> {
                                types.add(TypeItem("flac", sizeStr))
                                _types["flac"] = TypeItem("flac", sizeStr)
                            }
                            "ZQ24" -> {
                                types.add(TypeItem("flac24bit", sizeStr))
                                _types["flac24bit"] = TypeItem("flac24bit", sizeStr)
                            }
                        }
                    }
                }

                var img = data.optString("img3").ifBlank { data.optString("img2") }.ifBlank { data.optString("img1") }
                if (img.isNotBlank() && !img.startsWith("http")) {
                    img = "http://d.musicapp.migu.cn$img"
                }

                list.add(LxSdkSong(
                    singer = LxSdk.formatSingerName(data.opt("singerList")),
                    name = data.optString("name"),
                    albumName = data.optString("album"),
                    albumId = data.optString("albumId"),
                    songmid = songId,
                    source = "mg",
                    interval = LxSdk.formatPlayTime(data.optLong("duration")),
                    img = img,
                    types = types,
                    _types = _types,
                    ext = mapOf(
                        "copyrightId" to copyrightId,
                        "lrcUrl" to data.optString("lrcUrl"),
                        "mrcUrl" to data.optString("mrcurl"),
                        "trcUrl" to data.optString("trcUrl"),
                    ),
                ))
            }
        }
        return list
    }

    /**
     * 调试日志
     */
    fun log(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Exception) {}
    }
}
