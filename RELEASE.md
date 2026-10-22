# 发布规范与命名约定（RELEASE.md）

本文件约定鲸游（WhaleRoam）的版本命名、发布流程与 Release 说明写法，供后续每次发版遵循。

## 1. 软件命名规范

| 项目 | 规范 | 示例 |
| --- | --- | --- |
| 软件中文名 | 鲸游 | 鲸游 |
| 软件英文名 | WhaleRoam（驼峰，无空格） | WhaleRoam |
| Android 包名 | `com.whaleroam`，Debug 包加 `.debug` 后缀 | `com.whaleroam.debug` |
| 版本号 | [Semantic Versioning](https://semver.org/lang/zh-CN/)：`MAJOR.MINOR.PATCH` | `1.1.0` |
| versionCode | 单调递增整数，与版本号一一对应，**必须随发版提交入库** | `1.0.0 → 1`，`1.1.0 → 2`，`1.4.0 → 5` |
| Git Tag | `v` + 版本号 | `v1.1.0` |
| Release APK 命名 | `whale-roam-v<版本号>.apk` | `whale-roam-v1.1.0.apk` |
| 分支命名 | `feat/<功能>`、`fix/<问题>`、`docs/<文档>` | `feat/route-replay` |
| 提交信息 | [Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/)：`feat:` / `fix:` / `docs:` / `refactor:` / `chore:` | `fix: 摇杆服务缺少 startForeground 导致崩溃` |

版本号递增规则：

- **PATCH**（1.1.0 → 1.1.1）：仅向后兼容的问题修复；
- **MINOR**（1.1.0 → 1.2.0）：新增向后兼容的功能；
- **MAJOR**（1.1.0 → 2.0.0）：引入不兼容变更（如调整数据格式、调整权限模型）。

## 2. 统一签名

- 所有 Release 包必须使用**同一把发布密钥**（`whale-roam-release.jks`）签名，否则用户无法覆盖升级、会丢数据。
- 密钥文件**严禁提交到 Git 仓库**，仅存放于维护者本地安全位置，并另行备份。
- 本地构建时在项目根目录放置 `keystore.properties`（已被 `.gitignore` 忽略）：

  ```properties
  storeFile=/absolute/path/to/whale-roam-release.jks
  storePassword=********
  keyAlias=whaleroam
  keyPassword=********
  ```

- 应用内更新在唤起安装前会校验安装包签名与当前应用一致（见 `util/ApkSignatureChecker.kt`），签名不符将拒绝安装。

### 2.1 签名历史（如实记录）

| 版本 | 签名证书 SHA-256 | 证书 DN | 状态 |
| --- | --- | --- | --- |
| v1.0.0（原始资产） | `f0e26e8c684fca404db42fa0b27e53cb01bbb2404816e914f482ddf80054947b` | `CN=WhaleRoam, O=OpenSource, C=CN` | ❌ 早期临时密钥，已废弃 |
| v1.0.0（2026-10-17 重签后） | `137d9f9cb8330b4b36872ab5e5054b6fe9e162c85717994e5fabab43a9e3ad5c` | `CN=WhaleRoam, OU=WhaleRoam, O=WhaleRoam, C=CN` | ✅ 当前资产 |
| v1.1.0 ~ v1.5.0 | 同上 | 同上 | ✅ |

> **历史事实**：v1.0.0 首次发布时误用了早期临时密钥，导致它无法直接覆盖升级到 v1.1.0+。该临时密钥已不在任何本地密钥库中，无法复现，因此 2026-10-17 将 v1.0.0 的 Release 资产用统一发布密钥重新签名，使 6 个版本的证书指纹完全一致。重签前已备份原始资产（`_backup\2026-10-17-191500\release-assets\`）。

#### 逐版本验证结果（2026-10-18 实测）

从 GitHub 线上重新下载全部 6 个 Release 资产，逐个用 `apksigner verify --print-certs` 验证：

| 版本 | versionCode | 证书 SHA-256 | 与基准一致性 |
| --- | --- | --- | --- |
| v1.0.0（基准） | 1 | `137d9f9c…3ad5c` | — |
| v1.1.0 | 2 | 同上 | ✅ |
| v1.2.0 | 3 | 同上 | ✅ |
| v1.3.0 | 4 | 同上 | ✅ |
| v1.4.0 | 5 | 同上 | ✅ |
| v1.5.0 | 6 | 同上 | ✅ |

**结论：6 个版本签名完全一致，无需替换。** 发布密钥库 `keystore/whale-roam-release.jks` 的 keytool 指纹 `13:7D:9F:9C:…:5C` 与上述全部 APK 对应，确认为同一把密钥。

> 唯一出现过签名不一致的是 **v1.0.0 的原始资产**（早期临时密钥 `f0e26e8c…`，DN 为 `CN=WhaleRoam, O=OpenSource, C=CN`），已于 2026-10-17 用统一密钥重签替换。除此之外没有任何版本出过问题。

### 2.2 重签已有资产（仅在签名事故时使用）

> ⚠️ 重签会改变 APK 的字节内容，已安装旧包的设备**仍无法**用新包覆盖升级（签名由安装时校验，与下载时的包无关）。重签只解决「新下载用户能否顺滑升级」的问题，不能修复已安装用户的升级路径。

```bash
apksigner sign --ks whale-roam-release.jks --ks-pass pass:<storePassword> \
  --ks-key-alias whaleroam --key-pass pass:<keyPassword> \
  --out whale-roam-vX.Y.Z-resigned.apk whale-roam-vX.Y.Z.apk
```

重签后必须：① 用 `apksigner verify --print-certs` 确认指纹为 `137d9f9c…`；② 删除 Release 上的旧资产再上传新资产（GitHub 不允许同名资产并存）；③ 在 Release 正文的「安装说明」里注明重签事实。

## 3. 发版流程（Checklist）

> 下面第 4 步（敏感信息扫描）是**硬性闸门**：扫描不通过禁止构建、禁止发版。

1. **合码**：确认 `main` 分支包含本次全部变更，CI/本地构建通过。

2. **敏感信息扫描（必做，发版闸门）**：

   ```powershell
   .\tools\scan-secrets.ps1            # 扫 Git 全历史 + 敏感文件跟踪检查
   .\tools\scan-secrets.ps1 -Cloud     # 额外扫描线上全部 Release 资产
   ```

   退出码非 0 时**立即停止发版**，逐条确认真泄露还是误报：
   - 真泄露：轮换凭证、从 Git 历史中清除（`git filter-repo`），再重新扫描
   - 误报：在 `.gitleaks.toml` 加 allowlist，或在行尾加 `# gitleaks:allow`

   说明：
   - **构建已自动扫描**：`app/build.gradle.kts` 里注册了 `secretScan` 任务并挂到 `preBuild`，任何 `assembleXxx` / `bundleXxx` 都会先跑 gitleaks；命中即让**构建失败**，报告在 `build/gitleaks-report.json`
   - 手动闸门仍保留（`tools/scan-secrets.ps1`）：构建只扫 Git 历史，工作区未提交文件由这个脚本和提交钩子覆盖
   - gitleaks 二进制在 `tools/gitleaks/gitleaks.exe`（不入库），缺失时构建会打印提示并跳过扫描，先跑 `.\tools\install-gitleaks.ps1` 安装
   - **提交前钩子已在本机安装并实测**（`.git/hooks/pre-commit`，模板在 `tools/hooks/pre-commit`，换机器用 `.\tools\install-hooks.ps1` 重装）：暂存区含密钥时提交会被直接阻止；
     受限沙箱里 Git 可能无法执行 sh 钩子（会报 signal pipe 错误），那种环境下以构建闸门和 `scan-secrets.ps1` 为准

   > 本项目实测记录（2026-10-22）：30 个提交、6 个 Release 资产全部扫描，未发现敏感信息；平台侧 GitHub Secret Scanning 告警数 0。

3. **改版本号**：`app/build.gradle.kts` 中更新 `versionCode`（+1）与 `versionName`。

   > ⚠️ **版本号（versionName / versionCode）必须随本次发版一并提交入库**，不能只改本地构建。否则从源码构建出的包版本号会落后于线上发布包，用户覆盖升级会直接失败——v1.2 / v1.3 就是实际踩过这个坑（仓库长期停留在 versionCode=2 / 1.1.0，而线上已是 versionCode=4 / 1.3.0）。

4. **写 CHANGELOG**：在 `CHANGELOG.md` 中将 `[Unreleased]` 条目整理为正式版本段（`## [x.y.z] - YYYY-MM-DD`），按「新增 / 变更 / 修复 / 移除 / 安全」分类，条目面向用户、一行一条，并附 compare 链接。
5. **构建签名包**：

   ```bash
   ./gradlew assembleRelease
   ```

   产物位于 `app/build/outputs/apk/release/`，重命名为 `whale-roam-v<版本号>.apk`。
6. **推送 Tag**：**必须使用附注标签（annotated tag）**，不要用轻量标签，以便 Tag 本身携带发版说明：

   ```bash
   git tag -a v<版本号> -m "鲸游 WhaleRoam v<版本号>: <一句话要点>"
   git push origin v<版本号>
   ```

   > 历史遗留：v1.0.0 与 v1.4.0 曾用轻量标签，已于 2026-10-17 重建为附注标签（v1.4.0 同时前移，使 Tag 内容包含校正后的 CHANGELOG）。
7. **建 Release**：基于该 Tag 发布，标题 `鲸游 WhaleRoam v<版本号>`，正文**严格按第 4 节的统一模板**撰写（不要直接粘贴 CHANGELOG 段落——CHANGELOG 用 `### 新增/变更/修复` 的分类，Release 正文另需「安装说明」与 CHANGELOG 链接），并上传签名 APK。

   正文草稿放在 `release/RELEASE_BODY.md`，然后用 `python tools/release.py --version <x.y.z> --code <n>` 一键建 Tag / Release / 上传资产。
8. **更新 latest.json**：将 `versionName` / `versionCode` / `changelog` / `apkUrl` 更新为新版本（`apkUrl` 指向刚上传的 APK 下载地址），提交并推送——应用内更新即以此文件为准。
9. **自检**：用旧版本 App 手动点「检查更新」，确认能发现新版本、下载进度正常、签名校验通过、可覆盖安装。

## 4. Release 说明（更新日志）写法

Release 正文**统一使用表情分段格式**，五个历史版本已于 2026-10-17 全部按此格式重写，后续必须保持一致。

### 4.1 统一模板

```markdown
## <版本号> 更新说明

> <可选：一句话背景，如「依据 XX 报告对标补齐」>

### 🎨 界面与主题
- **<变更点>**：<原行为 → 新行为>

### 🛡️ 合规与声明
- **<变更点>**：<说明>

### ⬇️ 更新与下载
- **<变更点>**：<说明>

### ✨ 新增功能
- **<功能名>**：<一句话说明，讲清「做了什么」与「为什么」>

### 🐛 问题修复
- **<问题名>**：<触发场景 + 现象 + 现在的处理>

### 📦 安装说明
1. 下载 `whale-roam-v<版本号>.apk` 安装（Android 8.0 及以上）
2. <签名说明：与哪些版本同签名、能否直接覆盖升级>
3. <升级路径：应用内检查更新 / 需卸载重装>

完整变更历史见 [CHANGELOG.md](https://github.com/2016YYy/whale-roam/blob/main/CHANGELOG.md)。
```

### 4.2 分段标题固定表

| 分段标题 | 用途 | 无内容时 |
| --- | --- | --- |
| `### 🎨 界面与主题` | UI 重构、主题、配色、交互调整 | 省略 |
| `### 🛡️ 合规与声明` | 免责声明、权限、合规相关 | 省略 |
| `### ⬇️ 更新与下载` | 更新检测、下载、镜像加速 | 省略 |
| `### ✨ 新增功能` | 新功能 | 省略（极少见） |
| `### 🐛 问题修复` | 缺陷修复 | 省略 |
| `### 📦 安装说明` | 安装与升级指引 | **不可省略** |

### 4.3 格式硬性规则

| 规则 | 说明 |
| --- | --- |
| 一级标题 | 固定 `## <版本号> 更新说明`，**不要**写「首次发布」「新版本来了」这类自由文案 |
| 分段标题 | 必须带 emoji 且与上表**逐字一致**，不增删不改 |
| 条目格式 | 统一 `- **<名称>**：<说明>`，**名称一律加粗** |
| 条目 emoji | 不加在条目里（分段标题的 emoji 已表意），保持条目干净 |
| 修复条目 | 正文**不要**再重复「修复」二字，直接写问题与现象 |
| 安装说明 | 固定三行结构，且必须写明与哪些版本同签名、能否直接覆盖升级 |
| 结尾链接 | 每个版本都必须有 CHANGELOG 链接，指向仓库 main 分支 |
| 长度 | 单条不超过两行，面向用户，不堆砌 Git 提交信息 |

### 4.4 分类写法（遵循 Keep a Changelog）

- **新增（Added）**：新功能，动词开头；
- **变更（Changed）**：已有行为的变化，写成「原行为 → 新行为」；
- **修复（Fixed）**：写清触发场景 + 现象 + 现在的处理，避免「修复若干 bug」这类空话；
- **移除（Removed）** / **安全（Security）** / **弃用（Deprecated）**：按需使用。

反面示例 → 正面示例：

| 不要这样写 | 应该这样写 |
| --- | --- |
| 修复了一些 bug | 悬浮摇杆开启即崩溃（前台服务未调用 startForeground） |
| 优化更新体验 | 下载通知新增「取消下载」按钮，取消后自动清理残缺文件 |
| 更新依赖 | 升级 osmdroid 至 6.1.18，修复部分瓦片加载失败 |

### 4.5 发版后自检

```bash
python tools/verify_release_consistency.py
```

该脚本会校验：① 所有 Release 资产的签名证书指纹是否一致；② 资产的 versionCode / versionName 是否与 Tag、latest.json 一致；③ Release 正文是否符合本节模板；④ 是否全部为附注标签。

## 6. 文档结构约定

- `README.md`：项目门面——简介、徽章、免责声明、功能、使用、构建、更新机制、FAQ、贡献、协议；
- `CHANGELOG.md`：面向用户的完整变更史；
- `RELEASE.md`（本文件）：版本命名与发版流程；
- `latest.json`：应用内更新检查读取的版本清单（`versionName` / `versionCode` / `changelog` / `apkUrl`），**每次发版必须与 APK 同步更新**，否则用户收不到更新；
- 架构/接口类文档放 `docs/`，随代码演进同步更新。