#!/usr/bin/env python3
"""One-shot, artifact-only Pest Control V2 qualification seed freeze.

Preflight and fixture validation request no entropy. Production generation makes exactly one
``os.urandom(1200)`` call, quarantines the complete draw before validation, and never retries,
replaces, or rerolls a value. All outputs stay under the caller-provided artifact directory.
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


PROTOCOL = "PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1"
BLOCK = f"{PROTOCOL}_V2_QUALIFICATION_50"
QUALIFIED_RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
READINESS_COMMIT = "5cd65def599b1017dae29f988c8c9ff211cac258"
ACCEPTED_CALIBRATION_COMMIT = "c4f02ff2c19bd256a356e1f97a18d762484ecb7d"
ACCEPTED_CALIBRATION_RUN = 35525262024
ACCEPTED_CALIBRATION_ARTIFACT = 10610310479
ACCEPTED_CALIBRATION_ARCHIVE = "577ecf33bbbd6fa2b9967867228b6eabb81b6b2c5590f2c0670f5d3536f80a2b"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
PEST_SIDEBOARD = "c1910468c228662b21647eb7ca8481cd11691906e56e0a37990e00f22886368c"
PEST_75 = "2927737eb084657cda58fd1877db933037c383273062f0bd209c7ff3046c1cf5"
RED_MAIN = "38c7850d1b9b070637502cedfffc6116d3504a525db8b51223505d7935134258"
RED_SIDEBOARD = "d0aab592e6c82ad019dba0eadc028db75ef5cf13c9b3e4e0531a04d513bfc77a"
RED_75 = "e9ff7ecbdbc8f41ebe526fe8fee4f87f706e0630491f9d78121677922fbd647d"
REGISTRY_SHA256 = "fcc09ba4db85c0b157de7301b5506a1c9cd29c1d85ccb3f6482d6cfe9260a246"
OFFICIAL_SEEDS_SHA256 = "41d9cf707fba5548d14040d7e3a4a26bd8f9f5bbacf1b12d7651423dcabaff0a"
READINESS_SOURCE_SHA256 = "23bbb7c70394da6136933b79991f45471a6a09535e0fee1349faf0c3772b72fe"
READINESS_DOC_SHA256 = "e95e2a12d98a4debb18a5d0737e70a899e0fdcc4c6153b75bcf3670b2657e9de"
ACK = "GENERATE_PEST_V2_QUALIFICATION_50_NO_GAMEPLAY"

REJECTED_V2_CANDIDATE = (
    352421150441762375, -7897966070063678192, 5918577377114013031,
    -676340927639613933, 7240270641543801242, -6824674089531578284,
    5070049516395972099, -8923988165019673829, -4759076379260409612,
    2895538220561058244,
)
SMOKE_SEED = 0x5045_5354_5632_0001
ASSIGNMENT_HEADER = (
    "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
    "mono_red_seat", "starting_player", "pest_play_draw", "pest_main_sha256",
    "pest_sideboard_sha256", "pest_complete75_sha256", "mono_red_main_sha256",
    "mono_red_sideboard_sha256", "mono_red_complete75_sha256", "qualified_runner",
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode()


def csv_bytes(header: tuple[str, ...], rows: list[dict[str, object]]) -> bytes:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=header, lineterminator="\n")
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


def source_paths(root: Path) -> dict[str, Path]:
    return {
        "registry": root / "docs/experiments/pest-control/matchup-block-a-seed-registry.csv",
        "official": root / "docs/experiments/pest-control/v2-official-execution/attempted-seeds.csv",
        "readiness_source": root / "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlV2QualificationReadiness.kt",
        "readiness_doc": root / "docs/experiments/pest-control/v2-matchup-qualification-readiness.md",
    }


def read_seed_column(data: bytes, expected_header: tuple[str, ...] | None = None) -> list[int]:
    text = data.decode("utf-8")
    if "\r" in text or not text.endswith("\n"):
        raise ValueError("seed source is not canonical LF text")
    reader = csv.DictReader(io.StringIO(text, newline=""))
    if expected_header is not None and tuple(reader.fieldnames or ()) != expected_header:
        raise ValueError("seed source header mismatch")
    if "seed_decimal" not in (reader.fieldnames or []):
        raise ValueError("seed source lacks seed_decimal")
    return [int(row["seed_decimal"]) for row in reader]


def audit_sources(root: Path) -> tuple[set[int], dict[str, object]]:
    paths = source_paths(root)
    expected_hashes = {
        "registry": REGISTRY_SHA256,
        "official": OFFICIAL_SEEDS_SHA256,
        "readiness_source": READINESS_SOURCE_SHA256,
        "readiness_doc": READINESS_DOC_SHA256,
    }
    actual_hashes = {name: sha256(path.read_bytes()) for name, path in paths.items()}
    if actual_hashes != expected_hashes:
        raise ValueError(f"pinned source hash mismatch: expected={expected_hashes} actual={actual_hashes}")
    registry = read_seed_column(
        paths["registry"].read_bytes(),
        ("category", "identity", "position", "seed_decimal", "seed_hex", "source_path", "disposition"),
    )
    official = read_seed_column(paths["official"].read_bytes(), ("game_number", "seed_decimal"))
    if len(registry) != 463 or len(set(registry)) != 463:
        raise ValueError("registry must contain exactly 463 unique values")
    if len(official) != 10 or len(set(official)) != 10:
        raise ValueError("accepted calibration source must contain exactly ten unique seeds")
    groups = {
        "registry": set(registry), "accepted_v2_calibration": set(official),
        "rejected_v2_candidate": set(REJECTED_V2_CANDIDATE), "v2_smoke_fixture": {SMOKE_SEED},
    }
    names = tuple(groups)
    for index, left in enumerate(names):
        for right in names[index + 1:]:
            overlap = groups[left] & groups[right]
            if overlap:
                raise ValueError(f"exclusion sources overlap: {left}/{right}: {sorted(overlap)}")
    excluded = set().union(*groups.values())
    if len(excluded) != 484:
        raise ValueError("complete exclusion set must contain exactly 484 values")
    return excluded, {
        "accepted_v2_calibration_count": 10,
        "complete_unique_count": len(excluded),
        "pinned_sha256": actual_hashes,
        "registry_count": len(registry),
        "rejected_v2_candidate_count": 10,
        "v2_smoke_fixture_count": 1,
    }


def decode_entropy(entropy: bytes) -> tuple[list[int], list[str]]:
    if len(entropy) != 1200:
        raise ValueError("entropy draw must be exactly 1200 bytes")
    seeds = [int.from_bytes(entropy[offset:offset + 8], "big", signed=True) for offset in range(0, 400, 8)]
    tags = [entropy[400 + offset:400 + offset + 16].hex() for offset in range(0, 800, 16)]
    return seeds, tags


def assignment_rows(seeds: list[int], tags: list[str]) -> list[dict[str, object]]:
    shard_cells = (
        [("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY")] * 7
        + [("SEAT_ZERO", "SEAT_ONE", "MONO_RED_MADNESS", "DRAW")] * 6
        + [("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY")] * 6
        + [("SEAT_ONE", "SEAT_ZERO", "MONO_RED_MADNESS", "DRAW")] * 6,
        [("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY")] * 6
        + [("SEAT_ZERO", "SEAT_ONE", "MONO_RED_MADNESS", "DRAW")] * 6
        + [("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY")] * 6
        + [("SEAT_ONE", "SEAT_ZERO", "MONO_RED_MADNESS", "DRAW")] * 7,
    )
    cells: list[tuple[str, str, str, str]] = []
    for shard in range(2):
        start = shard * 25
        order = sorted(range(25), key=lambda index: tags[start + index])
        cells.extend(shard_cells[shard][index] for index in order)
    rows = []
    for game, (seed, cell) in enumerate(zip(seeds, cells, strict=True), 1):
        pest_seat, red_seat, starter, play_draw = cell
        rows.append({
            "protocol_id": PROTOCOL, "block_id": BLOCK, "game_number": game,
            "seed_decimal": seed, "seed_hex": seed_hex(seed), "pest_seat": pest_seat,
            "mono_red_seat": red_seat, "starting_player": starter, "pest_play_draw": play_draw,
            "pest_main_sha256": PEST_MAIN, "pest_sideboard_sha256": PEST_SIDEBOARD,
            "pest_complete75_sha256": PEST_75, "mono_red_main_sha256": RED_MAIN,
            "mono_red_sideboard_sha256": RED_SIDEBOARD, "mono_red_complete75_sha256": RED_75,
            "qualified_runner": QUALIFIED_RUNNER,
        })
    return rows


def generate(root: Path, output: Path, entropy: bytes, fixture: bool, freeze_commit: str, freeze_tree: str) -> dict[str, object]:
    excluded, exclusion_audit = audit_sources(root)
    if output.exists() and any(output.iterdir()):
        raise ValueError("output directory must be absent or empty")
    output.mkdir(parents=True, exist_ok=True)
    seeds, tags = decode_entropy(entropy)
    status = "NONEXPERIMENTAL_FIXTURE" if fixture else "QUARANTINED_UNATTEMPTED"
    quarantine = {
        "block_id": BLOCK, "entropy_byte_count": 1200, "entropy_sha256": sha256(entropy),
        "generation_method": "one 1200-byte draw; first 400 bytes are 50 signed big-endian 64-bit seeds in unchanged draw order; remaining 800 bytes are 50 independent 128-bit within-shard assignment sort tags",
        "permutation_tags_hex": tags, "seeds_decimal": seeds,
        "seeds_hex": [seed_hex(seed) for seed in seeds], "status": status,
    }
    if not fixture:
        quarantine["drawn_at_utc"] = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    quarantine_path = output / "quarantined-vector.json"
    write_new_fsynced(quarantine_path, canonical_json(quarantine))
    errors = []
    if any(seed == 0 for seed in seeds): errors.append("zero seed")
    if len(set(seeds)) != 50: errors.append("duplicate seed")
    if len(set(tags)) != 50: errors.append("duplicate permutation tag")
    overlap = sorted(set(seeds) & excluded)
    if overlap: errors.append(f"complete-registry overlap: {overlap}")
    if errors:
        write_new_fsynced(output / "invalid-retired.json", canonical_json({
            "block_id": BLOCK, "errors": errors, "quarantine_sha256": sha256(quarantine_path.read_bytes()),
            "status": "NONEXPERIMENTAL_FIXTURE_INVALID" if fixture else "INVALID_RETIRED",
        }))
        raise ValueError("; ".join(errors))
    rows = assignment_rows(seeds, tags)
    vector = ("\n".join(str(seed) for seed in seeds) + "\n").encode()
    assignments = csv_bytes(ASSIGNMENT_HEADER, rows)
    shards = []
    for shard_id, (first, last) in enumerate(((1, 25), (26, 50)), 1):
        shard_rows = rows[first - 1:last]
        shard_csv = csv_bytes(ASSIGNMENT_HEADER, shard_rows)
        counts = {name: sum(
            row["pest_seat"] == seat and row["pest_play_draw"] == play_draw for row in shard_rows
        ) for name, seat, play_draw in (
            ("SEAT_ZERO_PLAY", "SEAT_ZERO", "PLAY"), ("SEAT_ZERO_DRAW", "SEAT_ZERO", "DRAW"),
            ("SEAT_ONE_PLAY", "SEAT_ONE", "PLAY"), ("SEAT_ONE_DRAW", "SEAT_ONE", "DRAW"),
        )}
        shards.append({
            "assignment_csv_slice_sha256": sha256(shard_csv), "expected_games": 25,
            "first_game_number": first, "joint_cells": counts, "last_game_number": last,
            "shard_id": f"SHARD_{shard_id:02d}_OF_02", "timeout_minutes": 240,
        })
    shard_data = canonical_json({
        "block_id": BLOCK, "global_assignment_csv_sha256": sha256(assignments),
        "global_order_preserved": True, "shards": shards,
    })
    artifact_status = "NONEXPERIMENTAL_FIXTURE" if fixture else "FROZEN_UNEXECUTED"
    manifest = {
        "accepted_calibration": {
            "artifact_archive_sha256": ACCEPTED_CALIBRATION_ARCHIVE,
            "artifact_id": ACCEPTED_CALIBRATION_ARTIFACT, "commit": ACCEPTED_CALIBRATION_COMMIT,
            "disposition": "ACCEPTED_CALIBRATION", "run_id": ACCEPTED_CALIBRATION_RUN,
        },
        "artifact_hashes": {
            "assignment_csv_sha256": sha256(assignments), "ordered_vector_sha256": sha256(vector),
            "quarantined_vector_sha256": sha256(quarantine_path.read_bytes()),
            "shard_allocation_sha256": sha256(shard_data),
        },
        "assignment_counts": {
            "joint_cells": {"SEAT_ZERO_PLAY": 13, "SEAT_ZERO_DRAW": 12, "SEAT_ONE_PLAY": 12, "SEAT_ONE_DRAW": 13},
            "pest_draw": 25, "pest_play": 25, "pest_seat_one": 25, "pest_seat_zero": 25,
        },
        "block_id": BLOCK,
        "collision_audit": {"new_seed_count": 50, "new_unique_count": 50, "overlap_count": 0, "result": "PASS"},
        "deck_hashes": {
            "mono_red_complete75": RED_75, "mono_red_main": RED_MAIN, "mono_red_sideboard": RED_SIDEBOARD,
            "pest_complete75": PEST_75, "pest_main": PEST_MAIN, "pest_sideboard": PEST_SIDEBOARD,
        },
        "environment": {"machine": platform.machine(), "os": platform.platform(), "python": platform.python_version()},
        "exclusion_audit": exclusion_audit,
        "freeze_source": {"commit": freeze_commit, "tree": freeze_tree},
        "generation": {"generator_sha256": sha256(Path(__file__).read_bytes()), "regeneration_permitted": False},
        "protocol_id": PROTOCOL, "qualified_runner": QUALIFIED_RUNNER,
        "readiness": {"commit": READINESS_COMMIT, "games": 50, "runner_state": "DISABLED", "shards": [25, 25]},
        "shards": shards, "status": artifact_status,
    }
    manifest_data = canonical_json(manifest)
    artifacts = {
        "ordered-seeds.txt": vector, "assignments.csv": assignments,
        "shard-allocation.json": shard_data, "freeze-manifest.json": manifest_data,
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
        "complete_exclusion_count": len(excluded), "manifest_sha256": sha256(manifest_data),
        "ordered_vector_sha256": sha256(vector), "seed_count": 50, "status": artifact_status,
    }


def preflight(root: Path) -> dict[str, object]:
    excluded, audit = audit_sources(root)
    return {"entropy_requested": False, "excluded_values": len(excluded), "source_audit": audit, "status": "READY"}


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
        result = preflight(root)
    else:
        if args.output_dir is None:
            parser.error("--output-dir is required")
        if args.generate:
            if os.environ.get("PEST_V2_QUALIFICATION_FREEZE_ACK") != ACK:
                raise ValueError("exact production acknowledgement is required")
            if os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
                raise ValueError("production entropy is restricted to workflow_dispatch")
            if os.environ.get("GITHUB_REF") != "refs/heads/main" or os.environ.get("GITHUB_RUN_ATTEMPT") != "1":
                raise ValueError("production entropy requires main and workflow attempt 1")
            if any(len(value) != 40 or any(char not in "0123456789abcdef" for char in value)
                   for value in (args.freeze_commit, args.freeze_tree)):
                raise ValueError("freeze commit and tree must be lowercase 40-digit hashes")
            # Repeat the source and destination guards immediately before the sole entropy request.
            # The workflow preflight is intentionally not the only barrier.
            audit_sources(root)
            if args.output_dir.exists() and any(args.output_dir.iterdir()):
                raise ValueError("output directory must be absent or empty")
            entropy = os.urandom(1200)  # The sole production entropy request.
        else:
            entropy = hashlib.shake_256(b"NONEXPERIMENTAL_PEST_V2_QUALIFICATION_FREEZE_FIXTURE_V1").digest(1200)
        result = generate(root, args.output_dir, entropy, args.validate_fixture, args.freeze_commit, args.freeze_tree)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
