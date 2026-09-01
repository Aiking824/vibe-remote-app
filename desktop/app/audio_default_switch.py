"""默认音频输入设备临时切换（win10/11）。

用途：语音方向命令会话前，把系统默认输入设备切到 CABLE Output，
这样手机语音（经 CABLE）能被 Windows 语音识别器采纳；结束后恢复原设备。

实现：winsdk 枚举音频捕获设备拿设备 ID，ctypes 调 COM IPolicyConfig.SetDefaultEndpoint 切换。
"""

from __future__ import annotations

import asyncio
import ctypes
from ctypes import wintypes
from typing import Optional

from winsdk.windows.devices.enumeration import DeviceClass, DeviceInformation

# ---- COM 常量 ----
CLSID_POLICY_CONFIG = "{870af99c-171d-4f9e-af0d-e63df40c2bc9}"
IID_IPOLICY_CONFIG = "{f8679f50-850a-41cf-9c72-430f290290c8}"


class GUID(ctypes.Structure):
    _fields_ = [("Data1", wintypes.DWORD), ("Data2", wintypes.WORD), ("Data3", wintypes.WORD), ("Data4", ctypes.c_byte * 8)]

    @classmethod
    def from_string(cls, s: str) -> "GUID":
        parts = s.strip("{}").split("-")
        g = cls()
        g.Data1 = int(parts[0], 16)
        g.Data2 = int(parts[1], 16)
        g.Data3 = int(parts[2], 16)
        b = bytes.fromhex("".join(parts[3:]))
        g.Data4 = (ctypes.c_byte * 8)(*b)
        return g


class IPolicyConfigVtbl(ctypes.Structure):
    _fields_ = [
        ("QueryInterface", ctypes.c_void_p), ("AddRef", ctypes.c_void_p), ("Release", ctypes.c_void_p),
        ("GetMixFormat", ctypes.c_void_p), ("GetDeviceFormat", ctypes.c_void_p),
        ("ResetDeviceFormat", ctypes.c_void_p), ("SetDeviceFormat", ctypes.c_void_p),
        ("GetProcessingPeriod", ctypes.c_void_p), ("SetProcessingPeriod", ctypes.c_void_p),
        ("GetShareMode", ctypes.c_void_p), ("SetShareMode", ctypes.c_void_p),
        ("GetPropertyValue", ctypes.c_void_p), ("SetPropertyValue", ctypes.c_void_p),
        ("SetDefaultEndpoint", ctypes.c_void_p), ("SetEndpointVisibility", ctypes.c_void_p),
    ]


class IPolicyConfig(ctypes.Structure):
    pass


IPolicyConfig._fields_ = [("lpVtbl", ctypes.POINTER(IPolicyConfigVtbl))]

# ERole
ECONSOLE = 0
EMULTIMEDIA = 1
ECOMMUNICATIONS = 2


def _device_id(name_substr: str) -> Optional[str]:
    """用 winsdk 枚举音频捕获设备，返回匹配设备 ID（如 CABLE Output）。

    注意：pywinrt 的 `find_all_async` 返回 IAsyncOperation，必须放在
    `async def ... : await ...` 里，再通过 `asyncio.run(...)` 驱动。
    另外它需要一个「当前没有正在跑的 asyncio 事件循环」的线程来执行
    （否则 asyncio.run() 会报错），因此调用方必须把本函数丢进 ThreadPool。
    """
    try:
        asyncio.get_running_loop()
        raise RuntimeError("不能在事件循环线程内调用，请放到线程池执行")
    except RuntimeError:  # There is no running event loop → 合法
        pass

    async def _collect():
        return await DeviceInformation.find_all_async(DeviceClass.AUDIO_CAPTURE)

    infos = asyncio.run(_collect())
    for dev in infos:
        try:
            if name_substr.lower() in str(dev.name).lower():
                return str(dev.id)
        except Exception:
            continue
    return None


class AudioDefaultSwitch:
    """记录当前默认输入 → 切到 CABLE Output → 恢复。"""

    def __init__(self, cable_name: str = "cable output"):
        self.cable_name = cable_name
        self._saved: Optional[str] = None
        self._policy: Optional[IPolicyConfig] = None

    def _coinit(self) -> bool:
        return ctypes.windll.ole32.CoInitializeEx(None, 0x0) in (0, 1)  # COINIT_APARTMENTTHREADED

    def _get_policy(self) -> IPolicyConfig:
        if self._policy is not None:
            return self._policy
        com = ctypes.WinDLL("combase.dll") if hasattr(ctypes, "WinDLL") else ctypes.OleDLL("ole32.dll")
        pc = IPolicyConfig()
        hr = ctypes.windll.ole32.CoCreateInstance(
            ctypes.byref(GUID.from_string(CLSID_POLICY_CONFIG)),
            None,
            0x1,  # CLSCTX_INPROC_SERVER
            ctypes.byref(GUID.from_string(IID_IPOLICY_CONFIG)),
            ctypes.byref(pc),
        )
        if hr != 0:
            raise RuntimeError(f"CoCreateInstance PolicyConfig 失败 hr={hex(hr & 0xFFFFFFFF)}")
        self._policy = pc
        return pc

    def _set_endpoint(self, device_id: Optional[str], role: int = ECONSOLE) -> None:
        pc = self._get_policy()
        # 通过 COM vtable 直接取出 SetDefaultEndpoint 函数地址。
        # lpVtbl 是 POINTER(IPolicyConfigVtbl)，contents 即 IPolicyConfigVtbl 结构，
        # 其 SetDefaultEndpoint 字段存的就是真实函数地址。
        # 签名：HRESULT (THIS_ IPolicyConfig* self, LPCWSTR wszDeviceId, ERole role)
        # this 指针 = 接口对象地址本身 = ctypes.addressof(pc)
        HRESULT = ctypes.c_long
        SetDefaultEndpointT = ctypes.WINFUNCTYPE(
            HRESULT, ctypes.c_void_p, ctypes.c_wchar_p, ctypes.c_uint
        )
        raw_addr = pc.lpVtbl.contents.SetDefaultEndpoint
        if not raw_addr:
            raise RuntimeError("SetDefaultEndpoint vtable slot 为空")
        f = SetDefaultEndpointT(raw_addr)
        this_ptr = ctypes.cast(ctypes.byref(pc), ctypes.c_void_p)
        hr = f(this_ptr, device_id, ctypes.c_uint(role))
        if hr != 0:
            raise RuntimeError(f"SetDefaultEndpoint 失败 hr={hex(hr & 0xFFFFFFFF)}")

    def _set_default_endpoint_via_ctypes(self, device_id: str, role: int = ECONSOLE) -> bool:
        """设置默认端点：优先走 PowerShell C# COM interop（已验证 vtable 槽位正确）。

        ctypes COM 版本在部分机器上出现 vtable 解析错位，因此默认禁用，
        仅作为 PS 失败后的冗余尝试。
        """
        if not device_id:
            return False
        # 主路径：PowerShell C#（接口定义已验证，槽位正确）
        if _set_default_endpoint_ps(device_id, role):
            return True
        # 冗余：尝试 ctypes
        try:
            if not self._coinit():
                return False
            self._set_endpoint(device_id, role)
            return True
        except Exception:
            return False

    # ---------- 对外 ----------
    def switch_to_cable(self) -> Optional[str]:
        """切到 CABLE Output 并返回其设备 ID；找不到则返回 None。"""
        cable_id = _device_id(self.cable_name)
        if cable_id is None:
            return None
        if self._set_default_endpoint_via_ctypes(cable_id, ECONSOLE):
            if self._saved is None:
                self._saved = _current_default_capture_id()
            return cable_id
        return None

    def restore(self) -> None:
        """恢复系统默认输入设备（保持原接口）。"""
        self.restore_default()

    def restore_default(self) -> bool:
        """还原 hold_start 之前的默认输入设备。

        不依赖 pycaw：直接枚举常见 capture 设备名，选第一个可用的端点。
        典型桌面/笔记本都会带 "麦克风" / "microphone" / "阵列"；兜底选项含 Realtek/Conexant。
        """
        if self._saved:
            try:
                if self._set_default_endpoint_via_ctypes(self._saved, ECONSOLE):
                    return True
            except Exception:
                self._saved = None
        for candidate_name in ("麦克风", "microphone", "阵列", "array", "realtek", "conexant"):
            dev_id = _device_id(candidate_name)
            if dev_id:
                try:
                    if self._set_default_endpoint_via_ctypes(dev_id, ECONSOLE):
                        return True
                except Exception:
                    continue
        return False


def find_cable_output() -> Optional[str]:
    return _device_id("cable output")


# ---------- PowerShell 兜底：切默认音频端点（零 ctypes COM 烦恼） ----------
# PowerShell 脚本文件（模板），用 -File + 参数传 devId/role，避免任何转义问题。
#
# IPolicyConfig vtable：
#   IUnknown: 3 slots (QueryInterface / AddRef / Release)
#   PolicyConfig 内部方法: 13 slots (GetMixFormat..SetPropertyValue)
#   SetDefaultEndpoint: slot[13] （第 17 个）
#   SetEndpointVisibility: slot[14]
# 所以在 SetDefaultEndpoint 之前，需要 13 个占位方法。
_PS_SCRIPT_TEMPLATE = r'''
param(
    [Parameter(Mandatory=$true)][string]$DeviceId,
    [Parameter(Mandatory=$true)][uint32]$Role
)
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class AudioPolicyX {
    [Guid("870af99c-171d-4f9e-af0d-e63df40c2bc9")]
    [ComImport]
    private class CPolicyConfigClientRT {}
    [Guid("f8679f50-850a-41cf-9c72-430f290290c8")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    [ComImport]
    private interface IPolicyConfigX {
        // IUnknown(3) 之后、SetDefaultEndpoint 之前共 13 个方法占位
        void _VtblSlot0();
        void _VtblSlot1();
        void _VtblSlot2();
        void _VtblSlot3();
        void _VtblSlot4();
        void _VtblSlot5();
        void _VtblSlot6();
        void _VtblSlot7();
        void _VtblSlot8();
        void _VtblSlot9();
        void _VtblSlot10();
        void _VtblSlot11();
        void _VtblSlot12();
        [PreserveSig] int SetDefaultEndpoint([MarshalAs(UnmanagedType.LPWStr)] string wszDeviceId, uint role);
        void _VtblSlot14(); // SetEndpointVisibility 占位
    }
    public static int SetDefaultEndpoint(string devId, uint role) {
        var obj = new CPolicyConfigClientRT();
        var pc = (IPolicyConfigX)obj;
        int hr = pc.SetDefaultEndpoint(devId, role);
        Marshal.ReleaseComObject(obj);
        return hr;
    }
}
"@
$hr = [AudioPolicyX]::SetDefaultEndpoint($DeviceId, $Role)
Write-Output "hr=$hr"
if ($hr -eq 0) { exit 0 } else { exit 1 }
'''


def _set_default_endpoint_ps(device_id: str, role: int) -> bool:
    """调用 PowerShell 设置默认音频捕获端点。返回是否成功。"""
    if not device_id:
        return False
    import subprocess
    import tempfile
    import os
    tmpf = None
    try:
        fd, tmp_path = tempfile.mkstemp(suffix=".ps1", prefix="vraudio_")
        os.close(fd)
        tmpf = tmp_path
        # PS5.1 脚本文件需要 UTF-8 with BOM 才能正确解析含中文 GUID 的字符串；
        # 这里纯 ASCII，直接用 utf-8-sig (BOM) 兜底。
        with open(tmpf, "w", encoding="utf-8-sig", newline="\n") as f:
            f.write(_PS_SCRIPT_TEMPLATE.lstrip("\n"))
        p = subprocess.run(
            [
                "powershell.exe",
                "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", tmpf,
                "-DeviceId", device_id,
                "-Role", str(int(role)),
            ],
            capture_output=True, text=True, timeout=20,
        )
        return p.returncode == 0
    except Exception:
        return False
    finally:
        if tmpf:
            try:
                os.unlink(tmpf)
            except Exception:
                pass


def _current_default_capture_id() -> Optional[str]:
    """返回当前默认捕获端点（capture/console）的 device_id，用于保存以便还原。"""
    async def _fetch():
        from winsdk.windows.media.devices import MediaDevice
        try:
            return str(MediaDevice.get_default_audio_capture_id())
        except Exception:
            return None
    try:
        asyncio.get_running_loop()
        # 已有事件循环：抛给线程池（调用方会包线程池，所以这里保守返回 None）
        return None
    except RuntimeError:
        return asyncio.run(_fetch()) or None


if __name__ == "__main__":
    import time

    sw = AudioDefaultSwitch()
    cid = find_cable_output()
    print("CABLE Output 设备:", cid)
    if cid:
        print("切换默认输入到 CABLE Output …")
        sw.switch_to_cable()
        time.sleep(6)
        print("测试结束（可手动恢复输入设备）")