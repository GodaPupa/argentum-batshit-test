#!/usr/bin/env python3
"""Deterministic, paired structural screen for Industrial Waste Gate 1.

This is deliberately a transparent deck model, not an Argentum rules-engine
game. Its purpose is to reject weak deck-shape hypotheses cheaply.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import re
import statistics
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path

from validate_inputs import parse_deck, validate_project


ROOT = Path(__file__).resolve().parents[1]
LANDS = {"Forest", "Swamp", "Conduit Pylons", "Haunted Fengraf", "Urza's Mine", "Urza's Power Plant", "Urza's Tower"}
TRON = ("Urza's Mine", "Urza's Power Plant", "Urza's Tower")
PERMANENTS = LANDS | {
    "Ashnod's Altar", "Candy Trail", "Giant's Boulder", "Golem Foundry",
    "Ichor Wellspring", "Myr Kinsmith", "Myr Retriever", "Pactdoll Terror",
    "Prophetic Prism", "Blood Fountain",
}
ARTIFACTS = {
    "Ashnod's Altar", "Candy Trail", "Giant's Boulder", "Golem Foundry",
    "Ichor Wellspring", "Myr Kinsmith", "Myr Retriever", "Pactdoll Terror",
    "Prophetic Prism", "Blood Fountain",
}
CREATURES = {"Myr Kinsmith", "Myr Retriever", "Pactdoll Terror"}
PAYOFFS = {"Pactdoll Terror", "Golem Foundry"}


@dataclass
class Outcome:
    mulligans: int
    final_hand_size: int
    tron_t3: bool
    tron_t4: bool
    tron_t5: bool
    green_failure: bool
    black_failure: bool
    combo_ready_turn: int | None
    lethal_turn: int | None
    retriever_pair: bool
    redundant_payoffs: int
    pactdoll_damage: int
    recursion_access: bool
    interaction_applied: bool
    recovered_after_interaction: bool
    fair_material: int


def seed_for(namespace: str, index: int) -> int:
    raw = hashlib.sha256(f"{namespace}:{index}".encode()).digest()[:8]
    seed = int.from_bytes(raw, "big") & ((1 << 63) - 1)
    return seed or 1


def expanded(main: Counter[str]) -> list[str]:
    return [card for card, count in sorted(main.items()) for _ in range(count)]


def hand_score(card: str, hand: list[str]) -> tuple[int, str]:
    missing = {piece for piece in TRON if piece not in hand}
    if card in missing:
        return (100, card)
    scores = {
        "Forest": 94, "Swamp": 92, "Conduit Pylons": 86, "Haunted Fengraf": 82,
        "Expedition Map": 90, "Crop Rotation": 88, "Malevolent Rumble": 84,
        "Myr Retriever": 78, "Ashnod's Altar": 72,
        "Pactdoll Terror": 68, "Golem Foundry": 64,
        "Candy Trail": 60, "Giant's Boulder": 58,
        "Ichor Wellspring": 56, "Prophetic Prism": 54,
        "Blood Fountain": 53, "Myr Kinsmith": 50,
        "Eviscerator's Insight": 45, "Fanatical Offering": 44,
        "Weather the Storm": 10,
    }
    return (scores.get(card, 30), card)


def keep(hand: list[str], size: int) -> bool:
    lands = sum(card in LANDS for card in hand)
    access = lands + ("Expedition Map" in hand) + ("Crop Rotation" in hand and "Forest" in hand)
    engine = any(card in hand for card in {"Malevolent Rumble", "Candy Trail", "Giant's Boulder", "Ichor Wellspring", "Prophetic Prism", "Myr Retriever"})
    if size <= 5:
        return access >= 2 and lands >= 1
    return 2 <= lands <= 5 and access >= 2 and engine


def london_open(deck: list[str], rng: random.Random) -> tuple[list[str], list[str], int]:
    for mulligans in range(4):
        library = deck[:]
        rng.shuffle(library)
        hand = library[:7]
        library = library[7:]
        size = 7 - mulligans
        if keep(hand, size) or mulligans == 3:
            hand.sort(key=lambda card: hand_score(card, hand), reverse=True)
            bottom = hand[size:]
            hand = hand[:size]
            library.extend(bottom)
            return hand, library, mulligans
    raise AssertionError("unreachable")


def tron_complete(lands: Counter[str]) -> bool:
    return all(lands[piece] > 0 for piece in TRON)


def mana_total(lands: Counter[str], spawn: int) -> int:
    complete = tron_complete(lands)
    total = spawn + lands["Forest"] + lands["Swamp"] + lands["Conduit Pylons"] + lands["Haunted Fengraf"]
    for piece in TRON:
        total += lands[piece] * ({"Urza's Mine": 2, "Urza's Power Plant": 2, "Urza's Tower": 3}[piece] if complete else 1)
    return total


def has_color(color: str, lands: Counter[str], battlefield: Counter[str], total: int) -> bool:
    natural = lands["Forest"] if color == "G" else lands["Swamp"]
    filters = lands["Conduit Pylons"] + battlefield["Giant's Boulder"] + battlefield["Prophetic Prism"]
    return natural > 0 or (filters > 0 and total >= 2)


def fetch_missing_tron(hand: list[str], library: list[str]) -> str | None:
    wanted = [piece for piece in TRON if piece not in hand]
    for piece in wanted + list(TRON):
        if piece in library:
            library.remove(piece)
            hand.append(piece)
            return piece
    return None


def choose_land(hand: list[str], lands: Counter[str]) -> str | None:
    for piece in TRON:
        if lands[piece] == 0 and piece in hand:
            return piece
    needs_green = any(card in hand for card in {"Crop Rotation", "Malevolent Rumble"})
    needs_black = any(card in hand for card in {"Eviscerator's Insight", "Fanatical Offering", "Pactdoll Terror", "Blood Fountain"})
    priorities = (["Forest"] if needs_green else []) + (["Swamp"] if needs_black else []) + ["Conduit Pylons"] + list(TRON) + ["Haunted Fengraf", "Forest", "Swamp"]
    for card in priorities:
        if card in hand:
            return card
    return None


def run_one(main: Counter[str], seed: int, forced_interaction: bool = False) -> Outcome:
    rng = random.Random(seed ^ (0x517A if forced_interaction else 0))
    hand, library, mulligans = london_open(expanded(main), rng)
    battlefield: Counter[str] = Counter()
    grave: Counter[str] = Counter()
    lands: Counter[str] = Counter()
    spawn = 0
    pact_damage = 0
    foundry_counters = 0
    combo_turn = None
    lethal_turn = None
    tron_turns: dict[int, bool] = {}
    removed = False
    recovered = False

    def artifact_enters(card: str) -> None:
        nonlocal pact_damage, foundry_counters
        if card == "Pactdoll Terror":
            pact_damage += battlefield["Pactdoll Terror"] + 1
        else:
            pact_damage += battlefield["Pactdoll Terror"]
        if card != "Golem Foundry":
            foundry_counters += battlefield["Golem Foundry"]

    for turn in range(1, 9):
        if turn > 1 or seed & 1:
            if library:
                hand.append(library.pop(0))

        spent = 0
        total_before = mana_total(lands, spawn)
        if battlefield["Expedition Map"] and total_before >= 2:
            battlefield["Expedition Map"] -= 1
            grave["Expedition Map"] += 1
            fetch_missing_tron(hand, library)
            spent += 2

        land = choose_land(hand, lands)
        if land:
            hand.remove(land)
            lands[land] += 1

        total = mana_total(lands, spawn)
        green = has_color("G", lands, battlefield, total)
        black = has_color("B", lands, battlefield, total)

        if "Crop Rotation" in hand and green and not tron_complete(lands) and total - spent >= 1:
            sacrifice = next((name for name in ["Haunted Fengraf", "Conduit Pylons", "Swamp", "Forest"] if lands[name]), None)
            if sacrifice is None:
                sacrifice = next((name for name in TRON if lands[name] > 1), None)
            missing = next((piece for piece in TRON if lands[piece] == 0 and piece in library), None)
            if sacrifice and missing:
                hand.remove("Crop Rotation")
                grave["Crop Rotation"] += 1
                lands[sacrifice] -= 1
                library.remove(missing)
                lands[missing] += 1
                spent += 1

        total = mana_total(lands, spawn)
        green = has_color("G", lands, battlefield, total)
        black = has_color("B", lands, battlefield, total)
        if "Malevolent Rumble" in hand and green and total - spent >= 2 and len(library) >= 4:
            hand.remove("Malevolent Rumble")
            grave["Malevolent Rumble"] += 1
            seen = [library.pop(0) for _ in range(4)]
            missing = [card for card in seen if card in TRON and lands[card] == 0]
            preferred = missing + [card for card in seen if card == "Myr Retriever"] + [card for card in seen if card == "Ashnod's Altar"] + [card for card in seen if card in PAYOFFS] + [card for card in seen if card in PERMANENTS]
            chosen = preferred[0] if preferred else None
            if chosen:
                seen.remove(chosen)
                hand.append(chosen)
            grave.update(seen)
            spawn += 1
            spent += 2

        # Cheap map deployment precedes engine permanents.
        if "Expedition Map" in hand and total - spent >= 1:
            hand.remove("Expedition Map")
            battlefield["Expedition Map"] += 1
            artifact_enters("Expedition Map")
            spent += 1

        priorities = [
            ("Ashnod's Altar", 3, None), ("Myr Retriever", 2, None),
            ("Pactdoll Terror", 4, "B"), ("Golem Foundry", 3, None),
            ("Myr Kinsmith", 4, None), ("Ichor Wellspring", 2, None),
            ("Prophetic Prism", 2, None), ("Blood Fountain", 1, "B"),
            ("Candy Trail", 1, None), ("Giant's Boulder", 1, None),
        ]
        progress = True
        while progress:
            progress = False
            total = mana_total(lands, spawn)
            for card, cost, color in priorities:
                if card not in hand or total - spent < cost:
                    continue
                if color and not has_color(color, lands, battlefield, total - spent):
                    continue
                hand.remove(card)
                battlefield[card] += 1
                artifact_enters(card)
                spent += cost
                if card in {"Ichor Wellspring", "Prophetic Prism"} and library:
                    hand.append(library.pop(0))
                if card == "Myr Kinsmith" and "Myr Retriever" in library:
                    library.remove("Myr Retriever")
                    hand.append("Myr Retriever")
                if card in {"Candy Trail", "Giant's Boulder"} and library:
                    # Conservative scry-2 proxy: keep the best of two on top.
                    seen = [library.pop(0) for _ in range(min(2, len(library)))]
                    if seen:
                        seen.sort(key=lambda value: hand_score(value, hand), reverse=True)
                        library = [seen[0]] + library + seen[1:]
                progress = True
                break

        # Blood Fountain is deliberately valued only when it actually recovers creatures.
        total = mana_total(lands, spawn)
        if battlefield["Blood Fountain"] and total - spent >= 4 and any(grave[c] for c in CREATURES):
            battlefield["Blood Fountain"] -= 1
            grave["Blood Fountain"] += 1
            returned = 0
            for card in ["Myr Retriever", "Pactdoll Terror", "Myr Kinsmith"]:
                while grave[card] and returned < 2:
                    grave[card] -= 1
                    hand.append(card)
                    returned += 1
            spent += 4

        total = mana_total(lands, spawn)
        if lands["Haunted Fengraf"] and total - spent >= 3 and any(grave[c] for c in CREATURES):
            choices = [card for card in CREATURES for _ in range(grave[card])]
            card = rng.choice(choices)
            lands["Haunted Fengraf"] -= 1
            grave["Haunted Fengraf"] += 1
            grave[card] -= 1
            hand.append(card)
            spent += 3

        if forced_interaction and turn == 5 and not removed:
            target = next((card for card in ["Ashnod's Altar", "Pactdoll Terror", "Golem Foundry", "Myr Retriever"] if battlefield[card]), None)
            if target:
                battlefield[target] -= 1
                grave[target] += 1
                removed = True

        retrievers = battlefield["Myr Retriever"] + grave["Myr Retriever"]
        payoff = battlefield["Pactdoll Terror"] or battlefield["Golem Foundry"]
        loop = battlefield["Ashnod's Altar"] and payoff and battlefield["Myr Retriever"] and retrievers >= 2
        if loop and combo_turn is None:
            combo_turn = turn
            lethal_turn = turn if battlefield["Pactdoll Terror"] else min(turn + 1, 9)
            if removed:
                recovered = True

        for marker in (3, 4, 5):
            if turn == marker:
                tron_turns[marker] = tron_complete(lands)

    visible_retrievers = battlefield["Myr Retriever"] + grave["Myr Retriever"] + hand.count("Myr Retriever")
    redundant = max(0, sum(hand.count(card) for card in PAYOFFS) - 1)
    green_failure = any(card in hand for card in {"Crop Rotation", "Malevolent Rumble"}) and not has_color("G", lands, battlefield, mana_total(lands, spawn))
    black_failure = any(card in hand for card in {"Eviscerator's Insight", "Fanatical Offering", "Pactdoll Terror", "Blood Fountain"}) and not has_color("B", lands, battlefield, mana_total(lands, spawn))
    fair_material = pact_damage + 3 * battlefield["Pactdoll Terror"] + 3 * battlefield["Myr Kinsmith"] + battlefield["Myr Retriever"] + 3 * (foundry_counters // 3)
    return Outcome(
        mulligans=mulligans,
        final_hand_size=7 - mulligans,
        tron_t3=tron_turns.get(3, False), tron_t4=tron_turns.get(4, False), tron_t5=tron_turns.get(5, False),
        green_failure=green_failure, black_failure=black_failure,
        combo_ready_turn=combo_turn, lethal_turn=lethal_turn,
        retriever_pair=visible_retrievers >= 2, redundant_payoffs=redundant,
        pactdoll_damage=pact_damage,
        recursion_access=bool(battlefield["Blood Fountain"] or grave["Blood Fountain"] or lands["Haunted Fengraf"] or grave["Haunted Fengraf"]),
        interaction_applied=removed,
        recovered_after_interaction=recovered, fair_material=fair_material,
    )


def pct(values: list[bool]) -> float:
    return round(100 * sum(values) / len(values), 2)


def summarize(normal: list[Outcome], disrupted: list[Outcome]) -> dict:
    def median_turn(field: str):
        values = [getattr(outcome, field) for outcome in normal if getattr(outcome, field) is not None]
        return statistics.median(values) if values else None

    exposed = [o for o in disrupted if o.interaction_applied]
    return {
        "games": len(normal),
        "mulligan_rate_pct": pct([o.mulligans > 0 for o in normal]),
        "mean_final_hand_size": round(statistics.mean(o.final_hand_size for o in normal), 3),
        "tron_t3_pct": pct([o.tron_t3 for o in normal]),
        "tron_t4_pct": pct([o.tron_t4 for o in normal]),
        "tron_t5_pct": pct([o.tron_t5 for o in normal]),
        "green_failure_pct": pct([o.green_failure for o in normal]),
        "black_failure_pct": pct([o.black_failure for o in normal]),
        "combo_ready_by_t8_pct": pct([o.combo_ready_turn is not None for o in normal]),
        "median_combo_ready_turn": median_turn("combo_ready_turn"),
        "lethal_by_t8_pct": pct([o.lethal_turn is not None and o.lethal_turn <= 8 for o in normal]),
        "median_lethal_turn": median_turn("lethal_turn"),
        "retriever_pair_pct": pct([o.retriever_pair for o in normal]),
        "mean_redundant_payoffs": round(statistics.mean(o.redundant_payoffs for o in normal), 3),
        "mean_noninfinite_pactdoll_damage": round(statistics.mean(o.pactdoll_damage for o in normal), 3),
        "recursion_access_pct": pct([o.recursion_access for o in normal]),
        "interaction_exposure_pct": pct([o.interaction_applied for o in disrupted]),
        "recovery_given_interaction_pct": pct([o.recovered_after_interaction for o in exposed]) if exposed else None,
        "mean_fair_material_proxy": round(statistics.mean(o.fair_material for o in normal), 3),
    }


def paired_delta(control: list[Outcome], challenger: list[Outcome]) -> dict:
    fields = {
        "mulligan_rate_pct": lambda o: float(o.mulligans > 0),
        "tron_t3_pct": lambda o: float(o.tron_t3),
        "tron_t4_pct": lambda o: float(o.tron_t4),
        "tron_t5_pct": lambda o: float(o.tron_t5),
        "green_failure_pct": lambda o: float(o.green_failure),
        "black_failure_pct": lambda o: float(o.black_failure),
        "combo_ready_by_t8_pct": lambda o: float(o.combo_ready_turn is not None),
        "lethal_by_t8_pct": lambda o: float(o.lethal_turn is not None and o.lethal_turn <= 8),
        "retriever_pair_pct": lambda o: float(o.retriever_pair),
        "mean_noninfinite_pactdoll_damage": lambda o: float(o.pactdoll_damage),
        "mean_fair_material_proxy": lambda o: float(o.fair_material),
    }
    result = {}
    for name, getter in fields.items():
        differences = [getter(test) - getter(base) for base, test in zip(control, challenger)]
        mean = statistics.mean(differences)
        se = statistics.stdev(differences) / math.sqrt(len(differences)) if len(differences) > 1 else 0.0
        scale = 100 if name.endswith("_pct") else 1
        result[name] = {
            "delta": round(mean * scale, 3),
            "ci95_low": round((mean - 1.96 * se) * scale, 3),
            "ci95_high": round((mean + 1.96 * se) * scale, 3),
        }
    return result


def repository_integer_seeds(repo: Path) -> set[int]:
    found: set[int] = set()
    pattern = re.compile(r"(?<![A-Za-z0-9])(?:seed\s*[=:]\s*)?(\d{5,19})(?![A-Za-z0-9])", re.I)
    for path in repo.rglob("*"):
        if not path.is_file() or ".git" in path.parts or ROOT in path.parents:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for match in pattern.finditer(text):
            value = int(match.group(1))
            if 0 < value < (1 << 63):
                found.add(value)
    return found


def validate_seed_registry(root: Path, requested_namespace: str, requested_count: int) -> None:
    registry = json.loads((root / "seed-registry.json").read_text(encoding="utf-8"))
    vectors = {}
    for entry in registry:
        namespace, count = entry["namespace"], entry["count"]
        vector = [seed_for(namespace, index) for index in range(1, count + 1)]
        digest = hashlib.sha256("\n".join(map(str, vector)).encode()).hexdigest()
        if digest != entry["vector_sha256"] or len(vector) != len(set(vector)) or 0 in vector:
            raise SystemExit(
                f"invalid registered vector: {namespace}; computed={digest}; registered={entry['vector_sha256']}"
            )
        vectors[namespace] = set(vector)
    if requested_namespace not in vectors:
        raise SystemExit(f"unregistered namespace: {requested_namespace}")
    expected_count = next(entry["count"] for entry in registry if entry["namespace"] == requested_namespace)
    if requested_count != expected_count:
        raise SystemExit(f"registered count for {requested_namespace} is {expected_count}")
    names = sorted(vectors)
    for index, left in enumerate(names):
        for right in names[index + 1:]:
            if vectors[left] & vectors[right]:
                raise SystemExit(f"registered seed overlap: {left} / {right}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--games", type=int, default=2000)
    parser.add_argument("--namespace", default="IW-G1-V1")
    parser.add_argument("--candidate", action="append")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.games < 1:
        raise SystemExit("--games must be positive")

    validate_project(ROOT)
    validate_seed_registry(ROOT, args.namespace, args.games)
    seeds = [seed_for(args.namespace, i) for i in range(1, args.games + 1)]
    if len(seeds) != len(set(seeds)) or 0 in seeds:
        raise SystemExit("diagnostic seed vector is invalid")
    collisions = set(seeds) & repository_integer_seeds(ROOT.parent)
    if collisions:
        raise SystemExit(f"diagnostic seed collision: {sorted(collisions)[:3]}")

    results = {
        "protocol": args.namespace,
        "evidence_class": "DIAGNOSTIC_MODEL_ONLY",
        "seed_count": len(seeds),
        "seed_vector_sha256": hashlib.sha256("\n".join(map(str, seeds)).encode()).hexdigest(),
        "candidates": {},
    }
    paths = [ROOT / "control/industrial-waste-v1.0-submitted.dck"] + sorted((ROOT / "challengers").glob("*.dck"))
    if args.candidate:
        requested = set(args.candidate) | {"v1.0-control"}
        paths = [path for path in paths if ("v1.0-control" if path.parent.name == "control" else path.stem) in requested]
    raw_outcomes = {}
    for path in paths:
        deck, _ = parse_deck(path)
        normal = [run_one(deck, seed, False) for seed in seeds]
        disrupted = [run_one(deck, seed, True) for seed in seeds]
        key = "v1.0-control" if path.parent.name == "control" else path.stem
        raw_outcomes[key] = normal
        results["candidates"][key] = summarize(normal, disrupted)
    control_outcomes = raw_outcomes["v1.0-control"]
    results["paired_deltas_vs_control"] = {
        key: paired_delta(control_outcomes, outcomes)
        for key, outcomes in raw_outcomes.items() if key != "v1.0-control"
    }

    rendered = json.dumps(results, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")


if __name__ == "__main__":
    main()
