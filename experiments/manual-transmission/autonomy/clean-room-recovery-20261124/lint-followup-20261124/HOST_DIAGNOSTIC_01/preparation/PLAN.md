# Manual host diagnosis after the preserved failed qualification

Status: prepared for root review; no Java compiler, probe, build, repository test, container, service, or qualification has been invoked by this agent.

## Established findings

The first failure is `AiControllerProviderTest`, case `an external mode is usable without an LLM key and receives honest placeholder context`, at `AiControllerProviderTest.kt:59`. MockK initialization calls `ByteBuddyAgent.install()`, which fails in its external attach helper. The first case took 10.65 seconds. All 118 game-server failure elements contain this same attach cause, including later poisoned-initializer failures. `OBSERVATION.json` records the exact XML, log, agent, and worker-classpath hashes.

The worker classpath selects MockK 1.14.11 and Byte Buddy agent 1.18.10. The exact agent is 267,556 bytes, SHA256 `9cfa3c71c8f07bfb5f4c3c68db7715940d792801eba82d346fc343f7bd7c2401`. Other cached agent versions are not used by this diagnostic.

The failed worker accepted `JAVA_TOOL_OPTIONS` containing only the observed Gradle daemon property, separate platform trust store, non-proxy hosts, and loopback HTTP(S) proxy properties. Its recorded options do not show an attach-disabling flag. The logs do not include an attacher dump, so the exact subprocess cause remains unproven. The pinned JDK source uses a default 10,000 ms attach timeout; the elapsed time is consistent with a socket-response timeout, but is not sufficient to distinguish permission, namespace, signal, or socket failures.

The nine additional Flyway skips are a separate, established prerequisite failure. Their raw XML records an unavailable Docker environment and missing `/var/run/docker.sock`. Static inspection also found neither default Docker socket, no configured Docker endpoint, and no Docker/Podman/PostgreSQL executables on this invocation's PATH. `DATABASE_STATIC_OBSERVATION.json` binds that evidence and the nine exact skipped identities. The missing Docker CLI alone is not the deciding check: the unchanged Testcontainers setup needs a Docker-compatible API.

## Root-run standalone probe

The driver verifies the pinned JDK executables/libraries, separate trust store, and exact cached agent against `HOST_INPUTS.json`, acquires the existing Manual lock exclusively without waiting, compiles only the new scratch Java harness, then runs exactly these cases:

| Case | Agent mode | Properties | Required observation |
| --- | --- | --- | --- |
| `01_original_dynamic` | `ByteBuddyAgent.install()` | Observed property set, with this invocation's fresh proxy ports | Capture the external helper dump and actual JVM flags |
| `02_dynamic_without_proxy_properties` | Same dynamic attach | Remove only four HTTP(S) proxy host/port properties; retain platform trust and other properties | Isolate the Java proxy properties as a possible contributor |
| `03_startup_agent_original_properties` | Explicit `-javaagent:` using the same cached agent | Same properties as case 1 | Instrumentation must exist before `install()`, and `install()` must return the same instance |

All cases record PID/parent PID, runtime arguments, actual attach-related VM flags, Java version/home, selected process namespace/permission metadata, instrumentation capabilities, elapsed time, and full separate stdout/stderr. The startup case refuses a dynamic fallback if premain instrumentation is absent. The harness does not load MockK or application/test classes and does not transform any class.

The driver reconstructs proxy properties from the current process environment without calling `environment.prepare_environment()` or writing Gradle properties. It never reuses port 46159 from the failed run. Unexpected injected JVM options or credential-bearing proxies cause refusal before Java starts. It never changes OS/security controls or enables self-attach/dynamic loading through override flags.

After root has reviewed the draft and published/read back the original failed qualification, use the actual immutable commit and its existing local receipt:

```bash
python3 /workspace/scratch/65bf30f1888a/manual-mockk-host-diagnosis/run_attach_probe.py \
  --execute \
  --preservation-commit ACTUAL_IMMUTABLE_PRESERVATION_COMMIT \
  --preservation-receipt /absolute/path/to/original/preserved/RECEIPT.json \
  --out /workspace/scratch/65bf30f1888a/manual-mockk-host-probe-01
```

The commit and receipt binding is a required root-supplied attestation/reference. This diagnostic does not replace immutable preservation readback or independently fetch Git objects.

Each compiler/probe process has a 30-second deadline. Every process starts in a distinct owned process group/session. Cleanup signals only that owned group, with at most two seconds of TERM grace and two seconds of KILL grace; it records remaining live group members and stops later cases if cleanup is incomplete. No pre-existing JVM is targeted. Ordinary attach failures are preserved and do not prevent the startup comparison. A fresh output directory is mandatory; there is no automatic retry.

Output contains the standalone source/classes, `compile/` and three separate case directories, per-command JSON and logs, helper dumps where produced, aggregate `RECEIPT.json`, and `MANIFEST.json`. The receipt explicitly counts zero Gradle, repository-test, qualification, and network invocations. Host Gradle properties and pinned inputs are hashed before/after. A completed diagnostic is not reported as a candidate qualification PASS.

## Interpret the result before proposing any retry

| Observation | Supported conclusion | Follow-up decision |
| --- | --- | --- |
| Both dynamic cases fail with the same socket/attach cause, startup succeeds | Dynamic attachment is unavailable in these probe conditions; the four Java proxy properties do not explain the reproduced failure | Explicit startup loading of the same agent is a concrete host-only candidate remedy; preserve the dump and scope the future launch change to test JVMs |
| Original dynamic fails, proxy-free dynamic succeeds | The four proxy properties correlate with the difference in these controlled cases | Inspect the exact helper output and effective arguments before changing launch options; one pair does not prove every original failure cause |
| All cases succeed | The original failure was not reproduced by this minimal host process | Do not claim a verified remedy or automatically rerun qualification; investigate the difference from the original Gradle worker |
| Startup case fails | Startup loading has not been validated as a remedy | Preserve the error; do not alter versions or expand diagnostic cases without a concrete reason |

A startup-agent success proves instrumentation availability for this isolated JVM, not full MockK behavior or the repository's complete ordinary inventory. A later bounded validation must explicitly cover that remaining gap. Adding `-XX:+EnableDynamicAgentLoading` merely to suppress a JDK 21 warning does not diagnose the observed external-process failure.

## Database prerequisite must also be resolved

A valid retry requires an authorized Docker-compatible runtime that the unchanged Flyway test setup can discover, reach, and use for its pinned PostgreSQL container. A separately installed PostgreSQL service does not by itself satisfy a test that always provisions through Testcontainers. Do not add the nine skips to the accepted inventory or weaken its predicate.

Before any further full qualification, select a supported host/runtime with that capability and perform a separately authorized bounded host prerequisite check: identify the runtime endpoint, prove API reachability, confirm the exact required PostgreSQL image is available, and prove container startup/database readiness with cleanup. Preserve runtime/image identities and logs. No such service check, image pull, runtime installation, or container start is included in this prepared driver or has been performed here.

If this workspace cannot expose a supported runtime without changing platform controls, retain the failure and prepare execution on an authorized host that supplies it. This remains a prerequisite decision; it does not activate CI or create another qualification attempt.

## Primary references

- Java 21 instrumentation API documents explicit startup `-javaagent:` and premain instrumentation: https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/package-summary.html
- Oracle's Java 21 release notes state that dynamic agent loading remains enabled by default and distinguish warning suppression from disabling it: https://www.oracle.com/java/technologies/javase/21all-relnotes.html
- Testcontainers' runtime requirements specify a Docker-API-compatible runtime: https://java.testcontainers.org/supported_docker_environment/
- Testcontainers documents endpoint discovery/configuration: https://java.testcontainers.org/features/configuration/

The exact timeout observation comes from the already verified Temurin archive's `lib/src.zip`: `jdk.attach/sun/tools/attach/HotSpotVirtualMachine.java` SHA256 `1e919d3d0f834b835fa4c824b3317d2c301664db2c359f14519ac5f8bccc97cb` and `VirtualMachineImpl.java` SHA256 `b55df458ec04114a7763898ef4c5ac0698cc426d984a983cc3024500310541a7`. No substitute JDK or agent artifact was downloaded.
