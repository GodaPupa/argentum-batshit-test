# Manual paused-token EP02–EP04 — prospective test-oracle correction PROPOSAL ONLY

**UNAPPLIED · UNCOMPILED · UNTESTED · UNADOPTED.** The original `EntryChoiceEventProvenanceTest.kt` file at Git blob `0250a12ad245eded770bec604dbbfda63f7210e9` is **not changed** by this branch. It is identical in the previously qualified Counter/Metamorph source and in the failed R4 source. No production code, main, frozen v0.7, 23 protected goldens, official games/seeds or reserved Precision games are touched.

## Source-bound observations

- R4 original [failed run 39977292393](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39977292393): 687/690 focused pass, three unchanged `EntryChoiceEventProvenanceTest` failures EP02–EP04. Immutable original artifact `12512810994`; receipt `checkpoint/manual-paused-token-entry-failed-r4-20261023`. R4 remains failed/unadopted.
- [Trace run 39981459115](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39981459115): 3/3 diagnostic pass on additive-only source, artifact `12514545313` (7,226 bytes, SHA-256 `d8f69a0f85c17debbdecfd7048a0b5aa0b4cd0278a990293c320cd0b4deec183`). Receipt `checkpoint/manual-r4-boundary-diagnostic-received-r1-20261023` includes all source-bound observations and exact archive hashes.
- EP02 direct continuation exposes triggers from **both** the entered token and sacrificed watcher causality: `detectPublishedEventsSources=[e83,e84]`. Public handler returns `ChooseOptionDecision`, not a completed priority stack. The empty stack is expected until the controller orders two pending triggers.
- EP03 after the second serialized color answer likewise returns `ChooseOptionDecision` with both `e83,e84` observed entry triggers; no abilities are stacked until player order is supplied.
- EP04's synthetic raw event `marked.copy(entryTriggersAlreadyProcessed=false)` **inherits** an immutable `observedEntryTriggers` snapshot. Thus changing event identity by copying the event does not imply a new event-time observation. The trace shows one detection with cached raw, two with explicit legacy uncaptured raw (`observedEntryTriggers=null`).

## Proposed prospective fixture changes, not executed

Preserve **all original expected outcomes**, test identities, histories, original broad failures, counterfactual scopes and original assertions; only govern how the test reaches the correct post-ordering observation point and how it constructs an intentionally synthetic legacy input.

1. **EP02:** Keep the original accepted color choice result/events. Explicitly assert a `ChooseOptionDecision` for trigger order and an empty stack *before* ordering. Submit `OptionChosenResponse(question.id,0)` through `ActionProcessor`. Merge returned ordering events with the original entry/leave events for the provenance assertions. Retain the original requirement that the stack contain both watcher sources exactly once and +3 life after resolving them. Never auto-order triggers in production just to satisfy a test.
2. **EP03:** Keep the first/second color answers and their deterministic serialized replay checks. After the second answer, assert the order prompt; submit the same deterministic ordering choice in both serialized and original branches. Retain state/event round-trip equality, two entry provenance markers, exactly one trigger per token, and +2 life on resolution.
3. **EP04:** Only for its explicitly *synthetic legacy raw event*, construct `marked.copy(entryTriggersAlreadyProcessed=false, observedEntryTriggers=null)` so it actually models an uncaptured legacy input. Preserve all original duplicate/processed occurrence assertions, source/recipient reference-context assertions, and the distinct two-detection counterfactual. Add a new assertion that the **real** marked event retains its captured list. **Never strip `observedEntryTriggers` from actual gameplay events.**

No original fixture path should be changed, no positive R5 qualification dispatched, and no old failure retrospectively reclassified until (a) the separately launched end-to-end ordering diagnostic proves both actual stack placement and final life outcomes and (b) the user explicitly authorizes a versioned, narrowly scoped prospective amendment to the protected EP test source.

If approved, freeze exact R5 source and negative protocol with original file retained as byte-for-byte annex; only then run focused, broad, ordinary and Assay gates and receive immutable original evidence. Passing any test is not multiplayer/competitive admission.

**Open issue until next diagnostic result:** [post-ordering run 39985587423](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39985587423) was active at time of preparation. Treat its source/result as distinct from the earlier three-case trace and do not anticipate success.
