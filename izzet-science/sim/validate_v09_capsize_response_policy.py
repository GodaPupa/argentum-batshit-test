#!/usr/bin/env python3
"""Seed-free Phase-19 validation of opponent events and response selection."""
from __future__ import annotations

import hashlib
from pathlib import Path

from capsize_interaction import PermanentTarget,cast_and_resolve_capsize
from capsize_response_policy import ResponseEvent,select_capsize_response
import mana_harness as harness


ROOT=Path(__file__).resolve().parents[2]
CONTROL=ROOT/"izzet-science/v0.7-control.md"
CONTROL_SHA256="726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"


def event(event_id,event_class,target_class,legal=True):
    return ResponseEvent(event_id,event_class,target_class,legal)


def state_with_capsize(lands,guildmage=False):
    state=harness.DevState(["Capsize"])
    state.turn=8
    if guildmage:
        state.battlefield.append(
            {"card":"Izzet Guildmage","tapped":False,"entered":2})
    state.battlefield.extend(
        {"card":card,"tapped":False,"entered":1} for card in lands)
    return state


def expect_rejection(callback):
    try: callback()
    except (ValueError,TypeError): return
    raise AssertionError("accepted invalid response-policy input")


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest()!=CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")

    tempo=event("tempo","tempo_only","opposing_permanent")
    loss=event("loss","imminent_loss","opposing_permanent")
    lock=event("lock","next_main_lock","opposing_permanent")
    rescue=event("rescue","guildmage_combo_removal","own_guildmage")
    commander=event("commander","imminent_loss","opposing_commander")
    illegal=event("illegal","imminent_loss","opposing_permanent",False)

    assert select_capsize_response(
        [loss],one_shot_ready=False,buyback_ready=False,
        guildmage_battlefield=False,combo_lethal_now=False).action=="pass"
    assert select_capsize_response(
        [tempo],one_shot_ready=True,buyback_ready=True,
        guildmage_battlefield=True,combo_lethal_now=True).reason=="no_qualified_event"
    normal=select_capsize_response(
        [loss],one_shot_ready=True,buyback_ready=False,
        guildmage_battlefield=False,combo_lethal_now=False)
    assert normal.action=="cast_normal" and not normal.buyback
    retained=select_capsize_response(
        [loss],one_shot_ready=True,buyback_ready=True,
        guildmage_battlefield=False,combo_lethal_now=False)
    assert retained.action=="cast_buyback" and retained.buyback
    assert select_capsize_response(
        [rescue],one_shot_ready=True,buyback_ready=False,
        guildmage_battlefield=True,combo_lethal_now=False).action=="pass"
    assert select_capsize_response(
        [rescue],one_shot_ready=True,buyback_ready=False,
        guildmage_battlefield=True,combo_lethal_now=True).selected_event_id=="rescue"
    assert select_capsize_response(
        [illegal,tempo],one_shot_ready=True,buyback_ready=False,
        guildmage_battlefield=True,combo_lethal_now=True).action=="pass"

    priority=select_capsize_response(
        [lock,rescue,loss],one_shot_ready=True,buyback_ready=True,
        guildmage_battlefield=True,combo_lethal_now=True)
    assert priority.selected_event_id=="loss"
    tie=select_capsize_response(
        [event("z","next_main_lock","opposing_permanent"),
         event("a","next_main_lock","opposing_commander")],
        one_shot_ready=True,buyback_ready=False,
        guildmage_battlefield=False,combo_lethal_now=False)
    assert tie.selected_event_id=="a"

    normal_state=state_with_capsize(["Island","Island","Mountain"])
    normal_target=PermanentTarget("Hostile permanent")
    normal_result=cast_and_resolve_capsize(
        normal_state,normal_target,buyback=normal.buyback)
    assert normal_result.resolved and normal_result.spell_zone=="graveyard"
    retained_state=state_with_capsize(
        ["Island","Island","Island","Island","Mountain","Mountain"])
    retained_target=PermanentTarget("Hostile permanent")
    retained_result=cast_and_resolve_capsize(
        retained_state,retained_target,buyback=retained.buyback)
    assert retained_result.resolved and retained_result.spell_zone=="hand"

    for destination in ("hand","command"):
        commander_state=state_with_capsize(["Island","Island","Mountain"])
        target=PermanentTarget("Opposing commander",is_commander=True)
        decision=select_capsize_response(
            [commander],one_shot_ready=True,buyback_ready=False,
            guildmage_battlefield=False,combo_lethal_now=False)
        result=cast_and_resolve_capsize(
            commander_state,target,buyback=decision.buyback,
            commander_destination=destination)
        assert result.resolved and result.commander_destination==destination

    for failure in ("countered","illegal"):
        failure_state=state_with_capsize(
            ["Island","Island","Island","Island","Mountain","Mountain"])
        target=PermanentTarget("Hostile permanent")
        result=cast_and_resolve_capsize(
            failure_state,target,buyback=True,
            countered=failure=="countered",
            target_still_legal=failure!="illegal")
        assert result.cast and not result.resolved and not result.bought_back
        assert result.spell_zone=="graveyard" and target.zone=="battlefield"

    event_sets=((),(tempo,),(loss,),(lock,),(rescue,),(commander,),
                (tempo,illegal,lock,rescue,loss))
    readiness=((False,False),(True,False),(True,True))
    exhaustive=0
    for events in event_sets:
        for one_shot,buyback in readiness:
            for guildmage in (False,True):
                for lethal in (False,True):
                    first=select_capsize_response(
                        events,one_shot_ready=one_shot,buyback_ready=buyback,
                        guildmage_battlefield=guildmage,combo_lethal_now=lethal)
                    second=select_capsize_response(
                        events,one_shot_ready=one_shot,buyback_ready=buyback,
                        guildmage_battlefield=guildmage,combo_lethal_now=lethal)
                    assert first==second
                    exhaustive+=1
    assert exhaustive==84

    invalid=(
        lambda:select_capsize_response([],one_shot_ready=False,buyback_ready=True,
            guildmage_battlefield=False,combo_lethal_now=False),
        lambda:select_capsize_response([loss,loss],one_shot_ready=True,buyback_ready=False,
            guildmage_battlefield=False,combo_lethal_now=False),
        lambda:select_capsize_response([event("bad id","tempo_only","opposing_permanent")],
            one_shot_ready=True,buyback_ready=False,guildmage_battlefield=False,
            combo_lethal_now=False),
        lambda:select_capsize_response([event("x","unknown","opposing_permanent")],
            one_shot_ready=True,buyback_ready=False,guildmage_battlefield=False,
            combo_lethal_now=False),
        lambda:select_capsize_response([event("x","guildmage_combo_removal","opposing_permanent")],
            one_shot_ready=True,buyback_ready=False,guildmage_battlefield=True,
            combo_lethal_now=True),
        lambda:select_capsize_response([event("x","tempo_only","own_guildmage")],
            one_shot_ready=True,buyback_ready=False,guildmage_battlefield=True,
            combo_lethal_now=True),
        lambda:select_capsize_response("events",one_shot_ready=True,buyback_ready=False,
            guildmage_battlefield=False,combo_lethal_now=False),
        lambda:select_capsize_response([],one_shot_ready=1,buyback_ready=False,
            guildmage_battlefield=False,combo_lethal_now=False),
    )
    for callback in invalid: expect_rejection(callback)

    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-19-opponent-response-policy")
    print("event_classes=imminent_loss,guildmage_combo_removal,next_main_lock,tempo_only")
    print("priority=imminent_loss,guildmage_combo_removal,next_main_lock")
    print("exhaustive_policy_states=84")
    print("invalid_inputs_rejected=8")
    print("sampled_games=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE19_SEED_FREE_VALIDATED")


if __name__=="__main__":
    main()
