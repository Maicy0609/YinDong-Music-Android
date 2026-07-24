package com.yindong.music.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object AudioRecorder {

    private const val TAG = "AudioRecorder"
    private const val SAMPLE_RATE = 8000
    private const val DURATION_MS = 3000
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    data class RecordResult(
        val samples: FloatArray,
        val durationMs: Long,
        val rms: Float,
    )

    suspend fun record(): RecordResult = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("无法获取音频缓冲区大小")
        }

        val totalSamples = SAMPLE_RATE * DURATION_MS / 1000
        val audioData = ShortArray(totalSamples)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            throw IllegalStateException("AudioRecord 初始化失败")
        }

        audioRecord?.startRecording()
        isRecording = true

        val startTime = System.currentTimeMillis()
        var readCount = 0

        try {
            while (isRecording && readCount < totalSamples) {
                val remaining = totalSamples - readCount
                val toRead = minOf(remaining, bufferSize / 2)
                val read = audioRecord?.read(audioData, readCount, toRead) ?: 0
                if (read > 0) {
                    readCount += read
                } else {
                    break
                }

                if (System.currentTimeMillis() - startTime >= DURATION_MS) {
                    break
                }
            }
        } finally {
            stopRecording()
        }

        val actualDuration = System.currentTimeMillis() - startTime

        val floatSamples = FloatArray(readCount)
        var sumSq = 0.0
        for (i in 0 until readCount) {
            val normalized = audioData[i] / 32768.0f
            floatSamples[i] = normalized
            sumSq += normalized.toDouble() * normalized.toDouble()
        }
        val rms = kotlin.math.sqrt(sumSq / readCount).toFloat()

        Log.d(TAG, "录制完成: ${readCount}样本, ${actualDuration}ms, RMS=$rms")

        RecordResult(
            samples = floatSamples,
            durationMs = actualDuration,
            rms = rms
        )
    }

    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "停止录制异常", e)
        }
        audioRecord = null
    }

    fun isRecording(): Boolean = isRecording
}
