#!/usr/bin/env python3
from phase33_position1_authorization import *

def good(**kw):
    base=dict(
        position=1,
        control_sha256=CONTROL_SHA256,
        vector_sha256=VECTOR_SHA256,
        assignment_sha256=ASSIGNMENT_SHA256,
        quarantine_artifact_id=QUARANTINE_ARTIFACT_ID,
        runner_sha256=RUNNER_SHA256,
        opponent_identity=OPPONENT_ID,
        durable_attempt_present=True,
        seed_consumed=False,
        game_initialized=False,
        outcome_exposed=False,
    )
    base.update(kw)
    return AuthorizationRequest(**base)

def main():
    d=authorize_position1(good())
    assert d.authorized and d.position==1 and d.reason=="position1_authorized"

    for p in range(2,13):
        d=authorize_position1(good(position=p))
        assert not d.authorized and d.reason=="position_not_authorized"

    mismatch_cases=[
        dict(control_sha256="0"*64),
        dict(vector_sha256="0"*64),
        dict(assignment_sha256="0"*64),
        dict(quarantine_artifact_id=1),
        dict(runner_sha256="0"*64),
        dict(opponent_identity="wrong"),
    ]
    for patch in mismatch_cases:
        d=authorize_position1(good(**patch))
        assert not d.authorized and d.reason=="identity_mismatch"

    assert authorize_position1(good(durable_attempt_present=False)).reason=="attempt_not_durable"
    assert authorize_position1(good(seed_consumed=True)).reason=="seed_already_consumed"
    assert authorize_position1(good(game_initialized=True)).reason=="game_already_initialized"
    assert authorize_position1(good(outcome_exposed=True)).reason=="outcome_already_exposed"

    # Deterministic replay.
    assert authorize_position1(good()) == authorize_position1(good())

    print("V09_PHASE33_POSITION1_AUTHORIZATION_VALIDATION_PASS")

if __name__=="__main__":
    main()
