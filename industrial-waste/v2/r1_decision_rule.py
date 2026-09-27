"""Numerical core of frozen IW_V2_R1_ENGINE_STRUCTURAL_2026_09_25.

This module performs no file IO, seed generation or game initialization. Its input
is a complete, already audited metric projection. It cannot establish the truth
of telemetry or replace the still-required runtime, provenance and evidence audit.
The only current callers are synthetic regression fixtures.
"""
from __future__ import annotations

from math import sqrt
from statistics import mean, variance

CONTROL = "IMMUTABLE_SUBMITTED_V1_0"
FAMILIES = ("COMPACT_LOOP", "RECURSIVE_EGGS", "LEAN_TRON_HYBRID")
DECKS = (CONTROL,) + FAMILIES
SCHEDULES = ("PLAY_SKIP_FIRST_DRAW", "DRAW_TAKE_FIRST_DRAW")
METRICS = ("loop_ready_by_t8", "conversion_by_t8", "colored_mana_failure", "total_mana_stranded_at_t4")
VALID_TERMINAL_STATUSES = {"REAL_TERMINAL", "TURN_CAP", "ACTION_CAP", "DRAW"}


def _validated_index(records: list[dict]) -> dict[tuple[str, int, str], dict]:
    if len(records) != 512:
        raise ValueError("R1 requires all 512 allocations; partial exposure is not analyzable")
    expected = {(deck, row, schedule) for deck in DECKS for row in range(1, 65) for schedule in SCHEDULES}
    indexed = {}
    for record in records:
        key = (record.get("deck"), record.get("row"), record.get("schedule"))
        if type(record.get("row")) is not int or key not in expected:
            raise ValueError("Unadmitted deck, row or play/draw schedule")
        if key in indexed:
            raise ValueError("Duplicate allocation; matched members cannot be counted twice")
        if record.get("validity") != "VALID" or record.get("terminal_status") not in VALID_TERMINAL_STATUSES:
            raise ValueError("Invalid or unresolved evidence must be quarantined before analysis")
        if any(type(record.get(metric)) is not bool for metric in METRICS):
            raise ValueError("All metric observations must be resolved Boolean values")
        indexed[key] = record
    if indexed.keys() != expected:
        raise ValueError("Incomplete fixed deck/row/schedule grid")
    return indexed


def _paired_metric(index: dict, family: str, metric: str) -> dict:
    improved = regressed = candidate_count = control_count = 0
    clusters = []
    for row in range(1, 65):
        paired_differences = []
        for schedule in SCHEDULES:
            challenger = index[(family, row, schedule)][metric]
            control = index[(CONTROL, row, schedule)][metric]
            candidate_count += int(challenger)
            control_count += int(control)
            improved += int(challenger and not control)
            regressed += int(control and not challenger)
            paired_differences.append(int(challenger) - int(control))
        clusters.append(mean(paired_differences))
    estimate = mean(clusters)
    standard_error = sqrt(variance(clusters) / 64)
    return {
        "candidate_numerator": candidate_count,
        "control_numerator": control_count,
        "denominator": 128,
        "paired_candidate_only": improved,
        "paired_control_only": regressed,
        "net_count_delta": candidate_count - control_count,
        "row_cluster_differences": clusters,
        "row_clusters": 64,
        "mean_paired_difference": estimate,
        "cluster_standard_error": standard_error,
        "normal_approximation_95_interval": [estimate - 1.96 * standard_error, estimate + 1.96 * standard_error],
    }


def evaluate_complete_metrics(records: list[dict]) -> dict:
    """Apply the exact prospective margins; never authorize a run or promote a deck."""
    index = _validated_index(records)
    comparisons = {}
    eligible = []
    for family in FAMILIES:
        metrics = {metric: _paired_metric(index, family, metric) for metric in METRICS}
        delta = {name: value["net_count_delta"] for name, value in metrics.items()}
        passes = (
            (delta["loop_ready_by_t8"] >= 8 or delta["conversion_by_t8"] >= 8)
            and delta["colored_mana_failure"] <= 4
            and delta["total_mana_stranded_at_t4"] <= 4
        )
        comparisons[family] = {"metrics": metrics, "eligible": passes}
        if passes:
            eligible.append(family)
    ranked = sorted(eligible, key=lambda family: (
        -comparisons[family]["metrics"]["conversion_by_t8"]["net_count_delta"],
        -comparisons[family]["metrics"]["loop_ready_by_t8"]["net_count_delta"],
        comparisons[family]["metrics"]["colored_mana_failure"]["net_count_delta"],
        comparisons[family]["metrics"]["total_mana_stranded_at_t4"]["net_count_delta"],
        family,
    ))
    return {
        "protocol_id": "IW_V2_R1_ENGINE_STRUCTURAL_2026_09_25",
        "scope": "NUMERICAL_CORE_REQUIRES_SEPARATE_PROVENANCE_AND_EVIDENCE_AUDIT",
        "authorizes_execution": False,
        "promotes_deck": False,
        "allocation_count": 512,
        "comparisons": comparisons,
        "eligible_in_declared_rank_order": ranked,
        "qualifies_for_prospective_recovery_gate": ranked[:2],
        "all_candidates_fail_r1": not ranked,
        "uncertainty_limit": "Normal approximation over 64 paired row-cluster means in a deterministic corpus; no tournament or unpaired independent-game inference.",
    }
