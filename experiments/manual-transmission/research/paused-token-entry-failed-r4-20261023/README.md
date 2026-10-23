# Paused-token R4 — failed original focused qualification (no adoption)

**Original GitHub Actions run `39977292393`: completed FAILURE, attempt 1, job `120495123447`; no re-execution.**

- Source SHA `9eb2be694761a4cae60ad4970d07e9d458d8054c`, Git tree `a48f7d3030d3c6a8ba01cfb165f589f4b3570c4f`.
- Validation child `c79f0eb389a392ddd35a54c3b64054dc325e6bb7`; [original run](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39977292393).
- Original artifact `12512810994` (`manual-paused-token-entry-r4-39977292393`) 110,636 bytes, ZIP SHA-256 `bff853cb4695dd789c491ce755c5fb6fcdfc96ffdc6650b0e73bd402417ae1c8`.
- Downloaded and read original ZIP without modification: 63 entries, all 62 originally recorded SHA-256 payload hashes verified, no ZIP CRC error. The entire ZIP remains attached to the GitHub Actions run, and is **not** embedded in this Git commit.

## Observed gates

| Gate | Original observation |
|---|---|
| Source and protected test binding | PASS |
| R3 original failed evidence bound without replay | PASS |
| Focused | **690 cases: 687 passed, 3 assertion failures, 0 errors/skips; 42 suites** |
| Engine in focused | **327 cases: 324 passed / 3 failed** |
| Broad engine + card-era | NOT EXECUTED |
| Ordinary whole-project build | NOT EXECUTED |
| Oracle Assay | NOT EXECUTED; expected incomplete report (not an additional parser failure) |

The original `focused/junit/rules-engine/build/test-results/test/TEST-com.wingedsheep.engine.event.EntryChoiceEventProvenanceTest.xml` is 40,266 bytes, SHA-256 `b62b85047972c677ed4269f6dcae81de34d7d1f54cd0f66322b6826f76ace035`, and preserves these **unchanged** test assertions:

1. **EP01 PASS**: exactly one self-ETB trigger, processed entry marker, correct life.
2. **EP02 FAIL**, line 137: `triggerSources(result.state)` expected two trigger sources (entry observer and leave observer), actual empty map. Entry/leave `ZoneChangeEvent` provenance checks *preceding* this assertion passed.
3. **EP03 FAIL**, line 159: two serialized pending-token entries expected one ETB trigger per token on stack, actual empty map. The preceding same-state, same-event serialized replay checks and processed provenance assertions passed.
4. **EP04 FAIL**, line 192: synthetic partial replay expected two detected triggers, got one. This original case explicitly labels its input an intentionally incomplete legal-history fragment; do not assume a legitimate gameplay trigger must fire from an observer that entered only after the earlier entry event.

## What is and is not known

R4's narrow source change in `SubmitDecisionHandler.kt` stamps published entry events with `entryTriggersAlreadyProcessed=true` after detecting their captured observations. This repaired the marker assertion in EP01 and also restored EP02/EP03 marker assertions. It **did not** restore the two pending-trigger sets. Therefore an independently failing **trigger-propagation/placement** issue remains; simply changing the marker cannot be a full fix.

The original `EntryChoiceEventProvenanceTest.kt` blob `0250a12ad245eded770bec604dbbfda63f7210e9` is byte exact to the previously qualified counter/Metamorph source; the R3 PT02 proposal remains prospectively approved and byte exact to R3. R1/R2/R3 failures are not reclassified by this R4 result.

The EP04 synthetic mixed-event replay may have an additional tension with the newer `observedEntryTriggers` event-time cache. Investigate **without** dropping snapshot provenance or changing original assertions. The next safe action is isolated instrumentation at the captured event, continuation-resume, and final stack-placement boundaries; do not invent a success from aggregate counts.

## Non-negotiable safeguards

- **R4 remains FAILED / UNADOPTED.** Do not rerun the same immutable validation or treat 687 passes as complete qualification.
- `main` remains last verified at `37a603617a03dfd2d81b39dae004f7f336a70dfc`; frozen v0.7 Git blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`; 23 protected goldens unchanged.
- Zero official Manual Phase 2 games/seeds consumed; both Precision allocations still reserved. No new deck hardware or competitive policy claim.
- Original archive, XML, source SHA, known limitations preserved. This is a same-assistant source/data review, **not** external independent verification.
