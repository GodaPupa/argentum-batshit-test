# ARGENTUM — Animar Manual Transmission R7: Seven-Land Source Attribution

**EXECUTED: TEN ORIGINAL ARCHIVES RE-EXTRACTED · READ-ONLY EVENT FORENSICS · ZERO NEW GAMES · NO MODIFICATIONS TO FROZEN OR PRODUCTION**

**Disposition: INCONCLUSIVE for the complete seven-card Challenger A, with several supported individual real-card improvements and two newly identified simulator-validation risks.** Date: 2026-10-20.

## 1. Original source and exact evidence

Repository: GodaPupa/argentum-batshit-test.

- R1 successful first four games: run 39477314282, source 84e6347bbcdfa2990eb304051771007daf849ce9; seeds 202610202111 and 202610202112, CONTROL and A on each.
- R6 successful six-game program: run 39498572460, exact source 53a5162eb3659310b1878894a90ae58c244a6745 and Git tree 3f60cbc4229c34a8786c55414ba01fd60ba1f79a; seeds 202610202141, 202610202142 and 202610202143, CONTROL and A on each.
- Accepted combined original report: branch research/manual-precision-r6-prospective-report-20261020, head f1f60b66636d3401e57bbf424880f018e95091d3; RESULTS.csv there pins all ten artifact IDs and ZIP digests.
- All ten original ZIP bytes were independently SHA-256 checked against GitHub Actions' original digests, opened, and analyzed. Each full-game original contains its source/seat/deck binding, initial/final states, action/results JSONL, decisions JSONL and independently generated 64-action prefix smoke trace.
- Original R1 audit artifact 12289828339, digest 08258db17f30fc0b28b569dd80dfe97a05e73c0b7be0d7209da1a6d662cbf0df.
- Original R6 audit artifact 12299506415, digest 4d583f0ba6c9621b898a03cab612c8ee60ab972c86e14e25edb2cf77ead49314.
- Frozen 100-card CONTROL Git blob c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89 (measured frozen file SHA-256 2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1; historical printed SHA claim differs). Challenger A exact 100-card seven-slot substitution Git blob 7984a2e87d8ddbe023d45620414d25a61691a040. Both unchanged.
- All game seats used the identical manual-transmission-r5-survival-opt-in policy. AiProfile.CURRENT, main, accepted survival/combo protections, and the original R1–R6 game artifacts are unchanged.
- Failed prior R6 attempt 39498303247 ended at Git ancestry admission with no games. Accepted R6 was run attempt 1 on a separate authorized recovery branch, no outcome-selected seed or replay.
- No additional original full games were run during R7. Original twelve-game cap: 10 used, 2 remain.

## 2. Methodology and what the data can establish

All counts below are derived from the authoritative original submitted action/result pairs and real engine events (LandPlayedEvent and ZoneChangeEvent, AbilityActivatedEvent, TappedEvent, ManaSpentEvent, LifeChangedEvent, LibrarySearchedEvent, DamageDealtEvent, SpellCastEvent and PlayerLostEvent). Land entity IDs and printed rules text were mapped from the original initial-state CardComponent data; if the same card entity transformed later, sourceName at action time was used for first action divergence rather than the final entity face. Game source and artifact identity are retained in every detail CSV.

EARLY means each seat's **first three personal turns**, not simply global turns 1–3 or every action in global turns 1–12. An early mana activation is reported only if observed while that seat was the active player; fetches may be cracked during someone else's turn. Counts are for **five CONTROL games versus five A games, twenty seat-observations per arm**, not fifty independent randomized trials.

ManaSpentEvent proves resources spent, not all mana the board could have produced; TappedEvent + AbilityActivatedEvent prove the source was used, not which individual land produced a particular colored pip when multiple sources paid a composite cost. A fetched-land ZoneChangeEvent proves a land entered; it does not by itself prove an optional shockland life payment or that the land entered untapped. Where no snapshots or decision events establish a tapped state, this report marks it UNKNOWN rather than guessing.

Different cards were drawn/played in the two mirrors, and normal effects reshuffled libraries. Raw frequency differences are **not causal slot-by-slot counterfactuals**.

## 3. Exact seven-slot observations and acquisition classification

Interpret SUPPORTED UPGRADE as verified useful printed-card functionality observed in source-bound gameplay and supported by the known rules interaction, **not experimentally demonstrated improvement in win rate**. PROMISING BUT UNPROVEN means practical design case but no demonstrated relative early tempo gain under unchanged R5. INCONCLUSIVE means an important tradeoff or inadequate causal evidence. No land is experimentally proved inferior.

| Frozen v0.7 -> A replacement | Source-bound event evidence across five games per arm | Physical-card classification |
|---|---|---|
| Path of Ancestry -> Mana Confluence | CONTROL Path played from hand once / tapped for mana twice; A Mana played four times from hand, had ten recorded mana activations, one during an early own turn; seven 1-life payments recorded, three expected payments absent when used for nonspell abilities | **PROMISING BUT UNPROVEN — engineering gate** |
| Frontier Bivouac -> City of Brass | CONTROL Frontier played once, no recorded mana activation; A City played three times, had eight mana activations and **eight real 1-damage triggers**; no card in either arm was first played during a seat's first three own turns | **SUPPORTED UPGRADE — printed untapped five-color access**, not measured early outcome |
| Dreamroot Cascade -> Breeding Pool | CONTROL Dreamroot hand plays 3, early 1, mana uses 13; A Breeding Pool hand plays 3, early 1, mana uses 30; Breeding Pool entered twice from library via assorted effects, including one verified Wooded Foothills fetch; three direct pay-two-life prompts, all declined | **PROMISING BUT UNPROVEN** |
| Stormcarved Coast -> Steam Vents | CONTROL Coast hand plays 6, early 2, mana uses 25; A Steam Vents hand plays 3, early 0, mana uses 26; verified fetch targets of both Misty and Wooded; three direct shock-pay prompts, all declined | **PROMISING BUT UNPROVEN** |
| Sheltered Thicket -> Stomping Ground | CONTROL Thicket hand plays 2, early 1, mana uses 10; A Stomping Ground hand plays 3, early 1, mana uses 37; three direct shock-pay prompts, all declined; losing Thicket's Cycling 2 is a real downside and CONTROL actually cycled this land in one matched trace | **INCONCLUSIVE — explicit cycling tradeoff** |
| Evolving Wilds -> Misty Rainforest | CONTROL Evolving Wilds activated four times, three searches put a basic Forest into play and one search selected no card; A Misty activated four times and resolved Forest x2, Steam Vents x1, Cinder Glade x1; all four activations paid one life | **SUPPORTED UPGRADE — demonstrated typed nonbasic access** |
| Terramorphic Expanse -> Wooded Foothills | CONTROL Terramorphic activated four times, searching Forest x2 and Island x2; A Wooded activated four times, finding Forest x2, Breeding Pool x1, Steam Vents x1; all four activations paid one life | **SUPPORTED UPGRADE — demonstrated typed nonbasic access** |

Additional hard observations:
- Across all five A mirrors, shockland Pay-2-life choices totaled **nine**, all No. Three each for Breeding Pool, Steam Vents and Stomping Ground. Their *optional* untapped-entry advantages were not exercised in those nine original prompt outcomes.
- The eight premium fetch activations (four Misty, four Wooded) had eight corresponding LifeChangedEvent PAYMENT charges of one life each.
- Mana Confluence's ten recorded mana-ability activations included seven logged one-life payments; all eight City of Brass recorded mana activations had corresponding one-damage events. Spell-casting and nonspell activated-ability payment paths must be distinguished.
- During the first three personal turns per seat, the full five-game combined ManaSpentEvent totals were **78 actual mana** for CONTROL and **74 for A**, with 41 versus 39 spending events. Actual early color spending: green 40 vs 34; blue 20 vs 20; red 7 vs 7. These are *usage*, not all legally available colors, and do not demonstrate a fixing improvement for either arm.
- Early hand plays of the seven replacements were sparse: A Mana Confluence 1, City of Brass 0, Breeding Pool 1, Steam Vents 0, Stomping Ground 1, Misty Rainforest 1, Wooded Foothills 0. The sample contains too few direct early deployments of individual premium lands for reliable empirical per-slot ranking.

## 4. Material source-fidelity flags — must resolve before further full-game qualification

### A. Mana Confluence one-life activated-mana payment inconsistency (HIGH PRIORITY)

The pinned production card definition at mtg-sets/2008-2016/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/jou/cards/ManaConfluence.kt correctly specifies a composite Tap + PayLife(1) cost. An existing standalone test exercises direct land activation and expects one life lost. In the original A gameplay:
- Seed 202610202141, action **2292**, global turn 52, Seat 4: paying for a Temur Sabertooth activated ability tapped Mana Confluence. Pilot life **36 -> 36**; no life-payment event.
- Seed 202610202142, action **1210**, global turn 28, Seat 2: Spellskite activation tapped Mana Confluence. Pilot life **40 -> 40**; no life-payment event.
- Seed 202610202142, action **2712**, global turn 60, Seat 2: Purphoros activated ability tapped Mana Confluence. Pilot life **11 -> 11**; no life-payment event.

Seven other mana activations using the land to pay for spells did charge one life. These are exact original ActionProcessor trace-level violations of the printed mana ability's expected payment, not merely missing telemetry. No production fix was attempted in R7. **Proposed isolated, no-full-game regression:** cast a spell using this land and separately activate a nonspell ability using it; require exactly one life payment per mana activation in both paths. Do not silently rewrite R6 game outcomes.

### B. Typed shocklands fetched from library without recorded Pay-2-life prompt (VALIDATION GAP)

In three original candidate fetch resolutions a typed shock entered from library with no corresponding YesNoDecision recorded:
- Seed 202610202141 action **227**: Misty Rainforest put Steam Vents onto battlefield.
- Seed 202610202141 action **2231**: Wooded Foothills put Breeding Pool onto battlefield.
- Seed 202610202143 action **716**: Wooded Foothills put Steam Vents onto battlefield.

An optional As-this-enters 2-life decision should be legal regardless of whether the shock came from hand or a fetch. The artifact proves absence of a recorded choice, **not that the land definitively entered untapped or that life was wrongly charged**. Source-bound fetch-shock scenario coverage is needed, including tapped status, offered choice and life totals on both branches. Preserve the issue as a suspected rules/choice-path gap, not a proven runtime cheat.

These flags diminish confidence that current R5 mirror gameplay faithfully captures all potential benefits and costs of the physical lands.

## 5. First actual divergences across five matched seeds

These are the first different submitted actions or actual source cards, **not necessarily the first difference in game state**:

| Seed | First submitted-action difference | Original event interpretation |
|---|---|---|
| 202610202111 | Action 390, turn 12 | CONTROL played Stormcarved Coast; A played Opal Palace |
| 202610202112 | Action 14, turn 1 | Both had played source slot e83 at action 13 (Dreamroot Cascade vs Breeding Pool); A added a shock payment choice and declined |
| 202610202141 | Action 222, turn 7 | CONTROL activated Evolving Wilds; A activated Misty Rainforest. Card identity had already diverged at their otherwise identical PlayLand action 168, turn 6 |
| 202610202142 | Action 134, turn 5 | CONTROL played Training Center; A chose Mana Confluence |
| 202610202143 | Action 461, turn 13 | CONTROL cycled Sheltered Thicket; A passed priority instead |

The Evolving/Misty seed-2141 pair had a real follow-up difference: Evolving Wilds fetched basic Forest, whereas Misty fetched Steam Vents. The seed-2112 Breeding Pool turn-one choice was independently reproduced by R2–R5 microforks: paying 2 life would make Arbor Elf legally castable early, but opponent interaction ultimately prevented a proven third-turn mana/Animar advantage. No card play or faster final game by itself establishes a replacement's causal value.

## 6. Combo, survival and terminal-game forensics

- All ten original GameEndedEvent records cite **LIFE_ZERO** as the terminal reason. Each game's final player loss occurred on an action containing observed player-directed combat damage, although in one game the subsequent GameEndedEvent followed an additional commander-zone decision.
- The 29 recorded PlayerLostEvent rows contain 28 LIFE_ZERO and one COMMANDER_DAMAGE loss. No Pact-upkeep-loss reason appeared. This is not proof that every Pact debt was optimally managed.
- Pact of Negation spells actually cast: 7 CONTROL, 6 A. Ancestral Statue actually cast: 29 CONTROL, 44 A. Walking Ballista: 6 CONTROL, 7 A. Purphoros: 3 CONTROL, 5 A. Counts are real casting events, not verified combo conversion rates.
- Purphoros generated substantial real noncombat player damage in both arms; in **R1 seed 202610202112 / Challenger A action 3687**, a Purphoros trigger dealt 2 noncombat damage to Seat 1, reducing life **2 -> 0** and causing a real intermediate LIFE_ZERO elimination. It did **not** deliver that game's final victory; the last player loss was due to combat damage.
- No Ballista source was identified delivering player-targeted damage in the extracted player-damage events. No ten-game terminal finish was attributable to a Statue/Ballista infinite-damage line. Do not assert an absence of attempted combos, since logged casts/stack decisions do not encode a complete attempted-loop label.
- Inflated legacy race-clock/141.9 evaluator scores remain excluded from outcome or mana-quality inference.

## 7. Practical no-budget paper acquisition priority

1. **Misty Rainforest** — priority 1: actual searchable nonbasic dual-land utility, flexible green/blue typed choices and real tempo potential from fetching non-tap targets.
2. **Wooded Foothills** — priority 2: real Breeding Pool / Steam Vents typed fetch targets; pairs with the shocklands.
3. **City of Brass** — priority 3: mechanically solid untapped any-color alternative to forced-tapped Frontier Bivouac; actual cost is a damage trigger on tapping.
4. **Breeding Pool** — priority 4: Forest/Island typing, future Elf untap and fetch synergies; paper pilot can deliberately pay 2 when advantageous. Unchanged R5 did not.
5. **Steam Vents** — priority 5: Mountain/Island fetchable dual and optional untapped entry; actual fetch targets observed.
6. **Mana Confluence** — priority 6: attractive untapped any-color replacement to Path of Ancestry but **simulator cost regression is a mandatory gate for simulation qualification**. On paper it always requires one life per mana activation; do not regard logged missing payments as a perk.
7. **Stomping Ground** — priority 7: useful untapped option on a Mountain/Forest dual, but Sheltered Thicket is also typed and has Cycling 2; preserve this swap as a deliberate testing choice, not an automatic physical inclusion.

Because fetches can find the typed shocks, acquiring the **fetch-and-shock package together** offers the greatest practical synergy. Physical acquisition is a rules/design recommendation, not proof from five matched four-player mirrors that the entire seven-card package produces more wins. Nothing has been merged into the frozen physical-build authority.

## 8. Remaining budget and next execution lane

**Final R7 disposition: INCONCLUSIVE for wholesale adoption; SUPPORTED UPGRADE only for the three named mechanical/observed improvements.**

The final 2/12 full-game slots would provide only one new matched seed, insufficient to isolate seven distinct upgrades or repair source-fidelity defects. **Do not spend them yet.**

The narrowest justified next work is two isolated, source-bound, **no-complete-game** rules-engine regressions: (a) Mana Confluence's PayLife(1) on mana auto-payment for nonspell abilities; (b) shockland optional life/tap choices when brought from library by Misty/Wooded. Preserve existing card, Pact, Ballista, Statue and commander protections and do not patch production or CURRENT without independently proven defects and regression gates. After those issues are classified, determine whether per-land matched-slot microforks (not full games) or direct two-list head-to-head tests are needed for eventual paper adoption.

All ten original source decks/outcomes, main, AiProfile.CURRENT, R5 policy, and all R1–R6 original artifacts remain unchanged. ZERO R7 games, rerolls or promotion.
