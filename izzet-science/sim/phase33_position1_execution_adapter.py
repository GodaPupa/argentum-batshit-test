#!/usr/bin/env python3
"""Official position-1 execution adapter boundary.

The adapter enforces the accepted Phase-32 journal and Phase-33 authorization.
It does not implement game rules. A separately qualified execution engine must
provide the required interface before an official seed can be revealed.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Protocol, Any

from phase32_execution_readiness import DurableAttemptJournal, FrozenPositionBinding
from phase33_position1_authorization import AuthorizationRequest, authorize_position1

@dataclass(frozen=True)
class Position1ExecutionResult:
    initialized: bool
    completed: bool
    ledger_payload: dict[str, Any] | None
    terminal_error: str | None = None

class QualifiedExecutionEngine(Protocol):
    engine_identity: str
    def run_position1(self, *, seed: int, assignment: str) -> dict[str, Any]: ...

class ExecutionSeedPermit:
    """Narrow capability object required for the only reveal path."""
    __slots__=("position",)
    def __init__(self, position: int):
        if position != 1:
            raise ValueError("permit is position-1-only")
        self.position=position

def _reveal(binding: FrozenPositionBinding, permit: ExecutionSeedPermit) -> int:
    if binding.position != 1 or permit.position != 1:
        raise RuntimeError("seed reveal is position-1-only")
    # Phase-32 OpaqueSeed intentionally exposes no general reveal method.
    # This adapter is the sole authorized boundary allowed to unwrap it.
    return binding.opaque_seed._value

def execute_position1_once(
    *,
    binding: FrozenPositionBinding,
    journal: DurableAttemptJournal,
    authorization_request: AuthorizationRequest,
    engine: QualifiedExecutionEngine,
    expected_engine_identity: str,
) -> Position1ExecutionResult:
    if binding.position != 1:
        raise ValueError("only position 1 is authorized")
    if getattr(engine,"engine_identity",None) != expected_engine_identity:
        raise ValueError("execution engine identity mismatch")

    # Attempt marker MUST preexist before authorization can succeed.
    if not journal.is_attempted(1):
        journal.attempt(1)

    req=AuthorizationRequest(
        position=authorization_request.position,
        control_sha256=authorization_request.control_sha256,
        vector_sha256=authorization_request.vector_sha256,
        assignment_sha256=authorization_request.assignment_sha256,
        quarantine_artifact_id=authorization_request.quarantine_artifact_id,
        runner_sha256=authorization_request.runner_sha256,
        opponent_identity=authorization_request.opponent_identity,
        durable_attempt_present=journal.is_attempted(1),
        seed_consumed=journal.is_consumed(1),
        game_initialized=authorization_request.game_initialized,
        outcome_exposed=authorization_request.outcome_exposed,
    )
    decision=authorize_position1(req)
    if not decision.authorized:
        raise RuntimeError(f"position 1 authorization denied: {decision.reason}")

    permit=ExecutionSeedPermit(1)
    seed=_reveal(binding,permit)

    # Consumption marker is durable immediately before handing the seed to engine.
    journal.consume_marker(1)
    try:
        payload=engine.run_position1(seed=seed,assignment=binding.assignment)
    except Exception as exc:
        journal.reject_terminal(1,"execution-engine-failure")
        return Position1ExecutionResult(True,False,None,exc.__class__.__name__)

    if not isinstance(payload,dict):
        journal.reject_terminal(1,"invalid-ledger-payload")
        return Position1ExecutionResult(True,False,None,"invalid-ledger-payload")

    required={"game_result","terminal_turn","terminal_reason","event_ledger"}
    if not required <= set(payload):
        journal.reject_terminal(1,"missing-ledger-fields")
        return Position1ExecutionResult(True,False,None,"missing-ledger-fields")
    if not isinstance(payload["terminal_turn"],int) or payload["terminal_turn"] < 1:
        journal.reject_terminal(1,"invalid-terminal-turn")
        return Position1ExecutionResult(True,False,None,"invalid-terminal-turn")
    if not payload["terminal_reason"]:
        journal.reject_terminal(1,"invalid-terminal-reason")
        return Position1ExecutionResult(True,False,None,"invalid-terminal-reason")
    if not isinstance(payload["event_ledger"],list) or not payload["event_ledger"]:
        journal.reject_terminal(1,"missing-event-ledger")
        return Position1ExecutionResult(True,False,None,"missing-event-ledger")

    journal.complete(1)
    return Position1ExecutionResult(True,True,payload,None)
