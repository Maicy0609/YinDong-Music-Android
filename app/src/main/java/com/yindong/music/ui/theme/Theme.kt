package com.yindong.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun CloudMusicTheme(
    isDark: Boolean = false,
    colorSchemeConfig: ColorSchemeConfig? = null,
    content: @Composable () -> Unit,
) {
    val schemeConfig = colorSchemeConfig ?: CaramelBrownColorScheme
    val appColors = if (isDark) schemeConfig.darkAppColors else schemeConfig.lightAppColors
    val materialColorScheme = buildMaterialColorScheme(isDark, schemeConfig)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val activity = view.context as? Activity ?: return@SideEffect
                val window = activity.window ?: return@SideEffect
                if (!activity.isFinishing && !activity.isDestroyed) {
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        window.isNavigationBarContrastEnforced = false
                    }

                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                }
            } catch (_: Exception) { }
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides isDark,
        LocalAppColors provides appColors,
        LocalColorScheme provides schemeConfig,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

@Composable
fun appColors(): AppColors {
    return LocalAppColors.current
}

@Composable
fun isDarkTheme(): Boolean {
    return LocalIsDarkTheme.current
}
