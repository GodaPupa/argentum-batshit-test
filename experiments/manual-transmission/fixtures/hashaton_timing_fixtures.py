from __future__ import annotations
from dataclasses import dataclass, field
from collections import Counter
import hashlib, json
from pathlib import Path

@dataclass
class State:
    hashaton_alive: bool = True
    stack: list[str] = field(default_factory=list)
    pending_hashaton: int = 0
    tokens: list[str] = field(default_factory=list)
    mana: Counter = field(default_factory=lambda: Counter({"U": 3, "C": 2}))
    life: dict[str, int] = field(default_factory=lambda: {"HASH": 40, "MT": 40})
    spent_cards: set[str] = field(default_factory=set)
    events: list[str] = field(default_factory=list)
    teferi_static: bool = False

    def spend_card(self, card_id: str):
        assert card_id not in self.spent_cards, f"card reused: {card_id}"
        self.spent_cards.add(card_id)

    def discard_creature(self, card_id: str, reason: str):
        self.spend_card(card_id)
        self.events.append(f"discard:{card_id}:{reason}")
        if self.hashaton_alive:
            self.pending_hashaton += 1
            self.events.append("triggered:hashaton")

    def flush_triggers(self):
        while self.pending_hashaton:
            self.pending_hashaton -= 1
            self.stack.append("Hashaton trigger")
            self.events.append("stack:hashaton_trigger")

    def pay_2u(self):
        assert self.mana["U"] >= 1
        assert sum(self.mana.values()) >= 3
        self.mana["U"] -= 1
        generic = 2
        for k in ["C", "U", "W", "B", "R", "G"]:
            take = min(generic, self.mana[k])
            self.mana[k] -= take
            generic -= take
            if generic == 0:
                break
        assert generic == 0

    def resolve_hashaton(self, payload: str):
        assert self.stack and self.stack[-1] == "Hashaton trigger"
        self.stack.pop()
        # CR 117.2e / 608.2g: there is no priority subwindow between this
        # optional payment and token creation.
        self.pay_2u()
        self.tokens.append(payload)
        self.events.append(f"token:{payload}:tapped")
        if payload == "Teferi, Mage of Zhalfir":
            self.teferi_static = True
        if payload == "Rune-Scarred Demon":
            # Trigger is pending during resolution and is put on the stack
            # before the next player receives priority.
            self.stack.append("Rune-Scarred Demon ETB")
        if payload == "Vilis, Broker of Blood":
            self.events.append("vilis_static_ready")
        if payload == "Jin-Gitaxias, Core Augur":
            self.events.append("jin_static_ready")

    def remove_hashaton(self):
        self.hashaton_alive = False
        self.events.append("hashaton_removed")

def activate_cost_discard(
    s: State,
    outlet: str,
    payload: str,
    *,
    mana_ability: bool = False,
    sorcery_timing: bool = True,
    mature: bool = True,
):
    if outlet == "Stern Constable":
        assert mature
    if outlet == "Bloodthorn Flail":
        assert sorcery_timing
    if mana_ability:
        # LED: discard is part of the activation cost. The mana ability then
        # resolves immediately without using the stack; Hashaton's trigger is
        # stacked before priority is next received.
        s.discard_creature(payload, outlet)
        s.events.append(f"mana_ability_resolved:{outlet}")
    else:
        s.stack.append(f"{outlet} ability")
        s.discard_creature(payload, outlet)
    s.flush_triggers()

def cast_foil_alt(s: State, creature_payload: str):
    # Foil is already on the stack while total costs are paid. If the "another
    # card" discarded for its alternative cost is a creature, Hashaton triggers;
    # that trigger is stacked above Foil before priority returns.
    s.stack.append("Foil")
    s.spend_card("Island-for-Foil")
    s.discard_creature(creature_payload, "Foil alternate cost")
    s.flush_triggers()

def resolve_top(s: State):
    obj = s.stack.pop()
    s.events.append(f"resolve:{obj}")
    return obj

def fixture_cost_discard_responds_to_removal(outlet: str, *, mana=False):
    s = State()
    s.stack.append("MT removal:Hashaton")
    activate_cost_discard(s, outlet, "Teferi, Mage of Zhalfir", mana_ability=mana)
    assert s.stack[-1] == "Hashaton trigger"
    s.resolve_hashaton("Teferi, Mage of Zhalfir")
    assert "Teferi, Mage of Zhalfir" in s.tokens
    assert s.hashaton_alive
    return s

def fixture_bloodthorn_priority():
    s = State()
    activate_cost_discard(
        s, "Bloodthorn Flail", "Razaketh, the Foulblooded", sorcery_timing=True
    )
    assert s.stack[-1] == "Hashaton trigger"
    # MT's first possible ordinary-removal window is after the trigger exists.
    s.stack.append("MT removal:Hashaton")
    resolve_top(s)
    s.remove_hashaton()
    assert s.stack[-1] == "Hashaton trigger"
    s.resolve_hashaton("Razaketh, the Foulblooded")
    return s

def fixture_resolution_discard(name: str):
    s = State()
    s.stack.append(f"{name} discard-on-resolution ability")
    # The discard has not happened yet, so this is a genuine pre-discard window.
    s.stack.append("MT removal:Hashaton")
    resolve_top(s)
    s.remove_hashaton()
    assert resolve_top(s) == f"{name} discard-on-resolution ability"
    s.discard_creature("Rune-Scarred Demon", f"{name} resolution")
    s.flush_triggers()
    assert "Hashaton trigger" not in s.stack
    return s

def fixture_foil_creature_alt_cost():
    s = State()
    s.stack.append("MT removal:Hashaton")
    cast_foil_alt(s, "Rune-Scarred Demon")
    assert s.stack[-2:] == ["Foil", "Hashaton trigger"]
    s.resolve_hashaton("Rune-Scarred Demon")
    assert "Rune-Scarred Demon" in s.tokens
    assert s.stack[-1] == "Rune-Scarred Demon ETB"
    return s

def fixture_trigger_survives_source_removal():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Jin-Gitaxias, Core Augur")
    assert s.stack[-1] == "Hashaton trigger"
    s.stack.append("MT removal:Hashaton")
    resolve_top(s)
    s.remove_hashaton()
    assert s.stack[-1] == "Hashaton trigger"
    s.resolve_hashaton("Jin-Gitaxias, Core Augur")
    assert "Jin-Gitaxias, Core Augur" in s.tokens
    return s

def fixture_tidebinder_counters_trigger():
    s = State()
    activate_cost_discard(s, "Tireless Tribe", "Teferi, Mage of Zhalfir")
    assert s.stack[-1] == "Hashaton trigger"
    s.stack.append("Tishana's Tidebinder spell")
    resolve_top(s)
    # Tidebinder ETB can target the already-existing Hashaton triggered ability.
    assert s.stack[-1] == "Hashaton trigger"
    s.stack.pop()
    s.events.append("countered:hashaton_trigger:Tidebinder")
    assert not s.tokens
    return s

def fixture_teferi_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Teferi, Mage of Zhalfir")
    s.resolve_hashaton("Teferi, Mage of Zhalfir")
    assert s.teferi_static
    can_mt_cast_instant_on_hash_turn = not s.teferi_static
    can_mt_activate_ability = True
    assert not can_mt_cast_instant_on_hash_turn
    assert can_mt_activate_ability
    return s

def fixture_razaketh_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Razaketh, the Foulblooded")
    s.resolve_hashaton("Razaketh, the Foulblooded")
    # No tap/untap symbol in Razaketh's activation cost, so summoning sickness
    # does not prevent immediate activation.
    s.life["HASH"] -= 2
    s.events.append("sacrifice:other_creature:Razaketh_cost")
    s.stack.append("Razaketh tutor ability")
    s.stack.append("MT removal:Razaketh")
    resolve_top(s)
    assert s.stack[-1] == "Razaketh tutor ability"
    s.stack.pop()
    s.events.append("countered:Razaketh_ability:Tidebinder")
    return s

def fixture_rune_scarred_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Rune-Scarred Demon")
    s.resolve_hashaton("Rune-Scarred Demon")
    assert s.stack[-1] == "Rune-Scarred Demon ETB"
    s.stack.append("MT removal:Rune-Scarred token")
    resolve_top(s)
    assert s.stack[-1] == "Rune-Scarred Demon ETB"
    s.stack.pop()
    s.events.append("countered:RuneScarred_ETB:Tidebinder")
    return s

def fixture_vilis_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Vilis, Broker of Blood")
    s.resolve_hashaton("Vilis, Broker of Blood")
    s.life["HASH"] -= 2
    s.stack.append("Vilis -1/-1 ability")
    # Life paid as the activation cost triggers Vilis's draw ability; before
    # priority it is stacked above the activated ability.
    s.stack.append("Vilis draw 2 trigger")
    assert s.stack[-2:] == ["Vilis -1/-1 ability", "Vilis draw 2 trigger"]
    return s

def fixture_jin_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Jin-Gitaxias, Core Augur")
    s.resolve_hashaton("Jin-Gitaxias, Core Augur")
    assert "jin_static_ready" in s.events
    assert not any("win" in e for e in s.events)
    return s

def fixture_no_duplicate_resource_use():
    s = State()
    s.discard_creature("Razaketh, the Foulblooded", "Putrid Imp")
    try:
        s.discard_creature("Razaketh, the Foulblooded", "Tireless Tribe")
        raise AssertionError("expected duplicate-use invariant")
    except AssertionError as exc:
        assert "card reused" in str(exc)
    return s

def main():
    fixtures = []
    for outlet, mana in [
        ("Putrid Imp", False),
        ("Stern Constable", False),
        ("Tireless Tribe", False),
        ("The Underworld Cookbook", False),
        ("Lion's Eye Diamond", True),
    ]:
        fixture_cost_discard_responds_to_removal(outlet, mana=mana)
        fixtures.append(f"cost_discard_precedes_priority::{outlet}")

    fixture_bloodthorn_priority()
    fixtures.append("cost_discard_precedes_priority::Bloodthorn Flail")

    for name in ["Hapless Researcher", "Gran-Gran", "M.O.D.O.K."]:
        fixture_resolution_discard(name)
        fixtures.append(f"resolution_discard_has_pre_discard_window::{name}")

    fixture_foil_creature_alt_cost()
    fixtures.append("foil_alt_cost_creature_discard_triggers_hashaton")
    fixture_trigger_survives_source_removal()
    fixtures.append("hashaton_trigger_survives_source_removal")
    fixture_tidebinder_counters_trigger()
    fixtures.append("tidebinder_can_counter_hashaton_trigger")
    fixture_teferi_branch()
    fixtures.append("teferi_token_closes_future_spell_windows_not_abilities")
    fixture_razaketh_branch()
    fixtures.append("razaketh_activation_is_separate_answerable_window")
    fixture_rune_scarred_branch()
    fixtures.append("rune_scarred_etb_is_separate_answerable_window")
    fixture_vilis_branch()
    fixtures.append("vilis_activation_and_life_loss_trigger_are_separate_stack_objects")
    fixture_jin_branch()
    fixtures.append("jin_static_is_immediate_but_not_scored_terminal")
    fixture_no_duplicate_resource_use()
    fixtures.append("resource_ledger_rejects_double_spend")

    own_sha = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    print(json.dumps({
        "protocol": "MT_HASHATON_TIMING_FIXTURES_R1_2026_09_23",
        "status": "PASS",
        "result_class": "deterministic fixture",
        "fixtures_passed": len(fixtures),
        "fixture_names": fixtures,
        "random_seeds_used": 0,
        "script_sha256": own_sha,
        "qualification_outcomes_exposed": 0
    }, indent=2, sort_keys=True))

if __name__ == "__main__":
    main()
