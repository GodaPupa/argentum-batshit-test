#!/usr/bin/env python3
import json
import pathlib
import subprocess
import sys

EXPECTED_PARENT = "5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"
EXPECTED_TREE = "e7c37f620c8cefa0352742e38635ca196e887637"
EXPECTED_RUN = 37258189059
EXPECTED_ARTIFACT = 11328302918
EXPECTED_RESULT_SHA = "116bd630d563e9095ecda45ca6aa2e70191f20528fc581d89e6aaee635fab8a5"
EXPECTED_PLAN_SHA = "bcc577b1596c7b37b7f14f0c70d68fefafcf569de3b74f561a0b8317e6e1cc71"

def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()

def fail(msg: str) -> None:
    raise SystemExit(msg)

def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: verify_pest_current_pair_c2_a2_post_phase_b.py MANIFEST")
    manifest_path = pathlib.Path(sys.argv[1])
    data = json.loads(manifest_path.read_text())

    if data.get("schema") != "pest-current-pair-c2-a2-post-phase-b-gap-v1":
        fail("schema mismatch")
    if data.get("status") != "POST_PHASE_B_SOURCE_QUALIFICATION_IN_PROGRESS_NO_ADMISSION":
        fail("status mismatch")
    if data.get("authority") != "SOURCE_QUALIFICATION_ONLY_NO_ADMISSION_NO_GAMEPLAY":
        fail("authority mismatch")
    if data.get("source_parent", {}).get("commit") != EXPECTED_PARENT:
        fail("source parent commit mismatch")
    if data.get("source_parent", {}).get("tree") != EXPECTED_TREE:
        fail("source parent tree mismatch")

    phase_b = data.get("phase_b_v5_2_terminal", {})
    checks = {
        "run": EXPECTED_RUN,
        "aggregate_artifact": EXPECTED_ARTIFACT,
        "aggregate_result_json_sha256": EXPECTED_RESULT_SHA,
        "global_plan_sha256": EXPECTED_PLAN_SHA,
        "completed_rows": 34912840,
        "shard_count": 32,
        "mismatches": 0,
        "unknowns": 0,
        "exceptions": 0,
        "official_counters_delta": 0,
    }
    for key, expected in checks.items():
        if phase_b.get(key) != expected:
            fail(f"phase-b binding mismatch: {key}")

    gates = data.get("gates", {})
    if gates.get("C2P01", {}).get("status") != "SATISFIED_BY_PHASE_B_V5_2_TERMINAL_PARITY":
        fail("C2P01 must be satisfied by exact v5.2 terminal parity")
    if gates.get("C2P02", {}).get("status") != "SATISFIED_BY_PHASE_B_V5_2_TERMINAL_PARITY":
        fail("C2P02 must be satisfied by exact v5.2 terminal parity")
    for gate in ("C2P03", "C2P04", "C2P05", "C2P06", "C2P07"):
        status = gates.get(gate, {}).get("status", "")
        if status.startswith("SATISFIED"):
            fail(f"{gate} must remain fail-closed")

    counters = data.get("official_counters", {})
    if counters != {"allocations": 0, "claims": 0, "games": 0, "outcomes": 0}:
        fail("official counters must remain zero")

    prohibited = set(data.get("prohibited", []))
    for required in (
        "seed_generation", "allocation", "claim", "game_initialization", "gameplay",
        "outcome_exposure", "monster_tron_activation", "spy_combo_execution",
        "phase_b_rerun_or_retry", "reuse_consumed_phase_b_authority",
    ):
        if required not in prohibited:
            fail(f"missing prohibition {required}")

    protected = data.get("protected_blobs", {})
    for path, expected_blob in protected.items():
        actual = git("rev-parse", f"HEAD:{path}")
        if actual != expected_blob:
            fail(f"protected blob drift: {path}: {actual} != {expected_blob}")

    expected_delta = sorted([
        ".github/workflows/pest-current-pair-c2-a2-source-qualify-20261005.yml",
        "docs/experiments/pest-control/PEST_CURRENT_PAIR_C2_A2_POST_PHASE_B_GAP_20261005.json",
        "gym/src/test/kotlin/com/wingedsheep/gym/PestCurrentPairC2A2SourceInventoryTest.kt",
        "scripts/experiments/pest-control/verify_pest_current_pair_c2_a2_post_phase_b.py",
    ])
    if git("rev-parse", "HEAD^") != EXPECTED_PARENT:
        fail("candidate must be exactly one source-only child of the reviewed Phase-B implementation")
    actual_delta = sorted(filter(None, git("diff", "--name-only", f"{EXPECTED_PARENT}..HEAD").splitlines()))
    if actual_delta != expected_delta:
        fail(f"unexpected candidate delta: {actual_delta}")

    print(json.dumps({
        "schema": "pest-current-pair-c2-a2-post-phase-b-source-verifier-result-v1",
        "status": "PASS",
        "head": git("rev-parse", "HEAD"),
        "tree": git("rev-parse", "HEAD^{tree}"),
        "phase_b_run": EXPECTED_RUN,
        "phase_b_aggregate_artifact": EXPECTED_ARTIFACT,
        "phase_b_aggregate_result_json_sha256": EXPECTED_RESULT_SHA,
        "c2p01": gates["C2P01"]["status"],
        "c2p02": gates["C2P02"]["status"],
        "open_gates": ["C2P03", "C2P04", "C2P05", "C2P06", "C2P07"],
        "official_counters_delta": 0,
    }, sort_keys=True))

if __name__ == "__main__":
    main()
