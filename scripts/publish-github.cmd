@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ============================================
echo  QTaskMgr Standalone: Publish to GitHub
echo ============================================
echo.

:: Read version from build.gradle.kts
set "VERSION="
for /f "tokens=2 delims== " %%a in ('findstr /C:"versionName" "%~dp0..\src\quest-app\app\build.gradle.kts"') do (
    set "VERSION=%%~a"
)
set "VERSION=%VERSION:"=%"
if "%VERSION%"=="" (
    echo [ERROR] Cannot read versionName from build.gradle.kts
    pause
    exit /b 1
)
echo Version: %VERSION%
echo.

:: Check gh is available
gh --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] GitHub CLI not found. Install: winget install GitHub.cli
    pause
    exit /b 1
)

:: Check git status
cd /d "%~dp0.."
echo [1/5] Checking git status...
git status --porcelain 2>nul | findstr /r /c:"." >nul 2>&1
if not errorlevel 1 goto :HAS_CHANGES

echo No changes to commit.
echo.
set /p CONTINUE=Continue to release only? (y/n): 
if /i not "%CONTINUE%"=="y" (
    echo Cancelled.
    pause
    exit /b 0
)
goto :RELEASE

:HAS_CHANGES
echo Changes found:
git status --short
echo.

:: Commit
echo [2/5] Committing changes...
git add -A
git commit -m "v%VERSION%: release"
if errorlevel 1 (
    echo [ERROR] Commit failed
    pause
    exit /b 1
)
echo.

:: Push
echo [3/5] Pushing to GitHub...
git push
if errorlevel 1 (
    echo [ERROR] Push failed
    pause
    exit /b 1
)
echo.

:RELEASE
:: Check if APK exists
set "APK=%~dp0..\dist\QTaskMgr-Standalone-v%VERSION%-release.apk"
if not exist "%APK%" (
    echo [WARN] APK not found. Building...
    powershell -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" -Release
    if errorlevel 1 (
        echo [ERROR] Build failed
        pause
        exit /b 1
    )
)
if not exist "%APK%" (
    echo [ERROR] APK not found after build
    pause
    exit /b 1
)
echo APK: %APK%
echo.

:: Check if release notes exist
set "NOTES=%~dp0..\docs\RELEASE_NOTES_v%VERSION%.md"
if not exist "%NOTES%" (
    echo [WARN] Release notes not found, creating...
    echo # QTaskMgr Standalone v%VERSION%> "%NOTES%"
    echo.>> "%NOTES%"
    echo See CHANGELOG.md for details.>> "%NOTES%"
)

:: Create GitHub release
echo [4/5] Creating GitHub release v%VERSION%...
gh release create "v%VERSION%" "%APK%" --title "QTaskMgr Standalone v%VERSION%" --notes-file "%NOTES%"
if errorlevel 1 (
    echo [ERROR] Release creation failed
    pause
    exit /b 1
)
echo.

:: Done
echo [5/5] Done!
echo ============================================
echo  Published: v%VERSION%
echo  APK: dist\QTaskMgr-Standalone-v%VERSION%-release.apk
echo  URL: https://github.com/kabzon93region/Quest-Task-Manager-Standalone/releases/tag/v%VERSION%
echo ============================================
echo.
pause
