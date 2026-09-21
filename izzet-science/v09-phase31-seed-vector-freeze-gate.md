# v0.9 Phase 31 — Experimental Seed Vector Generation / Freeze Gate

Disposition: `V09_PHASE31_SEED_VECTOR_FREEZE_GATE_OPEN`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

Parent acceptances:
- Phase 29 sampled-pilot design accepted.
- Phase 30 runner construction accepted.
- Phase 25–28 public adversarial stack remains frozen.

Counters entering gate:
- experimental seeds generated: 0
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

## Authorization boundary

This gate authorizes seed-vector generation and freezing only after its own static
qualification passes. It does not authorize game execution.

## Required seed vector

- exactly 12 fresh values;
- each value is a signed 64-bit integer;
- each value is nonzero;
- all 12 are unique;
- values are generated once from an operating-system cryptographic source;
- no deterministic fallback;
- no rerolls, replacements, regeneration, or outcome-conditioned edits;
- vector is immediately written to a quarantined artifact and never printed in
  ordinary logs.

## Registry audit

Before freeze, every value must be checked against the complete Izzet experimental
seed registry, including accepted, rejected, retired, diagnostic, and previously
frozen Izzet seeds. Any overlap fails closed and invalidates the whole candidate
vector before use.

No overlapping value may be replaced individually. A collided candidate vector is
discarded as a whole and no second generation is authorized by this gate without a
separate provenance entry and explicit review.

## Assignment binding

Freeze exactly 12 position bindings:
- positions 1–12;
- exactly 6 Izzet play / 6 Izzet draw;
- assignments are fixed before outcome exposure;
- each frozen seed is bound to exactly one position and one assignment;
- no position swaps after freeze.

## Quarantine / exposure discipline

- raw seeds may exist only in the quarantined seed artifact and execution-only
  loader surface;
- public manifests may contain only hashes/digests, counts, and assignment totals;
- no seed value appears in CI logs, acceptance prose, issue/PR comments, or summary
  artifacts;
- outcome exposure remains 0/12.

## Required freeze manifest

The immutable freeze must bind:
- vector SHA256;
- runner version/hash;
- v0.7 control hash;
- opponent identity;
- Phase-29 protocol identity;
- Phase-30 runner identity;
- assignment-vector digest;
- Izzet seed-registry digest;
- exact count = 12;
- play/draw = 6/6;
- generated/consumed counters.

## Explicitly unauthorized

- game initialization;
- deck shuffling for a sampled game;
- matchup execution;
- outcome artifact creation;
- outcome inspection;
- card changes;
- changing the frozen opponent identity or accepted public-action semantics.

## Exit criterion

Phase 31 may close only after:
1. a seed-free static qualification proves the generator/freeze tooling is fail-closed;
2. exactly one authorized generation occurs;
3. the resulting quarantined vector passes uniqueness/nonzero/signed-64 and complete
   registry-overlap audits;
4. the vector and assignments are frozen with immutable digests;
5. zero games are initialized and zero outcomes are exposed.

A later Phase 32 execution-readiness gate must independently validate the frozen
loader, runner binding, durability, and stop-on-first-invalid behavior before any
official game is allowed to start.
