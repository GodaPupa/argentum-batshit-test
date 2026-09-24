# Pest Control Tier-1 coverage — Monster Tron disabled runner construction

Protocol: `PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

Authoritative predecessor: runner-surface preflight PR #135 merge
`7d86c5b88038db265fd09e6a6ef6f36f2c49ece5`.

## Scope

This gate constructs the future preboard execution boundary without creating an official execution
surface. It freezes the smoke design and validates one fixed synthetic construction fixture only.

The prospective smoke shape is fixed before any outcome exposure:

- four games;
- Pest seat zero twice and seat one twice;
- Pest starts twice and Monster Tron starts twice;
- exactly one observation in each seat × starting-deck cell.

No official seed vector exists in this gate.

## Runner contract

The future driver is pinned to:

- qualified Pest production runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`;
- Pest AI profile: `PRODUCTION_CANDIDATE_EXPIRING`;
- Monster Tron AI profile: `PRODUCTION_CANDIDATE_EXPIRING`;
- London mulligans through the engine AI;
- exact-one action submission;
- ceilings of 12,000 actions, 60 turns, and 500 actions per turn.

The runner state remains `DISABLED`; there is no `drive`, `execute`, command entrypoint, seed
source, artifact writer, or outcome-producing API in this gate.

## Construction-only initializer

The implementation class is private. It accepts no assignment, vector, caller-provided seed,
authorization, or execution commit and never exposes its `GameEnvironment`.

It may initialize only the fixed synthetic entropy
`0x6d74726f6e000001`, which is explicitly excluded from experimental evidence and from the future
official seed-overlap registry. It submits zero actions and validates exact opening conservation:

- Pest Control: 53 library / 7 hand / 0 other / 60 total;
- mehanske Monster Tron: 53 library / 7 hand / 0 other / 60 total.

Construction proof SHA-256:
`bab0cb5ba9473473ce5576c28535f62cfe49dc28d4c73e6432f0769fbfd1f1d7`

Canonical activation-blocker SHA-256:
`6eefe83b06fc89c4d4214724ee31983659e1b199cc9ef2b27cdc0240925abb34`

Terminal blockers include the disabled harness, absent official vector/assignment/execution commit and
durable-attempt marker, and the disabled initializer itself.

## Experimental boundary

Official counters remain:

- seeds generated: `0`;
- games authorized: `0`;
- games initialized: `0`;
- actions submitted: `0`;
- outcomes exposed: `0`.

A green merge authorizes only the next **smoke seed-freeze gate**. That next gate must derive the
complete live retired Pest identity universe, create exactly four fresh collision-free seeds once,
freeze the ordered 2×2 assignment vector, seal its hashes, and retire regeneration before gameplay.
