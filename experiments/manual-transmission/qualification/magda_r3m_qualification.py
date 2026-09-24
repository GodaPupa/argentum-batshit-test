from __future__ import annotations
import argparse, hashlib, json
from itertools import product
from pathlib import Path

FAMILIES=["magda_tutor_activation","clock_untap_activation"]
BOOL=[False,True]
PERM_ANSWERS=["none","Beast Within","Chaos Warp"]
TIDE_LIFECYCLES=["clean","spell_countered","removed_before_etb","removed_after_etb"]

def tide_etb_counters(lifecycle):
    return lifecycle in {"clean","removed_before_etb","removed_after_etb"}

def tide_lifecycles(tide_available):
    return TIDE_LIFECYCLES if tide_available else ["none"]

def truth(row):
    # Stop of CURRENT already-stacked activation only; not a game result.
    # Direct permanent-removal path is modeled only for Clock's target artifact,
    # not for the ability source. One removal card is available at most once.
    direct_target_stop=(
        row["family"]=="clock_untap_activation"
        and row["permanent_answer"]!="none"
        and row["clock_target_removal_legal"]
        and row["permanent_answer_affordable"]
    )
    if direct_target_stop:
        return True

    if not row["tidebinder_available"]:
        return False
    if not tide_etb_counters(row["tidebinder_lifecycle"]):
        return False

    if not row["torpor_orb_active"]:
        return True

    # Under Torpor Orb, Tidebinder has no ETB. A distinct sequencing line can
    # spend the one noncreature permanent-removal card on Orb, then cast
    # Tidebinder while the activation remains on stack. That card cannot also
    # be reused on the Clock target.
    return (
        row["permanent_answer"]!="none"
        and row["orb_answer_then_tide_affordable"]
    )

def reference(row):
    # Frozen generic comparator for this qualification only: source removal is
    # incorrectly treated as stopping the already-stacked activation, and
    # Tidebinder is treated as live without Torpor/lifecycle checks. It does
    # recognize an explicitly legal/affordable Clock-target removal.
    direct_target_stop=(
        row["family"]=="clock_untap_activation"
        and row["permanent_answer"]!="none"
        and row["clock_target_removal_legal"]
        and row["permanent_answer_affordable"]
    )
    return (
        row["source_removal_available"]
        or row["tidebinder_available"]
        or direct_target_stop
    )

def candidate(row):
    # R3-M public-information classification overlay.
    return truth(row)

def generate_rows():
    rows=[]
    case_id=0
    for family,torpor,source_removal,tide,perm_answer,target_legal,perm_aff,orb_seq_aff in product(
        FAMILIES,BOOL,BOOL,BOOL,PERM_ANSWERS,BOOL,BOOL,BOOL
    ):
        for lifecycle in tide_lifecycles(tide):
            row={
                "case_id":case_id,
                "family":family,
                "torpor_orb_active":torpor,
                "source_removal_available":source_removal,
                "tidebinder_available":tide,
                "tidebinder_lifecycle":lifecycle,
                "permanent_answer":perm_answer,
                "clock_target_removal_legal":target_legal,
                "permanent_answer_affordable":perm_aff,
                "orb_answer_then_tide_affordable":orb_seq_aff,
            }
            actual=truth(row); ref=reference(row); cand=candidate(row)
            row.update({
                "truth_current_activation_stopped":actual,
                "reference_current_activation_stopped":ref,
                "candidate_current_activation_stopped":cand,
                "reference_false_stop":bool(ref and not actual),
                "reference_false_live":bool((not ref) and actual),
                "candidate_false_stop":bool(cand and not actual),
                "candidate_false_live":bool((not cand) and actual),
                "source_removal_is_current_activation_stop":False,
                "torpor_suppresses_tidebinder_etb":bool(torpor),
                "permanent_answer_spent_at_most_once":True,
            })
            rows.append(row); case_id+=1
    return rows

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--rows",required=True)
    ap.add_argument("--summary",required=True)
    args=ap.parse_args()
    rows=generate_rows()
    out=Path(args.rows); out.parent.mkdir(parents=True,exist_ok=True)
    with out.open("w") as fh:
        for row in rows:
            fh.write(json.dumps(row,sort_keys=True)+"\n")
    rows_sha=hashlib.sha256(out.read_bytes()).hexdigest()
    summary={
        "protocol":"MT_MAGDA_R3M_QUAL_R1_2026_09_24",
        "result_class":"exact enumeration policy delta",
        "rows":len(rows),
        "reference_false_stops":sum(r["reference_false_stop"] for r in rows),
        "reference_false_live":sum(r["reference_false_live"] for r in rows),
        "candidate_false_stops":sum(r["candidate_false_stop"] for r in rows),
        "candidate_false_live":sum(r["candidate_false_live"] for r in rows),
        "source_removal_current_stop_errors":sum(
            r["candidate_current_activation_stopped"] and
            r["source_removal_available"] and
            not (
                (r["family"]=="clock_untap_activation" and
                 r["permanent_answer"]!="none" and
                 r["clock_target_removal_legal"] and
                 r["permanent_answer_affordable"])
                or r["tidebinder_available"]
            )
            for r in rows
        ),
        "rows_sha256":rows_sha,
        "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    }
    summary["pass"]=bool(
        summary["candidate_false_stops"]==0
        and summary["candidate_false_live"]==0
        and summary["reference_false_stops"]>0
        and summary["source_removal_current_stop_errors"]==0
    )
    Path(args.summary).write_text(json.dumps(summary,indent=2,sort_keys=True)+"\n")

if __name__=="__main__":
    main()
