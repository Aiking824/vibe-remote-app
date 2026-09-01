"""语音接收与播放：PCM16(Little-endian) → 重采样 → 立体声 → 播放到虚拟音频设备。

链路（对照 vibe-flow）：
  手机 WS 二进制帧(PCM16) -> AudioSink -> CABLE Input -> CABLE Output -> 语音工具
录音会话由 hold_start / hold_end 控制，内部用独立工作线程避免阻塞异步事件循环。
"""

from __future__ import annotations

import logging
import queue
import threading
import time
from typing import Callable, Optional, Sequence

import numpy as np

try:
    import sounddevice as sd
except ImportError:  # 依赖未安装时允许模块被导入（服务启动时会给出明确提示）
    sd = None

LOG = logging.getLogger("vibe-remote.audio")


def _linear_resample(x: np.ndarray, src_rate: int, dst_rate: int) -> np.ndarray:
    """线性插值重采样（单声道 float32）。"""
    if src_rate == dst_rate:
        return x
    n_src = x.size
    n_dst = max(1, int(round(n_src * dst_rate / src_rate)))
    idx_src = np.linspace(0.0, n_src - 1.0, num=n_dst)
    return np.interp(idx_src, np.arange(n_src, dtype=float), x)


class AudioSink:
    """队列 + 工作线程；所有事件通过 on_event 回调（线程安全地抛回事件循环）。"""

    def __init__(
        self,
        device_match: str = "CABLE Input",
        device_rate: int = 0,
        provider: Optional[dict] = None,
        long_press_ms: int = 500,
        on_event: Optional[Callable[[dict], None]] = None,
        keys: Optional[object] = None,
        level_interval: float = 0.1,
    ):
        self.device_match = device_match
        self.device_rate = device_rate
        self.provider = provider or {}
        self.long_press_ms = long_press_ms
        self.on_event = on_event
        self.keys = keys
        self.level_interval = level_interval

        self._q: "queue.Queue[tuple]" = queue.Queue()
        self._thread = None
        self._session = {"active": False}

    # ---------- 对外接口（异步线程调用，仅入队） ----------
    def start(self, rate: int) -> None:
        self._q.put(("start", int(rate) if rate else 16000))

    def stop(self) -> None:
        self._q.put(("stop", None))

    def feed_pcm(self, data: bytes, rate: int) -> None:
        self._q.put(("pcm", int(rate) if rate else 16000, data))

    def start_thread(self) -> None:
        if self._thread is None or not self._thread.is_alive():
            self._thread = threading.Thread(target=self._worker, daemon=True)
            self._thread.start()

    # ---------- 内部 ----------
    def _emit(self, event: dict) -> None:
        if self.on_event:
            try:
                self.on_event(event)  # 由调用方保证线程安全（call_soon_threadsafe）
            except Exception:
                pass

    def _find_output(self):
        """返回 (index, name, samplerate) 或 None。优先匹配 device_match，否则默认输出。"""
        if sd is None:
            LOG.error("sounddevice 未安装，无法播放音频")
            return None
        try:
            all_devs = sd.query_devices()
        except Exception as e:
            LOG.error("查询音频设备失败: %s", e)
            return None
        LOG.info("查找输出设备 match='%s'，共 %d 个设备", self.device_match, len(all_devs))
        for idx, dev in enumerate(all_devs):
            name = str(dev.get("name", ""))
            if (
                self.device_match
                and self.device_match.lower() in name.lower()
                and dev.get("max_output_channels", 0) > 0
            ):
                LOG.info("匹配到输出设备: idx=%d name='%s' rate=%d", idx, name, dev.get("default_samplerate", 48000))
                return idx, name, int(dev.get("default_samplerate", 48000))
        # 列出所有输出设备帮助诊断
        for idx, dev in enumerate(all_devs):
            name = str(dev.get("name", ""))
            oc = dev.get("max_output_channels", 0)
            if oc > 0:
                LOG.warning("可用输出设备: idx=%d name='%s' out_ch=%d", idx, name, oc)
        # 回退到系统默认输出设备
        try:
            output = sd.default.device
            if isinstance(output, (list, tuple)):
                output = output[1]
            if output is not None:
                dev = sd.query_devices(output)
                LOG.warning("回退到默认输出设备: idx=%d name='%s'", output, dev.get("name", ""))
                return output, str(dev.get("name", "")), int(dev.get("default_samplerate", 48000))
        except Exception:
            pass
        LOG.error("未找到任何可用的输出设备（CABLE Input 未安装？）")
        return None

    def _provider_tap(self) -> None:
        """按一下微信输入法语音快捷键（默认 Ctrl+Shift+Win：按下启动、再按停止）。"""
        shortcut = self.provider.get("shortcut") or ["ctrl", "shift", "win"]
        down_ms = max(10, int(self.provider.get("tap_down_ms", 40))) / 1000.0
        if self.keys:
            self.keys.provider_toggle(shortcut, down_ms=down_ms)

    def _worker(self) -> None:
        stream = None
        out_rate = 0
        capturing = False
        last_level_at = 0.0

        while True:
            item = self._q.get()
            kind = item[0]

            if kind == "start":
                if capturing:
                    continue  # 忽略重复开始
                info = self._find_output()
                if info is None:
                    LOG.error("AudioSink: 未找到输出设备，无法启动")
                    self._emit(
                        {
                            "type": "session",
                            "phase": "error",
                            "message": f"未找到音频设备：{self.device_match}（请检查 VB-CABLE 或修改音频设备配置）",
                        }
                    )
                    continue
                idx, name, native_rate = info
                out_rate = self.device_rate or native_rate
                try:
                    stream = sd.OutputStream(device=idx, samplerate=out_rate, channels=2, dtype="float32")
                    stream.start()
                    LOG.info("AudioSink: OutputStream 已打开 idx=%d name='%s' rate=%d", idx, name, out_rate)
                except Exception as exc:
                    LOG.error("AudioSink: 打开 OutputStream 失败: %s", exc)
                    self._emit(
                        {"type": "session", "phase": "error", "message": f"打开音频设备失败：{exc}"}
                    )
                    stream = None
                    continue
                # 启动提供者快捷键（微信 Ctrl+Shift+Win：按一下开始录音）
                delay = int(self.provider.get("start_delay_ms", 80)) / 1000.0
                self._emit({"type": "session", "phase": "starting"})
                time.sleep(delay)
                try:
                    LOG.info("AudioSink: 发送第一次 provider_toggle (启动语音)")
                    self._provider_tap()  # 按一下启动语音输入
                except Exception as e:
                    LOG.error("AudioSink: provider_tap(启动) 失败: %s", e)
                capturing = True
                self._emit(
                    {
                        "type": "session",
                        "phase": "capturing",
                        "device": name,
                        "rate": out_rate,
                    }
                )

            elif kind == "stop":
                if not capturing:
                    continue
                drain = int(self.provider.get("drain_ms", 200))
                LOG.info("AudioSink: stop 收到，drain=%dms 后发送 provider_toggle(结束)", drain)
                try:
                    time.sleep(drain / 1000.0)
                    LOG.info("AudioSink: 发送第二次 provider_toggle (结束语音)")
                    self._provider_tap()  # 再按一下结束语音输入
                except Exception as e:
                    LOG.error("AudioSink: provider_tap(结束) 失败: %s", e)
                if stream is not None:
                    try:
                        stream.stop()
                        stream.close()
                    except Exception:
                        pass
                    stream = None
                capturing = False
                self._emit({"type": "session", "phase": "idle"})

            elif kind == "pcm":
                rate, data = item[1], item[2]
                if not capturing or stream is None or sd is None:
                    continue
                try:
                    samples = np.frombuffer(data, dtype=np.int16).astype(np.float32) / 32768.0
                    if samples.size == 0:
                        continue
                    samples = _linear_resample(samples, rate, out_rate)
                    stereo = np.column_stack((samples, samples))
                    stream.write(stereo)
                    rms = float(np.sqrt(np.mean(np.square(samples))))
                    level = min(1.0, round(rms * 3.0, 3))
                    now = time.monotonic()
                    if now - last_level_at >= self.level_interval:
                        last_level_at = now
                        self._emit({"type": "level", "v": level})
                except Exception:
                    # 设备被拔出等运行期异常：结束会话并复位
                    if stream is not None:
                        try:
                            stream.stop()
                            stream.close()
                        except Exception:
                            pass
                    stream = None
                    capturing = False
                    self._emit({"type": "session", "phase": "error", "message": "音频播放中断，请检查音频设备"})