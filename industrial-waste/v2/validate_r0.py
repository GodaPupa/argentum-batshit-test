#!/usr/bin/env python3
"""Validate the immutable v2 construction freeze. Never initializes a game."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

FAMILIES = ("COMPACT_LOOP", "RECURSIVE_EGGS", "LEAN_TRON_HYBRID")
BASICS = {"Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes"}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_deck(path: Path) -> dict[str, dict[str, int]]:
    sections: dict[str, dict[str, int]] = {"main": {}, "sideboard": {}}
    section = None
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("["):
            section = line[1:-1] if line in ("[main]", "[sideboard]") else None
        elif section and line:
            match = re.fullmatch(r"([1-9][0-9]*) (.+)", line)
            if not match or match[2] in sections[section]:
                raise ValueError(f"malformed or duplicate deck row: {path}: {line}")
            sections[section][match[2]] = int(match[1])
    if [sum(sections[s].values()) for s in ("main", "sideboard")] != [60, 15]:
        raise ValueError(f"not exactly 60/15: {path}")
    for name, count in (Counter(sections["main"]) + Counter(sections["sideboard"])).items():
        if count > 4 and name not in BASICS:
            raise ValueError(f"more than four copies across main and side: {name}")
    return sections


def validate(repo: Path) -> dict:
    base = repo / "industrial-waste/v2"
    frozen = json.loads((base / "r0-freeze.json").read_text())
    if frozen["candidate_families"] != list(FAMILIES):
        raise ValueError("family admission drift")
    expected_files = {Path(x["path"]).name for x in frozen["candidates"].values()}
    if {x.name for x in (base / "candidates").glob("*.dck")} != expected_files:
        raise ValueError("candidate set changed; exactly three frozen families required")
    for name, expected in frozen["file_sha256"].items():
        if digest(repo / name) != expected:
            raise ValueError(f"frozen identity drift: {name}")
    control = parse_deck(repo / frozen["historical_control"]["path"])
    if digest(repo / frozen["historical_control"]["path"]) != frozen["historical_control"]["sha256"]:
        raise ValueError("historical comparator changed")
    summary = {}
    cards = set(control["main"]) | set(control["sideboard"])
    for family in FAMILIES:
        entry = frozen["candidates"][family]
        path = repo / entry["path"]
        if digest(path) != entry["sha256"]:
            raise ValueError(f"candidate byte identity drift: {family}")
        deck = parse_deck(path)
        if deck != entry["card_counts"]:
            raise ValueError(f"candidate normalized manifest drift: {family}")
        changes = sum((Counter(control["main"]) - Counter(deck["main"])).values())
        if changes < 8 or changes != entry["maindeck_slots_replaced"]:
            raise ValueError(f"structural difference invalid: {family}")
        if deck["sideboard"] != control["sideboard"]:
            raise ValueError(f"R0 common sideboard changed: {family}")
        cards |= set(deck["main"]) | set(deck["sideboard"])
        summary[family] = {"main": 60, "sideboard": 15, "maindeck_slots_replaced": changes,
                           "sha256": entry["sha256"]}
    legality = json.loads((base / "legality-source-audit.json").read_text())
    if set(legality["cards"]) != cards:
        raise ValueError("legality identity coverage differs from frozen decks")
    for card, record in legality["cards"].items():
        path = repo / record["archive_path"]
        if digest(path) != record["archive_sha256"]:
            raise ValueError(f"legality source byte drift: {card}")
        data = json.loads(path.read_text())
        if data["name"] != card or data["legalities"]["pauper"] != "legal":
            raise ValueError(f"card identity or Pauper legality invalid: {card}")
        if data["released_at"] > frozen["legality_as_of"]:
            raise ValueError(f"future printing not effective at freeze: {card}")
    corpus = json.loads((base / "r1-ordering-corpus.json").read_text())
    if len(corpus["rows"]) != 64 or len(set(corpus["labels"])) != len(corpus["labels"]):
        raise ValueError("invalid ordering corpus size or labels")
    for number, row in enumerate(corpus["rows"], 1):
        if row["row"] != number or len(row["initial_orderings"]) != 4:
            raise ValueError("row or London ordering allocation drift")
        for mulligans, ordering in enumerate(row["initial_orderings"]):
            def key(index: int):
                text = f'{corpus["namespace"]}|row={number}|initial={mulligans}|copy={corpus["labels"][index]}'
                return hashlib.sha256(text.encode("utf-8")).digest(), index
            expected = sorted(range(len(corpus["labels"])), key=key)
            if ordering != expected:
                raise ValueError("ordering is not the predeclared matched corpus")
    protocol = json.loads((base / "protocol-v2-r1.json").read_text())
    if protocol["design"]["total_allocations"] != 512 or protocol["postboard_authorized"]:
        raise ValueError("R1 allocation or postboard boundary drift")
    return {"status": "R0_STATIC_FREEZE_VALID", "candidate_count": 3, "candidates": summary,
            "legality_identities": len(cards), "r1_corpus_rows": 64,
            "gameplay_initialized_by_this_validator": 0, "comparative_outcomes_exposed": 0,
            "r1_execution_ready": False,
            "remaining_gate": "Exact engine/card/pilot/telemetry qualification and guarded runtime binding receipt"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = validate(args.repo)
    rendered = json.dumps(result, sort_keys=True, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered)
    print(rendered, end="")
