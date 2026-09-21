#!/usr/bin/env python3
"""Phase-27 deterministic behavior over Phase-26 compiled public actions."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Tuple
from public_action_compiler import CompiledAction

@dataclass(frozen=True)
class PublicBehaviorContext:
    guildmage_object_id: str | None
    required_object_ids: Tuple[str, ...] = ()
    required_action_tags: Tuple[str, ...] = ()
    commander_damage_to_self: int = 0
    commander_damage_threshold: int = 16

@dataclass(frozen=True)
class BehaviorDecision:
    action: str
    selected_action_id: str | None
    selected_target_id: str | None
    reason: str

def _validate_context(ctx: PublicBehaviorContext) -> None:
    if tuple(ctx.required_object_ids) != tuple(sorted(ctx.required_object_ids)):
        raise ValueError("required_object_ids must be canonical sorted")
    if tuple(ctx.required_action_tags) != tuple(sorted(ctx.required_action_tags)):
        raise ValueError("required_action_tags must be canonical sorted")
    if ctx.commander_damage_threshold != 16:
        raise ValueError("commander damage threshold must remain frozen at 16")
    if ctx.commander_damage_to_self < 0:
        raise ValueError("commander damage cannot be negative")

def _priority(a: CompiledAction, ctx: PublicBehaviorContext):
    # The compiler guarantees legality; this layer only chooses among legal actions.
    terminal_pressure = (
        a.surface in {"commander_power_scaling", "commander_trample", "commander_untap"}
        and ctx.commander_damage_to_self >= ctx.commander_damage_threshold - 1
    )
    if terminal_pressure:
        return (0, "public_terminal_commander_pressure")

    if ctx.guildmage_object_id is not None and a.target_id == ctx.guildmage_object_id and        a.surface in {"targeted_creature_control", "targeted_noncreature_control"}:
        return (1, "public_guildmage_control")

    lock = (
        a.target_id in set(ctx.required_object_ids) or
        a.surface in set(ctx.required_action_tags)
    )
    if lock:
        return (2, "public_next_main_lock")

    if a.surface in {"targeted_creature_control", "targeted_noncreature_control",
                     "damage_prevention", "targeting_protection", "destruction_replacement"}:
        return (3, "public_control")

    if a.surface in {"commander_power_scaling", "commander_trample", "commander_entry_scaling",
                     "commander_untap"}:
        return (4, "public_commander_development")

    if a.surface in {"battlefield_mana_source", "aura_access"}:
        return (5, "public_development")

    return (6, "public_tempo")

def choose_observed_action(
    actions: Tuple[CompiledAction, ...],
    context: PublicBehaviorContext,
) -> BehaviorDecision:
    _validate_context(context)
    if not isinstance(actions, tuple):
        raise ValueError("actions must be a tuple from the canonical compiler boundary")
    seen=set()
    ranked=[]
    for a in actions:
        if not isinstance(a, CompiledAction):
            raise ValueError("actions must contain CompiledAction values")
        identity=(a.action_id,a.target_id,a.payment_source_ids)
        if identity in seen:
            raise ValueError("duplicate compiled action identity")
        seen.add(identity)
        p,reason=_priority(a,context)
        ranked.append((p,a.action_id,a.target_id or "",a.payment_source_ids,reason,a))
    if not ranked:
        return BehaviorDecision("pass",None,None,"no_legal_action")
    *_, reason, selected = min(ranked)
    return BehaviorDecision("act",selected.action_id,selected.target_id,reason)
