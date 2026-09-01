package com.viberemote.mobile

import com.viberemote.mobile.domain.AgentLifecycle
import com.viberemote.mobile.domain.TaskSnapshot
import kotlin.math.abs

// ==================== AgentPager ArcadeSounds.kt 100% 移植 ====================

enum class ArcadeSoundCue {
    TASK_ACKNOWLEDGE,
    INPUT_REQUIRED,
    TASK_COMPLETE,
    TASK_INTERRUPTED,
}

/**
 * 只根据任务快照差分选择提示音，避免 Compose 重组或普通内容更新重复播放。
 */
class TaskSoundTracker {
    private var initialized = false
    private var previousLifecycles = emptyMap<String, AgentLifecycle>()

    fun nextCue(
        tasks: List<TaskSnapshot>,
        soundEnabled: Boolean = true,
    ): ArcadeSoundCue? {
        val currentLifecycles = tasks.associate { it.id to it.lifecycle }
        if (!initialized) {
            initialized = true
            previousLifecycles = currentLifecycles
            return null
        }

        val candidate = if (soundEnabled) {
            tasks
                .asSequence()
                .filterNot { it.isMuted }
                .filter { previousLifecycles[it.id] != it.lifecycle }
                .mapNotNull { task ->
                    val cue = task.lifecycle.soundCue()
                    cue?.let {
                        SoundCandidate(it, it.priority, task.updatedAt)
                    }
                }
                .maxWithOrNull(
                    compareBy<SoundCandidate> { it.priority }
                        .thenBy { it.updatedAt },
                )
        } else {
            null
        }

        previousLifecycles = currentLifecycles
        return candidate?.cue
    }

    private data class SoundCandidate(
        val cue: ArcadeSoundCue,
        val priority: Int,
        val updatedAt: Long,
    )
}

/**
 * 根据恢复出的 Vibe Island 内置 SoundSynthesizer 第二版参数生成单声道 PCM。
 *
 * 原版声音并非资源文件，而是用方波、失谐声部、三角波与硬削波实时合成。
 */
object ArcadeSoundSynthesizer {
    const val DEFAULT_SAMPLE_RATE = 48_000

    fun render(
        cue: ArcadeSoundCue,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
    ): ShortArray {
        require(sampleRate >= 8_000) { "采样率过低" }
        val samples = when (cue) {
            ArcadeSoundCue.TASK_ACKNOWLEDGE -> acknowledge(sampleRate)
            ArcadeSoundCue.INPUT_REQUIRED -> inputRequired(sampleRate)
            ArcadeSoundCue.TASK_COMPLETE -> taskComplete(sampleRate)
            ArcadeSoundCue.TASK_INTERRUPTED -> taskInterrupted(sampleRate)
        }
        return ShortArray(samples.size) { index ->
            val clipped = (samples[index] * 1.5f).coerceIn(-1f, 1f)
            (clipped * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun acknowledge(sampleRate: Int): FloatArray {
        val output = FloatArray(sampleCount(0.22, sampleRate))
        addPulse(
            output = output,
            sampleRate = sampleRate,
            frequency = 987.76,
            startSeconds = 0.0,
            durationSeconds = 0.055,
            amplitude = 0.2,
            attackSeconds = 0.004,
            releaseSeconds = 0.008,
            dutyCycle = 0.5,
        )
        addPulse(
            output = output,
            sampleRate = sampleRate,
            frequency = 1_318.51,
            startSeconds = 0.06,
            durationSeconds = 0.12,
            amplitude = 0.24,
            attackSeconds = 0.008,
            releaseSeconds = 0.018,
            dutyCycle = 0.5,
        )
        return output
    }

    private fun inputRequired(sampleRate: Int): FloatArray {
        val output = FloatArray(sampleCount(0.6, sampleRate))
        addPulse(output, sampleRate, 523.25, 0.0, 0.1, 0.2, 0.005, 0.02, 0.5)
        addPulse(output, sampleRate, 698.46, 0.1, 0.1, 0.22, 0.005, 0.02, 0.5)
        addPulse(output, sampleRate, 698.46, 0.28, 0.1, 0.2, 0.005, 0.02, 0.5)
        addPulse(output, sampleRate, 880.0, 0.38, 0.16, 0.21, 0.005, 0.03, 0.5)
        addTriangle(
            output = output,
            sampleRate = sampleRate,
            frequency = 174.61,
            startSeconds = 0.0,
            durationSeconds = 0.5,
            amplitude = 0.05,
            attackSeconds = 0.005,
            releaseSeconds = 0.1,
        )
        return output
    }

    private fun taskComplete(sampleRate: Int): FloatArray {
        val output = FloatArray(sampleCount(1.1, sampleRate))
        addPulse(output, sampleRate, 659.25, 0.0, 0.07, 0.2, 0.005, 0.02, 0.5)
        addPulse(output, sampleRate, 783.99, 0.08, 0.07, 0.2, 0.005, 0.02, 0.5)
        addPulse(output, sampleRate, 1_046.5, 0.16, 0.55, 0.22, 0.005, 0.25, 0.5)
        addTriangle(output, sampleRate, 523.25, 0.16, 0.55, 0.1, 0.005, 0.2)
        addTriangle(output, sampleRate, 1_046.5, 0.5, 0.5, 0.04, 0.005, 0.2)
        return output
    }

    private fun taskInterrupted(sampleRate: Int): FloatArray {
        val output = FloatArray(sampleCount(0.5, sampleRate))
        val frequencies = doubleArrayOf(261.63, 220.0, 174.61)
        frequencies.forEachIndexed { index, frequency ->
            val startSeconds = index * 0.13
            addPulse(
                output,
                sampleRate,
                frequency,
                startSeconds,
                0.12,
                0.2,
                0.005,
                0.015,
                0.5,
            )
            addPulse(
                output,
                sampleRate,
                frequency * 1.015,
                startSeconds,
                0.12,
                0.12,
                0.005,
                0.015,
                0.5,
            )
        }
        addTriangle(output, sampleRate, 87.305, 0.0, 0.4, 0.06, 0.005, 0.1)
        return output
    }

    private fun addPulse(
        output: FloatArray,
        sampleRate: Int,
        frequency: Double,
        startSeconds: Double,
        durationSeconds: Double,
        amplitude: Double,
        attackSeconds: Double,
        releaseSeconds: Double,
        dutyCycle: Double,
    ) {
        addWave(
            output,
            sampleRate,
            startSeconds,
            durationSeconds,
            amplitude,
            attackSeconds,
            releaseSeconds,
        ) { elapsed ->
            val phase = elapsed * frequency % 1.0
            if (phase < dutyCycle) 1.0 else -1.0
        }
    }

    private fun addTriangle(
        output: FloatArray,
        sampleRate: Int,
        frequency: Double,
        startSeconds: Double,
        durationSeconds: Double,
        amplitude: Double,
        attackSeconds: Double,
        releaseSeconds: Double,
    ) {
        addWave(
            output,
            sampleRate,
            startSeconds,
            durationSeconds,
            amplitude,
            attackSeconds,
            releaseSeconds,
        ) { elapsed ->
            val phase = elapsed * frequency % 1.0
            2.0 * abs(2.0 * phase - 1.0) - 1.0
        }
    }

    private fun addWave(
        output: FloatArray,
        sampleRate: Int,
        startSeconds: Double,
        durationSeconds: Double,
        amplitude: Double,
        attackSeconds: Double,
        releaseSeconds: Double,
        waveform: (elapsedSeconds: Double) -> Double,
    ) {
        val start = sampleCount(startSeconds, sampleRate)
        val duration = sampleCount(durationSeconds, sampleRate)
        repeat(duration) { offset ->
            val index = start + offset
            if (index >= output.size) return
            val elapsed = offset.toDouble() / sampleRate
            val attack = if (attackSeconds <= 0.0) {
                1.0
            } else {
                (elapsed / attackSeconds).coerceIn(0.0, 1.0)
            }
            val remaining = (duration - offset).toDouble() / sampleRate
            val release = if (releaseSeconds <= 0.0) {
                1.0
            } else {
                (remaining / releaseSeconds).coerceIn(0.0, 1.0)
            }
            output[index] += (waveform(elapsed) * amplitude * minOf(attack, release)).toFloat()
        }
    }

    private fun sampleCount(seconds: Double, sampleRate: Int): Int =
        (seconds * sampleRate).toInt()
}

private fun AgentLifecycle.soundCue(): ArcadeSoundCue? = when (this) {
    // 只在真正进入运行态时确认，避免 starting -> running 连响两次。
    AgentLifecycle.RUNNING -> ArcadeSoundCue.TASK_ACKNOWLEDGE
    AgentLifecycle.WAITING_APPROVAL,
    AgentLifecycle.WAITING_ANSWER,
    -> ArcadeSoundCue.INPUT_REQUIRED
    AgentLifecycle.SUCCEEDED -> ArcadeSoundCue.TASK_COMPLETE
    AgentLifecycle.INTERRUPTED -> ArcadeSoundCue.TASK_INTERRUPTED
    else -> null
}

private val ArcadeSoundCue.priority: Int
    get() = when (this) {
        ArcadeSoundCue.INPUT_REQUIRED -> 400
        ArcadeSoundCue.TASK_COMPLETE -> 300
        ArcadeSoundCue.TASK_INTERRUPTED -> 200
        ArcadeSoundCue.TASK_ACKNOWLEDGE -> 100
    }