# ARGENTUM — Manual Transmission Precision Upgrade R1 Stage 1

**STATUS: FOUR FULL GAMES COMPLETE · ORIGINAL ARTIFACTS VERIFIED · INCONCLUSIVE · NO ADOPTION**

Source-bound forensic report, 2026-10-20. This research report does not modify experimental source, main, frozen Manual Transmission v0.7 or AiProfile.CURRENT.

## Pinned authority

- Repository: GodaPupa/argentum-batshit-test.
- Frozen v0.7 Git blob: c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89.
- Frozen v0.7 measured SHA-256: 2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1. The header-declared SHA-256 6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111 is **not** the measured digest; the frozen file has not been changed.
- Challenger A: exactly seven positional manabase swaps; Git blob 7984a2e87d8ddbe023d45620414d25a61691a040, measured SHA-256 5bfa4467962b105c70408f555c72b2908423342debb8e454acd0419c7b0a5fed.
- Successful preflight source aafa138b1cbf6a7f4f024abdef716df4ae75e741; original run [39472941354](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39472941354), artifact 12287399974, ZIP SHA-256 67cac362825c661e367a368eaf6df34f370a2b40c5a5411bedd58a0b14310fc8.
- **Stage 1 execution source:** 84e6347bbcdfa2990eb304051771007daf849ce9, exactly one workflow-file addition over the accepted preflight source.
- **Stage 1 original Actions run:** [39477314282](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39477314282), SUCCESS, admission + four game jobs + independent read-only audit all successful.
- Stage 1 admission artifact 12288789926, ZIP SHA-256 031e9e4d34623ff34da8d3e8d43167c5a9c8c9f771599298e1a0388716d2e05a.
- All eight smoke/full executions used pinned manual-transmission-r5-survival-opt-in AI profile in all four seats. Seeds 202610202111 and 202610202112 were predeclared. Every smoke reached 64 accepted actions before its separate complete game; no rerolls or substitutions.

## Four verified terminal results

| Seed | Arm | Winner | Terminal global turn | Accepted actions | Engine rejections |
|---|---|---|---:|---:|---:|
| 202610202111 | CONTROL | Seat 1 | 66 | 3,193 | 0 |
| 202610202111 | A | Seat 4 | 67 | 3,177 | 0 |
| 202610202112 | CONTROL | Seat 2 | 50 | 2,963 | 0 |
| 202610202112 | A | Seat 2 | 47 | 3,744 | 0 |

Each original summary has COMPLETE status, gameOver=true and no error. Original terminal action traces show combat damage as the final event; do not infer absence of other combo attempts.

**First Animar cast, measured in each player's own turns:**

- Seed 202610202111: identical in CONTROL and A: Seat 1=6, Seat 2=8, Seat 3=8, Seat 4=9.
- Seed 202610202112: CONTROL Seat 1=9, Seat 2=11, Seat 3=never, Seat 4=4; A Seat 1=8, Seat 2=9, Seat 3=never, Seat 4=4.

**Interpretation:** A ended one global turn later on seed 111, with a different winner; A ended three global turns earlier on seed 112, with the same winner and earlier commander deployment in two seats. The outcome signal is mixed. These are two separate all-CONTROL and all-A four-player mirror matches, not head-to-head win-rate evidence. Small sample; no superiority claim.

## First divergences — original traces

- Seed 202610202111 at submitted action 390/global turn 12: CONTROL Seat 4 played Stormcarved Coast, entity e384; A Seat 4 played Opal Palace, entity e372. This is a genuine early land-choice difference but not by itself proof of a later winner's cause.
- Seed 202610202112 at action 13/global turn 1: Seat 1 in both arms played the same original land slot e83, CONTROL Dreamroot Cascade versus A Breeding Pool. CONTROL's land entered tapped. A's Breeding Pool requested the shockland life-payment choice; at action 14 the AI answered No, and the land entered tapped. The differing index 14 records this additional mandatory decision, not independently a strategic mistake.

## Forensic candidate: shock-entry payment

Original A action/event streams contain exactly **six shockland-entry decision events; all six declined to pay 2 life**:

- Seed 202610202111: Steam Vents at global turns 14 and 16; Stomping Ground at turn 17.
- Seed 202610202112: Breeding Pool at turns 1 and 26; Stomping Ground at turn 9.

**Specific possible missed tempo line:** In seed 202610202112, Seat 1's frozen-index-aligned opening hand contained Breeding Pool (e83) and Arbor Elf (e1). At turn 1, A declined the 2-life untapped-entry choice. An untapped Breeding Pool would ordinarily permit green mana for Arbor Elf on turn one, and Breeding Pool is also a Forest eligible for Arbor Elf's untap ability. In the observed game, Arbor Elf was not cast until global turn 9. This merits an exact decision-state micro-fork (Yes versus No) and R5 scoring review before claiming a defect or altering policy. No assertion that this counterfactual would win the game is made.

The replacementActionCounts telemetry logs CastSpell/ActivateAbility sources; it does not count all land plays or shockland prompts. Low counts there cannot be used to infer non-use of upgraded lands.

## Verified original ZIP artifacts

| Original arm | Artifact ID | SHA-256 digest |
|---|---:|---|
| CONTROL 202610202111 | 12290531167 | 9fcacf8bd8b9e629e5944d38f76bee31abd139db447682e62bb65c6b5d142e4c |
| A 202610202111 | 12290591119 | 7744f1916818bea1fcb3149474c7f696249c0fc075fce5380d268458eca1bb2c |
| CONTROL 202610202112 | 12291060323 | 3379a5376d961d54b8c5883207c8499dac66d373765f8c3ff8a7d032cb62c853 |
| A 202610202112 | 12290286293 | a614eb9998b25dd34803434bb9ca80cf113b545483a185eac49cfcd419c7fd09 |
| Independent audit | 12289828339 | 08258db17f30fc0b28b569dd80dfe97a05e73c0b7be0d7209da1a6d662cbf0df |

All five downloaded ZIP bytes were independently SHA-256 checked. The independent audit includes comparison.json and REPORT.md, confirms 4/4 original games, and explicitly disclaims causal/superiority inference.

## Disposition and next work

**INCONCLUSIVE — retain Challenger A as an opt-in research configuration.** Four of the twelve authorized full games have been used; eight remain. Prefix-only smokes are not full games.

Prioritize a separate, exact turn-one shockland Yes/No forensics pass using the real seed-202610202112 state, then narrowly diagnose whether existing R5 policy misses a worthwhile Arbor Elf acceleration line. Do not modify CURRENT or confound A-versus-CONTROL deck comparisons with a simultaneous AI-policy correction.

Only after confirming this decision issue should a prospective three-new-seed CONTROL-versus-A matched sample (six full games) be considered under the original twelve-game cap. Do not cherry-pick or reroll seeds.

Challenger B remains blocked on rules-faithful Fierce Guardianship support. Challenger C's source, catalog and initializer were preflight-tested, but C has zero full-game evidence and must not be silently substituted for B.

No promotion, official qualification, merge, or frozen-state change is authorized.
