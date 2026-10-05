#!/usr/bin/env python3
import json, pathlib, subprocess, sys
PARENT="5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"
DELTA=sorted([
 ".github/workflows/pest-c2-p05-whole-pair-closure-20261005.yml",
 "ai/src/test/kotlin/com/wingedsheep/ai/engine/PestCurrentPairC2P05PolicyCombatTest.kt",
 "docs/experiments/pest-control/PEST_C2_P05_WHOLE_PAIR_CLOSURE_20261005.json",
 "scripts/experiments/pest-control/verify_pest_c2_p05_whole_pair_closure.py",
])
def git(*a): return subprocess.check_output(["git",*a],text=True).strip()
def main():
 if len(sys.argv)!=2: raise SystemExit("manifest required")
 m=json.loads(pathlib.Path(sys.argv[1]).read_text())
 assert m["schema"]=="pest-current-pair-c2-p05-whole-pair-closure-v1"
 assert m["authority"]=="SOURCE_POLICY_QUALIFICATION_ONLY_NO_C2_ADMISSION_NO_GAMEPLAY"
 assert m["source_parent"]["commit"]==PARENT
 assert git("rev-parse","HEAD^")==PARENT
 assert sorted(git("diff","--name-only",PARENT+"..HEAD").splitlines())==DELTA
 e=m["accepted_predecessor_evidence"]
 assert e["current_combat_run"]==37311504539
 assert e["current_combat_artifact"]==11346153011
 assert e["current_combat_artifact_digest"]=="sha256:67f5c0e69f807f48b3f2afad2aca8adefbd3d6f62fa6a344968d157eafbc9047"
 assert e["current_combat_cases"]==32
 inv=m["finite_whole_pair_inventory"]
 assert len(inv)>=9 and len({x["id"] for x in inv})==len(inv)
 assert all(x["status"]=="QUALIFICATION_BOUND" for x in inv)
 assert {x["kind"] for x in inv}>={"attack","block","multiblock","trample","evasion","postblock_priority","postblock_response","combat_trigger"}
 for p,b in m["protected_blobs"].items():
  assert git("rev-parse","HEAD:"+p)==b,(p,git("rev-parse","HEAD:"+p),b)
 assert m["acceptance_if_validation_success"]["C2P05"]=="SATISFIED_CURRENT_PAIR_COMBAT_POSTBLOCK_SURFACE"
 assert m["official_counters"]=={"allocations":0,"claims":0,"games":0,"outcomes":0}
 print(json.dumps({"schema":"pest-c2-p05-whole-pair-closure-verifier-v1","status":"PASS",
  "head":git("rev-parse","HEAD"),"tree":git("rev-parse","HEAD^{tree}"),
  "finite_surfaces":len(inv),"official_counters_delta":0},sort_keys=True))
if __name__=="__main__": main()
