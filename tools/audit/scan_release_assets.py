# -*- coding: utf-8 -*-
"""扫描 GitHub 上所有 Release 资产是否泄露敏感信息。

做三件事：
1. 拉取全部 Release 与资产，逐个下载（走镜像）
2. 用 gitleaks dir 扫每个资产
3. APK 额外做高强度模式匹配：解开 dex，搜常见密钥前缀 / 签名口令 / 私钥块

用法（仓库根目录）：
    set GH_TOKEN=<token>
    python tools/audit/scan_release_assets.py
    python tools/audit/scan_release_assets.py --no-download   # 用本地缓存，不重新下载

退出码：0 = 未发现泄露；1 = 发现疑似泄露。
"""
import argparse
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import time
import zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import requests
import urllib3

urllib3.disable_warnings()

REPO = "2016YYy/whale-roam"
API_IP = "20.205.243.168"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CACHE = os.path.join(os.path.dirname(ROOT), "_build-cache", "cloud-assets")
GITLEAKS = os.path.join(ROOT, "tools", "gitleaks", "gitleaks.exe")

# 高强度模式：命中即报告（比 gitleaks 默认更严格，覆盖 GitHub/云厂商/签名口令）
PATTERNS = [
    ("GitHub PAT", re.compile(rb"github_pat_[A-Za-z0-9_]{20,}")),
    ("GitHub token", re.compile(rb"gh[pousr]_[A-Za-z0-9]{36,}")),
    ("AWS AK", re.compile(rb"AKIA[0-9A-Z]{16}")),
    ("Google API", re.compile(rb"AIza[0-9A-Za-z_\-]{35}")),
    ("Slack", re.compile(rb"xox[baprs]-[0-9A-Za-z\-]{10,}")),
    ("私钥块", re.compile(rb"-----BEGIN (RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----")),
    ("签名口令", re.compile(rb"storePassword\s*=\s*\S+")),
    ("Bearer 硬编码", re.compile(rb"Authorization:\s*Bearer\s+[A-Za-z0-9_\-\.]{20,}")),
]


class Pinned(requests.adapters.HTTPAdapter):
    def __init__(self, host, ip, **kw):
        self.host, self.ip = host, ip
        super().__init__(**kw)

    def send(self, request, **kw):
        if request.url.startswith("https://" + self.host):
            request.url = request.url.replace("https://" + self.host, "https://" + self.ip, 1)
            request.headers["Host"] = self.host
        return super().send(request, **kw)


def get_token():
    """优先环境变量；否则读工作区 _build-cache/.gh_token（由凭据管理器导出）。"""
    tok = os.environ.get("GH_TOKEN")
    if tok:
        return tok.strip()
    cache = os.path.join(os.path.dirname(ROOT), "_build-cache", ".gh_token")
    if os.path.exists(cache):
        return open(cache, encoding="utf-8").read().strip()
    raise SystemExit("缺少 GH_TOKEN（环境变量或 _build-cache/.gh_token）")


def api_session():
    s = requests.Session()
    s.mount("https://api.github.com", Pinned("api.github.com", API_IP))
    s.verify = False
    s.headers.update({
        "Authorization": "Bearer " + get_token(),
        "Accept": "application/vnd.github+json",
        "User-Agent": "whale-asset-scan",
    })
    return s


def dl_session():
    s = requests.Session()
    s.verify = False
    s.headers["User-Agent"] = "whale-asset-scan"
    return s


def download(s, url, dest, expect_size):
    if os.path.exists(dest) and os.path.getsize(dest) == expect_size:
        return True
    mirrors = ["https://gh-proxy.com/" + url, "https://ghfast.top/" + url, url]
    for m in mirrors:
        try:
            r = s.get(m, timeout=180, allow_redirects=True)
            if r.status_code == 200 and len(r.content) == expect_size:
                open(dest, "wb").write(r.content)
                return True
        except Exception:
            continue
    return False


def scan_file_patterns(path):
    """对文件做模式匹配；APK 会解开 dex 一起扫。"""
    hits = []
    blobs = []
    try:
        raw = open(path, "rb").read()
        blobs.append(("raw", raw))
        if path.lower().endswith(".apk"):
            try:
                with zipfile.ZipFile(path) as z:
                    for n in z.namelist():
                        if n.startswith("classes") and n.endswith(".dex"):
                            blobs.append(("dex:" + n, z.read(n)))
                        elif n.endswith((".xml", ".json", ".properties")):
                            blobs.append(("apk:" + n, z.read(n)))
            except Exception as e:
                hits.append(("APK 解析失败", str(e)))
    except Exception as e:
        return [("读取失败", str(e))]

    for label, blob in blobs:
        for name, pat in PATTERNS:
            m = pat.search(blob)
            if m:
                snippet = m.group(0)[:60].decode("utf-8", "replace")
                hits.append((name, label + " -> " + snippet))
    return hits


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-download", action="store_true", help="只用本地缓存")
    args = ap.parse_args()

    os.makedirs(CACHE, exist_ok=True)
    s = api_session()
    ds = dl_session()
    problems = []

    print("=" * 74)
    print("云端 Release 资产敏感信息扫描")
    print("=" * 74)

    rels = s.get(f"https://api.github.com/repos/{REPO}/releases?per_page=100", timeout=60).json()
    rels.sort(key=lambda r: [int(x) for x in r["tag_name"].lstrip("v").split(".")])
    print(f"共 {len(rels)} 个 Release\n")

    for rel in rels:
        tag = rel["tag_name"]
        for a in rel["assets"]:
            name = a["name"]
            size = a["size"]
            print(f"[{tag}] {name}  ({size} bytes)")

            dest = os.path.join(CACHE, name)
            if not args.no_download:
                t0 = time.time()
                okdl = download(ds, a["browser_download_url"], dest, size)
                if not okdl:
                    print("    [FAIL] 下载失败")
                    problems.append(f"{name}: 下载失败，未能扫描")
                    continue
                print(f"    下载完成 {time.time()-t0:.1f}s")
            elif not os.path.exists(dest):
                print("    [SKIP] 无本地缓存")
                continue

            # 1) 模式匹配
            hits = scan_file_patterns(dest)
            if hits:
                for kind, detail in hits:
                    print(f"    [HIT] {kind}: {detail}")
                    problems.append(f"{name}: {kind} ({detail})")
            else:
                print("    [ok] 未命中密钥模式")

            # 2) gitleaks dir
            if os.path.exists(GITLEAKS):
                out = subprocess.run([GITLEAKS, "dir", dest, "--no-banner", "--redact"],
                                     capture_output=True, text=True,
                                     encoding="utf-8", errors="replace")
                txt = ((out.stdout or "") + (out.stderr or "")).strip()
                if out.returncode != 0:
                    print("    [gitleaks] 命中：")
                    for line in txt.splitlines()[-6:]:
                        print("      " + line.strip())
                    problems.append(f"{name}: gitleaks 命中")
                else:
                    print("    [ok] gitleaks dir 通过")
            print()

    print("=" * 74)
    if problems:
        print(f"发现 {len(problems)} 个问题：")
        for p_ in problems:
            print("  - " + p_)
        print("=" * 74)
        sys.exit(1)
    print("全部资产未发现敏感信息泄露 ✅")
    print("=" * 74)


if __name__ == "__main__":
    main()
