#!/usr/bin/env python3
"""Deterministic Phase-15 validation for stateful Capsize interaction semantics."""
from __future__ import annotations

import copy

from capsize_interaction import (
    PermanentTarget,
    capsize_generic_cost,
    cast_and_resolve_capsize,
    legal_capsize_target,
)
from mana_harness import DevState


def state_with(cards, battlefield):
    state = DevState(cards)
    state.turn = 8
    state.battlefield = [
        {"card": card, "tapped": False, "entered": 1} for card in battlefield
    ]
    return state


def main():
    threat = PermanentTarget("Fierce Witchstalker")
    plain = state_with(["Capsize"], ["Island", "Island", "Mountain"])
    result = cast_and_resolve_capsize(plain, threat, buyback=False)
    assert result.cast and result.resolved and not result.bought_back
    assert result.spell_zone == "graveyard" and threat.zone == "hand"
    assert "Capsize" not in plain.hand

    retained_target = PermanentTarget("Pactdoll Terror")
    retained = state_with(["Capsize"], ["Island"] * 4 + ["Mountain"] * 2)
    result = cast_and_resolve_capsize(retained, retained_target, buyback=True)
    assert result.cast and result.resolved and result.bought_back
    assert result.spell_zone == "hand" and retained_target.zone == "hand"
    assert retained.hand == ["Capsize"]

    discounted_target = PermanentTarget("Guardian of the Guildpact")
    discounted = state_with(["Capsize"],
                            ["Goblin Electromancer"] + ["Island"] * 3 + ["Mountain"] * 2)
    assert capsize_generic_cost(discounted, True) == 3
    result = cast_and_resolve_capsize(discounted, discounted_target, buyback=True)
    assert result.resolved and result.bought_back

    short_target = PermanentTarget("Myr Enforcer")
    short = state_with(["Capsize", "Counterspell"], ["Island"] * 3 + ["Mountain"] * 2)
    before_hand = list(short.hand)
    before_battlefield = copy.deepcopy(short.battlefield)
    result = cast_and_resolve_capsize(short, short_target, buyback=True)
    assert not result.cast and short.hand == before_hand
    assert short.battlefield == before_battlefield and short_target.zone == "battlefield"

    assert not legal_capsize_target(PermanentTarget("Slippery Bogle", hexproof=True))
    assert not legal_capsize_target(PermanentTarget("Any Permanent", shroud=True))
    assert legal_capsize_target(PermanentTarget("Izzet Guildmage", controller="self", hexproof=True))
    illegal = state_with(["Capsize"], ["Island", "Island", "Mountain"])
    illegal_before = copy.deepcopy(illegal.battlefield)
    illegal_result = cast_and_resolve_capsize(
        illegal, PermanentTarget("Slippery Bogle", hexproof=True), buyback=False)
    assert not illegal_result.cast and illegal.battlefield == illegal_before
    assert illegal.hand == ["Capsize"]
    absent = state_with([], ["Island", "Island", "Mountain"])
    absent_result = cast_and_resolve_capsize(
        absent, PermanentTarget("Fierce Witchstalker"), buyback=False)
    assert not absent_result.cast and absent_result.spell_zone == "absent"

    hand_commander = PermanentTarget("Lilysplash Mentor", is_commander=True)
    command_commander = PermanentTarget("Lilysplash Mentor", is_commander=True)
    hand_state = state_with(["Capsize"], ["Island", "Island", "Mountain"])
    command_state = state_with(["Capsize"], ["Island", "Island", "Mountain"])
    hand_result = cast_and_resolve_capsize(hand_state, hand_commander, buyback=False,
                                           commander_destination="hand")
    command_result = cast_and_resolve_capsize(command_state, command_commander, buyback=False,
                                              commander_destination="command")
    assert hand_result.commander_destination == "hand" and hand_commander.zone == "hand"
    assert command_result.commander_destination == "command" and command_commander.zone == "command"

    counter_target = PermanentTarget("Blood Researcher")
    countered = state_with(["Capsize"], ["Island"] * 4 + ["Mountain"] * 2)
    result = cast_and_resolve_capsize(countered, counter_target, buyback=True, countered=True)
    assert result.cast and not result.resolved and not result.bought_back
    assert result.spell_zone == "graveyard" and counter_target.zone == "battlefield"
    assert "Capsize" not in countered.hand

    vanished_target = PermanentTarget("Carrier Thrall")
    vanished = state_with(["Capsize"], ["Island"] * 4 + ["Mountain"] * 2)
    result = cast_and_resolve_capsize(vanished, vanished_target, buyback=True,
                                      target_still_legal=False)
    assert result.cast and not result.resolved and result.spell_zone == "graveyard"
    assert vanished_target.zone == "battlefield" and "Capsize" not in vanished.hand

    signet_target = PermanentTarget("Fangren Marauder")
    signet = state_with(["Capsize"], ["Island", "Mountain", "Izzet Signet"])
    assert cast_and_resolve_capsize(signet, signet_target, buyback=False).resolved

    lens_target = PermanentTarget("Murmuring Mystic")
    lens = state_with(["Capsize"], ["Island", "Mountain", "Mountain", "Prismatic Lens"])
    assert cast_and_resolve_capsize(lens, lens_target, buyback=False).resolved

    compass_target = PermanentTarget("Golem Foundry")
    compass = state_with(["Capsize"], ["Island", "Mountain", "Star Compass"])
    assert cast_and_resolve_capsize(compass, compass_target, buyback=False).resolved

    try:
        cast_and_resolve_capsize(state_with(["Capsize"], ["Island"] * 3),
                                 PermanentTarget("Not a Commander"), buyback=False,
                                 commander_destination="command")
    except ValueError:
        pass
    else:
        raise AssertionError("accepted command-zone destination for noncommander")

    print("phase15_capsize_interaction_semantics=PASS")
    print("fixtures=12")
    print("samples=0")
    print("experimental_seeds_consumed=0")


if __name__ == "__main__":
    main()
