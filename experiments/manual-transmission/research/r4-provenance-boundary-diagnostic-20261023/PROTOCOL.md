# R4 provenance/trigger boundary diagnostic — PREPARED; NOT QUALIFIED

## Binding and prior failures
- R4 source `9eb2be694761a4cae60ad4970d07e9d458d8054c`, tree `a48f7d3030d3c6a8ba01cfb165f589f4b3570c4f`.
- R4 validation `c79f0eb389a392ddd35a54c3b64054dc325e6bb7`, [failed run 39977292393](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39977292393), original ZIP artifact `12512810994`, 110636 bytes, SHA-256 `bff853cb4695dd789c491ce755c5fb6fcdfc96ffdc6650b0e73bd402417ae1c8`.
- Original focused 690 = 687 passed, **three unchanged EP02–EP04 failures** (no error/skip). EP01 passed. Original R4 evidence received at `checkpoint/manual-paused-token-entry-failed-r4-20261023` (`a7c8327dac83ff307b18e92d0d8c24559e23ffae`).
- Original EP test blob `0250a12ad245eded770bec604dbbfda63f7210e9` remains untouched. Original R1–R4 artifacts and dispositions are immutable.

## Narrow diagnostic intent
A new stand-alone, *additional* `EntryChoiceR4BoundaryTraceDiagnosticTest.kt` reproduces the synthetic internal scenarios from original EP02, EP03 and EP04 but records, without overriding historical tests:
1. `ContinuationHandler.resume` directly after an as-enters choice, before public `SubmitDecisionHandler` post-SBA trigger processing.
2. The same accepted answer through `ActionProcessor.process(SubmitDecision)`.
3. Identity, marker, count and captured source IDs for each returned `ZoneChangeEvent`, as well as stack `TriggeredAbilityOnStackComponent` source IDs, continuation classes, and authoritative decision/priority flags.
4. Direct `TriggerDetector.detectTriggers` on raw continuation events to see whether event-time observer snapshots have eligible triggers before public action handling.
5. EP04's deliberately synthetic partial historical replay, with and without already-captured entry observations. These probes must be labeled synthetic and **must not be used to infer legal future observer triggers**.

The diagnostic makes **no claim of correctness if its own test runner completes**. Its purpose is to locate the missing transition from observation to actual stack placement. Original EP01–EP04 continue to fail/pass exactly as observed on R4 and must not be edited, skipped or relabeled.

## Execution
- New diagnostic source branch is a direct R4 child. Only these changes are permitted: this protocol, the standalone diagnostic test, and a `validation.just` recipe.
- After freezing exact source commit/tree, create a workflow-only validation child with shared Manual concurrency and the established `scripts/gradle-locked` outer `flock`.
- One diagnostic-only run of `*EntryChoiceR4BoundaryTraceDiagnosticTest` (three distinct scenarios); no R4 focused rerun, no broad/ordinary/Assay bypass claims.
- Preserve JUnit XML and stdout, source/validation SHAs, every original artifact hash, negative observations and missing outputs.
- Zero official games, official seeds or Precision allocations. Frozen v0.7, 23 goldens, 144 protected bindings, main, accepted policies and R1–R4 evidence remain unchanged.
