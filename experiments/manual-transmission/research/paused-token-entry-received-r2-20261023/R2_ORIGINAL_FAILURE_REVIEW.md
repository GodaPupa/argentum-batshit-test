# Paused-token R2 — original failed evidence and PT02 stack provenance review

**Disposition: R2 failed, unadopted.** This is a read-only post-run diagnosis, **not** a repaired or qualified component, and not a replacement for the prepared receiver's full source-bound audit. The original PT02 assertions and all production bytes are unchanged by this checkpoint.

## Immutable execution

- Source: `06c27e1c31efd974300028c8ce5c1ceeac703280` (tree `560298ec2a80d623dd0fabd41c9c26351a250a34`).
- Validation: `2a6630795646b3993314f60211e8544137073dcc`.
- Prior qualified carrier: `418cdfdacd5191549e9fc8e4a071330120b071b7`; its receiving receipt is `7e09f9e1a6b7f4542a1de08419883ac922e2f465`.
- [GitHub Actions run 39946289164](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39946289164), attempt 1, job `120389655294`: **completed failure**, October 23, 2026 at 11:39:08 UTC.
- Original artifact `12498707228`, `manual-paused-token-entry-r2-39946289164`; **365,886 bytes**, ZIP SHA-256 `492b55350389a9a391434d5e26c83193b34dc413b36478cca83d429a1795b71c`.
- Original archive was downloaded read-only and inspected. All **80/80** `SHA256SUMS` entries matched, all **81** ZIP entries passed CRC inspection. Original nested R1 ZIP: **103,571 bytes**, SHA-256 `252e35406d499cc2b7b8d4f91f6b8ace6338f6034aadefbaa546c302e0f39ca6`.
- This checkpoint retains original artifact identity, GitHub link and observations **but not the binary ZIP within Git history**. The Actions artifact may expire; do not imply durable source-embedded archive preservation.

## Observed gates, not predictions

| Gate | Actual result |
|---|---|
| R2 changed negative-control class | 14 cases: 6 expected `AssertionFailedError` frames, 8 passes; 0 errors/skips |
| Reused R1 negative controls | 56 original cases in nested retained R1 artifact: 42 assertion failures, 14 passes; **not 56 new executions** |
| Composite negative ledger | 70 distinct cases across two runs: 48 expected assertion failures, 22 passes, **not a single 70-case run** |
| Candidate focused | 686 original XML cases across 41 reports: **684 passed, 2 failed**, 0 errors/skips |
| Candidate broad | Not executed |
| Ordinary whole-project build | Not executed |
| Assay/differential | Not executed; `assay/RECEIVING.json` correctly marks incomplete |
| Official Phase 2 / Precision allocations | None spent by this work; two Precision games remain reserved |

The two focused failures are from the unchanged
`rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PausedTokenInstructionScenarioTest.kt`:

- `PT02 entry triggers remain unstacked while later batch choices wait; sourceCopy=false, serialized=false`: line 124, **expected 0, observed 1** (`AssertionFailedError`).
- Identical PT02 case with `serialized=true`: line 124, **expected 0, observed 1** (`AssertionFailedError`).

**Both `sourceCopy=true` PT02 variants passed**. All remaining focused cases passed. The original R1 failed evidence at `e0a5ed3ecc95793e7551b133d3209946871274a2` remains failed and untouched; its four PI02 `NullPointerException` outcomes were excluded from R2's 56-case reuse.

## Why PT02's stack-size observation is confounded (source-supported, runtime identity still to prove)

The PT02 property intends to assert that no *enter-the-battlefield triggered abilities* have been stacked while the later pre-entry color choice is pending, but it checks **`d.stackSize shouldBe 0`**, which counts **every** stack entity, not just triggered abilities:

1. `GameTestDriver.kt:1192` defines `stackSize` as `state.stack.size`.
2. In `PausedTokenInstructionScenarioTest.kt:71-85`, `sourceCopy=false` casts `Paused Token Copy Action`, an **instant spell**, to create target copies. In `sourceCopy=true`, the path activates an ability instead (`lines 68-69`).
3. `StackResolver.kt:2158-2160` explicitly retains the **resolving nonpermanent spell as a stack object** until its effect finishes; `lines 2279-2327` preserve a `FinishResolvingSpellContinuation` on suspension. By contrast `StackResolver.kt:2970-2977` removes the suspended activated-ability stack entity.
4. `PendingTokenEntries.advance` saves both entry questions before committing the token instruction, and `commit` is reached only after the plan's questions finish. The separate PT01 case explicitly requires zero tokens after the first answer, including the target-copy variant, and passed in this R2 focused run.

This provides a **specific, strong explanation** for `stackSize==1` only with `sourceCopy=false`: the still-resolving instant spell. It does **not** independently prove the actual identity of the one stack entity in the failed runtime; the original PT02 XML contains its count but not its type. Therefore do not claim that the engine has been proven correct on PT02 solely from static source review.

**Never change the production stack lifecycle, drop the original assertion, or assert that the counted object was an ETB trigger just to obtain a green workflow.**

## Exact next non-disruptive test

Prepare an isolated, separately preregistered **stack-provenance diagnostic**, leaving original PT02 bytes untouched. After the first color response, inspect each `state.stack` entity's components and object identity:

- For target-copy: determine whether the one stack entity has `SpellOnStackComponent` and corresponds to `Paused Token Copy Action`, and whether `TriggeredAbilityOnStackComponent` count is zero.
- For source-copy: determine whether its suspended activated ability has left the stack and ETB-trigger count is zero.
- In both variants: verify no tokens have entered before every as-enters answer, preserve exact continuation ordering, and verify the final trigger count/order and life totals only after the second answer, across serialized/nonserialized runs.
- Record original source SHA, literal original assertions, exact diagnostic fixture hash, required negative expectation and artifact/XML. Do not run official games or seeds. Do not re-run R2 unchanged.
- If the count proves to be the resolving spell, explicitly govern the **test-oracle correction** (a typed trigger-only count rather than unqualified stack size) while preserving the original R2 failure and every original assertion in the historical source. The current authorization does **not** permit silently deleting or weakening the original assertion; leave R2 unadopted until an admissible prospective remedy is reviewed.

## Readiness and safeguards

- **No R2 component adoption, main merge, frozen-v0.7 changes, golden updates, official gameplay or seed generation.**
- Main remains last observed at `37a603617a03dfd2d81b39dae004f7f336a70dfc`; frozen v0.7 Git blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`; all 23 protected goldens and the two remaining Precision games are protected.
- This is a same-assistant original-artifact/implementation review, not external independent review. Full source/receiver admission gates remain unsatisfied, and a failed focused stage blocks broad/ordinary/Assay qualification.

**No new workflow was dispatched to prepare this review.** An unrelated queued Pest run `34749202144` was left untouched.
