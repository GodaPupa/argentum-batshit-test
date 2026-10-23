# Manual paused-token R5 — authorized prospective EP02–EP04 fixture correction

**SOURCE PREPARED; no R5 execution result or adoption is claimed by this commit.** R4 remains failed. Production code is byte-identical to R4.

## Authority and diagnostic receipt

The user explicitly authorized the previously proposed narrow correction if the post-ordering diagnostic verified complete behavior. Run **39985587423**, attempt 1, job **120523783882**, completed successfully. The downloaded original artifact **12516126080** is 6,645 bytes, SHA-256 `4e451e414fbda806b8078c6bed4989d206ab28ba849cdabea4713dbd1c9aa415`; ZIP CRC and all seven original manifest entries passed verification. Its three original JUnit cases passed with no failures/errors/skips. The exact 987-byte JUnit is preserved here as `POST_ORDERING_JUNIT.xml` (SHA-256 `5e1f14843c4ec6504ef6c1f7ddf8e1524f590eeade1165bdf4e6fb2be3b02c61`). The complete binary ZIP is retained by Actions, not embedded in this tree. This is a same-assistant artifact recount, not external peer review.

The diagnostic actually submitted the controller's ordering decision, verified both expected trigger groups on the stack, resolved them for +3 life in EP02 and +2 in EP03, and checked serialized replay. EP04 independently distinguished cached observations from an explicitly uncaptured synthetic legacy event.

## Exact authorized test changes

- EP01 is unchanged.
- EP02 answers the actual trigger-ordering prompt before applying the original stack-source and life assertions. The original entry and leave events are retained for every provenance assertion.
- EP03 retains the original deterministic state/event comparisons immediately after both color answers, then answers ordering in both replay branches and retains the original final source-count and life assertions.
- EP04 clears `observedEntryTriggers` only on its deliberately synthetic legacy raw event. The real marked event keeps the cache, whose presence is now asserted. All original duplicate-occurrence and object-reference context assertions remain.

The original file blob `0250a12ad245eded770bec604dbbfda63f7210e9` is retained byte-for-byte in the annex. The amended file is pinned to `9392987680c91a7ac75e96ecb67779ac09fe483e`. `gate.py` verifies all original `shouldBe` assertion lines and all four original test names remain. No production engine file changes from R4, no changes to the authorized PT02 source, no other original test modifications.

## Negative control before positive qualification

A single guarded control uses the corrected four EP tests with only `SubmitDecisionHandler.kt` restored from failed R3 source `86a5dea55f088f1e242a151920aa83a671039b61`. It preregisters four assertion failures at the exact original marker/duplicate-detection anchors. Compilation errors or different failures do not count as successful controls. This is a known-regression reproduction, **not a qualified baseline or the historical 70-case experiment**. The exact candidate handler must then be restored and rebound before the 690-case focused bank, broad regressions, ordinary build and Assay.

## Reporting integrity repair

The R3 broad receiver rejected duplicate leaf names in unchanged parameterized tests. The last qualified Counter/Metamorph artifact contains exactly the same 11 repeated identities, twice each, in broad and ordinary reports. R5 retains every row and checks that exact multiset (`QUALIFIED_DUPLICATES.json`), verifies both fixture files are byte-identical to the qualified source, and rejects unknown or changed multiplicities. No tests are deduplicated, omitted, skipped or reclassified. R4's obsolete ordinary `engine_passed` dictionary lookup is mapped to its existing `broad_engine_passed` key, still requiring 3,978 engine passes. The exact effective collector is archived with each stage; original count, class, module, task-execution and 43-skip checks remain.

All execution is on isolated source/validation branches with a single-attempt workflow, shared Manual concurrency and the existing build semaphore. The final receipt remains a separate gate. Keep historical PT02 negative-control eligibility visible; a passing R5 workflow alone does not resolve every earlier admission obligation.

**Frozen v0.7, main, 23 protected goldens, 144 protected bindings and both reserved Precision games remain protected. No official games or official seeds; no multiplayer or competitive claim.**
