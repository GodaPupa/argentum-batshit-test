#!/usr/bin/env python3
import json,pathlib,subprocess,sys
PARENT="5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"
DELTA=sorted([
 ".github/workflows/pest-c2-p06-whole-pair-invariance-20261005.yml",
 "docs/experiments/pest-control/PEST_C2_P06_WHOLE_PAIR_INVARIANCE_20261005.json",
 "gym/src/test/kotlin/com/wingedsheep/gym/actorinput/PestCurrentPairC2P06HiddenInvarianceTest.kt",
 "scripts/experiments/pest-control/verify_pest_c2_p06_whole_pair_invariance.py",
])
def git(*a): return subprocess.check_output(["git",*a],text=True).strip()
def main():
 if len(sys.argv)!=2: raise SystemExit("manifest required")
 m=json.loads(pathlib.Path(sys.argv[1]).read_text())
 assert m["schema"]=="pest-current-pair-c2-p06-whole-pair-invariance-v1"
 assert m["authority"]=="NON_GAMEPLAY_INVARIANCE_QUALIFICATION_ONLY_NO_C2_ADMISSION"
 assert m["source_parent"]["commit"]==PARENT
 assert git("rev-parse","HEAD^")==PARENT
 assert sorted(git("diff","--name-only",PARENT+"..HEAD").splitlines())==DELTA
 e=m["accepted_predecessor_evidence"]
 assert e["source_inventory_run"]==37311687079
 assert e["source_inventory_artifact"]==11346790841
 inv=m["finite_permutation_inventory"]
 assert len(inv)>=8 and len({x["id"] for x in inv})==len(inv)
 assert all(x["status"]=="QUALIFICATION_BOUND" for x in inv)
 required={"opponent_hidden_hand_identity","opponent_hidden_library_order","own_hidden_library_order",
  "observation_projection","authorized_look","revealed_identity","randomness_boundary","pair_policy_output"}
 assert required.issubset({x["kind"] for x in inv})
 for p,b in m["protected_blobs"].items():
  assert git("rev-parse","HEAD:"+p)==b,(p,git("rev-parse","HEAD:"+p),b)
 assert m["acceptance_if_validation_success"]["C2P06"]=="SATISFIED_CURRENT_WHOLE_PAIR_HIDDEN_STATE_INVARIANCE"
 assert m["official_counters"]=={"allocations":0,"claims":0,"games":0,"outcomes":0}
 print(json.dumps({"schema":"pest-c2-p06-whole-pair-invariance-verifier-v1","status":"PASS",
   "head":git("rev-parse","HEAD"),"tree":git("rev-parse","HEAD^{tree}"),
   "permutation_surfaces":len(inv),"official_counters_delta":0},sort_keys=True))
if __name__=="__main__": main()
