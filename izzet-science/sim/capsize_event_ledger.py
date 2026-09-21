#!/usr/bin/env python3
"""Seed-free public-state event ledger for the frozen Capsize policy.

The ledger derives Phase-19 event classes from an enumerated set of public facts.
It assigns no event frequency, hidden information, opponent deck, or outcome value.
"""
from __future__ import annotations

from dataclasses import dataclass
import re

from capsize_response_policy import ResponseEvent


PUBLIC_FACTS = (
    "loss_before_next_response",
    "targets_own_guildmage",
    "primary_combo_lethal_now",
    "prevents_next_main_plan",
)
TARGET_CLASSES = ("own_guildmage", "opposing_permanent", "opposing_commander")
_IDENTITY = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")


@dataclass(frozen=True)
class OpponentPolicyContract:
    policy_id: str
    information_scope: str = "public_only"
    deterministic: bool = True
    uses_hidden_information: bool = False
    samples_events: bool = False


@dataclass(frozen=True)
class PublicEventRecord:
    event_id: str
    policy_id: str
    window_id: str
    turn_number: int
    sequence_number: int
    source_id: str
    target_id: str
    target_class: str
    legal_target: bool
    public_facts: tuple[str, ...]


@dataclass(frozen=True)
class CompiledResponseWindow:
    policy_id: str
    window_id: str
    turn_number: int
    ledger_event_ids: tuple[str, ...]
    response_events: tuple[ResponseEvent, ...]
    omitted_event_ids: tuple[str, ...]


def _identity(name: str, value: object) -> None:
    if not isinstance(value, str) or not _IDENTITY.fullmatch(value):
        raise ValueError(f"{name} must be stable 1-64 character ASCII identity")


def validate_policy_contract(contract: OpponentPolicyContract) -> None:
    if not isinstance(contract, OpponentPolicyContract):
        raise ValueError("contract must be an OpponentPolicyContract")
    _identity("policy_id", contract.policy_id)
    if contract.information_scope != "public_only":
        raise ValueError("opponent policy must use public state only")
    for name in ("deterministic", "uses_hidden_information", "samples_events"):
        if type(getattr(contract, name)) is not bool:
            raise ValueError(f"{name} must be Boolean")
    if not contract.deterministic:
        raise ValueError("opponent policy must be deterministic")
    if contract.uses_hidden_information:
        raise ValueError("hidden information is forbidden")
    if contract.samples_events:
        raise ValueError("event sampling is forbidden at this gate")


def validate_public_event(record: PublicEventRecord) -> None:
    if not isinstance(record, PublicEventRecord):
        raise ValueError("ledger entries must be PublicEventRecord values")
    for name in ("event_id", "policy_id", "window_id", "source_id", "target_id"):
        _identity(name, getattr(record, name))
    for name in ("turn_number", "sequence_number"):
        value = getattr(record, name)
        if type(value) is not int or value < 1:
            raise ValueError(f"{name} must be a positive integer")
    if record.target_class not in TARGET_CLASSES:
        raise ValueError("unknown target class")
    if type(record.legal_target) is not bool:
        raise ValueError("legal_target must be Boolean")
    if not isinstance(record.public_facts, tuple):
        raise ValueError("public_facts must be a tuple")
    if len(set(record.public_facts)) != len(record.public_facts):
        raise ValueError("public facts must be unique")
    if any(not isinstance(fact, str) or fact not in PUBLIC_FACTS
           for fact in record.public_facts):
        raise ValueError("unknown public fact")

    facts = set(record.public_facts)
    guildmage_target = record.target_class == "own_guildmage"
    if ("targets_own_guildmage" in facts) != guildmage_target:
        raise ValueError("Guildmage target fact and target class must coincide")
    if "primary_combo_lethal_now" in facts and not guildmage_target:
        raise ValueError("combo-lethal witness is valid only for own Guildmage")
    if guildmage_target and ({"loss_before_next_response", "prevents_next_main_plan"} & facts):
        raise ValueError("Phase-19 cannot encode another class on own Guildmage")


def classify_public_event(record: PublicEventRecord) -> ResponseEvent | None:
    """Derive a Phase-19 event, omitting an unqualified Guildmage ledger entry."""
    validate_public_event(record)
    facts = set(record.public_facts)
    if "loss_before_next_response" in facts:
        event_class = "imminent_loss"
    elif record.target_class == "own_guildmage":
        if "primary_combo_lethal_now" not in facts:
            return None
        event_class = "guildmage_combo_removal"
    elif "prevents_next_main_plan" in facts:
        event_class = "next_main_lock"
    else:
        event_class = "tempo_only"
    return ResponseEvent(record.event_id, event_class, record.target_class,
                         record.legal_target)


def compile_response_window(
    contract: OpponentPolicyContract,
    records: list[PublicEventRecord] | tuple[PublicEventRecord, ...],
) -> CompiledResponseWindow:
    """Validate one ordered public priority window and compile its policy input."""
    validate_policy_contract(contract)
    if not isinstance(records, (list, tuple)) or not records:
        raise ValueError("records must be a nonempty ordered list or tuple")
    seen_ids: set[str] = set()
    first_window = None
    first_turn = None
    previous_sequence = 0
    response_events = []
    omitted = []
    for record in records:
        validate_public_event(record)
        if record.policy_id != contract.policy_id:
            raise ValueError("ledger entry is bound to a different policy")
        if record.event_id in seen_ids:
            raise ValueError("duplicate ledger event identity")
        seen_ids.add(record.event_id)
        if first_window is None:
            first_window = record.window_id
            first_turn = record.turn_number
        if record.window_id != first_window or record.turn_number != first_turn:
            raise ValueError("one compiled window must share window and turn identity")
        if record.sequence_number <= previous_sequence:
            raise ValueError("ledger sequence must be strictly increasing")
        previous_sequence = record.sequence_number
        classified = classify_public_event(record)
        if classified is None:
            omitted.append(record.event_id)
        else:
            response_events.append(classified)
    return CompiledResponseWindow(
        contract.policy_id, first_window, first_turn,
        tuple(record.event_id for record in records),
        tuple(response_events), tuple(omitted))
