"""镜像源可用性探测工具。

用途：增删 UpdateMirror.LATEST_JSON_SOURCES / apkCandidates 里的镜像前，
先用本脚本实测一遍，避免留死源拖慢测速或误选失效线路。

用法（仓库根目录）：
    python tools/audit/probe_mirrors.py
    python tools/audit/probe_mirrors.py --version 1.5.0   # 指定要探测的 APK 版本

判定标准：
  - latest.json 源：HTTP 200 且能解析出 versionName / versionCode
  - APK 候选：HTTP 200/206 且 content-length 与预期一致
"""
import io
import sys
import time
import argparse

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import requests
import urllib3

urllib3.disable_warnings()

OWNER, REPO, BRANCH = "2016YYy", "whale-roam", "main"
RAW = "https://raw.githubusercontent.com/%s/%s/%s/latest.json" % (OWNER, REPO, BRANCH)

# 与 UpdateMirror.kt 保持一致：增删镜像时两处同步改
MIRRORS = ["ghfast.top", "gh-proxy.com", "ghproxy.net", "ghproxy.cc"]

def session():
    s = requests.Session()
    s.verify = False
    s.headers["User-Agent"] = "whale-roam-probe"
    return s


def probe_latest(s, name, url):
    t0 = time.time()
    try:
        r = s.get(url, timeout=8)
        el = (time.time() - t0) * 1000
        if r.status_code != 200:
            return False, "%d %.0fms" % (r.status_code, el)
        d = r.json()
        if "versionName" not in d or "versionCode" not in d:
            return False, "200 但缺字段 %.0fms" % el
        return True, "%d %.0fms v=%s c=%s" % (r.status_code, el, d["versionName"], d["versionCode"])
    except Exception as e:
        return False, "%s %.0fms" % (type(e).__name__, (time.time() - t0) * 1000)


def probe_apk(s, name, url, expect_size):
    t0 = time.time()
    try:
        r = s.head(url, timeout=8, allow_redirects=True)
        el = (time.time() - t0) * 1000
        if r.status_code not in (200, 206):
            return False, "%d %.0fms" % (r.status_code, el)
        size = r.headers.get("content-length")
        if expect_size and size and int(size) != expect_size:
            return False, "%d 但 size=%s≠%d" % (r.status_code, size, expect_size)
        return True, "%d %.0fms size=%s" % (r.status_code, el, size)
    except Exception as e:
        return False, "%s %.0fms" % (type(e).__name__, (time.time() - t0) * 1000)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default=None, help="要探测的 APK 版本，如 1.5.0")
    ap.add_argument("--size", type=int, default=None, help="预期 APK 字节数（可选）")
    args = ap.parse_args()

    s = session()
    print("=" * 72)
    print("镜像源可用性探测")
    print("=" * 72)

    print("\n[1] latest.json 源")
    print("-" * 72)
    rows = [("直连", RAW)] + [(m, "https://%s/%s" % (m, RAW)) for m in MIRRORS]
    rows.append(("jsdelivr", "https://cdn.jsdelivr.net/gh/%s/%s@%s/latest.json" % (OWNER, REPO, BRANCH)))
    good = 0
    for name, url in rows:
        ok, info = probe_latest(s, name, url)
        print("  [%s] %-16s %s" % ("OK" if ok else "FAIL", name, info))
        good += 1 if ok else 0
    print("  可用 %d/%d" % (good, len(rows)))

    if args.version:
        print("\n[2] APK 候选地址（v%s）" % args.version)
        print("-" * 72)
        apk = ("https://github.com/%s/%s/releases/download/v%s/whale-roam-v%s.apk"
               % (OWNER, REPO, args.version, args.version))
        rows2 = [("直连", apk)] + [(m, "https://%s/%s" % (m, apk)) for m in MIRRORS]
        good2 = 0
        for name, url in rows2:
            ok, info = probe_apk(s, name, url, args.size)
            print("  [%s] %-16s %s" % ("OK" if ok else "FAIL", name, info))
            good2 += 1 if ok else 0
        print("  可用 %d/%d" % (good2, len(rows2)))

    print("\n" + "=" * 72)


if __name__ == "__main__":
    main()
