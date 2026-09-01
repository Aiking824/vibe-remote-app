@echo off
REM ============================================================
REM  Mode 4 - USB Tethering / RNDIS (phone shares network to PC)
REM  Phone will give PC a new RNDIS network adapter.
REM  After server starts, find the RNDIS-assigned PC IP and in App:
REM     http://THAT_RNDIS_IP:8765/
REM ============================================================
setlocal EnableExtensions
title Vibe Remote - USB Tethering (RNDIS)

cd /d "%~dp0"
call env_setup.bat
if %errorlevel% neq 0 exit /b %errorlevel%

echo.
echo ============================================================
echo  Mode 4 - USB Tethering (RNDIS - phone shares net to PC)
echo ============================================================
echo.
echo  Do this ON PHONE first:
echo    Settings -^> Mobile Network / More Networks -^> USB tethering -^> ON
echo    (Some Android: Settings -^> Hotspot -^> USB tethering)
echo.
echo  After turning ON, PC will detect a new adapter
echo  ("Remote NDIS based Internet Sharing Device" or "Ethernet 2").
echo  The startup banner will list ALL local IPs - find the one on the
echo  RNDIS adapter and in App enter: http://THAT_IP:8765/
echo.
echo  Press any key to start the server ...
pause >nul

if not defined PORT set "PORT=8765"
cd /d "%DESKTOP_DIR%"
%PY% app\main.py --port %PORT%
pause
endlocal
exit /b 0
