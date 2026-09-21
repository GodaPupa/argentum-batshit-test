# v0.9 Phase 29 — First Sampled Matchup Pilot Design Gate

Disposition: `V09_PHASE29_SAMPLED_PILOT_DESIGN_GATE_OPEN`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

Accepted public stack:
- Phase 25 action surfaces.
- Phase 26 legality compiler.
- Phase 27 observed-action behavior.
- Phase 28 end-to-end public integration.

Current counters:
- experimental seeds generated: 0
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

## Purpose

Design the first sampled Veteran Beastrider matchup pilot before any seed generation
or gameplay. This gate is methodology-only.

## Pilot question

Estimate whether the accepted Capsize tutor/response policy creates observable
interactive value against the frozen Veteran Beastrider identity while preserving
the v0.7 primary plan.

This first pilot is calibration, not a promotion test and not a matchup-strength
claim.

## Required frozen design fields

1. Experimental unit
- one game trajectory from a fresh opaque seed;
- exact v0.7 list;
- exact frozen Veteran Beastrider identity;
- accepted Phase-25 through Phase-28 public stack.

2. Sample size
- exactly 12 games for the first pilot;
- 6 Izzet play / 6 Izzet draw;
- no adaptive sample-size increase;
- no replacement games except protocol-defined invalid execution before outcome
  exposure, which must be recorded and reviewed before any replacement is allowed.

3. Seed policy
- seeds must be generated only after this design gate is accepted;
- fresh unique nonzero signed 64-bit values from an operating-system cryptographic
  source;
- audit against the full Izzet experimental seed registry;
- freeze the exact vector before execution;
- no rerolls, regeneration, outcome-conditioned replacement, or reuse.

4. Primary observables
- game result;
- turn of terminal result;
- Capsize acquired/not acquired;
- Capsize cast count;
- Capsize buyback cast count;
- first meaningful Capsize interaction turn;
- interaction class (tempo, removal protection, commander-pressure relief,
  mana-development disruption, other);
- whether Capsize created at least one extra Izzet main-phase decision window;
- primary combo assembled;
- primary combo attempt;
- primary combo protected/disrupted;
- deterministic lethal opportunity;
- commander damage received.

5. Descriptive analysis only
- report counts and rates with exact denominators;
- no promotion/rejection of cards from this 12-game pilot;
- no claim that the matchup is favorable/unfavorable from this pilot alone;
- no tuning from exposed outcomes until a separately authorized replication gate.

6. Invalidation
- illegal action;
- hidden-information leak;
- seed mismatch/reuse;
- divergence from frozen list or opponent identity;
- non-deterministic replay from same seed and frozen runner;
- missing event ledger;
- terminal-state accounting defect.

Any invalidation stops execution fail-closed. Do not continue to later positions
until the defect is diagnosed.

7. Outcome exposure discipline
- execute only after runner identity, seed vector, assignments, and manifest are
  frozen;
- expose outcomes only through the canonical artifact;
- preserve per-position provenance.

## Explicitly unauthorized during Phase 29 design

- generating experimental seeds;
- assigning play/draw;
- executing games;
- exposing outcomes;
- changing either deck;
- modifying accepted Phase-25 through Phase-28 semantics.

## Exit criterion

Phase 29 design may be accepted after a seed-free CI/static-validation check confirms
that the protocol is internally complete, exact, deterministic where required, and
contains no execution path.

The next gate after acceptance is runner construction and deterministic seed-free
validation. Seed generation remains separately gated.
