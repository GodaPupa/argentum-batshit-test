#!/usr/bin/env python3
"""Seed-free Phase-5 gate for an explicit, dormant Capsize tutor policy."""
from __future__ import annotations

import hashlib
import importlib.util
from pathlib import Path


ROOT=Path(__file__).resolve().parents[2]
HARNESS=ROOT/"izzet-science/sim/mana_harness.py"
CONTROL=ROOT/"izzet-science/v0.7-control.md"
CONTROL_SHA256="726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest()!=CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    spec=importlib.util.spec_from_file_location("mana_harness",HARNESS)
    if spec is None or spec.loader is None:
        raise SystemExit("unable to load harness")
    harness=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(harness)
    _,cards=harness.parse_deck(CONTROL)
    if len(cards)!=99:
        raise SystemExit(f"control main-deck identity mismatch: {len(cards)} cards")
    harness.tutor_regressions()
    harness.backup_tutor_connectivity_regressions()
    harness.backup_tutor_opportunity_regressions()
    harness.tutor_execution_regressions()
    harness.capsize_tutor_policy_regressions()
    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-5-capsize-tutor-policy")
    print("default_trajectory_changes=0")
    print("sampled_games=0")
    print("seeds_consumed=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE5_SEED_FREE_VALIDATED")


if __name__=="__main__":
    main()
