#!/usr/bin/env python3
"""Seed-free deterministic opponent action policy for Capsize experiments.

The selector sees only public candidate actions and public Izzet status/plan
requirements. It does not receive the Izzet hand, an opponent hidden zone, a deck,
an event frequency, or a random source.
"""
from __future__ import annotations

from dataclasses import dataclass

from capsize_event_ledger import OpponentPolicyContract, validate_policy_contract
from capsize_state_adapter import (
    FrozenNextMainPlan,
    ObservedOpponentEvent,
    PublicPlayerStatus,
    validate_next_main_plan,
    validate_observed_event,
    validate_public_status,
)


@dataclass(frozen=True)
class OpponentActionCandidate:
    event: ObservedOpponentEvent
    legal_action: bool = True
    uses_hidden_information: bool = False


@dataclass(frozen=True)
class OpponentActionDecision:
    action: str
    selected_event_id: str | None
    selected_event: ObservedOpponentEvent | None
    reason: str


def _public_priority(
    event: ObservedOpponentEvent,
    status: PublicPlayerStatus,
    plan: FrozenNextMainPlan,
) -> tuple[int, str]:
    imminent = (
        event.damage_before_next_response + event.life_loss_before_next_response
        >= status.life or
        status.poison_counters + event.poison_before_next_response >= 10)
    if imminent:
        return 0, "public_imminent_loss"
    if (event.event_kind == "remove_target" and
            event.target.controller == "self" and
            event.target.card_name == "Izzet Guildmage"):
        return 1, "public_guildmage_removal"
    object_lock = bool(set(plan.required_object_ids) &
                       set(event.disabled_through_next_main))
    action_lock = bool(set(plan.required_action_tags) &
                       set(event.prohibited_through_next_main))
    if object_lock or action_lock:
        return 2, "public_next_main_lock"
    return 3, "public_tempo_only"


def select_opponent_action(
    contract: OpponentPolicyContract,
    candidates: list[OpponentActionCandidate] | tuple[OpponentActionCandidate, ...],
    *,
    status: PublicPlayerStatus,
    next_main_plan: FrozenNextMainPlan,
) -> OpponentActionDecision:
    """Choose at most one legal public action using stable priority and identity."""
    validate_policy_contract(contract)
    validate_public_status(status)
    validate_next_main_plan(next_main_plan)
    if not isinstance(candidates, (list, tuple)):
        raise ValueError("candidates must be an ordered list or tuple")
    if not candidates:
        return OpponentActionDecision("pass", None, None, "no_legal_action")

    seen = set()
    first = None
    qualified = []
    for candidate in candidates:
        if not isinstance(candidate, OpponentActionCandidate):
            raise ValueError("candidates must contain OpponentActionCandidate values")
        if type(candidate.legal_action) is not bool:
            raise ValueError("legal_action must be Boolean")
        if type(candidate.uses_hidden_information) is not bool:
            raise ValueError("uses_hidden_information must be Boolean")
        if not candidate.legal_action:
            raise ValueError("candidate set is contaminated by an illegal action")
        if candidate.uses_hidden_information:
            raise ValueError("opponent hidden information is forbidden")
        event = candidate.event
        validate_observed_event(event)
        if event.policy_id != contract.policy_id:
            raise ValueError("candidate is bound to a different policy")
        if event.event_id in seen:
            raise ValueError("duplicate candidate identity")
        seen.add(event.event_id)
        identity = (event.policy_id, event.window_id, event.turn_number,
                    event.sequence_number)
        if first is None:
            first = identity
        elif identity != first:
            raise ValueError("candidate actions must share one decision window")
        priority, reason = _public_priority(event, status, next_main_plan)
        qualified.append((priority, event.event_id, reason, event))

    _, _, reason, selected = min(qualified)
    return OpponentActionDecision("act", selected.event_id, selected, reason)
