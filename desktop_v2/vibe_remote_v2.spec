# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for Vibe Remote Bridge v2（onefile 单文件 + 无控制台 + steve 图标）。

打包命令（在 desktop_v2/ 目录下执行）：
    D:\\Python312\\python.exe -m PyInstaller vibe_remote_v2.spec --noconfirm

产物：dist/VibeRemoteBridge.exe（单文件，无控制台，双击即启动）
配置：vibe_config.json / keymap.json 优先从 exe 同级目录读取（便于用户修改），
      找不到再从打包内 app/ 目录读取（出厂默认）。
"""
from PyInstaller.utils.hooks import collect_data_files, collect_submodules

block_cipher = None

# ===== 隐式导入（PyInstaller 静态分析发现不了的） =====
hiddenimports = []
# winsdk：Windows SDK 绑定（音频/蓝牙/进程状态等）
hiddenimports += list(collect_submodules("winsdk"))
# sounddevice：PortAudio 绑定（音频回采）
hiddenimports += list(collect_submodules("sounddevice"))
# 其他三方库
hiddenimports += ["numpy", "PIL", "qrcode", "websockets", "websockets.legacy.server"]
# desktop/app/ 内部模块（runtime.py 用 importlib 加载，PyInstaller 不会自动发现）
hiddenimports += [
    "pairing", "keymap", "audio_default_switch", "audio_sink",
    "codex_rollout", "codex_usage", "key_injector", "status_watcher",
    "agent_hook",
]

# ===== 数据文件 =====
datas = []
# desktop/app/ 整个目录（.py + .json）打包到 _MEIPASS/desktop/app/
# runtime.py 用 importlib.util.spec_from_file_location 加载 desktop/app/main.py
datas += [("../desktop/app", "desktop/app")]
# web 静态页（server 提供配对控制台 /pair/console）
datas += [("../desktop/web", "web")]
# 程序图标（main.py 用 HERE/assets/app.ico 加载程序图标）
datas += [("./assets/app.ico", "assets")]

a = Analysis(
    ["./main.py"],
    pathex=["../desktop/app"],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)
pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name="VibeRemoteBridge",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,  # 无控制台（pythonw 模式，双击启动无黑窗）
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon="./assets/app.ico",
)
