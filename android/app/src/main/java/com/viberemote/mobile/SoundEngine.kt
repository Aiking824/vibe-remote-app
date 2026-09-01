package com.viberemote.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import java.util.concurrent.Executors

// ==================== AgentPager SoundEngine.kt 100% 移植 ====================

class SoundEngine(context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { }
        .build()

    val isEnabled: Boolean
        get() = preferences.getBoolean(SOUND_ENABLED_KEY, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(SOUND_ENABLED_KEY, enabled).apply()
    }

    fun play(cue: ArcadeSoundCue) {
        if (!isEnabled) return
        executor.execute {
            runCatching { playCue(cue) }
                .onFailure { Log.e(LOG_TAG, "提示音播放失败：$cue", it) }
        }
        if (cue == ArcadeSoundCue.INPUT_REQUIRED) {
            vibrator?.vibrate(VibrationEffect.createOneShot(70, 110))
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun playCue(cue: ArcadeSoundCue) {
        val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            .takeIf { it >= 8_000 }
            ?: ArcadeSoundSynthesizer.DEFAULT_SAMPLE_RATE
        val samples = ArcadeSoundSynthesizer.render(cue, sampleRate)
        val bufferSize = maxOf(
            samples.size * Short.SIZE_BYTES,
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bufferSize)
            .build()
        try {
            check(AudioTrackStatePolicy.canWrite(AudioTrack.MODE_STATIC, track.state)) {
                "AudioTrack 无法写入：state=${track.state}"
            }
            val focusResult = audioManager?.requestAudioFocus(focusRequest)
            val writtenSamples = track.write(samples, 0, samples.size)
            check(writtenSamples == samples.size) {
                "PCM 写入不完整：$writtenSamples/${samples.size}"
            }
            check(track.state == AudioTrack.STATE_INITIALIZED) {
                "PCM 写入后 AudioTrack 未就绪：state=${track.state}"
            }
            track.setVolume(1f)
            track.play()
            Thread.sleep(samples.size * 1_000L / sampleRate + 40L)
            Log.i(
                LOG_TAG,
                "提示音播放完成：cue=$cue focus=$focusResult " +
                    "frames=${track.playbackHeadPosition}/${samples.size}",
            )
        } finally {
            runCatching { track.stop() }
            track.release()
            audioManager?.abandonAudioFocusRequest(focusRequest)
        }
    }

    companion object {
        private const val LOG_TAG = "AgentPagerSound"
        private const val PREFERENCES_NAME = "agentgrid_sound"
        private const val SOUND_ENABLED_KEY = "enabled"

        private val audioAttributes = AudioAttributes.Builder()
            // 终端是常亮前台界面，走媒体通道，避免被手机的通知静音模式吞掉。
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

internal object AudioTrackStatePolicy {
    fun canWrite(transferMode: Int, state: Int): Boolean = when (transferMode) {
        AudioTrack.MODE_STATIC ->
            state == AudioTrack.STATE_NO_STATIC_DATA ||
                state == AudioTrack.STATE_INITIALIZED

        AudioTrack.MODE_STREAM -> state == AudioTrack.STATE_INITIALIZED
        else -> false
    }
}