@echo off
REM ============================================================
REM  Mode 1 - USB cable direct (lowest latency)
REM  Topology: Phone --USB-- PC
REM  Phone App URL: http://127.0.0.1:8765/
REM ============================================================
setlocal EnableExtensions
title Vibe Remote - USB Direct

cd /d "%~dp0"
call env_setup.bat
if %errorlevel% neq 0 exit /b %errorlevel%

echo.
echo ============================================================
echo  Mode 1 - USB cable direct (auto adb reverse tcp:8765)
echo ============================================================
set "ADB_OK=0"
if "%HAS_ADB%"=="1" (
    echo.
    echo  ^> Running adb reverse tcp:8765 tcp:8765 ...
    adb reverse tcp:8765 tcp:8765
    if %errorlevel%==0 (
        echo  [OK] USB direct ready. In App pick "USB cable direct" and use:
        echo       http://127.0.0.1:8765/
        set "ADB_OK=1"
    ) else (
        echo  [WARN] adb reverse failed. Check:
        echo    1^) Phone: enable Developer Options -^> USB debugging
        echo    2^) Plug USB cable; on phone tap "Allow USB debugging"
        echo    3^) Re-run this bat, or use WiFi/Hotspot mode instead
    )
)

echo.
echo  ^> Starting Vibe Remote server ...
if not defined PORT set "PORT=8765"
cd /d "%DESKTOP_DIR%"
%PY% app\main.py --port %PORT%
pause
endlocal
exit /b 0
