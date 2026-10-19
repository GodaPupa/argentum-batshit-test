# ARGENTUM — R5 Survival Intelligence: verified composed four-player gameplay

**Disposition:** VERIFIED COMPLETE for the bounded **opt-in R5 integration/gameplay workflow**, with a newly observed self-elimination policy defect explicitly *open*. This report does **not** claim that all survival behavior is correct, nor authorize C4/D2 or promotion to `main`.

## Immutable run and source bindings

- Repository: `GodaPupa/argentum-batshit-test`
- Integration branch: `gameplay/manual-r5-integration-20261019`
- Integration source: `9d50d7efd0192e3d04b7bc2f57789e6508e536f7`
- Source tree: `10b86e506d988b90c245a9633b12ef265da93ad7`
- Original parent: `2095949f7a15aa5b329fd73970a436e705c58abb`
- GitHub Actions run: [39290732616](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39290732616) — completed **success**, one attempt
- Artifact: `12206306434`, `manual-r5-integration-39290732616`, 791,194 bytes.
- Original artifact ZIP SHA-256, independently recalculated after download: `f6ebea7a6af4f14f6ff68f131d20fb7ccecb2a464c1a2ac8fa9de35639af1032`.
- Frozen deck Git blob: `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`; `AiProfile.kt` blob `180918b2d7a59309e7b0617f2579fb2a6d06b7d5`; opt-in R4 candidate blob `550f3d160e783ddb71c19bb9b045037165b69172`.
- R4 source archive SHA-256: `bd541f2a72aa7ec229c115a83c4d59ef44d35131ee11322ac52a10f9e9d3c6d0`.
- The original frozen deck, `AiProfile.CURRENT`, and accepted R4 source remain unchanged.

## Regression receipts

From original JUnit XML preserved in artifact:

| Stage | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| AI Pact / Ballista / composition / payment | 45 | 0 | 0 | 0 |
| Rules engine GatedEffect + repeated mana payment | 23 | 0 | 0 | 0 |
| Set PactOfNegation / unpaid mechanics | 4 | 0 | 0 | 0 |
| Four-player smoke | 1 | 0 | 0 | 0 |
| **Total** | **73** | **0** | **0** | **0** |

The original Pact archive is reproduced with correction at R4 decision indices 47, 393 (UNFUNDED → PassPriority), 588 (FUNDED → retain Pact), and real upkeep payment at 619: `ManaSpentEvent` paid five mana (`U=2, W=2, G=1`) from **three** tapped permanents, including multi-mana aura production. It is improper to require five unique source taps. Constructed Pact/Ballista protection test also passed, as did original accepted combo validation 8/8 and optional draw audit 3/3 in separate lanes.

## New composed gameplay: actual engine decisions, no forced passes

- Fixed seed: `202610191608`.
- Four independent 100-card Animar Commander decks from frozen v0.7, all players `manual-transmission-r5-survival-opt-in`.
- One sampled game, **COMPLETE**, **1,272 submitted / 1,272 accepted**, **0 engine rejections**, terminal on **global turn 32**, 35,819 ms simulation duration.
- Winner: `manual-mirror-3` (seat 4), life **38**.
- Final life: seat 1 `-22`, seat 2 `40` (left game due to Pact), seat 3 `-21`, seat 4 `38`.
- Eliminations: seat 2 unpaid Pact at **action 468 / turn 14**; seat 1 life zero at **action 1150 / turn 28**; seat 3 life zero at **action 1272 / turn 32**, yielding seat 4 win.
- Commander casts: seat 1 **0**, seat 2 **0**, seat 3 **1**, seat 4 **4**. Observed materialized legal-commander priority windows: seat 1 **7, all declined**; seat 2 **0**; seat 3 **2, one declined**; seat 4 **5, one declined**. Windows may repeat and must not be counted as independent full turns.
- Combo evidence: **1 Ancestral Statue cast** at action 1073, **0 Walking Ballista casts**; no Ballista terminal combo in this sampled match. Winner obtained via real combat (with earlier game actions including Purphoros damage). Constructed combo wins are different evidence, not claims about this mirror.
- No Soul of the Harvest optional-draw decision in this sample; the preexisting audit remains accepted, but no improvement was measured in this game.
- Actual decisions, priority passes, targets, cast payments, combat, life events and final game state are preserved as the artifact's `r5-output/smoke/seed-202610191608/` files.

## Critical new Pact self-elimination — observed failure of the survival policy

The active R5 policy has a **new, real self-elimination counterexample**:

- **Action 360**, turn 11, seat 2 casts its own `Veil of Summer` on another player's end step.
- **Action 361**, same player casts `Pact of Negation` *targeting its own Veil of Summer*, with an unfunded next-upkeep cost.
- **Action 468**, turn 14, the engine correctly applies the unpaid Pact loss. This is an AI policy defect, not an engine rejection or incorrect rule implementation.
- Original insight shows raw pass score **24.4445833**, actual adjusted pass threshold **22.9445833** (−1.5 end-step discount); Pact override score **23.4445833** (raw pass minus only 1), so a cast that should have been rejected won by **0.5**.
- Diagnosis verified against the exact `Strategist.kt` control flow: `CardAdvisor` receives unadjusted `passScore`, while `Strategist` later applies the end-step pass discount when `holdPolicy` is disabled.

**Corrective successor (unaccepted at report time):** isolated branch `gameplay/manual-r5-endstep-debt-20261019`, source `e2e92a199bb2c165625bf1505089e8420d91d8b2`, tree `449e68fc9a99c13f26ca7f90692c952a1ff17956`, parent **exact successful R5 source** `9d50d7e...`. Narrows the unfunded Pact floor to raw pass minus 3.0 and adds a targeted opponent-end-step self-Veil regression. CI run [39293363796](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39293363796) is the separate gate; **do not assume it passed without checking**.

## Remaining decisions

1. **Priority:** validate the Pact end-step scoring correction against the added regression and actual gameplay trace before accepting this follow-up.
2. Then pursue intelligent commander deployment: seat 1 had seven materialized legal scoring windows but never cast its commander before elimination.
3. Optional-draw near-deckout risk remains an audited but not yet implemented improvement; R5 sampled play made no such decision.
4. No automatic 40-game batch; one-game results do not establish population win rates.

**Qualification separation:** All changes are opt-in gameplay development; official C4/D2 is not qualified or activated. No main, frozen deck, or CURRENT changes have been requested or performed.
