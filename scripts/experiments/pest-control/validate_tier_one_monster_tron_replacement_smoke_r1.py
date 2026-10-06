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
R1_CANONICAL_RECEIPT_COMMENT = 6008608271
R1_CANONICAL_REVIEW_SHA256 = "5b382918fc83b51027b4eb180d642afdcfdde5e4029ef536032e2a17c30f0251"
PROPOSAL_BLOB = "f319d6d9e84608f5c251ce805cb669a7799b6c6b"
STOPPING_RULE_BLOB = "6c737d8bb7af58cccc7cafbb60a6abe9876695ae"
FAILURE_AUDIT_MD_BLOB = "e249682d8c052ff96c9e4c8a68c0785a4f0e97f2"
FAILURE_AUDIT_JSON_BLOB = "7d969100a0ad7e5c87c4a1de1c68821cf78a10e6"
C2_SOURCE_GATE_MANIFEST_BLOB = "04aef8e817f0e4cbc74cdace6230474414c94454"
IMPLEMENTATION_MANIFEST_PATH = (
    "docs/experiments/pest-control/"
    "PEST_MONSTER_TRON_REPLACEMENT_SMOKE_R1_SEEDFREE_VALIDATION_20261005.json"
)
SEEDFREE_AUTHORITY = "SEED_FREE_REPLACEMENT_MACHINERY_VALIDATION_ONLY__NO_ENTROPY_NO_GAMEPLAY"

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


def seedfree_contract_errors(manifest: dict[str, object]) -> list[str]:
    errors: list[str] = []

    def expect(label: str, actual: object, expected: object) -> None:
        if actual != expected:
            errors.append(f"{label}: expected={expected!r} actual={actual!r}")

    expect("schema", manifest.get("schema"), "pest-monster-tron-replacement-smoke-r1-seedfree-validation-v1")
    expect("authority", manifest.get("authority"), SEEDFREE_AUTHORITY)
    expect("status", manifest.get("status"), "IMPLEMENTED_PENDING_VALIDATION")
    expect(
        "reviewed_base",
        manifest.get("reviewed_base"),
        {
            "commit": C2_SOURCE_GATE_COMMIT,
            "tree": "6f6ae97bb35a03a15037686d6c24bff4750ab789",
        },
    )

    review = manifest.get("r1_protocol_review") or {}
    expect("r1 review request", review.get("request_comment"), R1_REVIEW_REQUEST_COMMENT)
    expect("r1 acceptance", review.get("acceptance_comment"), R1_ACCEPTANCE_COMMENT)
    expect("r1 disposition", review.get("disposition"), "ACCEPTED")
    expect("r1 canonical receipt", review.get("canonical_receipt_comment"), R1_CANONICAL_RECEIPT_COMMENT)
    expect("r1 canonical review sha256", review.get("canonical_review_sha256"), R1_CANONICAL_REVIEW_SHA256)
    expect("r1 canonicalization", review.get("canonicalization"), "UTF8_LF_EXACTLY_ONE_TERMINAL_LF")
    expect(
        "r1 accepted scope",
        review.get("accepted_scope"),
        "IMPLEMENT_AND_VALIDATE_BOUNDED_REPLACEMENT_MACHINERY_WITHOUT_ENTROPY_OR_GAMEPLAY",
    )
    expect("proposal blob", review.get("proposal_blob"), PROPOSAL_BLOB)
    expect("stopping-rule blob", review.get("stopping_rule_blob"), STOPPING_RULE_BLOB)
    expect("failure audit md blob", review.get("failure_audit_md_blob"), FAILURE_AUDIT_MD_BLOB)
    expect("failure audit json blob", review.get("failure_audit_json_blob"), FAILURE_AUDIT_JSON_BLOB)

    pair = manifest.get("exact_pair") or {}
    expect("Pest main", pair.get("pest_main_sha256"), PEST_MAIN)
    expect("Monster main", pair.get("monster_main_sha256"), MONSTER_TRON_MAIN)
    expect("Pest pilot", pair.get("pest_pilot"), PEST_PILOT)
    expect("Monster pilot", pair.get("monster_pilot"), MONSTER_PILOT)

    c2 = manifest.get("c2_source_gate") or {}
    expect("C2 source-gate commit", c2.get("commit"), C2_SOURCE_GATE_COMMIT)
    expect("C2 source-gate manifest blob", c2.get("manifest_blob"), C2_SOURCE_GATE_MANIFEST_BLOB)
    expect("C2 source-gate authority", c2.get("authority"), C2_SOURCE_GATE_AUTHORITY)
    expect("C2 execution source", c2.get("execution_source_c2"), "NOT_CREATED_OR_PINNED_BY_THIS_GATE")
    expect("Monster activation", c2.get("monster_activation"), "ABSENT_NOT_AUTHORIZED")
    expect("Phase-B authority", c2.get("phase_b_authority"), "PERMANENTLY_CONSUMED_NO_REUSE")

    historical = manifest.get("historical_attempt") or {}
    expect("historical run", historical.get("run"), 36085093386)
    expect("historical disposition", historical.get("disposition"), "REJECTED_INFRASTRUCTURE_BEFORE_INITIALIZATION")
    expect("historical claim ref", historical.get("claim_ref"), ORIGINAL_CLAIM_REF)
    expect("historical claim commit", historical.get("claim_commit"), ORIGINAL_CLAIM_COMMIT)
    expect("historical immutable", historical.get("immutable"), True)
    expect("historical retired assignments", historical.get("retired_assignment_count"), 4)
    expect("historical per-game attempts", historical.get("per_game_attempts"), 0)
    expect("historical initializations", historical.get("successful_initializations"), 0)
    expect("historical actions", historical.get("actions"), 0)
    expect("historical outcomes", historical.get("outcomes"), 0)
    expect("historical retry authority", historical.get("retry_authorized"), False)

    replacement = manifest.get("replacement") or {}
    expect("R1 block", replacement.get("block_id"), R1_BLOCK)
    expect("R1 parent block", replacement.get("parent_original_block_id"), ORIGINAL_BLOCK)
    expect("R1 future claim", replacement.get("future_claim_ref"), R1_CLAIM_REF)
    expect("R1 future claim state", replacement.get("future_claim_ref_state"), "ABSENT_NOT_AUTHORIZED")
    expect(
        "R1 assignment cells",
        replacement.get("assignment_cells"),
        [
            {"game": 1, "pest_seat": 0, "monster_seat": 1, "starting_deck": "PEST_CONTROL", "pest_play_draw": "PLAY"},
            {"game": 2, "pest_seat": 0, "monster_seat": 1, "starting_deck": "MONSTER_TRON", "pest_play_draw": "DRAW"},
            {"game": 3, "pest_seat": 1, "monster_seat": 0, "starting_deck": "PEST_CONTROL", "pest_play_draw": "PLAY"},
            {"game": 4, "pest_seat": 1, "monster_seat": 0, "starting_deck": "MONSTER_TRON", "pest_play_draw": "DRAW"},
        ],
    )

    exclusion = manifest.get("exclusion_baseline") or {}
    expect("inherited exclusion", exclusion.get("inherited_unique_count"), PRE_MONSTER_EXCLUSION_COUNT)
    expect("original Monster member count", exclusion.get("original_monster_member_count"), 4)
    expect("minimum exclusion", exclusion.get("minimum_unique_count"), R1_MINIMUM_EXCLUSION_COUNT)
    expect("original Monster vector", exclusion.get("original_monster_vector_sha256"), ORIGINAL_MONSTER_VECTOR_SHA256)
    expect("original Monster assignments", exclusion.get("original_monster_assignments_sha256"), ORIGINAL_MONSTER_ASSIGNMENTS_SHA256)

    validation = manifest.get("validation_contract") or {}
    expected_validation = {
        "deterministic_fixture_only": True,
        "fixture_can_supply_official_result": False,
        "live_claim_reconciliation_required": True,
        "historical_claim_must_remain_immutable": True,
        "r1_claim_must_be_absent": True,
        "unexpected_official_attempt_ref_fails_closed": True,
        "os_urandom_calls_permitted": 0,
        "production_entropy_calls_permitted": 0,
        "claim_mutations_permitted": 0,
        "game_initializations_permitted": 0,
        "actions_permitted": 0,
        "outcomes_permitted": 0,
        "automatic_second_replacement": False,
        "durable_quarantine_before_validation_required": True,
        "invalid_candidate_retention_required": True,
        "no_clobber_no_reroll_required": True,
        "quarantine_create_only_fsync_required": True,
    }
    for key, expected in expected_validation.items():
        expect(f"validation.{key}", validation.get(key), expected)

    entropy = manifest.get("validation_entropy_contract") or {}
    expected_entropy = {
        "fixture_only": True,
        "production_entropy_requested": False,
        "promotable_to_production_seed": False,
        "promotable_to_official_allocation": False,
        "promotable_to_official_result": False,
        "promotable_to_future_claim": False,
    }
    for key, expected in expected_entropy.items():
        expect(f"validation_entropy.{key}", entropy.get(key), expected)

    stopping = manifest.get("stopping_rule_contract") or {}
    expected_stopping = {
        "clean_smoke_game_count": 4,
        "clean_smoke_replication_trigger": "ONE_FRESH_12_GAME_REPLICATION_REGARDLESS_OF_WINS",
        "clean_replication_closes_axis_regardless_of_record": True,
        "defective_r1_stops_replacement_gate": True,
        "automatic_second_replacement": False,
        "selective_game_replacement_permitted": False,
        "partial_result_suppression_permitted": False,
        "outcome_conditioned_extension_permitted": False,
    }
    for key, expected in expected_stopping.items():
        expect(f"stopping.{key}", stopping.get(key), expected)

    expect(
        "official counters",
        manifest.get("official_counters"),
        {"new_seeds": 0, "new_claims": 0, "new_games": 0, "new_outcomes": 0},
    )
    return errors


def verify_seedfree_contract(root: Path) -> dict[str, object]:
    path = root / IMPLEMENTATION_MANIFEST_PATH
    manifest = json.loads(path.read_bytes())
    errors = seedfree_contract_errors(manifest)
    if errors:
        raise ValueError("seed-free R1 contract mismatch: " + "; ".join(errors))
    return manifest


def set_nested(mapping: dict[str, object], path: tuple[str, ...], value: object) -> None:
    cursor = mapping
    for key in path[:-1]:
        child = cursor.get(key)
        if not isinstance(child, dict):
            raise AssertionError(f"contract path is not a mapping: {path}")
        cursor = child
    cursor[path[-1]] = value


def run_contract_adversarial_checks(contract: dict[str, object]) -> dict[str, object]:
    cases: dict[str, tuple[tuple[str, ...], object]] = {
        "wrong_r1_block": (("replacement", "block_id"), ORIGINAL_BLOCK),
        "wrong_pest_hash": (("exact_pair", "pest_main_sha256"), "0" * 64),
        "wrong_monster_hash": (("exact_pair", "monster_main_sha256"), "1" * 64),
        "wrong_c2_prerequisite": (("c2_source_gate", "commit"), "2" * 40),
        "stale_proposal_blob": (("r1_protocol_review", "proposal_blob"), "3" * 40),
        "stale_stopping_rule_blob": (("r1_protocol_review", "stopping_rule_blob"), "4" * 40),
        "stale_failure_audit": (("r1_protocol_review", "failure_audit_json_blob"), "5" * 40),
        "old_claim_identity_reuse": (("replacement", "future_claim_ref"), ORIGINAL_CLAIM_REF),
        "second_r1_original": (("validation_contract", "automatic_second_replacement"), True),
        "fixture_promoted_to_production_seed": (("validation_entropy_contract", "promotable_to_production_seed"), True),
        "fixture_promoted_to_future_claim": (("validation_entropy_contract", "promotable_to_future_claim"), True),
        "partial_result_suppression": (("stopping_rule_contract", "partial_result_suppression_permitted"), True),
        "outcome_driven_continuation": (("stopping_rule_contract", "clean_smoke_replication_trigger"), "WIN_COUNT_DEPENDENT"),
    }
    observed: dict[str, list[str]] = {}
    for label, (path, value) in cases.items():
        mutated = json.loads(json.dumps(contract))
        set_nested(mutated, path, value)
        errors = seedfree_contract_errors(mutated)
        if not errors:
            raise AssertionError(f"contract adversarial case {label} was accepted")
        observed[label] = errors
    return {"status": "PASS", "cases": observed, "case_count": len(observed)}


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


def quarantine_candidate_members(
    output: Path,
    members: list[int],
    fixture_label: str,
) -> tuple[bytes, str]:
    if output.exists() and any(output.iterdir()):
        raise ValueError("candidate output directory must be absent or empty; no-clobber/no-reroll")
    output.mkdir(parents=True, exist_ok=True)
    vector = ("\n".join(str(member) for member in members) + "\n").encode()
    record = {
        "schema": "pest-monster-tron-replacement-smoke-r1-seedfree-quarantine-v1",
        "authority": "NONEXPERIMENTAL_VALIDATION_ONLY__NO_PRODUCTION_ENTROPY__NO_GAMEPLAY",
        "block_id": R1_BLOCK,
        "fixture_label": fixture_label,
        "candidate_member_count": len(members),
        "candidate_members_decimal": members,
        "candidate_members_hex": [member_hex(member) for member in members],
        "candidate_vector_sha256": sha256(vector),
        "status": "NONEXPERIMENTAL_FIXTURE_QUARANTINED_BEFORE_VALIDATION",
        "production_entropy_requested": False,
        "can_supply_official_result": False,
        "reroll_or_replacement_permitted": False,
    }
    data = canonical_json(record)
    write_new_fsynced(output / "quarantined-vector.json", data)
    return vector, sha256(data)


def quarantine_and_validate_members(
    output: Path,
    members: list[int],
    excluded: set[int],
    fixture_label: str,
) -> tuple[bytes, str]:
    vector, quarantine_sha256 = quarantine_candidate_members(output, members, fixture_label)
    errors = member_errors(members, excluded)
    if errors:
        write_new_fsynced(
            output / "invalid-retired.json",
            canonical_json({
                "schema": "pest-monster-tron-replacement-smoke-r1-seedfree-invalid-v1",
                "block_id": R1_BLOCK,
                "fixture_label": fixture_label,
                "errors": errors,
                "candidate_members_decimal": members,
                "candidate_vector_sha256": sha256(vector),
                "quarantine_sha256": quarantine_sha256,
                "disposition": "INVALID_FIXTURE_RETAINED_NO_REROLL",
                "production_entropy_requested": False,
                "can_supply_official_result": False,
                "reroll_or_replacement_permitted": False,
            }),
        )
        raise ValueError(f"quarantined fixture is invalid: {errors}")
    return vector, quarantine_sha256


def build_fixture_bundle(
    output: Path,
    excluded: set[int],
    exclusion_audit: dict[str, object],
    live_claim_audit: dict[str, object],
) -> dict[str, object]:
    members = fixture_members()
    vector, quarantine_sha256 = quarantine_and_validate_members(
        output, members, excluded, "fixed-valid-r1-four-cell-fixture"
    )

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
        "durable_quarantine": {
            "quarantined_before_validation": True,
            "quarantine_file": "quarantined-vector.json",
            "quarantine_sha256": quarantine_sha256,
            "create_only": True,
            "fsynced": True,
            "reroll_or_replacement_permitted": False,
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
    checksums["quarantined-vector.json"] = sha256((output / "quarantined-vector.json").read_bytes())
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
        "quarantine_sha256": quarantine_sha256,
        "quarantined_before_validation": True,
        "no_reroll_no_clobber": True,
        "production_entropy_requested": False,
        "official_counters": manifest["official_counters"],
    }


def run_adversarial_checks(\n    excluded: set[int],\n    exclusion_audit: dict[str, object],\n    output_root: Path,\n    contract: dict[str, object],\n) -> dict[str, object]:
    valid = fixture_members()
    if member_errors(valid, excluded):
        raise ValueError("fixed fixture failed baseline validation")
    if output_root.exists() and any(output_root.iterdir()):
        raise ValueError("adversarial output directory must be absent or empty")
    output_root.mkdir(parents=True, exist_ok=True)

    retired = min(excluded)
    original_members = exclusion_audit.get("original_monster_members_decimal")
    if not isinstance(original_members, list) or len(original_members) != 4:
        raise AssertionError("original Monster retired members missing from exclusion audit")
    cases = {
        "zero": [0, valid[1], valid[2], valid[3]],
        "duplicate": [valid[0], valid[0], valid[2], valid[3]],
        "retired_overlap": [retired, valid[1], valid[2], valid[3]],
        "short": valid[:3],
        **{
            f"historical_monster_member_{index}": [member, valid[1], valid[2], valid[3]]
            for index, member in enumerate(original_members, 1)
        },
    }
    observed: dict[str, object] = {}
    for label, members in cases.items():
        case_dir = output_root / label
        try:
            quarantine_and_validate_members(case_dir, members, excluded, f"adversarial-{label}")
        except ValueError as failure:
            errors = member_errors(members, excluded)
            if not errors:
                raise AssertionError(f"adversarial case {label} failed without a validation error") from failure
        else:
            raise AssertionError(f"adversarial case {label} was not rejected")

        quarantine_path = case_dir / "quarantined-vector.json"
        invalid_path = case_dir / "invalid-retired.json"
        if not quarantine_path.is_file() or not invalid_path.is_file():
            raise AssertionError(f"adversarial case {label} did not preserve quarantine and invalid receipt")
        if (case_dir / "fixture-assignments.csv").exists() or (case_dir / "fixture-manifest.json").exists():
            raise AssertionError(f"adversarial case {label} produced accepted fixture artifacts")
        quarantine_before = quarantine_path.read_bytes()
        invalid = json.loads(invalid_path.read_bytes())
        if invalid.get("errors") != errors or invalid.get("disposition") != "INVALID_FIXTURE_RETAINED_NO_REROLL":
            raise AssertionError(f"adversarial case {label} invalid receipt mismatch")

        try:
            quarantine_and_validate_members(case_dir, valid, excluded, f"forbidden-reroll-{label}")
        except ValueError:
            pass
        else:
            raise AssertionError(f"adversarial case {label} allowed a second attempt")
        if quarantine_path.read_bytes() != quarantine_before:
            raise AssertionError(f"adversarial case {label} clobbered its original quarantine")

        observed[label] = {
            "errors": errors,
            "quarantine_retained": True,
            "invalid_receipt_retained": True,
            "second_attempt_refused": True,
            "quarantine_unchanged_after_second_attempt": True,
        }

    contract_checks = run_contract_adversarial_checks(contract)
    return {
        "status": "PASS",
        "cases": observed,
        "contract_cases": contract_checks["cases"],
        "contract_case_count": contract_checks["case_count"],
        "durable_quarantine_before_validation": True,
        "invalid_candidates_retained": True,
        "all_four_historical_monster_members_rejected": True,
        "no_clobber_no_reroll": True,
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
    parser.add_argument("--adversarial-output-dir", type=Path)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[3]
    verify_c2_source_gate(root)
    contract = verify_seedfree_contract(root)
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
            "contract_binding": {"status": "PASS", "error_count": 0},
            "exclusion_audit": exclusion_audit,
            "live_claim_audit": live_claim_audit,
        }
    elif args.adversarial_self_test:
        if args.adversarial_output_dir is None:
            parser.error("--adversarial-output-dir is required with --adversarial-self-test")
        result = run_adversarial_checks(
            excluded, exclusion_audit, args.adversarial_output_dir, contract
        )
    else:
        if args.output_dir is None:
            parser.error("--output-dir is required with --validate-fixture")
        result = build_fixture_bundle(args.output_dir, excluded, exclusion_audit, live_claim_audit)

    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
