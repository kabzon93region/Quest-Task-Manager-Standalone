param(
    [switch]$Release
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$project = Join-Path $root "src\quest-app"
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (-not $env:JAVA_HOME) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
}
$env:ANDROID_HOME = $sdkRoot
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$sdkRoot\platform-tools;$env:Path" }

$versionName = "2.4.0"
$gradle = Join-Path $project "gradlew.bat"

if ($Release) {
    Write-Host "=== QTaskMgr Standalone: release APK ===" -ForegroundColor Cyan
    $task = "assembleRelease"
    $apkDir = "app\build\outputs\apk\release"
    $outName = "QTaskMgr-Standalone-v$versionName-release.apk"
} else {
    Write-Host "=== QTaskMgr Standalone: debug APK ===" -ForegroundColor Cyan
    $task = "assembleDebug"
    $apkDir = "app\build\outputs\apk\debug"
    $outName = "QTaskMgr-Standalone-debug.apk"
}

Set-Location $project
& $gradle $task --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Get-ChildItem "$apkDir\*.apk" | Select-Object -First 1
if (-not $apk) { throw "APK not found in $apkDir" }

$out = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$dest = Join-Path $out $outName
Copy-Item $apk.FullName $dest -Force
Write-Host "[OK] dist\$outName ($([math]::Round($apk.Length / 1MB, 2)) MB)" -ForegroundColor Green
