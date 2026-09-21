#!/usr/bin/env python3
"""Seed-free Phase-23 validation of concrete fixture action generation."""
from __future__ import annotations

import hashlib
from itertools import combinations
from pathlib import Path

from capsize_event_ledger import OpponentPolicyContract, compile_response_window
from capsize_fixture_action_generator import (
    FROZEN_FIXTURE_IDENTITY,
    GUILDMAGE_ID,
    FixtureOpponentState,
    generate_fixture_actions,
)
from capsize_opponent_action_policy import select_opponent_action
from capsize_response_policy import select_capsize_response
from capsize_state_adapter import (
    FrozenNextMainPlan,
    PublicPlayerStatus,
    adapt_observed_event,
)
import mana_harness as harness


ROOT = Path(__file__).resolve().parents[2]
CONTROL = ROOT / "izzet-science/v0.7-control.md"
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
POLICY = OpponentPolicyContract("public-opponent-v1")
PLAN = FrozenNextMainPlan((GUILDMAGE_ID,), ("cast_sorcery", "activate_guildmage"))
SOURCES = tuple(spec.source_id for spec in FROZEN_FIXTURE_IDENTITY.action_specs)


def state(battlefield, mana=(1, 1, 1)):
    return FixtureOpponentState(
        POLICY.policy_id, "t8-priority-1", 8, 1,
        mana[0], mana[1], mana[2], tuple(battlefield))


def responder_state():
    value = harness.DevState(["Lava Spike", "Desperate Ritual"])
    value.turn = 8
    value.battlefield = [
        {"card": "Izzet Guildmage", "tapped": False, "entered": 2},
    ]
    for index, card in enumerate(["Mountain"] * 3 + ["Island"] * 2):
        value.battlefield.append(
            {"card": card, "tapped": False, "entered": index + 1})
    assert harness.primary_combo_launch_feasible(value)
    return value


def expect_rejection(callback):
    try:
        callback()
    except (ValueError, TypeError):
        return
    raise AssertionError("accepted invalid Phase-23 fixture input")


def subsets(values):
    for count in range(len(values) + 1):
        yield from combinations(values, count)


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest() != CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")

    # Exhaust every source-presence, Guildmage-presence, and colored-mana bit.
    exhaustive = 0
    for present_sources in subsets(SOURCES):
        for guildmage_present in (False, True):
            battlefield = present_sources + ((GUILDMAGE_ID,) if guildmage_present else ())
            for red in (0, 1):
                for black in (0, 1):
                    for blue in (0, 1):
                        generated = generate_fixture_actions(
                            FROZEN_FIXTURE_IDENTITY,
                            state(battlefield, (red, black, blue)))
                        actual = tuple(item.event.event_id for item in generated)
                        expected = []
                        for spec in FROZEN_FIXTURE_IDENTITY.action_specs:
                            colored = {"red": red, "black": black, "blue": blue}
                            legal = spec.source_id in present_sources and colored[spec.mana_color]
                            if spec.targets_guildmage:
                                legal = legal and guildmage_present
                            if legal:
                                expected.append(spec.action_id)
                        assert actual == tuple(expected)
                        assert all(item.legal_action for item in generated)
                        assert not any(item.uses_hidden_information for item in generated)
                        exhaustive += 1
    assert exhaustive == 256

    full = generate_fixture_actions(
        FROZEN_FIXTURE_IDENTITY, state(SOURCES + (GUILDMAGE_ID,)))
    assert tuple(item.event.event_id for item in full) == (
        "fixture-pressure", "fixture-removal", "fixture-lock", "fixture-tempo")
    assert generate_fixture_actions(
        FROZEN_FIXTURE_IDENTITY, state((), (0, 0, 0))) == ()

    # The generated actions feed the accepted Phase-22 policy without reshaping.
    status = PublicPlayerStatus(5)
    decision = select_opponent_action(
        POLICY, full, status=status, next_main_plan=PLAN)
    assert decision.selected_event_id == "fixture-pressure"
    assert decision.reason == "public_imminent_loss"
    assert select_opponent_action(
        POLICY, full[1:], status=PublicPlayerStatus(30),
        next_main_plan=PLAN).reason == "public_guildmage_removal"
    assert select_opponent_action(
        POLICY, full[2:], status=PublicPlayerStatus(30),
        next_main_plan=PLAN).reason == "public_next_main_lock"
    assert select_opponent_action(
        POLICY, full[3:], status=PublicPlayerStatus(30),
        next_main_plan=PLAN).reason == "public_tempo_only"

    adapted = adapt_observed_event(
        decision.selected_event, status=status, next_main_plan=PLAN,
        izzet_state=responder_state())
    window = compile_response_window(POLICY, [adapted])
    response = select_capsize_response(
        window.response_events, one_shot_ready=True, buyback_ready=False,
        guildmage_battlefield=True, combo_lethal_now=True)
    assert response.selected_event_id == "fixture-pressure"
    assert response.action == "cast_normal"

    invalid = (
        lambda: generate_fixture_actions(object(), state(())),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY, object()),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            FixtureOpponentState("", "window", 8, 1, 0, 0, 0, ())),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            FixtureOpponentState(POLICY.policy_id, "", 8, 1, 0, 0, 0, ())),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            FixtureOpponentState("bad policy", "window", 8, 1, 0, 0, 0, ())),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            FixtureOpponentState(POLICY.policy_id, "window", 0, 1, 0, 0, 0, ())),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            FixtureOpponentState(POLICY.policy_id, "window", 8, 0, 0, 0, 0, ())),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            FixtureOpponentState(POLICY.policy_id, "window", 8, 1, -1, 0, 0, ())),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            FixtureOpponentState(POLICY.policy_id, "window", 8, 1, True, 0, 0, ())),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            FixtureOpponentState(POLICY.policy_id, "window", 8, 1, 0, 0, 0, [])),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            state((SOURCES[0], SOURCES[0]))),
        lambda: generate_fixture_actions(FROZEN_FIXTURE_IDENTITY,
            state(("unknown-source",))),
    )
    for callback in invalid:
        expect_rejection(callback)

    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-23-fixture-action-generator")
    print(f"fixture_identity={FROZEN_FIXTURE_IDENTITY.identity_id}")
    print("fixture_is_deck=0")
    print("fixture_representative_matchup=0")
    print("generator_information_scope=public_only")
    print("generator_deterministic=1")
    print(f"exhaustive_generator_states={exhaustive}")
    print(f"malformed_inputs_rejected={len(invalid)}")
    print("sampled_games=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE23_SEED_FREE_VALIDATED")


if __name__ == "__main__":
    main()
