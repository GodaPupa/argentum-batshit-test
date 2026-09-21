#!/usr/bin/env python3
"""Validate the seed-free Phase-25 Veteran Beastrider action-surface map."""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
CONTROL = ROOT / "izzet-science/v0.7-control.md"
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
IDENTITY = ROOT / "izzet-science/opponents/veteran-beastrider-commander-clash-2025.identity.json"
IDENTITY_SHA256 = "ecde45fcafc4f09ce7e69398ab0787979a6e0d84b4dba83f067a2153176dc0ff"
RULES = ROOT / "izzet-science/opponents/veteran-beastrider-commander-clash-2025.rules.json"
SURFACES = ROOT / "izzet-science/opponents/veteran-beastrider-commander-clash-2025.action-surfaces.json"
RULES_SHA256 = "481d255e817b34e76a12cd87fbe5acdfcac8c35eb20f1e233f2f43d1bae9c61c"
SURFACES_SHA256 = "bd33aa63ab246454fb5be34198605035f50b9016862d24575cd08bc0b4b7f219"
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

EXPECTED_SURFACES = {
    "aura_access": {
        "Heliod's Pilgrim", "Shrine Steward", "Totem-Guide Hartebeest"},
    "battlefield_mana_source": {
        "Arctic Treeline", "Avacyn's Pilgrim", "Blossoming Sands",
        "Bonder's Ornament", "Boreal Druid", "Command Tower",
        "Druid of the Cowl", "Elvish Aberration", "Elvish Mystic", "Forest",
        "Forge of Heroes", "Fyndhorn Elves", "Gene Pollinator", "Gold Myr",
        "Goobbue Gardener", "Heart Warden", "Ilysian Caryatid",
        "Jaspera Sentinel", "Leafkin Druid", "Llanowar Elves",
        "Llanowar Visionary", "Magnifying Glass", "Nightshade Dryad",
        "Opal Palace", "Ornithopter of Paradise", "Path of Ancestry", "Plains",
        "Poison Dart Frog", "Radiant Grove", "Rootrider Faun",
        "Selesnya Sanctuary", "Suburban Sanctuary", "The Fair Basilica",
        "The Hunter Maze", "Three Tree Rootweaver",
        "Ulvenwald Captive // Ulvenwald Abomination", "Whisperer of the Wilds",
        "Wose Pathfinder"},
    "commander_entry_scaling": {"Forge of Heroes", "Opal Palace"},
    "commander_power_scaling": {
        "Armadillo Cloak", "Colossal Dreadmask", "Eagles of the North",
        "Nyxborn Hydra", "Snake Umbra", "Veteran Beastrider",
        "Vines of Vastwood", "Wose Pathfinder"},
    "commander_trample": {
        "Armadillo Cloak", "Colossal Dreadmask", "Nyxborn Hydra",
        "Wose Pathfinder"},
    "commander_untap": {"Veteran Beastrider"},
    "damage_prevention": {
        "Guardian Naga // Banishing Coils", "Prismatic Strands",
        "Temporal Isolation"},
    "destruction_replacement": {"Snake Umbra"},
    "hidden_land_access": {
        "Alabaster Host Intercessor", "Balamb T-Rexaur", "Bushwhack",
        "Eagles of the North", "Elvish Aberration", "Generous Ent",
        "Orchard Strider", "Shardless Outlander", "Shepherding Spirits",
        "Slavering Branchsnapper", "Timberland Ancient"},
    "targeted_creature_control": {
        "Afterlife", "Alabaster Host Intercessor", "Bite Down", "Bushwhack",
        "Coordinated Maneuver", "Cosmic Hunger", "Crib Swap", "Destroy Evil",
        "Generous Gift", "Master's Rebuke", "Pinnacle Kill-Ship", "Ram Through",
        "Slash of Light", "Temporal Isolation", "Thraben Charm"},
    "targeted_noncreature_control": {
        "Coordinated Maneuver", "Destroy Evil", "Generous Gift",
        "Guardian Naga // Banishing Coils", "Shower of Arrows", "Thraben Charm"},
    "targeting_protection": {
        "Benevolent Blessing", "Cho-Manno's Blessing", "Sheltering Word",
        "Stave Off", "Vines of Vastwood"},
}
EXPECTED_DEFERRED = {
    "Beastrider Vanguard", "Brightwood Tracker", "Crusader of Odric",
    "Deepwood Denizen", "Owlbear", "Salt Road Packbeast", "Scion of the Wild",
    "Spirit Link",
}


def validate_rules(value: dict, identity: dict) -> None:
    if value["schema"] != "izzet-v09-phase25-opponent-oracle-rules-v1":
        raise ValueError("unexpected oracle rules schema")
    captured = datetime.fromisoformat(value["captured_at_utc"])
    if captured.utcoffset() is None or captured.year != 2026:
        raise ValueError("rules capture is not an aware 2026 timestamp")
    if value["opponent_identity"] != "veteran-beastrider-commander-clash-2025-v1":
        raise ValueError("opponent identity changed")
    if value["identity_snapshot_sha256"] != IDENTITY_SHA256:
        raise ValueError("rules snapshot is not bound to the accepted identity")
    if value["rules_source"] != {
        "api": "https://api.scryfall.com/cards/collection",
        "lookup_key": "oracle_id",
    }:
        raise ValueError("oracle rules source changed")

    identity_pairs = {(card["name"], card["oracle_id"]) for card in identity["cards"]}
    cards = value["cards"]
    if [card["name"] for card in cards] != sorted(card["name"] for card in cards):
        raise ValueError("oracle rule records are not name-sorted")
    if {(card["name"], card["oracle_id"]) for card in cards} != identity_pairs:
        raise ValueError("oracle rule identities differ from the accepted opponent")
    if len(cards) != 85:
        raise ValueError("oracle rule snapshot is incomplete")
    for card in cards:
        if not UUID.fullmatch(card["oracle_id"]):
            raise ValueError(f"malformed oracle identity: {card['name']}")
        if not isinstance(card["type_line"], str) or not card["type_line"]:
            raise ValueError(f"missing type line: {card['name']}")
        if not isinstance(card["oracle_text"], str) or not card["oracle_text"]:
            raise ValueError(f"missing oracle text: {card['name']}")
        if not isinstance(card["keywords"], list) or card["keywords"] != sorted(card["keywords"]):
            raise ValueError(f"invalid keywords: {card['name']}")
    for key in ("experimental_seeds_assigned", "experimental_seeds_consumed",
                "sampled_games", "outcome_claims"):
        if value[key] != 0:
            raise ValueError(f"oracle rules snapshot contaminated by {key}")


def validate_surfaces(value: dict, rules: dict) -> None:
    if value["schema"] != "izzet-v09-phase25-action-surface-map-v1":
        raise ValueError("unexpected action-surface schema")
    if value["opponent_identity"] != "veteran-beastrider-commander-clash-2025-v1":
        raise ValueError("action-surface opponent identity changed")
    if value["classification_basis"] != "exact current oracle text captured by frozen oracle identity":
        raise ValueError("classification basis changed")
    if value["format_rules"] != {
        "commander_damage_loss_threshold": 16,
        "source": "https://pdhhomebase.com/rules/",
    }:
        raise ValueError("commander-damage rule changed")

    definitions = value["surface_definitions"]
    if set(definitions) != set(EXPECTED_SURFACES):
        raise ValueError("surface definitions changed")
    for name, definition in definitions.items():
        if set(definition) != {"information_boundary", "meaning"}:
            raise ValueError(f"malformed surface definition: {name}")
        if not all(isinstance(item, str) and item for item in definition.values()):
            raise ValueError(f"empty surface definition: {name}")
    if definitions["battlefield_mana_source"]["information_boundary"] != "public_battlefield_only":
        raise ValueError("public mana boundary changed")
    for name in ("hidden_land_access", "targeted_creature_control",
                 "targeted_noncreature_control", "targeting_protection"):
        if "private" not in definitions[name]["information_boundary"]:
            raise ValueError(f"hidden information mislabeled public: {name}")

    actual = value["surfaces"]
    if set(actual) != set(EXPECTED_SURFACES):
        raise ValueError("action-surface names changed")
    for name, expected in EXPECTED_SURFACES.items():
        cards = actual[name]
        if cards != sorted(cards) or len(cards) != len(set(cards)):
            raise ValueError(f"surface cards are not a sorted set: {name}")
        if set(cards) != expected:
            raise ValueError(f"surface classification changed: {name}")

    rule_names = {card["name"] for card in rules["cards"]}
    mapped = set().union(*EXPECTED_SURFACES.values())
    deferred = set(value["deferred_unique_cards"])
    if value["deferred_unique_cards"] != sorted(value["deferred_unique_cards"]):
        raise ValueError("deferred cards are not sorted")
    if deferred != EXPECTED_DEFERRED or mapped & deferred or mapped | deferred != rule_names:
        raise ValueError("action-surface classification is not exhaustive and disjoint")
    if value["mapped_unique_cards"] != len(mapped) or value["total_unique_cards"] != 85:
        raise ValueError("classification counts changed")

    if value["execution_authorized"] is not False or value["pilot_authorized"] is not False:
        raise ValueError("action-surface map improperly authorizes execution")
    for key in ("experimental_seeds_assigned", "experimental_seeds_consumed",
                "sampled_games", "outcome_claims"):
        if value[key] != 0:
            raise ValueError(f"action-surface map contaminated by {key}")


def compare_replay(frozen: dict, replay_path: Path, identity: dict) -> None:
    replay = json.loads(replay_path.read_text(encoding="utf-8"))
    validate_rules(replay, identity)
    replay["captured_at_utc"] = frozen["captured_at_utc"]
    if replay != frozen:
        raise ValueError("live Scryfall replay differs from frozen oracle rules")


def expect_rejection(callback) -> None:
    try:
        callback()
    except (KeyError, TypeError, ValueError):
        return
    raise AssertionError("accepted contaminated Phase-25 artifact")


def clone(value: dict) -> dict:
    return json.loads(json.dumps(value))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--online-replay", type=Path)
    args = parser.parse_args()
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest() != CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    if hashlib.sha256(IDENTITY.read_bytes()).hexdigest() != IDENTITY_SHA256:
        raise SystemExit("accepted opponent identity hash mismatch")
    if hashlib.sha256(RULES.read_bytes()).hexdigest() != RULES_SHA256:
        raise SystemExit("frozen oracle rules hash mismatch")
    if hashlib.sha256(SURFACES.read_bytes()).hexdigest() != SURFACES_SHA256:
        raise SystemExit("frozen action-surface map hash mismatch")

    identity = json.loads(IDENTITY.read_text(encoding="utf-8"))
    rules = json.loads(RULES.read_text(encoding="utf-8"))
    surfaces = json.loads(SURFACES.read_text(encoding="utf-8"))
    validate_rules(rules, identity)
    validate_surfaces(surfaces, rules)
    if args.online_replay:
        compare_replay(rules, args.online_replay, identity)

    malformed = []
    value = clone(rules); value["cards"].pop(); malformed.append(lambda value=value: validate_rules(value, identity))
    value = clone(rules); value["cards"][0]["oracle_id"] = "bad"; malformed.append(lambda value=value: validate_rules(value, identity))
    value = clone(rules); value["cards"][0]["oracle_text"] = ""; malformed.append(lambda value=value: validate_rules(value, identity))
    value = clone(rules); value["sampled_games"] = 1; malformed.append(lambda value=value: validate_rules(value, identity))
    value = clone(surfaces); value["surfaces"]["commander_trample"].pop(); malformed.append(lambda value=value: validate_surfaces(value, rules))
    value = clone(surfaces); value["surfaces"]["aura_access"].append("Owlbear"); malformed.append(lambda value=value: validate_surfaces(value, rules))
    value = clone(surfaces); value["surface_definitions"]["hidden_land_access"]["information_boundary"] = "public"; malformed.append(lambda value=value: validate_surfaces(value, rules))
    value = clone(surfaces); value["format_rules"]["commander_damage_loss_threshold"] = 21; malformed.append(lambda value=value: validate_surfaces(value, rules))
    value = clone(surfaces); value["deferred_unique_cards"].pop(); malformed.append(lambda value=value: validate_surfaces(value, rules))
    value = clone(surfaces); value["mapped_unique_cards"] = 76; malformed.append(lambda value=value: validate_surfaces(value, rules))
    value = clone(surfaces); value["execution_authorized"] = True; malformed.append(lambda value=value: validate_surfaces(value, rules))
    value = clone(surfaces); value["experimental_seeds_consumed"] = 1; malformed.append(lambda value=value: validate_surfaces(value, rules))
    for callback in malformed:
        expect_rejection(callback)

    mapped = set().union(*EXPECTED_SURFACES.values())
    print(f"control_sha256={CONTROL_SHA256}")
    print(f"identity_sha256={IDENTITY_SHA256}")
    print("phase=commander-independent-readiness-25-real-opponent-action-surfaces")
    print("opponent_identity=veteran-beastrider-commander-clash-2025-v1")
    print("rules_source=scryfall_oracle_id")
    print(f"rules_cards={len(rules['cards'])}")
    print(f"surface_classes={len(EXPECTED_SURFACES)}")
    print(f"mapped_unique_cards={len(mapped)}")
    print(f"deferred_unique_cards={len(EXPECTED_DEFERRED)}")
    print(f"malformed_artifacts_rejected={len(malformed)}")
    print(f"online_replay_verified={int(args.online_replay is not None)}")
    print("commander_damage_loss_threshold=16")
    print("sampled_games=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("execution_authorized=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE25_ACTION_SURFACES_VALIDATED")


if __name__ == "__main__":
    main()
