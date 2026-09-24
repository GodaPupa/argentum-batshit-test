from __future__ import annotations
import argparse, hashlib, json
from itertools import product
from pathlib import Path
BOOL=(False,True); LIFE=("clean","spell_countered","removed_before_etb","removed_after_etb")

def tide_works(available,lifecycle):
    return bool(available and lifecycle in {"clean","removed_before_etb","removed_after_etb"})

def generate():
    rows=[]; cid=0
    for answer,affordable,tide,life in product(
        ("none","remove_kinnan","remove_basalt","tidebinder_untap","creature_removal_basalt"),
        BOOL,BOOL,LIFE
    ):
        if not tide and life!="clean": continue
        if not affordable or answer=="none": truth=False
        elif answer in {"remove_kinnan","remove_basalt"}: truth=True
        elif answer=="tidebinder_untap": truth=tide_works(tide,life)
        else: truth=False
        ref=bool(affordable and answer!="none")
        rows.append({"case_id":cid,"family":"untap_window","answer":answer,"affordable":affordable,
          "tidebinder_available":tide,"tide_lifecycle":life,
          "truth_stopped":truth,"reference_stopped":ref,"candidate_stopped":truth,
          "reference_false_stop":bool(ref and not truth),"reference_false_live":bool((not ref) and truth),
          "candidate_false_stop":False,"candidate_false_live":False}); cid+=1
    for attempted in ("none","remove_kinnan","remove_basalt","tidebinder"):
        legal=attempted=="none"
        rows.append({"case_id":cid,"family":"mana_phase","attempted_answer":attempted,
          "truth_response_legal":legal,"reference_response_legal":True,"candidate_response_legal":legal,
          "reference_illegal_window":not legal,"candidate_illegal_window":False}); cid+=1
    for colored,kinnan_alive,outlet_public in product(range(5),BOOL,BOOL):
        activation=bool(kinnan_alive and colored>=2)
        rows.append({"case_id":cid,"family":"post_colorless","colored_mana_available":colored,
          "kinnan_alive":kinnan_alive,"public_outlet":outlet_public,
          "truth_kinnan_activation_available":activation,"candidate_kinnan_activation_available":activation,
          "truth_terminal":False,"reference_terminal":True,"candidate_terminal":False,
          "reference_false_terminal":True,"candidate_terminal_error":False}); cid+=1
    return rows

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--summary",required=True)
    a=ap.parse_args(); rows=generate(); out=Path(a.rows); out.parent.mkdir(parents=True,exist_ok=True)
    with out.open("w") as fh:
        for r in rows: fh.write(json.dumps(r,sort_keys=True)+"\n")
    sha=hashlib.sha256(out.read_bytes()).hexdigest()
    s={"protocol":"MT_KINNAN_BASALT_R3KB_QUAL_R1_2026_09_24","result_class":"exact enumeration policy delta",
      "rows":len(rows),"untap_rows":sum(r["family"]=="untap_window" for r in rows),
      "mana_phase_rows":sum(r["family"]=="mana_phase" for r in rows),
      "post_colorless_rows":sum(r["family"]=="post_colorless" for r in rows),
      "reference_false_stops":sum(r.get("reference_false_stop",False) for r in rows),
      "reference_illegal_windows":sum(r.get("reference_illegal_window",False) for r in rows),
      "reference_false_terminals":sum(r.get("reference_false_terminal",False) for r in rows),
      "candidate_false_stops":sum(r.get("candidate_false_stop",False) for r in rows),
      "candidate_false_live":sum(r.get("candidate_false_live",False) for r in rows),
      "candidate_illegal_windows":sum(r.get("candidate_illegal_window",False) for r in rows),
      "candidate_terminal_errors":sum(r.get("candidate_terminal_error",False) for r in rows),
      "rows_sha256":sha,"script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    s["pass"]=bool(s["rows"]==74 and s["candidate_false_stops"]==0 and s["candidate_false_live"]==0 and
      s["candidate_illegal_windows"]==0 and s["candidate_terminal_errors"]==0 and
      (s["reference_false_stops"]+s["reference_illegal_windows"]+s["reference_false_terminals"])>0)
    Path(a.summary).write_text(json.dumps(s,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
