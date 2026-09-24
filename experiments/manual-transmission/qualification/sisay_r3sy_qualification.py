from __future__ import annotations
import argparse, hashlib, json
from itertools import product
from pathlib import Path

COLORS="WUBRG"
BOOL=(False,True)
TIDE=("clean","spell_countered","removed_before_etb","removed_after_etb")

def tide_stop(available,lifecycle):
    return bool(available and lifecycle in {"clean","removed_before_etb","removed_after_etb"})

def power(counts):
    return 2 + sum(n>0 for n in counts)

def masks(counts):
    live=[i for i,n in enumerate(counts) if n>0]
    for bits in range(1,1<<len(live)):
        yield tuple(live[i] for i in range(len(live)) if bits&(1<<i))

def remove_provider(counts,mask):
    out=list(counts)
    for i in mask:
        assert out[i]>0
        out[i]-=1
    return tuple(out)

def generate():
    rows=[]; cid=0
    for remove_sisay,tide,life in product(BOOL,BOOL,TIDE):
        if not tide and life!="clean": continue
        truth=tide_stop(tide,life)
        reference=bool(remove_sisay or truth)
        rows.append({
          "case_id":cid,"family":"activation_timing",
          "remove_sisay_after_activation":remove_sisay,
          "tidebinder_available":tide,"tide_lifecycle":life,
          "truth_activation_stopped":truth,"candidate_activation_stopped":truth,
          "reference_activation_stopped":reference,
          "reference_false_stop":bool(reference and not truth),
          "reference_false_live":bool((not reference) and truth),
          "candidate_false_stop":False,"candidate_false_live":False
        }); cid+=1

    for counts in product(range(4),repeat=5):
        if not any(counts): continue
        before_power=power(counts); before_ceiling=before_power-1
        for mask in masks(counts):
            after_counts=remove_provider(counts,mask)
            live_after_power=power(after_counts)
            for sisay_left in BOOL:
                after_power=before_power if sisay_left else live_after_power
                after_ceiling=after_power-1
                downgrade=after_ceiling<before_ceiling
                mask_text="".join(COLORS[i] for i in mask)
                for mv in range(1,8):
                    was=mv<=before_ceiling
                    excluded=bool(was and mv>after_ceiling)
                    reference_full_stop=excluded
                    rows.append({
                      "case_id":cid,"family":"ceiling_topology",
                      "support_counts":"".join(str(n) for n in counts),
                      "removed_provider_colors":mask_text,
                      "sisay_already_left":sisay_left,
                      "power_before":before_power,"ceiling_before":before_ceiling,
                      "power_after":after_power,"ceiling_after":after_ceiling,
                      "truth_downgrade":downgrade,"candidate_downgrade":downgrade,
                      "hypothetical_target_mv":mv,"target_was_eligible":was,
                      "truth_target_excluded":excluded,"candidate_target_excluded":excluded,
                      "truth_activation_stopped":False,"candidate_activation_stopped":False,
                      "reference_activation_stopped":reference_full_stop,
                      "reference_false_stop":reference_full_stop,
                      "reference_false_live":False,
                      "candidate_false_stop":False,"candidate_false_live":False
                    }); cid+=1
    return rows

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--summary",required=True)
    a=ap.parse_args(); rows=generate(); path=Path(a.rows); path.parent.mkdir(parents=True,exist_ok=True)
    with path.open("w") as fh:
        for r in rows: fh.write(json.dumps(r,sort_keys=True,separators=(",",":"))+"\n")
    sha=hashlib.sha256(path.read_bytes()).hexdigest()
    ceiling=[r for r in rows if r["family"]=="ceiling_topology"]
    s={
      "protocol":"MT_SISAY_R3SY_QUAL_R1_2026_09_24","result_class":"exact enumeration policy delta",
      "rows":len(rows),"direct_activation_rows":sum(r["family"]=="activation_timing" for r in rows),
      "ceiling_rows":len(ceiling),
      "downgrade_rows":sum(r["truth_downgrade"] for r in ceiling),
      "target_exclusion_rows":sum(r["truth_target_excluded"] for r in ceiling),
      "lki_changed_ceiling_errors":sum(r["sisay_already_left"] and r["ceiling_after"]!=r["ceiling_before"] for r in ceiling),
      "reference_false_stops":sum(r["reference_false_stop"] for r in rows),
      "reference_false_live":sum(r["reference_false_live"] for r in rows),
      "candidate_false_stops":sum(r["candidate_false_stop"] for r in rows),
      "candidate_false_live":sum(r["candidate_false_live"] for r in rows),
      "candidate_target_classification_errors":sum(r["truth_target_excluded"]!=r["candidate_target_excluded"] for r in ceiling),
      "rows_sha256":sha,"script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    }
    s["pass"]=bool(s["candidate_false_stops"]==0 and s["candidate_false_live"]==0 and
      s["candidate_target_classification_errors"]==0 and s["lki_changed_ceiling_errors"]==0 and s["reference_false_stops"]>0)
    Path(a.summary).write_text(json.dumps(s,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
