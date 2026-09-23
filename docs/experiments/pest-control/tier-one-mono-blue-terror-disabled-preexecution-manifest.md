# Pest Control Tier-1 coverage — Mono-Blue Terror disabled pre-execution manifest

## Purpose

This gate composes every accepted Mono-Blue Terror construction hash into one in-memory
pre-execution manifest. It binds the protocol, smoke block, qualified runner, deck identities,
initializer proof, activation blocker, cell plan, assignment schema, and synthetic provenance while
pinning every operational counter at zero.

The canonical manifest SHA-256 is
`e4ef6cee09398b4f3b3f08a15852e95fb930ab5f39ede991da8c0dbdedcd2e10`. The manifest is inspection
data only: there is no serializer or writer, workflow or command entry point, vector identity,
entropy, execution commit, enabled initializer, environment, action, artifact, or outcome.

## Current state

- Manifest state: `DISABLED`
- Expected cells: `4`
- Synthetic provenance rows: `4`
- Official vector: absent
- Initializer enabled: `false`
- Workflow entry points: `0`
- Command entry points: `0`
- Official assignments: `0`
- Official seeds generated: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Artifacts written: `0`
- Outcome exposure: `0`

State substitution fails closed and changes the pinned hash. The runner-surface preflight audits
eight inspect-only APIs. The following gate performs a final construction-readiness closure audit
over the complete disabled chain before any request to authorize vector creation. It does not create
or expose a vector, assignment, entropy value, game, action, artifact, or outcome.
