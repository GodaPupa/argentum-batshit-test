"""Fail-closed R1 execution claim / pre-initialization journal validation.

This module never creates an official claim, initializes a game, reads an R1 ordering row,
or exposes comparative output. It validates the durable repository records that must already
exist before a future authorized runner may initialize allocation 1/512.
"""
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

PROTOCOL_ID = "IW_V2_R1_ENGINE_STRUCTURAL_2026_09_25"
CLAIM_SCHEMA = "industrial-waste-v2-r1-execution-claim-v1"
JOURNAL_SCHEMA = "industrial-waste-v2-r1-attempt-journal-v1"
RUNTIME_STATUS = "ACCEPTED_R1_RUNTIME_BINDING"
AUTHORIZATION_STATUS = "AUTHORIZED_R1_EXECUTION"
HEX40 = re.compile(r"^[0-9a-f]{40}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")


def _json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected JSON object")
    return value


def _safe_repo_path(root: Path, raw: str) -> Path:
    if not raw or Path(raw).is_absolute():
        raise ValueError("bound path must be a non-empty repository-relative path")
    root = root.resolve()
    resolved = (root / raw).resolve()
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"bound path escapes repository root: {raw}") from exc
    return resolved


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _binding(root: Path, record: dict[str, Any], key: str, required_status: str) -> dict[str, Any]:
    binding = record.get(key)
    if not isinstance(binding, dict):
        raise ValueError(f"claim missing {key} binding")
    raw_path = binding.get("path")
    digest = binding.get("sha256")
    if not isinstance(raw_path, str) or not isinstance(digest, str) or not HEX64.fullmatch(digest):
        raise ValueError(f"{key} binding requires path and lowercase SHA-256")
    path = _safe_repo_path(root, raw_path)
    if not path.is_file():
        raise ValueError(f"{key} bound file does not exist: {raw_path}")
    actual = _sha256(path)
    if actual != digest:
        raise ValueError(f"{key} digest mismatch: expected {digest}, got {actual}")
    payload = _json(path)
    if payload.get("protocol_id") != PROTOCOL_ID:
        raise ValueError(f"{key} protocol mismatch")
    if payload.get("status") != required_status:
        raise ValueError(f"{key} is not accepted for R1 execution")
    return {"path": raw_path, "sha256": digest, "status": payload["status"]}


def _journal_records(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            raise ValueError(f"journal line {number} is blank")
        value = json.loads(line)
        if not isinstance(value, dict):
            raise ValueError(f"journal line {number} is not an object")
        records.append(value)
    if not records:
        raise ValueError("attempt journal is empty")
    return records


def validate_preinitialization_guard(
    root: Path,
    claim_path: Path,
    journal_path: Path,
    expected_head: str,
) -> dict[str, Any]:
    """Validate the exact committed guard state required before first initialization."""
    root = root.resolve()
    if not HEX40.fullmatch(expected_head):
        raise ValueError("expected branch HEAD must be a lowercase 40-hex commit")

    claim = _json(claim_path)
    if claim.get("schema") != CLAIM_SCHEMA:
        raise ValueError("wrong execution-claim schema")
    if claim.get("status") != "ACTIVE_PREINITIALIZATION":
        raise ValueError("execution claim is not active pre-initialization")
    if claim.get("protocol_id") != PROTOCOL_ID:
        raise ValueError("execution claim protocol mismatch")
    claim_id = claim.get("claim_id")
    if not isinstance(claim_id, str) or not claim_id.strip():
        raise ValueError("execution claim requires a non-empty claim_id")
    if claim.get("expected_branch_head") != expected_head:
        raise ValueError("execution claim branch HEAD mismatch")
    if claim.get("created_before_initialization") is not True:
        raise ValueError("execution claim does not attest pre-initialization creation")

    runtime = _binding(root, claim, "runtime_binding", RUNTIME_STATUS)
    authorization = _binding(root, claim, "execution_authorization", AUTHORIZATION_STATUS)

    records = _journal_records(journal_path)
    declared = [r for r in records if r.get("record_type") == "ATTEMPT_DECLARED"]
    if len(declared) != 1:
        raise ValueError("journal must contain exactly one durable attempt declaration before initialization")
    first = records[0]
    if first is not declared[0]:
        raise ValueError("attempt declaration must be the first journal record")
    if first.get("schema") != JOURNAL_SCHEMA:
        raise ValueError("wrong attempt-journal schema")
    if first.get("protocol_id") != PROTOCOL_ID or first.get("claim_id") != claim_id:
        raise ValueError("attempt journal is not bound to this protocol and claim")
    if first.get("expected_branch_head") != expected_head:
        raise ValueError("attempt journal branch HEAD mismatch")
    if first.get("attempt_number") != 1:
        raise ValueError("first R1 attempt number must be exactly 1")
    if first.get("stage") != "BEFORE_INITIALIZATION":
        raise ValueError("attempt declaration was not recorded before initialization")
    for key in ("allocations_initialized", "actions_submitted", "outcomes_exposed"):
        if first.get(key) != 0:
            raise ValueError(f"pre-initialization journal requires {key}=0")
    if first.get("initialized") is not False:
        raise ValueError("pre-initialization journal already reports initialization")

    # No later record may claim initialization before this verifier grants admission.
    for index, record in enumerate(records[1:], 2):
        if record.get("initialized") is True or (record.get("allocations_initialized") or 0) > 0:
            raise ValueError(f"journal line {index} exposes initialization before admission")

    return {
        "schema": "industrial-waste-v2-r1-preinitialization-guard-result-v1",
        "status": "READY_FOR_FIRST_INITIALIZATION_GUARD_ONLY",
        "protocol_id": PROTOCOL_ID,
        "claim_id": claim_id,
        "expected_branch_head": expected_head,
        "runtime_binding": runtime,
        "execution_authorization": authorization,
        "journal_records": len(records),
        "official_allocations_initialized": 0,
        "official_actions_submitted": 0,
        "official_outcomes_exposed": 0,
        "authorizes_extra_sampling": False,
        "promotes_candidate": False,
    }
