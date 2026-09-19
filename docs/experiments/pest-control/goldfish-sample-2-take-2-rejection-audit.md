# Pest Control v1.0 — Goldfish Sample #2 Take 2 Rejection Audit

## Disposition

Goldfish Sample #2 Take 2 is **formally rejected in full**. It is not performance, engine-replication,
baseline, matchup, optimization, or variant-comparison evidence. Its metrics are quarantined below
only to preserve the complete record; no pooled 60-game comparison with accepted Sample #1 is
admissible or produced.

The exact 30-seed vector is permanently retired and hard-disabled. It may never be replayed,
rehabilitated, compared against another deck or policy, sampled, optimized against, or reused. No
game is excluded or replaced.

## Execution provenance

- The Game 16/Game 28 correction gate was accepted as remotely green in CI #217 at
  `9e5adc4416c1d5c2684befa9ea699be28fe6c2f0`; implementation commit
  `dd0572ab61c6a2dd3d2e76a578999c2fa822b2c0` remains the actual correction.
- CI #215 is stranded/non-validation because it created zero jobs. It is neither successful nor
  failed validation and is not used here.
- The new vector was frozen before Game 1 at
  `8be1f0ba160a0c817595356f4c9f5391ed570889`; preflight CI #218 created nine jobs and completed green
  on that exact head.
- Vector SHA-256:
  `1db3fb1fcf4b969d9229bb2a060491a556ff5e1c8c80d327caf37698ca1c3cb7`.
- Two setup attempts executed zero tests/games: the first was Gradle-up-to-date, and the second failed
  while recompiling an unrelated set module before `:gym:test`. One subsequent task-scoped invocation
  executed all 30 seeds exactly once, in frozen CSV order, and wrote the complete raw/report block.
- There was no reroll, replay, replacement, exclusion, substitution, deck change, policy change,
  telemetry change, or mid-sample correction.

## Blocking defects

### False superior-setup telemetry — Games 1, 8, and 30

The automatic audit rejected each game's first T4 Weather cast with the same diagnostic:

`cast before validated setup [play Swamp, cast Weather the Storm, cast Weather the Storm]`

The executed order was `Weather → play Swamp → Weather`. The allegedly superior order was
`play Swamp → Weather → Weather`. A basic Swamp is not a spell, creates no trigger in these states,
and changes neither Storm count nor total mana spent. Both orders resolve one Storm-0 Weather and one
Storm-1 Weather and leave the same land, hand, and mana state. The telemetry therefore classified an
outcome-equivalent land timing as materially superior. This is a clear sequencing-telemetry defect,
not evidence of a missed profitable setup line, and independently invalidates the entire sample.

### Unproductive self-removal — Games 9 and 29

With no opposing creature, each game cast Cast Down on its own Carrier Thrall on the terminal turn.
The death produced one Scion, but no Essence Warden was present, no lifegain/counter conversion
occurred, no later spell used the Scion's mana, and no same-turn Weather gained Storm. The new Scion
was unused. Spending removal and mana to replace the Carrier with an unused smaller creature had no
concrete strategic utility. These are clear agent-policy defects.

The other four removal casts in Games 12, 15, 21, and 24 coincided with Warden/payoff trigger
conversion or, in Game 24, two fully consumed Scion mana activations funding enhanced Follow. No
additional clear removal defect was assigned to those lines.

## Automatic and manual audit

The frozen-deck/vector guard passed. All 30 games reached engine game-over by combat lethal. Seed
identity/order, legal engine completion, Weather copy counts, life-event totals, Researcher/Mascot
trigger-to-counter equality, Carrier-death-to-Scion equality, Scion mana production/consumption,
mana provenance, and terminal reporting were checked across the complete JSON block. The automatic
audit reported only the three superior-setup diagnostics above.

Every game's mulligan, opening access, turn actions, Weather/Follow/Food context, temporary-condition
use, Thrall/Scion line, Ent decision, removal use, actionable bottlenecks, Jungle Hollow delays,
stranded interaction, payoff state, and terminal sequence was then reviewed manually:

| Game | Automatic audit | Manual disposition |
|---:|---|---|
| 1 | superior-setup error | blocking false-positive sequencing telemetry |
| 2 | clean | no additional clear defect |
| 3 | clean | Food to enhanced Follow completed |
| 4 | clean | Weather to enhanced Follow completed |
| 5 | clean | Storm-0 Weather had Mascot utility |
| 6 | clean | Food and Weather each had Mascot utility |
| 7 | clean | no additional clear defect |
| 8 | superior-setup error | blocking false-positive sequencing telemetry |
| 9 | clean | blocking unproductive self-Cast Down |
| 10 | clean | Food had Mascot utility |
| 11 | clean | no additional clear defect |
| 12 | clean | removal/Weather line had Warden/Researcher utility |
| 13 | clean | no additional clear defect |
| 14 | clean | Food/Weather events had Mascot utility |
| 15 | clean | Thrall-to-Scion entry converted through Warden and both payoffs |
| 16 | clean | no additional clear defect |
| 17 | clean | no additional clear defect |
| 18 | clean | Weather/Food events had Researcher utility |
| 19 | clean | enhanced Follow used a current-turn life event |
| 20 | clean | Storm-0 Weather had Mascot utility |
| 21 | clean | Thrall-to-Scion entry converted through Warden/Mascot |
| 22 | clean | enhanced Follow used a current-turn life event |
| 23 | clean | Food had Mascot utility |
| 24 | clean | two Scions fully funded enhanced Follow |
| 25 | clean | no additional clear defect |
| 26 | clean | Food had Mascot utility |
| 27 | clean | terminal-turn Weather/Food each added two Mascot counters before combat lethal |
| 28 | clean | no additional clear defect |
| 29 | clean | blocking unproductive self-Cast Down |
| 30 | superior-setup error | blocking false-positive sequencing telemetry |

Five enhanced Follows all consumed a valid current-turn condition. One Food-to-enhanced-Follow and
one Weather-to-enhanced-Follow sequence completed. No clear temporary-condition expiry or additional
missed profitable enhanced-Follow line was found. Game 27's Follow remained in hand, but the chosen
terminal line converted both Weather and Food into four immediate Mascot counters before combat
lethal; enhanced Follow would not have added immediate board power.

## Quarantined descriptive metrics

These numbers describe the rejected block only and must not support replication or performance
inference.

| Metric | Rejected Take 2 result |
|---|---:|
| Games / modeled wins | 30 / 30 (all combat lethal) |
| Mulligan games / total mulligans | 4 / 6 |
| Opening G+B / G only / B only / neither | 25 / 3 / 2 / 0 |
| Meaningful development by T1 / T2 / T3 | 4 / 13 / 25 |
| Median first Warden / first payoff | T3.5 / T4 |
| Median win; wins by T4 / T5 / T6 / T7 | T7; 0 / 0 / 11 / 23 |
| Lifegain events / life gained | 131 / 205 |
| Researcher / Mascot counters | 53 / 64 |
| Engine-functional / fair-creature-functional | 26 / 4 |
| Warden+Researcher / Warden+Mascot / Researcher+Mascot | 9 / 10 / 10 |
| All-three coexistence | 4 |
| Lifegain events with a payoff present | 86 |
| Additional-Warden games / entries | 19 / 52 |
| Additional-Warden potential Researcher / Mascot counters | 20 / 58 |
| Weather casts; Storm 0 / Storm 1 | 18; 9 / 9 |
| Weather concrete context | all 18 had Researcher, Mascot, or enhanced-Follow utility |
| Follow normal / enhanced | 14 / 5 |
| Carrier Thrall casts / deaths / Scions | 28 / 7 / 7 |
| Scions sacrificed for mana / funded spells | 2 / 2 enhanced Follows |
| Ent cycles / creature casts | 17 / 0 |
| Actionable bottlenecks: total / color / tapland | 180: 156 / 11 / 13 |
| Jungle Hollow entries; proximate-delay games / events | 37; 5 / 13 |
| Solitaire-stranded interaction games / observations | 23 / 192 |
| Land-unlocked Weather observations / candidates | 3 / 5 |
| Validated setup flags | 3, all false-positive equivalent-order classifications |

## Laboratory stop

Accepted Sample #1 remains the sole Pest Control performance/engine goldfish sample. The original
rejected Sample #2 and this Take 2 vector are both permanently retired/hard-disabled. Pest Control
v1.0 remains exact; the challenger remains audit-only and unconstructed. No pooled comparison,
replacement vector, Sample #3, correction, optimization, or opponent self-play was started.
