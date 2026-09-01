"""Codex 用量加载器（AgentPager CodexUsageLoader 的 Python 移植）。

从 ~/.codex/sessions 与 ~/.codex/archived_sessions 的 rollout-*.jsonl 中：
  - LatestQuota：从最新文件的 token_count 事件的 rate_limits 提取额度窗口
    （primary = 5 小时窗口，secondary = 周窗口：used_percent / window_minutes /
     resets_at / plan_type / limit_id）
  - Daily：聚合近 90 天的每日 token 用量（total_token_usage 差分或 last_token_usage）

输出与 AgentPager UsageSnapshot wire 结构一致（毫秒时间戳）。
"""
from __future__ import annotations

import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path

HISTORY_DAYS = 90
TAIL_SIZE = 256 * 1024
TAIL_SIZE_QUOTA = 512 * 1024


def _codex_home() -> Path:
    return Path(os.environ.get("CODEX_HOME") or (Path.home() / ".codex"))


def _iso_to_epoch_ms(value: str | None) -> int | None:
    if not value:
        return None
    try:
        v = value.strip()
        if v.endswith("Z"):
            v = v[:-1] + "+00:00"
        dt = datetime.fromisoformat(v)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return int(dt.timestamp() * 1000)
    except Exception:
        return None


def _num(parent: dict, *keys):
    for key in keys:
        if key not in parent:
            continue
        try:
            return float(parent[key])
        except (TypeError, ValueError):
            return None
    return None


def _int_val(parent: dict, *keys) -> int:
    for key in keys:
        try:
            return int(parent.get(key) or 0)
        except (TypeError, ValueError):
            continue
    return 0


def _scalar(parent: dict, key: str) -> str | None:
    if key not in parent:
        return None
    v = parent[key]
    if isinstance(v, str):
        return v
    try:
        return json.dumps(v, ensure_ascii=False)
    except Exception:
        return str(v)


def _window_label(minutes: int) -> str:
    days, rem = divmod(minutes, 1440)
    hours, rest = divmod(rem, 60)
    if days > 0 and hours == 0 and rest == 0:
        return f"{days}d"
    if days > 0 and hours > 0:
        return f"{days}d {hours}h"
    if hours > 0 and rest == 0:
        return f"{hours}h"
    if hours > 0:
        return f"{hours}h {rest}m"
    return f"{minutes}m"


def _tail_lines(path: str, maximum: int):
    """从文件尾部读取（尽可能完整行长），生成器逆序遍历用。"""
    try:
        size = os.path.getsize(path)
    except OSError:
        return
    start = max(0, size - maximum)
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            f.seek(start)
            if start > 0:
                f.readline()  # 丢弃不完整的首行
            for line in f:
                yield line
    except OSError:
        return


class CodexUsageLoader:
    def __init__(self, home: Path | None = None):
        self._home = home or _codex_home()

    # ---------- 公开 ----------
    def load(self, now: datetime | None = None) -> dict | None:
        current = now or datetime.now()
        roots = [self._home / "sessions", self._home / "archived_sessions"]
        candidates: list[dict] = []
        seen: set[str] = set()
        for root in roots:
            if not root.is_dir():
                continue
            try:
                for p in root.rglob("rollout-*.jsonl"):
                    full = os.path.abspath(str(p))
                    low = full.lower()
                    if low in seen:
                        continue
                    seen.add(low)
                    try:
                        st = p.stat()
                    except OSError:
                        continue
                    candidates.append({
                        "path": full,
                        "mtime_sec": st.st_mtime,
                        "mtime_ms": int(st.st_mtime * 1000),
                        "size": st.st_size,
                    })
            except OSError:
                continue
        if not candidates:
            return None

        quota = None
        for c in sorted(candidates, key=lambda x: -x["mtime_sec"]):
            quota = self._latest_quota(c)
            if quota is not None:
                break

        start_ms = int((current.replace(hour=0, minute=0, second=0, microsecond=0)
                        - timedelta(days=HISTORY_DAYS - 1)).timestamp() * 1000)
        buckets: dict[str, dict] = {}

        def add(date_key: str, usage: dict) -> None:
            b = buckets.get(date_key)
            if b is None:
                buckets[date_key] = dict(usage)
            else:
                for k in usage:
                    b[k] = b.get(k, 0) + usage[k]

        for c in candidates:
            if c["mtime_ms"] < start_ms:
                continue
            if c["size"] <= TAIL_SIZE:
                self._aggregate_file(c["path"], start_ms, add)
            else:
                latest = self._latest_cumulative(c["path"])
                if latest and latest[0] >= start_ms and not self._is_empty(latest[1]):
                    add(self._day_key(latest[0]), latest[1])
        if quota is None and not buckets:
            return None

        daily = []
        for offset in range(HISTORY_DAYS):
            d = datetime.fromtimestamp(start_ms / 1000) + timedelta(days=offset)
            key = d.strftime("%Y-%m-%d")
            u = buckets.get(key) or {"input": 0, "cached": 0, "output": 0,
                                     "reasoning": 0, "total": 0}
            daily.append({
                "date": key,
                "inputTokens": u["input"],
                "cachedInputTokens": u["cached"],
                "outputTokens": u["output"],
                "reasoningOutputTokens": u["reasoning"],
                "totalTokens": u["total"],
            })

        latest_mtime = max((c["mtime_ms"] for c in candidates), default=0)
        return {
            "capturedAt": (quota or {}).get("capturedAt") or latest_mtime,
            "planType": (quota or {}).get("planType"),
            "limitId": (quota or {}).get("limitId"),
            "windows": (quota or {}).get("windows") or [],
            "daily": daily,
        }

    # ---------- 内部 ----------
    def _latest_quota(self, candidate: dict) -> dict | None:
        for line in reversed(list(_tail_lines(candidate["path"], TAIL_SIZE_QUOTA))):
            if '"token_count"' not in line:
                continue
            try:
                root = json.loads(line)
                if root.get("type") != "event_msg":
                    continue
                payload = root.get("payload") or {}
                if not isinstance(payload, dict) or payload.get("type") != "token_count":
                    continue
                limits = payload.get("rate_limits")
                if not isinstance(limits, dict):
                    info = payload.get("info")
                    if isinstance(info, dict):
                        limits = info.get("rate_limits")
                if not isinstance(limits, dict):
                    continue
                windows = []
                for key in ("primary", "secondary"):
                    w = limits.get(key)
                    if not isinstance(w, dict):
                        continue
                    used = _num(w, "used_percent")
                    minutes = _num(w, "window_minutes")
                    if used is None or minutes is None:
                        continue
                    try:
                        minutes_i = int(minutes)
                    except (TypeError, ValueError):
                        continue
                    resets_sec = _num(w, "resets_at")
                    windows.append({
                        "key": key,
                        "label": _window_label(minutes_i),
                        "usedPercentage": used,
                        "remainingPercentage": max(0.0, 100.0 - used),
                        "windowMinutes": minutes_i,
                        "resetsAt": int(resets_sec * 1000) if resets_sec else None,
                    })
                if not windows:
                    continue
                captured = _iso_to_epoch_ms(root.get("timestamp")) or candidate["mtime_ms"]
                return {
                    "capturedAt": captured,
                    "planType": _scalar(limits, "plan_type"),
                    "limitId": _scalar(limits, "limit_id"),
                    "windows": windows,
                }
            except Exception:
                continue
        return None

    def _aggregate_file(self, path: str, start_ms: int, add) -> None:
        previous: dict | None = None
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as f:
                for line in f:
                    if '"token_count"' not in line:
                        continue
                    try:
                        root = json.loads(line)
                        if root.get("type") != "event_msg":
                            continue
                        ts = _iso_to_epoch_ms(root.get("timestamp"))
                        if ts is None or ts < start_ms:
                            continue
                        payload = root.get("payload") or {}
                        if not isinstance(payload, dict) or payload.get("type") != "token_count":
                            continue
                        info = payload.get("info")
                        base = info if isinstance(info, dict) else payload
                        total = self._raw_usage_v(base, "total_token_usage") \
                            or self._raw_usage_v(payload, "total_token_usage")
                        last = self._raw_usage_v(base, "last_token_usage") \
                            or self._raw_usage_v(payload, "last_token_usage")
                        delta = dict(last) if last else self._subtract(total, previous)
                        if total is not None:
                            previous = total
                        if delta is None or self._is_empty(delta):
                            continue
                        add(self._day_key(ts), delta)
                    except Exception:
                        continue
        except OSError:
            pass

    def _latest_cumulative(self, path: str) -> tuple[int, dict] | None:
        for line in reversed(list(_tail_lines(path, TAIL_SIZE))):
            if '"token_count"' not in line:
                continue
            try:
                root = json.loads(line)
                ts = _iso_to_epoch_ms(root.get("timestamp"))
                payload = root.get("payload") or {}
                if ts is None or not isinstance(payload, dict):
                    continue
                info = payload.get("info")
                base = info if isinstance(info, dict) else payload
                usage = self._raw_usage_v(base, "total_token_usage") \
                    or self._raw_usage_v(payload, "total_token_usage")
                if usage is not None:
                    return ts, usage
            except Exception:
                continue
        return None

    @staticmethod
    def _raw_usage_v(parent: dict, key: str) -> dict | None:
        v = parent.get(key)
        if not isinstance(v, dict):
            return None
        inp = _int_val(v, "input_tokens")
        cached = min(inp, _int_val(v, "cached_input_tokens", "cache_read_input_tokens"))
        output = _int_val(v, "output_tokens")
        reasoning = _int_val(v, "reasoning_output_tokens")
        total = _int_val(v, "total_tokens") or (inp + output)
        return {"input": inp, "cached": cached, "output": output,
                "reasoning": reasoning, "total": total}

    @staticmethod
    def _subtract(cur: dict | None, prev: dict | None) -> dict | None:
        if cur is None:
            return None
        if prev is None:
            return dict(cur)

        def d(k):
            return max(0, cur.get(k, 0) - prev.get(k, 0))
        return {"input": d("input"), "cached": d("cached"), "output": d("output"),
                "reasoning": d("reasoning"), "total": d("total")}

    @staticmethod
    def _is_empty(u: dict) -> bool:
        return not any(u.get(k) for k in ("input", "cached", "output", "reasoning", "total"))

    @staticmethod
    def _day_key(ts_ms: int) -> str:
        return datetime.fromtimestamp(ts_ms / 1000).strftime("%Y-%m-%d")