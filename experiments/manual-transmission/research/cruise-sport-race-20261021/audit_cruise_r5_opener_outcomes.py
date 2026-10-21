#!/usr/bin/env python3
"""CRUISE R5: source-pinned read-only original-game opener/outcome join.

Inputs: one immutable R4 40-hand diagnostic ZIP, ten immutable original R1/R6
game ZIPs, and frozen R5/R2 manifests. No GameInitializer, no action replay, no
mulligan redraw, no new game, no production or deck edits. RETROSPECTIVE.
"""
import collections
import hashlib
import json
import os
from pathlib import Path
import statistics
import zipfile

ROOT = Path("experiments/manual-transmission/research/cruise-sport-race-20261021")
INPUT = Path(os.environ.get("CRUISE_R5_INPUT", "cruise-r5-input"))
OUTPUT = Path(os.environ.get("CRUISE_R5_OUTPUT", "cruise-r5-evidence"))
CARD = "com.wingedsheep.engine.state.components.identity.CardComponent"
PLAYER_IDS = {f"manual-mirror-{i}" for i in range(4)}

def check(yes, message):
    if not yes:
        raise AssertionError(message)

def digest(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def original_hand(state, seat):
    hand = [
        ids for zone, ids in zip(state["zones"][::2],state["zones"][1::2])
        if zone.get("zoneType") == "Hand" and zone.get("ownerId") == seat
    ]
    check(len(hand)==1 and len(hand[0])==7, f"Nonoriginal hand {seat}")
    return [state["entities"][card_id][CARD]["name"] for card_id in hand[0]]

def summarize(rows):
    out={}
    for keep in (False,True):
        subset=[x for x in rows if x["hypothetical_keep"]==keep]
        casts=[x["first_cast_personal_turn"] for x in subset
               if x["first_cast_personal_turn"] is not None]
        out["would_keep" if keep else "would_mulligan"]={
            "seats":len(subset),
            "original_winners":sum(x["original_winner"] for x in subset),
            "seats_with_original_cast":len(casts),
            "seats_without_original_cast":len(subset)-len(casts),
            "median_original_first_cast_personal_turn_among_casters":statistics.median(casts) if casts else None,
            "original_one_land_openers":sum(x["printed_land_count"]==1 for x in subset)
        }
    return out

def main():
    m=json.loads((ROOT/"CRUISE_R5_JOIN_MANIFEST.json").read_text())
    original_m=json.loads((ROOT/"CRUISE_R2_ZONE_MANIFEST.json").read_text())
    check(m["schema"]=="ARGENTUM_CRUISE_R5_PREDECLARED_RETROSPECTIVE_JOIN_V1","Wrong attribution protocol")
    check(m["guardrails"]["new_games"]==0 and m["guardrails"]["reserved"]==2, "Game budget breach")
    check(len(original_m["archives"])==10 and m["r4"]["run_id"]==39645091191,"Original universe changed")
    r4_zip=INPUT/"r4-original.zip"
    check(r4_zip.is_file() and digest(r4_zip)==m["r4"]["zip_sha256"],"R4 artifact ZIP mismatch")
    with zipfile.ZipFile(r4_zip) as z:
        p=m["r4"]["original_diagnostic_file"]
        check(z.namelist().count(p)==1,"No unique original R4 observations")
        r4=json.loads(z.read(p))
    check(r4["schema"]=="MANUAL_CRUISE_R4_ORIGINAL_HAND_CONTROLLER_DIAGNOSTIC_V1","Wrong R4 data")
    check(r4["sample_size"]==40 and len(r4["observations"])==40,"40 R4 decisions required")
    original_predictions={}
    for row in r4["observations"]:
        key=(row["seed"],row["arm"],row["seat"])
        check(key not in original_predictions,"Duplicate source R4 hand")
        check(row["originalHistoricalKept"] is True and row["originalHistoricalControllerInvoked"] is False,
              "Incorrectly attributed original mulligan")
        check(row["anyReplacementHandDrawn"] is False and row["simulatedFullGame"] is False,
              "R4 must have been read-only")
        check(row["realBotWouldKeepHypothetical"] is not row["realBotWouldMulliganHypothetical"],
              "Contradictory real-controller decision")
        original_predictions[key]=row
    check(len(original_predictions)==40,"R4 missing seats")
    results=[]
    run_files=[]
    for spec in original_m["archives"]:
        cohort,seed,arm=spec["cohort"],spec["seed"],spec["arm"]
        archive=INPUT/f"{cohort}-{arm}-{seed}-original.zip"
        check(archive.is_file() and digest(archive)==spec["sha256"],f"Original ZIP hash mismatch {archive}")
        outer="precision-r1-stage1-output/" if cohort=="R1" else "precision-r6-prospective-output/"
        base=outer+arm+"-seed-"+seed+"/"
        with zipfile.ZipFile(archive) as z:
            check(z.read(outer+"source-sha.txt").decode().strip()==spec["source_sha"],"Original game SHA drift")
            game=json.loads(z.read(base+"summary.json"))
            initial=json.loads(z.read(base+"initial-state.json"))
            init_metadata=json.loads(z.read(base+"initialization.json"))
            check(game["status"]=="COMPLETE" and game["gameOver"] is True and game["error"] is None,"Noncompleted game")
            check(game["submitted"]==game["accepted"] and game["accepted"]>=64,"Incomplete/rejected game")
            check(game["seed"]==int(seed) or str(game["seed"])==seed,"Seed mismatch")
            check(game["arm"]==arm and game["profile"]=="manual-transmission-r5-survival-opt-in","Wrong deck/profile")
            check(init_metadata["openingPolicy"]==
              "Four seats keep seed-derived initial seven, no smoother or mulligans","Original game invoked mulligans")
            check(set(initial["turnOrder"])==PLAYER_IDS, "Not a four-seat original")
            check(game["winner"] in PLAYER_IDS,"Invalid original winner")
            for seat in initial["turnOrder"]:
                key=(seed,arm,seat)
                check(key in original_predictions,f"Missing original R4 case {key}")
                predicted=original_predictions[key]
                hand=original_hand(initial,seat)
                check(hand==predicted["openingCards"],f"R4 hand provenance mismatch {key}")
                lands=sum("Land" in initial["entities"][card_id][CARD]["typeLine"]
                  for zone, ids in zip(initial["zones"][::2],initial["zones"][1::2])
                  if zone.get("zoneType")=="Hand" and zone.get("ownerId")==seat
                  for card_id in ids)
                check(lands==predicted["printedLandCount"],"Printed land discrepancy")
                first=game["firstCommanderPersonalTurn"].get(seat)
                casts=game["commanderCasts"].get(seat,0)
                check((first is not None)==(casts>0),"Commander cast count inconsistent")
                results.append({
                    "cohort":cohort,"seed":seed,"arm":arm,"seat":seat,
                    "historical_kept_initial_seven":True,
                    "historical_game_used_mulligans":False,
                    "hypothetical_keep":predicted["realBotWouldKeepHypothetical"],
                    "printed_land_count":lands,
                    "original_winner":game["winner"]==seat,
                    "first_cast_personal_turn":first,
                    "commander_casts":casts,
                    "opening_cards":hand
                })
            run_files.append({"cohort":cohort,"seed":seed,"arm":arm,"artifact":spec["id"],
              "archive_digest":spec["sha256"],"winner":game["winner"],
              "original_accepted_actions":game["accepted"]})
    check(len(results)==40 and len({(x["seed"],x["arm"],x["seat"]) for x in results})==40,
          "Full original 40-seat join missing/duplicate")
    check(sum(x["original_winner"] for x in results)==10,"Expect exactly ten original winners")
    keepcount=sum(x["hypothetical_keep"] for x in results)
    check(keepcount==28,"Previous real-controller decision count drifted")
    ones=[x for x in results if x["printed_land_count"]==1]
    check(len(ones)==10 and all(not x["hypothetical_keep"] for x in ones),"One-land R4 behavior changed")
    rejected_multiland=[x for x in results if x["printed_land_count"]>1 and not x["hypothetical_keep"]]
    check(len(rejected_multiland)==2, "Unexpected non-one-land rejection inventory")
    check(all(x["seed"]=="202610202141" and x["seat"]=="manual-mirror-1"
       and x["printed_land_count"]==2 for x in rejected_multiland),"Two-land pair mismatch")
    by_pair=collections.defaultdict(dict)
    for x in results:
        by_pair[x["seed"],x["seat"]][x["arm"]]=x
    check(len(by_pair)==20 and all(set(v)=={"a","control"} for v in by_pair.values()),
      "Invalid matched pairs")
    policy_disagreements=[
        {"seed":s,"seat":seat}
        for (s,seat),v in by_pair.items()
        if v["a"]["hypothetical_keep"] != v["control"]["hypothetical_keep"]
    ]
    a_case=next(x for x in rejected_multiland if x["arm"]=="a")
    c_case=next(x for x in rejected_multiland if x["arm"]=="control")
    check("Misty Rainforest" in a_case["opening_cards"] and
      "Evolving Wilds" in c_case["opening_cards"] and
      "Birds of Paradise" in a_case["opening_cards"],"Fetch candidate source hand missing")
    report={
      "schema":"ARGENTUM_CRUISE_R5_40_SEAT_RETROSPECTIVE_JOIN_REPORT_V1",
      "status":"NO_GAME_SOURCE_JOIN_COMPLETE",
      "cause_inference":"NOT_SUPPORTED",
      "matched_pair_independence":"NONE; paired hardware and four seats/game are correlated",
      "ten_exact_game_artifacts":run_files,
      "totals":{
        "original_games":10,"original_seat_hands":40,
        "historical_always_kept_sevens":40,
        "hypothetical_real_controller_keeps":keepcount,
        "hypothetical_real_controller_mulligans":len(results)-keepcount,
        "one_printed_land_hands":len(ones),
        "mulligan_recommendations_with_more_than_one_land":len(rejected_multiland),
        "paired_seed_seat_controller_disagreements":len(policy_disagreements),
        "original_game_winners":10,
        "original_accepted_actions":sum(x["original_accepted_actions"] for x in run_files),
        "new_full_games":0,
        "reserved_games":2
      },
      "strata":summarize(results),
      "arm_strata":{arm:summarize([x for x in results if x["arm"]==arm]) for arm in ("control","a")},
      "two_land_hypothetical_mulligans":rejected_multiland,
      "paired_controller_disagreements":policy_disagreements,
      "complete_40_seat_rows":results,
      "conclusion":"HOLD_DECK_AND_AI; ZERO_NEW_GAMES; TEST_FETCH_MANA_LEGALITY_WITH_ACTUAL_ENGINE_BEFORE_ANY_POLICY_CHANGE"
    }
    check(report["totals"]["original_accepted_actions"]==33440,"Original gameplay sample changed")
    OUTPUT.mkdir(parents=True,exist_ok=True)
    (OUTPUT/"ORIGINAL_40_SEAT_JOIN.json").write_text(json.dumps(report,indent=2)+"\n")
    f=report["strata"]["would_mulligan"]
    k=report["strata"]["would_keep"]
    narrative=[
      "# CRUISE R5 — original 40 opener-to-outcome retrospective",
      "",
      "**No new Commander games, no mulligan redraws, no changes to CONTROL/A, R5, CURRENT or main.**",
      "",
      "| Historical original-game property | Bot would mulligan | Bot would keep |",
      "|---|---:|---:|",
      f"| Seat-game observations | {f['seats']} | {k['seats']} |",
      f"| Original winners | {f['original_winners']} | {k['original_winners']} |",
      f"| Original Animar cast observed | {f['seats_with_original_cast']} | {k['seats_with_original_cast']} |",
      f"| Original game ended before any Animar cast | {f['seats_without_original_cast']} | {k['seats_without_original_cast']} |",
      "",
      "All ten original one-printed-land hands were hypothetically rejected by the real controller.",
      "One of these hands nevertheless won its *original forced-keep game*. That does not establish a good keep in general.",
      "Only one paired non-one-land opener was rejected in both arms: Opal Palace + Evolving Wilds",
      "(CONTROL) versus Opal Palace + Misty Rainforest (Challenger A), with Birds of Paradise.",
      "",
      "The paired keep/mulligan outputs were identical for 20 matched seat/seed pairs; this",
      "does not show the lands are equivalent at actual mana/payment level.",
      "",
      "**Decision:** Hold hardware and policy. Next consider source-bound single-turn fetch target,",
      "entry-tapped and paid-mana proof for the two-land pair, with no opponent autopass used as strategic evidence.",
      "",
      "This study is retrospective/nonblinded, pairs and seats are dependent, and a recommendation",
      "against an opening hand does not create an observable alternate-game winner.",
      ""
    ]
    (OUTPUT/"REPORT.md").write_text("\n".join(narrative))
    print("CRUISE_R5_40_SEAT_JOIN_PASS "+json.dumps(report["totals"],sort_keys=True))

if __name__=="__main__":
    main()
