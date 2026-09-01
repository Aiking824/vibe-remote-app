"""Codex rollout 观察器（AgentPager CodexRolloutObserver 的 Python 移植）。

观察 ~/.codex/sessions/**/*.jsonl 的新增行，把 Codex 的细粒度状态
（思考/执行/编辑/浏览、token 用量、子代理活动、完成/中断）转成 RolloutSignal，
供 main.py 聚合到任务快照并广播到手机。

事件语义与 AgentPager-0.3.0 CodexRolloutObserver.ParseLine 对齐：
  response_item.function_call / custom_tool_call / tool_search_call -> 执行中 + 最新步骤
  event_msg.task_started / turn_started            -> running + thinking
  event_msg.agent_reasoning* / context_compacted  -> running + thinking
  event_msg.exec_command_begin / terminal_interaction -> running + executing
  event_msg.patch_apply_*                         -> running + editing
  event_msg.web_search_*                          -> running + browsing
  event_msg.task_complete / turn_complete         -> succeeded
  event_msg.turn_aborted                          -> interrupted
  event_msg.user_message                          -> running + thinking（userPrompt）
  event_msg.token_count                           -> token 用量
  event_msg.sub_agent_activity                    -> 子代理启动线索（agent_thread_id/agent_path）
  event_msg.request_user_input / elicitation_request -> waitingAnswer（问题）
  event_msg.exec_approval_request / apply_patch_approval_request / request_permissions
                                                  -> waitingApproval（UI 标记；真正的审批走 hook）
"""
from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass
from pathlib import Path


def _iso_to_ms(value: str | None) -> int | None:
    if not value:
        return None
    try:
        from datetime import datetime, timezone
        v = value.strip()
        if v.endswith("Z"):
            v = v[:-1] + "+00:00"
        dt = datetime.fromisoformat(v)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return int(dt.timestamp() * 1000)
    except Exception:
        return None


def _clip(value, limit: int = 220) -> str | None:
    if value is None:
        return None
    v = " ".join(str(value).split()).strip()
    return v[:limit] if v else None


def _display(payload: dict, key: str) -> str | None:
    if key not in payload:
        return None
    v = payload[key]
    if isinstance(v, str):
        return v
    if isinstance(v, list):
        return " ".join(str(x) for x in v if isinstance(x, str))
    if isinstance(v, (int, float, bool)):
        return str(v)
    return None


def _millis(payload: dict, key: str) -> int | None:
    v = payload.get(key)
    try:
        return int(v)
    except Exception:
        return None


def _argument_summary(raw) -> str | None:
    if raw is None:
        return None
    if isinstance(raw, str):
        try:
            doc = json.loads(raw)
        except Exception:
            return _clip(raw)
    else:
        doc = raw
    if isinstance(doc, dict):
        for key in ("cmd", "command", "query", "path", "ref_id", "step"):
            s = _display(doc, key)
            if s:
                return _clip(s)
    return _clip(str(raw)) if raw else None


@dataclass
class RolloutSignal:
    session_id: str
    cwd: str
    lifecycle: str | None = None
    activity: str | None = None
    request_kind: str | None = None        # "approval" | "question"
    summary: str | None = None             # 等待批准/回答的文案
    user_prompt: str | None = None
    latest_step: str | None = None
    usage: dict | None = None              # {input,cached,output,reasoning,total}
    subagent_id: str | None = None
    subagent_path: str | None = None
    timestamp: int = 0                     # 毫秒


def _claude_tool_activity(name: str | None) -> str | None:
    """Claude 工具名 → 活动，与 vibe-remote main._reduce_hook 一致（小写 wire 名）。"""
    if not name:
        return None
    n = name.lower()
    if n in ("runcommand", "exec", "execute", "bash"):
        return "executing"
    if n in ("search", "searchcodebase", "websearch", "webfetch"):
        return "searching"
    if n in ("read", "edit", "write", "deletefile", "grep", "glob", "ls"):
        return "executing"
    if n == "browser_use":
        return "browsing"
    if "agent" in n or "sub" in n or "task" in n:
        return "delegating"
    return "thinking"


class CodexRolloutObserver:
    """跟踪 sessions 目录下 jsonl 的增量行并解析为 RolloutSignal。"""

    DiscoverWindowSec = 600.0
    MaxReplayBytes = 2 * 1024 * 1024

    def __init__(self, sessions_root: str | Path | None = None):
        home = Path(os.environ.get("CODEX_HOME") or (Path.home() / ".codex"))
        self._sessions_root = Path(sessions_root) if sessions_root else home / "sessions"
        self._tracked: dict[str, dict] = {}
        self._next_discovery = 0.0

    # ---------- 公开 API ----------
    def include(self, payload: dict) -> None:
        """hook payload 携带 transcript_path 时开始跟踪（从当前文件尾开始）。"""
        tp = payload.get("transcript_path")
        if not tp:
            return
        sid = str(payload.get("session_id") or "")
        cwd = str(payload.get("cwd") or "")
        if not sid:
            return
        try:
            if Path(tp).exists():
                self._track(str(tp), sid, cwd, read_existing=False)
        except OSError:
            pass

    def observe(self, now: float | None = None) -> list[RolloutSignal]:
        now_sec = now if now is not None else time.time()
        if now_sec >= self._next_discovery:
            self._discover(now_sec - self.DiscoverWindowSec)
            self._next_discovery = now_sec + 3.0

        signals: list[RolloutSignal] = []
        for path, tr in list(self._tracked.items()):
            try:
                size = os.path.getsize(path)
            except OSError:
                continue
            if size == tr["offset"]:
                continue
            try:
                with open(path, "rb") as f:
                    if size < tr["offset"]:
                        # 文件被重写：从头重读
                        tr["offset"] = 0
                        tr["pending"] = b""
                    f.seek(tr["offset"])
                    data = tr["pending"] + f.read()
                    tr["offset"] = size
            except OSError:
                continue
            start = 0
            complete = []
            for i, b in enumerate(data):
                if b != 0x0A:
                    continue
                if i > start:
                    complete.append(data[start:i].decode("utf-8", errors="replace"))
                start = i + 1
            tr["pending"] = data[start:]
            for line in complete:
                sig = self._parse_line(
                    line,
                    tr["session_id"],
                    tr["cwd"],
                    tr.get("subagent_id"),
                    tr.get("subagent_path"),
                    now_sec,
                )
                if sig is None:
                    continue
                signals.append(sig)
                # 主任务文件里出现子代理踪迹 → 在附近目录定位子代理 jsonl 并跟踪
                if tr.get("subagent_id") is None and sig.subagent_id and sig.subagent_path:
                    self._track_subagent(sig, os.path.dirname(path) or ".")
        signals.sort(key=lambda s: s.timestamp)
        return signals

    # ---------- 内部 ----------
    def _track(self, path: str, session_id: str, cwd: str, read_existing: bool,
               maximum_replay: int | None = None,
               subagent_id: str | None = None, subagent_path: str | None = None) -> None:
        full = os.path.abspath(path)
        if full in self._tracked:
            return
        try:
            size = os.path.getsize(full)
        except OSError:
            size = 0
        offset = 0
        if read_existing:
            offset = max(0, size - maximum_replay) if maximum_replay else 0
        self._tracked[full] = {
            "path": full,
            "session_id": session_id,
            "cwd": cwd,
            "offset": offset if read_existing else size,
            "pending": b"",
            "subagent_id": subagent_id,
            "subagent_path": subagent_path,
            "read_existing": read_existing,
            "maximum_replay": maximum_replay,
        }

    def _discover(self, modified_after: float) -> None:
        if not self._sessions_root.is_dir():
            return
        try:
            for path in self._sessions_root.rglob("*.jsonl"):
                try:
                    full = os.path.abspath(str(path))
                    if full in self._tracked:
                        continue
                    if path.stat().st_mtime < modified_after:
                        continue
                except OSError:
                    continue
                meta = self._read_metadata(str(path))
                if meta is None:
                    continue
                sid, cwd = meta
                self._track(str(path), sid, cwd, read_existing=True,
                            maximum_replay=self.MaxReplayBytes)
        except OSError:
            pass

    def _track_subagent(self, signal: RolloutSignal, nearby_dir: str) -> None:
        cid = signal.subagent_id
        if cid is None:
            return
        if any(tr.get("subagent_id") == cid for tr in self._tracked.values()):
            return
        found = None
        try:
            found = next(Path(nearby_dir).glob(f"*{cid}*.jsonl"), None)
            if found is None and self._sessions_root.is_dir():
                found = next(self._sessions_root.rglob(f"*{cid}*.jsonl"), None)
        except OSError:
            found = None
        if found is not None:
            self._track(str(found), signal.session_id, signal.cwd,
                        read_existing=True, subagent_id=cid,
                        subagent_path=signal.subagent_path)

    @staticmethod
    def _read_metadata(path: str) -> tuple[str, str] | None:
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as f:
                line = f.readline()
            if not line:
                return None
            doc = json.loads(line)
            if doc.get("type") != "session_meta":
                return None
            payload = doc.get("payload") or {}
            if not isinstance(payload, dict):
                return None
            src = payload.get("source")
            if isinstance(src, dict) and "subagent" in src:
                return None  # 子代理自己的会话文件，跳过
            sid = payload.get("id") or payload.get("session_id")
            cwd = payload.get("cwd")
            sid = str(sid).strip() if sid else ""
            cwd = str(cwd).strip() if cwd else ""
            if not sid or not cwd:
                return None
            return sid, cwd
        except Exception:
            return None

    @classmethod
    def _parse_line(cls, line: str, session_id: str, cwd: str,
                    tracked_subagent_id: str | None = None,
                    tracked_subagent_path: str | None = None,
                    now_sec: float | None = None) -> RolloutSignal | None:
        try:
            root = json.loads(line)
            payload = root.get("payload")
            if not isinstance(payload, dict):
                return None
            ptype = payload.get("type")
            if not ptype:
                return None
            timestamp = _iso_to_ms(root.get("timestamp")) or int((now_sec or time.time()) * 1000)
            root_type = root.get("type")

            def sig(lifecycle=None, activity=None, request_kind=None, summary=None,
                    user_prompt=None, latest_step=None, usage=None,
                    child_id=None, child_path=None, occurred_at=None):
                return RolloutSignal(
                    session_id=session_id, cwd=cwd,
                    lifecycle=lifecycle, activity=activity,
                    request_kind=request_kind, summary=summary,
                    user_prompt=user_prompt, latest_step=latest_step,
                    usage=usage,
                    subagent_id=child_id if child_id is not None else tracked_subagent_id,
                    subagent_path=child_path if child_path is not None else tracked_subagent_path,
                    timestamp=occurred_at if occurred_at is not None else timestamp,
                )

            # response_item：直接的工具调用 → 最新步骤
            if root_type == "response_item" and ptype in (
                "function_call", "custom_tool_call", "tool_search_call",
            ):
                name = payload.get("name") or ("tool_search" if ptype == "tool_search_call" else None)
                args = payload.get("arguments") or payload.get("input") or payload.get("arg")
                return sig(
                    lifecycle="running",
                    activity=_claude_tool_activity(name),
                    latest_step=cls._latest_step(name, _argument_summary(args)),
                )
            if root_type != "event_msg":
                return None

            if ptype in ("task_started", "turn_started"):
                return sig(lifecycle="running", activity="thinking")
            if ptype in ("task_complete", "turn_complete"):
                return sig(lifecycle="succeeded", activity=None)
            if ptype == "turn_aborted":
                return sig(lifecycle="interrupted", activity=None)
            if ptype in ("agent_reasoning", "agent_reasoning_raw_content",
                         "agent_reasoning_section_break", "context_compacted", "plan_update"):
                return sig(lifecycle="running", activity="thinking",
                           latest_step="更新计划" if ptype == "plan_update" else None)
            if ptype == "user_message":
                return sig(lifecycle="running", activity="thinking",
                           user_prompt=_clip(payload.get("message"), 240))
            if ptype == "token_count":
                usage = cls._parse_usage(payload)
                if usage is not None:
                    return sig(usage=usage)
                return None
            if ptype == "sub_agent_activity":
                if tracked_subagent_id is None:
                    child_id = payload.get("agent_thread_id")
                    child_path = payload.get("agent_path")
                    if child_id and child_path:
                        return sig(
                            lifecycle="running", activity="thinking",
                            child_id=str(child_id), child_path=str(child_path),
                            occurred_at=_millis(payload, "occurred_at_ms"),
                        )
                return None
            if ptype in ("exec_command_begin", "terminal_interaction"):
                return sig(lifecycle="running", activity="executing",
                           latest_step=_clip(_display(payload, "command") or _display(payload, "cmd")))
            if ptype in ("patch_apply_begin", "patch_apply_updated"):
                return sig(lifecycle="running", activity="editing", latest_step="apply_patch")
            if ptype in ("mcp_tool_call_begin", "dynamic_tool_call_request"):
                return sig(
                    lifecycle="running", activity="executing",
                    latest_step=cls._latest_step(
                        payload.get("tool_name") or payload.get("name"),
                        _clip(payload.get("reason") or payload.get("message")),
                    ),
                )
            if ptype in ("web_search_begin", "web_search_end"):
                detail = _clip(payload.get("query"))
                return sig(lifecycle="running", activity="browsing",
                           latest_step=f"Web {detail}" if detail else "Web")
            if ptype in ("image_generation_begin", "image_generation_end", "view_image_tool_call"):
                return sig(lifecycle="running", activity="reading", latest_step="Image")
            if ptype in ("exec_command_end", "patch_apply_end",
                         "mcp_tool_call_end", "dynamic_tool_call_response"):
                return sig(lifecycle="running", activity="thinking")
            if ptype in ("request_user_input", "elicitation_request"):
                return sig(
                    lifecycle="waitingAnswer", activity="thinking",
                    request_kind="question",
                    summary=_clip(payload.get("prompt") or payload.get("message")),
                )
            if ptype in ("exec_approval_request", "apply_patch_approval_request", "request_permissions"):
                return sig(
                    lifecycle="waitingApproval", activity="executing",
                    request_kind="approval",
                    summary=_clip(payload.get("reason") or payload.get("message")),
                )
            return None
        except Exception:
            return None

    @staticmethod
    def _parse_usage(payload: dict) -> dict | None:
        info = payload.get("info")
        total = None
        if isinstance(info, dict):
            total = info.get("total_token_usage")
        if not isinstance(total, dict):
            total = payload.get("total_token_usage")
        if not isinstance(total, dict):
            return None

        def _int(*keys):
            for k in keys:
                try:
                    return int(total.get(k) or 0)
                except Exception:
                    pass
            return 0

        inp = _int("input_tokens")
        cached = min(inp, _int("cached_input_tokens", "cache_read_input_tokens"))
        output = _int("output_tokens")
        reasoning = _int("reasoning_output_tokens")
        total_count = _int("total_tokens") or inp + output
        return {"input": inp, "cached": cached, "output": output,
                "reasoning": reasoning, "total": total_count}

    @staticmethod
    def _latest_step(name: str | None, detail: str | None) -> str | None:
        if not name and not detail:
            return None
        if name and detail:
            return _clip(f"{name} {detail}")
        return _clip(name or detail)

    @staticmethod
    def subagent_name_from_path(path: str | None) -> str:
        if not path:
            return "Codex 子代理"
        raw = Path(path.replace("\\", "/")).name
        raw = raw.replace("_", " ").replace("-", " ").strip()
        if not raw:
            return "Codex 子代理"
        return raw[:1].upper() + raw[1:]