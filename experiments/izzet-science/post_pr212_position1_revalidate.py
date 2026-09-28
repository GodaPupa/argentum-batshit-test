#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, pathlib, subprocess, sys

ROOT=pathlib.Path(".")
HIST_REF="25aca91fd476358d0d5b2adfb6f8d9e986a777af"
EXPECTED={
 "izzet-science/v0.7-control.md":"297ad4fe8f52f2cbeaaa6a17356f68f9c0f1fc1f",
 "izzet-science/v09-phase31-seed-vector-freeze-accepted.md":"62c60e3d988ec55c0d29a38b90f6193ec3f36910",
 "izzet-science/v09-phase32-execution-readiness-accepted.md":"2df8c6971ff5c539717c0877510cc267db1af473",
 "izzet-science/v09-phase33-position1-execution-authorization-accepted.md":"82770b1420f9738b2ee2cbd1e345382ee5d0f5ab",
 "izzet-science/seed-registry-v1.json":"c78c5d6bb6e0a61956edf97b027663180057322e",
 "izzet-science/sim/phase32_execution_readiness.py":"b0cc4dd03d35086baecc493faa01d39dc926d854",
 "izzet-science/sim/phase33_position1_authorization.py":"066731ae6505236b569455b091a9550aa4884380",
 "izzet-science/sim/phase33_position1_execution_adapter.py":"f88bf247a23bdc43e7b7a0d44d78e31fe99a2c8d",
 "izzet-science/sim/sampled_pilot_runner.py":"7281f42470436cf86746df1d421003385d119b96",
}
CURRENT={
 "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/ability/ActivateAbilityHandler.kt":"524d7917056d09558ddde1426f1e6f3a81d00810",
 "experiments/izzet-science/evidence/pr212-shared-explicit-rider-payment-adoption-20260928.json":"ed1542653fd2170b03644130582c362c06415115",
 "experiments/izzet-science/evidence/pr212-exact-compat-36474121083-independent-adoption.json":"23bed58598fbba712e54c29389271a9310169e4d",
 "experiments/izzet-science/evidence/post-pr212-position1-runtime-bridge-proposal-20260928.json":"59078fc66cbdaa271331362b0326f4f9c9fd72dc",
 "experiments/izzet-science/evidence/post-pr212-position1-runtime-bridge-independent-review-20260928.json":"820980ea2de5ca7deaf86ba500a04df01c41a292",
}
def git(*args:str)->str:
    return subprocess.check_output(["git",*args],text=True).strip()
def blob(ref:str,path:str)->str:
    return git("rev-parse",f"{ref}:{path}")
def show(ref:str,path:str)->str:
    return subprocess.check_output(["git","show",f"{ref}:{path}"],text=True)
def main()->int:
    # Fetch exact historical authority commit only; never read quarantine artifact or raw vector bytes.
    subprocess.run(["git","fetch","--no-tags","origin",HIST_REF],check=True,stdout=subprocess.DEVNULL)
    hist=git("rev-parse","FETCH_HEAD")
    assert hist==HIST_REF
    for p,h in EXPECTED.items():
        assert blob(hist,p)==h,(p,blob(hist,p),h)
    for p,h in CURRENT.items():
        assert git("hash-object",p)==h,(p,git("hash-object",p),h)

    p31=show(hist,"izzet-science/v09-phase31-seed-vector-freeze-accepted.md")
    p32=show(hist,"izzet-science/v09-phase32-execution-readiness-accepted.md")
    p33=show(hist,"izzet-science/v09-phase33-position1-execution-authorization-accepted.md")
    assert "5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f" in p31
    assert "b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3" in p31
    assert "experimental seeds consumed: 0" in p31
    assert "games initialized: 0" in p31
    assert "experimental seeds consumed: 0" in p32
    assert "games initialized: 0" in p32
    assert "position 1 authorized only" in p33
    assert "positions 2–12 explicitly rejected" in p33
    assert "experimental seeds consumed: 0" in p33
    assert "games initialized: 0" in p33

    adoption=json.loads((ROOT/"experiments/izzet-science/evidence/pr212-shared-explicit-rider-payment-adoption-20260928.json").read_text())
    compat=json.loads((ROOT/"experiments/izzet-science/evidence/pr212-exact-compat-36474121083-independent-adoption.json").read_text())
    review=json.loads((ROOT/"experiments/izzet-science/evidence/post-pr212-position1-runtime-bridge-independent-review-20260928.json").read_text())
    assert adoption["decision"]=="ACCEPT_SHARED_HANDLER_REPAIR_EXACT_BLOB_ONLY"
    assert compat["scope"]["total"]["cases"]==52 and compat["scope"]["total"]["failures"]==0
    assert review["decision"]=="ACCEPT_ZERO_SEED_DETERMINISTIC_REVALIDATION_PATH_ONLY"
    out={
      "schema":"izzet-post-pr212-position1-zero-seed-revalidation-result-v1",
      "historical_authority_commit":hist,
      "verified_historical_blobs":EXPECTED,
      "verified_current_blobs":CURRENT,
      "position1_only":True,
      "positions_2_12_authorized":False,
      "seed_values_read":False,
      "seed_values_printed":False,
      "seeds_consumed":0,
      "games_initialized":0,
      "outcomes_exposed":0,
      "result":"PASS_REQUIRES_INDEPENDENT_RESULT_ADOPTION",
    }
    pathlib.Path("build/reports/izzet-position1-revalidation").mkdir(parents=True,exist_ok=True)
    pathlib.Path("build/reports/izzet-position1-revalidation/result.json").write_text(json.dumps(out,indent=2)+"\n")
    print("IZZET_POST_PR212_POSITION1_ZERO_SEED_REVALIDATION_PASS")
    return 0
if __name__=="__main__":
    raise SystemExit(main())
