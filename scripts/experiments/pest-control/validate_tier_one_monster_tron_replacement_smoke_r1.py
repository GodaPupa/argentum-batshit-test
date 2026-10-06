#!/usr/bin/env python3
"""Seed-free validation for the Monster Tron replacement-smoke R1 machinery.

This program is validation-only. It never requests production entropy, creates or updates a claim,
initializes a game, submits an action, or exposes an outcome. Its deterministic fixture members are
nonexperimental test inputs and can never supply an official result.
"""

from __future__ import annotations

import argparse
import ast
import csv
import hashlib
import io
import json
import os
import runpy
from pathlib import Path

PROTOCOL = "PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1"
ORIGINAL_BLOCK = f"{PROTOCOL}_NONEXPERIMENTAL_SMOKE_4"
R1_BLOCK = f"{PROTOCOL}_NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1"
ORIGINAL_CLAIM_REF = "refs/heads/pest-control/official-attempts/monster-tron-smoke-v1"
ORIGINAL_CLAIM_COMMIT = "1c2e253ad7f5a7652304f7c9aaafc30e547a481e"
R1_CLAIM_REF = "refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1"
PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
MONSTER_TRON_MAIN = "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"
HISTORICAL_QUALIFIED_RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
PEST_PILOT = "AiProfile.PRODUCTION_CANDIDATE_EXPIRING"
MONSTER_PILOT = "PestMonsterTronPolicy.profile:pest-monster-tron-policy-audit"

C2_SOURCE_GATE_COMMIT = "d2d249c96e9a3917666efbbb5b35bc37a53a46ec"
C2_SOURCE_GATE_AUTHORITY = "SOURCE_GATE_CANDIDATE_ONLY__NO_GAMEPLAY_AUTHORITY"
R1_REVIEW_REQUEST_COMMENT = 6005652423
R1_ACCEPTANCE_COMMENT = 6007834588

TERROR_SMOKE_ARTIFACT_ID = 10733086089
TERROR_SMOKE_ARCHIVE_SHA256 = "bbf9f20e834f27f838e37de78c905c213a8917651818367360a8e8a140914961"
TERROR_REPLICATION_ARTIFACT_ID = 10773131628
TERROR_REPLICATION_ARCHIVE_SHA256 = "4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25"
TERROR_REPLICATION_VECTOR_SHA256 = "445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d"
ORIGINAL_MONSTER_ARTIFACT_ID = 10836436268
ORIGINAL_MONSTER_ARCHIVE_SHA256 = "70b9a665fbb154342e2789c1b6b2c2fd912579431a6ae1f9ab289984d5e7801c"
ORIGINAL_MONSTER_VECTOR_SHA256 = "1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31"
ORIGINAL_MONSTER_ASSIGNMENTS_SHA256 = "3e8c61d729d219261eb485600039b150d66e2e73e90f0f2e1450c7b48a57e098"
ORIGINAL_MONSTER_MANIFEST_SHA256 = "12b2b0c6c8209fc557c2a2a524223c603b42ae6c7c7ce211cbd291db75fbf6a8"

PRIOR_EXCLUSION_COUNT = 554
PRE_MONSTER_EXCLUSION_COUNT = 566
R1_MINIMUM_EXCLUSION_COUNT = 570

ASSIGNMENT_HEADER = (
    "protocol_id", "block_id", "game_number", "fixture_member_decimal", "fixture_member_hex",
    "pest_seat", "monster_tron_seat", "starting_deck", "pest_play_draw", "pest_main_sha256",
    "monster_tron_main_sha256", "pest_pilot", "monster_tron_pilot",
)
CELLS = (
    ("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY"),
    ("SEAT_ZERO", "SEAT_ONE", "MONSTER_TRON", "DRAW"),
    ("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY"),
    ("SEAT_ONE", "SEAT_ZERO", "MONSTER_TRON", "DRAW"),
)
FIXTURE_BYTES = bytes.fromhex(
    "1021324354657687"
    "90a1b2c3d4e5f607"
    "18395a7b9cbddef1"
    "8273645546372819"
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode()


def member_hex(member: int) -> str:
    return f"0x{member & ((1 << 64) - 1):016x}"


def vector_hash(values: list[int] | tuple[int, ...]) -> str:
    return sha256(("\n".join(str(value) for value in values) + "\n").encode())


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


def read_canonical_vector(path: Path, expected_sha256: str, expected_count: int, label: str) -> list[int]:
    data = path.read_bytes()
    if b"\r" in data or not data.endswith(b"\n"):
        raise ValueError(f"{label} is not canonical LF text")
    if sha256(data) != expected_sha256:
        raise ValueError(f"{label} hash mismatch")
    values = [int(line) for line in data.decode("utf-8").strip().splitlines()]
    if len(values) != expected_count or len(set(values)) != expected_count or any(value == 0 for value in values):
        raise ValueError(f"{label} shape mismatch")
    return values


def verify_original_assignments(path: Path, original_members: list[int]) -> None:
    data = path.read_bytes()
    if b"\r" in data or not data.endswith(b"\n"):
        raise ValueError("original Monster assignment CSV is not canonical LF text")
    if sha256(data) != ORIGINAL_MONSTER_ASSIGNMENTS_SHA256:
        raise ValueError("original Monster assignment CSV hash mismatch")
    rows = list(csv.DictReader(io.StringIO(data.decode("utf-8"))))
    if len(rows) != 4:
        raise ValueError("original Monster assignment CSV must contain exactly four rows")
    for game, (row, member, cell) in enumerate(zip(rows, original_members, CELLS, strict=True), 1):
        pest_seat, monster_seat, starter, play_draw = cell
        expected = {
            "protocol_id": PROTOCOL,
            "block_id": ORIGINAL_BLOCK,
            "game_number": str(game),
            "seed_decimal": str(member),
            "seed_hex": member_hex(member),
            "pest_seat": pest_seat,
            "monster_tron_seat": monster_seat,
            "starting_deck": starter,
            "pest_play_draw": play_draw,
            "pest_main_sha256": PEST_MAIN,
            "monster_tron_main_sha256": MONSTER_TRON_MAIN,
            "qualified_runner": HISTORICAL_QUALIFIED_RUNNER,
        }
        if row != expected:
            raise ValueError(f"original Monster assignment row {game} mismatch")


def verify_original_manifest(path: Path) -> None:
    data = path.read_bytes()
    if sha256(data) != ORIGINAL_MONSTER_MANIFEST_SHA256:
        raise ValueError("original Monster freeze manifest hash mismatch")
    manifest = json.loads(data)
    if manifest.get("protocol_id") != PROTOCOL or manifest.get("block_id") != ORIGINAL_BLOCK:
        raise ValueError("original Monster freeze manifest identity mismatch")
    if manifest.get("status") != "FROZEN_UNEXECUTED":
        raise ValueError("original Monster freeze manifest status mismatch")
    if manifest.get("collision_audit", {}).get("excluded_seed_count") != PRE_MONSTER_EXCLUSION_COUNT:
        raise ValueError("original Monster freeze manifest exclusion count mismatch")
    if manifest.get("deck_hashes") != {"pest_main": PEST_MAIN, "monster_tron_main": MONSTER_TRON_MAIN}:
        raise ValueError("original Monster freeze manifest deck identities mismatch")
    if manifest.get("official_counters") != {
        "actions_submitted": 0,
        "games_authorized": 0,
        "games_initialized": 0,
        "outcome_exposure": 0,
        "seeds_generated": 4,
    }:
        raise ValueError("original Monster freeze manifest counters mismatch")


def reconstruct_minimum_exclusion(
    root: Path,
    prior_terror_smoke_dir: Path,
    prior_terror_replication_dir: Path,
    original_monster_dir: Path,
) -> tuple[set[int], dict[str, object]]:
    terror_replication_generator = root / (
        "scripts/experiments/pest-control/generate_tier_one_mono_blue_terror_replication_freeze.py"
    )
    source = runpy.run_path(str(terror_replication_generator))
    prior, _smoke, prior_audit = source["complete_exclusion"](root, prior_terror_smoke_dir)
    prior = set(prior)
    if len(prior) != PRIOR_EXCLUSION_COUNT:
        raise ValueError("accepted Terror replication pre-freeze universe must contain exactly 554 values")

    terror_replication = read_canonical_vector(
        prior_terror_replication_dir / "ordered-seeds.txt",
        TERROR_REPLICATION_VECTOR_SHA256,
        12,
        "accepted Terror replication vector",
    )
    overlap = prior.intersection(terror_replication)
    if overlap:
        raise ValueError(f"accepted Terror replication vector overlaps prior exclusion: {sorted(overlap)}")
    pre_monster = prior | set(terror_replication)
    if len(pre_monster) != PRE_MONSTER_EXCLUSION_COUNT:
        raise ValueError("pre-Monster exclusion universe must contain exactly 566 values")

    original_monster = read_canonical_vector(
        original_monster_dir / "ordered-seeds.txt",
        ORIGINAL_MONSTER_VECTOR_SHA256,
        4,
        "retired original Monster vector",
    )
    verify_original_assignments(original_monster_dir / "assignments.csv", original_monster)
    verify_original_manifest(original_monster_dir / "freeze-manifest.json")
    overlap = pre_monster.intersection(original_monster)
    if overlap:
        raise ValueError(f"retired original Monster vector overlaps inherited exclusion: {sorted(overlap)}")
    minimum = pre_monster | set(original_monster)
    if len(minimum) != R1_MINIMUM_EXCLUSION_COUNT:
        raise ValueError("R1 minimum exclusion universe must contain exactly 570 values")

    return minimum, {
        "prior_unique_count": len(prior),
        "accepted_terror_replication_seed_count": len(terror_replication),
        "pre_monster_unique_count": len(pre_monster),
        "retired_original_monster_seed_count": len(original_monster),
        "minimum_unique_count": len(minimum),
        "accepted_terror_smoke_artifact_id": TERROR_SMOKE_ARTIFACT_ID,
        "accepted_terror_smoke_archive_sha256": TERROR_SMOKE_ARCHIVE_SHA256,
        "accepted_terror_replication_artifact_id": TERROR_REPLICATION_ARTIFACT_ID,
        "accepted_terror_replication_archive_sha256": TERROR_REPLICATION_ARCHIVE_SHA256,
        "accepted_terror_replication_vector_sha256": vector_hash(terror_replication),
        "original_monster_artifact_id": ORIGINAL_MONSTER_ARTIFACT_ID,
        "original_monster_archive_sha256": ORIGINAL_MONSTER_ARCHIVE_SHA256,
        "original_monster_vector_sha256": vector_hash(original_monster),
        "original_monster_assignments_sha256": ORIGINAL_MONSTER_ASSIGNMENTS_SHA256,
        "original_monster_members_decimal": original_monster,
        "extension_policy": "FAIL_CLOSED_IF_ANY_LATER_RETIRED_OR_RESERVED_IDENTITY_EXISTS; EXTEND_AND_REVIEW_BEFORE_PRODUCTION_FREEZE",
        "prior_audit": prior_audit,
    }


def verify_c2_source_gate(root: Path) -> None:
    path = root / "docs/experiments/pest-control/PEST_CURRENT_PAIR_C2_A2_ADMISSION_CANDIDATE_20261005.json"
    manifest = json.loads(path.read_bytes())
    if manifest.get("authority") != C2_SOURCE_GATE_AUTHORITY:
        raise ValueError("C2 source-gate authority mismatch")
    if manifest.get("exact_pair") != {
        "pest_main_sha256": PEST_MAIN,
        "monster_main_sha256": MONSTER_TRON_MAIN,
    }:
        raise ValueError("C2 source-gate pair mismatch")
    if manifest.get("monster_tron_activation") != "ABSENT_NOT_AUTHORIZED":
        raise ValueError("C2 source-gate unexpectedly authorizes Monster activation")
    if manifest.get("phase_b_authority") != "PERMANENTLY_CONSUMED_NO_REUSE":
        raise ValueError("Phase-B authority mismatch")
    if manifest.get("official_counters") != {"allocations": 0, "claims": 0, "games": 0, "outcomes": 0}:
        raise ValueError("C2 source-gate official counters mismatch")


def verify_live_claim_refs(path: Path) -> dict[str, object]:
    payload = json.loads(path.read_bytes())
    if not isinstance(payload, list):
        raise ValueError("live matching-refs payload must be a list")
    refs: dict[str, str] = {}
    for item in payload:
        ref = item.get("ref")
        sha = (item.get("object") or {}).get("sha")
        if not isinstance(ref, str) or not isinstance(sha, str):
            raise ValueError("live matching-refs entry malformed")
        refs[ref] = sha
    if refs.get(ORIGINAL_CLAIM_REF) != ORIGINAL_CLAIM_COMMIT:
        raise ValueError("historical Monster claim ref missing or moved")
    if R1_CLAIM_REF in refs:
        raise ValueError("R1 claim ref already exists; replacement attempt is consumed or ambiguous")
    unexpected = sorted(set(refs) - {ORIGINAL_CLAIM_REF})
    if unexpected:
        raise ValueError(f"unexpected official-attempt refs require exclusion reconciliation: {unexpected}")
    return {
        "historical_claim_ref": ORIGINAL_CLAIM_REF,
        "historical_claim_commit": ORIGINAL_CLAIM_COMMIT,
        "historical_claim_immutable": True,
        "r1_claim_ref": R1_CLAIM_REF,
        "r1_claim_ref_present": False,
        "unexpected_official_attempt_refs": [],
    }


def fixture_members() -> list[int]:
    if len(FIXTURE_BYTES) != 32:
        raise AssertionError("fixture must be exactly 32 bytes")
    return [
        int.from_bytes(FIXTURE_BYTES[offset:offset + 8], "big", signed=True)
        for offset in range(0, 32, 8)
    ]


def member_errors(members: list[int], excluded: set[int]) -> list[str]:
    errors: list[str] = []
    if len(members) != 4:
        errors.append("member-count")
    if any(member == 0 for member in members):
        errors.append("zero-member")
    if len(set(members)) != len(members):
        errors.append("duplicate-member")
    overlap = sorted(set(members).intersection(excluded))
    if overlap:
        errors.append(f"retired-overlap:{overlap}")
    return errors


def csv_bytes(rows: list[dict[str, object]]) -> bytes:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=ASSIGNMENT_HEADER, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue().encode()


def build_fixture_bundle(
    output: Path,
    excluded: set[int],
    exclusion_audit: dict[str, object],
    live_claim_audit: dict[str, object],
) -> dict[str, object]:
    if output.exists() and any(output.iterdir()):
        raise ValueError("output directory must be absent or empty")
    members = fixture_members()
    errors = member_errors(members, excluded)
    if errors:
        raise ValueError(f"fixed fixture is invalid: {errors}")

    rows: list[dict[str, object]] = []
    for game, (member, cell) in enumerate(zip(members, CELLS, strict=True), 1):
        pest_seat, monster_seat, starter, play_draw = cell
        rows.append({
            "protocol_id": PROTOCOL,
            "block_id": R1_BLOCK,
            "game_number": game,
            "fixture_member_decimal": member,
            "fixture_member_hex": member_hex(member),
            "pest_seat": pest_seat,
            "monster_tron_seat": monster_seat,
            "starting_deck": starter,
            "pest_play_draw": play_draw,
            "pest_main_sha256": PEST_MAIN,
            "monster_tron_main_sha256": MONSTER_TRON_MAIN,
            "pest_pilot": PEST_PILOT,
            "monster_tron_pilot": MONSTER_PILOT,
        })

    vector = ("\n".join(str(member) for member in members) + "\n").encode()
    assignments = csv_bytes(rows)
    manifest = {
        "schema": "pest-monster-tron-replacement-smoke-r1-seedfree-fixture-v1",
        "authority": "NONEXPERIMENTAL_VALIDATION_ONLY__NO_PRODUCTION_ENTROPY__NO_GAMEPLAY",
        "protocol_id": PROTOCOL,
        "block_id": R1_BLOCK,
        "parent_original_block_id": ORIGINAL_BLOCK,
        "parent_consumed_claim_ref": ORIGINAL_CLAIM_REF,
        "parent_consumed_claim_commit": ORIGINAL_CLAIM_COMMIT,
        "future_claim_ref": R1_CLAIM_REF,
        "future_claim_ref_state": "ABSENT_NOT_AUTHORIZED",
        "deck_hashes": {"pest_main": PEST_MAIN, "monster_tron_main": MONSTER_TRON_MAIN},
        "pilots": {"pest": PEST_PILOT, "monster_tron": MONSTER_PILOT},
        "historical_qualified_runner": HISTORICAL_QUALIFIED_RUNNER,
        "execution_source_c2": "NOT_CREATED_OR_PINNED_BY_THIS_GATE",
        "c2_source_gate": {
            "commit": C2_SOURCE_GATE_COMMIT,
            "authority": C2_SOURCE_GATE_AUTHORITY,
            "acceptance_scope": "SOURCE_GATE_ONLY__NO_EXECUTION_AUTHORITY",
        },
        "r1_protocol_review": {
            "request_comment": R1_REVIEW_REQUEST_COMMENT,
            "acceptance_comment": R1_ACCEPTANCE_COMMENT,
            "disposition": "ACCEPTED",
            "scope": "SEED_FREE_BOUNDED_REPLACEMENT_MACHINERY_IMPLEMENTATION_AND_VALIDATION_ONLY",
        },
        "fixture": {
            "byte_count": len(FIXTURE_BYTES),
            "bytes_sha256": sha256(FIXTURE_BYTES),
            "member_count": 4,
            "members_decimal": members,
            "members_hex": [member_hex(member) for member in members],
            "can_supply_official_result": False,
        },
        "collision_audit": {
            "minimum_excluded_identity_count": len(excluded),
            "fixture_member_count": 4,
            "fixture_unique_count": 4,
            "overlap_count": 0,
            "result": "PASS_NONEXPERIMENTAL_FIXTURE_ONLY",
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
        "live_claim_audit": live_claim_audit,
        "production_entropy_requested": False,
        "production_seed_freeze_authorized": False,
        "claim_creation_authorized": False,
        "game_initialization_authorized": False,
        "gameplay_authorized": False,
        "outcome_exposure_authorized": False,
        "automatic_second_replacement_authorized": False,
        "official_counters": {
            "new_seeds": 0,
            "new_claims": 0,
            "new_games": 0,
            "new_outcomes": 0,
        },
        "next_boundary": "SEPARATELY_RECORDED_ONE_SHOT_PRODUCTION_SEED_FREEZE_AUTHORITY_MAY_BE_CONSIDERED_ONLY_AFTER_INDEPENDENT_VALIDATION_ACCEPTANCE",
    }

    artifacts = {
        "fixture-members.txt": vector,
        "fixture-assignments.csv": assignments,
        "fixture-manifest.json": canonical_json(manifest),
    }
    for name, data in artifacts.items():
        write_new_fsynced(output / name, data)
    checksums = {name: sha256(data) for name, data in artifacts.items()}
    write_new_fsynced(
        output / "artifacts.sha256",
        ("\n".join(f"{digest}  {name}" for name, digest in sorted(checksums.items())) + "\n").encode(),
    )
    return {
        "status": "SEED_FREE_R1_FIXTURE_VALIDATED",
        "minimum_exclusion_count": len(excluded),
        "fixture_member_count": len(members),
        "fixture_vector_sha256": sha256(vector),
        "fixture_assignments_sha256": sha256(assignments),
        "fixture_manifest_sha256": checksums["fixture-manifest.json"],
        "production_entropy_requested": False,
        "official_counters": manifest["official_counters"],
    }


def run_adversarial_checks(excluded: set[int]) -> dict[str, object]:
    valid = fixture_members()
    if member_errors(valid, excluded):
        raise ValueError("fixed fixture failed baseline validation")
    retired = min(excluded)
    cases = {
        "zero": [0, valid[1], valid[2], valid[3]],
        "duplicate": [valid[0], valid[0], valid[2], valid[3]],
        "retired_overlap": [retired, valid[1], valid[2], valid[3]],
        "short": valid[:3],
    }
    observed: dict[str, list[str]] = {}
    for label, members in cases.items():
        errors = member_errors(members, excluded)
        if not errors:
            raise AssertionError(f"adversarial case {label} was not rejected")
        observed[label] = errors
    return {
        "status": "PASS",
        "cases": observed,
        "production_entropy_requested": False,
        "gameplay_reachable": False,
    }


def verify_new_source_is_entropy_free(script_path: Path) -> dict[str, object]:
    source = script_path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    forbidden_import_roots = {"random", "secrets", "subprocess", "socket", "requests", "httpx", "urllib"}
    imports: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            imports.update(alias.name.split(".")[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module:
            imports.add(node.module.split(".")[0])
        elif isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute):
            if isinstance(node.func.value, ast.Name) and node.func.value.id == "os" and node.func.attr == "urandom":
                raise ValueError("os.urandom is forbidden in the R1 seed-free validator")
    forbidden = sorted(imports & forbidden_import_roots)
    if forbidden:
        raise ValueError(f"forbidden seed-free validator imports: {forbidden}")
    return {
        "status": "PASS",
        "os_urandom_calls": 0,
        "forbidden_imports": [],
        "network_client_imports": [],
        "claim_helper_invocations": 0,
        "gameplay_entrypoints": 0,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--preflight", action="store_true")
    modes.add_argument("--adversarial-self-test", action="store_true")
    modes.add_argument("--validate-fixture", action="store_true")
    parser.add_argument("--prior-terror-smoke-dir", type=Path, required=True)
    parser.add_argument("--prior-terror-replication-dir", type=Path, required=True)
    parser.add_argument("--original-monster-dir", type=Path, required=True)
    parser.add_argument("--live-refs-json", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[3]
    verify_c2_source_gate(root)
    source_guard = verify_new_source_is_entropy_free(Path(__file__).resolve())
    excluded, exclusion_audit = reconstruct_minimum_exclusion(
        root,
        args.prior_terror_smoke_dir,
        args.prior_terror_replication_dir,
        args.original_monster_dir,
    )
    live_claim_audit = verify_live_claim_refs(args.live_refs_json)

    if args.preflight:
        result = {
            "status": "READY_SEED_FREE_R1_VALIDATION_ONLY",
            "minimum_exclusion_count": len(excluded),
            "production_entropy_requested": False,
            "claim_creation_authorized": False,
            "gameplay_authorized": False,
            "source_guard": source_guard,
            "exclusion_audit": exclusion_audit,
            "live_claim_audit": live_claim_audit,
        }
    elif args.adversarial_self_test:
        result = run_adversarial_checks(excluded)
    else:
        if args.output_dir is None:
            parser.error("--output-dir is required with --validate-fixture")
        result = build_fixture_bundle(args.output_dir, excluded, exclusion_audit, live_claim_audit)

    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
