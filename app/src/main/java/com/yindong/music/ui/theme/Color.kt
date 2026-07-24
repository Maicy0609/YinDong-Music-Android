package com.yindong.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textHint: Color,
    val glassBackground: Color,
    val glassSurface: Color,
    val glassSurfaceVariant: Color,
    val glassBorder: Color,
    val neumorphShadowLight: Color,
    val neumorphShadowDark: Color,
    val neumorphBackground: Color,
    val divider: Color,
    val tagBlue: Color,
    val tagGreen: Color,
    val tagOrange: Color,
    val tagPurple: Color,
    val tagPink: Color,
    val tagCyan: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

val LocalAppColors = staticCompositionLocalOf { PureWhiteColorScheme.lightAppColors }
val LocalColorScheme = staticCompositionLocalOf { PureWhiteColorScheme }

val DarkAppColors: AppColors
    @Composable get() = LocalColorScheme.current.darkAppColors
val LightAppColors: AppColors
    @Composable get() = LocalColorScheme.current.lightAppColors

val PrimaryBlack: Color
    @Composable get() = LocalColorScheme.current.primaryBlack
val PrimaryBlackLight: Color
    @Composable get() = LocalColorScheme.current.primaryBlackLight
val PrimaryBlackDark: Color
    @Composable get() = LocalColorScheme.current.primaryBlack

val AccentGray: Color
    @Composable get() = LocalColorScheme.current.accentGray
val AccentGrayLight: Color
    @Composable get() = LocalColorScheme.current.accentGrayLight
val AccentGrayDark: Color
    @Composable get() = LocalColorScheme.current.accentGrayDark

val PrimaryGreen: Color
    @Composable get() = LocalColorScheme.current.primaryGreen
val PrimaryGreenLight: Color
    @Composable get() = LocalColorScheme.current.primaryGreenLight
val PrimaryGreenDark: Color
    @Composable get() = LocalColorScheme.current.primaryGreenDark

val AccentCyan: Color
    @Composable get() = LocalColorScheme.current.accentCyan
val AccentCyanLight: Color
    @Composable get() = LocalColorScheme.current.accentCyanLight
val AccentCyanDark: Color
    @Composable get() = LocalColorScheme.current.accentCyanDark

val DarkBg: Color
    @Composable get() = LocalColorScheme.current.darkBg
val DarkBgLight: Color
    @Composable get() = LocalColorScheme.current.darkBgLight

val GlassDarkBackground: Color
    @Composable get() = LocalColorScheme.current.glassDarkBackground
val GlassDarkSurface: Color
    @Composable get() = LocalColorScheme.current.glassDarkSurface
val GlassDarkSurfaceVariant: Color
    @Composable get() = LocalColorScheme.current.glassDarkSurfaceVariant
val GlassDarkSurfaceElevated: Color
    @Composable get() = LocalColorScheme.current.glassDarkSurfaceElevated

val GlassLightBackground: Color
    @Composable get() = LocalColorScheme.current.glassLightBackground
val GlassLightSurface: Color
    @Composable get() = LocalColorScheme.current.glassLightSurface
val GlassLightSurfaceVariant: Color
    @Composable get() = LocalColorScheme.current.glassLightSurfaceVariant
val GlassLightSurfaceElevated: Color
    @Composable get() = LocalColorScheme.current.glassLightSurfaceElevated

val NeumorphDarkBackground: Color
    @Composable get() = LocalColorScheme.current.neumorphDarkBackground
val NeumorphLightBackground: Color
    @Composable get() = LocalColorScheme.current.neumorphLightBackground

val NeumorphShadowLight: Color
    @Composable get() = LocalColorScheme.current.neumorphShadowLight
val NeumorphShadowDark: Color
    @Composable get() = LocalColorScheme.current.neumorphShadowDark
val NeumorphShadowLightStrong: Color
    @Composable get() = LocalColorScheme.current.neumorphShadowLightStrong
val NeumorphShadowDarkStrong: Color
    @Composable get() = LocalColorScheme.current.neumorphShadowDarkStrong

val GlassBorder: Color
    @Composable get() = LocalColorScheme.current.glassBorder
val GlassBorderStrong: Color
    @Composable get() = LocalColorScheme.current.glassBorderStrong

val GlassTextPrimaryDark: Color
    @Composable get() = LocalColorScheme.current.glassTextPrimaryDark
val GlassTextSecondaryDark: Color
    @Composable get() = LocalColorScheme.current.glassTextSecondaryDark
val GlassTextTertiaryDark: Color
    @Composable get() = LocalColorScheme.current.glassTextTertiaryDark

val GlassTextPrimaryLight: Color
    @Composable get() = LocalColorScheme.current.glassTextPrimaryLight
val GlassTextSecondaryLight: Color
    @Composable get() = LocalColorScheme.current.glassTextSecondaryLight
val GlassTextTertiaryLight: Color
    @Composable get() = LocalColorScheme.current.glassTextTertiaryLight

val IconInactive: Color
    @Composable get() = LocalColorScheme.current.iconInactive
val IconActive: Color
    @Composable get() = LocalAppColors.current.accentPrimary

val DividerGlass: Color
    @Composable get() = LocalColorScheme.current.dividerGlass

val GlassGradients: List<List<Color>>
    @Composable get() = LocalColorScheme.current.glassGradients

val TagRedSoft: Color
    @Composable get() = LocalColorScheme.current.tagRedSoft
val TagBlueSoft: Color
    @Composable get() = LocalColorScheme.current.tagBlueSoft
val TagGreenSoft: Color
    @Composable get() = LocalColorScheme.current.tagGreenSoft
val TagOrangeSoft: Color
    @Composable get() = LocalColorScheme.current.tagOrangeSoft
val TagPurpleSoft: Color
    @Composable get() = LocalColorScheme.current.tagPurpleSoft
val TagPinkSoft: Color
    @Composable get() = LocalColorScheme.current.tagPinkSoft
val TagCyanSoft: Color
    @Composable get() = LocalColorScheme.current.tagCyanSoft

val SuccessSoft: Color
    @Composable get() = LocalColorScheme.current.successSoft
val WarningSoft: Color
    @Composable get() = LocalColorScheme.current.warningSoft
val ErrorSoft: Color
    @Composable get() = LocalColorScheme.current.errorSoft
val InfoSoft: Color
    @Composable get() = LocalColorScheme.current.infoSoft

val AccentRed: Color
    @Composable get() = LocalAppColors.current.accentPrimary
val AccentRedLight: Color
    @Composable get() = LocalColorScheme.current.accentRedLight
val AccentRedDark: Color
    @Composable get() = LocalAppColors.current.accentPrimary
val AccentBlue: Color
    @Composable get() = LocalColorScheme.current.accentBlue
val AccentBlueLight: Color
    @Composable get() = LocalColorScheme.current.accentBlueLight
val AccentBlueDark: Color
    @Composable get() = LocalColorScheme.current.accentBlueDark

val DarkBackground: Color
    @Composable get() = LocalColorScheme.current.glassDarkBackground
val DarkSurface: Color
    @Composable get() = LocalColorScheme.current.glassDarkSurface
val DarkSurfaceVariant: Color
    @Composable get() = LocalColorScheme.current.glassDarkSurfaceVariant
val LightBackground: Color
    @Composable get() = LocalColorScheme.current.glassLightBackground
val LightSurface: Color
    @Composable get() = LocalColorScheme.current.glassLightSurface
val LightSurfaceVariant: Color
    @Composable get() = LocalColorScheme.current.glassLightSurfaceVariant

val TextPrimary: Color
    @Composable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color
    @Composable get() = LocalAppColors.current.textSecondary
val TextTertiary: Color
    @Composable get() = LocalAppColors.current.textTertiary
val TextHint: Color
    @Composable get() = LocalAppColors.current.textHint

val GradientStart: Color
    @Composable get() = LocalColorScheme.current.darkBg
val GradientEnd: Color
    @Composable get() = LocalColorScheme.current.darkBgLight

val Red500: Color
    @Composable get() = LocalAppColors.current.accentPrimary
val Red600: Color
    @Composable get() = LocalAppColors.current.accentSecondary
val TagRed: Color
    @Composable get() = LocalAppColors.current.tagPink
val TagBlue: Color
    @Composable get() = LocalAppColors.current.tagBlue
val TagGreen: Color
    @Composable get() = LocalAppColors.current.tagGreen
val TagOrange: Color
    @Composable get() = LocalAppColors.current.tagOrange
val TagPurple: Color
    @Composable get() = LocalAppColors.current.tagPurple
val DividerColor: Color
    @Composable get() = LocalColorScheme.current.dividerGlass

val NebulaPink: Color
    @Composable get() = LocalAppColors.current.accentPrimary
val NebulaViolet: Color
    @Composable get() = LocalAppColors.current.accentSecondary
val NebulaBlue: Color
    @Composable get() = LocalAppColors.current.accentTertiary

val CoverGradients: List<List<Color>>
    @Composable get() = LocalColorScheme.current.glassGradients

val ButtonGlassBackgroundDark = Color(0x3D181818)
val ButtonGlassBackgroundHoverDark = Color(0x4D282828)
val ButtonGlassBackgroundPressedDark = Color(0x55121212)

val ButtonGlassBackgroundLight = Color(0xBFFFFFFF)
val ButtonGlassBackgroundHoverLight = Color(0xCCFFFFFF)
val ButtonGlassBackgroundPressedLight = Color(0x99FFFFFF)

val ButtonGradientPrimary: List<Color>
    @Composable get() = LocalColorScheme.current.buttonGradientPrimary
val ButtonGradientSecondary: List<Color>
    @Composable get() = LocalColorScheme.current.buttonGradientSecondary
val ButtonGradientAccent: List<Color>
    @Composable get() = LocalColorScheme.current.buttonGradientAccent
val ButtonGradientSuccess: List<Color>
    @Composable get() = LocalColorScheme.current.buttonGradientSuccess
val ButtonGradientWarning: List<Color>
    @Composable get() = LocalColorScheme.current.buttonGradientWarning

val ButtonBorderDark: Color
    @Composable get() = LocalColorScheme.current.buttonBorderDark
val ButtonBorderLight: Color
    @Composable get() = LocalColorScheme.current.buttonBorderLight
val ButtonBorderActiveDark: Color
    @Composable get() = LocalColorScheme.current.buttonBorderActiveDark
val ButtonBorderActiveLight: Color
    @Composable get() = LocalColorScheme.current.buttonBorderActiveLight

val ButtonShadowDark = Color(0x25000000)
val ButtonShadowGlowDark: Color
    @Composable get() = LocalColorScheme.current.buttonShadowGlowDark
val ButtonShadowLight = Color(0x15000000)
val ButtonShadowGlowLight: Color
    @Composable get() = LocalColorScheme.current.buttonShadowGlowLight

val IconButtonBgDark = Color(0x18181818)
val IconButtonBgHoverDark = Color(0x28282828)
val IconButtonBgLight = Color(0x15FFFFFF)
val IconButtonBgHoverLight = Color(0x25FFFFFF)

val CardGlassBackgroundDark: Color
    @Composable get() = LocalColorScheme.current.cardGlassBackgroundDark
val CardGlassBackgroundLight: Color
    @Composable get() = LocalColorScheme.current.cardGlassBackgroundLight

val CardGlassBorderDark: Color
    @Composable get() = LocalColorScheme.current.cardGlassBorderDark
val CardGlassBorderLight: Color
    @Composable get() = LocalColorScheme.current.cardGlassBorderLight

val CardGlassHighlightDark: Color
    @Composable get() = LocalColorScheme.current.cardGlassHighlightDark
val CardGlassHighlightLight: Color
    @Composable get() = LocalColorScheme.current.cardGlassHighlightLight

/** 6个主题色，用于快捷入口等场景 */
val ThemeColors: List<Color>
    @Composable get() = LocalColorScheme.current.themeColors
