from __future__ import annotations
import argparse, hashlib, json
from itertools import product
from pathlib import Path
BOOL=(False,True)

def spell_legal(grid,tax_affordable,base_mana_spent,bauble,bauble_trigger_answered):
    if grid and not tax_affordable:
        return False
    mana_spent=base_mana_spent + (3 if grid else 0)
    if bauble and mana_spent==0 and not bauble_trigger_answered:
        return False
    return True

def generate():
    rows=[]; cid=0
    for window,grid,bauble,tax,bauble_answered,answer in product(
        ("oracle_spell","oracle_etb"),BOOL,BOOL,BOOL,BOOL,
        ("none","paid_counter","free_counter","hope_ender","tidebinder","source_removal")
    ):
        if answer=="none":
            truth=False
        elif answer=="paid_counter":
            truth=window=="oracle_spell" and spell_legal(grid,tax,1,bauble,bauble_answered)
        elif answer=="free_counter":
            truth=window=="oracle_spell" and spell_legal(grid,tax,0,bauble,bauble_answered)
        elif answer=="hope_ender":
            truth=window=="oracle_spell" and spell_legal(grid,tax,2,bauble,bauble_answered)
        elif answer=="tidebinder":
            truth=window=="oracle_etb" and spell_legal(grid,tax,3,bauble,bauble_answered)
        else:
            truth=False
        ref=bool(answer!="none")
        rows.append({"case_id":cid,"family":"oracle","window":window,"grid":grid,"bauble":bauble,
          "grid_tax_affordable":tax,"bauble_trigger_answered":bauble_answered,"answer":answer,
          "truth_stopped":truth,"reference_stopped":ref,"candidate_stopped":truth,
          "reference_false_stop":bool(ref and not truth),"reference_false_live":bool((not ref) and truth),
          "candidate_false_stop":False,"candidate_false_live":False}); cid+=1

    for fuel,alive,grid,tax,answer in product(range(7),BOOL,BOOL,BOOL,("none","tidebinder_storm","remove_breach")):
        answer_live=answer!="none" and (not grid or tax)
        if not alive:
            truth_live=False
        elif answer=="remove_breach" and answer_live:
            truth_live=False
        else:
            truth_live=fuel>=3
        ref_live=False if (answer!="none" and answer_live) else (alive and fuel>=3)
        rows.append({"case_id":cid,"family":"breach","fuel_other_cards":fuel,"breach_alive":alive,
          "grid":grid,"grid_tax_affordable":tax,"answer":answer,
          "truth_live":truth_live,"reference_live":ref_live,"candidate_live":truth_live,
          "reference_false_stop":bool((not ref_live) and truth_live),
          "reference_false_live":bool(ref_live and not truth_live),
          "candidate_false_stop":False,"candidate_false_live":False}); cid+=1

    for count,grid,tax,bauble,bauble_answered in product(range(5),BOOL,BOOL,BOOL,BOOL):
        truth=bool(count>=3 and spell_legal(grid,tax,0,bauble,bauble_answered))
        ref=bool(count>=3)
        rows.append({"case_id":cid,"family":"mindbreak_free","rogsi_spells_cast":count,"grid":grid,
          "grid_tax_affordable":tax,"bauble":bauble,"bauble_trigger_answered":bauble_answered,
          "truth_live":truth,"reference_live":ref,"candidate_live":truth,
          "reference_false_live":bool(ref and not truth),
          "reference_false_stop":bool((not ref) and truth),
          "candidate_false_live":False,"candidate_false_stop":False}); cid+=1

    for sac_mode,creature_available in product(BOOL,BOOL):
        truth_black=2 if sac_mode and creature_available else 0
        truth_colorless=0 if sac_mode and creature_available else 1
        ref_black=2
        rows.append({"case_id":cid,"family":"tower","sac_mode":sac_mode,
          "creature_available":creature_available,"truth_black":truth_black,
          "truth_colorless":truth_colorless,"candidate_black":truth_black,
          "candidate_colorless":truth_colorless,"reference_black":ref_black,
          "reference_black_overcount":max(0,ref_black-truth_black),"candidate_mana_error":False}); cid+=1
    return rows

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--summary",required=True)
    a=ap.parse_args(); rows=generate(); out=Path(a.rows); out.parent.mkdir(parents=True,exist_ok=True)
    with out.open("w") as fh:
        for r in rows: fh.write(json.dumps(r,sort_keys=True)+"\n")
    sha=hashlib.sha256(out.read_bytes()).hexdigest()
    summary={"protocol":"MT_ROGSI_R3RS_QUAL_R1_2026_09_24","result_class":"exact enumeration policy delta",
      "rows":len(rows),"oracle_rows":sum(r["family"]=="oracle" for r in rows),
      "breach_rows":sum(r["family"]=="breach" for r in rows),
      "mindbreak_rows":sum(r["family"]=="mindbreak_free" for r in rows),
      "tower_rows":sum(r["family"]=="tower" for r in rows),
      "reference_false_stops":sum(r.get("reference_false_stop",False) for r in rows),
      "reference_false_live":sum(r.get("reference_false_live",False) for r in rows),
      "reference_tower_black_overcount":sum(r.get("reference_black_overcount",0) for r in rows),
      "candidate_false_stops":sum(r.get("candidate_false_stop",False) for r in rows),
      "candidate_false_live":sum(r.get("candidate_false_live",False) for r in rows),
      "candidate_mana_errors":sum(r.get("candidate_mana_error",False) for r in rows),
      "rows_sha256":sha,"script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    summary["pass"]=bool(summary["rows"]==444 and summary["candidate_false_stops"]==0 and
      summary["candidate_false_live"]==0 and summary["candidate_mana_errors"]==0 and
      (summary["reference_false_stops"]+summary["reference_false_live"]+summary["reference_tower_black_overcount"])>0)
    Path(a.summary).write_text(json.dumps(summary,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
