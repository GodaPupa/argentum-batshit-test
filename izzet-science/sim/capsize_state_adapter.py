#!/usr/bin/env python3
"""Concrete state adapter for the frozen Capsize event ledger.

Opponent-event inputs are public. The one responder-specific predicate reads the
Izzet player's own state to determine whether its primary combo can launch now.
No opponent hand, library, future action, or sampled event is represented.
"""
from __future__ import annotations

from dataclasses import dataclass
import re

from capsize_event_ledger import PublicEventRecord
from capsize_interaction import PermanentTarget, legal_capsize_target
import mana_harness as harness


_IDENTITY = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
_ACTION_TAG = re.compile(r"[a-z][a-z0-9_]{0,63}")


@dataclass(frozen=True)
class PublicPlayerStatus:
    life: int
    poison_counters: int = 0


@dataclass(frozen=True)
class FrozenNextMainPlan:
    required_object_ids: tuple[str, ...] = ()
    required_action_tags: tuple[str, ...] = ()


@dataclass(frozen=True)
class ObservedPermanent:
    object_id: str
    card_name: str
    controller: str
    is_commander: bool = False
    zone: str = "battlefield"
    hexproof: bool = False
    shroud: bool = False


@dataclass(frozen=True)
class ObservedOpponentEvent:
    event_id: str
    policy_id: str
    window_id: str
    turn_number: int
    sequence_number: int
    source_id: str
    target: ObservedPermanent
    event_kind: str
    damage_before_next_response: int = 0
    life_loss_before_next_response: int = 0
    poison_before_next_response: int = 0
    disabled_through_next_main: tuple[str, ...] = ()
    prohibited_through_next_main: tuple[str, ...] = ()


def _identity(name: str, value: object) -> None:
    if not isinstance(value, str) or not _IDENTITY.fullmatch(value):
        raise ValueError(f"{name} must be stable 1-64 character ASCII identity")


def _unique_tuple(name: str, values: object, pattern: re.Pattern[str]) -> None:
    if not isinstance(values, tuple):
        raise ValueError(f"{name} must be a tuple")
    if len(set(values)) != len(values):
        raise ValueError(f"{name} values must be unique")
    if any(not isinstance(value, str) or not pattern.fullmatch(value)
           for value in values):
        raise ValueError(f"{name} contains invalid identity")


def _nonnegative(name: str, value: object) -> None:
    if type(value) is not int or value < 0:
        raise ValueError(f"{name} must be a nonnegative integer")


def validate_public_status(status: PublicPlayerStatus) -> None:
    if not isinstance(status, PublicPlayerStatus):
        raise ValueError("status must be PublicPlayerStatus")
    if type(status.life) is not int or status.life < 1:
        raise ValueError("life must be a positive integer in a response window")
    if type(status.poison_counters) is not int or not 0 <= status.poison_counters < 10:
        raise ValueError("poison counters must be an integer from zero through nine")


def validate_next_main_plan(plan: FrozenNextMainPlan) -> None:
    if not isinstance(plan, FrozenNextMainPlan):
        raise ValueError("plan must be FrozenNextMainPlan")
    _unique_tuple("required_object_ids", plan.required_object_ids, _IDENTITY)
    _unique_tuple("required_action_tags", plan.required_action_tags, _ACTION_TAG)


def validate_observed_event(event: ObservedOpponentEvent) -> None:
    if not isinstance(event, ObservedOpponentEvent):
        raise ValueError("event must be ObservedOpponentEvent")
    for name in ("event_id", "policy_id", "window_id", "source_id"):
        _identity(name, getattr(event, name))
    for name in ("turn_number", "sequence_number"):
        value = getattr(event, name)
        if type(value) is not int or value < 1:
            raise ValueError(f"{name} must be a positive integer")
    if event.event_kind not in {
            "immediate_effect", "remove_target", "persistent_restriction",
            "tempo_change"}:
        raise ValueError("unknown observed event kind")
    for name in ("damage_before_next_response", "life_loss_before_next_response",
                 "poison_before_next_response"):
        _nonnegative(name, getattr(event, name))
    _unique_tuple("disabled_through_next_main",
                  event.disabled_through_next_main, _IDENTITY)
    _unique_tuple("prohibited_through_next_main",
                  event.prohibited_through_next_main, _ACTION_TAG)

    target = event.target
    if not isinstance(target, ObservedPermanent):
        raise ValueError("target must be ObservedPermanent")
    _identity("target object_id", target.object_id)
    if not isinstance(target.card_name, str) or not target.card_name.strip():
        raise ValueError("target card name must be nonempty")
    if target.controller not in {"self", "opponent"}:
        raise ValueError("target controller must be self or opponent")
    for name in ("is_commander", "hexproof", "shroud"):
        if type(getattr(target, name)) is not bool:
            raise ValueError(f"target {name} must be Boolean")
    if target.zone != "battlefield":
        raise ValueError("observed target must currently be on the battlefield")

    immediate = (event.damage_before_next_response or
                 event.life_loss_before_next_response or
                 event.poison_before_next_response)
    restrictions = (event.disabled_through_next_main or
                    event.prohibited_through_next_main)
    if event.event_kind == "immediate_effect" and not immediate:
        raise ValueError("immediate effect must expose a nonzero public amount")
    if event.event_kind == "persistent_restriction" and not restrictions:
        raise ValueError("persistent restriction must expose a public restriction")
    if event.event_kind in {"remove_target", "tempo_change"} and (immediate or restrictions):
        raise ValueError("event kind contradicts its public consequences")
    if target.controller == "self":
        if target.card_name != "Izzet Guildmage" or event.event_kind != "remove_target":
            raise ValueError("only removal targeting own Izzet Guildmage is supported")
        if target.is_commander is not True:
            raise ValueError("own Izzet Guildmage target must retain commander identity")
    elif event.event_kind == "remove_target":
        raise ValueError("opponent-target removal is not an opponent event for this adapter")


def _target_class(target: ObservedPermanent) -> str:
    if target.controller == "self":
        return "own_guildmage"
    return "opposing_commander" if target.is_commander else "opposing_permanent"


def adapt_observed_event(
    event: ObservedOpponentEvent,
    *,
    status: PublicPlayerStatus,
    next_main_plan: FrozenNextMainPlan,
    izzet_state: harness.DevState,
) -> PublicEventRecord:
    """Derive one ledger record from public consequences and responder-owned state."""
    validate_observed_event(event)
    validate_public_status(status)
    validate_next_main_plan(next_main_plan)
    if not isinstance(izzet_state, harness.DevState):
        raise ValueError("izzet_state must be a DevState")

    target = event.target
    target_class = _target_class(target)
    permanent = PermanentTarget(
        target.card_name, controller=target.controller, zone=target.zone,
        is_commander=target.is_commander, hexproof=target.hexproof,
        shroud=target.shroud)
    legal = legal_capsize_target(permanent)
    facts = []
    if (event.damage_before_next_response + event.life_loss_before_next_response >= status.life or
            status.poison_counters + event.poison_before_next_response >= 10):
        facts.append("loss_before_next_response")
    if target_class == "own_guildmage":
        facts.append("targets_own_guildmage")
        if harness.primary_combo_launch_feasible(izzet_state):
            facts.append("primary_combo_lethal_now")
    else:
        object_lock = bool(set(next_main_plan.required_object_ids) &
                           set(event.disabled_through_next_main))
        action_lock = bool(set(next_main_plan.required_action_tags) &
                           set(event.prohibited_through_next_main))
        if object_lock or action_lock:
            facts.append("prevents_next_main_plan")

    return PublicEventRecord(
        event.event_id, event.policy_id, event.window_id, event.turn_number,
        event.sequence_number, event.source_id, target.object_id, target_class,
        legal, tuple(facts))
