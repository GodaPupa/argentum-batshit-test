"""Source-local V6.1 M/O/W composition; never an operational gate or a permit.

The public evaluation is diagnostic ONLY. In particular, even a wholly plausible
three-model receipt cannot call V6's unreachable runtime path or unlock compile.
"""
from __future__ import annotations
from v6_contract import Blocked
from v61_scope import assess_model, revalidate_model, authenticated_held_scope
from v61_observer import evaluate_model as assess_observer, require_kernel_backend
from v61_watchdog import evaluate_model as assess_watchdog, termination_implementation

def assess_synthetic_candidate(r):
    """Return distinct provider dispositions without promoting model PASS."""
    if type(r) is not dict:r={}
    pre=r.get("scope_before")
    post=r.get("scope_after")
    manager=revalidate_model(pre,post)
    observer=assess_observer(r.get("observer"))
    watchdog=assess_watchdog(r.get("watchdog"))
    positive = observer.status=="FAIL" or r.get("verified_positive_oom") is True
    # Verified positives fail even if some other interval is missing. The model
    # flag is informational only, never trusted to attest an actual OOM event.
    return {"disposition":"FAIL_MODEL_INDICATOR" if positive else "BLOCKED_V61_RUNTIME",
            "manager":manager.state,"manager_reasons":list(manager.reasons),
            "observer":observer.status,"observer_reasons":list(observer.reasons),
            "watchdog":watchdog.status,"watchdog_reasons":list(watchdog.reasons),
            "model_only":True,"runtime_enforcement_qualified":False,
            "compiler_enabled":False,"hosted_permission":False,"historical_C4":"INCOMPLETE",
            "authorization_consumed":False}

def release_worker(*_args,**_kwargs):
    # Independent runtime authority must be outside this source package and
    # MUST be checked before even attempting to acquire/arm these providers.
    # All four stops below are independent fail-closed barriers.
    raise Blocked("BLOCKED_V61_RUNTIME: owner authority and M/O/W operational "
                  "capabilities are absent; worker release is hard-disabled")

def compile_request(*_args,**_kwargs):
    raise Blocked("C4 compilation DISABLED: C4 historical original INCOMPLETE")

RUNTIME_GATE_ENABLED=False
