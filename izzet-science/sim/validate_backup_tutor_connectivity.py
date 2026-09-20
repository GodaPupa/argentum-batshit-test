#!/usr/bin/env python3
"""Seed-free Phase-2 gate for declared backup-card tutor connectivity."""
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
    required=set(harness.TUTOR_SPECS)|set(harness.BACKUP_CARDS)
    absent=sorted(required-set(cards))
    if absent:
        raise SystemExit(f"control identity missing required cards: {absent}")
    harness.tutor_regressions()
    harness.backup_tutor_connectivity_regressions()
    harness.tutor_execution_regressions()
    library=list(harness.BACKUP_CARDS)
    for tutor in harness.TUTOR_SPECS:
        targets=",".join(harness.backup_tutor_targets(tutor,library)) or "none"
        print(f"{tutor}={targets}")
    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-2-tutor-connectivity")
    print("policy_changes=0")
    print("sampled_games=0")
    print("seeds_consumed=0")
    print("outcome_claims=0")


if __name__=="__main__":
    main()
