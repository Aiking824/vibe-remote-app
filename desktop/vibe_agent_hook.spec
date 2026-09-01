# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec: 打包 vibe_agent_hook.exe（Claude Code / Codex Hook 入口）。
• 单文件、无控制台、无 GUI。
• 部署后通过 `vibe_agent_hook.exe --install both` 安装 Hook。
"""
block_cipher = None
hiddenimports = []
datas = []

a = Analysis(
    ["./app/agent_hook.py"],
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

# —— 单文件（onefile）打包，方便作为独立 CLI 工具分发 ——
exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name="vibe_agent_hook",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,   # CLI 需要打印 install/uninstall 结果；--hook 时 agent 只走 stdin/stdout，即使短暂有黑窗也只闪一下
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
