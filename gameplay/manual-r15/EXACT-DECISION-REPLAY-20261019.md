# ARGENTUM — R15 exact archived decision-state replay (October 19–20, 2026)

## Disposition

**VALIDATED R15 TEST: PASS. Combat lane: `NO_DEFECT`; Animar deployment lane: `NO_PROVEN_DEFECT`.**

This isolated, source-bound diagnostic reconstructed the real R8 pre-action state for indices **1336, 1497, 1701, 1998**, using exactly **1,997** authoritative archived game actions from initial state, with **1,997 matching life/terminal records and 1,997 matching engine-event strings**, and **zero ability-ID rebindings or engine rejections**. No new complete Commander game or new seed was initialized. Both original R5 and R7 seed-1612 action streams matched through action 1998.

**No AI policy correction or promotion is justified.** The combat score anomaly reflects an **illegal no-attack comparator**; the three Animar casts were legally available but scored lower than the actually selected priority pass. Their downstream opportunity value has not been proven.

## Source and immutable artifact identities

- Base R14 report branch head `eeafdc0cf70d0566b723d0cc626ce18d9a2019ca`.
- Exact passing R15 test source: `ca2b3435cba82a699ea61ccbdc8b4b8813644a5c` on `gameplay/manual-r15-decision-replay-20261019`.
- Targeted GitHub Actions [39366363405](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39366363405): **SUCCESS**; single `ManualR15ExactDecisionReplayTest` test passed, no skipped/failing tests, Gradle `BUILD SUCCESSFUL`. Job test XML recorded 23.572 seconds of test-body execution.
- Original passing CI artifact `12239496615`, `manual-r15-exact-replay-39366363405`, original ZIP SHA-256 `251dc4cd30d955bbee3fef6172ca285664df78923859efa258299e5cd3fe7a5f`. The downloaded bytes, output JSON, serialized state checkpoint hashes and test XML were checked independently.
- Original accepted R8 source `d226f1a56eebab1800b85a06bf70617ba439a00b` from run `39340369095`.
- Original R8 R5 seed-1612 artifact `12228089328`, ZIP SHA-256 `4f823fb61ac29ecc84582b4fd974200d2ba1d89664d906df802013d823f026a9`.
- Matching R8 R7 seed-1612 artifact `12229530628`, ZIP SHA-256 `7ca290085e874cf4eaeee766224ba797d0dd9c5ab37d0b5cd72b04fc386c622c`.
- Both originals share identical initial GameState bytes, SHA-256 `342ac135199550a95d64bf3b356c8f6a41aebef3f749b35f4e840504216efee0`.
- First isolated CI [39365828429](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39365828429) **FAILED at Kotlin test compilation**, before any replay: three type-check expression syntax errors and three nullable JSON builder arguments. Original failed artifact `12238758901`, SHA-256 `63f3e2b911f303d83a6d6bd9b973e1c05a8356ef352bdf201b27f83eb43d9300` preserved. The exact six-item test-only compile repair was published, retaining every substantive assertion; replacement run passed.

## Exact pre-action checkpoint identities

| Historical R8 action | Lane | State-before SHA-256 |
| ---: | --- | --- |
| 1336 | Animar cast-versus-pass | `9fed665708996ce0468b87337bc2924a09e748acaee41dd5877a74a4f537ce1f` |
| 1497 | Animar cast-versus-pass | `0a8d7182119fd3c7e19f6e02092179131b9fcfdb50d447e8b1e44c794584dffe` |
| 1701 | Mandatory combat attack | `e23c75eb214017248d3181bf108837f31196071163cdf699baf4c06aa0156ec3` |
| 1998 | Animar cast-versus-pass | `f309851969bdfd5c51c08a89e3dc306840b357ed1c8aebf95039581e947408db` |

These are the complete serialized `GameState` JSON bytes produced by validated rules-engine replay. Each checkpoint and the original AI scored options are included in the successful CI artifact.

## Lane A — action 1701, global turn 36, Seat 4: NO_DEFECT

Historical `AiDecisionInsight` reported:

- `No attacks` scoring reference **+0.8202380952380879**.
- Chosen six-creature attack score **-2.4947619047618916**.
- The apparent score gap (approximately **3.315 evaluator units**) cannot be used as evidence that the AI selected an illegal or suboptimal action.

At the *actual pre-action state*, the engine enumerated **nine** valid attackers, of which exactly **three attackers were mandatory**: `e473`, `e474`, and `e475`, each **Amphin Mutineer**. They had designated defenders under their attacking requirements.

Authoritative `ActionProcessor` tests of the same checkpoint:

- Archived six-creature `DeclareAttackers` action: **ACCEPTED**.
- `DeclareAttackers(..., attackers=emptyMap())`: **REJECTED** with engine error `Amphin Mutineer must attack its designated defender this turn if able`.

**Finding:** `No attacks` was a scoring reference, not a legal available action. The observed selection respected the three mandatory attackers. This closes the claimed inversion as **`NO_DEFECT`**. It does *not* prove the remaining three optional attackers were uniquely optimal.

## Lane B — Seat 1, Animar cast versus priority pass: NO_PROVEN_DEFECT

At all three preserved `PRECOMBAT_MAIN` decision states, the original AI chose `PassPriority` and a materialized `Cast Animar, Soul of Elements` remained legal under the real processor. Animar was in the command zone, had **one previous cast from that zone**, and its full current quoted mana cost was **`{2}{G}{U}{R}`**. Both passing and casting were rules-engine accepted.

| Action | Turn | Seat life | Own creatures | Own lands (untapped) | Pass score | Cast score | Advantage of passing |
| ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| 1336 | 29 | 38 | 8 | 6 (6) | 12.515129862 | 12.050129862 | +0.465 |
| 1497 | 33 | 38 | 8 | 6 (6) | 16.364360766 | 15.899360766 | +0.465 |
| 1998 | 41 | 30 | 8 | 7 (7) | -4.339791667 | -4.804791667 | +0.465 |

Opponent controlled-creature counts by seat 2/3/4 were **7/9/5**, **7/10/6**, and **13/13/7** respectively. The cast's lower score is a **recorded preference**, not a legality refusal, and does not itself demonstrate a scoring defect or a better future outcome. No scripted opponent passes, favorable future cards, or invented Animar development lines were used.

**Finding:** These are real and interesting commander-deployment valuation decisions. There is **no proven alternative improvement** in the evidence actually validated at R15; claiming a defect or implementing an Animar score bonus would be premature. Further same-turn or bounded future-value counterfactuals would require a separately scoped, source-bound experiment and appropriate negative controls.

## Safeguards and next action

R15 changes only its one isolated test and workflow relative to the R14 source, plus this findings document. No `main` merge, frozen Manual Transmission v0.7 deck change, `AiProfile.CURRENT` change, accepted R5/R7 policy edit, Wan Shi Tong combination, R8/R11 rerun or official C4/D2 action occurred.

**Overall disposition: `PASS: R15 forensic validation; NO_DEFECT combat; NO_PROVEN_DEFECT Animar`.** No corrective change is justified within R15. The optional future task, if independently authorized, is a tightly bounded Animar same-turn development counterfactual across these exact pre-action snapshots (with unforced opponent priority and no hidden-future assumptions), not another unqualified full-game batch.
