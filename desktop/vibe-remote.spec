# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for Vibe Remote desktop server (onedir mode)."""
from PyInstaller.utils.hooks import collect_data_files, collect_submodules

block_cipher = None

# winsdk / sounddevice / qrcode / PIL 的隐藏导入
hiddenimports = []
hiddenimports += list(collect_submodules("winsdk"))
hiddenimports += list(collect_submodules("sounddevice"))
hiddenimports += ["numpy", "PIL", "qrcode", "websockets", "websockets.legacy.server"]

datas = []
# 打包 web 静态页（index.html / console.html）→ ./web
# spec 在 desktop/ 下，desktop/web 即静态目录
datas += [("./web", "web")]
# 打包 app/ 下的 JSON 配置 → ./app（作为 frozen 时的默认兜底）
# 用户若在 exe 同级放自己的 vibe_config.json / keymap.json，会优先用它们
datas += [
    ("./app/vibe_config.json", "app"),
    ("./app/keymap.json", "app"),
]

a = Analysis(
    ["./app/main.py"],
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
    name="vibe-remote",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,
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
    name="vibe-remote",
)
