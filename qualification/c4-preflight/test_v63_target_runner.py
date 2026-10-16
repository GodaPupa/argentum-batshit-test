"""Excluded, host-free V6.3 source tests; NO GitHub Actions, probe, or game."""
import ast
import copy
import hashlib
import importlib
import json
from pathlib import Path
import re
import tempfile
import unittest

import v63_target_runner as v

ROOT = Path(__file__).resolve().parent
SRC = "a139ba26be65a5130f55011c8fd021fd9a1c4e28"  # deliberately older than child
HEAD = "0" * 39 + "1"


def env():
    return {
        "GITHUB_ACTIONS": "true",
        "GITHUB_REPOSITORY": "GodaPupa/argentum-batshit-test",
        "GITHUB_EVENT_NAME": "push",
        "GITHUB_REF": "refs/heads/infra/argentum-c4-v63-readonly-once-20261015",
        "GITHUB_WORKFLOW_REF": v.EXPECTED_WORKFLOW_REF,
        "GITHUB_RUN_NUMBER": "1",
        "GITHUB_RUN_ATTEMPT": "1",
        "RUNNER_OS": "Linux",
        "RUNNER_ARCH": "X64",
        "RUNNER_ENVIRONMENT": "github-hosted",
        "GITHUB_SHA": HEAD,
        "GITHUB_RUN_ID": "1234567890",
    }


def fixture():
    return {
        "manager": {
            "pid1_comm_hint": "systemd", "same_pid_namespace_hint": True,
            "same_mount_namespace_hint": True,
            "system_bus_path_hint": {"state": "PRESENT", "uid": 0},
            "cgroup_mountinfo_hints": {"state": "PARSED_UNATTESTED", "mounts": [
                {"mountpoint_hint": "/sensitive/do-not-emit"}
            ]},
        },
        "observer": {
            "btf_vmlinux_file_hint": {"state": "PRESENT"},
            "tracepoint_enter_path_hint": {"state": "PRESENT"},
            "tracepoint_exit_path_hint": {"state": "PRESENT"},
            "perf_event_paranoid_hint": -1,
            "unprivileged_bpf_disabled_hint": 0,
            "active_lsm_hint": ["bpf", "selinux"],
        },
        "watchdog": {"cgroup_kill_path_hint": {"state": "PRESENT"},
                     "cgroup_events_path_hint": {"state": "PRESENT"}},
        "boot_id_sha256_hint": "never-copy-boot-identifier",
        "really_authorized": True,
    }


class V63TargetTests(unittest.TestCase):
    def test_valid_context_is_explicitly_self_reported(self):
        r=v.check_context(env(), expected_source=SRC)
        self.assertTrue(r["ok"])
        self.assertIn("SELF_REPORTED", r["status"])
        self.assertEqual(r["external_control_plane_verification"], "NOT_PERFORMED")

    def test_no_access_without_context(self):
        called=[]
        r=v.evaluate({}, expected_source=SRC, capture=lambda: called.append(1))
        self.assertEqual(called, [])
        self.assertEqual(r["disposition"], "NO_CAPTURE_CONTEXT_GUARD_REFUSED")
        self.assertFalse(r["captured"])

    def test_missing_action(self):
        e=env();e.pop("GITHUB_ACTIONS")
        self.assertFalse(v.check_context(e, expected_source=SRC)["ok"])

    def test_untrusted_repository(self):
        e=env();e["GITHUB_REPOSITORY"]="evil/repo"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_pull_request_event(self):
        e=env();e["GITHUB_EVENT_NAME"]="pull_request"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_workflow_dispatch_event(self):
        e=env();e["GITHUB_EVENT_NAME"]="workflow_dispatch"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_wrong_ref(self):
        e=env();e["GITHUB_REF"]="refs/heads/main"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_wrong_workflow_ref(self):
        e=env();e["GITHUB_WORKFLOW_REF"]="attacker@refs/heads/main"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_second_run_forbidden(self):
        e=env();e["GITHUB_RUN_NUMBER"]="2"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_rerun_forbidden(self):
        e=env();e["GITHUB_RUN_ATTEMPT"]="2"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_runner_os(self):
        e=env();e["RUNNER_OS"]="Windows"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_runner_architecture(self):
        e=env();e["RUNNER_ARCH"]="ARM64"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_self_hosted_not_allowed(self):
        e=env();e["RUNNER_ENVIRONMENT"]="self-hosted"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_bad_head_sha(self):
        e=env();e["GITHUB_SHA"]="aabb"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_source_head_not_same(self):
        e=env();e["GITHUB_SHA"]=SRC
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_bad_source_pin(self):
        self.assertFalse(v.check_context(env(),expected_source="bad")["ok"])

    def test_short_run_id(self):
        e=env();e["GITHUB_RUN_ID"]="0"
        self.assertFalse(v.check_context(e,expected_source=SRC)["ok"])

    def test_empty_context_never_capture(self):
        calls=[]
        r=v.evaluate({"authorized":"yes"},expected_source=SRC,
                     capture=lambda: calls.append(True))
        self.assertEqual(calls, [])
        self.assertFalse(r["compile_enabled"])

    def test_all_green_remains_blocked(self):
        r=v.evaluate(env(),expected_source=SRC,capture=fixture)
        self.assertTrue(r["captured"])
        self.assertEqual(r["disposition"],v.DISPOSITION)
        for k in ("runtime_enforcement_qualified", "compile_enabled", "hosted_permission",
                  "D2_qualified", "C4_PASS", "runtime_authorization_created",
                  "observer_authorization_created", "watchdog_authorization_created"):
            self.assertIs(r[k],False,k)
        self.assertEqual(r["historical_C4"],"INCOMPLETE")
        self.assertEqual(r["manager_status"],"BLOCKED_HOST_PARENT")
        self.assertEqual(r["observer_status"],"BLOCKED_OBSERVER")
        self.assertEqual(r["watchdog_status"],"BLOCKED_WATCHDOG")

    def test_all_green_hints_cannot_authenticate(self):
        r=v.evaluate(env(),expected_source=SRC,capture=fixture)
        h=r["hints"]
        self.assertFalse(h["source_authenticated"])
        self.assertFalse(h["target_host_authenticated"])
        self.assertFalse(h["event_stream_loss_accounted"])
        self.assertFalse(h["watchdog_external_armed"])
        self.assertFalse(h["held_D_S_C_FDs_verified"])

    def test_sensitive_hint_strings_excluded(self):
        r=v.evaluate(env(),expected_source=SRC,capture=fixture)
        s=json.dumps(r)
        self.assertNotIn("/sensitive",s)
        self.assertNotIn("never-copy-boot-identifier",s)
        self.assertNotIn('"really_authorized": true',s)

    def test_read_failure_incomplete(self):
        def fail(): raise PermissionError("secret-path")
        r=v.evaluate(env(),expected_source=SRC,capture=fail)
        self.assertFalse(r["captured"])
        self.assertEqual(r["disposition"],"INCOMPLETE_READ_ONLY_PROBE_FAILURE")
        self.assertEqual(r["probe_exception_type"],"PermissionError")
        self.assertNotIn("secret-path",json.dumps(r))

    def test_dependency_exact_bytes(self):
        src=ROOT/"v62_host_capability.py"
        self.assertEqual(hashlib.sha256(src.read_bytes()).hexdigest(),v.V62_MODULE_SHA256)
        with tempfile.TemporaryDirectory() as t:
            dest=Path(t)/"v62_host_capability.py"
            dest.write_bytes(src.read_bytes()+b"\n# altered\n")
            with self.assertRaisesRegex(ValueError,"FROZEN_V62_DEPENDENCY_MISMATCH"):
                v.load_frozen_v62(dest)

    def test_wrong_dependency_name(self):
        with self.assertRaisesRegex(ValueError,"WRONG_DEPENDENCY_NAME"):
            v.load_frozen_v62(ROOT/"wrong.py")

    def test_module_no_side_effect_imports(self):
        tree=ast.parse((ROOT/"v63_target_runner.py").read_text())
        forbidden={"socket","subprocess","ctypes","requests","urllib","http","multiprocessing"}
        imports=set()
        for node in ast.walk(tree):
            if isinstance(node,ast.Import):imports.update(n.name.split(".")[0] for n in node.names)
            if isinstance(node,ast.ImportFrom) and node.module:imports.add(node.module.split(".")[0])
        self.assertFalse(imports & forbidden)

    def test_workflow_source_is_inactive(self):
        d=ROOT/"V63_INACTIVE_WORKFLOW.yml.template"
        s=d.read_text()
        self.assertIn("on:\n  push:",s)
        self.assertIn("permissions:\n  contents: read",s)
        self.assertIn("cancel-in-progress: false",s)
        self.assertIn("actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",s)
        self.assertNotIn("sudo ",s)
        self.assertNotIn("workflow_dispatch",s)
        self.assertNotIn("git push",s)
        self.assertNotIn("./gradlew",s)
        self.assertNotIn("systemctl",s)
        self.assertIn("{{PINNED_SOURCE_SHA}}",s)
        self.assertIn("{{INLINE_V62_GZIP_B64}}",s)
        self.assertIn("{{INLINE_V63_GZIP_B64}}",s)

    def test_workflow_source_not_live(self):
        self.assertFalse((ROOT.parent.parent/".github"/"workflows"/"argentum-c4-v63-readonly-20261015.yml").exists())

    def test_cli_requires_flag(self):
        import contextlib,io
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as x:
                v.main(["--source-commit",SRC])
        self.assertEqual(x.exception.code,2)

    def test_inherited_source_unchanged(self):
        # Source identity is checked against preserved nested V6.2 ZIP by freeze code.
        self.assertIn("a139ba26", (ROOT/"V63_TARGET_HOST_SCOPE.md").read_text())


if __name__=="__main__":
    unittest.main()
