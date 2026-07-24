package com.yindong.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

fun buildMaterialColorScheme(isDark: Boolean, config: ColorSchemeConfig): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = config.primaryGreenLight,
            onPrimary = config.glassDarkBackground,
            primaryContainer = config.primaryGreenDark,
            onPrimaryContainer = config.glassTextPrimaryDark,

            secondary = config.accentGrayLight,
            onSecondary = config.glassDarkBackground,
            secondaryContainer = config.accentGrayDark,
            onSecondaryContainer = config.accentGrayLight,

            tertiary = config.accentCyanLight,
            onTertiary = config.glassDarkBackground,
            tertiaryContainer = config.accentCyanDark,
            onTertiaryContainer = config.accentCyanLight,

            background = config.darkBg,
            onBackground = config.glassTextPrimaryDark,

            surface = config.glassDarkSurface,
            onSurface = config.glassTextPrimaryDark,
            surfaceVariant = config.glassDarkSurfaceVariant,
            onSurfaceVariant = config.glassTextSecondaryDark,

            surfaceTint = config.primaryGreenLight.copy(alpha = 0.05f),
            surfaceContainerLowest = config.darkBg,
            surfaceContainerLow = config.darkBgLight,
            surfaceContainer = config.glassDarkSurface,
            surfaceContainerHigh = config.glassDarkSurfaceElevated,
            surfaceContainerHighest = config.primaryGreenLight.copy(alpha = 0.15f),

            outline = config.glassBorder,
            outlineVariant = config.dividerGlass,

            error = config.errorSoft,
            onError = config.glassDarkBackground,
            errorContainer = Color(0xFF2A1015),
            onErrorContainer = Color(0xFFFFCDD2),

            inverseSurface = config.glassLightSurface,
            inverseOnSurface = config.glassTextPrimaryLight,
            inversePrimary = config.primaryGreen,

            scrim = Color(0x80000000),
        )
    } else {
        lightColorScheme(
            primary = config.primaryGreen,
            onPrimary = Color.White,
            primaryContainer = config.primaryGreenDark,
            onPrimaryContainer = config.glassTextPrimaryLight,

            secondary = config.accentGray,
            onSecondary = Color.White,
            secondaryContainer = config.accentGrayLight,
            onSecondaryContainer = config.accentGrayDark,

            tertiary = config.accentCyan,
            onTertiary = Color.White,
            tertiaryContainer = config.accentCyanLight,
            onTertiaryContainer = config.accentCyanDark,

            background = config.glassLightBackground,
            onBackground = config.glassTextPrimaryLight,

            surface = config.glassLightSurface,
            onSurface = config.glassTextPrimaryLight,
            surfaceVariant = config.glassLightSurfaceVariant,
            onSurfaceVariant = config.glassTextSecondaryLight,

            surfaceTint = config.primaryGreen.copy(alpha = 0.03f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = config.glassLightBackground,
            surfaceContainer = config.glassLightSurface,
            surfaceContainerHigh = config.glassLightSurfaceElevated,
            surfaceContainerHighest = Color.White,

            outline = config.glassBorder,
            outlineVariant = config.dividerGlass,

            error = config.errorSoft,
            onError = Color.White,
            errorContainer = Color(0xFFFFEBEE),
            onErrorContainer = Color(0xFFB71C1C),

            inverseSurface = config.glassDarkSurface,
            inverseOnSurface = config.glassTextPrimaryDark,
            inversePrimary = config.primaryGreenLight,

            scrim = Color(0x60000000),
        )
    }
}
