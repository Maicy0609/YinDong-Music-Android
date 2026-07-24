package com.yindong.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yindong.music.data.api.LinkParser
import com.yindong.music.ui.theme.isDarkTheme
import com.yindong.music.viewmodel.MusicViewModel

@Composable
fun ImportPlaylistScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var showFaq by remember { mutableStateOf(false) }
    // 选择的音源：null=自动识别，"wy"/"tx"/"kw"/"kg"=指定音源
    var selectedSource by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val importState = viewModel.importState
    val isLoading = importState is MusicViewModel.ImportState.Loading

    val isDark = isDarkTheme()
    val systemBackground = if (isDark) Color(0xFF141414) else Color(0xFFF8F9FA)
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val tertiaryBackground = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val labelTertiary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accentColor = Color(0xFF007AFF)
    val accent = accentColor
    val surfaceColor = secondaryBackground
    val cardColor = tertiaryBackground
    val subtleBorder = separator
    val accentSoftBg = accent.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(systemBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 160.dp)
    ) {
        // ── 标题栏 ──
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    viewModel.cancelImport()
                    onBack()
                },
                modifier = Modifier
                    .size(40.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(cardColor)
                    .border(1.dp, subtleBorder, CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = labelPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "导入外部歌单",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                    color = labelPrimary,
                )
                Text(
                    "支持从酷我音乐、酷狗音乐、网易云音乐、QQ音乐导入歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = labelSecondary,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 使用提示 ──
        InfoTip("打开对应平台App，找到想导入的歌单，点击分享并复制链接，回到本App粘贴即可一键导入。")
        Spacer(Modifier.height(8.dp))
        InfoTip("仅支持导入官方公开歌单，私密歌单或需登录才能查看的歌单暂无法识别，请先将歌单设为公开。")

        Spacer(Modifier.height(24.dp))

        // ── 音源选择器（选择哪个音源导入哪个音源） ──
        Text(
            "选择音源",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            color = labelPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceChip("自动识别", selectedSource == null, labelPrimary, labelSecondary, accent, cardColor, subtleBorder) {
                selectedSource = null
            }
            SourceChip("网易云", selectedSource == "wy", labelPrimary, labelSecondary, accent, cardColor, subtleBorder) {
                selectedSource = "wy"
            }
            SourceChip("QQ音乐", selectedSource == "tx", labelPrimary, labelSecondary, accent, cardColor, subtleBorder) {
                selectedSource = "tx"
            }
            SourceChip("酷我", selectedSource == "kw", labelPrimary, labelSecondary, accent, cardColor, subtleBorder) {
                selectedSource = "kw"
            }
            SourceChip("酷狗", selectedSource == "kg", labelPrimary, labelSecondary, accent, cardColor, subtleBorder) {
                selectedSource = "kg"
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 输入框 ──
        OutlinedTextField(
            value = inputText,
            onValueChange = {
                inputText = it
                parseError = null
                if (importState is MusicViewModel.ImportState.Error) viewModel.resetImportState()
            },
            placeholder = { Text("粘贴歌单链接、分享口令或歌单ID") },
            trailingIcon = {
                IconButton(onClick = {
                    val clip = clipboardManager.getText()?.text ?: ""
                    if (clip.isNotEmpty()) {
                        inputText = clip
                        parseError = null
                    }
                }) {
                    Icon(Icons.Default.ContentPaste, "粘贴", tint = accent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
        )

        // ── 错误提示 ──
        parseError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall)
        }
        if (importState is MusicViewModel.ImportState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                (importState as MusicViewModel.ImportState.Error).message,
                color = cs.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── 操作按钮 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    parseError = null
                    if (selectedSource != null) {
                        // 选择哪个音源导入哪个音源：直接用所选音源 + 原始输入
                        viewModel.importExternalPlaylist(selectedSource!!, inputText.trim())
                    } else {
                        // 自动识别模式：使用 LinkParser 解析
                        val parsed = LinkParser.parse(inputText)
                        if (parsed == null) {
                            parseError = "无法识别歌单链接，请选择对应音源后重试"
                            return@Button
                        }
                        viewModel.importExternalPlaylist(parsed.source, parsed.playlistId)
                    }
                },
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("导入中...", color = Color.White)
                } else {
                    Text("开始导入", color = Color.White)
                }
            }

            OutlinedButton(
                onClick = {
                    viewModel.cancelImport()
                    onBack()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = surfaceColor,
                ),
            ) {
                Text("取消导入", color = labelPrimary)
            }
        }

        // ── 导入成功结果 ──
        if (importState is MusicViewModel.ImportState.Success) {
            val success = importState as MusicViewModel.ImportState.Success
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentSoftBg)
                    .border(1.dp, accent, RoundedCornerShape(12.dp))
                    .clickable {
                        viewModel.resetImportState()
                        onNavigateToPlaylist(success.playlistId)
                    }
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        "导入成功",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
                        color = accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "歌单「${success.playlistName}」已导入 ${success.songCount} 首歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = labelSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击查看歌单 →",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── FAQ 常见问题 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardColor)
                .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
                .clickable { showFaq = !showFaq }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Info, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("常见问题", style = MaterialTheme.typography.titleSmall, color = labelPrimary)
            Spacer(Modifier.weight(1f))
            Icon(
                if (showFaq) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = labelSecondary,
            )
        }

        AnimatedVisibility(
            visible = showFaq,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(start = 28.dp, top = 12.dp)) {
                FaqItem(
                    "如何获取歌单链接？",
                    "打开对应平台的App或网页版，进入歌单详情页，点击「分享」按钮，选择「复制链接」，粘贴到输入框中即可开始导入。",
                )
                Spacer(Modifier.height(16.dp))
                FaqItem(
                    "为什么导入失败？",
                    "请检查链接是否完整、歌单是否为公开状态，以及当前网络是否正常。若链接来自浏览器，请复制完整地址栏内容后重试。",
                )
                Spacer(Modifier.height(16.dp))
                FaqItem(
                    "导入后歌曲不全怎么办？",
                    "部分歌曲可能因版权限制或已下架而无法匹配成功。你可在导入结果中查看未成功的曲目，尝试手动搜索添加。",
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoTip(text: String) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val secondaryBackground = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    val separator = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
    val accentColor = Color(0xFF007AFF)
    val accent = accentColor
    val surfaceColor = secondaryBackground
    val subtleBorder = separator
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceColor)
            .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Info,
            null,
            tint = accent,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = labelSecondary,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun FaqItem(title: String, content: String) {
    val cs = MaterialTheme.colorScheme
    val isDark = isDarkTheme()
    val labelPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val labelSecondary = if (isDark) Color(0xFFAEAEB2) else Color(0xFF636366)
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            color = labelPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            content,
            style = MaterialTheme.typography.bodySmall,
            color = labelSecondary,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    labelPrimary: Color,
    labelSecondary: Color,
    accent: Color,
    cardColor: Color,
    subtleBorder: Color,
    onClick: () -> Unit,
) {
    val bg = if (selected) accent else cardColor
    val fg = if (selected) Color.White else labelPrimary
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, if (selected) accent else subtleBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
