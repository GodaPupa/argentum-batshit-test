# Manual Transmission — SPORT S3 source-only opponent-priority disposition

**Date:** 2026-10-21  
**Decision:** `PASS_ARCHIVED_PRIORITY_ATTRIBUTION_KEEP_R5` — **retain immutable R5 defensive comparator**; no SPORT policy change, R9 composition, deck promotion, production merge, or full game.

## Qualification chain and exact original evidence

- Frozen CONTROL: Manual Transmission **v0.7**, 100 cards, 0 Game Changers, `KEEP_V07`. CONTROL deck Git blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`. Challenger A remains unpromoted at `7984a2e87d8ddbe023d45620414d25a61691a040`.
- SPORT S1 historical Pact trace: [run 39672563388](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39672563388). **Ten immutable self-mirror source ZIPs**, 33,440 original accepted actions, 13/13 actual Pact payments at the first following caster upkeep. Historical trigger descriptions incorrectly said “End Step”; actual payment timing was correct in these 13 observed samples.
- SPORT S2 one-line `Step.END` → `step` **metadata-only** correction: [run 39674502310](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39674502310), four focused checks and 76 protected R9/R5 checks green. Original accepted card mechanics themselves were not modified.
- Independent existing Pact card mechanics coverage on the S2 source: [run 39677142827](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39677142827), exact head `177b24c048baaf4c2f81adb38ff0e93eb9f1f6b2`, artifact `12381258587`, SHA-256 `721cdafd7a452f4f7b49fd0a0b1c26b06b38fa287405c129d6bc75810814a5a3`, **4/4 original card mechanic scenarios** (counter, paid upkeep, unpayable upkeep, payable declined) and no unqualified games.

## SPORT S3A: exhaustive original priority inventory

[Run 39679385530](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39679385530), exact head `918bd1601f73fb0bb1b5bedd7fc5256348861f29`, artifact `12383521328`, original ZIP SHA-256 `f546ddfcd35c902318fbddb0276ea82d248cef07b7f72d9ad5236037c8db1050` (independently re-hashed). All ten R1/R6 original game ZIPs and their source SHA IDs verified. **33,440/33,440 accepted actions**, **7,827/7,827 scored priority decisions** uniquely matched to submitted actions; no new games.

- **3,629** off-turn scored priority decisions.
- **1,738** such decisions had an offered action on the *predeclared v0.7 interaction watchlist*.
- **75** selected one of those watched actions, **1,663** did not.
- Of the watched options, `Sylvan Safekeeper` was offered in 1,049 offturn windows, `Spellskite` 223, `Veil of Summer` 204, `Pact of Negation` 150. These options include cheap activated abilities that remain offered even when no relevant threat exists: **1,663 declines are NOT 1,663 errors**.
- All four seats in the original ten games used the same R5 pilot. They are **not seven different cEDH opponents** or independent head-to-head matchups. Neither an offer nor a pass proves correct alternative play.

## SPORT S3B: actual unresolved spell on stack

[Run 39680809168](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39680809168), exact head `707242b5b6697484605637e9464eb87438a4e8a0`, artifact `12384720613`, independently re-hashed ZIP SHA-256 `db13b2eea7e2673fe522809e13cbdb3eb2269cf3fd3c56990a3af92f5f9440ce`. The original source-bound public `SpellCastEvent` and STACK departure streams confirm:

- **150** actual off-turn Pact offered windows = **130** distinct target spell instances for the offering seat; **20** repeated opportunities during priority progression.
- Every offered Pact targets an actual unresolved publicly cast spell by **another seat** at the point of decision; **0** same-seat target offers.
- **13** actual selected Pact spells, all countering another seat's spell. They match the 13 historical observed next-upkeep payments reported independently by S1.
- **137** Pact option windows were not selected; **120/137** carry the actual original `ManualPactDebtAdvisor replaced the board score` note, indicating the debt guard changed the scored action. The other 17 have no such note. Those are **window counts, not independent games or blunders**.
- Target spell identities span genuine stack cases: Ancestral Statue, Animar, Walking Ballista, Chaos Warp, Cloud of Faeries, Decimator of the Provinces, and others. This is a real, identified target inventory, not a hypothetical answer manufactured by moving hidden cards.

## Strategic interpretation and next gate

The R5 Pact safeguard is doing observable work, while the thirteen actually cast Pacts paid their debt. That supports **retaining R5** rather than inventing a stronger SPORT counterspell preference. It does **not** prove 137 original declines were all strategically correct, nor does it establish matchup survival or win rates. In particular, neither a public stack target nor a score alone establishes opponent lethal, a profitable counterwar or post-Pact survival in a different branch.

**Next:** independently open **RACE** source-only combo conversion on the exact *frozen* v0.7 roster. Audit genuine Ancestral Statue, Walking Ballista, Purphoros and finite exit choices with source-bound original actions. Do not treat Palinchron—absent from the frozen v0.7 list—as an actual-deck card merely because a deterministic regression uses it. Predeclare one-variable challengers only where a falsifiable real error/opportunity is demonstrated. No CRUISE/SPORT/RACE integration or full game without its own gate.

**Budget and isolation:** Original Precision complete games **10/12**; **two remain reserved**, **zero** were spent by S1/S2/S3. `main`, `AiProfile.CURRENT`, R5, CRUISE R9, decks and the mana solver remain untouched. Official Phase 2 capability **0/36**, primary **0/864**; neither is admitted by these source-only analyses.
