# v0.9 Position 1 — Engine Coverage Batch A Gate

Disposition: `V09_POSITION1_ENGINE_BATCH_A_GATE_OPEN`

Parent:
- Engine gap triage accepted.

## Batch A scope

Verify and, only if justified by existing engine primitives, add the three identities
classified `registry_only`:

1. Boreal Druid
2. Deepwood Denizen
3. Owlbear

## Research question

Can these three identities be brought into CardRegistry coverage without adding a
new engine mechanic family or changing gameplay semantics outside their printed
card behavior?

## Required evidence

For each identity:
- exact card definition and oracle behavior source already frozen by the Phase-24/25
  opponent identity/rules snapshots;
- concrete mapping to existing engine primitives;
- unit tests for the printed behavior relevant to this matchup;
- CardRegistry resolution;
- no changes to Izzet v0.7 or Veteran Beastrider deck identities.

If any card actually requires a new mechanic, remove it from Batch A and reclassify
it in provenance rather than forcing a registry-only implementation.

## Exit criterion

A fresh CI gate must show all three cards resolve in CardRegistry and their minimal
printed behavior tests pass. The unresolved real-engine inventory must drop by
exactly three identities, from 55 to 52, with zero official seeds consumed and zero
official games initialized.

Official position 1 remains blocked.
