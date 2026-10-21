#!/usr/bin/env python3
"""ARGENTUM SPORT S1: inspect only ten immutable original full-game streams.

R1/R6 original gameplay budget is already consumed (10/12). This program
makes zero moves, creates zero game states, and replays no new gameplay. It
matches source-bound Pact casts to exact next-own-upkeep payment observations.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import re
import zipfile

ROOT=Path("experiments/manual-transmission/research/cruise-sport-race-20261021")
INPUT=Path(os.environ.get("MANUAL_SPORT_S1_INPUT","sport-s1-original-input"))
OUTPUT=Path(os.environ.get("MANUAL_SPORT_S1_OUTPUT","sport-s1-evidence"))
CAST="Pact of Negation"
P_PREFIX="ManaSpentEvent(playerId="
RE_TURN=re.compile(r"TurnChangedEvent\(turnNumber=(\d+), activePlayerId=([^\)]+)\)")
RE_MANA=re.compile(r"(white|blue|black|red|green|colorless)=(\d+)")
ORACLE="At the beginning of your next upkeep, pay {3}{U}{U}."

def assert_true(yes,why):
    if not yes:raise AssertionError(why)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def pairs_from_zip(spec):
    path=INPUT/(spec["cohort"]+"-"+spec["arm"]+"-"+spec["seed"]+"-original.zip")
    assert_true(path.is_file() and sha(path)==spec["sha256"],"Original artifact tampering or missing "+str(path))
    outer=("precision-r1-stage1-output/" if spec["cohort"]=="R1"
           else "precision-r6-prospective-output/")
    pre=outer+spec["arm"]+"-seed-"+spec["seed"]+"/"
    with zipfile.ZipFile(path) as zip:
        def get(member):
            assert_true(zip.namelist().count(member)==1,"Original archive member missing or duplicated: "+member)
            return zip.read(member).decode()
        assert_true(get(outer+"source-sha.txt").strip()==spec["source_sha"],
                    "Original R1/R6 source mismatch")
        summary=json.loads(get(pre+"summary.json"))
        bind=json.loads(get(pre+"r1-arm-binding.json"))
        init=json.loads(get(pre+"initialization.json"))
        assert_true(str(summary["seed"])==spec["seed"] and summary["arm"]==spec["arm"],"Original seed/arm drift")
        assert_true(summary["status"]=="COMPLETE" and summary["gameOver"] is True
                    and summary["error"] is None,"Not a valid completed original game")
        assert_true(summary["accepted"]==summary["submitted"] and summary["accepted"]>=64,
                    "Rejection or missing original action")
        assert_true(summary["profile"]=="manual-transmission-r5-survival-opt-in"
                    and bind["profile"]=="manual-transmission-r5-survival-opt-in",
                    "Original R5 comparator changed")
        assert_true(init["openingPolicy"]==
                    "Four seats keep seed-derived initial seven, no smoother or mulligans",
                    "Original no-mulligan opening policy changed")
        raw=[json.loads(x) for x in get(pre+"actions.jsonl").splitlines() if x.strip()]
        assert_true(len(raw)==2*summary["accepted"],"Original result/submit count differs")
        out=[]
        for i in range(summary["accepted"]):
            a,b=raw[2*i],raw[2*i+1]
            assert_true(a["record"]=="submit" and b["record"]=="result"
                        and a["index"]==b["index"]==i+1,"Original pair/index drift")
            assert_true(b["error"] is None,"Original action was rejected")
            out.append((a,b))
        assert_true(out[-1][1]["gameOver"] is True and
                    out[-1][1]["winner"]==summary["winner"],"Original terminal mismatch")
    return out,summary

def find_next_own_upkeep(pairs,cast_index,player):
    for a,b in pairs:
        if a["index"]<=cast_index:continue
        for event in b["events"]:
            match=RE_TURN.search(event)
            if match and match.group(2)==player:
                return int(match.group(1)),a["index"]
    return None,None

def audit_pact(pairs,a,spec):
    source=json.loads(a["action"])
    card_id=source["cardId"]
    caster=a["actor"]
    cast_idx=a["index"]
    expected_spell=("SpellCastEvent(spellEntityId="+card_id+
                    ", cardName=Pact of Negation, casterId="+caster)
    cast_events=next(b["events"] for x,b in pairs if x["index"]==cast_idx)
    assert_true(any(expected_spell in s for s in cast_events),
                "Pact source cast lacked genuine SpellCastEvent")
    next_turn,next_turn_change_index=find_next_own_upkeep(pairs,cast_idx,caster)
    if next_turn is None:
        return {"seed":spec["seed"],"arm":spec["arm"],"cohort":spec["cohort"],
                "caster":caster,"cardId":card_id,"castActionIndex":cast_idx,
                "castTurn":a["turn"],"nextOwnUpkeepTurn":None,
                "observation":"CENSORED_NEXT_OWN_TURN_NOT_OBSERVED"}
    after=[(x,b) for x,b in pairs if x["index"]>=cast_idx]
    resolved=[];triggers=[];decisions=[];payments=[]
    for step,result in after:
        for e in result["events"]:
            if ("ResolvedEvent(entityId="+card_id+", name=Pact of Negation)") in e:
                resolved.append((step,e))
            if ("AbilityTriggeredEvent(sourceId="+card_id+
                ", sourceName=Pact of Negation,") in e:
                triggers.append((step,e))
            if "DecisionSubmittedEvent(" in e and "(Pact of Negation) Chose " in e and step["actor"]==caster:
                decisions.append((step,e))
            if e.startswith(P_PREFIX+caster+", reason=Pay {3}{U}{U}"):
                payments.append((step,e))
    assert_true(len(resolved)==1,"Expected exactly one original Pact resolution")
    assert_true(len(triggers)==1,"Expected exactly one Pact delayed trigger event")
    assert_true(len(decisions)==1,"Expected exactly one Pact payment decision")
    assert_true(len(payments)==1,"Expected exactly one actual Pact-cost mana payment")
    decision,d_event=decisions[0]
    payment,p_event=payments[0]
    assert_true("Chose Yes" in d_event,"Original payment decision was not YES")
    assert_true(decision["actor"]==caster and decision["step"]=="UPKEEP"
                and decision["turn"]==next_turn,
                "Pact decision not at first subsequent caster upkeep")
    assert_true(payment["actor"]==caster and payment["step"]=="UPKEEP"
                and payment["turn"]==next_turn,"Pact 3UU payment not at next caster upkeep")
    spend={k:int(v) for k,v in RE_MANA.findall(p_event)}
    assert_true(spend.get("blue",0)>=2 and sum(spend.values())==5,
                "Pact 3UU payment did not spend five total / two blue")
    trigger,t_event=triggers[0]
    assert_true(cast_idx<trigger["index"]<decision["index"],
                "Trigger event not after actual cast and before payment")
    return {
        "cohort":spec["cohort"],"seed":spec["seed"],"arm":spec["arm"],
        "caster":caster,"cardId":card_id,"castActionIndex":cast_idx,
        "castTurn":a["turn"],"firstNextOwnTurn":next_turn,
        "nextOwnTurnStartedAtAction":next_turn_change_index,
        "sourceResolvedAtAction":resolved[0][0]["index"],
        "delayedTriggerEventAtAction":trigger["index"],
        "delayedTriggerEventStep":trigger["step"],
        "delayedTriggerEventTurn":trigger["turn"],
        "triggerDescriptionSaysEndStep":"at the beginning of each End Step" in t_event,
        "actualPaymentDecisionIndex":decision["index"],
        "actualPaymentTurn":payment["turn"],
        "actualPaymentStep":payment["step"],
        "paymentColorAmounts":spend,
        "paymentIsNextOwnUpkeep":True,
        "gameplayOutcomeInference":"ACTUAL_ACCEPTED_SOURCE_ACTIONS_ONLY"
    }

def main():
    protocol=json.loads((ROOT/"SPORT_S1_PACT_SOURCE_PROTOCOL.json").read_text())
    assert_true(protocol["schema"]=="ARGENTUM_MANUAL_SPORT_S1_ORIGINAL_PACT_PAYMENT_TRACE_AUDIT_V1",
                "Wrong original SPORT protocol")
    assert_true(protocol["qualifier"]["original_budget"]==
                {"used":10,"cap":12,"reserved":2,"new_games":0},"SPORT budget not frozen")
    manifest=json.loads((ROOT/"CRUISE_R2_ZONE_MANIFEST.json").read_text())
    archives=manifest["archives"]
    assert_true(len(archives)==10 and len({(x["seed"],x["arm"]) for x in archives})==10,
                "Original complete ten-game universe missing")
    records=[];manifest_rows=[];accepted=0
    for spec in archives:
        pairs,summary=pairs_from_zip(spec)
        accepted+=summary["accepted"]
        for a,_ in pairs:
            action=json.loads(a["action"])
            if (a.get("sourceName")==CAST and action.get("type")=="CastSpell"):
                records.append(audit_pact(pairs,a,spec))
        manifest_rows.append({"cohort":spec["cohort"],"seed":spec["seed"],
                "arm":spec["arm"],"originalArtifactId":spec["id"],
                "originalArtifactSha256":spec["sha256"],
                "acceptedActions":summary["accepted"]})
    assert_true(accepted==33440,"Original sample action count drifted")
    assert_true(len(records)==13,"Expected thirteen predeclared actual historical Pact casts")
    censor=[x for x in records if x.get("observation")=="CENSORED_NEXT_OWN_TURN_NOT_OBSERVED"]
    paid=[x for x in records if x.get("paymentIsNextOwnUpkeep") is True]
    labels=[x for x in records if x.get("triggerDescriptionSaysEndStep") is True]
    assert_true(not censor and len(paid)==13,"Original owed Pact payment witness missing")
    assert_true(len(labels)==13,"Historical trigger text inventory drifted")
    result={
        "schema":"ARGENTUM_MANUAL_SPORT_S1_ORIGINAL_PACT_PAYMENT_TRACE_REPORT_V1",
        "status":"SOURCE_TRACE_RECONCILED_NO_NEW_GAME",
        "originalGames":len(archives),"originalAcceptedActions":accepted,
        "actualPactSpellsCast":len(records),
        "actualPactPaymentsAtFirstNextOwnUpkeep":len(paid),
        "actualPactUnpayableOrDeclinedObserved":0,
        "historicalTriggerDescriptionsSayingEndStep":len(labels),
        "sourceCardOracleTextFromFrozenRepo":ORACLE,
        "eventTextStatus":"DESCRIPTION_AND_EVENT_EMISSION_PHASE_REQUIRE_FOCUSED_ENGINE_EXPLANATION",
        "actualGamePaymentTimingStatus":"ALL_THIRTEEN_NEXT_OWN_UPKEEP_PAID",
        "gameplayBugDemonstrated":False,
        "competitiveImpactQualified":False,
        "newFullCommanderGames":0,"remainingReserved":2,
        "limits":["Historical mirror games are dependent, not blinded.",
          "All observed Pact debt payments succeeded; the original games do not test unpaid-loss branches.",
          "End Step trigger descriptions are not alone proof of incorrect payment timing.",
          "Original game action and event streams are observations, not prospective opponent-response forecasts."],
        "originalSourceArtifacts":manifest_rows,
        "actualHistoricalPactCases":records,
        "recommendation":"HOLD ANY PACT POLICY CHANGE; independently inspect delayed-trigger event descriptions vs next-upkeep effect timing before mechanical repair."
    }
    OUTPUT.mkdir(parents=True,exist_ok=True)
    (OUTPUT/"SPORT_S1_ORIGINAL_PACT_AUDIT.json").write_text(
        json.dumps(result,indent=2)+"\n")
    lines=[
        "# Manual Transmission — SPORT S1 original Pact debt trace audit",
        "",
        "**Read-only ten original complete games; no new full games, no R5/R9/deck promotion.**",
        "",
        "- Pact spells cast and resolved: **13**.",
        "- Debt payments actually observed on the caster's first subsequent upkeep: **13/13**.",
        "- Accepted original submitted actions: **33,440**, with no rejection.",
        "- Observed triggered-ability descriptive text refers to \"End Step\": **13/13**.",
        "- The frozen card's own printed oracle and implementation specify next **UPKEEP**.",
        "",
        "**Disposition:** No historical unpaid-Pact planning defect is proven by these 13 paid examples.",
        "The trigger's recorded description and apparent pre-upkeep emission warrant an exact",
        "separate engine sequencing/presentation test, not an immediate default policy change.",
        "Self-mirror observations are dependent and cannot qualify competitive win rate.",
        "",
        "Original full-game budget **10/12 used, 2 reserved, 0 newly played**."
    ]
    (OUTPUT/"REPORT.md").write_text("\n".join(lines)+"\n")
    print("MANUAL_SPORT_S1_PACT_SOURCE_AUDIT_GREEN "+json.dumps({
        "games":len(archives),"accepted":accepted,"cast":len(records),
        "nextOwnUpkeepPaid":len(paid),"triggerDescriptionEndStep":len(labels),
        "newGames":0},sort_keys=True))

if __name__=="__main__":
    main()
