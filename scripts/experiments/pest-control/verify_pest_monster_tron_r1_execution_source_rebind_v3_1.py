#!/usr/bin/env python3
import json
import pathlib
import subprocess

V3 = "7b4767f76c0a7190f0402124f4616a1354958c94"
V3_TREE = "fa3102e5c431a1b9f2004fea968b944a7a22b4d3"
MANIFEST = pathlib.Path("docs/experiments/pest-control/PEST_MONSTER_TRON_R1_EXECUTION_SOURCE_REBIND_V3_1_20261006.json")
BOUNDARY = "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOneShotBoundary.kt"
REGRESSION = "gym/src/test/kotlin/com/wingedsheep/gym/PestControlTierOneMonsterTronR1RebindTest.kt"

UNCHANGED = {
    "scripts/pest-monster-tron-r1-one-shot-claim.py": "6ff7fcfa39c0f349d4a8bf7f189188be3ea29724",
    "tools/tests/test_pest_monster_tron_r1_one-shot-claim.py": None,
    "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronR1InputLoader.kt": "68193c024c75d632f7116c348c9a1f50c19187e1",
    "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronR1ExecutionIdentity.kt": "66330e1b7d14c98ccb3b317507e4c752f10cdc52",
    "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOperationalStack.kt": "b2e5809597cb620662ea31fdae74b0f4222e5733",
}
# Correct exact test path is assigned separately to avoid accidental typo reuse.
UNCHANGED["tools/tests/test_pest_monster_tron_r1_one_shot_claim.py"] = "3f431f8e6819cfe27ce2082562886e0fd0d371f1"
UNCHANGED.pop("tools/tests/test_pest_monster_tron_r1_one-shot-claim.py")

WRAPPERS = [
    "PestControlTierOneMonsterTronOneShotBoundary.kt",
    "PestControlTierOneMonsterTronRunnerSurfacePreflight.kt",
    "PestControlTierOneMonsterTronOperationalStack.kt",
    "PestControlTierOneMonsterTronR1ExecutionIdentity.kt",
    "PestControlTierOneMonsterTronR1InputLoader.kt",
]

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def main():
    m = json.loads(MANIFEST.read_text())
    assert m["schema"] == "pest-monster-tron-r1-execution-source-rebind-v3-1-v1"
    assert m["authority"] == "EXACT_R1_EXECUTION_SOURCE_REBIND_V3_1_REPAIR_CONSTRUCTION_QUALIFICATION_ONLY__NO_A2__NO_CLAIM__NO_GAMEPLAY"
    assert git("merge-base", V3, "HEAD") == V3
    assert git("rev-parse", V3 + "^{tree}") == V3_TREE

    got = sorted(x for x in git("diff", "--name-only", V3 + "..HEAD").splitlines() if x)
    expected = sorted(m["expected_delta_paths_from_v3"])
    assert got == expected, {"got": got, "expected": expected}

    for path, blob in UNCHANGED.items():
        assert git("rev-parse", "HEAD:" + path) == blob, (path, git("rev-parse", "HEAD:" + path), blob)

    boundary = pathlib.Path(BOUNDARY).read_text()
    for name in WRAPPERS:
        token = ":(top,exclude)gym/src/main/kotlin/com/wingedsheep/gym/matchup/" + name
        assert token in boundary, token
    assert '":(top)gym/src/main"' in boundary
    assert boundary.count(":(top,exclude)gym/src/main/kotlin/com/wingedsheep/gym/matchup/") == 5

    regression = pathlib.Path(REGRESSION).read_text()
    assert "baseline guard admits reviewed R1 sealing wrappers but still rejects protected source drift" in regression
    assert "unreviewed protected delta" in regression
    assert 'failure.commandLabel shouldBe "ENGINE_BASELINE"' in regression

    official = ".github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml"
    absent = subprocess.run(["git", "cat-file", "-e", "HEAD:" + official],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0
    assert absent, "official R1 workflow must remain absent before A2 candidate"

    assert m["fail_closed_state"] == {
        "historical_claim_ref": "refs/heads/pest-control/official-attempts/monster-tron-smoke-v1",
        "historical_claim_commit": "1c2e253ad7f5a7652304f7c9aaafc30e547a481e",
        "future_r1_claim_ref": "refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1",
        "future_r1_claim_state": "ABSENT_REQUIRED",
        "a2": "INACTIVE",
        "runner": "DISABLED",
        "frozen_seeds": 4,
        "claims": 0,
        "games": 0,
        "actions": 0,
        "outcomes": 0,
    }

    print(json.dumps({
        "schema": "pest-monster-tron-r1-execution-source-rebind-v3-1-verifier-v1",
        "status": "PASS_V3_1_BASELINE_GUARD_REPAIR_STRUCTURE",
        "head": git("rev-parse", "HEAD"),
        "tree": git("rev-parse", "HEAD^{tree}"),
        "accepted_v3": V3,
        "delta_paths": len(got),
        "reused_claim_helper_blob": UNCHANGED["scripts/pest-monster-tron-r1-one-shot-claim.py"],
        "reused_loader_blob": UNCHANGED["gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronR1InputLoader.kt"],
        "future_claim_ref": "ABSENT_REQUIRED",
        "a2": "INACTIVE",
        "runner": "DISABLED",
        "claims": 0,
        "games": 0,
        "actions": 0,
        "outcomes": 0,
    }, sort_keys=True))

if __name__ == "__main__":
    main()
