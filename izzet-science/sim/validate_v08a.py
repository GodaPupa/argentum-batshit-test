#!/usr/bin/env python3
"""Seed-free identity and deterministic-regression gate for v0.8-A."""
from __future__ import annotations

import collections
import importlib.util
from pathlib import Path


ROOT=Path(__file__).resolve().parents[2]
HARNESS=ROOT/"izzet-science/sim/mana_harness.py"
CONTROL=ROOT/"izzet-science/v0.7-control.md"
CHALLENGER=ROOT/"izzet-science/challengers/v08A-mizzium-skin.md"


def main() -> None:
    spec=importlib.util.spec_from_file_location("mana_harness",HARNESS)
    if spec is None or spec.loader is None:
        raise SystemExit("unable to load harness")
    harness=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(harness)
    _,control=harness.parse_deck(CONTROL)
    _,challenger=harness.parse_deck(CHALLENGER)
    added=collections.Counter(challenger)-collections.Counter(control)
    removed=collections.Counter(control)-collections.Counter(challenger)
    if added != collections.Counter({"Mizzium Skin":1}):
        raise SystemExit(f"unexpected additions: {added}")
    if removed != collections.Counter({"Turn Aside":1}):
        raise SystemExit(f"unexpected removals: {removed}")

    no_arg=(
        harness.regressions,harness.state_regressions,harness.development_regressions,
        harness.payment_regressions,harness.fetch_regressions,harness.rock_regressions,
        harness.policy_regressions,harness.mutating_payment_regressions,
        harness.unified_payment_regressions,harness.snow_regressions,
        harness.selection_regressions,harness.selection_resolution_regressions,
        harness.complex_selection_regressions,harness.draw_discard_regressions,
        harness.scheduler_regressions,harness.integration_regressions,
        harness.colored_payment_regressions,harness.mutable_library_regressions,
        harness.combo_assembly_regressions,harness.commander_regressions,
        harness.lethal_regressions,harness.interaction_regressions,
        harness.tutor_regressions,harness.tutor_execution_regressions,
        harness.first_lethal_regression,harness.electromancer_lethal_regressions,
        harness.five_mana_ritual_route_regressions,harness.seething_song_launch_regressions,
    )
    for regression in no_arg:
        regression()
    for regression in (
        harness.simulation_regressions,harness.readiness_regressions,
        harness.loop_selection_regression,
    ):
        regression(control)
    print("identity_delta=-Turn Aside,+Mizzium Skin")
    print("deterministic_regressions=pass")
    print("sampled_games=0")


if __name__=="__main__":
    main()
