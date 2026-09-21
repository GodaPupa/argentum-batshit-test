#!/usr/bin/env python3
"""Seed-free validation for the frozen position-1 engine-gap inventory."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
INV=ROOT/"position1-engine-gap-inventory.json"

EXPECTED_IZZET={
"Arcane Denial","Archaeomancer","Capsize","Echoing Truth","Everflowing Chalice",
"Fire // Ice","Frantic Search","Goblin Electromancer","Ideas Unbound",
"Izzet Guildmage","Kaervek's Torch","Lose Focus","Memory Lapse","Mnemonic Wall",
"Murmuring Mystic","Pieces of the Puzzle","Rolling Thunder","Shattering Pulse",
"Skred","Snap","Star Compass","Turn Aside",
}
EXPECTED_VETERAN={
"Afterlife","Alabaster Host Intercessor","Benevolent Blessing","Bonder's Ornament",
"Boreal Druid","Brightwood Tracker","Cho-Manno's Blessing","Colossal Dreadmask",
"Cosmic Hunger","Deepwood Denizen","Destroy Evil","Forge of Heroes","Generous Gift",
"Guardian Naga // Banishing Coils","Heliod's Pilgrim","Ilysian Caryatid",
"Leafkin Druid","Llanowar Visionary","Master's Rebuke","Nyxborn Hydra",
"Opal Palace","Owlbear","Prismatic Strands","Ram Through","Shardless Outlander",
"Shrine Steward","Snake Umbra","Spirit Link","Stave Off","Temporal Isolation",
"Ulvenwald Captive // Ulvenwald Abomination","Vines of Vastwood",
"Whisperer of the Wilds",
}
EXPECTED_PDH={
"PDH commander-zone initialization",
"PDH commander recast/tax semantics",
"16-damage commander-loss accounting in gameplay engine",
"30-life PDH game initialization",
"Phase-29 event-ledger extraction from full engine game",
}

def main():
    d=json.loads(INV.read_text())
    assert d["schema"]=="izzet-v09-position1-engine-gap-inventory-v1"
    izzet=set(d["izzet"]); veteran=set(d["veteran_beastrider"])
    assert izzet==EXPECTED_IZZET
    assert veteran==EXPECTED_VETERAN
    assert izzet.isdisjoint(veteran)
    assert len(izzet)==22 and len(veteran)==33
    assert d["izzet_unresolved_count"]==22
    assert d["veteran_unresolved_count"]==33
    assert d["total_unresolved"]==55
    assert set(d["pdhspecific_blockers"])==EXPECTED_PDH
    assert d["official_execution_authorized"] is False
    assert d["official_seeds_consumed"]==0
    assert d["games_initialized"]==0
    assert d["outcome_exposure"]==0
    print("V09_POSITION1_ENGINE_GAP_INVENTORY_VALIDATION_PASS")

if __name__=="__main__":
    main()
