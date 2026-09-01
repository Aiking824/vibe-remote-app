package com.viberemote.mobile

import com.viberemote.mobile.domain.LinkState
import com.viberemote.mobile.domain.PendingRequest
import com.viberemote.mobile.domain.TaskDashboardProjection
import com.viberemote.mobile.domain.TaskSnapshot
import com.viberemote.mobile.domain.UsageSnapshot

// ==================== AgentPager AgentGridUiState 移植（vibe-remote 适配：无 pairing 摘要） ====================

data class AgentGridUiState(
    val linkState: LinkState = LinkState.DISCONNECTED,
    val taskProjection: TaskDashboardProjection = TaskDashboardProjection.empty(),
    val usage: UsageSnapshot? = null,
    val pendingRequests: List<PendingRequest> = emptyList(),
    val activeTaskBrightness: Float = ScreenBrightnessPolicy.DEFAULT_ACTIVE_BRIGHTNESS,
) {
    val focusedTask: TaskSnapshot?
        get() = taskProjection.focusedTask
}