# ARGENTUM C4 V6.2 — local capability inventory / no runtime authorization

## Scope and source identity

This is an additive source-only local successor to V6.1 commit
`62d8da860a99c69b2f01d4f0024caef92a5e4b03` (tree
`c26ee2a7c7e2a7efec21a4a2176960f01b22f9f8`). Frozen V6.1 and its
V6/V5 predecessors, ELFs, source, unit contracts, tests, collector, and
historical experimental evidence are untouched. The user's supplied Reviewer B
text is SOURCE-BOUND TECHNICAL FEEDBACK, not an authenticated or operational
approval. No reviewer authority is transferred by this local addition.

The added `v62_host_capability.py` only reads fixed, bounded local `/proc` and
`/sys` pseudo-files and fixed-file metadata, and reports an **unattested
local-process diagnostic**. It is not a systemd transport, held-FD provider,
kernel-event observer, watchdog, C4 preflight, authority token, or gate. It
requires `--local-observe`, never executes an ELF/compiler or attaches a probe,
and does not open a D-Bus socket, signal a PID, or mutate systemd/cgroups.
`test_v62_host_capability.py` is a host-independent focused fixture suite.

## Observations, not acceptance

Observed in the current **tool sandbox** only (NOT the intended hosted C4
runner): kernel release hint `6.18.44`, PID1 comm `supervisord`, unavailable
`/run/dbus/system_bus_socket`, one syntactically parsed cgroup2 mount at
`/sys/fs/cgroup`, missing `/sys/kernel/btf/vmlinux` and raw syscall tracing
metadata paths, `perf_event_paranoid=2`, and metadata existence for
`/sys/fs/cgroup/cgroup.kill`. These are untrusted hints. Neither root uid nor
presence of `cgroup.kill` proves delegated control, and `unprivileged_bpf_disabled`
value 0 does not prove BPF/LSM attach permission or event coverage.

The diagnostic's local host JSON contains NO observed runtime memory-event
sequence, no systemd peer credentials, no PID1/InvocationID binding, no unit
properties, no held D/S/C FDs, no statx/fstatfs or ACL confirmation, no
kernel-event provenance or lost-event counters, and no independent watchdog.
The kernel's process identity cannot be projected onto GitHub-hosted runners.

Manager (M): `BLOCKED_HOST_PARENT`. Requires a **separately authorized** actual
system-bus/PID1 credential/unique-owner exchange, unit/boot/InvocationID /
MainPID/pidfd binding, verified effective delegation, and held D/S/C FD source
and identity checks. Present model inputs cannot discharge this condition.

Observer (O): `BLOCKED_OBSERVER`. Needs a verified non-stopping kernel backend
with independent fork/exec/reparent and all forbidden syscall outcomes,
reliable post-exec image/credentials/seccomp, S target resolution on openat
including failures, and accounted zero event loss. No BPF/perf/LSM/tracepoint
backend is attached, and metadata alone cannot substitute.

Watchdog (W): `BLOCKED_WATCHDOG`. Needs a live externally owned exact unit +
boot + InvocationID controller, held directory FD escrow, supervisor-death and
pre-C/pidfd failure handling, journal persistence and bounded drain checks.
Metadata about `cgroup.kill` cannot establish any of those guarantees.

**FINAL DISPOSITION: BLOCKED_CAPABILITY_ONLY.** Historical original C4 run
`38780475153` is still INCOMPLETE. D2 remains BLOCKED. Compiler/runtime gate
hard-disables in unchanged V6.1 and V6 are not altered. No GitHub publication,
Actions dispatch, sudo, systemd/cgroup mutation, privileged operation, ELF,
R5/R6 compilation, differential test, G11 or gameplay is authorized or performed.

## Completed local work

- Archived original V6.1 package SHA-256:
  `1d037f15f1a148876f5d26e07cc52e516dee9204defef92f7bf2cec84eb6baee`.
- Verified 85/85 nested V6.1 package SHA256SUMS entries and **48/48** inherited
  source files byte-identical.
- Reran exactly the new scoped host-free tests: **18/18 PASS** via Python `-I -B`.
- Recorded one fixed-read, side-effect-free current-sandbox host hint snapshot
  under `EVIDENCE/LOCAL_SANDBOX_HOST_CAPABILITIES.json`.
- Did not repeat unrelated V6/V6.1 or historical C4 tests.

## Next independently verifiable step, not authority

Only after a new explicit scope/authority decision should the exact target
host be inspected for live PID1 transport, cgroup delegation and kernel hook
capabilities. Such a probe must bind its source, host, boot, invocation, and
original evidence with exact proofs and be independently reviewed. Separate
review and authorization remain required for any active provider, privileged
probe, compilation, or trial. No existing fixture unlocks these steps.
