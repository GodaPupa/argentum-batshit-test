# Manual paused-token PT02 — source-bound successful **diagnostic**, not component qualification

## Verified immutable execution

- **Original failed R2:** [run 39946289164](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39946289164), job `120389655294`; source `06c27e1c31efd974300028c8ce5c1ceeac703280`, validation `2a6630795646b3993314f60211e8544137073dcc`. Its 686-case focused stage had **684 passes, two unchanged PT02 failures** at `PausedTokenInstructionScenarioTest.kt:124`. Broad, ordinary and Assay stages were not executed. R2 remains **FAILED / UNADOPTED**.
- **Prospective diagnostic source:** `859bb61f3f5fdd9a5a4460b2004349e03b47ebdc`, tree `6781c96e4e142ac64ee52e085386f8ae5b8c9058`. Parent `06c27e1c31efd974300028c8ce5c1ceeac703280`; delta contains **only** an additive protocol, `validation.just`, and the standalone `PausedTokenStackProvenanceDiagnosticTest.kt`. Original PT02 file blob `51c4162bb2d65fa2dc3a0c007734983e92b4c1ab` and frozen-v0.7 blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89` verified by the run's source-bind step.
- **Validation workflow commit:** `8039ab67687c37f4fe1c8056abc4b08fc2082688`, one validation-only workflow addition.
- **Original [diagnostic run 39952157757](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39952157757):** completed **success**, attempt 1, job `120408978986`, updated 2026-10-23 12:26:17 UTC. All setup, immutable source-bind, guarded single-class test, evidence hash, artifact upload steps successful.
- **Original artifact:** ID `12500948640`, `manual-pt02-stack-provenance-diagnostic-r1`, **6,525 bytes**; GitHub ZIP digest `sha256:2259fb4245496c85f21101775809885a26bb62442ab0ca9b595204ee2fc6a3cd`. Downloaded and independently read with Python `zipfile`; archive CRC good, all 8 ZIP entries accessible and all 7 `SHA256SUMS` entries exactly matched original payload hashes.
- **Original JUnit:** 1,271-byte `TEST-com.wingedsheep.engine.scenarios.PausedTokenStackProvenanceDiagnosticTest.xml`, SHA-256 `3594ab5573c4b92270ee08c5bd38b64c1c60d5fca08430b6730686a797888d33`; **four original exact case identities; 4 passed / 0 failures / 0 errors / 0 skips**, same four parameters as frozen protocol. Exact original XML copied to `original-junit.xml` here.
- **Log:** `BUILD SUCCESSFUL in 8m 15s`, 44 executed Gradle actions, `exit.txt` = `0`. Original full `gradle.log` remains in GitHub Actions artifact; SHA-256 `7b7a7b539e989ed20de96830472e67e897e900bfc761958781ee5e9c679ad19e`. Original artifact SHA manifest is preserved here as `ORIGINAL_SHA256SUMS.txt`. **The entire original binary ZIP is not embedded in Git history**; do not claim it is. Preserve/access the Actions artifact before retention expiry.

## Exact demonstrated result — diagnostic-only

| Parameter | Tested property | Result |
|---|---|---|
| `sourceCopy=false, serialized=false` | One still-resolving `SpellOnStackComponent` (copy action), zero triggered/activated ability stack objects, zero premature tokens, pending second color answer | PASS |
| `sourceCopy=false, serialized=true` | Same checks and byte-exact GameState serialization | PASS |
| `sourceCopy=true, serialized=false` | Zero spell/activated/triggered ability stack objects, zero premature tokens, pending second color answer | PASS |
| `sourceCopy=true, serialized=true` | Same checks and byte-exact GameState serialization | PASS |

The one extra `d.stackSize` in the target-copy PT02 variant is the **still-resolving instant spell**, not an early ETB trigger. It is required by the engine's existing pause/resolution semantics, whereas the activated-ability variant removes its ability object during a paused resolution. The original test's blanket `d.stackSize shouldBe 0` is therefore a **confounded measurement of the intended zero-ETB-trigger property**. This conclusion is supported by the actual four-case JUnit and source-bound runtime component checks, not only by a static guess.

**This does not qualify paused-token entry R2, prove correctness of all trigger ordering or validate multiplayer gameplay.** The standalone diagnostic intentionally stops at the first paused replacement question and does not itself replay the entire token-copy sequence. The original R2 failed receipt/artifact and all PT02 assertions remain unchanged.

## Next permissible move and authority boundary

1. Preserve original PT02 test and original failed R2 run as immutable historical evidence.
2. Write a separately versioned and source-bound **prospective test-oracle correction** explaining that the intended behavioral invariant is `count(TriggeredAbilityOnStackComponent) == 0`, not `state.stack.size == 0`; retain or strengthen a separate assertion for the still-resolving spell identity and slot, zero tokens before all choices, serialized continuations and final post-resolution triggers.
3. **Do not silently weaken or delete original PT02 assertions to obtain green CI.** The standing frozen-test constraints require explicit governance before replacing their literal historical assertion in the admitted test suite. A proposed patch may be archived on an isolated draft-only branch without execution/adoption while approval/authority is resolved.
4. If an approved prospective correction is made, bind new immutable source and protocol, guard new verification from duplicate runs, run new focused → broad → ordinary → Assay gates, receive original reports, and admit only the bounded component after all are green.
5. Continue independent frozen-card and pilot work only if safely nonoverlapping; do not fabricate gameplay, win rates, or new deck optimizations.

**Safeguards:** no main/frozen-v0.7 changes; 23 protected goldens unchanged by diagnostic; no official games/seeds; both Precision games reserved; no retrospective R2 reclassification; source-bound diagnostic receipt is same-assistant independent artifact recount, not external peer review.
