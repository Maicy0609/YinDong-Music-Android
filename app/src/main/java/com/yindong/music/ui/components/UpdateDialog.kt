package com.yindong.music.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yindong.music.data.RemoteConfig
import com.yindong.music.ui.theme.isDarkTheme

private const val TAG = "UpdateDialog"

data class UpdateInfo(
    val currentVersion: String,
    val newVersion: String,
    val changelog: String,
    val fileSize: String = "",
    val isForceUpdate: Boolean = false,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Downloading : UpdateState()
    data class Progress(val percent: Int) : UpdateState()
    data object DownloadComplete : UpdateState()
    data object Installing : UpdateState()
}

@Composable
fun GlassUpdateDialog(
    info: UpdateInfo,
    onDownload: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val isDark = isDarkTheme()
    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = { if (!info.isForceUpdate) onDismiss() else {} },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF))
                .border(1.dp, if (isDark) Color(0x1AFFFFFF) else Color(0x0F000000), RoundedCornerShape(28.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "发现新版本",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground,
                    )

                    if (!info.isForceUpdate) {
                        Icon(
                            Icons.Default.Close, null,
                            tint = cs.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp).clickable(onClick = onCancel),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF0F1F3))
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "v${info.newVersion}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.primary,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "当前版本 v${info.currentVersion}",
                            fontSize = 12.sp,
                            color = cs.onBackground.copy(alpha = 0.5f),
                        )
                        if (info.fileSize.isNotEmpty()) {
                            Text(
                                text = "大小 ${info.fileSize}",
                                fontSize = 11.sp,
                                color = cs.onBackground.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                if (info.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "更新内容",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF0F1F3))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = info.changelog,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = cs.onBackground.copy(alpha = 0.85f),
                        )
                    }
                }

                if (info.isForceUpdate) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.error.copy(alpha = 0.12f))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                    ) {
                        Text(
                            text = "⚠ 此为强制更新版本，请更新后继续使用",
                            fontSize = 11.sp,
                            color = cs.error,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (!info.isForceUpdate) {
                        GlassUpdateButton(
                            text = "稍后再说",
                            isPrimary = false,
                            isDark = isDark,
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    GlassUpdateButton(
                        text = "立即更新",
                        isPrimary = true,
                        isDark = isDark,
                        onClick = onDownload,
                        modifier = Modifier.weight(if (info.isForceUpdate) 1f else 1f),
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RowScope.GlassUpdateButton(
    text: String,
    isPrimary: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val bgColor = if (isPrimary) cs.primary else Color.Transparent
    val textColor = if (isPrimary) Color.White else cs.onBackground.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (!isPrimary) {
                    Modifier.border(1.dp, if (isDark) Color(0x1AFFFFFF) else Color(0x0F000000), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .then(
                if (isPrimary) Modifier.background(bgColor) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}
