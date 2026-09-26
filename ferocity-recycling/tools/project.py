#!/usr/bin/env python3
"""Deck integrity, immutable input freeze, and exact inventory arithmetic.

This file cannot run a Magic game and never produces a win-rate estimate.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
REPO = PROJECT.parent
BASICS = {"Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes",
          "Snow-Covered Plains", "Snow-Covered Island", "Snow-Covered Swamp",
          "Snow-Covered Mountain", "Snow-Covered Forest"}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path: Path):
    return json.loads(path.read_text())


def quantities(section):
    if isinstance(section, dict):
        return section
    return {row["name"]: row["count"] for row in section}


def deck_files():
    return sorted((PROJECT / "decks/candidates").glob("*.json")) + sorted(
        (PROJECT / "decks/comparators").glob("*.json"))


def validate_deck(path: Path):
    data = load(path)
    main = quantities(data["main"])
    side = quantities(data.get("sideboard", {}))
    for name, count in [*main.items(), *side.items()]:
        if not isinstance(count, int) or isinstance(count, bool) or count <= 0:
            raise ValueError(f"Invalid quantity: {path.name}: {name}")
        if name == "Deadly Dispute":
            raise ValueError(f"Banned card: {path.name}: {name}")
    if sum(main.values()) != 60:
        raise ValueError(f"Not sixty cards: {path.name}: {sum(main.values())}")
    if side and sum(side.values()) != 15:
        raise ValueError(f"Prototype sideboard must be exactly fifteen: {path.name}")
    for name in set(main) | set(side):
        if name not in BASICS and main.get(name, 0) + side.get(name, 0) > 4:
            raise ValueError(f"Copy limit: {path.name}: {name}")
    if path.parent.name == "candidates" and not 1 <= main.get("Ferocity of the Hunt", 0) <= 4:
        raise ValueError(f"Candidate lacks Ferocity: {path}")
    if path.parent.name == "comparators" and main.get("Ferocity of the Hunt", 0):
        raise ValueError(f"No-Ferocity comparator contains Ferocity: {path}")
    if isinstance(data["main"], list) and len(data["main"]) != len(main):
        raise ValueError(f"Duplicate main row: {path}")
    if isinstance(data.get("sideboard"), list) and len(data["sideboard"]) != len(side):
        raise ValueError(f"Duplicate sideboard row: {path}")
    canonical = "".join(f"{main[name]} {name}\n" for name in sorted(main))
    if hashlib.sha256(canonical.encode()).hexdigest() != data.get("deck_sha256"):
        raise ValueError(f"Canonical deck hash mismatch: {path}")
    text_path = path.with_suffix(".txt")
    imported = {}
    for line in text_path.read_text().splitlines():
        if not line.strip():
            continue
        count, name = line.split(" ", 1)
        imported[name] = imported.get(name, 0) + int(count)
    if imported != main:
        raise ValueError(f"Import list mismatch: {text_path}")
    return data, main


def validate():
    files = deck_files()
    candidates = [p for p in files if p.parent.name == "candidates"]
    comparators = [p for p in files if p.parent.name == "comparators"]
    if len(candidates) != 9 or len(comparators) != 9:
        raise ValueError(f"Initial allocation requires exactly nine + nine lists, found {len(candidates)} + {len(comparators)}")
    ids = []
    families = {}
    manifest_rows = {}
    for path in files:
        data, _ = validate_deck(path)
        ids.append(data["id"])
        if data["family"] not in {"A", "B", "C"}:
            raise ValueError(f"Unplanned family: {data['family']}")
        manifest_rows[data["id"]] = {key: data[key] for key in ("id", "family", "version", "deck_sha256")}
        key = (data["family"], path.parent.name)
        families[key] = families.get(key, 0) + 1
    if len(set(ids)) != len(ids):
        raise ValueError("Duplicate deck identity")
    if len(families) != 6 or any(n != 3 for n in families.values()):
        raise ValueError(f"Family budget mismatch: {families}")
    declared = load(PROJECT / "decks/DECK_MANIFEST.json")["all_decks"]
    if len(declared) != len(manifest_rows) or {row["id"]: row for row in declared} != manifest_rows:
        raise ValueError("Deck identity manifest drift")
    return files


def frozen_paths(files):
    paths = files + [p.with_suffix(".txt") for p in files] + [
        PROJECT / "protocols/RESEARCH_PROTOCOL.md", Path(__file__).resolve(),
        PROJECT / "decks/DECK_MANIFEST.json"]
    for name in ["DECK_DEVELOPMENT.md", "decks/card_roles.json"]:
        p = PROJECT / name
        if not p.is_file():
            raise ValueError(f"Missing freeze input: {p}")
        paths.append(p)
    return sorted(paths)


def freeze():
    files = validate()
    manifest = {
        "schema": "ferocity-recycling-initial-search-v1",
        "source_base": "d0c78bb4cca79b7402ba65bd62b5cb621230a054",
        "scope": "INITIAL_DEVELOPMENT_INPUTS_NOT_GAMEPLAY_ADMISSION",
        "randomized_outcomes_before_freeze": 0,
        "files_sha256": {str(p.relative_to(REPO)): sha(p) for p in frozen_paths(files)},
        "maximum_initial_ferocity_configurations": 9,
        "maximum_initial_no_ferocity_configurations": 9,
        "development_games_per_list": 40,
        "initial_development_game_ceiling": 720,
        "maximum_advancing_ferocity_configurations": 2,
        "refinement_rounds": 1,
    }
    target = PROJECT / "protocols/initial-search-manifest.json"
    with target.open("x") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
        f.write("\n")
    return manifest


def verify_freeze():
    manifest = load(PROJECT / "protocols/initial-search-manifest.json")
    for path, digest in manifest["files_sha256"].items():
        if sha(REPO / path) != digest:
            raise ValueError(f"Frozen input drift: {path}")
    return manifest


def choose(n, k):
    return math.comb(n, k) if 0 <= k <= n else 0


def hg(n, k, draws, lo=1, hi=None):
    hi = min(k, draws) if hi is None else min(hi, k, draws)
    return sum(choose(k, x) * choose(n-k, draws-x) for x in range(lo, hi+1)) / choose(n, draws)


def role_tags(value):
    return set(value if isinstance(value, list) else value.get("tags", value.get("roles", [])))


def screen():
    manifest = verify_freeze()
    roles_raw = load(PROJECT / "decks/card_roles.json")
    roles = roles_raw.get("cards", roles_raw)
    rows = []
    for path in validate():
        data, main = validate_deck(path)
        missing = set(main) - set(roles)
        if missing:
            raise ValueError(f"No audited role metadata for {sorted(missing)}")
        counts = {}
        for name, copies in main.items():
            for tag in role_tags(roles[name]):
                counts[tag] = counts.get(tag, 0) + copies
        f = main.get("Ferocity of the Hunt", 0)
        creatures = counts.get("creature", 0)
        sweepers = counts.get("sweeper", 0)
        if {"creature", "sweeper"} & role_tags(roles.get("Ferocity of the Hunt", [])):
            raise ValueError("Ferocity must be disjoint from creature/sweeper inventory sets")
        n = 7
        metrics = {
            "opening_seven_2_to_4_lands": hg(60, counts.get("land", 0), n, 2, 4),
            "opening_seven_zero_creatures": choose(60-creatures, n) / choose(60, n),
            "opening_seven_ferocity_at_least_one": hg(60, f, n),
            "opening_seven_ferocity_at_least_two": hg(60, f, n, 2),
            "opening_seven_ferocity_present_no_creature": (choose(60-creatures, n)-choose(60-creatures-f, n)) / choose(60, n),
            "opening_seven_ferocity_and_sweeper": 1-(choose(60-f,n)+choose(60-sweepers,n)-choose(60-f-sweepers,n))/choose(60,n),
        }
        for color in ["black", "red", "green", "white", "blue"]:
            tag = color + "_source"
            if counts.get(tag):
                metrics["opening_seven_at_least_one_" + tag] = hg(60, counts[tag], n)
        rows.append({"id": data["id"], "family": data["family"],
                     "deck_sha256": data["deck_sha256"], "deck_file_sha256": sha(path),
                     "role_inventory": counts, "probabilities": metrics})
    return {
        "schema": "ferocity-recycling-exact-inventory-v1",
        "method": "Exact hypergeometric arithmetic over uniformly shuffled sixty-card lists; no mulligans",
        "limitations": ["Inventory only; not turn-specific castability", "No sequencing, opponent, mulligan, or policy simulation", "Ferocity plus sweeper presence is not an executable line or a game win", "All decks remain unranked by performance"],
        "freeze_sha256": sha(PROJECT / "protocols/initial-search-manifest.json"),
        "randomized_seeds": 0, "games": 0, "outcomes": 0, "rows": rows,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["validate", "freeze", "verify", "screen"])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = {"validate": lambda: {"valid_lists": len(validate())}, "freeze": freeze,
              "verify": verify_freeze, "screen": screen}[args.command]()
    text = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        with args.output.open("x") as f:
            f.write(text)
    else:
        print(text, end="")


if __name__ == "__main__":
    main()
