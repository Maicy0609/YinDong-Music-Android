package com.yindong.music.data.lxsdk

import android.util.Log
import com.yindong.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * lx-music-mobile 4 平台排行榜实现
 *
 * 完全按照以下文件移植：
 * - src/utils/musicSdk/wy/leaderboard.js（eapi 加密请求 /api/v3/playlist/detail）
 * - src/utils/musicSdk/tx/leaderboard.js（musicu.fcg GetDetail）
 * - src/utils/musicSdk/kg/leaderboard.js（mobilecdnbj.kugou.com/api/v3/rank/song）
 * - src/utils/musicSdk/kw/leaderboard.js（wapi.kuwo.cn/api/pc/bang/bang/info 简化版）
 *
 * 每个平台的榜单列表为硬编码（与原版 getBoards 直接返回 topList 一致），
 * [getList] 按 source 分发到具体平台实现，结果统一为 [Song] 列表。
 */
object LxSdkLeaderboard {

    private const val TAG = "LxSdkLeaderboard"

    /** 榜单元信息 */
    data class Board(
        val id: String,     // "{source}__{bangid}"，如 "wy__3778678"
        val name: String,   // "热歌榜"
        val bangid: String,  // "3778678"
    )

    /** 排行榜获取结果 */
    data class LeaderboardResult(
        val source: String,        // "wy", "tx", "kw", "kg"
        val bangid: String,
        val list: List<Song>,
        val total: Int = 0,
    )

    // ===================== 榜单列表（硬编码，与原版 getBoards 一致） =====================

    private val wyBoards: List<Board> = listOf(
        Board("wy__19723756", "飙升榜", "19723756"),
        Board("wy__3778678", "热歌榜", "3778678"),
        Board("wy__3779629", "新歌榜", "3779629"),
        Board("wy__3779652", "原创榜", "3779652"),
        Board("wy__2884035", "云音乐说唱榜", "2884035"),
        Board("wy__991319590", "云音乐电音榜", "991319590"),
        Board("wy__71385702", "云音乐古典榜", "71385702"),
        Board("wy__1978921795", "黑胶VIP爱听榜", "1978921795"),
    )

    private val txBoards: List<Board> = listOf(
        Board("tx__4", "流行指数榜", "4"),
        Board("tx__26", "热歌榜", "26"),
        Board("tx__27", "新歌榜", "27"),
        Board("tx__62", "飙升榜", "62"),
        Board("tx__3", "巅峰榜·欧美", "3"),
        Board("tx__5", "巅峰榜·韩国", "5"),
        Board("tx__17", "巅峰榜·日本", "17"),
        Board("tx__36", "巅峰榜·内地", "36"),
    )

    private val kgBoards: List<Board> = listOf(
        Board("kg__8888", "TOP500", "8888"),
        Board("kg__6666", "飙升榜", "6666"),
        Board("kg__52144", "抖音热歌榜", "52144"),
        Board("kg__23784", "网络红歌榜", "23784"),
        Board("kg__22650", "华语新歌榜", "22650"),
        Board("kg__21663", "欧美榜", "21663"),
        Board("kg__21664", "韩国榜", "21664"),
        Board("kg__21665", "日本榜", "21665"),
    )

    private val kwBoards: List<Board> = listOf(
        Board("kw__93", "飙升榜", "93"),
        Board("kw__17", "新歌榜", "17"),
        Board("kw__16", "热歌榜", "16"),
        Board("kw__158", "抖音热歌榜", "158"),
        Board("kw__284", "热评榜", "284"),
        Board("kw__187", "流行趋势榜", "187"),
        Board("kw__26", "经典怀旧榜", "26"),
        Board("kw__104", "华语榜", "104"),
    )

    /**
     * 返回指定平台的硬编码榜单列表
     * @param source 平台 id：wy/tx/kw/kg
     */
    fun getBoards(source: String): List<Board> {
        return when (source) {
            "wy" -> wyBoards
            "tx" -> txBoards
            "kg" -> kgBoards
            "kw" -> kwBoards
            else -> emptyList()
        }
    }

    /**
     * 获取榜单歌曲（按 source 分发到具体平台实现）
     * @param source 平台 id：wy/tx/kw/kg
     * @param bangid 榜单 id（如 "3778678"）
     * @param page 页码（从 1 开始）
     * @param limit 每页数量
     * @return [LeaderboardResult]，失败时 list 为空
     */
    suspend fun getList(
        source: String,
        bangid: String,
        page: Int = 1,
        limit: Int = 100,
    ): LeaderboardResult = withContext(Dispatchers.IO) {
        try {
            when (source) {
                "wy" -> getListWy(bangid, page, limit)
                "tx" -> getListTx(bangid, page, limit)
                "kg" -> getListKg(bangid, page, limit)
                "kw" -> getListKw(bangid, page, limit)
                else -> {
                    Log.w(TAG, "unknown source: $source")
                    LeaderboardResult(source, bangid, emptyList(), 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getList[$source] failed: bangid=$bangid — ${e.message}", e)
            LeaderboardResult(source, bangid, emptyList(), 0)
        }
    }

    // ===================== 网易云 =====================

    /**
     * 网易云排行榜（与 wy/leaderboard.js 一致）
     * eapi 加密 POST /api/v3/playlist/detail，参数 {id, n, p}
     */
    private suspend fun getListWy(bangid: String, page: Int, limit: Int): LeaderboardResult {
        val url = "/api/v3/playlist/detail"
        val data = JSONObject().apply {
            put("id", bangid)
            put("n", 100000)
            put("p", 1)
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
            ?: throw RuntimeException("wy leaderboard response not JSON: ${resp.body}")
        if (body.optInt("code") != 200) {
            throw RuntimeException("wy leaderboard failed: code=${body.optInt("code")}")
        }

        val playlist = body.optJSONObject("playlist") ?: JSONObject()
        val tracks = playlist.optJSONArray("tracks") ?: JSONArray()
        val list = mutableListOf<Song>()
        for (i in 0 until tracks.length()) {
            val track = tracks.optJSONObject(i) ?: continue
            val song = parseWyTrack(track) ?: continue
            list.add(song)
        }

        // 分页截取（接口一次性返回全部，按 page/limit 截取）
        val from = ((page - 1) * limit).coerceAtLeast(0)
        val paged = if (from >= list.size) emptyList() else list.subList(from, minOf(from + limit, list.size))
        return LeaderboardResult("wy", bangid, paged, list.size)
    }

    /**
     * 解析网易云 track 对象为 [Song]
     * track 字段：name, ar(artists 数组), al(album 对象), dt(duration 毫秒), id
     */
    private fun parseWyTrack(track: JSONObject): Song? {
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
     * QQ 音乐排行榜（与 tx/leaderboard.js 一致）
     * POST https://u.y.qq.com/cgi-bin/musicu.fcg GetDetail
     */
    private suspend fun getListTx(bangid: String, page: Int, limit: Int): LeaderboardResult {
        val topid = bangid.toIntOrNull() ?: 0
        val data = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("ct", "24")
                put("cv", "1803")
                put("guid", "0")
                put("patch", "118")
                put("tmeAppID", "qqmusic")
                put("uin", "0")
            })
            put("toplist", JSONObject().apply {
                put("module", "musicToplist.ToplistInfoServer")
                put("method", "GetDetail")
                put("param", JSONObject().apply {
                    put("topid", topid)
                    put("num", limit)
                    put("period", "2023-01-01")
                })
            })
        }

        val resp = LxSdk.httpFetch(
            url = "https://u.y.qq.com/cgi-bin/musicu.fcg",
            method = "post",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)",
            ),
            body = data,
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("tx leaderboard response not JSON: ${resp.body}")
        if (body.optInt("code") != 0) {
            throw RuntimeException("tx leaderboard failed: code=${body.optInt("code")}")
        }

        // body.toplist.data.data.songInfoList[]
        val songInfoList = body.optJSONObject("toplist")
            ?.optJSONObject("data")
            ?.optJSONObject("data")
            ?.optJSONArray("songInfoList")
            ?: JSONArray()

        val list = mutableListOf<Song>()
        for (i in 0 until songInfoList.length()) {
            val song = songInfoList.optJSONObject(i) ?: continue
            val parsed = parseTxSong(song) ?: continue
            list.add(parsed)
        }

        return LeaderboardResult("tx", bangid, list, list.size)
    }

    /**
     * 解析 QQ 音乐 songInfoList 元素为 [Song]
     * 字段：name, singer[], album.name, album.mid, interval(秒), mid, id
     */
    private fun parseTxSong(song: JSONObject): Song? {
        val mid = song.optString("mid")
        if (mid.isBlank()) return null
        val name = LxSdk.decodeName(song.optString("name"))
        val singerArr = song.optJSONArray("singer") ?: JSONArray()
        val album = song.optJSONObject("album") ?: JSONObject()
        val albumName = LxSdk.decodeName(album.optString("name"))
        val albumMid = album.optString("mid")
        val interval = song.optLong("interval")

        // 封面：与 tx/musicSearch.js 一致
        val img = if (albumName.isBlank() || albumName == "空") {
            if (singerArr.length() > 0) {
                val singerMid = singerArr.optJSONObject(0)?.optString("mid") ?: ""
                if (singerMid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T001R500x500M000${singerMid}.jpg" else ""
            } else ""
        } else {
            if (albumMid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumMid}.jpg" else ""
        }

        // id = hash(song.mid)，保持 Long 类型
        val songIdLong = mid.hashCode().toLong() and 0xFFFFFFFFL

        // 与 LxSdkSearch.sdkSongToSong 一致：保留 songmid/strMediaMid 便于 LxSdkMusicUrl 解析播放
        val rawJson = JSONObject().apply {
            put("songmid", mid)
            put("name", name)
            put("singer", LxSdk.formatSingerName(singerArr))
            put("source", "tx")
            put("interval", LxSdk.formatPlayTime(interval))
        }.toString()

        return Song(
            id = songIdLong,
            title = name,
            artist = LxSdk.formatSingerName(singerArr),
            album = albumName,
            duration = interval * 1000L,
            coverUrl = img,
            platform = "QQ音乐",
            platformId = mid,
            lxSourceKey = "tx",
            pluginRawJson = rawJson,
        )
    }

    // ===================== 酷狗 =====================

    /**
     * 酷狗排行榜（与 kg/leaderboard.js 一致）
     * GET http://mobilecdnbj.kugou.com/api/v3/rank/song?...&rankid=${bangid}
     */
    private suspend fun getListKg(bangid: String, page: Int, limit: Int): LeaderboardResult {
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=1&plat=0&pagesize=${limit}&area_code=1&page=${page}&rankid=${bangid}&with_res_tag=0&show_portrait_mv=1"

        val resp = LxSdk.httpFetch(url = url, method = "get")
        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kg leaderboard response not JSON: ${resp.body}")
        // 与 kg/leaderboard.js 一致：body.errcode != 0 视为失败
        if (body.optInt("errcode") != 0) {
            throw RuntimeException("kg leaderboard failed: errcode=${body.optInt("errcode")}")
        }

        val dataObj = body.optJSONObject("data") ?: JSONObject()
        val info = dataObj.optJSONArray("info") ?: JSONArray()
        val total = dataObj.optInt("total", info.length())

        val list = mutableListOf<Song>()
        for (i in 0 until info.length()) {
            val item = info.optJSONObject(i) ?: continue
            val parsed = parseKgSong(item) ?: continue
            list.add(parsed)
        }

        return LeaderboardResult("kg", bangid, list, total)
    }

    /**
     * 解析酷狗 info 元素为 [Song]
     * filename 格式："歌手 - 歌名 - 专辑"，用 split(" - ") 解析
     * songid / hash 字段
     */
    private fun parseKgSong(item: JSONObject): Song? {
        val songIdStr = item.optString("songid")
        val songIdLong = songIdStr.toLongOrNull() ?: return null
        val filename = item.optString("filename")
        // filename: "歌手 - 歌名 - 专辑"
        val parts = if (filename.isNotBlank()) filename.split(" - ") else emptyList()
        val artist = LxSdk.decodeName(parts.getOrNull(0) ?: "")
        val title = LxSdk.decodeName(parts.getOrNull(1) ?: "")
        val album = LxSdk.decodeName(parts.getOrNull(2) ?: "")
        val duration = item.optLong("duration") * 1000L
        // 酷狗优先使用 hash 作为 songId（与 LxSdkSearch.sdkSongToSong 一致）
        val hash = item.optString("hash")
        val rawJson = JSONObject().apply {
            put("songmid", songIdStr)
            if (hash.isNotBlank()) put("hash", hash)
            put("name", title)
            put("singer", artist)
            put("source", "kg")
            put("interval", LxSdk.formatPlayTime(duration / 1000))
        }.toString()

        return Song(
            id = songIdLong,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            coverUrl = "",
            platform = "酷狗",
            platformId = songIdStr,
            lxSourceKey = "kg",
            pluginRawJson = rawJson,
        )
    }

    // ===================== 酷我 =====================

    /**
     * 酷我排行榜（简化版，与 kw/leaderboard.js 思路一致但避开 wbdCrypto）
     * GET http://wapi.kuwo.cn/api/pc/bang/bang/info?bangId=${bangid}&pn=${page-1}&rn=${limit}
     *
     * 响应字段兼容多种命名（musiclist / musicList / list），失败返回空列表。
     */
    private suspend fun getListKw(bangid: String, page: Int, limit: Int): LeaderboardResult {
        val url = "http://wapi.kuwo.cn/api/pc/bang/bang/info?bangId=${bangid}&pn=${page - 1}&rn=${limit}"

        val resp = LxSdk.httpFetch(
            url = url,
            method = "get",
            headers = mapOf("User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 9;)"),
        )

        val body = resp.body as? JSONObject
            ?: throw RuntimeException("kw leaderboard response not JSON: ${resp.body}")
        if (body.optInt("code") != 200) {
            throw RuntimeException("kw leaderboard failed: code=${body.optInt("code")}")
        }

        val dataObj = body.optJSONObject("data") ?: JSONObject()
        // 兼容 musiclist / musicList / list 三种字段
        val musicList = dataObj.optJSONArray("musiclist")
            ?: dataObj.optJSONArray("musicList")
            ?: dataObj.optJSONArray("list")
            ?: JSONArray()
        val total = dataObj.optInt("total", musicList.length())

        val list = mutableListOf<Song>()
        for (i in 0 until musicList.length()) {
            val item = musicList.optJSONObject(i) ?: continue
            val parsed = parseKwSong(item) ?: continue
            list.add(parsed)
        }

        return LeaderboardResult("kw", bangid, list, total)
    }

    /**
     * 解析酷我 musiclist 元素为 [Song]
     * 兼容 id/musicrid、name、artist、album、duration、pic 字段
     */
    private fun parseKwSong(item: JSONObject): Song? {
        val idStr = item.optString("id").ifBlank { item.optString("musicrid").replace("MUSIC_", "") }
        if (idStr.isBlank()) return null
        val idLong = idStr.toLongOrNull() ?: idStr.hashCode().toLong() and 0xFFFFFFFFL
        val name = LxSdk.decodeName(item.optString("name"))
        val artist = LxSdk.decodeName(item.optString("artist"))
        val album = LxSdk.decodeName(item.optString("album"))
        val durationSec = item.optString("duration").toLongOrNull() ?: item.optLong("duration")
        val pic = item.optString("pic").ifBlank { item.optString("albumpic") }
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
            coverUrl = pic,
            platform = "酷我",
            platformId = idStr,
            lxSourceKey = "kw",
            pluginRawJson = rawJson,
        )
    }
}
