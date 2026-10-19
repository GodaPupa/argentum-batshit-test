# R5 end-step Pact survival correction — exact two-game comparison

**Verified source:** `e2e92a199bb2c165625bf1505089e8420d91d8b2` (tree `449e68fc9a99c13f26ca7f90692c952a1ff17956`), isolated branch `gameplay/manual-r5-endstep-debt-20261019`, parent and frozen passing R5 source `9d50d7efd0192e3d04b7bc2f57789e6508e536f7` (tree `10b86e506d988b90c245a9633b12ef265da93ad7`). No merge to `main`; v0.7 deck and `AiProfile.CURRENT` unchanged.

## Runs and immutable artifacts

| | Original composed R5 | End-step corrective successor |
| --- | --- | --- |
| GitHub Actions | [39290732616](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39290732616) | [39293363796](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39293363796) |
| Outcome | SUCCESS | SUCCESS |
| Integration AI | 45/45 | 46/46 (one new end-step regression) |
| Rules engine | 23/23 | 23/23 |
| Set Pact mechanics | 4/4 | 4/4 |
| Four-player smoke | 1/1 | 1/1 |
| Total | 73/73 | 74/74 |
| Original artifact ID | `12206306434` | `12206343752` |
| Original ZIP SHA-256 | `f6ebea7a6af4f14f6ff68f131d20fb7ccecb2a464c1a2ac8fa9de35639af1032` | `0562942a8b66a803e6e35265b5005220d3676b21869cca935e5cb1f2f66f71a7` |
| Artifact bytes | 791,194 | 892,164 |

Both ZIP hashes were calculated from the downloaded original bytes and match GitHub's recorded digests. Both runs retained full action logs, AI decision insights, JUnit reports, game summaries and state snapshots.

## Root cause and direct replay evidence

The previous R5 policy assessed `Pact of Negation` against a raw pass score before the greedy Strategist's disabled-hold-policy end-step pass discount of **1.5**. A penalty of only **1.0** let an unfunded Pact beat the real pass threshold.

Original run:
- **Turn 11, action 360:** Seat 2 (`manual-mirror-1`) casts its own Veil of Summer.
- **Action 361:** Its R5 pilot casts Pact *on its own Veil*. Original insight raw pass `24.4445833333`, discounted pass `22.9445833333`, selected Pact `23.4445833333`.
- **Turn 14, action 468:** Seat 2 loses to unpaid Pact at **40 life**.

Corrected run, **same seed `202610191608`**:
- Exactly the same first **360** accepted actions (both the submitted action streams and their earlier decisions are identical).
- **First difference at action 361:** Seat 2 submits `PassPriority`, not Pact.
- This player survives well beyond the prior unpaid upkeep, later casts a legitimate defensive Pact at action **1134** countering an opponent's Ancestral Statue, and pays `{3}{U}{U}` at action **1356** with real `ManaSpentEvent` (`blue=2, red=1, green=2`).
- Seat 2 eventually leaves at turn 35, action **1651**, due to **LIFE_ZERO**, not Pact.

The correction changes only `ManualPactDebtAdvisorModule.kt` (score floor raw pass minus 3 instead of minus 1), adds one synthetic end-step debt regression to `ManualPactDebtRegressionTest.kt`, and adds an isolated gated workflow. It does not rewrite the previously validated R5 archive or deck.

## Gameplay comparison

| Measurement | Original R5 | Corrected follow-up |
| --- | ---: | ---: |
| Accepted / submitted actions | 1,272 / 1,272 | 2,571 / 2,571 |
| Rejected actions | 0 | 0 |
| Terminal global turn | 32 | 58 |
| Winner | Seat 4 (`manual-mirror-3`) | Seat 1 (`manual-mirror-0`) |
| Winner final life | 38 | 12 |
| Pact-caused player eliminations | 1 | **0** |
| Combat/life-zero eliminations | 2 | **3** |
| Commander casts, seats 1-4 | 0 / 0 / 1 / 4 | 0 / 1 / 1 / 1 |
| Winner commander casts | 4 | 0 |
| Legal commander offering windows, seat 1 | 7 (all declined) | **13 (all declined)** |
| Real Walking Ballista casts | 0 | 1, X=1 (action 1322) |
| Real Walking Ballista activations | 0 | 1 (action 1389, targeted opponent Animar) |
| Soul of the Harvest optional-draw decisions | 0 | 0 |

*Opportunity windows can repeat within one turn; not 13 separate turns. No sampled Statue/Ballista terminal combo occurred. End-step change gives a strict deterministic one-seed causal comparison for this exact decision, not a competitive win-rate estimate.*

## Corrected match terminal event receipts

- Seat 2, `manual-mirror-1`: turn 35, action 1651, `PlayerLostEvent(LIFE_ZERO)`, final `-2`.
- Seat 3, `manual-mirror-2`: turn 51, action 2355, `PlayerLostEvent(LIFE_ZERO)`, final `-8`.
- Seat 4, `manual-mirror-3`: turn 58, action 2571, `PlayerLostEvent(LIFE_ZERO)`; `GameEndedEvent(winnerId=manual-mirror-0, reason=LIFE_ZERO)`, final `-1`.
- Seat 1, `manual-mirror-0` wins at 12 life.

## Remaining limitations and next critical path

1. **Survival regression is now fixed for the verified end-step counterexample; full strategic survival remains unproven** across additional games.
2. Highest-impact next decision: **Commander deployment**, especially seat 1 passing on all 13 eligible scored opportunities before winning without casting Animar. Verify whether choices were appropriate under real board state before overriding the AI.
3. Optional draws at small library sizes were audited in separate R5 3/3 tests, not improved or re-exercised by either new game.
4. Combo reliability has accepted isolated 8/8 coverage. Actual two new sampled wins were **not** Ballista terminal kills; do not conflate constructed capability with observed frequency.
5. The corrected follow-up is **opt-in only**. `main`, frozen v0.7 deck, `AiProfile.CURRENT`, and C4/D2 official activation remain untouched. No 40-game batch launched.
