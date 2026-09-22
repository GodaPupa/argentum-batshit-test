# Pest Control Tier-1 coverage — Mono-Blue Terror synthetic coordinator ledger

## Boundary

This gate adds a pure event-ledger validator for the future four-game Mono-Blue Terror smoke
coordinator. It owns no callbacks, assignments, seeds, game environments, initializer, artifact
writer, or runner. Its schema is pinned at SHA-256
`2e453fd0cf6626925fcfc60a1ee5a55b6aa880c2df62374430884b0e6be0bc9f`.

## Required order

For each game, the only successful transition is:

1. `ATTEMPT_DURABLY_RECORDED`
2. `INITIALIZATION_ENTERED`
3. `RECORD_DURABLY_WRITTEN`

Games must complete in global order 1–4. A `REJECTED` event is terminal. Any later event, retry,
duplicate initialization, orphan record, skipped game, incomplete `VALIDATED` disposition, or
partial `REJECTED` ledger without an explicit rejection marker fails closed. An empty rejected
ledger remains valid for a failure before the first attempt.

## Current state

- Harness: `DISABLED`
- Official vector: absent
- Official seeds consumed: `0`
- Official games initialized: `0`
- Outcome exposure: `0`
- Coordinator implementation: absent
- Execution method: absent

The following gate is a disabled execution-coordinator contract that composes this ledger with the
initialization boundary and artifact index using synthetic records only. It still exposes no
official runner, initializer, or seed source.
