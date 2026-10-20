# ARGENTUM — Animar Manual Transmission Precision Upgrade R6

**FINAL: SIX PROSPECTIVE GAMES COMPLETE · TEN ORIGINAL FULL GAMES TOTAL · ZERO ENGINE REJECTIONS · INCONCLUSIVE · NO PROMOTION**

Report date: October 20, 2026. Independent archival comparison of immutable CONTROL v0.7 versus the seven-land Challenger A. This is an experimental research report, not a qualified deck or AI-policy change.

## 1. Source, restrictions and exact authority

- Repository: GodaPupa/argentum-batshit-test.
- CONTROL: frozen Manual Transmission v0.7, 100 cards, Git blob c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89. Measured actual SHA-256: 2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1. A historical header declares the different digest 6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111; the frozen file was not modified.
- Challenger A: 100 cards, same land count and original card positions, seven in-place replacements. Git blob 7984a2e87d8ddbe023d45620414d25a61691a040; SHA-256 5bfa4467962b105c70408f555c72b2908423342debb8e454acd0419c7b0a5fed.
- Seven substitutions: Path of Ancestry to Mana Confluence; Frontier Bivouac to City of Brass; Dreamroot Cascade to Breeding Pool; Stormcarved Coast to Steam Vents; Sheltered Thicket to Stomping Ground; Evolving Wilds to Misty Rainforest; Terramorphic Expanse to Wooded Foothills.
- Frozen AI policy in all forty seat-observations: manual-transmission-r5-survival-opt-in. Source blobs for AiProfile.kt 180918b2d7a59309e7b0617f2579fb2a6d06b7d5, ManualTransmissionR5Candidate.kt ef5ba9dacb02bbf2a00303c9ee4724565769385a, and GameInitializer.kt b4232ac2034b6d6163268ba8a72f4f628525ec5c, unchanged.
- Original accepted R1 program: four four-player games (CONTROL and A on seeds 202610202111, 202610202112), GitHub Actions [39477314282](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39477314282), source 84e6347bbcdfa2990eb304051771007daf849ce9, tree ee4203da45c56d11b4dc5b13fbb0a9d73fed2e4e. All four complete and zero rejections.
- Originally successful rules/card scenario source preflight: run 39472941354, source aafa138b1cbf6a7f4f024abdef716df4ae75e741, artifact 12287399974 (ZIP SHA-256 67cac362825c661e367a368eaf6df34f370a2b40c5a5411bedd58a0b14310fc8). R6 reused this original PASS rather than changing game/card rules.
- Superseded R6 attempt [39498303247](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39498303247) failed its checkout-depth ancestry check BEFORE gameplay; source 80a7ccfcfe702d6ea26dd3b1130bd30648454535. Original job record confirms its gameplay was skipped: **zero full games used**.
- **Accepted R6 source:** isolated branch gameplay/manual-precision-r6-prospective-recovery-20261020, exact commit 53a5162eb3659310b1878894a90ae58c244a6745, Git tree 3f60cbc4229c34a8786c55414ba01fd60ba1f79a. Exactly two new files relative to accepted R1 gameplay source: an isolated six-seed gameplay test harness and its workflow; no production, engine, R1 harness, deck or AI source modification.
- **Accepted R6 execution:** [GitHub Actions run 39498572460](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39498572460), run attempt 1, SUCCESS. Admission, all six independent gameplay jobs, and the independent audit succeeded.
- Three prospective seeds explicitly predeclared before dispatch: 202610202141, 202610202142, 202610202143. They were absent from the accepted R1 four game identities and repo search. No rerolls, substitutions or winner-selected seeds.
- Each of R6's six arms passed an exact 64-accepted-action prefix smoke test BEFORE its full game. Independent artifact re-extraction additionally verified each smoke's initial state and all 64 submission/result pairs were byte-identical to the first 64 actions of its original full game.

## 2. Complete ten-game terminal record

Each game had four seats, used identical R5 profile/opponent configuration within its pair, ended normally with a single terminal winner, and had **zero engine rejections**. Winning seats are indicated 1–4. These are separate four-player all-CONTROL and all-A mirror games, not two opposing decks in the same pod.

| Program | Seed | CONTROL winner | CONTROL global turn | CONTROL actions | A winner | A global turn | A actions | A minus CONTROL turns |
|---|---|---|---:|---:|---|---:|---:|---:|
| R1 | 202610202111 | Seat 1 | 66 | 3,193 | Seat 4 | 67 | 3,177 | +1 |
| R1 | 202610202112 | Seat 2 | 50 | 2,963 | Seat 2 | 47 | 3,744 | -3 |
| R6 | 202610202141 | Seat 2 | 88 | 4,286 | Seat 4 | 60 | 2,838 | -28 |
| R6 | 202610202142 | Seat 3 | 71 | 3,786 | Seat 4 | 60 | 2,733 | -11 |
| R6 | 202610202143 | Seat 1 | 62 | 2,950 | Seat 2 | 69 | 3,770 | +7 |

A ended earlier on **three** matched seeds and later on **two**. Across all five pairs, CONTROL's mean terminal global turn was 67.4, A's was 60.6; the descriptive -6.8-turn difference is driven heavily by seed 202610202141 (A ends 28 turns sooner). This is NOT an estimate of superior win rate, nor evidence that the seven mana swaps caused the terminal turn difference.

Winners are the same in only one of five pairs (R1 seed 202610202112). This reinforces how much the game paths diverged but is not itself evidence of which deck is stronger. Terminal reason in all ten originals was LIFE_ZERO; artifact action traces contain actual combat/ability details. Casting combo pieces does not establish that they delivered the terminal damage.

## 3. Animar deployment and measurable early resource use

- Across five games per arm, **18 of 20 CONTROL seats cast Animar at least once**, versus **17 of 20 A seats**.
- Among seats that cast Animar, the median first cast was **personal turn 7 for both**; conditional means were approximately 7.22 (CONTROL) and 6.94 (A). The latter mean excludes one additional A seat that never cast Animar and therefore cannot establish earlier overall deployment.
- R6 only: CONTROL had 11/12 seats casting Animar; A had 10/12. In the seed-141 pair A had 3/4 deploying Animar versus CONTROL 4/4 despite its earlier game end.
- Across all ten original action logs, the first twelve global turns contained 59 verified CONTROL land entries versus 60 A entries. This is an observed one-land-entry difference across twenty seats per arm, NOT comprehensive color-availability or color-screw measurement.
- Nine original A shockland Pay-2-life Yes/No decisions were observed across R1+R6; **all nine were declined** (six from R1, three from R6 seed 143). The premium shocklands therefore did not realize any untapped-entry benefit through their optional two-life payment on these recorded prompts. This does not imply zero other land life-costs: Mana Confluence and City of Brass require separate treatment.
- Artifact-level early mana-spend event counts (first twelve global turns across five games per arm): CONTROL 44, A 41. These count real ManaSpentEvents, NOT all mana colors legally available. They cannot establish fixing improvement or harm.
- The seven premium lands and fetches produced actual plays, searches, mana activations and changing decisions; differences cannot be credited automatically as beneficial.

The game summaries retain actual first commander global and personal turns, recasts, scored and legally offered deployment opportunities, first three personal-turn mana spending proxies, and chosen actions. Full initial/final states and all action/decision traces are preserved per artifact.

## 4. Earliest action-level divergence of each original matched pair

- Seed 202610202111: submitted action **390**, global turn 12, different land-play selection by Seat 4; CONTROL played Stormcarved Coast, A played Opal Palace.
- Seed 202610202112: submitted action **14**, global turn 1; A's added Breeding Pool Pay-2-life decision was declined while CONTROL passed priority after a tapped land entry. R2–R5 separately reproduced the exact shockland decisions, ruled both branches legal and found no demonstrated overall policy benefit before opponents' interaction.
- Seed 202610202141: submitted action **222**, global turn 7. CONTROL activated Evolving Wilds; A activated its in-slot Misty Rainforest. It is a real land/search divergence, not proof of the cause of an eventual 28-turn game-length difference.
- Seed 202610202142: submitted action **134**, global turn 5. The arms chose different cards for an in-hand PlayLand action.
- Seed 202610202143: submitted action **461**, global turn 13. CONTROL cycled a card and A passed priority.

The indices bind to the original action streams, not to inferred strategic decisions. Follow-up effects, opponent behavior, library shuffles/draws, and combo interactions must be examined before attributing terminal results.

## 5. Combo and protection observations

Across the ten games, actual logged SpellCastEvents include: Ancestral Statue 29 (CONTROL) versus 44 (A); Walking Ballista 6 versus 7; Purphoros, God of the Forge 3 versus 5; Pact of Negation 7 versus 6. Counts reflect real casts, including recursive Statue casts, NOT independently adjudicated combo conversion events or lethal wins. Both arms retain existing Pact debt/survival safeguards and validated Ballista/Statue protections. No new policy patch was applied. R6 established no new reproducible card-rules rejection.

No assertion of combo-assembly rate, fatal AI defect or causal advantage is justified merely from cast counts; the original event streams remain authoritative for any future exact terminal-line analysis.

## 6. Original SHA-256 ZIP artifacts and independent audit

| Accepted game | Exact artifact ID | SHA-256 ZIP |
|---|---:|---|
| R1 CONTROL seed 2111 | 12290531167 | 9fcacf8bd8b9e629e5944d38f76bee31abd139db447682e62bb65c6b5d142e4c |
| R1 A seed 2111 | 12290591119 | 7744f1916818bea1fcb3149474c7f696249c0fc075fce5380d268458eca1bb2c |
| R1 CONTROL seed 2112 | 12291060323 | 3379a5376d961d54b8c5883207c8499dac66d373765f8c3ff8a7d032cb62c853 |
| R1 A seed 2112 | 12290286293 | a614eb9998b25dd34803434bb9ca80cf113b545483a185eac49cfcd419c7fd09 |
| R6 CONTROL seed 2141 | 12298777958 | 031011134d10e3ae002ad7dc304ff7dafcc5e14f9a117c436e510dfb984349cb |
| R6 A seed 2141 | 12298778067 | a4396d43fdd831f5f67f2a6b865630af9a90ff1420c2b127be6e6b2f294a6efe |
| R6 CONTROL seed 2142 | 12299307002 | b0f08c6237e615b1befeeb47db515d5959e1b3dae7414fff3b46c5868efc9796 |
| R6 A seed 2142 | 12299690780 | db12379f8e87ec6736bd359634f16cbf9eb22042c40925509d1d08e913a6d421 |
| R6 CONTROL seed 2143 | 12299262511 | ddea4f0a6a9c96b6b484009a410e3c42f6c390e69342e4bb53752b4c347c3c39 |
| R6 A seed 2143 | 12299271355 | 1bb49d6bd7179ec4561cb2c635dca5a08653163d361c5509e81791521b46714d |

Original R6 admission artifact: **12297899241**, ZIP SHA-256 c2bc20f6968e07973e7c042c8b01fbd1390e27e3ab7da19e4ff3a94a2d16e367. Verifies the failed earlier R6 workflow executed zero games, original R1 seed set, exact three prospective R6 seeds, source tree/deck digests and preflight admission.

Original R6 independent audit: **12299506415**, ZIP SHA-256 4d583f0ba6c9621b898a03cab612c8ee60ab972c86e14e25edb2cf77ead49314. Its comparison.json verifies all six R6 arm artifacts were available, all six terminal, and all three pairs eligible for descriptive winner comparison.

Original R1 independent audit: **12289828339**, ZIP SHA-256 08258db17f30fc0b28b569dd80dfe97a05e73c0b7be0d7209da1a6d662cbf0df.

All ten individual ZIPs, original R6 admission and R6 audit ZIP were independently downloaded and SHA-256 checked. Independent extraction validated the R1 and R6 source commit/tree receipts, four-seat legal initializer bindings, R5 profiles, exact 64-action smoke-prefix identity against each full game, all action/result counts, terminal status and zero rejected actions. The R6 run completed successfully, and there was no rerun of its six games.

## 7. Disposition and remaining uncertainty

**INCONCLUSIVE — retain frozen Manual Transmission v0.7 as CONTROL; retain seven-land A as an isolated, promising-on-paper but NOT gameplay-qualified challenger.**

The results support that the substitutions change land timing and gameplay decisions, but do not establish an across-seat improvement in commander deployment, real colored mana availability, strategic combo conversion, or wins. Faster terminal matches alone cannot justify selecting A as the strongest physical build. The nine straight shockland life-payment declines demonstrate that the present R5 decision policy does not reliably capture the theoretical untapped-shockland benefit.

No rejection of the land package is proven either: fetch improvements may still be worthwhile. Do NOT interpret INCONCLUSIVE as evidence that premium lands are intrinsically bad or that life payment should always be forced.

Maintain the existing distinction between:
- Frozen v0.7 CONTROL and opt-in land-only A.
- Challenger B, still blocked by the absent rules-faithful Fierce Guardianship implementation.
- Challenger C, separately source-preflighted but not tested in this game comparison.
- Any opt-in AI-policy change, none adopted.

**Budget:** original 12 complete games allowed; R1 used 4, R6 used 6, **2 remain**. No more games, rerolls, merges, promotions, or policy changes were executed.

### Narrowest next step

Before spending the final two game slots, conduct an independent no-new-games, original-artifact-only land and terminal-line forensic comparison across the ten runs: classify each of the seven substitutions by actual turn-1 to turn-3 untapped access and realized color fixing, decide whether any are strong enough for real-world paper inclusion, and distinguish genuine demonstrated combo finishes from incidental casts. Cross-check the R2–R5 protected shockland decision evidence without changing R5. Only propose further gameplay with a separately declared decision criterion and predeclared seed/seat design; preserve the final two slots until then.
