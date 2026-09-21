#!/usr/bin/env python3
from pathlib import Path
import tempfile

from phase32_execution_readiness import DurableAttemptJournal, FrozenPositionBinding, OpaqueSeed
from phase33_position1_authorization import (
    AuthorizationRequest, CONTROL_SHA256, VECTOR_SHA256, ASSIGNMENT_SHA256,
    QUARANTINE_ARTIFACT_ID, RUNNER_SHA256, OPPONENT_ID,
)
from phase33_position1_execution_adapter import execute_position1_once

class SyntheticEngine:
    engine_identity="synthetic-position1-engine-v1"
    def run_position1(self, *, seed, assignment):
        assert isinstance(seed,int)
        assert assignment in {"play","draw"}
        return {
            "game_result":"synthetic-result",
            "terminal_turn":7,
            "terminal_reason":"synthetic-terminal",
            "event_ledger":[{"event":"synthetic"}],
        }

class BrokenEngine:
    engine_identity="synthetic-position1-engine-v1"
    def run_position1(self, *, seed, assignment):
        raise RuntimeError("synthetic failure")

def req():
    return AuthorizationRequest(
        1,CONTROL_SHA256,VECTOR_SHA256,ASSIGNMENT_SHA256,
        QUARANTINE_ARTIFACT_ID,RUNNER_SHA256,OPPONENT_ID,
        False,False,False,False,
    )

def main():
    binding=FrozenPositionBinding(1,"play",OpaqueSeed(123456789))

    with tempfile.TemporaryDirectory() as td:
        j=DurableAttemptJournal(Path(td))
        out=execute_position1_once(
            binding=binding,journal=j,authorization_request=req(),
            engine=SyntheticEngine(),expected_engine_identity=SyntheticEngine.engine_identity)
        assert out.initialized and out.completed
        assert j.is_attempted(1) and j.is_consumed(1) and j.is_complete(1)
        try:
            execute_position1_once(
                binding=binding,journal=j,authorization_request=req(),
                engine=SyntheticEngine(),expected_engine_identity=SyntheticEngine.engine_identity)
            raise AssertionError("duplicate position-1 execution accepted")
        except Exception:
            pass

    with tempfile.TemporaryDirectory() as td:
        j=DurableAttemptJournal(Path(td))
        out=execute_position1_once(
            binding=binding,journal=j,authorization_request=req(),
            engine=BrokenEngine(),expected_engine_identity=BrokenEngine.engine_identity)
        assert out.initialized and not out.completed
        assert j.has_terminal_rejection()
        assert not j.is_complete(1)

    with tempfile.TemporaryDirectory() as td:
        j=DurableAttemptJournal(Path(td))
        try:
            execute_position1_once(
                binding=binding,journal=j,authorization_request=req(),
                engine=SyntheticEngine(),expected_engine_identity="wrong")
            raise AssertionError("wrong engine identity accepted")
        except ValueError:
            pass
        assert not j.is_attempted(1)

    print("V09_POSITION1_EXECUTION_ADAPTER_VALIDATION_PASS")

if __name__=="__main__":
    main()
