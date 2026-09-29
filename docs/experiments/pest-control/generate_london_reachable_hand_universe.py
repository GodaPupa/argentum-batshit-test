#!/usr/bin/env python3
import hashlib, json
from pathlib import Path

ROOT=Path(".")
SOURCE=ROOT/"docs/experiments/pest-control/tier-one-monster-c2-exact-main-source-map.json"
EXPECTED_MAIN="79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"
LANDS={"Bojuka Bog","Conduit Pylons","Forest","Haunted Fengraf","Urza's Mine","Urza's Power Plant","Urza's Tower"}

src=json.loads(SOURCE.read_text())
assert src["frozen_main_sha256"]["monster"]==EXPECTED_MAIN
monster=next(d for d in src["decks"] if d["seat_policy"]=="monster")
cards=sorted((c["name"], int(c["count"])) for c in monster["cards"])
assert sum(c for _,c in cards)==60
assert len(cards)==22

digest=hashlib.sha256()
total=0
by_land={str(i):0 for i in range(8)}
with_ent=0
one_land_with_ent=0
examples=[]

chosen=[0]*len(cards)
def walk(i,left):
    global total,with_ent,one_land_with_ent
    if i==len(cards):
        if left: return
        parts=[f"{cards[j][0]}={n}" for j,n in enumerate(chosen) if n]
        line=";".join(parts)+"\n"
        digest.update(line.encode())
        total+=1
        lands=sum(n for (name,_),n in zip(cards,chosen) if name in LANDS)
        by_land[str(lands)]+=1
        ent=sum(n for (name,_),n in zip(cards,chosen) if name=="Generous Ent")
        if ent:
            with_ent+=1
            if lands==1: one_land_with_ent+=1
        if len(examples)<8: examples.append(line.strip())
        return
    cap=min(cards[i][1],left)
    for n in range(cap+1):
        chosen[i]=n
        walk(i+1,left-n)
    chosen[i]=0

walk(0,7)
assert total==882297
assert sum(by_land.values())==total
out={
  "schema":"pest-monster-london-reachable-hand-universe-v1",
  "source_map_path":str(SOURCE),
  "source_map_frozen_monster_sha256":EXPECTED_MAIN,
  "card_identities":len(cards),
  "deck_cards":60,
  "opening_hand_cards":7,
  "unique_name_multisets":total,
  "canonical_stream_sha256":digest.hexdigest(),
  "canonical_line_rule":"card names sorted lexicographically; emit only positive name=count pairs joined by semicolon; LF per hand; enumerate bounded count vector lexicographically by count 0..max",
  "land_names":sorted(LANDS),
  "by_physical_land_count":by_land,
  "hands_with_generous_ent":with_ent,
  "one_physical_land_hands_with_generous_ent":one_land_with_ent,
  "hidden_library_order_used":False,
  "library_entity_ids_used":False,
  "rng_used":False,
  "official_counters_delta":0,
  "sample_first_8_canonical_lines":examples,
  "limits":[
    "This freezes the exact reachable seven-card name-multiset denominator only.",
    "It does not compute M2/M4/M5, physical handle permutations, raw decisions, bottom equality, pair competence, C2/A2, replay or gameplay."
  ],
  "next_gate":"Map each canonical hand multiset plus mulligan-count/bottom-count context through lawful submitted-list/visible-hand certificates into the eleven-axis predicate vector; deduplicate vectors, add only required physical-handle permutations, then compare against the frozen raw controller."
}
print(json.dumps(out,sort_keys=True,indent=2))
