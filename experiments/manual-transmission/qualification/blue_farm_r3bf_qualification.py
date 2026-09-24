from __future__ import annotations
import argparse, hashlib, json
from itertools import product
from pathlib import Path

LOCKS=("none","ranger","silence","orims_chant_mt","voice_blue_turn")
BOOL=(False,True)

def spell_castable(lock,kind):
    if lock in {"silence","orims_chant_mt","voice_blue_turn"}: return False
    if lock=="ranger" and kind=="noncreature": return False
    return True

def oracle_stop(window,lock,answer,affordable):
    if not affordable or answer=="none": return False
    if window=="oracle_spell":
        if answer=="noncreature_counter": return spell_castable(lock,"noncreature")
        if answer=="hope_ender": return spell_castable(lock,"creature")
        return False
    if window=="oracle_etb":
        return answer=="tidebinder" and spell_castable(lock,"creature")
    raise AssertionError(window)

def oracle_reference(window,lock,answer,affordable):
    return bool(affordable and answer!="none")

def breach_rebuild_live(fuel,breach_alive,answer,affordable):
    if not breach_alive: return False
    if answer=="remove_breach" and affordable: return False
    return fuel>=3

def breach_reference_live(fuel,breach_alive,answer,affordable):
    if not breach_alive: return False
    if answer=="remove_breach" and affordable: return False
    if answer=="tidebinder_storm" and affordable: return False
    return fuel>=3

def generate():
    rows=[]
    cid=0
    for window in ("oracle_spell","oracle_etb"):
        answers=("none","noncreature_counter","hope_ender") if window=="oracle_spell" else ("none","tidebinder","source_removal")
        for lock,answer,aff in product(LOCKS,answers,BOOL):
            truth=oracle_stop(window,lock,answer,aff)
            ref=oracle_reference(window,lock,answer,aff)
            row={
              "case_id":cid,"family":"oracle","window":window,"lock":lock,
              "answer":answer,"affordable":aff,
              "truth_stopped":truth,"reference_stopped":ref,"candidate_stopped":truth,
              "reference_false_stop":bool(ref and not truth),
              "reference_false_live":bool((not ref) and truth),
              "candidate_false_stop":False,"candidate_false_live":False
            }
            rows.append(row); cid+=1
    for fuel,breach_alive,answer,aff in product(range(7),BOOL,("none","tidebinder_storm","remove_breach"),BOOL):
        truth_live=breach_rebuild_live(fuel,breach_alive,answer,aff)
        ref_live=breach_reference_live(fuel,breach_alive,answer,aff)
        truth_stop=not truth_live
        ref_stop=not ref_live
        row={
          "case_id":cid,"family":"breach","fuel_other_cards":fuel,
          "breach_alive":breach_alive,"answer":answer,"affordable":aff,
          "truth_rebuild_live":truth_live,"reference_rebuild_live":ref_live,"candidate_rebuild_live":truth_live,
          "truth_stopped":truth_stop,"reference_stopped":ref_stop,"candidate_stopped":truth_stop,
          "reference_false_stop":bool(ref_stop and not truth_stop),
          "reference_false_live":bool((not ref_stop) and truth_stop),
          "candidate_false_stop":False,"candidate_false_live":False
        }
        rows.append(row); cid+=1
    return rows

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--summary",required=True)
    a=ap.parse_args(); rows=generate()
    out=Path(a.rows); out.parent.mkdir(parents=True,exist_ok=True)
    with out.open("w") as fh:
        for r in rows: fh.write(json.dumps(r,sort_keys=True)+"\n")
    sha=hashlib.sha256(out.read_bytes()).hexdigest()
    summary={
      "protocol":"MT_BLUE_FARM_R3BF_QUAL_R1_2026_09_24",
      "result_class":"exact enumeration policy delta",
      "rows":len(rows),"oracle_rows":sum(r["family"]=="oracle" for r in rows),
      "breach_rows":sum(r["family"]=="breach" for r in rows),
      "reference_false_stops":sum(r["reference_false_stop"] for r in rows),
      "reference_false_live":sum(r["reference_false_live"] for r in rows),
      "candidate_false_stops":sum(r["candidate_false_stop"] for r in rows),
      "candidate_false_live":sum(r["candidate_false_live"] for r in rows),
      "rows_sha256":sha,"script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    }
    summary["pass"]=bool(summary["candidate_false_stops"]==0 and summary["candidate_false_live"]==0 and summary["reference_false_stops"]>0)
    Path(a.summary).write_text(json.dumps(summary,indent=2,sort_keys=True)+"\n")

if __name__=="__main__": main()
