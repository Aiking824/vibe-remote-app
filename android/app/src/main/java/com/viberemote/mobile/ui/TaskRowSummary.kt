package com.viberemote.mobile.ui

import com.viberemote.mobile.domain.AgentLifecycle
import com.viberemote.mobile.domain.TaskSnapshot

// ==================== AgentPager ui/TaskRowSummary.kt 100% 移植 ====================

internal fun shouldShowTokenSummary(task: TaskSnapshot): Boolean =
    task.lifecycle == AgentLifecycle.SUCCEEDED