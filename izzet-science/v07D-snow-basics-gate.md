# v0.7-D Snow-Basic Retrofit — Frozen Challenger Gate

Control: `izzet-science/v0.6-control.md`
Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
Challenger: `izzet-science/challengers/v07D-snow-basics.md`
Challenger SHA256: `29fa77acd45069b1374f10ae0569d4371c6cd5ac70d78cd07c8f5543fbb337da`
Change: replace all 19 Island and 7 Mountain copies with the corresponding
Snow-Covered basics.

## Corrected premise

The control contains one snow permanent: Volatile Fjord. Therefore Skred's current
damage ceiling is one, not zero. Snow-Covered Island and Snow-Covered Mountain are
common, Pauper Commander legal basic lands with the same respective mana ability and
basic land subtype as Island and Mountain. The challenger raises the possible snow
count while preserving all other 73 main-deck slots.

## Hypothesis

The snow-basic retrofit will create meaningful Skred removal readiness without
changing any pre-existing mana, selection, combo, protection, or recovery telemetry.

## Frozen execution

- Paired control and challenger trajectories
- Pilot samples: 10,000 per deck
- Pilot seed: `0x1A22E700A`
- Confirmation samples: 100,000 per deck, only after pilot pass
- Confirmation seed: `0x1A22E700B`
- Horizon: T1–T10
- One execution per stage; no rerolls or replacement seeds

The 10,000-game pilot cannot promote the challenger.

## Pilot pass criteria

1. At every turn T1–T10, every telemetry field that predates v0.7-D must match the
   paired control exactly. This includes land/rock buckets, mana access, executable
   U/R/UU spells, selection, tutors, commander deployment, pair assembly, lethal,
   High Tide, Dramatic Reversal, protection, and recovery.
2. At T10, castable Skred with at least three snow permanents improves by at least
   5.00 percentage points.
3. At T10, average available Skred damage and the probability of a live castable
   Skred both strictly improve.
4. Deck identity, card counts, legality data, source, seed, sample count, output
   completeness, and artifact hashes all validate.

Failure of any criterion rejects v0.7-D and permanently ends this challenger
identity. A pass authorizes exactly one 100,000-game confirmation on the
preregistered seed. Confirmation must satisfy the same criteria; only confirmation
may promote v0.7-D.

Disposition: `V07D_CONFIRMATION_ACCEPTED_PROMOTED_TO_V07`

The sole authorized pilot completed in GitHub Actions run `35536773714`. It passed
all criteria, including exact equality of every legacy telemetry field and a T10
Skred three-plus-damage gain of 17.64 percentage points. See
`v07D-snow-basics-pilot-accepted.md` for the artifact audit. Exactly one confirmation
was authorized on seed `0x1A22E700B`.

The confirmation completed in run `35537163085`, passed the same frozen criteria,
and promoted the challenger to `izzet-science/v0.7-control.md`. See
`v07D-snow-basics-confirmation-accepted.md`.
