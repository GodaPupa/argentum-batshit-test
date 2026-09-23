# Pest Control Tier-1 coverage — Mono-Blue Terror disabled synthetic provenance

## Purpose

This gate binds each opaque assignment-schema row to the accepted protocol, smoke block, qualified
runner, frozen Pest Control main-deck hash, frozen Mono-Blue Terror main-deck hash, and
assignment-schema hash. The provenance type has no seed, entropy, vector, execution commit,
environment, action, artifact, or outcome field.

The canonical synthetic provenance SHA-256 is
`abbdef63a86036d1f8c390fec9401308e7240cdcc9481957a859e74de5b5f76f`, classified as
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

`PestControlTierOneMonoBlueTerrorDisabledSyntheticProvenance` exposes only `inspect`, bringing
the compiled API surface audit to seven objects. The next gate may compose a disabled pre-execution
manifest from these hashes and counters. It must remain synthetic, vectorless, seedless,
environment-free, actionless, artifact-free, and unreachable from workflows or commands.
