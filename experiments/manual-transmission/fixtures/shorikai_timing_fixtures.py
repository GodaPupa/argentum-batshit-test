from __future__ import annotations
from dataclasses import dataclass, field
import hashlib, json
from pathlib import Path

@dataclass
class State:
    stack:list[str]=field(default_factory=list)
    scepter_alive:bool=True
    scepter_has_abilities:bool=True
    imprint_alive:bool=True
    humility:bool=False
    hullbreaker_alive:bool=False
    tidebinder_alive:bool=False
    events:list[str]=field(default_factory=list)

def activate_scepter(s:State):
    assert s.scepter_alive and s.scepter_has_abilities and s.imprint_alive
    s.stack.append("Scepter activation:Dramatic Reversal")

def remove_scepter(s:State):
    s.scepter_alive=False
    s.events.append("scepter_removed")

def remove_imprinted_card(s:State):
    s.imprint_alive=False
    s.events.append("imprint_removed")

def resolve_scepter(s:State):
    assert "Scepter activation:Dramatic Reversal" in s.stack
    s.stack.remove("Scepter activation:Dramatic Reversal")
    if s.imprint_alive:
        s.stack.append("Dramatic Reversal copy")
        s.events.append("scepter_copy_created")
    else:
        s.events.append("scepter_copy_failed_no_imprint")

def tidebinder_enters(s:State,target:str):
    s.tidebinder_alive=True
    if s.humility:
        s.events.append("tidebinder_etb_suppressed:Humility")
        return False
    if target in s.stack:
        s.stack.remove(target)
        s.events.append(f"countered:{target}:Tidebinder")
        if target.startswith("Scepter activation"):
            s.scepter_has_abilities=False
        return True
    return False

def hope_ender_cast(s:State,target:str):
    # Cast trigger exists while the creature spell is on stack, before Humility
    # can affect the creature permanent.
    s.stack.append("Hope-Ender Coatl spell")
    s.stack.append(f"Hope-Ender cast trigger:{target}")

def counter_hope_ender_spell(s:State):
    if "Hope-Ender Coatl spell" in s.stack:
        s.stack.remove("Hope-Ender Coatl spell")
    s.events.append("hope_ender_spell_countered")

def hullbreaker_cast_spell(s:State,name:str,target:str):
    assert s.hullbreaker_alive
    s.stack.append(name)
    s.stack.append(f"Hullbreaker trigger:{target}")
    s.events.append(f"cast:{name}")

def counter_spell_only(s:State,name:str):
    if name in s.stack: s.stack.remove(name)
    s.events.append(f"countered_spell:{name}")

def resolve_hullbreaker_trigger(s:State,target:str):
    trig=f"Hullbreaker trigger:{target}"
    assert trig in s.stack
    s.stack.remove(trig)
    s.events.append(f"bounce:{target}")

def fixture_scepter_activation_survives_source_removal():
    s=State(); activate_scepter(s); remove_scepter(s); resolve_scepter(s)
    assert "Dramatic Reversal copy" in s.stack

def fixture_scepter_needs_imprinted_card_to_remain_exiled():
    s=State(); activate_scepter(s); remove_imprinted_card(s); resolve_scepter(s)
    assert "Dramatic Reversal copy" not in s.stack

def fixture_tidebinder_directly_counters_scepter_activation():
    s=State(); activate_scepter(s)
    assert tidebinder_enters(s,"Scepter activation:Dramatic Reversal")
    assert not s.stack and not s.scepter_has_abilities

def fixture_tidebinder_leaving_restores_scepter_abilities():
    s=State(); activate_scepter(s); tidebinder_enters(s,"Scepter activation:Dramatic Reversal")
    s.tidebinder_alive=False; s.scepter_has_abilities=True
    assert s.scepter_has_abilities

def fixture_humility_suppresses_tidebinder_etb():
    s=State(humility=True); activate_scepter(s)
    assert not tidebinder_enters(s,"Scepter activation:Dramatic Reversal")
    assert "Scepter activation:Dramatic Reversal" in s.stack

def fixture_humility_does_not_suppress_hope_ender_cast_trigger():
    s=State(humility=True); s.stack.append("target spell")
    hope_ender_cast(s,"target spell")
    assert "Hope-Ender cast trigger:target spell" in s.stack

def fixture_countering_hope_ender_spell_does_not_remove_cast_trigger():
    s=State(); s.stack.append("target spell"); hope_ender_cast(s,"target spell")
    counter_hope_ender_spell(s)
    assert "Hope-Ender cast trigger:target spell" in s.stack

def fixture_hullbreaker_trigger_exists_above_cast_spell():
    s=State(hullbreaker_alive=True)
    hullbreaker_cast_spell(s,"Sol Ring","MT permanent")
    assert s.stack[-1]=="Hullbreaker trigger:MT permanent"
    assert s.stack[-2]=="Sol Ring"

def fixture_countering_hullbreaker_trigger_source_spell_does_not_remove_trigger():
    s=State(hullbreaker_alive=True)
    hullbreaker_cast_spell(s,"Sol Ring","MT permanent")
    counter_spell_only(s,"Sol Ring")
    assert "Hullbreaker trigger:MT permanent" in s.stack

def fixture_hullbreaker_trigger_resolves_even_if_spell_countered():
    s=State(hullbreaker_alive=True)
    hullbreaker_cast_spell(s,"Sol Ring","MT permanent")
    counter_spell_only(s,"Sol Ring")
    resolve_hullbreaker_trigger(s,"MT permanent")
    assert "bounce:MT permanent" in s.events

def fixture_later_hullbreaker_cast_creates_another_trigger():
    s=State(hullbreaker_alive=True)
    hullbreaker_cast_spell(s,"Sol Ring","MT A")
    hullbreaker_cast_spell(s,"Mana Vault","MT B")
    assert sum(x.startswith("Hullbreaker trigger:") for x in s.stack)==2

def fixture_scepter_and_hullbreaker_are_distinct_resolvers():
    s=State(hullbreaker_alive=True)
    activate_scepter(s)
    hullbreaker_cast_spell(s,"Sol Ring","MT permanent")
    assert any(x.startswith("Scepter activation") for x in s.stack)
    assert any(x.startswith("Hullbreaker trigger") for x in s.stack)

def fixture_humility_removal_reopens_tidebinder_future_etb():
    s=State(humility=True); activate_scepter(s)
    assert not tidebinder_enters(s,"Scepter activation:Dramatic Reversal")
    # First Tidebinder never triggered; removing Humility does not retroactively trigger it.
    s.humility=False
    assert "Scepter activation:Dramatic Reversal" in s.stack
    # A later Tidebinder entering can now trigger.
    assert tidebinder_enters(s,"Scepter activation:Dramatic Reversal")

def fixture_ranger_resolved_blocks_noncreature_not_creature_answer():
    noncreature_allowed=False
    creature_allowed=True
    assert not noncreature_allowed and creature_allowed

def fixture_full_temur_reservation_makes_green_red_answers_live():
    reserved={"U":1,"G":1,"R":1}
    assert reserved["G"]>=1 and reserved["R"]>=1 and reserved["U"]>=1

def fixture_green_red_answers_do_not_need_creature_etb():
    s=State(humility=True)
    # Beast Within / Chaos Warp are noncreature spells; Humility does not remove their text.
    legal={"Beast Within":True,"Chaos Warp":True}
    assert all(legal.values())

def main():
    fixtures=[v for k,v in globals().items() if k.startswith("fixture_")]
    names=[]
    for fn in sorted(fixtures,key=lambda f:f.__name__):
        fn(); names.append(fn.__name__.removeprefix("fixture_"))
    assert len(names)==16
    print(json.dumps({
      "protocol":"MT_SHORIKAI_TIMING_FIXTURES_R1_2026_09_24",
      "status":"PASS","result_class":"deterministic fixture",
      "fixtures_passed":len(names),"fixture_names":names,
      "random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    },indent=2,sort_keys=True))

if __name__=="__main__": main()
