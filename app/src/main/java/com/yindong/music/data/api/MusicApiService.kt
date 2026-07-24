package com.yindong.music.data.api

import android.util.Log
import com.yindong.music.BuildConfig
import com.yindong.music.data.LocalStorage
import com.yindong.music.data.RemoteConfig
import com.yindong.music.data.model.LyricLine
import com.yindong.music.data.model.LyricWord
import com.yindong.music.data.model.Song
import com.yindong.music.data.model.Playlist
import com.yindong.music.security.MusicPlaybackGate
import com.yindong.music.security.SecurityGuard
import com.lyrics.api.LyricsParser
import com.lyrics.api.LyricsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Random

/**
 * API 返回的音乐 URL 结果
 */
data class MusicUrlResult(
    val url: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val pic: String? = null,
    val directUrl: String? = null,
    val isStream: Boolean = false,
    val error: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class QualityInfo(
    val url: String,
    val size: Long = 0L,
    val type: String = "",
)

data class NeteaseApi2Detail(
    val playUrl: String?,
    val qualities: Map<String, QualityInfo>,
    val lyric: String?,
    val kLyric: String?,
    val level: String?,
)

/**
 * 推荐歌单数据
 */
data class RecommendPlaylist(
    val name: String,
    val coverUrl: String,
    val playCount: Long,
    val platform: String,
    val playlistId: String,
)


/**
 * 音乐网络请求服务 (服务端模式)
 *
 * 所有API逻辑已部署到服务器，客户端仅做HTTP转发。
 * 服务器地址在 App设置 → 服务器地址 中配置。
 */
object MusicApiService {

    private const val TAG = "MusicAPI"

    // ── 网易云歌单API（NeteaseCloudMusicApi） ──
    private const val NETEASE_PLAYLIST_API_BASE = "http://38.76.202.118:3000"

    // 当前默认音乐后端端口：开源版默认不内置
    internal const val SERVER_HTTPS_URL = ""
    internal const val SERVER_HTTP_URL = ""
    internal const val SERVER_URL = ""
    private const val TRUSTED_HTTPS_HOST = ""
    /** 候选地址池：开源版默认不内置后端地址 */
    private val SERVER_BASE_URLS = emptyList<String>()
    @Volatile
    private var remoteServerBaseUrls: List<String>? = null
    @Volatile
    private var preferredServerUrl: String = SERVER_URL

    /** 服务端鉴权密钥（由构建参数注入，避免硬编码在源码） */
    private val API_AUTH_KEY: String get() = BuildConfig.API_AUTH_KEY
    /** 服务端应用标识（由构建参数注入） */
    private val API_APP_ID: String get() = BuildConfig.API_APP_ID
    private const val CHALLENGE_TOKEN_REFRESH_SKEW_SECONDS = 20L
    private const val JSON_MEDIA_TYPE = "application/json"
    @Volatile private var challengeSessionToken: String = ""
    @Volatile private var challengeSessionExpireAt: Long = 0L
    private val challengeSessionLock = Any()

    // ── DNS缓存 (避免重复DNS解析) ──
    private val dnsCache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
    private const val DNS_TTL_MS = 300_000L // 5分钟

    private val cachedDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            val cached = dnsCache[hostname]
            if (cached != null && now - cached.second < DNS_TTL_MS) {
                return cached.first
            }
            val addresses = Dns.SYSTEM.lookup(hostname)
            dnsCache[hostname] = Pair(addresses, now)
            return addresses
        }
    }

    // ── 极速HTTP客户端 ──
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .dns(cachedDns)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // ── 长超时客户端 (热歌榜等需要服务端并发的接口, 复用连接池) ──
    private val longClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val searchIdCounter = AtomicLong(100000)

    // ── 内存缓存 (减少重复请求) ──
    private data class CacheEntry<T>(val data: T, val expireAt: Long)
    private val searchCache = ConcurrentHashMap<String, CacheEntry<List<Song>>>()
    private val lyricsCache = ConcurrentHashMap<String, CacheEntry<List<LyricLine>>>()
    private val playUrlCache = ConcurrentHashMap<String, CacheEntry<MusicUrlResult>>()
    private const val SEARCH_CACHE_TTL = 300_000L  // 搜索缓存5分钟
    private const val LYRICS_CACHE_TTL = 600_000L  // 歌词缓存10分钟
    private const val PLAY_CACHE_TTL = 180_000L    // 播放URL缓存3分钟 (QQ等平台CDN链接有效期较短)
    private const val HOTCHART_CACHE_TTL = 300_000L // 热歌榜缓存5分钟
    private val hotChartCache = ConcurrentHashMap<String, CacheEntry<List<Song>>>()
    private const val RECOMMEND_PLAYLIST_COVER_CACHE_KEY = "recommend_playlist_cover_cache_v1"
    private const val RECOMMEND_PLAYLIST_COVER_CACHE_MAX = 2000
    private const val RECOMMEND_PLAYLIST_COVER_PERSIST_BATCH = 8
    @Volatile private var recommendCoverCacheLoaded = false
    private val recommendCoverCache = ConcurrentHashMap<String, String>()
    private val recommendCoverOrder = LinkedHashMap<String, Long>()
    private val recommendCoverLock = Any()
    @Volatile private var recommendCoverDirtyCount = 0
    private const val SONG_COVER_CACHE_KEY = "song_cover_cache_v1"
    private const val SONG_COVER_CACHE_MAX_ENTRIES = 4000
    private const val SONG_COVER_CACHE_PERSIST_BATCH = 12
    @Volatile private var songCoverCacheLoaded = false
    private val songCoverCache = ConcurrentHashMap<String, String>()
    private val songCoverCacheOrder = LinkedHashMap<String, Long>()
    private val songCoverCacheLock = Any()
    @Volatile private var songCoverDirtyCount = 0

    private fun songCoverKey(platform: String, platformId: String): String {
        val p = platform.trim()
        val id = platformId.trim()
        if (p.isEmpty() || id.isEmpty()) return ""
        return "$p::$id"
    }

    private fun ensureSongCoverCacheLoaded() {
        if (songCoverCacheLoaded) return
        synchronized(songCoverCacheLock) {
            if (songCoverCacheLoaded) return
            val raw = LocalStorage.loadString(SONG_COVER_CACHE_KEY)
            if (raw.isNotBlank()) {
                try {
                    val json = JSONObject(raw)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = normalizeImageUrl(json.optString(key, ""))
                        if (value.isNotBlank()) {
                            songCoverCache[key] = value
                            songCoverCacheOrder[key] = System.currentTimeMillis()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "加载封面缓存失败: ${e.message}")
                }
            }
            songCoverCacheLoaded = true
        }
    }

    private fun trimSongCoverCacheIfNeededLocked() {
        while (songCoverCacheOrder.size > SONG_COVER_CACHE_MAX_ENTRIES) {
            val eldestKey = songCoverCacheOrder.entries.firstOrNull()?.key ?: break
            songCoverCacheOrder.remove(eldestKey)
            songCoverCache.remove(eldestKey)
        }
    }

    private fun persistSongCoverCacheLocked() {
        try {
            val json = JSONObject()
            songCoverCacheOrder.keys.forEach { key ->
                val value = songCoverCache[key]
                if (!value.isNullOrBlank()) json.put(key, value)
            }
            LocalStorage.saveString(SONG_COVER_CACHE_KEY, json.toString())
            songCoverDirtyCount = 0
        } catch (e: Exception) {
            Log.w(TAG, "保存封面缓存失败: ${e.message}")
        }
    }

    private fun cacheSongCover(platform: String, platformId: String, coverUrl: String, forcePersist: Boolean = false) {
        val key = songCoverKey(platform, platformId)
        if (key.isBlank()) return
        val normalized = normalizeImageUrl(coverUrl)
        if (normalized.isBlank()) return
        ensureSongCoverCacheLoaded()
        synchronized(songCoverCacheLock) {
            val old = songCoverCache[key]
            if (old == normalized) {
                songCoverCacheOrder.remove(key)
                songCoverCacheOrder[key] = System.currentTimeMillis()
                return
            }
            songCoverCache[key] = normalized
            songCoverCacheOrder.remove(key)
            songCoverCacheOrder[key] = System.currentTimeMillis()
            trimSongCoverCacheIfNeededLocked()
            songCoverDirtyCount++
            if (forcePersist || songCoverDirtyCount >= SONG_COVER_CACHE_PERSIST_BATCH) {
                persistSongCoverCacheLocked()
            }
        }
    }

    private fun flushSongCoverCacheIfDirty() {
        ensureSongCoverCacheLoaded()
        synchronized(songCoverCacheLock) {
            if (songCoverDirtyCount > 0) persistSongCoverCacheLocked()
        }
    }

    private fun cachedSongCover(platform: String, platformId: String): String {
        val key = songCoverKey(platform, platformId)
        if (key.isBlank()) return ""
        ensureSongCoverCacheLoaded()
        val value = songCoverCache[key].orEmpty()
        if (value.isNotBlank()) {
            synchronized(songCoverCacheLock) {
                songCoverCacheOrder.remove(key)
                songCoverCacheOrder[key] = System.currentTimeMillis()
            }
        }
        return value
    }

    private fun rememberSongCovers(songs: List<Song>, forcePersist: Boolean = false) {
        if (songs.isEmpty()) return
        songs.forEach { song ->
            if (song.coverUrl.isNotBlank()) {
                cacheSongCover(song.platform, song.platformId, song.coverUrl, forcePersist = false)
            }
        }
        if (forcePersist) flushSongCoverCacheIfDirty()
    }

    private fun recommendCoverKey(platform: String, playlistId: String): String {
        val p = platform.trim()
        val id = playlistId.trim()
        if (p.isBlank() || id.isBlank()) return ""
        return "$p::$id"
    }

    private fun ensureRecommendCoverCacheLoaded() {
        if (recommendCoverCacheLoaded) return
        synchronized(recommendCoverLock) {
            if (recommendCoverCacheLoaded) return
            val raw = LocalStorage.loadString(RECOMMEND_PLAYLIST_COVER_CACHE_KEY)
            if (raw.isNotBlank()) {
                try {
                    val obj = JSONObject(raw)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val cover = normalizeImageUrl(obj.optString(key, ""))
                        if (cover.isNotBlank()) {
                            recommendCoverCache[key] = cover
                            recommendCoverOrder[key] = System.currentTimeMillis()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "加载推荐歌单封面缓存失败: ${e.message}")
                }
            }
            recommendCoverCacheLoaded = true
        }
    }

    private fun trimRecommendCoverCacheLocked() {
        while (recommendCoverOrder.size > RECOMMEND_PLAYLIST_COVER_CACHE_MAX) {
            val eldest = recommendCoverOrder.entries.firstOrNull()?.key ?: break
            recommendCoverOrder.remove(eldest)
            recommendCoverCache.remove(eldest)
        }
    }

    private fun persistRecommendCoverCacheLocked() {
        try {
            val json = JSONObject()
            recommendCoverOrder.keys.forEach { key ->
                val value = recommendCoverCache[key]
                if (!value.isNullOrBlank()) json.put(key, value)
            }
            LocalStorage.saveString(RECOMMEND_PLAYLIST_COVER_CACHE_KEY, json.toString())
            recommendCoverDirtyCount = 0
        } catch (e: Exception) {
            Log.w(TAG, "保存推荐歌单封面缓存失败: ${e.message}")
        }
    }

    private fun cacheRecommendCover(platform: String, playlistId: String, coverUrl: String, forcePersist: Boolean = false) {
        val key = recommendCoverKey(platform, playlistId)
        if (key.isBlank()) return
        val normalized = normalizeImageUrl(coverUrl)
        if (normalized.isBlank()) return
        ensureRecommendCoverCacheLoaded()
        synchronized(recommendCoverLock) {
            val old = recommendCoverCache[key]
            if (old == normalized) {
                recommendCoverOrder.remove(key)
                recommendCoverOrder[key] = System.currentTimeMillis()
                return
            }
            recommendCoverCache[key] = normalized
            recommendCoverOrder.remove(key)
            recommendCoverOrder[key] = System.currentTimeMillis()
            trimRecommendCoverCacheLocked()
            recommendCoverDirtyCount++
            if (forcePersist || recommendCoverDirtyCount >= RECOMMEND_PLAYLIST_COVER_PERSIST_BATCH) {
                persistRecommendCoverCacheLocked()
            }
        }
    }

    private fun cachedRecommendCover(platform: String, playlistId: String): String {
        val key = recommendCoverKey(platform, playlistId)
        if (key.isBlank()) return ""
        ensureRecommendCoverCacheLoaded()
        val value = recommendCoverCache[key].orEmpty()
        if (value.isNotBlank()) {
            synchronized(recommendCoverLock) {
                recommendCoverOrder.remove(key)
                recommendCoverOrder[key] = System.currentTimeMillis()
            }
        }
        return value
    }

    private fun flushRecommendCoverCacheIfDirty() {
        ensureRecommendCoverCacheLoaded()
        synchronized(recommendCoverLock) {
            if (recommendCoverDirtyCount > 0) persistRecommendCoverCacheLocked()
        }
    }

    private fun <T> cacheGet(map: ConcurrentHashMap<String, CacheEntry<T>>, key: String): T? {
        val entry = map[key] ?: return null
        return if (System.currentTimeMillis() < entry.expireAt) entry.data
        else { map.remove(key); null }
    }

    private fun <T> cacheSet(map: ConcurrentHashMap<String, CacheEntry<T>>, key: String, data: T, ttl: Long) {
        map[key] = CacheEntry(data, System.currentTimeMillis() + ttl)
        // 简单清理：超过500条时删除过期的
        if (map.size > 500) {
            val now = System.currentTimeMillis()
            val expiredKeys = map.entries.filter { now >= it.value.expireAt }.map { it.key }
            expiredKeys.forEach { map.remove(it) }
        }
    }

    // ── 服务器熔断器 (连续失败后跳过, 避免空等) ──
    private val serverConsecutiveFailures = AtomicInteger(0)
    @Volatile private var serverCircuitOpenUntil = 0L
    private val halfOpenProbing = java.util.concurrent.atomic.AtomicBoolean(false)
    private const val CIRCUIT_BREAKER_THRESHOLD = 2       // 连续失败2次即熔断
    private const val CIRCUIT_BREAKER_COOLDOWN = 300_000L // 熔断后5分钟不再尝试

    private fun isServerAvailable(): Boolean {
        if (serverConsecutiveFailures.get() < CIRCUIT_BREAKER_THRESHOLD) return true
        if (System.currentTimeMillis() > serverCircuitOpenUntil) {
            // 冷却期过, 半开状态: CAS确保只允许一个协程探测
            if (halfOpenProbing.compareAndSet(false, true)) {
                serverConsecutiveFailures.set(CIRCUIT_BREAKER_THRESHOLD - 1)
                return true
            }
        }
        return false
    }

    private fun markServerFailed() {
        halfOpenProbing.set(false)
        val count = serverConsecutiveFailures.incrementAndGet()
        if (count >= CIRCUIT_BREAKER_THRESHOLD) {
            serverCircuitOpenUntil = System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN
            Log.w(TAG, "服务器熔断: 连续${count}次失败, ${CIRCUIT_BREAKER_COOLDOWN/1000}s后重试")
        }
    }

    // ── 通用HTTP工具 (多端口自动切换) ──

    private fun currentBasePool(): List<String> {
        val remote = remoteServerBaseUrls
        val localServerUrl = LocalStorage.loadServerUrl().trim().trimEnd('/')
        val localApiHost = LocalStorage.loadApiHost().trim().trimEnd('/')
        val basePool = when {
            !remote.isNullOrEmpty() -> remote
            localServerUrl.isNotEmpty() -> listOf(localServerUrl)
            localApiHost.isNotEmpty() -> listOf(localApiHost)
            else -> SERVER_BASE_URLS
        }
        val normalized = basePool.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.distinct()
        if (normalized.isEmpty()) return emptyList()
        if (!RemoteConfig.runtimeRequireHttpsOnly) return normalized
        return normalized.filter { it.startsWith("https://") }
    }

    internal fun serverBaseUrls(): List<String> {
        val pool = currentBasePool()
        if (pool.isEmpty()) return emptyList()
        val preferred = preferredServerUrl.trim().trimEnd('/')
        val first = when {
            preferred.isNotEmpty() && pool.contains(preferred) -> preferred
            else -> pool.firstOrNull { it.startsWith("https://") } ?: pool.first()
        }
        return listOf(first) + pool.filter { it != first }
    }

    internal fun applyServerBaseUrls(urls: List<String>) {
        val normalized = urls.mapNotNull { raw ->
            val v = raw.trim().trimEnd('/')
            if (isAllowedServerBaseUrl(v)) v else null
        }.distinct()
        val effective = if (RemoteConfig.runtimeRequireHttpsOnly) {
            normalized.filter { it.startsWith("https://") }
        } else {
            normalized
        }
        if (effective.isEmpty()) {
            Log.w(TAG, "忽略不安全后端地址下发")
            return
        }
        remoteServerBaseUrls = effective
        if (!effective.contains(preferredServerUrl)) {
            preferredServerUrl = effective.first()
        }
        Log.d(TAG, "已应用后端端口池: ${effective.joinToString()}")
    }

    internal fun isAllowedServerBaseUrl(raw: String): Boolean {
        val v = raw.trim().trimEnd('/')
        if (TRUSTED_HTTPS_HOST.isBlank()) {
            return v.startsWith("https://") || v.startsWith("http://")
        }
        if (v.startsWith("https://")) {
            return v.startsWith("https://$TRUSTED_HTTPS_HOST") || v.startsWith("https://$TRUSTED_HTTPS_HOST:")
        }
        if (v.startsWith("http://")) {
            return v.startsWith("http://$TRUSTED_HTTPS_HOST") || v.startsWith("http://$TRUSTED_HTTPS_HOST:")
        }
        return false
    }

    internal fun markServerHealthy(baseUrl: String) {
        if (baseUrl.isNotBlank()) {
            preferredServerUrl = baseUrl
        }
        serverConsecutiveFailures.set(0)
        halfOpenProbing.set(false)
    }

    private fun buildServerUrl(baseUrl: String, path: String): String {
        return "${baseUrl.trimEnd('/')}$path"
    }
    private fun isThirdPartyCleartextUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || !url.startsWith("http://")) return false
        return try {
            val host = URI(url).host?.trim().orEmpty()
            host.isNotEmpty() && host != TRUSTED_HTTPS_HOST
        } catch (_: Exception) {
            false
        }
    }
    private fun normalizeImageUrl(raw: String?): String {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        if (value.equals("null", ignoreCase = true) || value.equals("undefined", ignoreCase = true)) return ""
        value = value
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("{size}", "500")
            .replace("{w}", "500")
            .replace("{h}", "500")
            .replace("{width}", "500")
            .replace("{height}", "500")
            .trim()
        if (value.startsWith("data:image", ignoreCase = true)) return value
        if (value.startsWith("//")) value = "https:$value"
        if (value.startsWith("/")) {
            val base = serverBaseUrls().firstOrNull() ?: preferredServerUrl
            if (base.isBlank()) return ""
            return "${base.trimEnd('/')}$value"
        }
        if (value.startsWith("http://", ignoreCase = true)) {
            val host = try { URI(value).host?.trim().orEmpty() } catch (_: Exception) { "" }
            if (host.isNotEmpty() && host != TRUSTED_HTTPS_HOST) {
                value = value.replaceFirst("http://", "https://")
            }
        }
        return if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) value else ""
    }

    private fun firstNonBlank(vararg values: String?): String {
        for (value in values) {
            val v = value?.trim().orEmpty()
            if (v.isNotEmpty() && !v.equals("null", ignoreCase = true) && !v.equals("undefined", ignoreCase = true)) return v
        }
        return ""
    }

    private fun fetchKugouOfficialPlaylistCoverByName(playlistNameRaw: String): String {
        val playlistName = playlistNameRaw.trim()
        if (playlistName.isBlank()) return ""
        val urls = listOf(
            "https://mobilecdn.kugou.com/api/v3/search/special?keyword=${URLEncoder.encode(playlistName, "UTF-8")}&page=1&pagesize=10",
            "https://mobilecdnbj.kugou.com/api/v3/search/special?keyword=${URLEncoder.encode(playlistName, "UTF-8")}&page=1&pagesize=10",
        )
        for (url in urls) {
            val body = searchGet(url, mapOf("Referer" to "https://www.kugou.com/")) ?: continue
            val json = parseLooseJsonObject(body) ?: continue
            val data = json.optJSONObject("data") ?: continue
            val info = data.optJSONArray("info") ?: continue
            for (i in 0 until info.length()) {
                val item = info.optJSONObject(i) ?: continue
                val cover = firstValidImageUrl(
                    item.optString("imgurl", ""),
                    item.optString("img", ""),
                    item.optString("cover", ""),
                    item.optString("cover_url", ""),
                )
                if (cover.isNotBlank()) return cover
            }
        }
        return ""
    }

    private fun firstValidImageUrl(vararg candidates: String?): String {
        for (candidate in candidates) {
            val normalized = normalizeImageUrl(candidate)
            if (normalized.isNotEmpty()) return normalized
        }
        return ""
    }

    private fun qqAlbumCoverUrl(albumMid: String): String =
        if (albumMid.isBlank()) "" else "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albumMid}.jpg"

    private fun extractArtistPicUrl(item: JSONObject): String {
        val artistObj = item.optJSONObject("artist")
            ?: item.optJSONObject("artistInfo")
            ?: item.optJSONObject("singer")
        return firstValidImageUrl(
            item.optString("artistPicUrl", ""),
            item.optString("artistPic", ""),
            item.optString("singerPic", ""),
            item.optString("avatar", ""),
            artistObj?.optString("picUrl", ""),
            artistObj?.optString("avatar", ""),
            artistObj?.optString("img1v1Url", ""),
        )
    }

    private fun extractSongCoverUrl(item: JSONObject): String {
        val album = item.optJSONObject("album")
            ?: item.optJSONObject("al")
            ?: item.optJSONObject("albumInfo")
        val image = item.optJSONObject("image")
        val extra = item.optJSONObject("extra")
        val direct = firstValidImageUrl(
            item.optString("coverUrl", ""),
            item.optString("cover_url", ""),
            item.optString("cover", ""),
            item.optString("picUrl", ""),
            item.optString("pic_url", ""),
            item.optString("pic", ""),
            item.optString("imgUrl", ""),
            item.optString("img_url", ""),
            item.optString("img", ""),
            item.optString("imageUrl", ""),
            item.optString("image", ""),
            item.optString("thumbnail", ""),
            item.optString("artworkUrl", ""),
            item.optString("artwork", ""),
            item.optString("albumPicUrl", ""),
            item.optString("albumPic", ""),
            album?.optString("coverUrl", ""),
            album?.optString("picUrl", ""),
            album?.optString("pic", ""),
            album?.optString("imgUrl", ""),
            album?.optString("image", ""),
            album?.optString("blurPicUrl", ""),
            image?.optString("url", ""),
            image?.optString("src", ""),
            extra?.optString("coverUrl", ""),
        )
        if (direct.isNotEmpty()) return direct
        val albumMid = firstNonBlank(
            item.optString("albummid", ""),
            item.optString("albumMid", ""),
            album?.optString("mid", ""),
            album?.optString("albumMid", ""),
        )
        return firstValidImageUrl(qqAlbumCoverUrl(albumMid))
    }

    private fun extractPlaylistCoverUrl(item: JSONObject): String {
        val dirInfo = item.optString("dirinfo", "").takeIf { it.isNotBlank() }?.let {
            try { JSONObject(it) } catch (_: Exception) { null }
        }
        return firstValidImageUrl(
            item.optString("coverUrl", ""),
            item.optString("cover_url", ""),
            item.optString("cover", ""),
            item.optString("logo", ""),
            item.optString("picUrl", ""),
            item.optString("pic_url", ""),
            item.optString("pic", ""),
            item.optString("imgUrl", ""),
            item.optString("img_url", ""),
            item.optString("img", ""),
            item.optString("imageUrl", ""),
            item.optString("image", ""),
            item.optString("thumbnail", ""),
            dirInfo?.optString("picurl", ""),
            dirInfo?.optString("coverUrl", ""),
        )
    }

    private fun choosePlayableUrl(streamParam: String, directUrl: String?, rawUrl: String?): Pair<String?, Boolean> {
        val preferProxy = streamParam == "1"
        val shouldAvoidThirdPartyCleartext = isThirdPartyCleartextUrl(directUrl) && !rawUrl.isNullOrBlank()
        val bestUrl = when {
            preferProxy && !rawUrl.isNullOrBlank() -> rawUrl
            shouldAvoidThirdPartyCleartext -> rawUrl
            !directUrl.isNullOrBlank() -> directUrl
            else -> rawUrl
        }
        val isStream = !bestUrl.isNullOrBlank() && !rawUrl.isNullOrBlank() && bestUrl == rawUrl && rawUrl != directUrl
        if (shouldAvoidThirdPartyCleartext) {
            Log.w(TAG, "检测到第三方明文直链，已切换服务端代理流地址")
        }
        return bestUrl to isStream
    }
    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    private fun hmacSha256(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isChallengeSessionValid(): Boolean {
        if (challengeSessionToken.isBlank()) return false
        return nowSeconds() + CHALLENGE_TOKEN_REFRESH_SKEW_SECONDS < challengeSessionExpireAt
    }

    private fun parseApiDataBody(body: String?): JSONObject? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = JSONObject(body)
            if (root.has("code") && root.optInt("code", 0) != 200) return null
            root.optJSONObject("data") ?: root
        } catch (_: Exception) {
            null
        }
    }

    private fun requestChallengeSession(appKey: String, deviceId: String): Pair<String, Long>? {
        for (baseUrl in serverBaseUrls()) {
            try {
                val challengeBody = JSONObject().put("device_id", deviceId).toString()
                val challengeRequest = attachServerAuthHeaders(
                    Request.Builder()
                        .url(buildServerUrl(baseUrl, "/api/auth/challenge"))
                        .post(RequestBody.create(JSON_MEDIA_TYPE.toMediaTypeOrNull(), challengeBody)),
                    includeChallengeToken = false
                ).build()
                val challengeRaw = client.newCall(challengeRequest).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()
                }
                val challengeData = parseApiDataBody(challengeRaw) ?: continue
                val nonce = challengeData.optString("nonce", "").trim()
                if (nonce.isBlank()) continue

                val ts = nowSeconds().toString()
                val responseSign = hmacSha256(appKey, "$nonce:$ts:$deviceId")
                val sessionBody = JSONObject().apply {
                    put("nonce", nonce)
                    put("client_ts", ts)
                    put("response", responseSign)
                    put("device_id", deviceId)
                }.toString()
                val sessionRequest = attachServerAuthHeaders(
                    Request.Builder()
                        .url(buildServerUrl(baseUrl, "/api/auth/session"))
                        .post(RequestBody.create(JSON_MEDIA_TYPE.toMediaTypeOrNull(), sessionBody)),
                    includeChallengeToken = false
                ).build()
                val sessionRaw = client.newCall(sessionRequest).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()
                }
                val sessionData = parseApiDataBody(sessionRaw) ?: continue
                val token = sessionData.optString("session_token", "").trim()
                if (token.isBlank()) continue
                val expireAt = sessionData.optLong("expires_at", 0L).takeIf { it > 0 }
                    ?: (nowSeconds() + sessionData.optLong("ttl_seconds", 300L))
                markServerHealthy(baseUrl)
                return token to expireAt
            } catch (e: Exception) {
                Log.w(TAG, "challenge session 请求失败($baseUrl): ${e.message}")
            }
        }
        return null
    }

    private fun ensureChallengeSession(forceRefresh: Boolean = false): String? {
        val appId = API_APP_ID.trim()
        val appKey = API_AUTH_KEY.trim()
        val deviceId = LocalStorage.loadOrCreateDeviceId().trim()
        if (appId.isBlank() || appKey.isBlank() || deviceId.isBlank()) return null
        if (!forceRefresh && isChallengeSessionValid()) return challengeSessionToken
        synchronized(challengeSessionLock) {
            if (!forceRefresh && isChallengeSessionValid()) return challengeSessionToken
            val session = requestChallengeSession(appKey, deviceId) ?: run {
                challengeSessionToken = ""
                challengeSessionExpireAt = 0L
                return null
            }
            challengeSessionToken = session.first
            challengeSessionExpireAt = session.second
            return challengeSessionToken
        }
    }

    internal fun attachServerAuthHeaders(
        builder: Request.Builder,
        includeChallengeToken: Boolean = true,
        forceRefreshChallenge: Boolean = false,
    ): Request.Builder {
        builder.header("User-Agent", "CloudMusic/3.0.4")
            .header("Connection", "keep-alive")
        val appId = API_APP_ID.trim()
        if (appId.isNotEmpty()) {
            builder.header("X-App-Id", appId)
        }
        val key = API_AUTH_KEY.trim()
        if (key.isNotEmpty()) {
            builder.header("X-App-Key", key)
            builder.header("X-Auth-Key", key)
        } else {
            Log.e(TAG, "API_AUTH_KEY 为空：当前构建包未注入服务端密钥，受保护接口将返回应用密钥错误")
        }
        val deviceId = LocalStorage.loadOrCreateDeviceId().trim()
        if (deviceId.isNotEmpty()) {
            builder.header("X-Device-Id", deviceId)
        }
        if (includeChallengeToken) {
            val challengeToken = ensureChallengeSession(forceRefreshChallenge)
            if (!challengeToken.isNullOrBlank()) {
                builder.header("X-Challenge-Token", challengeToken)
            }
        }
        return builder
    }

    private fun serverGet(path: String): String? = serverGet(path, client, retriesPerBase = 1)

    private fun serverGet(path: String, httpClient: OkHttpClient, retriesPerBase: Int = 1): String? {
        var lastBody: String? = null
        var lastError: Exception? = null
        for (baseUrl in serverBaseUrls()) {
            var refreshAttempted = false
            repeat(retriesPerBase.coerceAtLeast(1) + 1) {
                try {
                    val request = attachServerAuthHeaders(
                        Request.Builder().url(buildServerUrl(baseUrl, path)),
                        includeChallengeToken = true,
                        forceRefreshChallenge = refreshAttempted
                    ).build()
                    httpClient.newCall(request).execute().use { response ->
                        val body = response.body?.string()
                        if (response.isSuccessful) {
                            markServerHealthy(baseUrl)
                            return body
                        }
                        if ((response.code == 401 || response.code == 403) && !refreshAttempted) {
                            refreshAttempted = true
                            challengeSessionToken = ""
                            challengeSessionExpireAt = 0L
                            return@repeat
                        }
                        lastBody = body
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        if (lastError != null) {
            Log.w(TAG, "serverGet failover exhausted: ${lastError?.message}")
        }
        return lastBody
    }

    private fun serverPost(path: String, jsonBody: String): String? = serverPost(path, jsonBody, client, retriesPerBase = 1)

    private fun serverPost(path: String, jsonBody: String, httpClient: OkHttpClient, retriesPerBase: Int = 1): String? {
        var lastBody: String? = null
        var lastError: Exception? = null
        for (baseUrl in serverBaseUrls()) {
            var refreshAttempted = false
            repeat(retriesPerBase.coerceAtLeast(1) + 1) {
                try {
                    val request = attachServerAuthHeaders(
                        Request.Builder().url(buildServerUrl(baseUrl, path))
                            .post(RequestBody.create(JSON_MEDIA_TYPE.toMediaTypeOrNull(), jsonBody)),
                        includeChallengeToken = true,
                        forceRefreshChallenge = refreshAttempted
                    ).build()
                    httpClient.newCall(request).execute().use { response ->
                        val body = response.body?.string()
                        if (response.isSuccessful) {
                            markServerHealthy(baseUrl)
                            return body
                        }
                        if ((response.code == 401 || response.code == 403) && !refreshAttempted) {
                            refreshAttempted = true
                            challengeSessionToken = ""
                            challengeSessionExpireAt = 0L
                            return@repeat
                        }
                        lastBody = body
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        if (lastError != null) {
            Log.w(TAG, "serverPost failover exhausted: ${lastError?.message}")
        }
        return lastBody
    }

    /** 从服务器JSON数组解析歌曲列表 */
    private fun parseSongArray(jsonStr: String?): List<Song> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        return try {
            ensureSongCoverCacheLoaded()
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("data") ?: return emptyList()
            val results = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val platform = item.optString("platform", "")
                val platformId = item.optString("platformId", "")
                val artistPic = extractArtistPicUrl(item)
                val cover = firstValidImageUrl(
                    extractSongCoverUrl(item),
                    cachedSongCover(platform, platformId),
                    artistPic
                )
                results.add(Song(
                    id = item.optLong("id", searchIdCounter.incrementAndGet()),
                    title = item.optString("title", ""),
                    artist = item.optString("artist", "未知"),
                    album = item.optString("album", ""),
                    duration = item.optLong("duration", 0),
                    coverUrl = cover,
                    artistPicUrl = artistPic,
                    platform = platform,
                    platformId = platformId,
                    directUrl = item.optString("directUrl", ""),
                    lrcText = item.optString("lrcText", ""),
                ))
            }
            rememberSongCovers(results, forcePersist = false)
            results
        } catch (e: Exception) {
            Log.e(TAG, "解析歌曲列表失败", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════════
    //  热歌榜
    // ═══════════════════════════════════════════

    /**
     * 一次获取全部平台热歌榜 (服务端并发合并, 更高效)
     * 使用更长超时, 因为服务端需要并发请求4个平台
     */
    suspend fun fetchAllHotChart(): List<Song> = withContext(Dispatchers.IO) {
        val cacheKey = "hotchart:all"
        cacheGet(hotChartCache, cacheKey)?.let { return@withContext it }

        // ① 尝试合并接口
        val body = serverGet("/api/hotchart?platform=all&num=50", longClient, retriesPerBase = 2)
        val songs = parseSongArray(body)
        Log.d(TAG, "热歌榜(all): ${songs.size} 首")
        if (songs.isNotEmpty()) {
            cacheSet(hotChartCache, cacheKey, songs, HOTCHART_CACHE_TTL)
            return@withContext songs
        }

        // ② Fallback: 并发请求各平台
        Log.w(TAG, "合并接口返回空，尝试各平台单独拉取")
        val platforms = listOf("qq" to 15, "netease" to 15, "kugou" to 10, "kuwo" to 10)
        val fallback = mutableListOf<Song>()
        for ((platform, num) in platforms) {
            try {
                val fb = parseSongArray(serverGet("/api/hotchart?platform=$platform&num=$num", longClient, retriesPerBase = 1))
                fallback.addAll(fb)
                Log.d(TAG, "热歌榜($platform): ${fb.size} 首")
            } catch (e: Exception) {
                Log.w(TAG, "热歌榜($platform) 失败: ${e.message}")
            }
        }
        if (fallback.isNotEmpty()) cacheSet(hotChartCache, cacheKey, fallback, HOTCHART_CACHE_TTL)
        else Log.w(TAG, "获取热歌榜全部失败")
        fallback
    }

    suspend fun fetchNeteaseHotChart(): List<Song> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode("netease", "UTF-8")
            parseSongArray(serverGet("/api/hotchart?platform=$encoded"))
        } catch (e: Exception) {
            Log.e(TAG, "获取网易云热歌榜失败", e); emptyList()
        }
    }

    suspend fun fetchKuwoHotChart(): List<Song> = withContext(Dispatchers.IO) {
        try {
            parseSongArray(serverGet("/api/hotchart?platform=kuwo"))
        } catch (e: Exception) {
            Log.e(TAG, "获取酷我热歌榜失败", e); emptyList()
        }
    }

    suspend fun fetchKugouHotChart(): List<Song> = withContext(Dispatchers.IO) {
        try {
            parseSongArray(serverGet("/api/hotchart?platform=kugou"))
        } catch (e: Exception) {
            Log.e(TAG, "获取酷狗热歌榜失败", e); emptyList()
        }
    }

    suspend fun fetchQQHotChart(num: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        try {
            parseSongArray(serverGet("/api/hotchart?platform=qq&num=$num"))
        } catch (e: Exception) {
            Log.e(TAG, "获取QQ热歌榜失败", e); emptyList()
        }
    }

    suspend fun fetchNewSongs(): List<Song> = withContext(Dispatchers.IO) {
        val cacheKey = "new:songs"
        cacheGet(hotChartCache, cacheKey)?.let { return@withContext it }
        try {
            val body = serverGet("/api/hotchart?platform=netease&num=50&listType=newsong", longClient, retriesPerBase = 2)
            val songs = parseSongArray(body)
            if (songs.isNotEmpty()) {
                Log.d(TAG, "新歌速递(服务器): ${songs.size} 首")
                cacheSet(hotChartCache, cacheKey, songs, HOTCHART_CACHE_TTL)
                return@withContext songs
            }
        } catch (e: Exception) {
            Log.w(TAG, "服务器新歌速递失败", e)
        }
        emptyList()
    }

    suspend fun fetchNeteaseHighQualityPlaylists(cat: String = "全部", limit: Int = 30): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val catEncoded = URLEncoder.encode(cat, "UTF-8")
            val url = "$NETEASE_PLAYLIST_API_BASE/top/playlist/highquality?cat=$catEncoded&limit=$limit"
            val body = searchGet(url) ?: return@withContext emptyList()
            val json = parseLooseJsonObject(body) ?: return@withContext emptyList()
            if (json.optInt("code", 0) != 200) return@withContext emptyList()
            val playlistsArr = json.optJSONArray("playlists") ?: return@withContext emptyList()
            val result = mutableListOf<Playlist>()
            for (i in 0 until playlistsArr.length()) {
                val pl = playlistsArr.getJSONObject(i)
                result.add(
                    Playlist(
                        id = pl.optLong("id", 0),
                        name = pl.optString("name", ""),
                        coverUrl = pl.optString("coverImgUrl", ""),
                        creator = pl.optJSONObject("creator")?.optString("nickname", "") ?: "",
                        playCount = pl.optLong("playCount", 0),
                        songCount = pl.optInt("trackCount", 0),
                        description = pl.optString("description", ""),
                        tags = pl.optJSONArray("tags")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        platform = "netease",
                        platformId = pl.optLong("id", 0).toString(),
                    )
                )
            }
            Log.d(TAG, "网易云精品歌单获取成功: ${result.size}个")
            result
        } catch (e: Exception) {
            Log.e(TAG, "网易云精品歌单获取失败", e)
            emptyList()
        }
    }

    suspend fun fetchNeteaseTopPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val url = "$NETEASE_PLAYLIST_API_BASE/toplist"
            val body = searchGet(url) ?: return@withContext emptyList()
            val json = parseLooseJsonObject(body) ?: return@withContext emptyList()
            if (json.optInt("code", 0) != 200) return@withContext emptyList()
            val listArr = json.optJSONArray("list") ?: return@withContext emptyList()
            val result = mutableListOf<Playlist>()
            for (i in 0 until listArr.length()) {
                val pl = listArr.getJSONObject(i)
                result.add(
                    Playlist(
                        id = pl.optLong("id", 0),
                        name = pl.optString("name", ""),
                        coverUrl = pl.optString("coverImgUrl", ""),
                        creator = "网易云音乐",
                        playCount = pl.optLong("playCount", 0),
                        songCount = pl.optInt("trackCount", 0),
                        description = pl.optString("description", ""),
                        platform = "netease",
                        platformId = pl.optLong("id", 0).toString(),
                    )
                )
            }
            Log.d(TAG, "网易云排行榜获取成功: ${result.size}个")
            result
        } catch (e: Exception) {
            Log.e(TAG, "网易云排行榜获取失败", e)
            emptyList()
        }
    }

    suspend fun fetchNeteaseHotPlaylists(cat: String = "全部", limit: Int = 30, offset: Int = 0): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val catEncoded = URLEncoder.encode(cat, "UTF-8")
            val url = "$NETEASE_PLAYLIST_API_BASE/top/playlist/highquality?cat=$catEncoded&limit=$limit&offset=$offset"
            val body = searchGet(url) ?: return@withContext emptyList()
            val json = parseLooseJsonObject(body) ?: return@withContext emptyList()
            if (json.optInt("code", 0) != 200) return@withContext emptyList()
            val playlistsArr = json.optJSONArray("playlists") ?: return@withContext emptyList()
            val result = mutableListOf<Playlist>()
            for (i in 0 until playlistsArr.length()) {
                val pl = playlistsArr.getJSONObject(i)
                result.add(
                    Playlist(
                        id = pl.optLong("id", 0),
                        name = pl.optString("name", ""),
                        coverUrl = pl.optString("coverImgUrl", ""),
                        creator = pl.optJSONObject("creator")?.optString("nickname", "") ?: "",
                        playCount = pl.optLong("playCount", 0),
                        songCount = pl.optInt("trackCount", 0),
                        description = pl.optString("description", ""),
                        tags = pl.optJSONArray("tags")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        platform = "netease",
                        platformId = pl.optLong("id", 0).toString(),
                    )
                )
            }
            Log.d(TAG, "网易云热门歌单获取成功: ${result.size}个")
            result
        } catch (e: Exception) {
            Log.e(TAG, "网易云热门歌单获取失败", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════════
    //  搜索: 多平台并发 (服务端处理)
    // ═══════════════════════════════════════════

    /**
     * 搜索歌曲（支持分页）
     * @param keyword 搜索关键词
     * @param offset 偏移量（用于分页）
     * @param limit 每页数量
     * @param isLoadMore 是否为加载更多（true时不使用缓存）
     */
    suspend fun searchByKeyword(
        keyword: String,
        offset: Int = 0,
        limit: Int = 30,
        isLoadMore: Boolean = false
    ): List<Song> = withContext(Dispatchers.IO) {
        try {
            if (!RemoteConfig.featureSearch) return@withContext emptyList()

            // 非加载更多时尝试使用缓存
            if (!isLoadMore) {
                val cacheKey = "s:$keyword"
                cacheGet(searchCache, cacheKey)?.let { return@withContext it }
            }

            val serverDeferred = async {
                if (!isServerAvailable()) return@async emptyList()
                try {
                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                    val url = buildServerUrl(preferredServerUrl, "/api/search?keyword=$encoded&offset=$offset&limit=$limit")
                    val request = attachServerAuthHeaders(Request.Builder().url(url), includeChallengeToken = true).build()
                    searchClient.newCall(request).execute().use { response ->
                        val body = response.body?.string()
                        if (response.isSuccessful && body != null) {
                            markServerHealthy(preferredServerUrl)
                            parseSongArray(body).filter { RemoteConfig.isPlatformEnabled(it.platform) }
                        } else {
                            markServerFailed()
                            emptyList()
                        }
                    }
                } catch (e: Exception) {
                    markServerFailed()
                    Log.w(TAG, "服务端搜索失败(${serverConsecutiveFailures.get()}次): ${e.message}")
                    emptyList()
                }
            }

            val merged = serverDeferred.await()

            Log.d(TAG, "搜索 \"$keyword\" (offset=$offset, limit=$limit): 服务器${merged.size}首")

            // 非加载更多时缓存结果
            if (!isLoadMore && merged.isNotEmpty() && offset == 0) {
                val cacheKey = "s:$keyword"
                cacheSet(searchCache, cacheKey, merged, SEARCH_CACHE_TTL)
            }
            merged
        } catch (e: Exception) {
            Log.e(TAG, "搜索失败", e); emptyList()
        }
    }

    // 搜索专用短超时客户端
    private val searchClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    // 搜索用通用HTTP GET
    private fun searchGet(url: String, headers: Map<String, String> = emptyMap()): String? {
        val reqBuilder = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/json")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return searchClient.newCall(reqBuilder.build()).execute().use { it.body?.string() }
    }

    private fun parseLooseJsonObject(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        val candidate = when {
            text.startsWith("{") -> text
            else -> {
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                if (start >= 0 && end > start) text.substring(start, end + 1) else return null
            }
        }
        return try { JSONObject(candidate) } catch (_: Exception) { null }
    }

    private fun fetchQQOfficialCover(songMidRaw: String): String {
        val songMid = songMidRaw.trim()
        if (songMid.isBlank()) return ""
        val url = "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg?songmid=${URLEncoder.encode(songMid, "UTF-8")}&format=json"
        val body = searchGet(url, mapOf("Referer" to "https://y.qq.com"))
        val json = parseLooseJsonObject(body) ?: return ""
        val arr = json.optJSONArray("data") ?: return ""
        val song = arr.optJSONObject(0) ?: return ""
        val album = song.optJSONObject("album")
        val albumMid = firstNonBlank(
            song.optString("albummid", ""),
            song.optString("albumMid", ""),
            album?.optString("mid", ""),
        )
        return firstValidImageUrl(
            qqAlbumCoverUrl(albumMid),
            song.optString("pic", ""),
            song.optString("picUrl", ""),
            album?.optString("picUrl", ""),
        )
    }

    private fun fetchNeteaseOfficialCover(songIdRaw: String): String {
        val songId = songIdRaw.trim()
        if (songId.isBlank()) return ""
        val url = "https://music.163.com/api/song/detail?ids=[$songId]"
        val body = searchGet(url, mapOf("Referer" to "https://music.163.com"))
        val json = parseLooseJsonObject(body) ?: return ""
        val songs = json.optJSONArray("songs") ?: return ""
        val s = songs.optJSONObject(0) ?: return ""
        val album = s.optJSONObject("al") ?: s.optJSONObject("album")
        return firstValidImageUrl(
            album?.optString("picUrl", ""),
            album?.optString("pic", ""),
            album?.optString("imgUrl", ""),
        )
    }

    private fun fetchKuwoOfficialCover(songIdRaw: String): String {
        val songId = songIdRaw.trim()
        if (songId.isBlank()) return ""
        val url = "https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=${URLEncoder.encode(songId, "UTF-8")}"
        val body = searchGet(url, mapOf("Referer" to "https://m.kuwo.cn/", "Cookie" to "kw_token=ABCDE"))
        val json = parseLooseJsonObject(body) ?: return ""
        val data = json.optJSONObject("data") ?: json
        val info = data.optJSONObject("songinfo") ?: data.optJSONObject("songInfo") ?: data
        return firstValidImageUrl(
            info?.optString("pic", ""),
            info?.optString("pic120", ""),
            info?.optString("pic300", ""),
            info?.optString("albumpic", ""),
            info?.optString("album_pic", ""),
            data.optString("pic", ""),
        )
    }

    private fun fetchKugouOfficialCover(songHashRaw: String, songTitle: String = "", songArtist: String = ""): String {
        val songHash = songHashRaw.trim()
        if (songHash.isBlank()) return ""
        // 方法1: getdata API
        try {
            val url = "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&hash=${URLEncoder.encode(songHash, "UTF-8")}&album_id=0"
            val body = searchGet(url, mapOf("Referer" to "https://www.kugou.com/"))
            val json = parseLooseJsonObject(body)
            val data = json?.optJSONObject("data")
            val cover = firstValidImageUrl(
                data?.optString("img", ""),
                data?.optString("album_img", ""),
                data?.optString("author_avatar", ""),
            )
            if (cover.isNotBlank()) return cover
        } catch (_: Exception) {}
        // 方法2: 用歌名搜索获取封面
        val keyword = "$songTitle $songArtist".trim().take(60)
        if (keyword.isNotBlank()) {
            try {
                val url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${URLEncoder.encode(keyword, "UTF-8")}&page=1&pagesize=3&showtype=1"
                val body = searchGet(url, mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36"))
                val json = parseLooseJsonObject(body)
                val info = json?.optJSONObject("data")?.optJSONArray("info")
                if (info != null) {
                    for (i in 0 until info.length()) {
                        val item = info.optJSONObject(i) ?: continue
                        val coverTpl = item.optJSONObject("trans_param")?.optString("union_cover", "").orEmpty()
                        if (coverTpl.isNotBlank()) {
                            return coverTpl.replace("{size}", "400").replace("http://", "https://")
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return ""
    }

    private fun fetchOfficialCoverForSong(song: Song): String {
        val platform = song.platform.trim()
        val cover = when {
            platform.contains("QQ") -> fetchQQOfficialCover(song.platformId)
            platform.contains("网易") -> fetchNeteaseOfficialCover(song.platformId)
            platform.contains("酷我") -> fetchKuwoOfficialCover(song.platformId)
            platform.contains("酷狗") -> fetchKugouOfficialCover(song.platformId, song.title, song.artist)
            else -> ""
        }
        // 本平台封面获取失败→用QQ音乐搜索補全
        if (cover.isBlank() && !platform.contains("QQ")) {
            try {
                val query = "${song.title} ${song.artist}".take(60)
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key=$encoded&format=json"
                val body = searchGet(url, mapOf("Referer" to "https://y.qq.com")) ?: return ""
                val json = parseLooseJsonObject(body) ?: return ""
                val songSection = json.optJSONObject("data")?.optJSONObject("song") ?: return ""
                val list = songSection.optJSONArray("itemlist") ?: return ""
                for (i in 0 until minOf(list.length(), 3)) {
                    val item = list.optJSONObject(i) ?: continue
                    val mid = item.optString("mid", "")
                    if (mid.isNotBlank()) {
                        val qqCover = fetchQQOfficialCover(mid)
                        if (qqCover.isNotBlank()) return qqCover
                    }
                }
            } catch (_: Exception) {}
        }
        return cover
    }

    private fun extractMetaImageFromHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val patterns = listOf(
            Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+name=["']twitter:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val value = pattern.find(html)?.groupValues?.getOrNull(1).orEmpty()
            val normalized = firstValidImageUrl(value)
            if (normalized.isNotBlank()) return normalized
        }
        return ""
    }

    private fun fetchQQOfficialPlaylistCover(playlistIdRaw: String): String {
        val playlistId = playlistIdRaw.trim()
        if (playlistId.isBlank()) return ""
        val urls = listOf(
            "https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg?newsong=1&id=${URLEncoder.encode(playlistId, "UTF-8")}&format=json&inCharset=utf8&outCharset=utf-8",
            "https://i.y.qq.com/qzone-music/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=${URLEncoder.encode(playlistId, "UTF-8")}&format=json",
        )
        for (url in urls) {
            val body = searchGet(url, mapOf("Referer" to "https://y.qq.com")) ?: continue
            val json = parseLooseJsonObject(body) ?: continue
            val data = json.optJSONObject("data")
            val cdlist = json.optJSONArray("cdlist") ?: data?.optJSONArray("cdlist")
            val cd = cdlist?.optJSONObject(0) ?: data ?: continue
            val cover = firstValidImageUrl(
                cd.optString("logo", ""),
                cd.optString("coverUrl", ""),
                cd.optString("picurl", ""),
            )
            if (cover.isNotBlank()) return cover
        }
        return ""
    }


    private fun fetchQQOfficialPlaylistCoverByName(playlistNameRaw: String): String {
        val playlistName = playlistNameRaw.trim()
        if (playlistName.isBlank()) return ""
        val url = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key=${URLEncoder.encode(playlistName, "UTF-8")}&format=json"
        val body = searchGet(url, mapOf("Referer" to "https://y.qq.com")) ?: return ""
        val json = parseLooseJsonObject(body) ?: return ""
        val data = json.optJSONObject("data") ?: return ""
        val songlist = data.optJSONObject("songlist")
        val list = songlist?.optJSONArray("list") ?: return ""
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val dissId = firstNonBlank(item.optString("disstid", ""), item.optString("id", ""))
            val logo = firstValidImageUrl(item.optString("cover", ""), item.optString("imgurl", ""), item.optString("pic", ""))
            if (logo.isNotBlank()) return logo
            if (dissId.isNotBlank()) {
                val fromId = fetchQQOfficialPlaylistCover(dissId)
                if (fromId.isNotBlank()) return fromId
            }
        }
        return ""
    }
    private fun fetchNeteaseOfficialPlaylistCover(playlistIdRaw: String): String {
        val playlistId = playlistIdRaw.trim()
        if (playlistId.isBlank()) return ""
        val urls = listOf(
            "https://music.163.com/api/v6/playlist/detail?id=${URLEncoder.encode(playlistId, "UTF-8")}",
            "https://music.163.com/api/playlist/detail?id=${URLEncoder.encode(playlistId, "UTF-8")}",
        )
        for (url in urls) {
            val body = searchGet(url, mapOf("Referer" to "https://music.163.com")) ?: continue
            val json = parseLooseJsonObject(body) ?: continue
            val playlist = json.optJSONObject("playlist") ?: json.optJSONObject("result") ?: continue
            val cover = firstValidImageUrl(
                playlist.optString("coverImgUrl", ""),
                playlist.optString("picUrl", ""),
                playlist.optString("imgUrl", ""),
            )
            if (cover.isNotBlank()) return cover
        }
        return ""
    }

    private fun fetchKuwoOfficialPlaylistCover(playlistIdRaw: String): String {
        val playlistId = playlistIdRaw.trim()
        if (playlistId.isBlank()) return ""
        val apiUrls = listOf(
            "https://www.kuwo.cn/api/www/playlist/playListInfo?pid=${URLEncoder.encode(playlistId, "UTF-8")}&pn=1&rn=1",
            "https://m.kuwo.cn/newh5app/api/mobile/v1/playlist/info?pid=${URLEncoder.encode(playlistId, "UTF-8")}",
        )
        for (url in apiUrls) {
            val body = searchGet(url, mapOf("Referer" to "https://www.kuwo.cn/")) ?: continue
            val json = parseLooseJsonObject(body) ?: continue
            val data = json.optJSONObject("data") ?: json
            val info = data.optJSONObject("data") ?: data.optJSONObject("playlist") ?: data
            val cover = firstValidImageUrl(
                info?.optString("img", ""),
                info?.optString("pic", ""),
                info?.optString("cover", ""),
                info?.optString("imgUrl", ""),
                info?.optString("picUrl", ""),
            )
            if (cover.isNotBlank()) return cover
        }
        val htmlUrls = listOf(
            "https://www.kuwo.cn/playlist_detail/${URLEncoder.encode(playlistId, "UTF-8")}",
            "https://m.kuwo.cn/h5app/playlist/${URLEncoder.encode(playlistId, "UTF-8")}",
        )
        for (url in htmlUrls) {
            val html = searchGet(url, mapOf("Referer" to "https://www.kuwo.cn/")) ?: continue
            val cover = extractMetaImageFromHtml(html)
            if (cover.isNotBlank()) return cover
        }
        return ""
    }


    private fun fetchNeteaseOfficialPlaylistCoverByName(playlistNameRaw: String): String {
        val playlistName = playlistNameRaw.trim()
        if (playlistName.isBlank()) return ""
        val url = "https://music.163.com/api/search/get?s=${URLEncoder.encode(playlistName, "UTF-8")}&type=1000&limit=5&offset=0"
        val body = searchGet(url, mapOf("Referer" to "https://music.163.com")) ?: return ""
        val json = parseLooseJsonObject(body) ?: return ""
        val result = json.optJSONObject("result") ?: return ""
        val playlists = result.optJSONArray("playlists") ?: return ""
        for (i in 0 until playlists.length()) {
            val item = playlists.optJSONObject(i) ?: continue
            val cover = firstValidImageUrl(
                item.optString("coverImgUrl", ""),
                item.optString("coverimgurl", ""),
                item.optString("imgUrl", ""),
            )
            if (cover.isNotBlank()) return cover
            val id = firstNonBlank(item.optString("id", ""), item.optString("playlistId", ""))
            if (id.isNotBlank()) {
                val byId = fetchNeteaseOfficialPlaylistCover(id)
                if (byId.isNotBlank()) return byId
            }
        }
        return ""
    }

    private fun fetchKuwoOfficialPlaylistCoverByName(playlistNameRaw: String, targetPlaylistId: String): String {
        val playlistName = playlistNameRaw.trim()
        if (playlistName.isBlank()) return ""
        val url = "https://search.kuwo.cn/r.s?all=${URLEncoder.encode(playlistName, "UTF-8")}&ft=playlist&pn=0&rn=20&rformat=json&encoding=utf8"
        val body = searchGet(url, mapOf("Referer" to "https://www.kuwo.cn/")) ?: return ""
        val targetId = targetPlaylistId.trim()
        if (targetId.isNotBlank()) {
            val itemRegex = Regex("""\{[^{}]*'playlistid':'""" + Regex.escape(targetId) + """'[^{}]*\}""", RegexOption.IGNORE_CASE)
            val item = itemRegex.find(body)?.value.orEmpty()
            if (item.isNotBlank()) {
                val match = Regex("""'(?:hts_pic|pic)':'([^']+)'""", RegexOption.IGNORE_CASE).find(item)
                val hit = firstValidImageUrl(match?.groupValues?.getOrNull(1))
                if (hit.isNotBlank()) return hit
            }
        }
        val fallback = Regex("""'(?:hts_pic|pic)':'([^']+)'""", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1).orEmpty()
        return firstValidImageUrl(fallback)
    }
    private fun fetchKugouOfficialPlaylistCover(playlistIdRaw: String): String {
        val playlistId = playlistIdRaw.trim()
        if (playlistId.isBlank()) return ""
        val apiUrls = listOf(
            "https://m.kugou.com/plist/list/${URLEncoder.encode(playlistId, "UTF-8")}?json=true",
            "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&specialid=${URLEncoder.encode(playlistId, "UTF-8")}",
        )
        for (url in apiUrls) {
            val body = searchGet(url, mapOf("Referer" to "https://www.kugou.com/")) ?: continue
            val json = parseLooseJsonObject(body) ?: continue
            val data = json.optJSONObject("data") ?: json.optJSONObject("info") ?: json.optJSONObject("plist") ?: json
            val cover = firstValidImageUrl(
                data?.optString("imgurl", ""),
                data?.optString("img", ""),
                data?.optString("cover", ""),
                data?.optString("cover_url", ""),
            )
            if (cover.isNotBlank()) return cover
        }
        val htmlUrls = listOf(
            "https://www.kugou.com/yy/special/single/${URLEncoder.encode(playlistId, "UTF-8")}.html",
            "https://www.kugou.com/songlist/${URLEncoder.encode(playlistId, "UTF-8")}/",
        )
        for (url in htmlUrls) {
            val html = searchGet(url, mapOf("Referer" to "https://www.kugou.com/")) ?: continue
            val cover = extractMetaImageFromHtml(html)
            if (cover.isNotBlank()) return cover
        }
        return ""
    }

    private fun fetchOfficialRecommendPlaylistCover(platformRaw: String, playlistIdRaw: String, playlistNameRaw: String): String {
        val platform = platformRaw.trim()
        return when {
            platform.contains("QQ") -> firstNonBlank(
                fetchQQOfficialPlaylistCover(playlistIdRaw),
                fetchQQOfficialPlaylistCoverByName(playlistNameRaw),
            )
            platform.contains("网易") -> firstNonBlank(
                fetchNeteaseOfficialPlaylistCover(playlistIdRaw),
                fetchNeteaseOfficialPlaylistCoverByName(playlistNameRaw),
            )
            platform.contains("酷我") -> firstNonBlank(
                fetchKuwoOfficialPlaylistCover(playlistIdRaw),
                fetchKuwoOfficialPlaylistCoverByName(playlistNameRaw, playlistIdRaw),
            )
            platform.contains("酷狗") -> firstNonBlank(
                fetchKugouOfficialPlaylistCover(playlistIdRaw),
                fetchKugouOfficialPlaylistCoverByName(playlistNameRaw),
            )
            else -> ""
        }
    }

    private fun parseFlexibleCount(raw: String?): Long {
        val text = raw?.trim().orEmpty().replace(",", "")
        if (text.isBlank()) return 0L
        text.toLongOrNull()?.let { return it }
        val n = Regex("""(\d+(?:\.\d+)?)""").find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return 0L
        return when {
            text.contains("亿") -> (n * 100_000_000).toLong()
            text.contains("万") -> (n * 10_000).toLong()
            text.endsWith("k", ignoreCase = true) -> (n * 1_000).toLong()
            text.endsWith("m", ignoreCase = true) -> (n * 1_000_000).toLong()
            else -> n.toLong()
        }
    }

    private fun recommendPlaylistKey(platformRaw: String, playlistIdRaw: String): String {
        val platform = platformRaw.trim()
        val playlistId = playlistIdRaw.trim()
        if (platform.isBlank() || playlistId.isBlank()) return ""
        return "$platform::$playlistId"
    }

    private fun addRecommendPlaylist(
        target: MutableList<RecommendPlaylist>,
        seen: MutableSet<String>,
        item: RecommendPlaylist,
    ) {
        val normalized = item.copy(
            name = item.name.trim(),
            platform = item.platform.trim(),
            playlistId = item.playlistId.trim(),
            coverUrl = firstValidImageUrl(item.coverUrl),
        )
        if (normalized.name.isBlank() || normalized.platform.isBlank() || normalized.playlistId.isBlank()) return
        val key = recommendPlaylistKey(normalized.platform, normalized.playlistId)
        if (key.isBlank() || !seen.add(key)) return
        target.add(normalized)
    }

    private fun fetchQQOfficialFeaturedPlaylists(limit: Int): List<RecommendPlaylist> {
        val target = limit.coerceAtLeast(1)
        val keywords = listOf("热歌", "流行", "华语", "经典", "抖音", "车载", "DJ", "伤感", "粤语", "英文")
        val result = mutableListOf<RecommendPlaylist>()
        val seen = LinkedHashSet<String>()
        for (keyword in keywords) {
            if (result.size >= target) break
            val url = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key=${URLEncoder.encode(keyword, "UTF-8")}&format=json"
            val body = searchGet(url, mapOf("Referer" to "https://y.qq.com")) ?: continue
            val json = parseLooseJsonObject(body) ?: continue
            val data = json.optJSONObject("data") ?: continue
            val list = data.optJSONObject("songlist")?.optJSONArray("list") ?: continue
            for (i in 0 until list.length()) {
                if (result.size >= target) break
                val item = list.optJSONObject(i) ?: continue
                val playlistId = firstNonBlank(item.optString("disstid", ""), item.optString("id", ""))
                val name = firstNonBlank(item.optString("dissname", ""), item.optString("name", ""), item.optString("title", ""))
                var cover = firstValidImageUrl(
                    item.optString("cover", ""),
                    item.optString("imgurl", ""),
                    item.optString("pic", ""),
                    item.optString("logo", ""),
                )
                if (cover.isBlank() && playlistId.isNotBlank()) {
                    cover = fetchQQOfficialPlaylistCover(playlistId)
                }
                val playCount = maxOf(
                    item.optLong("listen_num", 0),
                    parseFlexibleCount(firstNonBlank(
                        item.optString("listen_num", ""),
                        item.optString("play_num", ""),
                        item.optString("play_count", ""),
                    )),
                )
                addRecommendPlaylist(
                    target = result,
                    seen = seen,
                    item = RecommendPlaylist(
                        name = name,
                        coverUrl = cover,
                        playCount = playCount,
                        platform = "QQ音乐",
                    playlistId = playlistId,
                    ),
                )
            }
        }
        return result
    }

    private fun fetchNeteaseOfficialFeaturedPlaylists(limit: Int): List<RecommendPlaylist> {
        val target = limit.coerceAtLeast(1)
        val keywords = listOf("热歌", "流行", "华语", "经典", "民谣", "轻音乐", "电子", "英文", "粤语", "抖音")
        val result = mutableListOf<RecommendPlaylist>()
        val seen = LinkedHashSet<String>()
        for (keyword in keywords) {
            if (result.size >= target) break
            val url = "https://music.163.com/api/search/get?s=${URLEncoder.encode(keyword, "UTF-8")}&type=1000&limit=20&offset=0"
            val body = searchGet(url, mapOf("Referer" to "https://music.163.com")) ?: continue
            val json = parseLooseJsonObject(body) ?: continue
            val playlists = json.optJSONObject("result")?.optJSONArray("playlists") ?: continue
            for (i in 0 until playlists.length()) {
                if (result.size >= target) break
                val item = playlists.optJSONObject(i) ?: continue
                val playlistId = firstNonBlank(item.optString("id", ""), item.optString("playlistId", ""))
                val name = firstNonBlank(item.optString("name", ""), item.optString("title", ""))
                var cover = firstValidImageUrl(
                    item.optString("coverImgUrl", ""),
                    item.optString("coverimgurl", ""),
                    item.optString("imgUrl", ""),
                    item.optString("picUrl", ""),
                )
                if (cover.isBlank() && playlistId.isNotBlank()) {
                    cover = fetchNeteaseOfficialPlaylistCover(playlistId)
                }
                val playCount = maxOf(
                    item.optLong("playCount", 0),
                    parseFlexibleCount(item.optString("playCount", "")),
                )
                addRecommendPlaylist(
                    target = result,
                    seen = seen,
                    item = RecommendPlaylist(
                        name = name,
                        coverUrl = cover,
                        playCount = playCount,
                        platform = "网易云",
                        playlistId = playlistId,
                    ),
                )
            }
        }
        return result
    }

    private fun fetchKuwoOfficialFeaturedPlaylists(limit: Int): List<RecommendPlaylist> {
        val target = limit.coerceAtLeast(1)
        val keywords = listOf("热歌", "流行", "经典", "车载", "DJ", "轻音乐", "华语", "粤语")
        val result = mutableListOf<RecommendPlaylist>()
        val seen = LinkedHashSet<String>()
        val itemRegex = Regex("""\{[^{}]*'playlistid':'(\d+)'[^{}]*\}""", RegexOption.IGNORE_CASE)
        val nameRegex = Regex("""'name':'([^']+)'""", RegexOption.IGNORE_CASE)
        val coverRegex = Regex("""'(?:hts_pic|pic)':'([^']+)'""", RegexOption.IGNORE_CASE)
        val playRegex = Regex("""'playcnt':'([^']+)'""", RegexOption.IGNORE_CASE)
        for (keyword in keywords) {
            if (result.size >= target) break
            val url = "https://search.kuwo.cn/r.s?all=${URLEncoder.encode(keyword, "UTF-8")}&ft=playlist&pn=0&rn=30&rformat=json&encoding=utf8"
            val body = searchGet(url, mapOf("Referer" to "https://www.kuwo.cn/")) ?: continue
            val items = itemRegex.findAll(body)
            for (match in items) {
                if (result.size >= target) break
                val block = match.value
                val playlistId = match.groupValues.getOrElse(1) { "" }
                val name = nameRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
                var cover = firstValidImageUrl(coverRegex.find(block)?.groupValues?.getOrNull(1))
                if (cover.isBlank() && playlistId.isNotBlank()) {
                    cover = fetchKuwoOfficialPlaylistCover(playlistId)
                }
                val playCount = parseFlexibleCount(playRegex.find(block)?.groupValues?.getOrNull(1))
                addRecommendPlaylist(
                    target = result,
                    seen = seen,
                    item = RecommendPlaylist(
                        name = name,
                        coverUrl = cover,
                        playCount = playCount,
                        platform = "酷我音乐",
                        playlistId = playlistId,
                    ),
                )
            }
        }
        return result
    }

    private fun fetchKugouOfficialFeaturedPlaylists(limit: Int): List<RecommendPlaylist> {
        val target = limit.coerceAtLeast(1)
        val keywords = listOf("热歌", "流行", "经典", "车载", "DJ", "抖音", "华语", "英文")
        val result = mutableListOf<RecommendPlaylist>()
        val seen = LinkedHashSet<String>()
        for (keyword in keywords) {
            if (result.size >= target) break
            val url = "https://mobilecdn.kugou.com/api/v3/search/special?keyword=${URLEncoder.encode(keyword, "UTF-8")}&page=1&pagesize=20"
            val body = searchGet(url, mapOf("Referer" to "https://www.kugou.com/")) ?: continue
            val json = parseLooseJsonObject(body) ?: continue
            val data = json.optJSONObject("data") ?: continue
            val list = data.optJSONArray("info")
                ?: data.optJSONArray("lists")
                ?: continue
            for (i in 0 until list.length()) {
                if (result.size >= target) break
                val item = list.optJSONObject(i) ?: continue
                val playlistId = firstNonBlank(
                    item.optString("specialid", ""),
                    item.optString("special_id", ""),
                    item.optString("id", ""),
                )
                val name = firstNonBlank(
                    item.optString("specialname", ""),
                    item.optString("special_name", ""),
                    item.optString("name", ""),
                )
                var cover = firstValidImageUrl(
                    item.optString("imgurl", ""),
                    item.optString("img", ""),
                    item.optString("cover", ""),
                    item.optString("sizable_cover", ""),
                )
                if (cover.isBlank() && playlistId.isNotBlank()) {
                    cover = fetchKugouOfficialPlaylistCover(playlistId)
                }
                val playCount = maxOf(
                    item.optLong("play_count", 0),
                    parseFlexibleCount(firstNonBlank(
                        item.optString("play_count", ""),
                        item.optString("playcount", ""),
                        item.optString("total_play", ""),
                    )),
                )
                addRecommendPlaylist(
                    target = result,
                    seen = seen,
                    item = RecommendPlaylist(
                        name = name,
                        coverUrl = cover,
                        playCount = playCount,
                        platform = "酷狗音乐",
                        playlistId = playlistId,
                    ),
                )
            }
        }
        return result
    }

    suspend fun fetchOfficialFeaturedPlaylists(
        totalLimit: Int = 120,
        perPlatformLimit: Int = 36,
    ): List<RecommendPlaylist> = withContext(Dispatchers.IO) {
        val total = totalLimit.coerceIn(20, 500)
        val perPlatform = perPlatformLimit.coerceIn(6, 150)
        val groups = coroutineScope {
            listOf(
                async { fetchQQOfficialFeaturedPlaylists(perPlatform) },
                async { fetchNeteaseOfficialFeaturedPlaylists(perPlatform) },
                async { fetchKuwoOfficialFeaturedPlaylists(perPlatform) },
                async { fetchKugouOfficialFeaturedPlaylists(perPlatform) },
            ).awaitAll()
        }
        val merged = mutableListOf<RecommendPlaylist>()
        val seen = LinkedHashSet<String>()
        groups.flatten().forEach { item ->
            addRecommendPlaylist(merged, seen, item)
        }
        merged
            .sortedWith(
                compareByDescending<RecommendPlaylist> { it.coverUrl.isNotBlank() }
                    .thenByDescending { it.playCount }
            )
            .take(total)
    }

    suspend fun enrichRecommendPlaylistCovers(
        playlists: List<RecommendPlaylist>,
        maxLookup: Int = 120,
        parallelism: Int = 10,
    ): List<RecommendPlaylist> = withContext(Dispatchers.IO) {
        if (playlists.isEmpty()) return@withContext playlists
        ensureRecommendCoverCacheLoaded()

        val merged = playlists.toMutableList()
        val unresolved = mutableListOf<IndexedValue<RecommendPlaylist>>()
        var changed = false

        playlists.withIndex().forEach { indexed ->
            val item = indexed.value
            val fromItem = firstValidImageUrl(item.coverUrl)
            if (fromItem.isNotBlank()) {
                merged[indexed.index] = item.copy(coverUrl = fromItem)
                cacheRecommendCover(item.platform, item.playlistId, fromItem, forcePersist = false)
                if (fromItem != item.coverUrl) changed = true
                return@forEach
            }
            val cached = cachedRecommendCover(item.platform, item.playlistId)
            if (cached.isNotBlank()) {
                merged[indexed.index] = item.copy(coverUrl = cached)
                changed = true
            } else {
                unresolved.add(indexed)
            }
        }

        val toLookup = unresolved.take(maxLookup.coerceAtLeast(0))
        if (toLookup.isNotEmpty()) {
            val semaphore = Semaphore(parallelism.coerceAtLeast(1))
            val updates = coroutineScope {
                toLookup.map { (index, item) ->
                    async {
                        semaphore.withPermit {
                            try {
                                val cover = firstValidImageUrl(
                                    fetchOfficialRecommendPlaylistCover(item.platform, item.playlistId, item.name)
                                )
                                if (cover.isNotBlank()) Triple(index, item, cover) else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            updates.forEach { (index, old, cover) ->
                merged[index] = old.copy(coverUrl = cover)
                cacheRecommendCover(old.platform, old.playlistId, cover, forcePersist = false)
                changed = true
            }
        }

        if (changed) flushRecommendCoverCacheIfDirty()
        if (!changed) playlists else merged
    }

    /** 批量获取网易云歌曲封面 */
    private fun fetchNeteaseCovers(songIds: List<Long>): Map<Long, String> {
        if (songIds.isEmpty()) return emptyMap()
        try {
            val idsParam = songIds.joinToString(",") { "$it" }
            val cParam = songIds.joinToString(",") { "{\"id\":$it}" }
            val url = "https://music.163.com/api/song/detail?ids=[$idsParam]&c=[$cParam]"
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://music.163.com")
                .build()
            val body = searchClient.newCall(request).execute().use { it.body?.string() }
            if (body.isNullOrEmpty()) return emptyMap()
            val json = JSONObject(body)
            val songsArr = json.optJSONArray("songs") ?: return emptyMap()
            val map = mutableMapOf<Long, String>()
            for (i in 0 until songsArr.length()) {
                val s = songsArr.getJSONObject(i)
                val id = s.optLong("id", 0)
                val al = s.optJSONObject("al") ?: s.optJSONObject("album")
                val pic = firstValidImageUrl(
                    al?.optString("picUrl", ""),
                    al?.optString("pic", ""),
                    al?.optString("imgUrl", ""),
                )
                if (id != 0L && pic.isNotBlank()) map[id] = pic
            }
            return map
        } catch (e: Exception) {
            Log.w(TAG, "批量获取网易云封面失败: ${e.message}")
            return emptyMap()
        }
    }

    /** QQ音乐直接搜索 */
    private fun searchQQDirect(encoded: String, keyword: String, offset: Int = 0, limit: Int = 30): List<Song> {
        val page = (offset / limit) + 1
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&format=json&n=$limit&p=$page"
        val body = searchGet(url, mapOf("Referer" to "https://y.qq.com")) ?: return emptyList()
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: return emptyList()
        val song = data.optJSONObject("song") ?: return emptyList()
        val songList = song.optJSONArray("list") ?: return emptyList()
        val list = mutableListOf<Song>()
        for (i in 0 until songList.length()) {
            val s = songList.getJSONObject(i)
            val singers = s.optJSONArray("singer")
            val artistName = if (singers != null && singers.length() > 0)
                (0 until singers.length()).joinToString("/") { singers.getJSONObject(it).optString("name", "") }
            else "\u672a\u77e5"
            val mid = s.optString("songmid", "")
            val albumMid = s.optString("albummid", "")
            val coverUrl = firstValidImageUrl(
                qqAlbumCoverUrl(albumMid),
                s.optString("albumpic_big", ""),
                s.optString("albumpic_small", ""),
                s.optString("cover", ""),
            )
            list.add(Song(
                id = s.optLong("songid", searchIdCounter.incrementAndGet()),
                title = s.optString("songname", ""),
                artist = artistName,
                album = s.optString("albumname", ""),
                duration = s.optLong("interval", 0) * 1000,
                coverUrl = coverUrl,
                platform = "QQ\u97f3\u4e50",
                platformId = mid,
            ))
        }
        return list
    }

    /** 酷我音乐直接搜索 */
    private fun searchKuwoDirect(encoded: String, keyword: String, offset: Int = 0, limit: Int = 30): List<Song> {
        val pn = offset / limit
        val url = "https://search.kuwo.cn/r.s?all=$encoded&ft=music&rformat=json&encoding=utf8&rn=$limit&pn=$pn"
        val body = searchGet(url, mapOf(
            "Referer" to "https://www.kuwo.cn/",
            "Cookie" to "kw_token=ABCDE",
        )) ?: return emptyList()
        // 酷我返回的可能是 JSONP 或普通 JSON
        val cleanBody = body.trim().let {
            if (it.startsWith("(") || it.startsWith("try{")) {
                it.substringAfter("(").substringBeforeLast(")")
            } else it
        }
        val json = try { JSONObject(cleanBody) } catch (_: Exception) { return emptyList() }
        val abslist = json.optJSONArray("abslist") ?: return emptyList()
        val list = mutableListOf<Song>()
        for (i in 0 until abslist.length()) {
            val s = abslist.getJSONObject(i)
            val musicrid = s.optString("MUSICRID", "").removePrefix("MUSIC_")
            val dc_targetid = s.optString("DC_TARGETID", musicrid)
            val duration = s.optString("DURATION", "0")
            val durationMs = try { duration.toLong() * 1000 } catch (_: Exception) { 0L }
            val coverUrl = firstValidImageUrl(
                s.optString("web_albumpic_short", ""),
                s.optString("web_albumpic", ""),
                s.optString("MVPIC", ""),
                s.optString("hts_MVPIC", ""),
                s.optString("pic", ""),
            )
            list.add(Song(
                id = searchIdCounter.incrementAndGet(),
                title = s.optString("SONGNAME", ""),
                artist = s.optString("ARTIST", "\u672a\u77e5"),
                album = s.optString("ALBUM", ""),
                duration = durationMs,
                coverUrl = coverUrl,
                platform = "\u9177\u6211\u97f3\u4e50",
                platformId = dc_targetid,
            ))
        }
        return list
    }

    /** 酷狗音乐直接搜索 */
    private fun searchKugouDirect(encoded: String, keyword: String, offset: Int = 0, limit: Int = 30): List<Song> {
        val page = (offset / limit) + 1
        val url = "https://mobilecdn.kugou.com/api/v3/search/song?keyword=$encoded&page=$page&pagesize=$limit&showtype=1"
        val body = searchGet(url) ?: return emptyList()
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: return emptyList()
        val info = data.optJSONArray("info") ?: return emptyList()
        val list = mutableListOf<Song>()
        for (i in 0 until info.length()) {
            val s = info.getJSONObject(i)
            val fullName = s.optString("songname", "")
            val singerName = s.optString("singername", "\u672a\u77e5")
            val hash = s.optString("hash", "")
            val albumId = s.optString("album_id", "")
            val durationSec = s.optInt("duration", 0)
            val coverUrl = firstValidImageUrl(
                s.optString("img", ""),
                s.optString("album_img", ""),
                s.optString("sizable_cover", ""),
            )
            list.add(Song(
                id = searchIdCounter.incrementAndGet(),
                title = fullName,
                artist = singerName,
                album = s.optString("album_name", ""),
                duration = durationSec * 1000L,
                coverUrl = coverUrl,
                platform = "\u9177\u72d7\u97f3\u4e50",
                platformId = hash,
            ))
        }
        return list
    }

    // ═══════════════════════════════════════════
    //  抖音/汽水音乐链接解析
    // ═══════════════════════════════════════════

    suspend fun parseDouyinLink(shareUrl: String): Song? = withContext(Dispatchers.IO) {
        try {
            if (!RemoteConfig.featureDouyinParse) return@withContext null
            val jsonBody = JSONObject().apply { put("url", shareUrl) }.toString()
            val body = serverPost("/api/douyin/parse", jsonBody)
            if (body.isNullOrEmpty()) return@withContext null

            val json = JSONObject(body)
            if (json.optInt("code", 0) != 200) return@withContext null

            // Flask包装: {"code":200, "data":{...}}
            val data = json.optJSONObject("data") ?: return@withContext null
            val artistPic = extractArtistPicUrl(data)
            val cover = firstValidImageUrl(
                extractSongCoverUrl(data),
                data.optString("coverUrl", ""),
                data.optString("pic", ""),
                artistPic,
            )
            val platformName = data.optString("platform", "汽水音乐")
            val songPlatformId = data.optString("id", "").ifBlank { data.optLong("id").toString() }
            Song(
                id = data.optLong("id", searchIdCounter.incrementAndGet()),
                title = data.optString("title", "抖音音乐"),
                artist = data.optString("artist", "未知"),
                album = data.optString("album", "汽水音乐"),
                coverUrl = cover,
                artistPicUrl = artistPic,
                platform = platformName,
                platformId = songPlatformId,
                directUrl = data.optString("directUrl", ""),
                lrcText = data.optString("lrcText", ""),
                lxSourceKey = if (platformName == "汽水音乐" || platformName == "抖音") "qsvip" else "",
                pluginRawJson = data.toString(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "抖音解析失败", e); null
        }
    }

    // ═══════════════════════════════════════════
    //  歌词: 直接调用平台API (不经服务器代理, 更快更稳)
    // ═══════════════════════════════════════════

    // 短超时HTTP客户端 (歌词专用, 不需要长等待)
    private val lyricClient = client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /**
     * 直接从平台API获取歌词 (不经服务器, 更快更稳定)
     */
    suspend fun fetchLyricsDirect(platform: String, songId: String, songName: String = "", intervalSec: Int = 0): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "ld:$platform:$songId"
            cacheGet(lyricsCache, cacheKey)?.let { return@withContext it }

            val lrcText = when (platform) {
                "QQ音乐", "QQ", "qq" -> fetchQQLyricDirect(songId)
                "网易云", "网易云2", "网易云音乐", "netease", "wy" -> fetchWyLyricDirect(songId)
                "酷狗音乐", "酷狗", "kugou", "kg" -> fetchKugouLyricDirect(songId, songName, intervalSec)
                "酷我音乐", "酷我", "kuwo", "kw" -> fetchKuwoLyricDirect(songId)
                "咪咕音乐", "咪咕", "migu", "mg" -> fetchMgLyricDirect(songId)
                else -> ""
            }
            if (lrcText.isNotBlank()) {
                Log.d(TAG, "获取到歌词文本长度: ${lrcText.length}, 前200字符: ${lrcText.take(200)}")
                val yrcPattern = Regex("""^\[\d+,\d+\]""")
                val krcPattern = Regex("""^\[\d+,\d+\].*<-?\d+,-?\d+(?:,-?\d+)?>""")
                val jsonPattern = Regex("""^\s*\{.*"t"\s*:""")
                val isYrc = yrcPattern.containsMatchIn(lrcText)
                val isKrc = krcPattern.containsMatchIn(lrcText)
                val isJson = jsonPattern.containsMatchIn(lrcText) || (lrcText.trimStart().startsWith("{") && lrcText.contains("\"c\""))
                Log.d(TAG, "歌词格式检测: isYRC=$isYrc, isKRC=$isKrc, isJson=$isJson")

                val parsed: List<LyricLine> = when {
                    isKrc -> parseLyricsWithParserAndCache(lrcText, "krc").also { Log.d(TAG, "KRC解析结果: ${it.size}行, 第一行words数: ${it.firstOrNull()?.words?.size}") }
                    isYrc -> parseLyricsWithParserAndCache(lrcText, "yrc").also { Log.d(TAG, "YRC解析结果: ${it.size}行, 第一行words数: ${it.firstOrNull()?.words?.size}") }
                    isJson -> {
                        if (lrcText.contains("[") && lrcText.contains("](")) {
                            val yrcPart = extractYrcFromMixed(lrcText)
                            Log.d(TAG, "检测到混合格式，提取YRC部分长度: ${yrcPart.length}")
                            if (yrcPart.isNotBlank()) {
                                parseYrc(yrcPart).also { Log.d(TAG, "混合格式YRC解析结果: ${it.size}行, 第一行words数: ${it.firstOrNull()?.words?.size}") }
                            } else {
                                parseNeteaseJsonLyric(lrcText).also { Log.d(TAG, "JSON解析结果: ${it.size}行, 第一行words数: ${it.firstOrNull()?.words?.size}") }
                            }
                        } else {
                            parseNeteaseJsonLyric(lrcText).also { Log.d(TAG, "JSON解析结果: ${it.size}行, 第一行words数: ${it.firstOrNull()?.words?.size}") }
                        }
                    }
                    else -> parseLrc(lrcText).also { Log.d(TAG, "LRC解析结果: ${it.size}行") }
                }
                if (parsed.isNotEmpty()) {
                    cacheSet(lyricsCache, cacheKey, parsed, LYRICS_CACHE_TTL)
                    return@withContext parsed
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "直接获取歌词失败($platform): ${e.message}")
            emptyList()
        }
    }

    private fun lyricGet(url: String, headers: Map<String, String> = emptyMap()): String? {
        val reqBuilder = Request.Builder().url(url).get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return try {
            lyricClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string()
                Log.d("MusicAPI", "lyricGet code=${resp.code}, url=${url.take(80)}, bodyLen=${body?.length ?: "null"}")
                body
            }
        } catch (e: Exception) {
            Log.e("MusicAPI", "lyricGet FAIL: ${e.message}, url=${url.take(80)}")
            null
        }
    }

    /** QQ音乐歌词（与 lx-music-mobile src/utils/musicSdk/tx/lyric.js 完全一致） */
    private fun fetchQQLyricDirect(songmid: String): String {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&platform=yqq"
        val body = lyricGet(url, mapOf("Referer" to "https://y.qq.com/portal/player.html")) ?: return ""
        return try {
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) return ""
            val lyricB64 = json.optString("lyric", "")
            val transB64 = json.optString("trans", "")
            val lyric = b64DecodeUnicode(lyricB64)
            val tlyric = b64DecodeUnicode(transB64)
            if (tlyric.isNotBlank()) {
                // 返回特殊格式，让上层解析为翻译歌词
                "$lyric\n\n[tx:trans]\n$tlyric"
            } else {
                lyric
            }
        } catch (_: Exception) { "" }
    }

    /** Base64 解码为 UTF-8 字符串（与 lx-music-mobile b64DecodeUnicode 一致） */
    private fun b64DecodeUnicode(str: String): String {
        if (str.isBlank()) return ""
        return try {
            String(android.util.Base64.decode(str, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    /** 酷狗歌词（与 lx-music-mobile src/utils/musicSdk/kg/lyric.js 完全一致）
     *  需要 name + hash + interval 参数，不是仅 hash */
    private fun fetchKugouLyricDirect(hash: String, name: String = "", intervalSec: Int = 0): String {
        val timelength = intervalSec * 1000
        // Step 1: 搜索歌词候选（与 searchLyric 一致）
        val keyword = java.net.URLEncoder.encode(name, "UTF-8")
        val searchUrl = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=$keyword&hash=$hash&timelength=$timelength&lrctxt=1"
        val kgHeaders = mapOf(
            "KG-RC" to "1",
            "KG-THash" to "expand_search_manager.cpp:852736169:451",
            "User-Agent" to "KuGou2012-9020-ExpandSearchManager",
        )
        val searchBody = lyricGet(searchUrl, kgHeaders)
        if (searchBody == null) {
            android.util.Log.e("MusicAPI", "酷狗歌词搜索失败: lyricGet返回null")
            return ""
        }
        android.util.Log.d("MusicAPI", "酷狗歌词搜索: url=$searchUrl, body前300=${searchBody.take(300)}")
        val searchJson = try { JSONObject(searchBody) } catch (e: Exception) {
            android.util.Log.e("MusicAPI", "酷狗歌词搜索JSON解析失败: ${e.message}")
            return ""
        }
        val candidates = searchJson.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            android.util.Log.d("MusicAPI", "酷狗歌词搜索: 无候选歌词")
            return ""
        }
        val first = candidates.getJSONObject(0)
        val lrcId = first.optString("id", "")
        val accesskey = first.optString("accesskey", "")
        val krctype = first.optInt("krctype", 0)
        val contenttype = first.optInt("contenttype", 1)
        android.util.Log.d("MusicAPI", "酷狗歌词候选: id=$lrcId, accesskey=${accesskey.take(16)}..., krctype=$krctype, contenttype=$contenttype")
        val fmt = if (krctype == 1 && contenttype != 1) "krc" else "lrc"
        if (lrcId.isBlank() || accesskey.isBlank()) {
            android.util.Log.e("MusicAPI", "酷狗歌词: id或accesskey为空, lrcId=$lrcId, accesskey=$accesskey")
            return ""
        }
        // Step 2: 下载歌词（与 getLyricDownload 一致）
        val dlUrl = "http://lyrics.kugou.com/download?ver=1&client=pc&id=$lrcId&accesskey=$accesskey&fmt=$fmt&charset=utf8"
        val dlBody = lyricGet(dlUrl, kgHeaders)
        if (dlBody == null) {
            android.util.Log.e("MusicAPI", "酷狗歌词下载失败: lyricGet返回null")
            return ""
        }
        android.util.Log.d("MusicAPI", "酷狗歌词下载: body前200=${dlBody.take(200)}")
        return try {
            val dlJson = JSONObject(dlBody)
            val contentB64 = dlJson.optString("content", "")
            if (contentB64.isBlank()) {
                android.util.Log.e("MusicAPI", "酷狗歌词下载: content为空")
                return ""
            }
            val resultFmt = dlJson.optString("fmt", "lrc")
            android.util.Log.d("MusicAPI", "酷狗歌词下载: fmt=$resultFmt, content长度=${contentB64.length}")
            val lyrics = when (resultFmt) {
                "krc" -> parseKrcLyric(contentB64)
                else -> String(android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            }
            android.util.Log.d("MusicAPI", "酷狗歌词最终结果: 长度=${lyrics.length}, 前10行:\n${lyrics.lines().take(10).joinToString("\n")}")
            lyrics
        } catch (e: Exception) {
            android.util.Log.e("MusicAPI", "酷狗歌词下载解析失败: ${e.message}")
            ""
        }
    }

    /** KRC 歌词解析（与 lx-music-mobile kg/util.js decodeLyric 完全一致）
     *  enc_key = [0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e, 0x69] */
    private fun parseKrcLyric(contentB64: String): String {
        try {
            val raw = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
            // 酷狗 KRC key: @Gaw^2tGQ61-ÎÒni (16 bytes)
            val key = byteArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69)
            // slice(4): 跳过前4字节，再 XOR
            val data = raw.copyOfRange(4, raw.size)
            for (i in data.indices) {
                data[i] = (data[i].toInt() xor key[i % 16].toInt()).toByte()
            }
            // pako.inflate (zlib 解压)
            val inflater = java.util.zip.Inflater()
            inflater.setInput(data)
            val output = ByteArray(data.size * 4)
            val len = inflater.inflate(output)
            inflater.end()
            val result = String(output, 0, len, Charsets.UTF_8)
            // 转换 KRC 格式为 LRC
            return convertKrcToLrc(result)
        } catch (e: Exception) {
            android.util.Log.e("MusicAPI", "parseKrcLyric 失败: ${e.message}")
            return ""
        }
    }

    /** KRC → LRC 格式转换 */
    private fun convertKrcToLrc(krc: String): String {
        val lines = krc.lines()
        val sb = StringBuilder()
        // 去掉 [id:$...] 头
        val headExp = Regex("""^.*\[id:\$\w+\]\n?""")
        val cleaned = krc.replace(headExp, "")
        // 提取翻译
        val transExp = Regex("""\[language:([\w=/+]+)\]\n?""")
        val transMatch = transExp.find(cleaned)
        val body = if (transMatch != null) cleaned.replace(transExp, "") else cleaned
        // 转换时间戳格式 [ms,ms] → [mm:ss.ms]
        val lrc = body.replace(Regex("""\[(\d+),(\d+)\](.*)""")) { match ->
            val time = match.groupValues[1].toInt()
            val duration = match.groupValues[2].toInt()
            val text = match.groupValues[3]
            val totalMs = time + duration
            val m = totalMs / 60000
            val s = (totalMs % 60000) / 1000
            val ms = totalMs % 1000
            val cleanText = text.replace(Regex("""<-?\d+,-?\d+(?:,-?\d+)?>"""), "")
            "[%02d:%02d.%03d]$cleanText\n".format(m, s, ms)
        }
        return lrc
    }

    /** 酷我歌词（与 lx-music-mobile src/utils/musicSdk/kw/lyric.js 完全一致）
     *  使用 newlyric.kuwo.cn + XOR 加密参数 */
    private fun fetchKuwoLyricDirect(songId: String): String {
        try {
            // 构建 XOR 加密参数（与 buildParams 一致）
            val params = buildKuwoParams(songId, true)
            val url = "http://newlyric.kuwo.cn/newlyric.lrc?$params"
            // 二进制请求（与 binary: true 一致）
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                val raw = resp.body?.bytes() ?: return ""
                return decodeKuwoLyric(raw, true)
                    // 与 lx-music-mobile lrcTools.rxps.wordTimeAll 一致: 移除逐字时间标签 <-?\d+,-?\d+(?:,-?\d+)?>
                    .replace(Regex("""<-?\d+,-?\d+(?:,-?\d+)?>"""), "")
                    // 与 lx-music-mobile decodeName 一致: 解码 HTML 实体 &#(\d+);
                    .replace(Regex("""&#(\d+);""")) { mr ->
                        mr.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: mr.value
                    }
                    .also { result ->
                        Log.d("MusicAPI", "酷我歌词处理后: 长度=${result.length}, 前5行=\n${result.lines().take(5).joinToString("\n")}")
                    }
            }
        } catch (_: Exception) {
            return ""
        }
    }

    /** 酷我 XOR 参数构建（与 buildParams 一致） */
    private fun buildKuwoParams(songId: String, isGetLyricx: Boolean): String {
        var params = "user=12345,web,web,web&requester=localhost&req=1&rid=MUSIC_$songId"
        if (isGetLyricx) params += "&lrcx=1"
        val key = "yeelion".toByteArray(Charsets.UTF_8)
        val data = params.toByteArray(Charsets.UTF_8)
        for (i in data.indices) {
            data[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
    }

    /** 酷我歌词解密（与 decodeLyric 一致） */
    private fun decodeKuwoLyric(raw: ByteArray, isGetLyricx: Boolean): String {
        // 检查头部 "tp=content"
        val header = String(raw, 0, minOf(10, raw.size), Charsets.UTF_8)
        if (header != "tp=content") return ""
        // 找到 \r\n\r\n 分隔符
        val sepIndex = findBytes(raw, "\r\n\r\n".toByteArray(Charsets.UTF_8))
        if (sepIndex < 0) return ""
        val dataStart = sepIndex + 4
        // zlib 解压（与 handleInflate / pako.inflate 一致）
        val lrcData = zlibInflate(raw, dataStart, raw.size - dataStart)
        if (lrcData.isEmpty()) return ""

        if (!isGetLyricx) {
            return try { String(lrcData, Charsets.UTF_8) } catch (_: Exception) { "" }
        }
        // lrcx 模式: 先 base64 解码，再 XOR 解密
        val b64Str = String(lrcData, Charsets.UTF_8)
        val encrypted = try {
            android.util.Base64.decode(b64Str, android.util.Base64.DEFAULT)
        } catch (_: Exception) { return "" }
        val key = "yeelion".toByteArray(Charsets.UTF_8)
        for (i in encrypted.indices) {
            encrypted[i] = (encrypted[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return try { String(encrypted, java.nio.charset.Charset.forName("GB18030")) } catch (_: Exception) { "" }
    }

    /** zlib 解压 */
    private fun zlibInflate(data: ByteArray, offset: Int, length: Int): ByteArray {
        try {
            val inflater = java.util.zip.Inflater()
            inflater.setInput(data, offset, length)
            val output = ByteArray(length * 10)
            val len = inflater.inflate(output)
            inflater.end()
            return output.copyOf(len)
        } catch (_: Exception) {
            return ByteArray(0)
        }
    }

    /** 在字节数组中查找子数组 */
    private fun findBytes(data: ByteArray, pattern: ByteArray): Int {
        for (i in 0..(data.size - pattern.size)) {
            var found = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) { found = false; break }
            }
            if (found) return i
        }
        return -1
    }

    /** 网易云歌词 */
    private fun fetchWyLyricDirect(songId: String): String {
        try {
            val url = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
            val body = lyricGet(url, mapOf("Referer" to "https://music.163.com")) ?: return ""
            val json = JSONObject(body)
            val lrc = json.optJSONObject("lrc")?.optString("lyric", "")
                ?: json.optString("lyric", "")
            return lrc
        } catch (_: Exception) { return "" }
    }

    /** 咪咕歌词 */
    private fun fetchMgLyricDirect(copyrightId: String): String {
        try {
            val url = "https://music.migu.cn/v3/api/music/audioPlayer/getLyric?copyrightId=$copyrightId"
            val body = lyricGet(url) ?: return ""
            val json = JSONObject(body)
            return json.optString("lyric", "")
        } catch (_: Exception) { return "" }
    }

    // ═══════════════════════════════════════════
    //  歌词: 服务端获取 (备用)
    // ═══════════════════════════════════════════

    suspend fun fetchLyrics(platform: String, songId: String): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "l:$platform:$songId"
            cacheGet(lyricsCache, cacheKey)?.let { return@withContext it }

            val pEncoded = URLEncoder.encode(platform, "UTF-8")
            val sEncoded = URLEncoder.encode(songId, "UTF-8")
            val body = serverGet("/api/lyrics?platform=$pEncoded&songId=$sEncoded")
            if (body.isNullOrEmpty()) return@withContext emptyList()

            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: json
            val linesArr = data.optJSONArray("lines")
            if (linesArr != null && linesArr.length() > 0) {
                val lines = mutableListOf<LyricLine>()
                for (i in 0 until linesArr.length()) {
                    val item = linesArr.getJSONObject(i)
                    lines.add(LyricLine(item.optLong("timeMs", 0), item.optString("text", "")))
                }
                return@withContext lines.sortedBy { it.timeMs }
            }

            val lrcText = data.optString("lrcText", "")
            if (lrcText.isNotEmpty()) {
                val parsed = parseLrc(lrcText)
                if (parsed.isNotEmpty()) cacheSet(lyricsCache, cacheKey, parsed, LYRICS_CACHE_TTL)
                return@withContext parsed
            }

            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "服务端获取歌词失败", e); emptyList()
        }
    }

    // ═══════════════════════════════════════════
    //  播放URL: 服务端获取
    // ═══════════════════════════════════════════

    /**
     * 映射音质到API类型
     */
    private fun mapQualityToType(quality: String): String {
        return when (quality.lowercase()) {
            "standard", "128k" -> "128k"
            "exhigh", "320k" -> "320k"
            "lossless", "flac" -> "flac"
            "hires" -> "hires"
            "master" -> "master"
            else -> "320k"
        }
    }

    suspend fun fetchMusicUrl(
        platform: String,
        songId: String,
        quality: String = "exhigh",
    ): MusicUrlResult = withContext(Dispatchers.IO) {
        val cacheKey = "p:$platform:$songId:$quality"
        try {
            val gate = MusicPlaybackGate.evaluate(MusicPlaybackGate.Action.FETCH_PLAY_URL)
            if (!gate.allowed) {
                val reason = gate.reason ?: "当前环境存在风险，已阻止获取播放链接"
                Log.w(TAG, "安全策略拦截[FETCH_PLAY_URL]: $reason")
                if (gate.shouldKillProcess) {
                    SecurityGuard.killProcess()
                }
                return@withContext MusicUrlResult(error = reason)
            }
            // 播放URL缓存
            cacheGet(playUrlCache, cacheKey)?.let {
                Log.d(TAG, "使用缓存的播放链接: $cacheKey")
                return@withContext it
            }

            Log.d(TAG, "获取播放链接: platform=$platform, songId=$songId, quality=$quality")

            val pEncoded = URLEncoder.encode(platform, "UTF-8")
            val sEncoded = URLEncoder.encode(songId, "UTF-8")
            val qEncoded = URLEncoder.encode(quality, "UTF-8")
            val streamParam = when (RemoteConfig.runtimePlaybackStreamMode.trim().lowercase()) {
                "0", "direct" -> "0"
                "1", "stream", "proxy" -> "1"
                else -> "auto"
            }
            val body = serverGet("/api/playurl?platform=$pEncoded&songId=$sEncoded&quality=$qEncoded&stream=$streamParam")
            if (body.isNullOrEmpty()) {
                return@withContext MusicUrlResult(error = "服务器无响应")
            }

            val json = JSONObject(body)
            val code = json.optInt("code", 0)
            if (code == 200) {
                val data = json.optJSONObject("data") ?: json
                val directUrl = data.optString("directUrl", null)
                val rawUrl = data.optString("url", null)
                val (bestUrl, isStreamUrl) = choosePlayableUrl(streamParam, directUrl, rawUrl)
                val errText = data.optString("error", "")
                val errMsg = if (errText.isNullOrBlank()) null else errText
                val result = MusicUrlResult(
                    url = bestUrl,
                    name = data.optString("name", null),
                    artist = data.optString("artist", null),
                    album = data.optString("album", null),
                    pic = data.optString("pic", null),
                    directUrl = directUrl,
                    isStream = isStreamUrl,
                    error = if (bestUrl.isNullOrEmpty()) errMsg else null,
                )
                if (result.url != null) {
                    cacheSet(playUrlCache, cacheKey, result, PLAY_CACHE_TTL)
                }
                result
            } else {
                MusicUrlResult(error = json.optString("msg", "获取播放链接失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取播放URL失败", e)
            MusicUrlResult(error = e.message ?: "网络请求失败")
        }
    }


    // ═══════════════════════════════════════════
    //  启动批量接口 (一次获取所有初始化数据)
    // ═══════════════════════════════════════════

    /**
     * 启动批量数据（一个请求获取所有热歌榜）
     */
    data class InitData(
        val hotChart: List<Song>,
        val risingChart: List<Song>,
        val newChart: List<Song>,
        val originalChart: List<Song>,
    )

    suspend fun fetchInitData(): InitData? = withContext(Dispatchers.IO) {
        try {
            val body = serverGet("/api/init")
            if (body.isNullOrEmpty()) return@withContext null
            val json = JSONObject(body)
            if (json.optInt("code", 0) != 200) return@withContext null
            val data = json.optJSONObject("data") ?: return@withContext null

            fun parseChart(key: String): List<Song> {
                val arr = data.optJSONArray(key) ?: return emptyList()
                val wrapper = JSONObject().put("data", arr)
                return parseSongArray(wrapper.toString())
            }

            InitData(
                hotChart = parseChart("hotChart"),
                risingChart = parseChart("risingChart"),
                newChart = parseChart("newChart"),
                originalChart = parseChart("originalChart"),
            )
        } catch (e: Exception) {
            Log.e(TAG, "批量初始化数据获取失败", e)
            null
        }
    }

    // ═══════════════════════════════════════════
    //  连接预热 (提前建立TCP连接)
    // ═══════════════════════════════════════════

    fun warmup() {
        // 在后台线程发起一个轻量请求，预热DNS解析+TCP连接+连接池
        try {
            val baseUrl = serverBaseUrls().firstOrNull() ?: SERVER_URL
            val request = attachServerAuthHeaders(
                Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/ping"),
                includeChallengeToken = false
            ).build()
            // 异步发起，不阻塞调用方
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.d(TAG, "预热连接失败 (不影响使用): ${e.message}")
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                    Log.d(TAG, "预热连接成功")
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "预热发起失败: ${e.message}")
        }
    }

    /** 清除指定歌曲的播放URL缓存 (播放失败时调用, 强制重新获取) */
    fun invalidatePlayUrlCache(platform: String, songId: String) {
        val prefix = "p:$platform:$songId:"
        playUrlCache.keys.removeAll { it.startsWith(prefix) }
        Log.d(TAG, "已清除播放URL缓存: $platform/$songId")
    }

    /** 共享 OkHttpClient 供其他模块复用连接池和DNS缓存 */
    fun sharedClient(): OkHttpClient = client

    // ═══════════════════════════════════════════
    //  LRC 歌词解析 (本地保留，供直接解析用)
    // ═══════════════════════════════════════════

    private val LYRIC_METADATA_PATTERNS = listOf(
        "词：", "曲：", "编曲：", "作词：", "作曲：", "制作：", "制作人：",
        "混音：", "录音：", "母带：", "监制：", "配唱：", "和声：", "合声：",
        "吉他：", "贝斯：", "鼓：", "钢琴：", "弦乐：", "键盘：", "提琴：",
        "出品：", "企划：", "统筹：", "发行：", "OP：", "SP：",
        "词:", "曲:", "编曲:", "作词:", "作曲:", "制作:", "制作人:",
        "混音:", "录音:", "母带:", "监制:", "配唱:", "和声:", "合声:",
        "吉他:", "贝斯:", "鼓:", "钢琴:", "弦乐:", "键盘:",
        "Lyrics by", "Composed by", "Arranged by", "Produced by",
        "Written by", "Mixed by", "Mastered by", "Guitar:", "Bass:",
        "Drums:", "Piano:", "Vocal:", "Strings:",
    )

    private fun isMetadataLine(text: String): Boolean {
        if (LYRIC_METADATA_PATTERNS.any { text.startsWith(it, ignoreCase = true) }) return true
        if (text.contains(" - ") && text.split(" - ").size == 2 && text.length < 40) return true
        if (text.matches(Regex("^[\u4e00-\u9fa5A-Za-z]{1,6}[：:].{1,30}$"))) {
            val prefix = text.substringBefore("：").substringBefore(":")
            if (prefix.length <= 5) return true
        }
        return false
    }

    data class ParsedLyrics(
        val lines: List<LyricLine>,
        val lyricsData: LyricsData? = null
    )

    @Volatile
    var lastParsedLyricsData: LyricsData? = null
        private set

    fun parseLyricsWithParser(content: String, formatType: String): ParsedLyrics {
        val lyricsData = LyricsParser.parseLyrics(content, formatType)
        val result = mutableListOf<LyricLine>()

        if (lyricsData.isWordByWord && lyricsData.linesWithWords != null) {
            for (lww in lyricsData.linesWithWords) {
                val words = mutableListOf<LyricWord>()
                var lastEnd = lww.startTime.toLong()
                for (w in lww.words) {
                    val wordStartMs = w.startTime.toLong()
                    val wordDurationMs = if (formatType == "yrc" || formatType == "krc") {
                        w.duration.toLong() * 10
                    } else {
                        w.duration.toLong()
                    }
                    val wordEndMs = wordStartMs + wordDurationMs
                    if (wordStartMs > lastEnd && words.isNotEmpty()) {
                        words.add(LyricWord(" ", lastEnd, wordStartMs))
                    }
                    words.add(LyricWord(w.text, wordStartMs, wordEndMs))
                    lastEnd = wordEndMs
                }
                val fullText = lww.words.joinToString("") { it.text }
                result.add(LyricLine(lww.startTime.toLong(), fullText, words = words))
            }
        } else {
            for (line in lyricsData.lines) {
                result.add(LyricLine(line.time.toLong(), line.text))
            }
        }

        return ParsedLyrics(result.sortedBy { it.timeMs }, lyricsData)
    }

    fun parseLyricsWithParserAndCache(content: String, formatType: String): List<LyricLine> {
        val parsed = parseLyricsWithParser(content, formatType)
        lastParsedLyricsData = parsed.lyricsData
        return parsed.lines
    }

    fun parseLrc(lrc: String): List<LyricLine> {
        val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:]+(\d{1,3}))?\](.*)""")
        val lines = mutableListOf<LyricLine>()

        for (line in lrc.lines()) {
            val match = pattern.find(line) ?: continue
            val (minStr, secStr, msStr, text) = match.destructured
            val trimmed = text.trim()
            if (trimmed.isEmpty()) continue
            if (isMetadataLine(trimmed)) continue

            val minutes = minStr.toLongOrNull() ?: continue
            val seconds = secStr.toLongOrNull() ?: continue
            val millis = when (msStr.length) {
                1 -> (msStr.toLongOrNull() ?: 0) * 100
                2 -> (msStr.toLongOrNull() ?: 0) * 10
                3 -> msStr.toLongOrNull() ?: 0
                else -> 0L
            }
            val timeMs = minutes * 60_000 + seconds * 1000 + millis
            lines.add(LyricLine(timeMs, trimmed))
        }

        return lines.sortedBy { it.timeMs }
    }

    fun parseYrc(yrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val linePattern = Regex("""\[(\d+),(\d+)\](.*)""")
        val wordPattern = Regex("""\((\d+),(\d+),(\d+)\)([^\(]*)""")
        var lineIndex = 0
        for (line in yrc.lines()) {
            lineIndex++
            val lineMatch = linePattern.find(line) ?: continue
            val startTime = lineMatch.groupValues[1].toLongOrNull() ?: continue
            val content = lineMatch.groupValues[3]
            val words = mutableListOf<LyricWord>()
            val fullText = StringBuilder()
            var lastEnd = startTime
            for (wordMatch in wordPattern.findAll(content)) {
                val wordStart = wordMatch.groupValues[1].toLongOrNull() ?: continue
                val wordDurationCs = wordMatch.groupValues[2].toLongOrNull() ?: continue
                val wordDurationMs = wordDurationCs * 10
                val wordText = wordMatch.groupValues[4]
                if (wordText.isEmpty()) continue
                if (wordStart > lastEnd) {
                    words.add(LyricWord(" ", lastEnd, wordStart))
                    fullText.append(" ")
                }
                words.add(LyricWord(wordText, wordStart, wordStart + wordDurationMs))
                fullText.append(wordText)
                lastEnd = wordStart + wordDurationMs
            }
            val text = fullText.toString().trim()
            if (text.isEmpty()) continue
            lines.add(LyricLine(startTime, text, words = words))
        }
        Log.d(TAG, "parseYrc解析完成: 总行数=$lineIndex, 有效行=${lines.size}, 第一行words数=${lines.firstOrNull()?.words?.size}")
        if (lines.isNotEmpty() && lines.first().words.isNotEmpty()) {
            val firstLine = lines.first()
            Log.d(TAG, "第一行歌词示例: timeMs=${firstLine.timeMs}, text='${firstLine.text}', words数量=${firstLine.words.size}")
            firstLine.words.take(3).forEach { word ->
                Log.d(TAG, "  word: text='${word.text}', start=${word.startTimeMs}, end=${word.endTimeMs}")
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * 从混合格式（JSON元数据 + YRC歌词）中提取YRC部分
     * 混合格式示例：
     * {"t":-1000,"c":[{"tx":"作词: "},{"tx":"郝善良"}]}
     * {"t":-500,"c":[{"tx":"作曲: "},{"tx":"郝善良"}]}
     * [0,250](0,62,0)起(62,62,0)伏(124,62,0)压(186,64,0)声
     */
    private fun extractYrcFromMixed(mixedText: String): String {
        val lines = mixedText.lines()
        val yrcLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && Regex("""^\[\d+,""").containsMatchIn(trimmed)) {
                yrcLines.add(trimmed)
            }
        }

        val result = yrcLines.joinToString("\n")
        Log.d(TAG, "extractYrcFromMixed: 原始行数=${lines.size}, 提取YRC行数=${yrcLines.size}")
        return result
    }

    /**
     * 解析网易云新版JSON格式歌词（包含逐字时间信息）
     * JSON结构: {"t": 时间戳, "c": [{"tx": "文本", "li": "链接"}, ...]}
     */
    fun parseNeteaseJsonLyric(jsonStr: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        try {
            val json = JSONObject(jsonStr)
            parseJsonLyricNode(json, lines, 0L)
            Log.d(TAG, "JSON歌词解析完成: 总行数=${lines.size}, 第一行words数=${lines.firstOrNull()?.words?.size}")
            if (lines.isNotEmpty() && lines.first().words.isNotEmpty()) {
                val firstLine = lines.first()
                Log.d(TAG, "第一行歌词示例: timeMs=${firstLine.timeMs}, text='${firstLine.text}', words数量=${firstLine.words.size}")
                firstLine.words.take(3).forEach { word ->
                    Log.d(TAG, "  word: text='${word.text}', start=${word.startTimeMs}, end=${word.endTimeMs}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON歌词解析失败: ${e.message}")
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun parseJsonLyricNode(node: JSONObject, lines: MutableList<LyricLine>, parentTime: Long) {
        val time = node.optLong("t", parentTime)
        val contentArray = node.optJSONArray("c")

        if (contentArray == null) {
            val text = node.optString("tx", "").trim()
            if (text.isNotEmpty()) {
                lines.add(LyricLine(time, text))
            }
            return
        }

        for (i in 0 until contentArray.length()) {
            val item = contentArray.opt(i) ?: continue

            when (item) {
                is JSONObject -> {
                    val itemText = item.optString("tx", "").trim()

                    if (item.has("c")) {
                        val itemTime = item.optLong("t", time)
                        val subContentArray = item.optJSONArray("c")

                        if (subContentArray != null && subContentArray.length() > 0) {
                            val firstSubItem = subContentArray.opt(0)
                            val isLyricLine = firstSubItem is JSONObject &&
                                    firstSubItem.has("t") &&
                                    firstSubItem.optLong("t", 0) > 0

                            if (isLyricLine) {
                                parseJsonLyricLine(item, lines)
                            } else {
                                parseJsonLyricNode(item, lines, itemTime)
                            }
                        } else {
                            parseJsonLyricNode(item, lines, item.optLong("t", time))
                        }
                    } else if (itemText.isNotEmpty()) {
                        val itemTime = item.optLong("t", time)

                        if (itemTime > 0 || (time >= 0 && !isMetadata(itemText))) {
                            lines.add(LyricLine(itemTime.coerceAtLeast(0L), itemText))
                        }
                    }
                }

                is String -> {
                    val strText = item.trim()
                    if (strText.isNotEmpty()) {
                        lines.add(LyricLine(time.coerceAtLeast(0L), strText))
                    }
                }
            }
        }
    }

    private fun parseJsonLyricLine(lineNode: JSONObject, lines: MutableList<LyricLine>) {
        val lineTime = lineNode.optLong("t", 0L).coerceAtLeast(0L)
        val contentArray = lineNode.optJSONArray("c") ?: return

        val words = mutableListOf<LyricWord>()
        val fullText = StringBuilder()
        var lastEnd = lineTime

        for (i in 0 until contentArray.length()) {
            val item = contentArray.opt(i) ?: continue

            when (item) {
                is JSONObject -> {
                    val wordText = item.optString("tx", "").trim()
                    if (wordText.isEmpty()) continue

                    val wordTime = item.optLong("t", lineTime)

                    if (lastEnd < wordTime && fullText.isNotEmpty()) {
                        words.add(LyricWord(" ", lastEnd, wordTime))
                        fullText.append(" ")
                    }

                    val duration = if (i < contentArray.length() - 1) {
                        val nextItem = contentArray.opt(i + 1)
                        if (nextItem is JSONObject) {
                            (nextItem.optLong("t", wordTime + 300L) - wordTime).coerceAtLeast(200L)
                        } else 300L
                    } else 500L

                    words.add(LyricWord(wordText, wordTime, wordTime + duration))
                    fullText.append(wordText)
                    lastEnd = wordTime + duration
                }

                is String -> {
                    val strText = item.trim()
                    if (strText.isEmpty()) continue
                    if (fullText.isNotEmpty() && !fullText.last().isWhitespace()) {
                        fullText.append(" ")
                    }
                    fullText.append(strText)
                }
            }
        }

        val text = fullText.toString().trim()
        if (text.isEmpty()) return

        if (words.isNotEmpty()) {
            lines.add(LyricLine(lineTime, text, words = words.toList()))
        } else {
            lines.add(LyricLine(lineTime, text))
        }
    }

    private fun isMetadata(text: String): Boolean {
        val metadataPrefixes = listOf("作词", "作曲", "编曲", "混音", "制作人", "录音室", "母带")
        return metadataPrefixes.any { text.startsWith(it) || text.contains(":") && text.length < 20 }
    }

    fun parseKLyric(kLyricJson: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        try {
            val json = JSONObject(kLyricJson)
            val linesArr = json.optJSONArray("lines") ?: return emptyList()
            for (i in 0 until linesArr.length()) {
                val lineObj = linesArr.getJSONObject(i)
                val lineTime = lineObj.optLong("time", 0L)
                val lineText = lineObj.optString("lineText", "")
                if (lineText.isEmpty()) continue

                val wordsArr = lineObj.optJSONArray("words")
                val words = mutableListOf<LyricWord>()
                if (wordsArr != null && wordsArr.length() > 0) {
                    for (j in 0 until wordsArr.length()) {
                        val wordObj = wordsArr.getJSONObject(j)
                        val text = wordObj.optString("text", "")
                        if (text.isEmpty()) continue
                        val start = wordObj.optLong("start", 0L)
                        val end = wordObj.optLong("end", start + 300L)
                        words.add(LyricWord(text, start, end))
                    }
                }

                if (words.isNotEmpty()) {
                    lines.add(LyricLine(lineTime, lineText, words = words.toList()))
                } else {
                    lines.add(LyricLine(lineTime, lineText))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "kLyric解析失败: ${e.message}")
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * 解析 LRC 为时间戳→文本映射（用于翻译歌词合并）
     */
    fun parseLrcToMap(lrc: String): Map<Long, String> {
        val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:]+(\d{1,3}))?\](.*)""")
        val map = mutableMapOf<Long, String>()
        for (line in lrc.lines()) {
            val match = pattern.find(line) ?: continue
            val (minStr, secStr, msStr, text) = match.destructured
            val trimmed = text.trim()
            if (trimmed.isEmpty()) continue
            val minutes = minStr.toLongOrNull() ?: continue
            val seconds = secStr.toLongOrNull() ?: continue
            val millis = when (msStr.length) {
                1 -> (msStr.toLongOrNull() ?: 0) * 100
                2 -> (msStr.toLongOrNull() ?: 0) * 10
                3 -> msStr.toLongOrNull() ?: 0
                else -> 0L
            }
            val timeMs = minutes * 60_000 + seconds * 1000 + millis
            map[timeMs] = trimmed
        }
        return map
    }

    /**
     * 将翻译歌词按时间戳合并到主歌词列表
     */
    fun mergeLyricTranslation(lyrics: List<LyricLine>, tlyricMap: Map<Long, String>): List<LyricLine> {
        if (tlyricMap.isEmpty()) return lyrics
        return lyrics.map { line ->
            // 精确匹配时间戳；如果不匹配，尝试 ±100ms 容差
            val ttext = tlyricMap[line.timeMs]
                ?: tlyricMap.entries.firstOrNull { kotlin.math.abs(it.key - line.timeMs) <= 250 }?.value
                ?: ""
            if (ttext.isNotEmpty()) line.copy(ttext = ttext) else line
        }
    }

    // ═══════════════════════════════════════════
    //  导入外部歌单
    // ═══════════════════════════════════════════

    /**
     * 导入歌单的服务端返回数据
     */
    data class ImportedPlaylistData(
        val name: String,
        val coverUrl: String,
        val songs: List<Song>,
    )

    /**
     * 获取外部歌单详情
     * QQ音乐优先直接调用平台API，失败再fallback到服务器
     * @param platform 平台名称 ("QQ音乐" | "网易云" | "酷我音乐" | "酷狗音乐")
     * @param playlistId 歌单 ID
     */
    suspend fun fetchPlaylistImport(platform: String, playlistId: String): ImportedPlaylistData? =
        withContext(Dispatchers.IO) {
            // QQ音乐: 优先直接调用平台API
            if (platform == "QQ音乐" || platform == "qq") {
                try {
                    val directResult = fetchQQPlaylistDirect(playlistId)
                    if (directResult != null && directResult.songs.isNotEmpty()) {
                        Log.d(TAG, "QQ音乐歌单直接导入成功: ${directResult.songs.size}首")
                        return@withContext directResult
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "QQ音乐直接导入失败，尝试服务器: ${e.message}")
                }
            }

            // 网易云: 优先直接调用平台API
            if (platform == "网易云" || platform == "netease" || platform == "网易云音乐") {
                try {
                    val directResult = fetchNeteasePlaylistDirect(playlistId)
                    if (directResult != null && directResult.songs.isNotEmpty()) {
                        Log.d(TAG, "网易云歌单直接导入成功: ${directResult.songs.size}首")
                        return@withContext directResult
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "网易云直接导入失败，尝试服务器: ${e.message}")
                }
            }

            // Fallback: 服务器API
            try {
                val pEncoded = URLEncoder.encode(platform, "UTF-8")
                val idEncoded = URLEncoder.encode(playlistId, "UTF-8")
                val body = serverGet("/api/playlist/import?platform=$pEncoded&playlistId=$idEncoded")
                if (body.isNullOrEmpty()) return@withContext null

                val json = JSONObject(body)
                val code = json.optInt("code", 0)
                if (code != 200) {
                    Log.e(TAG, "导入歌单失败: ${json.optString("error", "未知错误")}")
                    return@withContext null
                }

                val data = json.optJSONObject("data") ?: return@withContext null
                val name = data.optString("name", "导入歌单")
                val coverUrl = extractPlaylistCoverUrl(data)
                val songsArr = data.optJSONArray("songs") ?: return@withContext null

                val songs = mutableListOf<Song>()
                for (i in 0 until songsArr.length()) {
                    val item = songsArr.getJSONObject(i)
                    val songPlatform = item.optString("platform", "")
                    val songPlatformId = item.optString("platformId", "")
                    val artistPic = extractArtistPicUrl(item)
                    val cover = firstValidImageUrl(
                        extractSongCoverUrl(item),
                        cachedSongCover(songPlatform, songPlatformId),
                        artistPic
                    )
                    songs.add(
                        Song(
                            id = item.optLong("id", searchIdCounter.incrementAndGet()),
                            title = item.optString("title", ""),
                            artist = item.optString("artist", "未知"),
                            album = item.optString("album", ""),
                            duration = item.optLong("duration", 0),
                            coverUrl = cover,
                            artistPicUrl = artistPic,
                            platform = songPlatform,
                            platformId = songPlatformId,
                        )
                    )
                }
                rememberSongCovers(songs, forcePersist = true)

                ImportedPlaylistData(name = name, coverUrl = coverUrl, songs = songs)
            } catch (e: Exception) {
                Log.e(TAG, "导入歌单请求失败", e)
                null
            }
        }

    /**
     * 网易云歌单直接获取（不经服务器）
     * 使用 NeteaseCloudMusicApi 获取歌单详情和歌曲列表
     * 第1步: /playlist/detail 获取歌单信息
     * 第2步: /playlist/track/all 获取全部歌曲（分页，每页100首，支持万首以上歌单）
     */
    private fun fetchNeteasePlaylistDirect(playlistId: String): ImportedPlaylistData? {
        try {
            // 第1步：获取歌单基本信息
            val detailUrl = "$NETEASE_PLAYLIST_API_BASE/playlist/detail?id=${URLEncoder.encode(playlistId, "UTF-8")}"
            val detailBody = searchGet(detailUrl) ?: return null
            val detailJson = parseLooseJsonObject(detailBody) ?: return null
            if (detailJson.optInt("code", 0) != 200) return null
            val playlist = detailJson.optJSONObject("playlist") ?: return null
            val playlistName = playlist.optString("name", "未知歌单")
            val coverUrl = playlist.optString("coverImgUrl", "")
            val trackCount = playlist.optInt("trackCount", 0)
            Log.d(TAG, "网易云歌单: $playlistName, 共 $trackCount 首")

            // 第2步：获取全部歌曲（/playlist/track/all 支持分页）
            val allSongs = mutableListOf<Song>()
            val limit = 100  // 每页100首，减少请求次数
            var offset = 0
            var fetched = 0
            var consecutiveErrors = 0
            do {
                val trackUrl = "$NETEASE_PLAYLIST_API_BASE/playlist/track/all?id=${URLEncoder.encode(playlistId, "UTF-8")}&limit=$limit&offset=$offset"
                // 使用 longClient 而非 searchClient，超时更长，支持大歌单
                val trackBody = try {
                    val reqBuilder = Request.Builder().url(trackUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                        .header("Accept", "application/json")
                    longClient.newCall(reqBuilder.build()).execute().use { it.body?.string() }
                } catch (e: Exception) {
                    Log.w(TAG, "获取歌曲分页失败 offset=$offset: ${e.message}")
                    null
                }

                if (trackBody.isNullOrEmpty()) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 3) {
                        Log.w(TAG, "连续3次失败，停止获取。已获取 ${allSongs.size} 首")
                        break
                    }
                    // 等待后重试
                    Thread.sleep(500)
                    continue
                }

                val trackJson = parseLooseJsonObject(trackBody)
                if (trackJson == null || trackJson.optInt("code", 0) != 200) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 3) break
                    Thread.sleep(500)
                    continue
                }

                consecutiveErrors = 0
                val songsArr = trackJson.optJSONArray("songs") ?: break
                if (songsArr.length() == 0) break
                for (i in 0 until songsArr.length()) {
                    val songObj = songsArr.getJSONObject(i)
                    val songId = songObj.optLong("id", 0)
                    val songName = songObj.optString("name", "")
                    val artistsArr = songObj.optJSONArray("ar") ?: songObj.optJSONArray("artists")
                    val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                        (0 until artistsArr.length()).joinToString("/") {
                            artistsArr.getJSONObject(it).optString("name", "")
                        }
                    } else "未知"
                    val albumObj = songObj.optJSONObject("al") ?: songObj.optJSONObject("album")
                    val albumName = albumObj?.optString("name", "") ?: ""
                    val songCover = albumObj?.optString("picUrl", "") ?: ""
                    val duration = songObj.optLong("dt", songObj.optLong("duration", 0))
                    allSongs.add(
                        Song(
                            id = songId,
                            title = songName,
                            artist = artistName,
                            album = albumName,
                            duration = duration,
                            coverUrl = songCover,
                            artistPicUrl = "",
                            platform = "网易云",
                            platformId = songId.toString(),
                        )
                    )
                }
                fetched = songsArr.length()
                offset += fetched
                Log.d(TAG, "已获取 $offset/${if (trackCount > 0) trackCount else "?"} 首")
            } while (fetched >= limit && (trackCount <= 0 || offset < trackCount))

            Log.d(TAG, "网易云歌单直接获取成功: $playlistName, ${allSongs.size}首")
            return ImportedPlaylistData(name = playlistName, coverUrl = coverUrl, songs = allSongs)
        } catch (e: Exception) {
            Log.e(TAG, "网易云歌单直接获取失败: $playlistId", e)
            return null
        }
    }

    /**
     * QQ音乐歌单直接获取（不经服务器）
     * 使用 QQ 音乐公开接口获取歌单详情和歌曲列表
     */
    private fun fetchQQPlaylistDirect(playlistId: String): ImportedPlaylistData? {
        // 尝试多个 QQ 音乐歌单 API
        val urls = listOf(
            "https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg?newsong=1&id=$playlistId&format=json&inCharset=utf8&outCharset=utf-8",
            "https://i.y.qq.com/qzone-music/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=$playlistId&format=json",
            "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?type=top&topid=$playlistId&format=json&inCharset=utf8&outCharset=utf-8",
            "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?topid=$playlistId&format=json&inCharset=utf8&outCharset=utf-8",
        )
        val headers = mapOf(
            "Referer" to "https://y.qq.com",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
        )

        for (url in urls) {
            try {
                val reqBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                val body = longClient.newCall(reqBuilder.build()).execute().use { it.body?.string() }
                if (body.isNullOrEmpty()) continue

                val result = parseQQPlaylistResponse(body)
                if (result != null && result.songs.isNotEmpty()) return result
            } catch (e: Exception) {
                Log.w(TAG, "QQ歌单API尝试失败: ${e.message}")
            }
        }
        return null
    }

    /**
     * 解析 QQ 音乐歌单 JSON 响应
     */
    private fun parseQQPlaylistResponse(body: String): ImportedPlaylistData? {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            val start = body.indexOf('{')
            val end = body.lastIndexOf('}')
            if (start >= 0 && end > start) {
                try {
                    JSONObject(body.substring(start, end + 1))
                } catch (_: Exception) {
                    return null
                }
            } else {
                return null
            }
        }

        // fcg_v8_playlist_cp 格式
        val data = json.optJSONObject("data")
        val cdlist = json.optJSONArray("cdlist")
            ?: data?.optJSONArray("cdlist")
        val topInfo = data?.optJSONObject("topinfo")

        val cd = cdlist?.optJSONObject(0)
            ?: data?.optJSONObject("cdlist")  // 某些响应格式
            ?: data  // fcg_ucc 格式
            ?: return null

        val dissname = cd.optString("dissname", "").ifBlank {
            cd.optString("dirinfo")?.let {
                try { JSONObject(it).optString("title", "") } catch (_: Exception) { "" }
            }.orEmpty()
        }.ifBlank {
            topInfo?.optString("ListName", "").orEmpty()
        }.ifBlank {
            cd.optString("title", "QQ\u97f3\u4e50\u6b4c\u5355")
        }
        val logo = firstValidImageUrl(cd.optString("logo", "")).ifBlank {
            firstValidImageUrl(
                topInfo?.optString("pic", ""),
                topInfo?.optString("pic_v12", ""),
                topInfo?.optString("headPic_v12", ""),
                topInfo?.optString("headPic", ""),
            )
        }.ifBlank {
            cd.optString("dirinfo")?.let {
                try { JSONObject(it).optString("picurl", "") } catch (_: Exception) { "" }
            }.orEmpty()
        }
        val songlist = cd.optJSONArray("songlist")
            ?: data?.optJSONArray("songlist")
            ?: data?.optJSONArray("list")
            ?: return null
        val songs = mutableListOf<Song>()

        for (i in 0 until songlist.length()) {
            val raw = songlist.optJSONObject(i) ?: continue
            val s = raw.optJSONObject("data")
                ?: raw.optJSONObject("songInfo")
                ?: raw
            val mid = s.optString("songmid", "").ifBlank {
                s.optString("songMid", "").ifBlank { s.optString("mid", "") }
            }
            val songName = s.optString("songname", "").ifBlank {
                s.optString("title", "").ifBlank { s.optString("name", "") }
            }
            if (songName.isBlank()) continue

            // 解析歌手
            val singerArr = s.optJSONArray("singer")
            val artistName = if (singerArr != null && singerArr.length() > 0) {
                (0 until singerArr.length()).mapNotNull {
                    singerArr.optJSONObject(it)?.optString("name", "")?.takeIf { n -> n.isNotBlank() }
                }.joinToString("/")
            } else "\u672a\u77e5"

            val albumName = s.optString("albumname", "").ifBlank {
                s.optString("albumName", "").ifBlank {
                    s.optJSONObject("album")?.optString("name", "").orEmpty()
                }
            }
            val albumMid = s.optString("albummid", "").ifBlank {
                s.optString("albumMid", "").ifBlank {
                    s.optString("albumPMid", "").ifBlank {
                        s.optJSONObject("album")?.optString("mid", "").orEmpty()
                    }
                }
            }
            val coverUrl = firstValidImageUrl(
                qqAlbumCoverUrl(albumMid),
                s.optString("albumpic", ""),
                s.optString("albumpic_big", ""),
                s.optString("albumpic_small", ""),
                s.optString("albumPic", ""),
                s.optString("pic", ""),
                s.optString("coverUrl", ""),
                s.optString("picUrl", ""),
                s.optJSONObject("album")?.optString("picUrl", ""),
                cachedSongCover("QQ音乐", mid),
            )
            val durationSec = s.optLong("interval", s.optLong("duration", 0))

            songs.add(Song(
                id = s.optLong("songid", s.optLong("id", searchIdCounter.incrementAndGet())),
                title = songName,
                artist = artistName,
                album = albumName,
                duration = durationSec * 1000,
                coverUrl = coverUrl,
                platform = "QQ\u97f3\u4e50",
                platformId = mid,
            ))
        }

        if (songs.isEmpty()) return null
        Log.d(TAG, "QQ歌单解析: name=$dissname, songs=${songs.size}")
        rememberSongCovers(songs, forcePersist = true)
        return ImportedPlaylistData(name = dissname, coverUrl = logo, songs = songs)
    }

    // ═══════════════════════════════════════
    //  推荐歌单: 四大平台精选
    // ═══════════════════════════════════════

    suspend fun fetchRecommendPlaylists(): List<RecommendPlaylist> = withContext(Dispatchers.IO) {
        try {
            val body = serverGet("/api/recommend/playlists")
            if (body.isNullOrEmpty()) return@withContext emptyList()
            val json = JSONObject(body)
            if (json.optInt("code", 0) != 200) return@withContext emptyList()
            val arr = json.optJSONArray("data") ?: return@withContext emptyList()
            val results = mutableListOf<RecommendPlaylist>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val platform = item.optString("platform", "")
                val playlistId = item.optString("playlistId", "")
                val cover = cachedRecommendCover(platform, playlistId)
                results.add(RecommendPlaylist(
                    name = item.optString("name", ""),
                    coverUrl = cover,
                    playCount = item.optLong("playCount", 0),
                    platform = platform,
                    playlistId = playlistId,
                ))
            }
            Log.d(TAG, "推荐歌单: ${results.size} 个(已应用本地封面缓存)")
            results
        } catch (e: Exception) {
            Log.e(TAG, "获取推荐歌单失败", e)
            emptyList()
        }
    }

    /**
     * 对缺失封面的歌曲做异步补全：
     * 直接调用各平台官方接口回填封面，并持久化到前端本地缓存。
     */
    suspend fun enrichMissingSongCovers(
        songs: List<Song>,
        maxLookup: Int = 60,
        parallelism: Int = 6,
    ): List<Song> = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext songs
        ensureSongCoverCacheLoaded()
        val pending = songs.withIndex()
            .filter { (_, song) ->
                song.coverUrl.isBlank() &&
                    song.artistPicUrl.isBlank() &&
                    song.platform.isNotBlank() &&
                    song.platformId.isNotBlank()
            }
            .take(maxLookup.coerceAtLeast(0))
        if (pending.isEmpty()) return@withContext songs
        val merged = songs.toMutableList()
        var changed = false
        val unresolved = mutableListOf<IndexedValue<Song>>()

        pending.forEach { (index, song) ->
            val cached = cachedSongCover(song.platform, song.platformId)
            if (cached.isNotBlank()) {
                merged[index] = song.copy(coverUrl = cached)
                changed = true
            } else {
                unresolved.add(IndexedValue(index, song))
            }
        }
        if (unresolved.isEmpty()) {
            return@withContext if (changed) merged else songs
        }

        // 网易云优先批量补图，减少请求数
        val unresolvedAfterNetease = unresolved.toMutableList()
        val neteaseItems = unresolved.filter { (_, song) ->
            val p = song.platform.trim()
            (p.contains("网易") || p == "netease") && song.platformId.toLongOrNull() != null
        }
        if (neteaseItems.isNotEmpty()) {
            val neteaseIds = neteaseItems.mapNotNull { (_, song) -> song.platformId.toLongOrNull() }.distinct()
            val neteaseCoverMap = fetchNeteaseCovers(neteaseIds)
            if (neteaseCoverMap.isNotEmpty()) {
                val keep = mutableListOf<IndexedValue<Song>>()
                unresolvedAfterNetease.forEach { item ->
                    val neteaseId = item.value.platformId.toLongOrNull()
                    val cover = neteaseId?.let { neteaseCoverMap[it] }.orEmpty()
                    if (cover.isNotBlank()) {
                        val normalized = firstValidImageUrl(cover)
                        if (normalized.isNotBlank()) {
                            merged[item.index] = item.value.copy(coverUrl = normalized)
                            cacheSongCover(item.value.platform, item.value.platformId, normalized, forcePersist = false)
                            changed = true
                        } else {
                            keep.add(item)
                        }
                    } else {
                        keep.add(item)
                    }
                }
                unresolvedAfterNetease.clear()
                unresolvedAfterNetease.addAll(keep)
            }
        }

        if (unresolvedAfterNetease.isNotEmpty()) {
            val semaphore = Semaphore(parallelism.coerceAtLeast(1))
            val updates = coroutineScope {
                unresolvedAfterNetease.map { (index, song) ->
                    async {
                        semaphore.withPermit {
                            try {
                                val cover = firstValidImageUrl(fetchOfficialCoverForSong(song))
                                if (cover.isNotBlank()) Triple(index, song, cover) else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            updates.forEach { (index, song, cover) ->
                merged[index] = song.copy(coverUrl = cover)
                cacheSongCover(song.platform, song.platformId, cover, forcePersist = false)
                changed = true
            }
        }

        if (changed) flushSongCoverCacheIfDirty()
        return@withContext if (changed) merged else songs

    }

}
