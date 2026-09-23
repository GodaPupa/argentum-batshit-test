#!/usr/bin/env python3
"""Artifact-only 12-game Mono-Blue Terror replication-vector freeze.

Pull-request validation is entropy-free. A later separately authorized production path may make
exactly one os.urandom(96) call, quarantine the full draw before validation, and may never reroll,
replace, or regenerate a value. No game initializer or outcome-bearing API is reachable here.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
import platform
import runpy
from datetime import datetime, timezone
from pathlib import Path

PROTOCOL = "PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1"
BLOCK = f"{PROTOCOL}_REPLICATION_12"
QUALIFIED_RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
TERROR_MAIN = "6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62"
TERROR_SIDEBOARD = "af4296c5e6af4be3b05b96d0267bd04da12188126e774ef8e63bb16c00bc908c"
TERROR_75 = "ae25ede2663cbc2fa41b4413381485df962b272791e1b3f49e2d69c45054e7d9"
SMOKE_VECTOR_SHA256 = "ca508c842886fff2af7db1c966fbedbb8ae801796c5043e26b22def056c724ea"
SMOKE_FREEZE_ARTIFACT_ID = 10733086089
SMOKE_FREEZE_ARTIFACT_SHA256 = "bbf9f20e834f27f838e37de78c905c213a8917651818367360a8e8a140914961"
LEGACY_EXCLUSION_COUNT = 550
COMPLETE_EXCLUSION_COUNT = 554
PRODUCTION_AUTH = "AUTOMATIC_ONE_SHOT_TERROR_REPLICATION_FREEZE_NO_GAMEPLAY"
FROZEN_PROVENANCE_PATH = (
    "docs/experiments/pest-control/tier-one-mono-blue-terror-replication-freeze-provenance.json"
)

ASSIGNMENT_HEADER = (
    "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
    "terror_seat", "starting_deck", "pest_play_draw", "pest_main_sha256",
    "terror_main_sha256", "terror_sideboard_sha256", "terror_complete75_sha256",
    "qualified_runner",
)
BASE_CELLS = (
    ("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY"),
    ("SEAT_ZERO", "SEAT_ONE", "MONO_BLUE_TERROR", "DRAW"),
    ("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY"),
    ("SEAT_ONE", "SEAT_ZERO", "MONO_BLUE_TERROR", "DRAW"),
)
CELLS = BASE_CELLS * 3


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n"
    ).encode()


def seed_hex(seed: int) -> str:
    return f"0x{seed & ((1 << 64) - 1):016x}"


def vector_hash(values: list[int] | tuple[int, ...]) -> str:
    return sha256(("\n".join(str(seed) for seed in values) + "\n").encode())


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


def csv_bytes(rows: list[dict[str, object]]) -> bytes:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=ASSIGNMENT_HEADER, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue().encode()


def read_prior_smoke_vector(path: Path) -> list[int]:
    data = path.read_bytes()
    if b"\r" in data or not data.endswith(b"\n"):
        raise ValueError("accepted Terror smoke vector is not canonical LF text")
    if sha256(data) != SMOKE_VECTOR_SHA256:
        raise ValueError("accepted Terror smoke vector hash mismatch")
    values = [int(line) for line in data.decode("utf-8").strip().splitlines()]
    if len(values) != 4 or len(set(values)) != 4 or any(seed == 0 for seed in values):
        raise ValueError("accepted Terror smoke vector shape mismatch")
    return values


def legacy_exclusion(root: Path) -> tuple[set[int], dict[str, object]]:
    smoke_generator = root / (
        "scripts/experiments/pest-control/"
        "generate_tier_one_mono_blue_terror_smoke_freeze.py"
    )
    source = runpy.run_path(str(smoke_generator))
    excluded, audit = source["audit_sources"](root)
    excluded = set(excluded)
    if len(excluded) != LEGACY_EXCLUSION_COUNT:
        raise ValueError("legacy Terror smoke exclusion set must contain exactly 550 values")
    return excluded, audit


def complete_exclusion(
    root: Path,
    prior_smoke_dir: Path,
) -> tuple[set[int], list[int], dict[str, object]]:
    excluded, legacy_audit = legacy_exclusion(root)
    smoke = read_prior_smoke_vector(prior_smoke_dir / "ordered-seeds.txt")
    overlap = excluded.intersection(smoke)
    if overlap:
        raise ValueError(
            f"accepted Terror smoke seeds overlap legacy exclusion: {sorted(overlap)}"
        )
    complete = excluded | set(smoke)
    if len(complete) != COMPLETE_EXCLUSION_COUNT:
        raise ValueError("complete Terror replication exclusion set must contain exactly 554 values")
    return complete, smoke, {
        "legacy": legacy_audit,
        "legacy_unique_count": len(excluded),
        "accepted_smoke_seed_count": len(smoke),
        "accepted_smoke_vector_sha256": vector_hash(smoke),
        "complete_unique_count": len(complete),
        "accepted_smoke_artifact_id": SMOKE_FREEZE_ARTIFACT_ID,
        "accepted_smoke_artifact_archive_sha256": SMOKE_FREEZE_ARTIFACT_SHA256,
    }


def generate(
    root: Path,
    prior_smoke_dir: Path,
    output: Path,
    entropy: bytes,
    fixture: bool,
    freeze_commit: str,
    freeze_tree: str,
) -> dict[str, object]:
    excluded, smoke, exclusion_audit = complete_exclusion(root, prior_smoke_dir)

    if output.exists() and any(output.iterdir()):
        raise ValueError("output directory must be absent or empty")
    if len(entropy) != 96:
        raise ValueError("entropy draw must be exactly 96 bytes")

    output.mkdir(parents=True, exist_ok=True)
    seeds = [
        int.from_bytes(entropy[offset:offset + 8], "big", signed=True)
        for offset in range(0, 96, 8)
    ]

    quarantine = {
        "block_id": BLOCK,
        "entropy_byte_count": 96,
        "entropy_sha256": sha256(entropy),
        "generation_method": (
            "one os.urandom(96) call; twelve signed big-endian 64-bit seeds "
            "in unchanged draw order"
        ),
        "seeds_decimal": seeds,
        "seeds_hex": [seed_hex(seed) for seed in seeds],
        "status": "NONEXPERIMENTAL_FIXTURE" if fixture else "QUARANTINED_UNATTEMPTED",
    }
    if not fixture:
        quarantine["drawn_at_utc"] = (
            datetime.now(timezone.utc)
            .isoformat(timespec="seconds")
            .replace("+00:00", "Z")
        )

    quarantine_path = output / "quarantined-vector.json"
    write_new_fsynced(quarantine_path, canonical_json(quarantine))

    errors = []
    if any(seed == 0 for seed in seeds):
        errors.append("zero seed")
    if len(set(seeds)) != 12:
        errors.append("duplicate seed")
    overlap = sorted(set(seeds) & excluded)
    if overlap:
        errors.append(f"complete-registry overlap: {overlap}")

    if errors:
        write_new_fsynced(
            output / "invalid-retired.json",
            canonical_json({
                "block_id": BLOCK,
                "errors": errors,
                "quarantine_sha256": sha256(quarantine_path.read_bytes()),
                "status": (
                    "NONEXPERIMENTAL_FIXTURE_INVALID"
                    if fixture else
                    "INVALID_RETIRED"
                ),
            }),
        )
        raise ValueError("; ".join(errors))

    rows = []
    for game, (seed, cell) in enumerate(zip(seeds, CELLS, strict=True), 1):
        pest_seat, terror_seat, starter, play_draw = cell
        rows.append({
            "protocol_id": PROTOCOL,
            "block_id": BLOCK,
            "game_number": game,
            "seed_decimal": seed,
            "seed_hex": seed_hex(seed),
            "pest_seat": pest_seat,
            "terror_seat": terror_seat,
            "starting_deck": starter,
            "pest_play_draw": play_draw,
            "pest_main_sha256": PEST_MAIN,
            "terror_main_sha256": TERROR_MAIN,
            "terror_sideboard_sha256": TERROR_SIDEBOARD,
            "terror_complete75_sha256": TERROR_75,
            "qualified_runner": QUALIFIED_RUNNER,
        })

    vector = ("\n".join(str(seed) for seed in seeds) + "\n").encode()
    assignments = csv_bytes(rows)
    manifest = {
        "artifact_hashes": {
            "assignment_csv_sha256": sha256(assignments),
            "ordered_vector_sha256": sha256(vector),
            "quarantined_vector_sha256": sha256(quarantine_path.read_bytes()),
        },
        "assignment_counts": {
            "games": 12,
            "pest_draw": 6,
            "pest_play": 6,
            "pest_seat_one": 6,
            "pest_seat_zero": 6,
            "joint_cells": {
                "SEAT_ZERO_PLAY": 3,
                "SEAT_ZERO_DRAW": 3,
                "SEAT_ONE_PLAY": 3,
                "SEAT_ONE_DRAW": 3,
            },
        },
        "block_id": BLOCK,
        "collision_audit": {
            "excluded_seed_count": COMPLETE_EXCLUSION_COUNT,
            "new_seed_count": 12,
            "new_unique_count": 12,
            "overlap_count": 0,
            "result": "PASS",
        },
        "deck_hashes": {
            "pest_main": PEST_MAIN,
            "terror_main": TERROR_MAIN,
            "terror_sideboard": TERROR_SIDEBOARD,
            "terror_complete75": TERROR_75,
        },
        "environment": {
            "machine": platform.machine(),
            "os": platform.platform(),
            "python": platform.python_version(),
        },
        "exclusion_audit": exclusion_audit,
        "freeze_source": {
            "commit": freeze_commit,
            "tree": freeze_tree,
        },
        "generation": {
            "generator_sha256": sha256(Path(__file__).read_bytes()),
            "method": "one os.urandom(96) call",
            "regeneration_permitted": False,
        },
        "official_counters": {
            "actions_submitted": 0,
            "artifacts_with_outcomes": 0,
            "games_initialized": 0,
            "outcome_exposure": 0,
        },
        "prior_smoke": {
            "ordered_vector_sha256": SMOKE_VECTOR_SHA256,
            "seed_count": len(smoke),
            "result": "PEST_CONTROL_4_0",
        },
        "protocol_id": PROTOCOL,
        "qualified_runner": QUALIFIED_RUNNER,
        "runner_state": "DISABLED",
        "status": "NONEXPERIMENTAL_FIXTURE" if fixture else "FROZEN_UNEXECUTED",
    }

    artifacts = {
        "ordered-seeds.txt": vector,
        "assignments.csv": assignments,
        "freeze-manifest.json": canonical_json(manifest),
    }
    for name, data in artifacts.items():
        write_new_fsynced(output / name, data)

    checksums = {name: sha256(data) for name, data in artifacts.items()}
    checksums[quarantine_path.name] = sha256(quarantine_path.read_bytes())
    write_new_fsynced(
        output / "artifacts.sha256",
        (
            "\n".join(
                f"{digest}  {name}"
                for name, digest in sorted(checksums.items())
            )
            + "\n"
        ).encode(),
    )

    return {
        "assignment_csv_sha256": sha256(assignments),
        "block_id": BLOCK,
        "complete_exclusion_count": len(excluded),
        "manifest_sha256": sha256(artifacts["freeze-manifest.json"]),
        "ordered_vector_sha256": sha256(vector),
        "seed_count": 12,
        "status": manifest["status"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--preflight", action="store_true")
    modes.add_argument("--validate-fixture", action="store_true")
    modes.add_argument("--generate", action="store_true")
    parser.add_argument("--prior-smoke-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--freeze-commit", default="0" * 40)
    parser.add_argument("--freeze-tree", default="0" * 40)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[3]
    excluded, smoke, audit = complete_exclusion(root, args.prior_smoke_dir)

    if args.preflight:
        result = {
            "entropy_requested": False,
            "excluded_values": len(excluded),
            "prior_smoke_seed_count": len(smoke),
            "source_audit": audit,
            "status": "READY",
        }
    else:
        if args.output_dir is None:
            parser.error("--output-dir is required")

        if args.generate:
            if (root / FROZEN_PROVENANCE_PATH).exists():
                raise ValueError(
                    "official Mono-Blue Terror replication vector is already frozen; "
                    "regeneration prohibited"
                )
            if os.environ.get("PEST_TERROR_REPLICATION_FREEZE_AUTH") != PRODUCTION_AUTH:
                raise ValueError("automatic replication freeze authorization mismatch")
            if os.environ.get("GITHUB_EVENT_NAME") != "push":
                raise ValueError("replication entropy is restricted to an authorized push")
            if (
                os.environ.get("GITHUB_REF") != "refs/heads/main"
                or os.environ.get("GITHUB_RUN_ATTEMPT") != "1"
            ):
                raise ValueError("replication production freeze requires main push attempt 1")
            if os.environ.get("GITHUB_SHA") != args.freeze_commit:
                raise ValueError("freeze commit must equal triggering GITHUB_SHA")
            if any(
                len(value) != 40
                or any(char not in "0123456789abcdef" for char in value)
                for value in (args.freeze_commit, args.freeze_tree)
            ):
                raise ValueError("freeze commit/tree must be lowercase 40-digit hashes")
            if args.output_dir.exists() and any(args.output_dir.iterdir()):
                raise ValueError("output directory must be absent or empty")
            entropy = os.urandom(96)
        else:
            entropy = hashlib.shake_256(
                b"NONEXPERIMENTAL_TIER_ONE_MONO_BLUE_TERROR_REPLICATION_12_V1"
            ).digest(96)

        result = generate(
            root=root,
            prior_smoke_dir=args.prior_smoke_dir,
            output=args.output_dir,
            entropy=entropy,
            fixture=args.validate_fixture,
            freeze_commit=args.freeze_commit,
            freeze_tree=args.freeze_tree,
        )

    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
