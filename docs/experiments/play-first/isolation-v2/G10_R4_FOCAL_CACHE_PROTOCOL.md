# Argentum R4 — excluded focal-first score caching candidate (UNQUALIFIED)

## Frozen authority and evidence

- G10 original Actions run: **38628025826**, original number/attempt 1/1, completed **CANCELLED**, permanently consumed, no legitimate winner or formal counter increment. Original game evidence artifact 11931191541, SHA-256 `6e53c39bc1b603d2b4421d774ee8538e00fb3e511cd38ac6d3940ade40126b86`.
- R3 synthetic first original run **38657812647**, number/attempt 1/1, completed **FAILURE**; original preserved ZIP artifact 11938526363, SHA-256 `41aeb15ede8616b29ac75799c41206d6e0020e70fba5516fc77179f3e38ccf99`. Profile includes 28 mid-board legal actions, ~17 ms standalone enumeration, then a strategy call lasting at least 998 seconds before timeout. No successful late-game synthetic terminal.
- R3 source: `ae6cabd07552fe57b8e0a55d4db92f81da8729f5`; exact original `Strategist.kt` blob `d8dd4cb300746dbc7dc4840220e970dd180ce20d`. Verified the exact same blob on G10.
- R2 sparse/dense synthetic original run **38650705588**, number/attempt 1/1, completed SUCCESS; two cases had 17/29 legal actions, the selected action class was CastSpell, and strategy latencies were 947.4/924.2 ms. This is category-level parity evidence, not card/target identity.
- This R4 work is **only** source-only diagnosis, a candidate performance patch and excluded synthetic validation. It does NOT permit G10 or R3 reruns, G11 allocation/gameplay, protected Environment modification, production merge, formal experiment admission, or declaration of a winning deck.

## Precise proposed source change

- Exactly one production source path may change in R4: `ai/src/main/kotlin/com/wingedsheep/ai/engine/Strategist.kt`.
- In `bestLandUnlockedPayoffSequence` allocate a fresh `MutableMap<Triple<EntityId,EntityId,EntityId>,Double?>` per function invocation.
- Replace repeat calls to `focalFirstCompleteScore(state,playerId,focalId,landId,setupId)` with key lookup; cache null and non-null results. Do not cache thrown exceptions, reuse across different root states or alter the action enumeration, state mutation, candidate selection, AI weights, or budget behavior.
- Unmodified original Git blob: `d8dd4cb300746dbc7dc4840220e970dd180ce20d`.
- Proposed candidate Git blob, independently computed from frozen source with exactly two anchored transformations: `a337a45157c527345b809eebbd4b50fddc2dce1f`.
- Local standalone Kotlin 1.9/JDK 21 semantic checks: 13/13 passed. In the controlled 3,000-query synthetic workload, memoization reduced recomputations to 216 while maintaining the same ordered scores, filtering, and selected synthetic option. This is **not** a real-game/engine performance measurement.

## Isolated qualification

- Source branch intended for **separately authorized Work/Codespace** session: `play-first/g10-r4-focal-first-cache-probe-20261014`.
- Only add `G10R4LateBoardCacheQualificationTest.kt` (exact R3 fixture translated to R4 branch and flags) and `G10R4SparseDenseParityTest.kt` (sparse/dense R2 control) plus this protocol and an original-only read-permission CI workflow. **Do not modify the frozen R3 test, R2 test, G10 test, accepted source branches, official seeds or workflows.**
- R4 must be based on the frozen R3 commit, not a later default-branch merge. Confirm exact changed-path allowlist and Git blob digests before validation.
- CI triggers only a push to the exact R4 branch, `run_number == 1`, `run_attempt == 1`; no workflow_dispatch, environment, admin bypass, approval, gameplay ACK, owner release, allocation or success marker. CI may compile Kotlin and execute only the named excluded synthetic test classes. Preserve logs, synthetic timing journals, and XML under a separate artifact whether success, failure or timeout.
- The R4 controls check that the sparse/dense R2 states still yield CastSpell (both had 17/29 legal actions). They do not prove identical chosen card or target. **Passing controls is necessary but insufficient** for strategy-equivalence qualification.
- Late-board R4 test must complete within the imposed bound, preserving source, timing spans, stack evidence and actual original run identity. If cache does not remove the stall, report failure and inspect remaining distinct mana-source combinations and outer land-sequence re-entry; never rerun the same original.

## Independent acceptance gates

1. Verify source parent, diff and all changed paths; reject unexpected production changes or any G10/R3 mutation.
2. Compile using the full repository/Kotlin toolchain; run excluded R2-control and R3-like R4 profile tests under bounded time. Do not execute a genuine G10/R3 replay or a complete exhibition game.
3. Download the original R4 validation artifact, verify provider ZIP SHA-256, CRC, unique paths, test XML, `CALL_START`/`CALL_END` or `CALL_ERROR` pairing, and the complete source identities; accept no unsupported success assertions.
4. Reconcile baseline R3 duration against patched R4 real-engine synthetic duration **without converting synthetic improvement into a gameplay result**. Run additional targeted regression checks for preserved action identity if possible.
5. Request genuinely independent Reviewer B assessment of this **exact** candidate head and evidence. Reviewer B must disclose any participation; a different conversation by itself does not establish independence. No production adoption before a separately authorized decision.
6. G11 remains a separate future authorization, allocation, release, and Environment approval even if this patch eventually passes.

## Hard stop

GitHub branch creation for this candidate was blocked by the connected tool safety controls in the prior session. Do not bypass that block with alternate API endpoints or pretend this candidate was published. This packet may be used in a legitimate authorized Work/Codespace execution context, if available; if writes remain prohibited, keep the source local and report the blocker.
