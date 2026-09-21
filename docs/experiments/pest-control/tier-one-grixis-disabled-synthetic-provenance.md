# Pest Control Tier-1 coverage — disabled synthetic provenance

## Purpose

This gate binds each opaque assignment-schema row to the accepted protocol, smoke block, qualified
runner, frozen Pest Control main-deck hash, frozen Grixis main-deck hash, and assignment-schema hash.
The provenance type has no seed, entropy, vector, execution commit, environment, action, artifact,
or outcome field.

The canonical synthetic provenance SHA-256 is
`f33aaf2abb7e8e80a35c68a81b3d1c80dd8caea085a1e2cddb07846fbe130259`, classified as
`NONEXPERIMENTAL_SYNTHETIC_PROVENANCE_ONLY`. Qualified-runner substitution changes the proof and
fails closed without changing any official counter.

## Current state

- Harness: `DISABLED`
- Synthetic provenance rows: `4`
- Official vector: absent
- Numeric entropy fields: `0`
- Official assignments: `0`
- Official seeds generated: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Outcome exposure: `0`
- Workflow/command entry points: `0`

`PestControlTierOneGrixisDisabledSyntheticProvenance` exposes only `inspect`, bringing the compiled
API surface audit to seven objects. The next gate may compose a disabled pre-execution manifest from
these hashes and counters. It must remain synthetic, vectorless, seedless, environment-free,
actionless, artifact-free, and unreachable from workflows or commands.
