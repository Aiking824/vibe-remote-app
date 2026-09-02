@echo off
REM ============================================================
REM  Vibe Remote desktop_v2 启动脚本
REM  - 使用 pythonw.exe 启动，避免黑色控制台窗口
REM  - 关闭主窗口 = 最小化到系统托盘（不退出）
REM  - 托盘"退出"菜单 = 停止后台服务 + 退出程序
REM ============================================================
setlocal EnableExtensions
cd /d "%~dp0"

REM 优先使用 D:\Python312\pythonw.exe（开发期已安装路径）
if exist "D:\Python312\pythonw.exe" (
    start "" "D:\Python312\pythonw.exe" main.py
) else (
    REM 回落到 PATH 中的 pythonw.exe
    where pythonw.exe >nul 2>&1
    if %errorlevel%==0 (
        start "" pythonw.exe main.py
    ) else (
        echo [ERROR] 未找到 pythonw.exe，请确认 Python 安装。
        pause
        exit /b 1
    )
)

endlocal
exit /b 0
