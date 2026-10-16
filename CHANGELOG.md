# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-10-16

### 新增
- 🎯 基于 Android Mock Location API 的虚拟定位（GPS + NETWORK 双 Provider 注入）
- 🗺️ 基于 OpenStreetMap（osmdroid）的开源地图，免 API Key
- 🔍 地名搜索（Nominatim）与经纬度直输
- 🕹️ 悬浮窗摇杆，八方向连续漂移，速度可调
- 🕐 位置收藏与历史记录（JSON 本地存储）
- 🔄 应用内更新检查、前台 Service 下载、进度条通知、后台可挂起、完成后唤起安装
- ⚙️ 设置页：摇杆速度、模拟精度、免责声明、关于
- 🔏 Release 统一签名，覆盖升级不丢数据

### 说明
- 本版本为首次完整重写发布，独立 clean-room 实现，未引用「影梭 GoGoGo」源码。
