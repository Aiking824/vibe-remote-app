package com.viberemote.mobile

import com.viberemote.mobile.domain.AgentLifecycle

// ==================== AgentPager ScreenBrightnessPolicy.kt 100% 移植 ====================

internal object ScreenBrightnessPolicy {
    const val DEFAULT_ACTIVE_BRIGHTNESS = 0.65f
    const val MIN_ACTIVE_BRIGHTNESS = 0.05f
    const val MAX_ACTIVE_BRIGHTNESS = 1f
    const val IDLE_BRIGHTNESS = 0.15f

    private val activeLifecycles = setOf(
        AgentLifecycle.STARTING,
        AgentLifecycle.RUNNING,
        AgentLifecycle.WAITING_APPROVAL,
        AgentLifecycle.WAITING_ANSWER,
    )

    fun sanitizeActiveBrightness(value: Float): Float {
        if (!value.isFinite()) return DEFAULT_ACTIVE_BRIGHTNESS
        return value.coerceIn(MIN_ACTIVE_BRIGHTNESS, MAX_ACTIVE_BRIGHTNESS)
    }

    fun brightnessFor(
        lifecycle: AgentLifecycle?,
        activeBrightness: Float,
    ): Float = if (lifecycle in activeLifecycles) {
        sanitizeActiveBrightness(activeBrightness)
    } else {
        IDLE_BRIGHTNESS
    }
}