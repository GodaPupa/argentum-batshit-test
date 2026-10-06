#!/usr/bin/env python3
import csv
import hashlib
import json
import pathlib
import subprocess
import sys

AUTH_COMMIT = "3e9e1d573a97e0030ce64242b41cc13f8f77440e"
AUTH_TREE = "bab0b72e6cd2ce699135a97620260176aad6ee47"
AUTH_BLOB = "429b4148429ce9c4ae12241d76ab61f807423926"
FREEZE_COMMIT = "41716f6918ec559ee60b14815b5b0f23d3aaf38a"
SOURCE_GATE_COMMIT = "d2d249c96e9a3917666efbbb5b35bc37a53a46ec"
SOURCE_GATE_TREE = "6f6ae97bb35a03a15037686d6c24bff4750ab789"
SOURCE_GATE_MANIFEST_BLOB = "04aef8e817f0e4cbc74cdace6230474414c94454"
IMPLEMENTATION_COMMIT = "433df3310efe31c49f27034b50e6d8d7e60561f7"
IMPLEMENTATION_TREE = "fbc32e30d9636fa7cc6c99a776554145b0cf6901"
VECTOR = [8509670981736218459, -8564666863904712979, -8259800499619080267, 3489680325849498530]
VECTOR_SHA = "8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89"
RAW_SHA = "0804de7809cc8eaad0f92adb3f26cbb0d564aa92011a39143859aefd30c67a31"
ASSIGN_SHA = "edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
MONSTER_MAIN = "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"
BASE = pathlib.Path("docs/experiments/pest-control/r1-production-freeze-20261005")
EXPECTED_DELTA = sorted([
    ".github/workflows/pest-monster-tron-r1-execution-c2-validate-20261005.yml",
    "docs/experiments/pest-control/PEST_MONSTER_TRON_R1_EXECUTION_C2_20261005.json",
    "scripts/experiments/pest-control/verify_pest_monster_tron_r1_execution_c2.py",
])

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if len(sys.argv) != 2:
        raise SystemExit("manifest required")
    manifest_path = pathlib.Path(sys.argv[1])
    m = json.loads(manifest_path.read_text())

    assert m["schema"] == "pest-monster-tron-r1-vector-specific-execution-c2-v1"
    assert m["authority"] == "EXECUTION_C2_CONSTRUCTION_QUALIFICATION_ONLY__RUNNER_DISABLED__NO_CLAIM__NO_GAMEPLAY"
    assert git("rev-parse", "HEAD^") == AUTH_COMMIT
    assert git("rev-parse", AUTH_COMMIT + "^{tree}") == AUTH_TREE
    assert git("rev-parse", "HEAD:docs/experiments/pest-control/PEST_MONSTER_TRON_R1_VECTOR_EXECUTION_AUTHORIZATION_20261005.json") == AUTH_BLOB
    assert git("rev-parse", SOURCE_GATE_COMMIT + "^{tree}") == SOURCE_GATE_TREE
    assert git("rev-parse", IMPLEMENTATION_COMMIT + "^{tree}") == IMPLEMENTATION_TREE
    subprocess.check_call(["git", "merge-base", "--is-ancestor", IMPLEMENTATION_COMMIT, "HEAD"])
    subprocess.check_call(["git", "merge-base", "--is-ancestor", SOURCE_GATE_COMMIT, "HEAD"])
    subprocess.check_call(["git", "merge-base", "--is-ancestor", FREEZE_COMMIT, "HEAD"])
    assert sorted(git("diff", "--name-only", AUTH_COMMIT + "..HEAD").splitlines()) == EXPECTED_DELTA

    assert git("rev-parse", "HEAD:docs/experiments/pest-control/PEST_CURRENT_PAIR_C2_A2_ADMISSION_CANDIDATE_20261005.json") == SOURCE_GATE_MANIFEST_BLOB

    sg = m["source_gate"]
    assert sg["candidate_commit"] == SOURCE_GATE_COMMIT
    assert sg["candidate_tree"] == SOURCE_GATE_TREE
    assert sg["implementation_commit"] == IMPLEMENTATION_COMMIT
    assert sg["implementation_tree"] == IMPLEMENTATION_TREE
    assert sg["independent_review_comment"] == 6005638499
    assert sg["independent_review_sha256"] == "83599adcfaafb32ef2339174f02fb2cf160f4f72aa30c61831fffb6d72f95c1f"

    auth_path = pathlib.Path("docs/experiments/pest-control/PEST_MONSTER_TRON_R1_VECTOR_EXECUTION_AUTHORIZATION_20261005.json")
    auth = json.loads(auth_path.read_text())
    assert auth["authority"] == "VECTOR_SPECIFIC_EXECUTION_AUTHORIZATION__RUNNER_STILL_DISABLED"
    assert auth["execution_c2"]["construction_authorized"] is True
    assert auth["execution_c2"]["approval_authorized"] is False
    assert auth["runner"]["state"] == "DISABLED"
    assert auth["a2"]["state"] == "INACTIVE"
    assert auth["future_r1_claim_ref"]["state"] == "ABSENT"

    freeze = m["freeze"]
    assert freeze["final_commit"] == FREEZE_COMMIT
    assert freeze["members_decimal"] == VECTOR
    assert freeze["ordered_vector_sha256"] == VECTOR_SHA
    assert freeze["raw_sha256"] == RAW_SHA
    assert freeze["assignment_sha256"] == ASSIGN_SHA
    assert freeze["exclusion_count"] == 570
    assert freeze["exclusion_sha256"] == "70c936391c43533b71698574eb9b58cca123fa8deee472970e1a25478131cf15"

    raw = BASE / "raw-entropy.bin"
    ordered = BASE / "ordered-members.txt"
    assignments = BASE / "assignments.csv"
    freeze_manifest = BASE / "freeze-manifest.json"
    validation = BASE / "validation.json"
    assert len(raw.read_bytes()) == 32 and sha(raw) == RAW_SHA
    assert sha(ordered) == VECTOR_SHA
    assert [int(x) for x in ordered.read_text().strip().splitlines()] == VECTOR
    assert sha(assignments) == ASSIGN_SHA

    rows = list(csv.DictReader(assignments.open()))
    assert len(rows) == 4
    expected_cells = [
        ("0", "1", "PEST_CONTROL", "PLAY"),
        ("0", "1", "MONSTER_TRON", "DRAW"),
        ("1", "0", "PEST_CONTROL", "PLAY"),
        ("1", "0", "MONSTER_TRON", "DRAW"),
    ]
    assert [(r["pest_seat"], r["monster_tron_seat"], r["starting_deck"], r["pest_play_draw"]) for r in rows] == expected_cells
    assert [int(r["seed_decimal"]) for r in rows] == VECTOR

    fm = json.loads(freeze_manifest.read_text())
    va = json.loads(validation.read_text())
    assert fm["status"] == "FROZEN_UNEXECUTED"
    assert fm["vector"]["ordered_vector_sha256"] == VECTOR_SHA
    assert fm["claim_execution_boundary"]["runner_state"] == "DISABLED"
    assert fm["claim_execution_boundary"]["execution_c2"] == "NOT_CREATED_OR_PINNED"
    assert fm["claim_execution_boundary"]["a2"] == "INACTIVE"
    assert fm["official_counters"]["new_claims"] == 0
    assert fm["official_counters"]["new_games"] == 0
    assert fm["official_counters"]["new_outcomes"] == 0
    assert va["status"] == "PASS_VALID_PRODUCTION_VECTOR"
    assert va["reroll_occurred"] is False
    assert va["partial_salvage_occurred"] is False

    for p, b in {**m["protected_source_blobs"], **m["protected_test_blobs"]}.items():
        actual = git("rev-parse", "HEAD:" + p)
        assert actual == b, (p, actual, b)

    assert m["pair"] == {
        "pest_main_sha256": PEST_MAIN,
        "monster_tron_main_sha256": MONSTER_MAIN,
    }
    assert m["runner"]["required_state"] == "DISABLED"
    assert m["a2"]["required_state"] == "INACTIVE"
    assert m["counters"] == {"frozen_seeds": 4, "claims": 0, "games": 0, "actions": 0, "outcomes": 0}

    wf = pathlib.Path(".github/workflows/pest-monster-tron-r1-execution-c2-validate-20261005.yml").read_text()
    forbidden = [
        "workflow_dispatch",
        "contents: write",
        "git update-ref",
        "pest-monster-tron-one-shot-claim.py",
        "PestControlTierOneMonsterTronOfficialExecutionRunner",
        "GameInitializer",
        "stepExactlyOne(",
        "os.urandom",
    ]
    for token in forbidden:
        assert token not in wf, token

    replay = pathlib.Path("gym/src/main/kotlin/com/wingedsheep/gym/pest/PestCurrentPairC2ReplayJournal.kt").read_text()
    for token in ["GameInitializer", "OfficialGame", "stepExactlyOne(", "ProductionDriver", "withRngSeed("]:
        assert token not in replay, token

    print(json.dumps({
        "schema": "pest-monster-tron-r1-execution-c2-verifier-v1",
        "status": "PASS",
        "head": git("rev-parse", "HEAD"),
        "tree": git("rev-parse", "HEAD^{tree}"),
        "source_gate_commit": SOURCE_GATE_COMMIT,
        "implementation_commit": IMPLEMENTATION_COMMIT,
        "protected_source_blobs": len(m["protected_source_blobs"]),
        "protected_test_blobs": len(m["protected_test_blobs"]),
        "vector_sha256": VECTOR_SHA,
        "assignment_sha256": ASSIGN_SHA,
        "runner": "DISABLED",
        "claims": 0,
        "games": 0,
        "actions": 0,
        "outcomes": 0
    }, sort_keys=True))

if __name__ == "__main__":
    main()
