# Pest Control Tier-1 coverage — frozen Grixis vector binding

## Result

The frozen artifact provenance is now bound to the accepted four-cell plan and qualified runner by
an inspect-only compiled contract. Its canonical binding SHA-256 is
`829afa5ed1e0bcc62481a99e236d1e5d0c04a6353c5c16efed5f40c4c5ee84cf`, and its only successful
status is `VECTOR_FROZEN_EXECUTION_NOT_AUTHORIZED`.

The binding pins the workflow run and artifact identities, source commit and tree, ordered-vector,
assignment, manifest, quarantine, and archive hashes. It does not load or expose seed values. The
historical seedless construction closure remains unchanged and is referenced by its accepted proof.

## Fail-closed state

- Official vector identity: present and frozen
- Official assignments: `4`
- Official seeds generated: `4`
- Regeneration permitted: `false`
- Production dispatch present: `false`
- Initializer enabled: `false`
- Runner enabled: `false`
- Official games authorized: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Outcome exposure: `0/4`

Vector-hash or runner substitution changes the canonical proof and fails closed while every
execution counter remains zero. `PestControlTierOneGrixisFrozenVectorBinding` exposes only
`inspect`; it has no seed loader, initializer, executor, writer, workflow, or command entry point.

This gate does not authorize execution. The next construction gate may define a disabled artifact
ingestion contract that verifies bytes against these hashes without initializing a game.
