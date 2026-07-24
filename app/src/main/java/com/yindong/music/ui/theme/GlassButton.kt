package com.yindong.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 高级感配色：使用主题对齐的中性灰，避免极端纯黑纯白
private val NeuLightBg = Color(0xFFF0F1F3)
private val NeuDarkBg = Color(0xFF1C1C1E)

// 弱化阴影：更柔和的明暗对比
private val NeuLightShadowDark = Color(0xFFD1D1D6)
private val NeuDarkShadowDark = Color(0xFF000000).copy(alpha = 0.3f)

private val NeuLightShadowLight = Color(0xFFFFFFFF)
private val NeuDarkShadowLight = Color(0xFF3A3A3C)

// 文字层级：非纯黑纯白
private val NeuLightText = Color(0xFF1C1C1E)
private val NeuDarkText = Color(0xFFF2F2F7)

private val NeuLightTextPressed = Color(0xFF8E8E93)
private val NeuDarkTextPressed = Color(0xFFAEAEB2)

enum class GlassButtonStyle {
    NEUMORPHIC,
    OUTLINED,
    FILLED,
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.NEUMORPHIC,
    contentColor: Color = if (isDarkTheme()) NeuDarkText else NeuLightText,
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    paddingValues: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val isDark = isDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isDark) NeuDarkBg else NeuLightBg
    val shadowDark = if (isDark) NeuDarkShadowDark else NeuLightShadowDark
    val shadowLight = if (isDark) NeuDarkShadowLight else NeuLightShadowLight

    val resolvedContentColor by animateColorAsState(
        targetValue = when {
            !enabled -> contentColor.copy(alpha = 0.4f)
            isPressed -> if (isDark) NeuDarkTextPressed else NeuLightTextPressed
            else -> contentColor
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "contentColor"
    )

    // 按压缩放反馈：微小缩放替代生硬变色
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "scale"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                when (style) {
                    GlassButtonStyle.NEUMORPHIC -> Modifier
                        .background(bgColor)
                        .border(borderWidth, bgColor, shape)
                        .drawBehind {
                            val paint = Paint()
                            val frameworkPaint = paint.asFrameworkPaint()
                            if (isPressed) {
                                // 按下态：弱化阴影
                                frameworkPaint.color = shadowDark.toArgb()
                                frameworkPaint.setShadowLayer(
                                    6f, -2f, -2f, shadowDark.toArgb()
                                )
                                drawRect(color = Color.Transparent)
                                frameworkPaint.color = shadowLight.toArgb()
                                frameworkPaint.setShadowLayer(
                                    6f, 2f, 2f, shadowLight.toArgb()
                                )
                                drawRect(color = Color.Transparent)
                            } else {
                                // 常态：柔化双层软阴影
                                frameworkPaint.color = shadowLight.toArgb()
                                frameworkPaint.setShadowLayer(
                                    10f, -3f, -3f, shadowLight.toArgb()
                                )
                                drawRect(color = Color.Transparent)
                                frameworkPaint.color = shadowDark.toArgb()
                                frameworkPaint.setShadowLayer(
                                    10f, 3f, 3f, shadowDark.toArgb()
                                )
                                drawRect(color = Color.Transparent)
                            }
                        }

                    GlassButtonStyle.OUTLINED -> Modifier
                        .background(bgColor.copy(alpha = 0.3f))
                        .border(borderWidth, if (isDark) Color(0x14FFFFFF) else Color(0x0AC7C7CC), shape)

                    GlassButtonStyle.FILLED -> Modifier
                        .background(bgColor)
                        .border(borderWidth, bgColor, shape)
                        .drawBehind {
                            // 极淡内阴影增加细腻度
                            drawRect(
                                color = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f),
                            )
                        }
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                enabled = enabled,
                role = Role.Button
            )
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.NEUMORPHIC,
    contentColor: Color = if (isDarkTheme()) NeuDarkText else NeuLightText,
    cornerRadius: Dp = 12.dp,
    fontSize: Int = 14,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = style,
        contentColor = contentColor,
        cornerRadius = cornerRadius,
        paddingValues = 14.dp,
    ) {
        Text(
            text = text,
            color = if (isDarkTheme()) NeuDarkText else NeuLightText,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
    tint: Color = if (isDarkTheme()) NeuDarkText else NeuLightText,
    containerSize: Dp = 44.dp,
    showBackground: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.NEUMORPHIC,
) {
    val isDark = isDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isDark) NeuDarkBg else NeuLightBg
    val shadowDark = if (isDark) NeuDarkShadowDark else NeuLightShadowDark
    val shadowLight = if (isDark) NeuDarkShadowLight else NeuLightShadowLight

    // 按压缩放反馈
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(containerSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .then(
                if (style == GlassButtonStyle.NEUMORPHIC && showBackground) {
                    Modifier
                        .background(bgColor)
                        .drawBehind {
                            val paint = Paint()
                            val frameworkPaint = paint.asFrameworkPaint()
                            if (isPressed) {
                                // 按下态：弱化阴影
                                frameworkPaint.color = shadowDark.toArgb()
                                frameworkPaint.setShadowLayer(
                                    4f, -1f, -1f, shadowDark.toArgb()
                                )
                                drawCircle(color = Color.Transparent)
                                frameworkPaint.color = shadowLight.toArgb()
                                frameworkPaint.setShadowLayer(
                                    4f, 1f, 1f, shadowLight.toArgb()
                                )
                                drawCircle(color = Color.Transparent)
                            } else {
                                // 常态：柔化软阴影
                                frameworkPaint.color = shadowLight.toArgb()
                                frameworkPaint.setShadowLayer(
                                    6f, -2f, -2f, shadowLight.toArgb()
                                )
                                drawCircle(color = Color.Transparent)
                                frameworkPaint.color = shadowDark.toArgb()
                                frameworkPaint.setShadowLayer(
                                    6f, 2f, 2f, shadowDark.toArgb()
                                )
                                drawCircle(color = Color.Transparent)
                            }
                        }
                } else {
                    Modifier.background(if (showBackground) bgColor.copy(alpha = 0.3f) else Color.Transparent)
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                enabled = enabled,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun GlassIconToggleButton(
    onClick: () -> Unit,
    icon: ImageVector,
    activeIcon: ImageVector? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeTint: Color = if (isDarkTheme()) NeuDarkText else NeuLightText,
    inactiveTint: Color = if (isDarkTheme()) IconInactive else Color(0xFF888888),
    iconSize: Dp = 24.dp,
    containerSize: Dp = 44.dp,
) {
    val isDark = isDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isDark) NeuDarkBg else NeuLightBg
    val shadowDark = if (isDark) NeuDarkShadowDark else NeuLightShadowDark
    val shadowLight = if (isDark) NeuDarkShadowLight else NeuLightShadowLight

    val currentTint by animateColorAsState(
        targetValue = when {
            isActive -> activeTint
            isPressed -> if (isDark) NeuDarkTextPressed else NeuLightTextPressed
            else -> inactiveTint
        },
        animationSpec = tween(durationMillis = 300),
        label = "currentTint"
    )

    val displayIcon = if (isActive && activeIcon != null) activeIcon else icon

    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(bgColor)
            .drawBehind {
                val paint = Paint()
                val frameworkPaint = paint.asFrameworkPaint()
                if (isActive || isPressed) {
                    // 激活/按下态：弱化阴影
                    frameworkPaint.color = shadowDark.toArgb()
                    frameworkPaint.setShadowLayer(
                        4f, -1f, -1f, shadowDark.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                    frameworkPaint.color = shadowLight.toArgb()
                    frameworkPaint.setShadowLayer(
                        4f, 1f, 1f, shadowLight.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                } else {
                    // 常态：柔化软阴影
                    frameworkPaint.color = shadowLight.toArgb()
                    frameworkPaint.setShadowLayer(
                        6f, -2f, -2f, shadowLight.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                    frameworkPaint.color = shadowDark.toArgb()
                    frameworkPaint.setShadowLayer(
                        6f, 2f, 2f, shadowDark.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = displayIcon,
            contentDescription = contentDescription,
            tint = currentTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun GlassPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val isDark = isDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isDark) NeuDarkBg else NeuLightBg
    val shadowDark = if (isDark) NeuDarkShadowDark else NeuLightShadowDark
    val shadowLight = if (isDark) NeuDarkShadowLight else NeuLightShadowLight

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .drawBehind {
                val paint = Paint()
                val frameworkPaint = paint.asFrameworkPaint()
                if (isPressed) {
                    // 按下态：弱化阴影
                    frameworkPaint.color = shadowDark.toArgb()
                    frameworkPaint.setShadowLayer(
                        6f, -2f, -2f, shadowDark.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                    frameworkPaint.color = shadowLight.toArgb()
                    frameworkPaint.setShadowLayer(
                        6f, 2f, 2f, shadowLight.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                } else {
                    // 常态：柔化软阴影
                    frameworkPaint.color = shadowLight.toArgb()
                    frameworkPaint.setShadowLayer(
                        10f, -3f, -3f, shadowLight.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                    frameworkPaint.color = shadowDark.toArgb()
                    frameworkPaint.setShadowLayer(
                        10f, 3f, 3f, shadowDark.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = if (isDark) NeuDarkText else NeuLightText,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

@Composable
fun GlassMiniPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val isDark = isDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isDark) NeuDarkBg else NeuLightBg
    val shadowDark = if (isDark) NeuDarkShadowDark else NeuLightShadowDark
    val shadowLight = if (isDark) NeuDarkShadowLight else NeuLightShadowLight

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .drawBehind {
                val paint = Paint()
                val frameworkPaint = paint.asFrameworkPaint()
                if (isPressed) {
                    // 按下态：弱化阴影
                    frameworkPaint.color = shadowDark.toArgb()
                    frameworkPaint.setShadowLayer(
                        4f, -1f, -1f, shadowDark.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                    frameworkPaint.color = shadowLight.toArgb()
                    frameworkPaint.setShadowLayer(
                        4f, 1f, 1f, shadowLight.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                } else {
                    // 常态：柔化软阴影
                    frameworkPaint.color = shadowLight.toArgb()
                    frameworkPaint.setShadowLayer(
                        6f, -2f, -2f, shadowLight.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                    frameworkPaint.color = shadowDark.toArgb()
                    frameworkPaint.setShadowLayer(
                        6f, 2f, 2f, shadowDark.toArgb()
                    )
                    drawCircle(color = Color.Transparent)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = if (isDark) NeuDarkText else NeuLightText,
            modifier = Modifier.size(size * 0.75f)
        )
    }
}

@Composable
fun GlassChipButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = if (isDarkTheme()) NeuDarkText else NeuLightText,
    unselectedColor: Color = if (isDarkTheme()) GlassDarkSurfaceVariant else GlassLightSurfaceVariant,
) {
    val isDark = isDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isDark) NeuDarkBg else NeuLightBg
    val shadowDark = if (isDark) NeuDarkShadowDark else NeuLightShadowDark
    val shadowLight = if (isDark) NeuDarkShadowLight else NeuLightShadowLight

    val backgroundColor by animateColorAsState(
        targetValue = when {
            selected -> selectedColor.copy(alpha = 0.75f)
            isPressed -> unselectedColor.copy(alpha = 0.8f)
            else -> unselectedColor.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "backgroundColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) (if (isDark) NeuDarkBg else NeuLightBg) else (if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)),
        animationSpec = tween(durationMillis = 200),
        label = "textColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) selectedColor.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "borderColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected || isPressed) {
                    Modifier
                        .background(bgColor)
                        .drawBehind {
                            val paint = Paint()
                            val frameworkPaint = paint.asFrameworkPaint()
                            // 选中态：弱化阴影
                            frameworkPaint.color = shadowDark.toArgb()
                            frameworkPaint.setShadowLayer(
                                3f, -1f, -1f, shadowDark.toArgb()
                            )
                            drawRect(color = Color.Transparent)
                            frameworkPaint.color = shadowLight.toArgb()
                            frameworkPaint.setShadowLayer(
                                3f, 1f, 1f, shadowLight.toArgb()
                            )
                            drawRect(color = Color.Transparent)
                        }
                } else {
                    Modifier
                        .background(backgroundColor)
                        .drawBehind {
                            val paint = Paint()
                            val frameworkPaint = paint.asFrameworkPaint()
                            // 未选中态：柔化软阴影
                            frameworkPaint.color = shadowLight.toArgb()
                            frameworkPaint.setShadowLayer(
                                4f, -1f, -1f, shadowLight.toArgb()
                            )
                            drawRect(color = Color.Transparent)
                            frameworkPaint.color = shadowDark.toArgb()
                            frameworkPaint.setShadowLayer(
                                4f, 1f, 1f, shadowDark.toArgb()
                            )
                            drawRect(color = Color.Transparent)
                        }
                }
            )
            .border(0.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Button
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.3.sp
        )
    }
}
