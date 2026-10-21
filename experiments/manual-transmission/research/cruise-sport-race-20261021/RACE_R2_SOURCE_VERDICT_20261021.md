# Manual Transmission RACE R1/R2 — independent frozen v0.7 conversion source verdict

Date: **2026-10-21**  
Disposition: **PASS_ORIGINAL_RACE_RESOURCE_CONVERSION_KEEP_R5**  
**No deck swap, default policy update, RACE promotion, main merge or new full game.**

## RACE R1: all ten source-bound original games

- Protocol: `RACE_R1_ORIGINAL_CONVERSION_PROTOCOL.json`; exact head `806d4ec3384c260981c216c5b2c923715e63ecf3`.
- [Run 39682033579](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39682033579) — SUCCESS; artifact ID **12383947635**, immutable ZIP SHA-256 **7a284a76af232684a59fe8a78d3686bab46235236513b19e1ed72264587825d2**, independently re-hashed from the original downloaded ZIP.
- Ten original R1/R6 four-seat **R5 self-mirror** games, all ten immutable ZIP/source SHA identities, **33,440 accepted actions**, **7,827 original R5 priority decisions** uniquely aligned, no rejected action and no new sampled Commander game.
- Frozen 100-card v0.7 includes **Ancestral Statue, Walking Ballista, Purphoros, God of the Forge**. `Palinchron` is **not** in the exact v0.7 list; existing deterministic card scenarios involving it are **not** proof of an actual deck line.
- Observed original accepted actions: **73 Ancestral Statue casts**, **13 Walking Ballista casts**, **8 Purphoros casts**. Original public action events captured **54 Statue battlefield→hand returns**, **146 Purphoros-to-player damage events** totaling **296**, **0 Walking Ballista-to-player damage events**, and **0 game-end results with same-result Purphoros or Ballista player damage**.
- Exactly three originally observed same-seat single-global-turn Statue cast chains had 19, 17, and 21 casts (all tied to exact original R1 seed `202610202112`). High repetition alone does not prove a strategic mistake or an empty loop.

## RACE R2: all 57 actual cast intervals in those three chains

- Protocol: `RACE_R2_STATUE_ORIGINAL_VALUE_PROTOCOL.json`, immutable original source parent `806d4ec3`; two verified R1 original archives: CONTROL artifact **12291060323** / SHA-256 `3379a5376d961d54b8c5883207c8499dac66d373765f8c3ff8a7d032cb62c853`; Challenger A artifact **12290286293** / SHA-256 `a614eb9998b25dd34803434bb9ca80cf113b545483a185eac49cfcd419c7fd09`.
- The first executable attempt [39682712307](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39682712307) correctly stopped on a Python multiline-expression **syntax error before any data aggregation**. It did not produce a behavioral result. A one-expression syntax fix was made; no metric, test case, protocol or game scope changed.
- [Run 39682826996](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39682826996) — SUCCESS, exact executable head `0f2456697be8a67ab731aa4ec74b0cd58a84f3fe`, artifact **12384358071**; independently re-hashed ZIP SHA-256 **ef6f7765b5bf7e494eb88a0e647989f6132e2d9415737bf7f59a037f0b4980c9**.
- All **57/57** accepted original Statue cast intervals were accounted for (CONTROL seat 3 / turn 36 = **19**, A seat 1 / turn 38 = **17**, A seat 3 / turn 36 = **21**). All **57** intervals showed at least one observed material card draw, Animar counter increment or damage event. **Zero** were event-empty under the frozen definition.
- Source interval observations total **137 cards drawn by the caster**, **77 Animar +1/+1 counter increments**, and **54 self-return events**. The three respective seat-level interval breakdowns were:
  - CONTROL / mirror-3 / turn 36: **19 Statue casts**, **55 caster draws**, **26 Animar counters**, **0 event-empty intervals**.
  - A / mirror-1 / turn 38: **17 casts**, **24 draws**, **23 Animar counters**, **0 event-empty intervals**.
  - A / mirror-3 / turn 36: **21 casts**, **58 draws**, **28 Animar counters**, **0 event-empty intervals**.
- **Zero Purphoros/Ballista player damage and zero game ends** were observed inside those 57 cast-to-next-cast source intervals.

## Technical and scientific limits

The event intervals partition historical source actions; they do **not** prove every draw or counter increment was caused *specifically by* Ancestral Statue, nor that continuing the line optimizes survival against adversarial opponents. An option to cast a combo card is not proof of an unchosen winning line. All original opponent seats used the same R5 pilot, are dependent, and were not a diverse competitive cEDH pod.

The actual evidence **contradicts the provisional suspicion of barren loop spam**: these long original cast sequences accumulated observable resources. Do not penalize repeat Statue casts by a generic action-count cap that would destroy draw engines, absent a real rules/strategy counterexample. Preserve R5 Ballista/Statue/Pact source behavior and independent CRUISE R9 opt-in.

## Disposition / next gate

**KEEP v0.7 hardware and R5 base policy.** There is no accepted new SPORT or RACE policy modification from these audits. The earlier September seven-opponent RACE overlay and same-hardware elasticity qualifications are historically accepted and remain separate from these October opt-in R5-derived pilot-lane studies.

Next scientific work should either (a) register a **specific falsifiable actual-deck combo failure** with an opposing response and true terminal/finite exit, to support a one-variable RACE pilot challenger; or (b) stop policy microtuning and work on **Phase 2 execution capability** for frozen whole-deck competitive opponents, replay, independence and admission. Do not equate a constructed single-player terminal with a valid multiplayer cEDH win rate.

**Budget:** Precision full games **10/12**, **2 reserved**, new games **0**. Phase 2 official capability **0/36**; primary **0/864**. `main`, `AiProfile.CURRENT`, original R5, CRUISE R9, v0.7, Challenger A, and `ManaSolver` are unchanged.
