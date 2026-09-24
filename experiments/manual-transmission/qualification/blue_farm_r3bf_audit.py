from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

def castable(lock,kind):
    if lock in {"silence","orims_chant_mt","voice_blue_turn"}: return False
    return not (lock=="ranger" and kind=="noncreature")

def expected(r):
    if r["family"]=="oracle":
        if not r["affordable"] or r["answer"]=="none": return False
        if r["window"]=="oracle_spell":
            if r["answer"]=="noncreature_counter": return castable(r["lock"],"noncreature")
            if r["answer"]=="hope_ender": return castable(r["lock"],"creature")
            return False
        return r["answer"]=="tidebinder" and castable(r["lock"],"creature")
    if r["family"]=="breach":
        live=bool(r["breach_alive"] and not (r["answer"]=="remove_breach" and r["affordable"]) and r["fuel_other_cards"]>=3)
        return not live
    raise AssertionError(r["family"])

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--out",required=True)
    a=ap.parse_args(); path=Path(a.rows); errors=[]; n=0; refs=0
    fam={}
    for line in path.read_text().splitlines():
        if not line: continue
        r=json.loads(line); n+=1; fam[r["family"]]=fam.get(r["family"],0)+1
        exp=expected(r)
        if r["truth_stopped"]!=exp: errors.append([r["case_id"],"truth",exp])
        if r["candidate_stopped"]!=exp: errors.append([r["case_id"],"candidate",exp])
        if r["candidate_false_stop"] or r["candidate_false_live"]: errors.append([r["case_id"],"candidate_error"])
        if r["reference_false_stop"]: refs+=1
        if r["family"]=="breach" and r["candidate_rebuild_live"]==(not exp) is False:
            errors.append([r["case_id"],"breach_live_mismatch"])
    out={"protocol":"MT_BLUE_FARM_R3BF_QUAL_R1_2026_09_24","audit":"independent row oracle",
      "result_class":"exact enumeration replay/audit","rows_recounted":n,"families":fam,
      "reference_false_stops_recounted":refs,"errors":len(errors),
      "rows_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
      "audit_script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
      "pass":len(errors)==0 and refs>0,"error_examples":errors[:20]}
    Path(a.out).write_text(json.dumps(out,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
