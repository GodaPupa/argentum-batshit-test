#!/usr/bin/env python3
import json,pathlib,subprocess,sys
PARENT="5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"
DELTA=sorted([
 ".github/workflows/pest-c2-p07-fresh-replay-qualification-20261005.yml",
 "docs/experiments/pest-control/PEST_C2_P07_FRESH_REPLAY_QUALIFICATION_20261005.json",
 "gym/src/main/kotlin/com/wingedsheep/gym/pest/PestCurrentPairC2ReplayJournal.kt",
 "gym/src/test/kotlin/com/wingedsheep/gym/pest/PestCurrentPairC2ReplayJournalTest.kt",
 "scripts/experiments/pest-control/verify_pest_c2_p07_fresh_replay_qualification.py",
])
def git(*a): return subprocess.check_output(["git",*a],text=True).strip()
def main():
 if len(sys.argv)!=2: raise SystemExit("manifest required")
 m=json.loads(pathlib.Path(sys.argv[1]).read_text())
 assert m["schema"]=="pest-current-pair-c2-p07-fresh-replay-qualification-v1"
 assert m["authority"]=="NON_OFFICIAL_SOURCE_REPLAY_QUALIFICATION_ONLY_NO_C2_ADMISSION"
 assert m["source_parent"]["commit"]==PARENT
 assert git("rev-parse","HEAD^")==PARENT
 assert sorted(git("diff","--name-only",PARENT+"..HEAD").splitlines())==DELTA
 e=m["accepted_predecessor_evidence"]
 assert e["source_inventory_run"]==37312150290
 assert e["source_inventory_artifact"]==11346043451
 assert e["source_inventory_artifact_digest"]=="sha256:c4d65d67ab5a0cf6c3e586e862fddba21fae7ba76f7c3b02081489a2b6dbc107"
 inv=m["finite_replay_inventory"]
 assert len(inv)>=7 and len({x["id"] for x in inv})==len(inv)
 assert all(x["status"]=="QUALIFICATION_BOUND" for x in inv)
 required={"package_binding","intent_before_result","duplicate_rejection","no_clobber","incomplete_prefix","deterministic_replay","terminal_consistency"}
 assert required.issubset({x["kind"] for x in inv})
 for p,b in m["protected_blobs"].items():
  assert git("rev-parse","HEAD:"+p)==b,(p,git("rev-parse","HEAD:"+p),b)
 assert m["acceptance_if_validation_success"]["C2P07"]=="SATISFIED_CURRENT_PAIR_SOURCE_BOUND_FRESH_REPLAY"
 assert m["official_counters"]=={"allocations":0,"claims":0,"games":0,"outcomes":0}
 print(json.dumps({"schema":"pest-c2-p07-fresh-replay-verifier-v1","status":"PASS",
   "head":git("rev-parse","HEAD"),"tree":git("rev-parse","HEAD^{tree}"),
   "replay_surfaces":len(inv),"official_counters_delta":0},sort_keys=True))
if __name__=="__main__": main()
