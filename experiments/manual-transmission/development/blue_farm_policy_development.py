from __future__ import annotations
from itertools import product
import hashlib, json
from pathlib import Path

LOCKS=["none","ranger","silence","orims_chant_mt","voice_blue_turn"]
BOOL=[False,True]

def spell_castable(lock,kind):
    if lock in {"silence","orims_chant_mt","voice_blue_turn"}:
        return False
    if lock=="ranger" and kind=="noncreature":
        return False
    return True

def oracle_truth(window,lock,answer,affordable):
    if not affordable or answer=="none": return False
    if window=="oracle_spell":
        if answer=="noncreature_counter": return spell_castable(lock,"noncreature")
        if answer=="hope_ender": return spell_castable(lock,"creature")
        return False
    if window=="oracle_etb":
        if answer=="tidebinder": return spell_castable(lock,"creature")
        if answer=="source_removal": return False
        return False
    raise AssertionError(window)

def oracle_reference(window,lock,answer,affordable):
    # Development comparator ignores Ranger/Voice/Silence type differences and
    # incorrectly treats post-ETB source removal as a stop.
    return bool(affordable and answer!="none")

def breach_truth(fuel,breach_alive,answer,affordable):
    if not breach_alive: return False
    if answer=="remove_breach" and affordable: return False
    # Countering one storm trigger never removes Breach or the original spell.
    # After the original Brain Freeze resolves it returns to graveyard; at least
    # three distinct other cards are needed for another escape.
    return fuel>=3

def breach_reference(fuel,breach_alive,answer,affordable):
    if not breach_alive: return False
    if answer=="remove_breach" and affordable: return False
    if answer=="tidebinder_storm" and affordable: return False
    return fuel>=3

def main():
    oracle_rows=[]
    for window in ["oracle_spell","oracle_etb"]:
        answers=["none","noncreature_counter","hope_ender"] if window=="oracle_spell" else ["none","tidebinder","source_removal"]
        for lock,answer,aff in product(LOCKS,answers,BOOL):
            t=oracle_truth(window,lock,answer,aff); r=oracle_reference(window,lock,answer,aff)
            oracle_rows.append({
              "window":window,"lock":lock,"answer":answer,"affordable":aff,
              "truth_stop":t,"reference_stop":r,"candidate_stop":t,
              "reference_false_stop":bool(r and not t),
              "reference_false_live":bool((not r) and t),
              "candidate_false_stop":False,"candidate_false_live":False
            })
    breach_rows=[]
    for fuel,breach_alive,answer,aff in product(range(7),BOOL,["none","tidebinder_storm","remove_breach"],BOOL):
        t=breach_truth(fuel,breach_alive,answer,aff); r=breach_reference(fuel,breach_alive,answer,aff)
        breach_rows.append({
          "fuel_other_cards":fuel,"breach_alive":breach_alive,"answer":answer,"affordable":aff,
          "truth_rebuild_live":t,"reference_rebuild_live":r,"candidate_rebuild_live":t,
          "reference_false_stop":bool((not r) and t),
          "reference_false_live":bool(r and not t),
          "candidate_false_stop":False,"candidate_false_live":False
        })
    rows=oracle_rows+breach_rows
    result={
      "protocol":"MT_BLUE_FARM_POLICY_DEVELOPMENT_R1_2026_09_24",
      "status":"DEVELOPMENT_ONLY","qualification_eligible":False,
      "result_class":"exact enumeration policy development",
      "oracle_rows":len(oracle_rows),"breach_rows":len(breach_rows),"cells":len(rows),
      "reference_false_stop":sum(r["reference_false_stop"] for r in rows),
      "reference_false_live":sum(r["reference_false_live"] for r in rows),
      "candidate_false_stop":0,"candidate_false_live":0,
      "candidate_rule":{
        "name":"R3-BF (development candidate)",
        "text":[
          "Distinguish Oracle spell window from Oracle ETB window; source removal after Oracle ETB is not a stop of the existing trigger.",
          "Resolved Ranger blocks Manual Transmission noncreature spells but leaves creature answers such as Tidebinder/Hope-Ender castable.",
          "Resolved Silence, a Chant targeting Manual Transmission, or Voice during Blue Farm's turn closes creature and noncreature spell windows.",
          "Track Breach graveyard fuel exactly; countering one Brain Freeze storm trigger is only a delay if Breach remains and at least three distinct other graveyard cards can fund another escape after the original resolves.",
          "Track Mindbreak/storm casts separately from spell copies."
        ],
        "hidden_information_required":False
      },
      "random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    }
    print(json.dumps(result,indent=2,sort_keys=True))

if __name__=="__main__": main()
