#!/usr/bin/env python3
"""Seed-free qualification fixtures for the Phase-30 runner contract."""
from sampled_pilot_runner import (
    CONTROL_SHA256, OPPONENT_ID, POSITION_COUNT,
    PilotAssignment, PilotObservables,
    build_synthetic_ledger, canonical_ledger_json, replay_digest,
    validate_assignments, validate_observables,
)

ASSIGNMENTS = tuple(
    PilotAssignment(i, "play" if i % 2 else "draw", None)
    for i in range(1, 13)
)
FIXTURES = tuple(f"synthetic-phase30-{i:02d}" for i in range(1, 13))

def obs_for(i):
    # Synthetic only: exercise every Phase-29 observable without exposing real outcomes.
    terminal = i in {3, 8, 12}
    return PilotObservables(
        game_result=("synthetic-win" if terminal else None),
        terminal_turn=(i if terminal else None),
        terminal_reason=("synthetic-terminal" if terminal else None),
        capsize_acquired=(i % 2 == 1),
        capsize_cast_count=(1 if i % 3 == 0 else 0),
        capsize_buyback_cast_count=(1 if i % 6 == 0 else 0),
        first_meaningful_capsize_interaction_turn=(i if i % 3 == 0 else None),
        capsize_interaction_class=("tempo" if i % 3 == 0 else None),
        capsize_created_extra_main_phase_window=(i % 4 == 0),
        primary_combo_assembled=(i % 2 == 0),
        primary_combo_attempt=(i % 4 == 0),
        primary_combo_protected=(i % 5 == 0),
        primary_combo_disrupted=(i % 7 == 0),
        deterministic_lethal_opportunity=(i % 6 == 0),
        commander_damage_received=i,
    )

OBS = tuple(obs_for(i) for i in range(1, 13))

def main():
    validate_assignments(ASSIGNMENTS)
    assert len(ASSIGNMENTS) == POSITION_COUNT
    assert sum(a.izzet_assignment == "play" for a in ASSIGNMENTS) == 6
    assert sum(a.izzet_assignment == "draw" for a in ASSIGNMENTS) == 6
    assert all(a.seed is None for a in ASSIGNMENTS)

    ledger = build_synthetic_ledger(ASSIGNMENTS, FIXTURES, OBS)
    assert ledger.control_sha256 == CONTROL_SHA256
    assert ledger.opponent_identity == OPPONENT_ID
    assert len(ledger.records) == 12
    assert ledger.experimental_seeds_generated == 0
    assert ledger.experimental_seeds_consumed == 0
    assert ledger.sampled_games == 0
    assert ledger.outcome_exposure == 0

    # Same input => byte-identical canonical ledger.
    ledger2 = build_synthetic_ledger(ASSIGNMENTS, FIXTURES, OBS)
    assert canonical_ledger_json(ledger) == canonical_ledger_json(ledger2)

    # Replay digest binds position, assignment, fixture identity.
    d1 = replay_digest(1, "play", FIXTURES[0])
    assert d1 == ledger.records[0].replay_digest
    assert d1 != replay_digest(1, "draw", FIXTURES[0])
    assert d1 != replay_digest(2, "play", FIXTURES[0])
    assert d1 != replay_digest(1, "play", "synthetic-other")

    # Stop-on-first-invalid: position 5 exists invalid, later positions never initialize.
    stopped = build_synthetic_ledger(
        ASSIGNMENTS, FIXTURES, OBS,
        invalid_at_position=5,
        invalid_reason="synthetic-illegal-action",
    )
    assert len(stopped.records) == 5
    assert stopped.records[-1].position == 5
    assert stopped.records[-1].valid is False
    assert stopped.stopped_after_position == 5
    assert all(r.position <= 5 for r in stopped.records)

    # Reject populated seed fields.
    seeded = list(ASSIGNMENTS)
    seeded[0] = PilotAssignment(1, "play", 123)
    try:
        validate_assignments(tuple(seeded))
        raise AssertionError("populated seed accepted")
    except ValueError:
        pass

    # Reject incorrect play/draw balance.
    bad = tuple(PilotAssignment(i, "play", None) for i in range(1, 13))
    try:
        validate_assignments(bad)
        raise AssertionError("unbalanced assignment accepted")
    except ValueError:
        pass

    # Terminal accounting must be complete.
    try:
        validate_observables(PilotObservables(game_result="synthetic-win"), synthetic=True)
        raise AssertionError("terminal result without accounting accepted")
    except ValueError:
        pass

    # Production-mode outcome exposure is forbidden.
    try:
        validate_observables(
            PilotObservables(game_result="synthetic-win", terminal_turn=5, terminal_reason="x"),
            synthetic=False,
        )
        raise AssertionError("production outcome accepted")
    except ValueError:
        pass

    # Every Phase-29 observable exists in the dataclass contract.
    fields = set(PilotObservables.__dataclass_fields__)
    required = {
        "game_result","terminal_turn","terminal_reason","capsize_acquired",
        "capsize_cast_count","capsize_buyback_cast_count",
        "first_meaningful_capsize_interaction_turn","capsize_interaction_class",
        "capsize_created_extra_main_phase_window","primary_combo_assembled",
        "primary_combo_attempt","primary_combo_protected","primary_combo_disrupted",
        "deterministic_lethal_opportunity","commander_damage_received",
    }
    assert required <= fields

    print("V09_PHASE30_RUNNER_CONSTRUCTION_VALIDATION_PASS")

if __name__ == "__main__":
    main()
