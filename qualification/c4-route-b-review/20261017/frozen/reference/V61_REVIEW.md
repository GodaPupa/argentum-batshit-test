# ARGENTUM C4 — Frozen V6.1 local-only source completion

Parent (exact, local unpublished V6): `54bfecc37e206ab034b7b7f14484876830b66a71`.
Root parent tree: `cf8a53e8e90043ee99e84ea072a8a86ce0654865`.
Original V6 outer ZIP SHA-256: `07de3f39873467fa76639da62bf55b7a633e8c449d903ea8c501a30ea285ae59`.
Existing V5/V6 source, both ELFs, helper build manifests, test files, V6 review and units stay byte-identical. All other repository subtree object IDs remain byte-identical to V6. No global tree checkout required; sparse Git tree construction preserves unrelated original IDs.

## Distinct source-only workstreams

**M manager:** `v61_scope.py` strictly validates a synthetic transport/readback model: system-bus peer, stable PID1 unique-owner/boot, introspection, exact effective unit/InvocationID/ControlGroup and delegation, MainPID/start/pidfd, same namespace, unambiguous cgroup2 mount, D/S/C inode/mode/root ACL, zero internal D processes, held-FD drift, no upstream changes. `assess_model` and `revalidate_model` are diagnostics, never authority. `authenticated_held_scope` hard-refuses. No operational system-bus client or actual held FD source is supplied.

**O observer:** `v61_observer.py` models one non-stopping narrowly scoped stream with per-CPU loss accounting, explicit release-to-drain coverage, fork/exec/reparent and child identity, kernel-backed postexec image/credential/seccomp prerequisites and independently paired nine negative syscall outcomes including **S** inode proof for cgroup migration. Helper JSON cannot make a missing event present. `require_kernel_backend` refuses because actual host BTF/LSM/perf capabilities, trustworthy image/FD provenance, verified zero-loss attach and data-plane implementation are absent. No ptrace substitute.

**W watchdog:** `v61_watchdog.py` verifies an externally armed, persistent exact unit/boot/InvocationID model, pre-C/pre-pidfd failure coverage, supervisor pidfd, held D cgroup kill/drain, time bounds, journal persistence, no StopUnit-by-name/PID fallback, and invocation reuse rejection. `termination_implementation` hard-refuses. No root-owned external agent or FD broker exists.

**Integrated gate:** `v61_gate.py` composes diagnostic M/O/W statuses, preserves model-positive OOM/escape as a model failure signal, returns no privileged authority, and always refuses `release_worker` and `compile_request`. V6's separate `v6_adapter.supervise_fixed_v6` and compiler barriers remain byte-identical and hard-disabled.

`V61_HOST_DEPENDENCY_BLOCKERS.md` lists exact missing host capabilities. The three verifiers deliberately do NOT implement live systemd calls, install perf/BPF hooks, issue cgroup.kill, open a privileged source, execute a helper ELF, or claim hardware/runtime evidence. Treat all synthetic model statuses as nonqualifying and caller-forgeable. M, O, W are **source contracts only** and remain `BLOCKED_HOST_PARENT`, `BLOCKED_OBSERVER`, `BLOCKED_WATCHDOG` for runtime.

## Source-local checks and mutation scope

The first source-local run contained a Python syntax error in the newly authored watchdog test import; corrected before accepted evidence. The initial focused run then passed 31/32 and found a fixture's unsafe import of the frozen runner-bound preflight; the final test statically hashes that unchanged preflight instead. `test_v61_source_contracts.py` has 32 deterministic tests (no host invocation). `v61_mutation_runner.py` uses isolated temporary copies to deliberately weaken 8 trust checks, and the relevant focused tests detect all 8; no source file in the frozen candidate is changed by mutant execution. Failures and previous test logs are retained in the new package.

## Outcomes and strict restrictions

- `BLOCKED_HOST_PARENT=true`; `BLOCKED_OBSERVER=true`; `BLOCKED_WATCHDOG=true`.
- `runtime_enforcement_qualified=false`; `compiler_enabled=false`; `hosted_permission=false`.
- **Historical C4 run 38780475153 remains INCOMPLETE**. Later discovery/host metadata originals prove no historic zero-OOM state, and V6.1 creates no such evidence.
- No new official entropy/seed/claim/gameplay; no R5/R6 compile, hosted workflow, privileged helper launch, systemd or cgroup mutation, or GitHub publication.
- Require a **substantively independent non-author Reviewer B source review**, followed by separate owner host-enforcement authority and separately qualified backend code before any runtime activity.
