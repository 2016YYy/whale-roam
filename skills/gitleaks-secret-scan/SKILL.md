---
name: gitleaks-secret-scan
description: 用 gitleaks 扫描 Git 仓库与发布资产中的密码 / Token / API Key 等敏感信息。每次提交构建版本、发版、或怀疑密钥泄露时使用；也用于检查历史提交与 GitHub Release 资产是否泄露凭证。
---

# gitleaks 敏感信息扫描

> 来源：《Gitleaks 敏感信息扫描工具使用指南》，已适配本机 Windows + 鲸游 WhaleRoam 项目。

## 何时使用

- **发版前必跑**：任何 `assembleRelease` / 打 tag / 建 Release 之前
- **提交前**：含配置、脚本、keystore、Token、CI 文件时
- **排查泄露**：怀疑密钥进过 Git 历史或 Release 资产
- **新增镜像 / 第三方地址**：确认没有把私有 token 拼进 URL

## 本机安装位置（便携式，不依赖 PATH）

```
<repo>/tools/gitleaks/gitleaks.exe      # v8.21.2，Windows x64
```

缺失时重新下载：

```powershell
. \tools\install-gitleaks.ps1
```

## 一键扫描（推荐）

```powershell
. \tools\scan-secrets.ps1              # 扫全历史 + 工作区
. \tools\scan-secrets.ps1 -Cloud       # 额外扫全部 Release 资产
. \tools\scan-secrets.ps1 -Staged      # 只扫暂存区（提交前用）
```

退出码：**0 = 干净**，**1 = 发现泄露（必须停止发版）**。

## 标准流程

### 1. 扫 Git 全历史（最重要）

```powershell
& .\tools\gitleaks\gitleaks.exe detect --source . --no-banner `
  --report-path "build\gitleaks.json" --report-format json
```

### 2. 扫暂存区（提交前）

```powershell
& .\tools\gitleaks.exe protect --staged --no-banner
```

### 3. 扫指定目录

```powershell
& .\tools\gitleaks.exe dir <path> --no-banner
```

## 本项目发版强制检查项

发版前按顺序执行，任一失败即停止发版：

1. `gitleaks detect` 全历史扫描 → 退出码必须为 0
2. 确认敏感文件未被跟踪：

   ```powershell
   git ls-files | Select-String "keystore.properties|local.properties|\.jks$|\.keystore$"
   # 输出必须为空
   ```

3. 确认 `tools/gitleaks/*.exe` 未入库（二进制已 gitignore）
4. 排除过期脚本与备份目录（@@B@_backup/`、@@B@_*.py` 等已 gitignore）

## 忽略与白名单

- 单行忽略：行尾加 `# gitleaks:allow`
- 项目规则：仓库根目录 `.gitleaks.toml`
- 误报基线：`--baseline-path`

## 常见泄露点

| 位置 | 风险 |
|---|---|
| `keystore.properties` | 签名口令（必须 gitignore） |
| `local.properties` | SDK 路径（含用户名） |
| `*.jks` / `*.keystore` | 签名私钥 |
| 更新模块 URL | 拼接了私有 token |
| CI / 脚本 | `GH_TOKEN`、`Authorization: Bearer` 硬编码 |
| 文档示例 | 真实 token 当示例粘贴 |

## 本机当前状态（2026-10-22 实测）

| 项 | 状态 |
|---|---|
| gitleaks 二进制 | `tools/gitleaks/gitleaks.exe`（8.21.2） ✅ |
| 构建闸门 | `app/build.gradle.kts` 的 `secretScan` 挂到 `preBuild` ✅ |
| 提交钩子 | `.git/hooks/pre-commit` 已安装，实测拦截 ✅ |
| 云端体检 | 6 个 Release 资产 + 全历史 30 提交，均无泄露 ✅ |
| GitHub 平台告警 | secret-scanning alerts open=0 / resolved=0 ✅ |

已验证的拦截效果：

- 暂存区含 `ghp_...` 假 token → 提交被阻止（`leaks found`）
- 构建前命中 3 条假密钥（AWS AK / 密钥对 / RSA 私钥）→ 构建失败

> 已知限制：gitleaks 内置白名单会放过官方示例密钥（如 `AKIAIOSFODNN7EXAMPLE`），
> 这是刻意行为，不代表工具失效；真实密钥仍会命中。
