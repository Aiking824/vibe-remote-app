"""Vibe Remote desktop_v2 入口。

启动顺序：
  1) 单例检查（QLocalServer，已运行则唤醒前台退出）
  2) QApplication + 应用 AgentPager 调色板/字体
  3) 创建 MainWindow
  4) 系统托盘（QSystemTrayIcon）
  5) 启动后台 Runtime（WebSocket 8765 + Hook TCP 49361 + Keymap + AudioDefaultSwitch + StatusWatcher）
  6) 进入 Qt 事件循环

打包：
  python -m PyInstaller vibe_remote_v2.spec  （待创建）

开发运行：
  python desktop_v2/main.py
"""
from __future__ import annotations

import logging
import sys
from pathlib import Path

from PySide6.QtCore import QObject, QTimer, Qt, Signal
from PySide6.QtGui import QIcon, QPixmap
from PySide6.QtWidgets import QApplication, QSystemTrayIcon, QMenu

LOG = logging.getLogger("vibe-remote-v2")

if getattr(sys, "frozen", False):
    # PyInstaller frozen：desktop/app/ 打包在 _MEIPASS/desktop/app/
    HERE = Path(sys._MEIPASS)  # type: ignore[attr-defined]
    DESKTOP_APP_DIR = HERE / "desktop" / "app"
else:
    HERE = Path(__file__).resolve().parent
    DESKTOP_APP_DIR = HERE.parent / "desktop" / "app"
# 引入 desktop/app/ 作为可复用模块路径
if str(DESKTOP_APP_DIR) not in sys.path:
    sys.path.insert(0, str(DESKTOP_APP_DIR))

from theme import apply_theme  # noqa: E402
from main_window import MainWindow  # noqa: E402
from runtime import RuntimeService  # noqa: E402


# ====== 单例运行 ======
_SINGLETON_KEY = "vibe-remote-v2.singleton"


def _ensure_single_instance() -> bool:
    """返回 True 表示当前进程是首个实例，False 表示已有实例在运行。"""
    from PySide6.QtNetwork import QLocalServer, QLocalSocket
    # 尝试连接已有实例
    client = QLocalSocket()
    client.connectToServer(_SINGLETON_KEY)
    if client.waitForConnected(200):
        # 唤醒已有实例（发送一个字节）
        client.write(b"1")
        client.flush()
        client.waitForBytesWritten(500)
        return False
    # 清理残留服务端
    QLocalServer.removeServer(_SINGLETON_KEY)
    return True


class _SingletonServer(QObject):
    """监听后续实例的唤起请求。"""

    activate = Signal()

    def __init__(self, parent: QObject | None = None) -> None:
        super().__init__(parent)
        from PySide6.QtNetwork import QLocalServer
        self._srv = QLocalServer(self)
        self._srv.newConnection.connect(self._on_new)
        self._srv.listen(_SINGLETON_KEY)

    def _on_new(self) -> None:
        conn = self._srv.nextPendingConnection()
        if conn:
            conn.readAll()
            conn.deleteLater()
        self.activate.emit()


def _load_app_icon() -> QIcon:
    """加载程序图标（assets/app.ico，steve-minecraft-vector-1.ico 拷贝版）。"""
    ico_path = HERE / "assets" / "app.ico"
    if ico_path.exists():
        return QIcon(str(ico_path))
    # 回落：黄色 V 占位
    from PySide6.QtGui import QColor, QPainter
    pm = QPixmap(32, 32)
    pm.fill(QColor(C.AMBER))
    painter = QPainter(pm)
    painter.setPen(QColor(C.BG))
    from PySide6.QtGui import QFont
    painter.setFont(QFont("Arial", 18, QFont.Bold))
    painter.drawText(pm.rect(), Qt.AlignCenter, "V")
    painter.end()
    return QIcon(pm)


def _make_tray(app: QApplication, window: MainWindow, icon: QIcon,
               on_quit) -> QSystemTrayIcon:
    """创建系统托盘图标。on_quit: 退出前的清理回调。"""
    tray = QSystemTrayIcon(icon, app)
    tray.setToolTip("Vibe Remote Bridge")
    menu = QMenu()
    show_act = menu.addAction("显示主窗口")
    show_act.triggered.connect(window.showNormal)
    menu.addSeparator()
    quit_act = menu.addAction("退出")
    quit_act.triggered.connect(on_quit)
    tray.setContextMenu(menu)
    # 双击托盘图标显示主窗口
    tray.activated.connect(
        lambda reason: window.showNormal() if reason == QSystemTrayIcon.DoubleClick else None
    )
    return tray


# 引入主题色（用于托盘图标）
from theme import C  # noqa: E402


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    argv = list(sys.argv[1:] if argv is None else argv)
    background = "--background" in argv

    # 1) 单例检查
    if not _ensure_single_instance():
        LOG.info("已有实例运行，正在唤起前台窗口。")
        return 0

    # 2) QApplication + 加载图标
    app = QApplication(sys.argv)
    app.setApplicationName("Vibe Remote Bridge")
    app.setQuitOnLastWindowClosed(False)  # 关闭主窗口时不退出，进托盘
    app_icon = _load_app_icon()
    app.setWindowIcon(app_icon)
    apply_theme(app)

    # 3) 主窗口
    window = MainWindow()
    window.setWindowIcon(app_icon)
    singleton = _SingletonServer(app)
    singleton.activate.connect(window.showNormal)

    # 4) 后台 Runtime（先实例化以便托盘退出时能 stop）
    runtime = RuntimeService(parent=app)

    # 5) 系统托盘（关闭托盘"退出"时停止服务并退出程序）
    def _on_quit():
        LOG.info("用户从托盘退出，正在停止后台服务...")
        runtime.stop()
        app.quit()

    tray = _make_tray(app, window, app_icon, on_quit=_on_quit)
    tray.show()

    # ===== 信号 → UI 连接 =====
    runtime.service_status_changed.connect(window.set_service_status)
    runtime.phone_count_changed.connect(window.set_phone_count)
    runtime.pairing_text_changed.connect(window.pairing_page.set_pairing_text)
    runtime.qr_pixmap_changed.connect(window.pairing_page.set_qr_pixmap)
    runtime.networks_changed.connect(lambda items, sel: window.pairing_page.set_networks(items, sel))
    runtime.last_error_changed.connect(window.set_diag)
    runtime.codex_hook_status_changed.connect(window.codex_hook_page.set_status)
    runtime.claude_hook_status_changed.connect(window.claude_hook_page.set_status)
    runtime.codex_last_event_changed.connect(window.codex_hook_page.set_last_event)
    runtime.claude_last_event_changed.connect(window.claude_hook_page.set_last_event)
    runtime.hook_event_arrived.connect(
        lambda src, ev, summary: window.append_event(f"[{src}] {summary}")
    )
    runtime.keymap_changed.connect(window.keymap_page.set_keymap)

    # ===== UI → 信号 连接 =====
    window.pairing_page.refresh_requested.connect(runtime.refresh_networks)
    window.pairing_page.network_changed.connect(lambda _: runtime.refresh_networks())
    window.pairing_page.copy_pairing_requested.connect(runtime.copy_pairing_text)
    window.codex_hook_page.install_requested.connect(lambda: runtime.install_hook("codex"))
    window.codex_hook_page.uninstall_requested.connect(lambda: runtime.uninstall_hook("codex"))
    window.claude_hook_page.install_requested.connect(lambda: runtime.install_hook("claude"))
    window.claude_hook_page.uninstall_requested.connect(lambda: runtime.uninstall_hook("claude"))
    window.keymap_page.push_requested.connect(runtime.push_keymap)
    window.keymap_page.save_requested.connect(
        lambda keys: window.set_diag(runtime.save_keymap(keys))
    )
    window.keymap_page.add_requested.connect(
        lambda item: window.set_diag(runtime.add_key_to_store(item))
    )
    window.keymap_page.remove_requested.connect(
        lambda kid: window.set_diag(runtime.remove_key_from_store(kid))
    )
    window.keymap_page.reset_requested.connect(
        lambda: window.set_diag(runtime.reset_keymap_store())
    )
    window.log_btn.clicked.connect(runtime.open_logs)

    # 启动后台服务（状态由 runtime 通过信号驱动，避免覆盖错误状态）
    runtime.start()

    if not background:
        window.show()

    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
