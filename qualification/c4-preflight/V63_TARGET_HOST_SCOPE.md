# ARGENTUM C4 — V6.3 prepared read-only target-runner feasibility (LOCAL ONLY)

Frozen V6.2 parent **a139ba26be65a5130f55011c8fd021fd9a1c4e28**. Its source and evidence, including the original V6.1 and V6 archives and all prior ELFs/tests/protocols, must remain byte-identical. Historical C4: **INCOMPLETE**. D2: **BLOCKED**.

## Execution authority and scope

The owner said **Proceed** after the recommendation to *prepare* the target-host feasibility candidate, separately review its exact source and seek a source-bound authorization before publishing or dispatching. This checkpoint performs only **offline implementation and synthetic verification**. It is not approval to publish an Actions workflow or let a push activate it. The current package provides a frozen source commit and a separately staged, unpublished workflow-only child, both immutable until new review/disposition.

## What the source can collect if later authorized

- The source has no default run path: explicit `--target-observe` is required; it refuses nonmatching event, repository, workflow ref, run number/attempt, Linux/X64 hosted-runner context and malformed source/ref/run identity **before any host metadata read**. These are GitHub-provided environment claims, **not external host authentication**.
- The only host paths accessed are the already-reviewed fixed whitelists from V6.2 `v62_host_capability.py`, whose SHA-256 is pinned. Both source payloads are SHA-256 checked before invocation in the future workflow.
- One local snapshot returns minimal PID1/D-Bus path, cgroup2 mount count, BTF/tracepoint availability, kernel knobs and cgroup.kill metadata hints. No raw boot ID, cgroup membership text or mount paths appear in the wrapper output. The output always states source not authenticated, kernel not attached and watchdog not armed.
- Source cannot connect system D-Bus, query systemd, attach BPF/perf/ptrace, acquire privileged held D/S/C FDs, authorize cgroup operations, start JVM/Gradle, execute helper ELFs or perform gameplay. Workflow content permissions are read-only; the only artifacts are written under runner.temp and uploaded as evidence if, and only if, a future separately authorized run occurs.
- Any capture or context failure is INCOMPLETE/BLOCKED, never PASS. Even a hypothetical all-green probe remains `BLOCKED_TARGET_CAPABILITY_HINTS_ONLY`. Manager M / Observer O / Watchdog W remain `BLOCKED_HOST_PARENT` / `BLOCKED_OBSERVER` / `BLOCKED_WATCHDOG`; historical C4 is INCOMPLETE, D2 false, hosted permission false, compiler disabled.

## Exact inherited Git tree provenance

The frozen V6.2 outer root includes `.github` SHA `bae579ed3c21564f3cd002adc25f9ff095548d07`, but the nested archive does not contain its raw tree bytes. The exact original `.github` and `.github/workflows` entries were fetched **read-only** from the public repository Git Trees API, and raw Git tree bytes were independently reconstructed and verified against `bae579ed...` and `b226184936b0375dee35100683db5a849657dc26`. These two inherited tree objects are preserved separately in the local package. No repository write or workflow publication occurred.

## Staged, inactive first-attempt workflow child

A separately pinned child is retained **only inside the local package**, with a unique future push-only branch `infra/argentum-c4-v63-readonly-once-20261015`. It is not added to GitHub and has no current Actions workflow. An owner/controller must separately review the exact source commit, staged child commit/blob, archived YAML, branch name, permission model, target runner, expected artifact schema, original-only run_number=1/run_attempt=1 limits, and decide whether to authorize **one** publication/activation. An independent reviewer must assess the same immutable candidate. Absence of either decision means **NO PUBLICATION / NO HOST RUN**. No generic “Proceed” to earlier checkpoints can substitute for exact source-bound release.

A future GitHub run would still require post-run reconciliation of run/event/ref/head, original artifact bytes, source SHA, workflow blob, external identity context, and all negative outcomes. GitHub-generated action metadata and local filesystem hints alone cannot prove PID1 credentials, systemd delegation, kernel-event coverage, external watchdog or C4/D2 readiness.

## Specific feasibility decision still outstanding

- **Manager**: actual system bus SO_PEERCRED and unique PID1 owner exchange, unit/boot/InvocationID, delegation, cgroup/ACL/held D/S/C FDs and MainPID pidfd verified at startup and after races.
- **Observer**: actual non-stopping kernel backend with image, post-exec identity and nine outcome/target-inode predicates, no-loss per-CPU accounting and verified fork/exec/reparent coverage.
- **Watchdog**: separately running authorized agent with exact InvocationID/held-D FD escrow, pre-C/supervisor-death coverage, bounded drain and journal persistence.

These cannot be established by the read-only inventory. If the target proves incompatible, record a host feasibility blocker and recommend an alternative preapproved host instead of weakening evidence gates.
