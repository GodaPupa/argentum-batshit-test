#!/usr/bin/env python3
"""Phase-32 seed-free/opaque readiness qualification."""
from pathlib import Path
import json, tempfile, hashlib
import phase32_execution_readiness as m

def make_opaque_fixture():
    values=[900000000000000001+i for i in range(12)]
    assignments=["play" if i%2==0 else "draw" for i in range(12)]
    payload={"schema":"izzet-v09-phase31-quarantined-seed-vector-v1",
             "values":values,"assignments":assignments}
    data=(json.dumps(payload,sort_keys=True,separators=(",",":"))+"\n").encode()
    expected=m.FreezeIdentity(
        artifact_id=999001,
        artifact_zip_sha256="f"*64,
        vector_file_sha256=hashlib.sha256(data).hexdigest(),
        vector_sha256=hashlib.sha256(json.dumps(values,separators=(",",":")).encode()).hexdigest(),
        assignment_sha256=m._digest_json(assignments),
    )
    return data,expected

def main():
    data,expected=make_opaque_fixture()
    bindings=m.validate_quarantine_payload(
        artifact_id=999001,
        artifact_zip_sha256="f"*64,
        vector_file_bytes=data,
        expected=expected,
    )
    m.validate_binding_set(bindings)
    assert len(bindings)==12
    assert sum(b.assignment=="play" for b in bindings)==6
    assert sum(b.assignment=="draw" for b in bindings)==6
    assert all("<redacted>" in repr(b.opaque_seed) for b in bindings)
    try:
        bindings[0].opaque_seed.reveal_for_authorized_execution(object())
        raise AssertionError("Phase 32 seed reveal unexpectedly allowed")
    except RuntimeError:
        pass

    # Exact-loader failures.
    bad=[
        dict(artifact_id=999002,artifact_zip_sha256="f"*64,vector_file_bytes=data),
        dict(artifact_id=999001,artifact_zip_sha256="e"*64,vector_file_bytes=data),
        dict(artifact_id=999001,artifact_zip_sha256="f"*64,vector_file_bytes=data+b"x"),
    ]
    for kwargs in bad:
        try:
            m.validate_quarantine_payload(expected=expected,**kwargs)
            raise AssertionError("wrong frozen identity accepted")
        except (ValueError,json.JSONDecodeError):
            pass

    # Durable attempt-before-initialization and one-shot markers.
    with tempfile.TemporaryDirectory() as td:
        j=m.DurableAttemptJournal(Path(td))
        seen=[]
        def init(pos):
            assert j.is_attempted(pos)
            seen.append(pos)
            return "synthetic-initialized"
        assert m.synthetic_initialize_after_attempt(j,1,init)=="synthetic-initialized"
        assert seen==[1]
        try:
            j.attempt(1)
            raise AssertionError("attempt replay accepted")
        except FileExistsError:
            pass
        j.consume_marker(1)
        try:
            j.consume_marker(1)
            raise AssertionError("seed consumption replay accepted")
        except FileExistsError:
            pass
        j.complete(1)
        j.attempt(2)
        # Simulate crash after attempt; recovery terminally rejects, never retries.
        assert j.recover()=="terminal-rejected"
        assert j.has_terminal_rejection()
        try:
            j.attempt(2)
            raise AssertionError("crashed position retry accepted")
        except RuntimeError:
            pass
        try:
            j.attempt(3)
            raise AssertionError("post-failure continuation accepted")
        except RuntimeError:
            pass

    # Sequential attempt constraint.
    with tempfile.TemporaryDirectory() as td:
        j=m.DurableAttemptJournal(Path(td))
        try:
            j.attempt(2)
            raise AssertionError("out-of-order position accepted")
        except RuntimeError:
            pass

    print("V09_PHASE32_EXECUTION_READINESS_VALIDATION_PASS")

if __name__=="__main__":
    main()
