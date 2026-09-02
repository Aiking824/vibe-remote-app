"""Vibe Remote desktop_v2 后台 Runtime。

复用 desktop/app/main.py 的 VibeRemoteServer（WebSocket 8765 + Hook TCP 49361 + Keymap + AudioDefaultSwitch + StatusWatcher + PairingManager），通过 QThread 后台运行，用 Qt Signal 把状态推送到 UI 主线程。

整合策略（最小侵入）：
  1) importlib 加载 desktop/app/main.py 作为独立模块
  2) 实例化 VibeRemoteServer(config)
  3) 子线程跑 asyncio.run(server.run())
  4) 主线程 QTimer 轮询 server 状态，emit 信号给 UI

注意：desktop_v2/main.py 已经把 desktop/app/ 加到 sys.path，所以内部模块（pairing/keymap/audio_default_switch 等）能互相 import。
"""
from __future__ import annotations

import asyncio
import importlib.util
import io
import logging
import socket
import sys
import threading
import time
from pathlib import Path
from typing import Any, Optional

from PySide6.QtCore import QObject, QTimer, Qt, Signal, Slot
from PySide6.QtGui import QPixmap, QImage

LOG = logging.getLogger("vibe-remote-v2.runtime")

if getattr(sys, "frozen", False):
    # PyInstaller frozen 模式：desktop/app 打包在 _MEIPASS/desktop/app/
    HERE = Path(sys._MEIPASS)  # type: ignore[attr-defined]
    DESKTOP_APP_DIR = HERE / "desktop" / "app"
else:
    HERE = Path(__file__).resolve().parent
    DESKTOP_APP_DIR = HERE.parent / "desktop" / "app"


def _load_desktop_main_module():
    """加载 desktop/app/main.py 作为独立模块（避免与 desktop_v2/main.py 模块同名冲突）。"""
    if str(DESKTOP_APP_DIR) not in sys.path:
        sys.path.insert(0, str(DESKTOP_APP_DIR))
    spec = importlib.util.spec_from_file_location(
        "vibe_desktop_app_main", str(DESKTOP_APP_DIR / "main.py")
    )
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


# 模块级单例：仅加载一次
_desktop_main = _load_desktop_main_module()
VibeRemoteServer = _desktop_main.VibeRemoteServer
load_config = _desktop_main.load_config
CONFIG_PATH = _desktop_main.CONFIG_PATH
KEYMAP_PATH = _desktop_main.KEYMAP_PATH

# 复用 agent_hook 的安装/卸载入口（子进程调用 vibe_agent_hook.exe --install）
try:
    from agent_hook import cmd_install, cmd_uninstall  # type: ignore
    _HAS_HOOK_INSTALLER = True
except Exception:
    _HAS_HOOK_INSTALLER = False


def _bytes_to_pixmap(data: bytes) -> QPixmap:
    """从 PNG/JPEG 字节流构造 QPixmap。"""
    if not data:
        return QPixmap()
    img = QImage()
    img.loadFromData(data, "PNG")
    if img.isNull():
        # 尝试不指定格式
        img.loadFromData(data)
    return QPixmap.fromImage(img)


class RuntimeService(QObject):
    """后台服务包装：把 VibeRemoteServer 的状态通过 Qt 信号暴露给 UI。"""

    # ====== 信号（UI 监听） ======
    service_status_changed = Signal(str)
    phone_count_changed = Signal(int)
    pairing_text_changed = Signal(str)
    qr_pixmap_changed = Signal(object)  # QPixmap
    networks_changed = Signal(list, str)  # items, selected
    last_error_changed = Signal(str)
    hook_event_arrived = Signal(str, str, str)  # source, event_name, summary
    keymap_changed = Signal(list)  # list[dict]
    codex_hook_status_changed = Signal(str)
    claude_hook_status_changed = Signal(str)
    codex_last_event_changed = Signal(str)
    claude_last_event_changed = Signal(str)
    backups_changed = Signal(str)  # source — 安装/卸载/删除备份后触发，UI 据此刷新备份列表

    def __init__(self, config_path: Optional[Path] = None, parent: QObject | None = None) -> None:
        super().__init__(parent)
        self.config_path = config_path or CONFIG_PATH
        self._server: Optional[VibeRemoteServer] = None
        self._thread: Optional[threading.Thread] = None
        self._started = False
        self._last_phone_count = -1
        self._last_pairing_text = ""
        self._last_qr_bytes: bytes = b""
        self._last_event_ts: dict[str, float] = {}  # source -> last hook ts seen
        self._last_keymap_keys: list = []
        # 缓存 lan_ip()（避免每次轮询都阻塞主线程做 socket 连接）
        self._cached_lan_ip: str = ""
        self._lan_ip_ts: float = 0.0

        # 轮询定时器：1500ms（降低对 UI 主线程的影响）
        self._poll = QTimer(self)
        self._poll.setInterval(1500)
        self._poll.timeout.connect(self._on_poll)

    # ====== 生命周期 ======
    def start(self) -> None:
        if self._started:
            return
        try:
            config = load_config(self.config_path)
        except Exception as exc:
            LOG.exception("load config failed: %s", exc)
            config = {}
        # GUI 模式：server 不开浏览器
        try:
            self._server = VibeRemoteServer(config, no_browser=True)
        except Exception as exc:
            LOG.exception("build server failed: %s", exc)
            self.service_status_changed.emit(f"启动失败：{exc}")
            self.last_error_changed.emit(str(exc))
            return

        # 子线程跑 server.run()
        self._thread = threading.Thread(
            target=self._run_server_thread,
            name="vibe-server-v2",
            daemon=True,
        )
        self._thread.start()

        # 等 server 起来（绑定端口）
        port = int(config.get("port", 8765))
        for _ in range(40):
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                    break
            except OSError:
                time.sleep(0.1)

        self._started = True
        self.service_status_changed.emit("服务运行中")
        self.last_error_changed.emit("✓ 服务已启动 · 监听 ws://0.0.0.0:8765 · Hook TCP 49361")
        self._poll.start()

    def stop(self) -> None:
        self._poll.stop()
        self._started = False
        # server.run() 是阻塞的，daemon 线程会随主进程退出而结束
        # TODO: 优雅关闭可调用 server.loop.call_soon_threadsafe(server.loop.stop)

    def _run_server_thread(self) -> None:
        """子线程入口：跑 asyncio.run(server.run())。"""
        try:
            asyncio.run(self._server.run())
        except Exception as exc:
            LOG.exception("server thread crashed: %s", exc)
            # 在主线程中触发状态更新
            self.service_status_changed.emit(f"服务崩溃：{exc}")
            self.last_error_changed.emit(str(exc))

    # ====== 轮询 ======
    def _on_poll(self) -> None:
        """主线程轮询：从 server 拉状态，emit 信号。"""
        if self._server is None:
            return
        try:
            self._poll_pairing()
            self._poll_networks()
            self._poll_phone_count()
            self._poll_tasks_and_events()
            self._poll_keymap()
        except Exception as exc:
            LOG.exception("poll failed: %s", exc)

    def _poll_pairing(self) -> None:
        # server 在子线程，但 pairing_info 是简单属性访问，安全
        try:
            info = self._server.pairing_info(is_local=True)
        except Exception:
            return
        url = info.get("pairing_url", "")
        if url and url != self._last_pairing_text:
            self._last_pairing_text = url
            self.pairing_text_changed.emit(url)
            # 同步生成 QR 码（本端生成，避免访问 server 私有属性）
            try:
                import qrcode  # type: ignore
                img = qrcode.make(url, box_size=10, border=2)
                buf = io.BytesIO()
                img.save(buf, format="PNG")
                qr_bytes = buf.getvalue()
                if qr_bytes != self._last_qr_bytes:
                    self._last_qr_bytes = qr_bytes
                    pm = _bytes_to_pixmap(qr_bytes)
                    if not pm.isNull():
                        self.qr_pixmap_changed.emit(pm)
            except Exception as exc:
                LOG.warning("QR gen failed: %s", exc)

    def _poll_networks(self) -> None:
        try:
            ips = self._server.pairing_info(is_local=True).get("ips", [])
        except Exception:
            ips = []
        # ips 可能是 list[str] 或 list[dict]
        items: list[str] = []
        for ip in ips:
            if isinstance(ip, dict):
                items.append(ip.get("address") or ip.get("ip") or str(ip))
            else:
                items.append(str(ip))
        # 缓存 lan_ip()，每 30s 才更新一次（避免阻塞主线程）
        now = time.time()
        if not self._cached_lan_ip or (now - self._lan_ip_ts) > 30.0:
            try:
                self._cached_lan_ip = _desktop_main.lan_ip()
                self._lan_ip_ts = now
            except Exception:
                pass
        selected = self._cached_lan_ip
        if items:
            self.networks_changed.emit(items, selected)

    def _poll_phone_count(self) -> None:
        try:
            count = len(self._server.authorized)
        except Exception:
            count = 0
        if count != self._last_phone_count:
            self._last_phone_count = count
            self.phone_count_changed.emit(count)

    def _poll_tasks_and_events(self) -> None:
        """轮询 Hook 任务和最近事件。"""
        tasks = getattr(self._server, "tasks", {}) or {}
        # 把按 source 分组的事件计数与最近事件名 emit 出去
        per_source: dict[str, list[dict]] = {"codex": [], "claude": []}
        for sid, t in tasks.items():
            src = (t.get("source") or "").lower()
            if src == "claude":
                per_source["claude"].append(t)
            elif src == "codex":
                per_source["codex"].append(t)
        # 最近事件时间戳
        for src, items in per_source.items():
            if not items:
                continue
            # 按 updated_at/started_at 取最新
            latest = max(
                items,
                key=lambda x: float(x.get("updated_at") or x.get("started_at") or 0),
            )
            ts = float(latest.get("updated_at") or latest.get("started_at") or 0)
            if ts > self._last_event_ts.get(src, 0):
                self._last_event_ts[src] = ts
                event_name = latest.get("lifecycle") or latest.get("event") or "—"
                title = latest.get("title") or latest.get("project_name") or ""
                summary = f"{event_name} / {title}" if title else event_name
                self.hook_event_arrived.emit(src, event_name, summary)
                if src == "codex":
                    self.codex_last_event_changed.emit(summary)
                else:
                    self.claude_last_event_changed.emit(summary)
                # 状态
                if src == "codex":
                    self.codex_hook_status_changed.emit(f"已配置 · {len(items)} 个会话")
                else:
                    self.claude_hook_status_changed.emit(f"已配置 · {len(items)} 个会话")

    def _poll_keymap(self) -> None:
        try:
            km = self._server.keymap_store.load()
        except Exception:
            return
        keys = km.get("keys", [])
        if keys != self._last_keymap_keys:
            self._last_keymap_keys = keys
            self.keymap_changed.emit(keys)

    # ====== 对外槽（UI 调用） ======
    @Slot()
    def refresh_networks(self) -> None:
        # 立即触发一次轮询
        self._poll_networks()

    @Slot()
    def copy_pairing_text(self) -> None:
        from PySide6.QtWidgets import QApplication
        if self._last_pairing_text:
            QApplication.clipboard().setText(self._last_pairing_text)

    @Slot(str, result=str)
    def install_hook(self, source: str) -> str:
        """安装 Codex/Claude Hook。source 取 codex/claude/both/all。"""
        if not _HAS_HOOK_INSTALLER:
            return "agent_hook 不可用"
        try:
            # 修复：cmd_install 需要 (target, exe) 两个参数；
            # 传 None 让 _default_hook_exe 自动解析 vibe_agent_hook.exe 路径。
            rc = cmd_install(source, None)
            # 通知 UI 刷新备份列表
            self.backups_changed.emit(source)
            return "已安装" if rc == 0 else f"安装失败 rc={rc}"
        except Exception as exc:
            return f"安装失败：{exc}"

    @Slot(str, result=str)
    def uninstall_hook(self, source: str) -> str:
        if not _HAS_HOOK_INSTALLER:
            return "agent_hook 不可用"
        try:
            rc = cmd_uninstall(source)
            # 通知 UI 刷新备份列表
            self.backups_changed.emit(source)
            return "已卸载" if rc == 0 else f"卸载失败 rc={rc}"
        except Exception as exc:
            return f"卸载失败：{exc}"

    @Slot(str, result=list)
    def list_backups(self, source: str) -> list:
        """返回指定 source 的 hook 备份列表（按时间倒序）。"""
        if not _HAS_HOOK_INSTALLER:
            return []
        try:
            from agent_hook import list_backups as _list_backups  # type: ignore
            return _list_backups(source)
        except Exception as exc:
            LOG.exception("list_backups failed: %s", exc)
            return []

    @Slot(str, str, result=bool)
    def delete_backup(self, source: str, name: str) -> bool:
        """删除指定 source 的某个 hook 备份文件。"""
        if not _HAS_HOOK_INSTALLER:
            return False
        try:
            from agent_hook import delete_backup as _delete_backup  # type: ignore
            ok = _delete_backup(source, name)
            if ok:
                self.backups_changed.emit(source)
            return ok
        except Exception as exc:
            LOG.exception("delete_backup failed: %s", exc)
            return False

    @Slot()
    def push_keymap(self) -> None:
        """把当前 keymap 推送给已连接手机。"""
        if self._server is None:
            return
        loop = self._server.loop
        if loop is None or not loop.is_running():
            return
        loop.call_soon_threadsafe(self._server._broadcast_keymap)  # noqa: SLF001

    @Slot(list, result=str)
    def save_keymap(self, keys: list) -> str:
        """把 UI 表格内容写回 keymap.json（整体覆盖）。

        keys 为 list[dict]，每个 dict 含 id/label/display/group/type/mapping。
        """
        try:
            store = self._server.keymap_store  # type: ignore[attr-defined]
            data = store.load()
            # 校验并归一化每个键
            from keymap import KeymapStore, VALID_TYPES  # type: ignore
            normalized: list = []
            for k in keys:
                kind = (k.get("type") or "key").strip()
                if kind not in VALID_TYPES:
                    return f"非法类型: {kind}"
                mapping = k.get("mapping", "")
                norm = KeymapStore._normalize_mapping(kind, mapping)  # noqa: SLF001
                normalized.append({
                    "id": k.get("id", ""),
                    "label": k.get("label", ""),
                    "display": k.get("display", "") or k.get("label", ""),
                    "group": k.get("group", "fn"),
                    "type": kind,
                    "mapping": norm,
                })
            data["keys"] = normalized
            store.save(data)
            # 立刻触发一次 keymap_changed 让 UI 同步显示
            self._last_keymap_keys = []
            self._poll_keymap()
            return "已保存"
        except Exception as exc:
            LOG.exception("save_keymap failed: %s", exc)
            return f"保存失败：{exc}"

    @Slot(dict, result=str)
    def add_key_to_store(self, item: dict) -> str:
        """新增单个键到 keymap.json（UI 表格已加好，仅写文件）。"""
        try:
            store = self._server.keymap_store  # type: ignore[attr-defined]
            store.add_key(
                key_id=item.get("id", ""),
                label=item.get("label", ""),
                display=item.get("display", ""),
                kind=item.get("type", "key"),
                mapping=item.get("mapping", ""),
            )
            return "已新增"
        except Exception as exc:
            LOG.exception("add_key_to_store failed: %s", exc)
            return f"新增失败：{exc}"

    @Slot(str, result=str)
    def remove_key_from_store(self, key_id: str) -> str:
        """从 keymap.json 删除一个键。"""
        try:
            store = self._server.keymap_store  # type: ignore[attr-defined]
            store.remove_key(key_id)
            return "已删除"
        except Exception as exc:
            LOG.exception("remove_key_from_store failed: %s", exc)
            return f"删除失败：{exc}"

    @Slot(result=str)
    def reset_keymap_store(self) -> str:
        """恢复出厂默认 keymap。"""
        try:
            store = self._server.keymap_store  # type: ignore[attr-defined]
            store.reset()
            # 立刻同步给 UI
            self._last_keymap_keys = []
            self._poll_keymap()
            return "已恢复默认"
        except Exception as exc:
            LOG.exception("reset_keymap_store failed: %s", exc)
            return f"恢复失败：{exc}"

    @Slot(result=str)
    def pair_text(self) -> str:
        return self._last_pairing_text

    @Slot()
    def open_logs(self) -> None:
        import os
        import subprocess
        log_dir = DESKTOP_APP_DIR
        try:
            if getattr(sys, "frozen", False):
                log_dir = Path(sys.executable).resolve().parent
            # Windows 打开文件夹
            subprocess.Popen(["explorer", str(log_dir)])
        except Exception as exc:
            LOG.warning("open logs failed: %s", exc)
