# Argentum G10 R6 — decision-local land-sequencing memoization, excluded synthetic qualification

## Frozen origins and limits

- G10 original GitHub Actions run **38628025826**, run_number/attempt **1/1**, conclusion **CANCELLED**; game artifact **11931191541** sha256 **6e53c39bc1b603d2b4421d774ee8538e00fb3e511cd38ac6d3940ade40126b86**. It reached 688 accepted actions and stalled selecting sequence 689. No verified winner; preserve and do not rerun.
- R3 synthetic run **38657812647**, attempt **1/1**, FAILURE; original artifact **11938526363**, sha256 **41aeb15ede8616b29ac75799c41206d6e0020e70fba5516fc77179f3e38ccf99**. An unchanged AI mid-board decision did not complete before timeout.
- R4 synthetic run **38666670958**, attempt **1/1**, FAILURE; original artifact **11942942827**, sha256 **e83c9fa11abab19b348a234689c5481cf2ff541f41452237e8d4487c29e0544b**. It improved the mid board but left the crowded board incomplete.
- R5 performance run **38670804050**, attempt **1/1**, FAILURE; original artifact **11943844458**, sha256 **a72e092ca63d47a62f0f41fcb771363e00a52b1209a93b46a57b522b7e0ec928**. The mid synthetic decision finished in 120.151 s; the crowded one remained incomplete after ~320 s. Its original trace repeatedly shows `bestLandUnlockedPayoffSequence` in 316/320 sampled strategy stacks and `shouldDeferForLandUnlockedSequence` in 224/320. These are attribution leads, not a proof of exact search-call count.
- R5 separate AI regression original **38671205141**, attempt **1/1**, SUCCESS; artifact **11944373469**, sha256 **6a2d7a7c1ba92119c295b2aa2fd74d995a568b3013f15cc69056401bf6703bef**, 86/86 PestControlAgentDecisionTest passes.
- The R5 frozen head and **sole parent** for this R6 candidate is **b5195ad2d090d26a533aef5bf3fa6a486b5128c6**, with Strategist.kt source blob **360c40b490458cfba82d43b29b2fcb0ea0913b68**.

## One-variable R6 hypothesis

The frozen Strategist calls `bestLandUnlockedPayoffSequence(state, playerId)` both when adjusting a land-play candidate and when considering whether a cast should be deferred. In a single `chooseAction` invocation, those callers receive the identical **evaluationState and playerId**, but recompute the entire expensive nested search for multiple leaves. This is a plausible contributor to R5 latency; it is NOT yet proven to be its complete root cause.

R6 introduces exactly **one production code-file delta**: `ai/src/main/kotlin/com/wingedsheep/ai/engine/Strategist.kt`.

- At the leaf-adjustment loop, construct `lazy(LazyThreadSafetyMode.NONE) { bestLandUnlockedPayoffSequence(evaluationState, playerId) }`.
- Pass its evaluator only through `adjustScore`, `strategicSequencingAdjustment`, and `shouldDeferForLandUnlockedSequence`.
- Both call sites obtain the **same decision-local result**, including null. The value is calculated only when a qualifying cast/land candidate needs it; no new eager search is introduced.
- Preserve the full underlying land/setup/focal nested search, the R4 focal-first cache, the R5 source-choice sampler, their candidate ordering, evaluator, mana solver, action processor, AI profile, and gameplay guards.
- There is no cross-decision cache and no timeout, sampling, replacement, budget cutoff or candidate-pruning change in this R6 patch. Existing nominal decision budgets still do **not** bound a single expensive inner search; this remains a known unresolved limit.
- Expected R6 Strategist blob: **9ad64c2cf141beca0f1091a801c0f7af3bb1e0c7**. Semantics equivalence still requires evidence and independent review. Do not promote this code to an accepted production branch based on this assertion.

## Frozen synthetic validation

- Branch: `play-first/g10-r6-decision-local-sequence-cache-20261014`, never update R5 or G10 original refs. The CI workflow must be a single **push-only**, branch-scoped original, with run_number=1/run_attempt=1, no workflow_dispatch, no approval/Environment, no gameplay ACK, no workflow write permissions, no full-game test.
- Add new isolated test classes only:
  - `G10R6SparseDenseParityTest` mirrors R5 sparse/dense source-only controls (expected 17/29 legal actions, chosen action category CastSpell).
  - `G10R6LateBoardDecisionMemoTest` mirrors R5's artificial 40-Forest mid/crowded synthetic state, source-bound to this R6 ref. Its scratch actions are never submitted; a synthetic fixture initializer is **not** an authorized G10/G11 game. Record no outcomes.
- Run existing `FocalSourceChoiceSamplerTest` and `PestControlAgentDecisionTest` on this **same patched source**, as two independent CI jobs; preserve separate XML and logs.
- Fail/timeout/incomplete outputs must preserve all possible evidence; an unfinished synthetic selection is not a pass. Only if both synthetic boards finish, existing regressions pass, full source/ZIP binding verifies, and an independent non-author review accepts the diff may this R6 candidate be considered for a separately authorized adoption decision.
- Any accepted source patch still does not authorize G11. No official allocation, results, merge, release or Environment approval may be inferred from this work.

## Test and receiving checks

1. Read run history before interpreting; the first original is consumed whether success or failure. Never rerun or replace it.
2. Verify exact head SHA, sole parent, allowed path diff, pinned blobs; reject production-drift in any other file and changes to the G10/R3/R4/R5 originals.
3. Review CI job statuses and steps, then download artifacts; verify original provider SHA-256, member CRC, unique normalized names, payload manifest, XML tests/failures/skips, source identities, timing spans, and no game/outcome claims.
4. Compare R5 original mid 120.151-second decision and crowded incomplete prefix with actual R6 durations. Distinguish test setup from action-selection time; don't claim equivalence from action class alone.
5. If one computed search remains pathologically slow, follow up with **deterministic work accounting inside the search** before considering any search-work cap; merely cutting wall-clock time risks load-dependent move selection.
6. Request truly independent Reviewer B review of exact commit and evidence. A different conversation does not itself establish independence. No production merge or gameplay is authorized.
