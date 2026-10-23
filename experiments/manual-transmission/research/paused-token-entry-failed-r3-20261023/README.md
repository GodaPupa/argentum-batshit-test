# Manual paused-token R3 — failed broad qualification, original evidence preserved

**Disposition: FAILED / UNADOPTED; no competitive evidence.** This is a separate review of the original [run 39967952498](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39967952498), attempt 1, job 120462681155, completed 2026-10-23 14:29:24 UTC. This receipt never reclassifies the failed run as qualified.

- Source `86a5dea55f088f1e242a151920aa83a671039b61`, tree `827d1772e77742437fb74d92a6a2801c5c664528`.
- Validation-only child `683195fb09870307cadbaec9c6d29916744c9b24`.
- Original artifact ID `12509450010`, name `manual-paused-token-entry-r3-39967952498`, 1,142,875 bytes, ZIP SHA-256 `27406f2d253996eb351d7ebbbe4afc2915412b7c5afbb61dd795d57cf733026b`.
- Original ZIP was downloaded and inspected read-only with Python's ZIP/ElementTree and SHA-256. **All 1,147 payload hashes matched the original SHA256SUMS; CRC good for all 1,148 entries.** Complete binary archive currently remains in the Actions artifact, *not* published on this branch.
- R3 prospective PT02 modification was explicitly authorized. R2 original assertion bytes are preserved in the R3 annex. R1 and R2 failures remain immutable.

## Actual test gates

- Source binding, historical RED segregation, and exact original 23-golden safeguards: **PASS**.
- Focused: 686/686 **PASS**, identities verified by dedicated receiving gate.
- Broad: engine **3,978 cases: 3,974 pass / 4 failures**. The original engine/card-era run halted on engine failures; do not describe this as a passing broad suite.
- Ordinary whole-project build and 2,000-card Assay/differential: **not executed** because broad failed; the Assay receiver correctly says INCOMPLETE.
- Original failed engine JUnit XML `broad/junit/rules-engine/build/test-results/test/TEST-com.wingedsheep.engine.event.EntryChoiceEventProvenanceTest.xml`: **53,230 bytes, SHA-256 `26b9579e7c91829d90f8397079f3a690563fee28fa92ef4e38672c84f90994c8`**; 4 tests, 4 original `AssertionFailedError` failures, no errors/skips.
- All four original assertions and source file blob `0250a12ad245eded770bec604dbbfda63f7210e9` are unchanged from the previously **qualified Counter/Metamorph source 418cdfdac...**, where the same four original tests passed.

## Exact four original failures

1. EP01 `EntryChoiceEventProvenanceTest.kt:118`: expected `entryTriggersAlreadyProcessed=true`, actual `false`.
2. EP02 `:133`: expected `[true,false]` on distinct entry and leave events, actual `[false,false]`.
3. EP03 `:158`: expected `[true,true]` for two serialized pending-token entry records, actual `[false,false]`.
4. EP04 `:177`: expected one remaining detected trigger in mixed marked/unmarked replay, observed two; duplicate detection follows from the missing processed marker.

The original failed JUnit is **not proof of production stack placement failure or absence of ETB effects**. Its consistent first mismatch is **post-detection event provenance**, not the earlier PT02 resolving-spell stack-count issue.

## Source-supported cause and narrow prospective remediation

The new `PendingTokenEntries.commit` captures `observedEntryTriggers` on the completed instruction but returns `ZoneChangeEvent` with `entryTriggersAlreadyProcessed=false`. The shared `SubmitDecisionHandler` then runs `TriggerDetector.detectTriggers` against these captured events and stacks/delegates the detected triggers, but publishes the original events without marking them as processed. The old pre-pending-token resumer directly detected/processed entry triggers and returned a `zoneChangeEvent.copy(entryTriggersAlreadyProcessed=true)`.

A **prospective, explicitly source-bound repair** should stamp only exact event occurrences actually sent to `detectTriggers`, after detection. It must not stamp an event before processing, suppress an unprocessed event, conflate equal-but-distinct events, drop entry snapshots, or change the original four assertions. Use reference identity where necessary.

Do not rerun identical failed R3. A new isolated candidate must bind the R3 source, add only the reviewed marker fix and its immutable protocol/tests, and run exact focused EP01–EP04 plus the prior focused bank, broad, ordinary, and Assay. Any failure preserves the R3 original result and all prospective results without adoption.

## Safeguards

- Frozen v0.7 Git blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89` and SHA-256 `2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1` remain immutable.
- Last verified main `37a603617a03dfd2d81b39dae004f7f336a70dfc`; protected goldens 23, saved-state fixtures, accepted policies unchanged.
- No official Phase 2 games/seeds; both unspent Precision games reserved.
- This is same-assistant artifact and source review, **not external independent review**.
