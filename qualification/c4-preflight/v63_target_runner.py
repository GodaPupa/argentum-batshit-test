"""ARGENTUM C4 V6.3 — source-only, unprivileged target-runner *hint* collector.

This file is INACTIVE unless a separately reviewed workflow and exact owner
authorization are later published/approved. Presence of GitHub Actions environment
variables is SELF-REPORTED context, not proof of hosted execution or authority.

No runtime source in this module may enable compilation, qualification, a provider,
experiment, cgroup/systemd mutation, kernel observer, or watchdog deployment.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
from typing import Callable, Mapping

CONTRACT = "ARGENTUM_C4_V63_UNATTESTED_TARGET_RUNNER_HINTS_V1"
EXPECTED_REPOSITORY = "GodaPupa/argentum-batshit-test"
WORKFLOW_PATH = ".github/workflows/argentum-c4-v63-readonly-20261015.yml"
EXPECTED_BRANCH = "infra/argentum-c4-v63-readonly-once-20261015"
EXPECTED_WORKFLOW_REF = (
    EXPECTED_REPOSITORY + "/" + WORKFLOW_PATH + "@refs/heads/" + EXPECTED_BRANCH
)
V62_MODULE_SHA256 = "5728476d6fdd6b2b4a7849c2a91d1c3dda9d00a43c083e2b572eae9ada058282"
DISPOSITION = "BLOCKED_TARGET_CAPABILITY_HINTS_ONLY"

# This wrapper does not import the frozen parent until *after* input guards.
# The parent reader uses only fixed /proc, /sys, /run file paths.
def check_context(env: Mapping[str, str], *, expected_source: str) -> dict:
    """Do not read host metadata unless all local context guards agree.

    No check authenticates the GitHub host. Caller must independently verify the
    Actions original against the GitHub control plane, commit, workflow, and artifact.
    """
    if not re.fullmatch(r"[0-9a-f]{40}", expected_source or ""):
        return {"ok": False, "reason": "BAD_EXPECTED_SOURCE_PIN"}
    exact = {
        "GITHUB_ACTIONS": "true",
        "GITHUB_REPOSITORY": EXPECTED_REPOSITORY,
        "GITHUB_EVENT_NAME": "push",
        "GITHUB_REF": "refs/heads/" + EXPECTED_BRANCH,
        "GITHUB_WORKFLOW_REF": EXPECTED_WORKFLOW_REF,
        "GITHUB_RUN_NUMBER": "1",
        "GITHUB_RUN_ATTEMPT": "1",
        "RUNNER_OS": "Linux",
        "RUNNER_ARCH": "X64",
        "RUNNER_ENVIRONMENT": "github-hosted",
    }
    for name, required in exact.items():
        if env.get(name) != required:
            return {"ok": False, "reason": "CONTEXT_MISMATCH_" + name}
    sha = env.get("GITHUB_SHA", "")
    run_id = env.get("GITHUB_RUN_ID", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        return {"ok": False, "reason": "INVALID_RUN_HEAD"}
    if not re.fullmatch(r"[1-9][0-9]{5,19}", run_id):
        return {"ok": False, "reason": "INVALID_RUN_ID"}
    if sha == expected_source:
        return {"ok": False, "reason": "VALIDATION_HEAD_MUST_BE_SEPARATE_FROM_SOURCE"}
    return {
        "ok": True,
        "status": "CONTEXT_MATCHED_SELF_REPORTED_NOT_AUTHENTICATED",
        "expected_source_commit": expected_source,
        "run_head_hint": sha,
        "run_id_hint": run_id,
        "run_attempt_hint": 1,
        "run_number_hint": 1,
        "workflow_ref_hint": EXPECTED_WORKFLOW_REF,
        "external_control_plane_verification": "NOT_PERFORMED",
    }


def load_frozen_v62(source: Path) -> Callable[[], dict]:
    """Verify exact frozen reader bytes before import; NEVER auto-fetch/modify."""
    if source.name != "v62_host_capability.py":
        raise ValueError("WRONG_DEPENDENCY_NAME")
    data = source.read_bytes()
    if hashlib.sha256(data).hexdigest() != V62_MODULE_SHA256:
        raise ValueError("FROZEN_V62_DEPENDENCY_MISMATCH")
    spec = importlib.util.spec_from_file_location("_argentum_c4_frozen_v62", source)
    if spec is None or spec.loader is None:
        raise ValueError("DEPENDENCY_UNLOADABLE")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if module.CONTRACT != "ARGENTUM_C4_V62_LOCAL_CAPABILITY_DIAGNOSTIC_V1":
        raise ValueError("FROZEN_CONTRACT_MISMATCH")
    return module.capture


def _minimize_snapshot(snap: dict) -> dict:
    """Retain only selected bounded hints; no raw mount paths or boot/cgroup IDs."""
    manager = snap.get("manager", {})
    observer = snap.get("observer", {})
    watchdog = snap.get("watchdog", {})
    mounts = manager.get("cgroup_mountinfo_hints", {})
    m_list = mounts.get("mounts", []) if type(mounts) is dict else []
    bus = manager.get("system_bus_path_hint")
    btf = observer.get("btf_vmlinux_file_hint")
    return {
        "provenance": "SELF_REPORTED_RUNNER_CONTEXT_PLUS_LOCAL_FILESYSTEM_HINTS_ONLY",
        "pid1_comm_hint": manager.get("pid1_comm_hint"),
        "same_pid_namespace_hint": manager.get("same_pid_namespace_hint"),
        "same_mount_namespace_hint": manager.get("same_mount_namespace_hint"),
        "system_bus_path_state": bus.get("state") if type(bus) is dict else "UNKNOWN",
        "cgroup2_mount_count_hint": len(m_list) if type(m_list) is list else None,
        "mount_parse_state_hint": mounts.get("state") if type(mounts) is dict else "UNKNOWN",
        "btf_file_state": btf.get("state") if type(btf) is dict else "UNKNOWN",
        "trace_enter_state": observer.get("tracepoint_enter_path_hint", {}).get("state"),
        "trace_exit_state": observer.get("tracepoint_exit_path_hint", {}).get("state"),
        "perf_event_paranoid_hint": observer.get("perf_event_paranoid_hint"),
        "unprivileged_bpf_disabled_hint": observer.get("unprivileged_bpf_disabled_hint"),
        "active_lsm_hint": observer.get("active_lsm_hint"),
        "cgroup_kill_path_state": watchdog.get("cgroup_kill_path_hint", {}).get("state"),
        "cgroup_events_path_state": watchdog.get("cgroup_events_path_hint", {}).get("state"),
        "source_authenticated": False,
        "target_host_authenticated": False,
        "kernel_attached": False,
        "event_stream_loss_accounted": False,
        "watchdog_external_armed": False,
        "held_D_S_C_FDs_verified": False,
    }


def evaluate(env: Mapping[str, str], *, expected_source: str,
             capture: Callable[[], dict]) -> dict:
    """All paths return BLOCKED; never interpret positive hints as PASS."""
    context = check_context(env, expected_source=expected_source)
    output = {
        "contract": CONTRACT,
        "disposition": DISPOSITION,
        "context": context,
        "captured": False,
        "manager_status": "BLOCKED_HOST_PARENT",
        "observer_status": "BLOCKED_OBSERVER",
        "watchdog_status": "BLOCKED_WATCHDOG",
        "runtime_enforcement_qualified": False,
        "compile_enabled": False,
        "hosted_permission": False,
        "D2_qualified": False,
        "C4_PASS": False,
        "historical_C4": "INCOMPLETE",
        "runtime_authorization_created": False,
        "observer_authorization_created": False,
        "watchdog_authorization_created": False,
        "read_only_capability_only": True,
        "no_games_executed": True,
        "no_compiler_invoked": True,
    }
    if not context.get("ok"):
        output["disposition"] = "NO_CAPTURE_CONTEXT_GUARD_REFUSED"
        return output
    try:
        snap = capture()
        output["hints"] = _minimize_snapshot(snap)
        output["captured"] = True
    except Exception as exc:
        # Do not leak input data or full exception strings.
        output["disposition"] = "INCOMPLETE_READ_ONLY_PROBE_FAILURE"
        output["probe_exception_type"] = type(exc).__name__
    return output


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--target-observe", action="store_true")
    ap.add_argument("--source-commit", required=True)
    args = ap.parse_args(argv)
    if not args.target_observe:
        ap.error("explicit --target-observe required; default is inert")
    # Guard context before reading the frozen dependency or any host facts.
    ctx = check_context(os.environ, expected_source=args.source_commit)
    if not ctx.get("ok"):
        print(json.dumps(evaluate(os.environ, expected_source=args.source_commit,
                                  capture=lambda: (_ for _ in ()).throw(AssertionError("UNREACHABLE"))), sort_keys=True))
        return 2
    frozen = load_frozen_v62(Path(__file__).resolve().parent / "v62_host_capability.py")
    r = evaluate(os.environ, expected_source=args.source_commit, capture=frozen)
    print(json.dumps(r, sort_keys=True, separators=(",", ":")))
    return 0 if r.get("captured") else 2


if __name__ == "__main__":
    raise SystemExit(main())
