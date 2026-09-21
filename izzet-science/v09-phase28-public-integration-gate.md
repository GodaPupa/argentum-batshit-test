# v0.9 Phase 28 — End-to-End Public Integration Gate

Disposition: `V09_PHASE28_PUBLIC_INTEGRATION_GATE_OPEN`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

Accepted parents:
- Phase 25 rules-sourced action surfaces.
- Phase 26 public-state action compiler.
- Phase 27 observed-action behavior policy.

Counters entering gate:
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

## Research question

Does the complete public pipeline preserve legality, identity, information boundaries,
and deterministic behavior when composed end to end over concrete public Veteran
Beastrider fixtures?

Pipeline under qualification:

Phase 25 public action surfaces
-> Phase 26 legal-action compiler
-> Phase 27 deterministic behavior selection.

## Authorized scope

Build only seed-free integration fixtures from:
- frozen Phase-25 surface names;
- explicit public battlefield objects;
- already observed/revealed source cards;
- current public timing/priority;
- public Izzet objects and public next-main requirements.

No hidden cards may be synthesized.

## Required integration invariants

1. Identity preservation
- every compiled/selected source must exist in the frozen Phase-25 map;
- every action surface must match that source's frozen mapped capability.

2. Legality preservation
- Phase 27 receives only Phase-26 CompiledAction values;
- no illegal/tapped/summoning-sick/timing-invalid action can be selected.

3. Information-boundary preservation
- an unobserved source in the frozen opponent 99 must not become a candidate;
- hidden_land_access remains noncandidate until the action itself is observed;
- no hand, library, future draw, seed, or private-zone field may enter the integration contract.

4. Determinism
- identical public fixture yields byte-identical compiled candidates and decision;
- candidate/source declaration order may not change the decision.

5. Fail-closed contamination
- reject a source/surface pair not present in Phase 25;
- reject duplicate object/action identities;
- reject noncanonical public plan inputs.

## Qualification fixtures

At minimum:
- observed Guildmage removal;
- observed commander-pressure development;
- observed mana development;
- timing-invalid action rejection;
- tapped/summoning-sick rejection;
- unobserved hidden interaction rejection;
- invalid Phase-25 source/surface pairing rejection;
- order-invariance replay;
- deterministic repeated replay;
- no-action/pass case.

## Explicitly unauthorized

Hidden-hand generation, opponent mulligans, draw simulation, sampled matchup
positions, experimental seeds, game execution, outcome exposure, deck changes, or
card-control promotion.

## Exit criterion

A fresh seed-free CI qualification must pass:
- frozen v0.7 hash;
- Phase-25, Phase-26, and Phase-27 parent-presence checks;
- all integration fixtures;
- no-experimental-execution-surface audit;
- manifest/artifact generation.

Acceptance authorizes planning of the first sampled matchup pilot, but does not by
itself authorize seed generation or game execution.
