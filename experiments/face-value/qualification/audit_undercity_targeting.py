#!/usr/bin/env python3
"""Audit diagnostic-only Undercity Arena target selection."""

import argparse
import json
import re
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--logs", required=True, type=Path)
    ap.add_argument("--assignments", required=True, type=Path)
    ap.add_argument("--output", required=True, type=Path)
    args = ap.parse_args()

    rows = []
    for line in args.assignments.read_text(encoding="utf-8").splitlines():
        seat, seed = line.split("\t")
        rows.append((int(seat), int(seed)))

    records = []
    arena_attempts = 0
    arena_failures = 0
    terminal_games = 0
    flagged_games = 0

    for seat, seed in rows:
        path = args.logs / f"undercity-seat{seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""

        attempts = len(re.findall(
            r"^Add To Stack: .* triggered Undercity targeting \[[^\]]+\]$",
            text, re.I | re.M
        ))
        failures = len(re.findall(
            r"^Add To Stack: .*Undercity.*failed to target.*Goad target creature\.",
            text, re.I | re.M
        ))
        terminal = bool(re.search(r"^Game Result: .+(has won|Draw)", text, re.I | re.M))

        flags = []
        for name, pattern in {
            "exception": r"Exception|StackOverflowError|AssertionError",
            "slow_draw": r"Stopping slow match as draw",
            "outer_timeout": r"DIAGNOSTIC_PROCESS_EXIT_124",
            "missing_deck": r"Deck not found|Unable to load deck",
        }.items():
            if re.search(pattern, text, re.I):
                flags.append(name)
        if not terminal:
            flags.append("missing_terminal_result")

        arena_attempts += attempts
        arena_failures += failures
        terminal_games += int(terminal)
        flagged_games += int(bool(flags))
        records.append({
            "fixture_seat": seat,
            "diagnostic_seed": seed,
            "log": str(path),
            "terminal": terminal,
            "flags": flags,
            "arena_attempts": attempts,
            "arena_target_failures": failures,
        })

    demonstrated = (
        len(rows) == 8
        and terminal_games == 8
        and flagged_games == 0
        and arena_attempts >= 2
        and arena_failures == 0
    )

    summary = {
        "classification": "DIAGNOSTIC_ONLY_NONLEGAL_UNDERCITY_TARGETING",
        "qualification_authority": False,
        "expected_games": 8,
        "games_found": len(records),
        "terminal_games": terminal_games,
        "flagged_games": flagged_games,
        "arena_attempts": arena_attempts,
        "arena_target_failures": arena_failures,
        "undercity_targeting_capability_demonstrated": demonstrated,
        "records": records,
    }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if not demonstrated:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
