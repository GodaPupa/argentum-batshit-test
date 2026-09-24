from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

def hr(profile): return "removal" in profile
def ht(profile): return "tidebinder" in profile
def tide_works(life): return life in {"clean","removed_before_etb","removed_after_etb"}

def independent_expected(r):
    rem=hr(r["answers"]); tide=ht(r["answers"]) and tide_works(r["tide_lifecycle"])
    if r["family"]=="resolution_discard": return rem or tide
    if r["family"] in {"single_cost","foil_alt_cost"}: return tide
    if r["family"]=="led": return tide if r["trigger_count"]==1 else False
    raise AssertionError(r["family"])

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--out",required=True)
    a=ap.parse_args()
    path=Path(a.rows)
    errors=[]; count=0; ref_false=0
    led_multi=0
    for line in path.read_text().splitlines():
        if not line: continue
        r=json.loads(line); count+=1
        exp=independent_expected(r)
        if r["truth_current_attempt_stopped"] != exp: errors.append([r["case_id"],"truth",exp])
        if r["candidate_current_attempt_stopped"] != exp: errors.append([r["case_id"],"candidate",exp])
        if r["candidate_false_stop"] or r["candidate_false_live"]: errors.append([r["case_id"],"candidate_error",exp])
        if r["reference_false_stop"]: ref_false+=1
        if r["family"]=="led" and r["trigger_count"]>=2 and r["candidate_current_attempt_stopped"]:
            led_multi+=1; errors.append([r["case_id"],"led_multi_false_full_stop",False])
        if r.get("hashaton_blanked_after_tidebinder_exchange",False) and r["tide_lifecycle"]!="clean":
            errors.append([r["case_id"],"lifecycle_blank_error",False])
    result={
        "protocol":"MT_HASHATON_R3HT_QUAL_R1_2026_09_23",
        "audit":"independent row oracle",
        "result_class":"exact enumeration replay/audit",
        "rows_recounted":count,
        "reference_false_stops_recounted":ref_false,
        "errors":len(errors),
        "led_multi_false_full_stops":led_multi,
        "rows_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
        "audit_script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "pass":len(errors)==0 and ref_false>0,
        "error_examples":errors[:20],
    }
    Path(a.out).write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")

if __name__=="__main__": main()
