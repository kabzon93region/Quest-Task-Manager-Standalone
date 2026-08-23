@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ============================================
echo  QTaskMgr Standalone: Публикация на GitHub
echo ============================================
echo.

:: Читаем версию из build.gradle.kts
set "VERSION="
for /f "tokens=2 delims== " %%a in ('findstr /C:"versionName" "%~dp0..\src\quest-app\app\build.gradle.kts"') do (
    set "VERSION=%%~a"
)
set "VERSION=%VERSION:"=%"
if "%VERSION%"=="" (
    echo [ОШИБКА] Не удалось прочитать версию из build.gradle.kts
    pause
    exit /b 1
)
echo Версия: %VERSION%
echo.

:: Проверяем gh CLI
gh --version >nul 2>&1
if errorlevel 1 (
    echo [ОШИБКА] GitHub CLI не найден. Установите: winget install GitHub.cli
    pause
    exit /b 1
)

:: Проверяем git
cd /d "%~dp0.."
echo [1/5] Проверка изменений...
git status --porcelain 2>nul | findstr /r /c:"." >nul 2>&1
if not errorlevel 1 goto :HAS_CHANGES

echo Изменений нет.
echo.
set /p CONTINUE=Продолжить только создание релиза? (y/n): 
if /i not "%CONTINUE%"=="y" (
    echo Отменено.
    pause
    exit /b 0
)
goto :RELEASE

:HAS_CHANGES
echo Найдены изменения:
git status --short
echo.

:: Коммит
echo [2/5] Сохранение изменений...
git add -A
git commit -m "v%VERSION%: release"
if errorlevel 1 (
    echo [ОШИБКА] Не удалось создать коммит
    pause
    exit /b 1
)
echo.

:: Push
echo [3/5] Отправка на GitHub...
git push
if errorlevel 1 (
    echo [ОШИБКА] Не удалось отправить на GitHub
    pause
    exit /b 1
)
echo.

:RELEASE
:: Проверяем APK
set "APK=%~dp0..\dist\QTaskMgr-Standalone-v%VERSION%-release.apk"
if not exist "%APK%" (
    echo [!] APK не найден. Сборка...
    powershell -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" -Release
    if errorlevel 1 (
        echo [ОШИБКА] Сборка APK не удалась
        pause
        exit /b 1
    )
)
if not exist "%APK%" (
    echo [ОШИБКА] APK не найден даже после сборки
    pause
    exit /b 1
)
echo APK: %APK%
echo.

:: Проверяем release notes
set "NOTES=%~dp0..\docs\RELEASE_NOTES_v%VERSION%.md"
if not exist "%NOTES%" (
    echo [!] Release notes не найдены, создаю заглушку...
    echo # QTaskMgr Standalone v%VERSION%> "%NOTES%"
    echo.>> "%NOTES%"
    echo Подробности в CHANGELOG.md.>> "%NOTES%"
)

:: Создаём релиз на GitHub
echo [4/5] Создание релиза v%VERSION% на GitHub...
gh release create "v%VERSION%" "%APK%" --repo kabzon93region/Quest-Task-Manager-Standalone --title "QTaskMgr Standalone v%VERSION%" --notes-file "%NOTES%" 2>&1
if errorlevel 1 (
    echo.
    echo [!] Релиз v%VERSION% уже существует на GitHub.
    echo     Обновите вручную: https://github.com/kabzon93region/Quest-Task-Manager-Standalone/releases/tag/v%VERSION%
    set "RELEASE_OK=0"
) else (
    set "RELEASE_OK=1"
)
echo.

:: Итог
echo [5/5] Готово!
echo ============================================
if "%RELEASE_OK%"=="1" (
    echo  СТАТУС: ВСЁ УСПЕШНО
    echo  - Код отправлен на GitHub
    echo  - Релиз v%VERSION% создан
    echo  - APK прикреплен к релизу
) else (
    echo  СТАТУС: ЧАСТИЧНО
    echo  - Код отправлен на GitHub
    echo  - Релиз v%VERSION% уже существовал
)
echo.
echo  Ссылка: https://github.com/kabzon93region/Quest-Task-Manager-Standalone/releases/tag/v%VERSION%
echo ============================================
echo.
pause
