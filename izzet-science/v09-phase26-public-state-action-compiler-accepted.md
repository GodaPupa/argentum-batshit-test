# v0.9 Phase 26 — Public-State Action Compiler Accepted

Disposition: `V09_PHASE26_PUBLIC_STATE_COMPILER_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

## Provenance

- Gate: `izzet-science/v09-phase26-public-state-action-compiler-gate.md`
- Branch head under test: `1ea260bf9f1d58025f88b8711fef8643f72ef6b2`
- Pull-request merge ref checkout: `30e6a34513de8a07a693b792384a9fd7ba7ad137`
- Compiler SHA256: `131bdd91bd04e479fdc4a6bb55ea4f394e459ac825284754046f93cac1aefce6`
- Validator SHA256: `fe855f7cf5b68b7097838d64479320f28f3fb39a3560ce932ec8332af254860e`
- Gate SHA256: `9743c2c420907e7a15f7834153f1db1c8714e6623ca6c24f8a301f748dfec006`
- Workflow run: `35568092238` — success
- Job: `106233823774` — success
- Artifact: `10625185600`, `izzet-v09-phase26-public-state-compiler`
- GitHub artifact ZIP SHA256: `4a993d6e8a12b58bf63092b861392f3928b5ba386935b4d6e9f5d72352de9f10`
- Validation transcript SHA256: `60ebba3e4f0ef4af4fdc592c7bd5bf2ea5ad8eafc6a7a9f14159f65db20de664`

The downloaded artifact contained exactly two nonempty files, `manifest.txt` and
`validation.txt`. The locally computed ZIP digest matched GitHub's reported
artifact digest exactly. The validation transcript contained exactly the accepted
terminal marker `V09_PHASE26_PUBLIC_STATE_COMPILER_VALIDATION_PASS`.

## Qualification history

Three earlier runs failed closed during qualification:

1. run 35567116685 exposed an underfunded positive fixture;
2. run 35567479725 exposed an overly restrictive mana-allocation implementation;
3. run 35567753014 exposed a missing target-class declaration in the Generous Gift fixture.

No failed run was accepted. The second failure resulted in a compiler correction;
the first and third were fixture corrections. The qualifying fourth run passed the
frozen-control check, compiler validation, no-experimental-execution-surface check,
manifest generation, and artifact upload.

## Decision

Accept the Phase-26 public-state action compiler as a deterministic, seed-free
legality boundary for observed Veteran Beastrider actions.

The accepted compiler may reason from public battlefield state and already observed
or revealed action sources. It enforces timing, public mana availability, tapped
state, summoning sickness for tap sources, target eligibility, observable
hexproof/shroud/protection constraints, deterministic ordering, and the frozen
16-damage PDH commander threshold.

This acceptance does not authorize hidden-hand generation, mulligan policy, random
action selection, sampled matchup positions, experimental seed generation or
consumption, outcome exposure, deck changes, or any promotion of a new card control.

Counters at acceptance:
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

The exact v0.7 100 remains frozen.

## Next gate

Build and qualify deterministic observed-action behavior over concrete public states,
using the accepted Phase-26 compiler as the legality boundary. Remain seed-free and
keep hidden opponent information unavailable. A sampled matchup pilot remains
unauthorized until that behavior layer passes independently.
