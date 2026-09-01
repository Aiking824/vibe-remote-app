@echo off
REM ============================================================
REM  Source mode runner - runs main.py directly.
REM  No Chinese text to avoid CMD codepage mishaps.
REM  Usage: double-click, or pass args like original:
REM     start.bat [--port 8765] [--no-browser] [--verbose]
REM ============================================================
setlocal EnableExtensions
title Vibe Remote
cd /d "%~dp0"
python app\main.py %*
pause
