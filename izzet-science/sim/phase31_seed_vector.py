#!/usr/bin/env python3
"""Phase-31 seed vector generator/freezer.

Production generation exists behind an explicit mode but Phase-31 static qualification
must pass before that mode is authorized. Raw seed values are written only to the
requested quarantine path and are never printed.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import secrets

COUNT = 12
MIN_I64 = -(1 << 63)
MAX_I64 = (1 << 63) - 1
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
OPPONENT_ID = "veteran-beastrider-commander-clash-2025-v1"
RUNNER_VERSION = "izzet-v09-phase30-runner-v1"

def _signed_i64_from_u64(value: int) -> int:
    return value if value <= MAX_I64 else value - (1 << 64)

def validate_vector(values: tuple[int, ...], registry: set[int]) -> None:
    if len(values) != COUNT:
        raise ValueError("seed vector must contain exactly 12 values")
    if any(type(v) is not int or v < MIN_I64 or v > MAX_I64 for v in values):
        raise ValueError("all seeds must be signed 64-bit integers")
    if any(v == 0 for v in values):
        raise ValueError("zero seed forbidden")
    if len(set(values)) != COUNT:
        raise ValueError("seed vector must be unique")
    overlap = set(values) & registry
    if overlap:
        raise ValueError("candidate vector overlaps Izzet seed registry")

def assignment_vector() -> tuple[str, ...]:
    # Frozen deterministic balance; seed values are bound by position after generation.
    values = tuple("play" if i % 2 else "draw" for i in range(1, COUNT + 1))
    assert values.count("play") == 6 and values.count("draw") == 6
    return values

def digest_json(value) -> str:
    blob = json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(blob).hexdigest()

def generate_once() -> tuple[int, ...]:
    # Exactly one draw per position; no per-value reroll path exists.
    raw = tuple(_signed_i64_from_u64(secrets.randbits(64)) for _ in range(COUNT))
    return raw

def freeze(values: tuple[int, ...], registry: set[int], quarantine: Path,
           public_manifest: Path, registry_digest: str,
           protocol_identity: str, runner_sha256: str) -> None:
    validate_vector(values, registry)
    assignments = assignment_vector()
    payload = {
        "schema": "izzet-v09-phase31-quarantined-seed-vector-v1",
        "values": list(values),
        "assignments": list(assignments),
    }
    quarantine.parent.mkdir(parents=True, exist_ok=True)
    quarantine.write_text(json.dumps(payload, sort_keys=True, separators=(",", ":")) + "\n")
    vector_sha = hashlib.sha256(
        json.dumps(list(values), separators=(",", ":")).encode()
    ).hexdigest()
    manifest = {
        "schema": "izzet-v09-phase31-public-freeze-manifest-v1",
        "vector_sha256": vector_sha,
        "runner_version": RUNNER_VERSION,
        "runner_sha256": runner_sha256,
        "control_sha256": CONTROL_SHA256,
        "opponent_identity": OPPONENT_ID,
        "phase29_protocol_identity": protocol_identity,
        "assignment_vector_sha256": digest_json(list(assignments)),
        "izzet_seed_registry_sha256": registry_digest,
        "count": COUNT,
        "izzet_play": 6,
        "izzet_draw": 6,
        "experimental_seeds_generated": COUNT,
        "experimental_seeds_consumed": 0,
        "games_initialized": 0,
        "outcome_exposure": 0,
    }
    public_manifest.parent.mkdir(parents=True, exist_ok=True)
    public_manifest.write_text(json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n")

def main() -> None:
    p=argparse.ArgumentParser()
    p.add_argument("--generate", action="store_true")
    p.add_argument("--registry")
    p.add_argument("--quarantine")
    p.add_argument("--public-manifest")
    p.add_argument("--registry-digest")
    p.add_argument("--protocol-identity")
    p.add_argument("--runner-sha256")
    args=p.parse_args()
    if not args.generate:
        raise SystemExit("generation requires explicit --generate")
    required=[args.registry,args.quarantine,args.public_manifest,args.registry_digest,
              args.protocol_identity,args.runner_sha256]
    if any(x is None for x in required):
        raise SystemExit("generation arguments incomplete")
    registry_data=json.loads(Path(args.registry).read_text())
    registry=set(int(v) for v in registry_data["signed_i64_values"])
    values=generate_once()
    freeze(values,registry,Path(args.quarantine),Path(args.public_manifest),
           args.registry_digest,args.protocol_identity,args.runner_sha256)
    print("V09_PHASE31_GENERATION_COMPLETE_VALUES_QUARANTINED")

if __name__=="__main__":
    main()
