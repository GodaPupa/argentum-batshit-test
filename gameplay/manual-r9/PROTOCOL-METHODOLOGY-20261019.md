# ARGENTUM — R9 observation censor protocol and fair paired comparison

## Execution and authority

**ISOLATED INFRASTRUCTURE-ONLY CANDIDATE; no Commander game was started.** The validation receipt below remains governed by the actual current CI run. R9 does not alter `main`, `AiProfile.CURRENT`, frozen Manual Transmission v0.7, R4/R5/R7 production advisor source, or official C4/D2.

- R8 frozen original experiment source: `d226f1a56eebab1800b85a06bf70617ba439a00b`.
- R8 final report: `gameplay/manual-r8/HELDOUT-FINDINGS-20261019.md`, accepted head `6286befca1330faa96185f0726e8385dd348d60d`.
- Original Actions [39340369095](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39340369095) **FAILED** due to the R5 seed-1611 arm's strictly nonterminal wall-clock cap, **not** an engine rejection.
- R8 R5 seed-1611 original artifact `12229835135`; ZIP SHA-256 `d7775db042d2e00f4ab6a7ea7e71e479a202841e476d917830bfe5de67a9b4b1`.
- Matched completed R7 seed-1611 original `12228479674`; ZIP SHA-256 `f563f47aafc1a4fb570aabb666c10d0108db982ff217598018a19bab5d9cf1cd`.
- The two original archives are read-only inputs. This patch **does not rewrite the historical R8 failure** or rerun old seeds.

## Specific finding and correction

The original R8 R5 arm was at global turn **54**, accepted/submitted **3,330/3,330** actions with **zero rejected**, still active at **300,163 ms**. Its original `summary.json` has `status=RESOURCE_CAP`, `error="Bound reached before terminal game state"`, `gameOver=false`, and **no winner**. R7 on the identical seed finished on turn 54 after **3,445** accepted actions and **276,095 ms**, with Seat 2 winning. Original indexed submissions reconverged through R5's stopping point.

**The five-minute cap is a legitimate runner safety limit, not a gameplay elimination.** Classifying the cap as an error made a completed bounded observation fail CI and created unequal time-based horizons; interpreting that CI failure as a gameplay loss would be unsound.

R9 creates a separate **test-only** shared protocol `ManualR9CensorProtocol` and an isolated, future-only version of the R8 harness. It distinguishes:

| Condition | Status |
| --- | --- |
| 300,000 ms exceeded while still active | `CENSORED_WALL_CLOCK` |
| 10,000 submitted actions exhausted | `CENSORED_ACTION_BUDGET` |
| Global turn 200 exceeded | `CENSORED_TURN_BUDGET` |
| 1,500 actions in the same turn | `CENSORED_PER_TURN_BUDGET` |
| Actual engine terminal winner | `COMPLETE` |
| Engine rejection / exception | **Error; failing test, not censoring** |

At a nonterminal cap the new harness preserves a complete `censor-checkpoint.json` before exiting, sets its gameplay error to null, and adds `censorReason`, `comparableHorizonActions`, `wallClockMs`, and `censorCheckpointSaved` to its ordinary summary. The full actions, decisions, initial state and terminal/nonterminal final state remain preserved. Prefix gates still require exactly 64/64 valid actions; a prematurely censored prefix does **not** satisfy the gate.

The future-game harness requires `MANUAL_R9_GAMEPLAY_AUTHORIZATION=EXPLICIT_NEW_SAMPLE`, an expressly predeclared seed in two matching environment bindings, and **rejects the previously tested R7/R8 seeds**. None of those enabling inputs is supplied by R9's CI workflow.

## Fair paired comparison contract

Per-arm results retain their true status: `COMPLETE` with winner or a typed nonterminal `CENSORED_*` with no winner. Do not relabel a completed opponent game as censored just because its paired arm was.

The common action-count analysis horizon is `min(accepted_r5,accepted_r7)`. For original R8 seed 1611 this is **3,330**. It bounds which events can be compared; **it does not assert identical board states or equivalent elapsed real time** after the first divergent decision. To compare full states at that index, reconstruct them using the preserved initial state and authoritative action stream or capture exact per-index checkpoints in a separately authorized future harness.

A pair is eligible for a winner-versus-winner comparison **only if both arms have `COMPLETE` with verified terminal outcomes**. When either arm is censored, classify the pair as `CENSORED_PAIR`; no win-rate result follows. Even two arms censored at the same action count cannot contribute a completed-game win or loss.

## Validation scope and non-execution guarantee

R9's isolated test `ManualR9CensorClassificationTest` calls the *same* shared boundary classifier and snapshot writer used by the future harness, testing actual threshold crossing, precedence, complete/prefix/error controls, nonterminal-victory exclusion, and pair classification. The archive-bound test downloads both exact R8 ZIPs, verifies their SHA-256 and original R8 source, deserializes the **actual historical nonterminal R5 GameState**, derives its typed R9 censor and writes a fresh checkpoint; then verifies it is still turn 54 with no winner and that the pair remains winner-ineligible.

The R9 workflow runs **only** `:ai:test --tests '*ManualR9CensorClassificationTest'`; it does not dispatch any game or R8 rerun. Its checksum and frozen-source checks require the exact preexisting bytes. Failed test evidence must remain preserved, and the observed R8 run must retain its original FAILURE label.

**CI status is authoritative; do not claim R9 validated until a successful source-bound workflow and uploaded audit artifact are verified.**

## Next critical path

After a complete successful targeted regression, publish the CI receipt and stop this infrastructure assignment. Any future paired gameplay sample requires a **separate, explicitly authorized** predeclared experiment and an independent review of completion/censoring rules. Do not automatically merge, promote R7, run new seeds, repeat R8, or combine independent Wan Shi Tong v0.8.

## Source-bound completed R9 validation receipt

**VERIFIED PASS — TARGETED PROTOCOL ONLY.**

- Exact tested R9 source: `2378787b83f2265a3bc1c08069656910f5defc93`.
- GitHub Actions [39350147606](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39350147606): **SUCCESS**; **6/6** Kotlin regression tests passed. The shell post-check also independently verified `protocol-audit.json` and the saved GameState checkpoint.
- Uploaded original CI artifact: `12233131596`, `manual-r9-censor-protocol-39350147606`, **65,390 bytes**; ZIP SHA-256 `e58d46d866be85479c8a3f84b02c5b1d828bf1ec4002d585c18f74db3766c21e`. Downloaded and independently SHA-256 verified. The ZIP contains `r9-protocol-output/protocol-audit.json`, full nonterminal `censor-checkpoint.json`, `validation.log`, and the no-game receipt.
- Original R8 R5 seed-1611 archive remains `RESOURCE_CAP` / old error, without rewriting its bytes. The **derived R9** classification of its unchanged state is `CENSORED_WALL_CLOCK` with `derivedEngineError=null`, saved GameState still global turn **54**, `gameOver=false`, winner null.
- Paired original R7 accepted **3,445** versus capped original R5 accepted **3,330** → **shared action observation horizon 3,330**, `CENSORED_PAIR`, `winnerComparisonEligible=false`.
- Tests covered 300,000-ms exact boundary, 300,001-ms typed censor, all other resource bounds, simultaneous-bound precedence, honest engine errors, exact prefix count, terminal validation, no winner imputation, paired-censor disqualification, original ZIP hash/source binding, and actual GameState checkpoint round trip.
- The previous CI run [39349495462](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39349495462) **FAILED** at test Kotlin compilation because the test lacked the engine serializer import. Its failed artifact `12233220569`, SHA-256 `fa908e89b6d39c9153db4c5b416a47a134a857fc821d08e3337f9205215c673d` is preserved. A single import-only repair yielded the verified successor; it did not change any classifier rule or assertion.

**Final R9 disposition: `PROTOCOL_VALIDATED` as an isolated, opt-in infrastructure candidate.** No R8 games rerun, no new Commander games, no frozen input or production AI changes, no promotion to `CURRENT` or `main`, and no official C4/D2 status change.
