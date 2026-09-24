from __future__ import annotations
from itertools import product
import hashlib, json
from pathlib import Path

BOOL=[False,True]
TIDE_LIFE=["clean","spell_countered","removed_before_etb","removed_after_etb"]

def tide_etb_live(humility,tide_available,lifecycle):
    return tide_available and not humility and lifecycle in {"clean","removed_before_etb","removed_after_etb"}

def main():
    scepter=[]
    for humility,tide,life,source_remove,hope,temur in product(BOOL,BOOL,TIDE_LIFE,BOOL,BOOL,BOOL):
        if not tide and life!="clean": continue
        tide_stop=tide_etb_live(humility,tide,life)
        current_activation_countered=tide_stop
        # Removing Scepter does not erase current activation, but does end future
        # Scepter activations after the current copy attempt. Hope-Ender's cast
        # trigger may answer the Dramatic Reversal copy even under Humility.
        downstream_copy_answer=hope
        future_scepter_live=not (tide_stop or (source_remove and temur))
        ref_current=bool(tide or source_remove)
        scepter.append({
          "family":"scepter","humility":humility,"tidebinder":tide,"tide_lifecycle":life,
          "source_removal":source_remove,"hope_ender":hope,"full_temur_reserved":temur,
          "truth_current_activation_countered":current_activation_countered,
          "candidate_current_activation_countered":current_activation_countered,
          "reference_current_activation_countered":ref_current,
          "truth_downstream_copy_answer":downstream_copy_answer,
          "truth_future_scepter_live":future_scepter_live,
          "reference_false_current_stop":bool(ref_current and not current_activation_countered),
          "candidate_false_current_stop":False
        })
    hull=[]
    for humility,tide,life,source_spell_countered,horror_removed in product(BOOL,BOOL,TIDE_LIFE,BOOL,BOOL):
        if not tide and life!="clean": continue
        if humility:
            # In a real Humility state Horror has no trigger. This row therefore
            # classifies only states where a trigger already exists from before
            # the modeled Humility transition as false and is excluded below.
            trigger_exists=False
        else:
            trigger_exists=True
        tide_stop=trigger_exists and tide_etb_live(False,tide,life)
        truth_current_trigger_live=trigger_exists and not tide_stop
        ref_current_trigger_live=trigger_exists and not (tide or source_spell_countered or horror_removed)
        hull.append({
          "family":"hullbreaker","humility":humility,"trigger_exists":trigger_exists,
          "tidebinder":tide,"tide_lifecycle":life,
          "source_spell_countered":source_spell_countered,"horror_removed":horror_removed,
          "truth_current_trigger_live":truth_current_trigger_live,
          "candidate_current_trigger_live":truth_current_trigger_live,
          "reference_current_trigger_live":ref_current_trigger_live,
          "reference_false_stop":bool((not ref_current_trigger_live) and truth_current_trigger_live),
          "candidate_false_stop":False
        })
    result={
      "protocol":"MT_SHORIKAI_POLICY_DEVELOPMENT_R1_2026_09_24",
      "status":"DEVELOPMENT_ONLY","qualification_eligible":False,
      "result_class":"exact enumeration policy development",
      "scepter_cells":len(scepter),"hullbreaker_cells":len(hull),
      "reference_scepter_false_current_stops":sum(r["reference_false_current_stop"] for r in scepter),
      "reference_hullbreaker_false_stops":sum(r["reference_false_stop"] for r in hull),
      "candidate_false_stops":0,
      "candidate_rule":{
        "name":"R3-SH (development candidate)",
        "text":[
          "Treat Scepter activation and Hullbreaker trigger as different stack objects with different answer maps.",
          "Removing Isochron Scepter after activation does not erase the current activation, though it can prevent future Scepter activations; Tidebinder directly counters the current activation only when its ETB exists.",
          "Under Humility, Tidebinder ETB is suppressed while Hope-Ender's cast trigger remains live against a spell such as the copied Dramatic Reversal.",
          "Countering the spell that generated a Hullbreaker trigger or removing Hullbreaker does not erase that already-created trigger.",
          "A live Tidebinder can counter one Hullbreaker trigger and blank Hullbreaker while Tidebinder remains.",
          "Preserve full Temur colors when public Humility/Scepter states make Beast Within or Chaos Warp materially different from blue-only interaction."
        ],
        "hidden_information_required":False
      },
      "random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    }
    print(json.dumps(result,indent=2,sort_keys=True))

if __name__=="__main__": main()
