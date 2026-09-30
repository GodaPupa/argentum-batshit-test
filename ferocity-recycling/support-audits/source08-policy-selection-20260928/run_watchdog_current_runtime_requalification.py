#!/usr/bin/env python3
"""Requalify the unchanged source08 watchdog with exactly 13 fixed process/resource cases.

This harness never starts Java, Gradle, entropy allocation, FerocityDevelopmentCli, or gameplay.
"""
from __future__ import annotations

import hashlib
import json
import os
import pathlib
import platform
import re
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
SOURCE08 = "3a4f99a7653839506e96d19e6639f58d9e8c5ced"
CONTAINER = "mcr.microsoft.com/devcontainers/universal:linux@sha256:584e41561451c910dcd96221634be07545803aed9cd437a60288f06ddeaf5880"
WATCHDOG = "ferocity-recycling/tools/trial_watchdog.py"
PROCESS_TEST = "ferocity-recycling/tools/tests/test_trial_watchdog.py"
RESOURCE_TEST = "ferocity-recycling/tools/tests/test_trial_watchdog_resources.py"
EXPECTED_WATCHDOG_SHA = "803a03aae3f63ffb114d9a8b56d4bf108b2f5f71f051057e204220f5f52adb37"
EXPECTED_CASES = 13

CASE_NAMES = [
    "test_normal_exit_records_exact_command_versions_pins_and_durable_claim",
    "test_nonzero_exit_is_unresolved_and_never_retried",
    "test_wall_timeout_terminates_the_owned_process_group",
    "test_term_ignoring_child_receives_kill_after_fixed_grace",
    "test_timeout_preserves_unmatched_intent_bytes_without_claiming_engine_verification",
    "test_duplicate_run_identity_fails_before_another_launch_and_preserves_files",
    "test_changed_pins_or_unknown_schema_fail_before_claim_or_launch",
    "test_normal_leader_exit_with_descendant_still_requires_cleanup_and_is_unresolved",
    "test_cancellation_after_os_spawn_before_popen_assignment_cleans_owned_child_and_preserves_intent",
    "test_wp01_exact_limit_is_inherited_and_cannot_be_raised",
    "test_wp02_over_limit_write_preserves_the_prefix_and_never_becomes_a_game",
    "test_wp03_insufficient_space_is_retained_before_any_child_launch",
    "test_wp04_unbounded_or_changed_resource_configuration_is_refused",
]

def sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def file_sha(path: pathlib.Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def write_json(path: pathlib.Path, value) -> str:
    data = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return sha_bytes(data)

expected_container = os.environ.get("FEROCITY_CONTAINER_IMAGE")
assert expected_container == CONTAINER, (expected_container, CONTAINER)
assert platform.machine() == "x86_64", platform.machine()

source_paths = [WATCHDOG, PROCESS_TEST, RESOURCE_TEST]
before = {p: file_sha(ROOT / p) for p in source_paths}
assert before[WATCHDOG] == EXPECTED_WATCHDOG_SHA

report = ROOT / "build/reports/ferocity-watchdog-current-runtime-requalification"
assert not report.exists()
report.mkdir(parents=True)
evidence = report / "watchdog"
source_copy = evidence / "source/trial_watchdog.py"
source_copy.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(ROOT / WATCHDOG, source_copy)
assert file_sha(source_copy) == EXPECTED_WATCHDOG_SHA

runner_temp = pathlib.Path(os.environ["RUNNER_TEMP"]).resolve()
token = os.environ.get("GITHUB_RUN_ID", "local")
process_root = runner_temp / f"ferocity-watchdog-process-{token}"
resource_root = runner_temp / f"ferocity-watchdog-resource-{token}"
assert not process_root.exists() and not resource_root.exists()

python_path = pathlib.Path(sys.executable).resolve(strict=True)
python_sha = file_sha(python_path)
env = os.environ.copy()
env["FEROCITY_WATCHDOG_FIXTURE_ROOT"] = str(process_root)
env["FEROCITY_WATCHDOG_RESOURCE_FIXTURE_ROOT"] = str(resource_root)

command = [
    str(python_path), "-m", "unittest", "discover",
    "-s", "ferocity-recycling/tools/tests",
    "-p", "test_trial_watchdog*.py",
]
completed = subprocess.run(
    command,
    cwd=ROOT,
    env=env,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    text=True,
    check=False,
)
log = completed.stdout
(evidence / "unittest.log").parent.mkdir(parents=True, exist_ok=True)
(evidence / "unittest.log").write_text(log)
assert completed.returncode == 0, log
assert re.search(r"Ran\s+13\s+tests", log), log
assert re.search(r"^OK\s*$", log, re.MULTILINE), log

after = {p: file_sha(ROOT / p) for p in source_paths}
assert before == after

normal_claim = (
    process_root
    / "test_normal_exit_records_exact_command_versions_pins_and_durable_claim"
    / "supervisor/fixed-run/claim.json"
)
assert normal_claim.is_file()
claim_raw = normal_claim.read_bytes()
claim = json.loads(claim_raw)
assert claim["schema_version"] == 1
assert claim["scope"] == "PROCESS_SUPERVISION_ONLY"
assert claim["supervisor_source_sha256"] == EXPECTED_WATCHDOG_SHA
assert pathlib.Path(claim["python_executable"]).resolve() == python_path
assert claim["python_executable_sha256"] == python_sha

claim_rel = pathlib.Path("fixtures/normal-exit/supervisor/fixed-run/claim.json")
claim_copy = evidence / claim_rel
claim_copy.parent.mkdir(parents=True, exist_ok=True)
claim_copy.write_bytes(claim_raw)
claim_sha = file_sha(claim_copy)

source_ref_path = "watchdog/source/trial_watchdog.py"
author = {
    "schema": "ferocity-watchdog-current-runtime-author-v1",
    "status": "SOURCE_BOUND",
    "source08_commit": SOURCE08,
    "container_image": CONTAINER,
    "python_executable": str(python_path),
    "python_executable_sha256": python_sha,
    "owned_files": [
        {"path": source_ref_path, "sha256": EXPECTED_WATCHDOG_SHA},
        {"path": PROCESS_TEST, "sha256": before[PROCESS_TEST]},
        {"path": RESOURCE_TEST, "sha256": before[RESOURCE_TEST]},
    ],
}
author_sha = write_json(evidence / "AUTHOR_RECEIPT.json", author)

run_receipt = {
    "schema": "ferocity-watchdog-current-runtime-run-v1",
    "status": "PASS",
    "returncode": completed.returncode,
    "author_receipt_sha256": author_sha,
    "source_guard_unchanged": before == after,
    "source_before": before,
    "source_after": after,
    "command": command,
    "python_executable": str(python_path),
    "python_executable_sha256": python_sha,
    "container_image": CONTAINER,
    "official_seed_files_read": False,
    "official_counters_delta": 0,
    "java_started": False,
    "gradle_started": False,
    "game_initialized": False,
}
run_sha = write_json(evidence / "RUN_RECEIPT.json", run_receipt)

platform_receipt = {
    "schema": "ferocity-watchdog-current-runtime-platform-v1",
    "container_image": CONTAINER,
    "architecture": platform.machine(),
    "platform": platform.platform(),
    "python_executable": str(python_path),
    "python_executable_sha256": python_sha,
    "python_version": sys.version,
    "os_release": pathlib.Path("/etc/os-release").read_text() if pathlib.Path("/etc/os-release").is_file() else None,
}
platform_sha = write_json(evidence / "PLATFORM.json", platform_receipt)

log_sha = file_sha(evidence / "unittest.log")
acceptance = {
    "schema": "ferocity-watchdog-current-runtime-acceptance-v1",
    "status": "PASS",
    "run_receipt_sha256": run_sha,
    "source_guard_unchanged": True,
    "fixed_process_cases": {
        "executed": EXPECTED_CASES,
        "passed": EXPECTED_CASES,
        "failed": 0,
        "errors": 0,
        "skipped": 0,
    },
    "case_names": CASE_NAMES,
    "files": [
        {"path": claim_rel.as_posix(), "sha256": claim_sha},
        {"path": "source/trial_watchdog.py", "sha256": EXPECTED_WATCHDOG_SHA},
        {"path": "AUTHOR_RECEIPT.json", "sha256": author_sha},
        {"path": "RUN_RECEIPT.json", "sha256": run_sha},
        {"path": "PLATFORM.json", "sha256": platform_sha},
        {"path": "unittest.log", "sha256": log_sha},
    ],
    "python_executable": str(python_path),
    "python_executable_sha256": python_sha,
    "container_image": CONTAINER,
    "authority": "FIXED_PROCESS_WATCHDOG_REQUALIFICATION_ONLY",
    "final_d2_verify_authorized": False,
    "allocation_authorized": False,
    "gameplay_authorized": False,
    "official_counters_delta": 0,
}
acceptance_sha = write_json(evidence / "ACCEPTANCE.json", acceptance)

summary = {
    "schema": "ferocity-watchdog-current-runtime-requalification-summary-v1",
    "status": "PASS",
    "source08_commit": SOURCE08,
    "container_image": CONTAINER,
    "python_executable": str(python_path),
    "python_executable_sha256": python_sha,
    "watchdog_source_sha256": EXPECTED_WATCHDOG_SHA,
    "tests": EXPECTED_CASES,
    "passes": EXPECTED_CASES,
    "acceptance_sha256": acceptance_sha,
    "run_receipt_sha256": run_sha,
    "author_receipt_sha256": author_sha,
    "process_claim_sha256": claim_sha,
    "source_guard_unchanged": True,
    "java_started": False,
    "gradle_started": False,
    "entropy_read": False,
    "allocation_read": False,
    "game_initialized": False,
    "official_counters_delta": 0,
}
write_json(report / "summary.json", summary)
print(json.dumps(summary, sort_keys=True))
