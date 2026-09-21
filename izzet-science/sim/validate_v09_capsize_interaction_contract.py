#!/usr/bin/env python3
"""Seed-free Phase-16 validation for paired fixed-event interaction estimands."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path

from audit_v09_capsize_interaction_output import loads_strict
import mana_harness as harness
from paired_capsize_interaction_contract import (
    EVENT_MODEL,METRICS,aggregate_paired_interaction_games,
    validate_interaction_summary,
)


ROOT=Path(__file__).resolve().parents[2]
CONTROL=ROOT/"izzet-science/v0.7-control.md"
CONTROL_SHA256="726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
SOURCE="e"*40
MASTER=1
SAMPLES=2


def row(turn,game,policy):
    present=(game==0 and policy and turn>=3) or (game==1 and turn>=4)
    one_shot=present and turn>=4
    buyback=present and turn>=7
    commander=turn>=(3 if policy and game==0 else 2)
    lethal=turn>=(7 if game==0 else 8)
    out={
        "turn":turn,"response_window_capsize_present":present,
        "hostile_permanent_one_shot":one_shot,
        "hostile_permanent_buyback":buyback,
        "guildmage_self_rescue":one_shot and commander,
        "opposing_commander_to_hand":one_shot,
        "opposing_commander_to_command":one_shot,
        "combo_lethal":lethal,"lethal_by_now":lethal,
        "commander_battlefield":commander,
    }
    if policy:
        event=game==0 and turn in {2,5}
        out.update({"capsize_tutor_used":event,"capsize_tutor_found":event,
                    "capsize_scroll_used":event and turn==2,
                    "capsize_drift_used":event and turn==5})
    return out


def expect_rejection(summary,mutator):
    bad=copy.deepcopy(summary)
    mutator(bad)
    try:
        validate_interaction_summary(bad,SOURCE,MASTER,SAMPLES)
    except (ValueError,KeyError,TypeError):
        return
    raise AssertionError("accepted adversarial interaction summary")


def main():
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest()!=CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    _,cards=harness.parse_deck(CONTROL)
    if len(cards)!=99:
        raise SystemExit("accepted control deck count mismatch")

    normal=harness.DevState(["Capsize"]); normal.turn=4
    normal.battlefield=[{"card":c,"tapped":False,"entered":1}
                        for c in ("Island","Island","Mountain")]
    ready=harness.capsize_interaction_readiness(normal)
    assert ready["hostile_permanent_one_shot"]
    assert not ready["hostile_permanent_buyback"]
    assert ready["opposing_commander_to_hand"]==ready["opposing_commander_to_command"]
    assert not any(ready[key] for key in (
        "countered_resolves","countered_buyback_retained",
        "illegal_target_resolves","illegal_target_buyback_retained"))

    rescue=harness.DevState(["Capsize"]); rescue.turn=6
    rescue.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    rescue.battlefield += [{"card":c,"tapped":False,"entered":1}
                           for c in ("Island","Island","Island","Island","Mountain","Mountain")]
    rescued=harness.capsize_interaction_readiness(rescue)
    assert rescued["hostile_permanent_buyback"] and rescued["guildmage_self_rescue"]

    class FixtureRng:
        def shuffle(self,items): return None
        def choice(self,items): return items[0]
    front=["Capsize","Merchant Scroll","Snow-Covered Island",
           "Snow-Covered Mountain","Snow-Covered Island","Counterspell","Ponder"]
    ordered=list(cards)
    for card in front: ordered.remove(card)
    ordered=front+ordered
    plain=harness.simulate_one(ordered,FixtureRng(),10,False,True,False,True,False)
    observed=harness.simulate_one(ordered,FixtureRng(),10,False,True,False,True,True)
    for baseline,instrumented in zip(plain,observed):
        legacy={key:value for key,value in instrumented.items()
                if key not in harness.CAPSIZE_INTERACTION_ROW_KEYS}
        assert baseline==legacy
        assert set(harness.CAPSIZE_INTERACTION_ROW_KEYS)<=set(instrumented)
        assert (instrumented["opposing_commander_to_hand"]==
                instrumented["opposing_commander_to_command"]==
                instrumented["hostile_permanent_one_shot"])
        assert (not instrumented["hostile_permanent_buyback"] or
                instrumented["hostile_permanent_one_shot"])
        assert (not instrumented["guildmage_self_rescue"] or
                instrumented["hostile_permanent_one_shot"] and
                instrumented["commander_battlefield"])

    pairs=[]
    for game in range(SAMPLES):
        control=[row(turn,game,False) for turn in range(1,11)]
        policy=[row(turn,game,True) for turn in range(1,11)]
        pairs.append((game,harness.derive_paired_game_seed(MASTER,game),control,policy))
    summary=aggregate_paired_interaction_games(
        pairs,SOURCE,MASTER,SAMPLES,harness.derive_paired_game_seed)
    assert validate_interaction_summary(summary,SOURCE,MASTER,SAMPLES)
    assert summary["event_model"]==EVENT_MODEL
    assert tuple(summary["turns"][0]["metrics"])==METRICS

    mutators=(
        lambda s:s.pop("schema"),
        lambda s:s.__setitem__("source_sha","f"*40),
        lambda s:s["event_model"]["opposing_commander_destinations"].pop(),
        lambda s:s["event_model"]["countered"].__setitem__("buyback_retained",True),
        lambda s:s["turns"].pop(),
        lambda s:s["turns"][0].__setitem__("n",True),
        lambda s:s["turns"][0]["metrics"]["combo_lethal"].__setitem__("neither",99),
        lambda s:s["turns"][0]["metrics"]["combo_lethal"].__setitem__("delta",1),
        lambda s:s["turns"][0]["metrics"].pop("guildmage_self_rescue"),
        lambda s:s["turns"][4]["metrics"]["opposing_commander_to_hand"].update(
            {"both":0,"control_only":0,"policy_only":0,"neither":2,"delta":0}),
        lambda s:s["turns"][0].__setitem__("capsize_tutor_events_by_now",1),
    )
    for mutator in mutators: expect_rejection(summary,mutator)

    buyback_bad=copy.deepcopy(summary)
    buyback_bad["turns"][0]["metrics"]["hostile_permanent_buyback"].update(
        {"both":0,"control_only":0,"policy_only":1,"neither":1,"delta":1})
    expect_rejection(buyback_bad,lambda s:None)
    one_shot_bad=copy.deepcopy(summary)
    one_shot_bad["turns"][0]["metrics"]["hostile_permanent_one_shot"].update(
        {"both":0,"control_only":0,"policy_only":1,"neither":1,"delta":1})
    one_shot_bad["turns"][0]["metrics"]["opposing_commander_to_hand"].update(
        {"both":0,"control_only":0,"policy_only":1,"neither":1,"delta":1})
    one_shot_bad["turns"][0]["metrics"]["opposing_commander_to_command"].update(
        {"both":0,"control_only":0,"policy_only":1,"neither":1,"delta":1})
    expect_rejection(one_shot_bad,lambda s:None)
    rescue_bad=copy.deepcopy(summary)
    rescue_bad["turns"][0]["metrics"]["guildmage_self_rescue"].update(
        {"both":0,"control_only":0,"policy_only":1,"neither":1,"delta":1})
    expect_rejection(rescue_bad,lambda s:None)
    lethal_bad=copy.deepcopy(summary)
    lethal_bad["turns"][7]["metrics"]["lethal_by_now"].update(
        {"both":2,"control_only":0,"policy_only":0,"neither":0,"delta":0})
    lethal_bad["turns"][8]["metrics"]["lethal_by_now"].update(
        {"both":0,"control_only":0,"policy_only":0,"neither":2,"delta":0})
    expect_rejection(lethal_bad,lambda s:None)

    assert loads_strict(json.dumps(summary))==summary
    for payload in ('{"schema":"x","schema":"y"}','{"value":NaN}'):
        try: loads_strict(payload)
        except ValueError: pass
        else: raise AssertionError("accepted noncanonical JSON")

    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-16-interaction-contract")
    print("paired_metrics="+",".join(METRICS))
    print("adversarial_rejections=17")
    print("sampled_games=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE16_SEED_FREE_VALIDATED")


if __name__=="__main__":
    main()
