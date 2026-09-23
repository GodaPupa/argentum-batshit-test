# Pest Control Tier-1 coverage — Mono-Blue Terror disabled assignment schema

## Purpose

This gate validates the future four-row assignment shape without creating assignments or accepting
entropy. Each row contains only its game number, seats, starting deck, and an opaque synthetic slot
label. The row type structurally contains no seed, entropy, vector, commit, environment, action,
artifact, or outcome field.

The canonical schema SHA-256 is
`8de7cd011a21453f74b8ef5b0e2c2312fb6b8be5fffe75e8cfcfeb64f1f6ef9f`, with classification
`NONEXPERIMENTAL_SCHEMA_VALIDATION_ONLY`. Tests prove that duplicate or substituted slot labels
fail closed without creating an official assignment.

## Current state

- Harness: `DISABLED`
- Official vector: absent
- Numeric entropy fields: `0`
- Official assignments: `0`
- Official seeds generated: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Outcome exposure: `0`
- Workflow/command entry points: `0`

`PestControlTierOneMonoBlueTerrorDisabledAssignmentSchema` exposes only `inspect`, bringing the
compiled API surface audit to six objects. The next gate may validate a synthetic provenance binding
for these opaque rows. It must not turn slot labels into seeds, generate or freeze a vector,
initialize a game, submit an action, or write an execution artifact.
