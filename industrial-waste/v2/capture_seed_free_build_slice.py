#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,pathlib,platform,subprocess

ROOT=pathlib.Path(".")
OUT=ROOT/"build/reports/industrial-seed-free-build-capture"

def sha(p: pathlib.Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()

def cmd(*args):
    return subprocess.check_output(list(args),text=True,stderr=subprocess.STDOUT).strip()

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    roots=sorted(p for p in ROOT.glob("**/build/classes/kotlin/main") if p.is_dir() and ".gradle" not in p.parts)
    if not roots:
        raise SystemExit("no compiled Kotlin main roots found")
    inventory=[]
    seen=set()
    for root in roots:
        relroot=root.relative_to(ROOT).as_posix()
        for p in sorted(x for x in root.rglob("*") if x.is_file()):
            rel=p.relative_to(ROOT).as_posix()
            key=(relroot,p.relative_to(root).as_posix())
            if key in seen: raise AssertionError("duplicate inventory key "+str(key))
            seen.add(key)
            inventory.append({"root":relroot,"path":p.relative_to(root).as_posix(),"length":p.stat().st_size,"sha256":sha(p)})
    result={
      "schema":"industrial-r1-seed-free-build-output-capture-v1",
      "source_commit":cmd("git","rev-parse","HEAD"),
      "source_tree":cmd("git","rev-parse","HEAD^{tree}"),
      "roots":[r.relative_to(ROOT).as_posix() for r in roots],
      "file_count":len(inventory),
      "files":inventory,
      "toolchain":{
        "java":cmd("java","-version"),
        "python":cmd("python3","--version"),
        "bash":cmd("bash","--version").splitlines()[0],
        "just":cmd("just","--version"),
        "platform":platform.platform(),
      },
      "official_seed_files_read":False,
      "official_counters":{"allocations":0,"games":0,"outcomes":0},
      "authority":"CANDIDATE_BUILD_OUTPUT_SLICE_ONLY",
    }
    (OUT/"capture.json").write_text(json.dumps(result,sort_keys=True,indent=2)+"\n")
    print(json.dumps({"roots":len(roots),"files":len(inventory),"authority":result["authority"]},indent=2))
if __name__=="__main__": main()
