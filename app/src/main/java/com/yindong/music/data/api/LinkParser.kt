package com.yindong.music.data.api

/**
 * 歌单链接解析结果
 *
 * [source] 为 LX 源 id（"wy"/"tx"/"kw"/"kg"），用于直接调用 LxSdkSongList.getListDetail；
 * [playlistId] 为已从 URL 中提取出的歌单 id（纯数字）。
 */
data class ParsedLink(
    val source: String,    // "wy" | "tx" | "kw" | "kg"
    val playlistId: String,
)

/**
 * 外部歌单链接解析器
 *
 * 严格遵循 lx-music-mobile 各 songList.js 中 regExps 的实现：
 * - src/utils/musicSdk/wy/songList.js （listDetailLink / listDetailLink2）
 * - src/utils/musicSdk/tx/songList.js （listDetailLink / listDetailLink2）
 * - src/utils/musicSdk/kw/songList.js （listDetailLink）
 * - src/utils/musicSdk/kg/songList.js （listDetailLink）
 *
 * 解析流程：
 * 1. 通过域名确定平台 → 2. 应用对应平台正则提取歌单 id → 3. 返回 LX 源 id + 歌单 id
 *
 * 支持从用户输入文本中识别 QQ音乐、网易云、酷我音乐、酷狗音乐 的歌单链接，
 * 也支持从分享口令文本中提取嵌入的 URL。
 */
object LinkParser {

    // URL 提取正则（从分享口令文本中提取链接）
    private val URL_EXTRACTOR = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""")

    /**
     * 从用户输入文本中解析歌单链接
     * 支持直接 URL 和分享口令（含短链接的文本）
     * @return [ParsedLink] 或 null（无法识别时）
     */
    fun parse(input: String): ParsedLink? {
        if (input.isBlank()) return null

        // 先尝试直接匹配整段文本
        matchPlatform(input)?.let { return it }

        // 从文本中提取所有 URL，逐个尝试匹配
        val urls = URL_EXTRACTOR.findAll(input).map { it.value }.toList()
        for (url in urls) {
            matchPlatform(url)?.let { return it }
        }

        return null
    }

    /**
     * 通过 URL 域名判定平台，再调用对应平台的正则提取歌单 id
     */
    private fun matchPlatform(text: String): ParsedLink? {
        return when {
            // 网易云：music.163.com / 163cn.tv / y.music.163.com
            text.contains("music.163.com") ||
                text.contains("163cn.tv") ||
                text.contains("y.music.163.com") -> matchWy(text)

            // QQ音乐：y.qq.com / i.y.qq.com / c.y.qq.com
            text.contains("y.qq.com") ||
                text.contains("i.y.qq.com") ||
                text.contains("c.y.qq.com") -> matchTx(text)

            // 酷我：kuwo.cn
            text.contains("kuwo.cn") -> matchKw(text)

            // 酷狗：kugou.com
            text.contains("kugou.com") -> matchKg(text)

            else -> null
        }
    }

    // ── 网易云（与 wy/songList.js regExps 一致） ──
    // listDetailLink: /^.+(?:\?|&)id=(\d+)(?:&.*$|#.*$|$)/
    // listDetailLink2: /^.+\/playlist\/(\d+)\/\d+\/.+$/
    private val wyListDetailLinkRegex = Regex("^.+(?:\\?|&)id=(\\d+)(?:&.*$|#.*$|$)")
    private val wyListDetailLink2Regex = Regex("^.+/playlist/(\\d+)/\\d+/.+$")

    private fun matchWy(text: String): ParsedLink? {
        wyListDetailLinkRegex.find(text)?.let {
            return ParsedLink("wy", it.groupValues[1])
        }
        wyListDetailLink2Regex.find(text)?.let {
            return ParsedLink("wy", it.groupValues[1])
        }
        return null
    }

    // ── QQ音乐（与 tx/songList.js regExps 一致 + 扩展） ──
    // listDetailLink: /\/playlist\/(\d+)/
    // listDetailLink2: /id=(\d+)/
    // 扩展：/playsquare/(\d+) 路径
    private val txListDetailLinkRegex = Regex("/playlist/(\\d+)")
    private val txListDetailLink2Regex = Regex("id=(\\d+)")
    private val txListDetailLink3Regex = Regex("/playsquare/(\\d+)")

    private fun matchTx(text: String): ParsedLink? {
        txListDetailLinkRegex.find(text)?.let {
            return ParsedLink("tx", it.groupValues[1])
        }
        txListDetailLink2Regex.find(text)?.let {
            return ParsedLink("tx", it.groupValues[1])
        }
        txListDetailLink3Regex.find(text)?.let {
            return ParsedLink("tx", it.groupValues[1])
        }
        return null
    }

    // ── 酷我（与 kw/songList.js regExps 一致 + 扩展） ──
    // listDetailLink: /^.+\/playlist(?:_detail)?\/(\d+)(?:\?.*|&.*$|#.*$|$)/
    // 扩展：playlists/ 路径、playlistId= 查询参数
    private val kwListDetailLinkRegex = Regex("/playlists?(?:_detail)?/(\\d+)")
    private val kwListDetailLink2Regex = Regex("playlistId=(\\d+)")

    private fun matchKw(text: String): ParsedLink? {
        kwListDetailLinkRegex.find(text)?.let {
            return ParsedLink("kw", it.groupValues[1])
        }
        kwListDetailLink2Regex.find(text)?.let {
            return ParsedLink("kw", it.groupValues[1])
        }
        return null
    }

    // ── 酷狗（与 kg/songList.js regExps 一致 + 扩展） ──
    // listDetailLink: /^.+\/(\d+)\.html(?:\?.*|&.*$|#.*$|$)/
    // 扩展：/special/(?:single/)?(\d+) 路径
    private val kgListDetailLinkRegex = Regex("/(\\d+)\\.html(?:\\?.*|&.*$|#.*$|$)")
    private val kgListDetailLink2Regex = Regex("/special/(?:single/)?(\\d+)")

    private fun matchKg(text: String): ParsedLink? {
        kgListDetailLinkRegex.find(text)?.let {
            return ParsedLink("kg", it.groupValues[1])
        }
        kgListDetailLink2Regex.find(text)?.let {
            return ParsedLink("kg", it.groupValues[1])
        }
        return null
    }
}
