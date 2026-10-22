# 鲸游 WhaleRoam 敏感信息扫描（gitleaks 封装）
#
# 用法（仓库根目录）：
#   .\tools\scan-secrets.ps1            扫 Git 全历史 + 工作区
#   .\tools\scan-secrets.ps1 -Staged    只扫暂存区（提交前用）
#   .\tools\scan-secrets.ps1 -Cloud     额外扫全部 GitHub Release 资产
#   .\tools\scan-secrets.ps1 -Quiet     只输出结论
#
# 退出码：0 = 干净；1 = 发现泄露（必须停止发版）；2 = 工具缺失/参数错误
param(
    [switch]$Staged,
    [switch]$Cloud,
    [switch]$Quiet
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $PSScriptRoot "gitleaks\gitleaks.exe"
$reportDir = Join-Path $root "build"
$report = Join-Path $reportDir "gitleaks-report.json"

function Say($msg, $color = "Gray") { if (-not $Quiet) { Write-Host $msg -ForegroundColor $color } }

if (-not (Test-Path $exe)) {
    Write-Host "[FAIL] 未找到 gitleaks: $exe" -ForegroundColor Red
    Write-Host "       先执行: .\tools\install-gitleaks.ps1" -ForegroundColor Yellow
    exit 2
}

$ver = (& $exe version 2>&1 | Select-Object -First 1)
Say "gitleaks $ver" "Cyan"

if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }

Push-Location $root
try {
    $failed = $false

    if ($Staged) {
        Say "`n[1/1] 扫描暂存区..." "Cyan"
        & $exe protect --staged --no-banner --redact -v 2>&1 | ForEach-Object { Say "  $_" }
        if ($LASTEXITCODE -ne 0) { $failed = $true }
    }
    else {
        Say "`n[1/2] 扫描 Git 全历史..." "Cyan"
        & $exe detect --source . --no-banner --redact -v `
            --report-path $report --report-format json 2>&1 | ForEach-Object { Say "  $_" }
        if ($LASTEXITCODE -ne 0) { $failed = $true }

        Say "`n[2/2] 检查敏感文件是否被 Git 跟踪..." "Cyan"
        $tracked = git ls-files | Select-String -Pattern "keystore\.properties|local\.properties|\.jks$|\.keystore$|tools/gitleaks/.*\.exe"
        if ($tracked) {
            $failed = $true
            Write-Host "  [FAIL] 以下敏感文件被 Git 跟踪，必须从索引移除：" -ForegroundColor Red
            $tracked | ForEach-Object { Write-Host "         $_" -ForegroundColor Red }
        } else {
            Say "  [OK] 无敏感文件被跟踪" "Green"
        }
    }

    if ($Cloud) {
        Say "`n[Cloud] 扫描 GitHub Release 资产..." "Cyan"
        $py = Join-Path $PSScriptRoot "audit\scan_release_assets.py"
        if (Test-Path $py) {
            & python $py
            if ($LASTEXITCODE -ne 0) { $failed = $true }
        } else {
            Say "  [SKIP] 未找到 $py" "Yellow"
        }
    }

    Write-Host ""
    if ($failed) {
        Write-Host "===== 扫描未通过：禁止提交/发版 =====" -ForegroundColor Red
        if (Test-Path $report) { Write-Host "报告: $report" -ForegroundColor Yellow }
        exit 1
    }
    Write-Host "===== 扫描通过：无敏感信息泄露 =====" -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
