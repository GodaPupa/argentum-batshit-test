# Manual Transmission: linter follow-up and host findings

## Outcome

The authorized five-line generic `SELF_ALTERNATIVE` linter repair and eight regression cases are frozen in source commit `82ec335573572edfd2a2e4d734d2f0d81801139f`. The new complete qualification attempt finished on **November 24, 2026, at 7:25 p.m. Pacific** (`2026-11-25T03:25:49.540718Z`) with the ordinary backend gate failed.

The original failed evidence is preserved and independently read back from immutable commit `18168564399e67612f3b4e1a4d98c76ccf0f2d38`. The state is **archive readback passed; qualification failed**. CI remains unactivated. This checkpoint is sufficient to continue without the inaccessible chat or old cloud VM.

## Verified work

| Gate | Observed result |
|---|---|
| New lint regression cases | All 8 passed in preflight and in the ordinary SDK run |
| Compilation forms | All 15 passed |
| Normal behavioral identities | Exact 67 passed |
| Mutation witnesses and controls | All 14 designated witnesses failed as required; all 14 paired controls passed |
| Restored behavioral identities | Exact 67 passed |
| Registry identities | Exact 401 passed, zero failures/errors/skips |
| Full ordinary compatibility inventory | Failed and incomplete: 2,297 raw rows against 19,429 required identities |
| Client, typecheck, printing and Assay gates | Not reached |
| Source restoration | All 378 bound files restored; tracked worktree clean |
| Host settings | Restored after qualification; unchanged by the later diagnostic |
| Original archive and publication | Exact bytes, CRC, both manifests and live reference independently verified |

The mutation reports contain 28 executed witness/control rows and 303 intentionally unselected rows. Their raw failures are expected controls; they do not represent a failed mutation gate. See [the independent completed-stage analysis](LOCAL_QUALIFICATION_01/COMPLETED_LOCAL_RAW_STAGES_01.json).

## Why the backend gate failed

### Java agent attachment

All 118 failed game-server rows share a Byte Buddy external-attachment failure during MockK initialization. The first failing case is `AiControllerProviderTest: an external mode is usable without an LLM key and receives honest placeholder context`. Later initialization and Spring errors inherit the failed MockK gateway.

A separate, bounded host diagnostic used the exact cached Byte Buddy agent and JDK. It performed one standalone compilation and three small JVM probes; it ran no project tests or new qualification.

| Diagnostic | Actual outcome |
|---|---|
| Dynamic attach with the original property set | Failed |
| Dynamic attach with only HTTP(S) proxy properties removed | Failed |
| Explicit startup `-javaagent` with the original property set | Passed |

The captured helper errors report a missing/unresponsive attach socket under `/proc/<PID>/root/tmp/.java_pid<PID>` after 10,500 ms. This diagnostic does not establish the underlying kernel or namespace cause. Removing the proxy properties did not fix it. Startup loading supplied instrumentation before `install()`, reused that same instance, and supported both class redefinition and retransformation. **That proves the isolated startup mechanism; the 118 project tests have not been rerun with it.**

The diagnostic preserved host inputs and Gradle properties and left no owned helper processes running. See [host findings](HOST_DIAGNOSTIC_01/FINDINGS.json) and [the original diagnostic receipt](HOST_DIAGNOSTIC_01/original/RECEIPT.json).

### Docker/database readiness

Nine Flyway migration tests newly skipped. Their preserved Testcontainers output reports no valid Docker environment and a missing `/var/run/docker.sock`. Separate host inspection also found no configured Docker endpoint or Docker/Podman executable. The standalone Java mitigation does not resolve this database prerequisite. See [the database observation](HOST_DIAGNOSTIC_01/preparation/DATABASE_STATIC_OBSERVATION.json).

### Exact ordinary inventory

| Module with archived reports | Rows | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| AI | 746 | 745 | 0 | 1 |
| Game server | 479 | 348 | 118 | 13 |
| Gym | 535 | 500 | 0 | 35 |
| SDK | 537 | 537 | 0 | 0 |
| **Total** | **2,297** | **2,130** | **118** | **49** |

There are zero XML error elements. Against the frozen inventory, 17,134 expected identities are absent and two framework failure identities are unexpected. The observed skips comprise 40 historical skips plus nine newly skipped Flyway cases; three historical tooling skips are absent with the unrun module. Acceptance remains **19,429 total identities, 19,386 passes and the same 43 historical skips**.

The evidence supports a host setup explanation for the observed failures. It does not establish full compatibility. See [concise raw findings](LOCAL_QUALIFICATION_01/RAW_FAILURE_FINDINGS_01.json). The complete identity comparison is preserved losslessly with its [storage manifest](LOCAL_QUALIFICATION_01/TERMINAL_ORDINARY_RAW_ANALYSIS_STORAGE.json).

## Durable evidence

The original archive contains **952 files** and **5,961,295 bytes**. Its SHA-256 is `6179df2fed79eea77c5bb7fa696a17e0998923780d4315001667f02a4242c264`. The 14 original parts preserve those exact ZIP bytes without repacking.

- [Original local receipt](LOCAL_QUALIFICATION_01/LOCAL_RECEIPT.json)
- [Immutable and live readback](LOCAL_QUALIFICATION_01/READBACK_VERIFICATION.json)
- [Original archive index](LOCAL_QUALIFICATION_01/ARCHIVE_PARTS.json)
- [Terminal restoration record](LOCAL_QUALIFICATION_01/LOCAL_TERMINAL.json)
- [Current state and attempt counters](CURRENT.json)
- [Original authorization and bounds](CHARTER.json)
- [Final live-state reconciliation](FINAL_RECONCILIATION.json)

The original receipt retains its failed terminal and absent-success-result checks. No successful qualification or audit result was manufactured to replace them. The local archive readback passed all ten integrity checks while explicitly retaining `published_local_qualification_pass_validated=false`.

## Continuation boundary

This authorization allowed one fresh full local qualification, followed by CI only after a complete local pass and immutable/live readback. That local attempt is consumed. The old launch and envelope cannot be replayed as if they were a new authorized attempt.

A valid continuation needs a Docker-capable host and actual MockK/Flyway readiness checks before the complete suite. A standard GitHub Ubuntu VM is a practical hosted candidate. The repository is public; GitHub documents standard public-repository runner compute as free. This is a prospective recommendation, not a claim that a runner has been started or passed readiness.

Using hosted qualification before a local pass requires a new, explicit process authorization. Production code and tests would remain identical to S2, but the verifier authority and matching pins would need a narrow successor so the new run has its own honest identity and counters. No such successor or activation has been made here.

References: [GitHub-hosted runner specifications](https://docs.github.com/en/actions/reference/runners/github-hosted-runners), [GitHub workflow container and service requirements](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).

CI activations and CI artifact downloads remain **zero**. Both historical automations remain paused. All previous failure records and counters remain intact. Phase 2 remains unadmitted, with **zero seeds and games**.
