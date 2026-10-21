#!/usr/bin/env python3
"""RACE R1: exhaustive original finite conversion observations. NO NEW GAME.

Reuses the source-pinned 10-game reader from SPORT S3A only. No effect
evaluation, new game state, competitor hand inspection or imagined terminal.
"""
from __future__ import annotations
from collections import Counter, defaultdict
import json
import os
from pathlib import Path
import re
import sys

ROOT=Path("experiments/manual-transmission/research/cruise-sport-race-20261021")
sys.path.insert(0,str(ROOT))
from audit_sport_s3_original_priority import original
OUT=Path(os.environ.get("RACE_R1_OUTPUT","race-r1-evidence"))
SPELL=re.compile(r"^SpellCastEvent\(spellEntityId=([^,]+), cardName=(.*?), casterId=([^,]+),")
BOUNCE=re.compile(r"^ZoneChangeEvent\(entityId=([^,]+), entityName=Ancestral Statue, fromZone=BATTLEFIELD, toZone=HAND,")
DAMAGE_NAME=re.compile(r"sourceName=(.*?), targetName=")
DAMAGE_AMOUNT=re.compile(r"amount=(\d+),")
COMBOS={"Ancestral Statue","Walking Ballista","Purphoros, God of the Forge"}
SUPPORT={"Temur Sabertooth","Peregrine Drake","Beast Whisperer","Soul of the Harvest"}
WATCH=COMBOS|SUPPORT

def check(yes,msg):
    if not yes:raise AssertionError(msg)

def join_priorities(submits,decisions):
    eligible=[x for x in submits if x["pendingDecision"] is None]
    out={}
    cursor=0
    for d in decisions:
        if d["kind"]!="PRIORITY":continue
        chosen=[o for o in d["options"] if o.get("chosen") is True]
        check(len(chosen)==1,"More/less than one original chosen scored action")
        for k in range(cursor,len(eligible)):
            a=eligible[k]
            if a["turn"]>d["turnNumber"]+1:break
            if a["actor"]==d["playerId"] and a["turn"]==d["turnNumber"] and \
               a["step"]==d["step"] and json.loads(a["action"])==chosen[0]["action"]:
                check(a["index"] not in out,"Repeated original decision/action match")
                out[a["index"]]=d
                cursor=k+1
                break
        else:
            raise AssertionError("Unmatched historical R5 priority decision")
    check(len(out)==sum(d["kind"]=="PRIORITY" for d in decisions),"Missing priority decisions")
    return out

def main():
    p=json.loads((ROOT/"RACE_R1_ORIGINAL_CONVERSION_PROTOCOL.json").read_text())
    m=json.loads((ROOT/"CRUISE_R2_ZONE_MANIFEST.json").read_text())
    check(p["schema"]=="ARGENTUM_MANUAL_RACE_R1_FROZEN_V07_ORIGINAL_CONVERSION_INVENTORY_V1"
          and p["status"]=="PREDECLARED_BEFORE_RACE_R1_AUDIT_EXECUTION",
          "Missing predeclared frozen RACE protocol")
    check(m["original_game_count"]==10 and m["original_accepted_actions"]==33440 and
          m["original_priority_decisions_matched"]==7827,"Original complete universe drift")
    check(p["hard_guards"]["new_games"]==0 and p["hard_guards"]["reserved_games"]==2,"Budget drift")
    fixed=Path("experiments/manual-transmission/control/v0.7.decklist.txt").read_text()
    names={line[2:] for line in fixed.splitlines() if line.startswith("1 ")}
    check(set(p["actual_deck_watchlist"]["conversion_cards"])==COMBOS,"Combos watchlist drift")
    check(set(p["actual_deck_watchlist"]["support_cards"])==SUPPORT,"Support watchlist drift")
    check(WATCH <= names,"Frozen RACE target cards absent from actual deck")
    check("Palinchron" not in names and
          p["actual_deck_watchlist"]["excluded_not_in_frozen_hardware"]==["Palinchron"],
          "Regression-only Palinchron wrongly admitted to frozen hardware")

    totals=Counter()
    offers=defaultdict(Counter)
    actual=defaultdict(Counter)
    payoff_damage=defaultdict(Counter)
    cast_by_turn=Counter()
    option_rows=[]
    game_rows=[]
    bounce_rows=[]
    damage_rows=[]
    terminal_rows=[]
    for spec in m["archives"]:
        game,submits,results,decisions=original(spec)
        linked=join_priorities(submits,decisions)
        totals["games"]+=1
        totals["accepted_actions"]+=game["accepted"]
        totals["matched_priorities"]+=len(linked)
        for a,b in zip(submits,results):
            ix=a["index"]
            d=linked.get(ix)
            if d is not None:
                for option in d["options"]:
                    name=option.get("cardName")
                    typ=option.get("actionType")
                    if name not in WATCH or typ not in ("CastSpell","ActivateAbility"):
                        continue
                    chosen=option.get("chosen") is True
                    category="cast" if typ=="CastSpell" else "activation"
                    offers[name][category+"_offered"]+=1
                    if chosen:offers[name][category+"_chosen"]+=1
                    else:offers[name][category+"_not_chosen"]+=1
                    option_rows.append({
                      "cohort":spec["cohort"],"seed":spec["seed"],"arm":spec["arm"],
                      "original_action_index":ix,"turn":a["turn"],"step":a["step"],
                      "seat":a["actor"],"watched_card":name,"option_action_type":typ,
                      "original_chose_this":chosen,"historical_score":option.get("score"),
                      "original_action_accepted":True,
                      "qualification":"SCORED_ACTION_OPTION_NO_ALTERNATIVE_OUTCOME_PROVEN"
                    })
            action=json.loads(a["action"])
            name=a.get("sourceName")
            if action.get("type")=="CastSpell" and name in WATCH:
                actual[name]["submitted_casts"]+=1
                cast_by_turn[(spec["cohort"],spec["seed"],spec["arm"],a["actor"],a["turn"],name)]+=1
                check(any((x.startswith("SpellCastEvent(") and
                           f"cardName={name}, casterId={a['actor']}," in x)
                           for x in b["events"]),
                      f"Accepted watch cast lacks actual SpellCastEvent {spec} {ix} {name}")
            if action.get("type")=="ActivateAbility" and name in WATCH:
                actual[name]["submitted_activations"]+=1
            turn_damage=[]
            for e in b["events"]:
                if e.startswith("DamageDealtEvent(") and "targetIsPlayer=true" in e:
                    match=DAMAGE_NAME.search(e)
                    if match and match.group(1) in ("Purphoros, God of the Forge","Walking Ballista"):
                        src=match.group(1)
                        mag=DAMAGE_AMOUNT.search(e)
                        check(mag is not None,"Payoff damage lost amount")
                        amount=int(mag.group(1))
                        payoff_damage[src]["player_damage_events"]+=1
                        payoff_damage[src]["player_damage_amount"]+=amount
                        turn_damage.append(src)
                        damage_rows.append({
                          "cohort":spec["cohort"],"seed":spec["seed"],"arm":spec["arm"],
                          "original_action_index":ix,"actor":a["actor"],"turn":a["turn"],
                          "source":src,"amount":amount,"gameOverInSameResult":b["gameOver"] is True
                        })
                z=BOUNCE.match(e)
                if z:
                    actual["Ancestral Statue"]["observed_battlefield_to_hand_bounces"]+=1
                    bounce_rows.append({"cohort":spec["cohort"],"seed":spec["seed"],"arm":spec["arm"],
                                        "turn":a["turn"],"action_index":ix,"entity_id":z.group(1)})
            if b["gameOver"] is True:
                totals["original_game_end_actions"]+=1
                if turn_damage:
                    totals["payoff_player_damage_in_game_end_result"]+=1
                terminal_rows.append({
                  "cohort":spec["cohort"],"seed":spec["seed"],"arm":spec["arm"],
                  "original_terminal_action_index":ix,"winner":b["winner"],
                  "same_result_payoff_player_damage_sources":sorted(set(turn_damage)),
                  "interpretation":"ACTUAL_SAME_ACTION_EVENTS_ONLY"
                })
        game_rows.append({
          "cohort":spec["cohort"],"seed":spec["seed"],"arm":spec["arm"],
          "source_sha":spec["source_sha"],"original_zip_sha256":spec["sha256"],
          "accepted":game["accepted"],"priority_joined":len(linked),
          "original_winner":game["winner"]
        })
    check(totals["games"]==10 and totals["accepted_actions"]==33440 and
          totals["matched_priorities"]==7827 and totals["original_game_end_actions"]==10,
          "Original RACE source accounting gap")

    option_rows.sort(key=lambda r:(0 if r["cohort"]=="R1" else 1,r["seed"],
      0 if r["arm"]=="control" else 1,r["original_action_index"],r["seat"],r["watched_card"]))
    first_by_card={name:next((r for r in option_rows if r["watched_card"]==name),None)
                   for name in sorted(WATCH)}
    per_turn=[dict(cohort=k[0],seed=k[1],arm=k[2],seat=k[3],turn=k[4],
                   card=k[5],actual_casts=c)
              for k,c in cast_by_turn.items()]
    per_turn.sort(key=lambda r:(0 if r["cohort"]=="R1" else 1,r["seed"],
      0 if r["arm"]=="control" else 1,r["seat"],r["turn"],r["card"]))
    repeated=[x for x in per_turn if x["card"]=="Ancestral Statue" and x["actual_casts"]>1]
    totals["watched_original_option_rows"]=len(option_rows)
    totals["original_statue_repeated_cast_turns"]=len(repeated)
    totals["original_statue_max_casts_same_seat_turn"]=max(
       (x["actual_casts"] for x in per_turn if x["card"]=="Ancestral Statue"),default=0)

    report={
      "schema":"ARGENTUM_MANUAL_RACE_R1_ALL_ORIGINAL_FINITE_CONVERSION_REPORT_V1",
      "disposition":"ORIGINAL_SOURCE_INVENTORY_NOT_WINRATE_OR_POLICY_CLAIM",
      "source_sha":os.environ.get("GITHUB_SHA","UNSPECIFIED"),
      "original_counts":dict(totals),
      "per_card_offered_chosen":{k:dict(v) for k,v in sorted(offers.items())},
      "per_card_accepted_uses":{k:dict(actual[k]) for k in sorted(WATCH)},
      "observed_payoff_damage":{k:dict(v) for k,v in sorted(payoff_damage.items())},
      "finite_casts_by_seat_turn":per_turn,
      "repeated_statue_cast_turns":repeated,
      "observed_statue_bounces":bounce_rows,
      "observed_payoff_damage_rows":damage_rows,
      "original_terminal_action_rows":terminal_rows,
      "earliest_original_option_by_card":first_by_card,
      "all_original_qualified_option_rows":option_rows,
      "original_source_game_bindings":game_rows,
      "Palinchron_in_frozen_hardware":False,
      "limitations":[
        "Historical legal-action options do not prove that selecting them improves a game.",
        "Original 10 games ran R5 self-mirror opponents rather than tournament deck pilots.",
        "Same-result payoff damage and a terminal game do not establish competitive conversion frequency.",
        "All observed repeated Statue casts are finite action observations, never proof of infinite recursion.",
        "No new game, artificial priority pass, hidden opponent card peek or policy change occurred."
      ],
      "game_budget":dict(used=10,max=12,reserved=2,new=0)
    }
    OUT.mkdir(exist_ok=True,parents=True)
    (OUT/"RACE_R1_ALL_ORIGINAL_CONVERSION.json").write_text(
      json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    lines=["# RACE R1 — original v0.7 finite conversion source inventory",
           "",
           "**Ten original R5 mirror games, no new games, no new gear policy.**",
           "","| Frozen card | Scored cast options | Scored cast chosen | Real accepted casts |",
           "|---|---:|---:|---:|"]
    for name in sorted(WATCH):
        o=offers[name];a=actual[name]
        lines.append(f"| {name} | {o['cast_offered']} | {o['cast_chosen']} | {a['submitted_casts']} |")
    lines.extend(["","| Source-bound observed count | Total |","|---|---:|",
      f"| Original accepted actions | {totals['accepted_actions']} |",
      f"| Original R5 priority decisions | {totals['matched_priorities']} |",
      f"| Original game end actions | {totals['original_game_end_actions']} |",
      f"| Statue battlefield-to-hand bounces | {actual['Ancestral Statue']['observed_battlefield_to_hand_bounces']} |",
      f"| Repeated same-turn Statue cast groups | {len(repeated)} |",
      f"| Maximum original Statue casts in a seat/turn | {totals['original_statue_max_casts_same_seat_turn']} |",
      f"| Purphoros player damage events | {payoff_damage['Purphoros, God of the Forge']['player_damage_events']} |",
      f"| Walking Ballista player damage events | {payoff_damage['Walking Ballista']['player_damage_events']} |",
      f"| Game-end results with same-action watched payoff damage | {totals['payoff_player_damage_in_game_end_result']} |",
      "",
      "**Palinchron is not in frozen v0.7 and was not counted as a deck line.**",
      "Observed original R5 scored options are not proven missed wins. A same-action",
      "damage-and-terminal observation is bounded to its archived source and is not",
      "a tournament statistic. Do not promote a RACE challenger absent a falsifiable",
      "real decision deficiency. Two original full games remain reserved.",
      ""])
    (OUT/"REPORT.md").write_text("\n".join(lines))
    print("MANUAL_RACE_R1_FROZEN_ORIGINALS_GREEN "+
          json.dumps(dict(totals),sort_keys=True)+" NO_NEW_GAMES")

if __name__=="__main__":
    main()
