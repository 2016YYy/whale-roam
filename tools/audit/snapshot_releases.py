import io, json, os, sys, urllib3, requests
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
urllib3.disable_warnings()
class Pinned(requests.adapters.HTTPAdapter):
    def __init__(self, host, ip, **kw):
        self.host, self.ip = host, ip
        super().__init__(**kw)
    def send(self, request, **kw):
        if request.url.startswith("https://" + self.host):
            request.url = request.url.replace("https://" + self.host, "https://" + self.ip, 1)
            request.headers["Host"] = self.host
        return super().send(request, **kw)

s = requests.Session()
s.mount("https://api.github.com", Pinned("api.github.com", "20.205.243.168"))
s.verify = False
s.headers.update({"Authorization": "Bearer " + os.environ["GH_TOKEN"], "User-Agent": "dsh"})
rels = s.get("https://api.github.com/repos/2016YYy/whale-roam/releases?per_page=100", timeout=60).json()

bak_dir = r"E:\AIDame\DeepSeekHarnessGithub\_backup\2026-10-17-191500"
os.makedirs(bak_dir, exist_ok=True)
snapshot = []
for rel in sorted(rels, key=lambda r: r["tag_name"]):
    snapshot.append({
        "id": rel["id"], "tag_name": rel["tag_name"], "name": rel["name"],
        "body": rel["body"], "draft": rel["draft"], "prerelease": rel["prerelease"],
        "target_commitish": rel.get("target_commitish"),
        "assets": [{"name": a["name"], "size": a["size"], "digest": a.get("digest"), "id": a["id"]} for a in rel["assets"]],
    })
p = os.path.join(bak_dir, "releases-snapshot.json")
open(p, "w", encoding="utf-8").write(json.dumps(snapshot, ensure_ascii=False, indent=2))
print("saved:", p, "| releases:", len(snapshot))
for r in snapshot:
    print(" ", r["tag_name"], "| body len", len(r["body"]), "| assets", [a["name"] for a in r["assets"]])
