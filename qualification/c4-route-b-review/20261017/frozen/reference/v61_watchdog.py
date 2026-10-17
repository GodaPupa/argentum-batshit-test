"""V6.1 exact-InvocationID *external* watchdog protocol, source-only.

No service, process, pidfd, unit or cgroup is accessed here. The only proposed
termination mechanism is via an authenticated held D directory descriptor,
NOT a StopUnit(name) call or kill(numeric PID). Actual external service control,
root FD escrow, lease loss and kernel finalization are unimplemented blockers.
"""
from __future__ import annotations
from dataclasses import dataclass
from v6_contract import Blocked

TRIGGERS=frozenset({"SUPERVISOR_DEATH","PRE_C_JOIN_FAILURE","PRE_PIDFD_FAILURE",
                    "HEARTBEAT_TIMEOUT","OBSERVER_LOSS","ABSOLUTE_DEADLINE", "EXPLICIT_CANCEL"})
STAGES=("REGISTERED","ARMED","RELEASED","TRIGGERED","SCOPE_REVALIDATED",
        "D_FD_KILL","DRAINED","UNIT_INACTIVE","JOURNAL_FSYNC")

@dataclass(frozen=True)
class WatchdogAssessment:
    status: str
    reasons: tuple[str,...]
    model_only: bool = True
    scope_terminated: bool = False
    runtime_qualified: bool = False


def _pair(v):return type(v) in (tuple,list) and len(v)==2 and all(type(x) is int and x>0 for x in v)

def _evaluate_model(r)->WatchdogAssessment:
    reasons=[]
    if type(r) is not dict:
        return WatchdogAssessment("BLOCKED_WATCHDOG",("missing externally controlled watchdog",))
    bind=r.get("binding",{})
    if type(bind) is not dict or any(type(bind.get(k)) is not str or not bind[k] for k in ("boot","invocation","unit","run")) or not _pair(bind.get("d")) or not _pair(bind.get("s")):
        return WatchdogAssessment("BLOCKED_WATCHDOG",("invalid exact unit/invocation/held scope",))
    events=r.get("events",[])
    if type(events) is not list:events=[]
    # Evidence of a failed termination is never replaced with a model success.
    if r.get("kill_failed") is True or r.get("cleanup_timeout") is True or r.get("journal_corrupt") is True:
        reasons.append("termination/drain/journal failed")
    if (r.get("controller_outside_dsc") is not True or
        r.get("external_controller_alive_after_supervisor_death") is not True or
        r.get("controller_root_owned") is not True or
        r.get("held_dfd_broker_attested") is not True):
        reasons.append("watchdog not independent/root-controlled")
    if r.get("supervisor_pidfd_start_verified") is not True and r.get("trigger")!="PRE_PIDFD_FAILURE":
        reasons.append("supervisor pidfd identity unverified")
    if r.get("trigger") not in TRIGGERS:
        reasons.append("no supported independent failure trigger")
    if r.get("pre_c_join_covered") is not True or r.get("pre_pidfd_window_covered") is not True:
        reasons.append("pre-C/pre-pidfd missing watchdog coverage")
    if (r.get("stop_method")!="held-D-FD-cgroup.kill" or
        r.get("numeric_pid_kill") is not False or r.get("StopUnit_by_name") is not False):
        reasons.append("unsafe termination fallback")
    if (r.get("fresh_invocation_before_stop")!=bind["invocation"] or
        r.get("fresh_boot_before_stop")!=bind["boot"] or
        r.get("unit_name_before_stop")!=bind["unit"] or
        r.get("fresh_dfd_id_before_stop")!=bind["d"] or
        r.get("no_unit_name_reuse_window") is not True):
        reasons.append("stale/reused/ambiguous unit invocation or D FD")
    if (r.get("fd_kill_target")!=bind["d"] or
        r.get("d_same_fd_at_kill") is not True or
        r.get("all_descendants_drained") is not True or
        r.get("unit_manager_inactive_same_invocation") is not True or
        r.get("poststop_event_source")!="independent-held-D-cgroup.events"):
        reasons.append("no exact-D termination/drain evidence")
    deadline=r.get("bounds",{})
    if (type(deadline) is not dict or any(type(deadline.get(k)) is not int for k in
         ("armed_ns","released_ns","triggered_ns","kill_ns","drained_ns","final_ns","absolute_ns")) or
        not (deadline["armed_ns"]<deadline["released_ns"]<=deadline["triggered_ns"]<=
             deadline["kill_ns"]<=deadline["drained_ns"]<=deadline["final_ns"]<=deadline["absolute_ns"])):
        reasons.append("unbounded/invalid cleanup deadline")
    if [e.get("stage") for e in events if type(e) is dict]!=list(STAGES):
        reasons.append("incomplete watchdog lifecycle")
    else:
        prev=-1
        for e in events:
            if (type(e) is not dict or e.get("binding")!=bind or
                e.get("origin")!="external-watchdog-attested" or
                type(e.get("t_ns")) is not int or e["t_ns"]<=prev):
                reasons.append("watchdog lifecycle origin/time mismatch");break
            prev=e["t_ns"]
        if (type(deadline) is dict and type(deadline.get("armed_ns")) is int and
            (events[1]["t_ns"]>deadline["released_ns"] or events[-1]["t_ns"]>deadline["absolute_ns"])):
            reasons.append("watchdog armed late or completed after deadline")
    if (r.get("journal_fsync_verified") is not True or
        r.get("stop_receipt_scope")!=bind or r.get("observer_loss_logged") is not True):
        reasons.append("durable failure/cleanup receipt absent")
    return WatchdogAssessment("INCOMPLETE_CLEANUP" if reasons else "SYNTHETIC_TERMINATION_SEQUENCE_CONSISTENT",
                              tuple(sorted(set(reasons))))


def evaluate_model(r)->WatchdogAssessment:
    try:
        return _evaluate_model(r)
    except (TypeError,ValueError,KeyError,IndexError,AttributeError,OverflowError):
        return WatchdogAssessment("INCOMPLETE_CLEANUP",("malformed watchdog event/receipt",))


def termination_implementation(*_args,**_kwargs):
    """Never issue systemd StopUnit, cgroup.kill or PID signal in source-only mode."""
    raise Blocked("BLOCKED_WATCHDOG: no externally deployed watchdog, "
                  "authenticated held-D FD escrow, exact InvocationID race-free revalidation, "
                  "pre-C/pre-pidfd coverage or verified bounded teardown receipt")

RUNTIME_WATCHDOG_ENABLED=False
