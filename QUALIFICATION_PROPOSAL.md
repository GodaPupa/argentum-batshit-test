# Isolated parent-JVM observation component — prospective software qualification

Candidate base: `1261a25494d93f588efbcd1c1e5798b212a058c9`, tree `7e635c7ff700094d840bb64fdba7699f724cf594`.

This proposal covers two new generic Python files only. It does not amend the Industrial R1 protocol, issue execution authority, invoke `r1_execute.py`, reserve a namespace, construct official requests, initialize the engine, or allocate entropy. No current qualification event has run for these files. The root conductor authorized local implementation and independent source/fixture review. A new workflow/trigger needs its own exact source and activation review before publication or dispatch.

The motivating defect is documented in `industrial-waste/v2/evidence/receiving-source-1261a254/ADMISSION_DELTA.md`: source1261's `prepare` can reserve its remote claim before its separate JVM exists, while the current runtime receipt observes classpath bytes after qualification. This new module is one reusable observation component toward a prospective accepted solution. It is **not the missing complete execution guard** and its success never opens official execution.

## Source and reused primitive

- `tools/parent_jvm_observer.py`: reads two snapshots of its actual direct parent JVM's executable inode, process start time/boot identity, raw command digest, explicit worker argument file and ordered classpath bytes. It validates positive finite read/entry/journal/time budgets, uses exclusive output directories and files, and retains intent, input identity, partial observations and final status on observed errors.
- `tools/test_parent_jvm_observer.py`: twenty-one standard-library unittest cases using a fake procfs plus a separately invoked, tiny actual-parent Java probe. Tests do not import or initialize the Magic engine.
- Unchanged `tools/observed_test_runtime.py` at source1261: only the canonical `safe_child` path-validation primitive is imported. Exact dependency identity is in the manifest. No copied generic engine implementation, SDK vocabulary, card definition, existing pilot or existing assertion changes.

`AGENTS.md`, `CONTRIBUTING.md`, the current add-feature and verify guidance were read. This adds tooling, not card behavior or engine/SDK/server/client semantics. The card Assay differential, SDK catalog and card-scenario requirements have no changed surface here. No Gradle build is proposed. Compilation is only one embedded, small software probe class via `javac`; the normal heavy-build semaphore remains unchanged. If a future receiving gate needs engine builds, it must use the existing prescribed routes.

## Exact observations and limits

Input schema `parent-jvm-classpath-observation-input-v1` contains expected Java, raw-command and argument-file SHA-256 values, canonical disjoint repository/Gradle roots, an exact ordered classpath in the existing observation shape, and seven finite bounds. Expected input is a comparison specimen; this module does not declare it accepted, authoritative or source-qualified.

The CLI has no alternate-parent or procfs option. Python-only private seams exist for deterministic software fixtures. The actual observer obtains `os.getppid()`, reads `/proc/<pid>/stat`, `/proc/<pid>/exe`, `/proc/<pid>/cmdline` and boot identity, and hashes the executable through the kernel's executable inode. Only one explicit `@argument-file` and the existing two-line `-cp` worker form are supported; inline classpath aliases, duplicate entries, ambiguous/noncanonical roots, outside paths and symlink directory members fail closed. Files observed changing during their own read fail. Both snapshots must match their expected bytes and each other while the direct parent and its lifetime identity remain stable.

Success is `MATCHED_TWO_SNAPSHOTS_NOT_ADMISSION`, with `admission=false`, zero claims and zero engine initializations. Error results preserve a typed error code and available earlier evidence. An already-existing output directory is rejected without modification. Output writes fsync their file and containing directory; this is not a proof of host or remote durability. Raw command lines and environment values are not written to observation reports.

The elapsed-time budget is cooperative between I/O operations, not a hard interrupt of blocked filesystem operations. Qualification therefore wraps both commands in an independent process timeout. Two snapshots cannot establish that classes already loaded in the JVM match disk, that JDK modules/agents/native libraries/classloaders are fully captured, that the image is immutable, or that nothing changes during subsequent gameplay. Relative paths, other JVM argument encodings, launcher wrappers and injected Java options are outside this narrow probe and require explicit future qualification. The full512 executor duration/resource/persistence envelope remains unresolved. No generic watchdog bypass is proposed.

## Finite fixture bank

The manifest enumerates all twenty-one unittest method names; its SHA binds the exact assertions. Cases cover matching observations; changed class bytes; reordered classpath; missing entry with partial evidence; duplicate entry; path outside roots; symlink escape; wrong executable; direct-parent replacement; same PID with a changed lifetime; malformed worker arguments; classpath mutation between snapshots; missing finite bounds; byte/time/journal bounds; missing input; nonblocking FIFO-input rejection; exclusive attempt preservation; executable-byte mismatch; and command mismatch.

These are deterministic software cases, not game outcomes, R1 allocations, policy comparisons or capacity calibration. The fake Java/class bytes are clearly labeled non-executable fixtures. The bank must report exactly21 cases, zero failures/errors/skips. Each retained case directory contains its generated input/procfs specimen and the observation's intent/result/partial journal when applicable. The existing-attempt case retains its untouched prior evidence sentinel.

The separate actual-parent probe compiles exactly one embedded `ParentJvmObserverProbe` class using JDK21, launches that JVM with an explicit two-line argument file, and has it start the observer as its direct Python child. Expected classpath hashes and raw launch digest are built from the known single compiled class and explicit command independently of the observer's inventory routine. It preserves Java source, compiled bytes, expected input, argv, stdout/stderr, exits and observer reports. Missing tools or injected Java options fail; they are not skipped. `javac` has a20-second timeout, the Java launch25 seconds and the child wait20 seconds followed by a bounded termination wait. The outer probe launches each command in its own session, observes exit without reaping, sends termination to that owned process group and reaps the direct child before finalizing command evidence; a recycled PID cannot be targeted between exit observation and group cleanup. This one positive probe is separately counted, not a22nd unittest and not an engine test.

## Intended commands and evidence

After exact independent source review and a reviewed activation route, a fresh owned attempt directory will be created once; `PARENT_JVM_OBSERVER_FIXTURE_ROOT` must name its pre-existing canonical `fixtures` directory. `PYTHONDONTWRITEBYTECODE=1` avoids source-tree cache files. Commands are run from the source checkout with the unchanged dependency available in `tools/`:

```sh
timeout --signal=TERM --kill-after=5s 60s python3 tools/test_parent_jvm_observer.py -v
timeout --signal=TERM --kill-after=5s 60s python3 tools/test_parent_jvm_observer.py --actual-jvm-probe
```

A prospective gate must record each actual exit independently, run the second command only after a clean first command, retain full stdout/stderr without masking failures, archive all partial attempt bytes on every exit, and bind the checkout tree, these exact source hashes, dependency hash and actual Python/JDK versions. Its overall timeout must cover at most these two finite commands plus bounded source recording/artifact upload; it must not run Gradle, `prepare`, any engine/experiment workflow, claims or official allocation code. No workflow file is part of this candidate and no trigger is activated by this proposal.

Publication and raw-artifact review must state actual freshness, identities and scope. A failed attempt remains failed and retained; correction requires a reviewed source successor. Passing these twenty-one software cases and one tiny actual-parent probe can support this observation component only. The accepted complete runtime, lifecycle design, whole-image identity, resource envelope, exclusive claim/journal and separate gameplay permit remain independent gates.
