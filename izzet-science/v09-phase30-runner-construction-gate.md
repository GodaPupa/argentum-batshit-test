# v0.9 Phase 30 — Sampled Pilot Runner Construction Gate

Disposition: `V09_PHASE30_RUNNER_CONSTRUCTION_GATE_OPEN`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

Parent acceptance:
- Phase 29 sampled-pilot design accepted.
- Phase 25–28 public adversarial stack remains frozen.

Current counters:
- experimental seeds generated: 0
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

## Authorized work

Build, but do not execute with experimental seeds:
1. a deterministic 12-position runner contract;
2. a per-position assignment schema supporting exactly 6 Izzet play / 6 Izzet draw;
3. a canonical event ledger containing every Phase-29 observable;
4. terminal-state accounting;
5. replay identity/digest fields;
6. fail-closed invalidation and stop-on-first-invalid semantics;
7. a seed-free synthetic fixture suite exercising all paths.

## Runner invariants

- exactly 12 positions numbered 1–12;
- assignment vector must contain exactly six `play` and six `draw`;
- no seed field may be populated in Phase 30 production fixtures;
- no outcome may be emitted from a production position;
- all Phase-29 observables must have explicit schema fields;
- invalid position N prevents positions N+1 through 12 from initializing;
- same synthetic fixture input must produce byte-identical ledger output;
- every terminal result must include terminal turn and terminal reason;
- replay digest must bind runner version, control hash, opponent identity, position,
  assignment, and synthetic fixture identity.

## Explicitly unauthorized

- OS seed generation;
- experimental seed registry mutation;
- random shuffling from an experimental seed;
- real sampled game execution;
- outcome exposure;
- deck changes;
- modification of accepted Phase 25–29 semantics.

## Exit criterion

Phase 30 may be accepted only after a fresh seed-free CI qualification proves all
runner and ledger invariants with synthetic/nonexperimental fixtures. Acceptance
authorizes a later seed-vector generation/freeze gate only; it does not authorize
game execution.
