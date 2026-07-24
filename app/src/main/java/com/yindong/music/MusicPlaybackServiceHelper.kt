package com.yindong.music

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap

object MusicPlaybackServiceHelper {
    private var service: MusicPlaybackService? = null

    fun attach(service: MusicPlaybackService) {
        this.service = service
    }

    fun detach() {
        this.service = null
    }

    fun updateCover(context: Context, bitmap: Bitmap) {
        service?.updateCover(bitmap) ?: run {
            try {
                val intent = Intent(context, MusicPlaybackService::class.java)
                intent.action = "ACTION_UPDATE_COVER"
                intent.putExtra("cover", bitmap)
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
