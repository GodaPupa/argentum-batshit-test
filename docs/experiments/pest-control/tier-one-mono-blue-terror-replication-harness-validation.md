# Pest Control Tier-1 coverage — Mono-Blue Terror 12-game replication harness validation

## Scope

This gate combines two non-gameplay steps for the already-frozen 12-game replication vector:

1. construction of the ordered fail-closed replication execution harness; and
2. read-only validation of the exact frozen replication artifact against that harness.

It does not initialize an official game, submit an official action, invoke the production driver, or
expose an official outcome.

## Frozen identities

- freeze run: `35911067849`
- artifact ID: `10773131628`
- artifact archive SHA-256: `4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25`
- ordered vector SHA-256: `445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d`
- assignment CSV SHA-256: `24c1ce43362dbb2ae61fa79926437182fb74d0b76ff28a6ec44d040c2a434b70`
- manifest SHA-256: `ad10e52e6b64d85aa4d890ff772aa31a271435ed100564490f63ec9afc1d6862`
- freeze source commit: `e418f746b4d19457aa7ef748329c8a25f40e92c8`

## Harness invariants

The loader requires exactly 12 ordered unique nonzero seeds and exact protocol, block, deck and
qualified-runner identities.

The assignment plan is exactly:

- 6 Pest play / 6 Pest draw;
- 6 Pest seat zero / 6 Pest seat one; and
- 3 observations in each seat × starting-deck cell.

The coordinator requires attempt persistence, initialization-entry persistence and completed-record
persistence in strict global order. Any exception terminates the block immediately; there is no
continue-after-failure, reroll, replacement or regeneration path.

Synthetic tests cover complete Games 1–12 success and a Game 5 terminal failure that leaves only
Games 1–4 recorded.

## Exact artifact validation

The dedicated pull-request workflow downloads artifact `10773131628`, verifies the outer archive
digest and internal checksum inventory, then invokes only the replication input loader.

The validation test checks exact seed/assignment order and frozen hashes. It does not print seed
values or invoke an initializer, game environment, pilot, production driver, coordinator execution
with official assignments, or outcome-bearing API.

Official replication counters remain:

- games initialized: `0/12`;
- actions submitted: `0`;
- outcome exposure: `0/12`.

A later separately reviewed gate is required before official replication execution.
