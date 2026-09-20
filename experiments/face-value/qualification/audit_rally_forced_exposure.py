#!/usr/bin/env python3
"""Audit a diagnostic-only forced-exposure Rally sequencing fixture."""

import argparse
import json
import re
from pathlib import Path

from audit_opponent_pilot_capability import rally_order


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", required=True, type=Path)
    parser.add_argument("--assignments", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    assignments = []
    for line in args.assignments.read_text(encoding="utf-8").splitlines():
        fixture_seat, seed = line.split("\t")
        assignments.append((int(fixture_seat), int(seed)))

    records = []
    joint_total = 0
    violation_total = 0
    flagged_games = 0
    terminal_games = 0
    for fixture_seat, seed in assignments:
        path = args.logs / f"rally-forced-seat{fixture_seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        joint, violations = rally_order(text)
        flags = []
        for name, pattern in {
            "exception": r"Exception|StackOverflowError|AssertionError",
            "slow_draw": r"Stopping slow match as draw",
            "outer_timeout": r"DIAGNOSTIC_PROCESS_EXIT_124",
            "missing_deck": r"Deck not found|Unable to load deck",
        }.items():
            if re.search(pattern, text, re.I):
                flags.append(name)
        terminal = bool(re.search(r"^Game Result: .+(has won|Draw)", text, re.I | re.M))
        if not terminal:
            flags.append("missing_terminal_result")
        if len(re.findall(r"^Game Outcome: .+ has won because", text, re.I | re.M)) > 1:
            flags.append("contradictory_terminal_bookkeeping")
        joint_total += joint
        violation_total += violations
        terminal_games += int(terminal)
        flagged_games += int(bool(flags))
        records.append({
            "fixture_seat": fixture_seat,
            "diagnostic_seed": seed,
            "log": str(path),
            "terminal": terminal,
            "flags": flags,
            "joint_rally_bushwhacker_turns": joint,
            "order_violations": violations,
        })

    demonstrated = (
        len(assignments) == 8
        and terminal_games == 8
        and flagged_games == 0
        and joint_total >= 5
        and violation_total == 0
    )
    summary = {
        "classification": "DIAGNOSTIC_ONLY_NONLEGAL_FORCED_EXPOSURE_RALLY_SEQUENCING",
        "qualification_authority": False,
        "expected_games": 8,
        "games_found": len(records),
        "terminal_games": terminal_games,
        "flagged_games": flagged_games,
        "joint_rally_bushwhacker_turns": joint_total,
        "order_violations": violation_total,
        "sequencing_capability_demonstrated": demonstrated,
        "records": records,
    }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if not demonstrated:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
