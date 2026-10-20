# ARGENTUM — R16 source-bound Animar same-turn valuation (October 19–20, 2026)

## Disposition

**R16 TESTED AND VALIDATED: `NO_PROVEN_DEFECT`.** Three archived R8 seed-`202610191612` Animar cast-versus-pass decisions were examined using their previously validated exact R15 GameStates. Both actions were accepted by the authoritative engine. The original recorded scores were reproduced **exactly** by the independent board evaluator. The existing `ManualAnimarDevelopmentAdvisor` returned **`null` (no override)** at all three states, correctly reflecting the **absence of any creature card already in hand** to exploit Animar's immediate same-turn discount.

No R8/R11 full-game rerun, new Commander game, production advisor correction, deck change, merge or policy promotion was performed.

## Exact authority and input

- Frozen R15 report head: `62472cc4216cdf71e67287097275930bdddc1b34`; passing exact replay source `ca2b3435cba82a699ea61ccbdc8b4b8813644a5c`.
- Source-bound R15 run [39366363405](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39366363405): SUCCESS. R15 archived state artifact `12239496615`, original ZIP SHA-256 `251dc4cd30d955bbee3fef6172ca285664df78923859efa258299e5cd3fe7a5f`.
- Existing production-independent, opt-in advisor: `ai/src/main/kotlin/com/wingedsheep/ai/engine/advisor/modules/ManualAnimarDevelopmentAdvisorModule.kt`, unchanged blob `b995dfc8d27dbc71da7785f7f708b0b0a442c3e8`.
- Tested R16 source: `8320b246fb138837e2bfa90ba1a3a850637a0614` on isolated branch `gameplay/manual-r16-animar-valuation-20261019`.
- Successful GitHub Actions [39367868387](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39367868387): **SUCCESS**, `ManualR16AnimarValuationTest` 1/1 PASSED, `BUILD SUCCESSFUL in 5m 58s`, no other gameplay harness dispatched.
- Original R16 CI artifact `12240223523`, `manual-r16-animar-valuation-39367868387`, original ZIP SHA-256 **`425f0ca27da8efb50738f127e1ef378027fa9fd88611f1cf72698be88f4cf1c2`**; downloaded and byte-verified independently.
- Within the original artifact: `r16-output/r16-valuation.json` SHA-256 `de88a3c49269d2584aff5f0a5a51d4c41b86b7e59c246e0ffa599942b071c190`; `r16-output/r16-summary.json` SHA-256 `e4de1899339181f07892506f3cf91ecd1c4066052d3fc51a3135aa3ad73957d9`. Test XML reports 1 test, 0 skipped, 0 failures, 0 errors.

## Original decisions, actual hand and scorer outputs

All three states concern Seat 1 (`manual-mirror-0`) in `PRECOMBAT_MAIN`; Animar had been cast from the command zone once, with an available recast costing `{2}{G}{U}{R}`. The canonical R15 GameState checkpoints were rechecked against their exact previously accepted SHA-256 values.

| Original action | Global turn | Life | Cards actually in hand | Original pass | Original Animar cast | Difference favoring pass |
| ---: | ---: | ---: | --- | ---: | ---: | ---: |
| 1336 | 29 | 38 | Lifecrafter's Bestiary; Spire Garden | 12.515129862 | 12.050129862 | 0.465 |
| 1497 | 33 | 38 | Lifecrafter's Bestiary; Spire Garden; Forest | 16.364360766 | 15.899360766 | 0.465 |
| 1998 | 41 | 30 | Lifecrafter's Bestiary; Forest; Rapid Hybridization | -4.339791667 | -4.804791667 | 0.465 |

**Each hand has zero creatures.** After simulating Animar deployment, no same-turn creature from that original hand could be cast; count was **0** at each state.

The R16 test independently evaluated both the cast and pass quiet-state leaves. Its `BoardEvaluator` cast/pass outputs matched the *original* decision `AiDecisionInsight` score values **exactly**, including the repeated 0.465 gap.

## Existing advisor behavior verified (not patched)

The advisor is explicitly limited to **measurable same-turn incremental value from casting the SAME real hand creature with and without Animar**. It checks the original hand, legal affordability, real post-Animar cast and counters, positive follow-up board gain and the incremental delta versus casting the same creature without Animar. It does not provide a fixed commander bonus, assume later hidden draws, create a fictitious mana refresh, or assume opponents decline interaction.

At these exact three states:

- Original hand-creature count: **0 / 0 / 0**.
- Immediate hand-creature follow-ups after deploying Animar: **0 / 0 / 0**.
- `ManualAnimarDevelopmentAdvisor.evaluateCast(...)` output: **`null` / `null` / `null`**.
- Original engine actions `CastSpell(Animar)` and `PassPriority`: both accepted in all three states.
- Commander tax: one prior command-zone cast; the current mana cost remained `{2}{G}{U}{R}`.

Thus the adviser properly deferred to the existing, negative immediate incremental valuation. The 0.465 score difference is demonstrably a real **one-ply board-evaluator** difference, not an incorrectly applied manual advisor bonus or corrupted telemetry. The test did **not** isolate each evaluator weight mathematically, so avoid asserting the 0.465 is exclusively mana expenditure or body cost.

### Priority and causal limits

A direct `ActionProcessor` cast leaves the spell on the stack at an ordinary priority window. The `GameSimulator` used for leaf evaluation automatically passes priorities to reach a quiet boundary; this is the same scoring-horizon mechanism the shipped advisor uses, but **not** evidence that competent opponents would let Animar resolve or survive a later turn. No opponent passes were scripted in the test and no future draw/combo was attributed to the original decision. R16 did not execute a bounded multi-turn causal fork.

Future creature cards may make Animar deployment valuable. These three no-creature-hand positions simply cannot demonstrate that optional future value strongly enough to justify a new fixed scoring bonus.

## Guardrails and recommendation

The R16 branch adds only its test, bounded no-game workflow, and findings report relative to the accepted R15 report head. The frozen v0.7 deck blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`, `AiProfile.CURRENT`, original R5/R7 policy and advisor implementations, historical R8–R15 records, Wan Shi Tong v0.8 and official C4/D2 gates are unchanged. No main merge or qualification.

**R16 final: `PASS — EXACT_SAME_TURN_ADVISOR_VALIDATED; NO_PROVEN_DEFECT`.** No corrective code change is justified. Next critical-path action within R16: **none**. If future experimentation is separately requested, prioritize decision windows with *actual creatures already in hand* or visible executable combo/survival consequences; avoid retesting this same three-state no-follow-up hypothesis.
