#!/usr/bin/env python3
"""Entropy-free validation for the Pest Control vs Monster Tron four-game smoke-vector freeze.

This gate deliberately cannot request production entropy. It reconstructs the complete retired Pest
seed universe from accepted, pinned artifacts and validates the exact four-cell assignment shape
against deterministic fixture bytes. Production entropy belongs to a later separately reviewed
one-shot gate.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import runpy
from pathlib import Path

PROTOCOL = "PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1"
BLOCK = f"{PROTOCOL}_NONEXPERIMENTAL_SMOKE_4"
QUALIFIED_RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
MONSTER_TRON_MAIN = "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"

TERROR_SMOKE_ARTIFACT_ID = 10733086089
TERROR_SMOKE_ARCHIVE_SHA256 = "bbf9f20e834f27f838e37de78c905c213a8917651818367360a8e8a140914961"
TERROR_REPLICATION_ARTIFACT_ID = 10773131628
TERROR_REPLICATION_ARCHIVE_SHA256 = "4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25"
TERROR_REPLICATION_VECTOR_SHA256 = "445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d"

PRIOR_EXCLUSION_COUNT = 554
COMPLETE_EXCLUSION_COUNT = 566

ASSIGNMENT_HEADER = (
    "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
    "monster_tron_seat", "starting_deck", "pest_play_draw", "pest_main_sha256",
    "monster_tron_main_sha256", "qualified_runner",
)
CELLS = (
    ("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY"),
    ("SEAT_ZERO", "SEAT_ONE", "MONSTER_TRON", "DRAW"),
    ("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY"),
    ("SEAT_ONE", "SEAT_ZERO", "MONSTER_TRON", "DRAW"),
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode()


def seed_hex(seed: int) -> str:
    return f"0x{seed & ((1 << 64) - 1):016x}"


def vector_hash(values: list[int] | tuple[int, ...]) -> str:
    return sha256(("\n".join(str(seed) for seed in values) + "\n").encode())


def read_vector(path: Path, expected_sha256: str, expected_count: int, label: str) -> list[int]:
    data = path.read_bytes()
    if b"\r" in data or not data.endswith(b"\n"):
        raise ValueError(f"{label} is not canonical LF text")
    if sha256(data) != expected_sha256:
        raise ValueError(f"{label} hash mismatch")
    values = [int(line) for line in data.decode("utf-8").strip().splitlines()]
    if len(values) != expected_count or len(set(values)) != expected_count or any(seed == 0 for seed in values):
        raise ValueError(f"{label} shape mismatch")
    return values


def complete_exclusion(
    root: Path,
    prior_terror_smoke_dir: Path,
    prior_terror_replication_dir: Path,
) -> tuple[set[int], dict[str, object]]:
    terror_replication_generator = root / (
        "scripts/experiments/pest-control/"
        "generate_tier_one_mono_blue_terror_replication_freeze.py"
    )
    source = runpy.run_path(str(terror_replication_generator))
    prior, _smoke, prior_audit = source["complete_exclusion"](root, prior_terror_smoke_dir)
    prior = set(prior)
    if len(prior) != PRIOR_EXCLUSION_COUNT:
        raise ValueError("accepted Terror replication pre-freeze universe must contain exactly 554 values")

    replication = read_vector(
        prior_terror_replication_dir / "ordered-seeds.txt",
        TERROR_REPLICATION_VECTOR_SHA256,
        12,
        "accepted Terror replication vector",
    )
    overlap = prior.intersection(replication)
    if overlap:
        raise ValueError(f"accepted Terror replication vector overlaps prior exclusion: {sorted(overlap)}")

    complete = prior | set(replication)
    if len(complete) != COMPLETE_EXCLUSION_COUNT:
        raise ValueError("complete Monster Tron smoke exclusion set must contain exactly 566 values")

    return complete, {
        "prior_unique_count": len(prior),
        "accepted_terror_replication_seed_count": len(replication),
        "accepted_terror_replication_vector_sha256": vector_hash(replication),
        "accepted_terror_smoke_artifact_id": TERROR_SMOKE_ARTIFACT_ID,
        "accepted_terror_smoke_archive_sha256": TERROR_SMOKE_ARCHIVE_SHA256,
        "accepted_terror_replication_artifact_id": TERROR_REPLICATION_ARTIFACT_ID,
        "accepted_terror_replication_archive_sha256": TERROR_REPLICATION_ARCHIVE_SHA256,
        "complete_unique_count": len(complete),
        "prior_audit": prior_audit,
    }


def csv_bytes(rows: list[dict[str, object]]) -> bytes:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=ASSIGNMENT_HEADER, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue().encode()


def validate_fixture(
    root: Path,
    prior_terror_smoke_dir: Path,
    prior_terror_replication_dir: Path,
    output: Path,
) -> dict[str, object]:
    excluded, exclusion_audit = complete_exclusion(
        root,
        prior_terror_smoke_dir,
        prior_terror_replication_dir,
    )
    if output.exists() and any(output.iterdir()):
        raise ValueError("output directory must be absent or empty")
    output.mkdir(parents=True, exist_ok=True)

    entropy = hashlib.shake_256(
        b"NONEXPERIMENTAL_PEST_CONTROL_MONSTER_TRON_SMOKE_4_FREEZE_FIXTURE_V1"
    ).digest(32)
    seeds = [
        int.from_bytes(entropy[offset:offset + 8], "big", signed=True)
        for offset in range(0, 32, 8)
    ]
    if len(set(seeds)) != 4 or any(seed == 0 for seed in seeds):
        raise ValueError("deterministic fixture seed shape invalid")
    if set(seeds) & excluded:
        raise ValueError("deterministic fixture collides with retired Pest seed universe")

    rows = []
    for game, (seed, cell) in enumerate(zip(seeds, CELLS, strict=True), 1):
        pest_seat, tron_seat, starter, play_draw = cell
        rows.append({
            "protocol_id": PROTOCOL,
            "block_id": BLOCK,
            "game_number": game,
            "seed_decimal": seed,
            "seed_hex": seed_hex(seed),
            "pest_seat": pest_seat,
            "monster_tron_seat": tron_seat,
            "starting_deck": starter,
            "pest_play_draw": play_draw,
            "pest_main_sha256": PEST_MAIN,
            "monster_tron_main_sha256": MONSTER_TRON_MAIN,
            "qualified_runner": QUALIFIED_RUNNER,
        })

    vector = ("\n".join(str(seed) for seed in seeds) + "\n").encode()
    assignments = csv_bytes(rows)
    manifest = {
        "schema": "pest-control-tier-one-monster-tron-smoke-freeze-fixture@v1",
        "protocol_id": PROTOCOL,
        "block_id": BLOCK,
        "status": "NONEXPERIMENTAL_FIXTURE",
        "qualified_runner": QUALIFIED_RUNNER,
        "runner_state": "DISABLED",
        "deck_hashes": {
            "pest_main": PEST_MAIN,
            "monster_tron_main": MONSTER_TRON_MAIN,
        },
        "collision_audit": {
            "excluded_seed_count": COMPLETE_EXCLUSION_COUNT,
            "new_seed_count": 4,
            "new_unique_count": 4,
            "overlap_count": 0,
            "result": "PASS",
        },
        "assignment_counts": {
            "games": 4,
            "pest_play": 2,
            "pest_draw": 2,
            "pest_seat_zero": 2,
            "pest_seat_one": 2,
            "joint_cells": {
                "SEAT_ZERO_PLAY": 1,
                "SEAT_ZERO_DRAW": 1,
                "SEAT_ONE_PLAY": 1,
                "SEAT_ONE_DRAW": 1,
            },
        },
        "exclusion_audit": exclusion_audit,
        "official_counters": {
            "seeds_generated": 0,
            "games_authorized": 0,
            "games_initialized": 0,
            "actions_submitted": 0,
            "outcome_exposure": 0,
        },
        "production_entropy_requested": False,
        "regeneration_permitted": False,
    }

    (output / "ordered-seeds.txt").write_bytes(vector)
    (output / "assignments.csv").write_bytes(assignments)
    (output / "freeze-manifest.json").write_bytes(canonical_json(manifest))

    return {
        "status": "READY_ENTROPY_NOT_REQUESTED",
        "complete_exclusion_count": len(excluded),
        "fixture_seed_count": len(seeds),
        "fixture_ordered_vector_sha256": sha256(vector),
        "fixture_assignment_csv_sha256": sha256(assignments),
        "fixture_manifest_sha256": sha256(canonical_json(manifest)),
        "production_entropy_requested": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--preflight", action="store_true")
    modes.add_argument("--validate-fixture", action="store_true")
    parser.add_argument("--prior-terror-smoke-dir", type=Path, required=True)
    parser.add_argument("--prior-terror-replication-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[3]
    excluded, audit = complete_exclusion(
        root,
        args.prior_terror_smoke_dir,
        args.prior_terror_replication_dir,
    )

    if args.preflight:
        result = {
            "status": "READY_ENTROPY_NOT_REQUESTED",
            "entropy_requested": False,
            "excluded_values": len(excluded),
            "source_audit": audit,
        }
    else:
        if args.output_dir is None:
            parser.error("--output-dir is required with --validate-fixture")
        result = validate_fixture(
            root,
            args.prior_terror_smoke_dir,
            args.prior_terror_replication_dir,
            args.output_dir,
        )

    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
