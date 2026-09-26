"""Fail-closed official artifact/completeness contract for Industrial Waste v2 R1.

This module validates evidence *after* an allocation runner has produced it. It cannot initialize a
game, create a claim or attempt journal, submit actions, expose provisional comparison results, or
apply the R1 decision rule. Its purpose is to make the eventual 512-member corpus mechanically
complete, unique, provenance-bound and explicit about invalid attempts.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any

PROTOCOL_ID = "IW_V2_R1_ENGINE_STRUCTURAL_2026_09_25"
SCHEMA = "industrial-waste-v2-r1-allocation-artifact-v1"
DECKS = (
    "IMMUTABLE_SUBMITTED_V1_0",
    "COMPACT_LOOP",
    "RECURSIVE_EGGS",
    "LEAN_TRON_HYBRID",
)
SCHEDULES = ("PLAY_SKIP_FIRST_DRAW", "DRAW_TAKE_FIRST_DRAW")
VALID_TERMINALS = {"REAL_TERMINAL", "TURN_CAP", "ACTION_CAP", "DRAW"}
INVALID_TERMINALS = {"REJECTED_ACTION", "EXCEPTION", "UNRESOLVED_TELEMETRY"}
METRICS = (
    "loop_ready_by_t8",
    "conversion_by_t8",
    "colored_mana_failure",
    "total_mana_stranded_at_t4",
)
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def _require_sha256(value: Any, label: str) -> str:
    if not isinstance(value, str) or SHA256_RE.fullmatch(value) is None:
        raise ValueError(f"{label} must be a lowercase SHA-256 hex digest")
    return value


def _require_int(value: Any, label: str, minimum: int | None = None) -> int:
    if type(value) is not int:
        raise ValueError(f"{label} must be an integer")
    if minimum is not None and value < minimum:
        raise ValueError(f"{label} must be >= {minimum}")
    return value


def _allocation_index(plan: dict[str, Any]) -> dict[str, dict[str, Any]]:
    if plan.get("protocol_id") != PROTOCOL_ID:
        raise ValueError("allocation plan protocol drift")
    allocations = plan.get("allocations")
    if not isinstance(allocations, list) or len(allocations) != 512:
        raise ValueError("allocation plan must contain exactly 512 allocations")
    indexed: dict[str, dict[str, Any]] = {}
    for expected_index, allocation in enumerate(allocations, 1):
        if not isinstance(allocation, dict):
            raise ValueError("allocation plan member is not an object")
        allocation_id = allocation.get("allocation_id")
        if allocation_id != f"IW_V2_R1_{expected_index:04d}":
            raise ValueError("allocation plan id/order drift")
        if allocation_id in indexed:
            raise ValueError("allocation plan contains a duplicate allocation id")
        if allocation.get("allocation_index") != expected_index:
            raise ValueError("allocation plan index drift")
        if allocation.get("row") not in range(1, 65):
            raise ValueError("allocation plan row drift")
        if allocation.get("schedule") not in SCHEDULES:
            raise ValueError("allocation plan schedule drift")
        if allocation.get("deck_id") not in DECKS:
            raise ValueError("allocation plan deck drift")
        indexed[allocation_id] = allocation
    return indexed


def validate_record(record: dict[str, Any], expected: dict[str, Any]) -> dict[str, Any]:
    if record.get("schema") != SCHEMA:
        raise ValueError("allocation artifact schema drift")
    if record.get("protocol_id") != PROTOCOL_ID:
        raise ValueError("allocation artifact protocol drift")

    allocation_id = record.get("allocation_id")
    if allocation_id != expected.get("allocation_id"):
        raise ValueError("allocation id does not match frozen plan")
    if record.get("allocation_index") != expected.get("allocation_index"):
        raise ValueError("allocation index does not match frozen plan")
    if record.get("row") != expected.get("row"):
        raise ValueError("allocation row does not match frozen plan")
    if record.get("schedule") != expected.get("schedule"):
        raise ValueError("allocation schedule does not match frozen plan")
    if record.get("deck") != expected.get("deck_id"):
        raise ValueError("allocation deck does not match frozen plan")
    if record.get("deck_sha256") != expected.get("deck_sha256"):
        raise ValueError("allocation deck digest does not match frozen plan")

    provenance = record.get("provenance")
    if not isinstance(provenance, dict):
        raise ValueError("provenance object missing")
    source_commit = provenance.get("source_commit")
    if not isinstance(source_commit, str) or re.fullmatch(r"[0-9a-f]{40}", source_commit) is None:
        raise ValueError("source_commit must be a full lowercase Git SHA")
    for field in (
        "allocation_plan_sha256",
        "runtime_identity_receipt_sha256",
        "runner_binding_sha256",
        "artifact_contract_sha256",
        "metric_projection_sha256",
    ):
        _require_sha256(provenance.get(field), f"provenance.{field}")

    execution = record.get("execution")
    if not isinstance(execution, dict):
        raise ValueError("execution object missing")
    terminal = execution.get("terminal_status")
    if terminal not in VALID_TERMINALS | INVALID_TERMINALS:
        raise ValueError("terminal status is not admitted by the frozen contract")
    submitted = _require_int(execution.get("submitted_actions"), "execution.submitted_actions", 0)
    accepted = _require_int(execution.get("accepted_actions"), "execution.accepted_actions", 0)
    if accepted > submitted:
        raise ValueError("accepted_actions cannot exceed submitted_actions")
    _require_int(execution.get("own_turns_started"), "execution.own_turns_started", 0)
    _require_int(execution.get("own_turns_completed"), "execution.own_turns_completed", 0)
    if submitted > 4000:
        raise ValueError("submitted action cap exceeded")
    if execution.get("own_turns_completed") > 8:
        raise ValueError("own-turn cap exceeded")

    evidence = record.get("evidence")
    if not isinstance(evidence, dict):
        raise ValueError("evidence object missing")
    _require_sha256(evidence.get("action_transcript_sha256"), "evidence.action_transcript_sha256")
    _require_sha256(evidence.get("raw_telemetry_sha256"), "evidence.raw_telemetry_sha256")
    _require_sha256(evidence.get("checkpoint_telemetry_sha256"), "evidence.checkpoint_telemetry_sha256")
    _require_sha256(evidence.get("initial_ordering_sha256"), "evidence.initial_ordering_sha256")
    failure_digest = evidence.get("failure_bytes_sha256")
    if failure_digest is not None:
        _require_sha256(failure_digest, "evidence.failure_bytes_sha256")

    projection = record.get("metric_projection")
    if not isinstance(projection, dict):
        raise ValueError("metric_projection object missing")
    validity = projection.get("validity")
    metrics = {name: projection.get(name) for name in METRICS}
    reason = projection.get("invalid_reason")

    if terminal in VALID_TERMINALS:
        if validity != "VALID":
            raise ValueError("valid terminal/cap/draw evidence must project as VALID")
        if reason is not None:
            raise ValueError("VALID evidence cannot carry invalid_reason")
        if failure_digest is not None:
            raise ValueError("VALID evidence cannot carry failure bytes")
        if any(type(value) is not bool for value in metrics.values()):
            raise ValueError("VALID evidence requires all four resolved Boolean decision metrics")
    else:
        if validity != "INVALID":
            raise ValueError("rejected/exception/unresolved evidence must project as INVALID")
        if not isinstance(reason, str) or not reason.strip():
            raise ValueError("INVALID evidence requires an invalid_reason")
        if failure_digest is None:
            raise ValueError("INVALID evidence must preserve failure bytes")
        if any(value is not None for value in metrics.values()):
            raise ValueError("INVALID evidence must not expose decision-rule metric values")

    return {
        "allocation_id": allocation_id,
        "terminal_status": terminal,
        "validity": validity,
    }


def validate_complete_corpus(records: list[dict[str, Any]], plan: dict[str, Any]) -> dict[str, Any]:
    expected = _allocation_index(plan)
    if len(records) != 512:
        raise ValueError("official R1 corpus requires exactly 512 allocation artifacts")
    seen: set[str] = set()
    valid = invalid = 0
    for record in records:
        if not isinstance(record, dict):
            raise ValueError("allocation artifact is not an object")
        allocation_id = record.get("allocation_id")
        if not isinstance(allocation_id, str) or allocation_id not in expected:
            raise ValueError("allocation artifact is not in the frozen 512-member plan")
        if allocation_id in seen:
            raise ValueError("duplicate official allocation artifact")
        summary = validate_record(record, expected[allocation_id])
        seen.add(allocation_id)
        if summary["validity"] == "VALID":
            valid += 1
        else:
            invalid += 1
    if seen != set(expected):
        raise ValueError("official R1 corpus is incomplete")
    return {
        "schema": "industrial-waste-v2-r1-corpus-completeness-v1",
        "protocol_id": PROTOCOL_ID,
        "allocation_artifacts": 512,
        "valid_artifacts": valid,
        "invalid_artifacts": invalid,
        "complete_fixed_grid": True,
        "decision_rule_admission_ready": invalid == 0,
        "authorizes_execution": False,
        "promotes_deck": False,
    }


def descriptor() -> dict[str, Any]:
    payload = {
        "schema": "industrial-waste-v2-r1-artifact-contract-capability-v1",
        "status": "QUALIFIED_SEED_FREE_ARTIFACT_AND_COMPLETENESS_CONTRACT_ONLY",
        "protocol_id": PROTOCOL_ID,
        "allocation_artifact_schema": SCHEMA,
        "required_allocation_count": 512,
        "valid_terminal_statuses": sorted(VALID_TERMINALS),
        "invalid_terminal_statuses": sorted(INVALID_TERMINALS),
        "decision_metrics": list(METRICS),
        "invariants": [
            "each artifact must match exactly one frozen allocation id/index/row/schedule/deck",
            "the complete corpus contains all 512 frozen allocations exactly once",
            "valid terminal/cap/draw evidence exposes all four Boolean decision metrics",
            "rejected/exception/unresolved evidence preserves failure bytes and exposes no decision metrics",
            "submitted actions never exceed 4000 and completed own turns never exceed 8",
            "all provenance and evidence digests are explicit SHA-256 values",
        ],
        "official_counters": {
            "allocations_initialized": 0,
            "actions_submitted": 0,
            "comparative_outcomes_exposed": 0,
        },
        "claim_creation_authorized": False,
        "attempt_journal_creation_authorized": False,
        "initialization_admitted": False,
        "execution_authorized": False,
        "candidate_elimination_authorized": False,
        "candidate_promotion_authorized": False,
        "closes_binding_when_formally_accepted": "official_r1_artifact_schema_and_completeness_contract",
        "next_gate": "Bind the real official corpus runner and complete metric projection, then issue a successor runtime-binding receipt before any durable official claim.",
    }
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    payload["contract_sha256"] = hashlib.sha256(canonical).hexdigest()
    return payload


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--descriptor-out", type=Path)
    args = parser.parse_args()
    payload = json.dumps(descriptor(), indent=2, sort_keys=True) + "\n"
    if args.descriptor_out:
        args.descriptor_out.parent.mkdir(parents=True, exist_ok=True)
        args.descriptor_out.write_text(payload, encoding="utf-8")
    print(payload, end="")


if __name__ == "__main__":
    main()
