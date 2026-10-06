#!/usr/bin/env python3
import hashlib
import json
import pathlib
import subprocess

C2 = "38e834c1275a861e87487195842fb4d98e373ee7"
C2_TREE = "72f5369af53ecb5430548ce43288f5453fcd2d9c"
SMOKE_HARNESS = "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronSmokeHarness.kt"
SMOKE_HARNESS_BLOB = "305097a2f3e78de60345af4e33a32ef39a8099f5"
BOUNDARY = "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOneShotBoundary.kt"
STACK = "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOperationalStack.kt"
IDENTITY = "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronR1ExecutionIdentity.kt"
LOADER = "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronR1InputLoader.kt"
CLAIM = "scripts/pest-monster-tron-r1-one-shot-claim.py"
OFFICIAL_WORKFLOW = ".github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml"
QUALIFY_WORKFLOW = ".github/workflows/pest-monster-tron-r1-execution-source-rebind-v3-qualify-20261006.yml"
MANIFEST = pathlib.Path("docs/experiments/pest-control/PEST_MONSTER_TRON_R1_EXECUTION_SOURCE_REBIND_V3_20261006.json")

def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()

def read(path: str) -> str:
    return pathlib.Path(path).read_text()

def absent_in_head(path: str) -> bool:
    p = subprocess.run(["git", "cat-file", "-e", f"HEAD:{path}"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return p.returncode != 0

def main() -> None:
    m = json.loads(MANIFEST.read_text())
    assert m["schema"] == "pest-monster-tron-r1-execution-source-rebind-v3-v1"
    assert m["authority"] == "EXACT_R1_EXECUTION_SOURCE_IDENTITY_REBIND_CONSTRUCTION_AND_QUALIFICATION_ONLY__NO_A2__NO_CLAIM__NO_GAMEPLAY"
    assert git("merge-base", C2, "HEAD") == C2
    subprocess.check_call(["git", "merge-base", "--is-ancestor", C2, "HEAD"])
    assert git("rev-parse", C2 + "^{tree}") == C2_TREE
    assert git("rev-parse", "HEAD:" + SMOKE_HARNESS) == SMOKE_HARNESS_BLOB

    got = sorted(x for x in git("diff", "--name-only", C2 + "..HEAD").splitlines() if x)
    expected = sorted(m["expected_delta_paths"])
    assert got == expected, {"unexpected_delta": sorted(set(got) - set(expected)), "missing_delta": sorted(set(expected) - set(got))}

    smoke = read(SMOKE_HARNESS)
    assert "NONEXPERIMENTAL_SMOKE_4" in smoke
    assert "NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1" not in smoke

    boundary = read(BOUNDARY)
    assert "PEST_MONSTER_TRON_R1_BLOCK_ID" in boundary
    assert "PEST_MONSTER_TRON_SMOKE_BLOCK_ID" not in boundary
    assert "PestControlTierOneMonsterTronR1InputLoader" in boundary
    assert "scripts/pest-monster-tron-r1-one-shot-claim.py" in boundary
    assert "refs/heads/pest-control/official-attempts/monster-tron-smoke-v1" not in boundary

    stack = read(STACK)
    for token in (
        "PEST_MONSTER_TRON_R1_VECTOR_SHA256",
        "PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256",
        "PEST_MONSTER_TRON_R1_FREEZE_COMMIT",
        "PEST_MONSTER_TRON_R1_BLOCK_ID",
    ):
        assert token in stack, token
    assert "PEST_MONSTER_TRON_SMOKE_BLOCK_ID" not in stack
    assert "PEST_MONSTER_TRON_FROZEN_SMOKE_" not in stack

    identity = read(IDENTITY)
    for token in (
        "PEST_MONSTER_TRON_R1_BLOCK_ID",
        "8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89",
        "edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7",
        "refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1",
    ):
        assert token in identity, token

    loader = read(LOADER)
    assert "PEST_MONSTER_TRON_R1_BLOCK_ID" in loader
    assert "LOAD_FROZEN_MONSTER_TRON_R1_FOR_VALIDATION_ONLY" in loader
    assert "AUTOMATIC_ONE_SHOT_MONSTER_TRON_R1_EXECUTION" in loader

    claim = read(CLAIM)
    for token in (
        "NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1",
        "refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1",
        "8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89",
        "edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7",
        "41716f6918ec559ee60b14815b5b0f23d3aaf38a",
    ):
        assert token in claim, token
    assert "refs/heads/pest-control/official-attempts/monster-tron-smoke-v1" not in claim

    assert absent_in_head(OFFICIAL_WORKFLOW), "official R1 gameplay workflow must remain absent at this gate"

    wf = read(QUALIFY_WORKFLOW)
    for forbidden in (
        "workflow_dispatch",
        "contents: write",
        "git update-ref",
        "python3 scripts/pest-monster-tron-r1-one-shot-claim.py",
        "PestControlTierOneMonsterTronOfficialExecutionRunner",
        "AUTOMATIC_ONE_SHOT_MONSTER_TRON_R1_EXECUTION",
    ):
        assert forbidden not in wf, forbidden

    assert m["qualification"] == {
        "jobs": ["structural", "claim_helper", "loader_and_rebind"],
        "broad_c2_reexecution_required": False,
        "official_workflow_created_by_this_gate": False,
        "claim_creation_permitted": False,
        "a2_activation_permitted": False,
        "runner_enablement_permitted": False,
        "gameplay_permitted": False,
    }
    assert m["counters"] == {"frozen_seeds": 4, "claims": 0, "games": 0, "actions": 0, "outcomes": 0}

    print(json.dumps({
        "schema": "pest-monster-tron-r1-execution-source-rebind-v3-verifier-v1",
        "status": "PASS_REBIND_QUALIFICATION_STRUCTURE_ONLY",
        "head": git("rev-parse", "HEAD"),
        "tree": git("rev-parse", "HEAD^{tree}"),
        "accepted_c2": C2,
        "delta_paths": len(got),
        "smoke_harness_blob": SMOKE_HARNESS_BLOB,
        "future_claim_ref_required_absent": True,
        "official_workflow_present": False,
        "a2": "INACTIVE",
        "runner": "DISABLED",
        "claims": 0,
        "games": 0,
        "actions": 0,
        "outcomes": 0,
    }, sort_keys=True))

if __name__ == "__main__":
    main()
