from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path
COLORS="WUBRG"

def power(counts): return 2+sum(n>0 for n in counts)
def tide_stop(avail,life): return bool(avail and life in {"clean","removed_before_etb","removed_after_etb"})

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--out",required=True)
    a=ap.parse_args(); path=Path(a.rows); errors=[]; n=0; refs=0; fam={}
    for line in path.read_text().splitlines():
        if not line: continue
        r=json.loads(line); n+=1; fam[r["family"]]=fam.get(r["family"],0)+1
        if r["family"]=="activation_timing":
            exp=tide_stop(r["tidebinder_available"],r["tide_lifecycle"])
            if r["truth_activation_stopped"]!=exp: errors.append([r["case_id"],"truth_stop",exp])
            if r["candidate_activation_stopped"]!=exp: errors.append([r["case_id"],"candidate_stop",exp])
        else:
            counts=tuple(int(x) for x in r["support_counts"])
            before=power(counts); assert before==r["power_before"]
            if r["sisay_already_left"]:
                after=before
            else:
                out=list(counts)
                for c in r["removed_provider_colors"]:
                    i=COLORS.index(c); out[i]-=1
                after=power(tuple(out))
            ceiling=after-1
            excluded=bool(r["hypothetical_target_mv"]<=r["ceiling_before"] and r["hypothetical_target_mv"]>ceiling)
            downgrade=ceiling<r["ceiling_before"]
            if r["power_after"]!=after or r["ceiling_after"]!=ceiling: errors.append([r["case_id"],"ceiling",ceiling])
            if r["truth_downgrade"]!=downgrade or r["candidate_downgrade"]!=downgrade: errors.append([r["case_id"],"downgrade",downgrade])
            if r["truth_target_excluded"]!=excluded or r["candidate_target_excluded"]!=excluded: errors.append([r["case_id"],"target",excluded])
            if r["truth_activation_stopped"] or r["candidate_activation_stopped"]: errors.append([r["case_id"],"ceiling_not_full_stop"])
        if r["candidate_false_stop"] or r["candidate_false_live"]: errors.append([r["case_id"],"candidate_error"])
        if r["reference_false_stop"]: refs+=1
    out={"protocol":"MT_SISAY_R3SY_QUAL_R1_2026_09_24","audit":"independent row oracle",
      "result_class":"exact enumeration replay/audit","rows_recounted":n,"families":fam,
      "reference_false_stops_recounted":refs,"errors":len(errors),
      "rows_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
      "audit_script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
      "pass":len(errors)==0 and refs>0,"error_examples":errors[:20]}
    Path(a.out).write_text(json.dumps(out,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
