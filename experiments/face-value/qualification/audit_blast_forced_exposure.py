#!/usr/bin/env python3
"""Audit fresh-seed nonlegal fixtures for blast casting and mode selection."""

import argparse
import json
import re
from collections import Counter
from pathlib import Path


CARDS = ("Pyroblast", "Red Elemental Blast", "Hydroblast", "Blue Elemental Blast")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", type=Path, required=True)
    parser.add_argument("--assignments", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assignments = []
    for line in args.assignments.read_text(encoding="utf-8").splitlines():
        family, caster_seat, seed = line.split("\t")
        assignments.append((family, int(caster_seat), int(seed)))

    totals = Counter()
    records = []
    for family, caster_seat, seed in assignments:
        path = args.logs / f"{family}-caster-seat{caster_seat}-seed{seed}.log"
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
        casts = {card: len(re.findall(rf" cast {re.escape(card)}(?:\s|$)", text, re.I | re.M)) for card in CARDS}
        counter_modes = len(re.findall(r"Resolve Stack: (?:Pyroblast|Red Elemental Blast|Hydroblast|Blue Elemental Blast).+Counter target (?:blue|red) spell", text, re.I))
        destroy_modes = len(re.findall(r"Resolve Stack: (?:Pyroblast|Red Elemental Blast|Hydroblast|Blue Elemental Blast).+Destroy target (?:blue|red) permanent", text, re.I))
        for card, value in casts.items():
            totals[card] += value
        totals[f"{family}_counter_modes"] += counter_modes
        totals[f"{family}_destroy_modes"] += destroy_modes
        totals["terminal_games"] += int(terminal)
        totals["flagged_games"] += int(bool(flags))
        records.append({"family": family, "caster_seat": caster_seat, "diagnostic_seed": seed, "log": str(path), "terminal": terminal, "flags": flags, "casts": casts, "counter_modes": counter_modes, "destroy_modes": destroy_modes})

    reasons = []
    if len(assignments) != 8:
        reasons.append(f"expected 8 assignments, found {len(assignments)}")
    if totals["terminal_games"] != 8:
        reasons.append(f"terminal games {totals['terminal_games']}/8")
    if totals["flagged_games"]:
        reasons.append(f"{totals['flagged_games']} flagged game(s)")
    for card in CARDS:
        if totals[card] < 2:
            reasons.append(f"{card} casts {totals[card]}, required at least 2")
    for family in ("red-blasts", "blue-blasts"):
        if totals[f"{family}_counter_modes"] < 1:
            reasons.append(f"{family} counter modes {totals[f'{family}_counter_modes']}, required at least 1")
        if totals[f"{family}_destroy_modes"] < 1:
            reasons.append(f"{family} destroy modes {totals[f'{family}_destroy_modes']}, required at least 1")
    passed = not reasons
    summary = {
        "classification": "DIAGNOSTIC_ONLY_NONLEGAL_BLAST_FORCED_EXPOSURE",
        "qualification_authority": False,
        "expected_games": 8,
        "games_found": len(records),
        "totals": dict(totals),
        "reasons": reasons,
        "blast_capability_demonstrated": passed,
        "records": records,
    }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if not passed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
