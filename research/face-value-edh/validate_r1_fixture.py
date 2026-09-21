#!/usr/bin/env python3
import json, re, sys
SURFACES={"ordinary_cast","targeted_interaction","stack_priority","commander_unavailable","food_chain_pressure"}
ZONES={"command","battlefield","graveyard","exile","hand","library"}
REQ={"fixture_id","surface","synthetic","official_material","initial_state","decision","expected"}

def fail(msg):
    raise ValueError(msg)

def validate(x):
    if set(x)!=REQ: fail("top-level fields must match contract exactly")
    if not re.fullmatch(r"FVEDH-R1-[A-Z0-9_-]+",x["fixture_id"]): fail("bad fixture_id")
    if x["surface"] not in SURFACES: fail("unsupported surface")
    if x["synthetic"] is not True or x["official_material"] is not False: fail("official/non-synthetic material forbidden")
    s=x["initial_state"]
    if set(s)!={"active_player","priority_player","zones","stack","commander"}: fail("bad initial_state fields")
    if not isinstance(s["stack"],list) or not isinstance(s["zones"],dict): fail("bad zones/stack")
    c=s["commander"]
    if set(c)!={"name","zone","tax"} or c["name"]!="Animar, Soul of Elements" or c["zone"] not in ZONES or type(c["tax"]) is not int or c["tax"]<0: fail("bad commander state")
    d=x["decision"]
    if not {"action","supported"} <= set(d) or set(d)-{"action","supported","targets"}: fail("bad decision fields")
    if not isinstance(d["action"],str) or not d["action"]: fail("empty action")
    if type(d["supported"]) is not bool: fail("supported must be boolean")
    if "targets" in d and (not isinstance(d["targets"],list) or not all(isinstance(t,str) and t for t in d["targets"])): fail("bad targets")
    e=x["expected"]
    if not {"result"} <= set(e) or set(e)-{"result","reason"}: fail("bad expected fields")
    if e["result"] not in {"accept","reject_unsupported","reject_illegal"}: fail("bad expected result")
    # Fail closed: unsupported actions can never be expected to accept.
    if d["supported"] is False and e["result"]!="reject_unsupported": fail("unsupported action must reject_unsupported")
    return True

if __name__=="__main__":
    if len(sys.argv)!=2:
        print("usage: validate_r1_fixture.py FIXTURE.json",file=sys.stderr); sys.exit(2)
    try:
        with open(sys.argv[1],encoding="utf-8") as f: x=json.load(f)
        validate(x)
    except Exception as exc:
        print(f"FAIL-CLOSED: {exc}",file=sys.stderr); sys.exit(1)
    print("VALID")
