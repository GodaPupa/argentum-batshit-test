#!/usr/bin/env python3
"""Seed-free Phase-21 validation of concrete Capsize state predicates."""
from __future__ import annotations

import hashlib
from pathlib import Path

from capsize_event_ledger import OpponentPolicyContract, compile_response_window
from capsize_response_policy import select_capsize_response
from capsize_state_adapter import (
    FrozenNextMainPlan,
    ObservedOpponentEvent,
    ObservedPermanent,
    PublicPlayerStatus,
    adapt_observed_event,
)
import mana_harness as harness


ROOT = Path(__file__).resolve().parents[2]
CONTROL = ROOT / "izzet-science/v0.7-control.md"
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
POLICY = OpponentPolicyContract("public-opponent-v1")
PLAN = FrozenNextMainPlan(("guildmage-self",), ("cast_sorcery", "activate_guildmage"))


def state(lethal):
    hand = ["Lava Spike", "Desperate Ritual"] if lethal else ["Lava Spike"]
    value = harness.DevState(hand)
    value.turn = 8
    value.battlefield = [{"card": "Izzet Guildmage", "tapped": False, "entered": 2}]
    for index, card in enumerate(["Mountain"] * 3 + ["Island"] * 2):
        value.battlefield.append({"card": card, "tapped": False, "entered": index + 1})
    assert harness.primary_combo_launch_feasible(value) is lethal
    return value


def permanent(object_id, card, controller="opponent", commander=False,
              hexproof=False, shroud=False):
    return ObservedPermanent(object_id, card, controller, commander,
                             "battlefield", hexproof, shroud)


def event(event_id, target, kind, *, sequence=1, damage=0, life_loss=0,
          poison=0, disabled=(), prohibited=()):
    return ObservedOpponentEvent(
        event_id, "public-opponent-v1", "t8-priority-1", 8, sequence,
        f"source-{event_id}", target, kind, damage, life_loss, poison,
        tuple(disabled), tuple(prohibited))


def adapt(value, *, life=5, poison=0, lethal=False, plan=PLAN):
    return adapt_observed_event(
        value, status=PublicPlayerStatus(life, poison), next_main_plan=plan,
        izzet_state=state(lethal))


def expect_rejection(callback):
    try:
        callback()
    except (ValueError, TypeError):
        return
    raise AssertionError("accepted invalid Phase-21 adapter input")


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest() != CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")

    hostile = permanent("hostile-1", "Hostile Permanent")
    commander = permanent("commander-1", "Opposing Commander", commander=True)
    guildmage = permanent("guildmage-self", "Izzet Guildmage", "self", True)

    damage = adapt(event("damage", hostile, "immediate_effect", damage=5))
    assert damage.public_facts == ("loss_before_next_response",)
    poison = adapt(event("poison", hostile, "immediate_effect", poison=1), poison=9)
    assert poison.public_facts == ("loss_before_next_response",)
    nonlethal_damage = adapt(event("chip", hostile, "immediate_effect", damage=4))
    assert nonlethal_damage.public_facts == ()
    lock = adapt(event("lock", commander, "persistent_restriction",
                       prohibited=("cast_sorcery",)))
    assert lock.target_class == "opposing_commander"
    assert lock.public_facts == ("prevents_next_main_plan",)
    irrelevant_lock = adapt(event("other-lock", hostile, "persistent_restriction",
                                  prohibited=("attack",)))
    assert irrelevant_lock.public_facts == ()
    rescue = adapt(event("rescue", guildmage, "remove_target"), lethal=True)
    assert rescue.public_facts == (
        "targets_own_guildmage", "primary_combo_lethal_now")
    nonlethal_rescue = adapt(event("nonlethal-rescue", guildmage, "remove_target"))
    assert nonlethal_rescue.public_facts == ("targets_own_guildmage",)
    illegal = adapt(event("illegal", permanent(
        "hexproof-1", "Hexproof Permanent", hexproof=True), "tempo_change"))
    assert not illegal.legal_target

    records = [
        adapt(event("loss", hostile, "immediate_effect", damage=5, sequence=1)),
        adapt(event("plan-lock", commander, "persistent_restriction", sequence=2,
                    disabled=("guildmage-self",))),
        adapt(event("combo-rescue", guildmage, "remove_target", sequence=3), lethal=True),
        adapt(event("tempo", hostile, "tempo_change", sequence=4)),
        adapt(event("nonlethal", guildmage, "remove_target", sequence=5)),
    ]
    window = compile_response_window(POLICY, records)
    assert window.omitted_event_ids == ("nonlethal",)
    decision = select_capsize_response(
        window.response_events, one_shot_ready=True, buyback_ready=True,
        guildmage_battlefield=True, combo_lethal_now=True)
    assert decision.selected_event_id == "loss" and decision.action == "cast_buyback"

    # Exhaust the three derived thresholds, both target legality branches, both
    # combo states, and both plan-intersection forms without random input.
    exhaustive = 0
    for life in (1, 5):
        for damage_amount in (0, 1, 5):
            for poison_count in (0, 9):
                for poison_amount in (0, 1):
                    for lethal in (False, True):
                        for hexproof in (False, True):
                            for commander_identity in (False, True):
                                target = permanent(
                                    "matrix-target", "Matrix Permanent",
                                    commander=commander_identity, hexproof=hexproof)
                                amount = damage_amount or poison_amount
                                candidate = event(
                                    f"matrix-{exhaustive}", target, "immediate_effect",
                                    damage=damage_amount, poison=poison_amount)
                                if amount == 0:
                                    candidate = event(
                                        f"matrix-{exhaustive}", target,
                                        "persistent_restriction",
                                        prohibited=("cast_sorcery",))
                                first = adapt(candidate, life=life, poison=poison_count,
                                              lethal=lethal)
                                second = adapt(candidate, life=life, poison=poison_count,
                                               lethal=lethal)
                                assert first == second
                                assert first.legal_target is (not hexproof)
                                assert first.target_class == (
                                    "opposing_commander" if commander_identity
                                    else "opposing_permanent")
                                assert ("loss_before_next_response" in first.public_facts) == (
                                    damage_amount >= life or
                                    poison_count + poison_amount >= 10)
                                exhaustive += 1
    assert exhaustive == 192

    for disabled, prohibited, expected in (
            (("guildmage-self",), (), True),
            ((), ("activate_guildmage",), True),
            (("unrelated",), ("attack",), False),
            ((), (), False)):
        kind = "persistent_restriction" if disabled or prohibited else "tempo_change"
        derived = adapt(event("plan-matrix", hostile, kind, disabled=disabled,
                              prohibited=prohibited))
        assert ("prevents_next_main_plan" in derived.public_facts) is expected

    invalid = (
        lambda: adapt(event("x", hostile, "immediate_effect")),
        lambda: adapt(event("x", hostile, "persistent_restriction")),
        lambda: adapt(event("x", hostile, "tempo_change", damage=1)),
        lambda: adapt(event("x", hostile, "remove_target")),
        lambda: adapt(event("x", permanent("self-1", "Island", "self"), "tempo_change")),
        lambda: adapt(event("x", permanent("g", "Izzet Guildmage", "self", False),
                                  "remove_target")),
        lambda: adapt(event("x", hostile, "unknown")),
        lambda: adapt(event("bad id", hostile, "tempo_change")),
        lambda: adapt(event("x", hostile, "immediate_effect", damage=-1)),
        lambda: adapt_observed_event(event("x", hostile, "tempo_change"),
            status=PublicPlayerStatus(0), next_main_plan=PLAN, izzet_state=state(False)),
        lambda: adapt_observed_event(event("x", hostile, "tempo_change"),
            status=PublicPlayerStatus(5, 10), next_main_plan=PLAN, izzet_state=state(False)),
        lambda: adapt_observed_event(event("x", hostile, "tempo_change"),
            status=PublicPlayerStatus(5), next_main_plan=FrozenNextMainPlan(
                ("duplicate", "duplicate"), ()), izzet_state=state(False)),
        lambda: adapt_observed_event(event("x", hostile, "tempo_change"),
            status=PublicPlayerStatus(5), next_main_plan=FrozenNextMainPlan(
                (), ("Bad-Tag",)), izzet_state=state(False)),
        lambda: adapt_observed_event(event("x", hostile, "tempo_change"),
            status=PublicPlayerStatus(5), next_main_plan=PLAN, izzet_state=object()),
    )
    for callback in invalid:
        expect_rejection(callback)

    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-21-concrete-state-adapter")
    print("opponent_information_scope=public_only")
    print("responder_information_scope=own_legally_known_state")
    print("opponent_hidden_information_used=0")
    print(f"exhaustive_threshold_states={exhaustive}")
    print("plan_intersection_states=4")
    print(f"malformed_inputs_rejected={len(invalid)}")
    print("sampled_games=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE21_SEED_FREE_VALIDATED")


if __name__ == "__main__":
    main()
