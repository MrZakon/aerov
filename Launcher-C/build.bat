@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo       Aero Visual Launcher C++ - Build Script
echo ===================================================

cd /d "%~dp0"

:: 1. Check if cl.exe is already initialized in current shell
where cl.exe >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [OK] MSVC compiler found in current environment.
    goto :BUILD
)

:: 2. Locate Visual Studio via vswhere
set "FOUND_VC=0"
set "VSWHERE_PATH="
if exist "%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" (
    set "VSWHERE_PATH=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
) else if exist "%ProgramFiles%\Microsoft Visual Studio\Installer\vswhere.exe" (
    set "VSWHERE_PATH=%ProgramFiles%\Microsoft Visual Studio\Installer\vswhere.exe"
)

if defined VSWHERE_PATH (
    for /f "usebackq delims=" %%i in (`"!VSWHERE_PATH!" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do (
        set "VS_DIR=%%i"
    )
)
if defined VS_DIR set "VCVARS_BAT=!VS_DIR!\VC\Auxiliary\Build\vcvars64.bat"
if defined VCVARS_BAT if exist "!VCVARS_BAT!" (
    echo [INFO] Initializing MSVC x64...
    call "!VCVARS_BAT!" >nul
    goto :BUILD
)

:: 3. Try standard default paths directly
call :CHECK_VC "%ProgramFiles%\Microsoft Visual Studio\2022\Community"
if "!FOUND_VC!"=="1" goto :BUILD

call :CHECK_VC "%ProgramFiles%\Microsoft Visual Studio\2022\Professional"
if "!FOUND_VC!"=="1" goto :BUILD

call :CHECK_VC "%ProgramFiles%\Microsoft Visual Studio\2022\Enterprise"
if "!FOUND_VC!"=="1" goto :BUILD

call :CHECK_VC "%ProgramFiles%\Microsoft Visual Studio\2022\BuildTools"
if "!FOUND_VC!"=="1" goto :BUILD

call :CHECK_VC "%ProgramFiles(x86)%\Microsoft Visual Studio\2019\Community"
if "!FOUND_VC!"=="1" goto :BUILD

call :CHECK_VC "%ProgramFiles(x86)%\Microsoft Visual Studio\2019\BuildTools"
if "!FOUND_VC!"=="1" goto :BUILD

echo [ERROR] MSVC environment could not be initialized.
exit /b 1

:BUILD
where cmake.exe >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [INFO] Building with CMake...
    if not exist "build" mkdir "build"
    cmake -B build -DCMAKE_BUILD_TYPE=Release
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] CMake configuration failed.
        exit /b !ERRORLEVEL!
    )
    cmake --build build --config Release
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] CMake build failed.
        exit /b !ERRORLEVEL!
    )

    echo.
    echo [SUCCESS] Build complete!
    echo   - Native C++ Launcher: bin\Aero Launcher.exe
    goto :END
)

echo [ERROR] CMake is required to build Launcher-C.
exit /b 1

:CHECK_VC
if exist "%~1\VC\Auxiliary\Build\vcvars64.bat" (
    echo [INFO] Initializing MSVC x64 via %~1...
    call "%~1\VC\Auxiliary\Build\vcvars64.bat" >nul
    set "FOUND_VC=1"
)
exit /b

:END
endlocal
