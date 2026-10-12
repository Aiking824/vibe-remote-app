# Vibe Remote

> Android 手机 + Windows 电脑的远程遥控系统
>
> 把手机变成电脑的遥控器：按键、鼠标、语音输入、AI Agent 状态监控，一个 App 全搞定。

## 功能特性

### 遥控按键
- **方向键 + 确认**：上下左右 + Enter，操控电脑焦点应用
- **功能键**：Ctrl+Enter（终极确认）、Shift+Enter（换行）、Win+D（桌面）、Win+Tab（任务视图）、Win+Shift+S（区域截图）、退格、取消
- **剪贴板**：复制（Ctrl+C）、剪切（Ctrl+X）、粘贴（Ctrl+V）
- **系统快捷键**：Win+D（显示桌面）、Alt+Tab（切换窗口）、Win+L（锁屏）
- **数字键**：1-4 快速输入
- **语音**：左Shift+左Ctrl+左Alt 组合键，触发语音输入工具

### 触控板鼠标
- 手机屏幕当触控板，移动光标
- 左键 / 中键 / 右键点击
- 滚轮滚动

### 语音输入
- 手机麦克风实时录音（16kHz PCM）
- 流式传输到电脑，播放到虚拟声卡（VB-CABLE）
- 语音转文字工具从虚拟麦克风读取，识别成文字写入输入框

### AI Agent 状态监控
- 接入 **Claude Code** 和 **Codex CLI** 的 Hook 事件
- 实时显示任务状态：READY / THINKING / WORKING / CONFIRM / WAITING / DONE / ERROR
- 手机端可审批权限、回答问题、中断任务、查看用量明细

### 按键映射在线编辑
- 电脑端 KEYMAP 页面可视化编辑任意按键的组合键映射
- 保存后实时推送到手机端，无需重新打包

## 技术栈

| 端 | 技术 |
|---|---|
| 手机端 | Kotlin + Jetpack Compose + OkHttp WebSocket |
| 电脑端 | Python + PySide6（GUI）+ websockets（WebSocket 服务）|
| 按键注入 | ctypes.windll.user32（keybd_event + SendInput）|
| 音频传输 | PCM16 16kHz 流式传输 + sounddevice 播放 |
| Agent Hook | Python 接收器，对接 Claude Code / Codex CLI 生命周期事件 |

## 目录结构

```
vibe-remote/
├─ android/                       # Android 端（Jetpack Compose）
│   └─ app/src/main/java/com/viberemote/mobile/
│       ├─ MainActivity.kt        # 入口：权限、扫码、亮度回调
│       ├─ net/RemoteClient.kt    # OkHttp WebSocket 客户端
│       ├─ audio/MicPcmRecorder.kt # 按住说话 PCM 录制
│       ├─ domain/                # AgentPager 领域层（任务投影/枚举模型）
│       └─ ui/RemoteScreen.kt     # 主界面：遥控/鼠标/快捷键/Agent
├─ desktop/                       # 电脑端后端（Python）
│   └─ app/
│       ├─ main.py                # WebSocket 服务（端口 8765）
│       ├─ key_injector.py         # 按键/鼠标/剪贴板注入
│       ├─ pairing.py             # 配对（token + 确认码）
│       ├─ audio_sink.py          # PCM 接收 → 重采样 → 播放
│       ├─ status_watcher.py      # Agent 状态监听
│       └─ agent_hook.py           # Hook 接收器
├─ desktop_v2/                    # 电脑端 GUI（PySide6）
│   ├─ main.py                    # 入口：单例 + 托盘 + Runtime
│   ├─ main_window.py            # 主窗口：KEYMAP 编辑 + Hook 管理
│   ├─ runtime.py                 # 后台服务整合
│   ├─ theme.py                   # 主题/调色板
│   └─ vibe_remote_v2.spec        # PyInstaller 打包配置
└─ DEV_LOG.md                     # 开发日志
```

## 快速开始

### 运行环境

**电脑端（Windows）**
- Windows 10 / 11（64 位）
- 使用打包好的 exe 无需安装 Python
- 局域网（Wi-Fi 或 USB 网络共享）
- VB-CABLE 虚拟声卡（语音输入功能需要，[官网下载](https://vb-audio.com/Cable/)）
- 语音转文字工具（微信输入法 / Typeless / 豆包 / Win+H 之一）

**手机端（Android）**
- Android 8.0（API 26）及以上
- 麦克风、相机、网络权限

### 安装运行

1. **电脑端**：双击 `启动.bat`（或运行 `VibeRemoteBridge.exe`）
   - 程序显示主窗口 + 系统托盘图标
   - 关闭主窗口 = 最小化到托盘；托盘右键"退出" = 完全退出

2. **手机端**：安装 `app-debug.apk`（允许"安装未知来源应用"）

3. **配对连接**（电脑端启动后，手机端 App 内选择）：

| 模式 | 适用场景 | 操作 |
|---|---|---|
| 同 Wi-Fi | 手机和电脑连同一 Wi-Fi | 扫电脑端二维码或输入配对 URL |
| USB 直连 | 有 USB 数据线 | 手机连电脑，App 选"USB 直连" |
| USB 网络共享 | 无 Wi-Fi | 手机开启 USB 网络共享 |

配对时核对两位确认码（防中间人），确认后建立 WebSocket 长连接。

## 配置说明

### vibe_config.json（程序主配置）

```json
{
  "port": 8765,
  "host": "0.0.0.0",
  "max_clients": 2,
  "audio_device": "CABLE Input",
  "voice_cmd": false,
  "voice_source": "cable",
  "provider": {
    "shortcut": ["ctrl", "shift", "win"],
    "mode": "toggle"
  }
}
```

| 字段 | 说明 |
|---|---|
| port | WebSocket 服务端口 |
| host | 监听地址（0.0.0.0 = 所有网卡）|
| max_clients | 最大连接数 |
| audio_device | 虚拟声卡设备名（语音输入用）|
| voice_source | 语音来源（cable = 虚拟声卡）|
| provider.shortcut | 语音提供者快捷键 |
| provider.mode | 触发模式（toggle = 切换）|

### keymap.json（按键映射配置）

定义手机端每个按键对应的电脑端组合键。可在电脑端 KEYMAP 页面在线编辑。

```json
{
  "id": "copy",
  "label": "复制",
  "display": "Ctrl+C",
  "group": "clip",
  "type": "action",
  "mapping": "copy"
}
```

| 分组 | 内容 | 按键数 |
|---|---|---|
| dpad | 方向键（上下左右 + 确认）| 5 |
| fn | 功能键（Ctrl+Enter / 桌面 / 任务视图 / 截图 / 退格 / 取消）| 7 |
| clip | 剪贴板（复制 / 剪切 / 粘贴）| 3 |
| hot | 系统快捷键（Win+D / Alt+Tab / Win+L）| 3 |
| num | 数字键 1-4 | 4 |
| voice | 语音（LShift+LCtrl+LAlt）| 1 |

### Hook 配置（Agent 状态监控）

`vibe_agent_hook.exe` 接收 Claude Code / Codex CLI 的生命周期事件。

电脑端主程序内置 Hook 管理界面（CODEX / CLAUDE 两个 Tab），可一键安装/卸载 Hook，自动创建版本备份。

**Claude Code**：配置合并到 `~/.claude/settings.json`

**Codex CLI**：配置合并到 `~/.codex/hooks.json`

## 从源码构建

### 电脑端（PySide6 GUI）

```bash
# 安装依赖
pip install -r desktop_v2/requirements.txt

# 开发运行
python desktop_v2/main.py

# 打包 exe
python -m PyInstaller desktop_v2/vibe_remote_v2.spec --noconfirm
```

产物：`desktop_v2/dist/VibeRemoteBridge.exe`

### 手机端（Android）

```bash
# 环境要求：JDK 17 + Android SDK (compileSdk 34, minSdk 26)

# 构建 APK
cd android
./gradlew.bat assembleDebug
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`

## 技术架构

```
┌──────────────┐    WebSocket (JSON)    ┌──────────────────┐
│  Android App  │ ←──────────────────→ │  Windows 桌面端   │
│  (Kotlin)     │                       │  (Python)         │
│               │  JSON 协议消息：       │                   │
│  RemoteScreen │  • key: 按键事件      │  VibeRemoteServer │
│   ├ 遥控      │  • mouse: 鼠标事件    │   ├ WebSocket 8765│
│   ├ 鼠标      │  • audio: 音频流      │   ├ Hook TCP 49361│
│   ├ 快捷键    │  • pair: 配对请求     │   ├ Keymap 编辑   │
│   └ Agent     │  • status: 状态快照   │   └ AudioSink     │
│               │                       │                   │
│  RemoteClient │  PCM16 16kHz 音频     │  key_injector      │
│  (OkHttp WS)  │ ←──────────────────  │  (ctypes user32)  │
│               │                       │                   │
│  MicPcmRecorder│                      │  PySide6 GUI       │
│  (16kHz PCM)  │                       │  + 系统托盘        │
└──────────────┘                       └──────────────────┘
                                         CABLE Input → 语音转文字
```

**通信协议**：JSON over WebSocket，双向实时。

**按键注入**：`ctypes.windll.user32` 调用 Windows API（`keybd_event` + `SendInput`），零额外依赖。组合键按顺序按下修饰键 → 主键 → 释放。

**语音链路**：手机麦克风 → 16kHz PCM 录制 → WebSocket 流式传输 → 电脑接收 → 重采样 → 播放到 VB-CABLE Input → 语音转文字工具从 CABLE Output 读取 → 识别成文字写入输入框。

## 常见问题

**Q：配对时显示"确认码不匹配"？**
A：检查电脑端和手机端的两位确认码是否一致。不一致请重新配对。

**Q：语音输入没有声音？**
A：检查 `vibe_config.json` 的 `audio_device` 是否为 `CABLE Input`；确认已安装 VB-CABLE；确认语音转文字工具的麦克风设为 `CABLE Output`。

**Q：Win 键组合键无效？**
A：某些系统策略会拦截 Win 键。检查组策略 `DisableWinKeys` 是否为 1；尝试以管理员身份运行。

**Q：Agent 状态不更新？**
A：检查 `vibe_agent_hook.exe` 路径是否已配置到 `~/.claude/settings.json` 和 `~/.codex/hooks.json`。电脑端 Hook 管理页面可一键安装。

**Q：手机连接后频繁断开？**
A：检查网络稳定性；`vibe_config.json` 的 `max_clients` 默认 2，超过会拒绝新连接。

## 开发日志

详细架构、数据流、关键约束见 [DEV_LOG.md](DEV_LOG.md)。

## License

MIT
