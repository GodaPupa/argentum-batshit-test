#!/usr/bin/env python3
"""Fail-closed matched-game contract for Izzet Science v0.7 vs v0.7-T.

No RNG or gameplay execution lives here.  This module only validates already-produced matched
records and evaluates the preregistered Stage-A / replication decision rule.
"""
from __future__ import annotations

from dataclasses import dataclass
from math import comb
from typing import Any, Iterable, Mapping

CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
CHALLENGER_SHA256 = "0da295e9fcf066728181323dd9acbc0c122f623c99a607709a1e8a85e8936f85"
STAGE_PAIRS = 24

ARM_KEYS = {
    "deck_sha256", "won", "mulligans", "starting_position", "decisive_turn",
    "engine_invalid", "invalid_reason",
}
VISION_KEYS = {
    "seen_drawn", "cast_from_hand", "cast_from_graveyard", "copied",
    "cards_drawn", "guildmage_overlap", "mana_spent",
    "materially_enabled", "materially_enabled_reason",
    "stranded_or_inefficient", "stranded_reason",
    "recovered_after_disruption", "strategic_planning_would_be_live",
    "early_tempo_failure",
}
PAIR_KEYS = {
    "schema", "stage", "pair_index", "position_id", "engine_sha", "opponent_sha",
    "pilot_policy", "seed_commitment", "control", "challenger", "twinned_vision",
}


def _nonneg_int(value: Any, name: str) -> int:
    if type(value) is not int or value < 0:
        raise ValueError(f"{name} must be a nonnegative integer")
    return value


def _bool(value: Any, name: str) -> bool:
    if type(value) is not bool:
        raise ValueError(f"{name} must be Boolean")
    return value


def _arm(arm: Mapping[str, Any], expected_sha: str, name: str) -> None:
    if not isinstance(arm, Mapping) or set(arm) != ARM_KEYS:
        raise ValueError(f"{name} arm schema mismatch")
    if arm["deck_sha256"] != expected_sha:
        raise ValueError(f"{name} deck hash mismatch")
    _bool(arm["won"], f"{name}.won")
    _nonneg_int(arm["mulligans"], f"{name}.mulligans")
    if arm["starting_position"] not in (1, 2):
        raise ValueError(f"{name}.starting_position must be 1 or 2")
    if arm["decisive_turn"] is not None:
        _nonneg_int(arm["decisive_turn"], f"{name}.decisive_turn")
    invalid = _bool(arm["engine_invalid"], f"{name}.engine_invalid")
    if invalid != bool(arm["invalid_reason"]):
        raise ValueError(f"{name} invalid flag/reason mismatch")


def validate_pair(row: Mapping[str, Any], expected_index: int, stage: str) -> None:
    if not isinstance(row, Mapping) or set(row) != PAIR_KEYS:
        raise ValueError("pair schema mismatch")
    if row["schema"] != "izzet-v07t-twinned-vision-matched-v1":
        raise ValueError("pair schema identity mismatch")
    if row["stage"] != stage or stage not in {"stage-a", "replication"}:
        raise ValueError("stage mismatch")
    if row["pair_index"] != expected_index:
        raise ValueError("pair index mismatch")
    for key in ("position_id", "engine_sha", "opponent_sha", "pilot_policy", "seed_commitment"):
        if not isinstance(row[key], str) or not row[key]:
            raise ValueError(f"{key} must be a nonempty string")

    _arm(row["control"], CONTROL_SHA256, "control")
    _arm(row["challenger"], CHALLENGER_SHA256, "challenger")

    # Matched treatment: same start role and validity status for both arms.
    if row["control"]["starting_position"] != row["challenger"]["starting_position"]:
        raise ValueError("starting-position mismatch within pair")
    if row["control"]["engine_invalid"] != row["challenger"]["engine_invalid"]:
        raise ValueError("pair validity must be symmetric; quarantine the technical defect")

    vision = row["twinned_vision"]
    if not isinstance(vision, Mapping) or set(vision) != VISION_KEYS:
        raise ValueError("Twinned Vision telemetry schema mismatch")
    for key in (
        "seen_drawn", "cast_from_hand", "cast_from_graveyard", "copied",
        "cards_drawn", "guildmage_overlap", "mana_spent",
    ):
        _nonneg_int(vision[key], f"twinned_vision.{key}")
    for key in (
        "materially_enabled", "stranded_or_inefficient",
        "recovered_after_disruption", "early_tempo_failure",
    ):
        _bool(vision[key], f"twinned_vision.{key}")
    if vision["materially_enabled"] != bool(vision["materially_enabled_reason"]):
        raise ValueError("materially-enabled flag/reason mismatch")
    if vision["stranded_or_inefficient"] != bool(vision["stranded_reason"]):
        raise ValueError("stranded flag/reason mismatch")
    if vision["strategic_planning_would_be_live"] not in (True, False, None):
        raise ValueError("Strategic Planning counterfactual must be Boolean or null")

    # Realized draw identity: hand casts produce 1, graveyard casts and copies produce 2.
    # A copy count is telemetry for actual resolving copies; invalid/fizzled objects must not be counted.
    minimum_draws = vision["cast_from_hand"] + 2 * vision["cast_from_graveyard"] + 2 * vision["copied"]
    if vision["cards_drawn"] < minimum_draws:
        raise ValueError("Twinned Vision draw telemetry violates qualified card semantics")


def one_sided_sign_p(challenger_only: int, control_only: int) -> float:
    """P[X >= challenger_only] for X~Binomial(discordant, .5)."""
    n = challenger_only + control_only
    if n == 0:
        return 1.0
    return sum(comb(n, k) for k in range(challenger_only, n + 1)) / (2 ** n)


@dataclass(frozen=True)
class StageSummary:
    valid_pairs: int
    challenger_only: int
    control_only: int
    both_win: int
    both_loss: int
    sign_p: float
    mulligan_delta_per_game: float
    challenger_early_tempo_failures_where_control_won: int
    trigger_replication: bool


def summarize_stage(rows: Iterable[Mapping[str, Any]], stage: str) -> StageSummary:
    rows = list(rows)
    if len(rows) != STAGE_PAIRS:
        raise ValueError(f"expected exactly {STAGE_PAIRS} matched pairs, got {len(rows)}")
    for i, row in enumerate(rows):
        validate_pair(row, i, stage)

    valid = [
        row for row in rows
        if not row["control"]["engine_invalid"] and not row["challenger"]["engine_invalid"]
    ]
    if len(valid) != STAGE_PAIRS:
        raise ValueError("stage is incomplete: technical invalidations require protocol-authorized replacement")

    challenger_only = sum(
        row["challenger"]["won"] and not row["control"]["won"] for row in valid
    )
    control_only = sum(
        row["control"]["won"] and not row["challenger"]["won"] for row in valid
    )
    both_win = sum(row["control"]["won"] and row["challenger"]["won"] for row in valid)
    both_loss = sum(not row["control"]["won"] and not row["challenger"]["won"] for row in valid)
    sign_p = one_sided_sign_p(challenger_only, control_only)
    mulligan_delta = (
        sum(row["challenger"]["mulligans"] - row["control"]["mulligans"] for row in valid)
        / len(valid)
    )
    tempo_failures = sum(
        row["control"]["won"]
        and not row["challenger"]["won"]
        and row["twinned_vision"]["early_tempo_failure"]
        for row in valid
    )

    trigger = (
        challenger_only > control_only
        and sign_p <= 0.10
        and mulligan_delta <= 0.25
        and tempo_failures < 3
    )
    return StageSummary(
        valid_pairs=len(valid),
        challenger_only=challenger_only,
        control_only=control_only,
        both_win=both_win,
        both_loss=both_loss,
        sign_p=sign_p,
        mulligan_delta_per_game=mulligan_delta,
        challenger_early_tempo_failures_where_control_won=tempo_failures,
        trigger_replication=trigger,
    )
