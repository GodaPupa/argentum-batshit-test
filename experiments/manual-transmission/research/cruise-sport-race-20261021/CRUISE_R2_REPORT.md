# Manual Transmission — CRUISE R2 archival opening/Animar forensics

**Disposition:** `DESCRIPTIVE_ARCHIVE_FORENSICS_ONLY_NO_POLICY_CHANGE`. Original complete artifacts, not new games.
**Method:** SHA-256-verified 10 original R1/R6 ZIP archives, same five paired seeds; all original action submissions, results and priority insights examined.
**Integrity:** Original accepted actions and source commits enforced; no unapproved main/CURRENT/deck/R5 writes.

## Quantitative audit
- Original full games: **10** across **5 matched seed pairs**; **33,440** accepted actions, **0** rejections.
- **40** seat-game observations; **40** have an archived AI-scored Animar cast option.
- Commander cast at least once in **35** / 40 seats; **5** never cast before their game ended.
- Median **first scored Animar option**: personal turn **4.0**; median **first actual cast among 35 casters**: personal turn **7**.
- At the **first scored offer**, Animar was selected in **2 / 40** seat-game observations. This is not 38 proven mistakes: an alternative cast, land play, or response may be superior.
- For seats that eventually cast, median deferment from first scored offer: **2 personal turns**; **31** cast later than the first scored personal turn.
- The original harness counted **233** legal own-main commander options; the AI insight stream contains **249** scored options. Counts are NOT automatically interchangeable.
- Original AI priority insights mapped to exact submitted action indices: **7827 / 7827** (no gaps in PRIORITY matches).

### Matched hardware — descriptive
| Arm | Seat observations | First casts | Median cast turn (casters only) | Scored offers | Original legal options |
|---|---:|---:|---:|---:|---:|
| Frozen v0.7 | 20 | 18 | 7.0 | 137 | 121 |
| Challenger A | 20 | 17 | 7 | 112 | 112 |

### Five matched seed pairs
| Seed | CONTROL winner | A winner | Same winner? | Cast-turn differences across seats (A−CONTROL) |
|---|---|---|---|---|
| 202610202111 | manual-mirror-0 | manual-mirror-3 | False | S0:+0, S1:+0, S2:+0, S3:+0 |
| 202610202112 | manual-mirror-1 | manual-mirror-1 | True | S0:-1, S1:-2, S2:NA, S3:+0 |
| 202610202141 | manual-mirror-1 | manual-mirror-3 | False | S0:+1, S1:NA, S2:+0, S3:+2 |
| 202610202142 | manual-mirror-2 | manual-mirror-3 | False | S0:+1, S1:-5, S2:NA, S3:+0 |
| 202610202143 | manual-mirror-0 | manual-mirror-1 | False | S0:+0, S1:-1, S2:+0, S3:NA |

### Instrumentation discrepancy — HOLD
One original game arm has different counts for AI-scored Animar options and independent on-turn legal-enumerator offers. It is incorrect to call every scored option a separately verified legal command-zone opportunity.
- Seed 202610202141, control: scored=39, source-counted legal=23. An exact archived-state legality replay would be needed to explain the difference.

## All 40 original opening hands and first scored/cast observations
Opening land counts use the serialized printed card type line; **MDFC land capability is not counted as a land**. These are actual keep-seven hands, not controller mulligan outcomes.

| Seed | Arm | Seat | Initial lands | Ramp in hand | First scored (turn/action) | Chosen instead | First actual cast PT |
|---|---|---:|---:|---|---|---|---:|
| 202610202111 | control | 0 | 3 | Gwenna, Eyes of Gaea, Fyndhorn Elves | PT5 / #588 | Cast Eldrazi Repurposer | 6 |
| 202610202111 | control | 1 | 3 | — | PT4 / #468 | Cast Spellskite | 8 |
| 202610202111 | control | 2 | 1 | Sakura-Tribe Elder | PT3 / #343 | Cast Eldrazi Repurposer | 8 |
| 202610202111 | control | 3 | 3 | Paradise Druid | PT4 / #546 | Cast Cloud of Faeries | 9 |
| 202610202111 | a | 0 | 3 | Gwenna, Eyes of Gaea, Fyndhorn Elves | PT5 / #589 | Cast Eldrazi Repurposer | 6 |
| 202610202111 | a | 1 | 3 | — | PT5 / #639 | Pass priority | 8 |
| 202610202111 | a | 2 | 1 | Sakura-Tribe Elder | PT3 / #343 | Cast Eldrazi Repurposer | 8 |
| 202610202111 | a | 3 | 3 | Paradise Druid | PT4 / #546 | Cast Cloud of Faeries | 9 |
| 202610202112 | control | 0 | 2 | Arbor Elf | PT6 / #776 | Cast Temur Sabertooth | 9 |
| 202610202112 | control | 1 | 3 | — | PT3 / #306 | Cast Transit Mage | 11 |
| 202610202112 | control | 2 | 1 | Delighted Halfling | PT3 / #345 | Play Spire Garden | — |
| 202610202112 | control | 3 | 1 | — | PT4 / #568 | Play Sulfur Falls | 4 |
| 202610202112 | a | 0 | 2 | Arbor Elf | PT8 / #1136 | Cast Animar, Soul of Elements | 8 |
| 202610202112 | a | 1 | 3 | — | PT3 / #308 | Cast Transit Mage | 9 |
| 202610202112 | a | 2 | 1 | Delighted Halfling | PT3 / #347 | Play Spire Garden | — |
| 202610202112 | a | 3 | 1 | — | PT4 / #570 | Play Sulfur Falls | 4 |
| 202610202141 | control | 0 | 3 | — | PT5 / #608 | Cast Amphin Mutineer | 6 |
| 202610202141 | control | 1 | 2 | Birds of Paradise | PT4 / #486 | Cast Gwenna, Eyes of Gaea | 7 |
| 202610202141 | control | 2 | 5 | Llanowar Elves, Sakura-Tribe Elder | PT4 / #526 | Cast Reckless Bushwhacker | 5 |
| 202610202141 | control | 3 | 4 | Delighted Halfling | PT4 / #564 | Cast Eldrazi Repurposer | 5 |
| 202610202141 | a | 0 | 3 | — | PT6 / #815 | Cast Amphin Mutineer | 7 |
| 202610202141 | a | 1 | 2 | Birds of Paradise | PT6 / #869 | Cast Phyrexian Metamorph | — |
| 202610202141 | a | 2 | 5 | Llanowar Elves, Sakura-Tribe Elder | PT4 / #533 | Cast Reckless Bushwhacker | 5 |
| 202610202141 | a | 3 | 4 | Delighted Halfling | PT5 / #763 | Cast Eldrazi Repurposer | 7 |
| 202610202142 | control | 0 | 2 | Sakura-Tribe Elder | PT4 / #434 | Cast Sakura-Tribe Elder | 5 |
| 202610202142 | control | 1 | 1 | Elvish Mystic | PT7 / #1051 | Cast World Breaker | 10 |
| 202610202142 | control | 2 | 1 | Elvish Mystic | PT3 / #353 | Cast Elvish Visionary | 7 |
| 202610202142 | control | 3 | 2 | Birds of Paradise | PT4 / #598 | Cast Transit Mage | 6 |
| 202610202142 | a | 0 | 2 | Sakura-Tribe Elder | PT4 / #458 | Cast Sakura-Tribe Elder | 6 |
| 202610202142 | a | 1 | 1 | Elvish Mystic | PT5 / #671 | Cast Animar, Soul of Elements | 5 |
| 202610202142 | a | 2 | 1 | Elvish Mystic | PT5 / #733 | Play Spire Garden | — |
| 202610202142 | a | 3 | 2 | Birds of Paradise | PT4 / #587 | Cast Transit Mage | 6 |
| 202610202143 | control | 0 | 3 | — | PT5 / #588 | Cast Delighted Halfling | 6 |
| 202610202143 | control | 1 | 2 | — | PT3 / #307 | Cast Chaos Warp → Malevolent Hermit | 10 |
| 202610202143 | control | 2 | 2 | — | PT5 / #675 | Cast Phyrexian Metamorph | 8 |
| 202610202143 | control | 3 | 3 | — | PT8 / #1328 | Cast Beast Whisperer | — |
| 202610202143 | a | 0 | 3 | — | PT5 / #589 | Cast Delighted Halfling | 6 |
| 202610202143 | a | 1 | 2 | — | PT3 / #307 | Cast Chaos Warp → Malevolent Hermit | 9 |
| 202610202143 | a | 2 | 2 | — | PT5 / #676 | Cast Phyrexian Metamorph | 8 |
| 202610202143 | a | 3 | 3 | — | PT6 / #923 | Cast Amphin Mutineer | 7 |

## Decision boundary and next gate
- **No proven R5 strategic mistake yet.** Passes and alternative plays often outscore Animar. An early legal/scored option alone cannot establish that casting it would be better after interactions.
- **No causality assigned to colors, lands, mulligans or commander tax:** the historical harness skipped mulligans; full per-action mana state was not recorded. Early-card classifications are descriptive only.
- The source-bound next test should independently reconstruct a SMALL, preselected set of high-delay first-opportunity checkpoints, validate legal mana and real opponent priority without scripted passes, and compare unchanged R5 to an isolated one-variable proposal. No full games.
- A positive turn-count gap is **not** sufficient for a deck change, a promotion, or a claim that Challenger A wins more often. Sample is five paired seeds with four dependent mirror seats.
- Original 10/12 game budget remains consumed; **2 full games reserved**, **0 new games used** in CRUISE R2.

The complete `observations.json`, `seat-observations.csv`, and `paired-seeds.json` accompany this report.
