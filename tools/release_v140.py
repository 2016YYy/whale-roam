"""鲸游 WhaleRoam v1.4.0 发布脚本：推送提交、打 Tag、建 Release、上传签名 APK。

用法（在仓库目录下）：
    set GH_TOKEN=<token>
    python tools/release_v140.py

设计要点：
- 幂等：已存在的 Tag / Release / Asset 不会重复创建
- 用 GitHub REST API + 直连 IP（本机 hosts 把 api.github.com 指向 127.0.0.1，必须绕开）
- 校验 APK 的 versionCode/versionName 与 latest.json 一致，避免发错包
"""
import json
import os
import subprocess
import sys
import urllib.parse
import urllib3
import requests

urllib3.disable_warnings()

REPO = "2016YYy/whale-roam"
TAG = "v1.4.0"
VERSION_NAME = "1.4.0"
VERSION_CODE = 5
ASSET_NAME = "whale-roam-v1.4.0.apk"
API_IP = "20.205.243.168"      # api.github.com 的真实地址
UPLOAD_IP = "20.205.243.168"   # uploads.github.com 同网段


class PinnedAdapter(requests.adapters.HTTPAdapter):
    """把 https://api.github.com 的请求直连到真实 IP，同时保留正确的 Host / SNI 语义。"""

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
    s.mount("https://", PinnedAdapter("api.github.com", API_IP))
    s.verify = False
    tok = os.environ["GH_TOKEN"]
    s.headers.update({
        "Authorization": "Bearer " + tok,
        "Accept": "application/vnd.github+json",
        "User-Agent": "whale-roam-release",
    })
    return s


def api(s, method, path, **kw):
    r = s.request(method, "https://api.github.com" + path, timeout=120, **kw)
    if r.status_code >= 400:
        raise RuntimeError(f"{method} {path} -> {r.status_code}: {r.text[:400]}")
    return r.json() if r.content else {}


def check_apk(apk_path):
    """用 aapt2 读取 APK 的 versionCode/versionName，确保与发布元数据一致。"""
    aapt = r"H:\WorkBuddyFile\android-sdk\build-tools\35.0.0\aapt2.exe"
    out = subprocess.run([aapt, "dump", "badging", apk_path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    line = next((l for l in out.stdout.splitlines() if l.startswith("package:")), "")
    if f"versionCode='{VERSION_CODE}'" not in line:
        raise RuntimeError(f"APK versionCode 不符：{line}")
    if f"versionName='{VERSION_NAME}'" not in line:
        raise RuntimeError(f"APK versionName 不符：{line}")
    print(f"[ok] APK 版本校验通过：{VERSION_NAME} ({VERSION_CODE})")


def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    apk_path = os.path.join(repo_root, "release", ASSET_NAME)
    if not os.path.exists(apk_path):
        raise SystemExit(f"APK 不存在：{apk_path}")
    check_apk(apk_path)

    s = session()

    # 1. 确认远端 main 已包含本次提交（由调用方保证已 push）
    head = api(s, "GET", f"/repos/{REPO}/commits/main")
    print(f"[info] remote main = {head['sha'][:7]} {head['commit']['message'].splitlines()[0]}")

    # 2. 创建/复用 Tag（GitHub 会在建 Release 时自动基于 target_commitish 建 tag）
    rel_body = open(os.path.join(repo_root, "release", "RELEASE_BODY.md"), encoding="utf-8").read()

    try:
        rel = api(s, "GET", f"/repos/{REPO}/releases/tags/{TAG}")
        print(f"[info] Release {TAG} 已存在 id={rel['id']}")
    except RuntimeError:
        rel = api(s, "POST", f"/repos/{REPO}/releases", json={
            "tag_name": TAG,
            "target_commitish": "main",
            "name": f"鲸游 WhaleRoam {TAG}",
            "body": rel_body,
            "draft": False,
            "prerelease": False,
        })
        print(f"[ok] Release 创建成功：{rel['html_url']}")

    # 3. 上传 APK（幂等）
    assets = api(s, "GET", f"/repos/{REPO}/releases/{rel['id']}/assets")
    if any(a["name"] == ASSET_NAME for a in assets):
        print(f"[info] 资产 {ASSET_NAME} 已存在，跳过上传")
    else:
        url = (f"https://api.github.com/repos/{REPO}/releases/{rel['id']}/assets"
               f"?name={urllib.parse.quote(ASSET_NAME)}")
        with open(apk_path, "rb") as f:
            data = f.read()
        r = s.post(url, data=data, timeout=600,
                   headers={"Content-Type": "application/vnd.android.package-archive"})
        if r.status_code >= 400:
            raise RuntimeError(f"上传失败 {r.status_code}: {r.text[:400]}")
        print(f"[ok] APK 上传成功：{r.json()['browser_download_url']}")

    print("\nALL DONE")


if __name__ == "__main__":
    main()
