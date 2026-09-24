from __future__ import annotations
from dataclasses import dataclass, field
import hashlib, json
from pathlib import Path

@dataclass
class State:
    magda_alive: bool = True
    magda_has_abilities: bool = True
    clock_alive: bool = True
    clock_has_abilities: bool = True
    torpor_orb: bool = False
    tidebinder_alive: bool = False
    treasures: int = 5
    stack: list[str] = field(default_factory=list)
    battlefield: set[str] = field(default_factory=set)
    events: list[str] = field(default_factory=list)

def activate_magda(s: State, target: str):
    assert s.magda_alive and s.magda_has_abilities
    assert s.treasures >= 5
    # Sacrificing five Treasures is the activation cost, paid before priority.
    s.treasures -= 5
    s.events.append("cost:sacrifice_five_treasures")
    s.stack.append(f"Magda tutor:{target}")

def activate_clock(s: State, target: str, artifacts: tuple[str, str]):
    assert s.clock_alive and s.clock_has_abilities
    assert len(set(artifacts)) == 2
    s.events.append("cost:tap_artifacts:" + ";".join(artifacts))
    s.stack.append(f"Clock untap:{target}")

def remove_source(s: State, source: str):
    if source == "Magda":
        s.magda_alive = False
    elif source == "Clock of Omens":
        s.clock_alive = False
    else:
        raise AssertionError(source)
    s.events.append(f"removed:{source}")

def resolve_top(s: State):
    obj = s.stack.pop()
    s.events.append(f"resolve:{obj}")
    if obj.startswith("Magda tutor:"):
        target = obj.split(":",1)[1]
        s.battlefield.add(target)
    return obj

def cast_tidebinder(s: State, target_ability: str):
    s.stack.append("Tishana's Tidebinder")
    resolve_top(s)
    s.tidebinder_alive = True
    s.battlefield.add("Tishana's Tidebinder")
    if s.torpor_orb:
        s.events.append("torpor_orb_suppresses_tidebinder_etb")
        return False
    assert target_ability in s.stack
    s.stack.remove(target_ability)
    s.events.append(f"countered:{target_ability}:Tidebinder")
    if target_ability.startswith("Magda tutor:"):
        s.magda_has_abilities = False
        s.events.append("blanked:Magda")
    elif target_ability.startswith("Clock untap:"):
        s.clock_has_abilities = False
        s.events.append("blanked:Clock of Omens")
    return True

def remove_tidebinder(s: State):
    s.tidebinder_alive = False
    s.battlefield.discard("Tishana's Tidebinder")
    # The continuous rider lasts only while Tidebinder remains.
    s.magda_has_abilities = True
    s.clock_has_abilities = True
    s.events.append("removed:Tishana's Tidebinder")

def answer_torpor_with_noncreature_removal(s: State, card: str):
    assert card in {"Beast Within", "Chaos Warp"}
    assert s.torpor_orb
    s.stack.append(f"{card}:Torpor Orb")
    resolve_top(s)
    s.torpor_orb = False
    s.battlefield.discard("Torpor Orb")
    s.events.append(f"removed:Torpor Orb:{card}")

def fixture_magda_cost_paid_before_priority():
    s=State(treasures=5)
    activate_magda(s,"Clock of Omens")
    assert s.treasures == 0
    assert s.stack == ["Magda tutor:Clock of Omens"]
    return s

def fixture_magda_activation_survives_source_removal():
    s=State()
    activate_magda(s,"Clock of Omens")
    remove_source(s,"Magda")
    assert s.stack[-1] == "Magda tutor:Clock of Omens"
    resolve_top(s)
    assert "Clock of Omens" in s.battlefield
    return s

def fixture_tidebinder_counters_magda_activation():
    s=State()
    activate_magda(s,"Clock of Omens")
    assert cast_tidebinder(s,"Magda tutor:Clock of Omens")
    assert not s.stack
    assert "Clock of Omens" not in s.battlefield
    assert not s.magda_has_abilities
    assert s.treasures == 0
    return s

def fixture_tidebinder_leave_restores_magda():
    s=fixture_tidebinder_counters_magda_activation()
    remove_tidebinder(s)
    assert s.magda_has_abilities
    return s

def fixture_torpor_suppresses_tidebinder_vs_magda():
    s=State(torpor_orb=True)
    s.battlefield.add("Torpor Orb")
    activate_magda(s,"Clock of Omens")
    assert not cast_tidebinder(s,"Magda tutor:Clock of Omens")
    assert s.stack[-1] == "Magda tutor:Clock of Omens"
    resolve_top(s)
    assert "Clock of Omens" in s.battlefield
    return s

def fixture_clock_cost_paid_before_priority():
    s=State()
    activate_clock(s,"Liquimetal Torque",("Magda artifact","Treasure A"))
    assert s.stack == ["Clock untap:Liquimetal Torque"]
    assert any(e.startswith("cost:tap_artifacts") for e in s.events)
    return s

def fixture_clock_activation_survives_source_removal():
    s=State()
    activate_clock(s,"Liquimetal Torque",("Magda artifact","Treasure A"))
    remove_source(s,"Clock of Omens")
    assert s.stack[-1] == "Clock untap:Liquimetal Torque"
    resolve_top(s)
    return s

def fixture_tidebinder_counters_clock_activation():
    s=State()
    activate_clock(s,"Liquimetal Torque",("Magda artifact","Treasure A"))
    assert cast_tidebinder(s,"Clock untap:Liquimetal Torque")
    assert not s.stack
    assert not s.clock_has_abilities
    return s

def fixture_torpor_suppresses_tidebinder_vs_clock():
    s=State(torpor_orb=True)
    s.battlefield.add("Torpor Orb")
    activate_clock(s,"Liquimetal Torque",("Magda artifact","Treasure A"))
    assert not cast_tidebinder(s,"Clock untap:Liquimetal Torque")
    assert s.stack[-1] == "Clock untap:Liquimetal Torque"
    return s

def fixture_beast_within_reopens_tidebinder_window():
    s=State(torpor_orb=True)
    s.battlefield.add("Torpor Orb")
    activate_clock(s,"Liquimetal Torque",("Magda artifact","Treasure A"))
    answer_torpor_with_noncreature_removal(s,"Beast Within")
    assert not s.torpor_orb
    assert cast_tidebinder(s,"Clock untap:Liquimetal Torque")
    return s

def fixture_chaos_warp_reopens_tidebinder_window():
    s=State(torpor_orb=True)
    s.battlefield.add("Torpor Orb")
    activate_magda(s,"Clock of Omens")
    answer_torpor_with_noncreature_removal(s,"Chaos Warp")
    assert not s.torpor_orb
    assert cast_tidebinder(s,"Magda tutor:Clock of Omens")
    return s

def fixture_countered_magda_activation_does_not_refund_treasures():
    s=State(treasures=5)
    activate_magda(s,"Portal to Phyrexia")
    cast_tidebinder(s,"Magda tutor:Portal to Phyrexia")
    assert s.treasures == 0
    assert "Portal to Phyrexia" not in s.battlefield
    return s

def main():
    fixtures = [
      ("magda_cost_paid_before_priority", fixture_magda_cost_paid_before_priority),
      ("magda_activation_survives_source_removal", fixture_magda_activation_survives_source_removal),
      ("tidebinder_counters_magda_activation", fixture_tidebinder_counters_magda_activation),
      ("tidebinder_leave_restores_magda", fixture_tidebinder_leave_restores_magda),
      ("torpor_suppresses_tidebinder_vs_magda", fixture_torpor_suppresses_tidebinder_vs_magda),
      ("clock_cost_paid_before_priority", fixture_clock_cost_paid_before_priority),
      ("clock_activation_survives_source_removal", fixture_clock_activation_survives_source_removal),
      ("tidebinder_counters_clock_activation", fixture_tidebinder_counters_clock_activation),
      ("torpor_suppresses_tidebinder_vs_clock", fixture_torpor_suppresses_tidebinder_vs_clock),
      ("beast_within_reopens_tidebinder_window", fixture_beast_within_reopens_tidebinder_window),
      ("chaos_warp_reopens_tidebinder_window", fixture_chaos_warp_reopens_tidebinder_window),
      ("countered_magda_activation_does_not_refund_treasures", fixture_countered_magda_activation_does_not_refund_treasures),
    ]
    passed=[]
    for name, fn in fixtures:
        fn(); passed.append(name)
    print(json.dumps({
      "protocol":"MT_MAGDA_TIMING_FIXTURES_R1_2026_09_24",
      "status":"PASS",
      "result_class":"deterministic fixture",
      "fixtures_passed":len(passed),
      "fixture_names":passed,
      "random_seeds_used":0,
      "qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    },indent=2,sort_keys=True))

if __name__=="__main__":
    main()
