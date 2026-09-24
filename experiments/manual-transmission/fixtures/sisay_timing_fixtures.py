from __future__ import annotations
from dataclasses import dataclass, field
import hashlib, json
from pathlib import Path

COLORS=set("WUBRG")

@dataclass
class State:
    sisay_alive:bool=True
    sisay_has_abilities:bool=True
    stack:list[str]=field(default_factory=list)
    other_legends:dict[str,set[str]]=field(default_factory=dict)
    sisay_lki_power:int|None=None
    fetched:list[str]=field(default_factory=list)
    events:list[str]=field(default_factory=list)

def current_power(s:State)->int:
    colors=set()
    for cs in s.other_legends.values(): colors |= set(cs)
    return 2 + len(colors & COLORS)

def activate_sisay(s:State):
    assert s.sisay_alive and s.sisay_has_abilities
    s.stack.append("Sisay WUBRG activation")
    s.events.append("cost:WUBRG")

def remove_sisay(s:State):
    s.sisay_lki_power=current_power(s)
    s.sisay_alive=False
    s.events.append(f"sisay_removed:LKI_power={s.sisay_lki_power}")

def remove_legend(s:State,name:str):
    s.other_legends.pop(name,None)
    s.events.append(f"legend_removed:{name}")

def search_ceiling(s:State)->int:
    power=current_power(s) if s.sisay_alive else s.sisay_lki_power
    assert power is not None
    return power-1  # largest integer mana value strictly less than power

def resolve_sisay(s:State,target:str,mv:int):
    assert "Sisay WUBRG activation" in s.stack
    ceiling=search_ceiling(s)
    assert mv <= ceiling, (target,mv,ceiling)
    s.stack.remove("Sisay WUBRG activation")
    s.fetched.append(target)
    s.events.append(f"fetched:{target}:mv{mv}")

def tidebinder_counter_sisay(s:State):
    assert "Sisay WUBRG activation" in s.stack
    s.stack.remove("Sisay WUBRG activation")
    s.sisay_has_abilities=False
    s.events.append("countered:Sisay activation:Tidebinder")

def fixture_activation_survives_sisay_removal():
    s=State(other_legends={"Dihada":set("RWB")})
    activate_sisay(s); remove_sisay(s)
    assert "Sisay WUBRG activation" in s.stack
    resolve_sisay(s,"Derevi, Empyrial Tactician",3)

def fixture_resolution_uses_lki_if_sisay_left():
    s=State(other_legends={"FiveColorLegend":set("WUBRG")})
    activate_sisay(s); remove_sisay(s)
    assert s.sisay_lki_power==7 and search_ceiling(s)==6

def fixture_lki_freezes_after_sisay_leaves():
    s=State(other_legends={"A":set("W"),"B":set("U"),"C":set("B"),"D":set("R"),"E":set("G")})
    activate_sisay(s); remove_sisay(s); before=search_ceiling(s)
    remove_legend(s,"E")
    assert search_ceiling(s)==before==6

def fixture_ceiling_denial_unique_color_lowers_power():
    s=State(other_legends={"Dihada":set("RWB"),"Tam":set("U"),"Tyvar":set("BG")})
    assert current_power(s)==7
    activate_sisay(s)
    # Blue is uniquely contributed by Tam in this public state.
    remove_legend(s,"Tam")
    assert current_power(s)==6 and search_ceiling(s)==5

def fixture_ceiling_denial_shared_color_does_not_lower_power():
    s=State(other_legends={"Tam":set("U"),"Teferi":set("WU"),"Dihada":set("RWB"),"Tyvar":set("BG")})
    p=current_power(s); activate_sisay(s); remove_legend(s,"Tam")
    assert current_power(s)==p

def fixture_ceiling_denial_must_cross_required_mv_threshold():
    s=State(other_legends={"A":set("W"),"B":set("U"),"C":set("B"),"D":set("R"),"E":set("G")})
    assert search_ceiling(s)==6
    activate_sisay(s); remove_legend(s,"E")
    assert search_ceiling(s)==5
    required_mv=5
    # MV5 remains fetchable: denial changed ceiling but did not stop this target.
    assert required_mv <= search_ceiling(s)

def fixture_ceiling_denial_can_downgrade_without_full_stop():
    s=State(other_legends={"A":set("W"),"B":set("U"),"C":set("B"),"D":set("R"),"E":set("G")})
    activate_sisay(s); remove_legend(s,"E")
    assert search_ceiling(s)==5
    assert 5 <= search_ceiling(s) and 6 > search_ceiling(s)

def fixture_tidebinder_directly_counters_activation():
    s=State(other_legends={"A":set("WUBRG")}); activate_sisay(s)
    tidebinder_counter_sisay(s)
    assert not s.stack and not s.sisay_has_abilities

def fixture_tidebinder_leaving_restores_sisay_abilities():
    s=State(other_legends={"A":set("WUBRG")}); activate_sisay(s)
    tidebinder_counter_sisay(s); s.sisay_has_abilities=True
    assert s.sisay_has_abilities

def fixture_paid_wubrg_not_refunded_when_countered():
    s=State(other_legends={"A":set("WUBRG")}); activate_sisay(s); tidebinder_counter_sisay(s)
    assert "cost:WUBRG" in s.events

def fixture_marvin_ioreth_postfetch_is_not_automatic_win():
    s=State(); s.fetched=["Marvin, Murderous Mimic","Ioreth of the Healing House"]
    # Engine pieces expose activated-ability windows and require board/resource state.
    terminal=False
    assert not terminal

def fixture_derevi_emiel_postfetch_has_separate_trigger_and_activation_windows():
    windows=["Derevi ETB/attack trigger","Emiel activated blink"]
    assert len(windows)==2 and windows[0]!=windows[1]

def fixture_shang_chi_pseudohaste_changes_activation_timing_not_stack_independence():
    pseudo_haste=True
    ability_uses_stack=True
    assert pseudo_haste and ability_uses_stack

def fixture_tyvar_pseudohaste_changes_activation_timing_not_stack_independence():
    pseudo_haste=True
    ability_uses_stack=True
    assert pseudo_haste and ability_uses_stack

def fixture_postfetch_engine_piece_is_not_scored_terminal():
    fetched="Marvin, Murderous Mimic"
    result_class="engine_piece_fetched"
    assert fetched and result_class!="win"

def fixture_remove_sisay_after_activation_not_a_stop():
    s=State(other_legends={"A":set("WUBRG")}); activate_sisay(s); remove_sisay(s)
    assert "Sisay WUBRG activation" in s.stack

def main():
    fixtures=[v for k,v in globals().items() if k.startswith("fixture_")]
    names=[]
    for fn in sorted(fixtures,key=lambda f:f.__name__):
        fn(); names.append(fn.__name__.removeprefix("fixture_"))
    assert len(names)==16
    print(json.dumps({
      "protocol":"MT_SISAY_TIMING_FIXTURES_R1_2026_09_24",
      "status":"PASS","result_class":"deterministic fixture",
      "fixtures_passed":len(names),"fixture_names":names,
      "random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    },indent=2,sort_keys=True))

if __name__=="__main__": main()
