from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

def tide_live(humility,available,lifecycle):
    return bool(available and not humility and lifecycle in {"clean","removed_before_etb","removed_after_etb"})

def expected(r):
    if r["family"]=="scepter":
        return tide_live(r["humility"],r["tidebinder"],r["tide_lifecycle"])
    if r["family"]=="hullbreaker":
        if not r["trigger_exists"]: return False
        return tide_live(False,r["tidebinder"],r["tide_lifecycle"])
    raise AssertionError(r["family"])

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--out",required=True)
    a=ap.parse_args(); path=Path(a.rows); errors=[]; n=0; refs=0; fam={}
    for line in path.read_text().splitlines():
        if not line: continue
        r=json.loads(line); n+=1; fam[r["family"]]=fam.get(r["family"],0)+1
        exp=expected(r)
        if r["truth_current_stopped"]!=exp: errors.append([r["case_id"],"truth",exp])
        if r["candidate_current_stopped"]!=exp: errors.append([r["case_id"],"candidate",exp])
        if r["candidate_false_stop"] or r["candidate_false_live"]: errors.append([r["case_id"],"candidate_error"])
        if r["reference_false_stop"]: refs+=1
        if r["family"]=="hullbreaker" and r["trigger_exists"] and r["candidate_current_trigger_live"]!=(not exp):
            errors.append([r["case_id"],"trigger_live_mismatch"])
    out={"protocol":"MT_SHORIKAI_R3SH_QUAL_R1_2026_09_24","audit":"independent row oracle",
      "result_class":"exact enumeration replay/audit","rows_recounted":n,"families":fam,
      "reference_false_stops_recounted":refs,"errors":len(errors),
      "rows_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
      "audit_script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
      "pass":len(errors)==0 and refs>0,"error_examples":errors[:20]}
    Path(a.out).write_text(json.dumps(out,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
