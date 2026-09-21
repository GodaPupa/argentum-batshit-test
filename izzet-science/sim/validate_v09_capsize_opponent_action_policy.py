#!/usr/bin/env python3
"""Seed-free Phase-22 validation of deterministic opponent action choice."""
from __future__ import annotations

import hashlib
from itertools import permutations
from pathlib import Path

from capsize_event_ledger import OpponentPolicyContract, compile_response_window
from capsize_opponent_action_policy import (
    OpponentActionCandidate,
    select_opponent_action,
)
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


def permanent(object_id, card, controller="opponent", commander=False,
              hexproof=False):
    return ObservedPermanent(object_id, card, controller, commander,
                             "battlefield", hexproof, False)


def event(event_id, target, kind, *, damage=0, life_loss=0, poison=0,
          disabled=(), prohibited=(), policy_id="public-opponent-v1",
          window="t8-priority-1", turn=8, sequence=1):
    return ObservedOpponentEvent(
        event_id, policy_id, window, turn, sequence, f"source-{event_id}",
        target, kind, damage, life_loss, poison, tuple(disabled),
        tuple(prohibited))


def candidate(value, legal=True, hidden=False):
    return OpponentActionCandidate(value, legal, hidden)


def responder_state(lethal=True):
    hand = ["Lava Spike", "Desperate Ritual"] if lethal else ["Lava Spike"]
    value = harness.DevState(hand)
    value.turn = 8
    value.battlefield = [{"card": "Izzet Guildmage", "tapped": False, "entered": 2}]
    for index, card in enumerate(["Mountain"] * 3 + ["Island"] * 2):
        value.battlefield.append({"card": card, "tapped": False, "entered": index + 1})
    assert harness.primary_combo_launch_feasible(value) is lethal
    return value


def expect_rejection(callback):
    try:
        callback()
    except (ValueError, TypeError):
        return
    raise AssertionError("accepted invalid Phase-22 opponent action input")


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest() != CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")

    hostile = permanent("hostile-1", "Hostile Permanent")
    commander = permanent("commander-1", "Opposing Commander", commander=True)
    guildmage = permanent("guildmage-self", "Izzet Guildmage", "self", True)
    lethal = candidate(event("lethal", hostile, "immediate_effect", damage=5))
    removal = candidate(event("removal", guildmage, "remove_target"))
    lock = candidate(event("lock", commander, "persistent_restriction",
                           prohibited=("cast_sorcery",)))
    tempo = candidate(event("tempo", hostile, "tempo_change"))
    status = PublicPlayerStatus(5, 0)

    decision = select_opponent_action(
        POLICY, [tempo, lock, removal, lethal], status=status,
        next_main_plan=PLAN)
    assert decision.selected_event_id == "lethal"
    assert decision.reason == "public_imminent_loss"

    # Candidate iteration order cannot change the result.
    permutation_states = 0
    for ordered in permutations((lethal, removal, lock, tempo)):
        replay = select_opponent_action(
            POLICY, ordered, status=status, next_main_plan=PLAN)
        assert replay == decision
        permutation_states += 1
    assert permutation_states == 24

    assert select_opponent_action(
        POLICY, [removal, lock, tempo], status=status,
        next_main_plan=PLAN).reason == "public_guildmage_removal"
    assert select_opponent_action(
        POLICY, [lock, tempo], status=status,
        next_main_plan=PLAN).reason == "public_next_main_lock"
    assert select_opponent_action(
        POLICY, [candidate(event("z", hostile, "tempo_change")),
                 candidate(event("a", commander, "tempo_change"))],
        status=status, next_main_plan=PLAN).selected_event_id == "a"
    assert select_opponent_action(
        POLICY, [], status=status, next_main_plan=PLAN).action == "pass"

    # Exhaust public loss thresholds with optional removal and plan-lock choices.
    exhaustive = 0
    for life in (1, 5):
        for damage_amount in (0, 1, 5):
            for poison_count in (0, 9):
                for poison_amount in (0, 1):
                    for include_removal in (False, True):
                        for include_lock in (False, True):
                            options = [candidate(event(
                                f"tempo-{exhaustive}", hostile, "tempo_change"))]
                            if damage_amount or poison_amount:
                                options.append(candidate(event(
                                    f"pressure-{exhaustive}", hostile,
                                    "immediate_effect", damage=damage_amount,
                                    poison=poison_amount)))
                            if include_removal:
                                options.append(candidate(event(
                                    f"remove-{exhaustive}", guildmage,
                                    "remove_target")))
                            if include_lock:
                                options.append(candidate(event(
                                    f"lock-{exhaustive}", commander,
                                    "persistent_restriction",
                                    disabled=("guildmage-self",))))
                            selected = select_opponent_action(
                                POLICY, options,
                                status=PublicPlayerStatus(life, poison_count),
                                next_main_plan=PLAN)
                            imminent = (damage_amount >= life or
                                        poison_count + poison_amount >= 10)
                            expected = (
                                "public_imminent_loss" if imminent else
                                "public_guildmage_removal" if include_removal else
                                "public_next_main_lock" if include_lock else
                                "public_tempo_only")
                            assert selected.reason == expected
                            exhaustive += 1
    assert exhaustive == 96

    # End-to-end: public opponent choice, responder-owned fact derivation, ledger,
    # and frozen response selection remain separate and deterministic.
    selected = select_opponent_action(
        POLICY, [tempo, lock, removal, lethal], status=status,
        next_main_plan=PLAN)
    adapted = adapt_observed_event(
        selected.selected_event, status=status, next_main_plan=PLAN,
        izzet_state=responder_state(True))
    window = compile_response_window(POLICY, [adapted])
    response = select_capsize_response(
        window.response_events, one_shot_ready=True, buyback_ready=False,
        guildmage_battlefield=True, combo_lethal_now=True)
    assert response.selected_event_id == "lethal"
    assert response.action == "cast_normal"

    invalid = (
        lambda: select_opponent_action(POLICY, "actions", status=status,
                                       next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [object()], status=status,
                                       next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [candidate(lethal.event, False)],
                                       status=status, next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [candidate(lethal.event, True, True)],
                                       status=status, next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [lethal, lethal], status=status,
                                       next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [lethal, candidate(event(
            "other-window", hostile, "tempo_change", window="other"))],
            status=status, next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [lethal, candidate(event(
            "other-turn", hostile, "tempo_change", turn=9))],
            status=status, next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [lethal, candidate(event(
            "other-sequence", hostile, "tempo_change", sequence=2))],
            status=status, next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [candidate(event(
            "other-policy", hostile, "tempo_change", policy_id="other"))],
            status=status, next_main_plan=PLAN),
        lambda: select_opponent_action(OpponentPolicyContract(
            "public-opponent-v1", uses_hidden_information=True), [tempo],
            status=status, next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [candidate(event(
            "bad-kind", hostile, "unknown"))], status=status,
            next_main_plan=PLAN),
        lambda: select_opponent_action(POLICY, [tempo],
            status=PublicPlayerStatus(0), next_main_plan=PLAN),
    )
    for callback in invalid:
        expect_rejection(callback)

    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-22-opponent-action-policy")
    print("priority=public_imminent_loss,public_guildmage_removal,public_next_main_lock,public_tempo_only")
    print("opponent_information_scope=public_only")
    print("opponent_hidden_information_used=0")
    print(f"permutation_states={permutation_states}")
    print(f"exhaustive_policy_states={exhaustive}")
    print(f"malformed_inputs_rejected={len(invalid)}")
    print("sampled_games=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE22_SEED_FREE_VALIDATED")


if __name__ == "__main__":
    main()
