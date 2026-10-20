# ARGENTUM — Animar R3 exact-state early-development continuations

**R3 STATUS: SUCCESSFUL THREE-BRANCH MICROFORK · NO FULL GAMES · NO POLICY CHANGES · INCONCLUSIVE FOR PROMOTION**

Source-bound forensic report, 2026-10-20. No frozen Manual Transmission, main, CURRENT, or accepted earlier gameplay source modified.

## Source authority

- Original R1 four-player run: [39477314282](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39477314282), source 84e6347bbcdfa2990eb304051771007daf849ce9, original Challenger A/seed 202610202112 archive 12290286293.
- Original R2 exact Breeding Pool microfork: run [39481840733](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39481840733), source 37e5cdd9b550a3b42c1c93f5c1d73b2751955822, archived state artifact 12291623583 (SHA-256 901efcd0f81a0f98aa0fddd52b27b885108bbfe1ac99b092e28fa32b9844d91f).
- Original R2 R5 continuation: run 39483773523, source 836df1cd72fb64585b6d14c1a0247380763b123d, artifact 12292258246.
- **R3 execution:** [run 39486360088](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39486360088) SUCCESS, exact source 50456046cc25878591df9a498f6e950aeb1a93b5. Relative to accepted R2 continuation source, exactly two new files: an isolated R3 test and its no-full-game Actions workflow.
- **R3 original artifact:** 12294360902, SHA-256 **012a3d9deb37b70f9eceb606b2d7a9f493f49cd9f1748be76522fb9a594f6388**, independently verified after download. Includes three action streams, three complete bounded end-state snapshots, JUnit receipt and structured result.
- Original decision: seed 202610202112, Challenger A, Seat 1, global turn 1; Breeding Pool e83, Arbor Elf e1, Generous Ent e40. Exact R2 fork state hashes: before 0df555d80bc0a4eab24577dd4cb6de0424564643597d2bd64379c9bde23014d0; No 552cd7d26bf9965077d5ea8b0e502301d07fd171bbd99bbc745fe9ce0b25afd5; Yes 7e8373fd825b1f50c09158d25b45621152548ff8743c68ca604df13332366031.

## Experimental boundaries

The three branches begin at the original real-engine Yes/No results. For all later actions, every seat uses the identical untouched profile manual-transmission-r5-survival-opt-in. The third branch forces exactly one real-engine legal Arbor Elf cast before reverting to R5. Each continuation stops on Seat 1's next own turn, at global turn 5 UPKEEP, before any actions on that turn. Hard caps: 550 accepted actions and 70 seconds per branch. All reached their intended boundary without censors, engine rejections, or terminal game states. These are **not** additional full games or a new sample of outcomes.

## Exact global-turn-5 comparison

| Seat 1 metric | Original No + R5 | Yes + unchanged R5 | Yes + forced Elf, then R5 |
|---|---|---|---|
| Life | 40 | 38 | 38 |
| First action | Pass priority | Forestcycle Generous Ent | Cast Arbor Elf |
| Accepted actions to cutoff | 107 | 109 | 112 |
| Lands in play | Breeding Pool | Breeding Pool | Breeding Pool |
| Relevant land in hand | Rockfall Vale | Rockfall Vale + Stomping Ground | Rockfall Vale |
| Arbor Elf | Hand | Hand | Battlefield |
| Generous Ent | Hand | Forestcycled | Hand |
| Hand size | 7 | 7 | 6 |
| Rejected actions | 0 | 0 | 0 |

At this cutoff, all opponents had identical visible one-land boards, hands and 40-life totals across all branches; none had a creature. The cycling branch's hidden library order diverged through legitimate shuffle/search, so this is a bounded same-source policy comparison, not a promise of matched future card draws.

Direct evaluator leaf scores at end of Seat 1's first own turn were No=4.65, Yes/R5=3.65, Yes/forced Elf=4.635. This comparison does not establish terminal superiority.

## Why the recorded second-turn 141.9 score is misleading

At global turn 5, direct evaluator scores read No=0.0, Yes/R5=-1.0, and Yes/forced Elf=141.9. The last figure **must not** be cited as evidence of a massive real advantage.

The inherited legacy ThreatAssessment implementation uses a sentinel 99-turn opponent clock when the opponent has zero attacking power. A ready, unsick 1/1 Arbor Elf facing a 40-life opponent generates a theoretical 40-turn clock. The source expression awards (99 - 40) × 2.0 = 118 raw points, then multiplies by threat-assessment weight 1.2, yielding **141.6** points of artificial race credit. This explains nearly all of the measured 141.9 and is a documented legacy evaluator scale artifact, not a prediction of winning imminently.

Separately, Forestcycling Generous Ent receives a +5.00 structural early-land-development sequencing bonus. This score is not a guarantee that the fetched Stomping Ground will be deployed or provide extra mana on the following turn.

## What is established and what remains unknown

- Paying two life for untapped Breeding Pool is legal and unlocks a real turn-one Arbor Elf cast.
- Forcing that cast, with unchanged R5 thereafter, creates a surviving Arbor Elf in play by the next personal turn, while unchanged R5 uses the mana to cycle Generous Ent and obtain Stomping Ground instead.
- Both are distinct early-development resources. At the end of the bounded comparison, neither branch has played the next land, tapped Arbor Elf for the next turn's mana, or attempted earlier Animar deployment.
- A ready Arbor Elf can untap a Forest, including Breeding Pool, but its realized mana advantage on the next turn is not established by a simulated activation in R3.
- R5's immediate two-life valuation is -0.55 (life charge -1.00 plus untapped-land board benefit +0.45); a one-step valuation cannot credit an unexecuted creature-development line.

**R3 disposition: INCONCLUSIVE for any policy patch, deck adoption or qualification.** The best next delta is to resume the three exact global-turn-5 snapshots only through the end of Seat 1's second personal turn (stop before global turn 6), while observing real land-entry choices, mana uses, Arbor Elf activations, action scoring and first meaningful Animar opportunity. Do not trust the legacy 99-turn race term as a payoff. Keep the same opponents, bounded budgets, frozen source, and zero full-game authorization.

R1 full-game budget remains **4 used / 8 available** out of 12. No R2/R3 full games, new seeds, merges, main changes, CURRENT changes or frozen deck changes.
