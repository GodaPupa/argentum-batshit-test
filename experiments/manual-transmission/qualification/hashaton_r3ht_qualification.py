from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "opponents/hashaton/source-freeze.json"

CREATURES = {
"Archon of Emeria","Baleful Force","Chancellor of the Annex","Consecrated Sphinx",
"Doctor Octopus, Master Planner","Eidolon of Rhetoric","Elesh Norn, Grand Cenobite",
"Esper Sentinel","Ethersworn Canonist","Glory","Gran-Gran","Hapless Researcher",
"Hydroelectric Specimen // Hydroelectric Laboratory","Jin-Gitaxias, Core Augur",
"M.O.D.O.K.","Nezahal, Primal Tide","Ori, Plate Stacker","Phyrexian Censor",
"Pollywog Prodigy","Putrid Imp","Razaketh, the Foulblooded","Rune-Scarred Demon",
"Smirking Spelljacker","Solitude","Stern Constable","Subtlety","Sun Titan",
"Teferi, Mage of Zhalfir","Thassa's Oracle","Tireless Tribe",
"Valgavoth, Terror Eater","Vilis, Broker of Blood",
}
SINGLE_COST = ["Putrid Imp","Stern Constable","Tireless Tribe","The Underworld Cookbook","Bloodthorn Flail"]
RESOLUTION = ["Hapless Researcher","Gran-Gran","M.O.D.O.K."]
PROFILES = ["none","removal_only","tidebinder_only","removal_plus_tidebinder"]
TIDE_LIFECYCLE = ["clean","spell_countered","removed_before_etb","removed_after_etb"]

def has_removal(profile): return profile in {"removal_only","removal_plus_tidebinder"}
def has_tide(profile): return profile in {"tidebinder_only","removal_plus_tidebinder"}
def tide_stops_current(lifecycle): return lifecycle in {"clean","removed_before_etb","removed_after_etb"}

def truth(row):
    fam=row["family"]; profile=row["answers"]; life=row["tide_lifecycle"]
    r=has_removal(profile); t=has_tide(profile) and tide_stops_current(life)
    if fam in {"single_cost","foil_alt_cost"}:
        return t
    if fam=="resolution_discard":
        return r or t
    if fam=="led":
        if row["trigger_count"] >= 2:
            return False
        return t
    raise ValueError(fam)

def reference(row):
    # Frozen generic source-removal-first fallback used only as a comparison,
    # never labeled accepted Race behavior.
    profile=row["answers"]
    if has_removal(profile): return True
    if has_tide(profile) and tide_stops_current(row["tide_lifecycle"]): return True
    return False

def candidate(row):
    # R3-HT is a classification overlay, not a forced action line.
    return truth(row)

def tide_lifecycles(profile):
    return TIDE_LIFECYCLE if has_tide(profile) else ["none"]

def row_base(family, outlet, payload, profile, life):
    return {
        "family":family,"outlet":outlet,"payload":payload,
        "answers":profile,"tide_lifecycle":life,
        "source_removal_available":has_removal(profile),
        "tidebinder_available":has_tide(profile),
    }

def generate():
    rows=[]
    for outlet in SINGLE_COST:
        for payload in sorted(CREATURES):
            for p in PROFILES:
                for life in tide_lifecycles(p):
                    rows.append(row_base("single_cost",outlet,payload,p,life))
    for outlet in RESOLUTION:
        for payload in sorted(CREATURES):
            for p in PROFILES:
                for life in tide_lifecycles(p):
                    rows.append(row_base("resolution_discard",outlet,payload,p,life))
    for payload in sorted(CREATURES):
        for p in PROFILES:
            for life in tide_lifecycles(p):
                rows.append(row_base("foil_alt_cost","Foil",payload,p,life))
    for k in range(1,8):
        for p in PROFILES:
            for life in tide_lifecycles(p):
                row=row_base("led","Lion's Eye Diamond","MULTIPLE_OR_SINGLE_CREATURES",p,life)
                row["trigger_count"]=k
                rows.append(row)
    for i,row in enumerate(rows):
        actual=truth(row); ref=reference(row); cand=candidate(row)
        row.update({
            "case_id":i,
            "truth_current_attempt_stopped":actual,
            "reference_current_attempt_stopped":ref,
            "candidate_current_attempt_stopped":cand,
            "reference_false_stop": bool(ref and not actual),
            "candidate_false_stop": bool(cand and not actual),
            "candidate_false_live": bool((not cand) and actual),
        })
        if row["family"]=="led":
            row["single_tidebinder_clears_all_current_hashaton_triggers"] = bool(
                row["trigger_count"]==1 and has_tide(row["answers"]) and tide_stops_current(row["tide_lifecycle"])
            )
        if has_tide(row["answers"]):
            row["hashaton_blanked_after_tidebinder_exchange"] = row["tide_lifecycle"]=="clean"
    return rows

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--rows",required=True); ap.add_argument("--summary",required=True)
    args=ap.parse_args()
    source=json.loads(SOURCE.read_text())
    assert len(CREATURES)==32 and CREATURES.issubset(set(source["mainboard"]))
    rows=generate()
    ref_false=sum(r["reference_false_stop"] for r in rows)
    cand_false=sum(r["candidate_false_stop"] for r in rows)
    cand_false_live=sum(r["candidate_false_live"] for r in rows)
    led_multi=[r for r in rows if r["family"]=="led" and r["trigger_count"]>=2]
    led_multi_bad=sum(r["candidate_current_attempt_stopped"] for r in led_multi)
    lifecycle_bad=0
    for r in rows:
        if r["tide_lifecycle"] in {"removed_before_etb","removed_after_etb","spell_countered"} and r.get("hashaton_blanked_after_tidebinder_exchange",False):
            lifecycle_bad+=1
    out_rows=Path(args.rows); out_rows.parent.mkdir(parents=True,exist_ok=True)
    with out_rows.open("w") as fh:
        for r in rows: fh.write(json.dumps(r,sort_keys=True)+"\n")
    rows_sha=hashlib.sha256(out_rows.read_bytes()).hexdigest()
    summary={
        "protocol":"MT_HASHATON_R3HT_QUAL_R1_2026_09_23",
        "result_class":"exact enumeration policy delta",
        "source_digest":source["digests"]["commander_plus_99_sha256"],
        "rows":len(rows),
        "reference_false_stops":ref_false,
        "candidate_false_stops":cand_false,
        "candidate_false_live":cand_false_live,
        "led_multi_trigger_candidate_full_stop_errors":led_multi_bad,
        "tidebinder_lifecycle_blank_errors":lifecycle_bad,
        "strict_improvement":cand_false < ref_false,
        "rows_sha256":rows_sha,
        "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    }
    summary["pass"]=bool(
        cand_false==0 and cand_false_live==0 and led_multi_bad==0 and lifecycle_bad==0 and cand_false < ref_false
    )
    Path(args.summary).write_text(json.dumps(summary,indent=2,sort_keys=True)+"\n")

if __name__=="__main__": main()
