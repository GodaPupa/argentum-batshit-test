#!/usr/bin/env python3
"""Seed-free regression fixtures for the frozen Twinned Vision matched-analysis contract."""
from __future__ import annotations

from twinned_vision_matched_contract import (
    CHALLENGER_SHA256,
    CONTROL_SHA256,
    STAGE_PAIRS,
    one_sided_sign_p,
    summarize_stage,
)


def arm(deck_sha: str, won: bool, mulligans: int = 0) -> dict:
    return {
        "deck_sha256": deck_sha,
        "won": won,
        "mulligans": mulligans,
        "starting_position": 1,
        "decisive_turn": 8,
        "engine_invalid": False,
        "invalid_reason": "",
    }


def vision(*, early: bool = False) -> dict:
    return {
        "seen_drawn": 1,
        "cast_from_hand": 1,
        "cast_from_graveyard": 0,
        "copied": 0,
        "cards_drawn": 1,
        "guildmage_overlap": 0,
        "mana_spent": 2,
        "materially_enabled": False,
        "materially_enabled_reason": "",
        "stranded_or_inefficient": early,
        "stranded_reason": "early tempo" if early else "",
        "recovered_after_disruption": False,
        "strategic_planning_would_be_live": None,
        "early_tempo_failure": early,
    }


def row(index: int, control_win: bool, challenger_win: bool, *, early: bool = False) -> dict:
    return {
        "schema": "izzet-v07t-twinned-vision-matched-v1",
        "stage": "stage-a",
        "pair_index": index,
        "position_id": f"fixture-{index:02d}",
        "engine_sha": "0" * 40,
        "opponent_sha": "1" * 64,
        "pilot_policy": "fixture-policy",
        "seed_commitment": f"opaque-{index:02d}",
        "control": arm(CONTROL_SHA256, control_win),
        "challenger": arm(CHALLENGER_SHA256, challenger_win),
        "twinned_vision": vision(early=early),
    }


def main() -> None:
    # Exact one-sided sign-test sanity checks.
    assert one_sided_sign_p(0, 0) == 1.0
    assert one_sided_sign_p(4, 0) == 0.0625
    assert one_sided_sign_p(3, 1) == 0.3125

    neutral = [row(i, False, False) for i in range(STAGE_PAIRS)]
    summary = summarize_stage(neutral, "stage-a")
    assert not summary.trigger_replication

    # Four clean challenger-only discordances meet p=0.0625 and all guardrails.
    positive = [
        row(i, False, i < 4)
        for i in range(STAGE_PAIRS)
    ]
    summary = summarize_stage(positive, "stage-a")
    assert summary.challenger_only == 4
    assert summary.control_only == 0
    assert summary.sign_p == 0.0625
    assert summary.trigger_replication

    # The same statistical signal must fail the preregistered tempo guardrail at three failures
    # where control wins. Add those three control-only early-tempo pairs plus enough additional
    # challenger-only pairs to retain a nominally favorable sign-test result.
    guarded = []
    for i in range(STAGE_PAIRS):
        if i < 3:
            guarded.append(row(i, True, False, early=True))
        elif i < 12:
            guarded.append(row(i, False, True))
        else:
            guarded.append(row(i, False, False))
    summary = summarize_stage(guarded, "stage-a")
    assert summary.challenger_only == 9
    assert summary.control_only == 3
    assert summary.sign_p <= 0.10
    assert summary.challenger_early_tempo_failures_where_control_won == 3
    assert not summary.trigger_replication

    print("V07T_TWINNED_VISION_MATCHED_CONTRACT_VALIDATION_PASS")
    print("experimental_seeds_consumed=0")
    print("sampled_games=0")
    print("outcome_exposure=0")


if __name__ == "__main__":
    main()
