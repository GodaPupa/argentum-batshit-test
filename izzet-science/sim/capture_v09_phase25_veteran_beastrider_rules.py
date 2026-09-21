#!/usr/bin/env python3
"""Capture oracle rules for the frozen Phase-24 opponent identity.

The script prints one canonical JSON object. It does not modify repository files,
classify behavior, assign seeds, simulate games, or authorize opponent execution.
"""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import time
import urllib.request


ROOT = Path(__file__).resolve().parents[2]
IDENTITY = ROOT / "izzet-science/opponents/veteran-beastrider-commander-clash-2025.identity.json"
SCRYFALL_COLLECTION = "https://api.scryfall.com/cards/collection"
USER_AGENT = "IzzetScience-Phase25/1.0"


def fetch_cards(oracle_ids: list[str]) -> list[dict]:
    cards = []
    for offset in range(0, len(oracle_ids), 75):
        identifiers = [{"oracle_id": value} for value in oracle_ids[offset:offset + 75]]
        body = json.dumps({"identifiers": identifiers}).encode("utf-8")
        request = urllib.request.Request(
            SCRYFALL_COLLECTION,
            data=body,
            headers={
                "User-Agent": USER_AGENT,
                "Accept": "application/json",
                "Content-Type": "application/json",
            },
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            if response.status != 200:
                raise ValueError(f"Scryfall returned HTTP {response.status}")
            result = json.load(response)
        if result.get("not_found"):
            raise ValueError(f"Scryfall identifiers not found: {result['not_found']}")
        cards.extend(result["data"])
        if offset + 75 < len(oracle_ids):
            time.sleep(0.1)
    return cards


def joined_face_field(card: dict, field: str) -> str | None:
    value = card.get(field)
    if value is not None:
        return value
    values = [face.get(field) for face in card.get("card_faces", [])]
    values = [value for value in values if value is not None]
    return " // ".join(values) if values else None


def main() -> None:
    identity_bytes = IDENTITY.read_bytes()
    identity = json.loads(identity_bytes)
    expected = {card["oracle_id"]: card["name"] for card in identity["cards"]}
    if len(expected) != 85:
        raise ValueError("frozen identity no longer has 85 unique oracle identities")

    fetched = fetch_cards(sorted(expected))
    if {card["oracle_id"] for card in fetched} != set(expected):
        raise ValueError("Scryfall response differs from frozen oracle identities")

    rules = []
    for card in sorted(fetched, key=lambda value: value["name"]):
        if card["name"] != expected[card["oracle_id"]]:
            raise ValueError(f"oracle name changed: {card['oracle_id']}")
        rules.append({
            "name": card["name"],
            "oracle_id": card["oracle_id"],
            "mana_cost": joined_face_field(card, "mana_cost"),
            "type_line": card["type_line"],
            "oracle_text": joined_face_field(card, "oracle_text") or "",
            "power": joined_face_field(card, "power"),
            "toughness": joined_face_field(card, "toughness"),
            "keywords": sorted(card.get("keywords", [])),
        })

    result = {
        "schema": "izzet-v09-phase25-opponent-oracle-rules-v1",
        "captured_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "opponent_identity": "veteran-beastrider-commander-clash-2025-v1",
        "identity_snapshot_sha256": hashlib.sha256(identity_bytes).hexdigest(),
        "rules_source": {
            "api": SCRYFALL_COLLECTION,
            "lookup_key": "oracle_id",
        },
        "cards": rules,
        "experimental_seeds_assigned": 0,
        "experimental_seeds_consumed": 0,
        "sampled_games": 0,
        "outcome_claims": 0,
    }
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
