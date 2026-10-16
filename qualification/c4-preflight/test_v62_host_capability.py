"""V6.2 bounded, host-free fixture tests for diagnostic-only capability inventory."""
import ast
import hashlib
import json
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent
module_spec = importlib.util.spec_from_file_location("v62_host_capability", ROOT / "v62_host_capability.py")
assert module_spec and module_spec.loader
h = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(h)

MOUNTINFO = ("32 1 0:29 / /proc rw,nosuid - proc proc rw\n"
             "33 1 0:34 / /sys/fs/cgroup rw,nosuid - cgroup2 cgroup rw\n")


class FakeUname:
    release = "synthetic-kernel"
    machine = "synthetic-machine"


def fixture():
    texts = {p: {"state": "UNAVAILABLE", "reason": "Synthetic"} for p in h.TEXT_PATHS}
    texts.update({
        "/proc/sys/kernel/random/boot_id": {"state": "READABLE", "text": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\n"},
        "/proc/1/comm": {"state": "READABLE", "text": "systemd\n"},
        "/proc/1/status": {"state": "READABLE", "text": "Name:\tsystemd\nUid:\t0\t0\t0\t0\n"},
        "/proc/self/status": {"state": "READABLE", "text": "CapEff:\t0000000000000000\n"},
        "/proc/self/mountinfo": {"state": "READABLE", "text": MOUNTINFO},
        "/proc/sys/kernel/perf_event_paranoid": {"state": "READABLE", "text": "-1\n"},
        "/proc/sys/kernel/unprivileged_bpf_disabled": {"state": "READABLE", "text": "0\n"},
        "/sys/kernel/security/lsm": {"state": "READABLE", "text": "bpf,selinux\n"},
    })
    metas = {p: {"state": "PRESENT", "type": "regular", "uid": 0, "mode": "0o444", "size": 1} for p in h.META_PATHS}
    metas["/run/dbus/system_bus_socket"] = {"state": "PRESENT", "type": "socket", "uid": 0, "mode": "0o666", "size": 0}
    links = {p: {"state": "READABLE", "value": "pid:[4026531836]" if "/pid" in p else "mnt:[4026531840]"} for p in h.LINK_PATHS}
    def r(p): return texts[p]
    def m(p): return metas[p]
    def l(p): return links[p]
    return texts, metas, links, r, m, l


def cap(fs=None):
    _, _, _, r, m, l = fixture() if fs is None else fs
    return h.capture(r, m, l, lambda: FakeUname())


class CapabilityTests(unittest.TestCase):
    def test_default_no_authority_even_all_green(self):
        d = cap()
        self.assertEqual(d["disposition"], h.UNQUALIFIED)
        self.assertFalse(d["runtime_enforcement_qualified"])
        self.assertFalse(d["compile_enabled"])
        self.assertFalse(d["D2_qualified"])
        self.assertFalse(d["target_github_runner_identified"])
        self.assertFalse(d["authority_receipt_created"])
        self.assertEqual(d["historical_C4"], "INCOMPLETE")

    def test_manager_never_gets_dbus_authority_from_socket(self):
        m = cap()["manager"]
        self.assertEqual(m["pid1_comm_hint"], "systemd")
        self.assertEqual(m["pid1_dbus_unique_owner"], "NOT_QUERIED_NO_CONNECTION")
        self.assertEqual(m["held_D_S_C_fds"], "NOT_OPENED")
        self.assertFalse(m["source_authenticated"])
        self.assertEqual(m["status"], "BLOCKED_HOST_PARENT")

    def test_observer_btf_and_perm_flags_not_backend(self):
        o = cap()["observer"]
        self.assertTrue(o["btf_metadata_present"])
        self.assertEqual(o["unprivileged_bpf_disabled_hint"], 0)
        self.assertEqual(o["kernel_fd_inode_resolution"], "NOT_ESTABLISHED")
        self.assertEqual(o["per_cpu_loss_accounting"], "NOT_ESTABLISHED")
        self.assertEqual(o["status"], "BLOCKED_OBSERVER")

    def test_watchdog_cgroup_kill_metadata_does_not_grant_termination(self):
        w = cap()["watchdog"]
        self.assertEqual(w["cgroup_kill_path_hint"]["state"], "PRESENT")
        self.assertEqual(w["externally_armed_controller"], "NOT_IMPLEMENTED")
        self.assertEqual(w["status"], "BLOCKED_WATCHDOG")

    def test_missing_btf_remains_blocked(self):
        t, m, l, r, _, lk = fixture()
        m["/sys/kernel/btf/vmlinux"] = {"state": "UNAVAILABLE"}
        d = h.capture(r, lambda p: m[p], lk, lambda: FakeUname())
        self.assertFalse(d["observer"]["btf_metadata_present"])
        self.assertEqual(d["disposition"], h.UNQUALIFIED)

    def test_missing_bus_remains_blocked(self):
        t, m, l, r, _, lk = fixture()
        m["/run/dbus/system_bus_socket"] = {"state": "UNAVAILABLE"}
        d = h.capture(r, lambda p: m[p], lk, lambda: FakeUname())
        self.assertFalse(d["manager"]["source_authenticated"])

    def test_even_pid1_spoof_does_not_grant_manager(self):
        t, m, l, _, mt, lk = fixture()
        t["/proc/1/comm"] = {"state": "READABLE", "text": "systemd\n"}
        d = h.capture(lambda p: t[p], mt, lk, lambda: FakeUname())
        self.assertEqual(d["manager"]["status"], "BLOCKED_HOST_PARENT")

    def test_pid_namespace_missing_not_assumed_equal(self):
        t, m, l, rd, mt, _ = fixture()
        l["/proc/1/ns/pid"] = {"state": "UNAVAILABLE"}
        d = h.capture(rd, mt, lambda p: l[p], lambda: FakeUname())
        self.assertIsNone(d["manager"]["same_pid_namespace_hint"])

    def test_pid_namespace_mismatch_not_assumed_equal(self):
        t, m, l, rd, mt, _ = fixture()
        l["/proc/1/ns/pid"] = {"state": "READABLE", "value": "pid:[101]"}
        d = h.capture(rd, mt, lambda p: l[p], lambda: FakeUname())
        self.assertFalse(d["manager"]["same_pid_namespace_hint"])

    def test_ambiguous_mounts_not_attested(self):
        s = MOUNTINFO + "35 1 0:35 / /other rw - cgroup2 cgroup rw\n"
        d = h.parse_mountinfo(s)
        self.assertEqual(len(d["mounts"]), 2)
        self.assertEqual(d["state"], "PARSED_UNATTESTED")

    def test_malformed_mountinfo_rejected(self):
        self.assertEqual(h.parse_mountinfo("not mountinfo")["state"], "MALFORMED")
        self.assertEqual(h.parse_mountinfo(None)["state"], "UNKNOWN")
        self.assertEqual(h.parse_mountinfo("x" * 8193)["state"], "MALFORMED")

    def test_sysctl_unavailable_not_assumed_permitted(self):
        t, m, l, _, mt, lk = fixture()
        t["/proc/sys/kernel/perf_event_paranoid"] = {"state": "UNAVAILABLE"}
        d = h.capture(lambda p: t[p], mt, lk, lambda: FakeUname())
        self.assertIsNone(d["observer"]["perf_event_paranoid_hint"])
        self.assertEqual(d["observer"]["status"], "BLOCKED_OBSERVER")

    def test_untrusted_extra_authorized_key_ignored(self):
        t, m, l, _, mt, lk = fixture()
        t["/proc/1/status"]["authorized"] = True
        d = h.capture(lambda p: t[p], mt, lk, lambda: FakeUname())
        self.assertFalse(d["hosted_permission"])

    def test_no_raw_cgroup_path_or_boot_id_leaks(self):
        t, m, l, _, mt, lk = fixture()
        t["/proc/self/cgroup"] = {"state": "READABLE", "text": "0::/very-private-id-12345\n"}
        d = h.capture(lambda p: t[p], mt, lk, lambda: FakeUname())
        s = json.dumps(d)
        self.assertNotIn("very-private-id", s)
        self.assertNotIn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", s)
        self.assertIn(hashlib.sha256(b"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee").hexdigest(), s)

    def test_no_observation_by_default_cli(self):
        cp = subprocess.run([sys.executable,"-I","-B",str(ROOT/"v62_host_capability.py")],
                            stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        self.assertNotEqual(cp.returncode, 0)
        self.assertIn("--local-observe required", cp.stderr)

    def test_read_targets_explicitly_fixed(self):
        self.assertEqual(len(h.TEXT_PATHS), len(set(h.TEXT_PATHS)))
        self.assertEqual(len(h.META_PATHS), len(set(h.META_PATHS)))
        self.assertEqual(len(h.LINK_PATHS), len(set(h.LINK_PATHS)))
        self.assertEqual(h._bounded_read("/sys/fs/cgroup/cgroup.kill")["state"], "REJECTED_NONWHITELISTED_PATH")
        self.assertEqual(h._lstat_only("/tmp/user-chosen")["state"], "REJECTED_NONWHITELISTED_PATH")
        self.assertEqual(h._readlink_only("/tmp/user-chosen")["state"], "REJECTED_NONWHITELISTED_PATH")

    def test_source_does_not_have_side_effect_transports(self):
        source = (ROOT/"v62_host_capability.py").read_text()
        tree = ast.parse(source)
        forbidden_imports = {"subprocess", "socket", "ctypes", "requests", "urllib", "fcntl", "signal", "bcc", "bpf"}
        for node in ast.walk(tree):
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                names = [x.name.split(".")[0] for x in node.names] if isinstance(node, ast.Import) else [node.module.split(".")[0] if node.module else ""]
                self.assertTrue(set(names).isdisjoint(forbidden_imports))
            if isinstance(node, ast.Attribute) and node.attr in ("connect", "bind", "send", "sendto", "kill", "execv", "system", "popen", "spawnl", "fork", "write", "replace", "unlink", "mkdir", "chmod", "chown"):
                self.fail("side-effect method in capability source: " + node.attr)
        self.assertNotIn("subprocess", source.split("import argparse",1)[1].split("CONTRACT =",1)[0])

    def test_v61_gates_remain_frozen(self):
        for name in ("v61_gate.py", "v61_scope.py", "v61_observer.py", "v61_watchdog.py", "v6_adapter.py", "preflight.py"):
            self.assertTrue((ROOT/name).exists())
        self.assertFalse(cap()["compile_enabled"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
