"""配对管理：一次性 token + 两位确认码 + 会话 ID。

参考 remote-mic-app 的扫码直连与两位确认码授权流程。
token 与确认码只用于本地局域网配对，日志中不允许打印。
"""

from __future__ import annotations

import secrets
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Optional


@dataclass
class Pairing:
    token: str
    code: str
    expires_at: float
    consumed: bool = False


class PairingManager:
    """同一时刻只保留一个有效配对；生成新配对会使旧配对失效。"""

    def __init__(self, ttl_sec: int = 600):
        self.ttl_sec = ttl_sec
        self._lock = threading.Lock()
        self._pairing: Optional[Pairing] = None

    def new_pairing(self) -> Pairing:
        with self._lock:
            self._pairing = Pairing(
                token=secrets.token_hex(16),
                code=f"{secrets.randbelow(100):02d}",
                expires_at=time.monotonic() + self.ttl_sec,
            )
            return self._pairing

    def current(self) -> Optional[Pairing]:
        """返回当前有效配对；已过期则自动清空。"""
        with self._lock:
            p = self._pairing
            if p is None:
                return None
            if p.expires_at < time.monotonic():
                self._pairing = None
                return None
            return p

    def validate_token(self, token: str) -> bool:
        """hello 阶段：token 是否存在且未消费。"""
        with self._lock:
            p = self._pairing
            return bool(
                p
                and not p.consumed
                and p.token == token
                and p.expires_at >= time.monotonic()
            )

    def consume(self, token: str, code: str) -> Optional[Pairing]:
        """confirm 阶段：令牌与确认码都匹配则消费该配对。"""
        with self._lock:
            p = self._pairing
            if not (
                p
                and not p.consumed
                and p.token == token
                and p.expires_at >= time.monotonic()
            ):
                return None
            if p.code != code:
                return None
            p.consumed = True
            return p

    def new_session_id(self) -> str:
        return uuid.uuid4().hex