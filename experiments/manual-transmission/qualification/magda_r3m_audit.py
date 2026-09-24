from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

def tide_works(life):
    return life in {"clean","removed_before_etb","removed_after_etb"}

def independent_truth(r):
    direct=(
        r["family"]=="clock_untap_activation"
        and r["permanent_answer"]!="none"
        and r["clock_target_removal_legal"]
        and r["permanent_answer_affordable"]
    )
    if direct:
        return True
    if not r["tidebinder_available"] or not tide_works(r["tidebinder_lifecycle"]):
        return False
    if not r["torpor_orb_active"]:
        return True
    return r["permanent_answer"]!="none" and r["orb_answer_then_tide_affordable"]

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--rows",required=True)
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    path=Path(args.rows)
    errors=[]; count=0; ref_false=0
    families={}
    for line in path.read_text().splitlines():
        if not line: continue
        r=json.loads(line); count+=1
        families[r["family"]]=families.get(r["family"],0)+1
        exp=independent_truth(r)
        if r["truth_current_activation_stopped"]!=exp:
            errors.append([r["case_id"],"truth",exp])
        if r["candidate_current_activation_stopped"]!=exp:
            errors.append([r["case_id"],"candidate",exp])
        if r["candidate_false_stop"] or r["candidate_false_live"]:
            errors.append([r["case_id"],"candidate_error",exp])
        if r["reference_false_stop"]: ref_false+=1
        if r["source_removal_is_current_activation_stop"]:
            errors.append([r["case_id"],"source_removal_mislabeled",False])
        if not r["permanent_answer_spent_at_most_once"]:
            errors.append([r["case_id"],"resource_reuse",False])
    result={
        "protocol":"MT_MAGDA_R3M_QUAL_R1_2026_09_24",
        "audit":"independent row oracle",
        "result_class":"exact enumeration replay/audit",
        "rows_recounted":count,
        "families":families,
        "reference_false_stops_recounted":ref_false,
        "errors":len(errors),
        "rows_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
        "audit_script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "pass":bool(len(errors)==0 and ref_false>0),
        "error_examples":errors[:20],
    }
    Path(args.out).write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")

if __name__=="__main__":
    main()
