#!/usr/bin/env python3
"""Materialize deterministic 60/15 postboard decks from frozen source lists."""

import argparse
import json
from collections import OrderedDict
from pathlib import Path


def parse_deck(path: Path):
    metadata = []
    sections = {"main": OrderedDict(), "sideboard": OrderedDict()}
    current = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line == "[main]":
            current = "main"
        elif line == "[sideboard]":
            current = "sideboard"
        elif current is None:
            metadata.append(raw)
        elif line:
            qty, card = line.split(" ", 1)
            sections[current][card] = sections[current].get(card, 0) + int(qty)
    return metadata, sections


def apply_map(source: Path, output: Path, swap: dict, label: str):
    metadata, sections = parse_deck(source)
    main, side = sections["main"], sections["sideboard"]
    assert sum(main.values()) == 60 and sum(side.values()) == 15, source
    assert sum(swap["in"].values()) == sum(swap["out"].values()), label
    for card, qty in swap["out"].items():
        assert main.get(card, 0) >= qty, (label, "out", card, qty)
        main[card] -= qty
        side[card] = side.get(card, 0) + qty
    for card, qty in swap["in"].items():
        assert side.get(card, 0) >= qty, (label, "in", card, qty)
        side[card] -= qty
        main[card] = main.get(card, 0) + qty
    main = OrderedDict((card, qty) for card, qty in main.items() if qty)
    side = OrderedDict((card, qty) for card, qty in side.items() if qty)
    assert sum(main.values()) == 60 and sum(side.values()) == 15, label
    output.parent.mkdir(parents=True, exist_ok=True)
    name = next((x[5:] for x in metadata if x.startswith("Name=")), source.stem)
    lines = ["[metadata]", f"Name={name} POSTBOARD {label}", "Deck Type=constructed", "[main]"]
    lines += [f"{qty} {card}" for card, qty in main.items()]
    lines += ["[sideboard]"] + [f"{qty} {card}" for card, qty in side.items()]
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).parent)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = json.loads((args.root / "postboard-map-v1.json").read_text(encoding="utf-8"))
    control = args.root / "control/fv-temur-chrysalis-e-final-forge-75.dck"
    for matchup, row in data["matchups"].items():
        apply_map(control, args.output / f"face-value-vs-{matchup}.dck", row["face_value"], f"vs {matchup}")
        opponent = args.root / "opponents" / row["opponent_file"]
        apply_map(opponent, args.output / f"opponent-{matchup}.dck", row["opponent"], f"{matchup} vs Face Value")


if __name__ == "__main__":
    main()
