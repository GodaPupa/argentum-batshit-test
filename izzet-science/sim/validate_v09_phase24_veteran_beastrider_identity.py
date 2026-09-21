#!/usr/bin/env python3
"""Validate the frozen Phase-24 real PDH opponent identity."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
CONTROL = ROOT / "izzet-science/v0.7-control.md"
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
LIST = ROOT / "izzet-science/opponents/veteran-beastrider-commander-clash-2025.txt"
SNAPSHOT = ROOT / "izzet-science/opponents/veteran-beastrider-commander-clash-2025.identity.json"
SOURCE_URL = (
    "https://topdeck.gg/deck/cpdh-commander-clash-2025/"
    "RF01KBbpm5h2JMkwqdzbDuLWKo12"
)
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
SHA256 = re.compile(r"[0-9a-f]{64}")


def parse_list() -> tuple[Counter, Counter]:
    section = None
    zones = {"Commander": Counter(), "Mainboard": Counter()}
    for raw in LIST.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line in zones:
            section = line
            continue
        if section is None:
            raise ValueError("card appears before a list section")
        count_text, name = line.split(" ", 1)
        count = int(count_text)
        if count < 1 or not name:
            raise ValueError("invalid frozen list line")
        zones[section][name] += count
    return zones["Commander"], zones["Mainboard"]


def validate_snapshot(value: dict) -> None:
    if value["schema"] != "izzet-v09-phase24-opponent-identity-v1":
        raise ValueError("unexpected identity schema")
    captured = datetime.fromisoformat(value["captured_at_utc"])
    if captured.utcoffset() is None or captured.year != 2026:
        raise ValueError("capture timestamp is not an aware 2026 timestamp")
    source = value["source"]
    expected_source = {
        "url": SOURCE_URL,
        "event": "CPDH Commander Clash: 2025",
        "event_start_unix": 1765638000,
        "player": "Scarecrow1779",
        "standing": "1st",
        "record": "3-1-1",
        "format": "Pauper EDH",
    }
    for key, expected in expected_source.items():
        if source[key] != expected:
            raise ValueError(f"source {key} changed")
    if not SHA256.fullmatch(source["page_sha256"]):
        raise ValueError("source page digest is malformed")

    identity = value["identity"]
    if identity != {
        "identity_id": "veteran-beastrider-commander-clash-2025-v1",
        "commander": "Veteran Beastrider",
        "commander_count": 1,
        "mainboard_count": 99,
        "colors": ["G", "W"],
        "representative_matchup": True,
    }:
        raise ValueError("frozen opponent identity changed")

    cards = value["cards"]
    if not isinstance(cards, list) or not cards:
        raise ValueError("cards must be a nonempty list")
    if [card["name"] for card in cards] != sorted(card["name"] for card in cards):
        raise ValueError("card records must remain name-sorted")
    if len({card["name"] for card in cards}) != len(cards):
        raise ValueError("duplicate card name record")
    if len({card["oracle_id"] for card in cards}) != len(cards):
        raise ValueError("duplicate oracle identity")

    commander_list, mainboard_list = parse_list()
    commander_snapshot = Counter()
    mainboard_snapshot = Counter()
    commander_colors = set(identity["colors"])
    for card in cards:
        if not UUID.fullmatch(card["oracle_id"]):
            raise ValueError(f"malformed oracle identity: {card['name']}")
        if not set(card["color_identity"]).issubset(commander_colors):
            raise ValueError(f"color identity violation: {card['name']}")
        if type(card["count"]) is not int or card["count"] < 1:
            raise ValueError(f"invalid card count: {card['name']}")
        if card["zone"] == "commander":
            commander_snapshot[card["name"]] += card["count"]
            if card["rarity_of_returned_print"] != "uncommon":
                raise ValueError("commander returned print is not uncommon")
            if "Creature" not in card["type_line"]:
                raise ValueError("commander returned object is not a creature")
            if card["paupercommander_legality"] != "not_legal":
                raise ValueError("commander must remain excluded from the 99 legality field")
        elif card["zone"] == "mainboard":
            mainboard_snapshot[card["name"]] += card["count"]
            if card["paupercommander_legality"] != "legal":
                raise ValueError(f"mainboard legality failed: {card['name']}")
        else:
            raise ValueError(f"unknown zone: {card['zone']}")
    if commander_snapshot != commander_list or mainboard_snapshot != mainboard_list:
        raise ValueError("snapshot card identity differs from frozen text list")
    if sum(commander_snapshot.values()) != 1 or sum(mainboard_snapshot.values()) != 99:
        raise ValueError("identity is not one commander plus 99 cards")
    duplicates = {name for name, count in mainboard_snapshot.items() if count > 1}
    if duplicates != {"Forest", "Plains"}:
        raise ValueError("nonbasic singleton rule failed")

    if value["legality_source"] != {
        "api": "https://api.scryfall.com/cards/collection",
        "mainboard_legality_field": "legalities.paupercommander",
    }:
        raise ValueError("legality source changed")
    for key in ("experimental_seeds_assigned", "experimental_seeds_consumed",
                "sampled_games", "outcome_claims"):
        if value[key] != 0:
            raise ValueError(f"identity freeze contaminated by {key}")


def compare_replay(frozen: dict, replay_path: Path) -> None:
    replay = json.loads(replay_path.read_text(encoding="utf-8"))
    validate_snapshot(replay)
    replay["captured_at_utc"] = frozen["captured_at_utc"]
    if replay != frozen:
        raise ValueError("live source/Scryfall replay differs from frozen identity")


def expect_rejection(value: dict) -> None:
    try:
        validate_snapshot(value)
    except (KeyError, TypeError, ValueError):
        return
    raise AssertionError("accepted contaminated Phase-24 identity")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--online-replay", type=Path)
    args = parser.parse_args()
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest() != CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    frozen = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
    validate_snapshot(frozen)
    if args.online_replay:
        compare_replay(frozen, args.online_replay)

    malformed = []
    for mutate in (
        lambda x: x["source"].update(standing="2nd"),
        lambda x: x["identity"].update(mainboard_count=98),
        lambda x: x["identity"].update(representative_matchup=False),
        lambda x: x["cards"][0].update(oracle_id="bad"),
        lambda x: x["cards"][0].update(color_identity=["U"]),
        lambda x: x["cards"][0].update(paupercommander_legality="not_legal"),
        lambda x: x["cards"][0].update(count=2),
        lambda x: x.update(experimental_seeds_consumed=1),
    ):
        value = json.loads(json.dumps(frozen))
        mutate(value)
        malformed.append(value)
    for value in malformed:
        expect_rejection(value)

    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-24-real-opponent-identity")
    print("opponent_identity=veteran-beastrider-commander-clash-2025-v1")
    print("source_event=CPDH_Commander_Clash_2025")
    print("source_standing=1st")
    print("commander_count=1")
    print("mainboard_count=99")
    print(f"unique_card_names={len(frozen['cards'])}")
    print("current_mainboard_pdh_legal=99/99")
    print(f"malformed_identities_rejected={len(malformed)}")
    print(f"online_replay_verified={int(args.online_replay is not None)}")
    print("sampled_games=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE24_IDENTITY_VALIDATED")


if __name__ == "__main__":
    main()
