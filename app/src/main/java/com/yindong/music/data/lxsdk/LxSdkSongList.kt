package com.yindong.music.data.lxsdk

import android.util.Log
import com.yindong.music.data.api.RecommendPlaylist
import com.yindong.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * lx-music-mobile 4 平台歌单广场实现
 *
 * 完全按照以下文件移植：
 * - src/utils/musicSdk/wy/songList.js（eapi 加密请求 /api/playlist/list）
 * - src/utils/musicSdk/tx/songList.js（musicu.fcg get_playlist_by_tag）
 * - src/utils/musicSdk/kw/songList.js（wapi.kuwo.cn getRcmPlayList）
 * - src/utils/musicSdk/kg/songList.js（www2.kugou.kugou.com getSpecial）
 *
 * 每个平台的歌单列表统一为 [RecommendPlaylist] 列表，[fetchAllFeatured] 并行合并 4 平台推荐。
 */
object LxSdkSongList {

    private const val TAG = "LxSdkSongList"

    /** 歌单排序/分类标签 */
    data class SongListTag(
        val name: String,
        val id: String,
        val tid: String,
    )

    /** 歌单广场获取结果 */
    data class SongListResult(
        val source: String,        // "wy", "tx", "kw", "kg"
        val list: List<RecommendPlaylist>,
        val total: Int = 0,
        val page: Int = 1,
    )

    // ===================== 排序选项（硬编码，与原版 sortList 一致） =====================

    private val wySortList: List<SongListTag> = listOf(
        SongListTag("最热", "hot", "hot"),
    )

    private val txSortList: List<SongListTag> = listOf(
        SongListTag("最热", "5", "hot"),
        SongListTag("最新", "2", "new"),
    )

    private val kwSortList: List<SongListTag> = listOf(
        SongListTag("最新", "new", "new"),
        SongListTag("最热", "hot", "hot"),
    )

    private val kgSortList: List<SongListTag> = listOf(
        SongListTag("推荐", "5", "recommend"),
        SongListTag("最热", "6", "hot"),
        SongListTag("最新", "7", "new"),
    )

    /**
     * 返回指定平台的排序选项
     * @param source 平台 id：wy/tx/kw/kg
     */
    fun getSortList(source: String): List<SongListTag> = when (source) {
        "wy" -> wySortList
        "tx" -> txSortList
        "kw" -> kwSortList
        "kg" -> kgSortList
        else -> emptyList()
    }

    /**
     * 获取歌单列表（按 source 分发到具体平台实现）
     * @param source 平台 id：wy/tx/kw/kg
     * @param sortId 排序 id（来自 [getSortList] 的 [SongListTag.id]）
     * @param tagId 分类标签 id（空表示全部）
     * @param page 页码（从 1 开始）
     * @param limit 每页数量
     * @return [SongListResult]，失败时 list 为空
     */
    suspend fun getList(
        source: String,
        sortId: String,
        tagId: String = "",
        page: Int = 1,
        limit: Int = 30,
    ): SongListResult = withContext(Dispatchers.IO) {
        try {
            when (source) {
                "wy" -> getListWy(sortId, tagId, page, limit)
                "tx" -> getListTx(sortId, tagId, page, limit)
                "kw" -> getListKw(sortId, tagId, page, limit)
                "kg" -> getListKg(sortId, tagId, page, limit)
                else -> {
                    Log.w(TAG, "unknown source: $source")
                    SongListResult(source, emptyList(), 0, page)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getList[$source] failed: sortId=$sortId — ${e.message}", e)
            SongListResult(source, emptyList(), 0, page)
        }
    }

    // ===================== 网易云 =====================

    /**
     * 网易云歌单广场（与 wy/songList.js getList 一致）
     * eapi 加密 POST /api/playlist/list，参数 {cat, order, limit, offset, total}
     */
    private suspend fun getListWy(sortId: String, tagId: String, page: Int, limit: Int): SongListResult {
        val url = "/api/playlist/list"
        val data = JSONObject().apply {
            put("cat", if (tagId.isBlank()) "全部" else tagId)
            put("order", sortId)
            put("limit", limit)
            put("offset", limit * (page - 1))
            put("total", true)
        }

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
            ?: throw RuntimeException("wy songList response not JSON: ${resp.body}")
        if (body.optInt("code") != 200) {
            throw RuntimeException("wy songList failed: code=${body.optInt("code")}")
        }

        val playlists = body.optJSONArray("playlists") ?: JSONArray()
        val total = body.optInt("total", playlists.length())
        val list = mutableListOf<RecommendPlaylist>()
        for (i in 0 until playlists.length()) {
            val item = playlists.optJSONObject(i) ?: continue
            val parsed = parseWyPlaylist(item) ?: continue
            list.add(parsed)
        }
        return SongListResult("wy", list, total, page)
    }

    /**
     * 与 wy/songList.js filterList 一致
     * 字段：id, name, coverImgUrl, creator.nickname, playCount, trackCount
     */
    private fun parseWyPlaylist(item: JSONObject): RecommendPlaylist? {
        val id = item.optString("id")
        if (id.isBlank() || id == "0") return null
        return RecommendPlaylist(
            name = LxSdk.decodeName(item.optString("name")),
            coverUrl = item.optString("coverImgUrl"),
            playCount = item.optLong("playCount"),
            platform = "网易云",
            playlistId = id,
        )
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
     * QQ 音乐歌单广场（与 tx/songList.js getList 一致）
     * POST https://u.y.qq.com/cgi-bin/musicu.fcg get_playlist_by_tag
     */
    private suspend fun getListTx(sortId: String, tagId: String, page: Int, limit: Int): SongListResult {
        val order = sortId.toIntOrNull() ?: 5
        val data = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("ct", "24")
                put("cv", "1803")
                put("guid", "0")
                put("patch", "118")
                put("tmeAppID", "qqmusic")
                put("uin", "0")
            })
            put("playlist", JSONObject().apply {
                put("module", "playlist.PlayListPlazaServer")
                put("method", "get_playlist_by_tag")
                put("param", JSONObject().apply {
                    put("id", 10000000)
                    put("sin", limit * (page - 1))
                    put("size", limit)
                    put("order", order)
                    put("cur_page", page)
                })
            })
        }

        val resp = LxSdk.httpFetch(
            url = "https://u.y.qq.com/cgi-bin/musicu.fcg",
            method = "post",
            headers = mapOf("User-Agent" to "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)"),
            body = data,
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("tx songList response not JSON: ${resp.body}")
        if (body.optInt("code") != 0) {
            throw RuntimeException("tx songList failed: code=${body.optInt("code")}")
        }

        // body.playlist.data.v_playlist[]
        val vPlaylist = body.optJSONObject("playlist")
            ?.optJSONObject("data")
            ?.optJSONArray("v_playlist")
            ?: JSONArray()
        val total = body.optJSONObject("playlist")
            ?.optJSONObject("data")
            ?.optInt("total", vPlaylist.length())
            ?: vPlaylist.length()

        // 诊断日志：打印第一个 v_playlist 元素的完整结构（用于确认 tid 等字段）
        if (vPlaylist.length() > 0) {
            val firstItem = vPlaylist.optJSONObject(0)
            Log.w(TAG, "getListTx: first v_playlist item=${firstItem}")
        }

        val list = mutableListOf<RecommendPlaylist>()
        for (i in 0 until vPlaylist.length()) {
            val item = vPlaylist.optJSONObject(i) ?: continue
            val parsed = parseTxPlaylist(item) ?: continue
            list.add(parsed)
        }
        return SongListResult("tx", list, total, page)
    }

    /**
     * 解析 QQ 音乐 v_playlist 元素为 [RecommendPlaylist]
     * 与原版 tx/songList.js filterList 一致，只用 tid 作为歌单 id
     * （dirinfo_1.id / id 是目录/分类 id，不是有效 disstid，不能用作歌单详情查询）
     */
    private fun parseTxPlaylist(item: JSONObject): RecommendPlaylist? {
        // 只用 tid（有效 disstid）；dissid 作为兼容备选
        var id = item.optString("tid")
        if (id.isBlank()) id = item.optString("dissid")
        if (id.isBlank()) return null

        val name = item.optString("title").ifBlank { item.optString("name") }

        // 封面：cover_url_medium → picurl → cover_url → imgurl
        var coverUrl = item.optString("cover_url_medium")
        if (coverUrl.isBlank()) coverUrl = item.optString("picurl")
        if (coverUrl.isBlank()) coverUrl = item.optString("cover_url")
        if (coverUrl.isBlank()) coverUrl = item.optString("imgurl")

        // 播放量：access_num → listennum → play_count
        var playCount = item.optLong("access_num")
        if (playCount == 0L) playCount = item.optLong("listennum")
        if (playCount == 0L) playCount = item.optLong("play_count")

        return RecommendPlaylist(
            name = LxSdk.decodeName(name),
            coverUrl = coverUrl,
            playCount = playCount,
            platform = "QQ音乐",
            playlistId = id,
        )
    }

    // ===================== 酷我 =====================

    /**
     * 酷我歌单广场（与 kw/songList.js getList 一致）
     * GET http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList
     */
    private suspend fun getListKw(sortId: String, tagId: String, page: Int, limit: Int): SongListResult {
        val url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList" +
            "?loginUid=0&loginSid=0&app=pc&client=kt&pn=${page}&rn=${limit}" +
            "&order=${sortId}&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1" +
            "&newver=1&platform=pc&pagesize=${limit}"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf("User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 9;)"),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kw songList response not JSON: ${resp.body}")
        if (body.optInt("code") != 200) {
            throw RuntimeException("kw songList failed: code=${body.optInt("code")}")
        }

        val dataObj = body.optJSONObject("data") ?: JSONObject()
        // 兼容 data.data（原版）与 data.list 两种字段
        val listArr = dataObj.optJSONArray("data")
            ?: dataObj.optJSONArray("list")
            ?: JSONArray()
        val total = dataObj.optInt("total", listArr.length())

        val list = mutableListOf<RecommendPlaylist>()
        for (i in 0 until listArr.length()) {
            val item = listArr.optJSONObject(i) ?: continue
            val parsed = parseKwPlaylist(item) ?: continue
            list.add(parsed)
        }
        return SongListResult("kw", list, total, page)
    }

    /**
     * 解析酷我歌单元素为 [RecommendPlaylist]
     * 字段：id, name, img, uname, listencnt, songnum
     */
    private fun parseKwPlaylist(item: JSONObject): RecommendPlaylist? {
        val id = item.optString("id")
        if (id.isBlank()) return null
        return RecommendPlaylist(
            name = LxSdk.decodeName(item.optString("name")),
            coverUrl = item.optString("img"),
            playCount = item.optLong("listencnt"),
            platform = "酷我",
            playlistId = id,
        )
    }

    // ===================== 酷狗 =====================

    /**
     * 酷狗歌单广场
     * 原版 getSpecial API 已不再返回歌单列表（仅返回标签数据），
     * 改用 getSongListRecommend（guess_special_recommend）获取推荐歌单列表。
     * 参考 lx-music-desktop kg/songList.js getSongListRecommend + filterList
     */
    private suspend fun getListKg(sortId: String, tagId: String, page: Int, limit: Int): SongListResult {
        // 先尝试原版 getSpecial API（若恢复可用）
        val url = "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial" +
            "?is_smarty=1&is_ajax=1&cdn=cdn&t=${sortId}&c=${tagId}&p=${page}"

        val resp = LxSdk.httpFetch(url = url, method = "get")
        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kg songList response not JSON: ${resp.body}")
        if (body.optInt("status") != 1) {
            throw RuntimeException("kg songList failed: status=${body.optInt("status")}")
        }

        // 兼容 special_db（原版）与 specialList 两种字段
        val specialArr = body.optJSONArray("special_db")
            ?: body.optJSONArray("specialList")
            ?: JSONArray()

        // special_db 为空时回退到 getSongListRecommend API
        if (specialArr.length() == 0) {
            Log.d(TAG, "getListKg: special_db empty, fallback to getSongListRecommend")
            return getKgRecommendList(page)
        }

        val total = body.optInt("total", specialArr.length())
        val list = mutableListOf<RecommendPlaylist>()
        for (i in 0 until specialArr.length()) {
            val item = specialArr.optJSONObject(i) ?: continue
            val parsed = parseKgPlaylist(item) ?: continue
            list.add(parsed)
        }
        return SongListResult("kg", list, total, page)
    }

    /**
     * 酷狗推荐歌单（与 kg/songList.js getSongListRecommend 一致）
     * POST http://everydayrec.service.kugou.com/guess_special_recommend
     * 返回 body.data.special_list，用 filterList 解析
     */
    private suspend fun getKgRecommendList(page: Int): SongListResult {
        val reqBody = JSONObject().apply {
            put("appid", 1001)
            put("clienttime", 1566798337219)
            put("clientver", 8275)
            put("key", "f1f93580115bb106680d2375f8032d96")
            put("mid", "21511157a05844bd085308bc76ef3343")
            put("platform", "pc")
            put("userid", "262643156")
            put("return_min", 6)
            put("return_max", 15)
        }

        val resp = LxSdk.httpFetch(
            url = "http://everydayrec.service.kugou.com/guess_special_recommend",
            method = "post",
            headers = mapOf("User-Agent" to "KuGou2012-8275-web_browser_event_handler"),
            body = reqBody,
        )
        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kg recommend response not JSON: ${resp.body}")
        if (body.optInt("status") != 1) {
            throw RuntimeException("kg recommend failed: status=${body.optInt("status")}")
        }

        // body.data.special_list
        val specialList = body.optJSONObject("data")
            ?.optJSONArray("special_list")
            ?: JSONArray()
        val total = specialList.length()

        val list = mutableListOf<RecommendPlaylist>()
        for (i in 0 until specialList.length()) {
            val item = specialList.optJSONObject(i) ?: continue
            val parsed = parseKgPlaylist(item) ?: continue
            list.add(parsed)
        }
        Log.d(TAG, "getKgRecommendList: parsed ${list.size} playlists (total=$total)")
        return SongListResult("kg", list, total, page)
    }

    /**
     * 解析酷狗歌单元素为 [RecommendPlaylist]
     * 字段：specialid, specialname, img, username, total_play_count, songcount
     */
    private fun parseKgPlaylist(item: JSONObject): RecommendPlaylist? {
        val id = item.optString("specialid")
        if (id.isBlank()) return null
        // 封面：img → imgurl（imgurl 含 {size} 占位符需替换）
        var coverUrl = item.optString("img")
        if (coverUrl.isBlank()) {
            coverUrl = item.optString("imgurl").replace("{size}", "150")
        }
        // 播放量：total_play_count → play_count
        var playCount = item.optLong("total_play_count")
        if (playCount == 0L) playCount = item.optLong("play_count")
        return RecommendPlaylist(
            name = LxSdk.decodeName(item.optString("specialname")),
            coverUrl = coverUrl,
            playCount = playCount,
            platform = "酷狗",
            playlistId = id,
        )
    }

    // ===================== 合并 =====================

    /**
     * 并行获取 4 平台推荐歌单并合并。
     * 各平台使用默认排序第 1 页：wy=最热, tx=最热, kw=最新, kg=推荐。
     * 单个平台失败不影响其他平台（runCatching 隔离异常）。
     */
    suspend fun fetchAllFeatured(): List<RecommendPlaylist> = withContext(Dispatchers.IO) {
        val sources = listOf("wy", "tx", "kw", "kg")
        val results = coroutineScope {
            listOf(
                async { runCatching { getListWy("hot", "", 1, 30) }.getOrNull() },
                async { runCatching { getListTx("5", "", 1, 30) }.getOrNull() },
                async { runCatching { getListKw("new", "", 1, 30) }.getOrNull() },
                async { runCatching { getListKg("5", "", 1, 30) }.getOrNull() },
            ).awaitAll()
        }

        // 记录每个平台的结果（成功返回数 / 失败原因）
        results.forEachIndexed { idx, r ->
            val src = sources[idx]
            if (r == null) {
                Log.w(TAG, "fetchAllFeatured[$src]: 失败（异常）")
            } else {
                Log.d(TAG, "fetchAllFeatured[$src]: 成功 ${r.list.size} 个歌单 (total=${r.total})")
            }
        }

        val failedSources = results.mapIndexedNotNull { idx, r ->
            if (r == null) sources[idx] else null
        }
        if (failedSources.isNotEmpty()) {
            Log.w(TAG, "fetchAllFeatured partial failure: $failedSources")
        }

        results.filterNotNull().flatMap { it.list }
    }

    // ===================== 歌单详情（外部歌单导入） =====================
    // 移植自 lx-music-mobile src/utils/musicSdk/{wy,tx,kw,kg}/songList.js 的 getListDetail 方法
    // 支持 URL 作为 id 输入（与原版 getListId 解析逻辑一致）

    /** 歌单详情信息 */
    data class PlaylistInfo(
        val name: String = "",
        val img: String = "",
        val desc: String = "",
        val author: String = "",
        val playCount: String = "",
    )

    /** 歌单详情获取结果 */
    data class SongListDetailResult(
        val source: String,            // "wy", "tx", "kw", "kg"
        val list: List<Song>,
        val total: Int = 0,
        val page: Int = 1,
        val info: PlaylistInfo = PlaylistInfo(),
    )

    // URL → id 正则（与 lx-music-mobile 各 songList.js regExps 一致）
    private val wyListDetailLinkRegex = Regex("^.+(?:\\?|&)id=(\\d+)(?:&.*$|#.*$|$)")
    private val wyListDetailLink2Regex = Regex("^.+/playlist/(\\d+)/\\d+/.+$")
    // QQ 音乐：playlist 路径 / playsquare 路径 / id= 查询参数
    private val txListDetailLinkRegex = Regex("/playlist/(\\d+)")
    private val txListDetailLink2Regex = Regex("id=(\\d+)")
    private val txListDetailLink3Regex = Regex("/playsquare/(\\d+)")
    // 酷我：playlist_detail 路径 / playlists 路径 / playlistId= 查询参数
    private val kwListDetailLinkRegex = Regex("/playlists?(?:_detail)?/(\\d+)")
    private val kwListDetailLink2Regex = Regex("playlistId=(\\d+)")
    // 酷狗：xxx.html 路径 / special/single/ 路径
    private val kgListDetailLinkRegex = Regex("/(\\d+)\\.html(?:\\?.*|&.*$|#.*$|$)")
    private val kgListDetailLink2Regex = Regex("/special/(?:single/)?(\\d+)")
    // 酷狗分享链接：gcid_xxx 形式（与 kg/songList.js getUserListDetail 一致）
    private val kgGcidRegex = Regex("gcid_(\\w+)")
    // 酷狗 HTML 内嵌 global_collection_id（与 kg/songList.js getUserListDetail 解析 body 一致）
    private val kgGlobalCollectionIdRegex = Regex("\"global_collection_id\"\\s*:\\s*\"(\\w+)\"")
    private val kgEncodeGicRegex = Regex("\"encode_gic\"\\s*:\\s*\"(\\w+)\"")
    private val kgEncodeSrcGidRegex = Regex("\"encode_src_gid\"\\s*:\\s*\"(\\w+)\"")

    // kg HTML 数据正则（与 kg/songList.js regExps 一致）
    private val kgListDataRegex = Regex("global\\.data = (\\[.+\\]);", RegexOption.DOT_MATCHES_ALL)
    private val kgListInfoRegex = Regex("global = \\{[\\s\\S]+?name: \"(.+?)\"[\\s\\S]+?pic: \"(.+?)\"[\\s\\S]+?\\};")

    /**
     * 获取歌单详情（移植自 lx-music-mobile songList.getListDetail）
     * @param source 平台 id：wy/tx/kw/kg
     * @param idOrUrl 歌单 id 或歌单 URL
     * @param page 页码（从 1 开始）
     * @return [SongListDetailResult]，失败时 list 为空
     */
    suspend fun getListDetail(
        source: String,
        idOrUrl: String,
        page: Int = 1,
    ): SongListDetailResult = withContext(Dispatchers.IO) {
        try {
            when (source) {
                "wy" -> getListDetailWy(idOrUrl, page)
                "tx" -> getListDetailTx(idOrUrl, page)
                "kw" -> getListDetailKw(idOrUrl, page)
                "kg" -> getListDetailKg(idOrUrl, page)
                else -> {
                    Log.w(TAG, "getListDetail: unknown source: $source")
                    SongListDetailResult(source, emptyList(), 0, page)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getListDetail[$source] failed: id=$idOrUrl — ${e.message}", e)
            SongListDetailResult(source, emptyList(), 0, page)
        }
    }

    // ===================== 网易云歌单详情 =====================

    /**
     * 网易云歌单详情（与 wy/songList.js getListDetail 一致）
     * linuxapi 加密 POST /api/linux/forward，参数 {method, url, params: {id, n, s}}
     */
    private suspend fun getListDetailWy(rawId: String, page: Int): SongListDetailResult {
        val id = getWyListId(rawId) ?: return SongListDetailResult("wy", emptyList(), 0, page)

        val params = JSONObject().apply {
            put("method", "POST")
            put("url", "https://music.163.com/api/v3/playlist/detail")
            put("params", JSONObject().apply {
                put("id", id)
                put("n", 100000)
                put("s", 8)
            })
        }
        val eparams = linuxapiEncrypt(params)

        val resp = LxSdk.httpFetch(
            url = "https://music.163.com/api/linux/forward",
            method = "post",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
                "Cookie" to "MUSIC_U=",
            ),
            form = mapOf("eparams" to eparams),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("wy getListDetail response not JSON: ${resp.body}")
        if (body.optInt("code") != 200) {
            throw RuntimeException("wy getListDetail failed: code=${body.optInt("code")}")
        }

        val playlist = body.optJSONObject("playlist")
            ?: return SongListDetailResult("wy", emptyList(), 0, page)

        val trackIds = playlist.optJSONArray("trackIds") ?: JSONArray()
        val tracks = playlist.optJSONArray("tracks") ?: JSONArray()
        val total = trackIds.length()

        // 网易云 API /api/v3/playlist/detail 返回的 tracks 通常只有前 1000 首，
        // 但 trackIds 包含全部歌曲 ID。
        // 策略：
        // 1. 先解析 API 已返回的 tracks（最多约1000首）
        // 2. 收集 tracks 中已有的歌曲 ID
        // 3. 对 trackIds 中尚未获取详情的 ID，分批调用 /weapi/v3/song/detail 补全
        Log.d(TAG, "getListDetailWy: trackIds=${total}, tracks=${tracks.length()}")

        val list = mutableListOf<Song>()
        val fetchedIds = mutableSetOf<String>()

        // 1. 解析已有的 tracks
        for (i in 0 until tracks.length()) {
            val track = tracks.optJSONObject(i) ?: continue
            parseWyTrackDetail(track)?.let {
                list.add(it)
                fetchedIds.add(it.platformId)
            }
        }

        // 2. 收集尚未获取详情的 trackIds
        val remainingIds = mutableListOf<String>()
        for (i in 0 until total) {
            val tid = trackIds.optJSONObject(i)?.optString("id") ?: continue
            if (tid.isNotBlank() && !fetchedIds.contains(tid)) {
                remainingIds.add(tid)
            }
        }

        Log.d(TAG, "getListDetailWy: already fetched=${fetchedIds.size}, remaining=${remainingIds.size}")

        // 3. 分批获取剩余歌曲详情（每批最多 1000 首）
        if (remainingIds.isNotEmpty()) {
            val batchSize = 1000
            var processed = 0
            while (processed < remainingIds.size) {
                val end = minOf(processed + batchSize, remainingIds.size)
                val batch = remainingIds.subList(processed, end)
                Log.d(TAG, "getListDetailWy: fetching batch ${processed / batchSize + 1}/${(remainingIds.size + batchSize - 1) / batchSize} (${batch.size} ids)")
                val batchResult = fetchWyMusicDetailList(batch)
                list.addAll(batchResult)
                processed = end
            }
        }

        val info = PlaylistInfo(
            name = playlist.optString("name"),
            img = playlist.optString("coverImgUrl"),
            desc = playlist.optString("description"),
            author = playlist.optJSONObject("creator")?.optString("nickname").orEmpty(),
            playCount = playlist.optLong("playCount").toString(),
        )
        return SongListDetailResult("wy", list, total, page, info)
    }

    /**
     * 与 wy/musicDetail.js getList 一致
     * 通过 /weapi/v3/song/detail 批量获取歌曲详情（weapi 加密）
     * 用于 getListDetailWy 的回退路径：当 playlist.tracks 不完整时获取所有歌曲
     * @param ids 歌曲 id 列表（字符串形式）
     * @return 解析后的 [Song] 列表
     */
    private suspend fun fetchWyMusicDetailList(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()

        // 与原版一致：c = '[{"id":123},{"id":456}]'，ids = '[123,456]'
        val cStr = ids.joinToString(",", "[", "]") { "{\"id\":$it}" }
        val idsStr = ids.joinToString(",", "[", "]")

        val weapiParams = weapiEncrypt(JSONObject().apply {
            put("c", cStr)
            put("ids", idsStr)
        })

        val resp = LxSdk.httpFetch(
            url = "https://music.163.com/weapi/v3/song/detail",
            method = "post",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
                "Origin" to "https://music.163.com",
                "Referer" to "https://music.163.com",
            ),
            form = weapiParams,
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("wy musicDetail response not JSON: ${resp.body}")
        if (body.optInt("code") != 200) {
            throw RuntimeException("wy musicDetail failed: code=${body.optInt("code")}")
        }

        val songs = body.optJSONArray("songs") ?: JSONArray()
        val list = mutableListOf<Song>()
        for (i in 0 until songs.length()) {
            val track = songs.optJSONObject(i) ?: continue
            parseWyTrackDetail(track)?.let { list.add(it) }
        }
        Log.d(TAG, "fetchWyMusicDetailList: requested=${ids.size}, parsed=${list.size}")
        return list
    }

    /**
     * 与 wy/utils/crypto.js weapi 一致
     * weapi 加密：两轮 AES-CBC-128-PKCS7Padding + RSA NoPadding
     * @param obj 待加密的 JSON 对象
     * @return 表单数据 map（params + encSecKey）
     */
    private fun weapiEncrypt(obj: JSONObject): Map<String, String> {
        val text = obj.toString()
        val presetKey = LxSdk.b64EncodeStr("0CoJUm6Qyw8W8jud")
        val iv = LxSdk.b64EncodeStr("0102030405060708")

        // 生成随机 secretKey（16 字符，与 JS String(Math.random()).substring(2, 18) 一致）
        val secretKey = buildString {
            repeat(16) {
                append(('0'..'9').random())
            }
        }

        // 第一轮 AES-CBC-128-PKCS7Padding with presetKey
        val textB64 = LxSdk.b64EncodeStr(text)
        val firstEncrypted = LxSdk.aesEncrypt(textB64, presetKey, iv, "CBC_128_PKCS7Padding")

        // 第二轮 AES-CBC-128-PKCS7Padding with secretKey
        val firstEncryptedB64 = LxSdk.b64EncodeStr(firstEncrypted)
        val secretKeyB64 = LxSdk.b64EncodeStr(secretKey)
        val params = LxSdk.aesEncrypt(firstEncryptedB64, secretKeyB64, iv, "CBC_128_PKCS7Padding")

        // RSA NoPadding 加密 reversed secretKey（与 JS rsaEncrypt(Buffer.from(secretKey).reverse(), publicKey) 一致）
        val publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
        val reversedSecretKeyBytes = secretKey.reversed().toByteArray(StandardCharsets.UTF_8)
        val reversedSecretKeyB64 = LxSdk.b64Encode(reversedSecretKeyBytes)
        val encSecKeyB64 = LxSdk.rsaEncrypt(reversedSecretKeyB64, publicKey, "NoPadding")
        // base64 → hex（与 JS .toString('hex') 一致）
        val encSecKeyBytes = LxSdk.b64Decode(encSecKeyB64)
        val sb = StringBuilder()
        for (b in encSecKeyBytes) sb.append(String.format("%02x", b))

        return mapOf("params" to params, "encSecKey" to sb.toString())
    }

    /**
     * 与 wy/songList.js filterListDetail 一致
     * track 字段：name, ar(artists 数组), al(album 对象), dt(duration 毫秒), id
     */
    private fun parseWyTrackDetail(track: JSONObject): Song? {
        val id = track.optLong("id")
        if (id == 0L) return null
        val name = LxSdk.decodeName(track.optString("name"))
        val ar = track.optJSONArray("ar") ?: JSONArray()
        val al = track.optJSONObject("al") ?: JSONObject()
        val duration = track.optLong("dt")
        val img = al.optString("picUrl")
        // 与 LxSdkSearch.sdkSongToSong 一致：保留 songmid 便于 LxSdkMusicUrl 解析播放
        val rawJson = JSONObject().apply {
            put("songmid", id.toString())
            put("name", name)
            put("singer", LxSdk.formatSingerName(ar))
            put("source", "wy")
            put("interval", LxSdk.formatPlayTime(duration / 1000))
        }.toString()

        return Song(
            id = id,
            title = name,
            artist = LxSdk.formatSingerName(ar),
            album = LxSdk.decodeName(al.optString("name")),
            duration = duration,
            coverUrl = img,
            platform = "网易云",
            platformId = id.toString(),
            lxSourceKey = "wy",
            pluginRawJson = rawJson,
        )
    }

    /**
     * 与 wy/utils/crypto.js linuxapi 一致：AES-ECB-128-NoPadding + hex 大写
     * @return hex 大写字符串
     */
    private fun linuxapiEncrypt(obj: JSONObject): String {
        val text = obj.toString()
        val linuxapiKey = LxSdk.b64EncodeStr("rFgB&h#%2?^eDg:Q")
        val dataB64 = LxSdk.b64EncodeStr(text)
        val encrypted = LxSdk.aesEncrypt(dataB64, linuxapiKey, "", "ECB_128_NoPadding")
        val encryptedBytes = LxSdk.b64Decode(encrypted)
        val sb = StringBuilder()
        for (b in encryptedBytes) sb.append(String.format("%02X", b))
        return sb.toString()
    }

    /**
     * 解析 wy 歌单 URL，返回歌单 id（与 wy/songList.js getListId 一致）
     * 支持：URL → id 提取、### 分隔 cookie token、纯数字 id
     */
    private fun getWyListId(rawId: String): String? {
        var id = rawId
        // 处理 ### 分隔的 cookie token（与 wy/songList.js 一致）
        if (id.contains("###")) {
            id = id.split("###")[0]
        }
        // 包含 URL 字符时尝试正则匹配
        if (Regex("[?&:/]").containsMatchIn(id)) {
            wyListDetailLinkRegex.find(id)?.let { return it.groupValues[1] }
            wyListDetailLink2Regex.find(id)?.let { return it.groupValues[1] }
            // 短链等其他 URL 格式不支持自动重定向解析
            Log.w(TAG, "getWyListId: cannot extract id from URL: $id")
            return null
        }
        return id
    }

    // ===================== QQ 音乐歌单详情 =====================

    /**
     * QQ 音乐歌单详情（与 tx/songList.js getListDetail 一致）
     * GET https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?disstid=${id}
     */
    private suspend fun getListDetailTx(rawId: String, page: Int): SongListDetailResult {
        // 先尝试本地正则提取 id；失败时跟随分享链接重定向，从响应中解析 id
        var id = getTxListId(rawId)
        if (id == null && (rawId.startsWith("http://") || rawId.startsWith("https://"))) {
            id = resolveTxShareUrl(rawId)
        }
        if (id == null) return SongListDetailResult("tx", emptyList(), 0, page)

        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
            "?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=${id}" +
            "&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8" +
            "&notice=0&platform=yqq.json&needNewCode=0"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf(
                "Origin" to "https://y.qq.com",
                "Referer" to "https://y.qq.com/n/yqq/playsquare/${id}.html",
            ),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("tx getListDetail response not JSON: ${resp.body}")
        if (body.optInt("code") != 0) {
            // 诊断日志：打印完整响应体，便于确认 code 字段值和错误信息
            Log.w(TAG, "getListDetailTx: code=${body.optInt("code")}, id=$id, body=${body}")
            throw RuntimeException("tx getListDetail failed: code=${body.optInt("code")}")
        }

        val cdlist = body.optJSONArray("cdlist") ?: JSONArray()
        if (cdlist.length() == 0) return SongListDetailResult("tx", emptyList(), 0, page)

        val cd = cdlist.optJSONObject(0) ?: JSONObject()
        val songlist = cd.optJSONArray("songlist") ?: JSONArray()

        val list = mutableListOf<Song>()
        for (i in 0 until songlist.length()) {
            val item = songlist.optJSONObject(i) ?: continue
            parseTxSongDetail(item)?.let { list.add(it) }
        }

        val info = PlaylistInfo(
            name = LxSdk.decodeName(cd.optString("dissname")),
            img = cd.optString("logo"),
            desc = LxSdk.decodeName(cd.optString("desc")).replace("<br>", "\n"),
            author = cd.optString("nickname"),
            playCount = cd.optLong("visitnum").toString(),
        )
        return SongListDetailResult("tx", list, list.size, 1, info)
    }

    /**
     * 与 tx/songList.js filterListDetail 一致
     * item 字段：singer[], title, album.{name,mid}, interval, mid, id, file.media_mid
     */
    private fun parseTxSongDetail(item: JSONObject): Song? {
        val songmid = item.optString("mid")
        val songId = item.optString("id")
        if (songmid.isBlank() && songId.isBlank()) return null

        val singer = item.optJSONArray("singer")
        val singerName = LxSdk.formatSingerName(singer)
        val name = LxSdk.decodeName(item.optString("title"))
        val album = item.optJSONObject("album") ?: JSONObject()
        val albumName = LxSdk.decodeName(album.optString("name"))
        val albumMid = album.optString("mid")
        val interval = item.optLong("interval")
        val file = item.optJSONObject("file") ?: JSONObject()
        val strMediaMid = file.optString("media_mid")

        // 封面：与 lx-music-mobile filterListDetail 一致
        val img = if (albumName.isBlank() || albumName == "空") {
            val firstSinger = singer?.optJSONObject(0)
            if (firstSinger != null) {
                "https://y.gtimg.cn/music/photo_new/T001R500x500M000${firstSinger.optString("mid")}.jpg"
            } else ""
        } else {
            "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumMid}.jpg"
        }

        // 与 LxSdkSearch.sdkSongToSong 一致：保留 songmid/strMediaMid 便于 LxSdkMusicUrl 解析播放
        val rawJson = JSONObject().apply {
            put("songmid", songmid)
            put("songId", songId)
            put("strMediaMid", strMediaMid)
            put("albumMid", albumMid)
            put("name", name)
            put("singer", singerName)
            put("source", "tx")
            put("interval", LxSdk.formatPlayTime(interval))
        }.toString()

        val songIdLong = songmid.hashCode().toLong() and 0xFFFFFFFFL
        return Song(
            id = songIdLong,
            title = name,
            artist = singerName,
            album = albumName,
            duration = interval * 1000L,
            coverUrl = img,
            platform = "QQ音乐",
            platformId = songmid,
            lxSourceKey = "tx",
            pluginRawJson = rawJson,
        )
    }

    /**
     * 解析 tx 歌单 URL，返回歌单 id（与 tx/songList.js getListId 一致）
     * 支持：URL → id 提取、纯数字 id
     */
    private fun getTxListId(rawId: String): String? {
        val id = rawId
        if (Regex("[?&:/]").containsMatchIn(id)) {
            txListDetailLinkRegex.find(id)?.let { return it.groupValues[1] }
            txListDetailLink2Regex.find(id)?.let { return it.groupValues[1] }
            txListDetailLink3Regex.find(id)?.let { return it.groupValues[1] }
            Log.w(TAG, "getTxListId: cannot extract id from URL: $id")
            return null
        }
        return id
    }

    // ===================== 酷我歌单详情 =====================

    /**
     * 酷我歌单详情（与 kw/songList.js getListDetail digest-8 路径一致）
     * GET http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${id}
     *
     * 注意：digest-5 路径需额外查询 sourceid，本项目暂不支持；
     * digest-13 路径走 album 模块，本项目暂不支持。
     */
    private suspend fun getListDetailKw(rawId: String, page: Int): SongListDetailResult {
        val id = getKwListId(rawId) ?: return SongListDetailResult("kw", emptyList(), 0, page)

        val url = "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${id}" +
            "&pn=${page - 1}&rn=1000&encode=utf8&keyset=pl2012" +
            "&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf("User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 9;)"),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kw getListDetail response not JSON: ${resp.body}")
        if (body.optString("result") != "ok") {
            throw RuntimeException("kw getListDetail failed: result=${body.optString("result")}")
        }

        val musiclist = body.optJSONArray("musiclist") ?: JSONArray()
        val list = mutableListOf<Song>()
        for (i in 0 until musiclist.length()) {
            val item = musiclist.optJSONObject(i) ?: continue
            parseKwSongDetail(item)?.let { list.add(it) }
        }

        val info = PlaylistInfo(
            name = LxSdk.decodeName(body.optString("title")),
            img = body.optString("pic"),
            desc = LxSdk.decodeName(body.optString("info")),
            author = LxSdk.decodeName(body.optString("uname")),
            playCount = body.optLong("playnum").toString(),
        )
        return SongListDetailResult("kw", list, body.optInt("total"), page, info)
    }

    /**
     * 与 kw/songList.js filterListDetail 一致
     * item 字段：name, artist, album, duration(秒), id
     */
    private fun parseKwSongDetail(item: JSONObject): Song? {
        val idStr = item.optString("id")
        if (idStr.isBlank()) return null
        val idLong = idStr.toLongOrNull() ?: idStr.hashCode().toLong() and 0xFFFFFFFFL
        val name = LxSdk.decodeName(item.optString("name"))
        val artist = LxSdk.decodeName(item.optString("artist"))
        val album = LxSdk.decodeName(item.optString("album"))
        val durationSec = item.optString("duration").toLongOrNull() ?: 0L
        // 与 LxSdkSearch.sdkSongToSong 一致：保留 songmid 便于 LxSdkMusicUrl 解析播放
        val rawJson = JSONObject().apply {
            put("songmid", idStr)
            put("name", name)
            put("singer", artist)
            put("source", "kw")
            put("interval", LxSdk.formatPlayTime(durationSec))
        }.toString()

        return Song(
            id = idLong,
            title = name,
            artist = artist,
            album = album,
            duration = durationSec * 1000L,
            platform = "酷我",
            platformId = idStr,
            lxSourceKey = "kw",
            pluginRawJson = rawJson,
        )
    }

    /**
     * 解析 kw 歌单 URL，返回歌单 id（与 kw/songList.js getListDetail 一致）
     * 支持：URL → id 提取、digest-8__xxx 格式、纯数字 id
     */
    private fun getKwListId(rawId: String): String? {
        val id = rawId
        if (Regex("[?&:/]").containsMatchIn(id)) {
            kwListDetailLinkRegex.find(id)?.let { return it.groupValues[1] }
            kwListDetailLink2Regex.find(id)?.let { return it.groupValues[1] }
            Log.w(TAG, "getKwListId: cannot extract id from URL: $id")
            return null
        }
        // digest-X__id 格式（与 lx-music-mobile 一致）
        // 仅支持 digest-8 路径（最常见）；其他 digest 类型 best-effort 返回 id 部分
        if (id.startsWith("digest-")) {
            val parts = id.split("__")
            if (parts.size >= 2) return parts[1]
        }
        return id
    }

    // ===================== 酷狗歌单详情 =====================

    /**
     * 酷狗歌单详情（与 kg/songList.js getListDetail 路由一致）
     * - 包含 gcid_ 的分享链接 → decodeGcid → getUserListDetail2（mobiles.kugou.com）
     * - special/single/{id}.html 路径 → getListDetailBySpecialId（HTML 解析）
     * - 其他 URL 形式 → 尝试 resolveKgShareUrl 解析得到 global_collection_id → getUserListDetail2
     */
    private suspend fun getListDetailKg(rawId: String, page: Int): SongListDetailResult {
        // 分支 1：gcid_ 分享链接（与 kg/songList.js getUserListDetail link.includes('gcid_') 一致）
        if (rawId.contains("gcid_")) {
            return getKgListDetailByGcid(rawId, page)
        }
        // 分支 2：包含 global_collection_id 参数的 URL（与 link.includes('global_collection_id') 一致）
        if (rawId.contains("global_collection_id")) {
            val gcid = Regex("global_collection_id=(\\w+)").find(rawId)?.groupValues?.get(1)
            if (!gcid.isNullOrBlank()) {
                return getKgUserListDetail2(gcid, page)
            }
        }
        // 分支 3：先尝试本地正则提取 specialid；失败时尝试 fetch 分享链接解析
        var id = getKgListId(rawId)
        if (id == null && (rawId.startsWith("http://") || rawId.startsWith("https://"))) {
            val gcid = resolveKgShareUrl(rawId)
            if (!gcid.isNullOrBlank()) return getKgUserListDetail2(gcid, page)
        }
        if (id == null) return SongListDetailResult("kg", emptyList(), 0, page)

        val url = "http://www2.kugou.kugou.com/yueku/v9/special/single/${id}-5-9999.html"
        val resp = LxSdk.httpFetch(url = url, method = "get")

        val body = resp.body as? String
            ?: throw RuntimeException("kg getListDetail response not HTML: ${resp.body}")

        val listDataMatch = kgListDataRegex.find(body)
            ?: return SongListDetailResult("kg", emptyList(), 0, page)

        val listDataJson = listDataMatch.groupValues[1]
        val listArr = try {
            JSONArray(listDataJson)
        } catch (e: Exception) {
            Log.w(TAG, "kg getListDetail: failed to parse list JSON: ${e.message}")
            return SongListDetailResult("kg", emptyList(), 0, page)
        }

        val list = mutableListOf<Song>()
        for (i in 0 until listArr.length()) {
            val item = listArr.optJSONObject(i) ?: continue
            parseKgSongDetail(item)?.let { list.add(it) }
        }

        val listInfoMatch = kgListInfoRegex.find(body)
        val info = PlaylistInfo(
            name = listInfoMatch?.groupValues?.get(1).orEmpty(),
            img = listInfoMatch?.groupValues?.get(2).orEmpty(),
        )
        return SongListDetailResult("kg", list, list.size, 1, info)
    }

    /**
     * 与 kg/songList.js filterData 一致（global.data 元素结构）
     * item 字段：singername, songname, album_name, audio_id, duration(毫秒), hash
     */
    private fun parseKgSongDetail(item: JSONObject): Song? {
        val hash = item.optString("hash")
        val audioId = item.optString("audio_id")
        if (hash.isBlank() && audioId.isBlank()) return null

        val singerName = LxSdk.decodeName(item.optString("singername"))
        val songname = LxSdk.decodeName(item.optString("songname"))
        val albumName = LxSdk.decodeName(item.optString("album_name"))
        val durationMs = item.optLong("duration")

        // 酷狗优先使用 hash 作为 songId（与 LxSdkSearch.sdkSongToSong 一致）
        val songIdStr = if (audioId.isNotBlank()) audioId else hash
        val rawJson = JSONObject().apply {
            put("songmid", songIdStr)
            if (hash.isNotBlank()) put("hash", hash)
            put("name", songname)
            put("singer", singerName)
            put("source", "kg")
            put("interval", LxSdk.formatPlayTime(durationMs / 1000))
        }.toString()

        val songIdLong = songIdStr.hashCode().toLong() and 0xFFFFFFFFL
        return Song(
            id = songIdLong,
            title = songname,
            artist = singerName,
            album = albumName,
            duration = durationMs,
            platform = "酷狗",
            platformId = songIdStr,
            lxSourceKey = "kg",
            pluginRawJson = rawJson,
        )
    }

    /**
     * 解析 kg 歌单 URL，返回歌单 id（与 kg/songList.js getListDetail 一致）
     * 支持：special/single/{id}.html 路径、{id}.html 路径、纯数字 id、id_{id} 格式
     */
    private fun getKgListId(rawId: String): String? {
        val id = rawId
        // 包含 .html 的 URL（如 special/single/{id}.html）
        if (id.contains(".html")) {
            kgListDetailLinkRegex.find(id)?.let { return it.groupValues[1] }
        }
        // 包含 special/ 路径
        if (id.contains("special/")) {
            kgListDetailLink2Regex.find(id)?.let { return it.groupValues[1] }
        }
        // 包含 URL 字符的通用处理
        if (Regex("[?&:/]").containsMatchIn(id)) {
            kgListDetailLink2Regex.find(id)?.let { return it.groupValues[1] }
            Log.w(TAG, "getKgListId: cannot extract id from URL: $id")
            return null
        }
        if (id.startsWith("id_")) return id.removePrefix("id_")
        if (Regex("^\\d+$").matches(id)) return id
        Log.w(TAG, "getKgListId: unsupported format: $id")
        return null
    }

    // ===================== 酷狗分享链接（gcid_）解析 =====================

    /**
     * 处理 gcid_ 分享链接（与 kg/songList.js getUserListDetail gcid_ 分支一致）
     * 1. 从 URL 提取 gcid_xxx
     * 2. 调用 decodeGcid 转换为 global_collection_id
     * 3. 失败时回退到 resolveKgShareUrl 直接从 HTML 中提取
     * 4. 调用 getKgUserListDetail2 获取歌单详情
     */
    private suspend fun getKgListDetailByGcid(rawId: String, page: Int): SongListDetailResult {
        val gcidMatch = kgGcidRegex.find(rawId)
        var globalCollectionId: String? = null
        if (gcidMatch != null) {
            val gcid = "gcid_" + gcidMatch.groupValues[1]
            globalCollectionId = try { decodeGcid(gcid) } catch (e: Exception) {
                Log.w(TAG, "getKgListDetailByGcid: decodeGcid failed: ${e.message}")
                null
            }
        }
        // 回退：fetch 分享链接 HTML，直接提取 global_collection_id / encode_gic / encode_src_gid
        if (globalCollectionId.isNullOrBlank() && (rawId.startsWith("http://") || rawId.startsWith("https://"))) {
            globalCollectionId = resolveKgShareUrl(rawId)
        }
        if (globalCollectionId.isNullOrBlank()) {
            Log.w(TAG, "getKgListDetailByGcid: cannot resolve global_collection_id from: $rawId")
            return SongListDetailResult("kg", emptyList(), 0, page)
        }
        return getKgUserListDetail2(globalCollectionId, page)
    }

    /**
     * fetch 分享 URL，从 HTML body 中提取 global_collection_id
     * （与 kg/songList.js getUserListDetail typeof body == 'string' 分支一致）
     * 若 HTML 内嵌 encode_gic / encode_src_gid，则再调用 decodeGcid 转换
     *
     * 支持 JSON / JS 对象 / URL 参数 / HTML 属性等多种格式
     */
    private suspend fun resolveKgShareUrl(url: String): String? {
        return try {
            val resp = LxSdk.httpFetch(
                url = url,
                method = "get",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1",
                    "Referer" to url,
                ),
                timeoutMs = 20000L,
            )
            val body = when (val b = resp.body) {
                is String -> b
                is JSONObject -> b.toString()
                else -> return null
            }
            if (body.isBlank()) return null
            // 1. 直接提取 global_collection_id（JSON / JS 对象 / URL 参数 / HTML 属性）
            // 覆盖："global_collection_id":"xxx" / global_collection_id: "xxx" / global_collection_id=xxx
            Regex("global_collection_id[\"']?\\s*[:=]\\s*[\"']?(\\w+)").find(body)?.let {
                if (it.groupValues[1].isNotBlank()) return it.groupValues[1]
            }
            // 2. 提取 encode_gic / encode_src_gid → decodeGcid
            val gcid = kgEncodeGicRegex.find(body)?.groupValues?.get(1)
                ?: kgEncodeSrcGidRegex.find(body)?.groupValues?.get(1)
                ?: Regex("encode_gic[\"']?\\s*[:=]\\s*[\"']?(\\w+)").find(body)?.groupValues?.get(1)
                ?: Regex("encode_src_gid[\"']?\\s*[:=]\\s*[\"']?(\\w+)").find(body)?.groupValues?.get(1)
            if (!gcid.isNullOrBlank()) {
                return try { decodeGcid("gcid_$gcid") } catch (e: Exception) {
                    Log.w(TAG, "resolveKgShareUrl: decodeGcid($gcid) failed: ${e.message}")
                    null
                }
            }
            // 3. 提取 specialid 作为最后回退（需配合 getListDetailBySpecialId 使用，
            //    但此处返回 null 让调用方走默认路径，避免类型混淆）
            Log.w(TAG, "resolveKgShareUrl: cannot find global_collection_id in body (len=${body.length})")
            null
        } catch (e: Exception) {
            Log.w(TAG, "resolveKgShareUrl: failed: ${e.message}")
            null
        }
    }

    /**
     * 与 kg/util.js signatureParams 完全一致
     * sign_params = keyparam + sortedParamList.join('') + body + keyparam，最后 md5
     * @param params 例如 "dfid=-&appid=1005&mid=0&clientver=20109&clienttime=640612895&uuid=-"
     * @param platform "android" 或 "web"
     * @param body 请求 body 的 JSON 字符串（GET 请求传空字符串）
     */
    private fun signatureParamsKg(params: String, platform: String, body: String): String {
        val keyparam = if (platform == "web") "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
        else "OIlwieks28dk2k092lksi2UIkp"
        val paramList = params.split("&").sorted()
        val signParams = keyparam + paramList.joinToString("") + body + keyparam
        return LxSdk.md5(signParams)
    }

    /**
     * 与 kg/songList.js decodeGcid 一致
     * POST https://t.kugou.com/v1/songlist/batch_decode?${params}&signature=${sig}
     * body: {ret_info:1, data:[{id:gcid_xxx, id_type:2}]}
     * 返回 result.list[0].global_collection_id
     *
     * 注意：body 字符串顺序必须与 JSON.stringify 一致，否则签名不匹配
     */
    private suspend fun decodeGcid(gcid: String): String {
        val params = "dfid=-&appid=1005&mid=0&clientver=20109&clienttime=640612895&uuid=-"
        // 与 JS JSON.stringify({ret_info:1, data:[{id:gcid, id_type:2}]}) 完全一致的字符串
        val bodyStr = """{"ret_info":1,"data":[{"id":"$gcid","id_type":2}]}"""
        val signature = signatureParamsKg(params, "android", bodyStr)
        val url = "https://t.kugou.com/v1/songlist/batch_decode?${params}&signature=${signature}"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "post",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; HUAWEI HMA-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36",
                "Referer" to "https://m.kugou.com/",
                "Content-Type" to "application/json",
            ),
            body = bodyStr,
        )
        val body = resp.body as? JSONObject
            ?: throw RuntimeException("decodeGcid response not JSON: ${resp.body}")
        // 关键诊断日志：打印完整响应体，便于确认实际返回结构
        Log.w(TAG, "decodeGcid: statusCode=${resp.statusCode}, body=${body}")
        // 与 createHttp 一致：errcode/error_code/err_code 任一非 0 视为失败
        // JS: error_code ?? errcode ?? err_code
        val errCode = if (body.has("error_code")) body.optInt("error_code", -1)
            else if (body.has("errcode")) body.optInt("errcode", -1)
            else if (body.has("err_code")) body.optInt("err_code", -1)
            else -1
        if (errCode != 0) {
            Log.w(TAG, "decodeGcid: errCode=$errCode, body=${body}")
            throw RuntimeException("decodeGcid failed: errcode=$errCode")
        }
        // 与 createHttp 返回路径一致：body.data ?? body（若 info 是数组） ?? body.info
        // decodeGcid 中 result.list[0] 实际是 body.data.list[0] / body.list[0] / body.info.list[0]
        val list = body.optJSONObject("data")?.optJSONArray("list")
            ?: body.optJSONArray("list")
            ?: body.optJSONObject("info")?.optJSONArray("list")
            ?: body.optJSONObject("data")?.optJSONArray("info")
            ?: run {
                Log.w(TAG, "decodeGcid: cannot find list, body=${body}")
                throw RuntimeException("decodeGcid: missing list in response")
            }
        if (list.length() == 0) throw RuntimeException("decodeGcid: empty list")
        val firstItem = list.optJSONObject(0) ?: throw RuntimeException("decodeGcid: list[0] not object")
        val globalCollectionId = firstItem.optString("global_collection_id")
            .ifBlank { firstItem.optString("global_specialid") }
        if (globalCollectionId.isNullOrBlank()) {
            Log.w(TAG, "decodeGcid: list[0]=${firstItem}")
            throw RuntimeException("decodeGcid: missing global_collection_id")
        }
        return globalCollectionId
    }

    /**
     * 与 kg/songList.js getUserListDetail2 一致
     * 1. 调用 info_v2 获取歌单元信息（specialname/songcount 等）
     * 2. 分页调用 song_v2 获取所有歌曲 hash 列表（每页最多 300）
     * 3. 调用 getKgMusicInfos 批量获取完整歌曲信息
     */
    private suspend fun getKgUserListDetail2(globalCollectionId: String, page: Int): SongListDetailResult {
        if (globalCollectionId.length > 1000) {
            return SongListDetailResult("kg", emptyList(), 0, page)
        }
        val id = globalCollectionId
        val commonHeaders = mapOf(
            "mid" to "1586163242519",
            "Referer" to "https://m3ws.kugou.com/share/index.php",
            "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Mobile/15A372 Safari/604.1",
            "dfid" to "-",
            "clienttime" to "1586163242519",
        )

        // 1. 获取歌单元信息
        val infoParams = "appid=1058&specialid=0&global_specialid=${id}&format=jsonp&srcappid=2919&clientver=20000&clienttime=1586163242519&mid=1586163242519&uuid=1586163242519&dfid=-"
        val infoSig = signatureParamsKg(infoParams, "web", "")
        val infoUrl = "https://mobiles.kugou.com/api/v5/special/info_v2?${infoParams}&signature=${infoSig}"
        val infoResp = LxSdk.httpFetch(url = infoUrl, method = "get", headers = commonHeaders)
        val infoBody = infoResp.body as? JSONObject
            ?: throw RuntimeException("kg info_v2 response not JSON: ${infoResp.body}")
        val errCode = if (infoBody.has("error_code")) infoBody.optInt("error_code", -1)
            else if (infoBody.has("errcode")) infoBody.optInt("errcode", -1)
            else if (infoBody.has("err_code")) infoBody.optInt("err_code", -1)
            else -1
        if (errCode != 0) {
            Log.w(TAG, "getKgUserListDetail2: info_v2 errCode=$errCode, body=${infoBody}")
            throw RuntimeException("kg info_v2 failed: errcode=$errCode")
        }
        // 与 createHttp 一致：优先 body.data，其次 body（若 info 是数组），最后 body.info
        val info = infoBody.optJSONObject("data")
            ?: (if (infoBody.optJSONArray("info") != null) infoBody else infoBody.optJSONObject("info"))
            ?: infoBody
        val songCount = info.optInt("songcount", 0)
        val playlistName = LxSdk.decodeName(info.optString("specialname"))
        val playlistImg = info.optString("imgurl").replace("{size}", "240")
        val playlistDesc = LxSdk.decodeName(info.optString("intro"))
        val playlistAuthor = LxSdk.decodeName(info.optString("nickname"))
        if (songCount == 0) {
            Log.w(TAG, "getKgUserListDetail2: songcount=0, info=${info}")
        }

        // 2. 分页获取歌曲 hash 列表
        val hashList = mutableListOf<JSONObject>()
        var total = songCount
        var p = 0
        while (total > 0) {
            val limit = minOf(total, 300)
            total -= limit
            p += 1
            val songParams = "appid=1058&global_specialid=${id}&specialid=0&plat=0&version=8000&page=${p}&pagesize=${limit}&srcappid=2919&clientver=20000&clienttime=1586163263991&mid=1586163263991&uuid=1586163263991&dfid=-"
            val songSig = signatureParamsKg(songParams, "web", "")
            val songUrl = "https://mobiles.kugou.com/api/v5/special/song_v2?${songParams}&signature=${songSig}"
            val songResp = LxSdk.httpFetch(url = songUrl, method = "get", headers = commonHeaders)
            val songBody = songResp.body as? JSONObject
            if (songBody == null) {
                Log.w(TAG, "getKgUserListDetail2: song_v2 page=$p response not JSON")
                break
            }
            val sErr = if (songBody.has("error_code")) songBody.optInt("error_code", -1)
                else if (songBody.has("errcode")) songBody.optInt("errcode", -1)
                else if (songBody.has("err_code")) songBody.optInt("err_code", -1)
                else -1
            if (sErr != 0) {
                Log.w(TAG, "getKgUserListDetail2: song_v2 page=$p errCode=$sErr, body=${songBody}")
                break
            }
            // 与 createHttp + .then(data => data.info) 一致：list 在 body.data.info
            val infoArr = songBody.optJSONObject("data")?.optJSONArray("info")
                ?: songBody.optJSONArray("info")
                ?: songBody.optJSONObject("info")?.optJSONArray("info")
                ?: JSONArray()
            for (i in 0 until infoArr.length()) {
                infoArr.optJSONObject(i)?.let { hashList.add(it) }
            }
        }

        // 3. 批量获取完整歌曲信息
        val list = getKgMusicInfos(hashList)
        val infoObj = PlaylistInfo(
            name = playlistName,
            img = playlistImg,
            desc = playlistDesc,
            author = playlistAuthor,
        )
        return SongListDetailResult("kg", list, list.size, 1, infoObj)
    }

    /**
     * 与 kg/songList.js getMusicInfos + createTask + deDuplication 一致
     * 按 100 个一批分组，POST 到 gateway.kugou.com/v2/album_audio/audio
     * 返回的每个元素是数组（取 [0]），整体 flatten 后由 parseKgSongDetailV2 解析为 Song
     */
    private suspend fun getKgMusicInfos(list: List<JSONObject>): List<Song> = coroutineScope {
        if (list.isEmpty()) return@coroutineScope emptyList()
        // 去重（按 hash）
        val seen = HashSet<String>()
        val deduped = mutableListOf<JSONObject>()
        for (item in list) {
            val hash = item.optString("hash")
            if (hash.isBlank() || seen.contains(hash)) continue
            seen.add(hash)
            deduped.add(item)
        }
        // 分批
        val batches = deduped.chunked(100)
        val results = batches.map { batch ->
            async {
                try {
                    val dataObj = JSONObject().apply {
                        put("area_code", "1")
                        put("show_privilege", 1)
                        put("show_album_info", 1)
                        put("is_publish", "")
                        put("appid", 1005)
                        put("clientver", 11451)
                        put("mid", "1")
                        put("dfid", "-")
                        put("clienttime", System.currentTimeMillis())
                        put("key", "OIlwieks28dk2k092lksi2UIkp")
                        put("fields", "album_info,author_name,audio_info,ori_audio_name,base,songname")
                        put("data", JSONArray().apply {
                            for (item in batch) put(item)
                        })
                    }
                    val resp = LxSdk.httpFetch(
                        url = "http://gateway.kugou.com/v2/album_audio/audio",
                        method = "post",
                        headers = mapOf(
                            "KG-THash" to "13a3164",
                            "KG-RC" to "1",
                            "KG-Fake" to "0",
                            "KG-RF" to "00869891",
                            "User-Agent" to "Android712-AndroidPhone-11451-376-0-FeeCacheUpdate-wifi",
                            "x-router" to "kmr.service.kugou.com",
                            "Content-Type" to "application/json",
                        ),
                        body = dataObj,
                        timeoutMs = 20000L,
                    )
                    val body = resp.body as? JSONObject ?: return@async emptyList()
                    val errCode = if (body.has("error_code")) body.optInt("error_code", -1)
                        else if (body.has("errcode")) body.optInt("errcode", -1)
                        else if (body.has("err_code")) body.optInt("err_code", -1)
                        else -1
                    if (errCode != 0) {
                        Log.w(TAG, "getKgMusicInfos: errCode=$errCode, body=${body}")
                        return@async emptyList()
                    }
                    val dataArr = body.optJSONArray("data") ?: return@async emptyList()
                    val songs = mutableListOf<Song>()
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.optJSONArray(i)?.optJSONObject(0) ?: continue
                        parseKgSongDetailV2(item)?.let { songs.add(it) }
                    }
                    songs
                } catch (e: Exception) {
                    Log.w(TAG, "getKgMusicInfos batch failed: ${e.message}")
                    emptyList()
                }
            }
        }.awaitAll()
        results.flatten()
    }

    /**
     * 与 kg/songList.js filterData2 单元素映射一致
     * 输入：gateway.kugou.com/v2/album_audio/audio 返回的元素（数组首元素）
     * 字段：author_name, songname, album_info.album_name, album_info.album_id,
     *       audio_info.audio_id, audio_info.hash, audio_info.timelength(毫秒)
     */
    private fun parseKgSongDetailV2(item: JSONObject): Song? {
        val audioInfo = item.optJSONObject("audio_info") ?: JSONObject()
        val albumInfo = item.optJSONObject("album_info") ?: JSONObject()
        val hash = audioInfo.optString("hash")
        val audioId = audioInfo.optString("audio_id")
        if (hash.isBlank() && audioId.isBlank()) return null

        val singerName = LxSdk.decodeName(item.optString("author_name"))
        val songname = LxSdk.decodeName(item.optString("songname"))
        val albumName = LxSdk.decodeName(albumInfo.optString("album_name"))
        val durationMs = audioInfo.optLong("timelength", 0L)

        // 与 parseKgSongDetail 一致：优先使用 audio_id，hash 写入 rawJson 便于 LxSdkMusicUrl 解析
        val songIdStr = if (audioId.isNotBlank()) audioId else hash
        val rawJson = JSONObject().apply {
            put("songmid", songIdStr)
            if (hash.isNotBlank()) put("hash", hash)
            put("name", songname)
            put("singer", singerName)
            put("source", "kg")
            put("interval", LxSdk.formatPlayTime(durationMs / 1000))
        }.toString()

        val songIdLong = songIdStr.hashCode().toLong() and 0xFFFFFFFFL
        return Song(
            id = songIdLong,
            title = songname,
            artist = singerName,
            album = albumName,
            duration = durationMs,
            platform = "酷狗",
            platformId = songIdStr,
            lxSourceKey = "kg",
            pluginRawJson = rawJson,
        )
    }

    // ===================== QQ 音乐分享链接解析 =====================

    /**
     * fetch QQ 音乐分享 URL，从 HTML/JSON body 中提取歌单 id
     * （与 tx/songList.js handleParseId + getListId 一致：原版读 Location 头，
     * 本项目 followRedirects=true 拿不到 Location，改为从最终响应 body 中提取）
     * 搜索模式（按优先级）：
     * 1. id=\d+ 查询参数
     * 2. /playlist/\d+ 路径
     * 3. /playsquare/\d+ 路径
     * 4. "disstid":"?\d+"  JSON 字段
     * 5. "dissid":"?\d+"   JSON 字段
     */
    private suspend fun resolveTxShareUrl(url: String): String? {
        return try {
            val resp = LxSdk.httpFetch(
                url = url,
                method = "get",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; HLK-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.5112.102 Mobile Safari/537.36 EdgA/104.0.1293.70",
                ),
            )
            val body = when (val b = resp.body) {
                is String -> b
                is JSONObject -> b.toString()
                is JSONArray -> b.toString()
                else -> return null
            }
            // 1. id= 查询参数
            txListDetailLink2Regex.find(body)?.let { return it.groupValues[1] }
            // 2. /playlist/\d+ 路径
            txListDetailLinkRegex.find(body)?.let { return it.groupValues[1] }
            // 3. /playsquare/\d+ 路径
            txListDetailLink3Regex.find(body)?.let { return it.groupValues[1] }
            // 4. "disstid":"?\d+"  JSON 字段
            Regex("\"disstid\"\\s*:\\s*\"?(\\d+)\"?").find(body)?.let { return it.groupValues[1] }
            // 5. "dissid":"?\d+"   JSON 字段
            Regex("\"dissid\"\\s*:\\s*\"?(\\d+)\"?").find(body)?.let { return it.groupValues[1] }
            null
        } catch (e: Exception) {
            Log.w(TAG, "resolveTxShareUrl: failed: ${e.message}")
            null
        }
    }
}
