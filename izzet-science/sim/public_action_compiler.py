#!/usr/bin/env python3
"""Phase-26 seed-free compiler for public opponent actions.

This module intentionally accepts only public state plus already-observed/revealed
action descriptors. It does not know the opponent hand, library, seeds, or future draws.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Tuple

COMMANDER_DAMAGE_THRESHOLD = 16

@dataclass(frozen=True)
class PublicPermanent:
    object_id: str
    card_name: str
    controller: str
    tapped: bool = False
    summoning_sick: bool = False
    mana_colors: Tuple[str, ...] = ()
    mana_amount: int = 0
    has_tap_cost: bool = False
    hexproof: bool = False
    shroud: bool = False
    protection_colors: Tuple[str, ...] = ()
    ward_generic: int = 0

@dataclass(frozen=True)
class PublicState:
    phase: str
    step: str
    active_player: str
    priority_player: str
    permanents: Tuple[PublicPermanent, ...]
    observed_cards: Tuple[str, ...] = ()
    commander_damage_to_self: int = 0

@dataclass(frozen=True)
class ObservedActionSpec:
    action_id: str
    source_name: str
    surface: str
    speed: str  # "instant", "sorcery", "activated", "triggered"
    generic_cost: int = 0
    colored_cost: Tuple[str, ...] = ()
    target_type: str | None = None
    target_controller: str | None = None
    source_color: str | None = None
    requires_tap_source_id: str | None = None

@dataclass(frozen=True)
class CompiledAction:
    action_id: str
    source_name: str
    surface: str
    target_id: str | None
    payment_source_ids: Tuple[str, ...]
    reason: str

def _stable_permanents(state: PublicState):
    return tuple(sorted(state.permanents, key=lambda p: p.object_id))

def _mana_units(state: PublicState):
    units=[]
    for p in _stable_permanents(state):
        if p.mana_amount <= 0:
            continue
        if p.tapped:
            continue
        if p.has_tap_cost and p.summoning_sick:
            continue
        # one activation per permanent; multi-mana permanents contribute amount units
        units.append((p.object_id,p.mana_amount,set(p.mana_colors)))
    return units

def _find_payment(state: PublicState, generic: int, colored: Tuple[str,...]):
    import itertools
    units=_mana_units(state)
    need_total=generic+len(colored)
    if need_total==0:
        return ()
    for k in range(1,len(units)+1):
        for subset in itertools.combinations(units,k):
            if sum(a for _,a,_ in subset) < need_total:
                continue
            # Assign colored symbols to source activations. A source that produces
            # multiple mana may satisfy multiple symbols only up to its public amount.
            ids=[x[0] for x in subset]
            amounts=[x[1] for x in subset]
            colors=[x[2] for x in subset]
            for picks in itertools.product(range(len(subset)), repeat=len(colored)):
                used=[0]*len(subset)
                legal=True
                for sym,i in zip(colored,picks):
                    used[i]+=1
                    if sym not in colors[i] or used[i]>amounts[i]:
                        legal=False; break
                if not legal:
                    continue
                colored_spent=len(colored)
                remaining=sum(amounts)-colored_spent
                if remaining >= generic:
                    return tuple(ids)
    return None

def _timing_legal(state: PublicState, speed: str) -> bool:
    if speed in {"instant","activated","triggered"}:
        return state.priority_player == "opponent"
    if speed=="sorcery":
        return (state.active_player=="opponent" and state.priority_player=="opponent"
                and state.phase=="main" and state.step in {"precombat","postcombat"})
    return False

def _target_legal(target: PublicPermanent, spec: ObservedActionSpec) -> bool:
    if spec.target_controller and target.controller != spec.target_controller:
        return False
    if spec.target_type=="creature" and target.card_name == "":
        return False
    if target.shroud:
        return False
    if target.hexproof and target.controller=="self":
        return False
    if spec.source_color and spec.source_color in target.protection_colors:
        return False
    return True

def compile_public_actions(
    state: PublicState,
    observed_actions: Tuple[ObservedActionSpec,...],
) -> Tuple[CompiledAction,...]:
    """Compile only legal actions derivable from public/observed information."""
    if state.commander_damage_to_self >= COMMANDER_DAMAGE_THRESHOLD:
        return ()
    if tuple(state.observed_cards) != tuple(sorted(state.observed_cards)):
        raise ValueError("observed_cards must use canonical sorted ordering")
    out=[]
    seen=set()
    for spec in sorted(observed_actions,key=lambda a:a.action_id):
        if spec.action_id in seen:
            raise ValueError("duplicate observed action id")
        seen.add(spec.action_id)
        if spec.source_name not in state.observed_cards:
            raise ValueError("observed action source is not in observed_cards")
        if not _timing_legal(state,spec.speed):
            continue
        if spec.requires_tap_source_id is not None:
            src=next((p for p in state.permanents if p.object_id==spec.requires_tap_source_id),None)
            if src is None or src.tapped or src.summoning_sick:
                continue
        payment=_find_payment(state,spec.generic_cost,spec.colored_cost)
        if payment is None:
            continue
        targets=(None,)
        if spec.target_type is not None:
            targets=tuple(p.object_id for p in _stable_permanents(state) if _target_legal(p,spec))
        for tid in targets:
            out.append(CompiledAction(spec.action_id,spec.source_name,spec.surface,tid,payment,"public_legal"))
    return tuple(sorted(out,key=lambda a:(a.action_id,a.target_id or "",a.payment_source_ids)))
