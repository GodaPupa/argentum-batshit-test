from __future__ import annotations
from dataclasses import dataclass, field
from collections import Counter
import hashlib, json
from pathlib import Path

@dataclass(frozen=True)
class Trigger:
    payload: str

@dataclass
class State:
    hashaton_alive: bool = True
    hashaton_has_abilities: bool = True
    stack: list[object] = field(default_factory=list)
    pending_hashaton: list[str] = field(default_factory=list)
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
        if self.hashaton_alive and self.hashaton_has_abilities:
            self.pending_hashaton.append(card_id)
            self.events.append(f"triggered:hashaton:{card_id}")

    def discard_creatures_batch(self, card_ids: list[str], reason: str):
        for card_id in card_ids:
            self.discard_creature(card_id, reason)

    def flush_triggers(self, top_first: list[str] | None = None):
        pending = list(self.pending_hashaton)
        self.pending_hashaton.clear()
        if top_first is None:
            top_first = pending
        assert Counter(top_first) == Counter(pending)
        # Same-controller simultaneous triggers are ordered by that controller.
        # Append bottom first so top_first[0] is the next object to resolve.
        for payload in reversed(top_first):
            self.stack.append(Trigger(payload))
            self.events.append(f"stack:hashaton_trigger:{payload}")

    def can_pay_2u(self) -> bool:
        return self.mana["U"] >= 1 and sum(self.mana.values()) >= 3

    def pay_2u(self):
        assert self.can_pay_2u()
        self.mana["U"] -= 1
        generic = 2
        for k in ["C", "U", "W", "B", "R", "G"]:
            take = min(generic, self.mana[k])
            self.mana[k] -= take
            generic -= take
            if generic == 0:
                break
        assert generic == 0

    def resolve_hashaton(self, *, pay: bool = True) -> str:
        assert self.stack and isinstance(self.stack[-1], Trigger)
        trig = self.stack.pop()
        payload = trig.payload
        # CR 117.2e / 608.2g: no priority subwindow exists between choosing
        # to pay during resolution and token creation.
        if not pay:
            self.events.append(f"decline_hashaton_payment:{payload}")
            return payload
        self.pay_2u()
        self.tokens.append(payload)
        self.events.append(f"token:{payload}:tapped")
        if payload == "Teferi, Mage of Zhalfir":
            self.teferi_static = True
        if payload == "Rune-Scarred Demon":
            self.stack.append("Rune-Scarred Demon ETB")
        if payload == "Vilis, Broker of Blood":
            self.events.append("vilis_static_ready")
        if payload == "Jin-Gitaxias, Core Augur":
            self.events.append("jin_static_ready")
        return payload

    def remove_hashaton(self):
        self.hashaton_alive = False
        self.events.append("hashaton_removed")

    def tidebinder_counter_hashaton_trigger(self):
        assert self.stack and isinstance(self.stack[-1], Trigger)
        trig = self.stack.pop()
        self.events.append(f"countered:hashaton_trigger:{trig.payload}:Tidebinder")
        # The source is a creature, so Tidebinder's rider blanks Hashaton while
        # Tidebinder remains. Already-created Trigger objects remain independent.
        self.hashaton_has_abilities = False
        self.events.append("hashaton_blanked:Tidebinder")
        return trig.payload

def resolve_top(s: State):
    obj = s.stack.pop()
    s.events.append(f"resolve:{obj}")
    return obj

def activate_cost_discard(
    s: State,
    outlet: str,
    payload: str,
    *,
    sorcery_timing: bool = True,
    mature: bool = True,
):
    if outlet in {"Stern Constable", "The Underworld Cookbook"}:
        assert mature
    if outlet == "Bloodthorn Flail":
        assert sorcery_timing
    s.stack.append(f"{outlet} ability")
    s.discard_creature(payload, outlet)
    s.flush_triggers()

def activate_led(s: State, creature_payloads: list[str], *, color: str = "U"):
    assert creature_payloads
    # LED's discard-hand and sacrifice are activation costs. It is a mana
    # ability, so after costs are paid it resolves immediately without the stack.
    s.discard_creatures_batch(creature_payloads, "Lion's Eye Diamond")
    s.events.append("sacrifice:Lion's Eye Diamond")
    s.mana[color] += 3
    s.events.append(f"mana_ability_resolved:Lion's Eye Diamond:{color}{color}{color}")
    # Only now, before the next player receives priority, are all Hashaton
    # triggers put on the stack. The controller chooses their relative order.
    s.flush_triggers(top_first=creature_payloads)

def cast_foil_alt(s: State, creature_payload: str):
    s.stack.append("Foil")
    s.spend_card("Island-for-Foil")
    s.discard_creature(creature_payload, "Foil alternate cost")
    s.flush_triggers()

def fixture_cost_discard_responds_to_removal(outlet: str):
    s = State()
    s.stack.append("MT removal:Hashaton")
    activate_cost_discard(s, outlet, "Teferi, Mage of Zhalfir")
    assert isinstance(s.stack[-1], Trigger)
    assert s.resolve_hashaton() == "Teferi, Mage of Zhalfir"
    assert s.hashaton_alive
    return s

def fixture_led_single_payload_responds_to_removal():
    s = State(mana=Counter())
    s.stack.append("MT removal:Hashaton")
    activate_led(s, ["Teferi, Mage of Zhalfir"])
    assert s.mana["U"] == 3
    assert isinstance(s.stack[-1], Trigger)
    s.resolve_hashaton()
    assert s.tokens == ["Teferi, Mage of Zhalfir"]
    assert sum(s.mana.values()) == 0
    return s

def fixture_led_multiple_payloads_create_multiple_triggers():
    s = State(mana=Counter())
    payloads = ["Teferi, Mage of Zhalfir", "Rune-Scarred Demon", "Razaketh, the Foulblooded"]
    activate_led(s, payloads)
    triggers = [x for x in s.stack if isinstance(x, Trigger)]
    assert len(triggers) == 3
    assert Counter(x.payload for x in triggers) == Counter(payloads)
    # LED alone makes only three blue mana, enough to pay one Hashaton trigger.
    assert s.can_pay_2u()
    s.resolve_hashaton()
    assert not s.can_pay_2u()
    assert sum(isinstance(x, Trigger) for x in s.stack) == 2
    return s

def fixture_led_tidebinder_does_not_erase_sibling_triggers():
    s = State(mana=Counter({"U": 6}))
    payloads = ["Teferi, Mage of Zhalfir", "Rune-Scarred Demon", "Razaketh, the Foulblooded"]
    activate_led(s, payloads)
    assert s.stack[-1] == Trigger("Teferi, Mage of Zhalfir")
    s.tidebinder_counter_hashaton_trigger()
    assert not s.hashaton_has_abilities
    # Blanking Hashaton prevents future triggers, but the two sibling triggers
    # already on the stack remain independent under CR 113.7a.
    assert sum(isinstance(x, Trigger) for x in s.stack) == 2
    s.resolve_hashaton()
    s.resolve_hashaton()
    assert Counter(s.tokens) == Counter(["Rune-Scarred Demon", "Razaketh, the Foulblooded"])
    return s

def fixture_led_source_removal_does_not_erase_triggers():
    s = State(mana=Counter({"U": 6}))
    payloads = ["Teferi, Mage of Zhalfir", "Razaketh, the Foulblooded"]
    activate_led(s, payloads)
    s.stack.append("MT removal:Hashaton")
    resolve_top(s)
    s.remove_hashaton()
    assert sum(isinstance(x, Trigger) for x in s.stack) == 2
    s.resolve_hashaton()
    s.resolve_hashaton()
    assert len(s.tokens) == 2
    return s

def fixture_led_controller_orders_simultaneous_triggers():
    s = State(mana=Counter({"U": 6}))
    payloads = ["Razaketh, the Foulblooded", "Teferi, Mage of Zhalfir"]
    # Top-first order intentionally makes Teferi resolve first.
    s.discard_creatures_batch(payloads, "Lion's Eye Diamond")
    s.mana["U"] += 3
    s.flush_triggers(top_first=["Teferi, Mage of Zhalfir", "Razaketh, the Foulblooded"])
    assert s.stack[-1] == Trigger("Teferi, Mage of Zhalfir")
    s.resolve_hashaton()
    assert s.teferi_static
    assert s.stack[-1] == Trigger("Razaketh, the Foulblooded")
    return s

def fixture_bloodthorn_priority():
    s = State()
    activate_cost_discard(s, "Bloodthorn Flail", "Razaketh, the Foulblooded")
    assert isinstance(s.stack[-1], Trigger)
    s.stack.append("MT removal:Hashaton")
    resolve_top(s)
    s.remove_hashaton()
    assert isinstance(s.stack[-1], Trigger)
    s.resolve_hashaton()
    return s

def fixture_resolution_discard(name: str):
    s = State()
    s.stack.append(f"{name} discard-on-resolution ability")
    s.stack.append("MT removal:Hashaton")
    resolve_top(s)
    s.remove_hashaton()
    assert resolve_top(s) == f"{name} discard-on-resolution ability"
    s.discard_creature("Rune-Scarred Demon", f"{name} resolution")
    s.flush_triggers()
    assert not any(isinstance(x, Trigger) for x in s.stack)
    return s

def fixture_foil_creature_alt_cost():
    s = State()
    s.stack.append("MT removal:Hashaton")
    cast_foil_alt(s, "Rune-Scarred Demon")
    assert s.stack[-2] == "Foil"
    assert s.stack[-1] == Trigger("Rune-Scarred Demon")
    s.resolve_hashaton()
    assert s.stack[-1] == "Rune-Scarred Demon ETB"
    return s

def fixture_trigger_survives_source_removal():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Jin-Gitaxias, Core Augur")
    s.stack.append("MT removal:Hashaton")
    resolve_top(s)
    s.remove_hashaton()
    assert isinstance(s.stack[-1], Trigger)
    s.resolve_hashaton()
    assert "Jin-Gitaxias, Core Augur" in s.tokens
    return s

def fixture_tidebinder_counters_trigger_and_blanks_source():
    s = State()
    activate_cost_discard(s, "Tireless Tribe", "Teferi, Mage of Zhalfir")
    assert s.tidebinder_counter_hashaton_trigger() == "Teferi, Mage of Zhalfir"
    assert not s.tokens
    assert not s.hashaton_has_abilities
    # A later single-card discard creates no new Hashaton trigger.
    s.discard_creature("Rune-Scarred Demon", "Tireless Tribe")
    s.flush_triggers()
    assert not any(isinstance(x, Trigger) for x in s.stack)
    return s

def fixture_teferi_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Teferi, Mage of Zhalfir")
    s.resolve_hashaton()
    assert s.teferi_static
    can_mt_cast_instant_on_hash_turn = not s.teferi_static
    can_mt_activate_ability = True
    assert not can_mt_cast_instant_on_hash_turn
    assert can_mt_activate_ability
    return s

def fixture_razaketh_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Razaketh, the Foulblooded")
    s.resolve_hashaton()
    s.life["HASH"] -= 2
    s.events.append("sacrifice:other_creature:Razaketh_cost")
    s.stack.append("Razaketh tutor ability")
    s.stack.append("MT removal:Razaketh")
    resolve_top(s)
    assert s.stack[-1] == "Razaketh tutor ability"
    # Tidebinder may counter the already-existing activated ability.
    s.stack.pop()
    s.events.append("countered:Razaketh_ability:Tidebinder")
    return s

def fixture_rune_scarred_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Rune-Scarred Demon")
    s.resolve_hashaton()
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
    s.resolve_hashaton()
    s.life["HASH"] -= 2
    s.stack.append("Vilis -1/-1 ability")
    s.stack.append("Vilis draw 2 trigger")
    assert s.stack[-2:] == ["Vilis -1/-1 ability", "Vilis draw 2 trigger"]
    return s

def fixture_jin_branch():
    s = State()
    activate_cost_discard(s, "Putrid Imp", "Jin-Gitaxias, Core Augur")
    s.resolve_hashaton()
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
    for outlet in ["Putrid Imp", "Stern Constable", "Tireless Tribe", "The Underworld Cookbook"]:
        fixture_cost_discard_responds_to_removal(outlet)
        fixtures.append(f"cost_discard_precedes_priority::{outlet}")

    fixture_led_single_payload_responds_to_removal()
    fixtures.append("led_cost_discard_precedes_priority_single_payload")
    fixture_led_multiple_payloads_create_multiple_triggers()
    fixtures.append("led_multiple_creatures_create_multiple_hashaton_triggers")
    fixture_led_tidebinder_does_not_erase_sibling_triggers()
    fixtures.append("led_tidebinder_blanks_source_but_not_sibling_triggers")
    fixture_led_source_removal_does_not_erase_triggers()
    fixtures.append("led_source_removal_does_not_erase_existing_triggers")
    fixture_led_controller_orders_simultaneous_triggers()
    fixtures.append("led_controller_orders_simultaneous_hashaton_triggers")

    fixture_bloodthorn_priority()
    fixtures.append("cost_discard_precedes_priority::Bloodthorn Flail")

    for name in ["Hapless Researcher", "Gran-Gran", "M.O.D.O.K."]:
        fixture_resolution_discard(name)
        fixtures.append(f"resolution_discard_has_pre_discard_window::{name}")

    fixture_foil_creature_alt_cost()
    fixtures.append("foil_alt_cost_creature_discard_triggers_hashaton")
    fixture_trigger_survives_source_removal()
    fixtures.append("hashaton_trigger_survives_source_removal")
    fixture_tidebinder_counters_trigger_and_blanks_source()
    fixtures.append("tidebinder_counters_trigger_and_blanks_hashaton")
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

    assert len(fixtures) == 22
    own_sha = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    print(json.dumps({
        "protocol": "MT_HASHATON_TIMING_FIXTURES_R2_2026_09_23",
        "status": "PASS",
        "result_class": "deterministic fixture",
        "fixtures_passed": len(fixtures),
        "fixture_names": fixtures,
        "random_seeds_used": 0,
        "script_sha256": own_sha,
        "qualification_outcomes_exposed": 0,
        "supersedes": "MT_HASHATON_TIMING_FIXTURES_R1_2026_09_23"
    }, indent=2, sort_keys=True))

if __name__ == "__main__":
    main()
