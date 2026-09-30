#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,pathlib

ROOT=pathlib.Path(".")
INPUT=ROOT/"build/reports/pest-current-pair-phase-u/unbanked-atoms.json"
OUT=ROOT/"build/reports/pest-current-pair-phase-u"
PEST=[
 ("Essence Warden",4),("Carrier Thrall",4),("Blood Researcher",4),("Pest Mascot",4),
 ("Fierce Witchstalker",4),("Generous Ent",3),("Follow the Lumarets",4),("Weather the Storm",4),
 ("Cast Down",4),("Bone Shards",2),("Chainer's Edict",2),("Forest",10),("Swamp",7),("Jungle Hollow",4),
]
PEST_HASH="7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
LANDS={"Forest","Swamp","Jungle Hollow"}
SPELLS={
 "Essence Warden":1,"Carrier Thrall":2,"Blood Researcher":3,"Pest Mascot":3,
 "Fierce Witchstalker":4,"Generous Ent":6,"Follow the Lumarets":2,"Weather the Storm":2,
 "Cast Down":2,"Bone Shards":1,"Chainer's Edict":2,
}
TAPPED_SOLO={"Jungle Hollow"}
FOREST_TOTAL=10

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

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    assert deck_hash(PEST)==PEST_HASH
    source=json.loads(INPUT.read_text())
    assert source["schema"]=="pest-current-pair-unbanked-m5-atoms-v1"
    assert source["monster"]==[]
    unknown={
      (tuple(tuple(x) for x in a["physical_lands"]),bool(a["m2_opening_candidate"]),a["early_spell"])
      for a in source["pest"]
    }
    assert len(unknown)==692
    reps={}
    for counts in hands(PEST):
        c={name:n for (name,_),n in zip(PEST,counts) if n}
        land_count=sum(c.get(x,0) for x in LANDS)
        sole=next((x for x in sorted(LANDS) if c.get(x,0)==1),None) if land_count==1 else None
        m2=bool(land_count==1 and sole not in TAPPED_SOLO and c.get("Generous Ent",0)>0 and FOREST_TOTAL-c.get("Forest",0)>0)
        horizon=min(3,land_count+(1 if m2 else 0))
        early=[name for name,cmc in SPELLS.items() if c.get(name,0) and cmc<=horizon]
        physical=tuple((x,c[x]) for x in sorted(LANDS) if c.get(x))
        rep=[[name,c[name]] for name in sorted(c)]
        repkey=json.dumps(rep,separators=(",",":"))
        for spell in set(early):
            key=(physical,m2,spell)
            if key in unknown and (key not in reps or repkey<reps[key][0]):
                reps[key]=(repkey,rep)
    assert set(reps)==unknown
    rows=[]
    for physical,m2,spell in sorted(unknown):
        rows.append({
          "atom":{"physical_lands":[list(x) for x in physical],"m2_opening_candidate":m2,"early_spell":spell},
          "representative_hand":reps[(physical,m2,spell)][1],
        })
    result={
      "schema":"pest-current-pair-phase-u-pest-unbanked-m5-worklist-v1",
      "source_unbanked_atoms_sha256":"a00aef9df49106a2611079ece2824bf4e97d40c7e89207b16bc1a9e05d7ae287",
      "pest_main_sha256":PEST_HASH,
      "rows":rows,
      "row_count":len(rows),
      "selection":"lexicographically smallest canonical seven-card name multiset realizing each exact frozen atom",
      "rng_used":False,
      "hidden_library_order_used":False,
    }
    raw=(json.dumps(result,sort_keys=True,separators=(",",":"))+"\n").encode()
    assert hashlib.sha256(raw).hexdigest()=="2174410ee36dc0e74d0d7f4335606de7236aa845ef43bab409259a3056133738"
    (OUT/"pest-unbanked-m5-worklist.json").write_bytes(raw)
    print(json.dumps({"rows":len(rows),"sha256":hashlib.sha256(raw).hexdigest(),"behavioral_cases":0,"official_counters_delta":0},sort_keys=True))

if __name__=="__main__": main()
