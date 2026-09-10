# Project X v0.2 — corrected same-seed regression audit

## Disposition

The original frozen 30-seed block remains formally **rejected as Project X validation/performance evidence**. Its original JSON and Markdown are retained in `project-x-v02-goldfish-30-rejected.*`.

The corrected replay of those exact 30 seeds is accepted only as **solitaire-harness regression evidence**. The seeds are retired from future sampling. No aggregate from this replay may be used to optimize Project X or estimate deck speed.

- Deck: frozen Project X v0.2, unchanged, no sideboard
- Seeds: exactly the original 30, in the original order; 30/30 unique
- New seeds, rerolls, replacements, exclusions: none
- Validated code head: `bf60884093765ac2de4dcc9db15550736e8ffcde`
- Full CI: [run #117](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34512379953), passed
- Argentum Validation: [run #127](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34512379940), passed
- Replay artifact: `project-x-v02-regression-replay`, artifact ID `10167160589`
- Artifact ZIP SHA-256: `0b7120075baa1a099ae0900b4ada9169c4d1cae8cbcd9b1bacda35b717bae113`

## Root causes and fixes

### A — illegal action and mana planning

Game 14 (`0x2B7B990F635FEBD`) exposed a disagreement between action enumeration and execution. The enumerator considered a spell payable through a future explicit Birchlore Rangers activation, while the selected `CastSpell` action used `AutoPay`, which cannot itself activate that mana ability. The policy therefore submitted an action that the executor rejected.

The general AI simulation path now preflights the fully materialized action against executor legality before ranking it. An unavailable `AutoPay` cast is recorded as a policy preflight rejection, including the pool, land state, Birchlore/Quirion possibilities, proposed payment plan, and reason; it is not submitted to the executor. Existing floating mana is separately labeled `AUTO_PAY:EXISTING_POOL` / `AFFORDABLE_FROM_EXISTING_POOL`.

Corrected Game 14 completed normally: Project X won on turn 6 by combat, with no illegal action or audit error.

### B — Winding Way

The card's real rules path produces a `ChooseOptionDecision`; the harness had treated it as a mode decision and then reported a synthetic `all-creatures` label. The agent now handles the real option decision, while telemetry keeps the agent choice, accepted rules choice, and report label separate. The replay contains 20 Creature choices and 5 Land choices, with agent and rules choices agreeing in every event.

### C — Wirewood Herald

The real SDK library-search effect produces a `SelectCardsDecision`, not the search-decision shape assumed by the first harness. The agent and telemetry now follow that real path. A duplicate test-only Safehold Elite definition also shadowed the real Elf Scout card and was removed from the scenario registry. Nonbattlefield subtype evaluation now uses printed subtype, Changeling, and applicable cross-zone grants.

Lifecycle totals reconcile exactly: 34 draws, 32 casts, 56 battlefield observations, 24 deaths, 24 tutor triggers created, 24 resolved, and 24 targets across 14 games. Targets were Safehold Elite 11 times, Ivy Lane Denizen 9 times, and Evolution Witness 4 times. Every observed one-role-missing sacrifice line searched the legal missing role on that turn. Carrion Feeder was correctly reported unsearchable in all 16 relevant availability observations.

### D — symbolic infinite recognition

The recognizer is exercised through the same solitaire/Gym path as the replay and symbolically distinguishes the complete Feeder/Elite/Denizen engine, arbitrarily large Feeder, infinite life, Falkenrath Noble deterministic lethal, and the validated Evolution Witness secondary loop without iterating an unbounded loop. Those focused end-to-end regressions passed in Validation #127.

None of these 30 replayed games assembled a recognizable primary or secondary loop. The zero recognition events in this block therefore describe the replay states, not a recognizer failure.

### E — terminal reporting

Every terminal game now records winner, game-over turn, terminal mechanism, and mutually consistent combat/triggered/combo/other flags. All 30 games reached a recorded terminal state: 29 combat wins and 1 triggered/ability win. No ordinary combat win is labeled deterministic combo lethal.

### F — bottleneck telemetry

The old `28/30 color bottleneck` headline conflated different constraints. The corrected telemetry classifies observations separately:

| Constraint | Events | Games |
|---|---:|---:|
| Genuine color unavailable | 130 | 28 |
| Intentionally held cast | 20 | 12 |
| Birchlore mana available | 53 | 10 |
| Quirion sequence available | 42 | 7 |
| Tapped-land tempo | 59 | 12 |
| Insufficient total mana | 998 | 30 |
| Other payment constraint | 106 | 25 |

These are diagnostic counts from a rejected sample and are not a basis for changing the frozen mana base.

## Final audit

- 30 games, 30 unique seeds, exact original order
- 0 per-game audit errors
- 0 illegal/wedged/max-actions/no-actor stops
- 0 executor-rejected cast attempts
- 0 applied casts lacking a valid payment plan; 6 applied casts correctly identified as using an existing floating pool
- 25 Winding Way choices correctly round-tripped (20 Creature, 5 Land)
- Herald death/trigger-created/trigger-resolved/target counts reconcile in every game
- 30/30 winner, game-over turn, terminal mechanism, and terminal flags present and consistent
- Project X v0.2 deck list unchanged
- No new seeds, optimization, Giant's Boulder work, or matchup self-play

This concludes the corrected same-seed replay and audit. A future Project X speed measurement must use a completely new seed block.
