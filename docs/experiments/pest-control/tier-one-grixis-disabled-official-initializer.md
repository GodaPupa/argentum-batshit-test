# Pest Control Tier-1 coverage — disabled official initializer

## Purpose

This gate adds a private implementation boundary for the future Grixis smoke initializer without
making it executable. The implementation accepts no assignment, vector, seed, authorization,
execution commit, or caller-provided entropy. Its sole reachable path initializes the existing
fixed construction fixture with entropy `0x4752495849530001`, submits zero actions, discards the
environment, and returns only a deterministic validation hash.

The construction validation SHA-256 is
`c5c4c64d9d6d7dcacefbce170be7ff6b7da518ab27f06f4d82609179dc5d26a3`. It binds the fixed synthetic
provenance, both accepted deck hashes, exact 53-library/7-hand/0-other/60-total conservation for
both seats, both evidence-exclusion flags, and zero submitted actions.

## Fail-closed boundary

The only public method remains `PestControlTierOneGrixisOfficialInitializationBoundary.inspect`.
Reflection validation proves the implementation class is not public and its environment-returning
`initialize` method is private. Workflows, tools, and server command surfaces remain free of Grixis
execution references under the preceding runner-surface preflight.

Every request, including a structurally complete synthetic request, terminates at `official
initializer is disabled`. The canonical default blocker SHA-256 is
`64cd80b7d0e7e5a76c89fe98b7b67355ff21a9569cbb5720a89314474efaad14`.

## Current state

- Harness: `DISABLED`
- Official vector: absent
- Official initializer implementation: private and `DISABLED`
- Public environment-returning initializer methods: `0`
- Disabled-initializer construction fixture initializations per boundary inspection: `1`
- Total construction-only fixture initializations per boundary inspection, including turn-zero preflight: `2`
- Construction actions submitted: `0`
- Official seeds generated: `0`
- Official games initialized: `0`
- Outcome exposure: `0`
- Execution method: absent

The next gate may validate a private, disabled single-game composition using only the same fixed
nonexperimental construction entropy. It must not add a workflow, command entry point, official
vector, seed source, artifact writer, or outcome-producing action.
