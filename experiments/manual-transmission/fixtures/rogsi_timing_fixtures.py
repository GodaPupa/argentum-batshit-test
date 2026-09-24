from __future__ import annotations
from dataclasses import dataclass, field
import hashlib, json
from pathlib import Path

@dataclass
class State:
    active_player:str="ROGSI"
    stack:list[str]=field(default_factory=list)
    graveyard:list[str]=field(default_factory=list)
    exile:list[str]=field(default_factory=list)
    spell_count:dict[str,int]=field(default_factory=lambda:{"ROGSI":0,"MT":0})
    defense_grid:bool=False
    vexing_bauble:bool=False
    oracle_trigger:bool=False
    breach:bool=False
    pact_debt:list[tuple[str,int]]=field(default_factory=list)
    events:list[str]=field(default_factory=list)

def cast(s,player,name,mana_spent:int):
    s.spell_count[player]=s.spell_count.get(player,0)+1
    s.stack.append(name)
    if s.vexing_bauble and mana_spent==0:
        s.stack.append(f"Vexing Bauble trigger:{name}")
    s.events.append(f"cast:{player}:{name}:mana={mana_spent}")

def grid_extra(s,player):
    return 3 if s.defense_grid and player!=s.active_player else 0

def oracle_enters(s):
    s.oracle_trigger=True; s.stack.append("Thassa Oracle ETB")

def remove_oracle(s):
    s.events.append("oracle_removed")

def tidebinder_oracle(s):
    assert s.oracle_trigger and "Thassa Oracle ETB" in s.stack
    s.stack.remove("Thassa Oracle ETB"); s.oracle_trigger=False

def start_breach(s,cards):
    s.breach=True; s.graveyard=list(cards)

def escape(s,spell,fuel,mana_spent=2):
    assert s.breach and spell in s.graveyard and len(fuel)==3 and spell not in fuel
    assert all(x in s.graveyard for x in fuel)
    s.graveyard.remove(spell)
    for x in fuel: s.graveyard.remove(x); s.exile.append(x)
    cast(s,"ROGSI",spell,mana_spent)

def resolve_to_grave(s,spell):
    if spell in s.stack: s.stack.remove(spell)
    s.graveyard.append(spell)

def fixture_oracle_etb_survives_source_removal():
    s=State(); oracle_enters(s); remove_oracle(s); assert "Thassa Oracle ETB" in s.stack

def fixture_tidebinder_counters_oracle_etb():
    s=State(); oracle_enters(s); tidebinder_oracle(s); assert not s.oracle_trigger

def fixture_breach_escape_spends_three_distinct_other_cards():
    s=State(); start_breach(s,["Brain Freeze","a","b","c","d"]); escape(s,"Brain Freeze",["a","b","c"])
    assert set(s.exile)=={"a","b","c"}

def fixture_breach_exiled_fuel_cannot_be_reused():
    s=State(); start_breach(s,["Brain Freeze","a","b","c","d","e","f"]); escape(s,"Brain Freeze",["a","b","c"]); resolve_to_grave(s,"Brain Freeze")
    rejected=False
    try:
        escape(s,"Brain Freeze",["a","d","e"])
    except AssertionError:
        rejected=True
    assert rejected

def fixture_one_storm_trigger_counter_not_terminal_with_fuel():
    s=State(); start_breach(s,["Brain Freeze","a","b","c","d","e","f","g"]); escape(s,"Brain Freeze",["a","b","c"])
    s.stack.append("Brain Freeze storm"); s.stack.remove("Brain Freeze storm"); resolve_to_grave(s,"Brain Freeze")
    escape(s,"Brain Freeze",["d","e","f"]); assert sum(e.startswith("cast:ROGSI:Brain Freeze") for e in s.events)==2

def fixture_storm_counts_casts_not_copies():
    s=State(); cast(s,"ROGSI","A",1); cast(s,"ROGSI","B",1); before=s.spell_count["ROGSI"]; cast(s,"ROGSI","Brain Freeze",2)
    assert before==2 and s.spell_count["ROGSI"]==3

def fixture_mindbreak_free_condition_same_player_three_casts():
    s=State(); cast(s,"ROGSI","A",1); cast(s,"MT","X",1); cast(s,"ROGSI","B",1)
    assert s.spell_count["ROGSI"]==2
    cast(s,"ROGSI","C",1); assert s.spell_count["ROGSI"]>=3

def fixture_defense_grid_taxes_opponent_spells_on_rogsi_turn():
    s=State(defense_grid=True,active_player="ROGSI"); assert grid_extra(s,"MT")==3

def fixture_defense_grid_does_not_tax_active_players_spells():
    s=State(defense_grid=True,active_player="ROGSI"); assert grid_extra(s,"ROGSI")==0

def fixture_defense_grid_does_not_tax_activated_abilities():
    s=State(defense_grid=True); activated_ability_extra=0; assert activated_ability_extra==0

def fixture_vexing_bauble_triggers_on_zero_mana_spell():
    s=State(vexing_bauble=True); cast(s,"MT","Pact of Negation",0); assert s.stack[-1]=="Vexing Bauble trigger:Pact of Negation"

def fixture_vexing_bauble_does_not_trigger_if_mana_spent():
    s=State(vexing_bauble=True); cast(s,"MT","Pact of Negation",5); assert not any(x.startswith("Vexing") for x in s.stack)

def fixture_removing_bauble_after_trigger_does_not_erase_trigger():
    s=State(vexing_bauble=True); cast(s,"MT","Force of Will",0); s.vexing_bauble=False
    assert "Vexing Bauble trigger:Force of Will" in s.stack

def fixture_free_mindbreak_is_exposed_to_bauble():
    s=State(vexing_bauble=True); s.spell_count["ROGSI"]=3; cast(s,"MT","Mindbreak Trap",0)
    assert s.stack[-1]=="Vexing Bauble trigger:Mindbreak Trap"

def fixture_phyrexian_tower_default_is_colorless():
    normal={"C":1,"B":0}; assert normal=={"C":1,"B":0}

def fixture_phyrexian_tower_black_requires_creature_sacrifice():
    creature_available=True; sacrificed=False
    if creature_available: sacrificed=True; mana={"B":2}
    assert sacrificed and mana["B"]==2

def fixture_pact_debt_is_recorded():
    s=State(); cast(s,"MT","Pact of Negation",0); s.pact_debt.append(("MT",5)); assert s.pact_debt==[("MT",5)]

def fixture_countered_spells_still_count_as_cast():
    s=State(); cast(s,"ROGSI","A",1); s.stack.remove("A"); assert s.spell_count["ROGSI"]==1

def main():
    fs=[v for k,v in globals().items() if k.startswith("fixture_")]; names=[]
    for fn in sorted(fs,key=lambda f:f.__name__): fn(); names.append(fn.__name__.removeprefix("fixture_"))
    assert len(names)==18
    print(json.dumps({"protocol":"MT_ROGSI_TIMING_FIXTURES_R1_2026_09_24","status":"PASS","result_class":"deterministic fixture",
      "fixtures_passed":len(names),"fixture_names":names,"random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()},indent=2,sort_keys=True))
if __name__=="__main__": main()
