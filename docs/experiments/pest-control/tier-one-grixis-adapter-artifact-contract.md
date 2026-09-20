# Pest Control Tier-1 coverage — Grixis adapter and artifact contract

## Boundary

This gate adds deterministic provenance mapping and a byte-level artifact reconciliation contract.
It does not add a game initializer, execution method, artifact writer, workflow, entropy source,
seed file, or vector. All tests use conspicuously synthetic in-memory identities and byte arrays.

The harness remains bound to smoke specification merge
`83f03cfd18664d6805944a153ebd730a2bc0789b`. Pest Control v1.0, the Pasquale Grixis 75, and every
historical Pest identity and disposition remain unchanged.

## Adapter contract

The provenance adapter rejects malformed source/freeze hashes, zero or mismatched seeds, invalid
seat pairs, game positions outside 1–4, and any assignment that differs from the predeclared joint
cell. Successful mapping binds both deck hashes, the accepted readiness source, all future freeze
hashes, and the nonexperimental classification. It exposes no `initialize` function.

## Artifact contract

The artifact index requires:

- exact protocol, block, accepted-readiness, freeze, vector, and assignment identities;
- four unique nonzero frozen seeds;
- attempts and records that are ordered prefixes of the frozen vector;
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

The next gate is a disabled initialization adapter plus conservation/telemetry fixtures. It must not
create a vector or execute a smoke game.
