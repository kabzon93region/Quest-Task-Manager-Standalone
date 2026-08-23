@echo off
REM Torero Line Ending & Encoding Normalizer
REM Usage: normalize_line_endings.cmd [--dry-run]
REM
REM Rules from .gitattributes:
REM   - All text files: LF
REM   - .cmd / .bat / .ps1: CRLF
REM   - Binary files: skip
REM   - UTF-8 without BOM for code files

setlocal

set "SCRIPT_DIR=%~dp0"
set "PS1=%SCRIPT_DIR%normalize_line_endings.ps1"

if not exist "%PS1%" (
    echo ERROR: normalize_line_endings.ps1 not found in %SCRIPT_DIR%
    exit /b 1
)

if "%1"=="--dry-run" (
    echo [DRY RUN MODE]
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" -WhatIf -Verbose
) else if "%1"=="-n" (
    echo [DRY RUN MODE]
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" -WhatIf -Verbose
) else if "%1"=="--verbose" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" -Verbose
) else if "%1"=="-v" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" -Verbose
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%"
)

endlocal
