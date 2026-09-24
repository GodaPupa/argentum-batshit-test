#!/usr/bin/env python3
"""One-shot four-game Pest Control vs Monster Tron smoke-vector freeze.

Pull-request validation is entropy-free. The separately authorized production path may make exactly
one os.urandom(32) call, quarantines the complete four-seed draw before validation, and has no reroll,
replacement, partial-salvage, or regeneration path. No game initializer or outcome API is reachable.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
import runpy
from datetime import datetime, timezone
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
PRODUCTION_AUTH = "AUTOMATIC_SINGLE_MONSTER_TRON_SMOKE_FREEZE_NO_GAMEPLAY"
AUTH_PATH = "docs/experiments/pest-control/tier-one-monster-tron-auto-freeze-authorization.json"
AUTH_SHA256 = "3934f18558cba9797bd888e6e5a194b72edb25e01e34c16b3cc900ce88a0621a"
FROZEN_PROVENANCE_PATH = (
    "docs/experiments/pest-control/tier-one-monster-tron-smoke-freeze-provenance.json"
)

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


def generate_bundle(
    root: Path,
    prior_terror_smoke_dir: Path,
    prior_terror_replication_dir: Path,
    output: Path,
    entropy: bytes,
    fixture: bool,
    freeze_commit: str,
    freeze_tree: str,
) -> dict[str, object]:
    excluded, exclusion_audit = complete_exclusion(
        root, prior_terror_smoke_dir, prior_terror_replication_dir
    )
    if output.exists() and any(output.iterdir()):
        raise ValueError("output directory must be absent or empty")
    if len(entropy) != 32:
        raise ValueError("entropy draw must be exactly 32 bytes")

    output.mkdir(parents=True, exist_ok=True)
    seeds = [
        int.from_bytes(entropy[offset:offset + 8], "big", signed=True)
        for offset in range(0, 32, 8)
    ]

    quarantine = {
        "block_id": BLOCK,
        "entropy_byte_count": 32,
        "entropy_sha256": sha256(entropy),
        "generation_method": "one os.urandom(32) call; four signed big-endian 64-bit seeds in unchanged draw order",
        "seeds_decimal": seeds,
        "seeds_hex": [seed_hex(seed) for seed in seeds],
        "status": "NONEXPERIMENTAL_FIXTURE" if fixture else "QUARANTINED_UNATTEMPTED",
    }
    if not fixture:
        quarantine["drawn_at_utc"] = (
            datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
        )
    quarantine_path = output / "quarantined-vector.json"
    write_new_fsynced(quarantine_path, canonical_json(quarantine))

    errors: list[str] = []
    if any(seed == 0 for seed in seeds):
        errors.append("zero seed")
    if len(set(seeds)) != 4:
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
                "status": "NONEXPERIMENTAL_FIXTURE_INVALID" if fixture else "INVALID_RETIRED",
            }),
        )
        raise ValueError("; ".join(errors))

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
        "schema": "pest-control-tier-one-monster-tron-smoke-freeze@v1",
        "protocol_id": PROTOCOL,
        "block_id": BLOCK,
        "status": "NONEXPERIMENTAL_FIXTURE" if fixture else "FROZEN_UNEXECUTED",
        "qualified_runner": QUALIFIED_RUNNER,
        "runner_state": "DISABLED",
        "deck_hashes": {"pest_main": PEST_MAIN, "monster_tron_main": MONSTER_TRON_MAIN},
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
        "freeze_source": {"commit": freeze_commit, "tree": freeze_tree},
        "generation": {
            "generator_sha256": sha256(Path(__file__).read_bytes()),
            "method": "deterministic fixture" if fixture else "one os.urandom(32) call",
            "regeneration_permitted": False,
        },
        "official_counters": {
            "games_authorized": 0,
            "games_initialized": 0,
            "actions_submitted": 0,
            "outcome_exposure": 0,
        },
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
        ("\n".join(f"{digest}  {name}" for name, digest in sorted(checksums.items())) + "\n").encode(),
    )

    return {
        "status": manifest["status"],
        "complete_exclusion_count": len(excluded),
        "seed_count": len(seeds),
        "ordered_vector_sha256": sha256(vector),
        "assignment_csv_sha256": sha256(assignments),
        "manifest_sha256": sha256(artifacts["freeze-manifest.json"]),
        "quarantined_vector_sha256": sha256(quarantine_path.read_bytes()),
    }


def verify_authorization(root: Path) -> None:
    path = root / AUTH_PATH
    data = path.read_bytes()
    if sha256(data) != AUTH_SHA256:
        raise ValueError("Monster Tron automatic freeze authorization bytes mismatch")
    record = json.loads(data)
    expected = {
        "schema": "pest-control-tier-one-monster-tron-auto-freeze-authorization@v1",
        "protocolId": PROTOCOL,
        "blockId": BLOCK,
        "authorization": PRODUCTION_AUTH,
        "productionEntropyCalls": 1,
        "productionEntropyBytes": 32,
        "officialGamesAuthorized": 0,
        "gamesInitialized": 0,
        "actionsSubmitted": 0,
        "outcomeExposure": 0,
    }
    if record != expected:
        raise ValueError("Monster Tron automatic freeze authorization semantics mismatch")


def main() -> None:
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--preflight", action="store_true")
    modes.add_argument("--validate-fixture", action="store_true")
    modes.add_argument("--generate", action="store_true")
    parser.add_argument("--prior-terror-smoke-dir", type=Path, required=True)
    parser.add_argument("--prior-terror-replication-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--freeze-commit", default="0" * 40)
    parser.add_argument("--freeze-tree", default="0" * 40)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[3]
    excluded, audit = complete_exclusion(
        root, args.prior_terror_smoke_dir, args.prior_terror_replication_dir
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
            parser.error("--output-dir is required")

        if args.generate:
            if (root / FROZEN_PROVENANCE_PATH).exists():
                raise ValueError("official Monster Tron smoke vector is already frozen; regeneration prohibited")
            verify_authorization(root)
            if os.environ.get("PEST_MONSTER_TRON_AUTO_FREEZE_AUTH") != PRODUCTION_AUTH:
                raise ValueError("automatic Monster Tron freeze authorization mismatch")
            if os.environ.get("GITHUB_EVENT_NAME") != "push":
                raise ValueError("Monster Tron production entropy is restricted to an authorized push")
            if os.environ.get("GITHUB_REF") != "refs/heads/main":
                raise ValueError("Monster Tron production freeze requires main")
            if os.environ.get("GITHUB_RUN_ATTEMPT") != "1":
                raise ValueError("Monster Tron production freeze requires workflow attempt 1")
            if os.environ.get("GITHUB_SHA") != args.freeze_commit:
                raise ValueError("freeze commit must equal triggering GITHUB_SHA")
            if any(
                len(value) != 40 or any(char not in "0123456789abcdef" for char in value)
                for value in (args.freeze_commit, args.freeze_tree)
            ):
                raise ValueError("freeze commit/tree must be lowercase 40-digit hashes")
            entropy = os.urandom(32)
            fixture = False
        else:
            entropy = hashlib.shake_256(
                b"NONEXPERIMENTAL_PEST_CONTROL_MONSTER_TRON_SMOKE_4_FREEZE_FIXTURE_V2"
            ).digest(32)
            fixture = True

        result = generate_bundle(
            root=root,
            prior_terror_smoke_dir=args.prior_terror_smoke_dir,
            prior_terror_replication_dir=args.prior_terror_replication_dir,
            output=args.output_dir,
            entropy=entropy,
            fixture=fixture,
            freeze_commit=args.freeze_commit,
            freeze_tree=args.freeze_tree,
        )

    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
