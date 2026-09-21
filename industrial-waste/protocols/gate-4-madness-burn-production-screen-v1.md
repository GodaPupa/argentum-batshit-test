# Gate 4 Madness Burn production-profile fresh screen v1

Status: completed; valid; directional replication gate passed.

## Question

With the opponent policy defect corrected, does Pactdoll-A show a meaningful preboard advantage
over immutable Industrial Waste v1.0 Control against contemporary Madness Burn?

## Frozen inputs

- Namespace: `IW-G4-MADNESS-BURN-PROD-S1`.
- Seeds: eight deterministic namespace-derived signed 64-bit values; unique, nonzero, and disjoint
  from every registered Industrial Waste namespace.
- Vector SHA-256: `134dda1362f5c9719a8e209bd1dec0e3d769a842704a156064f111eddb597031`.
- Industrial lists: immutable v1.0 Control and unpromoted Pactdoll-A.
- Industrial agent: unchanged `industrial-waste-policy-v2`.
- Opponent: Davide Canevazzi's published 2026-09-12 Madness Burn 60.
- Opponent agent: `PRODUCTION_CANDIDATE_EXPIRING`.
- Preboard only, London mulligans enabled, 16 turns per seat, and 4,000 accepted actions.

Each seed is used for both Industrial lists and both seat rotations: 32 games. Execution uses eight
one-seed CI jobs because calibration established that larger partitions can cross the test timeout.
Every registered seed is used exactly once; no rerolls or replacement seeds are permitted.

## Validity and decision gates

Reject the entire screen for any missing or duplicate `(deck, pair, seat)` key, digest mismatch,
exception, rejected action, unapproved draw, failed CI job, or cross-namespace seed overlap.

The matchup remains sufficiently two-sided only if Madness Burn wins at least 4/16 against each
Industrial list. Pactdoll-A earns a matchup-specific directional advantage only if it wins at least
two more games than Control. A gap of one game or less is noise at this sample size. This screen
cannot promote a deck or authorize postboard work by itself.

- If the pressure gate passes and Pactdoll-A leads by at least two wins, authorize one fresh
  replication under the same frozen profiles.
- If Control leads by at least two wins, record a matchup-specific regression for Pactdoll-A and do
  not replicate it against Burn.
- If the gap is at most one, record no Burn-specific challenger advantage and move to the next
  implementation-feasible gauntlet opponent without further Burn sampling.

Tron by turn 5, combo-ready incidence, mulligans, and colored-mana failures remain diagnostic
secondary metrics; they cannot override the win gate.

## Result

GitHub Actions run 35552681373 completed all eight one-seed jobs successfully. The canonical merge
contains exactly 32 unique `(deck, pair, seat)` keys, all eight registered pairs, zero exceptions,
zero rejected actions, and no draws.

Control went 2-14 and Pactdoll-A went 4-12. Burn therefore cleared the pressure floor against both
lists. Pactdoll-A's +2-win gap exactly met the directional threshold: three paired outcomes were
Pactdoll-A-only wins, one was a Control-only win, one was a shared win, and eleven were shared
losses. Both lists reached Tron by turn 5 once and neither recorded a combo-ready state. Pactdoll-A
also had fewer mean colored-mana-failure turns (1.56 versus 2.44); that is supporting diagnostic
evidence, not an independent promotion criterion.

Decision: authorize exactly one fresh production-profile Madness Burn replication. Pactdoll-A is
not promoted, and postboard work remains blocked.
