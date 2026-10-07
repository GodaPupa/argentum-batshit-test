#!/usr/bin/env python3
from pathlib import Path
import hashlib, json, subprocess

ROOT=Path(__file__).resolve().parents[3]
MAN=ROOT/"docs/experiments/pest-control/PEST_MONSTER_TRON_R1_A2_PREP_LAB_20261006.json"
FIX=ROOT/"docs/experiments/pest-control/pest-control-tier-one-monster-tron-r1-official-smoke.prospective.yml.txt"
REAL=ROOT/".github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml"
V31="dc22d47efa7b67442a6120b3a2aa8802c75d5a5a"
V31_TREE="74f02cfef8a9e66230012ca6b3af57ebd87a1717"
C2_ID="421a75c0aad541d9604840b1757c790c7f313155a215e55bdc156858514e2659"
FUTURE="refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1"
HIST="refs/heads/pest-control/official-attempts/monster-tron-smoke-v1"
HIST_SHA="1c2e253ad7f5a7652304f7c9aaafc30e547a481e"

def git(*args):
    return subprocess.check_output(["git",*args],cwd=ROOT,text=True).strip()

m=json.loads(MAN.read_text())
w=FIX.read_text()
assert m["authority"]=="NON_AUTHORITY_PREPARATION_ONLY__NO_A2__NO_CLAIM__NO_GAMEPLAY"
assert m["source_review_state"]=="V3_1_INDEPENDENT_REVIEW_PENDING"
assert m["prospective_execution_source"]["commit"]==V31
assert m["prospective_execution_source"]["tree"]==V31_TREE
assert m["accepted_c2"]["identifier"]==C2_ID
assert m["r1"]["future_claim_ref"]==FUTURE
assert m["r1"]["historical_claim_ref"]==HIST
assert m["r1"]["historical_claim_commit"]==HIST_SHA
assert m["caps"]["job_seconds"]==21600
assert m["caps"]["process_seconds"]==18000
assert m["caps"]["required_remaining_before_claim_seconds"]==19500
assert m["caps"]["latest_safe_claim_elapsed_seconds"]==2100
assert not REAL.exists(), "real official R1 workflow must remain absent in prep lab"
for s in [
    "NON-EXECUTABLE LAB FIXTURE",
    "workflow_dispatch:",
    "EXECUTION_SOURCE_SHA: "+V31,
    "ENGINE_BASELINE_SHA: 433df3310efe31c49f27034b50e6d8d7e60561f7",
    "github.run_attempt == 1",
    "refs/heads/pest-control/tier1-monster-tron-r1-official-smoke",
    "scripts/pest-monster-tron-r1-one-shot-claim.py",
    "PEST_MONSTER_TRON_R1_EXECUTION_INPUT_ACK",
    "LOAD_FROZEN_MONSTER_TRON_R1_FOR_VALIDATION_ONLY",
    "AUTOMATIC_ONE_SHOT_MONSTER_TRON_R1_EXECUTION",
    "build_pest_monster_tron_r1_input.py",
    "e9ddafe0a7aec28cb07950f2f1d5908a31af677ff58415e809117575f46d6817",
    "required_seconds': 5 * 3600 + 25 * 60",
    "today.isoformat() < '2026-10-12'",
    "if: always()",
]:
    assert s in w, s
assert "contents: write" in w, "prospective execution job needs reviewed future ref-write authority"
assert w.count("workflow_dispatch:")==1
assert "--run-attempt \"$GITHUB_RUN_ATTEMPT\"" in w
assert "R1_DETERMINISTIC_GIT_FREEZE" not in w
out={
 "schema":"pest-monster-tron-r1-a2-prep-lab-verifier-v1",
 "status":"PASS_INERT_A2_PREP_ONLY",
 "head":git("rev-parse","HEAD"),
 "tree":git("rev-parse","HEAD^{tree}"),
 "base_v3_1":V31,
 "manifest_sha256":hashlib.sha256(MAN.read_bytes()).hexdigest(),
 "prospective_workflow_sha256":hashlib.sha256(FIX.read_bytes()).hexdigest(),
 "real_workflow_absent":True,
 "a2_active":False,
 "claim_created":False,
 "gameplay":False,
}
print(json.dumps(out,sort_keys=True))
