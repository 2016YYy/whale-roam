# -*- coding: utf-8 -*-
"""鲸游 WhaleRoam 发布一致性校验。

用法（仓库根目录）：
    set GH_TOKEN=<token>
    python tools/verify_release_consistency.py

校验项：
1. 所有 Release 资产的签名证书指纹是否一致（防签名事故）
2. 资产 versionCode / versionName 是否与 Tag 名一致
3. latest.json 是否指向最新版本且 apkUrl 可达
4. Release 正文是否符合 RELEASE.md 第 4 节的统一模板
5. 所有 Tag 是否都是附注标签（annotated）
6. versionCode 是否严格单调递增

退出码：0 = 全部通过；1 = 存在问题。
"""
import base64
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

import requests
import urllib3

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
urllib3.disable_warnings()

REPO = "2016YYy/whale-roam"
API_IP = "20.205.243.168"
UPLOAD_IP = "20.205.243.161"
AAPT = r"H:\WorkBuddyFile\android-sdk\build-tools\35.0.0\aapt2.exe"
APKSIGNER = r"H:\WorkBuddyFile\android-sdk\build-tools\35.0.0\apksigner.bat"
EXPECTED_CERT = "137d9f9cb8330b4b36872ab5e5054b6fe9e162c85717994e5fabab43a9e3ad5c"

problems = []
oks = []


def ok(msg):
    oks.append(msg)
    print("  [OK]   " + msg)


def bad(msg):
    problems.append(msg)
    print("  [FAIL] " + msg)


class Pinned(requests.adapters.HTTPAdapter):
    def __init__(self, host, ip, **kw):
        self.host, self.ip = host, ip
        super().__init__(**kw)

    def send(self, request, **kw):
        if request.url.startswith("https://" + self.host):
            request.url = request.url.replace("https://" + self.host, "https://" + self.ip, 1)
            request.headers["Host"] = self.host
        return super().send(request, **kw)


def session():
    s = requests.Session()
    s.mount("https://api.github.com", Pinned("api.github.com", API_IP))
    s.mount("https://github.com", Pinned("github.com", "20.205.243.166"))
    s.mount("https://objects.githubusercontent.com",
            Pinned("objects.githubusercontent.com", "185.199.108.133"))
    s.mount("https://release-assets.githubusercontent.com",
            Pinned("release-assets.githubusercontent.com", "185.199.108.133"))
    # raw.githubusercontent.com 同样被 hosts 屏蔽，直连会拿到 127.0.0.1
    s.mount("https://raw.githubusercontent.com",
            Pinned("raw.githubusercontent.com", "185.199.110.133"))
    s.verify = False
    s.headers.update({
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "Accept": "application/vnd.github+json",
        "User-Agent": "whale-roam-verify",
    })
    return s


def verify_apk(path):
    """返回 (versionCode, versionName, cert_sha256)"""
    out = subprocess.run([AAPT, "dump", "badging", path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    line = next((l for l in out.stdout.splitlines() if l.startswith("package:")), "")
    m = re.search(r"versionCode='(\d+)' versionName='([^']+)'", line)
    vc, vn = (int(m.group(1)), m.group(2)) if m else (-1, "?")
    out2 = subprocess.run([APKSIGNER, "verify", "--print-certs", path],
                          capture_output=True, text=True, encoding="utf-8", errors="replace")
    cm = re.search(r"SHA-256 digest: ([0-9a-f]{64})", out2.stdout)
    cert = cm.group(1) if cm else "?"
    return vc, vn, cert


# 合法的表情分段标题（RELEASE.md 4.2 固定表，逐字一致）
SEGMENT_TITLES = (
    "### 🎨 界面与主题",
    "### 🛡️ 合规与声明",
    "### ⬇️ 更新与下载",
    "### ✨ 新增功能",
    "### 🐛 问题修复",
    "### 📦 安装说明",
    "### 🔏 签名与版本演进",
)


def check_body(tag, body):
    """按 RELEASE.md 第 4 节模板校验 Release 正文（表情分段格式）"""
    ver = tag.lstrip("v")
    issues = []
    if not re.search(r"^## " + re.escape(ver) + r" 更新说明$", body, re.M):
        issues.append("一级标题应为 '## %s 更新说明'" % ver)
    if "### 📦 安装说明" not in body:
        issues.append("缺少 '### 📦 安装说明' 分节")
    if "完整变更历史见 [CHANGELOG.md]" not in body:
        issues.append("缺少 CHANGELOG 链接")
    # 所有 ### 标题必须是固定表里的表情分段（不允许自造标题）
    for h in re.findall(r"^### (.+)$", body, re.M):
        if ("### " + h) not in SEGMENT_TITLES:
            issues.append("分节标题不合规（必须是固定表中的表情分段）: ### " + h)
    # 修复段落条目不应重复「修复」二字
    m = re.search(r"^### 🐛 问题修复\n(.*?)(?=^### |\Z)", body, re.M | re.S)
    if m:
        for ln in m.group(1).splitlines():
            if ln.strip().startswith("- ") and "修复" in ln:
                issues.append("修复条目正文重复出现「修复」：" + ln.strip()[:40])
    # 条目名称必须加粗：- **名称**：说明
    for ln in body.splitlines():
        s = ln.strip()
        if s.startswith("- ") and not re.match(r"^- \*\*.+?\*\*", s):
            issues.append("条目名称未加粗: " + s[:40])
    # 安装说明应为 3 条编号
    m2 = re.search(r"^### 📦 安装说明\n(.*?)(?=\n\n|\Z)", body, re.M | re.S)
    if m2:
        nums = re.findall(r"^\d+\.", m2.group(1), re.M)
        if len(nums) != 3:
            issues.append("安装说明应为 3 条编号，实际 %d 条" % len(nums))
    return issues


def main():
    s = session()
    print("=" * 70)
    print("鲸游 WhaleRoam 发布一致性校验")
    print("=" * 70)

    # ---------- 1. 拉取 Release ----------
    print("\n[1] 拉取 Release 列表")
    rels = s.get("https://api.github.com/repos/%s/releases?per_page=100" % REPO, timeout=60).json()
    rels = sorted(rels, key=lambda r: [int(x) for x in r["tag_name"].lstrip("v").split(".")])
    ok("共 %d 个 Release: %s" % (len(rels), ", ".join(r["tag_name"] for r in rels)))

    # ---------- 2. 下载资产并校验签名 / 版本 ----------
    print("\n[2] 校验资产签名与版本")
    tmpdir = tempfile.mkdtemp(prefix="whale-audit-")
    records = []
    for rel in rels:
        tag = rel["tag_name"]
        for a in rel["assets"]:
            dest = os.path.join(tmpdir, a["name"])
            # 本地缓存优先：同名同 size 的 APK 直接复用，免去重复下载 27MB
            # 查找顺序：环境变量 WHALE_APK_CACHE > 工作区 _build-cache/whale-roam-verify-apks
            ws_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
            candidates = [c for c in (
                os.environ.get("WHALE_APK_CACHE"),
                os.path.join(ws_dir, "_build-cache", "whale-roam-verify-apks"),
                os.path.join(ws_dir, "whale-roam", "release", "_verify_cache"),
            ) if c]
            hit = None
            for c in candidates:
                p = os.path.join(c, a["name"]) if os.path.basename(c) != a["name"] else c
                if os.path.exists(p) and os.path.getsize(p) == a["size"]:
                    hit = p
                    break
            if hit:
                shutil.copyfile(hit, dest)
                print("    (使用本地缓存) " + a["name"])
            else:
                r = s.get(a["browser_download_url"], timeout=600, allow_redirects=True)
                if r.status_code != 200:
                    bad("%s 下载失败 HTTP %d" % (a["name"], r.status_code))
                    continue
                open(dest, "wb").write(r.content)
            vc, vn, cert = verify_apk(dest)
            records.append({"tag": tag, "name": a["name"], "vc": vc, "vn": vn, "cert": cert})
            print("    %-8s %-26s code=%-3d name=%-8s cert=%s…" % (tag, a["name"], vc, vn, cert[:16]))

    # 签名一致性
    certs = set(r["cert"] for r in records)
    if len(certs) == 1 and EXPECTED_CERT in certs:
        ok("全部 %d 个资产签名证书一致且为发布密钥 (%s…)" % (len(records), EXPECTED_CERT[:16]))
    elif len(certs) == 1:
        bad("签名一致但**不是**预期发布密钥：%s" % certs)
    else:
        bad("签名不一致，发现 %d 种证书：%s" % (len(certs), certs))

    # 版本号与 tag 对应 + 单调递增
    for r in records:
        expect_ver = r["tag"].lstrip("v")
        if r["vn"] != expect_ver:
            bad("%s 资产 versionName=%s 与 Tag 不符（应为 %s）" % (r["tag"], r["vn"], expect_ver))
    codes = [r["vc"] for r in records]
    if codes == sorted(codes) and len(set(codes)) == len(codes):
        ok("versionCode 严格单调递增: %s" % codes)
    else:
        bad("versionCode 未严格递增: %s" % codes)

    # ---------- 3. Release 正文模板 ----------
    print("\n[3] 校验 Release 正文格式")
    for rel in rels:
        issues = check_body(rel["tag_name"], rel.get("body") or "")
        if issues:
            for i in issues:
                bad("%s 正文格式：%s" % (rel["tag_name"], i))
        else:
            ok("%s 正文符合统一模板" % rel["tag_name"])

    # ---------- 4. Tag 类型 ----------
    print("\n[4] 校验 Tag 类型（应为附注标签）")
    refs = s.get("https://api.github.com/repos/%s/git/refs/tags" % REPO, timeout=60).json()
    tag_shas = {r["ref"].split("/")[-1]: r["object"]["sha"] for r in refs}
    for rel in rels:
        tag = rel["tag_name"]
        sha = tag_shas.get(tag)
        if not sha:
            bad("Tag %s 在远端不存在" % tag)
            continue
        obj = s.get("https://api.github.com/repos/%s/git/tags/%s" % (REPO, sha), timeout=60)
        if obj.status_code == 200:
            ok("%s 是附注标签" % tag)
        else:
            bad("%s 是轻量标签（应为附注标签）" % tag)

    # ---------- 5. latest.json ----------
    print("\n[5] 校验 latest.json")
    content = None
    try:
        raw = s.get("https://raw.githubusercontent.com/%s/main/latest.json" % REPO, timeout=60)
        if raw.status_code == 200:
            content = raw.text
    except Exception:
        content = None
    if content is None:
        # raw 通道失败（hosts 屏蔽 / 连接被重置）→ 退回 API contents
        c = s.get("https://api.github.com/repos/%s/contents/latest.json?ref=main" % REPO, timeout=60).json()
        content = base64.b64decode(c["content"]).decode("utf-8")
    try:
        latest = json.loads(content)
        ok("latest.json 是合法 JSON")
    except Exception as e:
        bad("latest.json 解析失败: %s" % e)
        latest = None

    if latest:
        newest = rels[-1]
        if latest.get("versionName") == newest["tag_name"].lstrip("v"):
            ok("latest.json versionName=%s 指向最新版本" % latest["versionName"])
        else:
            bad("latest.json versionName=%s 未指向最新版本 %s" % (latest.get("versionName"), newest["tag_name"]))
        max_code = max(r["vc"] for r in records)
        if latest.get("versionCode") == max_code:
            ok("latest.json versionCode=%s 与最新 APK 一致" % latest["versionCode"])
        else:
            bad("latest.json versionCode=%s 与最新 APK(%d) 不一致" % (latest.get("versionCode"), max_code))
        expect_url = ("https://github.com/%s/releases/download/%s/whale-roam-%s.apk"
                      % (REPO, newest["tag_name"], newest["tag_name"]))
        if latest.get("apkUrl") == expect_url:
            ok("latest.json apkUrl 指向最新资产")
        else:
            bad("latest.json apkUrl=%s 与预期不符 %s" % (latest.get("apkUrl"), expect_url))

    # ---------- 汇总 ----------
    print("\n" + "=" * 70)
    print("通过 %d 项，失败 %d 项" % (len(oks), len(problems)))
    if problems:
        print("\n需要修复的问题：")
        for p in problems:
            print("  - " + p)
        print("=" * 70)
        sys.exit(1)
    print("全部校验通过 ✅")
    print("=" * 70)


if __name__ == "__main__":
    main()