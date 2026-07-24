package com.yindong.music.data

import android.os.Build
import android.util.Log
import androidx.compose.runtime.Stable
import com.yindong.music.BuildConfig
import com.yindong.music.data.model.Playlist
import com.yindong.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 歌单同步管理器
 *
 * 功能:
 * 1. 用户身份验证（10位以内数字ID注册/登录）
 * 2. 上传歌单、收藏、播放历史到服务器（文件存储，支持分块上传）
 * 3. 从服务器拉取数据恢复
 * 4. 同步进度状态管理
 *
 * 服务器: https://jilu.zh2026.cn/api/sync.php
 */
object PlaylistSyncManager {

    private const val TAG = "PlaylistSync"

    /** 同步接口地址 */
    private const val SYNC_API_URL = "https://jilu.zh2026.cn/api/sync.php"

    /** 分块上传时每块最大歌曲数 */
    private const val CHUNK_SIZE = 100

    /** 本地存储 key — 同步用户ID */
    private const val KEY_SYNC_USER_ID = "sync_user_id"

    /** 本地存储 key — 同步密码（用于自动重新登录） */
    private const val KEY_SYNC_PASSWORD = "sync_password"

    /** 本地存储 key — 同步QQ号（用于密码找回） */
    private const val KEY_SYNC_QQ = "sync_qq"

    /** 同步状态 */
    @Stable
    sealed class SyncState {
        object Idle : SyncState()
        object Authenticating : SyncState()
        object Uploading : SyncState()
        object Downloading : SyncState()
        data class Progress(val message: String, val percent: Int) : SyncState()
        data class Success(val message: String) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ═══════════════════════════════════════════
    //  用户身份管理
    // ═══════════════════════════════════════════

    /** 获取已登录的同步用户ID，未登录返回空 */
    fun getSyncUserId(): String {
        return LocalStorage.loadString(KEY_SYNC_USER_ID)
    }

    /** 是否已登录同步账号 */
    fun isLoggedIn(): Boolean = getSyncUserId().isNotEmpty()

    /**
     * 注册/登录
     * @param userId 10位以内数字
     * @param password 4-20位数字密码
     * @param qq QQ号（注册时必填，登录时可选）
     * @return 成功返回 true
     */
    suspend fun auth(userId: String, password: String, qq: String = ""): Boolean = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Authenticating
        try {
            val json = JSONObject().apply {
                put("action", "auth")
                put("user_id", userId)
                put("password", password)
                if (qq.isNotEmpty()) put("qq", qq)
                put("device_id", LocalStorage.loadOrCreateDeviceId())
                put("device_model", "${Build.BRAND} ${Build.MODEL}")
                put("app_version", BuildConfig.VERSION_NAME)
            }

            val response = post(json)
            if (response.optBoolean("success")) {
                LocalStorage.saveString(KEY_SYNC_USER_ID, userId)
                LocalStorage.saveString(KEY_SYNC_PASSWORD, password)
                if (qq.isNotEmpty()) LocalStorage.saveString(KEY_SYNC_QQ, qq)
                _syncState.value = SyncState.Idle
                true
            } else {
                _syncState.value = SyncState.Error(response.optString("message", "认证失败"))
                false
            }
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("网络错误: ${e.message}")
            false
        }
    }

    /** 退出登录 */
    fun logout() {
        LocalStorage.saveString(KEY_SYNC_USER_ID, "")
        LocalStorage.saveString(KEY_SYNC_PASSWORD, "")
        _syncState.value = SyncState.Idle
    }

    // ═══════════════════════════════════════════
    //  密码找回
    // ═══════════════════════════════════════════

    /**
     * 密码找回（通过QQ号查找ID并重置密码）
     * @param qq 注册时填写的QQ号
     * @param newPassword 新密码（4-20位数字）
     * @return 找回的用户ID，失败返回 null
     */
    suspend fun recover(qq: String, newPassword: String): String? = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Authenticating
        try {
            val json = JSONObject().apply {
                put("action", "recover")
                put("qq", qq)
                put("new_password", newPassword)
            }

            val response = post(json)
            if (response.optBoolean("success")) {
                val foundUserId = response.optString("user_id", "")
                _syncState.value = SyncState.Idle
                foundUserId.ifEmpty { null }
            } else {
                _syncState.value = SyncState.Error(response.optString("message", "找回失败"))
                null
            }
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("网络错误: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════
    //  上传同步
    // ═══════════════════════════════════════════

    /**
     * 上传所有数据到服务器
     * 自动判断数据量大小：
     * - 数据量小（<200首歌曲）直接整体上传
     * - 数据量大（≥200首歌曲）使用分块上传，避免服务器返回HTML错误
     *
     * @param playlists 用户歌单
     * @param favorites 收藏歌曲
     * @param playHistory 播放历史
     * @param searchHistory 搜索历史
     */
    suspend fun upload(
        playlists: List<Playlist>,
        favorites: List<Song>,
        playHistory: List<Song>,
        searchHistory: List<String>,
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = getSyncUserId()
        if (userId.isEmpty()) {
            _syncState.value = SyncState.Error("请先登录同步账号")
            return@withContext false
        }

        // 计算总歌曲数
        val totalSongs = playlists.sumOf { it.songs.size } + favorites.size + playHistory.size
        Log.d(TAG, "准备上传: ${playlists.size}个歌单, $totalSongs 首歌曲")

        if (totalSongs < 200) {
            // 小数据量：直接上传
            uploadDirect(userId, playlists, favorites, playHistory, searchHistory)
        } else {
            // 大数据量：分块上传
            uploadChunked(userId, playlists, favorites, playHistory, searchHistory)
        }
    }

    /**
     * 直接整体上传（小数据量）
     */
    private fun uploadDirect(
        userId: String,
        playlists: List<Playlist>,
        favorites: List<Song>,
        playHistory: List<Song>,
        searchHistory: List<String>,
    ): Boolean {
        _syncState.value = SyncState.Progress("正在序列化数据...", 10)
        return try {
            val playlistsArr = JSONArray()
            playlists.forEach { pl ->
                val plObj = JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("creator", pl.creator)
                    put("description", pl.description)
                    put("coverUrl", pl.coverUrl)
                    val songsArr = JSONArray()
                    pl.songs.forEach { songsArr.put(songToJson(it)) }
                    put("songs", songsArr)
                }
                playlistsArr.put(plObj)
            }

            _syncState.value = SyncState.Progress("正在序列化收藏...", 40)
            val favoritesArr = JSONArray()
            favorites.forEach { favoritesArr.put(songToJson(it)) }

            _syncState.value = SyncState.Progress("正在序列化播放历史...", 60)
            val historyArr = JSONArray()
            playHistory.take(200).forEach { historyArr.put(songToJson(it)) }

            val searchArr = JSONArray()
            searchHistory.take(50).forEach { searchArr.put(it) }

            _syncState.value = SyncState.Progress("正在上传到服务器...", 75)
            val json = JSONObject().apply {
                put("action", "upload")
                put("user_id", userId)
                put("playlists", playlistsArr)
                put("favorites", favoritesArr)
                put("play_history", historyArr)
                put("search_history", searchArr)
            }

            val response = post(json)
            if (response.optBoolean("success")) {
                val stats = response.optJSONObject("stats")
                val msg = if (stats != null) {
                    "同步成功: ${stats.optInt("playlist_count")}个歌单, ${stats.optInt("favorite_count")}首收藏, ${stats.optInt("song_total")}首歌曲"
                } else "同步成功"
                _syncState.value = SyncState.Success(msg)
                true
            } else {
                _syncState.value = SyncState.Error(response.optString("message", "上传失败"))
                false
            }
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("同步失败: ${e.message}")
            false
        }
    }

    /**
     * 分块上传（大数据量，≥200首歌曲）
     * 将歌单、收藏、播放历史分别按 CHUNK_SIZE 首分块上传，
     * 最后通知服务器合并，避免单个请求过大导致服务器返回 HTML 错误
     */
    private fun uploadChunked(
        userId: String,
        playlists: List<Playlist>,
        favorites: List<Song>,
        playHistory: List<Song>,
        searchHistory: List<String>,
    ): Boolean {
        return try {
            // 步骤1: 通知服务器开始分块上传
            _syncState.value = SyncState.Progress("正在初始化分块上传...", 5)
            val startResp = post(JSONObject().apply {
                put("action", "upload_start")
                put("user_id", userId)
            })
            if (!startResp.optBoolean("success")) {
                _syncState.value = SyncState.Error(startResp.optString("message", "初始化失败"))
                return false
            }

            // 构建歌单分块数据：每个歌单的歌曲按 CHUNK_SIZE 切分
            data class PlaylistChunkData(val playlistJson: JSONObject, val chunkIndex: Int, val totalChunks: Int)
            val playlistChunks = mutableListOf<PlaylistChunkData>()
            for (pl in playlists) {
                val songs = pl.songs
                if (songs.isEmpty()) {
                    playlistChunks.add(PlaylistChunkData(
                        JSONObject().apply {
                            put("id", pl.id)
                            put("name", pl.name)
                            put("creator", pl.creator)
                            put("description", pl.description)
                            put("coverUrl", pl.coverUrl)
                            put("songs", JSONArray())
                        }, 0, 1
                    ))
                } else {
                    val numChunks = (songs.size + CHUNK_SIZE - 1) / CHUNK_SIZE
                    for (i in 0 until numChunks) {
                        val from = i * CHUNK_SIZE
                        val to = minOf(from + CHUNK_SIZE, songs.size)
                        val chunkSongs = songs.subList(from, to)
                        playlistChunks.add(PlaylistChunkData(
                            JSONObject().apply {
                                put("id", pl.id)
                                put("name", pl.name)
                                put("creator", pl.creator)
                                put("description", pl.description)
                                put("coverUrl", pl.coverUrl)
                                val songsArr = JSONArray()
                                chunkSongs.forEach { songsArr.put(songToJson(it)) }
                                put("songs", songsArr)
                            }, i, numChunks
                        ))
                    }
                }
            }

            // 构建收藏分块
            val favChunks = mutableListOf<List<Song>>()
            if (favorites.isNotEmpty()) {
                val numFavChunks = (favorites.size + CHUNK_SIZE - 1) / CHUNK_SIZE
                for (i in 0 until numFavChunks) {
                    val from = i * CHUNK_SIZE
                    val to = minOf(from + CHUNK_SIZE, favorites.size)
                    favChunks.add(favorites.subList(from, to))
                }
            }

            // 构建播放历史分块
            val histLimited = playHistory.take(200)
            val histChunks = mutableListOf<List<Song>>()
            if (histLimited.isNotEmpty()) {
                val numHistChunks = (histLimited.size + CHUNK_SIZE - 1) / CHUNK_SIZE
                for (i in 0 until numHistChunks) {
                    val from = i * CHUNK_SIZE
                    val to = minOf(from + CHUNK_SIZE, histLimited.size)
                    histChunks.add(histLimited.subList(from, to))
                }
            }

            // 搜索历史（通常较小，一块即可）
            val searchLimited = searchHistory.take(50)

            // 计算总块数用于进度
            var totalChunks = playlistChunks.size + favChunks.size + histChunks.size
            if (searchLimited.isNotEmpty()) totalChunks++
            Log.d(TAG, "分块上传: $totalChunks 个分块 (歌单${playlistChunks.size}块, 收藏${favChunks.size}块, 历史${histChunks.size}块)")

            // 步骤2: 逐块上传
            var sentChunks = 0

            // 上传歌单分块
            for ((index, chunk) in playlistChunks.withIndex()) {
                val percent = 10 + (sentChunks * 70 / totalChunks.coerceAtLeast(1))
                _syncState.value = SyncState.Progress("正在上传歌单 (${index + 1}/${playlistChunks.size})...", percent)
                val arr = JSONArray()
                arr.put(chunk.playlistJson)
                val resp = post(JSONObject().apply {
                    put("action", "upload_chunk")
                    put("user_id", userId)
                    put("chunk_index", index)
                    put("total_chunks", playlistChunks.size)
                    put("chunk_type", "playlists")
                    put("chunk_data", arr)
                })
                if (!resp.optBoolean("success")) {
                    _syncState.value = SyncState.Error("歌单分块 ${index + 1} 上传失败: ${resp.optString("message")}")
                    return false
                }
                sentChunks++
            }

            // 上传收藏分块
            for ((index, chunk) in favChunks.withIndex()) {
                val percent = 10 + (sentChunks * 70 / totalChunks.coerceAtLeast(1))
                _syncState.value = SyncState.Progress("正在上传收藏 (${index + 1}/${favChunks.size})...", percent)
                val arr = JSONArray()
                chunk.forEach { arr.put(songToJson(it)) }
                val resp = post(JSONObject().apply {
                    put("action", "upload_chunk")
                    put("user_id", userId)
                    put("chunk_index", index)
                    put("total_chunks", favChunks.size)
                    put("chunk_type", "favorites")
                    put("chunk_data", arr)
                })
                if (!resp.optBoolean("success")) {
                    _syncState.value = SyncState.Error("收藏分块 ${index + 1} 上传失败: ${resp.optString("message")}")
                    return false
                }
                sentChunks++
            }

            // 上传播放历史分块
            for ((index, chunk) in histChunks.withIndex()) {
                val percent = 10 + (sentChunks * 70 / totalChunks.coerceAtLeast(1))
                _syncState.value = SyncState.Progress("正在上传播放历史 (${index + 1}/${histChunks.size})...", percent)
                val arr = JSONArray()
                chunk.forEach { arr.put(songToJson(it)) }
                val resp = post(JSONObject().apply {
                    put("action", "upload_chunk")
                    put("user_id", userId)
                    put("chunk_index", index)
                    put("total_chunks", histChunks.size)
                    put("chunk_type", "play_history")
                    put("chunk_data", arr)
                })
                if (!resp.optBoolean("success")) {
                    _syncState.value = SyncState.Error("播放历史分块 ${index + 1} 上传失败: ${resp.optString("message")}")
                    return false
                }
                sentChunks++
            }

            // 上传搜索历史
            if (searchLimited.isNotEmpty()) {
                _syncState.value = SyncState.Progress("正在上传搜索历史...", 85)
                val arr = JSONArray()
                searchLimited.forEach { arr.put(it) }
                val resp = post(JSONObject().apply {
                    put("action", "upload_chunk")
                    put("user_id", userId)
                    put("chunk_index", 0)
                    put("total_chunks", 1)
                    put("chunk_type", "search_history")
                    put("chunk_data", arr)
                })
                if (!resp.optBoolean("success")) {
                    _syncState.value = SyncState.Error("搜索历史上传失败: ${resp.optString("message")}")
                    return false
                }
            }

            // 步骤3: 通知服务器合并所有分块
            _syncState.value = SyncState.Progress("正在合并数据...", 90)
            val finishResp = post(JSONObject().apply {
                put("action", "upload_finish")
                put("user_id", userId)
            })

            if (finishResp.optBoolean("success")) {
                val stats = finishResp.optJSONObject("stats")
                val msg = if (stats != null) {
                    "同步成功: ${stats.optInt("playlist_count")}个歌单, ${stats.optInt("favorite_count")}首收藏, ${stats.optInt("song_total")}首歌曲"
                } else "同步成功"
                _syncState.value = SyncState.Success(msg)
                true
            } else {
                _syncState.value = SyncState.Error(finishResp.optString("message", "合并失败"))
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "分块上传失败", e)
            _syncState.value = SyncState.Error("同步失败: ${e.message}")
            false
        }
    }

    // ═══════════════════════════════════════════
    //  下载同步
    // ═══════════════════════════════════════════

    /**
     * 从服务器拉取数据
     * @return Triple(playlists, favorites, playHistory) 或 null
     */
    suspend fun download(): Triple<List<Playlist>, List<Song>, List<Song>>? = withContext(Dispatchers.IO) {
        val userId = getSyncUserId()
        if (userId.isEmpty()) {
            _syncState.value = SyncState.Error("请先登录同步账号")
            return@withContext null
        }

        _syncState.value = SyncState.Downloading
        try {
            val json = JSONObject().apply {
                put("action", "download")
                put("user_id", userId)
            }

            val response = post(json)
            if (response.optBoolean("success")) {
                _syncState.value = SyncState.Progress("正在解析数据...", 50)
                val data = response.optJSONObject("data") ?: return@withContext run {
                    _syncState.value = SyncState.Error("数据格式错误")
                    null
                }

                val playlists = mutableListOf<Playlist>()
                val playlistsArr = data.optJSONArray("playlists")
                if (playlistsArr != null) {
                    for (i in 0 until playlistsArr.length()) {
                        jsonToPlaylist(playlistsArr.getJSONObject(i))?.let { playlists.add(it) }
                    }
                }

                val favorites = mutableListOf<Song>()
                val favoritesArr = data.optJSONArray("favorites")
                if (favoritesArr != null) {
                    for (i in 0 until favoritesArr.length()) {
                        jsonToSong(favoritesArr.getJSONObject(i))?.let { favorites.add(it) }
                    }
                }

                val playHistory = mutableListOf<Song>()
                val historyArr = data.optJSONArray("play_history")
                if (historyArr != null) {
                    for (i in 0 until historyArr.length()) {
                        jsonToSong(historyArr.getJSONObject(i))?.let { playHistory.add(it) }
                    }
                }

                _syncState.value = SyncState.Success("恢复成功: ${playlists.size}个歌单, ${favorites.size}首收藏")
                Triple(playlists, favorites, playHistory)
            } else {
                _syncState.value = SyncState.Error(response.optString("message", "下载失败"))
                null
            }
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("恢复失败: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════
    //  查询同步状态
    // ═══════════════════════════════════════════

    suspend fun checkStatus(): JSONObject? = withContext(Dispatchers.IO) {
        val userId = getSyncUserId()
        if (userId.isEmpty()) return@withContext null
        try {
            val json = JSONObject().apply {
                put("action", "status")
                put("user_id", userId)
            }
            val response = post(json)
            if (response.optBoolean("success")) response.optJSONObject("meta") else null
        } catch (e: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════

    /** 重置状态 */
    fun resetState() {
        _syncState.value = SyncState.Idle
    }

    /**
     * 发送 POST 请求并解析 JSON 响应
     * 自动检测 HTML 错误页面（服务器超限时会返回 HTML），返回有意义的错误信息而非崩溃
     */
    private fun post(json: JSONObject): JSONObject {
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(SYNC_API_URL)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val respBody = response.body?.string() ?: ""

            // 检查响应是否为 HTML（服务器配置问题或 PHP 错误、nginx 404 等）
            if (isHtmlResponse(respBody)) {
                Log.w(TAG, "HTTP ${response.code}: ${respBody.take(200)}")
                val hint = when (response.code) {
                    404 -> "服务器接口未找到 (HTTP 404)，请确认 sync.php 已部署到服务器"
                    500 -> "服务器内部错误 (HTTP 500)，可能数据量过大"
                    else -> "服务器错误 (HTTP ${response.code})"
                }
                return JSONObject().put("success", false).put("message", hint)
            }

            // 尝试解析 JSON 响应（即使 HTTP 状态码非 200，服务器也可能返回了 JSON 错误信息）
            return try {
                val json = JSONObject(respBody)
                // 如果 HTTP 状态码非 200 但 JSON 中没有 success 字段，补充错误信息
                if (!response.isSuccessful && !json.has("success")) {
                    json.put("success", false)
                    if (!json.has("message")) {
                        json.put("message", "HTTP ${response.code}")
                    }
                }
                json
            } catch (e: Exception) {
                Log.w(TAG, "HTTP ${response.code}: ${respBody.take(200)}")
                JSONObject().put("success", false)
                    .put("message", if (response.isSuccessful) "服务器响应格式错误" else "HTTP ${response.code}")
            }
        }
    }

    /** 检测响应体是否为 HTML 页面（而非 JSON） */
    private fun isHtmlResponse(body: String): Boolean {
        val trimmed = body.trim()
        return trimmed.startsWith("<") || trimmed.startsWith("<!DOCTYPE") ||
               trimmed.startsWith("<html") || trimmed.contains("<head>") ||
               trimmed.contains("<meta ") || trimmed.contains("<title>")
    }

    // ── JSON 序列化 ──

    private fun songToJson(song: Song): JSONObject {
        return JSONObject().apply {
            put("id", song.id)
            put("title", song.title)
            put("artist", song.artist)
            put("album", song.album)
            put("duration", song.duration)
            put("coverUrl", song.coverUrl)
            put("artistPicUrl", song.artistPicUrl)
            put("platform", song.platform)
            put("platformId", song.platformId)
            put("directUrl", song.directUrl)
            put("lrcText", song.lrcText)
            put("lxSourceKey", song.lxSourceKey)
            put("lxPluginId", song.lxPluginId)
            put("musicFreePluginId", song.musicFreePluginId)
            put("pluginRawJson", song.pluginRawJson)
        }
    }

    private fun jsonToSong(json: JSONObject): Song? {
        return try {
            Song(
                id = json.optLong("id", 0),
                title = json.optString("title", ""),
                artist = json.optString("artist", ""),
                album = json.optString("album", ""),
                duration = json.optLong("duration", 0),
                coverUrl = json.optString("coverUrl", ""),
                artistPicUrl = json.optString("artistPicUrl", ""),
                platform = json.optString("platform", ""),
                platformId = json.optString("platformId", ""),
                directUrl = json.optString("directUrl", ""),
                lrcText = json.optString("lrcText", ""),
                lxSourceKey = json.optString("lxSourceKey", ""),
                lxPluginId = json.optString("lxPluginId", ""),
                musicFreePluginId = json.optString("musicFreePluginId", ""),
                pluginRawJson = json.optString("pluginRawJson", ""),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonToPlaylist(json: JSONObject): Playlist? {
        return try {
            val songsArr = json.optJSONArray("songs")
            val songs = mutableListOf<Song>()
            if (songsArr != null) {
                for (i in 0 until songsArr.length()) {
                    jsonToSong(songsArr.getJSONObject(i))?.let { songs.add(it) }
                }
            }
            Playlist(
                id = json.optLong("id", System.currentTimeMillis()),
                name = json.optString("name", "未命名歌单"),
                coverUrl = json.optString("coverUrl", ""),
                creator = json.optString("creator", "我"),
                description = json.optString("description", ""),
                songCount = songs.size,
                songs = songs,
            )
        } catch (_: Exception) {
            null
        }
    }
}
