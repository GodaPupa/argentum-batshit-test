#!/usr/bin/env python3
"""Seed-free recovery gate for exact selector/executor mana parity."""
from __future__ import annotations

import hashlib
from pathlib import Path

import mana_harness as harness
import run_v09_capsize_paired_pilot as retired_runner


ROOT=Path(__file__).resolve().parents[2]
CONTROL=ROOT/"izzet-science/v0.7-control.md"
CONTROL_SHA256="726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest()!=CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    if not retired_runner.RETIRED or "consumed" not in retired_runner.RETIRED_REASON:
        raise SystemExit("failed Phase-9 runner is not retired")
    harness.colored_payment_regressions()
    harness.capsize_tutor_policy_regressions()
    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-10-payment-recovery")
    print("shared_activation_engine=1")
    print("selector_executor_parity_fixtures=3")
    print("covered_sources=Izzet Signet,Prismatic Lens,Star Compass")
    print("phase9_runner_retired=1")
    print("sampled_games=0")
    print("seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE10_SEED_FREE_VALIDATED")


if __name__=="__main__":
    main()
