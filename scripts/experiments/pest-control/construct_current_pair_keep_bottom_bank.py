#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,math,pathlib,sys
from collections import defaultdict

ROOT=pathlib.Path(".")
TRUTH=ROOT/"build/reports/pest-current-pair-keep-bottom-bank/truth-bank.json"
OUT=ROOT/"build/reports/pest-current-pair-keep-bottom-bank"

PEST=[
 ("Essence Warden",4),("Carrier Thrall",4),("Blood Researcher",4),("Pest Mascot",4),
 ("Fierce Witchstalker",4),("Generous Ent",3),("Follow the Lumarets",4),("Weather the Storm",4),
 ("Cast Down",4),("Bone Shards",2),("Chainer's Edict",2),("Forest",10),("Swamp",7),("Jungle Hollow",4),
]
MONSTER=[
 ("Rooftop Percher",2),("Generous Ent",2),("Boulderbranch Golem",2),("Bramble Wurm",4),
 ("Maelstrom Colossus",4),("Ancient Stirrings",4),("Crop Rotation",3),("Breath Weapon",2),
 ("Unfathomable Truths",2),("Barrels of Blasting Jelly",1),("Candy Trail",2),("Expedition Map",4),
 ("Giant's Boulder",4),("Bonder's Ornament",2),("Pinnacle Kill-Ship",4),("Bojuka Bog",1),
 ("Conduit Pylons",2),("Forest",2),("Haunted Fengraf",1),("Urza's Mine",4),("Urza's Power Plant",4),("Urza's Tower",4),
]
PEST_HASH="7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
MONSTER_HASH="79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"

FACTS={
 "pest":{
  "lands":{"Forest":{"G"},"Swamp":{"B"},"Jungle Hollow":{"B","G"}},
  "spells":{
   "Essence Warden":(1,{"G"}),"Carrier Thrall":(2,{"B"}),"Blood Researcher":(3,{"B","G"}),
   "Pest Mascot":(3,{"B","G"}),"Fierce Witchstalker":(4,{"G"}),"Generous Ent":(6,{"G"}),
   "Follow the Lumarets":(2,{"G"}),"Weather the Storm":(2,{"G"}),"Cast Down":(2,{"B"}),
   "Bone Shards":(1,{"B"}),"Chainer's Edict":(2,{"B"}),
  },
  "forest_total":10,
  "tapped_solo":{"Jungle Hollow"},
 },
 "monster":{
  "lands":{"Bojuka Bog":{"B"},"Conduit Pylons":{"W","U","B","R","G"},"Forest":{"G"},
           "Haunted Fengraf":set(),"Urza's Mine":set(),"Urza's Power Plant":set(),"Urza's Tower":set()},
  "spells":{
   "Rooftop Percher":(5,set()),"Generous Ent":(6,{"G"}),"Boulderbranch Golem":(7,set()),
   "Bramble Wurm":(7,{"G"}),"Maelstrom Colossus":(8,set()),"Ancient Stirrings":(1,{"G"}),
   "Crop Rotation":(1,{"G"}),"Breath Weapon":(3,{"R"}),"Unfathomable Truths":(5,{"U"}),
   "Barrels of Blasting Jelly":(1,set()),"Candy Trail":(1,set()),"Expedition Map":(1,set()),
   "Giant's Boulder":(1,set()),"Bonder's Ornament":(3,set()),"Pinnacle Kill-Ship":(7,set()),
  },
  "forest_total":2,
  "tapped_solo":{"Bojuka Bog"},
 }
}

def canonical(v): return json.dumps(v,sort_keys=True,separators=(",",":"))
def deck_hash(rows):
    return hashlib.sha256("".join(f"{n},{c}\n" for n,c in rows).encode()).hexdigest()

def hands(rows,k=7):
    chosen=[0]*len(rows)
    def rec(i,left):
        if i==len(rows):
            if left==0: yield tuple(chosen)
            return
        for n in range(min(rows[i][1],left)+1):
            chosen[i]=n
            yield from rec(i+1,left-n)
        chosen[i]=0
    yield from rec(0,k)

def mult(counts):
    n=sum(counts); r=math.factorial(n)
    for c in counts:r//=math.factorial(c)
    return r

def truth_map():
    bank=json.loads(TRUTH.read_text())
    assert bank["schema"]=="pest-monster-london-m5-atomic-truth-bank-v1"
    assert len(bank["rows"])==6850
    result={}
    for row in bank["rows"]:
        a=row["atom"]
        key=(tuple(tuple(x) for x in a["physical_lands"]),bool(a["m2_opening_candidate"]),a["early_spell"])
        assert key not in result
        result[key]=bool(row["development_functional"])
    return result

def m5_status(land_multiset,m2,early,truth,unbanked):
    if not early:return None
    seen=[]
    unknown=False
    for spell in sorted(set(early)):
        key=(land_multiset,m2,spell)
        if key not in truth:
            unbanked.add(key);unknown=True
        else:seen.append(truth[key])
    if any(seen):return True
    if unknown:return None
    return False

def physical_factor(counts,rows,facts,bottom):
    land_names=set(facts["lands"])
    land_count=sum(n for (name,_),n in zip(rows,counts) if name in land_names)
    target=3 if 7-bottom>5 else 2
    excess=max(0,land_count-target)
    land_factor=math.factorial(land_count) if excess>0 else 1
    need=max(0,bottom-excess)
    if need==0:return land_factor
    groups=defaultdict(int)
    for (name,_),n in zip(rows,counts):
        if n and name not in land_names:groups[facts["spells"][name][0]]+=n
    spell_factor=1;left=need
    for cmc in sorted(groups,reverse=True):
        if left<=0:break
        spell_factor*=math.factorial(groups[cmc])
        left-=groups[cmc]
    return land_factor*spell_factor

def process(deck_id,rows,truth):
    facts=FACTS[deck_id];lands=set(facts["lands"]);unbanked=set();base_keys=set()
    denominator=0;physical={0:0,1:0,2:0};unknown_nonforced=0;known_nonforced=0
    stream=hashlib.sha256()
    by_land={str(i):0 for i in range(8)}
    for counts in hands(rows):
        denominator+=1
        c={name:n for (name,_),n in zip(rows,counts) if n}
        land_count=sum(c.get(x,0) for x in lands);by_land[str(land_count)]+=1
        sole=next((x for x in sorted(lands) if c.get(x,0)==1),None) if land_count==1 else None
        m2=bool(land_count==1 and sole not in facts["tapped_solo"] and c.get("Generous Ent",0)>0 and facts["forest_total"]-c.get("Forest",0)>0)
        effective=land_count+(1 if m2 else 0);horizon=min(3,effective)
        early=[]
        for name,(cmc,_) in facts["spells"].items():
            if c.get(name,0) and cmc<=horizon:early.extend([name]*c[name])
        produced=set()
        for name,colors in facts["lands"].items():
            if c.get(name,0):produced|=colors
        castable=sum(1 for name in early if not facts["spells"][name][1] or facts["spells"][name][1]&produced)
        color_functional=bool(early and ((len(early)-castable)<3 or castable>=2))
        land_multiset=tuple((x,c[x]) for x in sorted(lands) if c.get(x))
        m5=m5_status(land_multiset,m2,early,truth,unbanked)
        dup={k:v for k,v in sorted(c.items()) if v>1}
        for mull in (0,1,2):
            forced=mull>=2
            b0=3 if mull<2 else 2
            key={
              "deck":deck_id,"mulligans":mull,"forced_keep":forced,"physical_land_count":land_count,
              "m2":m2,"early_spells":sorted(early),"color_functional":color_functional,"m5":m5,
              "bottom_target_lands":b0,"protected_pair":m2,"duplicate_name_multiplicities":dup,
            }
            base_keys.add(canonical(key))
            if mull<2:
                if m5 is None:unknown_nonforced+=1
                else:known_nonforced+=1
            factor=1 if mull==0 else physical_factor(counts,rows,facts,mull)
            physical[mull]+=factor
            for seat in (0,1):
                for on_play in (False,True):
                    stream.update((canonical({"seat":seat,"on_play":on_play,**key,"physical_factor":factor})+"\n").encode())
    return {
      "name_multisets":denominator,"by_land_count":by_land,
      "base_logical_keys_without_seat_start":len(base_keys),
      "explicit_context_rows":denominator*3*2*2,
      "physical_sensitive_representatives_before_seat_start":physical,
      "physical_sensitive_representatives_with_seat_start":{str(k):v*4 for k,v in physical.items()},
      "unknown_nonforced_hand_contexts_before_seat_start":unknown_nonforced,
      "known_nonforced_hand_contexts_before_seat_start":known_nonforced,
      "unbanked_atom_keys":[{"physical_lands":[list(x) for x in k[0]],"m2_opening_candidate":k[1],"early_spell":k[2]} for k in sorted(unbanked)],
      "unbanked_atom_count":len(unbanked),
      "canonical_context_stream_sha256":stream.hexdigest(),
    }

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    assert deck_hash(PEST)==PEST_HASH
    assert deck_hash(MONSTER)==MONSTER_HASH
    truth=truth_map()
    result={
      "schema":"pest-current-pair-keep-bottom-bank-construction-v1",
      "pest":process("pest",PEST,truth),
      "monster":process("monster",MONSTER,truth),
      "truth_rows":len(truth),
      "rng_used":False,
      "hidden_library_order_used":False,
      "raw_controller_executed":False,
      "behavioral_cases":0,
      "official_counters_delta":0,
      "authority":"CURRENT_PAIR_KEEP_BOTTOM_BANK_CONSTRUCTION_ONLY",
    }
    assert result["monster"]["name_multisets"]==882297
    (OUT/"bank-construction.json").write_text(json.dumps(result,sort_keys=True,indent=2)+"\n")
    (OUT/"unbanked-atoms.json").write_text(json.dumps({
      "schema":"pest-current-pair-unbanked-m5-atoms-v1",
      "pest":result["pest"]["unbanked_atom_keys"],
      "monster":result["monster"]["unbanked_atom_keys"],
    },sort_keys=True,indent=2)+"\n")
    print(json.dumps({
      "pest_name_multisets":result["pest"]["name_multisets"],
      "monster_name_multisets":result["monster"]["name_multisets"],
      "pest_unbanked_atoms":result["pest"]["unbanked_atom_count"],
      "monster_unbanked_atoms":result["monster"]["unbanked_atom_count"],
      "behavioral_cases":0,"official_counters_delta":0
    },sort_keys=True))

if __name__=="__main__":main()
