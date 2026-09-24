from __future__ import annotations
from itertools import product
import hashlib, json
from pathlib import Path

FAMILIES = ["magda_tutor_activation", "clock_untap_activation"]
TORPOR = [False, True]
BOOL = [False, True]

def truth(row):
    # This development screen classifies whether the CURRENT already-stacked
    # activation is stopped by the modeled public/private-to-MT resources.
    # It is not a game result or a matchup result.
    tide = row["tidebinder_available"]
    if not tide:
        return False
    if not row["torpor_orb_active"]:
        return True
    # Torpor Orb suppresses Tidebinder's ETB. The current activation can still
    # be answered by the modeled two-layer line only when MT has a legal
    # noncreature Orb answer and can afford Orb-answer -> Tidebinder before the
    # activation resolves.
    return (
        row["orb_noncreature_answer_available"]
        and row["orb_answer_then_tide_affordable"]
    )

def generic_source_removal_reference(row):
    # Deliberately naive DEVELOPMENT comparator: it treats source removal as a
    # stop and treats Tidebinder as live without respecting Torpor Orb.
    return row["source_removal_available"] or row["tidebinder_available"]

def candidate_r3_m(row):
    # Candidate timing overlay uses only public board/stack state and MT's own
    # known hand/resources.
    return truth(row)

def main():
    rows=[]
    for family, torpor, source_removal, tide, orb_answer, sequence_affordable in product(
        FAMILIES, TORPOR, BOOL, BOOL, BOOL, BOOL
    ):
        row={
            "family":family,
            "torpor_orb_active":torpor,
            "source_removal_available":source_removal,
            "tidebinder_available":tide,
            "orb_noncreature_answer_available":orb_answer,
            "orb_answer_then_tide_affordable":sequence_affordable,
        }
        actual=truth(row)
        ref=generic_source_removal_reference(row)
        cand=candidate_r3_m(row)
        row.update({
            "truth_current_activation_stopped":actual,
            "reference_current_activation_stopped":ref,
            "candidate_current_activation_stopped":cand,
            "reference_false_stop":bool(ref and not actual),
            "reference_false_live":bool((not ref) and actual),
            "candidate_false_stop":bool(cand and not actual),
            "candidate_false_live":bool((not cand) and actual),
        })
        rows.append(row)

    result={
        "protocol":"MT_MAGDA_POLICY_DEVELOPMENT_R1_2026_09_24",
        "status":"DEVELOPMENT_ONLY",
        "result_class":"exact enumeration policy development",
        "qualification_eligible":False,
        "cells":len(rows),
        "reference_false_stops":sum(r["reference_false_stop"] for r in rows),
        "reference_false_live":sum(r["reference_false_live"] for r in rows),
        "candidate_false_stops":sum(r["candidate_false_stop"] for r in rows),
        "candidate_false_live":sum(r["candidate_false_live"] for r in rows),
        "candidate_rule":{
            "name":"R3-M (development candidate)",
            "text":[
                "Once a public Magda tutor activation is on the stack, removing Magda does not erase it; classify Tidebinder as the direct current-activation answer when its ETB is live.",
                "Once a public Clock of Omens activation is on the stack, removing Clock does not erase it; classify Tidebinder as the direct current-activation answer when its ETB is live.",
                "If Torpor Orb is active, do not count Tidebinder as an answer by itself because its ETB trigger is suppressed.",
                "If Torpor Orb is active and MT has Beast Within or Chaos Warp plus Tidebinder and can legally afford the full sequence before the current activation resolves, remove Orb first, then Tidebinder may answer the still-stacked activation.",
                "Do not treat paid Magda Treasure costs or tapped Clock cost artifacts as refunded when the activation is countered."
            ],
            "hidden_opponent_information_required":False
        },
        "rows":rows,
        "random_seeds_used":0,
        "qualification_outcomes_exposed":0,
        "notes":[
            "Source removal means removal of the ability source, not removal of Clock's target artifact.",
            "This screen does not enumerate every possible way to answer an artifact target or every Magda line.",
            "The candidate is a timing/classification overlay only, not a forced action sequence.",
            "No win rate, matchup rate, or full-game result is reported."
        ],
        "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    }
    print(json.dumps(result,indent=2,sort_keys=True))

if __name__=="__main__":
    main()
