"""Vibe Remote 电脑端服务（trae 风格控制台）。

单端口 8765：
  HTTP  : 托管 trae 风格管理面板（/）、配对控制台（/pair/console）、二维码
  WS    : /ws  配对 / 按键 / 语音流 / 按键映射管理 / 状态 / 语音命令日志

在 vibe-flow 基础上新增：
  - 按键映射管理（KeymapStore）：手机按键 ↔ 电脑快捷键，可在线编辑并推送
  - 语音方向命令（SpeechCommand）：系统中/英文语音识别，命中词注入按键并广播
  - 默认输入设备切换（AudioDefaultSwitch）：手机语音会话期间切到 CABLE Output
  - 数字键 1-4 支持按住连发

启动：
  python desktop\\app\\main.py [--port 8765] [--no-browser]

隐私：日志不打印配对 token、确认码、客户端 IP、语音内容。
"""

from __future__ import annotations

import argparse
import asyncio
import io
import ipaddress
import json
import logging
import mimetypes
import os
import secrets
import socket
import subprocess
import sys
import threading
import time
import webbrowser
from pathlib import Path
from typing import Optional

LOG = logging.getLogger("vibe-remote")

APP_DIR = Path(__file__).resolve().parent
# PyInstaller onedir/onefile：运行时会把资源解压到 sys._MEIPASS
if getattr(sys, "frozen", False):
    _MEI = Path(getattr(sys, "_MEIPASS", APP_DIR))
    # web 静态资源放在解包目录
    WEB_DIR = _MEI / "web"
    # 可编辑配置优先放在 exe 同级目录（便于用户修改），找不到再回落 _MEI/app
    _EXE_DIR = Path(sys.executable).resolve().parent
    CONFIG_PATH = _EXE_DIR / "vibe_config.json"
    KEYMAP_PATH = _EXE_DIR / "keymap.json"
    # onedir 里 APP_DIR 已是 _MEI/app 或 _EXE_DIR/_internal/app
    # 若 exe 同级没有，就从打包内 app/ 找（可选）
    for _fallback in (APP_DIR, _MEI / "app"):
        if not CONFIG_PATH.exists() and (_fallback / "vibe_config.json").exists():
            CONFIG_PATH = _fallback / "vibe_config.json"
        if not KEYMAP_PATH.exists() and (_fallback / "keymap.json").exists():
            KEYMAP_PATH = _fallback / "keymap.json"
    # DESKTOP_DIR 仅作为引用，不再依赖其位置
    DESKTOP_DIR = _EXE_DIR
else:
    DESKTOP_DIR = APP_DIR.parent
    WEB_DIR = DESKTOP_DIR / "web"
    CONFIG_PATH = APP_DIR / "vibe_config.json"
    KEYMAP_PATH = APP_DIR / "keymap.json"

# ---- websockets（legacy 稳定 API，版本固定为 12.0）----
try:
    import websockets
    from websockets.datastructures import Headers  # type: ignore
    from websockets.exceptions import ConnectionClosed  # type: ignore
    from websockets.http11 import Response  # type: ignore   # websockets 12.x 后 Response 移入 http11
    from websockets.legacy.server import serve as ws_serve  # type: ignore

    HAS_WS = True
except Exception:  # pragma: no cover
    HAS_WS = False

try:
    import qrcode  # type: ignore

    HAS_QR = True
except Exception:
    HAS_QR = False

from audio_default_switch import AudioDefaultSwitch, find_cable_output
from audio_sink import AudioSink
from key_injector import KeyInjector, VK
from keymap import ACTIONS, KeymapError, KeymapStore
from pairing import PairingManager
from status_watcher import StatusWatcher


def lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except OSError:
        try:
            ip = socket.gethostbyname_ex(socket.gethostname())[2][0]
        except Exception:
            ip = "127.0.0.1"
    finally:
        s.close()
    return ip


_CREATE_NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def all_local_ips() -> list:
    """列出本机所有活跃 IPv4（含热点虚拟网卡、USB 网络共享网段），供三种连接方式使用。"""
    ips: list = []
    try:
        out = subprocess.run(
            [
                "powershell", "-NoProfile", "-Command",
                "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*'"
                " -and $_.IPAddress -notlike '169.254.*' } | Select-Object -ExpandProperty IPAddress",
            ],
            capture_output=True,
            text=True,
            timeout=10,
            creationflags=_CREATE_NO_WINDOW,
        )
        for line in out.stdout.splitlines():
            line = line.strip()
            try:
                ipaddress.IPv4Address(line)
            except ValueError:
                continue
            if line not in ips:
                ips.append(line)
    except Exception:
        pass
    if not ips:
        ips = [lan_ip()]
    return ips


def run_adb_reverse(port: int) -> dict:
    """尝试 `adb reverse tcp:<port> tcp:<port>`：手机连 127.0.0.1:<port> 即直连电脑（USB 走线，零 WiFi）。"""
    try:
        ver = subprocess.run(
            ["adb", "version"],
            capture_output=True,
            text=True,
            timeout=10,
            creationflags=_CREATE_NO_WINDOW,
        )
        if ver.returncode != 0:
            return {"ok": False, "message": "未找到可用 adb（请安装 Android platform-tools 并开启 USB 调试）"}
    except FileNotFoundError:
        return {"ok": False, "message": "未找到 adb 命令（请安装 Android platform-tools 并开启 USB 调试）"}
    except Exception as e:
        return {"ok": False, "message": f"adb 检测失败：{e}"}
    try:
        r = subprocess.run(
            ["adb", "reverse", f"tcp:{port}", f"tcp:{port}"],
            capture_output=True,
            text=True,
            timeout=15,
            creationflags=_CREATE_NO_WINDOW,
        )
        if r.returncode == 0:
            return {"ok": True, "message": f"USB 直连已就绪：App 地址填 127.0.0.1:{port}"}
        err = (r.stderr or r.stdout or "").strip().splitlines()
        return {"ok": False, "message": f"adb reverse 失败：{err[-1] if err else '未知错误'}"}
    except Exception as e:
        return {"ok": False, "message": f"adb reverse 失败：{e}"}


def http_response(status: int, content_type: str, body: bytes) -> tuple:
    # websockets 12.x process_request 期望 (StatusLike, HeadersLike, bytes) 三元组
    return (
        status,
        Headers(
            [
                ("Content-Type", content_type),
                ("Content-Length", str(len(body))),
                ("Cache-Control", "no-store"),
            ]
        ),
        body,
    )


CONSOLE_HTML_PATH = WEB_DIR / "console.html"


class VibeRemoteServer:
    def __init__(self, config: dict, no_browser: bool = False):
        self.config = config
        self.no_browser = no_browser
        self.port = int(config.get("port", 8765))
        self.host = str(config.get("host", "0.0.0.0"))
        self.max_clients = int(config.get("max_clients", 2))
        self.long_press_ms = int(config.get("long_press_ms", 500))

        self.pairing = PairingManager(ttl_sec=int(config.get("token_ttl_sec", 600)))
        self.panel_token = secrets.token_hex(12)  # 本地管理面板自动授权凭据（仅 localhost 可见）
        self.injector = KeyInjector()
        self.keymap_store = KeymapStore(KEYMAP_PATH)
        self.audio_switch = AudioDefaultSwitch()
        self.audio = AudioSink(
            device_match=str(config.get("audio_device", "CABLE Input")),
            device_rate=int(config.get("sample_rate", 0)),
            provider=config.get("provider", {}),
            long_press_ms=self.long_press_ms,
            keys=self.injector,
        )
        # 授权会话：websocket -> {"id": str, "rate": int}
        self.authorized: dict = {}
        # 历史会话：id -> {"id": str, "seen_at": float, "last_ip": str}
        self.history_peers: dict = {}
        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.last_payload = {
            "status": {"state": "READY", "message": ""},
            "session": {"phase": "idle"},
            "level": 0.0,
        }
        self.speech: None = None
        self._rec_log: list = []  # 最近语音/日志（UI 拉取用）
        self.adb_status: dict = {"pending": True, "ok": False, "message": "检测中…"}

        status_file = str(config.get("status_file") or "").strip()
        if not status_file:
            status_file = str(Path.home() / ".vico" / "status.json")
        self.status_watcher = StatusWatcher(
            status_file,
            interval=float(config.get("status_poll_interval_s", 0.3)),
            on_change=self.on_status_change,
        )

    # ---------- 对外信息 ----------
    def pairing_url(self) -> str:
        ip = lan_ip()
        p = self.pairing.current()
        if p is None:
            p = self.pairing.new_pairing()
        return f"http://{ip}:{self.port}/?pair={p.token}"

    def pairing_info(self, is_local: bool = False) -> dict:
        p = self.pairing.current()
        if p is None:
            p = self.pairing.new_pairing()
        expires_in = max(0, int(p.expires_at - time.monotonic()))
        info = {
            "code": p.code,
            "expires_in": expires_in,
            "clients": len(self.authorized),
            "max_clients": self.max_clients,
            "pairing_url": f"http://{lan_ip()}:{self.port}/?pair={p.token}",
            "ips": all_local_ips(),
            "adb": self.adb_status,
        }
        if is_local:
            info["panel_token"] = self.panel_token
            info["peers"] = self._peers_snapshot()
        return info

    def _peers_snapshot(self) -> list:
        """已连接 + 历史会话的合并快照，供 UI 展示与重连。"""
        now = time.time()
        snapshot = []
        active_ids = {c["id"] for c in self.authorized.values() if c.get("id")}
        for ws, c in self.authorized.items():
            pid = c.get("id") or "?"
            ip = ""
            try:
                raddr = getattr(ws, "remote_address", None)
                if raddr and len(raddr) >= 1:
                    ip = str(raddr[0])
            except Exception:
                ip = ""
            snapshot.append({
                "id": pid,
                "name": c.get("name") or f"设备 {pid}",
                "online": True,
                "seen_at": now,
                "ip": ip,
            })
            self.history_peers[pid] = {"id": pid, "name": snapshot[-1]["name"], "seen_at": now, "last_ip": ip}
        for pid, h in list(self.history_peers.items()):
            if pid in active_ids:
                continue
            snapshot.append({
                "id": pid,
                "name": h.get("name") or f"设备 {pid}",
                "online": False,
                "seen_at": h.get("seen_at", 0),
                "ip": h.get("last_ip") or "",
            })
        # 在线的排前面，然后按最近见过来排
        snapshot.sort(key=lambda x: (0 if x["online"] else 1, -(x["seen_at"] or 0)))
        return snapshot

    # ---------- 事件回调（事件循环内） ----------
    def on_status_change(self, status: dict) -> None:
        self.last_payload["status"] = status
        self.broadcast({"type": "status", **status})

    def on_audio_event(self, event: dict) -> None:
        if event.get("type") == "session":
            self.last_payload["session"] = event
        elif event.get("type") == "level":
            self.last_payload["level"] = event.get("v", 0.0)
        self.broadcast(event)

    def on_voice_log(self, text: str) -> None:
        """语音日志占位：本端不再进行方向词识别（只保留按住说话→电脑文字识别）。"""
        self._push_rec(text)

    def on_voice_command(self, word: str, cmd: str, label: str) -> None:
        """语音命令占位：已停用方向命令。不再注入任何按键。"""
        self._push_rec(f"语音方向命令已停用：{label}")

    def _push_log(self, text: str) -> None:
        self.broadcast({"type": "log", "text": text})
        self._push_rec(text)

    def _push_rec(self, text: str) -> None:
        self._rec_log.append({"t": time.time(), "text": text})
        if len(self._rec_log) > 200:
            self._rec_log = self._rec_log[-200:]

    # ---------- 语音命令执行占位（已停用）----------
    def _apply_cmd(self, cmd: str) -> None:
        return None

    def on_voice_command(self, word: str, cmd: str, label: str) -> None:
        """语音命令占位：已停用方向命令。"""
        return None

    # ---------- 按键处理（按 keymap 解析键位与类型） ----------
    def _resolve(self, key_id: str):
        if not key_id:
            return None
        return next((k for k in self.keymap_store.document() if k["id"] == key_id), None)

    def handle_key(self, msg: dict) -> None:
        action = msg.get("action")
        key_id = str(msg.get("key") or "")
        kdef = self._resolve(key_id)
        mapping = kdef["mapping"] if kdef else key_id
        if isinstance(mapping, list):
            # 组合键不支持按住，一次性触发
            if action == "tap":
                try:
                    self.injector.chord(mapping[:-1], mapping[-1])
                except KeyError:
                    pass
            return
        if action == "down":
            if mapping in VK:
                self.injector.press(mapping)
        elif action == "up":
            if mapping in VK:
                self.injector.release(mapping)
        elif action == "tap":
            if kdef and kdef["type"] == "action":
                self._apply_action(mapping)
            elif mapping in VK:
                self.injector.tap(mapping)

    def _apply_action(self, name: str) -> None:
        name = str(name or "")
        if name == "enter":
            self.injector.tap("enter")
            return
        method = getattr(self.injector, ACTIONS.get(name), None) if name in ACTIONS else None
        if method is None:
            return
        method()

    def handle_action(self, msg: dict) -> None:
        self._apply_action(str(msg.get("name") or ""))

    # ---------- 鼠标控制（手机触控板） ----------
    def handle_mouse(self, msg: dict) -> None:
        action = str(msg.get("action") or "")
        try:
            if action == "move":
                self.injector.mouse_move_rel(int(msg.get("dx") or 0), int(msg.get("dy") or 0))
            elif action == "click":
                self.injector.mouse_click(str(msg.get("button") or "left"))
            elif action == "scroll":
                self.injector.mouse_scroll(int(msg.get("dy") or 0) if msg.get("dy") not in (None, "") else 1)
        except Exception as e:
            LOG.warning("mouse failed: %s", e)

    # ---------- 手机文字 → 电脑 ----------
    def handle_paste_text(self, msg: dict) -> None:
        text = str(msg.get("text") or "")
        if not text:
            return
        try:
            self.injector.paste_text(text)
            self._push_rec(f"[clipboard] len={len(text)}")
        except Exception as e:
            LOG.exception("paste_text failed: %s", e)
            self._push_rec(f"[clipboard] 粘贴失败：{e!r}")

    # ---------- 会话级音频切换 ----------
    def _run_blocking(self, fn, *args, **kwargs):
        """在 worker 线程里执行（不阻塞 event loop，也避免 winsdk / COM
        因“事件循环正在运行”而抛错）。"""
        import concurrent.futures
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as ex:
            return ex.submit(fn, *args, **kwargs).result()

    def handle_hold_start(self, msg: dict) -> None:
        rate = int(msg.get("rate") or 16000)
        self.audio.start(rate)
        # 非阻塞切默认输入到 CABLE Output（手机语音→识别器）
        # 注意：不能阻塞事件循环，否则手机发的 PCM 帧会全部延迟到达
        if self.config.get("voice_source") == "cable":
            threading.Thread(target=self._do_switch_to_cable, daemon=True).start()

    def _do_switch_to_cable(self) -> None:
        try:
            cable = find_cable_output()
            if cable:
                ok = self.audio_switch.switch_to_cable()
                LOG.info("switch_to_cable: cable_id=%s ok=%s", cable[:30] if cable else None, ok)
                if ok:
                    self._push_rec("已将默认输入切到 CABLE Output")
                else:
                    self._push_rec("切 CABLE Output 失败，请检查 VB-CABLE")
            else:
                LOG.warning("find_cable_output returned None - VB-CABLE 未安装？")
                self._push_rec("未找到 CABLE Output 设备，请安装 VB-CABLE")
        except Exception as e:
            LOG.warning("switch input failed: %s", e)

    def handle_hold_end(self) -> None:
        """点击结束：1) 停止往 CABLE 推 PCM；2) 再按一次语音快捷键结束；3) 还原默认输入。
        非阻塞，避免事件循环被卡住导致客户端收不到 session/idle 回执。"""
        try:
            self.audio.stop()
        except Exception as e:
            LOG.warning("audio.stop failed: %s", e)
        # 非阻塞恢复默认输入设备
        if self.config.get("voice_source") == "cable":
            threading.Thread(target=self._do_restore_audio, daemon=True).start()

    def _do_restore_audio(self) -> None:
        try:
            ok = self.audio_switch.restore_default()
            LOG.info("restore_default ok=%s", ok)
        except Exception as e:
            LOG.warning("restore default input failed: %s", e)

    # ---------- WS ----------
    def broadcast(self, message: dict) -> None:
        if self.loop is None:
            return
        payload = json.dumps(message, ensure_ascii=False)
        for ws in list(self.authorized.keys()):
            asyncio.ensure_future(self._safe_send(ws, payload))

    def broadcast_peer_info(self) -> None:
        """把当前会话列表同步到所有已授权端（包括管理面板浏览器）。"""
        peers = self._peers_snapshot()
        self.broadcast({
            "type": "peer_info",
            "clients": len(self.authorized),
            "max_clients": self.max_clients,
            "peers": peers,
        })

    async def _safe_send(self, ws, payload: str) -> None:
        try:
            await ws.send(payload)
        except Exception:
            self._drop(ws)

    async def handle_ws(self, websocket, path: str):
        # websockets 12.x legacy：签名 (websocket, path)
        if path != "/ws":
            await websocket.close(code=1008, reason="not_found")
            return
        client: dict = {"id": None, "rate": 16000}
        try:
            await self._send_json(websocket, {"type": "snapshot", **self.last_payload})
            await self._send_json(websocket, {"type": "keymap", "keys": self.keymap_store.document()})
            async for message in websocket:
                if isinstance(message, bytes):
                    if client["id"] is not None:
                        self.audio.feed_pcm(message, client["rate"])
                else:
                    await self._on_text(client, websocket, message)
        except ConnectionClosed:
            pass
        finally:
            self._drop(websocket)

    async def _send_json(self, websocket, payload: dict) -> None:
        try:
            await websocket.send(json.dumps(payload, ensure_ascii=False))
        except Exception:
            pass

    async def _on_text(self, client: dict, websocket, raw: str) -> None:
        try:
            msg = json.loads(raw)
        except json.JSONDecodeError:
            return
        kind = msg.get("type")

        if kind == "hello":
            token = str(msg.get("token") or "")
            # 本地管理面板自动授权
            if token and token == self.panel_token:
                client["id"] = "panel"
                self.authorized[websocket] = client
                await self._send_json(websocket, {"type": "paired", "id": "panel", "max_clients": self.max_clients})
                return
            # 当 token 为空（例如 USB 直连默认 127.0.0.1 不带 ?pair=），
            # 服务端即时生成一个新 pairing 并把 pair_info 返回给客户端，
            # 让 App 可以立刻弹出「输入确认码」对话框，而不是停在连接页。
            if not token:
                self.pairing.new_pairing()
                info = self.pairing_info()
                # 为了把 token 传回给 App（它会在 confirm 时一起带回来），塞到 pending_token
                cur = self.pairing.current()
                token_back = cur.token if cur else ""
                client["pending_token"] = token_back
                await self._send_json(
                    websocket,
                    {
                        "type": "pair_info",
                        "token": token_back,
                        "confirm_code": info["code"],
                        "expires_in": info["expires_in"],
                    },
                )
                return
            if not self.pairing.validate_token(token):
                await self._send_json(websocket, {"type": "error", "code": "token_invalid"})
                return
            client["pending_token"] = token
            info = self.pairing_info()
            await self._send_json(
                websocket,
                {"type": "pair_info", "token": token, "confirm_code": info["code"], "expires_in": info["expires_in"]},
            )
            return

        if kind == "confirm":
            token = client.get("pending_token") or ""
            code = str(msg.get("code") or "")
            paired = self.pairing.consume(token, code)
            if paired is None:
                await self._send_json(websocket, {"type": "error", "code": "confirm_failed"})
                return
            if len(self.authorized) >= self.max_clients and websocket not in self.authorized:
                oldest = next(iter(self.authorized))
                try:
                    await self._send_json(oldest, {"type": "error", "code": "replaced", "message": "被新设备接管"})
                    await oldest.close()
                except Exception:
                    pass
                self._drop(oldest)
            client["id"] = self.pairing.new_session_id()
            self.authorized[websocket] = client
            await self._send_json(
                websocket,
                {"type": "paired", "id": client["id"], "max_clients": self.max_clients},
            )
            if self.loop:
                self.loop.call_soon_threadsafe(self.broadcast_peer_info)
            LOG.info("paired clients=%s/%s", len(self.authorized), self.max_clients)
            return

        if client["id"] is None:
            # 未配对：仅允许 keymap 面板（管理面板自身浏览器连入，同样需配对）
            return

        if kind == "key":
            self.handle_key(msg)
        elif kind == "action":
            self.handle_action(msg)
        elif kind == "mouse":
            self.handle_mouse(msg)
        elif kind == "paste_text":
            self.handle_paste_text(msg)
        elif kind == "hold_start":
            client["rate"] = int(msg.get("rate") or 16000)
            self.handle_hold_start(msg)
        elif kind == "hold_end":
            self.handle_hold_end()
        elif kind == "get_keymap":
            await self._send_json(websocket, {"type": "keymap", "keys": self.keymap_store.document()})
        elif kind == "set_keymap":
            await self._set_keymap(websocket, msg)
        elif kind == "keymap_reset":
            self.keymap_store.reset()
            await self._broadcast_keymap()
        elif kind == "get_logs":
            await self._send_json(websocket, {"type": "logs", "logs": self._rec_log})
        elif kind == "delete_peer":
            pid = str(msg.get("id") or "")
            if pid and pid in self.history_peers:
                del self.history_peers[pid]
                self._push_rec(f"已删除设备记录 {pid}")
            if self.loop:
                self.loop.call_soon_threadsafe(self.broadcast_peer_info)
        elif kind == "disconnect_peer":
            # 断开指定设备（按 id 找对应的 ws 并关闭）
            pid = str(msg.get("id") or "")
            for ws, c in list(self.authorized.items()):
                if c.get("id") == pid:
                    try:
                        await self._send_json(ws, {"type": "error", "code": "replaced", "message": "被移除"})
                        await ws.close()
                    except Exception:
                        pass
                    self._drop(ws)
                    break
        elif kind == "voice_toggle":
            return None
        elif kind == "input_restore":
            # 手动恢复：切回默认（提示用户手动选择，避免误切其他设备）
            self._push_rec("如需恢复默认麦克风，请在 设置→系统→声音 中手动选择输入设备")

    async def _set_keymap(self, websocket, msg: dict) -> None:
        key_id = str(msg.get("key_id") or "")
        try:
            updated = self.keymap_store.set_mapping(
                key_id,
                msg.get("mapping"),
                label=msg.get("label"),
            )
        except KeymapError as e:
            await self._send_json(websocket, {"type": "error", "code": "keymap_error", "message": str(e)})
            return
        self._push_rec(f"键位更新 {key_id} → {json.dumps(updated.get('mapping'), ensure_ascii=False)}")
        await self._broadcast_keymap()

    async def _broadcast_keymap(self) -> None:
        doc = self.keymap_store.document()
        payload = json.dumps({"type": "keymap", "keys": doc}, ensure_ascii=False)
        for ws in list(self.authorized.keys()):
            asyncio.ensure_future(self._safe_send(ws, payload))

    def _drop(self, websocket) -> None:
        if websocket in self.authorized:
            del self.authorized[websocket]
            self.broadcast_peer_info()

    # ---------- HTTP ----------
    async def process_request(self, path: str, request_headers) -> Optional[tuple]:
        raw_path = path
        query_start = path.find("?")
        path = path if query_start < 0 else path[:query_start]

        # —— 修复 USB 直连升级失败：客户端对 /ws 发起普通 HTTP（比如 OKHttp 探活 / 预检）
        #    时，websockets 12 会把 process_request 返回的 (200 OK, static body) 当作
        #    "reject WS upgrade with 200" 打印。这里显式判定 Upgrade: websocket：
        #      • 有 Upgrade 且 path != /ws  →  走静态（非 /ws 就不该升级）
        #      • 有 Upgrade 且 path == /ws  →  返回 None，交给库执行升级握手
        #      • 无 Upgrade                   →  无论路径都按 HTTP 处理
        upgrade = str(request_headers.get("Upgrade", "") or "").lower()
        if upgrade == "websocket":
            if path == "/ws":
                return None  # 允许升级
            # 客户端把 WebSocket 请求发到了错误路径，明确拒绝而非 200
            return http_response(400, "text/plain; charset=utf-8",
                                 b"WS upgrade rejected: wrong path. Use ws://host:port/ws")

        if path == "/ws":
            # 普通 HTTP GET /ws：返回一个简短文本，方便手工探测 + 避免误报 200 OK
            return http_response(200, "text/plain; charset=utf-8",
                                 b"vibe-remote /ws (HTTP probe ok; WebSocket upgrade requires Upgrade: websocket header)")

        if path == "/pair/console":
            return self._console_page()
        if path == "/pair/info":
            host = str(request_headers.get("Host", "")).split(":")[0].lower()
            is_local = host in ("localhost", "127.0.0.1", "::1", "[::1]", "")
            return http_response(200, "application/json; charset=utf-8", json.dumps(self.pairing_info(is_local)).encode("utf-8"))
        if path == "/pair/new":
            self.pairing.new_pairing()
            return http_response(200, "application/json; charset=utf-8", json.dumps(self.pairing_info()).encode("utf-8"))
        if path == "/pair/qr.png":
            return self._qr_response()

        if path == "/":
            path = "/index.html"
        return self._static(path)

    def _console_page(self) -> tuple:
        body = CONSOLE_HTML_PATH.read_bytes() if CONSOLE_HTML_PATH.exists() else b""
        if not body:
            body = "<html><body>配对控制台缺失：desktop/web/console.html</body></html>".encode("utf-8")
        return http_response(200, "text/html; charset=utf-8", body)

    def _qr_response(self) -> tuple:
        if not HAS_QR:
            return http_response(
                503,
                "text/plain; charset=utf-8",
                "qrcode 库未安装，请在电脑端查看确认码配对".encode("utf-8"),
            )
        url = self.pairing_url()
        img = qrcode.make(url, box_size=10, border=2)
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        return http_response(200, "image/png", buf.getvalue())

    def _static(self, path: str) -> tuple:
        safe = Path(path.lstrip("/"))
        candidate = (WEB_DIR / safe).resolve()
        if not str(candidate).startswith(str(WEB_DIR.resolve())) or not candidate.is_file():
            return http_response(404, "text/plain; charset=utf-8", b"not found")
        ctype = mimetypes.guess_type(candidate.name)[0] or "application/octet-stream"
        if ctype.startswith("text/") or ctype in ("application/json", "application/javascript", "application/manifest+json"):
            ctype += "; charset=utf-8"
        return http_response(200, ctype, candidate.read_bytes())

    # ---------- 生命周期 ----------
    def _start_speech(self) -> None:
        """已停用：电脑端不再启动本地语音方向识别（winsdk/SpeechCommand）。

        现在的语音链路是：手机按住说话 → PCM → 电脑 CABLE Input → 微信输入法文字识别。
        """
        self._push_rec("电脑端语音方向命令已停用（仅保留手机按住→微信输入法文字识别）")

    async def _get_admin_page(self) -> None:
        pass

    def _setup_adb(self) -> None:
        """后台执行 adb reverse（不阻塞启动）。"""
        self.adb_status = run_adb_reverse(self.port)
        LOG.info("adb reverse: %s", self.adb_status.get("message"))

    async def run(self) -> None:
        self.loop = asyncio.get_running_loop()
        self.audio.on_event = lambda ev: self.loop.call_soon_threadsafe(self.on_audio_event, ev)
        self.audio.start_thread()
        self._start_speech()
        threading.Thread(target=self._setup_adb, daemon=True).start()

        if not HAS_WS:
            print("错误：缺少 websockets 依赖，请先执行 pip install -r desktop\\requirements.txt")
            sys.exit(1)

        self.pairing_info()

        async with ws_serve(
            self.handle_ws,
            self.host,
            self.port,
            process_request=self.process_request,
            max_size=2**20,  # 1MB 单帧上限
        ):
            banner_lines = [
                "=" * 56,
                "  Vibe Remote desktop server is running",
                f"  Pair console : http://localhost:{self.port}/pair/console",
                f"  Admin panel  : http://localhost:{self.port}/",
                "  Local IPs (WiFi / Hotspot / USB-tether all reachable):",
            ]
            for ip in all_local_ips():
                banner_lines.append(f"    http://{ip}:{self.port}/")
            banner_lines.append("  USB direct (adb reverse auto-config) : 127.0.0.1:%d" % self.port)
            banner_lines.append("=" * 56)
            for line in banner_lines:
                print(line, flush=True)
                LOG.info("%s", line)
            if not self.no_browser:
                threading.Timer(0.6, lambda: webbrowser.open(f"http://localhost:{self.port}/")).start()
            await self.status_watcher.run()


def load_config(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError:
        return {}


def main() -> None:
    parser = argparse.ArgumentParser(description="Vibe Remote desktop server")
    parser.add_argument("--port", type=int, default=None, help="listen port (default 8765 or read from config)")
    parser.add_argument("--no-browser", action="store_true", help="do not auto-open the pair console")
    parser.add_argument("--gui", action="store_true", help="run in GUI mode (pywebview window, close to exit)")
    parser.add_argument("--verbose", action="store_true", help="enable debug logging")
    args = parser.parse_args()

    log_level = logging.DEBUG if args.verbose else logging.INFO
    # Always print startup banner & errors to console + log file next to EXE
    log_handlers = [logging.StreamHandler(sys.stdout)]
    try:
        if getattr(sys, "frozen", False):
            log_dir = Path(sys.executable).resolve().parent
        else:
            log_dir = Path(__file__).resolve().parent
        log_dir.mkdir(parents=True, exist_ok=True)
        log_path = log_dir / "vibe-remote.log"
        log_handlers.append(logging.FileHandler(str(log_path), encoding="utf-8"))
    except Exception:
        log_path = None
    logging.basicConfig(
        level=log_level,
        format="%(asctime)s %(levelname)s %(message)s",
        handlers=log_handlers,
        force=True,
    )
    if log_path:
        LOG.info("log file: %s", log_path)

    try:
        config = load_config(CONFIG_PATH)
    except Exception as exc:
        LOG.exception("failed to load config %s: %s", CONFIG_PATH, exc)
        config = {}
    if args.port:
        config["port"] = args.port

    try:
        # GUI 模式：不自动开浏览器（由 pywebview 窗口替代）
        server = VibeRemoteServer(config, no_browser=args.no_browser or args.gui)
    except Exception as exc:
        LOG.exception("failed to build server: %s", exc)
        print(f"[INIT ERROR] {exc}")
        sys.exit(2)

    if args.gui:
        # GUI 模式：server 跑在子线程，主线程跑 pywebview 窗口
        run_gui_mode(server, config)
        return

    try:
        asyncio.run(server.run())
    except KeyboardInterrupt:
        pass
    except OSError as exc:
        # common: port already in use
        LOG.exception("server failed: %s", exc)
        print(f"[SERVER ERROR] {exc} (is port {config.get('port',8765)} already in use?)")
        sys.exit(3)
    except Exception as exc:
        LOG.exception("server crashed: %s", exc)
        print(f"[FATAL] {exc}")
        sys.exit(4)


def run_gui_mode(server: "VibeRemoteServer", config: dict) -> None:
    """GUI 模式：server 在子线程跑 asyncio，主线程跑 pywebview 窗口 + pystray 托盘。
    关闭窗口 = 最小化到托盘；托盘"退出" = 退出整个程序。
    """
    try:
        import webview
    except ImportError:
        LOG.error("pywebview not installed, fallback to console mode")
        print("[GUI] pywebview not installed, fallback to console mode")
        asyncio.run(server.run())
        return

    port = int(config.get("port", 8765))

    # server 在 daemon 子线程运行（主线程退出时自动结束）
    server_thread = threading.Thread(
        target=lambda: asyncio.run(server.run()),
        daemon=True,
        name="vibe-server",
    )
    server_thread.start()

    # 等 server 起来（绑定端口）
    for _ in range(40):  # 最多等 4 秒
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                break
        except OSError:
            time.sleep(0.1)

    # 生成托盘图标（PIL，琥珀色圆 + V）
    def _make_icon():
        try:
            from PIL import Image, ImageDraw
            img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            draw = ImageDraw.Draw(img)
            draw.ellipse([4, 4, 60, 60], fill=(253, 186, 74, 255))
            draw.text((24, 12), "V", fill=(5, 7, 11, 255))
            return img
        except Exception:
            return None

    # 托盘引用（供窗口事件回调使用）
    tray = {"icon": None, "window": None}

    # 强制退出函数（托盘"退出"或 pywebview 彻底退出时调用）
    def _force_exit():
        LOG.info("exiting vibe-remote GUI")
        try:
            if tray["icon"]:
                tray["icon"].stop()
        except Exception:
            pass
        os._exit(0)

    # 托盘菜单：显示窗口 / 退出
    def _on_show(icon, item):
        try:
            if tray["window"]:
                tray["window"].show()
                tray["window"].restore()
        except Exception as e:
            LOG.warning("restore window failed: %s", e)

    def _on_exit(icon, item):
        _force_exit()

    # 创建托盘图标（在子线程运行，pystray 有自己的消息循环）
    def _run_tray():
        try:
            import pystray
            icon_img = _make_icon()
            menu = pystray.Menu(
                pystray.MenuItem("显示窗口", _on_show, default=True),
                pystray.MenuItem("退出 Vibe Remote", _on_exit),
            )
            icon = pystray.Icon("vibe-remote", icon_img, "Vibe Remote", menu)
            tray["icon"] = icon
            icon.run()
        except Exception as exc:
            LOG.exception("pystray failed: %s", exc)

    tray_thread = threading.Thread(target=_run_tray, daemon=True, name="vibe-tray")
    tray_thread.start()

    # pywebview 窗口（主线程）
    LOG.info("starting pywebview window for http://localhost:%s/pair/console", port)
    try:
        window = webview.create_window(
            title="Vibe Remote",
            url=f"http://localhost:{port}/pair/console",
            width=960,
            height=720,
            min_size=(640, 480),
        )
        tray["window"] = window

        # 关闭按钮 = 隐藏到托盘（不退出）
        def _on_closing():
            LOG.info("window closing -> hide to tray")
            try:
                window.hide()
            except Exception:
                pass
            return False  # 阻止真正关闭

        window.events.closing += _on_closing

        # pywebview 6.x: start() 默认阻塞
        # 如果 pywebview 的事件循环结束（如所有窗口被销毁），才退出
        webview.start()
        # 走到这里意味着 pywebview 被强制结束（如系统关机）
        _force_exit()
    except Exception as exc:
        LOG.exception("pywebview failed: %s", exc)
        print(f"[GUI ERROR] {exc}")
        os._exit(4)


if __name__ == "__main__":
    main()