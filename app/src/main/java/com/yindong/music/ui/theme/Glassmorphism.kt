package com.yindong.music.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glassmorphism(
    backgroundColor: Color = if (isDarkTheme()) GlassDarkSurface else Color(0xF2FFFFFF),
    borderColor: Color = if (isDarkTheme()) GlassBorder else Color(0x0AC7C7CC),
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))

@Composable
fun Modifier.glassmorphismBlur(
    backgroundColor: Color = if (isDarkTheme()) GlassDarkSurface else Color(0xF2FFFFFF),
    borderColor: Color = if (isDarkTheme()) GlassBorder else Color(0x0AC7C7CC),
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    blurRadius: Dp = 20.dp,
): Modifier {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    return if (canBlur) {
        this
            .blur(blurRadius, BlurredEdgeTreatment(RoundedCornerShape(cornerRadius)))
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
    } else {
        this.glassmorphism(backgroundColor, borderColor, cornerRadius, borderWidth)
    }
}

@Composable
fun Modifier.neumorphismElevated(
    backgroundColor: Color = if (isDarkTheme()) NeumorphDarkBackground else GlassLightSurfaceElevated,
    shadowLight: Color = if (isDarkTheme()) NeumorphShadowLight else Color(0xFFFFFFFF),
    shadowDark: Color = if (isDarkTheme()) NeumorphShadowDark else Color(0x0A000000),
    cornerRadius: Dp = 12.dp,
    elevation: Dp = 4.dp,
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .drawBehind {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()

        frameworkPaint.color = shadowLight.toArgb()
        frameworkPaint.setShadowLayer(
            elevation.toPx(),
            -elevation.toPx() / 4,
            -elevation.toPx() / 4,
            shadowLight.toArgb()
        )
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(0f, 0f),
            size = Size(size.width / 2, size.height / 2)
        )

        frameworkPaint.color = shadowDark.toArgb()
        frameworkPaint.setShadowLayer(
            elevation.toPx(),
            elevation.toPx() / 4,
            elevation.toPx() / 4,
            shadowDark.toArgb()
        )
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(size.width / 2, size.height / 2),
            size = Size(size.width / 2, size.height / 2)
        )
    }

@Composable
fun Modifier.neumorphismPressed(
    backgroundColor: Color = if (isDarkTheme()) NeumorphDarkBackground else GlassLightSurfaceElevated,
    shadowLight: Color = if (isDarkTheme()) NeumorphShadowLight else Color(0xFFFFFFFF),
    shadowDark: Color = if (isDarkTheme()) NeumorphShadowDark else Color(0x0A000000),
    cornerRadius: Dp = 12.dp,
    elevation: Dp = 2.dp,
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .drawBehind {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()

        frameworkPaint.color = shadowDark.toArgb()
        frameworkPaint.setShadowLayer(
            elevation.toPx(),
            -elevation.toPx() / 4,
            -elevation.toPx() / 4,
            shadowDark.toArgb()
        )

        frameworkPaint.color = shadowLight.toArgb()
        frameworkPaint.setShadowLayer(
            elevation.toPx(),
            elevation.toPx() / 4,
            elevation.toPx() / 4,
            shadowLight.toArgb()
        )
    }

@Composable
fun Modifier.glassmorphismGradient(
    gradientColors: List<Color> = if (isDarkTheme()) listOf(
        Color(0xFF1C1C1E).copy(alpha = 0.15f),
        Color(0xFF141414).copy(alpha = 0.1f)
    ) else listOf(
        Color(0xFFFFFFFF).copy(alpha = 0.15f),
        Color(0xFFF0F1F3).copy(alpha = 0.1f)
    ),
    borderColor: Color = if (isDarkTheme()) GlassBorder else Color(0x0AC7C7CC),
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 0.5.dp,
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Brush.linearGradient(gradientColors))
    .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = if (isDarkTheme()) GlassBorder else Color(0x0AC7C7CC),
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .glassmorphism(backgroundColor, borderColor, cornerRadius, borderWidth),
        content = content
    )
}

@Composable
fun NeumorphButton(
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    backgroundColor: Color = if (isDarkTheme()) NeumorphDarkBackground else NeumorphLightBackground,
    cornerRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = if (pressed) {
            modifier.neumorphismPressed(backgroundColor = backgroundColor, cornerRadius = cornerRadius)
        } else {
            modifier.neumorphismElevated(backgroundColor = backgroundColor, cornerRadius = cornerRadius)
        },
        content = content
    )
}

@Composable
fun GlassBottomNav(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isDarkTheme()
    Box(
        modifier = modifier
            .glassmorphism(
                backgroundColor = if (isDark) GlassDarkSurface.copy(alpha = 0.85f) else Color(0xF2FFFFFF),
                borderColor = if (isDark) GlassBorder else Color(0x0AC7C7CC),
                cornerRadius = 0.dp,
                borderWidth = 0.dp
            ),
        content = content
    )
}

@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isDarkTheme()
    Box(
        modifier = modifier
            .glassmorphism(
                backgroundColor = if (isDark) GlassDarkSurface.copy(alpha = 0.85f) else Color(0xF2FFFFFF),
                borderColor = if (isDark) GlassBorder else Color(0x0AC7C7CC),
                cornerRadius = 0.dp,
                borderWidth = 0.dp
            ),
        content = content
    )
}

@Composable
fun BlurBackground(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    backgroundColor: Color = if (isDarkTheme()) GlassDarkBackground else Color(0xF2FFFFFF),
    content: @Composable BoxScope.() -> Unit,
) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = if (canBlur) {
            modifier
                .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                .background(backgroundColor)
        } else {
            modifier.background(backgroundColor)
        },
        content = content
    )
}

fun Modifier.softShadow(
    color: Color = Color(0x0A000000),
    blurRadius: Dp = 10.dp,
    offsetY: Dp = 2.dp,
): Modifier = this.drawBehind {
    val paint = Paint()
    val frameworkPaint = paint.asFrameworkPaint()
    frameworkPaint.color = color.toArgb()
    frameworkPaint.setShadowLayer(
        blurRadius.toPx(),
        0f,
        offsetY.toPx(),
        color.toArgb()
    )
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.drawRect(
            0f,
            0f,
            size.width,
            size.height,
            frameworkPaint
        )
        canvas.nativeCanvas.restore()
    }
}

fun Modifier.innerGlow(
    color: Color = Color.White.copy(alpha = 0.1f),
    blurRadius: Dp = 16.dp,
): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = size.minDimension / 2
        ),
        size = size
    )
}

@Composable
fun glassColors(): GlassColors {
    val isDark = isDarkTheme()
    return if (isDark) {
        GlassColors(
            background = GlassDarkBackground,
            surface = GlassDarkSurface,
            surfaceVariant = GlassDarkSurfaceVariant,
            surfaceElevated = GlassDarkSurfaceElevated,
            border = GlassBorder,
            borderStrong = GlassBorderStrong,
            shadowLight = NeumorphShadowLight,
            shadowDark = NeumorphShadowDark,
        )
    } else {
        GlassColors(
            background = GlassLightBackground,
            surface = GlassLightSurface,
            surfaceVariant = GlassLightSurfaceVariant,
            surfaceElevated = GlassLightSurfaceElevated,
            border = Color(0x0AC7C7CC),
            borderStrong = Color(0x28C7C7CC),
            shadowLight = Color(0xFFFFFFFF),
            shadowDark = Color(0x0A000000),
        )
    }
}

data class GlassColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderStrong: Color,
    val shadowLight: Color,
    val shadowDark: Color,
)
