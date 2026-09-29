#!/usr/bin/env python3
import hashlib, json
from pathlib import Path

ROOT=Path(".")
SOURCE=ROOT/"docs/experiments/pest-control/tier-one-monster-c2-exact-main-source-map.json"
EXPECTED_MAIN="79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"
LANDS={"Bojuka Bog","Conduit Pylons","Forest","Haunted Fengraf","Urza's Mine","Urza's Power Plant","Urza's Tower"}
NONLAND_FACTS={
  "Rooftop Percher":(5,"","c793b9938c0ce545c15daae20b90663fa766d82a"),
  "Generous Ent":(6,"G","eb2dc664f794b69442d479525537404ba4201875"),
  "Boulderbranch Golem":(7,"","e95ba63a99625b880e3a897b07cdf4ed1b567161"),
  "Bramble Wurm":(7,"G","29d677e2398389c4adb3bf2888b9d0a5f4a6f39f"),
  "Maelstrom Colossus":(8,"","73edbec8e9e53c52060e13ef1cabd295f8571f8f"),
  "Ancient Stirrings":(1,"G","c66d1118cb5484bdad9f09b5cbda8fa5ddefaf42"),
  "Crop Rotation":(1,"G","e687d18c2d6f00d26fe96bafd6e49d0c62651f60"),
  "Breath Weapon":(3,"R","6805dddc9b3c15b552b4650b97e37ea58cb1a28e"),
  "Unfathomable Truths":(5,"U","344a541b17ba3287316f4d5950d4cfd7b43ed651"),
  "Barrels of Blasting Jelly":(1,"","11e5dd06536db4998a05c6ae2dfb41c87de39f0d"),
  "Candy Trail":(1,"","6bc3a46de721c3dd81c3259111f8f7e81fbd9108"),
  "Expedition Map":(1,"","b99c571747143aed4c89b39dede9828612cd097a"),
  "Giant's Boulder":(1,"","0ee8f0689ee5e39bf0b5c9f020eb4f0bbce98c50"),
  "Bonder's Ornament":(3,"","bdb6f98fea9ca1ff819baabab70f440cee0ab791"),
  "Pinnacle Kill-Ship":(7,"","5745e4d02147ac2d3fb3760a6b49f78361d52779"),
}
LAND_BLOBS={
  "Bojuka Bog":"aa9212f8c45a68f302a7529fdee75faa442338a0",
  "Conduit Pylons":"f4e4f9ac3c0032176cc9d7bf793389e0401469de",
  "Forest":None,
  "Haunted Fengraf":"6c23dceac8d9fcad932da84502e3917573bdeda5",
  "Urza's Mine":"547d70da182bb8bb864ddfd5097f16550c63e1fc",
  "Urza's Power Plant":"f2c4e2b0347b0c2366b9d1e5d971aca82de90ea2",
  "Urza's Tower":"8f280147686880fd921acc43b49f8bb5d5234431",
}
# Exact raw EngineAiPlayerController colorsProducedBy summary behavior:
# basic Forest => G; Pylons oracle contains "mana of any color"; Bog prints {B}; other lands add only {C}.
LAND_COLORS={
  "Bojuka Bog":set("B"),"Conduit Pylons":set("WUBRG"),"Forest":set("G"),
  "Haunted Fengraf":set(),"Urza's Mine":set(),"Urza's Power Plant":set(),"Urza's Tower":set(),
}

src=json.loads(SOURCE.read_text())
assert src["frozen_main_sha256"]["monster"]==EXPECTED_MAIN
monster=next(d for d in src["decks"] if d["seat_policy"]=="monster")
cards=sorted((c["name"],int(c["count"])) for c in monster["cards"])
source_by_name={c["name"]:c.get("git_blob") for c in monster["cards"]}
assert len(cards)==22 and sum(c for _,c in cards)==60
for name,(_,_,blob) in NONLAND_FACTS.items():
    assert source_by_name[name]==blob,(name,source_by_name[name],blob)
for name,blob in LAND_BLOBS.items():
    assert source_by_name[name]==blob,(name,source_by_name[name],blob)

chosen=[0]*len(cards)
total=0
groups={}
m2_candidates=0
color_functional_true=0

def canonical_hand():
    return ";".join(f"{cards[i][0]}={n}" for i,n in enumerate(chosen) if n)

def walk(i,left):
    global total,m2_candidates,color_functional_true
    if i<len(cards):
        cap=min(cards[i][1],left)
        for n in range(cap+1):
            chosen[i]=n; walk(i+1,left-n)
        chosen[i]=0
        return
    if left: return
    total+=1
    counts={cards[i][0]:n for i,n in enumerate(chosen) if n}
    land_count=sum(counts.get(x,0) for x in LANDS)
    sole_land=next((x for x in sorted(LANDS) if counts.get(x,0)==1),None) if land_count==1 else None
    forest_outside=2-counts.get("Forest",0)
    m2=bool(land_count==1 and sole_land!="Bojuka Bog" and counts.get("Generous Ent",0)>0 and forest_outside>0)
    if m2:m2_candidates+=1
    effective=land_count+(1 if m2 else 0)
    horizon=min(3,effective)
    early=[]
    for name,(cmc,_,_) in NONLAND_FACTS.items():
        n=counts.get(name,0)
        if n and cmc<=horizon:
            early.extend([name]*n)
    produced=set()
    for name in LANDS:
        if counts.get(name,0): produced |= LAND_COLORS[name]
    castable=0
    for name in early:
        req=NONLAND_FACTS[name][1]
        if not req or any(c in produced for c in req):
            castable+=1
    mismatch=len(early)-castable
    color_functional=bool(early and (mismatch<3 or castable>=2))
    if color_functional:color_functional_true+=1
    land_multiset=tuple((x,counts.get(x,0)) for x in sorted(LANDS) if counts.get(x,0))
    early_multiset=tuple((x,early.count(x)) for x in sorted(set(early)))
    sig={
      "physical_lands":land_multiset,
      "m2_opening_candidate":m2,
      "early_spells":early_multiset,
      "color_functional":color_functional,
    }
    key=json.dumps(sig,sort_keys=True,separators=(",",":"))
    g=groups.get(key)
    hand=canonical_hand()
    if g is None:
        groups[key]={"signature":sig,"reachable_name_multisets":1,"representative_hand":hand}
    else:
        g["reachable_name_multisets"]+=1
        if hand<g["representative_hand"]:g["representative_hand"]=hand

walk(0,7)
assert total==882297
rows=sorted(groups.values(),key=lambda r:json.dumps(r["signature"],sort_keys=True,separators=(",",":")))
digest=hashlib.sha256()
for row in rows:
    digest.update(json.dumps(row,sort_keys=True,separators=(",",":")).encode()); digest.update(b"\n")
out={
  "schema":"pest-monster-london-m5-worklist-v1",
  "source_map":str(SOURCE),
  "frozen_monster_main_sha256":EXPECTED_MAIN,
  "reachable_seven_card_name_multisets":total,
  "unique_m5_input_signatures":len(rows),
  "m2_opening_candidate_hands":m2_candidates,
  "color_functional_true_hands":color_functional_true,
  "canonical_worklist_sha256":digest.hexdigest(),
  "signature_rule":"exact physical land-name multiset + exact early-spell-name multiset under raw horizon + opening typecycle candidate + raw colorFunctional summary branch",
  "m5_truth_assigned":False,
  "seat_start_collapsed":True,
  "justification":"Raw M5 opening development uses own hand/card definitions and deterministic land resources; this worklist makes no equality claim and retains one exact representative hand for every distinct name-level input signature. Seat/start and physical-handle permutations remain separate parity dimensions.",
  "rows":rows,
  "limits":[
    "No deterministicDevelopmentPayable/M5 truth value is assigned.",
    "No raw-controller keep/bottom result is compared.",
    "Physical handle ordering/permutations are not collapsed into parity authority.",
    "No pair/C2/A2, replay/durability, seed, allocation or gameplay authority."
  ],
  "next_gate":"For every worklist signature, run the frozen raw controller's M5 development predicate and a lawful submitted-list/public-state certificate on the representative opening; require exact Boolean equality, then merge M5 into the eleven-axis logical bank and add required physical permutations."
}
print(json.dumps(out,sort_keys=True,indent=2))
