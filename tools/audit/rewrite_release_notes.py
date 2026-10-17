# -*- coding: utf-8 -*-
"""重新撰写 5 个 Release 的更新说明（信息更完整、格式统一）。

本轮改进（相对上一版正文）：
1. v1.2.0 起的版本顶部加「版本速览」表（发布日期 / 体积 / 最低系统 / 协议）
2. v1.0.0 增加「版本演进与签名说明」，明确首发版定位 + 重签事实 + 升级路径
3. v1.1.0 增加「版本说明」，明确自本版起统一密钥
4. v1.4.0 标注为当前最新版
5. 安装说明统一 3 条，统一「Android 8.0 及以上」表述
"""
import io, os, sys, urllib3, requests
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
urllib3.disable_warnings()

REPO = "2016YYy/whale-roam"


class Pinned(requests.adapters.HTTPAdapter):
    def __init__(self, host, ip, **kw):
        self.host, self.ip = host, ip
        super().__init__(**kw)

    def send(self, request, **kw):
        if request.url.startswith("https://" + self.host):
            request.url = request.url.replace("https://" + self.host, "https://" + self.ip, 1)
            request.headers["Host"] = self.host
        return super().send(request, **kw)


LINK = ("完整变更历史见 [CHANGELOG.md]"
        "(https://github.com/2016YYy/whale-roam/blob/main/CHANGELOG.md)。")


def overview(size_mb, date="2026-10-17"):
    lines = ["| 项目 | 内容 |", "| --- | --- |"]
    lines.append("| 发布日期 | %s |" % date)
    lines.append("| 安装包大小 | %s |" % size_mb)
    lines.append("| 最低系统 | Android 8.0（API 26） |")
    lines.append("| 开源协议 | Apache License 2.0 |")
    return "\n".join(lines)


B = {}

B["v1.0.0"] = """## 1.0.0 更新说明

> 这是鲸游 WhaleRoam 的**首个正式版本**。当前最新版为 **v1.4.0**，建议直接下载最新版。

### 新增
- **虚拟定位**：基于 Android Mock Location API 的免 Root 定位模拟，GPS + NETWORK 双 Provider 同时注入，避免被真实网络定位覆盖
- **开源地图**：基于 OpenStreetMap（osmdroid），无需任何地图 API Key
- **地点搜索与坐标输入**：地名搜索（Nominatim）与经纬度直输
- **悬浮摇杆**：悬浮窗八方向连续漂移，速度可调
- **位置收藏**：一键回到常用位置，支持删除
- **应用内更新**：检查更新、前台 Service 下载、进度条通知、切后台不中断、完成后唤起安装
- **设置页**：摇杆速度、模拟精度、免责声明、关于

### 安装说明
1. 下载 `whale-roam-v1.0.0.apk` 安装（Android 8.0 及以上）
2. 首次启动需阅读并同意免责声明；随后在「开发者选项 - 选择模拟位置应用」中选择「鲸游」
3. 本包为项目首个版本，无历史版本可升级

### 版本演进与签名说明
- **v1.0.0 是历史首发版**，自 v1.1.0 起版本进入正轨，当前最新为 v1.4.0
- 本版原始资产使用早期临时密钥签名（证书 SHA-256 `f0e26e8c`），与 v1.1.0 及之后版本的发布密钥（`137d9f9c`）**不一致**，因此**无法直接覆盖升级到 v1.1.0+**
- 该资产已于 2026-10-17 用统一发布密钥重新签名，因此**现在从 Releases 下载的 v1.0.0 可直接升级到 v1.4.0**
- 若你在重签之前就安装过旧版 v1.0.0，升级到 v1.1.0 及更高版本前需先卸载一次（会丢失历史收藏）

""" + LINK + "\n"

B["v1.1.0"] = """## 1.1.0 更新说明

### 新增
- **首次启动免责声明**：必须点击「同意并继续」方可使用，设置页可随时回看
- **启动自动检查更新**：默认 24 小时一次，静默检测，只在发现新版本时弹窗，不打扰
- **下载可取消**：下载通知新增「取消下载」按钮，取消后自动清理残缺文件
- **统一签名校验**：更新安装前校验安装包签名与当前应用一致，签名不符拒绝安装，覆盖升级不丢数据

### 修复
- **悬浮摇杆开启即崩溃**：前台服务未调用 `startForeground()`，Android 8+ 触发 `ForegroundServiceDidNotStartInTimeException`；现已按 Android 14 规范声明 `specialUse` 前台服务类型并正常发通知
- **源码无法构建**：release 构建脚本 keystore 属性引用错对象（`keystorePropsFile.getProperty`），现改为 `keystoreProps.getProperty`
- **停止模拟易误触**：停止操作新增二次确认

### 安装说明
1. 下载 `whale-roam-v1.1.0.apk` 安装（Android 8.0 及以上）
2. 本包与 v1.2.0 至 v1.4.0 使用同一发布密钥，可正常覆盖升级；但与 v1.0.0 的原始签名不一致。若你在 v1.0.0 资产重签前安装过旧版，请先卸载一次（会丢失历史收藏）
3. 已安装旧版本可通过 应用内「设置 - 检查更新」直接升级

### 版本说明
- 自本版本起，所有 Release 包统一使用同一把发布密钥（`whale-roam-release.jks`），之后版本均可直接覆盖升级
- v1.0.0 的签名历史差异见 v1.0.0 Release 说明

""" + LINK + "\n"


def build(ver, apk, size_mb, added, changed=None, fixed=None, extra=None, lead=False):
    p = ["## %s 更新说明" % ver, ""]
    p.append(overview(size_mb))
    p.append("")
    p.append("### 新增")
    p += ["- " + x for x in added]
    if changed:
        p += ["", "### 变更"] + ["- " + x for x in changed]
    if fixed:
        p += ["", "### 修复"] + ["- " + x for x in fixed]
    p += ["", "### 安装说明",
          "1. 下载 `%s` 安装（Android 8.0 及以上）" % apk]
    p.append("2. 与 v1.1.0 及之后的版本使用同一签名，可直接覆盖升级，历史收藏与设置不丢失")
    p.append("3. 已安装旧版本可通过 应用内「设置 - 检查更新」直接升级")
    if extra:
        p += ["", "### 版本说明"] + ["- " + x for x in extra]
    p += ["", LINK, ""]
    return "\n".join(p)

B["v1.2.0"] = build(
    "1.2.0", "whale-roam-v1.2.0.apk", "5.6 MB",
    added=[
        "**地图长按查询**：长按地图任意位置，自动逆地理编码并弹窗展示该点详细地址，支持「设为此处 / 收藏此处 / 复制地址」",
        "**多坐标系输入**：搜索框输入经纬度时可选择坐标系（WGS-84 / GCJ-02 / BD-09），自动换算为 WGS-84，可直接使用高德、百度地图复制的坐标",
        "**CloudDrive 风格更新窗口**：检查更新、更新说明、对话框内嵌下载进度条（百分比 + 已下载 / 总量）、一键安装；关闭窗口后下载自动转入通知栏后台继续",
        "**设置页入口**：新增「检查更新」入口与「当前版本」显示",
    ],
    changed=[
        "**主界面 UI 重构**：圆角胶囊搜索栏上移、坐标 / 状态悬浮卡片置于地图左上角、启动 / 停止改为地图右下角带图标悬浮按钮、底部操作条收纳摇杆与收藏",
        "**免责声明按钮文案**：改为「我同意 / 不同意」",
        "**启动检查更新的提示**：已是最新版本时增加吐司提示（检查失败仍静默）",
    ],
    extra=["自 v1.1.0 起所有版本使用同一发布密钥，本版本可直接覆盖升级"],
)

B["v1.3.0"] = build(
    "1.3.0", "whale-roam-v1.3.0.apk", "5.7 MB",
    added=[
        "**注入字段仿真**：伪造的 `Location` 携带完整字段（海拔默认 55m 可调、速度、方向角 bearing、GPS 卫星数 extras=7），并沿用 `elapsedRealtimeNanos` 避免时间一致性校验暴露",
        "**1 秒循环重注**：模拟定位从事件触发改为 1s 周期持续刷新（独立 HandlerThread 前台优先级），任何时刻读取到的都是模拟值，避免被真实定位顶掉",
        "**随机偏移**：可选的位置微抖动，偏移半径可调（默认 10m），每次注入独立随机，用于规避一致性检测",
        "**走 / 跑 / 骑三档速度**：设置页可分别预设步行（默认 1.2 m/s）、跑步（3.6）、骑行（10.0）速度；悬浮摇杆顶部新增档位切换按钮，一键循环切换",
        "**启动前置检查链**：启动模拟前依次检查「是否被选为模拟位置应用、系统定位服务是否开启、WiFi 干扰提醒」",
        "**自动记录定位历史**：启动模拟时自动入库，异步逆地理编码回填地址名称（失败保持坐标标签）",
        "**历史记录过期归档**：默认保留 7 天，可在设置中调整（1 至 365 天），读取时自动清理过期记录",
    ],
    changed=[
        "**侧边抽屉导航**：主界面重构为 DrawerLayout 侧边栏（历史记录 / 设置 / 检查更新 / 免责声明 / 关于），替代顶部菜单",
        "**设置页分组重构**：定位注入仿真（精度 / 海拔 / 随机偏移）/ 摇杆速度（三档）/ 历史有效期 / 关于",
        "**摇杆速度取值**：改为按档位读取设置值，档位切换立即生效",
    ],
    extra=[
        "本版本能力依据《GoGoGo（影梭）逆向分析报告》对标补齐",
        "自 v1.1.0 起所有版本使用同一发布密钥，本版本可直接覆盖升级",
    ],
)

B["v1.4.0"] = build(
    "1.4.0", "whale-roam-v1.4.0.apk", "5.6 MB",
    added=[
        "**摇杆无级调速**：推杆量按 0~1 归一化，实际速度 = 当前档位速度乘推杆量，可以慢速微调；此前只要推杆超过阈值就按满速移动",
        "**摇杆锁定持续移动**：点击摇杆中心锁定 / 解锁，锁定后松手仍按最后方向持续移动，解锁后回中停止（锁定为瞬时状态，摇杆服务重启后自动解除）",
        "**悬浮摇杆可拖动换位**：长按摇杆外圈拖动整个悬浮窗，松手记住位置，下次开启沿用",
        "**路线录制与回放**：从侧边抽屉菜单「开始录制路线 / 停止录制」，录制摇杆移动轨迹并按距离 + 时间自适应采样（移动中 0.5m / 200ms，原地 1s），保存为命名路线；支持按原速 / 倍速回放、循环回放与删除路线",
        "**通知栏快捷操作**：模拟定位通知新增「停止」按钮，悬浮摇杆通知新增「关闭」按钮",
        "**摇杆开启前置检查**：模拟未启动时开启摇杆会明确提示，不再出现「开了没反应」",
    ],
    fixed=[
        "**源码版本号与线上发布不一致**：仓库内版本号此前停留在 versionCode=2 / 1.1.0，低于线上 v1.3.0 的 versionCode=4，从源码构建的包版本号倒退会导致用户覆盖升级失败；现修正为 5 / 1.4.0，并确保此后每次发版都提交版本号",
        "**松开摇杆后注入速度不归零**：速度字段停留在上一次非零值而位置已静止，容易被运动类 App 的一致性校验识别；现松手即归零",
        "**热切换目标点时精度不生效**：`MockLocationEngine.push()` 的 `accuracy` 参数此前被忽略，现按传入值生效",
        "**历史记录有效期上限被 UI 卡死**：设置项允许 1 至 365 天，但设置页 SeekBar 上限只有 30 天，现放开到 365 天",
        "**停止模拟后悬浮摇杆仍在运行**：状态不一致、摇杆继续占着前台服务，现停止模拟时一并关闭摇杆",
        "**通知栏关闭摇杆后主界面按钮状态未同步**：按钮仍显示为开启，与服务实际状态不一致，现摇杆服务销毁时同步主界面开关状态",
        "**停止模拟时录制状态悬挂**：若正在录制路线，停止模拟后录制标记未清理、无法再次开始录制，现停止模拟时自动取消录制并给出提示",
        "**更新下载取消后可能重复回调完成事件**：取消标志与下载线程存在竞争，现加双重检查",
    ],
    extra=[
        "**这是当前最新版本**，自 v1.1.0 起所有版本使用同一发布密钥，可从任意 v1.1.0 以上版本直接覆盖升级",
    ],
    lead=True,
)


def main():
    s = requests.Session()
    s.mount("https://api.github.com", Pinned("api.github.com", "20.205.243.168"))
    s.verify = False
    s.headers.update({
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "Accept": "application/vnd.github+json",
        "User-Agent": "whale-roam-release",
    })
    rels = s.get("https://api.github.com/repos/%s/releases?per_page=100" % REPO, timeout=60).json()
    by_tag = {r["tag_name"]: r for r in rels}

    local = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                          "_build-cache", "release-notes")
    os.makedirs(local, exist_ok=True)

    for tag in ["v1.0.0", "v1.1.0", "v1.2.0", "v1.3.0", "v1.4.0"]:
        body = B[tag]
        rel = by_tag[tag]
        r = s.patch("https://api.github.com/repos/%s/releases/%d" % (REPO, rel["id"]),
                    json={"body": body}, timeout=60)
        status = "OK" if r.status_code < 400 else "FAIL %d" % r.status_code
        print("%s: %s  body=%d chars" % (tag, status, len(body)))
        open(os.path.join(local, "body-%s.md" % tag), "w", encoding="utf-8").write(body)
        if r.status_code >= 400:
            print(r.text[:300])

    print("\nDONE")


if __name__ == "__main__":
    main()
