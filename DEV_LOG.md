# Vibe Remote 开发日志（v2.0）

> 本文档面向未来接手本项目的 AI 智能体（Harness / Codex / Claude 等）以及新开发者。
> 目标：在最短时间内理解整个项目的架构、数据流、关键约束、如何编译运行。

***

## 0. 版本 / 里程碑

| 分支         | Commit    | 说明                                                                                      |
| ---------- | --------- | --------------------------------------------------------------------------------------- |
| `main`     | `6a4b07d` | 旧版本：GUI 桌面窗口 + 系统托盘 + 基础 Agent 页（手绘 UI）                                                 |
| **`v2.0`** | `5490feb` | **当前稳定版本**：AgentPager 全量移植（任务列表 / 详情 / 用量仪表盘 / 设置 / 8-bit 音效 / 像素核心渲染 / 任务删除 / Hook 聚合） |

*当前工作目录*：`d:\AIData\traedata\vibe-remote`

***

## 1. 项目是什么（一句话）

**Android App + Windows 桌面端，通过 WebSocket 双向通信**：

- 手机做"遥控器"：按键（DPad/功能/数字/快捷键）、触控板鼠标、按住说话（PCM → 电脑 CABLE 麦克风）；

- **Agent 页（v2.0 新增）**：Claude Code / Codex 的任务实时查看（Hook 聚合）、手机端审批权限、回答问题、中断、**删除任务**、用量明细。

***

## 2. 目录结构（请先在脑海里建图）

```
vibe-remote/
├─ android/                       # Android 端（Jetpack Compose）
│   ├─ app/
│   │   ├─ build.gradle.kts       # App 构建脚本
│   │   └─ src/main/java/com/viberemote/mobile/
│   │       ├─ MainActivity.kt    # 入口：权限、扫码、RemoteScreen 挂载、亮度回调
│   │       ├─ AgentUiState.kt    # AgentGridScreen 的 UI 状态类（来自 AgentPager）
│   │       ├─ ScreenBrightnessPolicy.kt  # AgentPager 屏幕亮度策略
│   │       ├─ SoundEngine.kt     # 8-bit 音效播放（AudioTrack + 8-bit 合成）
│   │       ├─ ArcadeSounds.kt    # 8-bit 音符合成（TASK_ACK / INPUT / COMPLETE / INTERRUPTED + TaskSoundTracker）
│   │       ├─ audio/             # MicPcmRecorder：按住说话 16kHz PCM 录制
│   │       ├─ voice/             # VoiceCmd：本地语音命令（当前已被"按住说话→CABLE"替代）
│   │       ├─ net/
│   │       │   ├─ Models.kt      # 线级数据模型（AgentStatus / KeyDef / TaskSnapshot / UsageSnapshot / PeerInfo…）
│   │       │   └─ RemoteClient.kt# OkHttp WebSocket 客户端：连接/重连/协议收发/StateFlow
│   │       ├─ domain/            # *v2.0 新增：AgentPager 领域层（枚举模型）*
│   │       │   ├─ Models.kt               # AgentLifecycle/Activity/TaskCapability/ControlAction/TaskSnapshot...
│   │       │   ├─ TaskDashboardProjection.kt# 投影：排序/置顶展开/紧急识别/自动切用量仪表盘
│   │       │   └─ ModelMapping.kt          # net（wire 字符串模型） → domain（枚举模型）
│   │       └─ ui/
│   │           ├─ RemoteScreen.kt         # 主 Scaffold：4 个 Tab（遥控/鼠标/快捷键/Agent）
│   │           ├─ AgentGridTheme.kt       # *v2.0 新增：AgentPager 主题（像素字体+调色板）*
│   │           ├─ AgentGridScreen.kt      # *v2.0 新增：AgentPager 任务页（列表/详情/用量仪表盘/设置）*
│   │           ├─ UsageDashboard.kt       # *v2.0 新增：用量明细（柱状图 + 时间段切换）*
│   │           ├─ UsagePresentation.kt    # *v2.0 新增：用量展示工具（柱状图尺寸/颜色）*
│   │           ├─ PixelCoreRender.kt      # *v2.0 新增：3×3 像素核心（Canvas 动画）*
│   │           ├─ TaskRowSummary.kt       # *v2.0 新增：任务行摘要工具*
│   │           └─ （旧的 AgentPage 辅助函数已全部删除，约 57KB）
│   └─ build.gradle.kts / settings.gradle.kts / gradlew.bat
│
└─ desktop/                       # Windows 桌面端（Python + asyncio + websockets）
    ├─ app/
    │   ├─ main.py                # 核心：VibeRemoteServer（WebSocket + HTTP + Hook TCP + 状态聚合）
    │   ├─ agent_hook.py          # *v2.0 新增：Claude/Codex Hook 入口（--install/--uninstall/--hook）*
    │   ├─ codex_usage.py         # *v2.0 新增：Codex 用量读取器（日志/配额 → UsageSnapshot dict）*
    │   ├─ codex_rollout.py       # *v2.0 新增：Codex rollout 文件观察（细粒度子代理/步骤/token）*
    │   ├─ key_injector.py        # Windows 按键注入（ctypes SendInput）
    │   ├─ keymap.py              # 键位存储（JSON 持久化）
    │   ├─ keymap.json            # 默认键位（DPad/Fn/Num/快捷键）
    │   ├─ pairing.py             # 配对（token + 确认码 + 过期）
    │   ├─ status_watcher.py      # 进程状态观察（备用，现以 Hook 为准）
    │   ├─ gui_entry.py           # GUI 模式入口（Tkinter + 托盘）
    │   ├─ audio_sink.py          # 手机 PCM 音频写入 CABLE Input
    │   ├─ audio_default_switch.py# 默认输入设备切换（WinAPI）
    │   └─ vibe_config.json       # 配置（端口/最大客户端/…）
    ├─ web/
    │   ├─ index.html             # 静态首页
    │   └─ console.html           # 配对控制台
    ├─ launch/                    # 4 种连接方式启动 BAT
    ├─ requirements.txt           # Python 依赖
    ├─ vibe-remote.spec           # PyInstaller：vibe-remote.exe（GUI 版，无黑窗）
    ├─ vibe-remote-gui.spec       # 同上（别名）
    └─ vibe_agent_hook.spec       # PyInstaller：vibe_agent_hook.exe（CLI，必须 dist 根目录）
```

***

## 3. 技术栈

| 层          | 技术                                                          | 说明                                                          |
| ---------- | ----------------------------------------------------------- | ----------------------------------------------------------- |
| Android 语言 | Kotlin + Jetpack Compose                                    | Material3；minSdk 26；包名 `com.viberemote.mobile`              |
| 网络 (手机→电脑) | OkHttp 3 WebSocket（15s ping）                                | `ws://host:8765/ws`                                         |
| Android 打包 | Gradle (KTS) + AGP                                          | `assembleDebug` 生成 `app-debug.apk`                          |
| Desktop 语言 | Python 3.10+（asyncio + websockets）                          | 单进程事件循环                                                     |
| 桌面 GUI     | Tkinter + pystray（系统托盘）                                     | `gui_entry.py` 启动                                           |
| 打包         | PyInstaller 3 个 spec：主程序 2 个 GUI spec + 1 个 Hook CLI spec   | **vibe\_agent\_hook.exe 必须在 dist 根目录**（GUI 重新打包会清空 gui 子目录） |
| Hook 通信    | TCP `127.0.0.1:49361` 行协议（JSON + `\n`）                      | agent\_hook.py → main.py `_start_hook_tcp_server`           |
| 用量         | `codex_usage.py` 扫 Codex 日志/配额文件 → dict                     | 每 120s 刷新，手机端随时 `get_usage` 触发                              |
| 音频         | 手机：`MicPcmRecorder` 16kHz 16-bit 单声道 PCM → WebSocket 二进制帧   | 电脑：`audio_sink.py` 写入虚拟 CABLE 输入                            |
| 字体         | `res/font/fusion_pixel_12px_zh_hans.ttf` + silkscreen（英文备用） | 像素风字体，AgentPager 风格                                         |

***

## 4. 通信协议速查（所有 AI 接手必须先读这一段）

### 4.1 WebSocket 上行（手机 → 桌面）

*所有消息 JSON 都带* *`type`* *字段*

| type                                         | 用途                                       | 关键字段                                          |
| -------------------------------------------- | ---------------------------------------- | --------------------------------------------- |
| `hello`                                      | 首次握手                                     | `token`                                       |
| `confirm`                                    | 输入确认码配对                                  | `code`                                        |
| `key`                                        | 按键                                       | `action=tap/down/up` + `key=<KeyDef.id>`      |
| `action`                                     | 命名动作（锁屏 LockWorkStation 等）               | `name`                                        |
| `mouse`                                      | 鼠标控制                                     | `action=move/click/scroll` + `dx,dy / button` |
| `paste_text`                                 | 手机文本 → 电脑（现功能已按需求"移除"，实际函数仍存在，只是 UI 未暴露） | `text`                                        |
| `hold_start` / `hold_end`                    | 按住说话帧边界                                  | `rate`                                        |
| （二进制帧）                                       | PCM16 16kHz mono 分块                      | -                                             |
| `get_keymap` / `set_keymap` / `keymap_reset` | 键位管理                                     | `mapping`                                     |
| `delete_peer` / `disconnect_peer`            | 设备管理                                     | `id`                                          |
| `get_logs`                                   | 获取运行日志                                   | -                                             |
| **`task_control`**                           | v2.0：手机控制 agent 状态                       | **见 4.1.1**                                   |
| **`get_usage`**                              | v2.0：立即刷新 Codex 用量                       | -                                             |

#### 4.1.1 `task_control` 格式（核心！）

```json
{
  "type": "task_control",
  "task_id": "<session_id>",
  "action": "approve|deny|answer|interrupt|retry|markread|pin|mute|delete",
  "value": "<answer text 或 pin/mute 的 bool>"
}
```

桌面端在 `VibeRemoteServer._handle_task_control` 处理（`main.py:915`）。关键点：

- **approve/deny**：把决策通过 TCP 写回挂起的 hook socket（Claude/Codex 两个格式不同，见 `_encode_permission_response`）。

- **answer**：写 `answerText` 到 task；若该等待挂着 Permission socket 也一并 `approve` 放行。

- **interrupt/retry**：改 lifecycle + capabilities。

- **markread/pin/mute**：改对应布尔。

- **delete**：先 release 挂起 socket（approve 防 agent 卡死）→ `del tasks[id]` → 加入 `deleted_tasks` 集合（防 hook 后续事件复活）。

### 4.2 WebSocket 下行（桌面 → 手机）

| type          | 用途                                                                   |
| ------------- | -------------------------------------------------------------------- |
| `pair_info`   | 需要输入确认码：`{token, confirm_code, expires_in}`                          |
| `paired`      | 配对成功：`{id, max_clients}`；**随后立即推送 tasks 快照**                         |
| `error`       | 错误：`{code, message}`                                                 |
| `keymap`      | 键位文档：`{keys: [...]}`                                                 |
| `peer_info`   | 设备列表：`{clients, maxClients, peers:[{id,name,online,ip,seenAt}]}`     |
| `status`      | Agent 状态：`{state,message,source,project}`（已被 tasks 取代，仍保留 PadPage 用） |
| `session`     | 旧版会话状态：`{phase,message}`（同上）                                         |
| `voice_label` | 电脑侧语音识别反馈                                                            |
| **`tasks`**   | v2.0 主力：`{tasks:[...], focusedTaskID, pendingRequests, usage}`       |

### 4.3 Hook TCP（Claude/Codex → 桌面端 localhost:49361）

桌面端 `_start_hook_tcp_server` 监听，`agent_hook.py` 以 `--hook --source claude|codex` 方式被 Claude/Codex 调用：

```
Claude/Codex stdin (JSON) → agent_hook.py → TCP 127.0.0.1:49361
  {"hook_source":"claude","payload":{...}} \n
  ↓
main._reduce_hook(source, payload) → self.tasks[session_id] 聚合
  ↓ (PermissionRequest 事件会挂起)
手机 approve/deny → TCP 决策 JSON 回写 → Claude/Codex stdout
```

**字段别名兼容**（`_reduce_hook._get`，必须保留，C# / 历史 hook 混用）：

```
sessionId    ↔ session_id          → id
projectRoot  ↔ cwd                 → projectName
userPrompt   ↔ prompt              → title
toolName     ↔ tool_name ↔ toolType ↔ tool_type
turnId       ↔ turn_id
agentId      ↔ agent_id
taskDescription ↔ task_description
```

### 4.4 Hook 事件 → lifecycle 映射表（`_reduce_hook` 中）

| 事件                                                                | lifecycle                                                         | capabilities                                                                                            |
| ----------------------------------------------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| SessionStart                                                      | starting                                                          | \[]（若带 userPrompt，则直接填 title）                                                                           |
| UserPromptSubmit                                                  | running                                                           | \[interrupt]                                                                                            |
| PreToolUse / PostToolUse                                          | running                                                           | \[interrupt] + 从 toolName 推断 activity（executing/searching/reading/editing/browsing/delegating/thinking） |
| PermissionRequest                                                 | waitingApproval（permissionMode 含 tool/bash/file） 否则 waitingAnswer | \[approve, deny, answer, interrupt]                                                                     |
| PostToolUseFailure / PermissionDenied                             | interrupted                                                       | \[retry]                                                                                                |
| Stop                                                              | succeeded（除非已 interrupted）                                        | \[retry]                                                                                                |
| StopFailure                                                       | 回滚 succeeded → running                                            | \[]                                                                                                     |
| SessionEnd                                                        | 非终态 → offline                                                     | \[]                                                                                                     |
| SubagentStart/Stop                                                | parent 不切 lifecycle，subagents 表增减                                 | -                                                                                                       |
| Notification idle\_prompt/elicitation\_dialog/agent\_needs\_input | waitingAnswer                                                     | \[answer, interrupt]                                                                                    |

### 4.5 PermissionRequest 等待链路（必须理解）

```
hook TCP 收到 PermissionRequest
  → 标记 task.lifecycle = waitingApproval / waitingAnswer
  → 把 TCP sock 复制到 self._pending_permission[session_id]
  → broadcast_tasks() 手机出现按钮
  → 本协程每 0.5s 轮询 lifecycle，直到非 waiting*（手机审批触发）或超时 24h
  → approve/deny 通过 _pending_permission[session_id].sendall(allow JSON) 回写
  → answer 写入 task.answerText + 附带 socket approve
  → delete 先 approve 再 pop task + deleted_tasks
```

***

## 5. 移动端数据流（v2.0 Agent 页）

```
RemoteClient.StateFlow  (net 层 wire 字符串模型)
  tasks: List<TaskSnapshot (net)>
  focusedTaskID, pendingRequests, usage, paired
        ↓ collectAsState()
        ↓ remember { tasks.map { it.toDomain() } ...  }   ← ModelMapping.kt（net→枚举）
  StateSnapshotPayload（domain 层）
        ↓ LaunchedEffect(snapshot) { TaskDashboardProjector.project(snapshot, prev, now) }
  TaskDashboardProjection
    - orderedTasks (按 WAITING_APPROVAL > RUNNING > SUCCEEDED + 置顶 + 更新时间 排序)
    - visibleTasks (最多 5 条，enteringVisibleTaskIDs 带动画)
    - urgentTaskID（需用户处理，自动置顶展开）
    - focusedTaskID / automaticallyExpandedTaskID
    - dashboardVisible (闲置 90 秒自动切回用量仪表盘；manualDashboardOverride 手动切换)
    - nextDashboardAt
        ↓
  AgentGridUiState(linkState, projection, usage, pendingRequests, activeTaskBrightness)
        ↓ 传入 AgentGridScreen
  AgentGridScreen (AgentPager UI：TaskTerminal → TASKS / SETTINGS 双屏 + 详情 + 用量仪表盘)
        ↓ 回调
  RemoteClient.taskControl(id, action.toWire(), value)
```

### 5.1 为什么要分 net 和 domain 两层？

- **net 层**：必须和桌面端 JSON 字段完全对齐（兼容旧设备/旧服务器），字段是字符串（lifecycle="waitingApproval" 等）。

- **domain 层**：来自 AgentPager 的模型，使用 Kotlin enum（AgentLifecycle / AgentActivity / ControlAction / TaskCapability…），AgentGridScreen / UsageDashboard / PixelCoreRender / ArcadeSounds 全部依赖这层。

- **ModelMapping.kt**：做翻译。未来桌面端新增字段时，先在 net/Models.kt 加，再在 ModelMapping 里加映射。

### 5.2 UI 入口链

```
MainActivity.setContent → AppContent
  └─ RemoteScreen(
       client, pairUrlInput,
       onAgentBrightness = { applyAgentBrightness(it) }  ← 调整 window.attributes.screenBrightness
     )
       └─ Scaffold bottomBar (已配对 4 Tab：PAD / MOUSE / HOT / AGENT)
            ├─ PadPage      → DPad/Fn/Num 行 + StatusCard + 语音按住说话
            ├─ MousePage    → TouchArea（move+click+scroll）
            ├─ HotPage      → Clipboard → Ctrl+C/X/V 快捷键（需求改名"快捷键"Tab）
            └─ AgentPage    ──v2.0──▶ AgentGridScreen 接线（见 4 数据流）
```

### 5.3 v2.0 UI 关键特性（AgentPager 100% 移植）

- **AgentGridTheme**：像素字体 `fusion_pixel_12px_zh_hans.ttf` + 深色调色板（Bg=`#05070B` / Amber=`#FDBA4A` 主色），无亮色主题切换。

- **任务卡片**：彩色 lifecycle 圆点、来源徽标（C/X/T/D/S 对应 Claude/Codex/Trae/DeepSeek/Solo）、项目名+标题+最近步骤+子代理 Mini 行、8-bit 音效差分触发（`TaskSoundTracker.nextCue`）。

- **紧急任务置顶展开**：`TaskDashboardProjector.urgentTaskID` / `automaticallyExpandedTaskID`，其他任务变暗色（`AgColors.Dimmed`）。

- **任务详情**：顶部 **DELETE 双步确认**（按 1 次变红闪烁"确认(3s)"，3s 内再按才发 `task_control: delete`，否则还原）、Lifecycle 时间线、Activity 进度条、子代理列表、权限审批条（APPROVE/DENY/ANSWER/INTERRUPT/RETRY 大按钮）、回答输入框。

- **用量仪表盘**：顶部 5 小时/周/月切换（AnimatedContent 动画）+ 柱状图（Canvas 直绘）+ 每日 tokens 明细 + 估算成本。

- **设置**：连接信息 / Hook 安装说明 4 步 / 卸载按钮 / 音量策略 + 亮度滑块。

- **像素核心**：`PixelCoreRender.kt` 自定义 3×3 像素 Canvas 动画，在用量仪表盘空状态/任务列表空状态显示。

- **屏幕亮度**：`ScreenBrightnessPolicy` —— 焦点任务处于 STARTING/RUNNING/WAITING\_\* 时调亮到 `activeTaskBrightness`（滑块可调，默认 65%），否则降回 15%，离开 Agent 页用 `null` 恢复系统亮度。

- **8-bit 音效**：`ArcadeSounds.TaskSoundTracker` 只在 lifecycle 差分触发（避免重组重播）：TASK\_ACKNOWLEDGE（RUNNING）、INPUT\_REQUIRED（WAITING\_\* + 振动 70ms）、TASK\_COMPLETE（SUCCEEDED）、TASK\_INTERRUPTED（INTERRUPTED）。

***

## 6. 桌面端关键模块

### 6.1 main.py（1500+ 行，**核心文件**）

```
VibeRemoteServer(websockets.WebSocketServerProtocol)
├─ handle_ws              WS 入口：未配对路由 hello/confirm；已配对路由 key/mouse/task_control/...
├─ _handle_task_control   §4.1.1 的实现 + broadcast_tasks
├─ _reduce_hook           Hook → tasks 聚合（别名兼容 + lifecycle/activity/capability 映射）
├─ _handle_hook_client    TCP 行协议：读 payload → _reduce_hook → Permission 挂起等待审批回写
├─ _start_hook_tcp_server 127.0.0.1:49361（Hook 端口被占用 → 整个 Hook 聚合禁用）
├─ broadcast_tasks        按 _effective_priority 排序 → push {tasks, pendingRequests, usage}
├─ _pending_requests      WAITING_APPROVAL/WAITING_ANSWER 任务汇总（AgentPager 专用）
├─ refresh_usage          CodexUsageLoader.load() → self.usage_snapshot
├─ _usage_tick            启动 10s 延迟 + 每 120s 刷新用量
├─ _rollout_tick          codex_rollout.include(payload) 额外细粒度信号（token/subagent）
├─ process_request        HTTP：/pair/info, /pair/console, /pair/qr.png, /, /ws（区分 Upgrade header）
└─ run                    生命周期：音频线程 + adb reverse + Hook TCP + rollout tick + usage tick + WS 服务
```

### 6.2 agent\_hook.py

- `--install claude|codex|both`：把 `vibe_agent_hook.exe --hook --source xxx` 写入 Claude Code 配置 / Codex hooks 配置；

- `--uninstall`：移除 WRAP\_TAG 标记的条目；

- `--hook --source xxx`：读取 stdin → TCP localhost:49361 → Permission 等待 server 决策写 stdout；非权限等 `\n` 即返回。Hook server 未启动必须静默放行（Permission 事件默认 allow，避免卡死 agent）。

### 6.3 codex\_usage.py（UsageSnapshot 字段）

```json
{
  "capturedAt": 1700000000000,
  "planType": "free|pro|team",
  "limitId": "weekly_400k_5h",
  "windows": [
    {"key":"5h","label":"近 5 小时","usedPercentage":62.5,"remainingPercentage":37.5,"windowMinutes":300,"resetsAt":1700000000000},
    {"key":"weekly","label":"本周","usedPercentage":38.0,"remainingPercentage":62.0,"windowMinutes":10080,"resetsAt":...}
  ],
  "daily": [
    {"date":"2026-09-01","inputTokens":1000,"cachedInputTokens":500,"outputTokens":2000,"reasoningOutputTokens":0,"totalTokens":3500,"estimatedCostUSD":0.021}
  ]
}
```

### 6.4 打包（PyInstaller 关键约束！）

- 主程序 GUI：`vibe-remote.spec` / `vibe-remote-gui.spec` → `dist/vibe-remote-gui/vibe-remote-gui.exe`（无控制台）

- Hook CLI：`vibe_agent_hook.spec` → **`dist/vibe_agent_hook.exe`（dist 根！不能在 gui 子目录）**

  - 原因：重新打包 GUI 会清空 `dist/vibe-remote-gui/`，把 Hook 放根就不会被删掉。

  - `--install` 默认 exe 路径 = `sys.argv[0]` 所在目录下的 `vibe_agent_hook.exe`。

***

## 7. 编译命令（日常开发）

### Android（Kotlin）

```powershell
cd d:\AIData\traedata\vibe-remote\android
# Kotlin 编译检查（最快定位语法/import 错误）
.\gradlew.bat :app:compileDebugKotlin --console=plain -q

# 打 debug APK
.\gradlew.bat :app:assembleDebug --console=plain -q
# 产物：app\build\outputs\apk\debug\app-debug.apk

# 安装到已连接的手机（需开启 USB 调试）
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.viberemote.mobile -c android.intent.category.LAUNCHER 1

# 查崩溃
adb logcat | Select-String FATAL,AndroidRuntime,viberemote
```

### Desktop（Python）

```powershell
cd d:\AIData\traedata\vibe-remote\desktop
pip install -r requirements.txt

# 直接运行开发模式
python -m app.main
# 或 GUI 模式
python -m app.gui_entry

# 安装 Hook（打包后用；开发模式直接 python agent_hook.py --install both）
dist\vibe_agent_hook.exe --install both

# 打包 Hook CLI
pyinstaller vibe_agent_hook.spec --noconfirm

# 打包 GUI
pyinstaller vibe-remote.spec --noconfirm
```

***

## 8. 关键用户需求约束（历史决策，**千万不要违反**）

以下来自用户多次明确要求，请视作硬约束：

1. **手机端删除任务**：必须双步确认（DELETE → 3s 内再次点击）。已实现在 AgentGridScreen.kt TaskDetails 的顶部工具栏。
2. **tokens 用量点开查看**：用量仪表盘必须和 AgentPager 完全一致（切换 5 小时/周的柱状图 + 每日明细）。已用 `UsageDashboard` + `UsagePresentation` 实现。
3. **遥控器按键一屏显示不滚动**：PadPage 严格按 Screen 高度切比例，不得让用户竖向滚动。
4. **移除"语音方向命令"软件**：桌面端本地 winsdk 语音方向必须停用（`_start_speech` 只打一条日志），防止和 CABLE 方案冲突。语音链路改为：手机按住说话 → PCM → 电脑 CABLE Input → 微信输入法文字识别。
5. **语音按钮只有"按住说话"文案**，不要加其他描述。
6. **Delete 键映射 Backspace（⌫），Esc 键需存在**。RemoteScreen.kt keyIcon：Icons.AutoMirrored.Backspace。
7. **移除手机→电脑文本发送**（pasteText 接口仍在，但 UI 上不得暴露"复制/剪切/粘贴"按键于遥控器；已合并到 Tab "快捷键" 中作为 Ctrl+C/X/V 按钮）。
8. **底部 Tab 命名**：原 "Clipboard" 改为 **"快捷键"**（快捷组合键：Ctrl+C/X/V 等），与 Ctrl+C/X/V 区分。
9. **Win+L 锁屏必须用系统 API** **`LockWorkStation()`**：不能用模拟按键 Win+L（企业策略常拦截）。`action("lock_workstation")` → `LockWorkStation()` ctypes 调用。
10. **电脑端 GUI 只显示连接界面**，不显示后续页；所有复杂页在手机。
11. **Hook 事件字段兼容 camelCase 与 snake\_case**：sessionId/session\_id, projectRoot/cwd, userPrompt/prompt, toolName/toolType/tool\_type... 见 `_reduce_hook._get`。
12. **Hook 安装目录必须指向 dist 根的 vibe\_agent\_hook.exe**，避免 GUI 重新打包被删。
13. **Hook install/uninstall 必须 console=True**（PyInstaller spec 中 `console=True`），stdout 显示给用户看安装结果。
14. **任务聚合 session\_id/sessionId 非空才合并**；SessionStart 带 userPrompt 立即写标题。
15. **toolType='bash' 归类 executing lifecycle activity**。
16. **Permission 关键词支持 3 种**：`bash` / `file` / `tool` → waitingApproval；其余 → waitingAnswer。
17. **任务状态 8 个必须显式区分**：STARTING / RUNNING / THINKING（activity） / WAITING\_APPROVAL / WAITING\_ANSWER / SUCCEEDED / INTERRUPTED / OFFLINE + 颜色（蓝/琥珀/青/紫/绿/红/灰）。
18. **待用户处理任务自动置顶 + 展开**，其他任务变灰（`AgColors.Dimmed`）。
19. **任务完成/中断/等待**使用像素动画 + 8-bit 音效 + 振动（`ArcadeSounds.INPUT_REQUIRED` 振动 70ms/110 振幅）。
20. **手机端权限处理**：手机直接 APPROVE/DENY，无需回电脑按。已实现见 §4.4 链路。
21. **子代理显示**：父任务卡片显示正在工作的子代理、耗时、最近步骤、token。已在 `SubMiniRow` / 详情 subagent 列表实现。
22. **配额监控**：TASKS 页顶部显示 Codex 5 小时 + 周用量 + `UsageDashboard` 明细点开查看，界面对齐 AgentPager。
23. **任务交互**：TASKS 页每条有 **ANSWER** 按钮、详情页有回复输入框。

***

## 9. 常见"坑"与修复记录

| 坑                                                            | 解决                                                                                                                                                                |
| ------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **枚举扩展函数写** **`AgentLifecycle.Companion.fromWire`** → 编译失败   | Kotlin enum 默认没有显式 Companion，改顶层函数（见 `ModelMapping.kt` 中 `lifecycleFromWire`/`activityFromWire`/`sourceFromWire`/`capabilityFromWire`）。                           |
| **RemoteClient.kt 里 tasks/usage 的字段变化了** → AgentPager UI 不显示 | 只改 `net/Models.kt`，忘记改 `domain/ModelMapping.kt` → UI 收到空值。**必须同时改两处**。                                                                                            |
| **Hook 安装后不触发**                                              | 1) dist 根没 vibe\_agent\_hook.exe；2) 路径被 GUI 重新打包清掉；3) Codex/Claude 不信任（首次触发需信任）。                                                                                  |
| **删除任务后复活** → hook 后续 SessionEnd/Stop 又加回来                   | `deleted_tasks.add(session_id)` 在 `_reduce_hook` 开头 if session\_id in deleted\_tasks 直接 return。                                                                   |
| **PermissionRequest 写回格式错误** → Claude/Codex 不识别              | Claude 需要 `hookSpecificOutput.decision.behavior = "allow" \| "deny"`；Codex 只需 `decision = "allow" \| "deny"`。已在 `_encode_permission_response` 分两路。                |
| **Android 屏幕亮度不生效**                                          | 必须在 Activity 的 window\.attributes.screenBrightness 赋值（非 Compose 层）。MainActivity.kt 通过 `onAgentBrightness` 回调实现；离开 Agent 页传 `null`（对应 `BRIGHTNESS_OVERRIDE_NONE`）。 |
| **WebSocket 升级被 HTTP 200 污染** → 移动端 OKHttp 报协议错              | `process_request` 必须先检查 `Upgrade: websocket`；是且 path==/ws 才 return None（允许升级）。                                                                                    |
| **RemoteScreen 超过 114KB 无法单次 Read**                          | 请用 Grep 定位函数 + 分段 Read offset/limit；或直接 VS Code 打开。                                                                                                               |
| **ArcadeAudio.kt 重复类冲突**（历史遗留）                               | 已删除。现在只有 `SoundEngine` + `ArcadeSounds` 各一份，与 AgentPager 对齐。                                                                                                      |
| **用量 refresh 太慢**                                            | CodexUsageLoader 可能阻塞，放到 `run_in_executor`（已在 `get_usage` 分支做）。                                                                                                   |
| **adb 未授权**                                                  | 手机开发者选项开启 USB 调试，并在首次连接点"始终允许"。                                                                                                                                   |

***

## 10. 未来常见变更指引

### 10.1 新增一个按钮到 Agent 页（如"导出任务日志"）

1. domain/Models.kt `enum ControlAction` 加 `EXPORT_LOGS`。
2. desktop/main.py `_handle_task_control` 加 `elif action == "export_logs": ...`。
3. Android RemoteScreen.kt AgentPage 里 `ControlAction.toWire()` 加分支（如果 wire 字段名不同）。
4. AgentGridScreen.kt TaskDetails 按钮区加 BigCap/CapButton + onControl 调。

### 10.2 新增一个 Hook 事件（如 AgentPager 的"ToolBilling"）

1. main.py `_reduce_hook` 的 `if ev == "..."` 加分支；
2. 若该事件会写 token/subagent：记得同步 `updatedAt` 并触发 `broadcast_tasks()`；
3. （可选）domain/Models.kt 加对应 activity 枚举 + lifecycle 判定 + ActivityPresentation/TaskRowSummary 显示。

### 10.3 Codex 配额变化（如新增"每月"窗口）

1. `codex_usage.py` 返回 window 的 list 加一条（key="monthly"/label/usedPercentage/remainingPercentage/windowMinutes/resetsAt）；
2. UsageDashboard 会自动渲染新增 window（因为它按 state.usage.windows 遍历），无需改 UI。

### 10.4 新增一种配对方式（蓝牙 / USB 配件）

1. 在 RemoteScreen.kt ConnectPage 增加 `WayButton` + `ConnWay` 枚举（已有 4 方式模板）；
2. desktop/launch/ 加 BAT；
3. MainActivity.kt `parsePairUrl` 新增 URL scheme；
4. desktop/main.py `VibeRemoteServer.__init__` 监听相应 host（例如 BLE proxy 转 WS）。

### 10.5 想把 v2.0 新代码回到 main 分支

```powershell
git checkout main
git merge v2.0
# 或 cherry-pick 5490feb
```

***

## 11. 关键文件速查链接（绝对路径）

- [desktop/app/main.py](file:///d:/AIData/traedata/vibe-remote/desktop/app/main.py) — 所有服务端逻辑

- [desktop/app/agent\_hook.py](file:///d:/AIData/traedata/vibe-remote/desktop/app/agent_hook.py) — Hook CLI（install/uninstall/hook）

- [desktop/app/codex\_usage.py](file:///d:/AIData/traedata/vibe-remote/desktop/app/codex_usage.py) — Codex 用量读取器

- [desktop/vibe\_agent\_hook.spec](file:///d:/AIData/traedata/vibe-remote/desktop/vibe_agent_hook.spec) — PyInstaller：Hook 打包位置约束

- [android/app/src/main/java/com/viberemote/mobile/MainActivity.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/MainActivity.kt) — 入口 + 权限 + 扫码 + 亮度

- [android/app/src/main/java/com/viberemote/mobile/net/RemoteClient.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/net/RemoteClient.kt) — 协议层

- [android/app/src/main/java/com/viberemote/mobile/net/Models.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/net/Models.kt) — wire 模型

- [android/app/src/main/java/com/viberemote/mobile/domain/Models.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/domain/Models.kt) — AgentPager 枚举模型

- [android/app/src/main/java/com/viberemote/mobile/domain/ModelMapping.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/domain/ModelMapping.kt) — net → domain 翻译

- [android/app/src/main/java/com/viberemote/mobile/domain/TaskDashboardProjection.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/domain/TaskDashboardProjection.kt) — 投影（排序/置顶/展开/自动仪表盘）

- [android/app/src/main/java/com/viberemote/mobile/ui/RemoteScreen.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/ui/RemoteScreen.kt) — 主 4 Tab + AgentPage

- [android/app/src/main/java/com/viberemote/mobile/ui/AgentGridScreen.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/ui/AgentGridScreen.kt) — AgentPager 任务页 UI

- [android/app/src/main/java/com/viberemote/mobile/ui/UsageDashboard.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/ui/UsageDashboard.kt) — 用量明细

- [android/app/src/main/java/com/viberemote/mobile/ui/AgentGridTheme.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/ui/AgentGridTheme.kt) — 像素主题

- [android/app/src/main/java/com/viberemote/mobile/SoundEngine.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/SoundEngine.kt) — 8-bit 音效播放

- [android/app/src/main/java/com/viberemote/mobile/ArcadeSounds.kt](file:///d:/AIData/traedata/vibe-remote/android/app/src/main/java/com/viberemote/mobile/ArcadeSounds.kt) — 8-bit 音符合成 + TaskSoundTracker

***

## 12. 本文件维护约定

- 每次新增/修改 §3/§4/§8/§9/§10 或关键文件路径，请同步更新本文件；

- 不要只写在 commit message 里，AI 无法跨仓库回溯 commit；

- **中文说明、精确行号引用、字段 JSON 示例**三种信息都保留（AI 强依赖示例）。

