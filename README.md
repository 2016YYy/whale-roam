# 鲸游 WhaleRoam

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/2016YYy/whale-roam)](https://github.com/2016YYy/whale-roam/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/2016YYy/whale-roam/total.svg)](https://github.com/2016YYy/whale-roam/releases)
[![Min SDK](https://img.shields.io/badge/minSdk-26-green.svg)](https://developer.android.com/)
[![Platform](https://img.shields.io/badge/platform-Android-brightgreen.svg)](https://www.android.com/)

> 一款基于 Android 调试 Mock Location API + OpenStreetMap 的免 Root 虚拟定位工具，自带可自由漂移的悬浮摇杆。

> ⚠️ 本项目为独立 **clean-room 重写**，与 Gitee 上的「影梭 GoGoGo」无任何代码关联、无源码引用，仅借鉴「虚拟定位 + 摇杆」这一通用产品形态。

---

## 📢 免责声明

**请务必仔细阅读：**

- 本软件**仅供学习 Android Mock Location API 与地图开发原理**使用；
- **严禁**用于校园跑步/打卡类 App 作弊、考试作弊、任何违反法律法规或侵犯他人权益的行为；
- 因使用本软件产生的一切后果由使用者自行承担，开发者不承担任何责任；
- 首次启动时会展示本声明，点击「同意并继续」方可使用；继续安装并使用本软件，即表示你已阅读并同意上述声明。

## ✨ 功能

- 🎯 **一键虚拟定位**：地图选点 / 搜索地名（Nominatim）/ 直接输入经纬度，即刻模拟位置
- 🎨 **8 套主题配色**：深海蓝 / 薄荷绿 / 落日橙 / 樱花粉 / 紫罗兰 / 青碧 / 琥珀金 / 石墨灰，设置页点色块即时切换
- 🌗 **三档主题模式**：跟随系统 / 浅色 / 深色，冷启动无闪烁，深色模式全量适配
- ⚡ **镜像加速更新**：6 源并发检查 + 下载前并发测速选最快线路，失败自动切换并断点续传，弹窗实时显示当前线路域名
- 🕹️ **悬浮摇杆**：悬浮窗八方向漂移，模拟真实移动轨迹，速度可调
- 🎚️ **摇杆无级调速与锁定**：推杆量实时映射速度，慢速微调更自然；点击摇杆中心可锁定，松手仍按最后方向持续移动
- 🫳 **悬浮摇杆可拖动换位**：长按摇杆外圈拖动整个悬浮窗，松手后记住位置，下次开启沿用
- 🎬 **路线录制与回放**：录制摇杆移动轨迹并保存为命名路线，支持原速 / 倍速回放、循环回放与删除
- 🔔 **通知栏快捷操作**：模拟定位通知可直接「停止」，悬浮摇杆通知可直接「关闭」
- 🕐 **历史收藏**：一键回到常用位置，支持删除
- 🧭 **开源地图**：基于 OpenStreetMap（osmdroid），无需任何地图 API Key
- 🔄 **应用内更新**：启动时自动检查 + 手动检查；前台 Service 下载带进度条通知，支持取消，切后台不中断；下载完成点击通知即唤起系统安装器
- 🔏 **统一签名**：自 v1.1.0 起所有 Release 包使用同一把发布密钥签名；更新安装前校验签名一致性，覆盖升级不丢数据（v1.0.0 的历史签名差异见下方 FAQ）

## 📋 版本时间线

> GitHub Releases 列表按创建时间倒序排列，与版本号无关，因此顺序可能与版本演进不一致。下表按版本号排列，便于定位：

| 版本 | 发布日期 | 版本代号 | 核心能力 | 签名 |
| --- | --- | --- | --- | --- |
| **v1.5.0** | 2026-10-17 | 1.5.0 | 8 套主题种子色 / 三档主题模式 / 勾选式免责声明 / 镜像加速更新 | 统一密钥 |
| v1.4.0 | 2026-10-17 | 1.4.0 | 摇杆无级调速 / 锁定 / 可拖动 + 路线录制回放 + 通知栏快捷操作 | 统一密钥 |
| v1.3.0 | 2026-10-16 | 1.3.0 | 注入字段仿真 / 1s 循环重注 / 随机偏移 / 三档速度 / 抽屉导航 | 统一密钥 |
| v1.2.0 | 2026-10-16 | 1.2.0 | 地图长按查询 / 多坐标系输入 / CloudDrive 风格更新窗 | 统一密钥 |
| v1.1.0 | 2026-10-16 | 1.1.0 | 免责声明 / 自动更新 / 下载取消 / 签名校验（自本版起统一密钥） | 统一密钥 |
| v1.0.0 | 2026-10-16 | 1.0.0 | 首个版本：虚拟定位 / 开源地图 / 悬浮摇杆 | ⚠️ 早期密钥，见 FAQ |

当前最新版本为 **v1.5.0**，可直接从 v1.1.0 及以上任意版本覆盖升级。

## 🚀 使用方法

1. 从 [Releases](https://github.com/2016YYy/whale-roam/releases) 下载最新 APK 安装；
2. 打开「鲸游」，阅读并同意免责声明，授予位置、通知、悬浮窗权限；
3. 进入系统 **开发者选项 → 选择模拟位置应用**，选择 **鲸游**；
4. 回到 App，在地图上选一个点（或搜索地点、输入经纬度），点击「启动模拟」；
5. 需要连续移动时点「开启悬浮摇杆」，用摇杆控制方向与速度。

## 🔧 从源码构建

环境要求：JDK 17+、Android SDK（compileSdk 35）。

```bash
git clone https://github.com/2016YYy/whale-roam.git
cd whale-roam
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/`。

Release 签名配置放在根目录 `keystore.properties`（**不入库**，格式见 [RELEASE.md](RELEASE.md)）：

```properties
storeFile=/abs/path/to/your.jks
storePassword=xxx
keyAlias=xxx
keyPassword=xxx
```

## 📦 更新机制

- App 启动后（默认 24 小时一次）或手动点「检查更新」时，拉取仓库根目录 [`latest.json`](latest.json)；
- 若 `versionCode` 高于当前版本，弹窗展示更新日志，可选择下载；
- 下载在前台 Service 中进行，通知栏实时显示进度百分比，可随时取消，切后台不中断；
- 下载完成后**先校验安装包签名与当前应用一致**，通过后点击通知唤起系统安装器；因使用同一签名，可直接覆盖升级，不丢数据。

## 📝 更新日志

见 [CHANGELOG.md](CHANGELOG.md)（遵循 Keep a Changelog + Semantic Versioning）。
版本命名与发版流程见 [RELEASE.md](RELEASE.md)。

## ❓ FAQ

**Q：为什么定位偶尔会飘回真实位置？**
A：这是 Android 调试 API 的固有现象——手机同时开启了基站/Wi-Fi 等其他定位方式时可能覆盖模拟值。可尝试在开发者选项中关闭「Wi-Fi 扫描」「蓝牙扫描」等辅助定位。

**Q：为什么在微信等腾讯系应用上定位不起作用？**
A：部分应用有自身的位置校验与风控机制，与 Mock Location API 的兼容性取决于应用自身实现。

**Q：支持鸿蒙系统吗？**
A：理论上兼容 Android 调试 API 的系统均可运行，以实际测试为准。

**Q：路线回放为什么没反应？**
A：需要先启动模拟定位；回放会按录制的原始轨迹重放，倍速只影响时间间隔，不影响路径。

**Q：覆盖安装失败 / 提示签名不一致？**
A：请确认安装包来自本仓库 Releases，并先排除以下两种已知情况：

1. **v1.0.0 的原始资产**：v1.0.0 首次发布时使用早期临时密钥签名（证书 SHA-256 `f0e26e8c…`），与 v1.1.0 及之后版本使用的发布密钥（证书 SHA-256 `137d9f9c…`）不一致，因此**无法直接覆盖升级到 v1.1.0+**。该资产已于 2026-10-17 用统一发布密钥重新签名，现在从 Releases 下载的 v1.0.0 可直接升级；若你在重签之前就装过旧版 v1.0.0，请先卸载一次（会丢失历史收藏）。
2. **第三方重新打包 / 改签名的版本**：需先卸载后重装（同样会丢失历史收藏）。

v1.1.0 → v1.4.0 之间证书完全一致，可正常覆盖升级。

## 🤝 贡献

欢迎提 Issue / PR。提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/)：

```
feat: 新增xx功能
fix: 修复xx问题
docs: 文档更新
refactor: 重构代码
chore: 构建/杂项
```

分支命名：`feat/<功能>`、`fix/<问题>`、`docs/<文档>`。

## 📄 开源协议

[Apache License 2.0](LICENSE) © WhaleRoam Contributors