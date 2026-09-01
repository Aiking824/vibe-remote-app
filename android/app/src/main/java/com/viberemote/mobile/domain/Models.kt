package com.viberemote.mobile.domain

// ==================== AgentPager domain/Models.kt 100% 移植（枚举版，无序列化注解） ====================

enum class AgentSource {
    CODEX_DESKTOP,
    CODEX_CLI,
    CLAUDE_CODE,
}

enum class AgentLifecycle {
    OFFLINE,
    IDLE,
    STARTING,
    RUNNING,
    WAITING_APPROVAL,
    WAITING_ANSWER,
    SUCCEEDED,
    INTERRUPTED,
}

enum class AgentActivity {
    THINKING,
    READING,
    SEARCHING,
    EDITING,
    EXECUTING,
    TESTING,
    BROWSING,
    DELEGATING,
}

enum class TaskCapability {
    APPROVE,
    DENY,
    ANSWER,
    INTERRUPT,
    RETRY,
}

data class TokenUsage(
    val input: Int = 0,
    val cachedInput: Int = 0,
    val output: Int = 0,
    val reasoningOutput: Int = 0,
    val total: Int = 0,
)

data class SubagentSnapshot(
    val id: String,
    val path: String,
    val displayName: String,
    val lifecycle: AgentLifecycle = AgentLifecycle.RUNNING,
    val activity: AgentActivity? = AgentActivity.THINKING,
    val latestStep: String? = null,
    val tokenUsage: TokenUsage? = null,
    val startedAt: Long,
    val updatedAt: Long,
) {
    val isTerminal: Boolean
        get() = lifecycle in setOf(
            AgentLifecycle.SUCCEEDED,
            AgentLifecycle.INTERRUPTED,
        )

    fun elapsedAt(now: Long): Long =
        ((if (isTerminal) updatedAt else now) - startedAt).coerceAtLeast(0)
}

data class TaskSnapshot(
    val id: String,
    val source: AgentSource,
    val projectName: String,
    val title: String = projectName,
    val userPrompt: String? = null,
    val latestStep: String? = null,
    val tokenUsage: TokenUsage? = null,
    val subagents: List<SubagentSnapshot> = emptyList(),
    val lifecycle: AgentLifecycle,
    val activity: AgentActivity? = null,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val isUnread: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val capabilities: Set<TaskCapability> = emptySet(),
) {
    val isTerminal: Boolean
        get() = lifecycle in setOf(
            AgentLifecycle.SUCCEEDED,
            AgentLifecycle.INTERRUPTED,
        )

    fun elapsedAt(now: Long): Long {
        val endAt = if (isTerminal) {
            requireNotNull(completedAt) { "终态任务必须包含完成时间" }
        } else {
            now
        }
        return (endAt - startedAt).coerceAtLeast(0)
    }

    val attentionPriority: Int
        get() {
            val lifecycleValue = when (lifecycle) {
                AgentLifecycle.WAITING_APPROVAL -> 600
                AgentLifecycle.WAITING_ANSWER -> 500
                AgentLifecycle.SUCCEEDED -> 300
                AgentLifecycle.STARTING, AgentLifecycle.RUNNING -> 200
                else -> 100
            }
            val unreadValue = if (isUnread && lifecycle == AgentLifecycle.SUCCEEDED) 50 else 0
            val pinnedValue = if (isPinned) 25 else 0
            return lifecycleValue + unreadValue + pinnedValue
        }
}

data class UsageWindow(
    val key: String,
    val label: String,
    val usedPercentage: Double,
    val remainingPercentage: Double,
    val windowMinutes: Int,
    val resetsAt: Long? = null,
)

data class DailyUsagePoint(
    val date: String,
    val inputTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningOutputTokens: Long = 0,
    val totalTokens: Long = 0,
    val estimatedCostUSD: Double? = null,
)

data class UsageSnapshot(
    val capturedAt: Long? = null,
    val planType: String? = null,
    val limitID: String? = null,
    val windows: List<UsageWindow> = emptyList(),
    val dailyUsage: List<DailyUsagePoint> = emptyList(),
)

data class StateSnapshotPayload(
    val tasks: List<TaskSnapshot>,
    val usage: UsageSnapshot? = null,
    val focusedTaskID: String? = null,
    val pendingRequests: List<PendingRequest> = emptyList(),
)

enum class PendingRequestKind {
    APPROVAL,
    QUESTION,
}

data class PendingRequest(
    val taskID: String,
    val kind: PendingRequestKind,
    val summary: String? = null,
    val options: List<String> = emptyList(),
)

enum class ControlAction {
    APPROVE,
    DENY,
    ANSWER,
    INTERRUPT,
    RETRY,
    MUTE,
    MARK_READ,
    PIN,
    DELETE,
}

enum class LinkState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}