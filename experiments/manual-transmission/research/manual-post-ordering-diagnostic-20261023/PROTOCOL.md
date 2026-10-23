# Manual R4 follow-up — original trigger-ordering and synthetic-cache diagnostic

**Diagnostic-only; predeclared, not executed or qualified at source freeze.** Inherits FAILED R4 source `9eb2be694761a4cae60ad4970d07e9d458d8054c` without altering any original cards, assertions or production code.

The original [R4 trace run 39981459115](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39981459115), 3/3 successful, artifact `12514545313` (7,226 bytes, SHA-256 `d8f69a0f85c17debbdecfd7048a0b5aa0b4cd0278a990293c320cd0b4deec183`) directly observed EP02 and EP03 `ChooseOptionDecision` after both expected pending abilities were detected and EP04's captured-vs-legacy behavior. Receipt: `checkpoint/manual-r4-boundary-diagnostic-received-r1-20261023`.

## Exactly three prospective diagnostic cases

1. **EP02 order/resolve:** After the controller's token-copy color choice, capture the exact two pending trigger identities and require the `ChooseOptionDecision` while the stack is still empty. Answer the **real triggered-ability order prompt** through `ActionProcessor`, require one entry-watcher trigger and one leave-watcher trigger on stack, then resolve both and require +3 life. Verify the original ZoneChangeEvent captured entry cause remains marked and that the leave event remains unmarked.
2. **EP03 serialized order/resolve:** One choice for each of two simultaneous token entries with a self-ETB; require an ordering prompt, compare deterministic serialized and nonserialized decision responses, then submit the real ordering decision. Require one trigger per token on stack and +2 life after both resolve. The two original entry events must remain processed and not create duplicate triggers.
3. **EP04 synthetic cache control:** One marked real event with `observedEntryTriggers` must be inert to duplicate detection; copying that cached event into a synthetic, altered context must **not** invent a new event-time observer. A separate explicitly **legacy uncaptured synthetic** raw event with `observedEntryTriggers=null` is permitted to re-detect on the artificial final state. Expected cached one detection, legacy uncaptured two; do not claim the latter represents actual legal gameplay history.

All three run through source-bound deterministic engine test fixtures; no official Manual gameplay or experimental seeds are produced. Original EP01–EP04 `EntryChoiceEventProvenanceTest.kt` is unchanged at Git blob `0250a12ad245eded770bec604dbbfda63f7210e9`. Original R3 PT02 amendment remains unchanged at blob `2783d8891a70c93b88a1e5be8237cd4ad1afdb83`. Frozen v0.7 blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`, all 23 protected goldens, main and two Precision reservations protected.

## Qualification and authority

This diagnostic may establish the missing observation→ordering→stack→life pipeline and the historical-cache mismatch. **It cannot qualify R4, turn a failed historical test into a pass, rewrite test assertions, or authorize R5.** The three tests run once under a workflow-only child with the existing Manual concurrency group, outer `flock` and locked Gradle recipe. Preserve XML/log/manifest/artifact and exact source. If diagnosis confirms the test-oracle mismatch, archive a prospective, versioned EP02–EP04 test correction for review; modifying the original protected EP test requires the user's explicit authorization.
