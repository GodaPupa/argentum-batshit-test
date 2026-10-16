# ARGENTUM C4 — V6.2 read-only capability discovery, LOCAL ONLY

Parent: frozen local V6.1 commit `62d8da860a99c69b2f01d4f0024caef92a5e4b03`.
Historical C4 original `38780475153` remains **INCOMPLETE**; no historical memory-event evidence is reconstructed. V6 and V6.1 original source, ELF artifacts, tests, units and review files remain byte-identical.

## Exact step executed

One local, unprivileged capability inventory was executed inside the **ChatGPT/container sandbox**, not the GitHub Actions target and not a dedicated host. This environment is NOT certified as the target runner and cannot supply source-bound target-host authority. The probe uses Python standard library and fixed read-only `/proc`, `/sys` and `/run` metadata paths. It does not connect to a socket, invoke systemctl, query D-Bus, open privileged held D/S/C FDs, execute an ELF, attach BPF/ptrace, start a process, write cgroup files, kill a PID, start Gradle, or dispatch Actions.

Probe execution requires explicit `--local-observe`. Invocation without it performs no inspection and fails. The output excludes raw boot ID, raw cgroup membership strings, command lines, environment and credentials. It records only a SHA-256 hint of boot ID. No reported value creates a token or permits an action. Even a hypothetical all-green diagnostic is **BLOCKED_CAPABILITY_ONLY**.

### What was observed in this sandbox (not a target runner)

- Kernel release hint: see archived local JSON (observed Linux `6.18.44`, `x86_64`). This is not an authenticated target kernel fingerprint.
- `/proc/1/comm` returned `supervisord`, **not** systemd PID1.
- `/run/dbus/system_bus_socket` was absent. No peer credentials, unique systemd1 owner, manager methods or effective unit/delegation properties could be authenticated.
- `/proc/self/mountinfo` exposed one cgroup2 mount hint at `/sys/fs/cgroup`; the local `cgroup.kill` file exists. Neither fact establishes D/S/C held directory authority or cleanup permission.
- `/sys/kernel/btf/vmlinux` and inspected raw syscall tracepoint path hints were unavailable; no attachment, provenance, loss coverage, post-exec image or target-inode evidence exists.
- No root-owned externally surviving watchdog is implemented, armed or independently attested.

## Three source-bound blocker decisions

**Manager M — `BLOCKED_HOST_PARENT`.** This sandbox lacks the operational PID1/systemd bus endpoint. File existence/proc identity is insufficient even on a different host where it exists. The next target-host review needs system-bus SO_PEERCRED + stable unique owner/PID1 mapping, boot/InvocationID, effective Delegate/subgroup/controllers, independently checked D/S/C mount and ACL/held FDs, MainPID start/pidfd, and pre/post changes to authority. NO authority in this package.

**Observer O — `BLOCKED_OBSERVER`.** The sandbox has no visible kernel BTF or raw syscall tracepoint path. BTF *presence*, even on another host, would still not establish a non-stopping, narrow kernel stream, nine forbidden syscall postconditions, verified S inode resolution, image/creds/seccomp provenance, descendant continuity or per-CPU loss accounting. No collector or kernel hooks are attached.

**Watchdog W — `BLOCKED_WATCHDOG`.** A visible `cgroup.kill` path is not an external control process or held-D FD. No externally surviving PID1/unit/InvocationID-bound agent, pre-C/pre-pidfd failure coverage, durable journal, drain proof or race-free name/PID-reuse prevention is installed. NO kill or service mutation is performed.

## Strict outcomes and outstanding target-host question

`runtime_enforcement_qualified=false`; `compile_enabled=false`; `hosted_permission=false`; `D2_qualified=false`; historical `C4=INCOMPLETE`. All V6/V6.1 blockers remain. This is a capability inventory and negative host-context determination **only**.

To determine actual GitHub runner feasibility, a separately scoped owner decision must select and authorize a target-specific, read-only collection and establish its source/runner identity. This local sandbox result cannot prove or disprove capabilities on the hosted runner. A target probe alone also cannot replace independent source review and implementation of M/O/W. Do not dispatch anything based on this package's source-only evidence.
