# Pest Control Tier-1 coverage — Mono-Blue Terror disabled four-cell plan

## Purpose

This gate composes all four accepted smoke cells as immutable metadata. It verifies exact game
order, Pest seat balance of 2/2, and starting-deck balance of 2/2 while retaining the qualified
runner and all prior disabled-initializer proofs.

The plan remains completely vectorless. It contains no seed, assignment, environment, action,
artifact, result, or workflow entry point. The pinned plan SHA-256 is
`9d63755d895f22edca56a4725189a8fd5cf1489abf816a11eacf9b5733f3fe86`.

| Game | Pest seat | Terror seat | Starting deck |
|---:|---|---|---|
| 1 | zero | one | Pest Control |
| 2 | zero | one | Mono-Blue Terror |
| 3 | one | zero | Pest Control |
| 4 | one | zero | Mono-Blue Terror |

## Public surface

`PestControlTierOneMonoBlueTerrorDisabledFourCellPlan` exposes only `inspect`. The runner-surface
preflight audits five compiled APIs. Public run, execute, initializer, seed, freeze, or writer
methods continue to fail closed.

## Current state

- Harness: `DISABLED`
- Official vector: absent
- Assignments: `0`
- Official seeds generated: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Outcome exposure: `0`
- Workflow/command entry points: `0`

The following gate validates a disabled assignment schema using opaque synthetic labels only. It
does not generate or freeze a vector, accept official entropy, initialize a game, submit an action,
or write an execution artifact.
