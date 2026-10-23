# Manual paused-token R4 — post-detection entry provenance repair

**SOURCE CANDIDATE ONLY — UNCOMPILED, UNTESTED, UNADOPTED** at time of freeze.

## Why this is an engine repair, not another PT02 test correction

R3's prospective PT02 correction was authorized and source-bound. All 686 focused cases passed. Broad run [39967952498](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39967952498) then failed **four original `EntryChoiceEventProvenanceTest` assertions**, which were byte-exact to the prior qualified engine source and had passed in [Counter/Metamorph run 39875902205](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39875902205).

The R3 original ZIP is artifact `12509450010`, 1,142,875 bytes, SHA-256 `27406f2d253996eb351d7ebbbe4afc2915412b7c5afbb61dd795d57cf733026b`. All 1,147 original SHA entries and ZIP CRC were checked. Full original JUnit failure XML is at `broad/junit/rules-engine/build/test-results/test/TEST-com.wingedsheep.engine.event.EntryChoiceEventProvenanceTest.xml`, 53,230 bytes, SHA-256 `26b9579e7c91829d90f8397079f3a690563fee28fa92ef4e38672c84f90994c8`. Original frame lines 118, 133, 158, and 177; all four were `AssertionFailedError`. R3 is permanently failed/unadopted. See receipt `checkpoint/manual-paused-token-entry-failed-r3-20261023`, head `fc9d3676eded0ebebd360bfa64c41de24ac8c56f`.

New `PendingTokenEntries.commit` preserves event-time `observedEntryTriggers` before returning from a paused answer, but the original `SubmitDecisionHandler` detected/processed those captured entries and then published their `ZoneChangeEvent`s with the old unprocessed bit. EP04 demonstrates that this causes the same event occurrence to be detected again. Historically, the old as-enters continuation detected triggers directly and returned a marked `ZoneChangeEvent`; R4 restores that provenance invariant at the shared **post-detection** boundary, not by suppressing events during token creation.

## Exact bounded production delta

Only `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/decision/SubmitDecisionHandler.kt` changes in production. A source-bound `bind.py` verifies that it:
- enumerates the exact **captured battlefield-entry events actually submitted to pre-SBA detection**;
- uses `IdentityHashMap` so equal-value but distinct event occurrences cannot suppress each other;
- marks their **published event** `entryTriggersAlreadyProcessed=true` only *after* trigger detection has run;
- leaves uncaptured events, other returned event kinds and the pending trigger list unchanged.

All four original `EntryChoiceEventProvenanceTest` assertions remain byte-identical to qualified source. R3's previously authorized PT02 corrected file remains byte-identical to R3, as does the R2 original annex. No production change outside the one handler is permitted. Existing R2 source/registry and 144 protected bindings, 23 golden hashes, frozen Manual v0.7 and accepted policies remain protected.

## Source-bound qualification requirements

`EXPECTED_CASES.json` freezes the exact prior 686 focused identities **plus four original EP01–EP04**, for **690** complete focused tests. Previously successful source-bound R3 focused results are not claimed as successes for R4's changed handler; rerun all 690. Original R3 EP failures are RED evidence and must not be replaced, weakened, or relabeled.

If focused is green, run **14,753** engine+card-era broad tests (including **3,978** engine), then the ordinary whole-project build **18,361** passes and exact **43** unchanged skips. Run all required 568 Assay tests, 2,000-card touchstone, and verify the 51 original differential blocks remain unchanged; no parser decline is turned into semantic evidence. All stages retain original XML, task exits, hashes, test identities, and source/workflow pins.

The R4 runner must:
1. precheck no overlapping GitHub work and pin immutable source SHA/tree in validation-only workflow child;
2. use shared Manual concurrency and outer Gradle `flock` guard;
3. stop on actual failures, preserve artifact even on failure; no silent bypass;
4. publish a separate source-bound receiving review before bounded component adoption.

**No official games, seeds, reserved Precision allocations, main merge, frozen deck edits, or golden reblessing.** This is a same-assistant engineering review, not external independent audit or competitive qualification.
