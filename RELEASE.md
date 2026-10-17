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

## 3. 发版流程（Checklist）

1. **合码**：确认 `main` 分支包含本次全部变更，CI/本地构建通过。
2. **改版本号**：`app/build.gradle.kts` 中更新 `versionCode`（+1）与 `versionName`。

   > ⚠️ **版本号（versionName / versionCode）必须随本次发版一并提交入库**，不能只改本地构建。否则从源码构建出的包版本号会落后于线上发布包，用户覆盖升级会直接失败——v1.2 / v1.3 就是实际踩过这个坑（仓库长期停留在 versionCode=2 / 1.1.0，而线上已是 versionCode=4 / 1.3.0）。

3. **写 CHANGELOG**：在 `CHANGELOG.md` 中将 `[Unreleased]` 条目整理为正式版本段（`## [x.y.z] - YYYY-MM-DD`），按「新增 / 变更 / 修复 / 移除 / 安全」分类，条目面向用户、一行一条，并附 compare 链接。
4. **构建签名包**：

   ```bash
   ./gradlew assembleRelease
   ```

   产物位于 `app/build/outputs/apk/release/`，重命名为 `whale-roam-v<版本号>.apk`。
5. **推送 Tag**：`git tag v<版本号> && git push origin v<版本号>`。
6. **建 Release**：在 GitHub Releases 页面基于该 Tag 发布，标题 `鲸游 WhaleRoam v<版本号>`，正文直接粘贴 CHANGELOG 对应段落，并上传签名 APK。
7. **更新 latest.json**：将 `versionName` / `versionCode` / `changelog` / `apkUrl` 更新为新版本（`apkUrl` 指向刚上传的 APK 下载地址），提交并推送——应用内更新即以此文件为准。
8. **自检**：用旧版本 App 手动点「检查更新」，确认能发现新版本、下载进度正常、签名校验通过、可覆盖安装。

## 4. Release 说明（更新日志）写法

Release 正文 = CHANGELOG 对应版本段，遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 六分类：

- **新增（Added）**：新功能，动词开头，如「新增：xxx」；
- **变更（Changed）**：已有行为的变化；
- **修复（Fixed）**：具体描述了修复了什么问题、在什么场景触发，避免写「修复若干 bug」这类空话；
- **移除（Removed）** / **安全（Security）** / **弃用（Deprecated）**：按需使用。

反面示例 → 正面示例：

| 不要这样写 | 应该这样写 |
| --- | --- |
| 修复了一些 bug | 修复开启悬浮摇杆必崩的问题（前台服务未调用 startForeground） |
| 优化更新体验 | 下载通知新增「取消下载」按钮，取消后自动清理残缺文件 |
| 更新依赖 | 升级 osmdroid 至 6.1.18，修复部分瓦片加载失败 |

## 5. 文档结构约定

- `README.md`：项目门面——简介、徽章、免责声明、功能、使用、构建、更新机制、FAQ、贡献、协议；
- `CHANGELOG.md`：面向用户的完整变更史；
- `RELEASE.md`（本文件）：版本命名与发版流程；
- `latest.json`：应用内更新检查读取的版本清单（`versionName` / `versionCode` / `changelog` / `apkUrl`），**每次发版必须与 APK 同步更新**，否则用户收不到更新；
- 架构/接口类文档放 `docs/`，随代码演进同步更新。
