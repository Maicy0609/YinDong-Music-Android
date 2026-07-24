package com.yindong.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yindong.music.ui.navigation.Screen
import com.yindong.music.ui.theme.isDarkTheme

/**
 * 纯白极简底部导航栏：白色卡片 + 轻量阴影 + 选中态强调色
 */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onPlayerClick: () -> Unit = {},
) {
    val items = listOf(
        Triple(Screen.Discover.route, "首页", Icons.Default.MusicNote),
        Triple(Screen.Playlist.route, "本地", Icons.Default.LibraryMusic),
        Triple(Screen.Mine.route, "设置", Icons.Default.Settings),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(
                shape = RectangleShape,
            )
            // 内容上移到系统导航栏之上（自动识别手势/三键模式与高度），
            // 背景已由 liquidGlassEffect 延伸到屏幕最底，避免与系统三键区域割裂
            .navigationBarsPadding()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { (route, label, icon) ->
            BottomNavItem(
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                icon = icon,
                label = label,
            )
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    val isDark = isDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    // 未激活图标/文字使用主题色阶，避免硬编码
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    val iconTint by animateColorAsState(
        targetValue = if (selected) accentColor else inactiveColor,
        animationSpec = tween(durationMillis = 250),
        label = "iconTint"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) accentColor else inactiveColor,
        animationSpec = tween(durationMillis = 250),
        label = "labelColor"
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = if (isDark) 0.15f else 0.08f) else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "pillColor"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RectangleShape)
            .background(pillColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = labelColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
