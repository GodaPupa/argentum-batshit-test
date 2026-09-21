#!/usr/bin/env python3
"""Phase-30 seed-free 12-position sampled-pilot runner contract.

No experimental seed generation or real game execution is implemented here.
This module validates assignments, records synthetic/nonexperimental fixtures,
and emits a canonical ledger for qualification only.
"""
from __future__ import annotations
from dataclasses import asdict, dataclass
import hashlib
import json
from typing import Tuple

RUNNER_VERSION = "izzet-v09-phase30-runner-v1"
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
OPPONENT_ID = "veteran-beastrider-commander-clash-2025-v1"
POSITION_COUNT = 12

@dataclass(frozen=True)
class PilotAssignment:
    position: int
    izzet_assignment: str  # play | draw
    seed: int | None = None

@dataclass(frozen=True)
class PilotObservables:
    game_result: str | None = None
    terminal_turn: int | None = None
    terminal_reason: str | None = None
    capsize_acquired: bool = False
    capsize_cast_count: int = 0
    capsize_buyback_cast_count: int = 0
    first_meaningful_capsize_interaction_turn: int | None = None
    capsize_interaction_class: str | None = None
    capsize_created_extra_main_phase_window: bool = False
    primary_combo_assembled: bool = False
    primary_combo_attempt: bool = False
    primary_combo_protected: bool = False
    primary_combo_disrupted: bool = False
    deterministic_lethal_opportunity: bool = False
    commander_damage_received: int = 0

@dataclass(frozen=True)
class PositionRecord:
    position: int
    assignment: str
    synthetic_fixture_id: str
    replay_digest: str
    observables: PilotObservables
    valid: bool
    invalid_reason: str | None = None

@dataclass(frozen=True)
class PilotLedger:
    schema: str
    runner_version: str
    control_sha256: str
    opponent_identity: str
    assignment_vector: Tuple[str, ...]
    records: Tuple[PositionRecord, ...]
    stopped_after_position: int | None
    experimental_seeds_generated: int = 0
    experimental_seeds_consumed: int = 0
    sampled_games: int = 0
    outcome_exposure: int = 0

def validate_assignments(assignments: Tuple[PilotAssignment, ...]) -> None:
    if not isinstance(assignments, tuple) or len(assignments) != POSITION_COUNT:
        raise ValueError("assignment vector must contain exactly 12 positions")
    expected = tuple(range(1, POSITION_COUNT + 1))
    got = tuple(a.position for a in assignments)
    if got != expected:
        raise ValueError("positions must be exactly 1 through 12 in order")
    values = tuple(a.izzet_assignment for a in assignments)
    if values.count("play") != 6 or values.count("draw") != 6:
        raise ValueError("assignment vector must contain exactly six play and six draw")
    if any(v not in {"play", "draw"} for v in values):
        raise ValueError("invalid play/draw assignment")
    if any(a.seed is not None for a in assignments):
        raise ValueError("Phase 30 forbids populated experimental seed fields")

def validate_observables(obs: PilotObservables, *, synthetic: bool) -> None:
    if obs.capsize_cast_count < 0 or obs.capsize_buyback_cast_count < 0:
        raise ValueError("Capsize counts cannot be negative")
    if obs.capsize_buyback_cast_count > obs.capsize_cast_count:
        raise ValueError("buyback casts cannot exceed total Capsize casts")
    if obs.commander_damage_received < 0:
        raise ValueError("commander damage cannot be negative")
    if obs.first_meaningful_capsize_interaction_turn is not None and obs.first_meaningful_capsize_interaction_turn < 1:
        raise ValueError("interaction turn must be positive")
    if obs.game_result is not None:
        if not synthetic:
            raise ValueError("Phase 30 production positions may not emit outcomes")
        if obs.terminal_turn is None or obs.terminal_reason is None:
            raise ValueError("terminal result requires turn and reason")
    if (obs.terminal_turn is None) != (obs.terminal_reason is None):
        raise ValueError("terminal turn and reason must be jointly present or absent")

def replay_digest(position: int, assignment: str, synthetic_fixture_id: str) -> str:
    payload = {
        "runner_version": RUNNER_VERSION,
        "control_sha256": CONTROL_SHA256,
        "opponent_identity": OPPONENT_ID,
        "position": position,
        "assignment": assignment,
        "synthetic_fixture_id": synthetic_fixture_id,
    }
    blob = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(blob).hexdigest()

def _canonical_json(ledger: PilotLedger) -> str:
    return json.dumps(asdict(ledger), sort_keys=True, separators=(",", ":"))

def build_synthetic_ledger(
    assignments: Tuple[PilotAssignment, ...],
    fixture_ids: Tuple[str, ...],
    observables: Tuple[PilotObservables, ...],
    invalid_at_position: int | None = None,
    invalid_reason: str | None = None,
) -> PilotLedger:
    validate_assignments(assignments)
    if len(fixture_ids) != POSITION_COUNT or len(observables) != POSITION_COUNT:
        raise ValueError("synthetic qualification requires 12 fixture ids and 12 observable rows")
    if invalid_at_position is not None and invalid_at_position not in range(1, POSITION_COUNT + 1):
        raise ValueError("invalid position out of range")
    if invalid_at_position is not None and not invalid_reason:
        raise ValueError("invalid position requires a reason")

    records = []
    for assignment, fixture_id, obs in zip(assignments, fixture_ids, observables):
        if invalid_at_position is not None and assignment.position > invalid_at_position:
            break
        if not fixture_id or not fixture_id.startswith("synthetic-"):
            raise ValueError("Phase 30 accepts only named synthetic fixtures")
        validate_observables(obs, synthetic=True)
        is_valid = assignment.position != invalid_at_position
        records.append(PositionRecord(
            position=assignment.position,
            assignment=assignment.izzet_assignment,
            synthetic_fixture_id=fixture_id,
            replay_digest=replay_digest(assignment.position, assignment.izzet_assignment, fixture_id),
            observables=obs,
            valid=is_valid,
            invalid_reason=(invalid_reason if not is_valid else None),
        ))
        if not is_valid:
            break

    return PilotLedger(
        schema="izzet-v09-phase30-pilot-ledger-v1",
        runner_version=RUNNER_VERSION,
        control_sha256=CONTROL_SHA256,
        opponent_identity=OPPONENT_ID,
        assignment_vector=tuple(a.izzet_assignment for a in assignments),
        records=tuple(records),
        stopped_after_position=(invalid_at_position if invalid_at_position is not None else None),
    )

def canonical_ledger_json(ledger: PilotLedger) -> str:
    return _canonical_json(ledger)
