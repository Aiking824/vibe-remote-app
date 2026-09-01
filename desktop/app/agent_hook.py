"""Vibe Remote Agent Hook（方案 B：自建 Hook 接收程序）。

用法：
  python agent_hook.py --hook --source claude   # Claude Code 调用
  python agent_hook.py --hook --source codex    # Codex 调用

协议（与 AgentPager HookTcpServer 对齐）：
  stdin JSON  -->  TCP(127.0.0.1:49361) 换行包  {"hook_source":"claude|codex","payload":{...}}
  PermissionRequest 事件挂起等待 server 通过 TCP 回复 allow/deny 决策。
"""
from __future__ import annotations

import argparse
import json
import os
import socket
import sys
import time
from pathlib import Path

HOOK_PORT = 49361
TCP_TIMEOUT = 1.5          # 非权限事件的最大阻塞时间（避免卡死 agent）
PERMISSION_TIMEOUT = 86400  # 权限事件允许挂一整天等用户决策
WRAP_TAG = "Managed by Vibe Remote"


# ========== 入口：hook stdin -> TCP ==========
def run_hook_cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="vibe-agent-hook")
    parser.add_argument("--hook", action="store_true")
    parser.add_argument("--source", choices=["claude", "codex"], default=None)
    parser.add_argument("--install", choices=["claude", "codex", "both"], default=None)
    parser.add_argument("--uninstall", choices=["claude", "codex", "both"], default=None)
    parser.add_argument("--exe", default=None, help="安装时指定 hook 可执行文件路径（默认 sys.argv[0] 所在的 vibe_agent_hook.exe）")
    parser.add_argument("--version", action="store_true", help="输出版本号并退出")
    args = parser.parse_args(argv)

    if args.version:
        print("vibe-agent-hook 0.3.0 (Vibe Remote / AgentPager protocol compatible)")
        return 0
    if args.install:
        return cmd_install(args.install, args.exe)
    if args.uninstall:
        return cmd_uninstall(args.uninstall)
    if not args.hook:
        parser.print_help()
        return 0

    # ---- 作为 hook 被 agent 调用 ----
    # 1) 从 stdin 读 JSON（Claude/Codex 约定是整段 JSON 或首行 JSON）
    try:
        raw = sys.stdin.read()
    except Exception as e:
        print(f"[vibe-hook] stdin read fail: {e}", file=sys.stderr)
        raw = ""
    if not raw.strip():
        return 0

    source = args.source or detect_source(raw)
    if source is None:
        return 0  # 来源不明则放行不阻塞

    envelope = json.dumps(
        {"hook_source": source, "payload": _safe_parse(raw)},
        ensure_ascii=False,
    ) + "\n"

    # 2) 判断是否为权限事件（permission 事件会挂起等回复）
    is_permission = _is_permission_event(source, envelope)
    timeout = PERMISSION_TIMEOUT if is_permission else TCP_TIMEOUT

    try:
        sock = socket.create_connection(("127.0.0.1", HOOK_PORT), timeout=TCP_TIMEOUT)
        sock.settimeout(timeout)
        try:
            sock.sendall(envelope.encode("utf-8"))
            if is_permission:
                # 等 server 回决策（与 AgentPager ClaudeHookOutput/CodexHookOutput 对齐）
                resp = _read_all(sock, timeout=PERMISSION_TIMEOUT)
                if resp:
                    sys.stdout.buffer.write(resp)
                    sys.stdout.buffer.flush()
            else:
                # 非权限事件：等 server 一行 ack（\n）即可释放
                try:
                    _read_line(sock, timeout=TCP_TIMEOUT)
                except Exception:
                    pass
        finally:
            try:
                sock.close()
            except Exception:
                pass
    except Exception as e:
        # Hook 服务器未启动则静默跳过，不阻塞 agent 正常工作
        sys.stderr.write(f"[vibe-hook] tcp send fail: {e}\n")
        # 权限事件必须输出 allow 决策避免卡死 agent
        if is_permission:
            _emit_default_permission_stdout(source)
        return 0
    return 0


def detect_source(raw: str) -> str | None:
    """根据 payload 字段猜测来源。"""
    try:
        obj = json.loads(raw)
    except Exception:
        return None
    # 优先看用户传的 source
    if isinstance(obj, dict):
        src = str(obj.get("source", "") or "").lower()
        if "claude" in src:
            return "claude"
        if "codex" in src:
            return "codex"
        # Codex 有明确的 codex 专用字段组合
        if obj.get("turn_id") and obj.get("transcript_path"):
            return "codex"
        if obj.get("hook_event_name") == "PermissionRequest" and obj.get("permission_mode") is not None:
            return "claude"
    return None


def _safe_parse(raw: str) -> dict:
    try:
        return json.loads(raw)
    except Exception:
        # 如果是多行（比如首行 JSON 后还有其他内容），只取第一行
        first = raw.splitlines()[0] if raw.splitlines() else raw
        try:
            return json.loads(first)
        except Exception:
            return {"raw": raw}


def _is_permission_event(source: str, envelope: str) -> bool:
    try:
        obj = json.loads(envelope)
        payload = obj.get("payload") or {}
        name = str(payload.get("hook_event_name") or "")
        return name == "PermissionRequest"
    except Exception:
        return False


def _read_all(sock: socket.socket, timeout: float) -> bytes:
    """读取直到超时/EOF（对权限决策足够短的响应，实际 server 一发即到）。"""
    chunks: list[bytes] = []
    deadline = time.time() + timeout
    while True:
        remain = deadline - time.time()
        if remain <= 0:
            break
        sock.settimeout(min(remain, 1.0))
        try:
            data = sock.recv(65536)
        except socket.timeout:
            if chunks:
                break
            continue
        if not data:
            break
        chunks.append(data)
        if chunks and chunks[-1].endswith(b"\n"):
            # 决策响应以单个 JSON + \n 结束
            break
    return b"".join(chunks)


def _read_line(sock: socket.socket, timeout: float) -> str:
    deadline = time.time() + timeout
    buf = bytearray()
    while True:
        remain = deadline - time.time()
        if remain <= 0:
            break
        sock.settimeout(min(remain, 0.5))
        try:
            b = sock.recv(1)
        except socket.timeout:
            continue
        if not b:
            break
        if b == b"\n":
            break
        buf.extend(b)
    return buf.decode("utf-8", errors="replace")


def _emit_default_permission_stdout(source: str) -> None:
    """hook 服务没启动时，权限事件默认 allow 以避免卡死 agent。"""
    if source == "claude":
        resp = {
            "continue": True,
            "suppressOutput": True,
            "hookSpecificOutput": {
                "hookEventName": "PermissionRequest",
                "decision": {"behavior": "allow"},
            },
        }
    else:  # codex
        resp = {
            "continue": True,
            "hookSpecificOutput": {
                "hookEventName": "PermissionRequest",
                "decision": "allow",
            },
        }
    sys.stdout.write(json.dumps(resp) + "\n")
    sys.stdout.flush()


# ========== Claude / Codex Hook 安装 & 卸载（对齐 AgentPager ClaudeHookConfiguration / CodexHookConfiguration） ==========

def _default_hook_exe(user_spec: str | None) -> str:
    if user_spec:
        return user_spec
    here = Path(sys.argv[0]).resolve().parent
    if getattr(sys, "frozen", False):
        # 优先选用 dist 根目录的 onefile hook exe：
        # PyInstaller 重建 GUI / 控制台目录时会整目录删除重建，
        # 指向目录内部的旧路径会在重建瞬间失效（os error 267），
        # dist 根目录的 onefile exe 全程稳定，不受重建影响。
        stable = here.parent / "vibe_agent_hook.exe"
        if stable.exists():
            return str(stable)
    cand = here / "vibe_agent_hook.exe"
    if cand.exists():
        return str(cand)
    # 开发模式：用 pythonw.exe 调 agent_hook.py（避免弹黑窗）
    py = Path(sys.executable).with_name("pythonw.exe")
    if not py.exists():
        py = Path(sys.executable)
    here = Path(__file__).resolve().parent / "agent_hook.py"
    return f'"{py}" "{here}"'


CLAUDE_EVENTS: list[tuple[str, str | None, int | None]] = [
    ("SessionStart", None, None),
    ("SessionEnd", None, None),
    ("UserPromptSubmit", None, None),
    ("Stop", None, None),
    ("StopFailure", None, None),
    ("SubagentStart", None, None),
    ("SubagentStop", None, None),
    ("Notification", "*", None),
    ("PreToolUse", "*", None),
    ("PermissionRequest", "*", 86_400),
    ("PostToolUse", "*", None),
    ("PostToolUseFailure", "*", None),
    ("PermissionDenied", "*", None),
    ("PreCompact", None, None),
]

CODEX_EVENTS: list[tuple[str, str | None, int]] = [
    ("SessionStart", "startup|resume", 45),
    ("UserPromptSubmit", None, 45),
    ("PreToolUse", None, 45),
    ("PostToolUse", None, 45),
    ("PermissionRequest", None, 3600),
    ("Stop", None, 45),
]


def _claude_settings_path() -> Path:
    env = os.environ.get("CLAUDE_CONFIG_DIR")
    if env:
        return Path(env) / "settings.json"
    return Path.home() / ".claude" / "settings.json"


def _codex_home() -> Path:
    # Codex hooks.json 默认位置：~/.codex/hooks.json
    return Path.home() / ".codex"


def _is_managed(group: dict) -> bool:
    """判断 hook group 是否由 Vibe Remote 管理（与 AgentPager ManagedStatus 等价）。"""
    handlers = group.get("hooks")
    if not isinstance(handlers, list):
        return False
    for h in handlers:
        if not isinstance(h, dict):
            continue
        status = str(h.get("statusMessage") or "")
        cmd = str(h.get("command") or "") + str(h.get("commandWindows") or "")
        if (WRAP_TAG in status) or ("vibe-agent-hook" in cmd) or ("agent_hook.py" in cmd):
            return True
    return False


def _write_json(path: Path, obj: dict, delete_empty: bool = False) -> tuple[bool, str | None]:
    """写入 JSON 文件并返回 (changed, backup_path)。"""
    original: str | None = None
    if path.exists():
        try:
            original = path.read_text(encoding="utf-8")
        except Exception:
            original = None

    if delete_empty and not obj:
        new_text: str | None = None
    else:
        new_text = json.dumps(obj, ensure_ascii=False, indent=2) + "\n"

    def norm(s: str | None) -> str | None:
        if s is None:
            return None
        return s.replace("\r\n", "\n").strip()

    if norm(original) == norm(new_text):
        return False, None

    path.parent.mkdir(parents=True, exist_ok=True)
    backup: str | None = None
    if original is not None:
        stamp = int(time.time())
        backup_path = path.with_suffix(path.suffix + f".vibe-hook-backup-{stamp}")
        i = 1
        while backup_path.exists():
            backup_path = path.with_suffix(path.suffix + f".vibe-hook-backup-{stamp}-{i}")
            i += 1
        backup_path.write_text(original, encoding="utf-8")
        backup = str(backup_path)

    if new_text is None:
        try:
            path.unlink()
        except FileNotFoundError:
            pass
    else:
        path.write_text(new_text, encoding="utf-8")
    return True, backup


def install_claude(exe: str | None) -> tuple[bool, str | None]:
    path = _claude_settings_path()
    root: dict = {}
    if path.exists():
        try:
            root = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            root = {}
    if not isinstance(root, dict):
        root = {}
    hooks = root.get("hooks")
    if not isinstance(hooks, dict):
        hooks = {}
    # 路径无空格时不加引号：部分 agent 对带引号命令的解析不一致，
    # 整串含引号被当作可执行文件名时 CreateProcess 会报 267 目录名无效。
    command = f"{exe} --hook --source claude"
    for name, matcher, timeout in CLAUDE_EVENTS:
        groups = hooks.get(name)
        if not isinstance(groups, list):
            groups = []
        retained = [g for g in groups if isinstance(g, dict) and not _is_managed(g)]
        handler = {
            "type": "command",
            "command": command,
            "statusMessage": WRAP_TAG,
        }
        if timeout is not None:
            handler["timeout"] = timeout
        group: dict = {"hooks": [handler]}
        if matcher is not None:
            group["matcher"] = matcher
        retained.append(group)
        hooks[name] = retained
    root["hooks"] = hooks
    return _write_json(path, root)


def uninstall_claude() -> tuple[bool, str | None]:
    path = _claude_settings_path()
    if not path.exists():
        return False, None
    try:
        root = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return False, None
    if not isinstance(root, dict):
        return False, None
    hooks = root.get("hooks")
    if not isinstance(hooks, dict):
        return False, None
    changed = False
    for name, groups in list(hooks.items()):
        if not isinstance(groups, list):
            continue
        kept = [g for g in groups if isinstance(g, dict) and not _is_managed(g)]
        if len(kept) != len(groups):
            changed = True
        if not kept:
            del hooks[name]
        else:
            hooks[name] = kept
    if changed and not hooks:
        del root["hooks"]
    if not changed:
        return False, None
    return _write_json(path, root, delete_empty=True)


def install_codex(exe: str | None) -> tuple[bool, str | None]:
    path = _codex_home() / "hooks.json"
    root: dict = {}
    if path.exists():
        try:
            root = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            root = {}
    if not isinstance(root, dict):
        root = {}
    hooks = root.get("hooks")
    if not isinstance(hooks, dict):
        hooks = {}
    command = f"{exe} --hook --source codex"
    for name, matcher, timeout in CODEX_EVENTS:
        groups = hooks.get(name)
        if not isinstance(groups, list):
            groups = []
        retained = [g for g in groups if isinstance(g, dict) and not _is_managed(g)]
        handler = {
            "type": "command",
            "command": command,
            "commandWindows": command,
            "timeout": timeout,
            "statusMessage": WRAP_TAG,
        }
        group: dict = {"hooks": [handler]}
        if matcher is not None:
            group["matcher"] = matcher
        retained.append(group)
        hooks[name] = retained
    root["hooks"] = hooks
    return _write_json(path, root)


def uninstall_codex() -> tuple[bool, str | None]:
    path = _codex_home() / "hooks.json"
    if not path.exists():
        return False, None
    try:
        root = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return False, None
    if not isinstance(root, dict):
        return False, None
    hooks = root.get("hooks")
    if not isinstance(hooks, dict):
        return False, None
    changed = False
    for name, groups in list(hooks.items()):
        if not isinstance(groups, list):
            continue
        kept = [g for g in groups if isinstance(g, dict) and not _is_managed(g)]
        if len(kept) != len(groups):
            changed = True
        if not kept:
            del hooks[name]
        else:
            hooks[name] = kept
    if changed and not hooks:
        del root["hooks"]
    if not changed:
        return False, None
    return _write_json(path, root, delete_empty=True)


def cmd_install(target: str, exe: str | None) -> int:
    exe_path = _default_hook_exe(exe)
    rc = 0
    if target in ("claude", "both"):
        ok, backup = install_claude(exe_path)
        print(f"[vibe-hook] Claude: {'已安装' if ok else '已是最新状态'}")
        if backup:
            print(f"  backup: {backup}")
        print(f"  command: {exe_path} --hook --source claude")
    if target in ("codex", "both"):
        ok, backup = install_codex(exe_path)
        print(f"[vibe-hook] Codex:  {'已安装' if ok else '已是最新状态'}")
        if backup:
            print(f"  backup: {backup}")
        print(f"  command: {exe_path} --hook --source codex")
    return 0


def cmd_uninstall(target: str) -> int:
    if target in ("claude", "both"):
        ok, backup = uninstall_claude()
        print(f"[vibe-hook] Claude: {'已卸载' if ok else '未安装'}")
        if backup:
            print(f"  backup: {backup}")
    if target in ("codex", "both"):
        ok, backup = uninstall_codex()
        print(f"[vibe-hook] Codex:  {'已卸载' if ok else '未安装'}")
        if backup:
            print(f"  backup: {backup}")
    return 0


if __name__ == "__main__":
    sys.exit(run_hook_cli())
