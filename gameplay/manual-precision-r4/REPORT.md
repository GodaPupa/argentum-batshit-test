# ARGENTUM — ANIMAR R4 SECOND-TURN DEVELOPMENT FORENSICS

**Status: R4 EXECUTED SUCCESSFULLY · THREE EXACT REAL-ENGINE BOUNDED CONTINUATIONS · ZERO FULL GAMES · NO POLICY CHANGES · INCONCLUSIVE FOR ADOPTION**

Report date: October 20, 2026. Source-bound, read-only decision analysis. The R1 original four-game comparison is immutable.

## Original source, run and artifacts

- Repository: GodaPupa/argentum-batshit-test.
- Original R1 full-game source: 84e6347bbcdfa2990eb304051771007daf849ce9, successful run 39477314282. Exactly four completed full games, no engine rejections.
- R2 primary real-engine Breeding Pool action-14 fork: successful run 39481840733, artifact 12291623583.
- Accepted R3 three original-state continuations: run 39486360088, source 50456046cc25878591df9a498f6e950aeb1a93b5, original artifact 12294360902, ZIP digest SHA-256 012a3d9deb37b70f9eceb606b2d7a9f493f49cd9f1748be76522fb9a594f6388.
- R3 original-snapshot SHA-256: no-r5 fb00c73279e64ca5b13c05ef9bef952e96ea1d864f1164cf417e8286192f62ac; yes-r5 70b12ac34651e0bbcdddc4d2cdcd62527f5a833c2ec2abc6585a5b87cbcd4af0; yes-elf-r5 ac2165c71b5f441d9d079010ad604bcbfc7ba4c51b87eae9d20f0b09b847d49a. R3 combined summary SHA-256 19c805675e902bc012fd754313478037ae41c6857a634ddeb407dce71d73b7f2. All originals separately hash-verified.
- R4 isolated source branch: research/manual-precision-r4-second-turn-20261020.
- **R4 exact source commit: a8fdd3d072167d824468d0e7073b3f8f264e6a72; Git tree: 165c115d1233c03d3e0f76c12c7b3db68c971467.** Exactly two new test-only files relative to pinned R3 execution source, a source-bound Kotest and no-full-game workflow. No main, frozen deck, current profile, engine or R5 policy modification.
- **R4 successful GitHub Actions run: [39490896191](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39490896191)**, attempt 1, all source and original-artifact gates PASS; test PASS.
- **Original R4 artifact: 12296381441**, ZIP digest SHA-256 **cfef51e23fe01f3676d9fc06821ace344b35460d4bdd31a884105968c7424a22**, independently downloaded and byte-verified. Contains individual original action streams, three end states, separate summaries, JUnit results, and original R3 binding receipts.

## Experimental boundary and validity

Begin with the three exact R3 saved GameState snapshots at the very start of global turn 5 (Seat 1's second own turn). Every seat uses the unchanged manual-transmission-r5-survival-opt-in policy. No actions are forced in R4. Stop as soon as global turn 6 begins, before Seat 2's next turn actions; strict maximum 450 actions and 75-second per-branch wall clock. All three naturally reached the intended boundary, with zero rejections, no censorship, no game-over, and no full game.

The R3 input branches already differ in hand contents and RNG/library history because cycling/shuffling legitimately happened at different times. Identical policies therefore do NOT mean identical hypothetical future draws, opponent responses, or terminal outcomes.

## Verified second-turn outcome

| At start of global turn 6 (after Seat 1's second turn) | Original NO + R5 | Pay two life + unchanged R5 | Pay two life + forced turn-one Arbor Elf, then R5 |
|---|---|---|---|
| Life | 40 | 38 | 38 |
| Original second-turn actions accepted | 31 | 34 | 42 |
| Land played on second turn | Rockfall Vale | Rockfall Vale | Rockfall Vale |
| Lands in play | Breeding Pool, Rockfall Vale | Same | Same |
| Untapped land at end of second turn | 0 | 0 | 1: Breeding Pool |
| Generous Ent | Forestcycled during second-turn upkeep | Already Forestcycled on first turn | Forestcycled during second-turn upkeep |
| Arbor Elf | Still in hand | **Cast on second turn, remains on battlefield** | Was cast turn one; destroyed on second turn |
| Other creature gained | None | None | **3/3 Frog Lizard token** |
| Stomping Ground | In hand after cycle | In hand after previous cycle | In hand after cycle |
| Cinder Glade | Drawn, in hand | Drawn, in hand | Drawn, in hand |
| Seat 1's second-turn mana expenditure | 1 blue to Forestcycle Ent | **1 green to cast Arbor Elf** | 1 blue to Forestcycle Ent |
| Arbor Elf untap activation | None | None (just cast) | Activated to untap Breeding Pool; resolved after Elf died |
| Shockland pay-two-life prompt on second turn | None | None | None |
| Animar legally castable during observed windows | No | No | No |
| Animar deployed | No | No | No |
| Engine rejections | 0 | 0 | 0 |

Recorded R4 end-state SHA-256:
- no-r5: 1a13bbc3f452f799759f06272e61c1b832f05caa0a257c27c7492e102357fe71
- yes-r5: a707fb6e95b9303b82d37e2c5c40fd34eecbdba9e3039581a211c21d6d1530e4
- yes-elf-r5: ce85f9fe2099d96f2bd61067e3a661a802a9773506bd693fc162c799a8ee582b
- Exact combined comparison JSON SHA-256: 6f95f8b19e4b7b892677458dd2a4d458b24d909501068374a8ed3556254912a5
- Original individual action trace SHA-256: no-r5 0fa99cbb5db366a5f28da49c5758baf08861b1838db70b3d20519ab5c4fdebc3; yes-r5 36743edf85ada2f2a963320e0a276c68b053cc74c1a628f10d08a99fb7d66b36; yes-elf-r5 07cd7373793d54d52db20fd32362c45b0c6a80a1bc4c2c1cec068c2c168677cd.

## Original action-level findings

1. **NO + unchanged R5:** At the start of global turn 5 upkeep, R5 spent U from Breeding Pool to Forestcycle Generous Ent, selected Stomping Ground from the library, and then drew Cinder Glade. In precombat main R5 chose Rockfall Vale among equally scored legal land plays. With its remaining one other land, Rockfall Vale entered tapped. R5 cast no spell and left Arbor Elf in hand.
2. **YES + unchanged R5:** Generous Ent had already been Forestcycled on turn one thanks to the shockland payment. On turn two R5 drew Cinder Glade, played Rockfall Vale, and cast Arbor Elf, spending G from Breeding Pool. The Elf resolved and survived. This is a REAL observed one-turn advancement of Arbor Elf deployment relative to NO; it cost two life and has not yet yielded a third-turn untap.
3. **YES + forced turn-one Elf + R5:** R5 Forestcycled Generous Ent at global turn 5 upkeep, then activated Arbor Elf to untap Breeding Pool after tapping it for the cycle. The opponent in Seat 2 (manual-mirror-1) responded at R4 action 5 by casting Rapid Hybridization, spending U and targeting Elf. At action 9 the instant resolved, moving Elf from battlefield to graveyard and creating a 3/3 Frog Lizard token for Seat 1. The already-stacked Arbor Elf untap ability then resolved at action 13, untapping Breeding Pool. R5 played Rockfall Vale later but cast no additional spell with the freed land. At end of turn, Elf was absent and Breeding Pool remained untapped. This interaction is legal and demonstrates a genuine opponent response, not a synthetic creature-removal assumption.

Original choice diagnostics are source-bounded. The unchanged R5 turn-two land scoring assigned equal immediate 1.2 scores to Rockfall Vale, Stomping Ground and Cinder Glade in the NO branch; in YES/R5 each was 0.65 before casting Elf. Relative turn-three mana colors therefore cannot be inferred from which land the heuristic might have preferred.

## What these observations do and do not establish

**Proven:** The first-turn two-life payment enables R5 to Forestcycle Generous Ent a turn earlier. With that earlier land search already completed, unchanged R5 successfully casts Arbor Elf on turn two. The original NO branch waits until turn two to cycle Generous Ent and does not cast Elf by that turn's end. The forced-Elf branch encounters real opponent removal and does not retain a mana creature, but obtains a 3/3 token and consumes opposing interaction.

**Not proven:** That paying two life is a consistently superior decision across other states, that the forced-Elf line is inferior in a full game, that the Mana Confluence/shockland package improves win rate, or that earlier Elf presence translates into an earlier Animar or combo kill. Neither R4 branch generated or spent additional mana from a surviving Arbor Elf; the YES/R5 Elf is newly cast and summoning-sick at the cutoff.

Do not use the raw **141.9** score recorded for forced Arbor Elf at the R3 boundary to declare victory. Most of it is the inherited 99-turn no-opponent-attack race-clock sentinel: (99 - 40) x 2.0 x 1.2 = **141.6** artificial points. Other R4 raw scores also are legacy evaluator outputs, not calibrated win probabilities or observed mana.

## Decision and next action

**Disposition: PROVEN SECOND-TURN DEVELOPMENT DIFFERENCE, BUT INCONCLUSIVE FOR STRATEGIC POLICY REPAIR OR DECK ADOPTION.**

The primary case shows that the two decisions are linked but an immediate *forced* Arbor Elf cast is not necessary to get earlier Elf deployment: changing the shockland decision alone, while preserving R5's subsequent Forestcycling choice, yields an additional mana creature by the second-turn cutoff. That is promising, but has a real two-life cost; the extra creature's third-turn contribution and resilience are untested.

Next narrow authorized step: resume the three original R4 states into Seat 1's third turn only, with actual Arbor Elf mana activation, future draw/land access, opponent responses and earliest legal Animar cast compared. Alternatively, test a minimally scoped, strictly opt-in shock-payment/development selector using held-out exact states (including the two documented justified/no-new-spell declines) before proposing any policy change. Do not change production, CURRENT or frozen v0.7; do not combine deck and AI-policy candidates in the same sample.

No complete four-player games were performed in R2/R3/R4. The original Stage 1 budget remains **4 used / 8 remaining** out of 12. Challenger B remains unsupported until rules-faithful Fierce Guardianship; C remains a distinct, unpromoted deck challenger.
