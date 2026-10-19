# ARGENTUM — Manual Transmission R4

Work in progress: mechanics and AI execution passed; one remaining raw-target enumeration regression and combined gameplay are pending.

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

Source `329cdd4ecbe43e9d44901d26d88fbb5ed4d9a6f4`, run `39268490241`, passed all 21 Ballista scenarios but failed the new departed-player test at line 44: the raw `LegalActionEnumerator` still advertised the departed player. The separate `TargetEnumerationUtils` path required the same active-player filter. Corrected targeting source `58a995ea96fad4503db20b539a27175392fc7585` is awaiting validation. No combined branch or game has been created yet.

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

## Preservation

Original R3 and R2 evidence remains unchanged. The frozen v0.7 deck and CURRENT policy remain unchanged. This report branch starts from `3057523a6bdd187eed83c886294edc9b9c728143`.

`artifact-receipts.json` binds raw archives to GitHub artifact IDs, SHA-256 digests, sizes, run IDs and exact tested sources. Run `python3 gameplay/manual-r4/verify-evidence.py` to verify those bindings offline. Combined gameplay has not yet been executed.
