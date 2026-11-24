"""Synthetic controls for the clean-room receiver's pure envelope scope check.

This script performs no receiving, Git access, downloads, builds, or qualification.
Repeated-hex Git identifiers and SYNTHETIC_* run/job/artifact IDs are fixtures,
not observations of any real GitHub objects. Fixed source and charter constants
are copied independently from the reviewed prospective receiver contract.
"""
from pathlib import Path
import argparse
import copy
import hashlib
import importlib.util
import json
import sys

sys.dont_write_bytecode = True

DEFAULT_ADAPTER = Path(__file__).with_name("receive_clean_room.py")
EPOCH = "manual-clean-room-20261124-01"
BASE = {
    "repository": "GodaPupa/argentum-batshit-test",
    "branch": "manual-transmission/clean-room-recovery-20261124",
    "epoch": EPOCH,
    "candidate_index": "02",
    "event": "22" * 20,
    "source": "2059afe031fb33dbe43a4741a3ca25ed9a2fabd9",
    "source_tree": "632d9a579db3eea42a1bd1f2565685009627fdf5",
    "validation": "33" * 20,
    "validation_tree": "44" * 20,
    "workflow_blob": "55" * 20,
    "workflow_sha256": "ab" * 32,
    "protocol_blob": "a2c26c3b31834a7182bbe0b3a6ceb83922443cd4",
    "candidate_pins_blob": "933c265ce8a6e8427148f422608b75e8246d7a51",
    "run_id": "SYNTHETIC_RUN_0001",
    "job_id": "SYNTHETIC_JOB_0001",
    "artifact_id": "SYNTHETIC_ARTIFACT_0001",
    "artifact_name": "manual-clean-room-candidate02-20261124-SYNTHETIC_RUN_0001",
    "artifact_digest": "sha256:" + "cd" * 32,
    "artifact_size": 1234,
    "attempt": 1,
    "run_number": 1,
    "status": "completed",
    "job_status": "completed",
    "conclusion": "success",
    "job_conclusion": "success",
    "already_received": False,
    "fresh_reconciliation": True,
    "sole_writer": True,
    "automation_disabled": True,
    "first_parent": "11" * 20,
    "workflow_path": ".github/workflows/manual-clean-room-surge-bushwhacker-20261124.yml",
    "recovery_id": EPOCH,
    "charter_commit": "c943c4eb9859a59aa4f9c3c8ac492e6519ce7855",
    "charter_blob": "944b7989a6e8a2d8551cc0a322ee7ce1b8eee709",
    "charter_sha256": "0795eedb0206add384500f6555698a43dbca06f907a0e2ef7ebaeffe99c79915",
    "lane": "ci",
    "attempt_id": EPOCH + "/ci-01",
    "prior_receipt_count": 0,
}

ORIGINAL_MATCH_FIELDS = (
    "repository", "branch", "epoch", "candidate_index", "event", "source",
    "source_tree", "validation", "validation_tree", "workflow_blob",
    "workflow_sha256", "protocol_blob", "candidate_pins_blob", "run_id",
    "job_id", "artifact_id", "artifact_name", "artifact_digest", "artifact_size",
)
ADDITIONAL_MATCH_FIELDS = (
    "first_parent", "workflow_path", "recovery_id", "charter_commit",
    "charter_blob", "charter_sha256", "lane", "attempt_id",
    "prior_receipt_count", "attempt", "run_number", "status", "job_status",
    "conclusion", "job_conclusion", "already_received", "fresh_reconciliation",
    "sole_writer", "automation_disabled",
)


def run(adapter_path, output_path):
    adapter_bytes = adapter_path.read_bytes()
    spec = importlib.util.spec_from_file_location(
        "manual_clean_room_synthetic_envelope_adapter", adapter_path
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    cases = []

    def check(name, category, actual, expected, must_accept=False):
        try:
            module.envelope_scope_check(actual, expected)
        except (ValueError, KeyError, TypeError) as exc:
            accepted = False
            exception = {"type": type(exc).__name__, "message": str(exc)}
        else:
            accepted = True
            exception = None
        cases.append({
            "name": name,
            "category": category,
            "expected": "ACCEPT" if must_accept else "REJECT",
            "observed": "ACCEPT" if accepted else "REJECT",
            "passed": accepted == must_accept,
            "exception": exception,
        })

    def matched_invalid(name, category, **changes):
        envelope = copy.deepcopy(BASE)
        envelope.update(changes)
        check(name, category, envelope, copy.deepcopy(envelope))

    check("correct_synthetic_new_epoch", "positive", copy.deepcopy(BASE),
          copy.deepcopy(BASE), must_accept=True)

    for category, fields in (
        ("original_fields_mismatch", ORIGINAL_MATCH_FIELDS),
        ("additional_independent_fields_mismatch", ADDITIONAL_MATCH_FIELDS),
    ):
        for key in fields:
            actual = copy.deepcopy(BASE)
            value = actual[key]
            actual[key] = (
                not value if type(value) is bool else
                value + 1 if type(value) is int else
                "SYNTHETIC_WRONG_" + key
            )
            check("wrong_actual_" + key, category, actual, copy.deepcopy(BASE))

    for key in ORIGINAL_MATCH_FIELDS:
        expected = copy.deepcopy(BASE)
        expected[key] = (
            expected[key] + 1 if type(expected[key]) is int else
            "SYNTHETIC_WRONG_EXPECTED_" + key
        )
        check("wrong_independent_expected_" + key,
              "independent_expected_mismatch", copy.deepcopy(BASE), expected)

    fixed_invalid = {
        "old_quarantine_branch": {"branch": "manual-transmission/quarantine-recovery-20261106"},
        "old_canonical_branch": {"branch": "manual-transmission/autonomous-execution"},
        "old_epoch": {"epoch": "manual-quarantine-20261106-01"},
        "old_recovery_id": {"recovery_id": "manual-quarantine-20261106-01"},
        "candidate01": {"candidate_index": "01"},
        "wrong_repository": {"repository": "synthetic-owner/synthetic-other-repository"},
        "stale_source": {"source": "3c7a9dd9a028b8644c92b67b3351ecf413eec484"},
        "stale_source_tree": {"source_tree": "4fd6a9d3bed8f853c9373c613fc4e333a7b8b3b6"},
        "stale_candidate_pins": {"candidate_pins_blob": "2233f823b2dcdda060b9efe55948625bb93b0b7d"},
        "wrong_protocol": {"protocol_blob": "66" * 20},
        "stale_charter_commit": {"charter_commit": "62b95ee8f854cb9a29830832c287106ad8baaac4"},
        "stale_charter_blob": {"charter_blob": "d77eca4b30458128468ae48b3d5b02c465bd3987"},
        "wrong_charter_hash": {"charter_sha256": "77" * 32},
        "old_workflow": {"workflow_path": ".github/workflows/manual-quarantine-surge-bushwhacker-candidate-02.yml"},
        "local_lane": {"lane": "local"},
        "second_attempt_identity": {"attempt_id": EPOCH + "/ci-02"},
        "wrong_artifact_name": {"artifact_name": "manual-surge-bushwhacker-candidate-02-SYNTHETIC_RUN_0001"},
        "artifact_run_name_mismatch": {"run_id": "SYNTHETIC_RUN_0002"},
    }
    for name, changes in fixed_invalid.items():
        matched_invalid(name, "matched_but_wrong_fixed_identity", **changes)

    live_invalid = {
        "second_attempt": {"attempt": 2},
        "second_run": {"run_number": 2},
        "zero_attempt": {"attempt": 0},
        "zero_run": {"run_number": 0},
        "run_in_progress": {"status": "in_progress"},
        "job_queued": {"job_status": "queued"},
        "artifact_already_received": {"already_received": True},
        "positive_prior_receipt_count": {"prior_receipt_count": 1},
        "negative_prior_receipt_count": {"prior_receipt_count": -1},
        "stale_reconciliation": {"fresh_reconciliation": False},
        "not_sole_writer": {"sole_writer": False},
        "automation_enabled": {"automation_disabled": False},
    }
    for name, changes in live_invalid.items():
        matched_invalid(name, "matched_but_invalid_receiving_state", **changes)

    for key in ("first_parent", "event", "validation", "validation_tree", "workflow_blob"):
        matched_invalid("malformed_object_" + key, "immutable_object_syntax",
                        **{key: "SYNTHETIC_NON_GIT_OBJECT_" + key})

    for key in ORIGINAL_MATCH_FIELDS + ADDITIONAL_MATCH_FIELDS:
        actual = copy.deepcopy(BASE)
        del actual[key]
        check("missing_required_" + key, "missing_required_field",
              actual, copy.deepcopy(BASE))

    # JSON booleans and floats compare equal to Python integers. The documented
    # attempt/run contract requires integer 1; this checks that boundary directly.
    for name, changes in {
        "boolean_attempt": {"attempt": True},
        "boolean_run_number": {"run_number": True},
        "floating_attempt": {"attempt": 1.0},
        "floating_run_number": {"run_number": 1.0},
        "boolean_prior_receipt_count": {"prior_receipt_count": False},
    }.items():
        matched_invalid(name, "counter_type_contract", **changes)
        for side in ("actual", "expected"):
            actual, expected = copy.deepcopy(BASE), copy.deepcopy(BASE)
            (actual if side == "actual" else expected).update(changes)
            check(name + "_" + side, "counter_type_contract", actual, expected)

    assert module._ROOT is None and module._BASE is None
    assert module._VERIFIER is None and module._CONTEXT is None
    assert adapter_path.read_bytes() == adapter_bytes, "Adapter changed during controls"
    by_category = {}
    for case in cases:
        group = by_category.setdefault(case["category"], {"cases": 0, "passed": 0, "failed": 0})
        group["cases"] += 1
        group["passed"] += int(case["passed"])
        group["failed"] += int(not case["passed"])
    failed = [case for case in cases if not case["passed"]]
    result = {
        "schema": "manual-transmission/clean-room-envelope-synthetic-controls/v1",
        "state": "PASS" if not failed else "FAIL_CONTROLS_IDENTIFIED_CONTRACT_GAP",
        "adapter_path": str(adapter_path),
        "adapter_bytes": len(adapter_bytes),
        "adapter_sha256": hashlib.sha256(adapter_bytes).hexdigest(),
        "synthetic_only": True,
        "synthetic_id_note": "Repeated-hex object IDs and SYNTHETIC_* identifiers are fixtures, not real GitHub observations.",
        "base_fixture": BASE,
        "function_called": "envelope_scope_check",
        "forbidden_functions_called": [],
        "receiver_configured": False,
        "topology_verified": False,
        "real_downloads": 0,
        "archive_unpack_or_receiving_operations": 0,
        "qualification_starts": 0,
        "cases_total": len(cases),
        "cases_passed": len(cases) - len(failed),
        "cases_failed": len(failed),
        "categories": by_category,
        "cases": cases,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({key: result[key] for key in (
        "state", "adapter_sha256", "cases_total", "cases_passed", "cases_failed", "categories"
    )}, indent=2))
    for case in failed:
        print("CONTRACT_GAP " + case["name"])
    return 0 if not failed else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adapter", type=Path, default=DEFAULT_ADAPTER)
    parser.add_argument("--out", type=Path, default=Path(__file__).with_name("ENVELOPE_SCOPE_CONTROLS.json"))
    arguments = parser.parse_args()
    sys.exit(run(arguments.adapter, arguments.out))
