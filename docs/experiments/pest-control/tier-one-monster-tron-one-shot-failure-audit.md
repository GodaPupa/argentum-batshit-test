# Monster Tron original smoke: infrastructure rejection and seed-free repair

## Accepted attempt disposition

**REJECTED_INFRASTRUCTURE_BEFORE_INITIALIZATION.** The original four-assignment block is retired
as a whole. There are no game results to accept, pool, salvage, score or replay. The existing
[stopping rule](tier-one-qualification-stopping-rule.md#monster-tron-smoke-and-replication-rule)
requires a fail-closed defect repair followed by a **separately reviewed replacement protocol**.
This repair grants no retry, replacement, seed generation or gameplay authority.

The official workflow [36085093386](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36085093386)
ran attempt 1 using activation source `ac2188f7fe1f67dd300d07b4c4e6b5b0c5d96814` and immutable
execution source `8a425d8532395e3d4262fbb35cfbac71e96feeb5` (C). Activation had been accepted in
[PR #147](https://github.com/GodaPupa/argentum-batshit-test/pull/147), merge
`684f93af469815d3b9636ba1b117ed3bfd6a34fb`. The remote create-only claim succeeded at
`1c2e253ad7f5a7652304f7c9aaafc30e547a481e`, under
`refs/heads/pest-control/official-attempts/monster-tron-smoke-v1`. That ref is retained unchanged.

| Counter | Audited value |
|---|---:|
| Original frozen assignments | 4 |
| Repository block claims / consumed block attempts | 1 |
| Reserved assignments retired without play | 4 |
| Per-game durable attempts | 0 |
| Initialization entries / successful initializations | 0 / 0 |
| Submitted actions / observed outcomes | 0 / 0 |
| Accepted sampled games / wins / losses | 0 / 0 / 0 |
| Rerolls / replacements / seed generation in this repair | 0 / 0 / 0 |

“Zero wins” here means no games occurred. It is not a four-loss result. The clean-four-games
replication trigger did not fire.

## Evidence audit

Artifact **10843459099**, `pest-control-tier-one-monster-tron-official-smoke`, is 254,190 ZIP bytes,
SHA-256 `33632b78325c2164da35d32b38e16de5dba2ac2034c728bd8a693ddf9ddc25b8`.
The artifact contains six passing sealed-boundary tests and five passing production-policy tests.
Its official-runner XML contains exactly one executed test and one failure, with process exit 1.
The failure is in `loadSealedInput` at source-C line 99, before creation of the `execution`
directory, per-game journal, initialization, action submission or outcome recording. The official
artifact directory contains the claim receipt, process exit and runner XML/binary evidence; it
contains no execution directory, per-game journal, raw game, summary or game outcome.

The claim receipt binds C, activation source, run/attempt and all four original reserved members.
Its SHA-256 is `d4c54726d566f6f184ce9033ce7fa68b43670e2c27248214dc14041b5b5d5e78`.
The failed official XML SHA-256 is
`959a40130c8fc77dc280c8e19f89acc7214972f320198a4f7330babb4ce81ce2`.
The retained original freeze ZIP has SHA-256
`70b9a665fbb154342e2789c1b6b2c2fd912579431a6ae1f9ab289984d5e7801c`; the September25 effective-rules
archive is 977,752 bytes and has SHA-256
`8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca`.
No artifact bytes or historical accepted results were modified or reblessed.

The entire original vector, SHA-256
`1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31`, and assignment CSV, SHA-256
`3e8c61d729d219261eb485600039b150d66e2e73e90f0f2e1450c7b48a57e098`, remain frozen and retired.
Any separately admitted replacement must exclude all four members alongside the existing registry;
this record does not authorize constructing that replacement. The accompanying
[machine-readable audit](tier-one-monster-tron-one-shot-failure-audit.json) retains these exact
identities and counter distinctions.

## Verified defect and repair scope

The Gradle test JVM launches in the `gym` module. C inherited that working directory when it invoked
`python3 scripts/pest-monster-tron-one-shot-claim.py`; consequently Python looked for the nonexistent
`gym/scripts` helper. A seed-free local `--help` probe reproduces exit 2 from `gym` and succeeds with
exit 0 from the repository root. The same relative-directory assumption also made the JVM's earlier
baseline pathspecs ineffective outside the root. The workflow's separate root-level baseline check
had passed, but that does not qualify the defective JVM check.

The repair discovers one canonical repository root and gives every boundary subprocess that explicit
working directory. Baseline checks additionally use top-anchored inclusion and exclusion pathspecs
and return changed path names without source content. The existing three wrapper exclusions are
unchanged; no engine, pilot, card, deck, protocol, authorization or gameplay cap is relaxed.

Failures now carry an exit status and bounded, redacted combined output. Raw command arguments,
environment and unsanitized exception causes are omitted. Truncated successful output fails closed.
A credential crossing the capture boundary is redacted before diagnostic clipping. A no-clobber,
fsynced `boundary-entry-error.json` can be written in the pre-existing artifact directory when
sealing fails before an execution directory exists. Its zero game counters describe that entry
phase and do not fabricate durable game attempts. The original claim remains untouched.

## Validation and remaining boundary

The existing boundary test class adds five deterministic tests: real helper `--help` from a `gym`
launch, an isolated Git repository proving protected SDK/engine/card/AI/gym changes are detected,
exit/stdout/stderr and credential redaction with durable no-clobber failure evidence, a credential
crossing the 16-KiB capture boundary, and refusal of truncated success plus sanitized start failure.
These fixtures contain no official seeds, make no authenticated helper request and never enter the
sealed official runner. Existing claim tests also retain the refusal of an already consumed claim.

Local verification: all 16 fake-only claim tests pass; the read-only helper path probe passes;
`git diff --check` passes. Kotlin runtime qualification belongs to the existing required GitHub
construction/surface/operational/CI checks; no local Kotlin pass is claimed. The original activation
workflow and exact-source C are unchanged. Passing repair checks alone cannot execute or revive the
retired vector. Pest's independent Spy support and postboard preparation may continue while the
separate replacement-protocol prerequisite remains outstanding.
