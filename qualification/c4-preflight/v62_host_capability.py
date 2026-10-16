"""ARGENTUM C4 V6.2 — unprivileged, read-only local capability inventory.

NOT a systemd transport, kernel-event observer, external watchdog or C4 gate.
Captures a few bounded local kernel/process facts; no socket connections,
attached probes, ELF invocations, systemd APIs, cgroup writes, child processes,
PID signals, privileged FDs or runtime authorization exist in this module.

This program CANNOT attest that the process is running on the intended future
GitHub runner. Its locally captured facts are diagnostic and NEVER sufficient
to authorize a protected compile or any runtime action.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import stat
from typing import Callable

CONTRACT = "ARGENTUM_C4_V62_LOCAL_CAPABILITY_DIAGNOSTIC_V1"
SNAPSHOT_PROVENANCE = "LOCAL_PROCESS_UNATTESTED_NOT_TARGET_RUNNER"
UNQUALIFIED = "BLOCKED_CAPABILITY_ONLY"

# All host read targets are hard-coded and intentionally narrow. In particular
# no relative unit/cgroup paths from the host are used as filesystem inputs.
TEXT_PATHS = (
    "/proc/sys/kernel/random/boot_id",
    "/proc/1/comm",
    "/proc/1/status",
    "/proc/self/status",
    "/proc/1/cgroup",
    "/proc/self/cgroup",
    "/proc/self/mountinfo",
    "/proc/sys/kernel/perf_event_paranoid",
    "/proc/sys/kernel/unprivileged_bpf_disabled",
    "/sys/kernel/security/lsm",
    "/sys/fs/cgroup/cgroup.controllers",
    "/sys/fs/cgroup/cgroup.subtree_control",
)
META_PATHS = (
    "/run/dbus/system_bus_socket",
    "/sys/kernel/btf/vmlinux",
    "/sys/kernel/tracing/events/raw_syscalls/sys_enter",
    "/sys/kernel/tracing/events/raw_syscalls/sys_exit",
    "/sys/fs/cgroup/cgroup.events",
    "/sys/fs/cgroup/cgroup.kill",
)
LINK_PATHS = ("/proc/1/ns/pid", "/proc/self/ns/pid", "/proc/1/ns/mnt", "/proc/self/ns/mnt")


def _bounded_read(path: str) -> dict:
    """Read a fixed, whitelisted pseudo-file only. Never mutate it."""
    if path not in TEXT_PATHS:
        return {"state": "REJECTED_NONWHITELISTED_PATH"}
    limit = 131072 if path.endswith("/mountinfo") else 16384
    try:
        with open(path, "rb") as fh:
            b = fh.read(limit + 1)
        if len(b) > limit:
            return {"state": "OVERSIZED"}
        return {"state": "READABLE", "text": b.decode("utf-8", errors="replace")}
    except (OSError, ValueError) as exc:
        return {"state": "UNAVAILABLE", "reason": type(exc).__name__}


def _lstat_only(path: str) -> dict:
    if path not in META_PATHS:
        return {"state": "REJECTED_NONWHITELISTED_PATH"}
    try:
        st = os.lstat(path)
        return {"state": "PRESENT", "type": "socket" if stat.S_ISSOCK(st.st_mode) else
            "regular" if stat.S_ISREG(st.st_mode) else "directory" if stat.S_ISDIR(st.st_mode) else
            "symlink" if stat.S_ISLNK(st.st_mode) else "other", "uid": st.st_uid,
            "mode": oct(stat.S_IMODE(st.st_mode)), "size": st.st_size}
    except OSError as exc:
        return {"state": "UNAVAILABLE", "reason": type(exc).__name__}


def _readlink_only(path: str) -> dict:
    if path not in LINK_PATHS:
        return {"state": "REJECTED_NONWHITELISTED_PATH"}
    try:
        v = os.readlink(path)
        if not re.fullmatch(r"(?:pid|mnt):\[[0-9]+\]", v):
            return {"state": "MALFORMED"}
        return {"state": "READABLE", "value": v}
    except OSError as exc:
        return {"state": "UNAVAILABLE", "reason": type(exc).__name__}


def _text(readbacks: dict, path: str) -> str | None:
    v = readbacks.get(path)
    if type(v) is dict and v.get("state") == "READABLE" and type(v.get("text")) is str:
        return v["text"]
    return None


def _sysctl_int(readbacks: dict, path: str) -> int | None:
    value = _text(readbacks, path)
    if value is None or not re.fullmatch(r"-?[0-9]{1,9}\s*", value):
        return None
    try:
        return int(value)
    except ValueError:
        return None


def _namespace(links: dict, key: str) -> str | None:
    r = links.get(key)
    return r.get("value") if type(r) is dict and r.get("state") == "READABLE" else None


def _status_value(raw: str | None, key: str) -> str | None:
    if raw is None:
        return None
    for line in raw.splitlines():
        if line.startswith(key + ":"):
            return line.partition(":")[2].strip()
    return None


def parse_mountinfo(data: str | None) -> dict:
    """Return cgroup2 mount hints only; never infer stable host mount identity."""
    if data is None:
        return {"state": "UNKNOWN", "mounts": []}
    seen = []
    for line in data.splitlines():
        if len(line) > 8192:
            return {"state": "MALFORMED", "mounts": []}
        parts = line.split(" - ", 1)
        if len(parts) != 2:
            return {"state": "MALFORMED", "mounts": []}
        pre, post = parts
        left, right = pre.split(), post.split()
        if len(left) < 6 or len(right) < 3 or not left[0].isdecimal():
            return {"state": "MALFORMED", "mounts": []}
        if right[0] == "cgroup2":
            seen.append({"mount_id_hint": int(left[0]), "root_hint": left[3][:512],
                "mountpoint_hint": left[4][:512], "filesystem": "cgroup2"})
            if len(seen) > 32:
                return {"state": "AMBIGUOUS_TOO_MANY", "mounts": []}
    return {"state": "PARSED_UNATTESTED", "mounts": seen}


def _safe_name(data: str | None) -> str | None:
    if data is None:
        return None
    v = data.strip()
    return v if re.fullmatch(r"[a-zA-Z0-9_.@-]{1,128}", v) else None


def capture(read: Callable[[str], dict] = _bounded_read,
            meta: Callable[[str], dict] = _lstat_only,
            link: Callable[[str], dict] = _readlink_only,
            uname: Callable[[], object] = platform.uname) -> dict:
    """Purely diagnostic local snapshot; caller-supplied readers enable synthetic testing.

    Does NOT authenticate PID1, attach a kernel observer, open held D/S/C FDs,
    assert an independent watchdog, or produce a source-bound runtime permit.
    """
    rb = {p: read(p) for p in TEXT_PATHS}
    mb = {p: meta(p) for p in META_PATHS}
    lb = {p: link(p) for p in LINK_PATHS}
    k = uname()
    boot = _text(rb, "/proc/sys/kernel/random/boot_id")
    boot_digest = (hashlib.sha256(boot.strip().encode()).hexdigest() if boot and
                   re.fullmatch(r"[0-9a-f-]{36}", boot.strip()) else None)
    p1_uid = _status_value(_text(rb, "/proc/1/status"), "Uid")
    self_caps = _status_value(_text(rb, "/proc/self/status"), "CapEff")
    self_caps = self_caps if self_caps and re.fullmatch(r"[0-9a-fA-F]{1,32}", self_caps) else None
    lsm = _text(rb, "/sys/kernel/security/lsm")
    lsm_names = ([x for x in lsm.strip().split(",") if re.fullmatch(r"[A-Za-z0-9_-]{1,40}", x)]
                 if lsm is not None else None)
    pid1_pidns = _namespace(lb, "/proc/1/ns/pid")
    self_pidns = _namespace(lb, "/proc/self/ns/pid")
    pid1_mntns = _namespace(lb, "/proc/1/ns/mnt")
    self_mntns = _namespace(lb, "/proc/self/ns/mnt")
    mounts = parse_mountinfo(_text(rb, "/proc/self/mountinfo"))
    bus = mb["/run/dbus/system_bus_socket"]
    btf = mb["/sys/kernel/btf/vmlinux"]
    available = lambda x: type(x) is dict and x.get("state") == "PRESENT"
    # Interpret no hint as authorization. Even a complete all-green snapshot is
    # untrusted without source-bound owner authority and a live provider design.
    return {
        "contract": CONTRACT,
        "provenance": SNAPSHOT_PROVENANCE,
        "kernel_release_hint": str(getattr(k, "release", ""))[:120],
        "machine_hint": str(getattr(k, "machine", ""))[:60],
        "boot_id_sha256_hint": boot_digest,
        "manager": {
            "pid1_comm_hint": _safe_name(_text(rb, "/proc/1/comm")),
            "pid1_uid_status_hint": p1_uid if p1_uid and re.fullmatch(r"[0-9\s]{1,80}", p1_uid) else None,
            "pid1_pid_namespace_hint": pid1_pidns, "self_pid_namespace_hint": self_pidns,
            "same_pid_namespace_hint": pid1_pidns == self_pidns if pid1_pidns and self_pidns else None,
            "same_mount_namespace_hint": pid1_mntns == self_mntns if pid1_mntns and self_mntns else None,
            "system_bus_path_hint": bus,
            "system_bus_peer_credentials": "NOT_QUERIED_NO_CONNECTION",
            "pid1_dbus_unique_owner": "NOT_QUERIED_NO_CONNECTION",
            "unit_InvocationID_and_MainPID": "NOT_QUERIED",
            "effective_delegation": "NOT_QUERIED",
            "cgroup_mountinfo_hints": mounts,
            "cgroup_root_controllers_text_state": rb["/sys/fs/cgroup/cgroup.controllers"].get("state"),
            "cgroup_root_subtree_control_text_state": rb["/sys/fs/cgroup/cgroup.subtree_control"].get("state"),
            "held_D_S_C_fds": "NOT_OPENED",
            "fstatfs_statx_ACL": "NOT_PERFORMED",
            "source_authenticated": False,
            "status": "BLOCKED_HOST_PARENT",
        },
        "observer": {
            "btf_vmlinux_file_hint": btf,
            "btf_metadata_present": available(btf) and btf.get("type") == "regular",
            "tracepoint_enter_path_hint": mb["/sys/kernel/tracing/events/raw_syscalls/sys_enter"],
            "tracepoint_exit_path_hint": mb["/sys/kernel/tracing/events/raw_syscalls/sys_exit"],
            "perf_event_paranoid_hint": _sysctl_int(rb, "/proc/sys/kernel/perf_event_paranoid"),
            "unprivileged_bpf_disabled_hint": _sysctl_int(rb, "/proc/sys/kernel/unprivileged_bpf_disabled"),
            "effective_process_capabilities_hex_hint": self_caps,
            "active_lsm_hint": lsm_names,
            "kernel_probe_attachments": "NONE",
            "kernel_fd_inode_resolution": "NOT_ESTABLISHED",
            "postexec_image_credential_seccomp_provenance": "NOT_ESTABLISHED",
            "per_cpu_loss_accounting": "NOT_ESTABLISHED",
            "status": "BLOCKED_OBSERVER",
        },
        "watchdog": {
            "cgroup_kill_path_hint": mb["/sys/fs/cgroup/cgroup.kill"],
            "cgroup_events_path_hint": mb["/sys/fs/cgroup/cgroup.events"],
            "externally_armed_controller": "NOT_IMPLEMENTED",
            "exact_invocation_fd_escrow": "NOT_IMPLEMENTED",
            "supervisor_loss_coverage": "NOT_ESTABLISHED",
            "bounded_drain_and_fsync_proof": "NOT_ESTABLISHED",
            "status": "BLOCKED_WATCHDOG",
        },
        "disposition": UNQUALIFIED,
        "runtime_enforcement_qualified": False,
        "compile_enabled": False,
        "hosted_permission": False,
        "D2_qualified": False,
        "historical_C4": "INCOMPLETE",
        "target_github_runner_identified": False,
        "authority_receipt_created": False,
        "notes": [
            "Captured from current process only; NOT proven to be the target hosted runner",
            "Path/UID/mountinfo/BTF indications are not bus or kernel-event attestations",
            "No memory.events values observed; no historical OOM conclusion",
            "No socket connection, BPF attach, perf_event_open, ELF, systemd or cgroup mutation",
        ],
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--local-observe", action="store_true", help="read only the fixed local hints")
    args = ap.parse_args(argv)
    if not args.local_observe:
        ap.error("explicit --local-observe required; no implicit probes")
    print(json.dumps(capture(), sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
