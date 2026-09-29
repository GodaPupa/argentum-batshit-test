#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, pathlib, subprocess

ROOT=pathlib.Path(".").resolve()
OUT=ROOT/"build/reports/ferocity-current-resource-binding"
TARGETS=[
 "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityDevelopmentAdmission.kt",
 "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityDevelopmentCli.kt",
 "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityFreshReplay.kt",
 "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityResourceLimits.kt",
 "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityReplayFileGuard.kt",
 "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityResourceBoundaryTest.kt",
 "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityJournalResourceProbe.kt",
]
def sha(p:pathlib.Path)->str:
 h=hashlib.sha256()
 with p.open("rb") as f:
  for chunk in iter(lambda:f.read(1<<20),b""): h.update(chunk)
 return h.hexdigest()
def main():
 OUT.mkdir(parents=True,exist_ok=True)
 rows=[]
 for name in TARGETS:
  p=ROOT/name
  if not p.is_file(): raise SystemExit(f"missing current source08 file: {name}")
  rows.append({
   "path":name,
   "bytes":p.stat().st_size,
   "sha256":sha(p),
   "git_blob":subprocess.check_output(["git","hash-object",name],text=True).strip()
  })
 result={
  "schema":"ferocity-current-source08-resource-binding-v1",
  "source_parent":"3a4f99a7653839506e96d19e6639f58d9e8c5ced",
  "source_parent_tree":"56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6",
  "files":rows,
  "seed_or_entropy_files_read":False,
  "jvm_started":False,
  "game_initialized":False,
  "official_counters_delta":0,
  "authority":"CURRENT_SOURCE08_BYTE_IDENTITY_DIAGNOSTIC_ONLY"
 }
 (OUT/"capture.json").write_text(json.dumps(result,sort_keys=True,indent=2)+"\n")
 print(json.dumps({"files":len(rows),"seed_or_entropy_files_read":False,"jvm_started":False,"game_initialized":False},indent=2))
if __name__=="__main__": main()
