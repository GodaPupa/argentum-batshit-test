# ARGENTUM — Manual Transmission R4

Work in progress: mechanics passed; multiplayer AI repair and combined gameplay are pending.

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

## Preservation

Original R3 and R2 evidence remains unchanged. The frozen v0.7 deck and CURRENT policy remain unchanged. This report branch starts from `3057523a6bdd187eed83c886294edc9b9c728143`.

`artifact-receipts.json` binds raw archives to GitHub artifact IDs, SHA-256 digests, sizes, run IDs and exact tested sources. Run `python3 gameplay/manual-r4/verify-evidence.py` to verify those bindings offline. Combined gameplay has not yet been executed.
