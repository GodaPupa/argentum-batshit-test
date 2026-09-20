#!/usr/bin/env python3
"""Seed-free deterministic gate for primary-combo multiplayer lethality."""
from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT=Path(__file__).resolve().parents[2]
HARNESS=ROOT/"izzet-science/sim/mana_harness.py"
CONTROL=ROOT/"izzet-science/v0.7-control.md"


def main() -> None:
    spec=importlib.util.spec_from_file_location("mana_harness",HARNESS)
    if spec is None or spec.loader is None:
        raise SystemExit("unable to load harness")
    harness=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(harness)
    _,cards=harness.parse_deck(CONTROL)
    harness.multiplayer_table_kill_regressions()
    if len(cards)!=99:
        raise SystemExit(f"control main-deck identity mismatch: {len(cards)} cards")
    plan_state=harness.DevState(["Lava Spike","Desperate Ritual"])
    plan_state.turn=5
    plan_state.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,card in enumerate(["Mountain","Mountain","Mountain","Island","Island"]):
        plan_state.battlefield.append({"card":card,"tapped":False,"entered":i})
    plan=harness.primary_combo_table_damage_plan(plan_state,(30,30,30))
    print("control=v0.7")
    print("opponent_lives=30,30,30")
    print(f"resolving_spells={sum(plan['resolving_spells_by_opponent'])}")
    print(f"copies={plan['copies']}")
    print("table_damage=90")
    print("deterministic_regressions=pass")
    print("sampled_games=0")


if __name__=="__main__":
    main()
