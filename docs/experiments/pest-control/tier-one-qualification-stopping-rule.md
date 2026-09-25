# Pest Control Tier-1 qualification — bounded coverage and stopping rule

## Purpose

This document closes opponent admission before the remaining official gameplay is exposed. It is a
research stopping rule, not a performance result.

Frozen Pest Control identity:

- main SHA-256: `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- sideboard SHA-256: `c1910468c228662b21647eb7ca8481cd11691906e56e0a37990e00f22886368c`
- complete-75 SHA-256: `2927737eb084657cda58fd1877db933037c383273062f0bd209c7ff3046c1cf5`

No deck or sideboard change is authorized by this document.

## Fixed preboard strategic-axis gauntlet

The Tier-1 preboard question is bounded to exactly five strategic axes:

1. **Speed / burn / recursive pressure** — SoterX Mono Red Madness.
   - closed accepted qualification: Pest Control 32-18;
   - rejected Block A remains retired and is never pooled.
2. **Artifact midrange / resource recursion** — Pasquale Grixis Affinity.
   - closed mixed evidence: accepted 3-1 smoke plus accepted 4-7 `SALVAGE_CONTINUATION_11`;
   - no further Grixis sampling is authorized.
3. **Permission / tempo / cheap threats** — Serpico_CC Mono-Blue Terror.
   - closed accepted primary replication: Pest Control 11-1;
   - replication and smoke seeds are retired.
4. **Big mana / inevitability / Cascade / Prototype** — mehanske Monster Tron.
   - active; exact frozen four-game smoke is authorized.
5. **Dedicated combo / library-empty reanimation** — Dr_dej96 Spy Combo, MTGO Pauper League,
   2026-09-15, 5-0.
   - exact 60/15 source freeze is admitted below;
   - this is the final preboard opponent.

No sixth preboard opponent may be admitted merely because an existing result is unfavorable,
ambiguous, or surprising. Expansion requires a written demonstration that one of the five strategic
axes above fails to exercise a materially distinct strategic class required to answer the original
Tier-1 question.

## Monster Tron smoke and replication rule

Monster Tron smoke block:
`PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4`.

The smoke is implementation/readiness evidence only.

A **clean 4/4 smoke automatically fires the replication trigger regardless of win count**. Clean
means:

- all four frozen assignments attempted once in order;
- all four initialized and terminal;
- zero rejected or fallback actions;
- zero protocol defects;
- all durable attempt / initialization / raw-record transitions reconcile;
- no reroll, replacement, regeneration, resume, or salvage.

If clean, freeze one fresh 12-game preboard replication block with exact 6/6 Pest play/draw,
6/6 Pest seat-zero/seat-one and three observations in every seat × starting-deck cell. The 12-game
block executes once. A clean accepted 12-game result closes Monster Tron regardless of win count;
performance is classified descriptively and may be positive, neutral, or negative.

If the smoke has an outcome-independent infrastructure/protocol defect, the block is rejected and
the defect must be repaired fail-closed before a separately reviewed replacement protocol. Outcome
must never determine whether a defective block is repaired.

## Spy Combo rule

After Monster Tron closes, Spy Combo is the only remaining preboard opponent. It must follow the same
sequence:

1. exact 60/15 source freeze and rules/card-support audit;
2. deterministic opponent policy qualification;
3. disabled runner construction and preflight;
4. fresh four-game smoke vector freeze;
5. one clean four-game smoke;
6. one fresh 12-game replication if and only if the smoke is methodologically clean;
7. close the matchup after the accepted replication regardless of record.

No outcome-conditioned sample extension is allowed.

## Postboard closure

Postboard testing is restricted to these same five frozen opponent identities. It is a **sideboard
robustness axis**, not a new opponent-admission program.

Before any postboard seed exists:

- complete exact sideboard card support for both decks in each matchup;
- freeze deterministic boarding policies using only deck identity and public matchup category, never
  future seed or outcome knowledge;
- preserve exact 60-card postboard decks and 15-card exchange conservation;
- freeze every postboard deck digest;
- qualify sideboard pilot policy seedlessly.

For each matchup, one fresh balanced four-game postboard smoke is permitted. A clean smoke closes
sideboard robustness for that matchup unless the predeclared policy itself identifies an
implementation defect. **No automatic postboard replication is authorized.** The purpose is to
verify that the frozen sideboard plan remains legal and operational across the five strategic axes,
not to estimate five precise postboard win rates.

## Challenger disposition rule

The audit-only Tier-1 challenger is not automatically promoted by an unfavorable matchup. Promotion
requires a separately predeclared challenger protocol showing a repeated structural failure of v1.0
across at least two strategic axes and a clean improvement without invalidating the accepted control
evidence. If that trigger is never met, the challenger is finally rejected/not promoted.

## Project stop

After:

- Monster Tron and Spy Combo preboard closure;
- postboard robustness smoke closure for the five fixed axes;
- reconciliation of accepted/rejected/retired evidence;
- explicit challenger disposition;

the gauntlet stops. The repository must record one bounded conclusion:

- Tier-1 qualification earned;
- competitive capability demonstrated but Tier-1 qualification not earned;
- inconclusive under the bounded evidence; or
- structural redesign required.

No extra opponent or sample may be added after that point to improve the conclusion.
