#!/usr/bin/env python3
"""Seed-free legal-action generator for the frozen Phase-23 fixture identity.

This identity is a synthetic, public battlefield fixture. It is not a deck and
does not represent a matchup. Its only purpose is to qualify the boundary from
one concrete opponent state to Phase-22 public action candidates.
"""
from __future__ import annotations

from dataclasses import dataclass
import re

from capsize_opponent_action_policy import OpponentActionCandidate
from capsize_state_adapter import ObservedOpponentEvent, ObservedPermanent


@dataclass(frozen=True)
class FixtureActionSpec:
    action_id: str
    source_id: str
    source_name: str
    mana_color: str
    event_kind: str
    damage: int = 0
    targets_guildmage: bool = False
    prohibited: tuple[str, ...] = ()


@dataclass(frozen=True)
class FixtureOpponentIdentity:
    identity_id: str
    version: int
    representative_matchup: bool
    is_deck: bool
    information_scope: str
    deterministic: bool
    action_specs: tuple[FixtureActionSpec, ...]


@dataclass(frozen=True)
class FixtureOpponentState:
    policy_id: str
    window_id: str
    turn_number: int
    sequence_number: int
    red_mana: int
    black_mana: int
    blue_mana: int
    battlefield_ids: tuple[str, ...]


PRESSURE_SOURCE = "fixture-pressure-source"
REMOVAL_SOURCE = "fixture-removal-source"
LOCK_SOURCE = "fixture-lock-source"
TEMPO_SOURCE = "fixture-tempo-source"
GUILDMAGE_ID = "guildmage-self"

FROZEN_FIXTURE_IDENTITY = FixtureOpponentIdentity(
    identity_id="phase23-public-action-fixture-v1",
    version=1,
    representative_matchup=False,
    is_deck=False,
    information_scope="public_only",
    deterministic=True,
    action_specs=(
        FixtureActionSpec(
            "fixture-pressure", PRESSURE_SOURCE, "Fixture Pressure Source",
            "red", "immediate_effect", damage=5),
        FixtureActionSpec(
            "fixture-removal", REMOVAL_SOURCE, "Fixture Removal Source",
            "black", "remove_target", targets_guildmage=True),
        FixtureActionSpec(
            "fixture-lock", LOCK_SOURCE, "Fixture Lock Source",
            "blue", "persistent_restriction",
            prohibited=("cast_sorcery",)),
        FixtureActionSpec(
            "fixture-tempo", TEMPO_SOURCE, "Fixture Tempo Source",
            "blue", "tempo_change"),
    ),
)

_ALL_BATTLEFIELD_IDS = {
    PRESSURE_SOURCE, REMOVAL_SOURCE, LOCK_SOURCE, TEMPO_SOURCE, GUILDMAGE_ID,
}
_IDENTITY = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")


def validate_fixture_identity(identity: FixtureOpponentIdentity) -> None:
    if not isinstance(identity, FixtureOpponentIdentity):
        raise ValueError("identity must be FixtureOpponentIdentity")
    if identity != FROZEN_FIXTURE_IDENTITY:
        raise ValueError("fixture identity does not match the frozen Phase-23 identity")
    if identity.representative_matchup or identity.is_deck:
        raise ValueError("qualification fixture cannot claim matchup or deck status")
    if identity.information_scope != "public_only" or not identity.deterministic:
        raise ValueError("fixture identity must be deterministic and public-only")


def _validate_state(state: FixtureOpponentState) -> None:
    if not isinstance(state, FixtureOpponentState):
        raise ValueError("state must be FixtureOpponentState")
    if not isinstance(state.policy_id, str) or not _IDENTITY.fullmatch(state.policy_id):
        raise ValueError("policy_id must be a stable 1-64 character ASCII identity")
    if not isinstance(state.window_id, str) or not _IDENTITY.fullmatch(state.window_id):
        raise ValueError("window_id must be a stable 1-64 character ASCII identity")
    for name in ("turn_number", "sequence_number"):
        value = getattr(state, name)
        if type(value) is not int or value < 1:
            raise ValueError(f"{name} must be a positive integer")
    for name in ("red_mana", "black_mana", "blue_mana"):
        value = getattr(state, name)
        if type(value) is not int or not 0 <= value <= 20:
            raise ValueError(f"{name} must be an integer from zero through twenty")
    if not isinstance(state.battlefield_ids, tuple):
        raise ValueError("battlefield_ids must be a tuple")
    if len(set(state.battlefield_ids)) != len(state.battlefield_ids):
        raise ValueError("battlefield identities must be unique")
    if any(not isinstance(value, str) or value not in _ALL_BATTLEFIELD_IDS
           for value in state.battlefield_ids):
        raise ValueError("battlefield contains an unknown fixture identity")


def generate_fixture_actions(
    identity: FixtureOpponentIdentity,
    state: FixtureOpponentState,
) -> tuple[OpponentActionCandidate, ...]:
    """Generate every individually legal action in frozen identity order."""
    validate_fixture_identity(identity)
    _validate_state(state)
    battlefield = set(state.battlefield_ids)
    mana = {
        "red": state.red_mana,
        "black": state.black_mana,
        "blue": state.blue_mana,
    }
    candidates = []
    for spec in identity.action_specs:
        if spec.source_id not in battlefield or mana[spec.mana_color] < 1:
            continue
        if spec.targets_guildmage and GUILDMAGE_ID not in battlefield:
            continue
        if spec.targets_guildmage:
            target = ObservedPermanent(
                GUILDMAGE_ID, "Izzet Guildmage", "self", True)
        else:
            target = ObservedPermanent(
                spec.source_id, spec.source_name, "opponent")
        event = ObservedOpponentEvent(
            spec.action_id, state.policy_id, state.window_id,
            state.turn_number, state.sequence_number, spec.source_id, target,
            spec.event_kind, damage_before_next_response=spec.damage,
            prohibited_through_next_main=spec.prohibited)
        candidates.append(OpponentActionCandidate(event))
    return tuple(candidates)
