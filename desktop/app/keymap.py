"""按键映射管理：手机端按键布局 ↔ 电脑端快捷键。

keymap.json 定义手机屏幕上渲染的按键与映射行为：
- type=key     : 单键/方向键（服务端直接 press/release，支持按住重复）
- type=combo   : 组合快捷键（如 Ctrl+C、Win+D），一次性触发
- type=action  : 内置动作（home/tv/copy/paste/screenshot/enter）

面板可编辑各键的 label、映射，保存后向手机端推送新布局。
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, List, Optional

# 出厂默认布局：十字方向+确认 / 功能键（终极确认、换行、Home/TV、截图）/ 剪贴板 / 1-4 数字键
DEFAULT_KEYMAP: Dict[str, Any] = {
    "version": 3,
    "keys": [
        {"id": "up", "label": "上", "display": "▲", "group": "dpad", "type": "key", "mapping": "up"},
        {"id": "down", "label": "下", "display": "▼", "group": "dpad", "type": "key", "mapping": "down"},
        {"id": "left", "label": "左", "display": "◀", "group": "dpad", "type": "key", "mapping": "left"},
        {"id": "right", "label": "右", "display": "▶", "group": "dpad", "type": "key", "mapping": "right"},
        {"id": "enter", "label": "确认", "display": "↵", "group": "dpad", "type": "key", "mapping": "enter"},
        {"id": "ctrl_enter", "label": "终极确认", "display": "Ctrl+↵", "group": "fn", "type": "combo", "mapping": ["ctrl", "enter"]},
        {"id": "shift_enter", "label": "换行", "display": "Shift+↵", "group": "fn", "type": "combo", "mapping": ["shift", "enter"]},
        {"id": "home", "label": "桌面", "display": "Win+D", "group": "fn", "type": "action", "mapping": "home"},
        {"id": "tv", "label": "任务视图", "display": "Win+Tab", "group": "fn", "type": "action", "mapping": "tv"},
        {"id": "shot", "label": "区域截图", "display": "Win+Shift+S", "group": "fn", "type": "combo", "mapping": ["win", "shift", "s"]},
        {"id": "copy", "label": "复制", "display": "Ctrl+C", "group": "clip", "type": "action", "mapping": "copy"},
        {"id": "cut", "label": "剪切", "display": "Ctrl+X", "group": "clip", "type": "action", "mapping": "cut"},
        {"id": "paste", "label": "粘贴", "display": "Ctrl+V", "group": "clip", "type": "action", "mapping": "paste"},
        {"id": "n1", "label": "1", "display": "1", "group": "num", "type": "key", "mapping": "1"},
        {"id": "n2", "label": "2", "display": "2", "group": "num", "type": "key", "mapping": "2"},
        {"id": "n3", "label": "3", "display": "3", "group": "num", "type": "key", "mapping": "3"},
        {"id": "n4", "label": "4", "display": "4", "group": "num", "type": "key", "mapping": "4"},
    ],
}

# 内置动作别名 → 注入器方法
ACTIONS = {"home": "home", "tv": "tv", "copy": "copy", "paste": "paste", "cut": "cut", "screenshot": "screenshot", "enter": "enter"}

VALID_TYPES = {"key", "combo", "action"}


class KeymapError(ValueError):
    pass


class KeymapStore:
    def __init__(self, path: Path):
        self.path = path

    def load(self) -> Dict[str, Any]:
        if not self.path.exists():
            self.save(DEFAULT_KEYMAP)
            return json.loads(json.dumps(DEFAULT_KEYMAP))
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return json.loads(json.dumps(DEFAULT_KEYMAP))
        if not isinstance(data, dict) or not isinstance(data.get("keys"), list):
            return json.loads(json.dumps(DEFAULT_KEYMAP))
        return data

    def save(self, data: Dict[str, Any]) -> None:
        tmp = self.path.with_name(self.path.name + ".tmp")
        tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(self.path)

    def document(self) -> List[Dict[str, Any]]:
        return self.load()["keys"]

    # ---------- 面板操作 ----------
    def set_mapping(self, key_id: str, mapping, label: Optional[str] = None) -> Dict[str, Any]:
        """更新一个键的映射（mapping 可以是 str 或 list[str]）。

        列表形态（组合键）自动将键位类型改为 combo；字符串保持原类型。
        """
        data = self.load()
        keys: List[Dict[str, Any]] = data["keys"]
        found = next((k for k in keys if k["id"] == key_id), None)
        if found is None:
            raise KeymapError(f"未知键位: {key_id}")
        if isinstance(mapping, list) and mapping:
            found["type"] = "combo"
            found["mapping"] = self._normalize_mapping("combo", mapping)
        else:
            found["mapping"] = self._normalize_mapping(found["type"], mapping)
        if label is not None:
            found["label"] = label
        self.save(data)
        return found

    def add_key(self, key_id: str, label: str, display: str, kind: str, mapping) -> Dict[str, Any]:
        if kind not in VALID_TYPES:
            raise KeymapError(f"非法类型: {kind}")
        data = self.load()
        keys: List[Dict[str, Any]] = data["keys"]
        if any(k["id"] == key_id for k in keys):
            raise KeymapError(f"键位 id 已存在: {key_id}")
        item = {
            "id": key_id,
            "label": label,
            "display": display or label,
            "type": kind,
            "mapping": self._normalize_mapping(kind, mapping),
        }
        keys.append(item)
        self.save(data)
        return item

    def remove_key(self, key_id: str) -> None:
        data = self.load()
        keys: List[Dict[str, Any]] = data["keys"]
        data["keys"] = [k for k in keys if k["id"] != key_id]
        self.save(data)

    def reset(self) -> None:
        self.save(DEFAULT_KEYMAP)

    @staticmethod
    def _normalize_mapping(kind: str, mapping) -> Any:
        if kind == "combo":
            if not isinstance(mapping, list) or not all(isinstance(x, str) for x in mapping):
                raise KeymapError("组合键映射需为按键名数组，如 [\"ctrl\",\"c\"]")
            return list(mapping)
        if not isinstance(mapping, str) or not mapping:
            raise KeymapError("映射需为按键名称字符串")
        return mapping