#!/usr/bin/env python3
"""Audit the isolated Highway Robbery opponent-pilot remediation."""

import argparse
import json
import re
from pathlib import Path

from audit_opponent_pilot_capability import count


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", type=Path, required=True)
    parser.add_argument("--assignments", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    rows = [line.split("\t") for line in args.assignments.read_text(encoding="utf-8").splitlines()]
    records = []
    totals = {"games": 0, "terminal_games": 0, "flagged_games": 0, "faithless_looting_casts": 0,
              "highway_robbery_casts": 0, "highway_robbery_activations": 0, "highway_robbery_mentions": 0}
    for matchup, opponent, face_seat, seed_raw in rows:
        seed = int(seed_raw)
        path = args.logs / f"{matchup}-face-seat{face_seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        metrics = {
            "faithless_looting_casts": count(text, r"cast Faithless Looting"),
            "highway_robbery_casts": count(text, r"cast Highway Robbery"),
            "highway_robbery_activations": count(text, r"activated Highway Robbery"),
            "highway_robbery_mentions": count(text, r"Highway Robbery"),
        }
        flags = []
        for name, pattern in {
            "exception": r"Exception|StackOverflowError|AssertionError",
            "slow_draw": r"Stopping slow match as draw",
            "outer_timeout": r"DIAGNOSTIC_PROCESS_EXIT_124",
            "missing_deck": r"Deck not found|Unable to load deck",
        }.items():
            if re.search(pattern, text, re.I):
                flags.append(name)
        if count(text, r"^Game Outcome: .+ has won because") > 1:
            flags.append("contradictory_terminal_bookkeeping")
        terminal = bool(re.search(r"^Game Result: .+(has won|Draw)", text, re.I | re.M))
        if not terminal:
            flags.append("missing_terminal_result")
        totals["games"] += 1
        totals["terminal_games"] += int(terminal)
        totals["flagged_games"] += int(bool(flags))
        for key, value in metrics.items():
            totals[key] += value
        records.append({"matchup": matchup, "opponent_file": opponent, "face_value_seat": int(face_seat),
                        "diagnostic_seed": seed, "log": str(path), "terminal": terminal,
                        "flags": flags, "metrics": metrics})

    reasons = []
    if len(rows) != 6:
        reasons.append(f"expected 6 games, found {len(rows)}")
    if totals["flagged_games"]:
        reasons.append(f"{totals['flagged_games']} runtime-flagged game(s)")
    if totals["faithless_looting_casts"] < 1:
        reasons.append("Faithless Looting execution coverage absent")
    if totals["highway_robbery_casts"] < 2:
        reasons.append(f"highway_robbery_casts={totals['highway_robbery_casts']}, required at least 2")
    summary = {
        "classification": "DIAGNOSTIC_ONLY_HIGHWAY_ROBBERY_REMEDIATION",
        "status": "LAB_REMEDIATION_SMOKE_DEMONSTRATED" if not reasons else "REMEDIATION_NOT_DEMONSTRATED",
        "reasons": reasons,
        "observed": totals,
        "records": records,
    }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if reasons:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
