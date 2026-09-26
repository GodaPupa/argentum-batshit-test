"""Seed-free compiler for the exact Industrial Waste v2 R1 512-allocation execution plan.

This module freezes a deterministic allocation *order* from already-frozen protocol geometry:
row-major (1..64), then the protocol's PLAY/DRAW schedule order, then comparator followed by
candidate_families in protocol order. It filters each row's four frozen global copy-rank
permutations to the exact 60-card main deck for the selected list and records cryptographic
digests. It never initializes a game, creates a claim/journal, submits an action, or reads an
outcome.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

PROTOCOL_ID = "IW_V2_R1_ENGINE_STRUCTURAL_2026_09_25"
COMPARATOR_ID = "IMMUTABLE_SUBMITTED_V1_0"
EXPECTED_CANDIDATES = ["COMPACT_LOOP", "RECURSIVE_EGGS", "LEAN_TRON_HYBRID"]
EXPECTED_SCHEDULES = ["PLAY_SKIP_FIRST_DRAW", "DRAW_TAKE_FIRST_DRAW"]


def _json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected JSON object")
    return value


def _sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha256_file(path: Path) -> str:
    return _sha256_bytes(path.read_bytes())


def _parse_main_deck(path: Path) -> dict[str, int]:
    counts: dict[str, int] = {}
    section: str | None = None
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1].lower()
            continue
        if section != "main":
            continue
        try:
            count_raw, name = line.split(" ", 1)
            count = int(count_raw)
        except Exception as exc:
            raise ValueError(f"{path}:{number}: invalid main-deck row") from exc
        if count <= 0 or not name.strip():
            raise ValueError(f"{path}:{number}: invalid main-deck count/name")
        counts[name.strip()] = counts.get(name.strip(), 0) + count
    if sum(counts.values()) != 60:
        raise ValueError(f"{path}: main deck must contain exactly 60 cards")
    return counts


def _copy_labels(counts: dict[str, int]) -> set[str]:
    return {f"{name}#{copy}" for name, count in counts.items() for copy in range(1, count + 1)}


def _filtered_ordering(global_order: list[int], labels: list[str], allowed: set[str]) -> list[str]:
    if sorted(global_order) != list(range(len(labels))):
        raise ValueError("corpus ordering is not an exact permutation of the label universe")
    filtered = [labels[index] for index in global_order if labels[index] in allowed]
    if len(filtered) != 60 or set(filtered) != allowed:
        raise ValueError("filtered ordering does not exactly match a frozen 60-card main deck")
    return filtered


def compile_plan(root: Path, source_head: str | None = None) -> dict[str, Any]:
    root = root.resolve()
    protocol_path = root / "industrial-waste/v2/protocol-v2-r1.json"
    corpus_path = root / "industrial-waste/v2/r1-ordering-corpus.json"
    freeze_path = root / "industrial-waste/v2/r0-freeze.json"
    protocol = _json(protocol_path)
    corpus = _json(corpus_path)
    freeze = _json(freeze_path)

    if protocol.get("protocol_id") != PROTOCOL_ID:
        raise ValueError("R1 protocol id drift")
    if protocol.get("comparator") != COMPARATOR_ID:
        raise ValueError("R1 comparator drift")
    if protocol.get("candidate_families") != EXPECTED_CANDIDATES:
        raise ValueError("R1 candidate order drift")
    design = protocol.get("design") or {}
    if design.get("first_draw_schedules_per_row") != EXPECTED_SCHEDULES:
        raise ValueError("R1 schedule order drift")
    if (
        design.get("corpus_rows") != 64
        or design.get("deck_count") != 4
        or design.get("allocations_per_deck") != 128
        or design.get("total_allocations") != 512
    ):
        raise ValueError("R1 allocation geometry drift")

    if corpus.get("namespace") != "IW_V2_R1_ORDERINGS_2026_09_25":
        raise ValueError("R1 corpus namespace drift")
    labels = corpus.get("labels")
    rows = corpus.get("rows")
    if not isinstance(labels, list) or not labels or len(set(labels)) != len(labels):
        raise ValueError("R1 corpus labels invalid")
    if not isinstance(rows, list) or len(rows) != 64:
        raise ValueError("R1 corpus must contain exactly 64 rows")
    if [row.get("row") for row in rows] != list(range(1, 65)):
        raise ValueError("R1 corpus row order drift")

    deck_specs: list[tuple[str, str, str]] = [
        (
            COMPARATOR_ID,
            freeze["historical_control"]["path"],
            freeze["historical_control"]["sha256"],
        )
    ]
    for family in EXPECTED_CANDIDATES:
        item = freeze["candidates"][family]
        deck_specs.append((family, item["path"], item["sha256"]))

    deck_data: dict[str, dict[str, Any]] = {}
    for family, raw_path, expected_sha in deck_specs:
        path = root / raw_path
        if not path.is_file():
            raise ValueError(f"frozen deck missing: {raw_path}")
        actual_sha = _sha256_file(path)
        if actual_sha != expected_sha:
            raise ValueError(f"frozen deck SHA drift: {family}")
        counts = _parse_main_deck(path)
        allowed = _copy_labels(counts)
        if not allowed.issubset(set(labels)):
            missing = sorted(allowed - set(labels))
            raise ValueError(f"corpus label universe misses {family}: {missing[:3]}")
        deck_data[family] = {
            "path": raw_path,
            "sha256": actual_sha,
            "main_copy_labels": allowed,
        }

    allocations: list[dict[str, Any]] = []
    allocation_index = 0
    for row in rows:
        raw_orderings = row.get("initial_orderings")
        if not isinstance(raw_orderings, list) or len(raw_orderings) != 4:
            raise ValueError(f"row {row.get('row')}: requires exactly four initial orderings")
        for schedule in EXPECTED_SCHEDULES:
            for family, _, _ in deck_specs:
                allocation_index += 1
                allowed = deck_data[family]["main_copy_labels"]
                filtered = [
                    _filtered_ordering(ordering, labels, allowed)
                    for ordering in raw_orderings
                ]
                ordering_digests = [
                    _sha256_bytes(("\n".join(ordering) + "\n").encode("utf-8"))
                    for ordering in filtered
                ]
                allocations.append(
                    {
                        "allocation_index": allocation_index,
                        "allocation_id": f"IW_V2_R1_{allocation_index:04d}",
                        "row": row["row"],
                        "schedule": schedule,
                        "deck_id": family,
                        "deck_path": deck_data[family]["path"],
                        "deck_sha256": deck_data[family]["sha256"],
                        "initial_ordering_count": 4,
                        "filtered_cards_per_ordering": 60,
                        "initial_ordering_sha256": ordering_digests,
                    }
                )

    if allocation_index != 512 or len(allocations) != 512:
        raise ValueError("compiled plan must contain exactly 512 allocations")
    if len({a["allocation_id"] for a in allocations}) != 512:
        raise ValueError("compiled allocation ids are not unique")

    plan_core = {
        "protocol_id": PROTOCOL_ID,
        "ordering_namespace": corpus["namespace"],
        "execution_order": {
            "major": "row_ascending_1_to_64",
            "middle": EXPECTED_SCHEDULES,
            "minor": [COMPARATOR_ID] + EXPECTED_CANDIDATES,
        },
        "allocations": allocations,
    }
    canonical = json.dumps(plan_core, sort_keys=True, separators=(",", ":")).encode("utf-8")

    return {
        "schema": "industrial-waste-v2-r1-allocation-plan-v1",
        "status": "QUALIFIED_SEED_FREE_EXACT_512_ALLOCATION_PLAN_ONLY",
        "source_head": source_head,
        **plan_core,
        "plan_sha256": _sha256_bytes(canonical),
        "frozen_input_sha256": {
            "protocol": _sha256_file(protocol_path),
            "corpus": _sha256_file(corpus_path),
            "freeze": _sha256_file(freeze_path),
        },
        "official_counters": {
            "allocations_initialized": 0,
            "actions_submitted": 0,
            "comparative_outcomes_exposed": 0,
        },
        "claim_creation_authorized": False,
        "attempt_journal_creation_authorized": False,
        "initialization_admitted": False,
        "execution_authorized": False,
        "closes_official_r1_corpus_runner_binding": False,
        "next_gate": (
            "Compose this exact 512-allocation plan with the real engine initializer, qualified "
            "full-horizon runner, accepted runtime identity, artifact/completeness contract, "
            "complete metric projection, and preinitialization claim/journal guard."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--source-head")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = compile_plan(args.root, args.source_head)
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(payload, encoding="utf-8")
    print(payload, end="")


if __name__ == "__main__":
    main()
