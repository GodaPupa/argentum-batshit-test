#!/usr/bin/env python3
"""Audit narrow lab-only Forge pilot remediations on actual tournament lists."""

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

from audit_opponent_pilot_capability import count


MATCHUPS = ("mono-red-madness", "grixis-affinity", "elves")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", type=Path, required=True)
    parser.add_argument("--assignments", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    assignments = []
    for line in args.assignments.read_text(encoding="utf-8").splitlines():
        matchup, opponent, face_seat, seed = line.split("\t")
        assignments.append((matchup, opponent, int(face_seat), int(seed)))

    totals: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    records = []
    for matchup, opponent, face_seat, seed in assignments:
        path = args.logs / f"{matchup}-face-seat{face_seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        metrics = {
            "faithless_looting_casts": count(text, r"cast Faithless Looting"),
            "highway_robbery_casts": count(text, r"cast Highway Robbery"),
            "shaman_casts": count(text, r"cast Krark-Clan Shaman"),
            "shaman_activations": count(text, r"activated Krark-Clan Shaman"),
            "reckoners_bargain_casts": count(text, r"cast Reckoner's Bargain"),
            "ranger_casts": count(text, r"cast Quirion Ranger"),
            "ranger_activations": count(text, r"activated Quirion Ranger"),
            "winding_way_casts": count(text, r"cast Winding Way"),
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
        for key, value in metrics.items():
            totals[matchup][key] += value
        totals[matchup]["games"] += 1
        totals[matchup]["terminal_games"] += int(terminal)
        totals[matchup]["flagged_games"] += int(bool(flags))
        records.append({
            "matchup": matchup,
            "opponent_file": opponent,
            "face_value_seat": face_seat,
            "diagnostic_seed": seed,
            "log": str(path),
            "terminal": terminal,
            "flags": flags,
            "metrics": metrics,
        })

    requirements = {
        "mono-red-madness": {"faithless_looting_casts": 1, "highway_robbery_casts": 2},
        "grixis-affinity": {"shaman_casts": 1, "shaman_activations": 1, "reckoners_bargain_casts": 2},
        "elves": {"ranger_casts": 1, "ranger_activations": 1, "winding_way_casts": 2},
    }
    dispositions = {}
    for matchup in MATCHUPS:
        observed = dict(totals[matchup])
        reasons = []
        if observed.get("games") != 6:
            reasons.append(f"expected 6 games, found {observed.get('games', 0)}")
        if observed.get("flagged_games", 0):
            reasons.append(f"{observed['flagged_games']} runtime-flagged game(s)")
        for metric, minimum in requirements[matchup].items():
            value = observed.get(metric, 0)
            if value < minimum:
                reasons.append(f"{metric}={value}, required at least {minimum}")
        dispositions[matchup] = {
            "status": "LAB_REMEDIATION_SMOKE_DEMONSTRATED" if not reasons else "REMEDIATION_NOT_DEMONSTRATED",
            "reasons": reasons,
            "observed": observed,
        }

    summary = {
        "classification": "DIAGNOSTIC_ONLY_LAB_FORGE_AI_REMEDIATION_SMOKE",
        "expected_games": 18,
        "games_found": len(records),
        "all_remediations_demonstrated": all(
            row["status"] == "LAB_REMEDIATION_SMOKE_DEMONSTRATED" for row in dispositions.values()
        ),
        "dispositions": dispositions,
        "records": records,
    }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if len(assignments) != 18 or set(dispositions) != set(MATCHUPS) or not summary["all_remediations_demonstrated"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
