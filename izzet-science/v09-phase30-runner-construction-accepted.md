# v0.9 Phase 30 — Sampled Pilot Runner Construction Accepted

Disposition: `V09_PHASE30_RUNNER_CONSTRUCTION_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

## Provenance

- Gate: `izzet-science/v09-phase30-runner-construction-gate.md`
- Workflow run: `35572629132` — success
- Job: `106247332796` — success
- Artifact: `10626353767`, `izzet-v09-phase30-runner-construction`
- Artifact ZIP SHA256: `e29348e84fd95b6c1e48df0b5ef5b8b94bf75f90437d31f90bd27e254b82b897`
- Artifact manifest source SHA: `6905cd9169e4f8e140e5bf088a7c1c011db54806`
- Gate SHA256: `6e80727549d594df7c17f0c0b63d34d71c88d28f6fc7308552430bb5ed0f1b27`
- Runner SHA256: `864d46745c7ffd1c1e9c8eef48c5a0ad14b34938aa0a6066d94ab7a394516fc3`
- Validator SHA256: `4e98c3b7579fa76fad392800a98b755c0e132823243d4f6dd8e0c99b6d0b6989`
- Validation transcript SHA256: `9e81b3507a57838954c89e0636fa7e872cf974c7bd649f87efe5f5e70b449368`

Independent artifact audit matched GitHub's reported ZIP digest exactly. The archive
contained exactly two nonempty files, `manifest.txt` and `validation.txt`, and the
validation transcript contained exactly
`V09_PHASE30_RUNNER_CONSTRUCTION_VALIDATION_PASS`.

## Qualified runner contract

The seed-free runner qualified:
- exactly 12 positions, numbered 1–12;
- exactly 6 Izzet play / 6 Izzet draw assignments;
- no populated seed fields in Phase 30;
- complete Phase-29 observable schema;
- terminal result requires terminal turn and reason;
- replay digest binding runner version, control hash, opponent identity, position,
  assignment, and synthetic fixture identity;
- byte-identical canonical ledger replay;
- stop-on-first-invalid semantics;
- rejection of seeded assignments, unbalanced assignment vectors, incomplete
  terminal accounting, and production-mode outcome exposure.

## Decision

Accept Phase 30 as runner/ledger infrastructure only. This acceptance does not
authorize experimental seed generation or game execution.

Counters at acceptance:
- experimental seeds generated: 0
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

The exact v0.7 100 remains frozen.

## Next gate

Open a seed-vector generation/freeze gate. That gate may authorize creation of
exactly 12 fresh unique nonzero signed 64-bit seeds from an OS cryptographic source,
full registry audit, fixed 6/6 play-draw assignment binding, immediate vector
quarantine, and immutable freeze before any game execution. Outcome exposure remains
zero and gameplay remains unauthorized until a later execution gate.
