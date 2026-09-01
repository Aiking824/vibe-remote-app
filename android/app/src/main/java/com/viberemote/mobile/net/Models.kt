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
    data object Closed : WsEvent()
}