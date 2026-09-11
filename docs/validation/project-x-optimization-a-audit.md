# Project X Optimization Experiment A — paired audit

## Disposition

Experiment A is preserved as a single, frozen 30-pair execution. The control is Project X v0.2 exactly. Variant A differs only by `-1 Falkenrath Noble`, `-1 Masked Vandal`, and `+2 Llanowar Elves`; all four Evolution Witness remain.

The variant is not promoted by this report. No rerolls, replacements, exclusions, seed substitutions, deck changes, or policy changes occurred.

## Provenance and validation

- Readiness tip: `bb93886c838f7fa54ce867bbfd49f1c3bb374aaf`
- Official seed freeze: `03ec58f134a9a05da5f3dff9bfe9cb57edfd2fa1`
- Execution head: `9875ae913ed5f2ec686dcf0179f697a039e97665`
- GitHub Actions run: `34547803872`
- Artifact ID: `10180210314`
- Artifact ZIP SHA-256: `04189e63873cc2bcf8a496123ad3f1d34b29c65a2c29b1c5bbe07c2f898c3bd3`
- Raw JSON SHA-256: `9f040b70f5bb19e87ccebcbc58d4d4cc9a1858e3fa545fafab75be1266c5860a`
- Raw Markdown SHA-256: `5611621e3bc697a9b8422aead6fa2da0a0e998ba3703c804e1c9a3cca5d6f6d1`
- The complete raw JSON is preserved losslessly as four ordered text parts (`00` through `03`). Concatenating them byte-for-byte reproduces the raw JSON hash above.
- Compile, full engine tests, Gym tests, Gym trainer tests, paired block, and artifact upload: PASS
- Frozen seeds: 30 unique; 30 control games and 30 paired variant games
- Harness audit errors: 0/60
- Executor-rejected casts: 0/447 applied casts
- Policy preflight diagnostics: 108, all explicitly classified as requiring Birchlore mana before auto-payment; none reached the executor as an illegal cast
- Winding Way mode agreement: 36/36 agent choices matched the resolved rules choice
- Wedges/action-cap failures/unresolved selections: 0

## Telemetry qualification

The raw artifact's redundant `successfulRecursions.counterSource` field is not authoritative. Because the target decision can precede the harness observing the corresponding trigger event, 16 of 18 embedded labels are `UNKNOWN`, and two contain a stale prior `ADAPT` label. This is a report-queue correlation issue, not a card, rules, engine-state, mana, or solitaire-policy defect.

The canonical `countersPlaced` and `counterTriggers` streams are complete and source-attributed. They reconstruct the successful-recursion sources unambiguously by pair and turn: control has nine Adapt-sourced returns and one Ivy Lane Denizen-sourced return; Variant A has six Adapt-sourced returns and two Ivy-sourced returns. Pair 26 control contains one return from each source, both returning a Swamp, so permanent-identity totals remain unambiguous. Raw output is preserved without alteration; analysis must use the canonical trigger stream for counter source and the successful-recursion stream for returned identity/deployment.

## Primary paired outcomes

| Outcome | Control | Variant A | Paired interpretation |
|---|---:|---:|---|
| First Ivy cast by T3 | 0 | 1 | Pair 15 gained T3 Ivy |
| First Ivy cast by T4 | 3 | 6 | Two shared pairs moved one turn earlier; Pair 15 was variant-only |
| First Ivy cast observed | 14/30 | 14/30 | 11 same, 2 one turn earlier, 1 variant-only, 1 control-only, 15 neither |
| Exactly-three-mana Ivy stall turns | 31 | 22 | -9 (-29.0%); four pairs improved, one worsened |
| Primary engine assembled | 3/30 | 4/30 | Pair 15 changed `NONE -> T6`; no control assembly was lost |
| Any validated infinite assembled | 3/30 | 4/30 | Identical to primary; no secondary Witness loop assembled |
| Engine by T4 / T5 / T6 | 0 / 0 / 2 | 0 / 0 / 3 | +1 by T6 |
| Combo before ordinary lethal | 3/30 | 4/30 | Pair 15 changed false to true |
| Actual wins by T4 / T5 / T6 | 0 / 3 / 16 | 0 / 3 / 17 | +1 by T6 |
| Median actual win | T6 | T6 | unchanged |
| Mean actual win | T6.633 | T6.433 | paired mean delta -0.20 turn |

Actual-win paired deltas: five pairs improved (3, 5, 19, 20, 23), twenty-two tied, and three slowed (8, 10, 28). The net paired delta is six turns earlier across 30 pairs. This small experiment is descriptive, not a promotion decision.

The primary engine appeared in pairs 14, 21, and 25 for both treatments at the same turns, plus pair 15 for Variant A at T6. Pair 15 also produced the only Noble deterministic-drain win. Huge Feeder occurred in 3 control games and 4 variant games; infinite life occurred in 0/30 for both; the validated secondary Witness loop occurred in 0/30 for both.

## Ivy Lane Denizen detail

- Shared observed Ivy casts: 13 pairs; 11 tied and 2 improved by one turn (pairs 5 and 10).
- Variant-only Ivy: pair 15 at T3; this pair subsequently assembled the primary engine at T6.
- Control-only Ivy: pair 19 at T6; Variant A instead ended the game by ordinary lethal on T5.
- Three-mana stalls improved in pairs 5 (-1), 15 (-2), 20 (-6), and 23 (-1); pair 27 worsened by one; all other pairs were unchanged.
- Among pairs where both treatments cast Ivy, the mean paired Ivy delta was -0.154 turn. Unmatched observations are reported separately rather than imputed.

## Llanowar Elves

- Casts: 7 across 7 variant games (T1: 1; T2: 2; T3: 1; T6: 2; T7: 1).
- Legal mana activations: 15, producing 15 green mana events. Turn distribution: T2: 1; T3: 3; T4: 4; T5: 3; T6: 2; T7: 2.
- Exact funded spells: Safehold Elite T2 (pair 2), Wirewood Herald T7 (pair 3), Ivy Lane Denizen T7 (pair 4), Ivy Lane Denizen T3 (pair 15), and Lead the Stampede T4 (pair 27).
- Ten other activations generated mana without making a subsequently cast spell newly affordable in the same telemetry window; they are retained as legal activations, not credited as funded spells.
- Pair 15 is the clearest structural hit: Llanowar funded T3 Ivy, removed two recorded three-mana stalls, changed primary/any-infinite assembly from none to T6, changed combo-before-lethal from false to true, and ended via Noble drain on T6.

## Evolution Witness

| Metric | Control | Variant A |
|---|---:|---:|
| Witness casts | 22 | 20 |
| Adapt activations | 15 | 11 |
| +1/+1 counter placements | 22 (15 Adapt, 7 Ivy) | 20 (11 Adapt, 9 Ivy) |
| Recursion triggers resolved | 10 (9 Adapt, 1 Ivy) | 8 (6 Adapt, 2 Ivy) |
| Returned permanents | 10 | 8 |
| Returned primary roles later deployed | 2 | 2 |

Control returned: Ivy Lane Denizen x2, Wirewood Herald x2, Safehold Elite x2, Swamp x2, Quirion Ranger x1, Forest x1. Variant A returned: Safehold Elite x2, Ivy Lane Denizen x1, Khalni Garden x1, Llanowar Elves x1, Wirewood Herald x1, Forest x1, Swamp x1.

Later deployments after recursion were identical in count: Ivy Lane Denizen, Wirewood Herald, Forest, and Safehold Elite once each in both treatments. No secondary Witness loop assembled.

## Noble, tutoring, selection, and mana

| Metric | Control | Variant A |
|---|---:|---:|
| Noble casts | 5 | 2 |
| Games with Noble available | 15 | 7 |
| Noble-availability turn observations | 74 | 32 |
| Primary-engine-without-Noble windows | 5 turns / 3 games | 5 turns / 3 games |
| Noble deterministic drain | 0 | 1 |
| Herald tutor resolutions | 12 | 11 |
| Herald targets: Elite / Ivy | 8 / 4 | 6 / 5 |
| Winding Way casts: Creature / Land | 10 / 8 | 11 / 7 |
| Winding Way cards to hand / graveyard | 28 / 44 | 31 / 41 |
| Lead casts / creature yield | 16 / 49 | 16 / 51 |
| Birchlore mana events | 45 | 13 |
| Nettle untap events | 70 | 83 |
| Quirion lines used | 13 | 10 |

No observed control engine had a Noble deterministic kill available that was lost in its paired variant. Variant A retained one Noble and demonstrated deterministic Noble lethal in pair 15.

## Fair-board and bottleneck telemetry

- Total combat damage: 660 control versus 659 variant; paired net -1.
- Mean maximum creature board: 4.83 control versus 5.10 variant; summed maxima 145 versus 153.
- Functional without primary combo: 26/30 for both treatments.
- Exactly-one-primary-role-short games: 23/30 for both; recorded short-state intervals were 47 control and 46 variant.
- Genuine color-bottleneck games/events: 25/97 control versus 23/93 variant.
- Insufficient-total-mana classification appeared in all 30 games for both; event observations fell from 1,041 to 886.
- Khalni Garden tempo: 6 events in 6 games for both.
- Haunted Mire tempo: 7 events in 6 control games versus 6 events in 5 variant games.

## Pair audit disposition

All 30 pairs passed rules/state, terminal classification, mana-execution, Winding Way choice, combo-recognition, and solitaire-agent audit. Pairs 4, 7, 10, 11, 14, 20, 23, 24, and 26 contain Witness recursion and use the canonical trigger stream for source attribution as documented above. The raw artifact remains the source of record; no game was rerun or replaced.
