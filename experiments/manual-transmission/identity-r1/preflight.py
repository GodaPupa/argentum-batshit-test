#!/usr/bin/env python3
"""Read-only structural preflight for identity-preserving Manual Transmission work.

This is NOT a Commander gameplay test, win-rate test, policy improvement, or
authorization to change frozen v0.7. It does not initialize the rules engine.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from math import comb
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[3]
FROZEN_ID = "6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111"
BLOBS = {
    "experiments/manual-transmission/control/v0.7.decklist.txt": "c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89",
    "experiments/manual-transmission/PROVENANCE_IMPORT.md": "b0d109febc8e03c2f25ce7960739e5dd36d3e600",
    "experiments/manual-transmission/results/FINAL_CONCLUSION_2026_09_24.md": "dbf081b0c6e205c65750866adcb6f7d43f532caf",
    "experiments/manual-transmission/protocols/same-hardware-elasticity-r1.json": "47f7f6458ee2e760ae961dfd6e56a87717a4ef67",
    "experiments/manual-transmission/protocols/final-race-policy-r1.json": "7c41bec574b616e6f628c87effa333929e288f7f",
    "gameplay/manual-v08-wan/CHALLENGER.decklist.txt": "854cedf1f3f789c2ddc49c1f9004f2fa63431851",
    "ai/src/main/kotlin/com/wingedsheep/ai/engine/AiProfile.kt": "180918b2d7a59309e7b0617f2579fb2a6d06b7d5",
}
SECTION_TOTALS = {"Commander": 1, "Creatures": 52, "Noncreatures": 15, "Lands": 32}
BASICS = {"Forest": 6, "Island": 2, "Mountain": 2}
RACE_AXES = {
    "R3-HT", "R3-M", "R3-BF", "R3-SH", "R3-SY", "R3-RS", "R3-KB"
}
SPORT_LAYERS = ["R3 Balanced Mulligan", "R3-P Priority Conservation", "R3-K Kinnan Awareness"]
# Examples of identity-protecting roles; overlapping roles are intentional.
# Presence is a structural test, not proof of a card's strategic effectiveness.
ROLE_ANCHORS = {
    "Cruise/value-and-fair-floor": {
        "Wood Elves", "Farhaven Elf", "Coiling Oracle", "Elvish Visionary",
        "Wall of Blossoms", "Beast Whisperer", "Soul of the Harvest",
        "Aether Channeler", "Lifecrafter’s Bestiary", "Decimator of the Provinces",
    },
    "Sport/adaptive-interaction": {
        "Foundation Breaker", "Bane of Progress", "Amphin Mutineer",
        "Tishana’s Tidebinder", "World Breaker", "Pongify",
        "Rapid Hybridization", "Reality Shift", "Chaos Warp",
        "Spellskite", "Sylvan Safekeeper", "Glen Elendra Archmage",
    },
    "Race/deterministic-payoffs-and-access": {
        "Ancestral Statue", "Walking Ballista", "Purphoros, God of the Forge",
        "Imperial Recruiter", "Fauna Shaman", "Chord of Calling",
        "Cloud of Faeries", "Peregrine Drake",
    },
}


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def parse_deck(text: str) -> tuple[dict[str, int], Counter[str]]:
    sections: dict[str, int] = {}
    cards: Counter[str] = Counter()
    section: str | None = None
    for line_no, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line in SECTION_TOTALS:
            require(line not in sections, f"repeated section {line}")
            section = line
            sections[line] = 0
            continue
        match = re.fullmatch(r"([1-9][0-9]*) (.+)", line)
        require(match is not None and section is not None, f"invalid deck entry line {line_no}")
        quantity = int(match.group(1))
        name = match.group(2)
        cards[name] += quantity
        sections[section] += quantity
    return sections, cards


def check_deck_structure(text: str) -> dict:
    sections, cards = parse_deck(text)
    require(sections == SECTION_TOTALS, f"frozen section sizes changed: {sections}")
    require(sum(cards.values()) == 100, "not exactly 100 cards")
    require(cards["Animar, Soul of Elements"] == 1, "wrong commander")
    require(cards["Disciple of Freyalise"] == 1, "lost land-capable MDFC")
    for name, count in BASICS.items():
        require(cards[name] == count, f"frozen basic count changed: {name}")
    for name, count in cards.items():
        if name not in BASICS:
            require(count == 1, f"duplicate nonbasic or commander: {name}")
    for gear, anchors in ROLE_ANCHORS.items():
        missing = sorted(anchors - set(cards))
        require(not missing, f"{gear} role anchor(s) missing: {missing}")
    return {
        "card_count": sum(cards.values()),
        "unique_names": len(cards),
        "sections": sections,
        "land_capable_mdfc": "Disciple of Freyalise",
        "role_anchor_counts": {gear: len(anchors) for gear, anchors in ROLE_ANCHORS.items()},
        "role_anchors_overlap_allowed": True,
    }


def check_policy(elasticity: dict, race: dict, conclusion: str) -> dict:
    require(elasticity["protocol"] == "MT_SAME_HARDWARE_ELASTICITY_R1_2026_09_24", "protocol mismatch")
    hw = elasticity["hardware"]
    require(hw["sha256"] == FROZEN_ID, "elasticity hardware mismatch")
    require(hw["game_changers"] == 0, "frozen Game-Changer declaration changed")
    require(hw["card_changes_authorized"] is False, "unexpected hardware edit authorization")
    modes = elasticity["mode_definitions"]
    require(set(modes) == {"Cruise-R1", "Sport-R1", "Race-R1"}, "mode set changed")
    require(modes["Cruise-R1"]["generic_layers"] == SPORT_LAYERS[:1], "Cruise layer mismatch")
    require(modes["Sport-R1"]["generic_layers"] == SPORT_LAYERS, "Sport layer mismatch")
    require(modes["Race-R1"]["generic_layers"] == SPORT_LAYERS, "Race generic mismatch")
    require(modes["Cruise-R1"]["opponent_overlays"] == [], "Cruise must not inherit Race overlays")
    require(modes["Sport-R1"]["opponent_overlays"] == [], "Sport must not inherit Race overlays")
    require(set(modes["Race-R1"]["opponent_overlays"]) == RACE_AXES, "Race overlays changed")
    require(len(modes["Race-R1"]["opponent_overlays"]) == len(RACE_AXES), "duplicate Race overlay")
    require(race["hardware"]["sha256"] == FROZEN_ID, "Race hardware mismatch")
    require(race["hardware"]["disposition"] == "KEEP_V07", "Race disposition changed")
    require(set(race["opponent_overlays"].values()) == RACE_AXES, "Race protocol overlay mismatch")
    require("PASS_BOUNDED_POLICY_ELASTICITY_KEEP_V07" in conclusion, "missing accepted conclusion")
    require("Card changes authorized by the final gate: **0**" in conclusion, "final gate permission changed")
    require("not a cEDH game-win-rate claim" in conclusion, "win-rate caveat lost")
    return {
        "Cruise-R1": {"generic_layers": 1, "opponent_overlays": 0},
        "Sport-R1": {"generic_layers": 3, "opponent_overlays": 0},
        "Race-R1": {"generic_layers": 3, "opponent_overlays": 7},
    }


def check_blob(root: Path, relative: str, expected: str) -> str:
    require((root / relative).is_file(), f"missing pinned source: {relative}")
    sha = subprocess.run(["git", "hash-object", relative], cwd=root,
                         capture_output=True, text=True, check=True).stdout.strip()
    require(sha == expected, f"pinned blob mismatch for {relative}: {sha}")
    return sha


def opening_seven_probability(library_cards: int, lands: int, minimum: int, maximum: int) -> float:
    # Combinatorial, no mulligans, no color/tempo/replay assumptions.
    draws = 7
    return sum(comb(lands, k) * comb(library_cards - lands, draws - k)
               for k in range(minimum, maximum + 1)) / comb(library_cards, draws)


def audit(root: Path) -> dict:
    for path, expected in BLOBS.items():
        check_blob(root, path, expected)
    deck_relative = "experiments/manual-transmission/control/v0.7.decklist.txt"
    raw = (root / deck_relative).read_bytes()
    text = raw.decode("utf-8")
    declared = re.search(r"(?m)^# Declared frozen deck SHA-256: ([0-9a-f]{64})$", text)
    require(declared is not None and declared.group(1) == FROZEN_ID, "inherited declaration changed")
    structure = check_deck_structure(text)
    policies_root = root / "experiments/manual-transmission"
    elasticity = json.loads((policies_root / "protocols/same-hardware-elasticity-r1.json").read_text())
    race = json.loads((policies_root / "protocols/final-race-policy-r1.json").read_text())
    conclusion = (policies_root / "results/FINAL_CONCLUSION_2026_09_24.md").read_text()
    modes = check_policy(elasticity, race, conclusion)
    return {
        "status": "PASS_READ_ONLY_STRUCTURAL_PREFLIGHT",
        "scope": "source-pinned deck identity, accepted policy shape, descriptive opening seven only",
        "gameplay_executed": False,
        "optimization_win_rate_measured": False,
        "hardware_promotion_authorized": False,
        "policy_promotion_authorized": False,
        "frozen_deck_changed": False,
        "ai_profile_current_changed": False,
        "inherited_experiment_identifier_not_plaintext_sha256": FROZEN_ID,
        "exact_plaintext_sha256": hashlib.sha256(raw).hexdigest(),
        "exact_deck_git_blob": BLOBS[deck_relative],
        "pinned_git_blobs": BLOBS,
        "structure": structure,
        "policy_surfaces": modes,
        "opening_seven_without_mulligans": {
            "library_cards": 99,
            "lands_counting_disciple_as_creature": 32,
            "p_zero_or_one_land": round(opening_seven_probability(99, 32, 0, 1), 8),
            "p_two_through_four_lands": round(opening_seven_probability(99, 32, 2, 4), 8),
            "interpretation": "static combinatorial distribution only, not mulligan/gear/Commander game performance",
        },
        "next_gate": "prospective same-deck live-policy adapter capability plus original-game decision telemetry, not automatic card changes",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="write deterministic JSON")
    args = parser.parse_args()
    result = audit(ROOT)
    output = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
    print("IDENTITY_R1_PASS frozen_100=100 roles=3 policies=1/3/10 games=0 promotions=0")
    print("EXACT_PLAINTEXT_SHA256=" + result["exact_plaintext_sha256"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
