#!/usr/bin/env python3
"""Audit fresh legal-map integration games under Forge profile v4."""

import argparse
import json
import re
from collections import Counter
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", type=Path, required=True)
    parser.add_argument("--assignments", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = [line.split("\t") for line in args.assignments.read_text(encoding="utf-8").splitlines()]
    totals = Counter()
    records = []
    for matchup, face_seat_s, seed_s in rows:
        face_seat, seed = int(face_seat_s), int(seed_s)
        path = args.logs / f"{matchup}-face-seat{face_seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        flags = []
        for name, pattern in {
            "exception": r"Exception|StackOverflowError|AssertionError",
            "slow_draw": r"Stopping slow match as draw",
            "outer_timeout": r"DIAGNOSTIC_PROCESS_EXIT_124",
            "missing_deck": r"Deck not found|Unable to load deck",
            "unsupported": r"UnsupportedOperationException|not implemented",
        }.items():
            if re.search(pattern, text, re.I):
                flags.append(name)
        terminal = bool(re.search(r"^Game Result: .+(has won|Draw)", text, re.I | re.M))
        if not terminal:
            flags.append("missing_terminal_result")
        if len(re.findall(r"^Game Outcome: .+ has won because", text, re.I | re.M)) > 1:
            flags.append("contradictory_terminal_bookkeeping")
        casts = {card: len(re.findall(rf" cast {card}(?:\s|$)", text, re.I | re.M)) for card in ("Pyroblast", "Hydroblast")}
        totals["Pyroblast"] += casts["Pyroblast"]
        totals["Hydroblast"] += casts["Hydroblast"]
        totals["terminal_games"] += int(terminal)
        totals["flagged_games"] += int(bool(flags))
        totals[f"games_{matchup}"] += 1
        records.append({"matchup": matchup, "face_value_seat": face_seat, "diagnostic_seed": seed, "log": str(path), "terminal": terminal, "flags": flags, "conditional_blast_casts": casts})
    reasons = []
    if len(rows) != 14:
        reasons.append(f"expected 14 assignments, found {len(rows)}")
    if totals["terminal_games"] != 14:
        reasons.append(f"terminal games {totals['terminal_games']}/14")
    if totals["flagged_games"]:
        reasons.append(f"{totals['flagged_games']} flagged game(s)")
    for matchup in sorted(k[6:] for k in totals if k.startswith("games_")):
        if totals[f"games_{matchup}"] != 2:
            reasons.append(f"{matchup} games {totals[f'games_{matchup}']}/2")
    for card in ("Pyroblast", "Hydroblast"):
        if totals[card] < 1:
            reasons.append(f"{card} casts {totals[card]}, required at least 1")
    passed = not reasons
    summary = {"classification": "DIAGNOSTIC_ONLY_LEGAL_POSTBOARD_MAP_V1_PROFILE_V4_INTEGRATION", "qualification_authority": False, "expected_games": 14, "games_found": len(records), "totals": dict(totals), "reasons": reasons, "automated_integration_passed": passed, "records": records}
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if not passed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
