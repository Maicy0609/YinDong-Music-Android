package com.yindong.music.ui.components

import android.content.Context
import coil.request.ImageRequest
import coil.size.Scale

/**
 * 模糊背景播放器的高斯模糊背景辅助类。
 *
 * 实现要点：
 * 1. 下采样：请求尺寸为屏幕宽高 1/8，降低模糊计算量。
 * 2. 子线程：Coil 内部在子线程执行图片解码/缩放。
 * 3. 缓存：通过 memoryCacheKey 将已下采样的封面加入 Coil 内存缓存，
 *    重复播放同一首歌时直接命中缓存，不重复计算。
 * 4. 硬件加速：实际高斯模糊由 Composable 层的 Modifier.blur(24.dp) 调用系统
 *    RenderEffect（API 31+）或 Skia 硬件加速路径完成，避免纯软件卷积。
 */
object BlurBgHelper {
    private const val DOWNSAMPLE_FACTOR = 8

    /**
     * 为全屏高斯模糊背景创建 Coil 图片请求。
     *
     * @param coverUrl 当前歌曲专辑封面 URL；如需替换为本地占位图，可在此处将 url 改为 R.drawable.xxx 或 File。
     */
    fun createBlurBgRequest(context: Context, coverUrl: String): ImageRequest {
        val metrics = context.resources.displayMetrics
        val targetWidth = (metrics.widthPixels / DOWNSAMPLE_FACTOR).coerceAtLeast(1)
        val targetHeight = (metrics.heightPixels / DOWNSAMPLE_FACTOR).coerceAtLeast(1)
        return ImageRequest.Builder(context)
            .data(coverUrl)
            .size(targetWidth, targetHeight)
            .scale(Scale.FILL)
            .memoryCacheKey("${coverUrl}_blur_bg_${targetWidth}x${targetHeight}")
            .crossfade(false)
            .build()
    }
}
