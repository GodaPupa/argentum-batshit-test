#!/usr/bin/env python3
"""One-shot, artifact-only Tier-1 Grixis smoke-vector freeze.

Preflight and fixture validation request no entropy. Production generation makes exactly one
``os.urandom(32)`` call, quarantines the complete draw before validation, and never retries,
replaces, or rerolls a value. No game is initialized and no outcome-bearing API is reachable.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
import platform
from datetime import datetime, timezone
from pathlib import Path


PROTOCOL = "PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1"
BLOCK = f"{PROTOCOL}_NONEXPERIMENTAL_SMOKE_4"
QUALIFIED_RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
CONSTRUCTION_MERGE = "fe8b2cfe796e307b2ed0deb29fd19d98e4286b3c"
CONSTRUCTION_PROOF = "dc981e02af9b687f4e999bfc13b35630299dfd9ad84cf5ae86ff49f752bdcecf"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
GRIXIS_MAIN = "2e20ec68c58dda1f913f99e8c97b7458f920a9be18bb4ee2c09784326cc2896c"
GRIXIS_SIDEBOARD = "19c64bb94ef1dbdd953409dede79b02a478e9cf959bb230fe0c67a9b43d3177a"
GRIXIS_75 = "b73fe84ec0dd11961f45cf0ab39f155c92d99636ff1ad961bbc8f4774504bdcd"
REGISTRY_SHA256 = "fcc09ba4db85c0b157de7301b5506a1c9cd29c1d85ccb3f6482d6cfe9260a246"
CALIBRATION_SEEDS_SHA256 = "41d9cf707fba5548d14040d7e3a4a26bd8f9f5bbacf1b12d7651423dcabaff0a"
QUALIFICATION_VECTOR_SHA256 = "c38ff24c9b45e36036cab506475bb8283f4e14e211318a7a7e6ab99acef6f497"
CLOSURE_DOC_SHA256 = "0e0cbffe4e01ea9dcefcb45cebfdac508ca0dc5a01b38a5677a71b474eb310f8"
CLOSURE_SOURCE_SHA256 = "66b4fe32a638b18f2bc41294d62a578915d75abb54c15f7bace7e2aea09df799"
ACK = "GENERATE_TIER_ONE_GRIXIS_SMOKE_4_NO_GAMEPLAY"

REJECTED_V2_CANDIDATE = (
    352421150441762375, -7897966070063678192, 5918577377114013031,
    -676340927639613933, 7240270641543801242, -6824674089531578284,
    5070049516395972099, -8923988165019673829, -4759076379260409612,
    2895538220561058244,
)
V2_SMOKE_SEED = 0x5045_5354_5632_0001
V2_QUALIFICATION_SEEDS = (
    -732397062409606731, 4596979853245926030, 4507032754122982316,
    -5564966065980129261, 8476619641367786031, 1414160894548832567,
    3302650432450662658, 341720040458009850, -5747635901131759596,
    -3699742829816830332, 7583083738567727547, -5906369554324289093,
    3514950241826136275, 3842885181253920113, 6311427428069810662,
    -1491108298918688329, 4669580447433488797, 2752179294295495270,
    4105628388144233595, 6462402248114406222, -992781660344579861,
    -1289506778599929880, 8279720869557306922, -4554198001461372596,
    -6860404425658278260, -7068309785984030093, -3750794017522638449,
    -7901838964760946079, -4070092933175538502, -5219400242073648556,
    4961486274658274, -3078655684468158075, 5059307618337066103,
    -2904703119014970335, -5946696101573497423, -2058770259479906003,
    6718623573204494842, -3343120105487636534, 8290640892018664272,
    1356855089750109821, 8851789986585421424, 7742524024597294797,
    -7445736929764600755, -4087539278869089627, 8083107424683092545,
    6344396672439742185, 1397810286474485893, 5876708504126753079,
    2899146021165779889, 5987363842123421214,
)
ASSIGNMENT_HEADER = (
    "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
    "grixis_seat", "starting_deck", "pest_play_draw", "pest_main_sha256",
    "grixis_main_sha256", "grixis_sideboard_sha256", "grixis_complete75_sha256",
    "qualified_runner",
)
CELLS = (
    ("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY"),
    ("SEAT_ZERO", "SEAT_ONE", "GRIXIS_AFFINITY", "DRAW"),
    ("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY"),
    ("SEAT_ONE", "SEAT_ZERO", "GRIXIS_AFFINITY", "DRAW"),
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode()


def csv_bytes(rows: list[dict[str, object]]) -> bytes:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=ASSIGNMENT_HEADER, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue().encode()


def seed_hex(seed: int) -> str:
    return f"0x{seed & ((1 << 64) - 1):016x}"


def write_new_fsynced(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o444)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    directory = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def read_seed_column(data: bytes, expected_header: tuple[str, ...]) -> list[int]:
    text = data.decode("utf-8")
    if "\r" in text or not text.endswith("\n"):
        raise ValueError("seed source is not canonical LF text")
    reader = csv.DictReader(io.StringIO(text, newline=""))
    if tuple(reader.fieldnames or ()) != expected_header:
        raise ValueError("seed source header mismatch")
    return [int(row["seed_decimal"]) for row in reader]


def audit_sources(root: Path) -> tuple[set[int], dict[str, object]]:
    registry_path = root / "docs/experiments/pest-control/matchup-block-a-seed-registry.csv"
    calibration_path = root / "docs/experiments/pest-control/v2-official-execution/attempted-seeds.csv"
    closure_doc = root / "docs/experiments/pest-control/tier-one-grixis-construction-closure.md"
    closure_source = root / "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneGrixisConstructionClosure.kt"
    actual_hashes = {
        "permanent_registry": sha256(registry_path.read_bytes()),
        "accepted_v2_calibration": sha256(calibration_path.read_bytes()),
        "v2_qualification_vector": sha256(
            ("\n".join(str(seed) for seed in V2_QUALIFICATION_SEEDS) + "\n").encode()
        ),
        "construction_closure_doc": sha256(closure_doc.read_bytes()),
        "construction_closure_source": sha256(closure_source.read_bytes()),
    }
    expected_hashes = {
        "permanent_registry": REGISTRY_SHA256,
        "accepted_v2_calibration": CALIBRATION_SEEDS_SHA256,
        "v2_qualification_vector": QUALIFICATION_VECTOR_SHA256,
        "construction_closure_doc": CLOSURE_DOC_SHA256,
        "construction_closure_source": CLOSURE_SOURCE_SHA256,
    }
    if actual_hashes != expected_hashes:
        raise ValueError(f"pinned source hash mismatch: expected={expected_hashes} actual={actual_hashes}")
    registry = read_seed_column(
        registry_path.read_bytes(),
        ("category", "identity", "position", "seed_decimal", "seed_hex", "source_path", "disposition"),
    )
    calibration = read_seed_column(calibration_path.read_bytes(), ("game_number", "seed_decimal"))
    groups = {
        "permanent_registry": set(registry),
        "accepted_v2_calibration": set(calibration),
        "rejected_v2_candidate": set(REJECTED_V2_CANDIDATE),
        "v2_smoke_fixture": {V2_SMOKE_SEED},
        "frozen_v2_qualification": set(V2_QUALIFICATION_SEEDS),
    }
    expected_counts = {
        "permanent_registry": 463, "accepted_v2_calibration": 10,
        "rejected_v2_candidate": 10, "v2_smoke_fixture": 1, "frozen_v2_qualification": 50,
    }
    if {name: len(values) for name, values in groups.items()} != expected_counts:
        raise ValueError("exclusion source cardinality mismatch")
    names = tuple(groups)
    for index, left in enumerate(names):
        for right in names[index + 1:]:
            overlap = groups[left] & groups[right]
            if overlap:
                raise ValueError(f"exclusion sources overlap: {left}/{right}: {sorted(overlap)}")
    excluded = set().union(*groups.values())
    if len(excluded) != 534:
        raise ValueError("complete exclusion set must contain exactly 534 values")
    return excluded, {"counts": expected_counts, "complete_unique_count": 534, "pinned_sha256": actual_hashes}


def generate(root: Path, output: Path, entropy: bytes, fixture: bool, freeze_commit: str, freeze_tree: str) -> dict[str, object]:
    excluded, exclusion_audit = audit_sources(root)
    if output.exists() and any(output.iterdir()):
        raise ValueError("output directory must be absent or empty")
    if len(entropy) != 32:
        raise ValueError("entropy draw must be exactly 32 bytes")
    output.mkdir(parents=True, exist_ok=True)
    seeds = [int.from_bytes(entropy[offset:offset + 8], "big", signed=True) for offset in range(0, 32, 8)]
    quarantine = {
        "block_id": BLOCK, "entropy_byte_count": 32, "entropy_sha256": sha256(entropy),
        "generation_method": "one os.urandom(32) call; four signed big-endian 64-bit seeds in unchanged draw order",
        "seeds_decimal": seeds, "seeds_hex": [seed_hex(seed) for seed in seeds],
        "status": "NONEXPERIMENTAL_FIXTURE" if fixture else "QUARANTINED_UNATTEMPTED",
    }
    if not fixture:
        quarantine["drawn_at_utc"] = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    quarantine_path = output / "quarantined-vector.json"
    write_new_fsynced(quarantine_path, canonical_json(quarantine))
    errors = []
    if any(seed == 0 for seed in seeds): errors.append("zero seed")
    if len(set(seeds)) != 4: errors.append("duplicate seed")
    overlap = sorted(set(seeds) & excluded)
    if overlap: errors.append(f"complete-registry overlap: {overlap}")
    if errors:
        write_new_fsynced(output / "invalid-retired.json", canonical_json({
            "block_id": BLOCK, "errors": errors, "quarantine_sha256": sha256(quarantine_path.read_bytes()),
            "status": "NONEXPERIMENTAL_FIXTURE_INVALID" if fixture else "INVALID_RETIRED",
        }))
        raise ValueError("; ".join(errors))
    rows = []
    for game, (seed, cell) in enumerate(zip(seeds, CELLS, strict=True), 1):
        pest_seat, grixis_seat, starter, play_draw = cell
        rows.append({
            "protocol_id": PROTOCOL, "block_id": BLOCK, "game_number": game,
            "seed_decimal": seed, "seed_hex": seed_hex(seed), "pest_seat": pest_seat,
            "grixis_seat": grixis_seat, "starting_deck": starter, "pest_play_draw": play_draw,
            "pest_main_sha256": PEST_MAIN, "grixis_main_sha256": GRIXIS_MAIN,
            "grixis_sideboard_sha256": GRIXIS_SIDEBOARD, "grixis_complete75_sha256": GRIXIS_75,
            "qualified_runner": QUALIFIED_RUNNER,
        })
    vector = ("\n".join(str(seed) for seed in seeds) + "\n").encode()
    assignments = csv_bytes(rows)
    manifest = {
        "artifact_hashes": {
            "assignment_csv_sha256": sha256(assignments), "ordered_vector_sha256": sha256(vector),
            "quarantined_vector_sha256": sha256(quarantine_path.read_bytes()),
        },
        "assignment_counts": {
            "games": 4, "pest_draw": 2, "pest_play": 2, "pest_seat_one": 2, "pest_seat_zero": 2,
            "joint_cells": {"SEAT_ZERO_PLAY": 1, "SEAT_ZERO_DRAW": 1, "SEAT_ONE_PLAY": 1, "SEAT_ONE_DRAW": 1},
        },
        "block_id": BLOCK,
        "collision_audit": {"excluded_seed_count": 534, "new_seed_count": 4, "new_unique_count": 4, "overlap_count": 0, "result": "PASS"},
        "construction": {"closure_sha256": CONSTRUCTION_PROOF, "merge_commit": CONSTRUCTION_MERGE},
        "deck_hashes": {"grixis_complete75": GRIXIS_75, "grixis_main": GRIXIS_MAIN, "grixis_sideboard": GRIXIS_SIDEBOARD, "pest_main": PEST_MAIN},
        "environment": {"machine": platform.machine(), "os": platform.platform(), "python": platform.python_version()},
        "exclusion_audit": exclusion_audit,
        "freeze_source": {"commit": freeze_commit, "tree": freeze_tree},
        "generation": {"generator_sha256": sha256(Path(__file__).read_bytes()), "method": "one os.urandom(32) call", "regeneration_permitted": False},
        "official_counters": {"actions_submitted": 0, "artifacts_with_outcomes": 0, "games_initialized": 0, "outcome_exposure": 0},
        "protocol_id": PROTOCOL, "qualified_runner": QUALIFIED_RUNNER,
        "runner_state": "DISABLED", "status": "NONEXPERIMENTAL_FIXTURE" if fixture else "FROZEN_UNEXECUTED",
    }
    artifacts = {
        "ordered-seeds.txt": vector, "assignments.csv": assignments,
        "freeze-manifest.json": canonical_json(manifest),
    }
    for name, data in artifacts.items():
        write_new_fsynced(output / name, data)
    checksums = {name: sha256(data) for name, data in artifacts.items()}
    checksums[quarantine_path.name] = sha256(quarantine_path.read_bytes())
    write_new_fsynced(output / "artifacts.sha256", ("\n".join(
        f"{digest}  {name}" for name, digest in sorted(checksums.items())
    ) + "\n").encode())
    return {
        "assignment_csv_sha256": sha256(assignments), "block_id": BLOCK,
        "complete_exclusion_count": len(excluded), "manifest_sha256": sha256(artifacts["freeze-manifest.json"]),
        "ordered_vector_sha256": sha256(vector), "seed_count": 4, "status": manifest["status"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--preflight", action="store_true")
    modes.add_argument("--validate-fixture", action="store_true")
    modes.add_argument("--generate", action="store_true")
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--freeze-commit", default="0" * 40)
    parser.add_argument("--freeze-tree", default="0" * 40)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    if args.preflight:
        excluded, audit = audit_sources(root)
        result = {"entropy_requested": False, "excluded_values": len(excluded), "source_audit": audit, "status": "READY"}
    else:
        if args.output_dir is None:
            parser.error("--output-dir is required")
        if args.generate:
            if os.environ.get("PEST_GRIXIS_FREEZE_ACK") != ACK:
                raise ValueError("exact production acknowledgement is required")
            if os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
                raise ValueError("production entropy is restricted to workflow_dispatch")
            if os.environ.get("GITHUB_REF") != "refs/heads/main" or os.environ.get("GITHUB_RUN_ATTEMPT") != "1":
                raise ValueError("production entropy requires main and workflow attempt 1")
            if any(len(value) != 40 or any(char not in "0123456789abcdef" for char in value)
                   for value in (args.freeze_commit, args.freeze_tree)):
                raise ValueError("freeze commit and tree must be lowercase 40-digit hashes")
            audit_sources(root)
            if args.output_dir.exists() and any(args.output_dir.iterdir()):
                raise ValueError("output directory must be absent or empty")
            entropy = os.urandom(32)
        else:
            entropy = hashlib.shake_256(b"NONEXPERIMENTAL_TIER_ONE_GRIXIS_SMOKE_FREEZE_V1").digest(32)
        result = generate(root, args.output_dir, entropy, args.validate_fixture, args.freeze_commit, args.freeze_tree)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
