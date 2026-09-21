#!/usr/bin/env python3
from public_action_compiler import CompiledAction
from observed_action_behavior import PublicBehaviorContext, choose_observed_action

def A(action_id,surface,target=None,pay=("forest",)):
    return CompiledAction(action_id,"Observed Source",surface,target,pay,"public_legal")

def main():
    ctx=PublicBehaviorContext("guild",("combo-piece",),("persistent_restriction",),0,16)

    removal=A("remove-guild","targeted_creature_control","guild")
    lock=A("lock-main","persistent_restriction","combo-piece")
    control=A("other-control","targeted_creature_control","other")
    develop=A("ramp","battlefield_mana_source",None)
    tempo=A("tempo","tempo_change","other")

    d=choose_observed_action((develop,control,lock,removal,tempo),ctx)
    assert d.selected_action_id=="remove-guild" and d.reason=="public_guildmage_control"

    d2=choose_observed_action((develop,control,lock,tempo),ctx)
    assert d2.selected_action_id=="lock-main" and d2.reason=="public_next_main_lock"

    d3=choose_observed_action((develop,control,tempo),ctx)
    assert d3.selected_action_id=="other-control" and d3.reason=="public_control"

    # canonical tie-break and candidate-order invariance
    t1=A("a-control","targeted_creature_control","other")
    t2=A("b-control","targeted_creature_control","other")
    assert choose_observed_action((t2,t1),ctx)==choose_observed_action((t1,t2),ctx)
    assert choose_observed_action((t2,t1),ctx).selected_action_id=="a-control"

    assert choose_observed_action((),ctx).action=="pass"

    try:
        choose_observed_action((t1,t1),ctx)
        raise AssertionError("duplicate action accepted")
    except ValueError:
        pass

    terminal_ctx=PublicBehaviorContext("guild",(),(),15,16)
    pressure=A("trample","commander_trample",None)
    d4=choose_observed_action((removal,pressure),terminal_ctx)
    assert d4.selected_action_id=="trample" and d4.reason=="public_terminal_commander_pressure"

    # deterministic replay
    assert choose_observed_action((removal,control),ctx)==choose_observed_action((removal,control),ctx)

    print("V09_PHASE27_OBSERVED_ACTION_BEHAVIOR_VALIDATION_PASS")

if __name__=="__main__":
    main()
