#!/usr/bin/env python3
"""Original-ten-archive, zone-aware CRUISE audit. Read-only: ZERO full games."""
import hashlib
import json
import os
from pathlib import Path
import re
import zipfile

MANIFEST = Path("experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R2_ZONE_MANIFEST.json")
SEATS = tuple(f"manual-mirror-{x}" for x in range(4))
COMMANDER = "Animar, Soul of Elements"
PROFILE = "manual-transmission-r5-survival-opt-in"
EVENT = re.compile(r"^ZoneChangeEvent\(entityId=([^,]+), entityName=(.*?), fromZone=([^,]+), toZone=([^,]+),")

def check(ok, detail):
    if not ok:
        raise AssertionError(detail)

def lines(data):
    return [json.loads(s) for s in data.decode().splitlines() if s.strip()]

def archived(spec):
    file = Path(os.environ.get("MANUAL_R2_ARCHIVES", "cruise-r2-originals")) / (
        f"{spec['cohort'].lower()}-{spec['arm']}-{spec['seed']}.zip")
    check(file.is_file(), f"Missing original {file}")
    check(hashlib.sha256(file.read_bytes()).hexdigest() == spec["sha256"], f"Original digest mismatch: {file}")
    prefix = ("precision-r1-stage1-output/" if spec["cohort"]=="R1" else "precision-r6-prospective-output/")
    base = prefix+spec["arm"]+"-seed-"+spec["seed"]+"/"
    with zipfile.ZipFile(file) as z:
        def read(suffix):
            p = base + suffix
            check(z.namelist().count(p)==1, f"Wrong exact archive member: {p}")
            return z.read(p)
        check(z.read(prefix+"source-sha.txt").decode().strip()==spec["source_sha"], "Archive source SHA changed")
        summary = json.loads(read("summary.json"))
        init = json.loads(read("initialization.json"))
        binding = json.loads(read("r1-arm-binding.json"))
        opening = json.loads(read("initial-state.json"))
        stream = lines(read("actions.jsonl"))
        decisions = lines(read("decisions.jsonl"))
        check(str(summary["seed"])==spec["seed"] and summary["arm"]==spec["arm"], "Seed/arm not original")
        check(summary["profile"]==PROFILE and binding["profile"]==PROFILE, "R5 comparator changed")
        check(summary["status"]=="COMPLETE" and summary["gameOver"] is True and summary["error"] is None, "Not completed gameplay")
        check(summary["accepted"]==summary["submitted"] and summary["accepted"]>=64, "Rejected or missing actions")
        check(summary["prefixOnly"] is False, "Smoke run inadmissible")
        check(init["openingPolicy"]=="Four seats keep seed-derived initial seven, no smoother or mulligans", "Mulligan-seam not historical")
        check(binding["changedSlots"]==(0 if spec["arm"]=="control" else 7), "Hardware contamination")
        check(binding["matchedInitializerPreflightVerified"] is True, "Unmatched initializer")
        check(len(stream)==2*summary["accepted"], "Submit/result count mismatch")
        submit=stream[::2]; results=stream[1::2]
        for index, (a,b) in enumerate(zip(submit,results),1):
            check(a["record"]=="submit" and b["record"]=="result" and a["index"]==b["index"]==index,"Action stream gap")
            check(b["error"] is None, "Illegal/rejected action in original game")
        check(results[-1]["gameOver"] is True and results[-1]["winner"]==summary["winner"],"Terminal provenance mismatch")
        # Pair source AI insights to actual accepted actions. Never infer a legal
        # commander offer from a label without exact original action alignment.
        priority=[a for a in submit if a["pendingDecision"] is None]
        cursor=0
        offered={}
        matched=0
        for d in decisions:
            if d["kind"] != "PRIORITY":
                continue
            chosen=[o for o in d["options"] if o.get("chosen") is True]
            check(len(chosen)==1,"Ambiguous original chosen option")
            choice=chosen[0]
            hit=None
            for k in range(cursor,len(priority)):
                a=priority[k]
                if a["turn"]>d["turnNumber"]+1:
                    break
                if (a["actor"]==d["playerId"] and a["turn"]==d["turnNumber"] and
                    a["step"]==d["step"] and json.loads(a["action"])==choice["action"]):
                    hit=a
                    cursor=k+1
                    break
            check(hit is not None,"Priority insight cannot be matched to actual submitted action")
            matched+=1
            if any(o.get("actionType")=="CastSpell" and o.get("cardName")==COMMANDER for o in d["options"]):
                check(d["onOwnTurn"] is True and d["step"] in ("PRECOMBAT_MAIN","POSTCOMBAT_MAIN"),"Wrong timing")
                check(hit["index"] not in offered,"Duplicate scored-index")
                offered[hit["index"]]=dict(seat=d["playerId"],chosen=choice.get("cardName")==COMMANDER and
                    choice.get("actionType")=="CastSpell",turn=d["turnNumber"])
        check(matched==sum(d["kind"]=="PRIORITY" for d in decisions),"Priority match incomplete")
        zones={}
        owners={}
        for i in range(0,len(opening["zones"]),2):
            key, ids = opening["zones"][i:i+2]
            if key.get("zoneType")=="Command" and key.get("ownerId") in SEATS:
                check(len(ids)==1,"Expected exact commander")
                zones[ids[0]]="COMMAND"
                owners[key["ownerId"]]=ids[0]
        check(set(owners)==set(SEATS),"Four original commanders missing")
        count={seat:{"COMMAND":0,"HAND":0,"scored":0,"scored_declined":0} for seat in SEATS}
        observations=[]
        bounce_indices=[]
        no_at_2957=False
        for a,b in zip(submit,results):
            index=a["index"]
            if index==2957 and spec["seed"]=="202610202141" and spec["arm"]=="control":
                act=json.loads(a["action"])
                no_at_2957=(a["actor"]=="manual-mirror-3" and act.get("type")=="SubmitDecision"
                            and act.get("response",{}).get("type")=="YesNoResponse"
                            and act["response"].get("choice") is False)
            if index in offered:
                op=offered[index]
                seat=op["seat"]
                current=zones[owners[seat]]
                check(current in ("COMMAND","HAND"),f"Scored Animar while outside hand/command at {index}: {current}")
                count[seat][current]+=1
                count[seat]["scored"]+=1
                count[seat]["scored_declined"]+=int(not op["chosen"])
                observations.append(dict(seat=seat,index=index,turn=op["turn"],zone=current,
                    selected_commander=op["chosen"]))
            for e in b["events"]:
                m=EVENT.match(e)
                if m is None:
                    continue
                card,name,old,new=m.groups()
                if card not in zones:
                    continue
                check(name==COMMANDER, f"Wrong commander name at {index}")
                check(zones[card]==old, f"Commander zone-chain mismatch at {index}")
                zones[card]=new
                if (spec["seed"]=="202610202141" and spec["arm"]=="control" and
                    card==owners["manual-mirror-3"] and old=="BATTLEFIELD" and new=="HAND"):
                    bounce_indices.append(index)
        check(len(observations)==len(offered),"Missing zone for scored action")
        for seat in SEATS:
            check(count[seat]["scored"]==summary["scoredCommanderOpportunityDecisions"][seat],"Scored count changed")
            check(count[seat]["scored_declined"]==summary["scoredCommanderDeclinedDecisions"][seat],"Scored declined changed")
            check(count[seat]["COMMAND"]==summary["legalCommanderOpportunityDecisions"][seat],"COMMAND legal-offer count failed reconciliation")
        if spec["seed"]=="202610202141" and spec["arm"]=="control":
            check(bounce_indices==[2956] and no_at_2957, "Critical bounced-commander negative decision missing")
            hand=[o for o in observations if o["seat"]=="manual-mirror-3" and o["zone"]=="HAND"]
            check(len(hand)==16 and all(x["index"]>2957 for x in hand),"Expected 16 hand-origin options")
        return dict(cohort=spec["cohort"],seed=spec["seed"],arm=spec["arm"],
            accepted=summary["accepted"],priority_aligned=matched,
            scored=sum(x["scored"] for x in count.values()),
            from_command=sum(x["COMMAND"] for x in count.values()),
            from_hand=sum(x["HAND"] for x in count.values()),
            per_seat=count,original_scored_options=observations,
            bounce_at_2956=bounce_indices,command_opt_out_at_2957=no_at_2957)

def main():
    m=json.loads(MANIFEST.read_text())
    check(m["schema"]=="MANUAL_CRUISE_R2_ORIGINAL_ZONE_AUDIT_V1","Wrong source manifest")
    check(m["budget"]==dict(used=10,max=12,reserved=2,new_games=0),"Game budget compromised")
    check(len(m["archives"])==10 and len({(x["seed"],x["arm"]) for x in m["archives"]})==10, "Ten matched originals required")
    rows=[archived(a) for a in m["archives"]]
    totals=dict(games=len(rows),
        accepted_actions=sum(x["accepted"] for x in rows),
        aligned_priority_decisions=sum(x["priority_aligned"] for x in rows),
        scored_animar=sum(x["scored"] for x in rows),
        command_zone_scored=sum(x["from_command"] for x in rows),
        hand_zone_scored=sum(x["from_hand"] for x in rows))
    check(totals==dict(games=10,accepted_actions=33440,aligned_priority_decisions=7827,
        scored_animar=249,command_zone_scored=233,hand_zone_scored=16),
        "Original total or classification changed")
    output=Path(os.environ.get("MANUAL_R2_OUTPUT","cruise-r2-evidence"))
    output.mkdir(parents=True,exist_ok=True)
    (output/"zone-reconciliation.json").write_text(json.dumps(dict(
        schema="MANUAL_CRUISE_R2_ZONE_RECONCILIATION_V1",
        disposition="EVENT_PROVENANCE_ONLY_NO_INDEPENDENT_LEGAL_MANA_REPLAY",
        source_sha=os.environ.get("GITHUB_SHA","UNSPECIFIED"),
        totals=totals,originals=rows,game_budget=m["budget"]),indent=2)+"\n")
    print("CRUISE_R2_ZONE_AUDIT_PASS "+json.dumps(totals,sort_keys=True)+" NO_NEW_GAMES")

if __name__=="__main__":
    main()
