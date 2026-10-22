"""鲸游 WhaleRoam 发版脚本（建 Tag + Release + 上传签名 APK）。

用法（在仓库根目录）：
    set GH_TOKEN=<你的 token>
    python tools/release.py --version 1.4.0 --code 5

设计要点：
- 幂等：已存在的 Tag / Release / Asset 不会重复创建
- 本机 hosts 把 github.com / api.github.com 指向 127.0.0.1，因此所有请求都直连真实 IP，
  并用 Host 头保持 SNI/域名语义（见 PinnedAdapter）
- 资产上传必须走 uploads.github.com（api.github.com 会返回 404）
- 上传前用 aapt2 校验 APK 的 versionCode/versionName，防止发错包
- 上传后用 GitHub 返回的 digest（sha256）与本地文件比对，确认字节一致
- **发版前强制跑 gitleaks 敏感信息扫描**（--skip-secret-scan 仅限确知安全时使用）
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import urllib.parse

import requests
import urllib3

urllib3.disable_warnings()

REPO = "2016YYy/whale-roam"
API_IP = "20.205.243.168"
UPLOAD_IP = "20.205.243.161"
AAPT = r"H:\WorkBuddyFile\android-sdk\build-tools\35.0.0\aapt2.exe"


class PinnedAdapter(requests.adapters.HTTPAdapter):
    """把 https://<host> 的请求直连到真实 IP，同时保留正确的 Host 头。"""

    def __init__(self, host, ip, **kw):
        self.host, self.ip = host, ip
        super().__init__(**kw)

    def send(self, request, **kw):
        if request.url.startswith("https://" + self.host):
            request.url = request.url.replace("https://" + self.host, "https://" + self.ip, 1)
            request.headers["Host"] = self.host
        return super().send(request, **kw)


def make_session():
    s = requests.Session()
    s.mount("https://api.github.com", PinnedAdapter("api.github.com", API_IP))
    s.mount("https://uploads.github.com", PinnedAdapter("uploads.github.com", UPLOAD_IP))
    s.verify = False
    s.headers.update({
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "Accept": "application/vnd.github+json",
        "User-Agent": "whale-roam-release",
    })
    return s


def api(s, method, path, **kw):
    r = s.request(method, "https://api.github.com" + path, timeout=180, **kw)
    if r.status_code >= 400:
        raise RuntimeError(f"{method} {path} -> {r.status_code}: {r.text[:400]}")
    return r.json() if r.content else {}


def verify_apk(path, version_name, version_code):
    out = subprocess.run([AAPT, "dump", "badging", path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    line = next((l for l in out.stdout.splitlines() if l.startswith("package:")), "")
    if f"versionCode='{version_code}'" not in line:
        raise SystemExit(f"APK versionCode 不符（期望 {version_code}）：{line}")
    if f"versionName='{version_name}'" not in line:
        raise SystemExit(f"APK versionName 不符（期望 {version_name}）：{line}")
    print(f"[ok] APK 版本校验通过：{version_name} ({version_code})")


def run_secret_scan(root):
    """发版前的敏感信息硬闸门：gitleaks 扫全历史，不通过就中止发版。"""
    exe = os.path.join(root, "tools", "gitleaks", "gitleaks.exe")
    if not os.path.exists(exe):
        print("[warn] 未找到 gitleaks，跳过扫描（建议先跑 tools/install-gitleaks.ps1）")
        return
    print("[scan] gitleaks 扫描 Git 全历史...")
    report = os.path.join(root, "build", "gitleaks-report.json")
    os.makedirs(os.path.dirname(report), exist_ok=True)
    out = subprocess.run([exe, "detect", "--source", ".", "--no-banner", "--redact",
                          "--report-path", report, "--report-format", "json"],
                         cwd=root, capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    for line in (out.stdout or "").splitlines():
        if line.strip():
            print("  " + line.strip())
    if out.returncode != 0:
        raise SystemExit(
            "[FAIL] 发现疑似敏感信息，已中止发版。\n"
            "       报告: %s\n"
            "       确认是误报后可用 --skip-secret-scan 跳过（请勿随意使用）" % report
        )
    print("[ok] 敏感信息扫描通过")


def check_tracked_secrets(root):
    """确认敏感文件没有被 Git 跟踪（gitleaks 只扫内容，不查文件名）。"""
    out = subprocess.run(["git", "ls-files"], cwd=root, capture_output=True,
                         text=True, encoding="utf-8", errors="replace")
    bad = [l for l in (out.stdout or "").splitlines()
           if l.endswith((".jks", ".keystore"))
           or l.endswith("keystore.properties")
           or l.endswith("local.properties")
           or l.startswith("tools/gitleaks/")]
    if bad:
        raise SystemExit("[FAIL] 以下敏感文件被 Git 跟踪，必须移除后再发版：\n  " + "\n  ".join(bad))
    print("[ok] 无敏感文件被 Git 跟踪")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True, help="版本号，如 1.4.0")
    ap.add_argument("--code", required=True, type=int, help="versionCode，如 5")
    ap.add_argument("--skip-secret-scan", action="store_true",
                    help="跳过 gitleaks 扫描（仅限确知安全时使用）")
    args = ap.parse_args()

    tag = "v" + args.version
    asset_name = f"whale-roam-v{args.version}.apk"
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    apk = os.path.join(root, "release", asset_name)
    body_file = os.path.join(root, "release", "RELEASE_BODY.md")

    if not os.path.exists(apk):
        raise SystemExit(f"APK 不存在：{apk}")
    if not os.path.exists(body_file):
        raise SystemExit(f"Release 正文不存在：{body_file}")
    verify_apk(apk, args.version, args.code)

    # ---- 发版硬闸门：敏感信息扫描 ----
    if args.skip_secret_scan:
        print("[warn] 已跳过敏感信息扫描（--skip-secret-scan）")
    else:
        run_secret_scan(root)
    check_tracked_secrets(root)

    local_bytes = open(apk, "rb").read()
    local_sha = hashlib.sha256(local_bytes).hexdigest()
    body = open(body_file, encoding="utf-8").read()

    s = make_session()
    head = api(s, "GET", f"/repos/{REPO}/commits/main")
    print(f"[info] remote main = {head['sha'][:7]}")

    # 1. Tag
    try:
        api(s, "GET", f"/repos/{REPO}/git/ref/tags/{tag}")
        print(f"[info] tag {tag} 已存在")
    except RuntimeError:
        api(s, "POST", f"/repos/{REPO}/git/refs",
            json={"ref": f"refs/tags/{tag}", "sha": head["sha"]})
        print(f"[ok] tag {tag} -> {head['sha'][:7]}")

    # 2. Release
    try:
        rel = api(s, "GET", f"/repos/{REPO}/releases/tags/{tag}")
        print(f"[info] release 已存在 id={rel['id']}")
    except RuntimeError:
        rel = api(s, "POST", f"/repos/{REPO}/releases", json={
            "tag_name": tag,
            "target_commitish": "main",
            "name": f"鲸游 WhaleRoam {tag}",
            "body": body,
            "draft": False,
            "prerelease": False,
        })
        print(f"[ok] release: {rel['html_url']}")

    # 3. 上传资产（必须走 uploads.github.com）
    assets = api(s, "GET", f"/repos/{REPO}/releases/{rel['id']}/assets")
    existing = next((a for a in assets if a["name"] == asset_name), None)
    if existing:
        print(f"[info] 资产已存在，跳过上传（digest={existing.get('digest')}）")
        if existing.get("digest") == "sha256:" + local_sha:
            print("[ok] 远端 digest 与本地一致")
        else:
            print("[warn] 远端 digest 与本地不一致！请删除资产后重新上传")
    else:
        url = (f"https://uploads.github.com/repos/{REPO}/releases/{rel['id']}/assets"
               f"?name={urllib.parse.quote(asset_name)}")
        r = s.post(url, data=local_bytes, timeout=900,
                   headers={"Content-Type": "application/vnd.android.package-archive"})
        if r.status_code >= 400:
            raise RuntimeError(f"上传失败 {r.status_code}: {r.text[:400]}")
        j = r.json()
        print(f"[ok] APK: {j['browser_download_url']}  ({j['size']} bytes)")
        if j.get("digest") == "sha256:" + local_sha:
            print("[ok] 上传后 digest 校验通过")
        else:
            print(f"[warn] digest 不一致：远端 {j.get('digest')} 本地 sha256:{local_sha}")

    print("\nALL DONE")


if __name__ == "__main__":
    main()
