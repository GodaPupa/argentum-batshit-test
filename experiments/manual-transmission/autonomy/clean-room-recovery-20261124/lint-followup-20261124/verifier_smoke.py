#!/usr/bin/env python3
"""Draft: one valid finalized local envelope plus bounded negative input controls.

Import the exact frozen S2 verifier without monkeypatching it. All Git operations
are read-only. No source mutation, builds, qualification, receiving or publication.
The supplied positive envelope is used verbatim; no simulated reservation or
invented positive authority is supplied. Run after the real immutable launch
reservation exists, before launching the full qualifier. Keep its output in
scratch/runtime; publishing it before launch would advance the exact live head
that run_local_qualification.py expects.

  python verifier_smoke.py --source SOURCE_WORKTREE --envelope ENVELOPE.json \
    --output SCRATCH_REPORT.json
"""
import argparse
import copy
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
RECORD = "experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114"
S1 = "2059afe031fb33dbe43a4741a3ca25ed9a2fabd9"
STALE_CARRIER = "618f562154ff10a9da082a144d43c2011703d2e0"
OLD_CHARTER = "c943c4eb9859a59aa4f9c3c8ac492e6519ce7855"
OLD_CHARTER_BLOB = "944b7989a6e8a2d8551cc0a322ee7ce1b8eee709"


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--envelope", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.source.resolve()
    output = args.output.resolve() if args.output else None
    if output:
        require(not output.is_relative_to(root), "control report must remain outside the source worktree")
        require(not output.exists(), "refusing to overwrite a control report")
    envelope_bytes = args.envelope.read_bytes()
    original = json.loads(envelope_bytes)
    require(isinstance(original, dict), "input envelope must be an object")
    source = original["source"]
    require(isinstance(source, str) and re.fullmatch(r"[0-9a-f]{40}", source) is not None,
            "input must identify the full frozen source commit")
    require(original.get("lint_followup", {}).get("lane") == "local", "use the actual finalized local launch envelope")

    def git(*arguments):
        return subprocess.check_output(["git", "--no-replace-objects", *arguments], cwd=root)

    require(git("rev-parse", "HEAD").decode().strip() == source, "source worktree must be at frozen S2")
    verifier_path = root / RECORD / "verify_dispatch.py"
    frozen = git("show", source + ":" + RECORD + "/verify_dispatch.py")
    require(verifier_path.read_bytes() == frozen, "local verifier differs from immutable S2 bytes")
    spec = importlib.util.spec_from_file_location("manual_lint_frozen_verifier_controls", verifier_path)
    require(spec is not None and spec.loader is not None, "verifier import unavailable")
    verifier = importlib.util.module_from_spec(spec)
    # Execute exactly the frozen source bytes, avoiding any pre-existing pyc cache.
    exec(compile(frozen, str(verifier_path), "exec"), verifier.__dict__)
    report = {
        "schema": "manual-transmission/lint-verifier-smoke/v1",
        "scope": "FROZEN_VERIFIER_INPUT_REJECTION_ONLY",
        "started_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "source": source,
        "first_parent": original.get("first_parent"),
        "envelope_sha256": digest(envelope_bytes),
        "verifier_sha256": digest(frozen),
        "actual_finalized_envelope_used": True,
        "simulated_reservation": False,
        "module_monkeypatches": 0,
        "source_modifications": 0,
        "full_qualification_invocations": 0,
        "behavioral_builds": 0,
        "ci_activations": 0,
        "original_artifact_receives": 0,
        "controls": [],
    }
    try:
        pins = verifier.verify(copy.deepcopy(original))
        require(isinstance(pins, dict) and len(pins["bindings"]) == 378, "positive verifier returned unexpected bindings")
        report["controls"].append({"name": "actual-finalized-local-envelope", "result": "PASS", "expected": "accept", "bindings": 378})
    except Exception as exc:
        report["controls"].append({"name": "actual-finalized-local-envelope", "result": "FAIL", "expected": "accept", "error": type(exc).__name__ + ": " + str(exc)})

    def old_authority(envelope):
        envelope["clean_room"] = envelope.pop("lint_followup")

    def old_charter(envelope):
        envelope["lint_followup"].update(charter_commit=OLD_CHARTER, charter_blob=OLD_CHARTER_BLOB)

    def second_attempt(envelope):
        envelope["attempt"] = 2
        envelope["lint_followup"]["attempt"] = 2

    def local_claims_ci(envelope):
        envelope.update(event=envelope["first_parent"], validation=envelope["source"], run="1")

    def ci_claims_local(envelope):
        local_claims_ci(envelope)
        envelope["lint_followup"].update(lane="ci", attempt_id=verifier.LINT_EPOCH + "/ci-01")
        envelope["local_execution"] = True

    def wrong_source(envelope):
        envelope["source"] = S1
        envelope["qualification"]["source"] = S1

    cases = [
        ("missing-new-authority-cannot-fall-back", lambda e: e.pop("lint_followup")),
        ("old-authority-key-cannot-relabel-followup", old_authority),
        ("wrong-authority-lane", lambda e: e["lint_followup"].__setitem__("lane", "legacy")),
        ("mixed-old-and-new-authorities", lambda e: e.__setitem__("clean_room", copy.deepcopy(e["lint_followup"]))),
        ("old-charter-cannot-authorize-followup", old_charter),
        ("attempt-two", second_attempt),
        ("local-envelope-cannot-claim-ci-identifiers", local_claims_ci),
        ("ci-lane-cannot-claim-local-execution", ci_claims_local),
        ("wrong-source", wrong_source),
        ("wrong-source-tree", lambda e: e["qualification"].__setitem__("source_tree", "0" * 40)),
        ("wrong-binding-count", lambda e: e["qualification"].__setitem__("binding_count", 377)),
        ("stale-carrier-before-new-authority", lambda e: e.__setitem__("first_parent", STALE_CARRIER)),
    ]
    # A failed real positive control prevents all negative-control claims.
    if report["controls"][0]["result"] == "PASS":
        for name, change in cases:
            altered = copy.deepcopy(original)
            change(altered)
            try:
                verifier.verify(altered)
            except (ValueError, KeyError) as exc:
                report["controls"].append({"name": name, "result": "PASS", "expected": "reject", "reason": type(exc).__name__ + ": " + str(exc)})
            except subprocess.CalledProcessError as exc:
                ancestry_rejection = (name == "stale-carrier-before-new-authority" and exc.returncode == 1
                                      and isinstance(exc.cmd, (tuple, list))
                                      and list(exc.cmd[:3]) == ["git", "merge-base", "--is-ancestor"])
                report["controls"].append({"name": name, "result": "PASS" if ancestry_rejection else "FAIL", "expected": "reject",
                                           "reason": "immutable ancestry rejected stale carrier" if ancestry_rejection else "unexpected Git error",
                                           "returncode": exc.returncode})
            except Exception as exc:
                report["controls"].append({"name": name, "result": "FAIL", "expected": "reject", "error": type(exc).__name__ + ": " + str(exc)})
            else:
                report["controls"].append({"name": name, "result": "FAIL", "expected": "reject", "error": "invalid envelope accepted"})

    unchanged = (verifier_path.read_bytes() == frozen and git("rev-parse", "HEAD").decode().strip() == source)
    report["frozen_verifier_and_head_unchanged"] = unchanged
    report["state"] = "PASS" if unchanged and len(report["controls"]) == 13 and all(c["result"] == "PASS" for c in report["controls"]) else "FAIL"
    report["finished_utc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    data = json.dumps(report, indent=2) + "\n"
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("x", encoding="utf-8") as destination:
            destination.write(data)
    print(data, end="")
    return 0 if report["state"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
