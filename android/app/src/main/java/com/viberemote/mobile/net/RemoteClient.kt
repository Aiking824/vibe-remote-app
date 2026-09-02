package com.viberemote.mobile.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 与电脑端（vibe-remote desktop）通信的 WS 客户端。
 *
 * 协议（与 desktop/app/main.py 一致）：
 *   hello → pair_info{confirm_code} → confirm{code} → paired → 可用
 *   key{action,key} / action{name} / hold_start{rate} + 二进制 PCM / hold_end
 *   get_keymap → keymap{keys: [...]}（服务器主动推送 / 变更广播）
 */
class RemoteClient(
    private val scope: CoroutineScope,
    val onEvent: (WsEvent) -> Unit,
) {
    private val ok = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var serverUrl: String = ""
    @Volatile private var token: String? = null
    private var reconnectJob: Job? = null
    private val sendLock = Mutex()

    // ---- 派生状态（UI 直接 collect） ----
    val paired = MutableStateFlow(false)
    val connText = MutableStateFlow("未连接")
    val status = MutableStateFlow(AgentStatus("READY", "尚未收到状态", "", ""))
    val sessionPhase = MutableStateFlow("idle")
    val sessionMsg = MutableStateFlow("")
    val keymap = MutableStateFlow<List<KeyDef>>(emptyList())
    val clients = MutableStateFlow(0)
    val peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val maxClients = MutableStateFlow(2)
    val lastVoice = MutableStateFlow("")
    val logs = MutableStateFlow<List<Pair<Long, String>>>(emptyList())

    // ---- AgentPager 风格任务快照（方案 B Hook 聚合）----
    val tasks = MutableStateFlow<List<TaskSnapshot>>(emptyList())
    val focusedTaskID = MutableStateFlow<String?>(null)
    val pendingRequests = MutableStateFlow<List<PendingRequest>>(emptyList())
    val usage = MutableStateFlow<UsageSnapshot?>(null)

    private fun pushLog(text: String) {
        val now = System.currentTimeMillis()
        val cur = logs.value.toMutableList()
        cur.add(0, now to text)
        if (cur.size > 80) cur.removeAt(cur.size - 1)
        logs.value = cur
    }

    // ---------- 连接 ----------
    /** @param url 形如 ws://192.168.1.2:8765/ws；token 为空则仅连接不配对。 */
    fun connect(url: String, token: String?) {
        reconnectJob?.cancel()
        serverUrl = url
        this.token = token
        connText.value = "连接中…"
        ws?.close(1000, "reconnect")
        ws = null
        openWs()
    }

    private fun openWs() {
        if (serverUrl.isBlank()) return
        // 先关闭旧连接，避免产生多条并行连接 + 被 scheduleReconnect 放大
        ws?.close(1000, "openWs-replace")
        ws = null
        val req = Request.Builder().url(serverUrl).build()
        ws = ok.newWebSocket(req, listener)
    }

    /** 断线重连（仅已配对/已带 token 时自动恢复）。
     *  增加幂等：若已有重连任务/正在工作中，则跳过重复调度。 */
    fun scheduleReconnect() {
        if (serverUrl.isBlank() || (token == null && !paired.value)) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(4000)
            // 如果当前有已连接的 WS，则不要重连（避免重复创建连接）
            if (ws != null && paired.value) return@launch
            connText.value = "重连中…"
            openWs()
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        ws?.close(1000, "bye")
        ws = null
        paired.value = false
    }

    // ---------- 发送 ----------
    private suspend fun sendRaw(obj: JSONObject) {
        sendLock.withLock {
            val s = ws ?: return
            if (!s.send(obj.toString())) {
                connText.value = "发送失败"
            }
        }
    }

    fun sendJson(obj: JSONObject) {
        scope.launch { sendRaw(obj) }
    }

    fun changeCloser() {}

    fun hello(token: String) {
        this.token = token.ifBlank { null }
        sendJson(JSONObject().put("type", "hello").put("token", token))
    }

    fun confirm(code: String) {
        sendJson(JSONObject().put("type", "confirm").put("code", code))
    }

    fun getKeymap() {
        sendJson(JSONObject().put("type", "get_keymap"))
    }

    fun getTasks() {
        sendJson(JSONObject().put("type", "get_tasks"))
    }

    fun keyTap(id: String) {
        sendJson(JSONObject().put("type", "key").put("action", "tap").put("key", id))
    }

    fun keyDown(id: String) {
        sendJson(JSONObject().put("type", "key").put("action", "down").put("key", id))
    }

    fun keyUp(id: String) {
        sendJson(JSONObject().put("type", "key").put("action", "up").put("key", id))
    }

    fun action(name: String) {
        sendJson(JSONObject().put("type", "action").put("name", name))
    }

    /** 触控板鼠标控制 */
    fun mouseMove(dx: Int, dy: Int) {
        sendJson(JSONObject().put("type", "mouse").put("action", "move").put("dx", dx).put("dy", dy))
    }

    fun mouseClick(button: String = "left") {
        sendJson(JSONObject().put("type", "mouse").put("action", "click").put("button", button))
    }

    fun mouseScroll(dy: Int) {
        sendJson(JSONObject().put("type", "mouse").put("action", "scroll").put("dy", dy))
    }

    /** 手机文本 → 电脑剪贴板并粘贴 */
    fun pasteText(text: String) {
        sendJson(JSONObject().put("type", "paste_text").put("text", text))
    }

    fun holdStart(rate: Int = 16000) {
        sendJson(JSONObject().put("type", "hold_start").put("rate", rate))
    }

    fun holdEnd() {
        sendJson(JSONObject().put("type", "hold_end"))
    }

    fun deletePeer(id: String) {
        sendJson(JSONObject().put("type", "delete_peer").put("id", id))
    }

    fun disconnectPeer(id: String) {
        sendJson(JSONObject().put("type", "disconnect_peer").put("id", id))
    }

    /** 发送 AgentPager 风格控制消息（approve/deny/interrupt/retry/markRead/pin/mute）。 */
    fun taskControl(taskID: String, action: String, value: String? = null) {
        val obj = JSONObject()
            .put("type", "task_control")
            .put("task_id", taskID)
            .put("action", action)
        if (value != null) obj.put("value", value)
        sendJson(obj)
    }

    /** 手动刷新 Codex 用量（服务端立即重载并回发 usage） */
    fun getUsage() {
        sendJson(JSONObject().put("type", "get_usage"))
    }

    /** 语音 PCM16（16kHz 单声道）分块上行 */
    fun feedPcm(chunk: ByteArray) {
        val s = ws ?: return
        try {
            s.send(chunk.toByteString())
        } catch (_: Exception) {
        }
    }

    // ---------- 接收 ----------
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connText.value = "已连接"
            // 每次 onOpen 都用最新的 webSocket 绑定，避免 connect()/openWs() 替换后
            // 回调仍然落到旧 socket 引用上
            ws = webSocket
            val t = token
            // 不管 URL 里是否有 ?pair=，都发一次 hello：
            //   • 有 token  →  走常规带 token 的配对流程
            //   • 无 token (USB 直连 127.0.0.1 默认) → 服务端自动生成 pair_info 并弹窗
            if (!paired.value) hello(t ?: "")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleJson(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // 本 socket 已失效，只有当它仍是当前活跃 socket 时才更新 UI/触发重连
            if (ws === webSocket) {
                connText.value = "连接失败：${t.message ?: "未知错误"}"
                ws = null
                scheduleReconnect()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            val replaced = reason == "reconnect" || reason == "openWs-replace" || reason == "bye"
            if (ws === webSocket) {
                if (code == 1000 || replaced) {
                    connText.value = if (replaced) "已断开" else "连接关闭"
                    // "reconnect" / "openWs-replace" 是主动替换：保留现有 paired 状态与重连能力
                    if (!replaced) paired.value = false
                    ws = null
                } else {
                    ws = null
                    scheduleReconnect()
                }
            }
        }
    }

    private fun handleJson(text: String) {
        val m = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        when (m.optString("type")) {
            "paired" -> {
                paired.value = true
                connText.value = "已配对"
                val mc = m.optInt("max_clients", 2)
                if (mc >= 1) maxClients.value = mc
                getKeymap()
                getTasks()
                onEvent(WsEvent.Paired)
            }
            "pair_info" -> {
                // 当客户端没带 ?pair= token（USB 直连默认 URL）时，服务端会生成 token 并塞回来；
                // 这里保存一份，使得 scheduleReconnect / confirm 时都能带上。
                val tok = m.optString("token")
                if (!tok.isNullOrBlank()) this@RemoteClient.token = tok
                onEvent(WsEvent.PairInfo(m.optString("confirm_code"), m.optInt("expires_in")))
            }
            "error" -> {
                onEvent(WsEvent.Error(m.optString("code"), m.optString("message")))
                if (m.optString("code") == "replaced") paired.value = false
            }
            "status" -> {
                val st = AgentStatus(
                    state = m.optString("state", "READY"),
                    message = m.optString("message"),
                    source = m.optString("source"),
                    project = m.optString("project"),
                )
                status.value = st
                onEvent(WsEvent.Status(st))
            }
            "keymap" -> {
                val arr = m.optJSONArray("keys") ?: return
                val keys = (0 until arr.length()).map { KeyDef.fromJson(arr.getJSONObject(it)) }
                keymap.value = keys
                onEvent(WsEvent.Keymap(keys))
            }
            "session" -> {
                sessionPhase.value = m.optString("phase", "idle")
                sessionMsg.value = m.optString("message")
                onEvent(WsEvent.Session(sessionPhase.value, sessionMsg.value))
            }
            "peer_info" -> {
                clients.value = m.optInt("clients")
                val list = mutableListOf<PeerInfo>()
                val arr = m.optJSONArray("peers")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        list += PeerInfo(
                            id = o.optString("id"),
                            name = o.optString("name").ifBlank { "设备 ${o.optString("id")}" },
                            online = o.optBoolean("online"),
                            ip = o.optString("ip"),
                            seenAt = o.optLong("seen_at"),
                        )
                    }
                }
                peers.value = list
                onEvent(WsEvent.PeerInfoEv(clients.value, list))
            }
            "voice" -> {
                val label = m.optString("label", m.optString("word"))
                lastVoice.value = label
                onEvent(WsEvent.Voice(label))
            }
            "log" -> {
                pushLog(m.optString("text"))
                onEvent(WsEvent.Log(m.optString("text")))
            }
            "tasks" -> {
                // AgentPager 风格：tasks 快照（与 WireModels.StateSnapshotPayload 对齐）
                val arr = m.optJSONArray("tasks")
                val list = mutableListOf<TaskSnapshot>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        list += TaskSnapshot.fromJson(o)
                    }
                }
                tasks.value = list
                val fid = m.optString("focusedTaskID", "").ifBlank { null }
                focusedTaskID.value = fid
                val reqArr = m.optJSONArray("pendingRequests")
                val reqs = mutableListOf<PendingRequest>()
                if (reqArr != null) {
                    for (i in 0 until reqArr.length()) {
                        val o = reqArr.optJSONObject(i) ?: continue
                        reqs += PendingRequest.fromJson(o)
                    }
                }
                pendingRequests.value = reqs
                val u = m.optJSONObject("usage")
                usage.value = u?.let { UsageSnapshot.fromJson(it) } ?: usage.value
                onEvent(WsEvent.TasksSnapshot(list, fid, reqs))
            }
        }
    }
}