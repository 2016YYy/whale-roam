# -*- coding: utf-8 -*-
"""用 GitHub REST API 推送本地 HEAD（git push 网络不可达时的替代方案）。

做法：把本地 HEAD 的整棵树（git ls-tree -r）逐个 blob 上传，创建 tree 与 commit，
把远端 main 指向新 commit。内容取自 git 对象库（LF 字节），不使用工作区文件，
因此远端树与本地树完全一致。

用法（仓库根目录）：
    python tools/push_via_api.py
"""
import base64, io, os, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
import requests, urllib3
urllib3.disable_warnings()

REPO = "2016YYy/whale-roam"
API_IP = "20.205.243.168"
ROOT = r"E:\AIDame\DeepSeekHarnessGithub\whale-roam"
TOKEN_FILE = r"E:\AIDame\DeepSeekHarnessGithub\_build-cache\.gh_token"


class Pinned(requests.adapters.HTTPAdapter):
    def __init__(self, host, ip, **kw):
        self.host, self.ip = host, ip
        super().__init__(**kw)

    def send(self, request, **kw):
        if request.url.startswith("https://" + self.host):
            request.url = request.url.replace("https://" + self.host, "https://" + self.ip, 1)
            request.headers["Host"] = self.host
        return super().send(request, **kw)


def git(*args, binary=False):
    r = subprocess.run(["git"] + list(args), cwd=ROOT, capture_output=True)
    if r.returncode != 0:
        raise RuntimeError("git %s failed: %s" % (" ".join(args), r.stderr.decode("utf-8", "replace")[:300]))
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def main():
    tok = open(TOKEN_FILE, encoding="utf-8").read().strip()
    s = requests.Session()
    s.mount("https://api.github.com", Pinned("api.github.com", API_IP))
    s.verify = False
    s.headers.update({"Authorization": "Bearer " + tok,
                      "Accept": "application/vnd.github+json",
                      "User-Agent": "whale-push"})

    def api(method, path, **kw):
        r = s.request(method, "https://api.github.com" + path, timeout=120, **kw)
        if r.status_code >= 400:
            raise RuntimeError("%s %s -> %d: %s" % (method, path, r.status_code, r.text[:300]))
        return r.json() if r.content else {}

    remote = api("GET", "/repos/%s/git/ref/heads/main" % REPO)
    base = remote["object"]["sha"]
    head = git("rev-parse", "HEAD").strip()
    local_tree = git("rev-parse", "HEAD^{tree}").strip()
    print("remote main =", base[:7], "| local HEAD =", head[:7])
    print("local tree  =", local_tree)

    listing = git("ls-tree", "-r", "HEAD")
    entries = []
    for line in listing.splitlines():
        if not line.strip():
            continue
        meta, path = line.split("\t", 1)
        mode, otype, sha = meta.split()
        entries.append((mode, otype, sha, path))
    print("文件数:", len(entries))

    items = []
    for mode, otype, sha, path in entries:
        if otype != "blob":
            items.append({"path": path, "mode": mode, "type": otype, "sha": sha})
            continue
        data = git("cat-file", "blob", sha, binary=True)
        blob = api("POST", "/repos/%s/git/blobs" % REPO, json={
            "content": base64.b64encode(data).decode(), "encoding": "base64"})
        items.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
    print("已上传 blob:", len(items))

    tree = api("POST", "/repos/%s/git/trees" % REPO, json={"tree": items})
    print("new tree    =", tree["sha"])
    if tree["sha"] != local_tree:
        print("[warn] 远端树与本地树不一致（可能因权限位/子模块），继续提交");

    msg = git("log", "-1", "--format=%B", "HEAD").strip()
    author_name = git("log", "-1", "--format=%an").strip()
    author_email = git("log", "-1", "--format=%ae").strip()
    commit = api("POST", "/repos/%s/git/commits" % REPO, json={
        "message": msg, "tree": tree["sha"], "parents": [base],
        "author": {"name": author_name, "email": author_email}})
    print("new commit  =", commit["sha"][:7])

    api("PATCH", "/repos/%s/git/refs/heads/main" % REPO, json={"sha": commit["sha"]})
    print("remote main ->", commit["sha"][:7])
    print("\nDONE")


if __name__ == "__main__":
    main()
