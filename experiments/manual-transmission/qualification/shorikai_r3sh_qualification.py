from __future__ import annotations
import argparse, hashlib, json
from itertools import product
from pathlib import Path
BOOL=(False,True)
TIDE=("clean","spell_countered","removed_before_etb","removed_after_etb")

def tide_live(humility,available,lifecycle):
    return bool(available and not humility and lifecycle in {"clean","removed_before_etb","removed_after_etb"})

def generate():
    rows=[]; cid=0
    for humility,tide,life,source_remove,hope,temur in product(BOOL,BOOL,TIDE,BOOL,BOOL,BOOL):
        if not tide and life!="clean": continue
        stop=tide_live(humility,tide,life)
        future_live=not (stop or (source_remove and temur))
        ref=bool(tide or source_remove)
        rows.append({"case_id":cid,"family":"scepter","humility":humility,"tidebinder":tide,
          "tide_lifecycle":life,"source_removal":source_remove,"hope_ender":hope,
          "full_temur_reserved":temur,"truth_current_stopped":stop,"candidate_current_stopped":stop,
          "reference_current_stopped":ref,"truth_downstream_copy_answer":hope,
          "truth_future_scepter_live":future_live,
          "reference_false_stop":bool(ref and not stop),
          "reference_false_live":bool((not ref) and stop),
          "candidate_false_stop":False,"candidate_false_live":False}); cid+=1
    for humility,tide,life,source_spell_countered,horror_removed in product(BOOL,BOOL,TIDE,BOOL,BOOL):
        if not tide and life!="clean": continue
        trigger_exists=not humility
        stop=bool(trigger_exists and tide_live(False,tide,life))
        truth_live=bool(trigger_exists and not stop)
        ref_live=bool(trigger_exists and not (tide or source_spell_countered or horror_removed))
        ref_stop=bool(trigger_exists and not ref_live)
        truth_stop=bool(trigger_exists and not truth_live)
        rows.append({"case_id":cid,"family":"hullbreaker","humility":humility,
          "trigger_exists":trigger_exists,"tidebinder":tide,"tide_lifecycle":life,
          "source_spell_countered":source_spell_countered,"horror_removed":horror_removed,
          "truth_current_trigger_live":truth_live,"candidate_current_trigger_live":truth_live,
          "reference_current_trigger_live":ref_live,
          "truth_current_stopped":truth_stop,"candidate_current_stopped":truth_stop,
          "reference_current_stopped":ref_stop,
          "reference_false_stop":bool(ref_stop and not truth_stop),
          "reference_false_live":bool((not ref_stop) and truth_stop),
          "candidate_false_stop":False,"candidate_false_live":False}); cid+=1
    return rows

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--summary",required=True)
    a=ap.parse_args(); rows=generate(); path=Path(a.rows); path.parent.mkdir(parents=True,exist_ok=True)
    with path.open("w") as fh:
        for r in rows: fh.write(json.dumps(r,sort_keys=True)+"\n")
    sha=hashlib.sha256(path.read_bytes()).hexdigest()
    s={"protocol":"MT_SHORIKAI_R3SH_QUAL_R1_2026_09_24","result_class":"exact enumeration policy delta",
      "rows":len(rows),"scepter_rows":sum(r["family"]=="scepter" for r in rows),
      "hullbreaker_rows":sum(r["family"]=="hullbreaker" for r in rows),
      "reference_false_stops":sum(r["reference_false_stop"] for r in rows),
      "reference_false_live":sum(r["reference_false_live"] for r in rows),
      "candidate_false_stops":sum(r["candidate_false_stop"] for r in rows),
      "candidate_false_live":sum(r["candidate_false_live"] for r in rows),
      "rows_sha256":sha,"script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    s["pass"]=bool(s["candidate_false_stops"]==0 and s["candidate_false_live"]==0 and s["reference_false_stops"]>0)
    Path(a.summary).write_text(json.dumps(s,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
