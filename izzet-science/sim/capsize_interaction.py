#!/usr/bin/env python3
"""Seed-free Capsize interaction semantics for the Izzet Science lab.

This module deliberately models one declared permanent and one Capsize resolution.
It does not assign an opponent deck, threat frequency, recast policy, or win value.
"""
from __future__ import annotations

from dataclasses import dataclass

from mana_harness import DevState, capsize_generic_cost, pay_colored_mutating


@dataclass
class PermanentTarget:
    card: str
    controller: str = "opponent"
    zone: str = "battlefield"
    is_commander: bool = False
    hexproof: bool = False
    shroud: bool = False


@dataclass(frozen=True)
class CapsizeResolution:
    cast: bool
    resolved: bool
    bought_back: bool
    spell_zone: str
    target_zone: str
    commander_destination: str | None


def legal_capsize_target(target: PermanentTarget, caster: str = "self") -> bool:
    if target.zone != "battlefield" or target.shroud:
        return False
    if target.hexproof and target.controller != caster:
        return False
    return True


def cast_and_resolve_capsize(
    state: DevState,
    target: PermanentTarget,
    *,
    buyback: bool,
    caster: str = "self",
    countered: bool = False,
    target_still_legal: bool = True,
    commander_destination: str = "hand",
) -> CapsizeResolution:
    """Cast Capsize and resolve one explicit outcome, preserving failure atomicity.

    ``commander_destination`` records the commander's owner's choice; the model never
    assumes that bouncing an opposing commander creates commander tax.
    """
    if commander_destination not in {"hand", "command"}:
        raise ValueError("commander destination must be hand or command")
    if not target.is_commander and commander_destination != "hand":
        raise ValueError("only a commander may move to the command zone")
    if "Capsize" not in state.hand:
        return CapsizeResolution(False, False, False, "absent", target.zone, None)
    if not legal_capsize_target(target, caster):
        return CapsizeResolution(False, False, False, "hand", target.zone, None)

    generic = capsize_generic_cost(state, buyback)
    if not pay_colored_mutating(state, generic=generic, need_u=2):
        return CapsizeResolution(False, False, False, "hand", target.zone, None)
    state.hand.remove("Capsize")

    if countered or not target_still_legal or not legal_capsize_target(target, caster):
        return CapsizeResolution(True, False, False, "graveyard", target.zone, None)

    destination = commander_destination if target.is_commander else "hand"
    target.zone = destination
    spell_zone = "hand" if buyback else "graveyard"
    if buyback:
        state.hand.append("Capsize")
    return CapsizeResolution(True, True, buyback, spell_zone, target.zone,
                             destination if target.is_commander else None)
