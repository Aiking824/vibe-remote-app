# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for Vibe Remote GUI (pywebview + pystray, no console)."""
from PyInstaller.utils.hooks import collect_data_files, collect_submodules

block_cipher = None

# winsdk / sounddevice / qrcode / PIL / pywebview / pystray 的隐藏导入
hiddenimports = []
hiddenimports += list(collect_submodules("winsdk"))
hiddenimports += list(collect_submodules("sounddevice"))
hiddenimports += list(collect_submodules("pywebview"))
hiddenimports += list(collect_submodules("pystray"))
hiddenimports += [
    "numpy", "PIL", "PIL.Image", "PIL.ImageDraw",
    "qrcode", "websockets", "websockets.legacy.server",
    "clr_loader", "pythonnet",
]
# pywebview Windows 后端
hiddenimports += ["webview.platforms.winforms", "webview.platforms.edgechromium"]

datas = []
# 打包 web 静态页（index.html / console.html）→ ./web
datas += [("./web", "web")]
# 打包 app/ 下的 JSON 配置 → ./app（作为 frozen 时的默认兜底）
datas += [
    ("./app/vibe_config.json", "app"),
    ("./app/keymap.json", "app"),
]

a = Analysis(
    ["./app/gui_entry.py"],
    pathex=["./app"],
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
    [],
    exclude_binaries=True,
    name="vibe-remote-gui",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,  # 无控制台窗口
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="vibe-remote-gui",
)
