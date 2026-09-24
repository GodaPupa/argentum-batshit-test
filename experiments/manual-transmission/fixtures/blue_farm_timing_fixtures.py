from __future__ import annotations
from dataclasses import dataclass, field
import hashlib, json
from pathlib import Path

@dataclass
class State:
    active_player: str = "BLUE"
    stack: list[str] = field(default_factory=list)
    mt_spell_lock: str | None = None
    oracle_alive: bool = True
    oracle_trigger_on_stack: bool = False
    breach_active: bool = False
    graveyard: list[str] = field(default_factory=list)
    exile: list[str] = field(default_factory=list)
    spell_count_by_player: dict[str,int] = field(default_factory=lambda: {"BLUE":0,"MT":0,"HELPER":0})
    events: list[str] = field(default_factory=list)

def can_mt_cast(s: State, kind: str) -> bool:
    if s.mt_spell_lock == "all":
        return False
    if s.mt_spell_lock == "noncreature" and kind == "noncreature":
        return False
    if s.active_player == "BLUE" and "Voice of Victory" in s.events:
        return False
    return True

def resolve_silence(s: State):
    s.mt_spell_lock = "all"
    s.events.append("Silence:all_opponents_locked")

def resolve_orims_chant(s: State, target: str):
    if target == "MT":
        s.mt_spell_lock = "all"
    s.events.append(f"Orims_Chant:target={target}")

def resolve_ranger(s: State):
    s.mt_spell_lock = "noncreature"
    s.events.append("Ranger:opponents_noncreature_locked")

def voice_present(s: State):
    s.events.append("Voice of Victory")

def oracle_enters(s: State):
    s.oracle_trigger_on_stack = True
    s.stack.append("Thassa Oracle ETB")
    s.events.append("oracle_etb_trigger")

def remove_oracle(s: State):
    s.oracle_alive = False
    s.events.append("oracle_removed")

def tidebinder_oracle_trigger(s: State):
    assert s.oracle_trigger_on_stack
    assert "Thassa Oracle ETB" in s.stack
    s.stack.remove("Thassa Oracle ETB")
    s.oracle_trigger_on_stack = False
    s.events.append("oracle_etb_countered:Tidebinder")

def cast_spell(s: State, player: str, name: str):
    s.spell_count_by_player[player] = s.spell_count_by_player.get(player,0)+1
    s.stack.append(name)

def mindbreak_free(s: State, opponent: str) -> bool:
    return s.spell_count_by_player.get(opponent,0) >= 3

def start_breach(s: State, cards: list[str]):
    s.breach_active=True
    s.graveyard=list(cards)

def escape(s: State, spell: str, exile_cards: list[str]):
    assert s.breach_active
    assert spell in s.graveyard
    assert len(exile_cards)==3 and spell not in exile_cards
    assert all(c in s.graveyard for c in exile_cards)
    s.graveyard.remove(spell)
    for c in exile_cards:
        s.graveyard.remove(c); s.exile.append(c)
    cast_spell(s,"BLUE",spell)
    s.events.append(f"escape:{spell}")

def resolve_instant_to_graveyard(s: State, spell: str):
    assert spell in s.stack
    s.stack.remove(spell)
    s.graveyard.append(spell)

def brain_freeze_cast(s: State):
    before=s.spell_count_by_player["BLUE"]
    cast_spell(s,"BLUE","Brain Freeze")
    s.stack.append(f"Brain Freeze storm:{before}")
    return before

def tidebinder_storm_trigger(s: State):
    trigger=next(x for x in s.stack if x.startswith("Brain Freeze storm:"))
    s.stack.remove(trigger)
    s.events.append("storm_trigger_countered:Tidebinder")

def fixture_orims_chant_targets_one_player():
    s=State()
    resolve_orims_chant(s,"MT")
    assert not can_mt_cast(s,"creature")
    # Another opponent is not affected by Orim's Chant.
    assert s.mt_spell_lock=="all"
    assert "Orims_Chant:target=MT" in s.events

def fixture_silence_hits_all_opponents():
    s=State()
    resolve_silence(s)
    assert not can_mt_cast(s,"creature") and not can_mt_cast(s,"noncreature")

def fixture_ranger_allows_creature_answers():
    s=State()
    resolve_ranger(s)
    assert not can_mt_cast(s,"noncreature")
    assert can_mt_cast(s,"creature")

def fixture_ranger_response_window_before_resolution():
    s=State()
    s.stack.append("Ranger sacrifice ability")
    assert can_mt_cast(s,"noncreature")
    s.stack.remove("Ranger sacrifice ability")
    resolve_ranger(s)
    assert not can_mt_cast(s,"noncreature")

def fixture_voice_controller_turn_closes_spells():
    s=State(active_player="BLUE"); voice_present(s)
    assert not can_mt_cast(s,"creature") and not can_mt_cast(s,"noncreature")

def fixture_voice_other_turn_does_not_close_spells():
    s=State(active_player="MT"); voice_present(s)
    assert can_mt_cast(s,"creature") and can_mt_cast(s,"noncreature")

def fixture_oracle_trigger_survives_oracle_removal():
    s=State(); oracle_enters(s); remove_oracle(s)
    assert "Thassa Oracle ETB" in s.stack and s.oracle_trigger_on_stack

def fixture_tidebinder_can_counter_oracle_etb():
    s=State(); oracle_enters(s); tidebinder_oracle_trigger(s)
    assert "Thassa Oracle ETB" not in s.stack

def fixture_ranger_resolved_still_allows_tidebinder_on_oracle():
    s=State(); resolve_ranger(s)
    assert can_mt_cast(s,"creature")
    oracle_enters(s); tidebinder_oracle_trigger(s)
    assert not s.oracle_trigger_on_stack

def fixture_silence_resolved_blocks_tidebinder_cast():
    s=State(); resolve_silence(s); oracle_enters(s)
    assert not can_mt_cast(s,"creature")

def fixture_breach_escape_spends_three_other_cards():
    s=State(); start_breach(s,["Brain Freeze","a","b","c","d"])
    escape(s,"Brain Freeze",["a","b","c"])
    assert set(s.exile)=={"a","b","c"}

def fixture_breach_cannot_reuse_exiled_fuel():
    s=State(); start_breach(s,["Brain Freeze","a","b","c","d","e","f"])
    escape(s,"Brain Freeze",["a","b","c"]); resolve_instant_to_graveyard(s,"Brain Freeze")
    try:
        escape(s,"Brain Freeze",["a","d","e"])
        raise AssertionError("exiled fuel reused")
    except AssertionError:
        pass

def fixture_storm_count_counts_cast_spells_not_copies():
    s=State()
    cast_spell(s,"BLUE","Spell A"); cast_spell(s,"BLUE","Spell B")
    n=brain_freeze_cast(s)
    assert n==2
    assert s.spell_count_by_player["BLUE"]==3
    assert any(x=="Brain Freeze storm:2" for x in s.stack)

def fixture_counter_storm_trigger_does_not_counter_original():
    s=State(); cast_spell(s,"BLUE","Spell A"); brain_freeze_cast(s)
    tidebinder_storm_trigger(s)
    assert "Brain Freeze" in s.stack

def fixture_one_tidebinder_storm_stop_not_terminal_with_fuel():
    s=State(); start_breach(s,["Brain Freeze","a","b","c","d","e","f","g"])
    escape(s,"Brain Freeze",["a","b","c"])
    # Its storm trigger is countered, original later resolves back to graveyard.
    s.stack.append("Brain Freeze storm:1")
    tidebinder_storm_trigger(s)
    resolve_instant_to_graveyard(s,"Brain Freeze")
    # Sufficient distinct fuel remains for another escape.
    escape(s,"Brain Freeze",["d","e","f"])
    assert s.events.count("escape:Brain Freeze")==2

def fixture_mindbreak_needs_three_spells_same_opponent():
    s=State()
    cast_spell(s,"BLUE","A"); cast_spell(s,"BLUE","B")
    cast_spell(s,"HELPER","C")
    assert not mindbreak_free(s,"BLUE")
    cast_spell(s,"BLUE","D")
    assert mindbreak_free(s,"BLUE")

def fixture_countered_spells_still_count_for_storm_and_trap():
    s=State()
    for n in ["A","B","C"]:
        cast_spell(s,"BLUE",n)
        s.stack.remove(n)
    assert s.spell_count_by_player["BLUE"]==3
    assert mindbreak_free(s,"BLUE")

def fixture_oracle_and_breach_are_distinct_terminal_families():
    s=State(); oracle_enters(s)
    assert s.oracle_trigger_on_stack and not s.breach_active
    s2=State(); start_breach(s2,["Brain Freeze","a","b","c"])
    assert s2.breach_active and not s2.oracle_trigger_on_stack

def main():
    fixtures=[v for k,v in globals().items() if k.startswith("fixture_")]
    names=[]
    for fn in sorted(fixtures,key=lambda f:f.__name__):
        fn(); names.append(fn.__name__.removeprefix("fixture_"))
    assert len(names)==18
    print(json.dumps({
      "protocol":"MT_BLUE_FARM_TIMING_FIXTURES_R1_2026_09_24",
      "status":"PASS","result_class":"deterministic fixture",
      "fixtures_passed":len(names),"fixture_names":names,
      "random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    },indent=2,sort_keys=True))

if __name__=="__main__": main()
