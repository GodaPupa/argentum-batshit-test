#!/usr/bin/env python3
"""Audit diagnostic-only Forge opponent-pilot capability logs.

This gate measures execution coverage and obvious sequencing/runtime defects. It
does not create matchup evidence and must never expose or consume official
qualification seeds.
"""

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path


MATCHUPS = (
    "mono-red-madness",
    "mono-blue-terror",
    "grixis-affinity",
    "monster-tron",
    "jund-wildfire",
    "elves",
    "mono-red-rally",
    "mono-blue-faeries",
)


def count(text: str, pattern: str) -> int:
    return len(re.findall(pattern, text, re.I | re.M))


def rally_order(text: str) -> tuple[int, int]:
    joint_turns = 0
    violations = 0
    chunks = re.split(r"(?=^Turn: Turn )", text, flags=re.M)
    for chunk in chunks:
        rally = [m.start() for m in re.finditer(r"cast Rally at the Hornburg", chunk, re.I)]
        bush = [m.start() for m in re.finditer(r"cast Goblin Bushwhacker", chunk, re.I)]
        if rally and bush:
            joint_turns += 1
            if min(bush) < min(rally):
                violations += 1
    return joint_turns, violations


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

    records = []
    totals: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for matchup, opponent, face_seat, seed in assignments:
        path = args.logs / f"{matchup}-face-seat{face_seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        metrics = {
            "faithless_looting_casts": count(text, r"cast Faithless Looting"),
            "highway_robbery_casts": count(text, r"cast Highway Robbery"),
            "lorien_casts": count(text, r"cast L.rien Revealed"),
            "lorien_activations": count(text, r"activated L.rien Revealed"),
            "shaman_casts": count(text, r"cast Krark-Clan Shaman"),
            "shaman_activations": count(text, r"activated Krark-Clan Shaman"),
            "reckoners_bargain_casts": count(text, r"cast Reckoner's Bargain"),
            "prism_casts": count(text, r"cast Prophetic Prism"),
            "prism_activations": count(text, r"activated Prophetic Prism"),
            "ranger_casts": count(text, r"cast Quirion Ranger"),
            "ranger_activations": count(text, r"activated Quirion Ranger"),
            "winding_way_casts": count(text, r"cast Winding Way"),
        }
        joint, violations = rally_order(text)
        metrics["rally_bushwhacker_joint_turns"] = joint
        metrics["rally_order_violations"] = violations

        flags = []
        patterns = {
            "exception": r"Exception|StackOverflowError|AssertionError",
            "slow_draw": r"Stopping slow match as draw",
            "outer_timeout": r"DIAGNOSTIC_PROCESS_EXIT_124",
            "missing_deck": r"Deck not found|Unable to load deck",
        }
        flags.extend(name for name, pattern in patterns.items() if re.search(pattern, text, re.I))
        outcome_winners = count(text, r"^Game Outcome: .+ has won because",)
        if outcome_winners > 1:
            flags.append("contradictory_terminal_bookkeeping")
        terminal = bool(re.search(r"^Game Result: .+(has won|Draw)", text, re.I | re.M))
        if not terminal:
            flags.append("missing_terminal_result")

        for key, value in metrics.items():
            totals[matchup][key] += value
        totals[matchup]["games"] += 1
        totals[matchup]["terminal_games"] += int(terminal)
        totals[matchup]["flagged_games"] += int(bool(flags))
        records.append(
            {
                "matchup": matchup,
                "opponent_file": opponent,
                "face_value_seat": face_seat,
                "diagnostic_seed": seed,
                "log": str(path),
                "terminal": terminal,
                "flags": flags,
                "metrics": metrics,
            }
        )

    requirements = {
        "mono-red-madness": {"faithless_looting_casts": 1, "highway_robbery_casts": 1},
        "mono-blue-terror": {"lorien_uses": 1},
        "grixis-affinity": {"shaman_casts": 1, "shaman_activations": 1, "reckoners_bargain_casts": 1},
        "monster-tron": {"prism_casts": 1},
        "jund-wildfire": {"shaman_casts": 1, "shaman_activations": 1},
        "elves": {"ranger_casts": 1, "ranger_activations": 1, "winding_way_casts": 1},
        "mono-red-rally": {"rally_bushwhacker_joint_turns": 1, "rally_order_violations": 0},
        "mono-blue-faeries": {},
    }

    dispositions = {}
    for matchup in MATCHUPS:
        observed = dict(totals[matchup])
        if matchup == "mono-blue-terror":
            observed["lorien_uses"] = observed.get("lorien_casts", 0) + observed.get("lorien_activations", 0)
        reasons = []
        if observed.get("games") != 4:
            reasons.append(f"expected 4 games, found {observed.get('games', 0)}")
        if observed.get("flagged_games", 0):
            reasons.append(f"{observed['flagged_games']} runtime-flagged game(s)")
        for metric, minimum in requirements[matchup].items():
            value = observed.get(metric, 0)
            if metric == "rally_order_violations":
                if value != minimum:
                    reasons.append(f"{metric}={value}, required {minimum}")
            elif value < minimum:
                reasons.append(f"{metric}={value}, required at least {minimum}")
        dispositions[matchup] = {
            "status": "PRELIMINARY_CAPABILITY_DEMONSTRATED" if not reasons else "NOT_DEMONSTRATED",
            "reasons": reasons,
            "observed": observed,
        }

    summary = {
        "classification": "DIAGNOSTIC_ONLY_NONQUALIFICATION_OPPONENT_PILOT_SCREEN",
        "expected_games": 32,
        "games_found": len(records),
        "all_matchups_demonstrated": all(
            row["status"] == "PRELIMINARY_CAPABILITY_DEMONSTRATED" for row in dispositions.values()
        ),
        "dispositions": dispositions,
        "records": records,
    }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if len(assignments) != 32 or set(dispositions) != set(MATCHUPS) or not summary["all_matchups_demonstrated"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
