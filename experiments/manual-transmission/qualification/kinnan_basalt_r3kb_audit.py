from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

def expected_untap(r):
    if not r["affordable"] or r["answer"]=="none": return False
    if r["answer"] in {"remove_kinnan","remove_basalt"}: return True
    if r["answer"]=="tidebinder_untap":
        return bool(r["tidebinder_available"] and r["tide_lifecycle"] in {"clean","removed_before_etb","removed_after_etb"})
    return False

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--rows",required=True); ap.add_argument("--out",required=True)
    a=ap.parse_args(); p=Path(a.rows); errors=[]; n=0; fam={}; refs=0
    for line in p.read_text().splitlines():
        if not line: continue
        r=json.loads(line); n+=1; fam[r["family"]]=fam.get(r["family"],0)+1
        if r["family"]=="untap_window":
            e=expected_untap(r)
            if r["truth_stopped"]!=e or r["candidate_stopped"]!=e: errors.append([r["case_id"],"untap",e])
            if r.get("candidate_false_stop") or r.get("candidate_false_live"): errors.append([r["case_id"],"candidate_error"])
            refs+=int(r.get("reference_false_stop",False) or r.get("reference_false_live",False))
        elif r["family"]=="mana_phase":
            e=r["attempted_answer"]=="none"
            if r["truth_response_legal"]!=e or r["candidate_response_legal"]!=e: errors.append([r["case_id"],"mana_phase",e])
            if r.get("candidate_illegal_window"): errors.append([r["case_id"],"candidate_illegal_window"])
            refs+=int(r.get("reference_illegal_window",False))
        else:
            e=bool(r["kinnan_alive"] and r["colored_mana_available"]>=2)
            if r["truth_kinnan_activation_available"]!=e or r["candidate_kinnan_activation_available"]!=e:
                errors.append([r["case_id"],"post_colorless_activation",e])
            if r["truth_terminal"] or r["candidate_terminal"] or r.get("candidate_terminal_error"):
                errors.append([r["case_id"],"false_terminal"])
            refs+=int(r.get("reference_false_terminal",False))
    out={"protocol":"MT_KINNAN_BASALT_R3KB_QUAL_R1_2026_09_24","audit":"independent row oracle",
      "result_class":"exact enumeration replay/audit","rows_recounted":n,"families":fam,
      "reference_error_signal_recounted":refs,"errors":len(errors),
      "rows_sha256":hashlib.sha256(p.read_bytes()).hexdigest(),
      "audit_script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
      "pass":n==74 and len(errors)==0 and refs>0,"error_examples":errors[:20]}
    Path(a.out).write_text(json.dumps(out,indent=2,sort_keys=True)+"\n")
if __name__=="__main__": main()
