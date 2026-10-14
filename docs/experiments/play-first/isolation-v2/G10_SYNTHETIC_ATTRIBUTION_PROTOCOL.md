# G10 latency — excluded synthetic real-AI attribution, not a replay

**Purpose:** investigate the unresolved G10 action-selection latency using a distinct, intentionally artificial GameTestDriver state. This is source-only diagnostic/isolated synthetic validation, not a nonofficial game original, no formal experiment, no G11 allocation, no replay or rerun. The G10 original remains permanently consumed as **INCOMPLETE**.

## Provenance and stop conditions

- G10 frozen original: run 38628025826, attempt 1/1, cancelled; source 6d5c84b4e892603b356b68e29010fa41eebfe72d; game artifact 11931191541 (SHA-256 6e53c39bc1b603d2b4421d774ee8538e00fb3e511cd38ac6d3940ade40126b86).
- Controller diagnostic checkpoint: daf1e28e30f77e26b899af2255d12e0c4d745b44, five additive source files, no production changes, no Actions runs.
- Validation branch only: play-first/g10-latency-synthetic-validation-20261014.
- Test class: gym/src/test/kotlin/com/wingedsheep/gym/G10SyntheticDecisionAttributionTest.kt, disabled unless ARGENTUM_G10_SYNTHETIC_ATTRIBUTION=EXCLUDED_SYNTHETIC_STATE_ONLY.
- Two deliberately artificial states: throwaway 40-Forest mirror fixture, a sparse 6-Forest/2-creature board and a dense 18-Forest/6-creature board, with three hand cards. These test-only cards and boards are **not** the frozen G10 decks, positions, seed, or previously accepted game journal.
- One decision per fixture, no chosen action submission, no full game loop, no terminal-result evidence, no seed or case input. A GameTestDriver may initialize its throwaway synthetic test state; this is explicitly *not* G10 game initialization or new game execution.
- AI profile is the frozen PestMonsterTronPolicy.profile; the original getLegalActions(state, actor) and chooseFrom(state, legalActions) calls are passed unchanged into the test-only G10DecisionPhaseProbe.
- Durable event journal fsyncs CALL_START / CALL_END / CALL_ERROR for LEGAL_ACTION_ENUMERATION and STRATEGY_CHOOSE_FROM. A non-interrupting observation thread samples the executing JVM thread stack every five seconds.
- The validator must not define or use the G10 ACK, G10 case-file environment, owner release, protected Environment, GitHub deployment, G11 allocation, or any official seed.
- Source gate permits only the two test-only paths and this document/workflow beyond the frozen diagnostic checkpoint; all production/gameplay paths must remain at their checkpoint blobs. A failure, timeout, ambiguous action, or missing evidence is a **synthetic validation failure**, not a game outcome.
- The sole original validation workflow is push-only on the separate validation branch, run number 1/attempt 1, no deployment environment, no write-token permissions, no GH mutations. Do not rerun it if incomplete or failed; preserve evidence and propose a new reviewed validation child if necessary.

## Independent receiving checks

1. Read live GitHub branch SHA, source parent, workflow byte identity and exact changed paths; reject any production-source delta or altered original G10 workflow.
2. Verify full Kotlin compilation and execution of the **specific opt-in synthetic test only**; no production bench, G10 one-shot test, game-loop test, random full match, or other suite.
3. Download original validation ZIP. Verify provider artifact digest, ZIP CRC and required output records; reject claimed attribution when unpaired timing spans or missing samples prevent classification.
4. If LEGAL_ACTION_ENUMERATION stalls, narrow the synthetic probe inside legal-action enumeration/cost affordances without changing actions. If STRATEGY_CHOOSE_FROM stalls, narrow candidate materialization, simulation, mana payment, and rollout. If neither stalls, report non-reproduction and propose a closer *separately bounded synthetic* board without rehydrating or replaying G10.
5. Apply no speculative timeout or move-changing fallback. Any proposed production performance repair requires its own exact diff, synthetic before/after regression and independent technical review.
6. No gameplay workflow, no G10 rerun, no G11 original, no official outcome/counter until separately authorized and admitted.
