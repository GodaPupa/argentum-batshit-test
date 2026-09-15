#!/usr/bin/env python3
"""One-shot Gate 5 registry and Block A seed-freeze generator.

`--registry-only` is deterministic and requests no entropy. `--generate` performs exactly one
`os.urandom` call, uses the first 400 bytes as 50 signed 64-bit seeds, and uses the remaining bytes
as independent cryptographic permutation tags. It never retries or substitutes a seed.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
import platform
import sys
from datetime import datetime, timezone
from pathlib import Path


PROTOCOL = "PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1"
BLOCK = f"{PROTOCOL}_BLOCK_A"
GATE4_SOURCE = "d81ec35f0be74422315f9bb5bf8d69c395b2d872"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
PEST_SIDEBOARD = "c1910468c228662b21647eb7ca8481cd11691906e56e0a37990e00f22886368c"
PEST_75 = "2927737eb084657cda58fd1877db933037c383273062f0bd209c7ff3046c1cf5"
RED_MAIN = "38c7850d1b9b070637502cedfffc6116d3504a525db8b51223505d7935134258"
RED_SIDEBOARD = "d0aab592e6c82ad019dba0eadc028db75ef5cf13c9b3e4e0531a04d513bfc77a"
RED_75 = "e9ff7ecbdbc8f41ebe526fe8fee4f87f706e0630491f9d78121677922fbd647d"

SOURCES = (
    ("pest-control-v10-goldfish-sample-1-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-1-fresh-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-1-performance-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-1-untouched-seeds.csv", "ACCEPTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-2-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-2-take-2-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-2-take-3-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-2-take-4-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-2-take-5-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-2-take-6-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-2-take-7-seeds.csv", "REJECTED_RETIRED"),
    ("pest-control-v10-goldfish-sample-2-take-8-seeds.csv", "ACCEPTED_RETIRED"),
)

FIXTURES = (
    (0, "PestControlArtifactContractTest.zero_seed_record", "RESERVED_NONZERO_REQUIREMENT"),
    (0x0A71FAC7, "PestControlArtifactContractTest.withRngSeed", "FIXTURE_ONLY"),
    (0x504553544734, "NONEXPERIMENTAL_GATE4_FIXTURE_ENTROPY", "FIXTURE_ONLY"),
)

REGISTRY_HEADER = (
    "category", "identity", "position", "seed_decimal", "seed_hex", "source_path", "disposition",
)
BLOCK_HEADER = (
    "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
    "mono_red_seat", "starting_player", "pest_play_draw", "pest_main_sha256",
    "pest_sideboard_sha256", "pest_complete75_sha256", "mono_red_main_sha256",
    "mono_red_sideboard_sha256", "mono_red_complete75_sha256", "gate4_source_commit",
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def seed_hex(seed: int) -> str:
    return f"0x{seed & ((1 << 64) - 1):016x}"


def csv_bytes(header: tuple[str, ...], rows: list[dict[str, object]]) -> bytes:
    out = io.StringIO(newline="")
    writer = csv.DictWriter(out, fieldnames=header, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return out.getvalue().encode("utf-8")


def read_source(path: Path) -> list[tuple[int, int]]:
    with path.open("r", encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames is None:
            raise ValueError(f"missing header: {path}")
        decimal_column = "seed_decimal" if "seed_decimal" in reader.fieldnames else "seed"
        hex_column = "seed_hex" if "seed_hex" in reader.fieldnames else "hex" if "hex" in reader.fieldnames else None
        rows = []
        for expected, row in enumerate(reader, 1):
            game = int(row["game"])
            seed = int(row[decimal_column])
            if game != expected:
                raise ValueError(f"noncontiguous source positions: {path}")
            if hex_column and int(row[hex_column], 16) != seed:
                raise ValueError(f"decimal/hex mismatch: {path}:{game}")
            rows.append((game, seed))
    if len(rows) != 30:
        raise ValueError(f"expected 30 seeds: {path}")
    return rows


def build_registry(root: Path) -> tuple[bytes, set[int], list[dict[str, str]]]:
    resources = root / "gym/src/test/resources"
    discovered = {path.name for path in resources.glob("pest-control-v10-*-seeds.csv")}
    expected = {name for name, _ in SOURCES}
    if discovered != expected:
        raise ValueError(f"Pest seed-source inventory mismatch: missing={sorted(expected-discovered)} extra={sorted(discovered-expected)}")
    rows: list[dict[str, object]] = []
    source_records: list[dict[str, str]] = []
    historical: set[int] = set()
    for name, disposition in SOURCES:
        path = resources / name
        source_records.append({"path": str(path.relative_to(root)), "sha256": sha256(path.read_bytes()), "disposition": disposition})
        for position, seed in read_source(path):
            if seed in historical:
                raise ValueError(f"duplicate historical Pest seed: {seed}")
            historical.add(seed)
            rows.append({
                "category": "HISTORICAL_VECTOR", "identity": name.removesuffix(".csv"),
                "position": position, "seed_decimal": seed, "seed_hex": seed_hex(seed),
                "source_path": str(path.relative_to(root)), "disposition": disposition,
            })
    for seed, identity, disposition in FIXTURES:
        rows.append({
            "category": "FIXTURE_OR_RESERVED", "identity": identity, "position": 1,
            "seed_decimal": seed, "seed_hex": seed_hex(seed),
            "source_path": "repository source at Gate 4", "disposition": disposition,
        })
    exclusion = historical | {seed for seed, _, _ in FIXTURES}
    return csv_bytes(REGISTRY_HEADER, rows), exclusion, source_records


def write_exact(path: Path, data: bytes) -> None:
    if path.exists():
        if path.read_bytes() != data:
            raise ValueError(f"refusing to replace nonidentical artifact: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def registry_path(root: Path) -> Path:
    return root / "docs/experiments/pest-control/matchup-block-a-seed-registry.csv"


def write_registry(root: Path) -> tuple[bytes, set[int], list[dict[str, str]]]:
    data, exclusion, sources = build_registry(root)
    write_exact(registry_path(root), data)
    return data, exclusion, sources


def generate(root: Path) -> None:
    registry, excluded, source_records = write_registry(root)
    docs = root / "docs/experiments/pest-control"
    csv_path = root / "gym/src/test/resources/pest-control-v10-vs-mono-red-madness-soterx-2026-09-11-preboard-v1-block-a-seeds.csv"
    vector_path = docs / "matchup-block-a-ordered-seeds.txt"
    manifest_path = docs / "matchup-block-a-seed-freeze-manifest.json"
    manifest_hash_path = docs / "matchup-block-a-seed-freeze-manifest.sha256"
    for path in (csv_path, vector_path, manifest_path, manifest_hash_path):
        if path.exists():
            raise ValueError(f"one-shot output already exists: {path}")

    entropy = os.urandom(50 * 8 + 50 * 16)  # The only entropy request in Gate 5.
    seeds = [int.from_bytes(entropy[offset:offset + 8], "big", signed=True) for offset in range(0, 400, 8)]
    tags = [entropy[400 + offset:400 + offset + 16] for offset in range(0, 800, 16)]
    invalid = []
    if any(seed == 0 for seed in seeds): invalid.append("zero seed")
    if len(set(seeds)) != 50: invalid.append("duplicate seed")
    overlap = sorted(set(seeds) & excluded)
    if overlap: invalid.append(f"registry overlap: {overlap}")
    if len(set(tags)) != 50: invalid.append("permutation-tag collision")
    if invalid:
        invalid_path = docs / f"matchup-block-a-invalid-generation-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.json"
        write_exact(invalid_path, (json.dumps({"status": "INVALID_RETIRED", "errors": invalid, "entropy_sha256": sha256(entropy)}, sort_keys=True, separators=(",", ":")) + "\n").encode())
        raise ValueError("; ".join(invalid))

    cells = (
        [("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY")] * 13 +
        [("SEAT_ZERO", "SEAT_ONE", "MONO_RED_MADNESS", "DRAW")] * 12 +
        [("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY")] * 12 +
        [("SEAT_ONE", "SEAT_ZERO", "MONO_RED_MADNESS", "DRAW")] * 13
    )
    assignments = [cells[index] for index in sorted(range(50), key=lambda index: tags[index])]
    rows: list[dict[str, object]] = []
    reconciliation: list[dict[str, object]] = []
    for game, (seed, assignment) in enumerate(zip(seeds, assignments, strict=True), 1):
        pest_seat, red_seat, starter, play_draw = assignment
        row = {
            "protocol_id": PROTOCOL, "block_id": BLOCK, "game_number": game,
            "seed_decimal": seed, "seed_hex": seed_hex(seed), "pest_seat": pest_seat,
            "mono_red_seat": red_seat, "starting_player": starter, "pest_play_draw": play_draw,
            "pest_main_sha256": PEST_MAIN, "pest_sideboard_sha256": PEST_SIDEBOARD,
            "pest_complete75_sha256": PEST_75, "mono_red_main_sha256": RED_MAIN,
            "mono_red_sideboard_sha256": RED_SIDEBOARD, "mono_red_complete75_sha256": RED_75,
            "gate4_source_commit": GATE4_SOURCE,
        }
        rows.append(row)
        reconciliation.append({key: row[key] for key in BLOCK_HEADER})

    vector = ("\n".join(str(seed) for seed in seeds) + "\n").encode("utf-8")
    csv_data = csv_bytes(BLOCK_HEADER, rows)
    script_hash = sha256(Path(__file__).read_bytes())
    cell_counts = {
        "SEAT_ZERO_PLAY": assignments.count(("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY")),
        "SEAT_ZERO_DRAW": assignments.count(("SEAT_ZERO", "SEAT_ONE", "MONO_RED_MADNESS", "DRAW")),
        "SEAT_ONE_PLAY": assignments.count(("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY")),
        "SEAT_ONE_DRAW": assignments.count(("SEAT_ONE", "SEAT_ZERO", "MONO_RED_MADNESS", "DRAW")),
    }
    manifest = {
        "assignment_counts": {"pest_draw": 25, "pest_play": 25, "pest_seat_one": 25, "pest_seat_zero": 25, "joint_cells": cell_counts},
        "block_id": BLOCK,
        "canonicalization": {"csv": "UTF-8; LF-only; no BOM; RFC 4180 quoting; one trailing LF", "hex": "lowercase 0x plus 16 two's-complement hexadecimal digits", "manifest": "UTF-8; sorted keys; compact JSON; one trailing LF", "vector": "signed decimal; one seed per line; one trailing LF"},
        "collision_audit": {"new_seed_count": 50, "new_unique_count": 50, "overlap_count": 0, "registry_exclusion_count": len(excluded), "result": "PASS"},
        "deck_hashes": {"pest_main": PEST_MAIN, "pest_sideboard": PEST_SIDEBOARD, "pest_complete75": PEST_75, "mono_red_main": RED_MAIN, "mono_red_sideboard": RED_SIDEBOARD, "mono_red_complete75": RED_75},
        "environment": {"machine": platform.machine(), "os": platform.platform(), "python": platform.python_version()},
        "gate4_source_commit": GATE4_SOURCE,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "generation_method": "one os.urandom(1200) call; first 400 bytes parsed as 50 signed big-endian 64-bit seeds; remaining 800 bytes parsed as 50 independent 128-bit sort tags for a one-pass assignment permutation; no retry/replacement",
        "generator_sha256": script_hash,
        "ordered_vector_sha256": sha256(vector),
        "protocol_id": PROTOCOL,
        "reconciliation": reconciliation,
        "seed_csv_sha256": sha256(csv_data),
        "seed_registry_input_sha256": sha256(registry),
        "seed_registry_sources": source_records,
        "status": "FROZEN_UNEXECUTED",
    }
    manifest_data = (json.dumps(manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode("utf-8")
    manifest_hash = sha256(manifest_data)
    manifest_hash_data = f"{manifest_hash}  {manifest_path.name}\n".encode("utf-8")
    write_exact(vector_path, vector)
    write_exact(csv_path, csv_data)
    write_exact(manifest_path, manifest_data)
    write_exact(manifest_hash_path, manifest_hash_data)
    print(json.dumps({
        "csv_sha256": sha256(csv_data), "manifest_sha256": manifest_hash,
        "ordered_vector_sha256": sha256(vector), "registry_sha256": sha256(registry),
        "seeds": 50, "status": "FROZEN_UNEXECUTED",
    }, sort_keys=True))


def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--registry-only", action="store_true")
    mode.add_argument("--generate", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    if args.registry_only:
        registry, excluded, sources = write_registry(root)
        print(json.dumps({"registry_sha256": sha256(registry), "rows": 363, "excluded_values": len(excluded), "source_files": len(sources)}, sort_keys=True))
    else:
        generate(root)


if __name__ == "__main__":
    main()
