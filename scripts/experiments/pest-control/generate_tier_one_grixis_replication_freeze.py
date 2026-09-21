#!/usr/bin/env python3
"""One-shot, artifact-only 12-game Tier-1 Grixis replication-vector freeze.

Validation requests no entropy. Production generation makes exactly one os.urandom(96) call,
quarantines the complete draw before validation, and never retries/replaces/rerolls a value.
No game is initialized and no outcome-bearing API is reachable.
"""

from __future__ import annotations

import argparse, csv, hashlib, io, json, os, platform, runpy
from datetime import datetime, timezone
from pathlib import Path

PROTOCOL = "PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1"
BLOCK = f"{PROTOCOL}_REPLICATION_12"
QUALIFIED_RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
GRIXIS_MAIN = "2e20ec68c58dda1f913f99e8c97b7458f920a9be18bb4ee2c09784326cc2896c"
GRIXIS_SIDEBOARD = "19c64bb94ef1dbdd953409dede79b02a478e9cf959bb230fe0c67a9b43d3177a"
GRIXIS_75 = "b73fe84ec0dd11961f45cf0ab39f155c92d99636ff1ad961bbc8f4774504bdcd"
SMOKE_VECTOR_SHA256 = "99eb94c4ec28f073534c008b367f3384df25abebd29dde0a9574227599cb60eb"
ACK = "GENERATE_TIER_ONE_GRIXIS_REPLICATION_12_NO_GAMEPLAY"

ASSIGNMENT_HEADER = (
    "protocol_id","block_id","game_number","seed_decimal","seed_hex","pest_seat",
    "grixis_seat","starting_deck","pest_play_draw","pest_main_sha256",
    "grixis_main_sha256","grixis_sideboard_sha256","grixis_complete75_sha256",
    "qualified_runner",
)
BASE_CELLS = (
    ("SEAT_ZERO","SEAT_ONE","PEST_CONTROL","PLAY"),
    ("SEAT_ZERO","SEAT_ONE","GRIXIS_AFFINITY","DRAW"),
    ("SEAT_ONE","SEAT_ZERO","PEST_CONTROL","PLAY"),
    ("SEAT_ONE","SEAT_ZERO","GRIXIS_AFFINITY","DRAW"),
)
CELLS = BASE_CELLS * 3

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def canonical_json(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode()

def seed_hex(seed: int) -> str:
    return f"0x{seed & ((1 << 64) - 1):016x}"

def write_new(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o444)
    with os.fdopen(fd, "wb") as stream:
        stream.write(data); stream.flush(); os.fsync(stream.fileno())
    dfd = os.open(path.parent, os.O_RDONLY)
    try: os.fsync(dfd)
    finally: os.close(dfd)

def csv_bytes(rows: list[dict[str, object]]) -> bytes:
    out = io.StringIO(newline="")
    writer = csv.DictWriter(out, fieldnames=ASSIGNMENT_HEADER, lineterminator="\n")
    writer.writeheader(); writer.writerows(rows)
    return out.getvalue().encode()

def read_vector(path: Path) -> list[int]:
    data = path.read_bytes()
    if b"\r" in data or not data.endswith(b"\n"):
        raise ValueError("prior smoke vector is not canonical LF text")
    if sha256(data) != SMOKE_VECTOR_SHA256:
        raise ValueError("prior smoke vector hash mismatch")
    values = [int(x) for x in data.decode().strip().splitlines()]
    if len(values) != 4 or len(set(values)) != 4 or any(v == 0 for v in values):
        raise ValueError("prior smoke vector shape mismatch")
    return values

def legacy_exclusion(root: Path) -> set[int]:
    old = runpy.run_path(str(root / "scripts/experiments/pest-control/generate_tier_one_grixis_smoke_freeze.py"))
    excluded, _ = old["audit_sources"](root)
    return set(excluded)

def generate(root: Path, prior_smoke_dir: Path, output: Path, entropy: bytes, fixture: bool,
             freeze_commit: str, freeze_tree: str) -> dict[str, object]:
    excluded = legacy_exclusion(root)
    smoke = read_vector(prior_smoke_dir / "ordered-seeds.txt")
    if excluded.intersection(smoke):
        raise ValueError("accepted smoke seeds unexpectedly overlap legacy exclusion")
    excluded.update(smoke)
    if len(excluded) != 538:
        raise ValueError("complete replication exclusion set must contain exactly 538 values")
    if output.exists() and any(output.iterdir()):
        raise ValueError("output directory must be absent or empty")
    if len(entropy) != 96:
        raise ValueError("entropy draw must be exactly 96 bytes")
    output.mkdir(parents=True, exist_ok=True)

    seeds = [int.from_bytes(entropy[i:i+8], "big", signed=True) for i in range(0, 96, 8)]
    quarantine = {
        "block_id": BLOCK, "entropy_byte_count": 96, "entropy_sha256": sha256(entropy),
        "generation_method": "one os.urandom(96) call; twelve signed big-endian 64-bit seeds in unchanged draw order",
        "seeds_decimal": seeds, "seeds_hex": [seed_hex(s) for s in seeds],
        "status": "NONEXPERIMENTAL_FIXTURE" if fixture else "QUARANTINED_UNATTEMPTED",
    }
    if not fixture:
        quarantine["drawn_at_utc"] = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00","Z")
    qpath = output / "quarantined-vector.json"
    write_new(qpath, canonical_json(quarantine))

    errors = []
    if any(s == 0 for s in seeds): errors.append("zero seed")
    if len(set(seeds)) != 12: errors.append("duplicate seed")
    overlap = sorted(set(seeds) & excluded)
    if overlap: errors.append(f"complete-registry overlap: {overlap}")
    if errors:
        write_new(output / "invalid-retired.json", canonical_json({
            "block_id": BLOCK, "errors": errors, "quarantine_sha256": sha256(qpath.read_bytes()),
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
    vector = ("\n".join(str(s) for s in seeds) + "\n").encode()
    assignments = csv_bytes(rows)
    manifest = {
        "protocol_id": PROTOCOL, "block_id": BLOCK, "qualified_runner": QUALIFIED_RUNNER,
        "status": "NONEXPERIMENTAL_FIXTURE" if fixture else "FROZEN_UNEXECUTED",
        "runner_state": "DISABLED",
        "freeze_source": {"commit": freeze_commit, "tree": freeze_tree},
        "generation": {"generator_sha256": sha256(Path(__file__).read_bytes()),
                       "method": "one os.urandom(96) call", "regeneration_permitted": False},
        "collision_audit": {"excluded_seed_count": 538, "new_seed_count": 12,
                            "new_unique_count": 12, "overlap_count": 0, "result": "PASS"},
        "assignment_counts": {
            "games": 12, "pest_play": 6, "pest_draw": 6,
            "pest_seat_zero": 6, "pest_seat_one": 6,
            "joint_cells": {"SEAT_ZERO_PLAY":3,"SEAT_ZERO_DRAW":3,"SEAT_ONE_PLAY":3,"SEAT_ONE_DRAW":3},
        },
        "deck_hashes": {"pest_main":PEST_MAIN,"grixis_main":GRIXIS_MAIN,
                        "grixis_sideboard":GRIXIS_SIDEBOARD,"grixis_complete75":GRIXIS_75},
        "prior_smoke": {"ordered_vector_sha256": SMOKE_VECTOR_SHA256, "seed_count": 4},
        "artifact_hashes": {"ordered_vector_sha256":sha256(vector),
                            "assignment_csv_sha256":sha256(assignments),
                            "quarantined_vector_sha256":sha256(qpath.read_bytes())},
        "official_counters": {"games_initialized":0,"actions_submitted":0,
                              "artifacts_with_outcomes":0,"outcome_exposure":0},
        "environment": {"machine":platform.machine(),"os":platform.platform(),"python":platform.python_version()},
    }
    artifacts = {"ordered-seeds.txt":vector,"assignments.csv":assignments,
                 "freeze-manifest.json":canonical_json(manifest)}
    for name,data in artifacts.items(): write_new(output/name,data)
    checks = {name:sha256(data) for name,data in artifacts.items()}
    checks["quarantined-vector.json"] = sha256(qpath.read_bytes())
    write_new(output/"artifacts.sha256",
              ("\n".join(f"{digest}  {name}" for name,digest in sorted(checks.items()))+"\n").encode())
    return {"status":manifest["status"],"seed_count":12,"complete_exclusion_count":538,
            "ordered_vector_sha256":sha256(vector),"assignment_csv_sha256":sha256(assignments),
            "manifest_sha256":sha256(artifacts["freeze-manifest.json"])}

def main() -> None:
    p=argparse.ArgumentParser()
    m=p.add_mutually_exclusive_group(required=True)
    m.add_argument("--preflight",action="store_true"); m.add_argument("--validate-fixture",action="store_true"); m.add_argument("--generate",action="store_true")
    p.add_argument("--prior-smoke-dir",type=Path,required=True)
    p.add_argument("--output-dir",type=Path)
    p.add_argument("--freeze-commit",default="0"*40); p.add_argument("--freeze-tree",default="0"*40)
    a=p.parse_args(); root=Path(__file__).resolve().parents[3]
    excluded=legacy_exclusion(root); smoke=read_vector(a.prior_smoke_dir/"ordered-seeds.txt")
    if len(excluded | set(smoke)) != 538: raise ValueError("replication exclusion audit failed")
    if a.preflight:
        print(json.dumps({"status":"READY","entropy_requested":False,"excluded_values":538},sort_keys=True)); return
    if a.output_dir is None: p.error("--output-dir is required")
    if a.generate:
        if os.environ.get("PEST_GRIXIS_REPLICATION_FREEZE_ACK") != ACK: raise ValueError("exact production acknowledgement required")
        if os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch" or os.environ.get("GITHUB_REF") != "refs/heads/main" or os.environ.get("GITHUB_RUN_ATTEMPT") != "1":
            raise ValueError("production generation requires main workflow_dispatch attempt 1")
        entropy=os.urandom(96)
    else:
        entropy=hashlib.shake_256(b"NONEXPERIMENTAL_TIER_ONE_GRIXIS_REPLICATION_12_V1").digest(96)
    print(json.dumps(generate(root,a.prior_smoke_dir,a.output_dir,entropy,a.validate_fixture,a.freeze_commit,a.freeze_tree),sort_keys=True))

if __name__=="__main__": main()
