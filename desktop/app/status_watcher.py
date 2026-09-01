"""监听 Claude Code/AI 工具状态文件（.vico/status.json）并广播给手机端。

状态文件由 hooks/status_push.py 写入；格式：
  {"state": "THINKING", "message": "...", "source": "claude", "project": "...", ...}
"""

from __future__ import annotations

import asyncio
import json
from pathlib import Path
from typing import Callable, Optional


class StatusWatcher:
    def __init__(
        self,
        path: str,
        interval: float = 0.3,
        on_change: Optional[Callable[[dict], None]] = None,
    ):
        self.path = Path(path)
        self.interval = interval
        self.on_change = on_change
        self._last = None
        self._sent_initial = False

    def read(self) -> Optional[dict]:
        try:
            raw = self.path.read_text(encoding="utf-8").strip()
        except OSError:
            return None
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            return None
        if not isinstance(data, dict):
            return None
        return {
            "state": str(data.get("state") or "READY").upper(),
            "message": str(data.get("message") or ""),
            "source": str(data.get("source") or ""),
            "project": str(data.get("project") or ""),
        }

    async def run(self) -> None:
        while True:
            current = self.read()
            if current != self._last:
                self._last = current
                if current is not None and self.on_change:
                    self.on_change(current)
            elif current is None and not self._sent_initial:
                # 尚无状态文件时也推一次 READY，方便手机端初始化
                self._sent_initial = True
                if self.on_change:
                    self.on_change({"state": "READY", "message": "尚未收到状态", "source": "", "project": ""})
            await asyncio.sleep(self.interval)