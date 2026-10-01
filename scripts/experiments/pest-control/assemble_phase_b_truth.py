#!/usr/bin/env python3
"""Prospective Phase-B input assembly only; never an oracle or gameplay entry.

Read-only consumption of two already accepted byte streams. This module cannot
establish external acceptance, compare keep/bottom behavior, or admit a game.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path

BASE_SHA256 = "8d7e3e97a4dc70aab696b2d27012e410d57598d477f7b97689f4de5b442f348f"
SUPPLEMENT_SHA256 = "1bbe8b92f01e69cd679b36375ef4168893267e783441ba5d1b81c76166f0cf4a"
BASE_ROWS = 6850
SUPPLEMENT_ROWS = 692
UNBANKED_SHA256 = "a00aef9df49106a2611079ece2824bf4e97d40c7e89207b16bc1a9e05d7ae287"
WORKLIST_SHA256 = "2174410ee36dc0e74d0d7f4335606de7236aa845ef43bab409259a3056133738"
RAW_CONTROLLER_BLOB = "4f020ea2406e9bd6a87ec389685b48f15df4c5a3"
MAX_INPUT_BYTES = 16 * 1024 * 1024


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def canonical(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True,
                       separators=(",", ":"), allow_nan=False) + "\n").encode("utf-8")


def _load(raw: bytes, expected_sha256: str, schema: str) -> dict:
    require(type(raw) is bytes and 0 < len(raw) <= MAX_INPUT_BYTES, "invalid input bytes/size")
    require(hashlib.sha256(raw).hexdigest() == expected_sha256, "accepted input digest mismatch")

    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "duplicate JSON key: " + key)
            result[key] = value
        return result

    def bad_constant(value):
        raise ValueError("non-finite JSON value: " + value)

    value = json.loads(raw.decode("utf-8", errors="strict"),
                       object_pairs_hook=unique, parse_constant=bad_constant)
    require(type(value) is dict and value.get("schema") == schema, "unexpected truth schema")
    return value


def _key(row: object) -> tuple:
    require(type(row) is dict and set(row) == {"atom", "development_functional"}, "unexpected truth row")
    require(type(row["development_functional"]) is bool, "truth must be known boolean")
    atom = row["atom"]
    require(type(atom) is dict and set(atom) == {
        "physical_lands", "m2_opening_candidate", "early_spell"}, "unexpected atom fields")
    require(type(atom["m2_opening_candidate"]) is bool, "M2 must be boolean")
    spell = atom["early_spell"]
    require(type(spell) is str and bool(spell.strip()) and "\x00" not in spell, "invalid spell identity")
    lands = atom["physical_lands"]
    require(type(lands) is list, "land multiset must be a list")
    physical = []
    for pair in lands:
        require(type(pair) is list and len(pair) == 2, "invalid land pair")
        name, count = pair
        require(type(name) is str and bool(name.strip()) and "\x00" not in name, "invalid land identity")
        require(type(count) is int and 1 <= count <= 7, "invalid physical land count")
        physical.append((name, count))
    require(sum(count for _, count in physical) <= 7, "land multiset exceeds hand")
    require(physical == sorted(physical) and len({n for n, _ in physical}) == len(physical),
            "land multiset is not sorted and unique")
    return tuple(physical), atom["m2_opening_candidate"], spell


def assemble_accepted_truth(base_raw: bytes, supplement_raw: bytes) -> bytes:
    """No RNG, controller invocation, unknown coercion, or mutable input/output state."""
    base = _load(base_raw, BASE_SHA256, "pest-monster-london-m5-atomic-truth-bank-v1")
    supplement = _load(supplement_raw, SUPPLEMENT_SHA256,
                       "pest-current-pair-phase-u-m5-truth-supplement-v1")
    for field, expected in {
        "source_unbanked_atoms_sha256": UNBANKED_SHA256,
        "source_worklist_sha256": WORKLIST_SHA256,
        "raw_controller_blob": RAW_CONTROLLER_BLOB,
    }.items():
        require(supplement.get(field) == expected, "Phase-U provenance mismatch: " + field)
    merged = {}
    origins = {}
    for label, value, count in (("accepted-base-6850", base, BASE_ROWS),
                                ("accepted-phase-u-692", supplement, SUPPLEMENT_ROWS)):
        rows = value.get("rows")
        require(type(rows) is list and len(rows) == count, "incomplete accepted truth bank: " + label)
        for row in rows:
            key = _key(row)
            require(key not in merged, "duplicate/overlapping atom; no deduplication allowed")
            merged[key] = row
            origins[key] = label
    require(len(merged) == BASE_ROWS + SUPPLEMENT_ROWS, "incomplete combined closure")
    ordered = sorted(merged)
    return canonical({
        "schema": "pest-current-pair-phase-b-accepted-m5-input-v1",
        "authority": "INPUT_ASSEMBLY_ONLY_NOT_BEHAVIORAL_ACCEPTANCE",
        "source_sha256": {"base": BASE_SHA256, "phase_u": SUPPLEMENT_SHA256},
        "source_runs": {"base": 36531232867, "phase_u": 36699359839},
        "source_counts": {"base": BASE_ROWS, "phase_u": SUPPLEMENT_ROWS},
        "row_count": len(merged),
        "rows": [merged[key] for key in ordered],
        "row_origins": [origins[key] for key in ordered],
        "unknown_rows": 0,
        "oracle_invoked": False,
        "behavioral_comparison_performed": False,
        "official_counters_delta": 0,
    })


def _read_regular(path: Path) -> bytes:
    require(path.resolve(strict=True) == path.absolute(), "input path alias/symlink")
    require(path.is_file() and not path.is_symlink(), "input must be a regular file")
    require(path.stat().st_size <= MAX_INPUT_BYTES, "input exceeds size bound")
    return path.read_bytes()


def write_new(path: Path, raw: bytes) -> None:
    """Reserve once and fsync. Failure preserves any partial output; no reset/retry."""
    require(path.parent.resolve(strict=True) == path.parent.absolute(), "output parent alias/symlink")
    with path.open("xb") as stream:
        stream.write(raw)
        stream.flush()
        os.fsync(stream.fileno())
    fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--supplement", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    raw = assemble_accepted_truth(_read_regular(args.base), _read_regular(args.supplement))
    write_new(args.output, raw)
    print(json.dumps({"scope": "INPUT_ASSEMBLY_ONLY", "rows": BASE_ROWS + SUPPLEMENT_ROWS,
                      "sha256": hashlib.sha256(raw).hexdigest(), "official_counters_delta": 0}))


if __name__ == "__main__":
    main()
