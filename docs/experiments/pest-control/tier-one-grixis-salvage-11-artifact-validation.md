# Pest Control Grixis — SALVAGE_CONTINUATION_11 official-artifact validation

This gate validates the exact original frozen 12-game artifact against the merged continuation plan
without gameplay.

It downloads artifact `10660335894`, verifies archive SHA-256
`49597c4a3011464e1d4bb707dfa1b554090054059e0675254524c92fcce8d6fd`, verifies the internal checksum
inventory, loads the exact frozen 12-game identity, and then derives the executable suffix only through
`PestControlTierOneGrixisReplicationContinuationPlan.untouchedSuffix`.

The validation requires:

- original artifact still contains Games 1-12 in exact order;
- executable continuation contains exactly Games 2-12;
- Game 1 is absent;
- continuation seeds equal the untouched original seed suffix, with no new vector;
- actual suffix denominators remain 5 Pest-start / 6 Grixis-start and 5 Pest-seat-zero / 6
  Pest-seat-one;
- exact frozen vector, assignment, and manifest hashes remain unchanged.

No coordinator, initializer, game environment, pilot, or production driver is invoked. No official
seed is consumed and no outcome can be exposed.
