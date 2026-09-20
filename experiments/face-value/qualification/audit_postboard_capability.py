#!/usr/bin/env python3
"""Audit diagnostic-only games for frozen postboard map v1."""

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path


BLASTS = ("Blue Elemental Blast", "Hydroblast", "Pyroblast", "Red Elemental Blast")


def occurrences(text: str, card: str, action: str = r"(?:cast|activated)") -> int:
    return len(re.findall(rf" {action} {re.escape(card)}(?:\s|$)", text, re.I | re.M))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", type=Path, required=True)
    parser.add_argument("--assignments", type=Path, required=True)
    parser.add_argument("--map", dest="map_path", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    maps = json.loads(args.map_path.read_text(encoding="utf-8"))["matchups"]
    assignments = []
    for line in args.assignments.read_text(encoding="utf-8").splitlines():
        matchup, face_seat, seed = line.split("\t")
        assignments.append((matchup, int(face_seat), int(seed)))

    records = []
    totals = defaultdict(lambda: defaultdict(int))
    blast_casts = relic_casts = relic_activations = 0
    for matchup, face_seat, seed in assignments:
        path = args.logs / f"{matchup}-face-seat{face_seat}-seed{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        terminal = bool(re.search(r"^Game Result: .+(has won|Draw)", text, re.I | re.M))
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
        if not terminal:
            flags.append("missing_terminal_result")
        if len(re.findall(r"^Game Outcome: .+ has won because", text, re.I | re.M)) > 1:
            flags.append("contradictory_terminal_bookkeeping")
        mapped_cards = sorted(set(maps[matchup]["face_value"]["in"]) | set(maps[matchup]["opponent"]["in"]))
        exposure = {card: occurrences(text, card) for card in mapped_cards}
        for card, value in exposure.items():
            totals[matchup][card] += value
        totals[matchup]["games"] += 1
        totals[matchup]["terminal_games"] += int(terminal)
        totals[matchup]["flagged_games"] += int(bool(flags))
        blast_casts += sum(occurrences(text, card, "cast") for card in BLASTS)
        relic_casts += occurrences(text, "Relic of Progenitus", "cast")
        relic_activations += occurrences(text, "Relic of Progenitus", "activated")
        records.append({
            "matchup": matchup, "face_value_seat": face_seat, "diagnostic_seed": seed,
            "log": str(path), "terminal": terminal, "flags": flags, "mapped_card_exposure": exposure,
        })

    dispositions = {}
    for matchup in sorted(maps):
        observed = dict(totals[matchup])
        mapped_exposure = sum(v for k, v in observed.items() if k not in {"games", "terminal_games", "flagged_games"})
        reasons = []
        if observed.get("games") != 4:
            reasons.append(f"expected 4 games, found {observed.get('games', 0)}")
        if observed.get("terminal_games") != 4:
            reasons.append(f"terminal games {observed.get('terminal_games', 0)}/4")
        if observed.get("flagged_games", 0):
            reasons.append(f"{observed['flagged_games']} flagged game(s)")
        if mapped_exposure < 1:
            reasons.append("no mapped-in card exposure")
        dispositions[matchup] = {"status": "AUTOMATED_GREEN_PENDING_GAMEPLAY_REVIEW" if not reasons else "QUARANTINED", "reasons": reasons, "observed": observed}

    global_reasons = []
    if len(assignments) != 28 or len(records) != 28:
        global_reasons.append(f"expected 28 records, found {len(records)}")
    if blast_casts < 4:
        global_reasons.append(f"blast casts {blast_casts}, required at least 4")
    if relic_casts < 2:
        global_reasons.append(f"Relic casts {relic_casts}, required at least 2")
    if relic_activations < 1:
        global_reasons.append(f"Relic activations {relic_activations}, required at least 1")
    passed = not global_reasons and all(x["status"].startswith("AUTOMATED_GREEN") for x in dispositions.values())
    summary = {
        "classification": "DIAGNOSTIC_ONLY_POSTBOARD_MAP_V1_CAPABILITY",
        "qualification_authority": False,
        "expected_games": 28,
        "games_found": len(records),
        "blast_casts": blast_casts,
        "relic_casts": relic_casts,
        "relic_activations": relic_activations,
        "global_reasons": global_reasons,
        "automated_capability_passed": passed,
        "dispositions": dispositions,
        "records": records,
    }
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if not passed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
