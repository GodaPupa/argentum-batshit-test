#!/usr/bin/env python3
"""Capture the Phase-24 TopDeck identity and current Scryfall legality data.

The script prints one canonical JSON object. It does not modify repository files,
assign seeds, simulate games, or infer missing cards.
"""
from __future__ import annotations

from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import time
import urllib.request


ROOT = Path(__file__).resolve().parents[2]
LIST = ROOT / "izzet-science/opponents/veteran-beastrider-commander-clash-2025.txt"
SOURCE_URL = (
    "https://topdeck.gg/deck/cpdh-commander-clash-2025/"
    "RF01KBbpm5h2JMkwqdzbDuLWKo12"
)
SCRYFALL_COLLECTION = "https://api.scryfall.com/cards/collection"
USER_AGENT = "IzzetScience-Phase24/1.0"


def fetch(url: str, *, body: bytes | None = None) -> bytes:
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json,text/html"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise ValueError(f"source returned HTTP {response.status}")
        return response.read()


def embedded_object(page: str, key: str) -> dict:
    marker = f'"{key}":'
    start = page.find(marker)
    if start < 0:
        raise ValueError(f"TopDeck page has no {key} payload")
    start += len(marker)
    if page[start] != "{":
        raise ValueError(f"TopDeck {key} payload is not an object")
    depth = 0
    quoted = False
    escaped = False
    for index in range(start, len(page)):
        char = page[index]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return json.loads(page[start:index + 1])
    raise ValueError(f"unterminated TopDeck {key} payload")


def parse_list() -> tuple[Counter, Counter]:
    section = None
    commander: Counter[str] = Counter()
    mainboard: Counter[str] = Counter()
    for raw in LIST.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line in {"Commander", "Mainboard"}:
            section = line
            continue
        count_text, name = line.split(" ", 1)
        count = int(count_text)
        if count < 1 or section is None:
            raise ValueError("invalid frozen list line")
        (commander if section == "Commander" else mainboard)[name] += count
    return commander, mainboard


def source_cards(deck: dict, zone: str) -> Counter:
    cards = deck["deckObj"][zone]
    return Counter({card["name"]: card["count"] for card in cards})


def scryfall_cards(oracle_ids: list[str]) -> list[dict]:
    results = []
    for offset in range(0, len(oracle_ids), 75):
        identifiers = [{"oracle_id": value} for value in oracle_ids[offset:offset + 75]]
        body = json.dumps({"identifiers": identifiers}).encode("utf-8")
        response = json.loads(fetch(SCRYFALL_COLLECTION, body=body))
        if response.get("not_found"):
            raise ValueError(f"Scryfall identifiers not found: {response['not_found']}")
        results.extend(response["data"])
        if offset + 75 < len(oracle_ids):
            time.sleep(0.1)
    return results


def main() -> None:
    page_bytes = fetch(SOURCE_URL)
    page = page_bytes.decode("utf-8")
    player = embedded_object(page, "PLAYER")
    tournament = embedded_object(page, "TOURNAMENT")
    deck = embedded_object(page, "DECK")
    frozen_commander, frozen_mainboard = parse_list()
    source_commander = source_cards(deck, "Commanders")
    source_mainboard = source_cards(deck, "Mainboard")

    if player["standing"] != "1st" or player["record"] != "3-1-1":
        raise ValueError("TopDeck result identity changed")
    if tournament["name"] != "CPDH Commander Clash: 2025":
        raise ValueError("TopDeck tournament identity changed")
    if deck["format"] != "Pauper EDH":
        raise ValueError("TopDeck format identity changed")
    if frozen_commander != source_commander or frozen_mainboard != source_mainboard:
        raise ValueError("frozen list differs from TopDeck source")
    if sum(frozen_commander.values()) != 1 or sum(frozen_mainboard.values()) != 99:
        raise ValueError("opponent identity is not one commander plus 99 cards")
    duplicate_names = {name for name, count in frozen_mainboard.items() if count > 1}
    if duplicate_names != {"Forest", "Plains"}:
        raise ValueError("nonbasic singleton rule failed")

    source_entries = deck["deckObj"]["Commanders"] + deck["deckObj"]["Mainboard"]
    by_oracle = {entry["id"]: entry for entry in source_entries}
    if len(by_oracle) != len(source_entries):
        raise ValueError("duplicate TopDeck oracle identity")
    scryfall = scryfall_cards(sorted(by_oracle))
    by_name = {card["name"]: card for card in scryfall}
    all_names = set(frozen_commander) | set(frozen_mainboard)
    if set(by_name) != all_names:
        raise ValueError("Scryfall name set differs from frozen identity")

    commander_name = next(iter(frozen_commander))
    commander = by_name[commander_name]
    if commander["rarity"] != "uncommon" or "Creature" not in commander["type_line"]:
        raise ValueError("commander is not an uncommon creature")
    commander_colors = set(commander["color_identity"])
    if commander_colors != {"G", "W"}:
        raise ValueError("commander color identity changed")

    cards = []
    for name in sorted(all_names):
        card = by_name[name]
        zone = "commander" if name == commander_name else "mainboard"
        if zone == "mainboard" and card["legalities"]["paupercommander"] != "legal":
            raise ValueError(f"mainboard card is not PDH legal: {name}")
        if not set(card["color_identity"]).issubset(commander_colors):
            raise ValueError(f"card exceeds commander color identity: {name}")
        cards.append({
            "name": name,
            "oracle_id": card["oracle_id"],
            "zone": zone,
            "count": (frozen_commander | frozen_mainboard)[name],
            "color_identity": card["color_identity"],
            "rarity_of_returned_print": card["rarity"],
            "type_line": card["type_line"],
            "paupercommander_legality": card["legalities"]["paupercommander"],
        })

    result = {
        "schema": "izzet-v09-phase24-opponent-identity-v1",
        "captured_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "source": {
            "url": SOURCE_URL,
            "page_sha256": hashlib.sha256(page_bytes).hexdigest(),
            "event": tournament["name"],
            "event_start_unix": tournament["startDate"],
            "player": "Scarecrow1779",
            "standing": player["standing"],
            "record": player["record"],
            "format": deck["format"],
        },
        "identity": {
            "identity_id": "veteran-beastrider-commander-clash-2025-v1",
            "commander": commander_name,
            "commander_count": 1,
            "mainboard_count": 99,
            "colors": sorted(commander_colors),
            "representative_matchup": True,
        },
        "legality_source": {
            "api": SCRYFALL_COLLECTION,
            "mainboard_legality_field": "legalities.paupercommander",
        },
        "cards": cards,
        "experimental_seeds_assigned": 0,
        "experimental_seeds_consumed": 0,
        "sampled_games": 0,
        "outcome_claims": 0,
    }
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
