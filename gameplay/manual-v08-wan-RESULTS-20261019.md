# ARGENTUM — Wan Shi Tong v0.8 isolated challenger (October 19, 2026)

**VERIFIED TARGETED MECHANICS; COMPETITIVE DECK IMPROVEMENT NOT YET VALIDATED.**

- Frozen Manual Transmission v0.7 accepted R5 source: `e2e92a199bb2c165625bf1505089e8420d91d8b2`, passing R5 gameplay [39293363796](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39293363796).
- Isolated research branch: `gameplay/manual-v08-wan-shi-tong-probe-20261019`.
- Exact tested source: `6a5b834960f10f6338fd79d98b92d1e95e9d776e`; passing Actions [39329172513](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39329172513).
- Artifact `12223048263`, ZIP SHA-256 `ade4ffa5a02a1eaf1e4feb804dba6db965813250fb59ae9b9c4c1d3297d377a3`.
- Existing card definition `WanShiTongLibrarian.kt` (blob `a8bb98cf7c08d153accdb5dbe0ce5d64ef7a73d3`) was already in the 2025 TLA catalog. No card production implementation was added or changed.

## Validated targeted results

**9/9 passed**: four new isolated checks (three Animar-cost-reduced X variants and exact 100-card swap) plus five retained card tests (X=4, X=3, opponent search, own search exclusion, flash/flying/vigilance metadata).

| Animar counters before casting | Announced Wan X | Actual mana spent | Wan +1/+1 counters | Actual cards drawn | Animar counters after |
| ---: | ---: | --- | ---: | ---: | ---: |
| 0 | 0 | two blue | 0 | 0 | 1 |
| 2 | 2 | two blue | 2 | 1 | 3 |
| 6 | 6 | two blue | 6 | 3 | 7 |

The only provisional card change is `Lifecrafter's Bestiary` OUT, `Wan Shi Tong, Librarian` IN, recorded in `gameplay/manual-v08-wan/CHALLENGER.decklist.txt`. All 100 cards were counted and the complete card multiset differs from the frozen v0.7 by **exactly that one substitution**. The frozen v0.7 file and all production AI sources remain unchanged.

## Gate to gameplay

The interaction is highly synergistic but its extra mandatory draw triggers can increase near-library-out risk; the printed `{U}{U}` requirement may constrain early casting. The accepted v0.7 policy has not been proven to choose profitable X values or benefit from this swap across seeds. Next, add a bounded opt-in candidate profile/deck application and negative draw/deck-out control, then run focused composition tests. Only if those pass execute **one** explicitly labelled four-player candidate game; do not claim a win-rate improvement or automatically replace v0.7 based on a single result. Compare with the accepted R5 baseline and preserve original traces.

**No main merge, frozen input edit, AI CURRENT activation or official C4/D2 qualification.**
