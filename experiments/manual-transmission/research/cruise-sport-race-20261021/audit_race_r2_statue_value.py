#!/usr/bin/env python3
"""Retrospective, exact-event Ancestral Statue long-chain value audit, no games."""
from __future__ import annotations
import json
import os
from pathlib import Path
import re
import sys

ROOT=Path("experiments/manual-transmission/research/cruise-sport-race-20261021")
sys.path.insert(0,str(ROOT))
from audit_sport_s3_original_priority import original
OUT=Path(os.environ.get("RACE_R2_OUTPUT","race-r2-evidence"))
DRAW=re.compile(r"^CardsDrawnEvent\(playerId=([^,]+), count=(\d+),")
COUNT=re.compile(r"amount=(\d+),")
STATUE_EXIT=re.compile(r"^ZoneChangeEvent\(entityId=([^,]+), entityName=Ancestral Statue, fromZone=BATTLEFIELD, toZone=HAND, ownerId=([^,]+),")
DAMAGE_SOURCE=re.compile(r"sourceName=(.*?), targetName=")
TRIGGER_SOURCE=re.compile(r"^AbilityTriggeredEvent\(sourceId=[^,]+, sourceName=(.*?), controllerId=([^,]+),")

def check(ok,reason):
    if not ok:raise AssertionError(reason)

def main():
    p=json.loads((ROOT/"RACE_R2_STATUE_ORIGINAL_VALUE_PROTOCOL.json").read_text())
    m=json.loads((ROOT/"CRUISE_R2_ZONE_MANIFEST.json").read_text())
    check(p["schema"]=="ARGENTUM_MANUAL_RACE_R2_THREE_ORIGINAL_STATUE_CHAIN_VALUE_R1"
      and p["status"]=="PREDECLARED_BEFORE_R2_CI_EVENT_AGGREGATION",
      "Predeclared RACE R2 protocol missing")
    check(p["safety"]["zero_new_game"] is True and
          p["safety"]["original_games_reserved"]==2,"Game budget violation")
    cases=p["three_fixed_chains"]
    check(len(cases)==3 and sum(x["original_casts"] for x in cases)==57,
          "Original R1 three-chain universe changed")
    check(len({(x["seed"],x["arm"],x["seat"],x["turn"]) for x in cases})==3,
          "Duplicate original chain selection")
    indexed={(x["cohort"],x["seed"],x["arm"]):x for x in m["archives"]}
    all_cases=[]
    interval_rows=[]
    for item in cases:
        spec=indexed[(item["cohort"],item["seed"],item["arm"])]
        check(spec["id"]==item["artifact_id"] and spec["sha256"]==item["zip_sha256"],
              "Chain did not reference exact original R1 game ZIP")
        summary,submits,results,_=original(spec)
        seat=item["seat"];turn=item["turn"]
        ordered=[(a,b) for a,b in zip(submits,results) if a["turn"]==turn]
        casts=[a["index"] for a,b in ordered if
               a["actor"]==seat and a.get("sourceName")=="Ancestral Statue" and
               json.loads(a["action"]).get("type")=="CastSpell"]
        check(len(casts)==item["original_casts"],"Missing original accepted Statue casts")
        intervals=[]
        for i,start in enumerate(casts):
            finish=casts[i+1] if i+1<len(casts) else ordered[-1][0]["index"]+1
            region=[(a,b) for a,b in ordered if start<=a["index"]<finish]
            check(region and region[0][0]["index"]==start,
                  "Statue chain window excludes its actual submitted cast")
            evidence={
              "drawn_cards_to_seat":0,
              "actual_animar_counters_added_by_seat":0,
              "statue_battlefield_to_hand_returns":0,
              "beast_whisperer_triggers":0,
              "soul_harvest_triggers":0,
              "purphoros_player_damage":0,
              "walking_ballista_player_damage":0,
              "actual_game_end_in_interval":0,
            }
            for a,b in region:
                if b["gameOver"] is True:evidence["actual_game_end_in_interval"]+=1
                for event in b["events"]:
                    drawn=DRAW.match(event)
                    if drawn and drawn.group(1)==seat:
                        evidence["drawn_cards_to_seat"]+=int(drawn.group(2))
                    if (event.startswith("CountersAddedEvent(") and
                        "entityName=Animar, Soul of Elements," in event and f"placedBy={seat}" in event):
                        amount=COUNT.search(event)
                        check(amount is not None,"Animar counter increment lost amount")
                        evidence["actual_animar_counters_added_by_seat"]+=int(amount.group(1))
                    bounced=STATUE_EXIT.match(event)
                    if bounced and bounced.group(2)==seat:
                        evidence["statue_battlefield_to_hand_returns"]+=1
                    trigger=TRIGGER_SOURCE.match(event)
                    if trigger and trigger.group(2)==seat:
                        if trigger.group(1)=="Beast Whisperer":evidence["beast_whisperer_triggers"]+=1
                        if trigger.group(1)=="Soul of the Harvest":evidence["soul_harvest_triggers"]+=1
                    if event.startswith("DamageDealtEvent(") and "targetIsPlayer=true" in event:
                        source=DAMAGE_SOURCE.search(event)
                        if source and source.group(1) in ("Purphoros, God of the Forge","Walking Ballista"):
                            amount=COUNT.search(event)
                            check(amount is not None,"Payoff damage event lacked actual amount")
                            field=("purphoros_player_damage" if source.group(1)=="Purphoros, God of the Forge"
                                   else "walking_ballista_player_damage")
                            evidence[field]+=int(amount.group(1))
            evidence["material_observed_in_interval"]=bool(
                evidence["drawn_cards_to_seat"] or
                evidence["actual_animar_counters_added_by_seat"] or
                evidence["purphoros_player_damage"] or
                evidence["walking_ballista_player_damage"])
            row={
              "cohort":spec["cohort"],"seed":spec["seed"],"arm":spec["arm"],
              "caster_seat":seat,"source_global_turn":turn,
              "cast_number_within_turn":i+1,"original_cast_action_index":start,
              "exclusive_following_action_index":finish,
              "actual_cast_was_accepted":True,
              "event_interval_summary":evidence,
              "causality_limit":"EVENTS_BETWEEN_CASTS_NOT_ALL_CAUSED_BY_STATUE"
            }
            interval_rows.append(row)
            intervals.append(row)
        all_cases.append({
          "cohort":spec["cohort"],"seed":spec["seed"],"arm":spec["arm"],"seat":seat,
          "turn":turn,"actual_statue_casts":len(casts),
          "actual_source_sha":spec["source_sha"],
          "original_artifact_id":spec["id"],
          "original_zip_sha256":spec["sha256"],
          "total_draws_in_cast_intervals":sum(x["event_interval_summary"]["drawn_cards_to_seat"] for x in intervals),
          "total_animar_counters_in_cast_intervals":sum(x["event_interval_summary"]["actual_animar_counters_added_by_seat"] for x in intervals),
          "total_statue_self_returns_in_cast_intervals":sum(x["event_interval_summary"]["statue_battlefield_to_hand_returns"] for x in intervals),
          "zero_material_intervals":sum(not x["event_interval_summary"]["material_observed_in_interval"] for x in intervals),
          "same_turn_payoff_damage":sum(x["event_interval_summary"]["purphoros_player_damage"]+
                                        x["event_interval_summary"]["walking_ballista_player_damage"] for x in intervals),
          "original_global_turn_final_action":ordered[-1][0]["index"]
        })
    check(len(interval_rows)==57 and len(all_cases)==3,
          "All 57 independently frozen original casts required")
    totals={
      "chains":3,
      "original_actual_statue_casts":57,
      "intervals_with_material_observed":sum(x["event_interval_summary"]["material_observed_in_interval"] for x in interval_rows),
      "intervals_without_material_observed":sum(not x["event_interval_summary"]["material_observed_in_interval"] for x in interval_rows),
      "observed_card_draws_between_casts":sum(x["event_interval_summary"]["drawn_cards_to_seat"] for x in interval_rows),
      "observed_animar_counter_increments_between_casts":sum(x["event_interval_summary"]["actual_animar_counters_added_by_seat"] for x in interval_rows),
      "observed_statue_self_returns_between_casts":sum(x["event_interval_summary"]["statue_battlefield_to_hand_returns"] for x in interval_rows),
      "observed_payoff_damage_between_casts":sum(x["event_interval_summary"]["purphoros_player_damage"]+
                                                  x["event_interval_summary"]["walking_ballista_player_damage"] for x in interval_rows),
      "observed_game_ends_between_casts":sum(x["event_interval_summary"]["actual_game_end_in_interval"] for x in interval_rows)
    }
    report={
      "schema":"ARGENTUM_MANUAL_RACE_R2_THREE_REAL_REPEAT_CHAINS_SOURCE_REPORT_V1",
      "status":"RETROSPECTIVE_SOURCE_ONLY_NOT_STRATEGIC_CAUSALITY",
      "source_sha":os.environ.get("GITHUB_SHA","UNSPECIFIED"),
      "totals":totals,
      "three_original_chains":all_cases,
      "all_57_cast_interval_rows":interval_rows,
      "no_new_game":True,
      "precision_remaining":2,
      "limitations":[
        "Events between consecutive accepted Statue casts are not all necessarily caused by Statue.",
        "Cards drawn and Animar counters are genuine state changes but do not prove strategic optimality.",
        "No Purphoros damage or terminal in those intervals does not make resource/draw engines empty.",
        "Three original mirror chains are a dependent observational subset, not a tournament trial.",
        "No Palinchron is in the frozen deck; no hypothetical future opponent responses are modeled."
      ]
    }
    OUT.mkdir(exist_ok=True,parents=True)
    (OUT/"RACE_R2_THREE_STATUE_CHAINS.json").write_text(
      json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    lines=["# RACE R2 — all 3 original repeated Statue chains","",
      "**Immutable original accepted events only; 0 new games, no policy/deck change.**",
      "","| Source | Accepted Statue casts | Seat card draws during intervals | Animar counters during intervals | No-material intervals |",
      "|---|---:|---:|---:|---:|"]
    for case in all_cases:
        label=f"{case['arm']} / {case['seat']} / global turn {case['turn']}"
        lines.append(f"| {label} | {case['actual_statue_casts']} | {case['total_draws_in_cast_intervals']} | {case['total_animar_counters_in_cast_intervals']} | {case['zero_material_intervals']} |")
    lines.extend(["","**Scope:** Intervals partition real events from each accepted Statue cast",
      "up to but not including the next accepted same-seat Statue cast (last",
      "interval ends with original turn). Draws and counters are observed",
      "material gains, not a causal proof they all came from Statue. Do not",
      "call a long sequence wasted merely because it did not kill with Purphoros.",
      f"Original complete R1/R6 game budget still 10/12; 2 reserved; R2 new games 0.",
      ""])
    (OUT/"REPORT.md").write_text("\n".join(lines))
    print("MANUAL_RACE_R2_ALL_57_ORIGINAL_CHAINS_GREEN "+
          json.dumps(totals,sort_keys=True)+" NO_NEW_GAMES")

if __name__=="__main__":
    main()
