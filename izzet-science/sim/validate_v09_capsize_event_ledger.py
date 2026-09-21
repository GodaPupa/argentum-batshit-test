#!/usr/bin/env python3
"""Seed-free Phase-20 validation of the public-state event ledger contract."""
from __future__ import annotations

from itertools import combinations
import hashlib
from pathlib import Path

from capsize_event_ledger import (
    OpponentPolicyContract,
    PublicEventRecord,
    PUBLIC_FACTS,
    classify_public_event,
    compile_response_window,
)
from capsize_response_policy import select_capsize_response


ROOT = Path(__file__).resolve().parents[2]
CONTROL = ROOT / "izzet-science/v0.7-control.md"
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
POLICY = OpponentPolicyContract("public-opponent-v1")


def record(event_id, target_class, facts=(), *, sequence=1, legal=True,
           policy_id="public-opponent-v1", window_id="t8-priority-1", turn=8):
    return PublicEventRecord(
        event_id, policy_id, window_id, turn, sequence,
        f"source-{event_id}", f"target-{event_id}", target_class, legal,
        tuple(facts))


def expect_rejection(callback):
    try:
        callback()
    except (ValueError, TypeError):
        return
    raise AssertionError("accepted invalid Phase-20 ledger input")


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest() != CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")

    loss = record("loss", "opposing_permanent", ("loss_before_next_response",))
    lock = record("lock", "opposing_commander", ("prevents_next_main_plan",), sequence=2)
    tempo = record("tempo", "opposing_permanent", (), sequence=3)
    rescue = record("rescue", "own_guildmage",
                    ("targets_own_guildmage", "primary_combo_lethal_now"), sequence=4)
    nonlethal = record("nonlethal", "own_guildmage",
                       ("targets_own_guildmage",), sequence=5)
    window = compile_response_window(POLICY, [loss, lock, tempo, rescue, nonlethal])
    assert window.ledger_event_ids == ("loss", "lock", "tempo", "rescue", "nonlethal")
    assert window.omitted_event_ids == ("nonlethal",)
    assert tuple(event.event_class for event in window.response_events) == (
        "imminent_loss", "next_main_lock", "tempo_only", "guildmage_combo_removal")
    decision = select_capsize_response(
        window.response_events, one_shot_ready=True, buyback_ready=False,
        guildmage_battlefield=True, combo_lethal_now=True)
    assert decision.selected_event_id == "loss" and decision.action == "cast_normal"

    # Every fact subset and target class is either deterministically classified or
    # rejected by the frozen public-state consistency rules.
    exhaustive = accepted = rejected = omitted = 0
    for target_class in ("own_guildmage", "opposing_permanent", "opposing_commander"):
        for size in range(len(PUBLIC_FACTS) + 1):
            for facts in combinations(PUBLIC_FACTS, size):
                for legal in (False, True):
                    exhaustive += 1
                    candidate = record(f"e{exhaustive}", target_class, facts, legal=legal)
                    try:
                        first = classify_public_event(candidate)
                        second = classify_public_event(candidate)
                        assert first == second
                        accepted += 1
                        omitted += first is None
                    except ValueError:
                        rejected += 1
    assert exhaustive == 96 and accepted + rejected == exhaustive

    replay = compile_response_window(POLICY, (loss, lock, tempo, rescue, nonlethal))
    assert replay == window

    invalid = (
        lambda: compile_response_window(POLICY, []),
        lambda: compile_response_window(POLICY, [loss, loss]),
        lambda: compile_response_window(POLICY, [lock, loss]),
        lambda: compile_response_window(POLICY, [loss, record("x", "opposing_permanent",
            sequence=2, window_id="other")]),
        lambda: compile_response_window(POLICY, [loss, record("x", "opposing_permanent",
            sequence=2, turn=9)]),
        lambda: compile_response_window(POLICY, [record("x", "opposing_permanent",
            policy_id="other")]),
        lambda: compile_response_window(OpponentPolicyContract("p", information_scope="all"), [loss]),
        lambda: compile_response_window(OpponentPolicyContract("p", deterministic=False), [loss]),
        lambda: compile_response_window(OpponentPolicyContract("p", uses_hidden_information=True), [loss]),
        lambda: compile_response_window(OpponentPolicyContract("p", samples_events=True), [loss]),
        lambda: classify_public_event(record("x", "own_guildmage", ())),
        lambda: classify_public_event(record("x", "opposing_permanent",
            ("targets_own_guildmage",))),
        lambda: classify_public_event(record("x", "opposing_permanent",
            ("primary_combo_lethal_now",))),
        lambda: classify_public_event(record("x", "own_guildmage",
            ("targets_own_guildmage", "loss_before_next_response"))),
        lambda: classify_public_event(record("x", "own_guildmage",
            ("targets_own_guildmage", "prevents_next_main_plan"))),
        lambda: classify_public_event(record("x", "opposing_permanent", ("unknown",))),
        lambda: classify_public_event(record("x", "opposing_permanent", (), sequence=0)),
        lambda: classify_public_event(record("bad id", "opposing_permanent")),
    )
    for callback in invalid:
        expect_rejection(callback)

    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-20-public-event-ledger")
    print("policy_scope=public_only_deterministic_seed_free")
    print(f"exhaustive_public_fact_states={exhaustive}")
    print(f"consistent_states_classified={accepted}")
    print(f"contradictory_states_rejected={rejected}")
    print(f"nonlethal_guildmage_states_omitted={omitted}")
    print(f"malformed_inputs_rejected={len(invalid)}")
    print("sampled_games=0")
    print("experimental_seeds_assigned=0")
    print("experimental_seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE20_SEED_FREE_VALIDATED")


if __name__ == "__main__":
    main()
