# Pest Control Tier-1 coverage — Monster Tron smoke-vector freeze validation gate

Protocol: `PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

Authoritative predecessor: disabled runner construction merge
`d5fdbff18d1fb1607fe7ed951fdaa609d31ba470`.

## Scope

This is the entropy-free validation half of the one-shot Monster Tron smoke-vector freeze.
It fixes the collision universe, four-cell assignment, exact deck/runner identities, and fail-closed
artifact shape before any production entropy is requested.

It does **not** generate an official seed, initialize a game, submit an action, expose an outcome,
or enable the runner.

## Complete retired Pest seed universe

The complete live exclusion set is reconstructed from accepted immutable evidence:

1. the accepted Mono-Blue Terror replication generator reconstructs the complete pre-replication
   Pest universe at exactly **554** unique retired identities; and
2. accepted Terror replication artifact **10773131628** contributes exactly **12** additional
   unique nonzero seeds, bound to ordered-vector SHA-256
   `445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d`.

The two sets must be disjoint, producing exactly **566** retired Pest seed identities.
Any count drift, artifact digest mismatch, vector hash mismatch, duplicate, zero, or cross-source
collision fails before the fixture gate can pass.

Pinned accepted artifacts:

- Terror smoke artifact 10733086089, ZIP SHA-256
  `bbf9f20e834f27f838e37de78c905c213a8917651818367360a8e8a140914961`;
- Terror replication artifact 10773131628, ZIP SHA-256
  `4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25`.

## Frozen four-game shape

| Game | Pest seat | Monster Tron seat | Starting deck | Pest play/draw |
|---:|---|---|---|---|
| 1 | zero | one | Pest Control | play |
| 2 | zero | one | Monster Tron | draw |
| 3 | one | zero | Pest Control | play |
| 4 | one | zero | Monster Tron | draw |

This is exactly one observation in each seat × starting-deck cell.

Frozen identities:

- Pest main SHA-256:
  `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- Monster Tron main SHA-256:
  `79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f`
- qualified Pest runner:
  `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`

## Acceptance

The validation workflow must prove:

1. both accepted Terror artifact ZIP digests and internal checksum inventories match;
2. the inherited exclusion universe reconstructs to 554;
3. the accepted Terror replication vector adds exactly 12 disjoint identities;
4. the complete Monster Tron exclusion universe is exactly 566;
5. a deterministic nonexperimental four-seed fixture is unique, nonzero, and collision-free;
6. the assignment CSV contains exactly the four predeclared cells above;
7. official counters remain 0 seeds / 0 authorized games / 0 initialized games / 0 actions / 0 outcomes;
8. no production entropy API exists in this validation generator.

A green merge authorizes only a separate reviewed automatic production freeze that may request
exactly one 32-byte entropy draw. The complete draw must be quarantined before validation; any zero,
duplicate, or collision retires the entire draw with no reroll or replacement.

Gameplay remains unauthorized after this validation gate.
