@echo off
REM ============================================================
REM  Mode 3 - Same Wi-Fi network (general purpose)
REM  Topology: Phone + PC connected to same home/office Wi-Fi
REM  Scan the QR on pair console or paste the pair URL into App
REM ============================================================
setlocal EnableExtensions
title Vibe Remote - Same Wi-Fi

cd /d "%~dp0"
call env_setup.bat
if %errorlevel% neq 0 exit /b %errorlevel%

echo.
echo ============================================================
echo  Mode 3 - Same Wi-Fi network (5GHz recommended for lower latency)
echo ============================================================
echo.
echo  Steps:
echo    1^) Make sure Phone and PC are on the SAME Wi-Fi
echo    2^) After startup a browser tab opens on:
echo       http://localhost:8765/pair/console
echo    3^) In App scan the QR, or copy/paste the pair URL manually
echo.

if not defined PORT set "PORT=8765"
cd /d "%DESKTOP_DIR%"
%PY% app\main.py --port %PORT%
pause
endlocal
exit /b 0
