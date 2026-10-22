# 安装 Git 钩子（pre-commit 敏感信息扫描）
# 用法: .\tools\install-hooks.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$hooksDir = Join-Path $root ".git\hooks"
if (-not (Test-Path $hooksDir)) { Write-Host "不是 Git 仓库或缺少 .git/hooks" -ForegroundColor Red; exit 1 }

$src = Join-Path $PSScriptRoot "hooks\pre-commit"
$dst = Join-Path $hooksDir "pre-commit"
Copy-Item $src $dst -Force
Write-Host "已安装: $dst" -ForegroundColor Green
Write-Host "之后每次 git commit 都会自动扫描暂存区" -ForegroundColor Cyan
