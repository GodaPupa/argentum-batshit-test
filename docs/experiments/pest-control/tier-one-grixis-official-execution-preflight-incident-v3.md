# Pest Control Tier-1 Grixis — third preflight-only execution incident

GitHub Actions run `35619166656` is permanently retired as a third pre-execution infrastructure
failure.

The run failed in its first guard. Checkout, exact commit/tree verification, frozen-artifact download,
durable execution-envelope creation, official runner execution, and evidence generation were all
skipped. Therefore the official frozen vector remains unread by an execution runner and unconsumed:
zero official games initialized, zero official actions submitted, and zero outcomes exposed.

The v3 guard combined several silent shell assertions, including GitHub run-number metadata. Although
the REST run record reports run number 1 and attempt 1 and the supplied commit/tree/ack values are
correct, the guard exited without identifying which assertion disagreed with the runner environment.
That identity is retired rather than rerun.

## v4 correction

v4 removes the opaque run-number assertion and uses explicit, separately named preflight steps:

1. reject any GitHub rerun by requiring `GITHUB_RUN_ATTEMPT=1`;
2. validate exact acknowledgement;
3. validate commit/tree syntax;
4. checkout and verify the exact commit/tree;
5. verify the frozen artifact before creating any attempt marker.

Only one v4 dispatch is authorized by the research protocol. A second v4 dispatch is prohibited even
though the workflow does not depend on a fragile remote API/run-number assertion to enforce that
research rule. The workflow remains non-rerunnable through the run-attempt check.

Runs `35611002403`, `35614730818`, and `35619166656` are permanently retired and must never be
rerun.
