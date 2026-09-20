#!/usr/bin/env python3
"""Materialize map v2 by applying its frozen capability delta to map v1."""

import argparse
import json
from pathlib import Path

from materialize_postboard_decks import apply_map


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).parent)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    base = json.loads((args.root / "postboard-map-v1.json").read_text(encoding="utf-8"))
    v2 = json.loads((args.root / "postboard-map-v2.json").read_text(encoding="utf-8"))
    assert v2["base_map_sha256"] == "2a3570db4a4e739044db2078e9250d6344e75c78db0ecfa1f383e60710508627"
    delta = v2["delta"]
    assert delta["matchup"] == "elves" and delta["side"] == "opponent" and not delta["out_changes"]
    swap = base["matchups"]["elves"]["opponent"]
    for card, qty in delta["remove_in"].items():
        assert swap["in"].pop(card) == qty
    swap["in"].update(delta["add_in"])
    swap["rationale"] = delta["rationale"]
    control = args.root / "control/fv-temur-chrysalis-e-final-forge-75.dck"
    for matchup, row in base["matchups"].items():
        apply_map(control, args.output / f"face-value-vs-{matchup}.dck", row["face_value"], f"vs {matchup} map-v2")
        opponent = args.root / "opponents" / row["opponent_file"]
        apply_map(opponent, args.output / f"opponent-{matchup}.dck", row["opponent"], f"{matchup} vs Face Value map-v2")


if __name__ == "__main__":
    main()
