#!/usr/bin/env python3
import hashlib, json, pathlib, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parent
VALIDATOR=ROOT/"validate_r1_fixture.py"
FIX=ROOT/"fixtures"
FILES=[
"r1-schema-valid.json",
"r1b-ordinary-cast.json",
"r1b-targeted-interaction.json",
"r1b-stack-priority.json",
"r1b-commander-unavailable.json",
"r1b-food-chain-pressure.json",
]
def canonical(path):
    x=json.loads(path.read_text())
    return json.dumps(x,sort_keys=True,separators=(",",":")).encode()
def run_once():
    hashes={}
    for name in FILES:
        p=FIX/name
        cp=subprocess.run([sys.executable,str(VALIDATOR),str(p)],capture_output=True,text=True)
        if cp.returncode: raise SystemExit(f"{name} failed: {cp.stderr}")
        hashes[name]=hashlib.sha256(canonical(p)).hexdigest()
    return hashes
a=run_once(); b=run_once()
if a!=b: raise SystemExit("NONDETERMINISTIC REPLAY")
print(json.dumps({"result":"PASS","replay_equal":True,"fixtures":a},sort_keys=True))
