# Pest Control Tier-1 coverage — Mono-Blue Terror smoke-vector freeze gate

## Authorization and scope

The accepted construction closure is
`CONSTRUCTION_READY_VECTOR_CREATION_NOT_AUTHORIZED`. The subsequent research decision authorizes
construction of this guarded freeze gate only. The gate can validate deterministic fixture bytes in
pull requests and defines a separate manual one-shot production freeze path. It does not authorize
game initialization, action submission, execution, sideboarding, reporting, or outcome exposure.

Frozen inputs:

- Construction merge: `7c6919782fb15a0d647bc5a253f181efc3397615`
- Construction proof: `ff68ed38ab676ae989191aca44d720b32605743c1501455193e6a22d76df6538`
- Qualified runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`
- Protocol: `PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4`

## Collision exclusion universe

The generator validates the complete current Pest seed universe before any entropy request:

- permanent historical registry: 463;
- accepted V2 calibration: 10;
- rejected V2 candidate: 10;
- V2 smoke fixture: 1;
- frozen V2 qualification vector: 50;
- accepted Grixis smoke vector: 4; and
- frozen Grixis replication vector: 12.

These seven groups are pairwise disjoint for **550 unique retired identities**. The Grixis values
were recovered from their preserved immutable freeze artifacts and independently reconciled to
ordered-vector SHA-256 values
`99eb94c4ec28f073534c008b367f3384df25abebd29dde0a9574227599cb60eb`
and
`5cd8a78fb62a59495d07ed31c4579fab7bafe2bc9c075aa7757a7f67953c75d4`.
The replication exclusion retains all twelve frozen assignments, including consumed/incomplete Game
1; there is no reuse of any prior Grixis seed.

## Fixed assignment mapping

| Game | Pest seat | Terror seat | Starting deck |
|---:|---|---|---|
| 1 | zero | one | Pest Control |
| 2 | zero | one | Mono-Blue Terror |
| 3 | one | zero | Pest Control |
| 4 | one | zero | Mono-Blue Terror |

This gives exact 2/2 Pest play-draw and 2/2 Pest seat balance.

## Generation discipline

Pull requests request no entropy. They run source-hash preflight plus deterministic fixture
generation and assert the 550-value collision boundary and exact four-cell assignment structure.

The production path requires a separate manual `workflow_dispatch` on `main`, workflow attempt
one, exact acknowledgement
`GENERATE_TIER_ONE_MONO_BLUE_TERROR_SMOKE_4_NO_GAMEPLAY`, and absence of any prior named freeze
artifact. It makes exactly one `os.urandom(32)` call.

The complete four-value draw is fsynced to quarantine before validation. If any value is zero,
duplicated, or collides with the 550-value exclusion set, the entire draw becomes
`INVALID_RETIRED` and the operation stops permanently. There is no retry, replacement, reroll, or
seed regeneration path.

A valid production artifact contains the quarantined vector, ordered vector, fixed assignment CSV,
manifest, and checksum inventory with disposition `FROZEN_UNEXECUTED`. Official games initialized,
actions submitted, outcome artifacts, and outcome exposure remain zero.

Manual dispatch runs `35814874094` and `35816035083` are formally rejected as pre-entropy
dispatch-guard incidents. Runs `35817231288` and `35818142621` are separately rejected as a
checkout-order pre-entropy incident: both attempted to invoke a repository guard script before
checkout. Across all four runs, zero entropy calls occurred, zero seeds were drawn or retired, and
zero freeze artifacts were created. The production order is now checkout → metadata guard →
artifact guard → source audit → one-shot draw, and PR validation asserts that exact ordering.

This gate itself creates no official seed and executes no game.
