# Project X v0.2 — Goldfish Sample #2 replication audit

## Disposition

Project X Goldfish Sample #2 is formally **accepted as independent replication performance evidence** for the frozen Project X v0.2 deck and validated solitaire laboratory.

Sample #1 remains accepted independently. Its 30 seeds and Sample #2's 30 seeds are permanently retired from all future performance and optimization testing. Neither sample may be rerolled, replaced, excluded, or repurposed as an optimization vector.

- Accepted Sample #1 preservation tip: `beb201cbbe1b10d13b11d28eace8d50783a9ba5f`
- Missing-role telemetry commit: `8add6d61090a3f46aef7979dd227aa8bb5629fd3`
- Sample #2 seed-freeze commit: `7601745cda845ec7f2022a6daccec209c2b8252a`
- Immutable execution commit: `484054f3f662c8398f0e06339c0bf937b4e709db`
- Full CI: [run #122](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34524077624), passed
- Argentum Validation and one-time Sample #2 execution: [run #129](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34524077653), passed
- Artifact ID: `10171661314`
- Artifact ZIP SHA-256: `e778cbd524b713b022ec9fc8e9c3cf889d52481eb81095f6d1a78e9b90001610`
- JSON SHA-256: `9b6bcd6042c94372120885689d3950aa78e4000e7d3101e0d84d66f2b746dd3c`
- Markdown SHA-256: `c9e98057c53d690867658b56c7b8fe50be0726f7b8a877921ed15357505bfd6b`
- Frozen seed CSV SHA-256: `49445744bfb0a9575697765e6f859c28dce6ef4a3bf4a3db55e73b04d2bcafa0`

## Seed and laboratory provenance

Exactly 30 new deterministic seeds were frozen before execution. The vector was unique and had zero overlap with 464 currently preserved seed rows and all reachable Project X/Batshit seed-bearing Git history and development literals. The artifact contains the exact frozen order, with 30 unique games and no reruns, substitutions, exclusions, or replacements.

The Project X v0.2 deck, no-sideboard state, engine, Gym execution path, solitaire agent, mulligan policy, horizon, decision policy, and Sample #1 reporting definitions were unchanged. The only addition was the requested observational primary-role-short attribution telemetry; it did not affect actions or game state.

## Sample #2 — actual-win clock

| Metric | Result |
|---|---:|
| Wins by T4 | 0/30 (0.0%) |
| Wins by T5 | 2/30 (6.7%) |
| Wins by T6 | 14/30 (46.7%) |
| Median actual winning turn | 7.0 |
| Mean actual winning turn | 7.00 |
| Combat lethal | 29/30 (96.7%) |
| Triggered/ability lethal | 1/30 (3.3%) |
| Deterministic combo lethal | 0/30 |

Winning turns: T5 x2, T6 x12, T7 x8, T8 x3, T9 x4, T12 x1.

## Sample #2 — combo clock

| Metric | Result |
|---|---:|
| Primary engine online by T4 / T5 / T6 | 0 / 0 / 0 |
| Any validated infinite online by T4 / T5 / T6 | 0 / 0 / 0 |
| Games assembling a validated combo | 0/30 |
| Secondary Evolution Witness loop | 0/30 |
| Median combo turn among assemblers | Not reached |
| Combo available before game end | 0/30 |
| Huge Carrion Feeder | 0/30 |
| Infinite life | 0/30 |
| Falkenrath Noble deterministic drain | 0/30 |
| Ordinary lethal before combo assembly | 30/30 |

The symbolic recognizer remained active through the exact solitaire/Gym path. No primary or secondary infinite state was observed, and ordinary lethal was never mislabeled as combo lethal.

## Sample #2 — development and contribution

- Mulligans: 10/30 games (33.3%), 14 total
- Opening color access: both 24; green only 4; black only 0; none 2
- Functional without primary combo: 28/30 (93.3%) under the frozen Sample #1 classification threshold
- Historical hand-or-battlefield exactly-one-role-missing metric: 26/30 (86.7%)
- Wirewood Herald: contribution in 16/30 games; 26 resolved tutors
- Herald targets: Ivy Lane Denizen 14, Safehold Elite 12
- Evolution Witness recursion: 9/30 games, 10 events
- Birchlore / Nettle / Quirion contribution: 6/30 / 14/30 / 4/30 games; 18 / 75 / 8 events
- Winding Way: 23 resolutions in 17 games; Creature 14 and Land 9; 42 cards to hand and 50 to graveyard
- Winding Way yields: Creature 31/14 (2.21 average), Land 11/9 (1.22 average)
- Lead the Stampede: 15 resolutions in 14 games; 38 creatures to hand
- Khalni Garden / Haunted Mire tempo: 7 / 6 games

The two zero-color opening hands occurred after two mulligans and legally recovered to wins on T12 and T9. This was the unchanged frozen mulligan policy, not a mid-sample intervention or demonstrated execution defect. The two `NONFUNCTIONAL_WITHOUT_COMBO` classifications both still reached legal combat wins; the label is the preserved Sample #1 telemetry threshold, not a claim that the game could not function.

## Primary-role-short attribution

The Sample #1 hand-or-battlefield metric remains unchanged for direct comparison. Sample #2 additionally records a battlefield-assembly view whenever exactly one primary role is absent from the battlefield.

| Missing battlefield role | Games | Determinable attributions |
|---|---:|---|
| Carrion Feeder | 0 | None observed |
| Safehold Elite | 4 | Never drawn 1; sacrificed 3 |
| Ivy Lane Denizen | 14 | Mana constrained 11; never drawn 3; tutored but not yet cast 7 |

Attributions can overlap in a game. No primary role was observed leaving because of combat, another non-sacrifice battlefield departure, or a game ending with that role still undeployed in hand while exactly one battlefield role short. The three sacrifice cases were Safehold Elite states after legal sacrifice/persist development; the relevant copies were in graveyard/library and no complete engine was available.

All ten observed states where Herald was on the battlefield with Carrion Feeder, exactly one hand-or-battlefield primary role missing, and that role legally searchable resolved to the missing role: Ivy Lane Denizen in seven states and Safehold Elite in three. No fastest-line Herald policy failure was demonstrated.

## Sample #1 versus Sample #2

| Metric | Sample #1 | Sample #2 |
|---|---:|---:|
| Wins by T4 / T5 / T6 | 0 / 2 / 11 | 0 / 2 / 14 |
| Median / mean actual win | 7.0 / 6.97 | 7.0 / 7.00 |
| Combat / triggered / combo lethal | 29 / 1 / 0 | 29 / 1 / 0 |
| Primary engine assembled | 0 | 0 |
| Secondary infinite assembled | 0 | 0 |
| Huge Feeder / infinite life / Noble drain | 0 / 0 / 0 | 0 / 0 / 0 |
| Mulligan games / total mulligans | 12 / 16 | 10 / 14 |
| Functional without combo | 30 | 28 |
| Exactly one role missing | 28 | 26 |
| Herald contribution games / tutors | 17 / 27 | 16 / 26 |
| Herald targets: Elite / Ivy / Witness | 13 / 13 / 1 | 12 / 14 / 0 |
| Witness contribution games / events | 8 / 11 | 9 / 10 |
| Winding casts: Creature / Land | 27 / 7 | 14 / 9 |
| Winding total hand / grave yield | 61 / 75 | 42 / 50 |
| Lead casts / creature yield | 8 / 30 | 15 / 38 |
| Birchlore / Nettle / Quirion games | 7 / 16 / 4 | 6 / 14 / 4 |
| Birchlore / Nettle / Quirion events | 12 / 50 / 8 | 18 / 75 / 8 |
| Genuine-color bottleneck games / events | 26 / 137 | 27 / 154 |
| Garden / Mire tempo games | 6 / 8 | 7 / 6 |

Sample #2 replicated Sample #1's central result: the frozen solitaire policy won primarily through ordinary combat around T7, while no validated Project X infinite engine assembled before game end. These are descriptive results only and are not authorization to optimize the deck.

## Pooled 60-game descriptive results

Pooling is descriptive only; both constituent samples remain separately preserved.

| Metric | Pooled result |
|---|---:|
| Wins by T4 / T5 / T6 | 0/60 / 4/60 / 25/60 |
| Median / mean actual winning turn | 7.0 / 6.98 |
| Combat / triggered / combo lethal | 58 / 2 / 0 |
| Primary / secondary combo assembly | 0 / 0 |
| Huge Feeder / infinite life / Noble drain | 0 / 0 / 0 |
| Mulligan games / total mulligans | 22 / 30 |
| Functional without combo | 58/60 (96.7%) |
| Exactly one role missing | 54/60 (90.0%) |
| Herald contribution games / tutors | 33 / 53 |
| Herald targets: Elite / Ivy / Witness | 25 / 27 / 1 |
| Witness contribution games / events | 17 / 21 |
| Winding casts: Creature / Land | 41 / 16 |
| Winding total hand / grave yield | 103 / 125 |
| Lead casts / creature yield | 23 / 68 |
| Birchlore / Nettle / Quirion games | 13 / 30 / 8 |
| Birchlore / Nettle / Quirion events | 30 / 125 / 16 |
| Genuine-color bottleneck games / events | 53 / 291 |
| Garden / Mire tempo games | 13 / 14 |

Pooled mana-classification event counts: genuine color 291; intentionally held 52; Birchlore mana available 126; Quirion sequence available 118; tapped-land tempo 140; insufficient total mana 2,016; other payment constraint 210. The corresponding game counts are 53, 18, 19, 12, 23, 60, and 52.

## Final sanity audit

- 30 games, 30 unique seeds, exact frozen order
- Zero overlap with all checked prior seed sources before execution
- 0 rules/state audit errors
- 0 illegal executor submissions or rejected casts
- 0 applied casts lacking an ordinary payment plan
- 19 explicit-Birchlore `AutoPay` preflight rejections; none submitted
- 30/30 legal recorded wins with winner, turn, and terminal mechanism
- Terminal flags mutually exclusive and consistent in all games
- 23/23 Winding Way agent/rules labels agreed; both modes exercised
- Every Winding Way resolution accounted for exactly four cards, with matching cards going to hand
- All Lead yields were at most five creatures
- Herald deaths, trigger creation, resolution, and target counts reconciled
- Every Herald tutor target was a legal Elf role
- All immediately available legally searchable one-role Herald lines selected that role
- No physical iteration of a symbolically proven loop
- No clear rules/state, telemetry, mana-legality, selection, Herald, combo-recognition, or solitaire-policy defect demonstrated

Project X v0.2 remained frozen exactly, with no sideboard and no Giant's Boulder. No optimization seeds or matchup games were generated or run.
