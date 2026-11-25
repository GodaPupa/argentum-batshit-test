"""Root-run, bounded synthetic controls for the finalized lint-followup metadata scope.

Only envelope_scope_check() and its pure require()/object_id() helpers are called.
The driver imports the exact hash-pinned adapter without configuring it. It does
not prove real Git topology, API identity, archive content, receiving or admission.
The coherent historical146 suite and source-verifier controls are separate evidence.

This file was prepared and syntax-parsed only; root must explicitly invoke it.
"""
from pathlib import Path
import argparse
import copy
import hashlib
import importlib.util
import json
import sys

sys.dont_write_bytecode = True
ADAPTER_SHA256 = "93a44ed39d2d75996cda6629f9b477ae343c06c1c633d744325928354a5d3ee5"
PLANNED_CASES = 15
BASE = {'repository': 'GodaPupa/argentum-batshit-test',
 'branch': 'manual-transmission/clean-room-recovery-20261124',
 'epoch': 'manual-lint-followup-20261124-01',
 'candidate_index': '02',
 'event': '2222222222222222222222222222222222222222',
 'source': '82ec335573572edfd2a2e4d734d2f0d81801139f',
 'source_tree': '923ec79e1c867eedf35b408153bf60988fbd26b6',
 'validation': '3333333333333333333333333333333333333333',
 'validation_tree': '4444444444444444444444444444444444444444',
 'workflow_blob': '5555555555555555555555555555555555555555',
 'workflow_sha256': 'abababababababababababababababababababababababababababababababab',
 'protocol_blob': 'a2c26c3b31834a7182bbe0b3a6ceb83922443cd4',
 'candidate_pins_blob': '7e30df16c53a59358d507733b2a5443e2a3c9075',
 'run_id': 'SYNTHETIC_RUN_METADATA_ONLY_0001',
 'job_id': 'SYNTHETIC_JOB_METADATA_ONLY_0001',
 'artifact_id': 'SYNTHETIC_ARTIFACT_METADATA_ONLY_0001',
 'artifact_name': 'manual-lint-followup-candidate02-20261124-SYNTHETIC_RUN_METADATA_ONLY_0001',
 'artifact_digest': 'sha256:cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd',
 'artifact_size': 1234,
 'attempt': 1,
 'run_number': 1,
 'status': 'completed',
 'job_status': 'completed',
 'conclusion': 'success',
 'job_conclusion': 'success',
 'already_received': False,
 'fresh_reconciliation': True,
 'sole_writer': True,
 'automation_disabled': True,
 'first_parent': '1111111111111111111111111111111111111111',
 'workflow_path': '.github/workflows/manual-lint-followup-surge-bushwhacker-20261124.yml',
 'recovery_id': 'manual-lint-followup-20261124-01',
 'charter_commit': '86450c1ecb227804dbd45acf5ba28689ce0c61bc',
 'charter_blob': '418fb7f5a0ebeed92582f8285e76305c39250efa',
 'charter_sha256': 'a9a2f3b775c6231d1a520679492aba4d2bf3bdea0f1b0abe3fc47be39ef0087e',
 'lane': 'ci',
 'attempt_id': 'manual-lint-followup-20261124-01/ci-01',
 'prior_receipt_count': 0}

PRIOR_CLEAN_ROOM_VALUES = {'epoch': 'manual-clean-room-20261124-01',
 'recovery_id': 'manual-clean-room-20261124-01',
 'source': '2059afe031fb33dbe43a4741a3ca25ed9a2fabd9',
 'source_tree': '632d9a579db3eea42a1bd1f2565685009627fdf5',
 'candidate_pins_blob': '933c265ce8a6e8427148f422608b75e8246d7a51',
 'charter_commit': 'c943c4eb9859a59aa4f9c3c8ac492e6519ce7855',
 'charter_blob': '944b7989a6e8a2d8551cc0a322ee7ce1b8eee709',
 'charter_sha256': '0795eedb0206add384500f6555698a43dbca06f907a0e2ef7ebaeffe99c79915',
 'workflow_path': '.github/workflows/manual-clean-room-surge-bushwhacker-20261124.yml',
 'attempt_id': 'manual-clean-room-20261124-01/ci-01',
 'artifact_name': 'manual-clean-room-candidate02-20261124-SYNTHETIC_RUN_METADATA_ONLY_0001'}

class PurityViolation(RuntimeError):
    pass


def run(adapter_path, output_path):
    adapter_path = adapter_path.resolve()
    output_path = output_path.resolve()
    if output_path.exists():
        raise RuntimeError("Refusing to overwrite prior synthetic-control evidence")
    adapter_bytes = adapter_path.read_bytes()
    observed_sha = hashlib.sha256(adapter_bytes).hexdigest()
    if observed_sha != ADAPTER_SHA256:
        raise RuntimeError("Adapter differs from the exact reviewed finalized bytes")
    spec = importlib.util.spec_from_file_location("lint_followup_metadata_scope_controls", adapter_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if not all(getattr(module, key) is None for key in ("_ROOT", "_BASE", "_VERIFIER", "_CONTEXT")):
        raise RuntimeError("Adapter must remain unconfigured before controls")
    cases = []
    forbidden_calls = []
    allowed_functions = {"envelope_scope_check", "require", "object_id"}

    def call_boundary(frame, event, arg):
        if event == "call" and frame.f_code.co_filename == str(adapter_path):
            if frame.f_code.co_name not in allowed_functions:
                forbidden_calls.append(frame.f_code.co_name)
                raise PurityViolation("Forbidden adapter function call: " + frame.f_code.co_name)

    def check(name, actual_changes, expected_changes, must_accept=False):
        actual, expected = copy.deepcopy(BASE), copy.deepcopy(BASE)
        actual.update(actual_changes)
        expected.update(expected_changes)
        exception = None
        unexpected = False
        try:
            module.envelope_scope_check(actual, expected)
        except (ValueError, KeyError, TypeError) as exc:
            accepted = False
            exception = {"type":type(exc).__name__, "message":str(exc)}
        except Exception as exc:
            accepted = False
            unexpected = True
            exception = {"type":type(exc).__name__, "message":str(exc)}
        else:
            accepted = True
        cases.append({"name":name, "actual_changes":actual_changes, "expected_changes":expected_changes,
                      "expected":"ACCEPT" if must_accept else "REJECT",
                      "observed":"UNEXPECTED_EXCEPTION" if unexpected else "ACCEPT" if accepted else "REJECT",
                      "passed":not unexpected and accepted == must_accept, "exception":exception})

    previous_profile = sys.getprofile()
    try:
        sys.setprofile(call_boundary)
        check("accept_current_lint_followup_synthetic_metadata", {}, {}, must_accept=True)
        # Both sides agree on each stale value: rejection must come from the new
        # fixed identity, rather than merely actual/expected inequality.
        for key, value in PRIOR_CLEAN_ROOM_VALUES.items():
            check("reject_prior_clean_room_" + key, {key:value}, {key:value})
        check("reject_coherent_prior_clean_room_metadata", PRIOR_CLEAN_ROOM_VALUES, PRIOR_CLEAN_ROOM_VALUES)
        check("reject_independently_expected_prior_source", {}, {"source":PRIOR_CLEAN_ROOM_VALUES["source"]})
        check("reject_independently_expected_prior_charter", {}, {"charter_commit":PRIOR_CLEAN_ROOM_VALUES["charter_commit"]})
    finally:
        sys.setprofile(previous_profile)

    if len(cases) != PLANNED_CASES:
        raise RuntimeError("Prepared control inventory drift")
    unconfigured = all(getattr(module, key) is None for key in ("_ROOT", "_BASE", "_VERIFIER", "_CONTEXT"))
    adapter_unchanged = adapter_path.read_bytes() == adapter_bytes
    failed = [case for case in cases if not case["passed"]]
    passed = not failed and not forbidden_calls and unconfigured and adapter_unchanged
    result = {
        "schema":"manual-transmission/lint-followup-receiver-scope-controls/v1",
        "state":"PASS" if passed else "FAIL_SYNTHETIC_CONTROLS",
        "adapter_path":str(adapter_path), "adapter_sha256":observed_sha, "adapter_bytes":len(adapter_bytes),
        "driver_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "synthetic_only":True,
        "fixture_note":"SYNTHETIC_* IDs and repeated-hex C/V/E objects are fixtures, never real GitHub observations; fixed S2/charter values are independently literal expectations.",
        "scope":"Changed lint-followup identity metadata only; no repeat of the historical146 or source-verifier controls.",
        "function_called":"envelope_scope_check", "forbidden_functions_called":forbidden_calls,
        "receiver_configured":not unconfigured, "adapter_unchanged":adapter_unchanged,
        "topology_verified":False, "source_verifier_calls":0, "archive_unpack_or_receive_calls":0,
        "real_downloads":0, "ci_activations":0, "qualification_starts":0, "build_or_test_tasks":0,
        "real_future_receiving_budget_consumed":0, "base_fixture":BASE,
        "cases_total":len(cases), "cases_passed":len(cases)-len(failed), "cases_failed":len(failed),
        "cases":cases,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("x") as output:
        json.dump(result, output, indent=2)
        output.write("\n")
    print(json.dumps({key:result[key] for key in ("state","adapter_sha256","cases_total","cases_passed","cases_failed","forbidden_functions_called")}, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adapter", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    sys.exit(run(args.adapter, args.out))
