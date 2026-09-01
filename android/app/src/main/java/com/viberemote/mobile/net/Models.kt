package com.viberemote.mobile.net

import org.json.JSONArray
import org.json.JSONObject

/** 单个按键定义（来自电脑端 keymap） */
data class KeyDef(
    val id: String,
    val label: String,
    val display: String,
    val group: String,         // dpad / fn / num / clip（页面分组）
    val type: String,          // key / combo / action
    val mapping: Any,          // String 或 List<String>
) {
    companion object {
        fun fromJson(o: JSONObject): KeyDef {
            val raw = o.opt("mapping")
            val mapping: Any = when (raw) {
                is JSONArray -> (0 until raw.length()).map { raw.getString(it) }
                else -> raw?.toString() ?: ""
            }
            return KeyDef(
                id = o.optString("id"),
                label = o.optString("label"),
                display = o.optString("display"),
                group = o.optString("group", "fn"),
                type = o.optString("type", "key"),
                mapping = mapping,
            )
        }
    }
}

/** Agent 状态（来源：status_watcher 推送） */
data class AgentStatus(
    val state: String,
    val message: String,
    val source: String,
    val project: String,
)

/** 已连接/历史会话设备（来自服务端 peer_info.peers） */
data class PeerInfo(
    val id: String,
    val name: String,
    val online: Boolean,
    val ip: String,
    val seenAt: Long,       // Unix 秒
)

/** 服务器 → 客户端事件 */
sealed class WsEvent {
    data object Paired : WsEvent()
    data class PairInfo(val confirmCode: String, val expiresIn: Int) : WsEvent()
    data class Error(val code: String, val message: String) : WsEvent()
    data class Status(val status: AgentStatus) : WsEvent()
    data class Keymap(val keys: List<KeyDef>) : WsEvent()
    data class Session(val phase: String, val message: String) : WsEvent()
    data class PeerInfoEv(val clients: Int, val peers: List<PeerInfo>) : WsEvent()
    data class Voice(val label: String) : WsEvent()
    data class Log(val text: String) : WsEvent()
    data class TasksSnapshot(
        val tasks: List<TaskSnapshot>,
        val focusedTaskID: String?,
        val pendingRequests: List<PendingRequest>,
    ) : WsEvent()
    data object Closed : WsEvent()
}

// ==================== AgentPager 风格任务模型（对齐 WireModels.TaskSnapshot）====================
/** Token 用量（对齐 AgentPager TokenUsage：input/cachedInput/output/reasoningOutput/total） */
data class TokenUsage(
    val input: Int = 0,
    val cachedInput: Int = 0,
    val output: Int = 0,
    val reasoningOutput: Int = 0,
    val total: Int = 0,
) {
    companion object {
        /** 兼容两种形态：对象（Codex rollout 用量）或数字（旧格式 total） */
        fun fromRaw(v: Any?): TokenUsage? = when (v) {
            is JSONObject -> TokenUsage(
                input = v.optInt("input"),
                cachedInput = v.optInt("cachedInput", v.optInt("cached")),
                output = v.optInt("output"),
                reasoningOutput = v.optInt("reasoningOutput", v.optInt("reasoning")),
                total = v.optInt("total").takeIf { it > 0 }
                    ?: (v.optInt("input") + v.optInt("output")),
            )
            is Number -> TokenUsage(total = v.toInt())
            else -> null
        }
    }
}

/** AgentPager 子代理快照（Codex SubagentStart/SubagentStop 生成） */
data class SubagentSnapshot(
    val id: String,
    val path: String,
    val displayName: String,
    val lifecycle: String,      // running/succeeded/...
    val activity: String?,
    val latestStep: String?,
    val tokenUsage: TokenUsage?,
    val startedAt: Long,        // 毫秒时间戳
    val updatedAt: Long,
    val completedAt: Long?,     // 终态时才有
) {
    val isTerminal: Boolean
        get() = lifecycle in setOf("succeeded", "interrupted")

    /** 耗时毫秒（终态取 completedAt，否则取当前时间） */
    fun elapsedAt(now: Long): Long =
        ((if (isTerminal) completedAt ?: updatedAt else now) - startedAt).coerceAtLeast(0)
}

/** AgentPager 任务快照（一台 agent session 对应一条，含生命周期/活动/能力/子代理） */
data class TaskSnapshot(
    val id: String,
    val source: String,         // claude | codex
    val projectName: String,
    val title: String,
    val userPrompt: String?,
    val latestStep: String,
    val tokenUsage: TokenUsage?,
    val subagents: List<SubagentSnapshot>,
    val lifecycle: String,      // offline/idle/starting/running/thinking/waitingApproval/waitingAnswer/succeeded/interrupted
    val activity: String?,      // thinking/reading/searching/editing/executing/testing/browsing/delegating
    val startedAt: Long,        // 毫秒时间戳
    val updatedAt: Long,
    val completedAt: Long?,
    val isUnread: Boolean,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val capabilities: List<String>,  // approve/deny/answer/interrupt/retry
) {
    val isTerminal: Boolean
        get() = lifecycle in setOf("succeeded", "interrupted")

    /** 需要用户处理：等待批准/回答、未读完成、置顶（与 AgentPager attentionPriority 对齐） */
    val needsAttention: Boolean
        get() = lifecycle in setOf("waitingApproval", "waitingAnswer") ||
            (isUnread && lifecycle == "succeeded") || isPinned

    /** 列表排序权重（与 AgentPager EffectivePriority / 服务端 _effective_priority 对齐） */
    val attentionScore: Int
        get() {
            val base = when (lifecycle) {
                "waitingApproval" -> 600
                "waitingAnswer" -> 500
                "succeeded" -> 300
                "starting", "running", "thinking" -> 200
                else -> 100
            }
            return base +
                (if (isUnread && lifecycle == "succeeded") 50 else 0) +
                (if (isPinned) 25 else 0)
        }

    fun elapsedAt(now: Long): Long {
        val endAt = if (isTerminal) completedAt ?: updatedAt else now
        return (endAt - startedAt).coerceAtLeast(0)
    }

    companion object {
        fun fromJson(o: JSONObject): TaskSnapshot {
            val subs = o.optJSONArray("subagents")
            val subList = mutableListOf<SubagentSnapshot>()
            if (subs != null) {
                for (i in 0 until subs.length()) {
                    val s = subs.optJSONObject(i) ?: continue
                    subList += SubagentSnapshot(
                        id = s.optString("id"),
                        path = s.optString("path"),
                        displayName = s.optString("displayName"),
                        lifecycle = s.optString("lifecycle", "idle"),
                        activity = s.optString("activity").ifBlank { null },
                        latestStep = s.optString("latestStep").ifBlank { null },
                        tokenUsage = TokenUsage.fromRaw(s.opt("tokenUsage")),
                        startedAt = s.optLong("startedAt"),
                        updatedAt = s.optLong("updatedAt"),
                        completedAt = if (s.isNull("completedAt")) null else s.optLong("completedAt"),
                    )
                }
            }
            val caps = o.optJSONArray("capabilities")
            val capList = mutableListOf<String>()
            if (caps != null) {
                for (i in 0 until caps.length()) {
                    capList += caps.optString(i)
                }
            }
            return TaskSnapshot(
                id = o.optString("id"),
                source = o.optString("source"),
                projectName = o.optString("projectName"),
                title = o.optString("title"),
                userPrompt = if (o.isNull("userPrompt")) null else o.optString("userPrompt"),
                latestStep = o.optString("latestStep"),
                tokenUsage = TokenUsage.fromRaw(o.opt("tokenUsage")),
                subagents = subList,
                lifecycle = o.optString("lifecycle", "idle"),
                activity = o.optString("activity").ifBlank { null },
                startedAt = o.optLong("startedAt"),
                updatedAt = o.optLong("updatedAt"),
                completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
                isUnread = o.optBoolean("isUnread"),
                isPinned = o.optBoolean("isPinned"),
                isMuted = o.optBoolean("isMuted"),
                capabilities = capList,
            )
        }
    }
}

/** AgentPager 等待用户交互的请求（审批/问题） */
data class PendingRequest(
    val taskID: String,
    val kind: String,           // approval | question
    val summary: String,
    val options: List<String>,
) {
    companion object {
        fun fromJson(o: JSONObject): PendingRequest {
            val arr = o.optJSONArray("options")
            val list = mutableListOf<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    list += arr.optString(i)
                }
            }
            return PendingRequest(
                taskID = o.optString("taskID"),
                kind = o.optString("kind", "approval"),
                summary = o.optString("summary"),
                options = list,
            )
        }
    }
}

// ==================== Codex 用量（对齐 AgentPager UsageSnapshot wire 结构，毫秒时间戳） ====================
/** 额度窗口：primary = 5 小时窗口，secondary = 周窗口 */
data class UsageWindow(
    val key: String,
    val label: String,          // 5h / 7d ...
    val usedPercentage: Double,
    val remainingPercentage: Double,
    val windowMinutes: Int,
    val resetsAt: Long?,        // 毫秒时间戳
) {
    companion object {
        fun fromJson(o: JSONObject): UsageWindow = UsageWindow(
            key = o.optString("key"),
            label = o.optString("label"),
            usedPercentage = o.optDouble("usedPercentage"),
            remainingPercentage = o.optDouble("remainingPercentage"),
            windowMinutes = o.optInt("windowMinutes"),
            resetsAt = if (o.isNull("resetsAt")) null else o.optLong("resetsAt"),
        )
    }

    val displayLabel: String
        get() = when (key) {
            "primary" -> "5 小时"
            "secondary" -> if (windowMinutes >= 10080) "本周" else label
            else -> label
        }
}

data class DailyUsagePoint(
    val date: String,
    val inputTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningOutputTokens: Long = 0,
    val totalTokens: Long = 0,
    val estimatedCostUSD: Double? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): DailyUsagePoint = DailyUsagePoint(
            date = o.optString("date"),
            inputTokens = o.optLong("inputTokens"),
            cachedInputTokens = o.optLong("cachedInputTokens"),
            outputTokens = o.optLong("outputTokens"),
            reasoningOutputTokens = o.optLong("reasoningOutputTokens"),
            totalTokens = o.optLong("totalTokens"),
            estimatedCostUSD = if (o.isNull("estimatedCostUSD")) null else o.optDouble("estimatedCostUSD"),
        )
    }
}

data class UsageSnapshot(
    val capturedAt: Long?,      // 毫秒时间戳
    val planType: String?,
    val limitId: String?,
    val windows: List<UsageWindow>,
    val daily: List<DailyUsagePoint>,
) {
    companion object {
        fun fromJson(o: JSONObject): UsageSnapshot {
            val winArr = o.optJSONArray("windows")
            val wins = mutableListOf<UsageWindow>()
            if (winArr != null) {
                for (i in 0 until winArr.length()) {
                    wins += UsageWindow.fromJson(winArr.optJSONObject(i) ?: continue)
                }
            }
            val dayArr = o.optJSONArray("daily")
            val days = mutableListOf<DailyUsagePoint>()
            if (dayArr != null) {
                for (i in 0 until dayArr.length()) {
                    days += DailyUsagePoint.fromJson(dayArr.optJSONObject(i) ?: continue)
                }
            }
            return UsageSnapshot(
                capturedAt = if (o.isNull("capturedAt")) null else o.optLong("capturedAt"),
                planType = if (o.isNull("planType")) null else o.optString("planType"),
                limitId = if (o.isNull("limitId")) null else o.optString("limitId"),
                windows = wins,
                daily = days,
            )
        }
    }

    /** 近 N 天 token 总量（无额度窗口时兜底展示） */
    fun tokensIn(days: Int): Long = daily.takeLast(days).sumOf { it.totalTokens }
}