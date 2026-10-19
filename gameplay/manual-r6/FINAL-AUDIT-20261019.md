# ARGENTUM — R6 commander-deployment decision audit (2026-10-19)

## Disposition

**VERIFIED COMPLETE: diagnostic replay only.** Exact R5 game replay, thirteen commander scoring windows, commander tax, available mana, battlefield, actual competing actions and original terminal result validated. **NO PROVEN SUBOPTIMAL COMMANDER REFUSAL.** An isolated R6 behavioral correction is therefore **NOT AUTHORIZED BY THE EVIDENCE** and was not invented. No new R6 four-player game was launched, because no corrected policy passed its prerequisite gate.

## Bound identities and preserved receipts

- Repository: `GodaPupa/argentum-batshit-test`.
- Accepted R5 opt-in source: `e2e92a199bb2c165625bf1505089e8420d91d8b2`.
- R5 run `39293363796`, artifact `12206343752`, ZIP SHA-256 `0562942a8b66a803e6e35265b5005220d3676b21869cca935e5cb1f2f66f71a7`.
- R6 new test: `ai/src/test/kotlin/com/wingedsheep/ai/engine/ManualR6OpportunityReplayTest.kt`; published on isolated `gameplay/manual-r6-animar-diagnostic-20261019`.
- First diagnostic source `a539d6ff7aab907afd9df0d1280874259fa2f6c4`, run `39303331585`, artifact `12213525058`, ZIP SHA-256 `fc80e7cb6dbf789948dfd222102b47eaa5d00bb7220b2ef861a1b501b92f16e8`: **FAILED** Kotlin nullable JSON output compilation; failed evidence retained.
- Corrective test-only commit `30b3f3332b1306125cd4d655c79db66c0f209252`, run [39304199826](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39304199826): **PASSED**. Artifact `12214265732` SHA-256 `985f86cc30893b6243b429b033794d790653fd5bae6a42d2c4a5a1c00a49d7eb` verified against downloaded ZIP.
- Output: `r6-output/opportunities.json` (all 13 full states), `audit-summary.json`, `ability-bindings.json`, `state-before-1813.json`, `state-after-animar-1813.json`, `state-before-2368.json`, `state-after-animar-2368.json`, `validation.log`, JUnit.
- Preserved frozen Manual Transmission v0.7, original R5 opt-in candidate, and `AiProfile.CURRENT`. No `main` update and no official C4/D2 activation.

## Test outcome

- **1/1** targeted archival replay tests PASS; exactly **2,571 / 2,571** original ActionProcessor game actions replayed with **zero engine errors**.
- Verified original final outcome: four-player game, winning `manual-mirror-0` (Seat 1), global turn 58.
- Verified `13 / 13` published Animar scoring decisions with exact original submitted actions, command-zone legal offers, rules-engine mana-source availability and cost.
- All 13 commander tax counters exactly **zero** (Animar never previously cast).
- No true gameplay regression ran from this new branch because no production behavior changed.

## Real decision results (original scoring)

| R5 action | Turn | Winning Seat's selected action | Margin over Animar | Evidence classification |
| ---: | ---: | --- | ---: | --- |
| 434 | 13 | Wood Elves | 1.820 | 3 colored-capable lands; Wood Elves provides development; no defect proven |
| 816 | 21 | Wretched Gryff emerge | 3.840 | Significant immediate emerge benefit; no defect proven |
| 1025 | 25 | Decimator of the Provinces emerge | 4.901 | Offensive board benefit; no defect proven |
| 1316 | 29 | Fauna Shaman | 2.035 | Creature tutoring and board development; no defect proven |
| 1517 | 33 | Eternal Witness | 1.275 | Recovery/recursion; no defect proven |
| 1702 | 37 | Malevolent Hermit | 0.925 | Interactive protection; no defect proven |
| 1813 | 40 | Fyndhorn Elves | **0.100** | Four untapped lands. Original Fyndhorn produced mana in **five later actions** (1962, 2064, 2219, 2372, 2428); marginal but justified as observed contribution |
| 1921 | 43 | Temur Sabertooth | 3.595 | Bounce/resilience; no defect proven |
| 2214 | 49 | Glen Elendra Archmage | 3.212 | Counterspell protection; no defect proven |
| 2368 | 52 | Vizier of the Menagerie | **0.167** | Original R5 ranking close, but independent immediate engine-board value: Vizier +3.265 vs Animar +0.435; no defect proven |
| 2428 | 54 | Nulldrifter | 6.415 | Higher-valued threat/draw; no defect proven |
| 2494 | 56 | Bane of Progress | 4.672 | Higher-impact board effect during finish; no defect proven |
| 2556 | 58 | Soul of the Harvest | 6.520 | Higher-valued draw engine as winning game closes; no defect proven |

Rounded margins here are presentational; exact original scores and the actual source, mana pool, untapped sources, command tax, permanent lists, opponents and cards in hand appear in immutable `opportunities.json`.

## What the evidence establishes — and does not

The existing opt-in `ManualAnimarDevelopmentAdvisor` credits measured same-turn, engine-legal, productive creature continuations; no arbitrary static commander boost. At least some late-game options have that explicit recorded advisor note. The source does *not* project unknown future draws or invent next-turn untaps. A single winning R5 game without a commander cast is not proof of bad play: 13 offer windows are not thirteen mistakes.

The successful archived replay validates the original game state and scores, **not** the universal optimality of the R5 pilot. The turn-40 and turn-52 narrow margins are plausible leads for paired, rules-engine counterfactual work; they are not sufficient proof that a new policy is better under actual multiplayer responses.

**R6 candidate composition: NOT EXECUTED** — no demonstrated defect to repair.

**R6 new four-player gameplay: NOT EXECUTED** — candidate validation prerequisite absent; do not claim a second R5 game is an R6 improvement.

**Single next critical-path action:** bounded, evidence-labelled turn-40 / turn-52 alternative-line comparison with real engine and actual AI continuations, preserving legal payment and opponent priority, to determine whether any opt-in commander score correction is defensible. Do not merge or force commander spells to optimize a cast-count metric.
