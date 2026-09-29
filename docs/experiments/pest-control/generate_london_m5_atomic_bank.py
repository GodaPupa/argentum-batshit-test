#!/usr/bin/env python3
import hashlib, json
from pathlib import Path

ROOT=Path(".")
SOURCE=ROOT/"docs/experiments/pest-control/tier-one-monster-c2-exact-main-source-map.json"
EXPECTED_MAIN="79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"
LANDS={"Bojuka Bog","Conduit Pylons","Forest","Haunted Fengraf","Urza's Mine","Urza's Power Plant","Urza's Tower"}
NONLAND_FACTS={
  "Rooftop Percher":(5,""),"Generous Ent":(6,"G"),"Boulderbranch Golem":(7,""),
  "Bramble Wurm":(7,"G"),"Maelstrom Colossus":(8,""),"Ancient Stirrings":(1,"G"),
  "Crop Rotation":(1,"G"),"Breath Weapon":(3,"R"),"Unfathomable Truths":(5,"U"),
  "Barrels of Blasting Jelly":(1,""),"Candy Trail":(1,""),"Expedition Map":(1,""),
  "Giant's Boulder":(1,""),"Bonder's Ornament":(3,""),"Pinnacle Kill-Ship":(7,""),
}
LAND_COLORS={
  "Bojuka Bog":set("B"),"Conduit Pylons":set("WUBRG"),"Forest":set("G"),
  "Haunted Fengraf":set(),"Urza's Mine":set(),"Urza's Power Plant":set(),"Urza's Tower":set(),
}

src=json.loads(SOURCE.read_text())
assert src["frozen_main_sha256"]["monster"]==EXPECTED_MAIN
monster=next(d for d in src["decks"] if d["seat_policy"]=="monster")
cards=sorted((c["name"],int(c["count"])) for c in monster["cards"])
assert len(cards)==22 and sum(c for _,c in cards)==60

chosen=[0]*len(cards)
total=0
signature_keys=set()
atomic={}
signature_to_atoms={}

def canonical_hand():
    return ";".join(f"{cards[i][0]}={n}" for i,n in enumerate(chosen) if n)

def walk(i,left):
    global total
    if i<len(cards):
        for n in range(min(cards[i][1],left)+1):
            chosen[i]=n; walk(i+1,left-n)
        chosen[i]=0
        return
    if left:return
    total+=1
    counts={cards[i][0]:n for i,n in enumerate(chosen) if n}
    land_count=sum(counts.get(x,0) for x in LANDS)
    sole_land=next((x for x in sorted(LANDS) if counts.get(x,0)==1),None) if land_count==1 else None
    forest_outside=2-counts.get("Forest",0)
    m2=bool(land_count==1 and sole_land!="Bojuka Bog" and counts.get("Generous Ent",0)>0 and forest_outside>0)
    effective=land_count+(1 if m2 else 0)
    horizon=min(3,effective)
    early=[]
    for name,(cmc,_) in NONLAND_FACTS.items():
        if counts.get(name,0) and cmc<=horizon:
            early.append(name)
    produced=set()
    for name in LANDS:
        if counts.get(name,0): produced |= LAND_COLORS[name]
    castable=sum(1 for name in early if not NONLAND_FACTS[name][1] or any(c in produced for c in NONLAND_FACTS[name][1]))
    mismatch=len(early)-castable
    color_functional=bool(early and (mismatch<3 or castable>=2))
    land_multiset=tuple((x,counts.get(x,0)) for x in sorted(LANDS) if counts.get(x,0))
    early_multiset=tuple((x,counts.get(x,0)) for x in sorted(early))
    sig={"physical_lands":land_multiset,"m2_opening_candidate":m2,"early_spells":early_multiset,"color_functional":color_functional}
    sig_key=json.dumps(sig,sort_keys=True,separators=(",",":"))
    signature_keys.add(sig_key)
    atoms=[]
    for spell in sorted(set(early)):
        atom={"physical_lands":land_multiset,"m2_opening_candidate":m2,"early_spell":spell}
        key=json.dumps(atom,sort_keys=True,separators=(",",":"))
        atoms.append(key)
        row=atomic.setdefault(key,{"atom":atom,"representative_hand":canonical_hand(),"reachable_name_multisets":0})
        row["reachable_name_multisets"]+=1
        hand=canonical_hand()
        if hand<row["representative_hand"]:row["representative_hand"]=hand
    signature_to_atoms.setdefault(sig_key,atoms)

walk(0,7)
assert total==882297
rows=[atomic[k] for k in sorted(atomic)]
digest=hashlib.sha256()
for row in rows:
    digest.update(json.dumps(row,sort_keys=True,separators=(",",":")).encode());digest.update(b"\n")
mapping_digest=hashlib.sha256()
for key in sorted(signature_to_atoms):
    mapping_digest.update(json.dumps({"signature":json.loads(key),"atoms":signature_to_atoms[key]},sort_keys=True,separators=(",",":")).encode());mapping_digest.update(b"\n")
out={
  "schema":"pest-monster-london-m5-atomic-bank-v1",
  "frozen_monster_main_sha256":EXPECTED_MAIN,
  "reachable_seven_card_name_multisets":total,
  "unique_m5_input_signatures":len(signature_keys),
  "unique_atomic_development_questions":len(rows),
  "atomic_bank_sha256":digest.hexdigest(),
  "signature_to_atoms_sha256":mapping_digest.hexdigest(),
  "truth_assigned":False,
  "atom_rule":"exact physical land-name multiset + opening M2 candidate flag + one distinct early spell identity",
  "existential_reconstruction":"A signature's M5 is true iff at least one of its mapped atomic questions is true; false iff every mapped atom is false; empty early-spell sets remain false/unknown according to the frozen raw branch and are not silently certified here.",
  "rows":rows,
  "limits":[
    "No raw-controller or lawful-certificate truth value is assigned.",
    "No keep/bottom parity, physical-handle parity, pair/C2-A2, replay/durability, seed, allocation or gameplay authority."
  ],
  "next_gate":"Execute each atomic development question against the frozen raw controller planning predicate and a lawful synthetic/public certificate, require equality, then reconstruct M5 for all 79,972 signatures."
}
print(json.dumps(out,sort_keys=True,indent=2))
