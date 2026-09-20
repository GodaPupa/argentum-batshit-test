# v0.8 Multiplayer Kill Audit — Accepted Deterministic Gate

## Question

Does the accepted primary-combo model establish a whole-table goldfish kill, or
only lethal damage against one 30-life opponent?

## Controls

- Deck control: frozen `v0.7-control.md`.
- Deck changes: none.
- Random samples: none.
- Seeds consumed: none.
- Interaction assumptions: none; this is an undisrupted deterministic rules gate.

## Frozen semantics

After a legal launch, each copied combined Lava Spike deals 3 damage and adds RRR.
That RRR pays the next 2R Guildmage activation while the original remains on the
stack. Each new copy may choose a new target. The original has one fixed target.

For opponent life total `L`, the allocation requires `ceil(L / 3)` resolving
combined spells. Across the table, exactly one resolution is the original; every
other resolution is a Guildmage copy.

## Deterministic result

| Opponents | Life totals | Resolutions per opponent | Copies | Original | Damage |
|---:|---|---|---:|---:|---:|
| 3 | 30 / 30 / 30 | 10 / 10 / 10 | 29 | 1 | 90 |
| 3 | 1 / 4 / 31 | 1 / 2 / 11 | 13 | 1 | 42 |

The regression also rejects empty, non-positive, boolean, and non-integer life
vectors, and returns no plan when the initial five-mana launch is unavailable.

## Boundary

This proves deterministic damage capacity and target allocation only. It does not
model priority decisions, interaction, damage prevention, hexproof, concessions,
or multiplayer threat behavior. Existing sampled `combo_lethal` telemetry remains
a launch-opportunity metric; its historical values are unchanged.

## Disposition

Accepted as a seed-free correctness extension. No challenger was promoted, no
control changed, and no failed or exposed sampled run was reused.
