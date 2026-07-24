package com.yindong.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 内置拼音输入面板 —— 为没有中文输入法的车载设备提供基础中文输入能力。
 *
 * 工作原理：
 * 1. 用户通过 QWERTY 键盘输入拼音字母
 * 2. 面板根据拼音匹配候选汉字（单音节精确匹配 + 常用词组）
 * 3. 点击候选汉字将其追加到搜索框
 * 4. 支持退格、清空、确认（触发搜索）
 *
 * 词典为内置精简版，覆盖歌曲/歌手搜索常用字。
 */
@Composable
fun PinyinInputPanel(
    onCharacterSelected: (String) -> Unit,
    onBackspace: () -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var pinyinBuffer by remember { mutableStateOf("") }
    val candidates = remember(pinyinBuffer) { PinyinDict.match(pinyinBuffer) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant.copy(alpha = 0.95f))
            .padding(6.dp),
    ) {
        // ── 候选词栏 ──
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 已输入的拼音
            if (pinyinBuffer.isNotEmpty()) {
                Text(
                    pinyinBuffer,
                    fontSize = 14.sp,
                    color = cs.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            // 候选字横向滚动列表
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(candidates, key = { it }, contentType = { "char" }) { char ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(cs.surface, RoundedCornerShape(6.dp))
                            .clickable {
                                onCharacterSelected(char)
                                pinyinBuffer = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(char, fontSize = 18.sp, color = cs.onSurface)
                    }
                }
            }
            // 关闭面板
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "关闭", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── QWERTY 键盘 ──
        val rows = listOf(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm",
        )
        rows.forEachIndexed { index, rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (index == 2) {
                    // 第三行左侧加退格
                    KeyButton(
                        text = "退格",
                        icon = Icons.Default.Backspace,
                        onClick = {
                            if (pinyinBuffer.isNotEmpty()) {
                                pinyinBuffer = pinyinBuffer.dropLast(1)
                            } else {
                                onBackspace()
                            }
                        },
                        width = 56,
                    )
                }
                rowKeys.forEach { ch ->
                    KeyButton(
                        text = ch.toString(),
                        onClick = {
                            pinyinBuffer += ch
                        },
                    )
                }
                if (index == 2) {
                    // 第三行右侧加搜索
                    KeyButton(
                        text = "搜索",
                        icon = Icons.Default.KeyboardReturn,
                        onClick = {
                            onSearch()
                        },
                        width = 56,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    width: Int = 36,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(width.dp)
            .padding(horizontal = 2.dp)
            .background(cs.surface, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, text, tint = cs.onSurface, modifier = Modifier.size(18.dp))
        } else {
            Text(text, fontSize = 16.sp, color = cs.onSurface, fontWeight = FontWeight.Medium)
        }
    }
}

/** IconButton 简化版 */
@Composable
private fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        content()
    }
}
