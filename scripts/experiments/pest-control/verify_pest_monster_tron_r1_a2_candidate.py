#!/usr/bin/env python3
from pathlib import Path
import hashlib, json, subprocess
ROOT=Path(__file__).resolve().parents[3]
V31="dc22d47efa7b67442a6120b3a2aa8802c75d5a5a"; V31_TREE="74f02cfef8a9e66230012ca6b3af57ebd87a1717"; BRANCH="pest/monster-tron-r1-a2-candidate-20261006"
REAL=ROOT/".github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml"; MAN=ROOT/"docs/experiments/pest-control/PEST_MONSTER_TRON_R1_A2_CANDIDATE_20261006.json"; GUARD=ROOT/"gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronRunnerSurfacePreflight.kt"; TEST=ROOT/"gym/src/test/kotlin/com/wingedsheep/gym/PestControlTierOneMonsterTronRunnerSurfacePreflightTest.kt"; QUAL=ROOT/".github/workflows/pest-monster-tron-r1-a2-candidate-qualify-20261006.yml"
FUTURE="refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1"; HIST="refs/heads/pest-control/official-attempts/monster-tron-smoke-v1"; HIST_SHA="1c2e253ad7f5a7652304f7c9aaafc30e547a481e"
def git(*args): return subprocess.check_output(["git",*args],cwd=ROOT,text=True).strip()
m=json.loads(MAN.read_text()); w=REAL.read_text(); g=GUARD.read_text(); t=TEST.read_text(); q=QUAL.read_text()
assert git("merge-base",V31,"HEAD")==V31
assert git("rev-parse",V31+"^{tree}")==V31_TREE
assert int(git("rev-list","--count",V31+"..HEAD"))==m["construction_commits_from_v3_1"]
changed=set(git("diff","--name-only",V31,"HEAD").splitlines()); assert changed==set(m["changed_paths"]),(changed,m["changed_paths"])
assert m["authority"]=="CANDIDATE_ONLY__A2_INACTIVE__NO_CLAIM__NO_GAMEPLAY" and m["branch"]==BRANCH
assert m["accepted_v3_1"]["commit"]==V31 and m["accepted_v3_1"]["tree"]==V31_TREE
assert m["accepted_v3_1"]["canonical_review_comment"]==6031436135 and m["accepted_v3_1"]["canonical_review_sha256"]=="e114679f1881c1f9208498f153bae81a8290b51f1c5818909c7d84ff9375ffb5"
assert m["accepted_c2"]["identifier"]=="421a75c0aad541d9604840b1757c790c7f313155a215e55bdc156858514e2659"
assert hashlib.sha256(REAL.read_bytes()).hexdigest()==m["official_workflow"]["sha256"]=="80bf21c3c0577bd97384f64a250cfc3911e610e52cd91c3882e3d40c03197ffc"
for token in ["workflow_dispatch:","EXECUTION_SOURCE_SHA: "+V31,"github.run_attempt == 1","refs/heads/pest-control/tier1-monster-tron-r1-official-smoke","scripts/pest-monster-tron-r1-one-shot-claim.py","--kill-after=120s 5h","if: always()","2026-10-12"]: assert token in w,token
for token in ['CONSTRUCTION_WORKFLOW_SHA256 = "412a771569f7a3bad4e4e41a037953140b39fa8a5d3c71870dc21776fd01cccb"','OFFICIAL_WORKFLOW_SHA256 = "6f37cdb939ab310655f78d82ffd711c9f0f389b51e8bd6f6f1a84eb732b9fa3f"','R1_OFFICIAL_WORKFLOW_PATH = ".github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml"','R1_OFFICIAL_WORKFLOW_SHA256 = "80bf21c3c0577bd97384f64a250cfc3911e610e52cd91c3882e3d40c03197ffc"', 'V3_REBIND_QUALIFICATION_WORKFLOW_PATH = ".github/workflows/pest-monster-tron-r1-execution-source-rebind-v3-qualify-20261006.yml"', 'V3_REBIND_QUALIFICATION_WORKFLOW_SHA256 = "7822f712f4df32ef34540b6a38e8ba5bf2cbdbe092da78e276924a510107d109"', 'V3_1_REBIND_QUALIFICATION_WORKFLOW_PATH = ".github/workflows/pest-monster-tron-r1-execution-source-rebind-v3-1-qualify-20261006.yml"', 'V3_1_REBIND_QUALIFICATION_WORKFLOW_SHA256 = "15e58ebc7fc63b536134307be087869161e8e2afcede79bef7a72e57e7ffefe3"']: assert token in g,token
for token in ["R1 official workflow accepts only exact frozen bytes source branch and first attempt","historical V3 and V3.1 qualification workflows remain exact pinned and fail closed","invented-historical-qualify.yml","invented-r1.yml","github.run_attempt == 1","if: success()"]: assert token in t,token
assert "workflow_dispatch:" not in q and "contents: write" not in q and "pest-monster-tron-r1-one-shot-claim.py" not in q
reused=m["reused_evidence_by_exact_blob"]
for path,key in [("scripts/pest-monster-tron-r1-one-shot-claim.py","claim_helper"),("tools/tests/test_pest_monster_tron_r1_one_shot_claim.py","claim_tests"),("gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronR1InputLoader.kt","r1_input_loader"),("gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronR1ExecutionIdentity.kt","r1_execution_identity"),("gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOperationalStack.kt","operational_stack")]: assert git("rev-parse","HEAD:"+path)==reused[key],(path,git("rev-parse","HEAD:"+path),reused[key])
print(json.dumps({"status":"PASS_A2_CANDIDATE_STRUCTURE_ONLY","head":git("rev-parse","HEAD"),"tree":git("rev-parse","HEAD^{tree}"),"base":V31,"changed_paths":sorted(changed),"workflow_sha256":"80bf21c3c0577bd97384f64a250cfc3911e610e52cd91c3882e3d40c03197ffc","future_claim_ref":FUTURE,"historical_claim_ref":HIST,"historical_claim_commit":HIST_SHA,"a2_active":False,"claims":0,"games":0,"actions":0,"outcomes":0},sort_keys=True))
