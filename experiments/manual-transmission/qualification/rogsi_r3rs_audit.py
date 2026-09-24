from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

def spell_legal(r,base):
    if r["grid"] and not r["grid_tax_affordable"]: return False
    spent=base+(3 if r["grid"] else 0)
    return not (r["bauble"] and spent==0 and not r["bauble_trigger_answered"])

def expected(r):
    f=r["family"]
    if f=="oracle":
        a=r["answer"]; w=r["window"]
        if a=="none": return False
        if a=="paid_counter": return w=="oracle_spell" and spell_legal(r,1)
        if a=="free_counter": return w=="oracle_spell" and spell_legal(r,0)
        if a=="hope_ender": return w=="oracle_spell" and spell_legal(r,2)
        if a=="tidebinder": return w=="oracle_etb" and spell_legal(r,3)
        return False
    if f=="breach":
        live=False if not r["breach_alive"] else (
          False if r["answer"]=="remove_breach" and (not r["grid"] or r["grid_tax_affordable"])
          else r["fuel_other_cards"]>=3)
        return live
    if f=="mindbreak_free":
        return bool(r["rogsi_spells_cast"]>=3 and spell_legal(r,0))
    if f=="tower":
        black=2 if r["sac_mode"] and r["creature_available"] else 0
        colorless=0 if r["sac_mode"] and r["creature_available"] else 1
        return black,colorless
    raise AssertionError(f)

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--out",required=True)
    a=ap.parse_args(); p=Path(a.rows); errors=[]; n=0; fam={}; refs=0
    for line in p.read_text().splitlines():
        if not line: continue
        r=json.loads(line); n+=1; fam[r["family"]]=fam.get(r["family"],0)+1
        e=expected(r)
        if r["family"]=="tower":
            b,c=e
            if r["truth_black"]!=b or r["candidate_black"]!=b or r["truth_colorless"]!=c or r["candidate_colorless"]!=c:
                errors.append([r["case_id"],"tower",b,c])
            if r.get("candidate_mana_error"): errors.append([r["case_id"],"candidate_mana_error"])
            refs+=r.get("reference_black_overcount",0)
        elif r["family"]=="breach":
            if r["truth_live"]!=e or r["candidate_live"]!=e: errors.append([r["case_id"],"breach",e])
            if r.get("candidate_false_stop") or r.get("candidate_false_live"): errors.append([r["case_id"],"candidate_error"])
            refs+=int(r.get("reference_false_stop",False) or r.get("reference_false_live",False))
        else:
            key="truth_stopped" if r["family"]=="oracle" else "truth_live"
            ckey="candidate_stopped" if r["family"]=="oracle" else "candidate_live"
            if r[key]!=e or r[ckey]!=e: errors.append([r["case_id"],r["family"],e])
            if r.get("candidate_false_stop") or r.get("candidate_false_live"): errors.append([r["case_id"],"candidate_error"])
            refs+=int(r.get("reference_false_stop",False) or r.get("reference_false_live",False))
    out={"protocol":"MT_ROGSI_R3RS_QUAL_R1_2026_09_24","audit":"independent row oracle",
      "result_class":"exact enumeration replay/audit","rows_recounted":n,"families":fam,
      "reference_error_signal_recounted":refs,"errors":len(errors),
      "rows_sha256":hashlib.sha256(p.read_bytes()).hexdigest(),
      "audit_script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
      "pass":n==444 and len(errors)==0 and refs>0,"error_examples":errors[:20]}
    Path(a.out).write_text(json.dumps(out,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
