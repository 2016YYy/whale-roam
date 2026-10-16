# 鲸游 WhaleRoam

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84)
![targetSdk](https://img.shields.io/badge/targetSdk-35-3DDC84)

> 一款基于 Android 调试 Mock Location API + OpenStreetMap 的免 Root 虚拟定位工具，自带可自由漂移的悬浮摇杆。

> ⚠️ 本项目为独立 clean-room 重写，与 Gitee 上的「影梭 GoGoGo」无任何代码关联、无源码引用，仅借鉴「虚拟定位 + 摇杆」这一通用产品形态。

---

## 📢 免责声明

- 本软件**仅供学习 Android Mock Location API 与地图开发原理**使用。
- **严禁**用于校园跑步/打卡类 App 作弊、考试作弊、任何违反法律法规或侵犯他人权益的行为。
- 因使用本软件产生的一切后果由使用者自行承担，开发者不承担任何责任。
- 继续安装并使用本软件，即表示你已阅读并同意上述声明。

## ✨ 功能

- 🎯 **一键虚拟定位**：地图选点 / 搜索地名 / 直接输入经纬度，即刻模拟位置
- 🕹️ **悬浮摇杆**：悬浮窗八方向漂移，模拟真实移动轨迹，速度可调
- 🕐 **历史收藏**：一键回到常用位置，支持删除
- 🧭 **开源地图**：基于 OpenStreetMap（osmdroid），无需任何地图 API Key
- 🔄 **应用内更新**：内置更新检查，下载带进度条通知，可挂后台下载完自动唤起安装
- 🔏 **统一签名**：所有 Release 包使用同一把密钥签名，覆盖安装不丢数据

## 📸 截图

（待补充）

## 🚀 使用方法

1. 从 [Releases](https://github.com/2016YYy/whale-roam/releases) 下载最新 APK 安装。
2. 打开「鲸游」，授予位置、通知、悬浮窗权限。
3. 进入系统 **开发者选项 → 选择模拟位置应用**，选择 **鲸游**。
4. 回到 App，在地图上选一个点（或搜索地点），点击「启动模拟」。
5. 需要连续移动时点「开启悬浮摇杆」。

## 🔧 从源码构建

```bash
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/`。

Release 签名配置放在根目录 `keystore.properties`（不入库），格式：

```properties
storeFile=/abs/path/to/your.jks
storePassword=xxx
keyAlias=xxx
keyPassword=xxx
```

## 📦 更新机制

- App 启动后或手动点「检查更新」时，拉取仓库根目录 [`latest.json`](latest.json)。
- 若 `versionCode` 高于当前版本，弹窗展示更新日志并开始下载。
- 下载在前台 Service 中进行，通知栏实时显示进度百分比，切后台也不中断。
- 下载完成后点击通知唤起系统安装器，因使用同一签名，可直接覆盖升级。

## 📝 更新日志

见 [CHANGELOG.md](CHANGELOG.md)。

## 🤝 贡献

欢迎提 Issue / PR。提交信息建议遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
feat: 新增xx功能
fix: 修复xx问题
docs: 文档更新
refactor: 重构代码
chore: 构建/杂项
```

## 📄 开源协议

[Apache License 2.0](LICENSE) © WhaleRoam Contributors
