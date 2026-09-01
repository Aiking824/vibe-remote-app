@echo off
REM ============================================================
REM  Vibe Remote - Environment & Dependency Check
REM  Do NOT rename this file.
REM  Output is in pure ASCII to avoid CMD codepage parse errors.
REM  Usage (from another bat):   call "%~dp0env_setup.bat"
REM  After call the following vars become available in caller:
REM    PY          python launcher ("py -3" or "python")
REM    HAS_ADB     1 if adb exists, 0 otherwise
REM    DESKTOP_DIR full path to desktop/ folder
REM ============================================================
setlocal EnableExtensions

cd /d "%~dp0\.."
set "_DESKTOP_DIR=%cd%"

echo.
echo [1/3] Checking Python ...
set "_PY="
where py >nul 2>nul
if %errorlevel%==0 set "_PY=py -3"
if not defined _PY (
    where python >nul 2>nul
    if %errorlevel%==0 set "_PY=python"
)
if not defined _PY (
    echo [FAIL] Python not found. Install Python 3.10+ and tick "Add to PATH".
    echo        https://www.python.org/downloads/
    pause
    exit /b 1
)
for /f "delims=" %%v in ('%_PY% -c "import sys;print(sys.version.split(chr(91))[0])" 2^>nul') do set "_VER=%%v"
echo       %_PY%   version: %_VER%

echo.
echo [2/3] Checking Python dependencies ...
%_PY% -c "import websockets, numpy, sounddevice, qrcode, PIL, winsdk" >nul 2>nul
if %errorlevel%==0 (
    echo       [OK] dependencies ready
) else (
    echo       Missing dependencies, running pip install (needs Internet)...
    %_PY% -m pip install --upgrade pip
    %_PY% -m pip install -r "%_DESKTOP_DIR%\requirements.txt"
    if %errorlevel% neq 0 (
        echo.
        echo [FAIL] pip install failed. Check network or run manually:
        echo        %_PY% -m pip install -r requirements.txt
        pause
        exit /b 2
    )
)

echo.
echo [3/3] Checking optional tool: adb (USB-direct mode only)...
set "_ADB=0"
where adb >nul 2>nul
if %errorlevel%==0 (
    for /f "delims=" %%v in ('adb version 2^>nul ^| findstr /i "version"') do echo       [OK] %%v
    set "_ADB=1"
) else (
    echo       [--] adb not found. USB-direct mode requires Android Platform Tools:
    echo            https://developer.android.com/studio/releases/platform-tools
    echo            Unzip then add adb folder to PATH, or drop adb.exe next to this bat.
)

set "_TMP=%TEMP%\vibe_env_%RANDOM%.bat"
(
    echo set "DESKTOP_DIR=%_DESKTOP_DIR%"
    echo set "PY=%_PY%"
    echo set "HAS_ADB=%_ADB%"
) > "%_TMP%"
endlocal & call "%_TMP%" & del /q "%_TMP%" 2>nul
exit /b 0
