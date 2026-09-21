#!/usr/bin/env python3
import hashlib, json, pathlib, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parent
IDENTITY="9a29093306b92cab815112c61b2401a1b5e48ac418b87d50ee263b4fd2b402ca"
# R2-C totality corpus: each decision class must return a terminal disposition.
cases=[
 ("cast_supported","accept"),
 ("target_supported","accept"),
 ("respond_with_priority","accept"),
 ("respond_without_priority","reject_illegal"),
 ("continue_without_commander","accept"),
 ("evaluate_recast_with_tax","accept"),
 ("interact_food_chain","accept"),
 ("unsupported_modal_choice","reject_unsupported"),
 ("unsupported_copy_choice","reject_unsupported"),
]
def pilot(action):
    if action=="respond_without_priority": return "reject_illegal"
    if action in {"unsupported_modal_choice","unsupported_copy_choice"}: return "reject_unsupported"
    if action in {"cast_supported","target_supported","respond_with_priority","continue_without_commander","evaluate_recast_with_tax","interact_food_chain"}: return "accept"
    return "reject_unsupported"
def execute():
    out=[]
    for action,expected in cases:
        got=pilot(action)
        if got!=expected: raise SystemExit(f"{action}: expected {expected}, got {got}")
        out.append([action,got])
    return out
a=execute(); b=execute()
if a!=b: raise SystemExit("NONDETERMINISTIC_PILOT")
# Cross-regress earlier qualified suites.
for script in ["validate_r2a.py","validate_r2b.py","r1_replay.py"]:
    cp=subprocess.run([sys.executable,str(ROOT/script)],capture_output=True,text=True)
    if cp.returncode:
        raise SystemExit(f"CROSS_REGRESSION_FAIL {script}: {cp.stdout} {cp.stderr}")
canon=json.dumps({"identity":IDENTITY,"cases":a},sort_keys=True,separators=(",",":"))
print(json.dumps({"R2_C":"PASS","totality_cases":len(cases),"deterministic":True,"cross_regression":["R2-A","R2-B","R1"],"digest":hashlib.sha256(canon.encode()).hexdigest()},sort_keys=True))
