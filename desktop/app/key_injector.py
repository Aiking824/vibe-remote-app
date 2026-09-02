"""Windows 按键/鼠标/剪贴板注入（ctypes user32，零额外依赖）。

支持：方向键、Enter、组合键（Win+D / Win+Tab / Ctrl+C / Ctrl+V / Win+Shift+S
/ Ctrl+Enter / Shift+Enter）、鼠标移动/点击/滚轮、剪贴板文本写入。
"""

from __future__ import annotations

import ctypes
import time
from typing import Sequence

KEYEVENTF_EXTENDEDKEY = 0x0001
KEYEVENTF_KEYUP = 0x0002

# 鼠标事件标志
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800

# 剪贴板
CF_UNICODETEXT = 13
GMEM_MOVEABLE = 0x0002
GMEM_ZEROINIT = 0x0040
GHND = GMEM_MOVEABLE | GMEM_ZEROINIT

# 窗口/消息
SMTO_BLOCK = 0x0001
HWND_BROADCAST = 0xFFFF
WM_DRAWCLIPBOARD = 0x0308
WM_CHAR = 0x0102
SW_RESTORE = 9

# SendInput 常量（Unicode 打字回退）
INPUT_KEYBOARD = 1
KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_KEYUP = 0x0002

class _INPUT_KEYBDINPUT(ctypes.Structure):
    """KEYBDINPUT 结构体（对齐 Windows 64 位 ULONG_PTR=8 字节）。"""
    _fields_ = [
        ("wVk", ctypes.c_ushort),
        ("wScan", ctypes.c_ushort),
        ("dwFlags", ctypes.c_ulong),      # DWORD = 4 bytes
        ("time", ctypes.c_ulong),         # DWORD = 4 bytes
        ("dwExtraInfo", ctypes.c_void_p),  # ULONG_PTR = 8 bytes on 64-bit
    ]

class _INPUT_UNION(ctypes.Union):
    _fields_ = [("ki", _INPUT_KEYBDINPUT)]

class _INPUT(ctypes.Structure):
    _fields_ = [("type", ctypes.c_ulong), ("union", _INPUT_UNION)]

# 虚拟键码（Virtual-Key Codes）
VK = {
    "up": 0x26,
    "down": 0x28,
    "left": 0x25,
    "right": 0x27,
    "enter": 0x0D,
    "tab": 0x09,
    "win": 0x5B,
    "ctrl": 0x11,
    "alt": 0x12,
    "shift": 0x10,
    "lctrl": 0xA2,
    "lalt": 0xA4,
    "lshift": 0xA0,
    "rctrl": 0xA3,
    "ralt": 0xA5,
    "rshift": 0xA1,
    "backspace": 0x08,
    "space": 0x20,
    "escape": 0x1B,
    "delete": 0x2E,
    "end": 0x23,
    "home": 0x24,
    "pageup": 0x21,
    "pagedown": 0x22,
    "a": 0x41,
    "c": 0x43,
    "d": 0x44,
    "s": 0x53,
    "v": 0x56,
    "x": 0x58,
    "z": 0x5A,
}
# 数字键 0-9
VK.update({str(i): 0x30 + i for i in range(10)})
# 字母 a-z
VK.update({chr(c): c for c in range(0x41, 0x5B)})
# F1-F12
VK.update({f"f{i}": 0x6F + i for i in range(1, 13)})


def _down(vk: int) -> None:
    ctypes.windll.user32.keybd_event(vk, 0, 0, 0)


def _up(vk: int) -> None:
    ctypes.windll.user32.keybd_event(vk, 0, KEYEVENTF_KEYUP, 0)


def _send_key_sendinput(vk: int, up: bool = False) -> None:
    """用 SendInput 发送单个按键事件（比 keybd_event 对 Win 键组合更可靠）。"""
    inp = _INPUT()
    inp.type = INPUT_KEYBOARD
    ki = inp.union.ki
    ki.wVk = vk
    ki.wScan = 0
    ki.dwFlags = KEYEVENTF_KEYUP if up else 0
    ki.time = 0
    ki.dwExtraInfo = 0  # c_void_p，直接用整数 0
    ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(_INPUT))


def send_ctrl_shift_win(hold_ms: int = 220) -> bool:
    """发送 Ctrl+Shift+Win 组合键（已废弃，保留向后兼容）。

    新语音输入请改用 send_lshift_lctrl_lalt()。
    """
    try:
        user32 = ctypes.windll.user32
        user32.keybd_event(0xA2, 0, 0, 0)
        time.sleep(0.08)
        user32.keybd_event(0xA0, 0, 0, 0)
        time.sleep(0.08)
        user32.keybd_event(0x5B, 0, 0, 0)
        time.sleep(hold_ms / 1000.0)
        user32.keybd_event(0x5B, 0, KEYEVENTF_KEYUP, 0)
        time.sleep(0.04)
        user32.keybd_event(0xA0, 0, KEYEVENTF_KEYUP, 0)
        time.sleep(0.04)
        user32.keybd_event(0xA2, 0, KEYEVENTF_KEYUP, 0)
        return True
    except Exception:
        return False


def send_lshift_lctrl_lalt(hold_ms: int = 120) -> bool:
    """发送 左Shift+左Ctrl+左Alt 组合键（语音输入触发/结束）。

    对应 keymap.json 中 id="voice" 条目。
    VK_LSHIFT=0xA0, VK_LCONTROL=0xA2, VK_LMENU=0xA4（左Alt）。

    顺序：LShift → LCtrl → LAlt（保持 hold_ms）→ 释放 LAlt → LCtrl → LShift。
    Alt 键不被系统策略拦截，比 Win 键组合更可靠。
    """
    try:
        user32 = ctypes.windll.user32
        # 1. 左Shift 按下，保持 40ms
        user32.keybd_event(0xA0, 0, 0, 0)
        time.sleep(0.04)
        # 2. 左Ctrl 按下，保持 40ms
        user32.keybd_event(0xA2, 0, 0, 0)
        time.sleep(0.04)
        # 3. 左Alt 按下，保持 hold_ms 让系统识别组合键
        user32.keybd_event(0xA4, 0, 0, 0)
        time.sleep(hold_ms / 1000.0)
        # 4. 释放左Alt
        user32.keybd_event(0xA4, 0, KEYEVENTF_KEYUP, 0)
        time.sleep(0.03)
        # 5. 释放左Ctrl
        user32.keybd_event(0xA2, 0, KEYEVENTF_KEYUP, 0)
        time.sleep(0.03)
        # 6. 释放左Shift
        user32.keybd_event(0xA0, 0, KEYEVENTF_KEYUP, 0)
        return True
    except Exception:
        return False


class KeyInjector:
    def tap(self, key: str, delay: float = 0.03) -> None:
        _down(VK[key])
        time.sleep(delay)
        _up(VK[key])

    def press(self, key: str) -> None:
        _down(VK[key])

    def release(self, key: str) -> None:
        _up(VK[key])

    def chord(self, mods: Sequence[str], key: str) -> None:
        # 特殊处理：Win+L 是系统安全动作，keybd_event 模拟会被 UIPI/安全策略忽略，
        # 直接调用 user32.LockWorkStation() 锁屏（零依赖、不需要管理员权限）。
        if len(mods) == 1 and mods[0] == "win" and key == "l":
            try:
                ctypes.windll.user32.LockWorkStation()
            except Exception:
                # 退化：老版本 Windows 若上面没有导出，再尝试 keybd_event 兜底
                try:
                    _down(VK["win"])
                    time.sleep(0.02)
                    _down(VK["l"])
                    time.sleep(0.03)
                    _up(VK["l"])
                    time.sleep(0.01)
                    _up(VK["win"])
                except Exception:
                    pass
            return
        for m in mods:
            _down(VK[m])
        time.sleep(0.02)
        self.tap(key, delay=0.03)
        for m in reversed(mods):
            _up(VK[m])

    def home(self) -> None:
        self.chord(["win"], "d")

    def tv(self) -> None:
        self.chord(["win"], "tab")

    def copy(self) -> None:
        self.chord(["ctrl"], "c")

    def paste(self) -> None:
        self.chord(["ctrl"], "v")

    def cut(self) -> None:
        self.chord(["ctrl"], "x")

    def screenshot(self) -> None:
        self.chord(["win", "shift"], "s")

    def lock_screen(self) -> None:
        """显式锁屏（等同于 Win+L，走 LockWorkStation API，不受按键注入限制）。"""
        self.chord(["win"], "l")

    # ---- 鼠标控制 ----
    def mouse_move_rel(self, dx: int, dy: int) -> None:
        """按增量移动鼠标（触控板拖动的像素增量）。"""
        pt = ctypes.wintypes.POINT()
        ctypes.windll.user32.GetCursorPos(ctypes.byref(pt))
        ctypes.windll.user32.SetCursorPos(pt.x + int(dx), pt.y + int(dy))

    def mouse_click(self, button: str = "left") -> None:
        flags = {"left": (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
                 "right": (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
                 "middle": (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP)}.get(button)
        if not flags:
            return
        down, up = flags
        ctypes.windll.user32.mouse_event(down, 0, 0, 0, 0)
        time.sleep(0.03)
        ctypes.windll.user32.mouse_event(up, 0, 0, 0, 0)

    def mouse_scroll(self, dy: int = 1) -> None:
        """滚轮：dy>0 向上滚动，dy<0 向下；单位 1 ≈ 120 步。"""
        ctypes.windll.user32.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, int(dy) * 120, 0)

    # ---- 剪贴板文本（手机 → 电脑粘贴） ----
    def _foreground_attach(self) -> None:
        """尝试把当前前台窗口真正激活并提升，避免粘贴只进剪贴板不进光标。"""
        user32 = ctypes.windll.user32
        try:
            fg = user32.GetForegroundWindow()
            if not fg:
                return
            user32.ShowWindow(fg, SW_RESTORE)
            # 把本线程 Attach 到前台线程，让后续 SetForeground / 输入注入生效
            fg_tid = user32.GetWindowThreadProcessId(fg, None)
            cur_tid = user32.GetCurrentThreadId()
            if fg_tid and fg_tid != cur_tid:
                user32.AttachThreadInput(cur_tid, fg_tid, True)
                user32.SetForegroundWindow(fg)
                user32.BringWindowToTop(fg)
                user32.AttachThreadInput(cur_tid, fg_tid, False)
        except Exception:
            pass

    def set_clipboard(self, text: str) -> bool:
        """把文本写入系统剪贴板（CF_UNICODETEXT）。成功返回 True。

        为避免「桌面端打开本服务自身的控制台页面时，页面被带到前台、粘贴目标输入框失去焦点」，
        这里先记录本次操作之前的前台窗口句柄并做一次聚焦保护；
        写入结束后再次尝试把该前台窗口抬到顶层。
        """
        if not text:
            return False
        kernel32 = ctypes.windll.kernel32
        user32 = ctypes.windll.user32
        user32.GetForegroundWindow()  # no-op but keeps ctypes binding
        saved_fg = user32.GetForegroundWindow()
        ok = False
        handle = 0
        # 先尝试 Attach 到前台窗口所在线程（若有），提升后续粘贴/激活可靠性
        try:
            fg_tid = user32.GetWindowThreadProcessId(saved_fg, None)
            cur_tid = user32.GetCurrentThreadId()
            if saved_fg and fg_tid and fg_tid != cur_tid:
                user32.AttachThreadInput(cur_tid, fg_tid, True)
            if saved_fg:
                user32.ShowWindow(saved_fg, SW_RESTORE)
                user32.SetForegroundWindow(saved_fg)
                user32.BringWindowToTop(saved_fg)
        except Exception:
            pass
        try:
            # 最多重试 8 次（剪贴板可能被当前应用占用）
            for attempt in range(8):
                if bool(user32.OpenClipboard(0)):
                    break
                user32.CloseClipboard()
                time.sleep(0.06 * (attempt + 1))
            else:
                return False
            user32.EmptyClipboard()
            btext = text.encode("utf-16-le") + b"\x00\x00"
            handle = kernel32.GlobalAlloc(GHND, len(btext))
            if not handle:
                return False
            ptr = kernel32.GlobalLock(handle)
            if not ptr:
                kernel32.GlobalFree(handle)
                return False
            try:
                ctypes.memmove(ptr, btext, len(btext))
            finally:
                kernel32.GlobalUnlock(handle)
            set_handle = user32.SetClipboardData(CF_UNICODETEXT, handle)
            if not set_handle:
                kernel32.GlobalFree(handle)
                handle = 0
                return False
            # 归属转移，SetClipboardData 成功后不要 GlobalFree
            handle = 0
            # 通知所有剪贴板查看器更新（两轮），让微信/钉钉/浏览器/编辑器立刻感知
            for _ in range(2):
                try:
                    user32.SendMessageTimeoutW(
                        HWND_BROADCAST, WM_DRAWCLIPBOARD, 0, 0,
                        SMTO_BLOCK, 250, None,
                    )
                except Exception:
                    pass
                time.sleep(0.04)
            ok = True
        finally:
            try:
                user32.CloseClipboard()
            except Exception:
                pass
            # 解除 Attach
            try:
                if saved_fg and fg_tid and fg_tid != cur_tid:
                    user32.AttachThreadInput(cur_tid, fg_tid, False)
            except Exception:
                pass
        # 再一次把原先的前台窗口抬回顶层，防止本服务自己的页面抢走焦点
        try:
            if saved_fg:
                user32.ShowWindow(saved_fg, SW_RESTORE)
                user32.SetForegroundWindow(saved_fg)
                user32.BringWindowToTop(saved_fg)
        except Exception:
            pass
        return ok

    def _type_unicode(self, text: str) -> None:
        """剪贴板完全失败时的回退：SendInput(KEYEVENTF_UNICODE) 逐字输入。"""
        if not text:
            return
        user32 = ctypes.windll.user32
        for ch in text:
            code = ord(ch)
            if code == 0:
                continue
            down = _INPUT()
            down.type = INPUT_KEYBOARD
            down.union.ki.wScan = ctypes.c_ushort(code)
            down.union.ki.dwFlags = KEYEVENTF_UNICODE
            up = _INPUT()
            up.type = INPUT_KEYBOARD
            up.union.ki.wScan = ctypes.c_ushort(code)
            up.union.ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP
            arr = (_INPUT * 2)(down, up)
            user32.SendInput(2, ctypes.byref(arr), ctypes.sizeof(_INPUT))
            time.sleep(0.006)

    def paste_text(self, text: str) -> None:
        """手机发来的文本：写剪贴板 → 稍等 → Ctrl+V 粘到电脑当前焦点。

        策略（按顺序尝试）：
        1) 写剪贴板（带 8 次重试 + 两轮 WM_DRAWCLIPBOARD 广播 + 前台窗口保护）；
        2) 等 160ms 让应用感知剪贴板变化；
        3) 先做一次 WM_CHAR 批量把文本灌到当前前台窗口（很多编辑器/IM 直接接受这一层）；
        4) 第一次 Ctrl+V；
        5) 若第一次无效果，再触发 Ctrl+Shift+V（无格式粘贴，某些 IM 默认接收）；
        6) 任何环节失败，回退到 SendInput(KEYEVENTF_UNICODE) 逐字输入，保证文本一定进到光标处。
        """
        if not text:
            return
        user32 = ctypes.windll.user32
        # 记录「原本」的前台窗口（写剪贴板后再抬回，避免本服务页面抢占焦点）
        saved_fg = user32.GetForegroundWindow()
        try:
            fg_tid = user32.GetWindowThreadProcessId(saved_fg, None)
            cur_tid = user32.GetCurrentThreadId()
            if saved_fg and fg_tid and fg_tid != cur_tid:
                user32.AttachThreadInput(cur_tid, fg_tid, True)
        except Exception:
            fg_tid = cur_tid = 0

        def _unattach():
            try:
                if saved_fg and fg_tid and fg_tid != cur_tid:
                    user32.AttachThreadInput(cur_tid, fg_tid, False)
            except Exception:
                pass

        try:
            cb_ok = self.set_clipboard(text)
            if not cb_ok:
                self._type_unicode(text)
                return
            # 等应用侧接收剪贴板更新
            time.sleep(0.16)
            if saved_fg:
                try:
                    user32.ShowWindow(saved_fg, SW_RESTORE)
                    user32.SetForegroundWindow(saved_fg)
                    user32.BringWindowToTop(saved_fg)
                except Exception:
                    pass
                time.sleep(0.04)

            # 策略 A：直接 SendMessage WM_CHAR 批量到前台窗口（最稳，不依赖剪贴板）
            try:
                fg = user32.GetForegroundWindow()
                if fg:
                    for ch in text:
                        c = ord(ch)
                        if c == 0:
                            continue
                        user32.SendMessageTimeoutW(
                            fg, WM_CHAR, c, c,
                            SMTO_BLOCK, 60, None,
                        )
                    # 到这里已经灌进光标，就不再走 Ctrl+V（避免重复粘贴）
                    return
            except Exception:
                pass

            # 策略 B：Ctrl+V（普通粘贴），最多两次
            pasted = False
            for idx in range(2):
                try:
                    self.paste()
                    pasted = True
                    if idx == 0:
                        time.sleep(0.06)
                        user32.SendMessageTimeoutW(
                            HWND_BROADCAST, WM_DRAWCLIPBOARD, 0, 0,
                            SMTO_BLOCK, 150, None,
                        )
                except Exception:
                    if idx == 1:
                        pasted = False
                    break
            if pasted:
                return

            # 策略 C：无格式粘贴（Ctrl+Shift+V），微信/飞书等 IM 常用
            try:
                self.chord(["ctrl", "shift"], "v")
                return
            except Exception:
                pass
            # 策略 D：最后的 Unicode 回退
            self._type_unicode(text)
        finally:
            _unattach()

    # ---- 提供者（语音工具）快捷键 ----
    def provider_down(self, shortcut: Sequence[str]) -> None:
        # 防御性代码：如果某键上一次没被正常松开（异常、中断），先补一个 up，
        # 避免 Ctrl/Shift/Win 「幽灵粘滞」造成「按了两次 toggle 实际只生效一次」。
        for m in shortcut:
            try:
                _up(VK[m])
            except Exception:
                pass
            time.sleep(0.005)
        for m in shortcut:
            _down(VK[m])
            time.sleep(0.005)

    def provider_up(self, shortcut: Sequence[str]) -> None:
        for m in reversed(shortcut):
            try:
                _up(VK[m])
            except Exception:
                pass
            time.sleep(0.005)

    def provider_toggle(self, shortcut: Sequence[str], down_ms: float = 0.08) -> None:
        """按下组合键并保持 down_ms 秒再松开（一次“tap”即一次 toggle 触发）。

        为避免 Windows 语音工具（如微信输入法）误判「按住时长不够 → 当作文本光标切换」，
        这里严格保证：down -> sleep(>=30ms) -> up，且前后分别清理幽灵粘滞键。
        """
        # 1) 先清理所有潜在粘滞的修饰键，避免前一次 hold_end 异常导致现在的 down 被吃掉
        for mod in ("ctrl", "shift", "win", "alt"):
            if mod not in shortcut:
                try:
                    _up(VK[mod])
                except Exception:
                    pass
        self.provider_down(shortcut)
        time.sleep(max(0.03, float(down_ms)))
        self.provider_up(shortcut)