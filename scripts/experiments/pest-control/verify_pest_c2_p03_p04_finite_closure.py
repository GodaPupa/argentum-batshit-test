#!/usr/bin/env python3
import json
import pathlib
import subprocess
import sys

PARENT = "5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"
EXPECTED_DELTA = sorted([
    ".github/workflows/pest-c2-p03-p04-finite-closure-20261005.yml",
    "ai/src/test/kotlin/com/wingedsheep/ai/engine/PestCurrentPairC2P03P04DecisionSurfaceTest.kt",
    "docs/experiments/pest-control/PEST_C2_P03_P04_FINITE_CLOSURE_20261005.json",
    "scripts/experiments/pest-control/verify_pest_c2_p03_p04_finite_closure.py",
])

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def fail(message):
    raise SystemExit(message)

def main():
    if len(sys.argv) != 2:
        fail("usage: verify_pest_c2_p03_p04_finite_closure.py MANIFEST")
    m = json.loads(pathlib.Path(sys.argv[1]).read_text())
    if m.get("schema") != "pest-current-pair-c2-p03-p04-finite-closure-v1":
        fail("schema")
    if m.get("authority") != "SOURCE_POLICY_QUALIFICATION_ONLY_NO_C2_ADMISSION_NO_GAMEPLAY":
        fail("authority")
    if m.get("source_parent", {}).get("commit") != PARENT:
        fail("parent binding")
    if git("rev-parse", "HEAD^") != PARENT:
        fail("candidate must be one child of Phase-B I")
    actual = sorted(filter(None, git("diff", "--name-only", PARENT + "..HEAD").splitlines()))
    if actual != EXPECTED_DELTA:
        fail("unexpected delta: " + repr(actual))

    evidence = m.get("accepted_predecessor_evidence", {})
    required = {
        "source_rebind_run": 37310809639,
        "source_rebind_artifact": 11345496531,
        "monster_policy_run": 37312421562,
        "monster_policy_artifact": 11346498738,
    }
    for key, expected in required.items():
        if evidence.get(key) != expected:
            fail("evidence binding " + key)

    surfaces = m.get("finite_surface_inventory", [])
    ids = [row.get("id") for row in surfaces]
    if len(ids) < 18 or len(ids) != len(set(ids)):
        fail("finite surface inventory")
    required_kinds = {"priority", "action", "payment", "search", "reorder", "scry", "surveil", "target", "additional_cost", "cascade", "trigger_order"}
    kinds = {row.get("kind") for row in surfaces}
    if not required_kinds.issubset(kinds):
        fail("missing surface kinds: " + repr(sorted(required_kinds-kinds)))
    if any(row.get("status") != "QUALIFICATION_BOUND" for row in surfaces):
        fail("unbound finite surface")

    excluded = {row.get("source") for row in m.get("engine_internal_random_no_player_choice", [])}
    if excluded != {"Follow the Lumarets remainder random order", "Haunted Fengraf random creature choice", "Cascade nonchosen exiles random bottom order"}:
        fail("random exclusion inventory")

    for path, expected in m.get("protected_blobs", {}).items():
        got = git("rev-parse", "HEAD:" + path)
        if got != expected:
            fail(f"blob drift {path}: {got} != {expected}")

    if m.get("acceptance_if_validation_success", {}).get("C2P03") != "SATISFIED_CURRENT_FINITE_PAIR_ACTION_PAYMENT_SURFACE":
        fail("C2P03 acceptance")
    if m.get("acceptance_if_validation_success", {}).get("C2P04") != "SATISFIED_CURRENT_FINITE_PAIR_PENDING_CHOICE_SURFACE":
        fail("C2P04 acceptance")
    if m.get("official_counters") != {"allocations":0,"claims":0,"games":0,"outcomes":0}:
        fail("counters")
    if set(m.get("prohibited", [])) != {
        "official_seed_generation","allocation","official_claim","official_game_initialization",
        "gameplay","official_action_submission","outcome_exposure","monster_tron_activation",
        "spy_combo_execution","phase_b_rerun_or_retry","reuse_consumed_phase_b_authority"
    }:
        fail("prohibitions")

    print(json.dumps({
        "schema":"pest-c2-p03-p04-finite-closure-verifier-result-v1",
        "status":"PASS",
        "head":git("rev-parse","HEAD"),
        "tree":git("rev-parse","HEAD^{tree}"),
        "finite_surfaces":len(surfaces),
        "c2p03_candidate":m["acceptance_if_validation_success"]["C2P03"],
        "c2p04_candidate":m["acceptance_if_validation_success"]["C2P04"],
        "official_counters_delta":0,
    }, sort_keys=True))

if __name__ == "__main__":
    main()
