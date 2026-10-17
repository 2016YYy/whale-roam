# -*- coding: utf-8 -*-
"""统一 5 个 Release 的更新说明格式（模板化重写）。

统一模板：
    ## <版本> 更新说明
    > <可选背景引用>
    ### 新增 / ### 变更 / ### 修复
    - <emoji> **<功能名>**：<说明>
    ### 安装说明
    1..3 行
    末尾 CHANGELOG 链接

规范要点：
- 一级标题固定「## <版本> 更新说明」，不用「首次发布」等自由文案
- 分节标题纯文本，不加 emoji
- 条目统一「- <emoji> **<名称>**：<说明>」
- 「修复」条目正文不重复「修复」二字
- 每版必须有「### 安装说明」+ 末尾 CHANGELOG 链接
"""
import io
import os
import sys

import requests
import urllib3

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
urllib3.disable_warnings()

REPO = "2016YYy/whale-roam"
BT = chr(96)  # 反引号


class Pinned(requests.adapters.HTTPAdapter):
    def __init__(self, host, ip, **kw):
        self.host, self.ip = host, ip
        super().__init__(**kw)

    def send(self, request, **kw):
        if request.url.startswith("https://" + self.host):
            request.url = request.url.replace("https://" + self.host, "https://" + self.ip, 1)
            request.headers["Host"] = self.host
        return super().send(request, **kw)


CHANGELOG_LINK = (
    "完整变更历史见 [CHANGELOG.md]"
    "(https://github.com/2016YYy/whale-roam/blob/main/CHANGELOG.md)。"
)


def code(s):
    return BT + s + BT


BODIES = {}

BODIES["v1.0.0"] = """## 1.0.0 更新说明

### 新增
- 🎯 **虚拟定位**：基于 Android Mock Location API 的免 Root 定位模拟，GPS + NETWORK 双 Provider 注入
- 🗺️ **开源地图**：基于 OpenStreetMap（osmdroid），无需任何地图 API Key
- 🔍 **地点搜索与坐标输入**：地名搜索（Nominatim）与经纬度直输
- 🕹️ **悬浮摇杆**：悬浮窗八方向连续漂移，速度可调
- 🕐 **位置收藏**：一键回到常用位置，支持删除
- 🔄 **应用内更新**：检查更新、前台 Service 下载、进度条通知、切后台不中断、完成后唤起安装
- ⚙️ **设置页**：摇杆速度、模拟精度、免责声明、关于

### 安装说明
1. 下载 @@APK@@ 安装（Android 8.0 及以上）
2. 首次启动需阅读并同意免责声明；随后在「开发者选项 → 选择模拟位置应用」中选择「鲸游」
3. 本包为项目首个版本，无历史版本可升级

> ⚠️ **签名说明**：本版本原始资产使用早期临时密钥签名，与 v1.1.0 及之后版本的发布密钥不一致。该资产已于 2026-10-17 用统一发布密钥重新签名，因此**新下载的包可直接覆盖升级到 v1.4.0**；若你在重签之前安装过旧版 v1.0.0，升级到 v1.1.0 及更高版本需先卸载（会丢失历史收藏）。

@@LINK@@
"""

BODIES["v1.1.0"] = """## 1.1.0 更新说明

### 新增
- 🛡️ **首次启动免责声明**：必须点击「同意并继续」方可使用，设置页可随时回看
- 🔄 **启动自动检查更新**：默认 24 小时一次，静默检测，发现新版本才弹窗
- 🛑 **下载可取消**：下载通知新增「取消下载」按钮，取消后自动清理残缺文件
- 🔏 **统一签名校验**：更新安装前校验安装包签名与当前应用一致，签名不符拒绝安装，覆盖升级不丢数据

### 修复
- 🐛 **悬浮摇杆开启即崩溃**：前台服务未调用 @@FG@@，Android 8+ 触发 @@EX@@；现已按 Android 14 规范声明 @@SU@@ 前台服务类型并正常发通知
- 🐛 **源码无法构建**：release 构建脚本 keystore 属性引用错对象（@@BAD@@），现改为 @@GOOD@@
- 🐛 **停止模拟易误触**：停止操作新增二次确认

### 安装说明
1. 下载 @@APK@@ 安装（Android 8.0 及以上）
2. 与 v1.0.0 的原始签名不一致：若在 v1.0.0 资产重签之前安装过旧版，需先卸载（会丢失历史收藏）再安装本版本
3. 本包与 v1.2.0 及之后的版本使用同一签名，可直接覆盖升级

@@LINK@@
"""

BODIES["v1.2.0"] = """## 1.2.0 更新说明

### 新增
- 🖱️ **地图长按查询**：长按地图任意位置，自动逆地理编码并弹窗展示该点详细地址，支持「设为此处 / 收藏此处 / 复制地址」
- 📐 **多坐标系输入**：搜索框输入经纬度时可选择坐标系（WGS-84 / GCJ-02 / BD-09），自动换算为 WGS-84，可直接使用高德、百度地图复制的坐标
- 🪟 **CloudDrive 风格更新窗口**：检查更新 → 更新说明 → 对话框内嵌下载进度条（百分比 + 已下载 / 总量）→ 一键安装；关闭窗口后下载自动转入通知栏后台继续
- ⚙️ **设置页入口**：新增「检查更新」入口与「当前版本」显示

### 变更
- 🎨 **主界面 UI 重构**：圆角胶囊搜索栏上移、坐标 / 状态悬浮卡片置于地图左上角、启动 / 停止改为地图右下角带图标悬浮按钮、底部操作条收纳摇杆与收藏
- ✏️ **免责声明按钮文案**：改为「我同意 / 不同意」
- 🔔 **启动检查更新的提示**：已是最新版本时增加吐司提示（检查失败仍静默）

### 安装说明
1. 下载 @@APK@@ 安装（Android 8.0 及以上）
2. 与 v1.1.0 及之后的版本使用同一签名，可直接覆盖升级，历史收藏与设置不丢失
3. 已安装 v1.1.0 的用户也可通过 应用内「设置 → 检查更新」直接升级

@@LINK@@
"""

BODIES["v1.3.0"] = """## 1.3.0 更新说明

> 依据《GoGoGo（影梭）逆向分析报告》对标补齐的能力。

### 新增
- 🛰️ **注入字段仿真**：伪造的 @@LOC@@ 携带完整字段——海拔（默认 55m，可调）、速度、方向角（bearing，由摇杆实时驱动）、GPS 卫星数（extras=7），并沿用 @@ELAPSED@@ 避免时间一致性校验暴露
- 🔁 **1 秒循环重注**：模拟定位从事件触发改为 1s 周期持续刷新（独立 HandlerThread 前台优先级），任何时刻读取到的都是模拟值，避免被真实定位顶掉
- 🎲 **随机偏移**：可选的位置微抖动，偏移半径可调（默认 10m），每次注入独立随机，用于规避一致性检测
- 🚶 **走 / 跑 / 骑三档速度**：设置页可分别预设步行（默认 1.2 m/s）、跑步（3.6）、骑行（10.0）速度；悬浮摇杆顶部新增档位切换按钮，一键循环切换
- 🧭 **启动前置检查链**：启动模拟前依次检查「是否被选为模拟位置应用 → 系统定位服务是否开启 → WiFi 干扰提醒」
- 📜 **自动记录定位历史**：启动模拟时自动入库，异步逆地理编码回填地址名称（失败保持坐标标签）
- 🗑️ **历史记录过期归档**：默认保留 7 天，可在设置中调整（1~365 天），读取时自动清理过期记录

### 变更
- 🎨 **侧边抽屉导航**：主界面重构为 DrawerLayout 侧边栏（历史记录 / 设置 / 检查更新 / 免责声明 / 关于），替代顶部菜单
- ⚙️ **设置页分组重构**：定位注入仿真（精度 / 海拔 / 随机偏移）/ 摇杆速度（三档）/ 历史有效期 / 关于
- 🔧 **摇杆速度取值**：改为按档位读取设置值，档位切换立即生效

### 安装说明
1. 下载 @@APK@@ 安装（Android 8.0 及以上）
2. 与历史版本使用同一签名，可直接覆盖升级，历史收藏与设置不丢失
3. 已安装旧版本可通过 应用内「设置 → 检查更新」直接升级

@@LINK@@
"""

BODIES["v1.4.0"] = """## 1.4.0 更新说明

### 新增
- 🎚️ **摇杆无级调速**：推杆量按 0~1 归一化，实际速度 = 当前档位速度 × 推杆量，可以慢速微调；此前只要推杆超过阈值就按满速移动
- 🔒 **摇杆锁定持续移动**：点击摇杆中心锁定 / 解锁，锁定后松手仍按最后方向持续移动，解锁后回中停止（锁定为瞬时状态，摇杆服务重启后自动解除）
- 🫳 **悬浮摇杆可拖动换位**：长按摇杆外圈拖动整个悬浮窗，松手记住位置，下次开启沿用
- 🎬 **路线录制与回放**：从侧边抽屉菜单「开始录制路线 / 停止录制」，录制摇杆移动轨迹并按距离 + 时间自适应采样（移动中 0.5m / 200ms，原地 1s），保存为命名路线；支持按原速 / 倍速回放、循环回放与删除路线
- 🔔 **通知栏快捷操作**：模拟定位通知新增「停止」按钮，悬浮摇杆通知新增「关闭」按钮
- ✅ **摇杆开启前置检查**：模拟未启动时开启摇杆会明确提示，不再出现「开了没反应」

### 修复
- 🔢 **源码版本号与线上发布不一致**：仓库内版本号此前停留在 versionCode=2 / 1.1.0，低于线上 v1.3.0 的 versionCode=4，从源码构建的包版本号倒退会导致用户覆盖升级失败；现修正为 5 / 1.4.0，并确保此后每次发版都提交版本号
- 🛑 **松开摇杆后注入速度不归零**：速度字段停留在上一次非零值而位置已静止，容易被运动类 App 的一致性校验识别；现松手即归零
- 🎯 **热切换目标点时精度不生效**：@@PUSH@@ 的 @@ACC@@ 参数此前被忽略，现按传入值生效
- 📅 **历史记录有效期上限被 UI 卡死**：设置项允许 1~365 天，但设置页 SeekBar 上限只有 30 天，现放开到 365 天
- 🧹 **停止模拟后悬浮摇杆仍在运行**：状态不一致、摇杆继续占着前台服务，现停止模拟时一并关闭摇杆
- 🔘 **通知栏「关闭摇杆」后主界面按钮状态未同步**：按钮仍显示为开启，与服务实际状态不一致，现摇杆服务销毁时同步主界面开关状态
- ⏺️ **停止模拟时录制状态悬挂**：若正在录制路线，停止模拟后录制标记未清理、无法再次开始录制，现停止模拟时自动取消录制并给出提示
- ⏹️ **更新下载取消后可能重复回调完成事件**：取消标志与下载线程存在竞争，现加双重检查

### 安装说明
1. 下载 @@APK@@ 安装（Android 8.0 及以上）
2. 与历史版本使用同一签名，可直接覆盖升级，历史收藏与设置不丢失
3. 已安装旧版本可通过 应用内「设置 → 检查更新」直接升级

@@LINK@@
"""

SUBS = {
    "@@APK@@": code("whale-roam-v1.0.0.apk"),
    "@@LINK@@": CHANGELOG_LINK,
    "@@FG@@": code("startForeground()"),
    "@@EX@@": code("ForegroundServiceDidNotStartInTimeException"),
    "@@SU@@": code("specialUse"),
    "@@BAD@@": code("keystorePropsFile.getProperty"),
    "@@GOOD@@": code("keystoreProps.getProperty"),
    "@@LOC@@": code("Location"),
    "@@ELAPSED@@": code("elapsedRealtimeNanos"),
    "@@PUSH@@": code("MockLocationEngine.push()"),
    "@@ACC@@": code("accuracy"),
}

APK_NAME = {
    "v1.0.0": "whale-roam-v1.0.0.apk",
    "v1.1.0": "whale-roam-v1.1.0.apk",
    "v1.2.0": "whale-roam-v1.2.0.apk",
    "v1.3.0": "whale-roam-v1.3.0.apk",
    "v1.4.0": "whale-roam-v1.4.0.apk",
}


def render(tag):
    body = BODIES[tag]
    for k, v in SUBS.items():
        body = body.replace(k, v)
    # 每个版本替换成自己的 APK 名
    for name in APK_NAME.values():
        if name != APK_NAME[tag]:
            body = body.replace(code(name), code(APK_NAME[tag]))
    return body


def main():
    s = requests.Session()
    s.mount("https://api.github.com", Pinned("api.github.com", "20.205.243.168"))
    s.verify = False
    s.headers.update({
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "Accept": "application/vnd.github+json",
        "User-Agent": "whale-roam-release",
    })
    rels = s.get(f"https://api.github.com/repos/{REPO}/releases?per_page=100", timeout=60).json()
    by_tag = {r["tag_name"]: r for r in rels}

    for tag in ["v1.0.0", "v1.1.0", "v1.2.0", "v1.3.0", "v1.4.0"]:
        body = render(tag)
        rel = by_tag[tag]
        r = s.patch(f"https://api.github.com/repos/{REPO}/releases/{rel['id']}",
                    json={"body": body}, timeout=60)
        ok = "OK" if r.status_code < 400 else "FAIL " + str(r.status_code)
        print(f"{tag}: {ok}  body={len(body)} chars")
        if r.status_code >= 400:
            print(r.text[:300])

    print("\nDONE")


if __name__ == "__main__":
    main()
