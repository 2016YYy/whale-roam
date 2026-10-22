# 安装 gitleaks 到 tools/gitleaks/（便携式，不写系统 PATH）
# 用法: .\tools\install-gitleaks.ps1 [-Version 8.21.2]
param([string]$Version = "8.21.2")

$ErrorActionPreference = "Stop"
$dest = Join-Path $PSScriptRoot "gitleaks"
New-Item -ItemType Directory -Path $dest -Force | Out-Null

$asset = "gitleaks_${Version}_windows_x64.zip"
$raw = "https://github.com/gitleaks/gitleaks/releases/download/v$Version/$asset"
$mirrors = @(
    "https://gh-proxy.com/$raw",
    "https://ghfast.top/$raw",
    "https://ghproxy.net/$raw",
    $raw
)

$zip = Join-Path $env:TEMP $asset
$ok = $false
foreach ($u in $mirrors) {
    try {
        Write-Host "下载: $u" -ForegroundColor Cyan
        Invoke-WebRequest -Uri $u -OutFile $zip -TimeoutSec 180
        if ((Get-Item $zip).Length -gt 1000000) { $ok = $true; break }
    } catch {
        Write-Host "  失败: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}
if (-not $ok) { Write-Host "所有下载源失败" -ForegroundColor Red; exit 1 }

Expand-Archive -Path $zip -DestinationPath $dest -Force
Remove-Item $zip -Force

$exe = Join-Path $dest "gitleaks.exe"
if (Test-Path $exe) {
    Write-Host "安装成功: $exe" -ForegroundColor Green
    & $exe version
} else {
    Write-Host "解压后未找到 gitleaks.exe" -ForegroundColor Red
    exit 1
}
