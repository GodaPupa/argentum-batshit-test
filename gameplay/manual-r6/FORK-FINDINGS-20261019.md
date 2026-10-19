# R6 Animar — bounded alternative-choice continuations (October 19, 2026)

## Scope and verdict

**VERIFIED COMPLETE:** one new diagnostic-only test, 1/1 pass. Four isolated 160-action, rules-engine-legal AI continuations, originating at R5 action 1813 (turn 40) and action 2368 (turn 52). No rejected actions; no fabricated terminal winners; no forced opponent pass. Each fork differs by only its documented legal first action, then all four unchanged R5 AI pilots freely choose subsequent actions.

**NO GENERAL COMMANDER BONUS ACCEPTED.** The paired cases give contradictory evidence: turn-40 Animar looks better over the bounded horizon while turn-52 Animar looks substantially worse. These are exploratory outcome differences, not full-game competitive performance or validation of a new R6 policy.

## Exact identities

- Accepted R5 source `e2e92a199bb2c165625bf1505089e8420d91d8b2`, passing run `39293363796`, artifact `12206343752`, SHA-256 `0562942a8b66a803e6e35265b5005220d3676b21869cca935e5cb1f2f66f71a7`.
- Exact 13-opportunity state archive: R6 audit source `30b3f3332b1306125cd4d655c79db66c0f209252`, passing run `39304199826`, artifact `12214265732`, SHA-256 `985f86cc30893b6243b429b033794d790653fd5bae6a42d2c4a5a1c00a49d7eb`.
- **This fork source**: `2996a2623a77052473d4f991320b8ed69d47be21`, branch `gameplay/manual-r6-animar-forks-20261019`.
- **This run**: [39305605765](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39305605765) — SUCCESS, test `ManualR6AnimarForkAnalysisTest` 1/1 pass, artifact `12212839328`, SHA-256 `846d61c0bc6711f4881ed3baeeac2a2e718600e940a1916c57dad46e830ead95`; downloaded ZIP independently SHA-verified.
- Source files: `ai/src/test/kotlin/com/wingedsheep/ai/engine/ManualR6AnimarForkAnalysisTest.kt`, `.github/workflows/manual-r6-animar-forks-20261019.yml`. No production Kotlin file changed.
- Artifact has full `actions.jsonl`, exact initial/final `GameState` snapshots and per-branch `summary.json`.

## Paired observations

All four arms used exactly 160 accepted real-engine actions and had no rejection. Each ended at a deliberately bounded, non-terminal state. Evaluation scores are perspective of winning R5 Seat 1 (`manual-mirror-0`) and **are not wins**.

| Original opportunity | First action | End global turn | Seat 1 life | Opponent life | End evaluator | Terminal? |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Action 1813, global turn 40 | **Original Fyndhorn Elves** | 44 | 20 | Seat 3 = 19; Seat 4 = 38 | **-58.702** | No |
| Action 1813, global turn 40 | Hypothetical Animar | 44 | 20 | Seat 3 = 19; Seat 4 = 38 | **-54.453** | No |
| Action 2368, global turn 52 | **Original Vizier of the Menagerie** | 57 | 12 | Seat 4 = 10 | **83.133** | No |
| Action 2368, global turn 52 | Hypothetical Animar | 56 | **1** | Seat 4 = **22** | **16.311** | No |

- T40 bounded evaluator difference: hypothetical Animar **+4.248** versus original Fyndhorn. It had Animar on battlefield with an actual +1/+1 counter, while original Fyndhorn continuation had deployed Temur Sabertooth. Equal life and opponents, but a different later path. This is a candidate investment opportunity, not a proved longer-game improvement.
- T52 bounded evaluator difference: hypothetical Animar **-66.822** versus original Vizier. In the Animar arm the commander was ultimately back in the command zone; the original arm progressed through Vizier, Reckless Bushwhacker, Nulldrifter, Bane of Progress and combat. Pilot life fell to 1 under the counterfactual. This is an important regression guard against broadly encouraging Animar casting late.
- Histories diverge after the controlled first action; no hidden library identity is credited to policy, and original AI controller memory cannot be reconstructed. The branches represent a *fresh-controller fork experiment*, not an exact alternate history or a pair of comparable finished games.
- The resource cap prevents a sampled terminal-winner claim. Scores at different global turns / board circumstances are heuristic diagnostics, not win probabilities.

## Operating constraints preserved

- `main` unchanged; Manual Transmission v0.7 unchanged; `AiProfile.CURRENT` unchanged.
- R5 policy and approved Pact survival, payment, Ballista and combo repairs inherited unchanged; prior passing receipts remain authoritative. Those already-passing regressions were **not gratuitously rerun**, since there is no production source modification.
- **R6 policy correction: NOT EXECUTED.** No tested narrow causal scoring rule yet distinguishes the potential T40 gain from the real T52 downside without relying on future opponent cooperation or unknown cards.
- **R6 composed four-player full game: NOT EXECUTED.** No validated behavioral candidate to qualify.
- Official C4/D2 remains separate and no official activation was requested or performed.

**Single next critical-path action:** isolate a safe, explainable, information-legal prospective commander-development witness at the T40 snapshot, require a negative control from the T52 snapshot, then only if both pass implement a new opt-in advisor and run a bounded real four-player game. A flat commander bonus or forced cast-count increase would violate this gate.
