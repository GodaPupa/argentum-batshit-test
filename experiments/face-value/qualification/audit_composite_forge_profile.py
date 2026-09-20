#!/usr/bin/env python3
"""Fail-closed integrated audit for Face Value qualification Forge profile v1."""

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

from audit_opponent_pilot_capability import count, rally_order


EXPECTED = {
    "mono-red-madness": 4,
    "mono-blue-terror": 4,
    "grixis-affinity": 6,
    "monster-tron": 4,
    "jund-wildfire": 4,
    "elves": 4,
    "mono-red-rally": 12,
    "mono-blue-faeries": 8,
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", required=True, type=Path)
    parser.add_argument("--assignments", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
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
            "lorien_uses": count(text, r"(?:cast|activated) L.rien Revealed"),
            "shaman_casts": count(text, r"cast Krark-Clan Shaman"),
            "shaman_activations": count(text, r"activated Krark-Clan Shaman"),
            "reckoners_bargain_casts": count(text, r"cast Reckoner's Bargain"),
            "prism_casts": count(text, r"cast Prophetic Prism"),
            "ranger_casts": count(text, r"cast Quirion Ranger"),
            "ranger_activations": count(text, r"activated Quirion Ranger"),
            "winding_way_casts": count(text, r"cast Winding Way"),
        }
        joint, violations = rally_order(text)
        metrics["rally_bushwhacker_joint_turns"] = joint
        metrics["rally_order_violations"] = violations
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
        records.append({"matchup": matchup, "opponent_file": opponent, "face_value_seat": face_seat,
                        "diagnostic_seed": seed, "log": str(path), "terminal": terminal,
                        "flags": flags, "metrics": metrics})

    requirements = {
        "mono-red-madness": {"faithless_looting_casts": 2, "highway_robbery_casts": 2},
        "mono-blue-terror": {"lorien_uses": 1},
        "grixis-affinity": {"shaman_casts": 1, "shaman_activations": 1, "reckoners_bargain_casts": 1},
        "monster-tron": {"prism_casts": 1},
        "jund-wildfire": {"shaman_casts": 1, "shaman_activations": 1},
        "elves": {"ranger_casts": 1, "ranger_activations": 1, "winding_way_casts": 1},
        "mono-red-rally": {"rally_bushwhacker_joint_turns": 2, "rally_order_violations": 0},
        "mono-blue-faeries": {},
    }
    dispositions = {}
    for matchup, expected_games in EXPECTED.items():
        observed = dict(totals[matchup])
        reasons = []
        if observed.get("games", 0) != expected_games:
            reasons.append(f"expected {expected_games} games, found {observed.get('games', 0)}")
        if observed.get("flagged_games", 0):
            reasons.append(f"{observed['flagged_games']} runtime-flagged game(s)")
        for metric, required in requirements[matchup].items():
            value = observed.get(metric, 0)
            if metric == "rally_order_violations":
                if value != required:
                    reasons.append(f"{metric}={value}, required {required}")
            elif value < required:
                reasons.append(f"{metric}={value}, required at least {required}")
        dispositions[matchup] = {"status": "PROFILE_V1_CAPABILITY_DEMONSTRATED" if not reasons else "NOT_DEMONSTRATED",
                                   "reasons": reasons, "observed": observed}

    summary = {
        "classification": "DIAGNOSTIC_ONLY_INTEGRATED_FORGE_PROFILE_V1_GATE",
        "expected_games": sum(EXPECTED.values()),
        "games_found": len(records),
        "all_matchups_demonstrated": all(x["status"] == "PROFILE_V1_CAPABILITY_DEMONSTRATED" for x in dispositions.values()),
        "dispositions": dispositions,
        "records": records,
    }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if len(assignments) != sum(EXPECTED.values()) or not summary["all_matchups_demonstrated"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
