"""Fail-closed seed-free audit for Industrial Waste v2 R1 runtime bindings.

This audit binds the currently accepted structural components by exact Git blob identity,
revalidates the frozen deck/corpus geometry and accepted zero-official counters, and reports
the remaining prerequisites. It cannot create an execution claim, initialize an allocation,
read a gameplay outcome, or authorize R1 execution.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

PROTOCOL_ID = "IW_V2_R1_ENGINE_STRUCTURAL_2026_09_25"
EXPECTED_BLOBS = {
    "ai/src/test/kotlin/com/wingedsheep/ai/industrialwaste/IndustrialWasteV2CheckpointMana.kt": "da4a3994a8bc8c1cdeb9bd5b5a41bb5ed1b4f716",
    "ai/src/test/kotlin/com/wingedsheep/ai/industrialwaste/IndustrialWasteV2EventMetrics.kt": "9037d5d479df6a9718cbc53b84d1435faf236caa",
    "ai/src/test/kotlin/com/wingedsheep/ai/industrialwaste/IndustrialWasteV2ExecutionStatus.kt": "e6e67f5acf593b15f6e09c2aa9cee6d3e73b67da",
    "ai/src/test/kotlin/com/wingedsheep/ai/industrialwaste/IndustrialWasteV2FullHorizonRunner.kt": "3186e9434f006520107b7177d3261ad8aca689f4",
    "ai/src/test/kotlin/com/wingedsheep/ai/industrialwaste/IndustrialWasteV2SelectionAdvisorModule.kt": "e79f90b380b448af6adf7c2449035819ef70a510",
    "industrial-waste/control/industrial-waste-v1.0-submitted.dck": "198eed5bd95670ce490a9defda6e26c7fb1564ab",
    "industrial-waste/v2/candidates/compact-loop.dck": "2595609bb898fe6fe00f7d141568b5e3fc049d96",
    "industrial-waste/v2/candidates/lean-tron-hybrid.dck": "57aae0d0fa7f22ddd41f154d5a5d3c745314abe9",
    "industrial-waste/v2/candidates/recursive-eggs.dck": "ff55a6bc96eb8b7ccd9e2753f212dbebed57b234",
    "industrial-waste/v2/checkpoint-mana-capability-36180155341.json": "1abe8ab296afe9f4bdf06fbcbd1559e072b99a50",
    "industrial-waste/v2/conversion-metric-capability-36188025049.json": "7dc617022032584f75d3315235edc224a702cf80",
    "industrial-waste/v2/event-metrics-capability.json": "9104e3a167492b144c712b4c500c831d44628844",
    "industrial-waste/v2/execution-guard-capability-36213838282.json": "d9ead9d03525f218da08bacfc1ce096edc9330d4",
    "industrial-waste/v2/full-horizon-runner-capability-36219295879.json": "fab7feb44c06a8bfeb654675161189b88aefa174",
    "industrial-waste/v2/ordering-capability.json": "251f1543780c40fe4da8ee7d600edbbbfdf4c0e3",
    "industrial-waste/v2/protocol-v2-r0.json": "d4aa845b35e44a932d69eb46e268fa19f883b242",
    "industrial-waste/v2/protocol-v2-r1.json": "08267f0ae1688ab93bc6ea57b431e6a36acf33ff",
    "industrial-waste/v2/r0-freeze.json": "5077ecc51ecf28e43abdaf6e5582646dfade1202",
    "industrial-waste/v2/r1-arithmetic-capability.json": "5543bc4d0caba939cf9e4e1215238562c8b015ae",
    "industrial-waste/v2/r1-ordering-corpus.json": "8ac1cb2ee4b5e7812cbda0391b6ce0a029360632",
    "industrial-waste/v2/r1_decision_rule.py": "4e8f97ff8496eb7bf0a6c4c2ca4a756f52de022c",
    "industrial-waste/v2/r1_execution_guard.py": "b4ac532cb565040171a6d362651dd399cf9c3ac1",
    "industrial-waste/v2/replay-capability-36212571860.json": "d9deefc86a5934f789e154f47e287d4529bc25a0",
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/core/GameInitializer.kt": "9edef693887e38d4b151b32a67a5bd7734744d70",
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/MulliganHandler.kt": "239603efafdf494882e55a7619ba83799c504150",
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/library/ShuffleLibraryExecutor.kt": "3ef0c607d7fd7018c791af45d19e08285047959a",
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/library/LibraryOrderingService.kt": "2f92ae3941a7f7a17c7fb6e5945452b3bb0a1a55",
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/state/components/player/LibraryOrderingComponent.kt": "6164147b698b92f9a3c6b5edf69dd2c5222b2c43",
}
RECEIPT_STATUS = {
    "industrial-waste/v2/ordering-capability.json": "QUALIFIED_SCOPED_ORDERING_PATHS_NOT_FULL_R1_READINESS",
    "industrial-waste/v2/checkpoint-mana-capability-36180155341.json": "ACCEPTED_SEED_FREE_COMPONENT_ONLY",
    "industrial-waste/v2/conversion-metric-capability-36188025049.json": "ACCEPTED_SEED_FREE_COMPONENT_ONLY",
    "industrial-waste/v2/event-metrics-capability.json": "QUALIFIED_SCOPED_EVENT_METRICS_NOT_COMPLETE_R1_METRICS",
    "industrial-waste/v2/replay-capability-36212571860.json": "ACCEPTED_SEED_FREE_REPLAY_CAPABILITY_ONLY",
    "industrial-waste/v2/execution-guard-capability-36213838282.json": "ACCEPTED_SEED_FREE_EXECUTION_GUARD_CAPABILITY_ONLY",
    "industrial-waste/v2/full-horizon-runner-capability-36219295879.json": "ACCEPTED_SEED_FREE_FULL_HORIZON_RUNNER_CAPABILITY_ONLY",
    "industrial-waste/v2/r1-arithmetic-capability.json": "QUALIFIED_SYNTHETIC_NUMERICAL_CORE_NOT_OFFICIAL_ANALYSIS",
}
MISSING_RUNTIME_BINDINGS = [
    "effective_rules_archive_and_digest",
    "compiled_card_snapshot_digest",
    "official_r1_corpus_runner",
    "official_r1_artifact_schema_and_completeness_contract",
    "official_complete_metric_projection",
]


def _json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected JSON object")
    return value


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _git_blob_sha(path: Path) -> str:
    data = path.read_bytes()
    return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def verify_exact_bound_files(root: Path) -> dict[str, dict[str, str]]:
    bound: dict[str, dict[str, str]] = {}
    for raw, expected_blob in sorted(EXPECTED_BLOBS.items()):
        path = root / raw
        if not path.is_file():
            raise ValueError(f"bound file missing: {raw}")
        actual_blob = _git_blob_sha(path)
        if actual_blob != expected_blob:
            raise ValueError(f"bound file drift: {raw}: expected {expected_blob}, got {actual_blob}")
        bound[raw] = {"git_blob_sha1": actual_blob, "sha256": _sha256(path)}
    return bound


def verify_protocol_geometry(protocol: dict[str, Any], corpus: dict[str, Any]) -> None:
    if protocol.get("protocol_id") != PROTOCOL_ID:
        raise ValueError("R1 protocol id drift")
    if protocol.get("candidate_families") != ["COMPACT_LOOP", "RECURSIVE_EGGS", "LEAN_TRON_HYBRID"]:
        raise ValueError("R1 candidate family set/order drift")
    if protocol.get("comparator") != "IMMUTABLE_SUBMITTED_V1_0":
        raise ValueError("R1 comparator drift")
    design = protocol.get("design")
    if not isinstance(design, dict):
        raise ValueError("R1 design missing")
    expected = {
        "corpus_rows": 64,
        "allocations_per_deck": 128,
        "deck_count": 4,
        "total_allocations": 512,
        "own_turn_cap": 8,
        "submitted_action_cap": 4000,
    }
    for key, value in expected.items():
        if design.get(key) != value:
            raise ValueError(f"R1 geometry drift: {key}")
    if design.get("first_draw_schedules_per_row") != ["PLAY_SKIP_FIRST_DRAW", "DRAW_TAKE_FIRST_DRAW"]:
        raise ValueError("R1 play/draw schedule drift")
    rows = corpus.get("rows")
    labels = corpus.get("labels")
    if not isinstance(rows, list) or len(rows) != 64:
        raise ValueError("R1 corpus must contain exactly 64 rows")
    if not isinstance(labels, list) or len(labels) == 0:
        raise ValueError("R1 corpus labels missing")
    universe = list(range(len(labels)))
    seen_rows: list[int] = []
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("R1 corpus row is not an object")
        seen_rows.append(row.get("row"))
        orderings = row.get("initial_orderings")
        if not isinstance(orderings, list) or len(orderings) != 4:
            raise ValueError("each R1 row requires four frozen deck orderings")
        for ordering in orderings:
            if not isinstance(ordering, list) or sorted(ordering) != universe:
                raise ValueError("R1 ordering is not an exact permutation of frozen labels")
    if seen_rows != list(range(1, 65)):
        raise ValueError("R1 row numbering/order drift")


def _require_zero(value: Any, label: str) -> None:
    if value != 0:
        raise ValueError(f"{label} must remain zero before official claim")


def verify_freeze_and_receipts(root: Path) -> dict[str, Any]:
    protocol = _json(root / "industrial-waste/v2/protocol-v2-r1.json")
    corpus = _json(root / "industrial-waste/v2/r1-ordering-corpus.json")
    freeze = _json(root / "industrial-waste/v2/r0-freeze.json")
    verify_protocol_geometry(protocol, corpus)

    if freeze.get("candidate_families") != protocol.get("candidate_families"):
        raise ValueError("R0/R1 candidate freeze mismatch")
    _require_zero(freeze["official_counters"]["games_initialized"], "R0 games_initialized")
    _require_zero(freeze["official_counters"]["actions_submitted"], "R0 actions_submitted")
    _require_zero(freeze["official_counters"]["outcomes_exposed"], "R0 outcomes_exposed")

    frozen_files = freeze.get("file_sha256", {})
    for raw in ("industrial-waste/v2/protocol-v2-r1.json", "industrial-waste/v2/r1-ordering-corpus.json"):
        if frozen_files.get(raw) != _sha256(root / raw):
            raise ValueError(f"R0 freeze no longer matches {raw}")

    control = freeze["historical_control"]
    if _sha256(root / control["path"]) != control["sha256"]:
        raise ValueError("immutable v1 comparator bytes drifted")
    for family in protocol["candidate_families"]:
        item = freeze["candidates"][family]
        if _sha256(root / item["path"]) != item["sha256"]:
            raise ValueError(f"frozen candidate bytes drifted: {family}")

    receipt_summary: dict[str, Any] = {}
    for raw, expected_status in RECEIPT_STATUS.items():
        receipt = _json(root / raw)
        if receipt.get("status") != expected_status:
            raise ValueError(f"accepted receipt status drift: {raw}")
        receipt_summary[raw] = {
            "status": receipt["status"],
            "sha256": _sha256(root / raw),
            "source_commit": receipt.get("source_commit"),
            "workflow_run_id": receipt.get("workflow_run_id"),
        }

    ordering = _json(root / "industrial-waste/v2/ordering-capability.json")
    event = _json(root / "industrial-waste/v2/event-metrics-capability.json")
    checkpoint = _json(root / "industrial-waste/v2/checkpoint-mana-capability-36180155341.json")
    conversion = _json(root / "industrial-waste/v2/conversion-metric-capability-36188025049.json")
    replay = _json(root / "industrial-waste/v2/replay-capability-36212571860.json")
    guard = _json(root / "industrial-waste/v2/execution-guard-capability-36213838282.json")
    horizon = _json(root / "industrial-waste/v2/full-horizon-runner-capability-36219295879.json")
    arithmetic = _json(root / "industrial-waste/v2/r1-arithmetic-capability.json")

    _require_zero(ordering.get("official_initializations"), "ordering official_initializations")
    _require_zero(ordering.get("comparative_outcomes_exposed"), "ordering comparative_outcomes_exposed")
    _require_zero(event.get("official_initializations"), "event official_initializations")
    _require_zero(event.get("comparative_outcomes_exposed"), "event comparative_outcomes_exposed")
    for label, receipt in (("checkpoint", checkpoint), ("conversion", conversion)):
        official = receipt.get("official", {})
        _require_zero(official.get("allocations_initialized"), f"{label} allocations_initialized")
        _require_zero(official.get("actions_submitted"), f"{label} actions_submitted")
        _require_zero(official.get("outcomes_exposed"), f"{label} outcomes_exposed")
        if official.get("execution_authorized") is not False:
            raise ValueError(f"{label} receipt unexpectedly authorizes execution")
    safeguards = replay.get("safeguards", {})
    _require_zero(safeguards.get("official_allocations_initialized"), "replay allocations")
    _require_zero(safeguards.get("comparative_outcomes_exposed"), "replay outcomes")
    frozen = guard.get("frozen_r1", {})
    _require_zero(frozen.get("initialized_allocations"), "guard allocations")
    _require_zero(frozen.get("comparative_outcomes_exposed"), "guard outcomes")
    if guard.get("live_guard", {}).get("status") != "BLOCKED_BEFORE_INITIALIZATION":
        raise ValueError("execution guard is no longer blocked before initialization")
    exclusion = horizon.get("exclusion", {})
    _require_zero(exclusion.get("official_corpus_rows_read"), "full-horizon official rows")
    _require_zero(exclusion.get("official_allocations_initialized"), "full-horizon allocations")
    _require_zero(exclusion.get("official_actions_submitted"), "full-horizon actions")
    _require_zero(exclusion.get("comparative_outcomes_exposed"), "full-horizon outcomes")
    authority = horizon.get("authority", {})
    if any(authority.get(key) is not False for key in (
        "runtime_binding_complete", "official_claim_created", "attempt_journal_created",
        "execution_authorized", "candidate_elimination_authorized", "candidate_promotion_authorized",
    )):
        raise ValueError("full-horizon capability unexpectedly exposes official authority")
    _require_zero(arithmetic.get("official_allocations_initialized"), "arithmetic allocations")
    if arithmetic.get("deck_promoted") is not False:
        raise ValueError("arithmetic capability unexpectedly promotes a deck")

    return {
        "protocol_id": protocol["protocol_id"],
        "candidate_families": protocol["candidate_families"],
        "comparator": protocol["comparator"],
        "corpus_rows": 64,
        "deck_count": 4,
        "allocations_per_deck": 128,
        "total_allocations": 512,
        "receipts": receipt_summary,
    }


def audit(root: Path, source_head: str | None = None) -> dict[str, Any]:
    root = root.resolve()
    bound = verify_exact_bound_files(root)
    frozen = verify_freeze_and_receipts(root)
    return {
        "schema": "industrial-waste-v2-r1-runtime-binding-audit-v1",
        "status": "BLOCKED_MISSING_RUNTIME_BINDINGS",
        "source_head": source_head,
        "protocol_id": PROTOCOL_ID,
        "frozen_geometry": frozen,
        "bound_files": bound,
        "missing_runtime_bindings": MISSING_RUNTIME_BINDINGS,
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
        "next_gate": "Implement and independently qualify the five missing runtime bindings, then issue a successor exact runtime-binding receipt before any durable official claim.",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--source-head")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = audit(args.root, args.source_head)
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(payload, encoding="utf-8")
    print(payload, end="")


if __name__ == "__main__":
    main()
