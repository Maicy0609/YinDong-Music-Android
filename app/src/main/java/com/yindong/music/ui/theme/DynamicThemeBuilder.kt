package com.yindong.music.ui.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 动态主题构建器
 *
 * 借鉴 SPICaMusic / SimpMusic 的实现思路：
 * 1. 通过 Coil 加载封面 Bitmap（小尺寸，性能优先）
 * 2. 使用 androidx.palette 提取主色板
 * 3. 根据主色板生成完整的 [ColorSchemeConfig]，覆盖所有 UI 颜色槽位
 *
 * 生成策略：
 * - accentPrimary: Vibrant / LightVibrant 主色（亮色）
 * - accentSecondary: Muted 主色
 * - accentTertiary: DarkVibrant 主色
 * - 玻璃背景：主色与黑/白混合后的低饱和深/浅色
 * - 文本：依据明度自动取反色
 */
object DynamicThemeBuilder {

    /** 缓存最近一次提取结果，避免重复 IO 与 Palette 计算 */
    @Volatile
    private var lastCoverUrl: String? = null
    @Volatile
    private var lastScheme: ColorSchemeConfig? = null

    /**
     * 从封面 URL 提取颜色并生成 [ColorSchemeConfig]。
     * 在 IO 线程执行，调用方无需切换调度器。
     *
     * @return 提取失败或 URL 为空时返回 null
     */
    suspend fun extract(context: Context, coverUrl: String): ColorSchemeConfig? =
        withContext(Dispatchers.IO) {
            if (coverUrl.isBlank()) return@withContext null

            // 同一封面命中缓存，直接返回
            synchronized(this) {
                if (lastCoverUrl == coverUrl && lastScheme != null) {
                    return@withContext lastScheme
                }
            }

            runCatching {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)   // Palette 需要软件位图
                    .size(256)              // 小尺寸足够提取颜色，更快
                    .build()
                val result = loader.execute(request)
                val bitmap = (result as? SuccessResult)
                    ?.drawable?.let { it as? BitmapDrawable }?.bitmap
                    ?: return@runCatching null

                val palette = Palette.from(bitmap)
                    .maximumColorCount(24)
                    .clearFilters()
                    .generate()

                val scheme = buildFromPalette(palette)
                synchronized(this) {
                    lastCoverUrl = coverUrl
                    lastScheme = scheme
                }
                scheme
            }.getOrNull()
        }

    /** 清除缓存（用于关闭动态主题或应用退出） */
    fun clearCache() {
        synchronized(this) {
            lastCoverUrl = null
            lastScheme = null
        }
    }

    /** 根据 Palette 构建 [ColorSchemeConfig] */
    private fun buildFromPalette(palette: Palette): ColorSchemeConfig {
        // ── 提取关键 swatch ──
        val vibrantSw = palette.vibrantSwatch
        val darkVibrantSw = palette.darkVibrantSwatch
        val lightVibrantSw = palette.lightVibrantSwatch
        val mutedSw = palette.mutedSwatch
        val darkMutedSw = palette.darkMutedSwatch
        val lightMutedSw = palette.lightMutedSwatch
        val dominantSw = palette.dominantSwatch

        // ── 主色（优先 Vibrant，保证视觉识别度） ──
        val primaryRgb = vibrantSw?.rgb
            ?: lightVibrantSw?.rgb
            ?: dominantSw?.rgb
            ?: 0xFF3B82F6.toInt()

        // ── 辅助色（Muted，更柔和） ──
        val secondaryRgb = mutedSw?.rgb
            ?: lightMutedSw?.rgb
            ?: shiftHue(primaryRgb, 30f)

        // ── 第三色（DarkVibrant，深沉） ──
        val tertiaryRgb = darkVibrantSw?.rgb
            ?: darkMutedSw?.rgb
            ?: shiftHue(primaryRgb, -30f)

        // ── dominant：封面占比最大色 ──
        val dominantRgb = dominantSw?.rgb ?: primaryRgb

        // ── 派生：明/暗主题色 ──
        val primaryLight = adjustHsv(primaryRgb, dS = +0.05f, dV = +0.15f)
        val primaryDark = adjustHsv(primaryRgb, dS = -0.05f, dV = -0.20f)

        val secondaryLight = adjustHsv(secondaryRgb, dS = +0.05f, dV = +0.15f)
        val secondaryDark = adjustHsv(secondaryRgb, dS = -0.05f, dV = -0.20f)

        val tertiaryLight = adjustHsv(tertiaryRgb, dS = +0.05f, dV = +0.15f)
        val tertiaryDark = adjustHsv(tertiaryRgb, dS = -0.05f, dV = -0.20f)

        // ── 玻璃背景：主色混合深色，保证可读性 ──
        val glassDarkBg = mixWithBlack(dominantRgb, 0.78f)            // 主导色 + 黑
        val glassDarkBgLight = mixWithBlack(dominantRgb, 0.70f)
        val glassDarkSurface = mixWithBlack(dominantRgb, 0.72f)
        val glassDarkSurfaceVariant = mixWithBlack(dominantRgb, 0.66f)
        val glassDarkSurfaceElevated = mixWithBlack(dominantRgb, 0.55f)

        val glassLightBg = mixWithWhite(dominantRgb, 0.92f)
        val glassLightSurface = mixWithWhite(dominantRgb, 0.85f)
        val glassLightSurfaceVariant = mixWithWhite(dominantRgb, 0.78f)
        val glassLightSurfaceElevated = mixWithWhite(dominantRgb, 0.70f)

        val darkBg = mixWithBlack(dominantRgb, 0.82f)
        val darkBgLight = mixWithBlack(dominantRgb, 0.74f)

        // ── 玻璃文本 ──
        val glassTextPrimaryDark = Color(mixWithWhite(primaryLight, 0.85f))
        val glassTextSecondaryDark = Color(mixWithWhite(primaryLight, 0.65f))
        val glassTextTertiaryDark = Color(mixWithWhite(primaryLight, 0.45f))

        val glassTextPrimaryLight = Color(mixWithBlack(primaryDark, 0.85f))
        val glassTextSecondaryLight = Color(mixWithBlack(primaryDark, 0.65f))
        val glassTextTertiaryLight = Color(mixWithBlack(primaryDark, 0.45f))

        // ── 边框 / 分割线 ──
        val glassBorder = Color(primaryLight).copy(alpha = 0.30f)
        val glassBorderStrong = Color(primaryLight).copy(alpha = 0.50f)
        val dividerGlass = Color(primaryLight).copy(alpha = 0.15f)

        // ── 主色 AppColors ──
        val darkAppColors = AppColors(
            accentPrimary = Color(primaryLight),
            accentSecondary = Color(secondaryLight),
            accentTertiary = Color(tertiaryLight),
            textPrimary = glassTextPrimaryDark,
            textSecondary = glassTextSecondaryDark,
            textTertiary = glassTextTertiaryDark,
            textHint = glassTextTertiaryDark.copy(alpha = 0.55f),
            glassBackground = Color(glassDarkBg),
            glassSurface = Color(glassDarkSurface).copy(alpha = 0.80f),
            glassSurfaceVariant = Color(glassDarkSurfaceVariant).copy(alpha = 0.90f),
            glassBorder = glassBorder,
            neumorphShadowLight = glassBorder,
            neumorphShadowDark = Color.Black.copy(alpha = 0.50f),
            neumorphBackground = Color(glassDarkSurface).copy(alpha = 0.85f),
            divider = dividerGlass,
            tagBlue = Color(primaryLight),
            tagGreen = Color(secondaryLight),
            tagOrange = Color(0xFFFFB74D),
            tagPurple = Color(0xFFCE93D8),
            tagPink = Color(0xFFF48FB1),
            tagCyan = Color(tertiaryLight),
            gradientStart = Color(darkBg),
            gradientEnd = Color(darkBgLight),
            success = Color(secondaryLight),
            warning = Color(0xFFFFB74D),
            error = Color(0xFFEF5350),
            info = Color(primaryLight),
        )

        val lightAppColors = AppColors(
            accentPrimary = Color(primaryDark),
            accentSecondary = Color(secondaryDark),
            accentTertiary = Color(tertiaryDark),
            textPrimary = glassTextPrimaryLight,
            textSecondary = glassTextSecondaryLight,
            textTertiary = glassTextTertiaryLight,
            textHint = glassTextTertiaryLight.copy(alpha = 0.55f),
            glassBackground = Color(glassLightBg),
            glassSurface = Color(glassLightSurface).copy(alpha = 0.80f),
            glassSurfaceVariant = Color(glassLightSurfaceVariant).copy(alpha = 0.90f),
            glassBorder = glassBorder,
            neumorphShadowLight = Color.White,
            neumorphShadowDark = Color.Black.copy(alpha = 0.30f),
            neumorphBackground = Color(glassLightSurfaceVariant),
            divider = dividerGlass,
            tagBlue = Color(primaryDark),
            tagGreen = Color(secondaryDark),
            tagOrange = Color(0xFFFF9800),
            tagPurple = Color(0xFF9C27B0),
            tagPink = Color(0xFFE91E63),
            tagCyan = Color(tertiaryDark),
            gradientStart = Color(glassLightBg),
            gradientEnd = Color(glassLightSurfaceVariant),
            success = Color(secondaryDark),
            warning = Color(0xFFFF9800),
            error = Color(0xFFF44336),
            info = Color(primaryDark),
        )

        // ── 渐变色板（10 组，用于封面/卡片背景） ──
        val glassGradients = listOf(
            listOf(Color(darkBg), Color(glassDarkBg)),
            listOf(Color(glassDarkBgLight), Color(glassDarkSurfaceVariant)),
            listOf(Color(primaryDark), Color(darkBg)),
            listOf(Color(secondaryDark), Color(darkBg)),
            listOf(Color(primaryDark), Color(secondaryDark)),
            listOf(Color(primaryLight), Color(primaryDark)),
            listOf(Color(glassDarkSurfaceElevated), Color(darkBg)),
            listOf(Color(tertiaryDark), Color(darkBg)),
            listOf(Color(glassDarkSurfaceVariant), Color(darkBgLight)),
            listOf(Color(primaryLight), Color(secondaryDark)),
        )

        // ── 按钮渐变 ──
        val buttonGradientPrimary = listOf(Color(primaryDark), Color(darkBg))
        val buttonGradientSecondary = listOf(Color(secondaryDark), Color(primaryDark))
        val buttonGradientAccent = listOf(Color(primaryLight), Color(primaryDark))
        val buttonGradientSuccess = listOf(Color(secondaryLight), Color(secondaryDark))
        val buttonGradientWarning = listOf(Color(0xFFFF9800), Color(0xFFF57C00))

        // ── 卡片玻璃 ──
        val cardGlassBackgroundDark = Color(glassDarkSurface).copy(alpha = 0.55f)
        val cardGlassBorderDark = glassBorder
        val cardGlassHighlightDark = Color(primaryLight).copy(alpha = 0.12f)
        val cardGlassBackgroundLight = Color(glassLightSurface).copy(alpha = 0.85f)
        val cardGlassBorderLight = glassBorder
        val cardGlassHighlightLight = Color(primaryDark).copy(alpha = 0.22f)

        // ── 6 个快捷入口主题色 ──
        val themeColors = listOf(
            Color(primaryDark),
            Color(primaryLight),
            Color(secondaryDark),
            Color(secondaryLight),
            Color(tertiaryDark),
            Color(tertiaryLight),
        )

        return ColorSchemeConfig(
            id = "dynamic_cover",
            name = "封面动态色",
            previewColor = Color(primaryRgb),
            darkAppColors = darkAppColors,
            lightAppColors = lightAppColors,
            darkBg = Color(darkBg),
            darkBgLight = Color(darkBgLight),
            glassDarkBackground = Color(glassDarkBg),
            glassDarkSurface = Color(glassDarkSurface).copy(alpha = 0.80f),
            glassDarkSurfaceVariant = Color(glassDarkSurfaceVariant).copy(alpha = 0.90f),
            glassDarkSurfaceElevated = Color(glassDarkSurfaceElevated),
            glassLightBackground = Color(glassLightBg),
            glassLightSurface = Color(glassLightSurface).copy(alpha = 0.80f),
            glassLightSurfaceVariant = Color(glassLightSurfaceVariant).copy(alpha = 0.90f),
            glassLightSurfaceElevated = Color(glassLightSurfaceElevated),
            neumorphDarkBackground = Color(glassDarkSurface).copy(alpha = 0.85f),
            neumorphLightBackground = Color(glassLightSurfaceVariant),
            neumorphShadowLight = glassBorder,
            neumorphShadowDark = Color.Black.copy(alpha = 0.50f),
            neumorphShadowLightStrong = glassBorderStrong,
            neumorphShadowDarkStrong = Color.Black.copy(alpha = 0.60f),
            glassBorder = glassBorder,
            glassBorderStrong = glassBorderStrong,
            glassTextPrimaryDark = glassTextPrimaryDark,
            glassTextSecondaryDark = glassTextSecondaryDark,
            glassTextTertiaryDark = glassTextTertiaryDark,
            glassTextPrimaryLight = glassTextPrimaryLight,
            glassTextSecondaryLight = glassTextSecondaryLight,
            glassTextTertiaryLight = glassTextTertiaryLight,
            iconInactive = glassTextTertiaryDark,
            dividerGlass = dividerGlass,
            glassGradients = glassGradients,
            tagRedSoft = Color(0xFFEF5350),
            tagBlueSoft = Color(primaryLight),
            tagGreenSoft = Color(secondaryLight),
            tagOrangeSoft = Color(0xFFFFB74D),
            tagPurpleSoft = Color(0xFFCE93D8),
            tagPinkSoft = Color(0xFFF48FB1),
            tagCyanSoft = Color(tertiaryLight),
            successSoft = Color(secondaryLight),
            warningSoft = Color(0xFFFFB74D),
            errorSoft = Color(0xFFEF5350),
            infoSoft = Color(primaryLight),
            accentRedLight = Color(primaryLight),
            accentBlue = Color(primaryLight),
            accentBlueLight = Color(primaryLight),
            accentBlueDark = Color(primaryDark),
            primaryBlack = Color(darkBg),
            primaryBlackLight = Color(darkBgLight),
            accentGray = Color(secondaryDark),
            accentGrayLight = Color(secondaryLight),
            accentGrayDark = Color(secondaryDark),
            primaryGreen = Color(primaryDark),
            primaryGreenLight = Color(primaryLight),
            primaryGreenDark = Color(primaryDark),
            accentCyan = Color(tertiaryDark),
            accentCyanLight = Color(tertiaryLight),
            accentCyanDark = Color(tertiaryDark),
            buttonGradientPrimary = buttonGradientPrimary,
            buttonGradientSecondary = buttonGradientSecondary,
            buttonGradientAccent = buttonGradientAccent,
            buttonGradientSuccess = buttonGradientSuccess,
            buttonGradientWarning = buttonGradientWarning,
            buttonBorderDark = glassBorder,
            buttonBorderLight = glassBorder,
            buttonBorderActiveDark = glassBorderStrong,
            buttonBorderActiveLight = glassBorderStrong,
            buttonShadowGlowDark = Color(primaryLight).copy(alpha = 0.35f),
            buttonShadowGlowLight = Color(primaryDark).copy(alpha = 0.30f),
            cardGlassBackgroundDark = cardGlassBackgroundDark,
            cardGlassBorderDark = cardGlassBorderDark,
            cardGlassHighlightDark = cardGlassHighlightDark,
            cardGlassBackgroundLight = cardGlassBackgroundLight,
            cardGlassBorderLight = cardGlassBorderLight,
            cardGlassHighlightLight = cardGlassHighlightLight,
            themeColors = themeColors,
        )
    }

    // ── 颜色工具函数 ──

    /** 调整 HSV 的饱和度与明度 */
    private fun adjustHsv(rgb: Int, dS: Float = 0f, dV: Float = 0f): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = (hsv[1] + dS).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] + dV).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    /** 旋转色相 */
    private fun shiftHue(rgb: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[0] = (hsv[0] + degrees + 360f) % 360f
        return android.graphics.Color.HSVToColor(hsv)
    }

    /** 与黑色混合：ratio 越大越黑 */
    private fun mixWithBlack(rgb: Int, ratio: Float): Int {
        val r = android.graphics.Color.red(rgb)
        val g = android.graphics.Color.green(rgb)
        val b = android.graphics.Color.blue(rgb)
        val nr = (r * (1 - ratio)).toInt()
        val ng = (g * (1 - ratio)).toInt()
        val nb = (b * (1 - ratio)).toInt()
        return android.graphics.Color.rgb(nr, ng, nb)
    }

    /** 与白色混合：ratio 越大越白 */
    private fun mixWithWhite(rgb: Int, ratio: Float): Int {
        val r = android.graphics.Color.red(rgb)
        val g = android.graphics.Color.green(rgb)
        val b = android.graphics.Color.blue(rgb)
        val nr = (r + (255 - r) * ratio).toInt()
        val ng = (g + (255 - g) * ratio).toInt()
        val nb = (b + (255 - b) * ratio).toInt()
        return android.graphics.Color.rgb(nr, ng, nb)
    }
}
