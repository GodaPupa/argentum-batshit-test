# Route B integration successor — local candidate, not deployable

Disposition: PARTIAL_INTEGRATION_FROZEN__HOST_BINDING_AND_PRODUCTION_INTEGRATION_BLOCKED.

This successor contains actual source and focused tests. It is not a fully operational backend and does not qualify Manager, Observer or Watchdog. No independent acceptance is claimed. Frozen reference sources and five original runtime barriers remain unchanged. Live candidate constructors still reject execution; removing those barriers is not an authorized deployment method.

## Implemented delta

- `src/integration.py`: same-held-FD installed-image hashing and drift checks; one-attempt scope establishment ordering; fixed D-relative controller/C creation source behind the barrier; coordinator joining manager, scope, observer and watchdog preparation without worker release.
- `src/external_service.py`: real Unix SOCK_SEQPACKET credential checks and SCM_RIGHTS sender/receiver with binding, sequence and FD-count validation; descriptor-driven bounded watchdog loop; polling adapter behind barrier. The live service entry point remains deliberately sealed.
- `src/observer_lifecycle.py`: attachment-to-drain sequencing, complete expected-CPU loss-map checks, fail-closed hotplug response, durable flush/detach ordering. Injected provider responses are synthetic, not authenticated kernel observations.
- `src/watchdog.py`: a journal failure after a returned kill no longer suppresses bounded drain; result remains INCOMPLETE_EVIDENCE.

## Tests and limits

60/60 tests pass. Eight inherited and eight added weakening mutations are detected. Original 38 tests are reused within the successor suite. New tests include actual local socketpair FD transfer, wrong peer, replay, changed binding, image content/permissions, failed scope setup, supervisor death, loss, drain and post-kill evidence failure. Other backend operations use synthetic providers and temporary files. No cgroup control write, BPF load, service installation, runtime enforcement or compiler was executed.

The initial added mutation run detected 7/8: initial image-hash removal survived. A wrong-initial-hash test was added; final 8/8 passed. Both logs are preserved. No test establishes independent watchdog survival or authenticates kernel events.

## Target observations

Original bounded nonprivileged Codespace observation is preserved at `evidence/PSYCHIC_LAMP_READONLY_BINDING_1724.json`; SHA-256 d93661cd3f4dc92991d996187cd297ea79b5c28b4109d32ede3366b3e8f381f8. Timestamp 2026-10-17T00:26:32.604141+00:00. Codespace psychic-lamp-vpq9g5gjpqxv2xj94.

Visible PID1 is docker-init, UID1000; system bus socket ENOENT; self effective capabilities zero. Kernel 6.8.0-1064-azure/x86_64; BTF 6239943 bytes, SHA-256 e9eef569352fea1175f58f3e28e28573948d3bb13e975d126451d30432837521. Cgroup2 mount is visible with memory controller. These facts do not establish delegation or authority to mutate it. BPF unprivileged-disabled=2; perf_event_paranoid=4; inspected raw-syscall event paths absent. No attach attempt was made.

The systemctl command is a container wrapper reporting systemd is not running. dpkg reports installed systemd 255.4-1ubuntu8.17 and libbpf1 1:1.3.0-2build2; python3-dbus-next package was not found. Installed package metadata does not prove a usable PID1 service, D-Bus ABI, Python module absence, or exact runtime library bytes.

The underlying host has NOT been disqualified. No host PID1 credentials, delegated unit, verified normal host mount view, privileged attachment rights or independent external watchdog were obtained. An operator-controlled host connection is required to establish those facts.

## Remaining implementation and authority gaps

1. Manager: trusted supervisor/launcher implementation and installation identity, audited D-Bus dependency artifact, validated target ABI, concrete production scope backend joining held-D writes to authenticated upstream/controller topology. FixedDWriter is not a complete provenance-verifying broker by itself. It must not be instantiated by bypassing its barrier.
2. Observer: actual target BPF producer and authenticated loader, reviewed hook set, kernel map/link provenance, image/inode/credential/seccomp event semantics and inherited model receipt adapter. A BTF hash alone is insufficient to invent these. JSON samples remain a candidate ABI, not a kernel format.
3. Watchdog: independently installed external launcher/service, sealed descriptor table and pidfd/source/run binding, exact-invocation inactivity adapter, end-to-end failure survival. The event loop accepts injected dependencies and is not a production trust boundary. Backend errors fail closed; automatic kill retries are forbidden. Absolute drain deadline is never extended; late termination can legitimately end FAILED.
4. Dependency artifacts are not downloaded or hash-locked; deployment dependency closure remains incomplete. Target metadata only is in dependency_status.json.

These include unfinished production glue, not only unavailable host facts. This package is reviewable as a partial changed-source candidate, not as a completed operational acceptance submission.

## Reproduction

Python 3 standard library only for local tests; no build or package installation:

```
python3 -B freeze.py --check
python3 -B -m unittest discover -s tests -v
python3 -B mutate.py
python3 -B mutate_integration.py
```

`source.patch` is a unified source/test delta relative to the frozen parent. SOURCE_INVENTORY.json pins each source file. SHA256SUMS covers every packaged file except itself. The original input ZIP is included under inputs and verified unchanged. Fresh extraction validation is recorded separately. No Git commit or remote write was made.

C4 INCOMPLETE. D2 BLOCKED. No gameplay authority. Decision A/B originals remain consumed and unchanged.
