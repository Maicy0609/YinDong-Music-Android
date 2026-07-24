package com.yindong.music.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yindong.music.ui.theme.isDarkTheme

private val isRenderEffectSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// region Color conversion

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(alpha, red, green, blue)
}

// endregion

// region LiquidGlassEffect Modifier

/**
 * 高级感液态玻璃效果：半透明背景 + 柔化双层软阴影 + 极细边框
 * 对标网易云/微信的毛玻璃导航栏与卡片质感
 * - 日间：#F8F9FA底层 + 半透明白卡片 + 极淡灰边框
 * - 夜间：#141414底层 + 半透明深灰卡片 + 极淡亮边框
 */
@Composable
fun Modifier.liquidGlassEffect(
    shape: Shape,
): Modifier {
    val isDark = isDarkTheme()

    // 半透明背景：保留底层内容的朦胧通透感
    val containerColor = if (isDark) Color(0xF21C1C1E) else Color(0xF2FFFFFF)
    val borderColor = if (isDark) Color(0x14FFFFFF) else Color(0x0AC7C7CC)

    // 双层软阴影：近景淡 + 远景虚
    val nearShadowColor = if (isDark) Color(0x1A000000) else Color(0x0A000000)
    val farShadowColor = if (isDark) Color(0x28000000) else Color(0x10000002)

    val density = LocalDensity.current
    val nearShadowRadiusPx = with(density) { 6.dp.toPx() }
    val farShadowRadiusPx = with(density) { 16.dp.toPx() }
    val shadowOffsetY = with(density) { 2.dp.toPx() }

    return this
        .clip(shape)
        .drawBehind {
            // 1. 远景虚阴影（大范围漫射，营造轻盈悬浮感）
            drawOuterShadow(shape, farShadowColor, farShadowRadiusPx, shadowOffsetY)
            // 2. 近景淡阴影（紧凑投影，增加层次）
            drawOuterShadow(shape, nearShadowColor, nearShadowRadiusPx, shadowOffsetY)
            // 3. 半透明背景
            drawRect(containerColor)
            // 4. 顶部细微高光（仅亮色模式，极淡）
            if (!isDark) {
                val topHighlight = Color.White.copy(alpha = 0.35f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to topHighlight,
                        0.12f to topHighlight.copy(alpha = 0f),
                        1f to Color.Transparent
                    ),
                )
            }
            // 5. 极细边框（1px）
            drawBorderLine(shape, borderColor)
        }
}

private fun DrawScope.drawOuterShadow(
    shape: Shape,
    shadowColor: Color,
    shadowRadiusPx: Float,
    offsetY: Float = 0f,
) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val frameworkPaint = android.graphics.Paint().apply {
        color = shadowColor.toArgb()
        maskFilter = BlurMaskFilter(shadowRadiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(0f, offsetY)
        drawOutlineOnCanvas(outline, canvas, frameworkPaint)
        canvas.restore()
    }
}

private fun DrawScope.drawBorderLine(
    shape: Shape,
    borderColor: Color,
) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val frameworkPaint = android.graphics.Paint().apply {
        color = borderColor.toArgb()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1f
    }

    drawIntoCanvas { canvas ->
        drawOutlineOnCanvas(outline, canvas, frameworkPaint)
    }
}

private fun drawOutlineOnCanvas(
    outline: Outline,
    canvas: androidx.compose.ui.graphics.Canvas,
    frameworkPaint: android.graphics.Paint
) {
    val composePaint = androidx.compose.ui.graphics.Paint()
    composePaint.asFrameworkPaint().set(frameworkPaint)
    when (outline) {
        is Outline.Rectangle -> canvas.drawRect(outline.rect, composePaint)
        is Outline.Rounded -> canvas.drawRoundRect(
            outline.roundRect.left, outline.roundRect.top,
            outline.roundRect.right, outline.roundRect.bottom,
            outline.roundRect.topLeftCornerRadius.x,
            outline.roundRect.topLeftCornerRadius.y,
            composePaint
        )
        is Outline.Generic -> canvas.drawPath(outline.path, composePaint)
    }
}

private fun clipOutline(canvas: androidx.compose.ui.graphics.Canvas, outline: Outline) {
    when (outline) {
        is Outline.Rectangle -> canvas.clipRect(outline.rect)
        is Outline.Rounded -> {
            val path = Path()
            path.addRoundRect(outline.roundRect)
            canvas.clipPath(path)
        }
        is Outline.Generic -> canvas.clipPath(outline.path)
    }
}

// endregion

// region Fallback for pre-Android S

@Composable
private fun Modifier.fallbackGlassEffect(
    shape: Shape,
    isDark: Boolean,
): Modifier {
    val containerColor = if (isDark) Color(0xF21C1C1E) else Color(0xF2FFFFFF)
    val borderColor = if (isDark) Color(0x14FFFFFF) else Color(0x0AC7C7CC)
    val nearShadowColor = if (isDark) Color(0x1A000000) else Color(0x0A000000)
    val farShadowColor = if (isDark) Color(0x28000000) else Color(0x10000002)

    return this
        .clip(shape)
        .drawBehind {
            drawOuterShadow(shape, farShadowColor, 16f, 2f)
            drawOuterShadow(shape, nearShadowColor, 6f, 2f)
            drawRect(containerColor)
            if (!isDark) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.35f),
                        0.12f to Color.Transparent,
                        1f to Color.Transparent
                    ),
                )
            }
            drawBorderLine(shape, borderColor)
        }
}

// endregion
