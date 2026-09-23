# Pest Control Tier-1 coverage — Mono-Blue Terror execution authorization

## Decision

The frozen four-game Mono-Blue Terror smoke is authorized to proceed to a **separately reviewed**
operational implementation.

This gate records the research decision only. It does not expose artifact bytes, assignments, seed
values, an initializer, runner, environment, evidence path, action or gameplay entrypoint.

Authorization is pinned to the accepted frozen artifact, vector binding, real-engine/durable-evidence
rehearsal and four-cell production-AI compatibility calibration.

## Accepted state

- Status: `EXECUTION_AUTHORIZED_HARNESS_STILL_DISABLED`
- Authorization SHA-256: `616166cad640eb3fc7640d3967816b5ad7bb8ea8456a06104adbe69777028959`
- Authorized games: `4`
- Attempt limit: `1`
- Rerolls: `false`
- Replacements: `false`
- Seed regeneration: `false`
- Runner enabled: `false`
- Official initializer enabled: `false`
- Official seeds consumed: `0`
- Official games initialized: `0/4`
- Actions submitted: `0`
- Outcome exposure: `0/4`

The next gate may implement an authorized initializer, but that initializer must still require a
durable attempt marker, the exact frozen artifact identity, and a pinned execution source commit.
No official game is initialized by this authorization gate.
