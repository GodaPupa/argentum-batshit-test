# Pest Control Tier-1 coverage — Mono-Blue Terror runner-surface preflight

## Audit

This gate audits every repository workflow, tool source, server entry surface, and the compiled
public methods of the Mono-Blue Terror initialization boundary. It fails if it finds the Terror
smoke block, execution contract, initialization boundary, or execution label on a workflow/command
surface.

The compiled API audit requires:

- the construction initializer to expose only `initializeConstructionFixture`;
- the official initialization boundary to expose only `inspect`; and
- the execution composition contract to expose only `inspect`.

Public `run`, `execute`, `main`, official initialization, seed-generation, seed-freeze, or
artifact-writer methods fail closed. Synthetic negative fixtures verify each class of exposure is
detected.

## Current state

- Workflows capable of Mono-Blue Terror smoke execution: `0`
- Commands/server entry points capable of Terror smoke execution: `0`
- Official initializer methods: `0`
- Official seeds generated: `0`
- Official games initialized: `0`
- Outcome exposure: `0`
- Harness: `DISABLED`

The next gate may construct a disabled official initializer implementation only after this surface
audit and all prior layers are green. It must remain unreachable from workflows and commands and
must be validated with fixed nonexperimental construction entropy before any official vector exists.
