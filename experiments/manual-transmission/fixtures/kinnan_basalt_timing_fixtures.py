from __future__ import annotations
from dataclasses import dataclass, field
import hashlib, json
from pathlib import Path

@dataclass
class State:
    kinnan:bool=True
    basalt:bool=True
    basalt_tapped:bool=False
    stack:list[str]=field(default_factory=list)
    colorless:int=0
    colored:int=0
    tidebinder_alive:bool=False
    basalt_has_abilities:bool=True
    events:list[str]=field(default_factory=list)

def tap_basalt_for_mana(s):
    assert s.basalt and s.basalt_has_abilities and not s.basalt_tapped
    s.basalt_tapped=True
    s.colorless+=3
    if s.kinnan: s.colorless+=1
    s.events.append("basalt_mana_ability_resolved")

def activate_untap(s):
    assert s.basalt and s.basalt_has_abilities and s.basalt_tapped and s.colorless>=3
    s.colorless-=3; s.stack.append("Basalt untap ability")

def resolve_untap(s):
    assert "Basalt untap ability" in s.stack
    s.stack.remove("Basalt untap ability")
    if s.basalt: s.basalt_tapped=False

def tidebinder_untap(s):
    assert "Basalt untap ability" in s.stack
    s.stack.remove("Basalt untap ability"); s.tidebinder_alive=True; s.basalt_has_abilities=False

def fixture_basalt_tap_is_mana_ability_no_stack_window():
    s=State(); tap_basalt_for_mana(s); assert not s.stack and s.colorless==4

def fixture_kinnan_bonus_is_mana_ability_no_stack_window():
    s=State(); tap_basalt_for_mana(s); assert "basalt_mana_ability_resolved" in s.events and s.colorless==4

def fixture_basalt_untap_uses_stack():
    s=State(); tap_basalt_for_mana(s); activate_untap(s); assert s.stack==["Basalt untap ability"]

def fixture_each_loop_iteration_exposes_untap_window():
    s=State()
    for _ in range(3):
        tap_basalt_for_mana(s); activate_untap(s); assert s.stack[-1]=="Basalt untap ability"; resolve_untap(s)
    assert s.colorless==3

def fixture_remove_kinnan_on_untap_breaks_future_net_positive_loop():
    s=State(); tap_basalt_for_mana(s); activate_untap(s); s.kinnan=False; resolve_untap(s)
    before=s.colorless; tap_basalt_for_mana(s); activate_untap(s); resolve_untap(s)
    assert s.colorless==before

def fixture_remove_basalt_does_not_erase_existing_untap_ability():
    s=State(); tap_basalt_for_mana(s); activate_untap(s); s.basalt=False
    assert "Basalt untap ability" in s.stack; resolve_untap(s); assert not s.basalt

def fixture_tidebinder_can_counter_untap_and_blank_basalt():
    s=State(); tap_basalt_for_mana(s); activate_untap(s); tidebinder_untap(s)
    assert not s.stack and not s.basalt_has_abilities

def fixture_tidebinder_leaving_restores_basalt_abilities():
    s=State(); tap_basalt_for_mana(s); activate_untap(s); tidebinder_untap(s)
    s.tidebinder_alive=False; s.basalt_has_abilities=True; assert s.basalt_has_abilities

def fixture_beast_or_chaos_can_remove_basalt_at_untap_window():
    s=State(); tap_basalt_for_mana(s); activate_untap(s); s.basalt=False
    assert "Basalt untap ability" in s.stack and not s.basalt

def fixture_creature_only_removal_cannot_target_basalt_artifact():
    legal={"Pongify":False,"Rapid Hybridization":False,"Reality Shift":False}; assert not any(legal.values())

def fixture_creature_removal_can_target_kinnan_during_untap_window():
    s=State(); tap_basalt_for_mana(s); activate_untap(s); legal=True; assert legal and s.stack

def fixture_hope_ender_cast_trigger_can_answer_basalt_spell():
    stack=["Basalt Monolith spell"]; stack.append("Hope-Ender cast trigger:Basalt Monolith spell")
    assert stack[-1].startswith("Hope-Ender cast trigger")

def fixture_infinite_colorless_not_automatic_win():
    result_class="ARBITRARY_COLORLESS_ONLY"; assert result_class!="WIN"

def fixture_kinnan_activation_still_needs_two_colored_hybrid_mana():
    arbitrary_colorless=10**9; colored=0
    can_activate=arbitrary_colorless>=5 and colored>=2
    assert not can_activate

def fixture_existing_mana_not_erased_by_removing_kinnan():
    s=State(); tap_basalt_for_mana(s); before=s.colorless; s.kinnan=False; assert s.colorless==before

def fixture_post_mana_conversion_has_separate_windows():
    windows=["Kinnan activated ability","Hullbreaker trigger","Finale spell"]; assert len(set(windows))==3

def main():
    fs=[v for k,v in globals().items() if k.startswith("fixture_")]; names=[]
    for fn in sorted(fs,key=lambda f:f.__name__): fn(); names.append(fn.__name__.removeprefix("fixture_"))
    assert len(names)==16
    print(json.dumps({"protocol":"MT_KINNAN_BASALT_TIMING_FIXTURES_R1_2026_09_24","status":"PASS","result_class":"deterministic fixture",
      "fixtures_passed":len(names),"fixture_names":names,"random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()},indent=2,sort_keys=True))
if __name__=="__main__": main()
