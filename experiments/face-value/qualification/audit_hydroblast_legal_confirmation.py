#!/usr/bin/env python3
"""Audit targeted fresh-seed legal Hydroblast integration confirmation."""

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
    records, hydro_casts, terminal_games, flagged_games = [], 0, 0, 0
    for matchup, face_seat_s, seed_s in rows:
        face_seat, seed = int(face_seat_s), int(seed_s)
        path = args.logs / f"{matchup}-face-seat{face_seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        flags = []
        for name, pattern in {"exception": r"Exception|StackOverflowError|AssertionError", "slow_draw": r"Stopping slow match as draw", "outer_timeout": r"DIAGNOSTIC_PROCESS_EXIT_124", "missing_deck": r"Deck not found|Unable to load deck", "unsupported": r"UnsupportedOperationException|not implemented"}.items():
            if re.search(pattern, text, re.I):
                flags.append(name)
        terminal = bool(re.search(r"^Game Result: .+(has won|Draw)", text, re.I | re.M))
        if not terminal:
            flags.append("missing_terminal_result")
        if len(re.findall(r"^Game Outcome: .+ has won because", text, re.I | re.M)) > 1:
            flags.append("contradictory_terminal_bookkeeping")
        casts = len(re.findall(r" cast Hydroblast(?:\s|$)", text, re.I | re.M))
        hydro_casts += casts
        terminal_games += int(terminal)
        flagged_games += int(bool(flags))
        records.append({"matchup": matchup, "face_value_seat": face_seat, "diagnostic_seed": seed, "log": str(path), "terminal": terminal, "flags": flags, "hydroblast_casts": casts})
    reasons = []
    if len(rows) != 8:
        reasons.append(f"expected 8 assignments, found {len(rows)}")
    if terminal_games != 8:
        reasons.append(f"terminal games {terminal_games}/8")
    if flagged_games:
        reasons.append(f"{flagged_games} flagged game(s)")
    if hydro_casts < 1:
        reasons.append(f"Hydroblast casts {hydro_casts}, required at least 1")
    passed = not reasons
    summary = {"classification": "DIAGNOSTIC_ONLY_LEGAL_HYDROBLAST_PROFILE_V4_CONFIRMATION", "qualification_authority": False, "expected_games": 8, "games_found": len(records), "terminal_games": terminal_games, "flagged_games": flagged_games, "hydroblast_casts": hydro_casts, "reasons": reasons, "confirmation_passed": passed, "records": records}
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if not passed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
