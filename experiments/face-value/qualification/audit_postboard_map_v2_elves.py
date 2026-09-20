#!/usr/bin/env python3
"""Audit the fresh legal Elves integration for postboard map v2."""

import argparse
import json
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", type=Path, required=True)
    parser.add_argument("--assignments", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = [line.split("\t") for line in args.assignments.read_text(encoding="utf-8").splitlines()]
    records, casts, terminal_games, flagged_games = [], 0, 0, 0
    for seat_s, seed_s in rows:
        seat, seed = int(seat_s), int(seed_s)
        path = args.logs / f"elves-face-seat{seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        flags = []
        for name, pattern in {"exception": r"Exception|StackOverflowError|AssertionError", "slow_draw": r"Stopping slow match as draw", "outer_timeout": r"DIAGNOSTIC_PROCESS_EXIT_124", "missing_deck": r"Deck not found|Unable to load deck", "unsupported": r"UnsupportedOperationException|not implemented"}.items():
            if re.search(pattern, text, re.I):
                flags.append(name)
        terminal = bool(re.search(r"^Game Result: .+(has won|Draw)", text, re.I | re.M))
        if not terminal:
            flags.append("missing_terminal_result")
        game_casts = len(re.findall(r" cast Nylea's Disciple(?:\s|$)", text, re.I | re.M))
        casts += game_casts
        terminal_games += int(terminal)
        flagged_games += int(bool(flags))
        records.append({"face_value_seat": seat, "diagnostic_seed": seed, "log": str(path), "terminal": terminal, "flags": flags, "nyleas_disciple_casts": game_casts})
    reasons = []
    if len(rows) != 4:
        reasons.append(f"expected 4 assignments, found {len(rows)}")
    if terminal_games != 4:
        reasons.append(f"terminal games {terminal_games}/4")
    if flagged_games:
        reasons.append(f"{flagged_games} flagged game(s)")
    if casts < 1:
        reasons.append(f"Nylea's Disciple casts {casts}, required at least 1")
    passed = not reasons
    summary = {"classification": "DIAGNOSTIC_ONLY_LEGAL_POSTBOARD_MAP_V2_ELVES_INTEGRATION", "qualification_authority": False, "expected_games": 4, "games_found": len(records), "terminal_games": terminal_games, "flagged_games": flagged_games, "nyleas_disciple_casts": casts, "reasons": reasons, "integration_passed": passed, "records": records}
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if not passed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
