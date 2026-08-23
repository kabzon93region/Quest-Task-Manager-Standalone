<# 
.SYNOPSIS
    Normalizes line endings and encoding for all project files.
.DESCRIPTION
    Rules from .gitattributes:
    - All text files: LF
    - .cmd / .bat: CRLF (Windows requirement)
    - Binary files: skip
    - Encoding: UTF-8 no BOM (for .ts/.tsx/.js/.json/.md/.py/.css/.html/.yml)
    - UTF-8 with BOM allowed for .cmd/.bat/.ps1
.NOTES
    Usage: .\scripts\normalize_line_endings.ps1 [-WhatIf] [-Verbose]
#>

param(
    [switch]$WhatIf,
    [switch]$Verbose
)

$ErrorActionPreference = "Continue"
$repoRoot = Split-Path $PSScriptRoot -Parent
if (-not $repoRoot) { $repoRoot = Get-Location }

# --- EOL rules from .gitattributes ---
$eolRules = @{
    ".cmd" = "crlf"; ".bat" = "crlf"; ".ps1" = "crlf"
    ".py"="lf"; ".js"="lf"; ".ts"="lf"; ".tsx"="lf"; ".jsx"="lf"
    ".json"="lf"; ".yml"="lf"; ".yaml"="lf"; ".toml"="lf"
    ".md"="lf"; ".sql"="lf"; ".html"="lf"; ".css"="lf"
    ".svg"="lf"; ".xml"="lf"; ".sh"="lf"; ".proto"="lf"
    ".mdc"="lf"; ".txt"="lf"; ".cfg"="lf"; ".ini"="lf"
    ".env"="lf"
}

# Binary extensions — skip entirely
$binaryExtensions = @(
    ".png",".jpg",".jpeg",".gif",".ico",".bmp",".webp",
    ".woff",".woff2",".ttf",".eot",".otf",
    ".exe",".dll",".so",".dylib",
    ".zip",".tar",".gz",".7z",".rar",".vsix",".pdf"
)

# UTF-8 no BOM — strip BOM if present
$utf8NoBomExts = @(
    ".ts",".tsx",".js",".jsx",".json",
    ".py",".md",".sql",".html",".css",
    ".yml",".yaml",".toml",".svg",".xml",
    ".proto",".mdc",".sh",".txt",
    ".cfg",".ini",".env"
)

# UTF-8 with BOM allowed for Windows scripts
$utf8BomAllowed = @(".cmd",".bat",".ps1")

# --- Stats ---
$stats = @{ scanned=0; eolFixed=0; bomRemoved=0; skipped=0; errors=0 }

Write-Host "`n=== Torero Line Ending & Encoding Normalizer ===" -ForegroundColor Cyan
Write-Host "Repository: $repoRoot`n" -ForegroundColor DarkGray

$allFiles = Get-ChildItem -Path $repoRoot -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object {
        $_.FullName -notmatch '\\\.git\\' -and
        $_.FullName -notmatch '\\node_modules\\' -and
        $_.FullName -notmatch '\\__pycache__\\' -and
        $_.FullName -notmatch '\\venv\\' -and
        $_.FullName -notmatch '\\\.venv\\' -and
        $_.FullName -notmatch '\\env\\' -and
        $_.FullName -notmatch '\\bin\\' -and
        $_.FullName -notmatch '\\obj\\' -and
        $_.FullName -notmatch '\\build\\' -and
        $_.FullName -notmatch '\\dist\\' -and
        $_.FullName -notmatch '\\SDK\\' -and
        $_.FullName -notmatch '\\release\\' -and
        $_.FullName -notmatch '\\logs\\' -and
        $_.FullName -notmatch '\\\.cursor\\' -and
        $_.FullName -notmatch '\\\.cocoindex_code\\' -and
        $_.FullName -notmatch '\\\.mypy_cache\\' -and
        $_.FullName -notmatch '\\\.pytest_cache\\' -and
        $_.FullName -notmatch '\\\.tox\\' -and
        $_.FullName -notmatch '\\\.ruff_cache\\' -and
        $_.FullName -notmatch '\\\.ipynb_checkpoints\\' -and
        $_.FullName -notmatch '\\\.vscode\\' -and
        $_.FullName -notmatch '\\\.idea\\' -and
        $_.FullName -notmatch '\\3\.11\\'
    }

foreach ($file in $allFiles) {
    $ext = $file.Extension.ToLower()
    $stats.scanned++

    if ($binaryExtensions -contains $ext) { $stats.skipped++; continue }
    if (-not $ext -and $file.Name -notmatch '^(Makefile|Dockerfile|\.gitignore|\.gitattributes)$') { $stats.skipped++; continue }

    $targetEol = if ($eolRules[$ext]) { $eolRules[$ext] } else { "lf" }

    try {
        $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
        if ($bytes.Length -eq 0) { $stats.skipped++; continue }

        $changed = $false

        # BOM check
        $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
        if ($hasBom -and ($utf8NoBomExts -contains $ext)) {
            if (-not $WhatIf) {
                $noBom = New-Object byte[] ($bytes.Length - 3)
                [Array]::Copy($bytes, 3, $noBom, 0, $bytes.Length - 3)
                [System.IO.File]::WriteAllBytes($file.FullName, $noBom)
                $bytes = $noBom
            }
            $stats.bomRemoved++; $changed = $true
            if ($Verbose) { Write-Host "  BOM removed" -ForegroundColor Yellow }
        }

        # EOL check
        $text = [System.Text.Encoding]::UTF8.GetString($bytes)
        $crlf = ([regex]::Matches($text, "\r\n")).Count
        $lfOnly = ([regex]::Matches($text, "(?<!\r)\n")).Count
        $crOnly = ([regex]::Matches($text, "\r(?!\n)")).Count
        $total = $crlf + $lfOnly + $crOnly

        if ($total -eq 0) { $stats.skipped++; continue }

        $needsFix = $false
        if ($targetEol -eq "crlf") {
            if ($lfOnly -gt 0 -or $crOnly -gt 0) { $needsFix = $true }
        } else {
            if ($crlf -gt 0 -or $crOnly -gt 0) { $needsFix = $true }
        }

        if ($needsFix) {
            if (-not $WhatIf) {
                $norm = $text -replace "\r\n", "`n" -replace "\r", "`n"
                if ($targetEol -eq "crlf") { $norm = $norm -replace "`n", "`r`n" }
                $newBytes = [System.Text.Encoding]::UTF8.GetBytes($norm)
                [System.IO.File]::WriteAllBytes($file.FullName, $newBytes)
            }
            $stats.eolFixed++; $changed = $true
            if ($Verbose) { Write-Host "  EOL -> $($targetEol.ToUpper())" -ForegroundColor Green }
        }

        if ($changed) {
            $rel = $file.FullName.Substring($repoRoot.Length + 1)
            Write-Host "  FIXED: $rel" -ForegroundColor Green
        }
    } catch {
        $stats.errors++
        Write-Host "  ERROR: $($file.Name) - $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n=== Results ===" -ForegroundColor Cyan
Write-Host "  Scanned:    $($stats.scanned)"
Write-Host "  EOL fixed:  $($stats.eolFixed)" -ForegroundColor $(if($stats.eolFixed -gt 0){"Green"}else{"DarkGray"})
Write-Host "  BOM removed:$($stats.bomRemoved)" -ForegroundColor $(if($stats.bomRemoved -gt 0){"Yellow"}else{"DarkGray"})
Write-Host "  Skipped:    $($stats.skipped) (binary/empty)" -ForegroundColor DarkGray
Write-Host "  Errors:     $($stats.errors)" -ForegroundColor $(if($stats.errors -gt 0){"Red"}else{"DarkGray"})
if ($WhatIf) { Write-Host "`n  [DRY RUN]" -ForegroundColor Yellow }
Write-Host ""
