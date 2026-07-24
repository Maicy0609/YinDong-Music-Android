package com.yindong.music.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * VerifyLockScreen — 白屏拦截页（对应 JS verify.css 白屏遮罩）
 *
 * 校验失败时显示：
 *   - 纯白背景覆盖全部界面
 *   - 警告图标 + 报错文案（断网 / 服务器校验异常 / 设备拦截限制）
 *   - 唯一交互：关闭程序按钮
 *   - 不可返回（onBackPressed 阻断）
 */
@Composable
fun VerifyLockScreen(
    type: LockType,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            // 警告图标
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(20.dp))
            // 报错文案
            Text(
                text = type.message,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1D1D1F),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(28.dp))
            // 关闭程序按钮（唯一交互入口）
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "关闭程序",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 拦截类型 */
enum class LockType(val message: String) {
    OFFLINE("网络连接已断开\n请检查网络后重新启动软件"),
    SERVER("服务器校验异常\n软件无法启动，请联系管理员"),
    DEVICE("设备触发安全限制\n当前环境不被允许运行本软件"),
}
