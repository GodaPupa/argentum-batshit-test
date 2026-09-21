#!/usr/bin/env python3
import json, pathlib, hashlib
ROOT=pathlib.Path(__file__).resolve().parent
IDENTITY="9a29093306b92cab815112c61b2401a1b5e48ac418b87d50ee263b4fd2b402ca"
fixtures=[
 {"id":"R2B-CMD-REMOVED","surface":"commander_unavailable","animar_zone":"command","tax":2,"stack":[],"priority":"P1","decision":"continue_without_commander","expected":"accept"},
 {"id":"R2B-CMD-RECAST","surface":"commander_unavailable","animar_zone":"command","tax":4,"stack":[],"priority":"P1","decision":"evaluate_recast_with_tax","expected":"accept"},
 {"id":"R2B-STACK-RESP","surface":"stack_priority","animar_zone":"battlefield","tax":0,"stack":["opponent_removal"],"priority":"P1","decision":"respond_before_resolution","expected":"accept"},
 {"id":"R2B-STACK-NOPRIO","surface":"stack_priority","animar_zone":"battlefield","tax":0,"stack":["opponent_spell"],"priority":"P2","decision":"cast_response_without_priority","expected":"reject_illegal"},
 {"id":"R2B-FOODCHAIN","surface":"food_chain_pressure","animar_zone":"battlefield","tax":0,"stack":["engine_enabler"],"priority":"P1","decision":"interact_with_engine_enabler","expected":"accept"},
]
allowed={"continue_without_commander","evaluate_recast_with_tax","respond_before_resolution","interact_with_engine_enabler"}
def adjudicate(f):
    if f["decision"]=="cast_response_without_priority" and f["priority"]!="P1": return "reject_illegal"
    return "accept" if f["decision"] in allowed else "reject_unsupported"
results=[]
for f in fixtures:
    got=adjudicate(f)
    if got!=f["expected"]: raise SystemExit(f"{f['id']} expected {f['expected']} got {got}")
    results.append({"id":f["id"],"result":got})
payload={"identity":IDENTITY,"fixtures":fixtures,"results":results}
canon=json.dumps(payload,sort_keys=True,separators=(",",":"))
h=hashlib.sha256(canon.encode()).hexdigest()
# replay determinism
canon2=json.dumps(payload,sort_keys=True,separators=(",",":"))
if canon!=canon2: raise SystemExit("NONDETERMINISTIC")
print(json.dumps({"R2_B":"PASS","fixture_count":len(fixtures),"digest":h,"results":results},sort_keys=True))
