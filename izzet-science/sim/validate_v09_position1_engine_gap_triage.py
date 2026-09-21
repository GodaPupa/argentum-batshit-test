#!/usr/bin/env python3
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
INV=json.loads((ROOT/"position1-engine-gap-inventory.json").read_text())
TRI=json.loads((ROOT/"position1-engine-gap-triage.json").read_text())
ALLOWED={"registry_only","reusable_mechanic","new_engine_mechanic","special_modal_cost"}

def main():
    expected=set(INV["izzet"])|set(INV["veteran_beastrider"])
    cards=TRI["cards"]
    assert set(cards)==expected
    assert len(cards)==55
    for name,row in cards.items():
        assert row["classification"] in ALLOWED
        assert row["deck"] in {"izzet","veteran"}
        assert isinstance(row["dependencies"],list) and row["dependencies"]
        assert all(isinstance(x,str) and x for x in row["dependencies"])
    assert sum(row["deck"]=="izzet" for row in cards.values())==22
    assert sum(row["deck"]=="veteran" for row in cards.values())==33
    assert set(TRI["pdh_blockers"])=={
        "commander_zone_initialization","commander_tax_and_recast",
        "commander_damage_16","starting_life_30","phase29_event_ledger_extraction"}
    assert TRI["official_execution_authorized"] is False
    assert TRI["official_seeds_consumed"]==0
    assert TRI["games_initialized"]==0
    assert TRI["outcome_exposure"]==0
    print("V09_POSITION1_ENGINE_GAP_TRIAGE_VALIDATION_PASS")

if __name__=="__main__":
    main()
