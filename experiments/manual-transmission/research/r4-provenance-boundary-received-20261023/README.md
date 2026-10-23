# Manual R4 boundary trace — successful diagnostic, not paused-token qualification

**Disposition:** `DIAGNOSTIC_3_OF_3_PASS_SOURCE_BOUND_NO_COMPONENT_ADOPTION`.

- Original [GitHub run 39981459115](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39981459115), **SUCCESS**, attempt 1, job `120509529940`, updated `2026-10-23T15:54:43Z`; validation SHA `d69086d3edc4c3eeabde6390bb111581f905db45`.
- Prospective synthetic diagnostic source `d5b2152f1cf6a3bb66526263cae392d12fd9fae4`, Git tree `cde54a8a13cdf90bc6fd927c39f4776d7886ee89`. It adds exactly one test class, one protocol, and one build recipe to FAILED R4 source `9eb2be694761a4cae60ad4970d07e9d458d8054c`. No original assertion changes.
- Original artifact ID `12514545313`, name `manual-r4-provenance-boundary-diagnostic-39981459115`, **7,226 bytes**, GitHub SHA-256 `d8f69a0f85c17debbdecfd7048a0b5aa0b4cd0278a990293c320cd0b4deec183`.
- Original archive downloaded read-only, 8 entries, CRC valid, all **7 SHA256SUMS entries verified byte-for-byte**.
- Original JUnit `junit/TEST-com.wingedsheep.engine.event.EntryChoiceR4BoundaryTraceDiagnosticTest.xml`: **6,480 bytes**, SHA-256 `325c216e07909ea22e4cd337e8683e9d919763798a16da3d79ebf3844aee4326`, 3 exact test identities, **3 passes, 0 failures/errors/skips**. Full original ZIP and raw XML remain in Actions artifact (90-day retention); this summary is not a claim that binary bytes are embedded in Git.
- Original `gradle.log`, 14,670 bytes, SHA-256 `2ef2acdff94948336ae9cdc0421e1572f83d9ab8fde1c25957558e93ca301990`, `BUILD SUCCESSFUL in 5m 13s`. Every step including source checks, locked diagnostic, original XML preservation, hashes and artifact upload succeeded.
- Original exact artifact SHA manifest retained as `ORIGINAL_SHA256SUMS.txt`, and source-bound diagnosis below quotes the original `R4TRACE` records. Same-assistant audit; not independent external peer review.

## EP02 — both triggers detected, but ordering must happen before stack placement

Original direct continuation reported `ZoneChangeEvent` entry `e85`, `observedPresent=true`, `observedSources=[e83]`; watcher `e83` then leaves for graveyard. `detectPublishedEventsSources=[e83, e84]`, where `e83` is the now-departed entry watcher and `e84` the leave watcher. Both expected pending triggers are preserved.

Original public `SubmitDecision` returned `resultIsPaused=true`, `pendingDecision=ChooseOptionDecision`, `continuations=[Suspension]`, `stack=[]`, and a `DecisionRequestedEvent`. Entry event is correctly `processed=true`, leave event unprocessed.

**Interpretation:** `TriggerProcessor.processTriggers` queues a same-controller trigger-ordering decision when multiple simultaneous abilities require player ordering; no stack abilities are placed until the controller answers. Original EP02 tested `triggerSources(result.state)` before answering the new ordering decision. This is not evidence of lost triggers. **Actual post-ordering stack placement and life gain have not yet been measured by this three-case diagnostic.**

## EP03 — two self-ETB triggers likewise wait for ordering

After the first color response the source correctly returned a second `ChooseColorDecision`, no entry events or stack. After the second answer, original direct continuation returned two `ZoneChangeEvent`s: `e83` with `observedSources=[e83,e84]`, and `e84` with `observedSources=[]`. Original detector produced `detectPublishedEventsSources=[e83,e84]`.

Original public action instead returned `pendingDecision=ChooseOptionDecision`, `continuations=[Suspension]`, `stack=[]`, both entry events `processed=true` and a `DecisionRequestedEvent`. The two observed abilities await controller ordering. This explains EP03's empty-stack assertion, but not their unobserved post-ordering resolution.

## EP04 — synthetic event inherited stale cached observations

Original public decision's marked entry event had `observedSources=[e83]` and `processed=true`, and its self-trigger was stacked.

In a deliberately synthetic partial-event replay, the historical 'raw' event is constructed using `marked.copy(entryTriggersAlreadyProcessed=false)`. Under the newer event-time observer capture system, that copy **inherits nonnull** `observedEntryTriggers=[e83]`. The synthetic context event is also copied from the marked event and retains that same captured list despite changing the entity name and object identity to `e85`.

Original trace:
- `raw_markedContext detected=[e83]` (**one**).
- `raw_clearObserved_markedContext detected=[e83, e85]` (**two**).
- `raw_clearObserved_clearContext detected=[e83, e85]` (**two**).
- `rawObserved=[e83] contextObserved=[e83]`.

Therefore the original EP04 expectation of two detections assumes a legacy **uncaptured raw** event, whereas the modified event actually contains immutable captured observations. Clearing the cache only on the *synthetic historical raw input* restores the intended legacy re-detection without changing real gameplay or original past evidence. **Do not clear caches on real captured game events.** This is a test-fixture proposal only.

## Next permitted action and safeguards

Prepare a **new standalone non-qualifying post-ordering diagnostic** that submits the controller's real `ChooseOptionDecision` and verifies both triggers reach the stack, resolve for the expected life totals, and survive serialized save/replay. It must also pin EP04's cached and legacy-uncaptured replay behavior. Do not change `EntryChoiceEventProvenanceTest.kt` or assume the final engine behavior from this result alone.

Original R4 [run 39977292393](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39977292393) remains **FAILED / UNADOPTED** (687/690 focused). No broad/ordinary/Assay acceptance. If the follow-up diagnostic passes, a **prospective EP02–EP04 test-oracle amendment requires explicit authorization** under original assertion-preservation rules; prepare a review proposal before adoption. Do not run an R5 qualification merely to green the suite.

Preserve `main`, frozen Manual v0.7, both reserved Precision games, 23 protected goldens, all original assertions and all prior R1–R4 failures. **No official Phase 2 games or seeds consumed.**
