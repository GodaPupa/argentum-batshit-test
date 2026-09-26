"""Seed-free exact runtime identity binding for Industrial Waste v2 R1.

This gate fingerprints the effective rules/SDK/card-definition source surfaces plus every
canonical card snapshot. It composes the already-accepted fail-closed runtime-binding audit,
requires all official counters to remain zero, and emits provenance only. It cannot create
an execution claim, attempt journal, initialize allocations, expose outcomes, or authorize play.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
from typing import Iterable

HERE = Path(__file__).resolve().parent
AUDIT_PATH = HERE / "r1_runtime_binding_audit.py"
SPEC = importlib.util.spec_from_file_location("r1_runtime_binding_audit", AUDIT_PATH)
assert SPEC and SPEC.loader
_audit_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(_audit_module)


def _digest_files(root: Path, files: Iterable[Path]) -> dict[str, object]:
    ordered = sorted((p.resolve() for p in files if p.is_file()), key=lambda p: p.relative_to(root).as_posix())
    if not ordered:
        raise ValueError("runtime identity surface is empty")
    h = hashlib.sha256()
    paths: list[str] = []
    total_bytes = 0
    for path in ordered:
        rel = path.relative_to(root).as_posix()
        data = path.read_bytes()
        paths.append(rel)
        total_bytes += len(data)
        h.update(rel.encode("utf-8"))
        h.update(b"\0")
        h.update(str(len(data)).encode("ascii"))
        h.update(b"\0")
        h.update(data)
        h.update(b"\0")
    return {
        "sha256": h.hexdigest(),
        "files": len(paths),
        "bytes": total_bytes,
        "first_path": paths[0],
        "last_path": paths[-1],
    }


def bind(root: Path, source_head: str | None = None) -> dict[str, object]:
    root = root.resolve()
    prior = _audit_module.audit(root, source_head)
    if prior["status"] != "BLOCKED_MISSING_RUNTIME_BINDINGS":
        raise ValueError("predecessor runtime-binding audit status drift")
    expected_zero = {
        "allocations_initialized": 0,
        "actions_submitted": 0,
        "comparative_outcomes_exposed": 0,
    }
    if prior["official_counters"] != expected_zero:
        raise ValueError("official R1 counters are no longer zero")
    if prior["claim_creation_authorized"] is not False or prior["initialization_admitted"] is not False:
        raise ValueError("predecessor unexpectedly admits official initialization")

    rules = _digest_files(root, (root / "rules-engine/src/main/kotlin").rglob("*.kt"))
    sdk = _digest_files(root, (root / "mtg-sdk/src/main/kotlin").rglob("*.kt"))
    card_sources = _digest_files(root, (root / "mtg-sets").glob("*/src/main/kotlin/**/*.kt"))
    snapshots = _digest_files(root, (root / "mtg-sets/src/test/resources/snapshots/cards").glob("*.json"))

    return {
        "schema": "industrial-waste-v2-r1-runtime-identity-binding-v1",
        "status": "QUALIFIED_SEED_FREE_RUNTIME_IDENTITY_BINDING_ONLY",
        "source_head": source_head,
        "protocol_id": prior["protocol_id"],
        "bindings_closed": [
            "effective_rules_archive_and_digest",
            "compiled_card_snapshot_digest",
        ],
        "runtime_identity": {
            "rules_engine_sources": rules,
            "mtg_sdk_sources": sdk,
            "card_definition_sources": card_sources,
            "canonical_card_snapshots": snapshots,
        },
        "predecessor_missing_runtime_bindings": prior["missing_runtime_bindings"],
        "remaining_runtime_bindings_after_this_gate": [
            "official_r1_corpus_runner",
            "official_r1_artifact_schema_and_completeness_contract",
            "official_complete_metric_projection",
        ],
        "official_counters": expected_zero,
        "claim_creation_authorized": False,
        "attempt_journal_creation_authorized": False,
        "initialization_admitted": False,
        "execution_authorized": False,
        "candidate_elimination_authorized": False,
        "candidate_promotion_authorized": False,
        "next_gate": (
            "Bind an exact official 512-allocation corpus runner, complete artifact/completeness "
            "contract, and complete metric projection before any durable claim is created."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--source-head")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = bind(args.root, args.source_head)
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(payload, encoding="utf-8")
    print(payload, end="")


if __name__ == "__main__":
    main()
