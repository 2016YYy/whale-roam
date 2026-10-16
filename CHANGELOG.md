# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与 [Semantic Versioning](https://semver.org/lang/zh-CN/)。
所有重要变更都会记录在本文件中；条目面向用户编写，不是 Git 日志的堆砌。

## [Unreleased]

## [1.1.0] - 2026-10-16

### 新增
- 🛡️ 首次启动强制展示免责声明，需点击「同意并继续」后方可使用；设置页可随时回看
- 🔄 启动时自动检查更新（默认 24 小时一次，静默检测，发现新版本才弹窗）
- 🛑 下载通知新增「取消下载」按钮，可随时中止并自动清理残缺文件
- 🔏 更新安装前进行统一签名校验：安装包签名与当前应用不一致时拒绝安装并明确提示，保证覆盖升级不丢数据、防止来源不符的安装包

### 修复
- 🐛 修复开启悬浮摇杆时必崩的问题：`JoystickService` 此前以 `startForegroundService` 启动却从未调用 `startForeground()`，在 Android 8+ 上触发 `ForegroundServiceDidNotStartInTimeException` 崩溃；现已按 Android 14 规范声明 `specialUse` 前台服务类型并正常发通知
- 🐛 停止模拟时新增二次确认，避免误触（此前点击即停）

### 变更
- 摇杆开关按钮文案改为跟随开关状态切换（开启悬浮摇杆 / 关闭悬浮摇杆）

## [1.0.0] - 2026-10-16

### 新增
- 🎯 基于 Android Mock Location API 的虚拟定位（GPS + NETWORK 双 Provider 注入）
- 🗺️ 基于 OpenStreetMap（osmdroid）的开源地图，免 API Key
- 🔍 地名搜索（Nominatim）与经纬度直输
- 🕹️ 悬浮窗摇杆，八方向连续漂移，速度可调
- 🕐 位置收藏与历史记录（JSON 本地存储）
- 🔄 应用内更新检查、前台 Service 下载、进度条通知、切后台不中断、完成后唤起安装
- ⚙️ 设置页：摇杆速度、模拟精度、免责声明、关于
- 🔏 Release 统一签名，覆盖升级不丢数据

### 说明
- 本版本为首次完整重写发布，独立 clean-room 实现，未引用「影梭 GoGoGo」源码。

[Unreleased]: https://github.com/2016YYy/whale-roam/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/2016YYy/whale-roam/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/2016YYy/whale-roam/releases/tag/v1.0.0
