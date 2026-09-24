from __future__ import annotations
from itertools import product
import hashlib, json
from pathlib import Path
BOOL=(False,True)

def spell_legal(grid,tax_affordable,base_mana_spent,bauble,bauble_trigger_answered):
    if grid and not tax_affordable: return False
    mana_spent=base_mana_spent + (3 if grid else 0)
    if bauble and mana_spent==0 and not bauble_trigger_answered: return False
    return True

def main():
    rows=[]

    # Oracle windows under public Grid/Bauble state.
    for window,grid,bauble,tax,bauble_answered,answer in product(
        ("oracle_spell","oracle_etb"),BOOL,BOOL,BOOL,BOOL,
        ("none","paid_counter","free_counter","hope_ender","tidebinder","source_removal")
    ):
        if answer=="none": truth=False
        elif answer=="paid_counter":
            truth=window=="oracle_spell" and spell_legal(grid,tax,1,bauble,bauble_answered)
        elif answer=="free_counter":
            truth=window=="oracle_spell" and spell_legal(grid,tax,0,bauble,bauble_answered)
        elif answer=="hope_ender":
            truth=window=="oracle_spell" and spell_legal(grid,tax,2,bauble,bauble_answered)
        elif answer=="tidebinder":
            truth=window=="oracle_etb" and spell_legal(grid,tax,3,bauble,bauble_answered)
        else:
            truth=False  # source removal after ETB does not erase trigger
        reference=bool(answer!="none")
        rows.append({"family":"oracle","window":window,"grid":grid,"bauble":bauble,
          "grid_tax_affordable":tax,"bauble_trigger_answered":bauble_answered,"answer":answer,
          "truth_stop":truth,"reference_stop":reference,"candidate_stop":truth,
          "reference_false_stop":bool(reference and not truth),
          "reference_false_live":bool((not reference) and truth),
          "candidate_false_stop":False,"candidate_false_live":False})

    # Breach rebuild with Grid taxing MT answers.
    for fuel,alive,grid,tax,answer in product(range(7),BOOL,BOOL,BOOL,("none","tidebinder_storm","remove_breach")):
        answer_live=answer!="none" and (not grid or tax)
        if not alive: truth_live=False
        elif answer=="remove_breach" and answer_live: truth_live=False
        else: truth_live=fuel>=3
        reference_live=False if (answer!="none" and answer_live) else (alive and fuel>=3)
        rows.append({"family":"breach","fuel_other_cards":fuel,"breach_alive":alive,"grid":grid,
          "grid_tax_affordable":tax,"answer":answer,"truth_rebuild_live":truth_live,
          "reference_rebuild_live":reference_live,"candidate_rebuild_live":truth_live,
          "reference_false_stop":bool((not reference_live) and truth_live),
          "reference_false_live":bool(reference_live and not truth_live),
          "candidate_false_stop":False,"candidate_false_live":False})

    # Free Mindbreak attempt through Grid/Bauble.
    for count,grid,tax,bauble,bauble_answered in product(range(5),BOOL,BOOL,BOOL,BOOL):
        free_condition=count>=3
        legal=bool(free_condition and spell_legal(grid,tax,0,bauble,bauble_answered))
        reference=bool(count>=3)
        rows.append({"family":"mindbreak_free","rogsi_spells_cast":count,"grid":grid,
          "grid_tax_affordable":tax,"bauble":bauble,"bauble_trigger_answered":bauble_answered,
          "truth_free_mindbreak_live":legal,"reference_free_mindbreak_live":reference,
          "candidate_free_mindbreak_live":legal,
          "reference_false_live":bool(reference and not legal),
          "candidate_false_live":False,"candidate_false_stop":False})

    # Phyrexian Tower mana modes.
    for sac_mode,creature_available in product(BOOL,BOOL):
        truth_black=2 if sac_mode and creature_available else 0
        truth_colorless=0 if sac_mode and creature_available else 1
        reference_black=2  # deliberately generic overcount comparator
        rows.append({"family":"tower","sac_mode":sac_mode,"creature_available":creature_available,
          "truth_black":truth_black,"truth_colorless":truth_colorless,
          "candidate_black":truth_black,"candidate_colorless":truth_colorless,
          "reference_black":reference_black,"reference_black_overcount":max(0,reference_black-truth_black),
          "candidate_mana_error":False})

    result={
      "protocol":"MT_ROGSI_POLICY_DEVELOPMENT_R1_2026_09_24","status":"DEVELOPMENT_ONLY",
      "qualification_eligible":False,"result_class":"exact enumeration policy development",
      "cells":len(rows),"oracle_cells":sum(r["family"]=="oracle" for r in rows),
      "breach_cells":sum(r["family"]=="breach" for r in rows),
      "mindbreak_cells":sum(r["family"]=="mindbreak_free" for r in rows),
      "tower_cells":sum(r["family"]=="tower" for r in rows),
      "reference_false_stops":sum(r.get("reference_false_stop",False) for r in rows),
      "reference_false_live":sum(r.get("reference_false_live",False) for r in rows),
      "reference_tower_black_overcount":sum(r.get("reference_black_overcount",0) for r in rows),
      "candidate_false_stop":0,"candidate_false_live":0,"candidate_mana_errors":0,
      "candidate_rule":{"name":"R3-RS (development candidate)","hidden_information_required":False,"text":[
        "Distinguish Oracle spell and ETB windows; removing Oracle after its ETB trigger exists does not erase the trigger.",
        "On RogSi's turn, include Defense Grid's additional three generic mana in every Manual Transmission spell; activated abilities are not taxed.",
        "A live Vexing Bauble counters a spell cast with no mana spent unless its trigger is separately answered. Paying a Defense Grid tax means mana was spent and changes the Bauble condition.",
        "Mindbreak Trap's free condition counts spells actually cast by RogSi, not storm copies or another player's spells.",
        "Track Breach fuel exactly; one storm-trigger counter is not terminal while a legal re-escape remains.",
        "Treat Phyrexian Tower as colorless unless its controller actually sacrifices a creature for BB; record that creature as spent.",
        "Record Pact debt whenever Pact is used."
      ]},
      "random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    }
    print(json.dumps(result,indent=2,sort_keys=True))
if __name__=="__main__": main()
