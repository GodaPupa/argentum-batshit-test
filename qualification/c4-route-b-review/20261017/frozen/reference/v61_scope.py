"""V6.1 source-only PID1/HeldScope contract. No bus connect or privileged entry.

All inputs to the pure verifier are **untrusted readback models**, not capabilities.
A successful synthetic audit NEVER yields a live HeldScope or an enablement token.
The mandatory live transport, FD acquisition and explicit owner authority are
intentionally absent. This module does not perform any host discovery.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Mapping
import re
import stat
from v6_contract import unit_contract, Blocked

_BOOT=re.compile(r"^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$")
_INV=re.compile(r"^[0-9a-f]{32}$")
_REQUIRED_BUS_PROPERTIES=frozenset({"Id", "InvocationID", "ControlGroup",
    "MainPID", "Delegate", "DelegateSubgroup", "KillMode", "SendSIGKILL",
    "RuntimeMaxUSec", "TimeoutStopUSec", "ControlPID", "ActiveState"})
_REQUIRED_MANAGER_METHODS=frozenset({"GetUnit", "GetNameOwner", "GetConnectionUnixProcessID"})

@dataclass(frozen=True)
class ScopeAssessment:
    state: str
    reasons: tuple[str,...]
    fingerprint: tuple = ()
    model_only: bool = True
    runtime_authorized: bool = False
    privileged_fd_issued: bool = False

class _Invalid(ValueError):
    pass

def _need(ok: bool, reason: str):
    if not ok:
        raise _Invalid(reason)

def _pair(v, label):
    _need(type(v) in (list, tuple) and len(v)==2 and
          all(type(i) is int and i>0 for i in v), label)
    return tuple(v)

def _str(v, pattern, reason):
    _need(type(v) is str and pattern.fullmatch(v) is not None, reason)
    return v

def _validate(model: Mapping) -> tuple:
    _need(type(model) is dict, "BLOCKED_HOST_PARENT: model is not a source")
    u=unit_contract();m=model.get("manager");_need(type(m) is dict,"BLOCKED_HOST_PARENT: missing manager")
    # These are expected *bus-issued* facts; a dict alone can never attest them.
    bus=m.get("bus",{}); _need(type(bus) is dict,"BLOCKED_HOST_PARENT: bus")
    _need(bus.get("path")=="/run/dbus/system_bus_socket" and bus.get("uid")==0 and
          bus.get("peer_uid")==0 and type(bus.get("peer_pid")) is int and bus["peer_pid"]>0,
          "BLOCKED_HOST_PARENT: system bus socket and SO_PEERCRED")
    _need(m.get("well_known")=="org.freedesktop.systemd1" and
          type(m.get("unique_owner")) is str and re.fullmatch(r":1\.[0-9]+",m["unique_owner"]) and
          type(m.get("owner_pid")) is int and m["owner_pid"]==1 and
          type(m.get("version")) is int and m["version"]>=u["manager_minimum_version"],
          "BLOCKED_HOST_PARENT: PID1 unique owner/version")
    _need(type(m.get("introspected_properties")) is list and
          _REQUIRED_BUS_PROPERTIES.issubset(m["introspected_properties"]) and
          type(m.get("introspected_methods")) is list and
          _REQUIRED_MANAGER_METHODS.issubset(m["introspected_methods"]),
          "BLOCKED_HOST_PARENT: live introspection absent")
    _need(m.get("before_owner")==m["unique_owner"]==m.get("after_owner") and
          m.get("manager_boot") == model.get("boot"),
          "BLOCKED_HOST_PARENT: manager owner/boot changed")
    boot=_str(model.get("boot"),_BOOT,"BLOCKED_HOST_PARENT: boot")
    inv=_str(model.get("invocation"),_INV,"BLOCKED_HOST_PARENT: InvocationID")
    _need(model.get("invocation_new") is True,"BLOCKED_HOST_PARENT: invocation reused")
    effective=model.get("effective",{}); _need(type(effective) is dict,"BLOCKED_HOST_PARENT: effective unit properties")
    _need(effective.get("Id")==u["unit_name"] and effective.get("InvocationID")==inv and
          effective.get("ControlGroup")==model.get("path") and
          effective.get("DelegatedControllers")==["memory"] and
          effective.get("DelegateSubgroup")=="supervisor" and
          effective.get("KillMode")=="control-group" and
          effective.get("SendSIGKILL") is True and
          effective.get("Restart")=="no" and effective.get("RuntimeMaxSec")==60 and
          effective.get("TimeoutStopSec")==5 and effective.get("ExecHooks")==[],
          "BLOCKED_HOST_PARENT: effective unit/delegation drift")
    p=model.get("process",{});_need(type(p) is dict,"BLOCKED_HOST_PARENT: process")
    pid=_pair(p.get("main_pid_start"),"BLOCKED_HOST_PARENT: MainPID/start")
    _need(effective.get("MainPID")==pid[0] and pid==_pair(p.get("supervisor_pid_start"),"BLOCKED_HOST_PARENT: supervisor PID/start") and
          p.get("pidfd_matches_start") is True and p.get("pidfd_alive") is True and
          p.get("supervisor_in_s") is True,"BLOCKED_HOST_PARENT: MainPID/start/pidfd")
    ns=model.get("namespaces",{});_need(type(ns) is dict,"BLOCKED_HOST_PARENT: namespace")
    _need(all(type(ns.get(k)) is int and ns[k]>0 for k in
              ("host_cgroup","worker_cgroup","host_mnt","worker_mnt","host_user","worker_user")) and
          all(ns["host_"+k]==ns["worker_"+k] for k in ("cgroup","mnt","user")),
          "BLOCKED_HOST_PARENT: mixed namespaces")
    mount=model.get("mount",{});_need(type(mount) is dict,"BLOCKED_HOST_PARENT: cgroup2 mount")
    _need(mount.get("fs")=="cgroup2" and mount.get("root")=="/" and
          mount.get("point")=="/sys/fs/cgroup" and
          type(mount.get("id")) is int and mount["id"]>0 and mount.get("unique") is True,
          "BLOCKED_HOST_PARENT: ambiguous mount/cgroup2")
    path=model.get("path")
    _need(type(path) is str and path.startswith("/system.slice/") and
          path not in ("/system.slice/", "/system.slice/hosted-compute-agent.service") and
          "//" not in path and ".." not in path.split("/") and path.endswith(u["unit_name"]),
          "BLOCKED_HOST_PARENT: guessed/shared D path")
    nodes=model.get("held_fds")
    _need(type(nodes) is dict and set(nodes)=={"D","S","C"},"BLOCKED_HOST_PARENT: D/S/C held FDs")
    identities=[]
    for name,suffix in (("D",""),("S","/supervisor"),("C","/probe")):
        x=nodes[name];_need(type(x) is dict,"BLOCKED_HOST_PARENT: missing FD")
        identity=_pair(x.get("fstat_dev_ino"),"BLOCKED_HOST_PARENT: FD identity")
        _need(identity==_pair(x.get("expected_dev_ino"),"BLOCKED_HOST_PARENT: expected FD") and
              x.get("mount_id")==mount["id"] and x.get("statfs")=="cgroup2" and
              x.get("path")==path+suffix and x.get("uid")==0 and x.get("gid")==0 and
              x.get("mode")==0o755 and x.get("acl")==[] and
              x.get("control_files_root_only") is True and
              x.get("open_directory_no_symlink") is True and x.get("fd_stable") is True,
              "BLOCKED_HOST_PARENT: FD ownership/mode/ACL/identity")
        identities.append(identity)
    _need(len(set(identities))==3,"BLOCKED_HOST_PARENT: aliased D/S/C")
    _need(nodes["D"].get("direct_pids")==[] and
          nodes["S"].get("direct_pids")==[pid[0]] and
          nodes["C"].get("direct_pids")==[] and
          nodes["S"].get("manager_created") is True and
          nodes["C"].get("exclusive_new") is True,
          "BLOCKED_HOST_PARENT: population/creation")
    _need(model.get("memory_available_at_d") is True and
          model.get("memory_enabled_at_d") is True and
          model.get("controller_readback_fresh") is True and
          model.get("ancestor_diff")==[] and model.get("unexpected_control_subtrees")==[] and
          model.get("worker_can_write_ancestor") is False and
          model.get("worker_can_write_d_control") is False,
          "BLOCKED_HOST_PARENT: effective memory/ancestors")
    _need(model.get("source_run_token") is not None and
          type(model["source_run_token"]) is str and len(model["source_run_token"])>=32 and
          model.get("owner_enable_memory_authority") is not True,
          "BLOCKED_HOST_PARENT: source/run token or forbidden implicit authority")
    return (boot,inv,m["unique_owner"],mount["id"],path,*identities,pid,
            ns["host_cgroup"],ns["host_mnt"],ns["host_user"],model["source_run_token"])

def assess_model(model) -> ScopeAssessment:
    """Pure checker; a structurally perfect forgery remains NON-AUTHORITATIVE."""
    try:
        return ScopeAssessment("SYNTHETIC_SCOPE_CONSISTENT",(),_validate(model))
    except (_Invalid,KeyError,TypeError,ValueError) as e:
        return ScopeAssessment("BLOCKED_HOST_PARENT",(str(e) or "BLOCKED_HOST_PARENT: malformed",))

def revalidate_model(before,after) -> ScopeAssessment:
    a,b=assess_model(before),assess_model(after)
    if a.state!= "SYNTHETIC_SCOPE_CONSISTENT":return a
    if b.state!= "SYNTHETIC_SCOPE_CONSISTENT":return b
    if a.fingerprint!=b.fingerprint:
        return ScopeAssessment("BLOCKED_HOST_PARENT",("BLOCKED_HOST_PARENT: held scope drift/reuse",))
    return b

def authenticated_held_scope(*_args,**_kwargs):
    """No operational transport: fake calls, model flags and test fixtures cannot unlock it."""
    raise Blocked("BLOCKED_HOST_PARENT: root-bus credentials, PID1 DBus unique-owner proof, "
                  "effective delegation, host fstatfs/ACL and atomically held FD provenance "
                  "must be established by a separately reviewed privileged transport")

RUNTIME_BACKEND_ENABLED=False
