package com.viberemote.mobile.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * 手机麦克风 PCM16 采集（16kHz 单声道，与电脑端 audio_sink 对齐）。
 * 按住说话：分包回调给 WS 上行。
 */
class MicPcmRecorder(private val onChunk: (ByteArray) -> Unit) {

    @Volatile private var running = false
    private var thread: Thread? = null
    private val sampleRate = 16000

    val isRecording: Boolean
        get() = running

    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        } catch (_: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        running = true
        thread = Thread({
            val buf = ByteArray(minBuf)
            try {
                record.startRecording()
            } catch (_: Exception) {
                running = false
                record.release()
                return@Thread
            }
            while (running) {
                val n = try {
                    record.read(buf, 0, buf.size)
                } catch (_: Exception) {
                    -1
                }
                if (n > 0) {
                    try {
                        onChunk(buf.copyOf(n))
                    } catch (_: Exception) {
                    }
                }
            }
            try {
                record.stop()
            } catch (_: Exception) {
            }
            record.release()
        }, "mic-pcm")
        thread?.start()
        return true
    }

    fun stop() {
        running = false
        try {
            thread?.join(400)
        } catch (_: Exception) {
        }
        thread = null
    }
}