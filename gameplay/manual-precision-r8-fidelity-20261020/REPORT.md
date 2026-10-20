# ARGENTUM — Animar R8 Mana Base Rules Fidelity

**STATUS: R8 CLOSED IN ISOLATED RESEARCH — BOTH ENGINE FIDELITY DEFECTS REPAIRED, ALL TARGETED AND R5 SAFETY GATES PASSED. ZERO GAMES, MERGE OR PROMOTION.**

Research report dated 2026-10-20; updated only after source-bound original evidence. This document is experimental and does not replace accepted frozen gameplay.

## Original authorities

- Repo: GodaPupa/argentum-batshit-test. Frozen Manual Transmission v0.7 blob c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89; Challenger A blob 7984a2e87d8ddbe023d45620414d25a61691a040.
- Original R1 four complete Commander games: run 39477314282, source 84e6347bbcdfa2990eb304051771007daf849ce9.
- Original R6 six complete games: run 39498572460, source 53a5162eb3659310b1878894a90ae58c244a6745. Ten completed games total; zero rejected actions. No new full games occurred in R7/R8 and two slots remain unused under the original twelve-game ceiling.
- R7 forensic basis: branch research/manual-precision-r7-manabase-attribution-20261020, head cb6d0677a7c869a4cb4d5a7a567326ba955873eb, report and ENGINE_FIDELITY_FLAGS.csv. R7's seven-land package remains INCONCLUSIVE as to gameplay superiority.
- AiProfile.CURRENT, manual-transmission-r5-survival-opt-in source blob ef5ba9dacb02bbf2a00303c9ee4724565769385a, and original main 37a603617a03dfd2d81b39dae004f7f336a70dfc unchanged. No original R1/R6 actions, archives, winner or seed was revised.

## RED evidence — real-engine failures on original source

Original test-only reproduction branch research/manual-precision-r8-rules-fidelity-20261020, workflow run 39511363787, source 96d777375708130e2c97592292171f4d9e445c20, original run FAILURE by design:

1. Mana Confluence: direct activated mana and creature-spell AutoPay worked, but paying nonspell activated ability costs (Temur Sabertooth and Purphoros) failed two independent life-total assertions. 4 tests: 2 PASS, 2 FAIL. Original artifact 12305633767 ZIP SHA-256 68ec800cca30026af50e20de30159f4d03b0d15c120db6f4ebe954aa38dd7c1c.
2. Misty Rainforest and Wooded Foothills: existing basic-land search passed for each, but neither fetch offered an actual YesNoDecision for any of three eligible typed shocks in either pay/decline case. 14 tests total: 2 PASS, 12 FAIL. Original artifact 12305883337 ZIP SHA-256 44d31e962307a0ca0965dfe65c1ccc34afb06ccffdebe2210d30822ab9309b01.

All original RED artifact ZIPs have been re-downloaded and independently SHA-256 checked. The failed original engine behavior has not been rewritten as passing or silently edited.

## Lane A — Mana Confluence: PROVEN RULES DEFECT, isolated repair

Original observations (candidate Challenger A, source R6 run): seed 202610202141 action 2292 activating Temur Sabertooth; seed 202610202142 actions 1210 Spellskite and 2712 Purphoros. These paths auto-tapped Mana Confluence with no PayLife(1) event/life change. The card definition correctly says pay one life as part of the ability's activation cost.

Original root cause: ActivateAbilityHandler's nonspell auto-payment path hand-tapped sources without executing their matching printed mana ability's non-tap activation costs. Separately, a unique any-color ability can be matched ambiguously to a generic mana payment if no producedColor is recorded.

Original narrow source repair: route nonspell AutoPay through ManaAbilitySideEffectExecutor.tapSourcesWithSideEffects; use a unique available printed mana ability when the solver does not record a color, preserving the source activation event and charging life only for the ability actually used. The earlier failing Temur test fixture incorrectly left Llanowar Elves available as an alternative mana source; the corrected source-bound fixture marks it summoning-sick, preserving both target availability and the required Confluence payment. No test assertion was weakened.

Passing targeted validation:
- Run 39513972549, branch research/manual-precision-r8-mana-verified-20261020, SUCCEEDED. 4/4 Mana Confluence paths and City of Brass 1/1. Original artifact 12307097063, ZIP SHA-256 32393b468fbbf1fcb3b30f0fcbce0f5d1947a3755585fba61867fe400cf60f19.
- Combined source run 39514421498, source c3e3802185049a583e752a8aa8e068b34ec094ec, tree 4556fe31f59199b2ccbc383d9e8162eeb6bef396: Mana Confluence 4/4 and City of Brass 1/1 passed. Original artifact 12308130901 SHA-256 c2f9caf3b6c8d8b0dc4bedb718c40303f0f83ce0be9c26619d0b5d310f6df378.
- Further inspected PaymentStrategy.Explicit(nonspell activated ability) had an independent hand-tap path that also omitted side costs. Additional isolated research/manual-precision-r8-explicit-mana-20261020 source commit 91d8adbeb9a09d0944e365e20e82347daf79c5e3, tree 1530818bd14762f9dabf80085336ae80fbb2c1bf, routes the specifically selected source through the shared mana-ability tap/cost helper. [Run 39515144984](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39515144984) SUCCESS, FIVE of FIVE Confluence tests including this explicit payment, original artifact 12307603407 SHA-256 d9b13af14805e14f53957b829b35cb867c6083ef9db27721630113120c8825d8. Exactly one life charged in the explicit-source case.

These changes are isolated research commits, not merged into production or main; no accepted historical gameplay was replayed or 'fixed' retroactively.

## Lane B — fetched shockland entry: PROVEN RULES DEFECT, isolated repair

Original source-bound evidence: R6 seed 202610202141 action 227 Misty Rainforest to Steam Vents; same seed action 2231 Wooded Foothills to Breeding Pool; seed 202610202143 action 716 Wooded Foothills to Steam Vents. The fetch/search moved the shockland into battlefield but never asked the player if they wished to pay two life.

Root cause: off-stack MoveCollectionEffect battlefield-entry flow deferred to ZoneTransitionService's tapped-default for a printed shock without running a suspendable pre-entry payment choice. The original hand-play shockland choice flow already worked.

Isolated repair in the combined R8 source:
- PreEntryContinuation and PreEntryCoordinator suspend before eligible fetched shockland moves, issue real YesNoDecision, revalidate source/controller/life, and if accepted call LifePaymentService before commit.
- MoveCollectionExecutor threads the per-entry answer into ZoneEntryOptions; ZoneTransitionService bypasses only the card's own optional tapped clause when payment was accepted, preserving independent forced-tapped effects.
- Both the search/zone transition and choice are handled as real engine continuations, with preserved card identity and original fetch one-life cost. No synthetic shock effects or simulated answers were used.

Original GREEN source: run 39514421498, commit c3e3802185049a583e752a8aa8e068b34ec094ec (source tree 4556fe31f59199b2ccbc383d9e8162eeb6bef396), 3 parallel jobs SUCCESS. Fetched shock and hand-play test groups: 20/20 passed. Original fetch artifact 12307776585 ZIP SHA-256 e64e73c5e61ecba87e45d2b80c874a2fd7f4f1e67802e1b4f45fc18f0e787460. Explicitly checked 3 shocks x 2 fetches x 2 pay/decline outcomes, plus each original fetch basic test and each shockland played from hand both ways. No failed tests. Original ZIP re-downloaded, SHA-256 verified, and JUnit XML counts independently checked.

## R5 and engine protection gates

The same combined run 39514421498 passed 46 original AI and deck-initializer regressions, including Pact debt/survival, Ballista, Statue, Encore/combat, resolution payment and the Manual R5 composition plus CONTROL/A exact-slot initializer. Original AI regression artifact 12307007776 ZIP SHA-256 12b0277801edb8fd0fc92703601bcba4fc1d3ae0c07866721a341c6b2752322c, independently SHA-256 verified.

The additional explicit-source patch postdates that combined run; a source-bound repetition of the same R5 protections has been dispatched as run 39515726136 on branch research/manual-precision-r8-explicit-safety-20261020, commit 62e17ae7f5987dca2f8a9dba9ba01056ba74237d (tree 6f8f0fbc0dcbf71521582cc1e6a858c3c9aab400). **Run 39515726136 completed SUCCESS**, including the identical Pact/Ballista/Statue/Encore/resolution/R5 regression suite and 3/3 original CONTROL+A catalog/initializer tests. Original artifact ID **12306923894**, ZIP SHA-256 **9554700b8f348af432809937e18e3d1a4eef7fdc94195041d64215632d84f2c6**, independently downloaded and SHA-256 checked. The log records **46 passing AI/initializer tests** and zero failures; final JUnit XML contains the 3 initializer tests (the earlier Gradle cleanTest writes are preserved in the job log). This is the final exact explicit-source safety gate.

## Decision and restrictions

- Lane A: PROVEN RULES DEFECT — REPAIRED IN ISOLATED SOURCE; direct, spell AutoPay, nonspell AutoPay and explicit activated-cost payment regressions passed without duplicate life charges.
- Lane B: PROVEN RULES DEFECT — REPAIRED IN ISOLATED SOURCE; fetched shock life/tapped/search continuation regressions passed alongside original fetch and hand-play tests.
- Acceptance scope: experiment-local regression validation. **No production merge, official game qualification, deck adoption, or retroactive corrected outcomes**.
- Remaining caution: source fidelity for fetched shocks outside MoveCollectionEffect, low-life edge cases, multiple simultaneous replacement effects, and complicated explicit mana-source restrictions require additional held-out coverage if later applicable. Neither original ten-game win-rate comparison nor paper-build inferential confidence is magically upgraded by correcting the engine.
- Preserve 10/12 full-game budget consumed; last two remain untouched. Challenger B remains separate/blocked pending a rules-faithful Fierce Guardianship implementation; Challenger C remains separate.

**FINAL DISPOSITION: Both original issues PROVEN RULES DEFECTS, both REPAIRED AND REGRESSION-VERIFIED IN A PINNED ISOLATED EXPERIMENTAL ENGINE SOURCE.** One ancillary explicit-source payment defect was also repaired and passed a new regression. All three R8 experimental safety jobs, the five-path Mana Confluence gate, and the follow-up R5 protection gate passed. No original R1/R6 game is a validation of the repaired engine; those ten game results remain historical evidence and must not be silently recomputed. The full-game budget remains 10 used and 2 unspent.

**NEXT:** Keep these fixes isolated and propose an independent source-bound rules-engine review/qualification gate before any merge. Then decide whether a corrected-engine prospective comparison needs fresh, separately authorized full-game allocation. The last two original slots alone are insufficient to infer superiority of seven substitutions; do not spend or reroll them solely to chase a winner.
