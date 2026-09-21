#!/usr/bin/env python3
"""Phase-33 position-1-only execution authorization boundary.

This module does not initialize or execute an official game. It validates that an
execution request is bound to the accepted Phase-31/32 identities and authorizes
position 1 only.
"""
from __future__ import annotations
from dataclasses import dataclass

POSITION1 = 1
CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
VECTOR_SHA256 = "5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f"
ASSIGNMENT_SHA256 = "b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3"
QUARANTINE_ARTIFACT_ID = 10627816776
RUNNER_SHA256 = "864d46745c7ffd1c1e9c8eef48c5a0ad14b34938aa0a6066d94ab7a394516fc3"
OPPONENT_ID = "veteran-beastrider-commander-clash-2025-v1"

@dataclass(frozen=True)
class AuthorizationRequest:
    position: int
    control_sha256: str
    vector_sha256: str
    assignment_sha256: str
    quarantine_artifact_id: int
    runner_sha256: str
    opponent_identity: str
    durable_attempt_present: bool
    seed_consumed: bool
    game_initialized: bool
    outcome_exposed: bool

@dataclass(frozen=True)
class AuthorizationDecision:
    authorized: bool
    position: int | None
    reason: str

def authorize_position1(req: AuthorizationRequest) -> AuthorizationDecision:
    if not isinstance(req, AuthorizationRequest):
        raise ValueError("request must be AuthorizationRequest")
    if req.position != POSITION1:
        return AuthorizationDecision(False, None, "position_not_authorized")
    expected = (
        req.control_sha256 == CONTROL_SHA256 and
        req.vector_sha256 == VECTOR_SHA256 and
        req.assignment_sha256 == ASSIGNMENT_SHA256 and
        req.quarantine_artifact_id == QUARANTINE_ARTIFACT_ID and
        req.runner_sha256 == RUNNER_SHA256 and
        req.opponent_identity == OPPONENT_ID
    )
    if not expected:
        return AuthorizationDecision(False, None, "identity_mismatch")
    if not req.durable_attempt_present:
        return AuthorizationDecision(False, None, "attempt_not_durable")
    if req.seed_consumed:
        return AuthorizationDecision(False, None, "seed_already_consumed")
    if req.game_initialized:
        return AuthorizationDecision(False, None, "game_already_initialized")
    if req.outcome_exposed:
        return AuthorizationDecision(False, None, "outcome_already_exposed")
    return AuthorizationDecision(True, POSITION1, "position1_authorized")
