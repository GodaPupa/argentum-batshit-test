#!/usr/bin/env python3
"""Fail-closed automated audit for official postboard Stage 1."""

import argparse
import json
import math
import re
from collections import Counter, defaultdict
from pathlib import Path


CONTROL = "Face Value Temur Chrysalis E Final Forge 75"
SIDEBOARD_CARDS = ("Pyroblast", "Hydroblast", "Red Elemental Blast", "Blue Elemental Blast", "Relic of Progenitus", "Weather the Storm", "Breath Weapon", "Ancient Grudge", "Annul", "Tamiyo's Safekeeping", "Cast into the Fire", "Extract a Confession", "Duress", "Mwonvuli Acid-Moss", "Scattershot Archer", "Nylea's Disciple", "Gut Shot", "End the Festivities", "Krark-Clan Shaman")


def wilson(wins, games, z=1.959963984540054):
    if not games:
        return [None, None]
    p = wins / games
    d = 1 + z * z / games
    c = (p + z * z / (2 * games)) / d
    h = z * math.sqrt(p * (1 - p) / games + z * z / (4 * games * games)) / d
    return [c - h, c + h]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", type=Path, required=True)
    parser.add_argument("--assignments", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = []
    for line in args.assignments.read_text(encoding="utf-8").splitlines():
        index, matchup, seat, seed = line.split("\t")
        rows.append((int(index), matchup, int(seat), int(seed)))
    records = []
    for index, matchup, seat, seed in rows:
        path = args.logs / f"{index:03d}-{matchup}-seat{seat}-{seed}.log"
        text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        opp_seat = 3 - seat
        face_win = bool(re.search(rf"Ai\({seat}\)-{re.escape(CONTROL)}.+ has won!", text))
        opp_win = bool(re.search(rf"Ai\({opp_seat}\)-.+ has won!", text))
        draw = "ended in a Draw" in text
        flags = []
        for name, pattern in {"exception": r"Exception|StackOverflowError|AssertionError", "slow_draw": r"Stopping slow match as draw", "outer_timeout": r"QUALIFICATION_PROCESS_EXIT_124", "missing_deck": r"Deck not found|Unable to load deck", "unsupported": r"UnsupportedOperationException|not implemented"}.items():
            if re.search(pattern, text, re.I):
                flags.append(name)
        if sum((face_win, opp_win, draw)) != 1:
            flags.append(f"terminal_count_{sum((face_win, opp_win, draw))}")
        if len(re.findall(r"^Game Outcome: .+ has won because", text, re.I | re.M)) > 1:
            flags.append("contradictory_terminal_bookkeeping")
        turns = [int(x) for x in re.findall(r"^Turn: Turn (\d+)", text, re.M)]
        keeps = {}
        for player_seat, label in ((seat, "face"), (opp_seat, "opponent")):
            sizes = re.findall(rf"Mulligan: Ai\({player_seat}\)-.+ has kept a hand of (\d+) cards", text)
            keeps[label] = int(sizes[-1]) if sizes else None
            if keeps[label] is None:
                flags.append(f"missing_{label}_mulligan")
        exposure = {card: len(re.findall(rf" (?:cast|activated) {re.escape(card)}(?:\s|$)", text, re.I | re.M)) for card in SIDEBOARD_CARDS}
        metrics = {
            "faithless_looting_casts": len(re.findall(r"cast Faithless Looting", text, re.I)),
            "highway_robbery_casts": len(re.findall(r"cast Highway Robbery", text, re.I)),
            "lorien_uses": len(re.findall(r"(?:cast|activated) L.rien Revealed", text, re.I)),
            "shaman_casts": len(re.findall(r"cast Krark-Clan Shaman", text, re.I)),
            "shaman_activations": len(re.findall(r"activated Krark-Clan Shaman", text, re.I)),
            "bargain_casts": len(re.findall(r"cast Reckoner's Bargain", text, re.I)),
            "prism_casts": len(re.findall(r"cast Prophetic Prism", text, re.I)),
            "ranger_casts": len(re.findall(r"cast Quirion Ranger", text, re.I)),
            "ranger_activations": len(re.findall(r"activated Quirion Ranger", text, re.I)),
            "winding_way_casts": len(re.findall(r"cast Winding Way", text, re.I)),
        }
        joint = violations = 0
        for chunk in re.split(r"(?=^Turn: Turn )", text, flags=re.M):
            rally = [m.start() for m in re.finditer(r"cast Rally at the Hornburg", chunk, re.I)]
            bush = [m.start() for m in re.finditer(r"cast Goblin Bushwhacker", chunk, re.I)]
            if rally and bush:
                joint += 1
                violations += int(min(bush) < min(rally))
        metrics["rally_bushwhacker_joint_turns"] = joint
        metrics["rally_order_violations"] = violations
        records.append({"run_index": index, "matchup": matchup, "face_value_seat": seat, "seed": seed, "log": str(path), "winner": "face_value" if face_win else "opponent" if opp_win else "draw" if draw else "unknown", "turns": max(turns) if turns else None, "face_kept": keeps["face"], "opponent_kept": keeps["opponent"], "sideboard_exposure": exposure, "opponent_metrics": metrics, "flags": sorted(set(flags))})

    requirements = {
        "mono-red-madness": {"faithless_looting_casts": 1},
        "mono-blue-terror": {"lorien_uses": 1},
        "grixis-affinity": {"shaman_casts": 1, "shaman_activations": 1, "bargain_casts": 1},
        "monster-tron": {"prism_casts": 1},
        "jund-wildfire": {"shaman_casts": 1, "shaman_activations": 1},
        "elves": {"ranger_casts": 1, "ranger_activations": 1, "winding_way_casts": 1, "nyleas_disciple_exposure": 1},
        "mono-red-rally": {"rally_bushwhacker_joint_turns": 1, "rally_order_violations": 0},
    }
    blocks = {}
    for matchup in sorted(requirements):
        rr = [r for r in records if r["matchup"] == matchup]
        metric_totals = Counter()
        sideboard_totals = Counter()
        for r in rr:
            metric_totals.update(r["opponent_metrics"])
            sideboard_totals.update(r["sideboard_exposure"])
        metric_totals["nyleas_disciple_exposure"] = sideboard_totals["Nylea's Disciple"]
        reasons = []
        if len(rr) != 32:
            reasons.append(f"games={len(rr)}, required 32")
        if sum(bool(r["flags"]) for r in rr):
            reasons.append(f"flagged_games={sum(bool(r['flags']) for r in rr)}")
        for metric, minimum in requirements[matchup].items():
            value = metric_totals[metric]
            if metric == "rally_order_violations":
                if value != 0:
                    reasons.append(f"{metric}={value}, required 0")
            elif value < minimum:
                reasons.append(f"{metric}={value}, required at least {minimum}")
        seats = {}
        for seat in (1, 2):
            ss = [r for r in rr if r["face_value_seat"] == seat]
            seats[str(seat)] = {"games": len(ss), "wins": sum(r["winner"] == "face_value" for r in ss), "losses": sum(r["winner"] == "opponent" for r in ss), "draws": sum(r["winner"] == "draw" for r in ss)}
        wins = sum(r["winner"] == "face_value" for r in rr)
        blocks[matchup] = {"disposition": "AUTOMATED_GREEN_PENDING_GAMEPLAY_REVIEW" if not reasons else "QUARANTINED", "reasons": reasons, "games": len(rr), "wins": wins, "losses": sum(r["winner"] == "opponent" for r in rr), "draws": sum(r["winner"] == "draw" for r in rr), "win_rate": wins / len(rr) if rr else None, "wilson_95": wilson(wins, len(rr)), "seats": seats, "opponent_metrics": dict(metric_totals), "sideboard_exposure": dict(sideboard_totals)}
    global_reasons = []
    if len(rows) != 224 or len(records) != 224:
        global_reasons.append(f"records={len(records)}, required 224")
    if sum(b["sideboard_exposure"].get("Pyroblast", 0) for b in blocks.values()) < 1:
        global_reasons.append("no Pyroblast exposure")
    if sum(b["sideboard_exposure"].get("Hydroblast", 0) for b in blocks.values()) < 1:
        global_reasons.append("no Hydroblast exposure")
    passed = not global_reasons and all(b["disposition"].startswith("AUTOMATED_GREEN") for b in blocks.values())
    summary = {"classification": "OFFICIAL_QUALIFICATION_POSTBOARD_STAGE1_MAP_V2", "full_gauntlet_aggregate_permitted": False, "human_only_matchups": ["mono-blue-faeries"], "expected_games": 224, "games_found": len(records), "global_reasons": global_reasons, "automated_audit_passed": passed, "blocks": blocks, "records": records}
    args.output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, indent=2, sort_keys=True))
    if not passed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
