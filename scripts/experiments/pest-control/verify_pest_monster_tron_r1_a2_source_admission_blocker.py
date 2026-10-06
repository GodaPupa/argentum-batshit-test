#!/usr/bin/env python3
import json
import pathlib
import subprocess
import sys

C2 = "38e834c1275a861e87487195842fb4d98e373ee7"
C2_TREE = "72f5369af53ecb5430548ce43288f5453fcd2d9c"
EXPECTED_DELTA = sorted([
    ".github/workflows/pest-monster-tron-r1-a2-source-admission-blocker-validate-20261006.yml",
    "docs/experiments/pest-control/PEST_MONSTER_TRON_R1_A2_SOURCE_ADMISSION_BLOCKER_20261006.json",
    "scripts/experiments/pest-control/verify_pest_monster_tron_r1_a2_source_admission_blocker.py",
])

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def main():
    if len(sys.argv) != 2:
        raise SystemExit("manifest required")
    path = pathlib.Path(sys.argv[1])
    m = json.loads(path.read_text())
    assert m["schema"] == "pest-monster-tron-r1-a2-source-admission-blocker-v1"
    assert m["authority"] == "A2_SOURCE_ADMISSION_BLOCKER_EVIDENCE_ONLY__NO_SOURCE_REWRITE__NO_A2__NO_CLAIM__NO_GAMEPLAY"
    assert git("rev-parse", C2 + "^{tree}") == C2_TREE
    assert git("merge-base", C2, "HEAD") == C2
    assert sorted(git("diff", "--name-only", C2 + "..HEAD").splitlines()) == EXPECTED_DELTA
    c2 = m["accepted_c2"]
    assert c2["commit"] == C2 and c2["tree"] == C2_TREE
    assert c2["identifier_sha256"] == "421a75c0aad541d9604840b1757c790c7f313155a215e55bdc156858514e2659"
    assert m["c2_review"]["receipt_comment"] == 6016599830
    assert m["c2_review"]["canonical_sha256"] == "f43347936391ceb3c6176eba5856a1c118d54ee16c5d81a0c46987ebc87e3a8d"

    s = m["historical_execution_surface"]
    for p, b in s["blobs"].items():
        actual = git("rev-parse", C2 + ":" + p)
        assert actual == b, (p, actual, b)

    claim = git("show", C2 + ":scripts/pest-monster-tron-one-shot-claim.py")
    assert "NONEXPERIMENTAL_SMOKE_4'" in claim
    assert "refs/heads/pest-control/official-attempts/monster-tron-smoke-v1" in claim
    assert "1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31" in claim

    boundary = git("show", C2 + ":gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOneShotBoundary.kt")
    assert 'MONSTER_TRON_CLAIM_REF = "refs/heads/pest-control/official-attempts/monster-tron-smoke-v1"' in boundary
    assert "PEST_MONSTER_TRON_FROZEN_SMOKE_" in boundary

    stack = git("show", C2 + ":gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOperationalStack.kt")
    assert "PEST_MONSTER_TRON_FROZEN_SMOKE_" in stack
    assert "PEST_MONSTER_TRON_SMOKE_BLOCK_ID" in stack
    assert "maxActions: Int = 12_000" in stack
    assert "maxTurns: Int = 60" in stack
    assert "maxActionsPerTurn: Int = 500" in stack

    for p, b in m["validation_package_blobs"].items():
        assert git("rev-parse", "HEAD:" + p) == b, (p, git("rev-parse", "HEAD:" + p), b)

    assert m["future_r1_claim_ref"]["state"] == "ABSENT_REQUIRED"
    assert m["historical_claim"]["commit"] == "1c2e253ad7f5a7652304f7c9aaafc30e547a481e"
    assert m["runner"]["state"] == "DISABLED"
    assert m["a2"]["state"] == "INACTIVE"
    assert m["counters"] == {"frozen_seeds": 4, "claims": 0, "games": 0, "actions": 0, "outcomes": 0}

    wf = pathlib.Path(".github/workflows/pest-monster-tron-r1-a2-source-admission-blocker-validate-20261006.yml").read_text()
    for forbidden in ("workflow_dispatch:", "contents: write", "os.urandom(", "PEST_MONSTER_TRON_OFFICIAL_MODE", "GameInitializer"):
        assert forbidden not in wf, forbidden

    print(json.dumps({
        "schema": "pest-monster-tron-r1-a2-source-admission-blocker-verifier-v1",
        "status": "PASS_BLOCKER_EVIDENCE_ONLY",
        "head": git("rev-parse", "HEAD"),
        "tree": git("rev-parse", "HEAD^{tree}"),
        "base_c2": C2,
        "source_rebind_required": True,
        "runner_state": "DISABLED",
        "claims": 0,
        "games": 0,
        "actions": 0,
        "outcomes": 0
    }, sort_keys=True))

if __name__ == "__main__":
    main()
