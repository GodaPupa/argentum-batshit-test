# G10 R3 — excluded late-board synthetic profiling only

## Controlling evidence and scope

- Original G10 run `38628025826`, number/attempt `1/1`, finished **cancelled**. Gameplay artifact `11931191541`, SHA-256 `6e53c39bc1b603d2b4421d774ee8538e00fb3e511cd38ac6d3940ade40126b86`. Preserve 688 accepted actions, final unfinished sequence 689, no verified winner and zero formal counter delta.
- G10's existing traces identify slow Monster Tron precombat-main decisions on turns 20 (~39.7 seconds), 22 (~1,201.5 seconds), 24 (~612.3 seconds) and 26 (unfinished). They establish **a latency boundary**, not the exact internal cause.
- R2 excluded synthetic validation original run `38650705588` at source `6e83b5d6a80e2b14eb87ae5a4f50550e3cd9e54b` completed SUCCESS. Original artifact `11934970871`, SHA-256 `ac8eaa3e0f8ce3f88540674570cea18e67e74022bdd06a01617a47a4f61acd7a`; two tests passed, neither reproduced G10's multi-minute stall.
- The present **R3 source-only research** is authorized by the owner as isolated synthetic diagnostics. It is NOT G10 replay authority, G11 gameplay authority, permission to alter a frozen original, official experiment admission or approval of a production repair.

## New artificial late-board fixture

- Branch: `play-first/g10-latency-synthetic-r3-20261014`, prospective one-run-only workflow `.github/workflows/g10-latency-synthetic-r3-20261014.yml`.
- Test: `gym/src/test/kotlin/com/wingedsheep/gym/G10LateBoardSyntheticAttributionTest.kt`, disabled by default; only opt-in through `ARGENTUM_G10_R3_PROFILE=EXCLUDED_LATE_BOARD_ONLY`, with no gameplay ACK/case/request.
- `GameTestDriver` creates a **new synthetic test-only fixture** (40-Forest mirror deck, automatic phase setup, manually minted battlefield) and deliberately does not reconstruct G10's state, seed, hands, shuffle, move list or event journal. The fixture's test initializer and phase setup are not gameplay authorization, and no selected action is submitted after synthetic board construction.
- Artificial mid board: 2 Mine / 2 Plant / 2 Tower / 2 Forest / 1 Conduit Pylons / 1 Bonder's Ornament / 1 Candy Trail / 1 Centaur Courser, opposing 2 Courser. Artificial crowded board: 4 Mine / 4 Plant / 4 Tower / 2 Forest / 2 Conduit Pylons / 3 Bonder's Ornaments / 2 Candy Trails / 1 Giant's Boulder / 3 Courser, opposing 4 Courser. Both are intentionally **not** G10-equivalent.
- The unchanged `PestMonsterTronPolicy.profile` is used. Measure three standalone `ManaSolver.canPay` calls, one `GameSimulator.getLegalActions` call, up to two non-committing scratch `GameSimulator.simulate` calls, and one unchanged `AIPlayer.chooseFrom` call. Extra timing probes are separate diagnostic operations; they are not part of the original agent's strategy choice.
- The durable journal fsyncs CALL_START/CALL_END/CALL_ERROR. An observational daemon takes 1-second stack samples and labels topmost matching frames as MANA_OR_PAYMENT, ACTION_ENUMERATION, ROLLOUT_OR_PLAYOUT, CANDIDATE_SIMULATION_OR_ENGINE, STRATEGY_EVALUATION, or OTHER. Categories are **heuristics for attribution**, not a substitute for a causal reproduction.
- The synthetic fixture refuses an ACK/case/request environment and refuses mismatched branch or rerun attempts. No active G10 workflow, frozen G10 Kotlin adapter, production AI, official source, seed allocation, protected Environment or deployment changes are permitted.

## Validation and receiving contract

1. Inspect live GitHub before release and verify branch parent, exact changed paths, new workflow scope, no hidden trigger for gameplay, only read-only token permissions, zero deployments, and run_number=1/run_attempt=1. If an unwanted workflow runs, stop.
2. Run only `G10LateBoardSyntheticAttributionTest` under the explicit synthetic flag, with a bounded external test-process timeout and artifact preservation on success/failure. Do not execute `PlayFirstOneShotPestMonsterTronTest` or any full-game test.
3. Classify the first original validation on its own terms. On failure or timeout preserve artifacts without rerun; do not use a new original unless a specific defect is independently diagnosed and a fresh validation child is justified.
4. Before concluding performance results, verify original artifact ID and sha256, ZIP CRC, XML test results, source identities, all timing span pairs and interruption status. Reconcile counts and stage durations to the durable event journal and stack samples; distinguish setup from actual decision latency.
5. If a stage reproduces multi-second latency, propose a **separate non-gameplay instrumentation refinement**, instrumenting inside the suspect operation before any behavior-changing patch. If R3 passes quickly, report **non-reproduction** rather than claiming the defect fixed.
6. A production fix requires an independently reviewed causal finding, a synthetic failing case, bounded targeted before/after regression, and a separate production acceptance. G11 requires fresh owner allocation/release/Environment review and is out of scope.

## Historical validation failure

First validation R1 run `38650497401` failed before job creation because `runner.temp` was used in job-level env, where that context is unavailable. No test or engine step ran. R2 corrected the workflow in a new original; R1 remains consumed. R3 must use only contexts authorized at each GitHub Actions expression location.

No unsupported result claims, no reruns, and no formal result promotion.
