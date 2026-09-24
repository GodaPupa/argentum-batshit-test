# Pest Control Tier-1 coverage — Monster Tron execution authorization

## Scope

The exact four-game Monster Tron smoke vector has been frozen once and independently audited.
This gate authorizes that immutable vector to proceed to **separately reviewed operational
implementation**. It does not itself enable an initializer or runner and cannot execute gameplay.

Protocol:
`PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

Block:
`PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4`

## Frozen artifact

- production-freeze run: **36066393701** — SUCCESS
- source commit: `4230ccaef2760fef54604f64b260dc897d618250`
- artifact: `pest-control-tier-one-monster-tron-smoke-vector-freeze`
- artifact ID: **10836436268**
- archive SHA-256:
  `70b9a665fbb154342e2789c1b6b2c2fd912579431a6ae1f9ab289984d5e7801c`
- ordered vector SHA-256:
  `1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31`
- assignment CSV SHA-256:
  `3e8c61d729d219261eb485600039b150d66e2e73e90f0f2e1450c7b48a57e098`
- freeze manifest SHA-256:
  `12b2b0c6c8209fc557c2a2a524223c603b42ae6c7c7ce211cbd291db75fbf6a8`
- quarantine SHA-256:
  `037fce3d177782fdd26b10bd7a77227f8d0d8cae64b9ef8df025171633f037c8`
- checksum inventory SHA-256:
  `397249d512a86c59266eb98796e96ebac2891495db1182e7c3251e4c37e58d02`

Independent download reproduced the exact archive digest and all internal checksum entries.
The collision audit proves four unique new seeds and zero overlap with 566 retired Pest identities.

## Frozen deck identities

- Pest Control:
  `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- mehanske Monster Tron:
  `79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f`
- qualified Pest runner:
  `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`

## Authorization

Status: `EXECUTION_AUTHORIZED_HARNESS_STILL_DISABLED`

Authorization proof SHA-256:
`38d2c07deec8be4332264d8873b42aa793d5606f31047a656bb29866a2e5a2a5`

Exactly four games are authorized for one future attempt. No rerolls, replacements, seed
regeneration, resume, salvage, sideboarding, or deck changes are authorized.

This gate deliberately leaves:
- runner enabled: `false`;
- initializer enabled: `false`;
- official seeds generated: `4`;
- official games initialized: `0`;
- official actions submitted: `0`;
- outcome exposure: `0`.

The next gate may implement the operational initializer only if this authorization validates cleanly.
Gameplay still requires a later reviewed one-shot execution surface.
