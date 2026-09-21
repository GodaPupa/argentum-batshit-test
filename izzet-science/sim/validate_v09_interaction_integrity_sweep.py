#!/usr/bin/env python3
"""Invariant-only Phase-17 interaction qualification; emits no outcomes."""
from __future__ import annotations

import hashlib
from pathlib import Path

import mana_harness as harness
from paired_capsize_interaction_contract import (
    CONTROL_SHA256,
    SCHEMA,
    aggregate_paired_interaction_games,
    validate_interaction_summary,
)


ROOT=Path(__file__).resolve().parents[2]
CONTROL=ROOT/"izzet-science/v0.7-control.md"
RETIRED_RUNNERS=(
    ROOT/"izzet-science/sim/run_v09_capsize_paired_pilot.py",
    ROOT/"izzet-science/sim/run_v09_capsize_paired_pilot_v2.py",
    ROOT/"izzet-science/sim/run_v09_capsize_paired_pilot_v3.py",
)
QUALIFICATION_MASTER=1  # public regression coordinate, never an experimental seed
QUALIFICATION_PAIRS=1024
THROUGH=10
SOURCE="f"*40


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest()!=CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    if any(b"RETIRED=True" not in runner.read_bytes() for runner in RETIRED_RUNNERS):
        raise SystemExit("an exposed paired runner is not retired")
    _,cards=harness.parse_deck(CONTROL)
    if len(cards)!=99:
        raise SystemExit(f"control main-deck identity mismatch: {len(cards)} cards")

    pairs=harness.iter_paired_capsize_policy_games(
        cards,QUALIFICATION_PAIRS,QUALIFICATION_MASTER,THROUGH,
        False,True,False,True)
    summary=aggregate_paired_interaction_games(
        pairs,SOURCE,QUALIFICATION_MASTER,QUALIFICATION_PAIRS,
        harness.derive_paired_game_seed,THROUGH)
    if summary["schema"]!=SCHEMA or summary["samples"]!=QUALIFICATION_PAIRS:
        raise SystemExit("qualification summary identity mismatch")
    if not validate_interaction_summary(
            summary,SOURCE,QUALIFICATION_MASTER,QUALIFICATION_PAIRS):
        raise SystemExit("qualification summary validation failed")

    # Deliberately emit accounting and disposition only. The validated summary
    # remains in memory and is neither serialized nor interpreted.
    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-17-interaction-integrity")
    print(f"summary_schema={SCHEMA}")
    print(f"qualification_pairs={QUALIFICATION_PAIRS}")
    print(f"qualification_trajectories={2*QUALIFICATION_PAIRS}")
    print("qualification_master=existing-regression-coordinate-1")
    print("outcome_fields_emitted=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("pilot_authorized=0")
    print("disposition=V09_PHASE17_INTERACTION_INVARIANT_SWEEP_VALIDATED")


if __name__=="__main__":
    main()
