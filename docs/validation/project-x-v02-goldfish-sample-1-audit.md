# Project X v0.2 — Goldfish Sample #1 audit and acceptance

## Disposition

Project X Goldfish Sample #1 is formally **accepted as performance evidence** for the frozen Project X v0.2 deck and validated solitaire laboratory.

Sample #1's 30 seeds are permanently retired from all future performance and optimization testing. They remain preserved only as this accepted Sample #1 evidence.

The corrected original 30-seed replay remains accepted strictly as regression validation, and those 30 seeds are permanently retired from performance sampling. Sample #1 used a completely new frozen vector. No game was rerun, replaced, excluded, or substituted, and no deck or policy change occurred during the block.

- Accepted laboratory tip: `a3c205f3a67d7dc3038d24633289831943d9710a`
- Seed-freeze commit: `088fb47c2e4dc1c969063aa87df35c0e27293aa2`
- Immutable execution commit: `c43518961913b5ca4240bb7c7bd565905b749574`
- Full CI: [run #121](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34517061206), passed
- Argentum Validation and one-time sample execution: [run #128](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34517061302), passed
- Artifact ID: `10168937593`
- Artifact ZIP SHA-256: `6a5f81955c8674befc0883a8bd06886c108eca32657220f62af6cfbfc52115f8`
- JSON SHA-256: `629d82aef6fd05a8ab167e0efbb8c8640a4c702bebc507349f5743a9fc5e025d`
- Markdown SHA-256: `eb45c922c2d049809a62693454aeb3ead1e060b053dd8a5f3f02d06fde3b4528`

## Seed provenance

The 30 unique seeds were frozen before execution in `project-x-goldfish-sample-1-seeds.csv`. They were deterministically derived from the Sample #1 label, index, and accepted laboratory tip. Before execution, the vector was checked against the accepted tree and all reachable Git history, including Project X development and retired vectors plus Batshit development, regression, smoke, baseline, optimization, and matchup vectors. Overlap was zero.

## Actual-win clock

| Metric | Result |
|---|---:|
| Wins by T4 | 0/30 (0.0%) |
| Wins by T5 | 2/30 (6.7%) |
| Wins by T6 | 11/30 (36.7%) |
| Median winning turn | 7.0 |
| Mean winning turn | 6.97 |
| Combat lethal | 29/30 (96.7%) |
| Triggered/ability lethal | 1/30 (3.3%) |
| Deterministic combo lethal | 0/30 |

Winning-turn distribution: T5 2, T6 9, T7 11, T8 6, T10 2.

## Combo clock

| Metric | Result |
|---|---:|
| Primary engine online by T4 / T5 / T6 | 0 / 0 / 0 |
| Any validated infinite online by T4 / T5 / T6 | 0 / 0 / 0 |
| Games assembling a validated combo | 0/30 |
| Median combo turn among assemblers | Not reached |
| Combo available before game end | 0/30 |
| Huge Carrion Feeder | 0/30 |
| Infinite life | 0/30 |
| Falkenrath Noble deterministic drain | 0/30 |
| Ordinary lethal before combo assembly | 30/30 |

The end-to-end symbolic recognizer remained active throughout the exact solitaire/Gym path. No primary or secondary infinite state was observed in this sample; ordinary wins ended every game first. Ordinary combat was never labeled combo lethal.

## Development and contribution telemetry

- Mulligans: 12/30 games (40.0%), 16 total mulligans
- Opening color access: both colors 24/30; green only 3/30; black only 3/30; no-color hands 0/30
- Functional without combo: 30/30 (100%)
- At least one turn exactly one primary role short: 28/30 (93.3%)
- Wirewood Herald contribution: 17/30 games; 27 resolved tutors
- Herald targets: Safehold Elite 13, Ivy Lane Denizen 13, Evolution Witness 1
- Evolution Witness recursion contribution: 8/30 games
- Birchlore / Nettle / Quirion contribution: 7/30 / 16/30 / 4/30 games
- Winding Way: 34 resolutions in 23 games; 27 Creature choices and 7 Land choices
- Lead the Stampede: 8 resolutions in 7 games
- Khalni Garden / Haunted Mire tempo events: 6 / 8

Game 24 was inspected because Herald tutored Evolution Witness while Ivy Lane Denizen was legally searchable and was the single missing primary role. The game achieved ordinary combat lethal on that same turn; tutoring Ivy could not create an earlier deterministic win. This is therefore not evidence of a fastest-win policy defect.

## Mana and color telemetry

The corrected categories remain separate:

| Constraint | Events | Games |
|---|---:|---:|
| Genuine color unavailable | 137 | 26 |
| Intentionally held legal cast | 25 | 8 |
| Birchlore mana available | 71 | 9 |
| Quirion sequence available | 40 | 5 |
| Tapped-land tempo | 73 | 11 |
| Insufficient total mana | 1,019 | 30 |
| Other payment constraint | 85 | 25 |

There were 30 policy preflight rejections of casts whose enumerated affordability depended on an explicit Birchlore activation that `AutoPay` could not perform itself. None was submitted to the executor. There were zero executor-rejected casts, zero applied casts without a legal payment plan, and six applied casts correctly attributed to an existing floating mana pool.

## Final sanity audit

- 30 games and 30 unique seeds, exact frozen order
- Seed vector matched the pre-execution CSV exactly
- 0 rules/state audit errors
- 0 illegal, wedged, no-actor, or action-cap stops
- 30/30 games reached a legal recorded win
- Winner, actual win turn, engine game-over turn, and terminal mechanism present for every game
- Terminal flags mutually exclusive and consistent in every game
- Winding Way agent and rules choices agreed for all 34 resolutions; both modes exercised
- Herald death, trigger creation, resolution, and target counts reconciled in every game
- Every observed one-role-missing Herald sacrifice line had a legally searchable role
- No physical iteration of a symbolically proven loop
- No clear rules/state, telemetry, mana-legality, Winding Way, Herald, combo-recognition, or solitaire-policy defect demonstrated

Project X v0.2 remained frozen exactly, with no sideboard and no Giant's Boulder. This sample is preserved but must not be used for deck optimization. Sample #2 and matchup self-play were not started.
