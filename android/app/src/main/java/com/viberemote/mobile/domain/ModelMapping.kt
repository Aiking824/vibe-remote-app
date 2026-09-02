package com.viberemote.mobile.domain

import com.viberemote.mobile.net.PendingRequest as NetPendingRequest
import com.viberemote.mobile.net.SubagentSnapshot as NetSubagent
import com.viberemote.mobile.net.TaskSnapshot as NetTask
import com.viberemote.mobile.net.TokenUsage as NetTokenUsage
import com.viberemote.mobile.net.UsageSnapshot as NetUsage

// ==================== net（wire 字符串模型）→ domain（AgentPager 枚举模型）映射 ====================

private fun lifecycleFromWire(value: String?): AgentLifecycle = when (value?.trim()?.lowercase()) {
    "offline" -> AgentLifecycle.OFFLINE
    "idle" -> AgentLifecycle.IDLE
    "starting" -> AgentLifecycle.STARTING
    "running", "thinking" -> AgentLifecycle.RUNNING
    "waitingapproval", "waiting_approval" -> AgentLifecycle.WAITING_APPROVAL
    "waitinganswer", "waiting_answer" -> AgentLifecycle.WAITING_ANSWER
    "succeeded", "success" -> AgentLifecycle.SUCCEEDED
    "interrupted", "interrupt" -> AgentLifecycle.INTERRUPTED
    else -> AgentLifecycle.IDLE
}

private fun activityFromWire(value: String?): AgentActivity? = when (value?.trim()?.lowercase()) {
    "thinking", "think" -> AgentActivity.THINKING
    "reading", "read" -> AgentActivity.READING
    "searching", "search" -> AgentActivity.SEARCHING
    "editing", "edit", "writing", "write" -> AgentActivity.EDITING
    "executing", "exec", "run" -> AgentActivity.EXECUTING
    "testing", "test" -> AgentActivity.TESTING
    "browsing", "browse" -> AgentActivity.BROWSING
    "delegating", "delegate", "task" -> AgentActivity.DELEGATING
    else -> null
}

private fun sourceFromWire(value: String?): AgentSource = when (value?.trim()?.lowercase()) {
    "claude", "claudecode", "claude_code" -> AgentSource.CLAUDE_CODE
    "codex-desktop", "codex_desktop", "codexgui", "codex_gui" -> AgentSource.CODEX_DESKTOP
    null, "codex", "codexcli", "codex_cli" -> AgentSource.CODEX_CLI
    else -> AgentSource.CODEX_CLI
}

private fun capabilityFromWire(value: String): TaskCapability? = when (value.trim().lowercase()) {
    "approve" -> TaskCapability.APPROVE
    "deny" -> TaskCapability.DENY
    "answer" -> TaskCapability.ANSWER
    "interrupt" -> TaskCapability.INTERRUPT
    "retry" -> TaskCapability.RETRY
    else -> null
}

fun NetTokenUsage.toDomain() = TokenUsage(
    input = input,
    cachedInput = cachedInput,
    output = output,
    reasoningOutput = reasoningOutput,
    total = total,
)

fun NetSubagent.toDomain() = SubagentSnapshot(
    id = id,
    path = path,
    displayName = displayName,
    lifecycle = lifecycleFromWire(lifecycle),
    activity = activityFromWire(activity),
    latestStep = latestStep,
    tokenUsage = tokenUsage?.toDomain(),
    startedAt = startedAt,
    updatedAt = updatedAt,
)

fun NetTask.toDomain() = TaskSnapshot(
    id = id,
    source = sourceFromWire(source),
    projectName = projectName,
    title = title,
    userPrompt = userPrompt,
    latestStep = latestStep?.ifBlank { null },
    tokenUsage = tokenUsage?.toDomain(),
    subagents = subagents.map { it.toDomain() },
    lifecycle = lifecycleFromWire(lifecycle),
    activity = activityFromWire(activity)
        ?: if (lifecycle.trim().equals("thinking", ignoreCase = true)) {
            AgentActivity.THINKING
        } else {
            null
        },
    startedAt = startedAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    isUnread = isUnread,
    isPinned = isPinned,
    isMuted = isMuted,
    capabilities = capabilities.mapNotNull { capabilityFromWire(it) }.toSet(),
)

fun NetUsage.toDomain() = UsageSnapshot(
    capturedAt = capturedAt,
    planType = planType,
    limitID = limitId,
    windows = windows.map {
        UsageWindow(
            key = it.key,
            label = it.label,
            usedPercentage = it.usedPercentage,
            remainingPercentage = it.remainingPercentage,
            windowMinutes = it.windowMinutes,
            resetsAt = it.resetsAt,
        )
    },
    dailyUsage = daily.map {
        DailyUsagePoint(
            date = it.date,
            inputTokens = it.inputTokens,
            cachedInputTokens = it.cachedInputTokens,
            outputTokens = it.outputTokens,
            reasoningOutputTokens = it.reasoningOutputTokens,
            totalTokens = it.totalTokens,
            estimatedCostUSD = it.estimatedCostUSD,
        )
    },
)

fun NetPendingRequest.toDomain() = PendingRequest(
    taskID = taskID,
    kind = if (kind.trim().equals("question", ignoreCase = true)) {
        PendingRequestKind.QUESTION
    } else {
        PendingRequestKind.APPROVAL
    },
    summary = summary.ifBlank { null },
    options = options,
)