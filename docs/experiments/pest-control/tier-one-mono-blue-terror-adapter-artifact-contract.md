# Pest Control Tier-1 coverage — Mono-Blue Terror adapter and artifact contract

## Boundary

This gate adds deterministic provenance mapping and a byte-level artifact reconciliation contract.
It does not add a game initializer, execution method, artifact writer, workflow, entropy source,
seed file, vector, or seed-freeze operation. All tests use conspicuously synthetic in-memory
identities, seed values, and byte arrays.

The harness remains bound to smoke-specification merge
`8fabd51c12f46a8ce7c00e61f405d3be61f3731c`. Pest Control v1.0, the frozen Serpico_CC Mono-Blue
Terror 75, and every historical Pest identity and disposition remain unchanged.

## Adapter contract

The provenance adapter rejects malformed source/freeze hashes, zero or mismatched synthetic seeds,
invalid seat pairs, game positions outside 1–4, and any assignment that differs from the predeclared
joint cell. Successful mapping binds both maindeck hashes, the accepted readiness source, complete-75
identity, future freeze hashes, and the nonexperimental classification. It exposes no `initialize`
function and consumes no entropy.

## Artifact contract

The artifact index requires:

- exact protocol, block, accepted-readiness, freeze, vector, assignment, and manifest identities;
- four unique nonzero future frozen seeds when validating a hypothetical vector;
- attempts and records that are ordered prefixes of that hypothetical frozen vector;
- no record without a prior attempt;
- exact per-game raw and summary SHA-256 hashes; and
- either complete four-game `VALIDATED` disposition or preserved-prefix `REJECTED` disposition.

Reorder, replacement, orphan records, byte tampering, and false completion fail closed.

## Current state

- Harness: `DISABLED`
- Vector identity: absent
- Official seeds generated: `0`
- Official games authorized: `0`
- Outcome exposure: `0`
- Game initializer: absent
- Execution method: absent
- Outcome-bearing workflow: absent

The following gate adds a construction-only initializer plus conservation and telemetry fixtures.
It must not create a vector, accept an official assignment, or execute a smoke game.
