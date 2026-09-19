#!/usr/bin/env python3
"""Deterministic mana-development harness for Izzet Science?! v0.1.

Initial harness: parses the frozen markdown deck, validates 99 cards, samples
opening hands reproducibly, and exposes card semantics/regression checks.
Turn-policy execution is intentionally staged behind tests.
"""
from __future__ import annotations
import argparse, random, re
from pathlib import Path

SEED = 0x1A22E7001
LANDS = {
"Island","Mountain","Command Tower","Ash Barrens","Evolving Wilds",
"Terramorphic Expanse","Izzet Boilerworks","Volatile Fjord",
"Swiftwater Cliffs","Silverbluff Bridge","Lonely Sandbar","Forgotten Cave"
}
TAPPED_LANDS={"Izzet Boilerworks","Volatile Fjord","Swiftwater Cliffs",
"Silverbluff Bridge","Lonely Sandbar","Forgotten Cave"}
ROCKS={"Everflowing Chalice","Fellwar Stone","Mind Stone","Star Compass",
"Sky Diamond","Fire Diamond","Izzet Signet","Network Terminal",
"Ur-Golem's Eye","Sisay's Ring"}
MANA_CREATURES={"Ornithopter of Paradise","Silver Myr","Iron Myr"}

def parse_deck(path: Path):
    cards=[]
    commander=None
    section=""
    for raw in path.read_text().splitlines():
        line=raw.strip()
        if line=="Commander": section="commander"; continue
        if line.startswith("## "): section=line; continue
        m=re.match(r"^(\d+) (.+)$",line)
        if not m: continue
        n,name=int(m.group(1)),m.group(2)
        if section=="commander": commander=name
        elif section.startswith("## "): cards += [name]*n
    if commander!="Izzet Guildmage": raise ValueError(f"commander={commander!r}")
    if len(cards)!=99: raise ValueError(f"main deck count={len(cards)}")
    return commander,cards

def opening_baseline(cards, samples=100000, seed=SEED):
    rng=random.Random(seed)
    buckets=[0,0,0,0,0]
    rock_counts=[0,0,0,0]
    for _ in range(samples):
        h=rng.sample(cards,7)
        lands=sum(c in LANDS for c in h)
        buckets[min(lands,4)]+=1
        rocks=sum(c in ROCKS for c in h)
        rock_counts[min(rocks,3)]+=1
    return buckets,rock_counts

def regressions():
    assert "Volatile Fjord" in TAPPED_LANDS
    assert "Command Tower" not in TAPPED_LANDS
    assert "Izzet Signet" in ROCKS
    assert "Ornithopter of Paradise" in MANA_CREATURES
    assert "Silver Myr" not in ROCKS
    # High Tide subtype model: basics plus Volatile Fjord only in frozen v0.1.
    island_subtype={"Island","Volatile Fjord"}
    assert "Swiftwater Cliffs" not in island_subtype
    return True



# --- Stateful mana primitives (turn-sequence v1) ---
MANA_COST={
"Everflowing Chalice":0,"Fellwar Stone":2,"Mind Stone":2,"Star Compass":2,
"Sky Diamond":2,"Fire Diamond":2,"Izzet Signet":2,"Network Terminal":3,
"Ur-Golem's Eye":4,"Sisay's Ring":4,"Ornithopter of Paradise":2,
"Silver Myr":2,"Iron Myr":2,"Izzet Guildmage":2
}
def land_enters_tapped(card): return card in TAPPED_LANDS
def high_tide_island(card): return card in {"Island","Volatile Fjord"}
def rock_enters_tapped(card): return card in {"Star Compass","Sky Diamond","Fire Diamond"}
def creature_mana_ready(card, entered_turn, turn):
    return card in MANA_CREATURES and entered_turn < turn
def chalice_output(kicks): return max(0,kicks)
def signet_net_output(external_input=1):
    return {"U":1,"R":1,"input":external_input,"net":2-external_input}
def boilerworks_legal(lands_before_play): return lands_before_play >= 1

def state_regressions():
    assert land_enters_tapped("Volatile Fjord")
    assert not land_enters_tapped("Island")
    assert high_tide_island("Volatile Fjord")
    assert not high_tide_island("Swiftwater Cliffs")
    assert rock_enters_tapped("Sky Diamond")
    assert not rock_enters_tapped("Mind Stone")
    assert not creature_mana_ready("Silver Myr",2,2)
    assert creature_mana_ready("Silver Myr",2,3)
    assert chalice_output(2)==2
    assert signet_net_output()["net"]==1
    assert not boilerworks_legal(0)
    assert boilerworks_legal(1)
    return True

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--deck",default="izzet-science/v0.1-control.md")
    ap.add_argument("--samples",type=int,default=100000)
    ap.add_argument("--seed",type=lambda x:int(x,0),default=SEED)
    args=ap.parse_args()
    regressions()
    state_regressions()
    _,cards=parse_deck(Path(args.deck))
    b,r=opening_baseline(cards,args.samples,args.seed)
    print("seed",hex(args.seed),"samples",args.samples)
    print("land_buckets_0_1_2_3_4plus",b)
    print("rock_buckets_0_1_2_3plus",r)

if __name__=="__main__":
    main()
