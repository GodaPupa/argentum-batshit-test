"""V6.1 scoped non-stopping kernel-observer *evidence contract*, source-only.

A tracepoint/syscall stream alone cannot prove FD-target inode or immutable exec
bytes. These must be supplied by an independently authenticated kernel-backed
probe; no such probe is installed, loaded, or run here. No ptrace PASS fallback.
A consistent fixture returns SYNTHETIC_WITNESS_CONSISTENT, never runtime PASS.
"""
from __future__ import annotations
from dataclasses import dataclass
from v6_contract import DENIALS, Blocked

# A narrow backend must trace file-open/write for migration, and entry+return
# for these precise ABI calls. "unix_socket" is socket(AF_UNIX); "socketpair"
# likewise requires AF_UNIX verification, not just syscall number.
SYSCALLS={"migration":("openat","write"), "setuid":("setresuid",),
          "setgid":("setresgid",), "capset":("capset",),
          "unshare":("unshare",), "setns":("setns",),
          "mount":("mount",), "unix_socket":("socket",),
          "unix_socketpair":("socketpair",)}
REQUIRED_EVENTS=frozenset({"fork","vfork","clone","exec","exit","reparent",
                           "cgroup_attach","syscall_enter","syscall_exit"})

@dataclass(frozen=True)
class ObserverAssessment:
    status: str
    reasons: tuple[str,...]
    model_only: bool = True
    kernel_backend_attested: bool = False
    runtime_qualified: bool = False


def _good_identity(x):
    return type(x) in (tuple,list) and len(x)==2 and all(type(t) is int and t>0 for t in x)

def _is_event(event,binding):
    return (type(event) is dict and event.get("binding")==binding and
            event.get("source")=="independent-kernel-channel" and
            type(event.get("seq")) is int and type(event.get("t_ns")) is int and
            type(event.get("cpu")) is int and event["t_ns"]>=0 and event["cpu"]>=0)

def _evaluate_model(r) -> ObserverAssessment:
    """Check completeness/contradiction; even a perfect caller fixture is untrusted."""
    reasons=[];positives=[]
    if type(r) is not dict:
        return ObserverAssessment("BLOCKED_OBSERVER",("BLOCKED_OBSERVER: stream absent",))
    bind=r.get("binding",{})
    required=("boot","invocation","run","source_sha256","image_sha256","d","s","c")
    if type(bind) is not dict or any(type(bind.get(k)) is not str or not bind[k] for k in required[:5]) or any(not _good_identity(bind.get(k)) for k in required[5:]):
        return ObserverAssessment("BLOCKED_OBSERVER",("BLOCKED_OBSERVER: invalid exact binding",))
    if len({tuple(bind[k]) for k in ("d","s","c")})!=3:
        return ObserverAssessment("BLOCKED_OBSERVER",("BLOCKED_OBSERVER: D/S/C alias",))
    events=r.get("records",[])
    if type(events) is not list:events=[]
    # Fail-positive precedence: an independently attested fatal, unexpected
    # success/escape or positive scoped OOM can never be neutralized by a gap.
    for e in events:
        if not _is_event(e,bind):continue
        if e.get("kind") in ("escape","fatal"):
            positives.append("kernel escape/fatal")
        if e.get("kind")=="syscall_exit" and e.get("case") in DENIALS and type(e.get("raw_ret")) is int and e["raw_ret"]>=0:
            positives.append("forbidden syscall succeeded: "+e["case"])
    for x in r.get("oom_samples",[]) if type(r.get("oom_samples")) is list else []:
        if (type(x) is dict and x.get("binding")==bind and
            x.get("source")=="independent-held-memory.events" and
            any(type(x.get(k)) is int and x[k]>0 for k in ("oom","oom_kill"))):
            positives.append("positive scoped OOM")
    if positives:
        return ObserverAssessment("FAIL",tuple(sorted(set(positives))))

    provider=r.get("provider",{});provider=provider if type(provider) is dict else {}
    required_methods={"tracepoint/fentry", "syscall_entry_exit", "fork_exec_exit",
        "reparent", "cgroup_move", "fd_target_inode", "pidfd_start",
        "exec_image_fd", "privilege_seccomp", "loss_counters"}
    if (provider.get("stopping") is not False or provider.get("uses_ptrace") is not False or
        type(provider.get("capabilities")) is not list or not required_methods.issubset(provider["capabilities"])):
        reasons.append("non-stopping, complete scoped source not demonstrated")
    if (provider.get("attach_success") is not True or provider.get("probe_pinned") is not True or
        provider.get("source_verified") is not True or provider.get("observer_alive_through_drain") is not True):
        reasons.append("attach/pin/observer lifecycle absent")
    cpus=r.get("cpus")
    if type(cpus) is not list or not cpus or any(type(x) is not dict for x in cpus):
        reasons.append("CPU coverage unavailable")
    else:
        cpuids=[x.get("id") for x in cpus]
        if len(cpuids)!=len(set(cpuids)) or type(provider.get("online_cpus")) is not list or sorted(cpuids)!=sorted(provider["online_cpus"]):
            reasons.append("CPU set coverage mismatch")
        for c in cpus:
            if any(type(c.get(k)) is not int or c[k]<0 for k in ("id","lost","overrun","seq_gaps","attach_ns","detach_ns")) or c.get("lost")!=0 or c.get("overrun")!=0 or c.get("seq_gaps")!=0:
                reasons.append("CPU drop/overrun/invalid counters");break
    ts=r.get("times",{});ts=ts if type(ts) is dict else {}
    if (any(type(ts.get(k)) is not int for k in ("armed","released","last_event","drained","detached")) or
        not (ts.get("armed",-1)<ts.get("released",-1)<=ts.get("last_event",-1)<=ts.get("drained",-1)<=ts.get("detached",-1))):
        reasons.append("uncovered release-to-drain interval")
    if (not cpus or any(type(c) is not dict or c.get("attach_ns",10**40)>ts.get("armed",-1) or
                             c.get("detach_ns",-1)<ts.get("detached",-1) for c in cpus)):
        reasons.append("per-CPU coverage interval insufficient")
    if (r.get("coverage_gaps")!=[] or r.get("unknown_processes")!=[] or
        r.get("unpaired_syscalls")!=[] or r.get("helper_only_proof") is True):
        reasons.append("gaps, unknown processes, or helper-only evidence")
    if not events or any(not _is_event(e,bind) for e in events):
        reasons.append("missing/unauthenticated kernel records")
    else:
        seq=[e["seq"] for e in events]
        if seq!=list(range(seq[0],seq[0]+len(seq))) or any(events[i]["t_ns"]>events[i+1]["t_ns"] for i in range(len(events)-1)):
            reasons.append("sequence gap/order")
        if any(e["cpu"] not in (provider.get("online_cpus") or []) for e in events):
            reasons.append("event on unobserved CPU")
    owned=r.get("owned_processes")
    if type(owned) is not list or not owned or any(not _good_identity(p) for p in owned) or len({tuple(p) for p in owned})!=len(owned):
        reasons.append("unbound descendant PID/start/pidfd")
        owned=[]
    if r.get("all_pidfds_start_verified") is not True or r.get("all_descendants_accounted") is not True:
        reasons.append("pidfd/descendant graph incomplete")
    observed_types={e.get("kind") for e in events if type(e) is dict}
    if not {"fork","exec","exit","reparent","cgroup_attach","syscall_enter","syscall_exit"}.issubset(observed_types):
        reasons.append("missing process/lifecycle events")
    if (r.get("fork_graph_verified") is not True or r.get("reparent_source_verified") is not True or
        r.get("persistent_descendant_alive_pre_kill") is not True or
        r.get("postexec_proof_source")!="independent-kernel-backed-image-fd" or
        r.get("postexec_image_sha256")!=bind["image_sha256"] or
        r.get("postexec_privilege_verified") is not True or r.get("postexec_seccomp_filter_verified") is not True):
        reasons.append("process/image/privilege/retention proof absent")
    # Pair syscall entry/exit on a unique per-task cookie and exact case;
    # migration openat failure MUST resolve cgroup.procs relative to held S,
    # not D, not a string derived from helper output. Socket family = AF_UNIX.
    for case in DENIALS:
        enter=[e for e in events if type(e) is dict and e.get("kind")=="syscall_enter" and e.get("case")==case]
        exit=[e for e in events if type(e) is dict and e.get("kind")=="syscall_exit" and e.get("case")==case]
        if len(enter)!=1 or len(exit)!=1:
            reasons.append("missing/duplicate pair "+case);continue
        a,b=enter[0],exit[0]
        if (a.get("syscall") not in SYSCALLS[case] or b.get("syscall")!=a.get("syscall") or
            not _good_identity(a.get("task")) or list(a["task"]) not in owned or
            b.get("task")!=a["task"] or b.get("cookie")!=a.get("cookie") or
            type(a.get("cookie")) is not int or a["cookie"]<=0 or
            not a["t_ns"]<b["t_ns"] or a["seq"]>=b["seq"] or
            a.get("pidfd_start_verified") is not True or b.get("pidfd_start_verified") is not True):
            reasons.append("enter/exit identity or pairing "+case)
        raw=b.get("raw_ret")
        if type(raw) is not int or raw not in (-1,-13) or b.get("errno") != -raw:
            reasons.append("ambiguous syscall denial "+case)
        if (case=="migration" and (a.get("object_source")!="kernel-resolved-file-inode" or
             a.get("target") != bind["s"] or a.get("filename")!="cgroup.procs" or
             a.get("target_role")!="S" or
             b.get("membership_before")!=bind["c"] or b.get("membership_after")!=bind["c"])):
            reasons.append("wrong/missing independently resolved migration target S")
        if case in ("unix_socket","unix_socketpair") and a.get("domain")!="AF_UNIX":
            reasons.append("socket family unverified "+case)
    return ObserverAssessment("INCOMPLETE_OBSERVATION" if reasons else "SYNTHETIC_WITNESS_CONSISTENT",tuple(sorted(set(reasons))))


def evaluate_model(r) -> ObserverAssessment:
    try:
        return _evaluate_model(r)
    except (TypeError,ValueError,KeyError,IndexError,AttributeError,OverflowError):
        return ObserverAssessment("INCOMPLETE_OBSERVATION",("malformed kernel event/provider evidence",))


def require_kernel_backend(*args,**kwargs):
    """No BPF/perf/audit/ptrace attach from a source-only package, even with a mock PASS."""
    raise Blocked("BLOCKED_OBSERVER: need actual host BTF/tracepoint+LSM hooks, "
                  "permission and ring-loss capability evidence; syscall tracepoints "
                  "alone do not resolve S inode and postexec image/creds/seccomp; "
                  "no independently reviewed non-stopping collector is installed")

RUNTIME_OBSERVER_ENABLED=False
