# Argentum G10 R5 — bounded optional mana-source counterfactuals (experimental)

## Authority and preserved originals

This is a **source-only, excluded synthetic-performance experiment**, not a new G10/G11 game, replay, formal result, gameplay approval, or production adoption. The owner authorized continuation of R5 diagnosis and isolated validation only. Do not modify or rerun any consumed original, protected Environment, release gate, or official counter.

- G10 original: run 38628025826 attempt 1/1, CANCELLED, 688 accepted actions; gameplay evidence artifact 11931191541, SHA-256 6e53c39bc1b603d2b4421d774ee8538e00fb3e511cd38ac6d3940ade40126b86; no verified winner.
- R3 synthetic original: run 38657812647 attempt 1/1 FAILURE, artifact 11938526363, SHA-256 41aeb15ede8616b29ac75799c41206d6e0020e70fba5516fc77179f3e38ccf99; mid case incomplete after over 998 seconds.
- R4 synthetic original: run 38666670958 attempt 1/1 FAILURE, artifact 11942942827, SHA-256 e83c9fa11abab19b348a234689c5481cf2ff541f41452237e8d4487c29e0544b. Two sparse/dense controls passed; mid stress completed after 272.78s; crowded stress did not complete after 746 strategy stack samples. No successful crowded synthetic result.
- R4 frozen source commit: b4fc0ae5ce7aa2e3ac97d9d7451f5de8935c8f72, original Strategist.kt blob a337a45157c527345b809eebbd4b50fddc2dce1f. R5 is derived solely from this commit.
- Live PR #231 has no later owner decision accepting R4 or authorizing G11. No official game or result can be inferred from this source-only branch.

## Specific defect and candidate

R4 stack samples place almost the entire unfinished crowded strategy call in `Strategist.bestLandUnlockedPayoffSequence`, and most in `castStatesWithSourceChoices`. The original code materializes **all** combinations of untapped land sources of the selected spell's CMC, then simulates each before the outer hypothetical action search. For example, C(16,8) = 12,870 alternatives. R4's focal-score memoization does not bound these explicit variants. Direct enumeration and isolated mana-cost feasibility checks complete in milliseconds.

The R5 candidate adds `ai/src/main/kotlin/com/wingedsheep/ai/engine/FocalSourceChoiceSampler.kt` and replaces **only** the unbounded explicit-choice generation inside `Strategist.castStatesWithSourceChoices`; it retains all R4 memoization and the original automatic-payment candidate. The sampler enumerates **all original choices in original order** when total combinations <= 16; above 16, it returns at most 16 deterministic, unique, ordered representatives (first, last, rotating, bookend, interleaved). Every returned explicit candidate still goes through the existing authoritative `simulator.simulate`.

**Behavioral limitation:** This is a candidate search *approximation*, not a semantics-preserving equivalence proof for dense boards. An unrepresented payment may be legal or tactically superior. Neither replay/case reuse nor a hidden wall-clock shortcut is permitted. Positive performance alone cannot justify production acceptance: assess chosen card/targets/ordering on strategically important states, including colored-source reservation. Independent reviewer must explicitly evaluate whether representativeness is sufficient.

## Excluded test and validation gates

- Branch: `play-first/g10-r5-bounded-source-choice-20261014` (source-only, nonofficial).
- Tests: targeted AI `FocalSourceChoiceSamplerTest` (10 tests), gym sparse/dense `G10R5SparseDenseParityTest` (2 source-bound controls), gym late-board `G10R5LateBoardBoundedSourceTest` (2 independent artificial stress cases).
- Standalone JDK21/Kotlin1.9 sampler compilation: 12/12 pure source tests passed locally. This is not a full repo build or strategic-equivalence verification.
- The source-only GitHub Actions workflow may run once via **one push** to its new validation branch, run_number=1/attempt=1. It has contents:read, no Environment, no workflow_dispatch, no other branch trigger, no gameplay token or ACK. It must fail closed on exactly the intended file identities.
- Run only the three named opt-in test classes, serially and under one external time bound. Preserve each class's XML separately *before* the next Gradle test and always preserve logs, partial journals, last XML, complete hashes and inventory on failure/timeout.
- On completion independently inspect provider run status, original artifact ZIP/provider digest, payload manifest, exact source blobs, opt-in XML and synthetic journals. Do not infer a performance result from a run conclusion alone.
- If crowded still stalls or action controls change, preserve the original as failure; do not rerun. Diagnose and draft a **separate** corrected source or decision record if justified.
- Full source acceptability requires a new independent Reviewer B assessment of the exact implementation, constraints and evidence, followed by a separate owner acceptance. **G11 gameplay remains out of scope.**

## Source identities expected at qualification

- R5 Strategist.kt: 360c40b490458cfba82d43b29b2fcb0ea0913b68.
- R5 FocalSourceChoiceSampler.kt: 494d4921acc3f8877dc5be39904e3ac3ab832f7e.
- R5 FocalSourceChoiceSamplerTest.kt: cd0bbf46c3543bdfeb9a48bdf4f9e28d255eaa2d.
- R5 G10R5LateBoardBoundedSourceTest.kt: cb63fa2dacfe49734e1d5673a910dc9fdcc90058.
- R5 G10R5SparseDenseParityTest.kt: 9900641d4515dca6d691041e37fae0f1b75b5378.
- G10 original adapter: 5ae14e0ed7212c317deecfa1cbd6a54a809c211a.
- G10 original workflow: 18c451f9c20e09e58a109c85c8d3c96c645d2558.
- R4 predecessor workflow: 6f7e5e2c5b23138b3cac3cffe84011ef3e4984bd.

This protocol is documentation only. It is not an owner release, formal decision, Environment approval, proof of a game, or acceptance of a repair.
