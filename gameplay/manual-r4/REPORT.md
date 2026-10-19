# ARGENTUM — Manual Transmission R4

**Completed:** the Ballista mechanics gap is repaired, real AI-selected Statue/Ballista kills are verified, and the composed opt-in candidates completed one fresh four-player Commander mirror. Seat 4 won after **1,943 accepted actions**, with **zero engine rejections**. The mirror ended in deck-out, not a Ballista combo kill.

Exact combined gameplay source: `004ec925b3edc94f972d0141d1edb1e294e641b5`; Java 21 [run 39270737788](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39270737788); raw artifact `12195203589`, SHA-256 `bd541f2a72aa7ec229c115a83c4d59ef44d35131ee11322ac52a10f9e9d3c6d0`.

## Verified mechanics

Java 21 run [39263486325](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39263486325), source `be71b9f218d87407cd106bc7243bbe954b1d62d8`: **91 tests passed, zero failures/errors/skips**. The six runtime files are byte-identical to the previously tested unrestricted-X correction at `84105e45966c53b4c7ae647c9f48abb0ee414186`.

Sixteen new real-action cases cover X=0,1,2,3 and pre-cast Animar counters=0,1,3,6. Actual payment is `max(0, 2X − pre-cast counters)`. The cast trigger adds Animar's next counter afterward; Ballista enters with X counters. Positive-X cases remove counters to activate damage and pass priority through the engine. X=0 dies through state-based actions. Stack mana value is 2X; outside the stack X counts as zero.

Restricted-X and alternative-cost cases remain outside this validation. No new support is claimed for those paths.

## Initial AI observation

Java 21 run [39263715987](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39263715987), source `781457a3a01c32b35d5ea1175ebc0f59afbf426a`: **10 tests passed** (six discovery probes and four retained R3 payoff regressions). Passing discovery tests only establish successful observation without engine errors; the traces determine whether a combo actually happened.

These are constructed rules-engine discovery positions, not sampled 100-card Commander games. Every action after initialization was selected by an AI, including opponent priority responses.

| Position | CURRENT and R3 Statue result |
|---|---|
| Animar 6 counters, Ballista in hand, no mana, one opponent at 3 | Cast X=3 for zero mana; three activated shots; real terminal win after 14 submissions |
| Animar 4 counters, Statue and Ballista in hand, no mana, one opponent at 3 | Real terminal win after 27 submissions: Ballista X=2, Statue, damage, Ballista bounce/recast at X=3, terminal damage |
| Animar 8 counters, Statue and Ballista in hand, no mana, three opponents at 2 | Ballista X=4 on submission 1; no Ballista activations and no Statue casts; Animar combat kills one opponent on submission 27; exits combo turn after 39 submissions, life `[40,-8,2,2]`, no winner |

The multiplayer trace demonstrates a decision defect: a legal Ballista shot scored `318.2575` against passing at `319.66`, so both unchanged CURRENT and the R3 Statue candidate declined it. A successful single-opponent trace does not establish multiplayer combo execution.

## Candidate attempts

The first opt-in execution candidate, source `564d644b054555dc5f347d0c2b74df4a6515b0f0`, failed `:ai:compileKotlin` in run `39265781482`: `ManualBallistaContinuationAdvisorModule.kt:50:81 Unresolved reference 'ChosenTarget'`. No tests or games ran on that source. Its raw failed archive is preserved.

After fixing the import, source `a83cbbf4e9e944bd3619bc5aa276561147e140d6`, run `39266802615`, compiled and ran 22 tests: **18 passed, four failed**. Both four-player candidate probes failed their strict damage-before-combat requirement. The counter-building probe ended the turn after 77 actions at `[40,-11,1,1]`. The ready probe reached a terminal result after 46 actions, but used combat; it is explicitly not accepted as a Ballista execution proof. Both protection guards failed their X-action lookup before exercising the guard.

The planner and guard fixtures mistakenly assumed raw legal actions already contained a chosen X. They instead carry `xValue=null` plus `maxAffordableX`; the existing `XCostSelection.expandToX` API binds legitimate choices. This is a candidate API-use defect, not a new engine X-enumeration defect. R4 now also checks that its actual Statue response agrees with its bound-X terminal witness.

The same trace exposed a real rules-engine defect: submissions 60–62 targeted previously eliminated player 2 and were accepted. `TargetFinder` selected from historical `turnOrder` with `hasEntity`, which remained true for a player marked left; `TargetValidator` also accepted that player. The narrow repair filters out inactive player identities and rejects explicit targeting of them before paying costs. A separate test uses a real Ballista cast, activated lethal damage, an actual player-left event, rejected re-targeting, and a legal shot at a remaining player.

Source `329cdd4ecbe43e9d44901d26d88fbb5ed4d9a6f4`, run `39268490241`, passed all 21 Ballista scenarios but failed the new departed-player test at line 44: the raw `LegalActionEnumerator` still advertised the departed player. The separate `TargetEnumerationUtils` path required the same active-player filter. Corrected targeting source `58a995ea96fad4503db20b539a27175392fc7585`, run `39269758906`, **passed 22/22 tests**. This verifies the real elimination, raw legal target exclusion, explicit rejection without counter payment, and remaining player's legal damage.

AI source `bd0aeccba5b70a2e0ec20f28d1be953f9dc39578`, run `39268527721`, **passed all 22 tests**. These sources were originally published while validation was pending; only the receipts establish test outcomes. Four tests exercise retained Purphoros, finite-body, Palinchron, and Ballista exits through the R4 wrapper itself. Both player-shroud and loss-prevention guards passed using actual bound-X casts.

## Verified AI execution

The constructed four-player counter-building position starts with Animar at eight counters, Statue and Ballista in hand, no mana, and three opponents at two life each. All 92 submissions were accepted; none were forced by the test driver.

| Actual submission | Engine action |
|---|---|
| 1, 15, 29, 43 | Four zero-mana Statue casts, each generating an Animar counter; four actual self-return decisions |
| 57 | Ballista cast at X=6 for zero mana, entering with six counters; Animar's subsequent cast trigger adds its thirteenth counter |
| 66–69 | Four queued Ballista activations targeting players 2, 3, 4, 2 |
| 86–87 | Remaining activations target surviving players 3 and 4 |
| 92 | Sixth actual one-damage event completes the game; player 1 wins, life `[40,0,0,0]` |

There were six real damage events and zero combat-damage events. Opponents received and exercised AI priority windows. The ready four-player X=6 position completed in 36 accepted actions with the same six-shot terminal result. CURRENT and R3 still decline the corresponding multiplayer shots and leave two opponents alive.

This does not establish optimal sequencing: in the two-player counter-building position, R4 used 30 actions versus CURRENT's 27-action Ballista-bounce line. Both won. Planning is bounded to eight Statue casts, the top three legitimate X choices, and 160 hypothetical shots, with conditional opponent passing during planning. Actual gameplay can interrupt the plan. The verified positions are low-life fixtures, not full-life sampled Commander games, and no general infinite-combo or win-rate improvement is claimed.

## Composition

After all separate validations passed, the isolated integration branch `gameplay/manual-r4-integration-20261019` was created from preserved R3 report source `3057523a6bdd187eed83c886294edc9b9c728143`. Published source `004ec925b3edc94f972d0141d1edb1e294e641b5` passed Java 21 workflow `39270737788`: **121/121 regressions**, followed by **one successful fresh-game test**. There were zero skipped, failed, or errored tests. The 121 include the original Encore rejection/replay, all 213 archived Statue returns, R2 productive/unproductive commander decisions, retained combat tests, Ballista execution/guards, and twelve actual composition checks.

`composition-source-bindings.json` records exact Git blob identities for 26 reused paths from the validated sources. Seven new files provide the explicit composed profile, twelve composition regressions, one fresh mirror harness, and the gated workflow. The opt-in profile registers R2 Animar development plus R4 Ballista/Statue continuation and enables the Encore defender flag. R4 delegates ordinary Statue exits to the unchanged R3 advisor, avoiding duplicate card-advisor registration.

The frozen deck SHA-256 remains `2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1`. The R2 advisor remains `62ee5847e8f76d5fe4fc3ebe4f115681eecca1805c9e42c237f34915e2522650`. CURRENT's companion definitions are byte-identical to the preserved baseline; the new Encore flag defaults to false.

The workflow ran the original failures and composition regressions before its one fresh four-player Commander mirror. Seed `202610191401`; bounds were 10,000 submitted actions, 200 global turns, 1,500 actions on one turn, and five minutes of gameplay. All four pilots explicitly used `manual-transmission-r4-composed`. The same composed profile executed all four Ballista discovery positions successfully, including the 92-action Statue build and 36-action ready four-player kill. The profile also submitted the accepted archived Encore attack, took the finite archived Statue exit, and retained R2's demonstrated development decisions.

## Fresh mirror result

The compiled registry audit resolved all 100 frozen card entries and found zero setup/legality issues. Four identical decks initialized with 99 noncommander cards each, one Animar in each command zone, and 40 life. Every player kept the seeded opening seven; no hand smoother or card substitutions were used.

Final status **COMPLETE**, actual `gameOver=true`, winner `manual-mirror-3` (Seat 4). Global turn 62; 1,943 submitted/accepted actions; zero rejections/errors; 891 scored-choice records; gameplay duration 61,566 ms. All four seats appear in the full action and decision streams.

| Seat | Own turns observed | Animar casts | Recorded final life | Actual outcome |
|---|---:|---:|---:|---|
| 1 | 2 | 0 | 40 | Lost on global turn 5 to unpaid Pact of Negation upkeep |
| 2 | 28 | 0 | 34 | Failed a mandatory draw from an empty library on global turn 62 |
| 3 | 5 | 0 | 40 | Lost on global turn 16 to unpaid Pact of Negation upkeep |
| 4 | 27 | 3 | 5 | Winner |

Positive life totals for losing seats are expected for these non-life-loss eliminations. The final action contains `DrawFailedEvent(playerId=manual-mirror-1, reason=Library is empty)` and `GameEndedEvent(winnerId=manual-mirror-3, reason=CARD_EFFECT)`; the engine's reason label is preserved literally.

All three Animar casts came from the command zone, by Seat 4:

| Action | Global turn | Actual mana spent |
|---:|---:|---:|
| 596 | 21 (Seat 4's seventh turn) | 3 |
| 756 | 25 | 5 |
| 1430 | 47 | 7 |

Statue was cast once, at action 1095/global turn 33. Its one real decision, action 1100, returned **Imperial Recruiter**, retaining Statue on the battlefield. There were zero Statue self-returns and no repeated Statue combo in this mirror.

Ballista was cast twice and activated twice:

| Action | Pilot / event | Actual result |
|---:|---|---|
| 1529 | Seat 2 casts X=0 for zero mana | Enters with zero counters, dies normally; Soul of the Harvest supplies a real optional draw trigger |
| 1576 | Seat 4 casts X=2 with Animar at four counters | **Pays zero mana**, enters with two counters; Animar's cast trigger then raises Animar to five counters |
| 1581 | Seat 4 removes one counter targeting Reckless Bushwhacker | Seat 2 responds with Sylvan Safekeeper at action 1584, sacrificing a Mountain and granting shroud; this shot later fizzles legally at action 1590 |
| 1582 | Seat 4 removes the remaining counter targeting Paradise Druid | Ballista dies with zero toughness; the stacked ability still deals one real damage at action 1588 and destroys the Druid |

This reproduces the exact previously missing **Animar-four / Ballista-X=2 / zero-payment** interaction in an actual 100-card Commander game, including counters, last-known-information damage and an opponent's priority response. There were zero Purphoros casts/damage events, zero complete Statue/Ballista combo sequences, and zero terminal Ballista damage events in the fresh mirror. The confirmed combo kills are the separately recorded constructed-position tests; they are not counted as mirror-game combo wins.

`mirror-trace-audit.json` records all commander/Statue/Ballista actions, the Statue choice, Pact casts/eliminations, and both Ballista resolution paths. Full winning and losing seat traces, initial/final states and all 891 decision records are in `evidence/integration-39270737788.zip` under `r4-output/smoke/seed-202610191401/`.

## Remaining decisions and recommendation

The next clear strategic problem is **Pact upkeep planning**. Seat 1 used Pact on Wild Growth at action 47/global turn 2, then lost at action 133. Seat 3 used it on Reckless Bushwhacker at action 393/global turn 14, then lost at action 453. Seat 2's Pact at action 588 countered Beast Within and was genuinely paid at action 619. These are different real outcomes; no repair to Pact or CURRENT was added in R4.

Seat 2 also accepted Soul of the Harvest's optional draw at action 1863/global turn 58 with only two cards left in its library, then eventually lost to its mandatory draw. Library counts reconstructed from real zone-change events match the final snapshots exactly. This is a useful short-horizon draw-planning counterexample, not a rules-engine draw failure.

Commander deployment remains limited: Seat 2 declined 14 of 14 recorded affordable main-phase commander opportunities; Seat 4 declined 15 of 18 and cast three times. Windows repeat as priority returns, so these counts are not independent strategic mistakes. The two early Pact eliminations also sharply limited those pilots' deployment opportunities.

**Disposition:** retain the composed candidate as opt-in. The narrow unrestricted-X correction and departed-player targeting repair have focused reproducible coverage; Encore, Statue, R2 development and R4 Ballista continuation coexist under actual execution. This one game does not establish a win-rate improvement or broad Animar mastery.

**Next concrete experiment:** reproduce the two archived Pact choices and next-upkeep payments with unchanged CURRENT, then test a separate opt-in affordability/terminal-exit policy against those exact positions. Add one near-empty-library optional-draw counterexample. Before extending Ballista search, validate a full-40-life Statue/Ballista position and one realistic removal/counterspell interruption; the present eight-cast bound does not establish that general line. Run a further small gameplay smoke only after those independent checks, without an automatic 40-game batch. Cruise/Sport/Race still require distinct implemented action policies and their own real decision tests; they are not complete gameplay pilots.

## Preservation

Original R3 and R2 evidence remains unchanged. The frozen v0.7 deck and CURRENT policy remain unchanged. This report branch starts from `3057523a6bdd187eed83c886294edc9b9c728143`.

`artifact-receipts.json` binds all eight raw archives (including every failed attempt) to GitHub artifact IDs, SHA-256 digests, sizes, run IDs and exact tested sources. Run `python3 gameplay/manual-r4/verify-evidence.py` to verify those bindings offline. No main merge, frozen-deck edit, historical-evidence edit, privileged infrastructure, or automatic batch was performed.
