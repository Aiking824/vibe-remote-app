@echo off
REM ============================================================
REM  Vibe Remote - Launch Menu (choose one of 3 connection modes)
REM ============================================================
setlocal EnableExtensions
title Vibe Remote - Launch Menu
cd /d "%~dp0"

:menu
cls
echo.
echo ============================================================
echo   Vibe Remote desktop - Launch Menu
echo ============================================================
echo.
echo   [1] USB cable direct (BEST / lowest latency)
echo   [2] Same Wi-Fi network (general / easiest)
echo   [3] USB Tethering / RNDIS (phone shares net to PC)
echo.
echo   [Q] Quit
echo.
set /p "c=Enter option and press Enter: "

if /i "%c%"=="1" ( call "1_usb_direct.bat"      & goto :menu )
if /i "%c%"=="2" ( call "2_same_wifi.bat"       & goto :menu )
if /i "%c%"=="3" ( call "3_usb_tethering.bat"  & goto :menu )
if /i "%c%"=="Q" ( goto :eof )
if /i "%c%"==""  ( goto :menu )
goto :menu
