# ARGENTUM — R18 Wan Shi Tong v0.8 controlled deck-slot comparison (October 19–20, 2026)

## Final verdict

**`WAN_INCONCLUSIVE` — four bounded games successfully completed, four 64-action smoke gates passed, zero engine rejections, zero censors. No automatic deck adoption, no R7 Ballista combination, no official qualification.**

The challenger swapped **Wan Shi Tong, Librarian IN / Lifecrafter's Bestiary OUT**, preserving 99 other cards and the **same R5 AI policy for all four players in both arms**. Different winners/turn counts occurred, but **neither card was cast in any of the four complete games**. The Wan arms observed **zero Wan ETB draws, zero Wan counters, and zero Wan opponent-search triggered abilities**. Thus no observed competitive change can be attributed to either tested card's resolved effect.

The two decklists had **different initial GameState hashes at the same seed**: reordering the creature/noncreature card slot in the decklist changed seeded library layout, despite the exact multiset one-card substitution. This is not a one-action causal fork; the two complete-game results are descriptive, and no win-rate or card-strength superiority is established from two seeds.

## Exact run and frozen authority

- Isolated R18 execution branch: `gameplay/manual-r18-wan-challenger-20261019`.
- Exact tested source: `ab12320af91e2753ae73c9d1c465bb505a9ee3d3`. From accepted R17 report head `ecf44018e94c55bc92937c5b95da8f46022a74b6`, the change was only **three experimental files**: exact Wan deck copy, R18 observational test and workflow.
- GitHub Actions [39372582014](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39372582014): **SUCCESS**, four separately executed matrix arms with `fail-fast: false`. The workflow required 64/64 accepted-action smoke before each full game and used `:ai:cleanTest :ai:test` for a genuinely fresh full test.
- Predeclared seeds **202610191615 and 202610191616**, checked absent from earlier accepted R17 source. No replacement seeds, rerolls, extra games, merges or new scoring rules.
- Frozen deck v0.7 blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`.
- Original v0.8 challenger deck blob `854cedf1f3f789c2ddc49c1f9004f2fa63431851` (copied byte-for-byte, 100 cards, exactly one substitution).
- `AiProfile.CURRENT` original blob `180918b2d7a59309e7b0617f2579fb2a6d06b7d5`, R5 policy blob `ef5ba9dacb02bbf2a00303c9ee4724565769385a`; original R4 Ballista module retained. Opt-in R7 combined Ballista module **absent from every selected profile**.
- Accepted R9 censor protocol source `37733aee218e34ca6bb712f9bdc5be2935852737`; previous [R9 success 39350147606](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39350147606) preserved. Per-game bounds were 300 seconds, 10,000 actions, 200 global turns, and 1,500 actions per turn.
- Existing Wan rules source blob `a8bb98cf7c08d153accdb5dbe0ce5d64ef7a73d3`; accepted Wan mechanics and safety workflow runs **39329172513** and **39333033942** each had a successful readback before R18 gameplay.

## Four original independently verified artifact identities

| Seed | Arm | GitHub artifact ID | Original ZIP SHA-256 |
| --- | --- | --- | --- |
| `202610191615` | Frozen v0.7 control | `12241419401` | `6301e4e7b258476dd3e517f908af8efb38a9a917f5342f9b45548b82f9b11d77` |
| `202610191615` | Wan v0.8 challenger | `12241389305` | `5690e3d0d2932ec06d976abd4bf4855627264ec25f5305511691973af67c303d` |
| `202610191616` | Frozen v0.7 control | `12241478890` | `158c6006b3999eb6ca27fa31d6b17d57d295206c51bb13708b7c3a9842681994` |
| `202610191616` | Wan v0.8 challenger | `12241967493` | `9ff74259199bd70c3440015cd3c3b6168d6e8799cc9cef8a7f32998d62360bbd` |

All four ZIPs were downloaded and independently SHA-256 verified. Each contains original `initial-state.json`, `actions.jsonl`, `decisions.jsonl`, `final-state.json`, `summary.json`, 64-action prefix evidence, and `wan-event-candidates.json`. Original artifacts remain immutable. A separate read-only standard-library audit cross-checked exactly all original submit/result pairs, status and accepted-action counts, deck/profile bindings and card-level events.

## Complete observed game outcomes

| Seed | Arm | Winner | Terminal global turn | Accepted/submitted | Engine rejects | Winner life | Wan casts | Bestiary casts |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1615 | Control | Seat 4 (`manual-mirror-3`) | 46 | 2,091/2,091 | 0 | 21 | 0 | 0 |
| 1615 | Wan | Seat 1 (`manual-mirror-0`) | 45 | 2,477/2,477 | 0 | 9 | 0 | 0 |
| 1616 | Control | Seat 1 (`manual-mirror-0`) | 42 | 1,757/1,757 | 0 | 36 | 0 | 0 |
| 1616 | Wan | Seat 1 (`manual-mirror-0`) | 37 | 1,624/1,624 | 0 | 10 | 0 | 0 |

All are `BOTH_COMPLETE` under R9, so terminal winner comparisons are permissible descriptively. **No deck-out losses or `DrawFailedEvent` occurred** in any arm. No censor status was used, and no score or winner was imputed.

The raw games contained ordinary library searches (6 control / 9 Wan for seed1615; 5 / 5 for seed1616), but **Wan was never on the battlefield**; none fired its opponent-search ability. No ETB cards or +1/+1 counters were awarded by Wan in these games.

## Card-draw opportunity and initial-order confounding

| Seed | Control Bestiary draw | Challenger Wan draw | Both spells cast? |
| --- | --- | --- | --- |
| 1615 | Seat 1 drew Bestiary at action **1437** | Seat 1 drew Wan at action **2452**, only 25 actions before that game ended | No |
| 1616 | Seat 3 drew Bestiary at action **1721**, late in the 1,757-action game | Seat 4 drew Wan at action **1020** | No |

The card substituted in the *deck multiset* did not occupy an identical position in the initialized library ordering for these two arms. For seed 1616, the same indexed action **1020** drew `Sol Ring` in control versus `Wan Shi Tong, Librarian` in challenger (matching entity slot `e353`). Wan then passed priority where control cast Sol Ring at action **1025**. This is a direct observation of **different randomized library contents**, not evidence that Wan's battlefield effect improved anything.

Strict action-and-event comparison:

- **Seed 1615:** Serialized initial states differed. First divergent submitted action **82**, global turn 3, Seat 3: control cast **Wild Growth**, challenger cast **Sylvan Safekeeper**, long before either respective swap card was drawn. The first corresponding event divergence was also action 82. Winners subsequently differed, but that cannot be causally attributed to Wan's effect.
- **Seed 1616:** Serialized initial states differed. First divergent **engine result** was draw action **1020** (control drew Sol Ring, challenger drew Wan). First divergent **submitted action** was 1025 (control cast Sol Ring, challenger passed). Same final winning seat, earlier terminal turn in challenger, with no Wan cast.

The paired initial-state hashes were:
- Seed 1615 control `eb57017c2dedd05fc9b73e92a379ea39dd589ab1b3f632bd0b5902e3ee07a26d`; Wan `8f40b700c859b14968e7f3e26c2c0bcdadac4a9237b413ed861d2186bb09724e`.
- Seed 1616 control `ffe5fc5e3ea46d29d37e5d38aa859133e6c690aa48ddc53fa954c5ad30d0bcd0`; Wan `50074a372035365c8effaa78964ee0c31c065dd39838c9732650bffed004bd1c`.

## Interpretation, protection and next action

**`WAN_INCONCLUSIVE`.** The test completed the authorized four games and confirms both candidate decklists are legal and the R5 AI plays without engine rejection. Because Wan and Bestiary were **never cast** and the decklists changed seeded initial card ordering, it does **not** demonstrate an improvement attributable to Wan over Bestiary. No measured combat/development/ETB/draw advantage can be credited to a card that was not played. The prior separate Wan single-game mechanics witness (X=7, three ETB draws) remains accepted but was not repeated in R18.

The accepted source, frozen deck and `AiProfile.CURRENT` were not modified; `main`, original R4/R5/R7/R9/R11/R17 evidence, separate Wan v0.8 challenger and C4/D2 qualification boundaries remain intact. No deck adoption or extra games are authorized by this result.

**Next justified action:** A **read-only deck-initialization/shuffle-control diagnostic**, or a separate predeclared matched-card-slot protocol, could eliminate shared-card order confounding before authorizing more card-slot games. Also examine Wan's actual decision-time cast opportunities at R18 seed1616 action 1020–1025, with engine-legal costs and AI scores, without treating a declined cast as an automatic defect. Do not rerun these R18 seeds or silently replace them.
