#!/usr/bin/env python3
"""One-shot, resumable Pest Control replacement-block seed freeze.

Preflight consumes no entropy. Generation makes exactly one os.urandom(1200) call when and only
when the quarantine file does not exist. It writes and fsyncs that draw before validation or any
derived artifact. A later invocation resumes solely from the quarantined bytes and never redraws.
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
BLOCK = f"{PROTOCOL}_REPLACEMENT_BLOCK_1"
BASELINE_COMMIT = "c52d5de4018e4dc6909d1197d941413df2d5ec51"
BASELINE_TREE = "688ae8e4df7f9622998ec53170272b949ccb38fb"
READINESS_BASELINE = "9af6106915e4b41ab1d09d451633b7e09ab64bb4"
GATE4_SOURCE = "d81ec35f0be74422315f9bb5bf8d69c395b2d872"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
PEST_SIDEBOARD = "c1910468c228662b21647eb7ca8481cd11691906e56e0a37990e00f22886368c"
PEST_75 = "2927737eb084657cda58fd1877db933037c383273062f0bd209c7ff3046c1cf5"
RED_MAIN = "38c7850d1b9b070637502cedfffc6116d3504a525db8b51223505d7935134258"
RED_SIDEBOARD = "d0aab592e6c82ad019dba0eadc028db75ef5cf13c9b3e4e0531a04d513bfc77a"
RED_75 = "e9ff7ecbdbc8f41ebe526fe8fee4f87f706e0630491f9d78121677922fbd647d"
INPUT_REGISTRY_SHA256 = "d47fb9a84d82e1196f3f990cfa8d8e6ce2766f42af345c6d2f71e1d33c871e0f"
BLOCK_A_ID = f"{PROTOCOL}_BLOCK_A"
BLOCK_A_VECTOR_SHA256 = "48459229c8e261022c37fa52915c382a7ab9b95405ce2124254ff82b57ecf135"

REGISTRY_HEADER = (
    "category", "identity", "position", "seed_decimal", "seed_hex", "source_path", "disposition",
)
ASSIGNMENT_HEADER = (
    "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
    "mono_red_seat", "starting_player", "pest_play_draw", "pest_main_sha256",
    "pest_sideboard_sha256", "pest_complete75_sha256", "mono_red_main_sha256",
    "mono_red_sideboard_sha256", "mono_red_complete75_sha256", "gate4_source_commit",
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode()


def seed_hex(seed: int) -> str:
    return f"0x{seed & ((1 << 64) - 1):016x}"


def csv_bytes(header: tuple[str, ...], rows: list[dict[str, object]]) -> bytes:
    out = io.StringIO(newline="")
    writer = csv.DictWriter(out, fieldnames=header, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return out.getvalue().encode()


def write_new_fsynced(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o444)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
    except BaseException:
        raise
    directory = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def write_or_verify(path: Path, data: bytes) -> None:
    if path.exists():
        if path.read_bytes() != data:
            raise ValueError(f"refusing to replace nonidentical artifact: {path}")
        return
    write_new_fsynced(path, data)


def paths(root: Path) -> dict[str, Path]:
    docs = root / "docs/experiments/pest-control"
    stem = "matchup-replacement-block-1"
    return {
        "registry": docs / "matchup-block-a-seed-registry.csv",
        "quarantine": docs / f"{stem}-quarantined-vector.json",
        "vector": docs / f"{stem}-ordered-seeds.txt",
        "assignments": root / "gym/src/test/resources/pest-control-v10-vs-mono-red-madness-soterx-2026-09-11-preboard-v1-replacement-block-1-seeds.csv",
        "shards": docs / f"{stem}-shard-allocation.json",
        "manifest": docs / f"{stem}-seed-freeze-manifest.json",
        "checksums": docs / f"{stem}-artifacts.sha256",
    }


def parse_registry(data: bytes) -> list[dict[str, str]]:
    text = data.decode("utf-8")
    if "\r" in text or not text.endswith("\n"):
        raise ValueError("registry is not canonical LF text")
    reader = csv.DictReader(io.StringIO(text, newline=""))
    if tuple(reader.fieldnames or ()) != REGISTRY_HEADER:
        raise ValueError("registry header mismatch")
    return list(reader)


def audit_input_registry(root: Path) -> tuple[bytes, list[dict[str, str]], set[int]]:
    current_registry = paths(root)["registry"].read_bytes()
    current_rows = parse_registry(current_registry)
    replacement_rows = [row for row in current_rows if row["identity"] == BLOCK]
    rows = [row for row in current_rows if row["identity"] != BLOCK]
    registry = csv_bytes(REGISTRY_HEADER, rows)
    if sha256(registry) != INPUT_REGISTRY_SHA256:
        raise ValueError("permanent registry is not the exact validated baseline plus optional replacement rows")
    seeds = [int(row["seed_decimal"]) for row in rows]
    if len(rows) != 413 or len(set(seeds)) != 413:
        raise ValueError("baseline registry must contain 413 unique values")
    block_a = [row for row in rows if row["identity"] == BLOCK_A_ID]
    if len(block_a) != 50 or any(row["disposition"] != "REJECTED_RETIRED" for row in block_a):
        raise ValueError("Block A is not exactly 50 separately rejected and retired rows")
    block_a_vector = root / "docs/experiments/pest-control/matchup-block-a-ordered-seeds.txt"
    if sha256(block_a_vector.read_bytes()) != BLOCK_A_VECTOR_SHA256:
        raise ValueError("preserved Block A vector changed")
    if replacement_rows and (len(replacement_rows) != 50 or
                             any(row["disposition"] != "FROZEN_UNEXECUTED" for row in replacement_rows)):
        raise ValueError("existing replacement registry rows are incomplete or malformed")
    registered = set(seeds) | {int(row["seed_decimal"]) for row in replacement_rows}
    discovered: set[int] = set()
    resources = root / "gym/src/test/resources"
    for source in sorted(resources.glob("pest-control-v10-*-seeds.csv")):
        if source == paths(root)["assignments"]:
            continue
        with source.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream)
            if not reader.fieldnames:
                raise ValueError(f"missing seed header: {source}")
            column = "seed_decimal" if "seed_decimal" in reader.fieldnames else "seed"
            for row in reader:
                seed = int(row[column])
                if seed in discovered:
                    raise ValueError(f"duplicate seed across source artifacts: {seed}")
                discovered.add(seed)
    if not discovered.issubset(registered):
        raise ValueError(f"source seed absent from permanent registry: {sorted(discovered - registered)}")
    return registry, rows, set(seeds)


def quarantine_draw(root: Path) -> dict[str, object]:
    quarantine = paths(root)["quarantine"]
    if quarantine.exists():
        return json.loads(quarantine.read_text(encoding="utf-8"))
    entropy = os.urandom(50 * 8 + 50 * 16)  # The sole OS cryptographic-source request.
    seeds = [int.from_bytes(entropy[offset:offset + 8], "big", signed=True) for offset in range(0, 400, 8)]
    tags = [entropy[400 + offset:400 + offset + 16].hex() for offset in range(0, 800, 16)]
    record = {
        "block_id": BLOCK,
        "drawn_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "entropy_byte_count": len(entropy),
        "entropy_sha256": sha256(entropy),
        "generation_method": "one os.urandom(1200) call; first 400 bytes are 50 signed big-endian 64-bit seeds in unchanged draw order; remaining 800 bytes are 50 independent 128-bit assignment-permutation tags",
        "permutation_tags_hex": tags,
        "seeds_decimal": seeds,
        "seeds_hex": [seed_hex(seed) for seed in seeds],
        "status": "QUARANTINED_UNATTEMPTED",
    }
    write_new_fsynced(quarantine, canonical_json(record))
    return record


def assignments(seeds: list[int], tags: list[str]) -> list[dict[str, object]]:
    # Both shards have the minimal possible 6/6/6/7 joint-cell spread. Complementary cells sum to
    # the frozen global order ZP/ZD/OP/OD = 13/12/12/13.
    shard_cells = (
        [
            ("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY"),
        ] * 7 + [
            ("SEAT_ZERO", "SEAT_ONE", "MONO_RED_MADNESS", "DRAW"),
        ] * 6 + [
            ("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY"),
        ] * 6 + [
            ("SEAT_ONE", "SEAT_ZERO", "MONO_RED_MADNESS", "DRAW"),
        ] * 6,
        [
            ("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY"),
        ] * 6 + [
            ("SEAT_ZERO", "SEAT_ONE", "MONO_RED_MADNESS", "DRAW"),
        ] * 6 + [
            ("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY"),
        ] * 6 + [
            ("SEAT_ONE", "SEAT_ZERO", "MONO_RED_MADNESS", "DRAW"),
        ] * 7,
    )
    ordered_cells: list[tuple[str, str, str, str]] = []
    for shard_index in range(2):
        start = shard_index * 25
        order = sorted(range(25), key=lambda index: tags[start + index])
        ordered_cells.extend(shard_cells[shard_index][index] for index in order)
    rows = []
    for game, (seed, cell) in enumerate(zip(seeds, ordered_cells, strict=True), 1):
        pest_seat, red_seat, starter, play_draw = cell
        rows.append({
            "protocol_id": PROTOCOL, "block_id": BLOCK, "game_number": game,
            "seed_decimal": seed, "seed_hex": seed_hex(seed), "pest_seat": pest_seat,
            "mono_red_seat": red_seat, "starting_player": starter, "pest_play_draw": play_draw,
            "pest_main_sha256": PEST_MAIN, "pest_sideboard_sha256": PEST_SIDEBOARD,
            "pest_complete75_sha256": PEST_75, "mono_red_main_sha256": RED_MAIN,
            "mono_red_sideboard_sha256": RED_SIDEBOARD, "mono_red_complete75_sha256": RED_75,
            "gate4_source_commit": GATE4_SOURCE,
        })
    return rows


def kotlin_assignment_bytes(rows: list[dict[str, object]]) -> bytes:
    names = (
        ("protocolId", "protocol_id"), ("blockId", "block_id"), ("gameNumber", "game_number"),
        ("seedDecimal", "seed_decimal"), ("seedHex", "seed_hex"), ("pestSeat", "pest_seat"),
        ("monoRedSeat", "mono_red_seat"), ("startingPlayer", "starting_player"),
        ("pestPlayDraw", "pest_play_draw"), ("pestMainSha256", "pest_main_sha256"),
        ("pestSideboardSha256", "pest_sideboard_sha256"),
        ("pestComplete75Sha256", "pest_complete75_sha256"),
        ("monoRedMainSha256", "mono_red_main_sha256"),
        ("monoRedSideboardSha256", "mono_red_sideboard_sha256"),
        ("monoRedComplete75Sha256", "mono_red_complete75_sha256"),
        ("gate4SourceCommit", "gate4_source_commit"),
    )
    encoded = [{json_name: row[csv_name] for json_name, csv_name in names} for row in rows]
    return (json.dumps(encoded, separators=(",", ":"), ensure_ascii=False) + "\n").encode()


def freeze(root: Path) -> dict[str, object]:
    registry_input, registry_rows, excluded = audit_input_registry(root)
    quarantine = quarantine_draw(root)
    seeds = [int(seed) for seed in quarantine["seeds_decimal"]]
    tags = [str(tag) for tag in quarantine["permutation_tags_hex"]]
    errors = []
    if quarantine.get("block_id") != BLOCK or quarantine.get("status") != "QUARANTINED_UNATTEMPTED":
        errors.append("quarantine identity or status mismatch")
    if quarantine.get("entropy_byte_count") != 1200:
        errors.append("quarantine entropy length mismatch")
    if len(seeds) != 50 or len(tags) != 50: errors.append("wrong quarantine cardinality")
    if any(seed == 0 for seed in seeds): errors.append("zero seed")
    if len(set(seeds)) != 50: errors.append("duplicate seed")
    if len(set(tags)) != 50 or any(len(tag) != 32 or any(c not in "0123456789abcdef" for c in tag) for tag in tags):
        errors.append("invalid permutation tags")
    if quarantine.get("seeds_hex") != [seed_hex(seed) for seed in seeds]:
        errors.append("quarantine seed encoding mismatch")
    overlap = sorted(set(seeds) & excluded)
    if overlap: errors.append(f"registry overlap: {overlap}")
    if errors:
        raise ValueError("quarantined vector is INVALID_RETIRED: " + "; ".join(errors))

    rows = assignments(seeds, tags)
    vector = ("\n".join(str(seed) for seed in seeds) + "\n").encode()
    assignment_csv = csv_bytes(ASSIGNMENT_HEADER, rows)
    registry_additions = [{
        "category": "MATCHUP_VECTOR", "identity": BLOCK, "position": index,
        "seed_decimal": seed, "seed_hex": seed_hex(seed),
        "source_path": str(paths(root)["assignments"].relative_to(root)),
        "disposition": "FROZEN_UNEXECUTED",
    } for index, seed in enumerate(seeds, 1)]
    registry_output = csv_bytes(REGISTRY_HEADER, registry_rows + registry_additions)

    shard_records = []
    for shard_index, (first, last) in enumerate(((1, 25), (26, 50)), 1):
        shard_rows = rows[first - 1:last]
        slice_csv = csv_bytes(ASSIGNMENT_HEADER, shard_rows)
        cells = {name: sum(1 for row in shard_rows if f'{row["pest_seat"]}_{row["pest_play_draw"]}' == name)
                 for name in ("SEAT_ZERO_PLAY", "SEAT_ZERO_DRAW", "SEAT_ONE_PLAY", "SEAT_ONE_DRAW")}
        shard_records.append({
            "assignment_count": 25,
            "assignment_csv_slice_sha256": sha256(slice_csv),
            "assignment_sha256": sha256(kotlin_assignment_bytes(shard_rows)),
            "first_game_number": first,
            "joint_cells": cells,
            "last_game_number": last,
            "pest_draw": sum(row["pest_play_draw"] == "DRAW" for row in shard_rows),
            "pest_play": sum(row["pest_play_draw"] == "PLAY" for row in shard_rows),
            "pest_seat_one": sum(row["pest_seat"] == "SEAT_ONE" for row in shard_rows),
            "pest_seat_zero": sum(row["pest_seat"] == "SEAT_ZERO" for row in shard_rows),
            "shard_id": f"SHARD_{shard_index:02d}_OF_02",
            "timeout_minutes": 240,
        })
    shard_data = canonical_json({
        "block_id": BLOCK, "global_assignment_csv_sha256": sha256(assignment_csv),
        "global_order_preserved": True, "shards": shard_records,
    })
    quarantine_bytes = paths(root)["quarantine"].read_bytes()
    manifest = {
        "artifact_hashes": {
            "assignment_csv_sha256": sha256(assignment_csv),
            "ordered_vector_sha256": sha256(vector),
            "quarantined_vector_sha256": sha256(quarantine_bytes),
            "seed_registry_input_sha256": sha256(registry_input),
            "seed_registry_updated_sha256": sha256(registry_output),
            "shard_allocation_sha256": sha256(shard_data),
        },
        "assignment_counts": {
            "pest_draw": 25, "pest_play": 25, "pest_seat_one": 25, "pest_seat_zero": 25,
            "joint_cells": {"SEAT_ZERO_PLAY": 13, "SEAT_ZERO_DRAW": 12, "SEAT_ONE_PLAY": 12, "SEAT_ONE_DRAW": 13},
        },
        "block_id": BLOCK,
        "collision_audit": {
            "complete_registry_exclusion_count": len(excluded), "new_seed_count": 50,
            "new_unique_count": 50, "overlap_count": 0, "result": "PASS",
        },
        "deck_hashes": {
            "pest_main": PEST_MAIN, "pest_sideboard": PEST_SIDEBOARD, "pest_complete75": PEST_75,
            "mono_red_main": RED_MAIN, "mono_red_sideboard": RED_SIDEBOARD, "mono_red_complete75": RED_75,
        },
        "environment": {"machine": platform.machine(), "os": platform.platform(), "python": platform.python_version()},
        "freeze_source": {"commit": BASELINE_COMMIT, "tree": BASELINE_TREE},
        "gate4_source_commit": GATE4_SOURCE,
        "generation": {
            "drawn_at_utc": quarantine["drawn_at_utc"], "entropy_sha256": quarantine["entropy_sha256"],
            "generator_sha256": sha256(Path(__file__).read_bytes()),
            "method": quarantine["generation_method"], "regeneration_permitted": False,
        },
        "protocol_id": PROTOCOL,
        "readiness": {
            "baseline_commit": READINESS_BASELINE, "implementation_commit": BASELINE_COMMIT,
            "implementation_tree": BASELINE_TREE, "logical_block_games": 50,
            "readiness_document_sha256": sha256((root / "docs/experiments/pest-control/matchup-replacement-block-execution-readiness.md").read_bytes()),
            "runner_guard": "PestControlReplacementShardRunnerGuard", "runner_state": "DISABLED",
            "runner_source_sha256": sha256((root / "gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlMatchupSharding.kt").read_bytes()),
            "plan_schema": "pest-control-preboard-sharded-plan@v1",
            "shard_count": 2, "shard_games": [25, 25], "shard_timeout_minutes": 240,
        },
        "registry": {
            "block_a_identity_preserved": BLOCK_A_ID, "block_a_seed_count": 50,
            "block_a_disposition": "REJECTED_RETIRED", "new_disposition": "FROZEN_UNEXECUTED",
            "updated_row_count": 463,
        },
        "shards": shard_records,
        "status": "FROZEN_UNEXECUTED",
    }
    manifest_data = canonical_json(manifest)
    artifact_data = {
        paths(root)["quarantine"]: quarantine_bytes,
        paths(root)["vector"]: vector,
        paths(root)["assignments"]: assignment_csv,
        paths(root)["registry"]: registry_output,
        paths(root)["shards"]: shard_data,
        paths(root)["manifest"]: manifest_data,
    }
    checksum_lines = [f"{sha256(data)}  {path.relative_to(root)}" for path, data in artifact_data.items()]
    checksums = ("\n".join(sorted(checksum_lines)) + "\n").encode()
    for path, data in artifact_data.items():
        if path == paths(root)["registry"]:
            current = path.read_bytes()
            if current not in (registry_input, registry_output):
                raise ValueError("registry changed during freeze")
            if current == registry_input:
                path.chmod(0o644)
                path.write_bytes(data)
        else:
            write_or_verify(path, data)
    write_or_verify(paths(root)["checksums"], checksums)
    return {
        "assignment_csv_sha256": sha256(assignment_csv), "block_id": BLOCK,
        "manifest_sha256": sha256(manifest_data), "ordered_vector_sha256": sha256(vector),
        "quarantine_sha256": sha256(quarantine_bytes), "registry_sha256": sha256(registry_output),
        "seed_count": 50, "shard_allocation_sha256": sha256(shard_data),
        "status": "FROZEN_UNEXECUTED",
    }


def preflight(root: Path) -> dict[str, object]:
    registry, rows, excluded = audit_input_registry(root)
    return {
        "baseline_commit": BASELINE_COMMIT, "baseline_tree": BASELINE_TREE,
        "block_a_rows": sum(row["identity"] == BLOCK_A_ID for row in rows),
        "entropy_requested": False, "registry_rows": len(rows),
        "registry_sha256": sha256(registry), "unique_excluded_values": len(excluded),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--preflight", action="store_true")
    mode.add_argument("--generate-or-resume", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    result = preflight(root) if args.preflight else freeze(root)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
