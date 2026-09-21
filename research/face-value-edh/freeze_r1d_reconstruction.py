#!/usr/bin/env python3
import hashlib, json, pathlib, re
ROOT=pathlib.Path(__file__).resolve().parent
p=ROOT/"recovered-v0.11-user-list.txt"
raw=p.read_text(encoding="utf-8")
section=None; entries=[]
for line in raw.splitlines():
    s=line.strip()
    if not s: continue
    if s in {"Commander","Creatures","Noncreature spells","Lands"}:
        section=s; continue
    m=re.fullmatch(r"(\d+) (.+)",s)
    if not m: raise SystemExit(f"unparsed: {s}")
    entries.append({"section":section,"count":int(m.group(1)),"name":m.group(2)})
expanded=[]
for e in entries:
    expanded += [{"section":e["section"],"name":e["name"]} for _ in range(e["count"])]
if len(expanded)!=100: raise SystemExit("not 100")
canonical=json.dumps(expanded,ensure_ascii=False,sort_keys=True,separators=(",",":"))+"\n"
h=hashlib.sha256(canonical.encode()).hexdigest()
(ROOT/"r1d-reconstructed-v0.11.canonical.json").write_text(canonical,encoding="utf-8")
manifest={
 "status":"RECONSTRUCTED_CARD_IDENTITY_NOT_HISTORICAL_BYTE_EQUIVALENCE",
 "historical_sha256":"1548264c2199d2f5c376022d63200ed80a325fa0058dafaa4bfb4b4eb3d9b026",
 "reconstruction_sha256":h,
 "cards":100,
 "composition":{"commander":1,"creatures":51,"noncreatures":15,"lands":33},
 "source":"user-recovered exact list",
 "official_execution_authorized":False
}
(ROOT/"r1d-reconstruction-manifest.json").write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n",encoding="utf-8")
print(json.dumps(manifest,sort_keys=True))
