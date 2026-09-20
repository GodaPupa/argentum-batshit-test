# Pest Control v1.0 — Goldfish Sample #1 Acceptance Audit

Disposition: **accepted as development/engine goldfish performance evidence**. This is not matchup
evidence. The 30 games ran exactly once, in the frozen CSV order, from preflight head
`4e47607c435e280a4531b1d977fb176955bc8742`; no game was rerolled, replaced, excluded, repaired, or
replayed. The vector is now hard-disabled.

## Aggregate result

| Measure | Result |
|---|---:|
| Games / modeled wins | 30 / 30 (all combat lethal) |
| Mulligan games / total mulligans | 6 / 9 |
| Opening access (G+B / G only / B only / neither) | 27 / 1 / 2 / 0 |
| Meaningful development by T1 / T2 / T3 | 6 / 21 / 27 |
| Median first Warden / first payoff | T2 / T3 |
| Median winning turn | T7 |
| Wins by T4 / T5 / T6 / T7 | 0 / 1 / 13 / 25 |
| Separate lifegain events / life gained | 146 / 202 |
| Researcher triggers / resolved counters | 53 / 53 |
| Mascot triggers / resolved counters | 102 / 102 |
| Warden+Researcher / Warden+Mascot | 11 / 17 games |
| Researcher+Mascot / all three | 14 / 11 games |
| Lifegain events after a payoff was present | 99 |
| Engine-functional / fair-creature-functional | 23 / 7 games |

Wins occurred on T5/T6/T7/T8/T9/T10 in 1/12/12/2/2/1 games. Maximum Researcher sizes were 2/2
through 10/10; maximum Mascot sizes were 2/3 through 11/12. Maximum creature battlefield sizes were
2/3/4/5/6 in 3/5/11/6/5 games. Per-game casts, sizes, counters, actions, lifegain events, bottlenecks,
and terminal records are in the human-readable report and raw JSON.

## Architecture and additional-Warden opportunity

- Payoff without Warden occurred in 12 games across 42 turns; Warden without payoff occurred in 16
  games across 65 turns.
- Thirty-one creature entries occurred with Researcher and/or Mascot present but no Warden. As
  descriptive opportunity telemetry only, these correspond to 11 potential Researcher and 31
  potential Mascot counter opportunities if a suitable Warden-like engine had independently
  triggered. Bogwater Lumaret was neither simulated nor assumed.
- Payoff counters per game ranged from 0 to 21 (155 total). Seven games generated none; the exact
  per-game distribution and source events are preserved in the raw report.

## Weather, Follow, and pending resources

- Thirteen Weather casts occurred: four at Storm 0 and nine at Storm 1. Every cast had an active
  Researcher or Mascot. Each Storm-0 cast was therefore payoff-driven; none was justified merely by
  raw life, survival, convenience, or an already-guaranteed pending lifegain effect.
- No Weather record had a mana-plausible useful spell that should profitably have preceded it. The
  Storm-1 lines used productive creature/removal actions; no junk spell was cast merely to inflate
  Storm. Expected and observed Storm copies matched for every cast, and every copy produced a
  separate lifegain event with matching payoff triggers.
- There were six Food activations. Every activation had Mascot present and therefore independent
  counter value. None duplicated sufficient pending lifegain on the stack.
- Follow was cast 20 times: 17 normal and three enhanced. Four normal casts had Weather in hand, but
  all occurred on T2/T3 with only enough mana for one of the two spells; Weather-first was not an
  executable combined line. No materially superior enhanced-Follow opportunity was missed.

## Resources, mana, and solitaire interaction

- Carrier Thrall was cast 31 times. Five deaths created exactly five Scions. One Scion was
  sacrificed for one colorless mana; all of it funded Fierce Witchstalker on T5 and none was unused.
- Generous Ent was cycled 12 times and cast as a creature once. Manual review found the cycles tied
  to development and the cast to late-game board value.
- Actionable bottlenecks comprised 144 total-mana, five color, and six tapland constraints. No
  duplicate card/turn/constraint observation exists. Jungle Hollow entered tapped 33 times in 23
  games and was the proximate deployment delay for six constraints in five games.
- Opponent-dependent interaction was stranded in 22 games (202 deduplicated card/turn observations:
  Cast Down 104, Bone Shards 66, Chainer's Edict 32). Edict was never cast into the empty opponent.
  The five Cast Down/Bone Shards casts coincided with productive Carrier Thrall deaths, Scion entry,
  lifegain/payoff or lethal-development value; they were not classified as failed cards merely
  because the opponent was blank.

## Acceptance audit

All automated invariants passed with zero audit errors. Manual review covered every Weather, Food,
Follow, Edict, Bone Shards, Cast Down, Thrall/Scion, Ent, mana bottleneck, and terminal decision in all
30 games. Lifegain totals, Researcher/Mascot trigger-to-counter equality, Storm-copy counts, Thrall
death-to-Scion equality, Scion production/consumption, mana legality, and terminal classification all
balanced. No rules/state, telemetry, mana-provenance, pending-effect valuation, or agent-policy defect
was found.

Artifacts:

- `goldfish-sample-1-untouched-raw.json` — complete machine-readable traces and aggregate.
- `goldfish-sample-1-untouched-report.md` — complete human-readable aggregate and per-game telemetry.
- `goldfish-sample-1-untouched-seed-freeze.md` — pre-execution seed/collision freeze.
