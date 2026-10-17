# -*- coding: utf-8 -*-
"""用统一发布密钥重签 v1.0.0 并替换 Release 资产。

背景：v1.0.0 原始资产用的是早期临时密钥（SHA-256 f0e26e8c...），
与 v1.1.0+ 的发布密钥（137d9f9c...）不一致，导致 v1.0.0 无法覆盖升级到后续版本。
该早期密钥已不在本机任何 keystore 中，无法用它重新签名后续版本，
因此只能把 v1.0.0 资产换成统一密钥签名版，使 5 个资产的证书一致。
"""
import io
import os
import sys

import requests
import urllib3

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
urllib3.disable_warnings()

REPO = "2016YYy/whale-roam"
TAG = "v1.0.0"
ASSET = "whale-roam-v1.0.0.apk"
API_IP = "20.205.243.168"
UPLOAD_IP = "20.205.243.161"


class Pinned(requests.adapters.HTTPAdapter):
    def __init__(self, host, ip, **kw):
        self.host, self.ip = host, ip
        super().__init__(**kw)

    def send(self, request, **kw):
        if request.url.startswith("https://" + self.host):
            request.url = request.url.replace("https://" + self.host, "https://" + self.ip, 1)
            request.headers["Host"] = self.host
        return super().send(request, **kw)


def main():
    root = os.path.dirname(os.path.abspath(__file__))
    new_apk = os.path.join(root, "_audit_apks", "resigned", "v100-new.apk")
    if not os.path.exists(new_apk):
        raise SystemExit("重签后的 APK 不存在: " + new_apk)

    s = requests.Session()
    s.mount("https://api.github.com", Pinned("api.github.com", API_IP))
    s.mount("https://uploads.github.com", Pinned("uploads.github.com", UPLOAD_IP))
    s.verify = False
    s.headers.update({
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "Accept": "application/vnd.github+json",
        "User-Agent": "whale-roam-release",
    })

    rel = s.get(f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", timeout=60).json()
    rel_id = rel["id"]
    print("release id =", rel_id)

    assets = s.get(f"https://api.github.com/repos/{REPO}/releases/{rel_id}/assets", timeout=60).json()
    old = next((a for a in assets if a["name"] == ASSET), None)
    if old is None:
        raise SystemExit("未找到原资产")

    print(f"[old] {old['name']} id={old['id']} size={old['size']} digest={old.get('digest')}")

    # 1. 删除旧资产
    r = s.delete(f"https://api.github.com/repos/{REPO}/releases/assets/{old['id']}", timeout=60)
    print("[del] status =", r.status_code)
    if r.status_code >= 400:
        raise SystemExit("删除失败: " + r.text[:300])

    # 2. 上传重签后的资产
    data = open(new_apk, "rb").read()
    url = (f"https://uploads.github.com/repos/{REPO}/releases/{rel_id}/assets"
           f"?name={ASSET}")
    r = s.post(url, data=data, timeout=600,
               headers={"Content-Type": "application/vnd.android.package-archive"})
    print("[up ] status =", r.status_code, "size =", len(data))
    if r.status_code >= 400:
        raise SystemExit("上传失败: " + r.text[:400])
    j = r.json()
    print("[new] digest =", j.get("digest"))
    print("[new] url    =", j["browser_download_url"])

    # 3. 校验
    check = s.get(f"https://api.github.com/repos/{REPO}/releases/{rel_id}/assets", timeout=60).json()
    for a in check:
        print(f"[chk] {a['name']} size={a['size']} state={a['state']} digest={a.get('digest')}")


if __name__ == "__main__":
    main()
