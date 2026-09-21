# Pest Control Tier-1 coverage — disabled four-cell plan

## Purpose

This gate composes all four accepted smoke cells as immutable metadata. It verifies exact game order,
Pest seat balance of 2/2, and starting-deck balance of 2/2 while retaining the qualified runner and
all prior disabled-initializer proofs.

The plan remains completely vectorless. It contains no seed, assignment, environment, action,
artifact, result, or workflow entry point. The pinned plan SHA-256 is
`c52c6186d42c554dd3972feaed7d1325b7aa7fbdf83cce31492a525c3bf826a3`.

| Game | Pest seat | Grixis seat | Starting deck |
|---:|---|---|---|
| 1 | zero | one | Pest Control |
| 2 | zero | one | Grixis Affinity |
| 3 | one | zero | Pest Control |
| 4 | one | zero | Grixis Affinity |

## Public surface

`PestControlTierOneGrixisDisabledFourCellPlan` exposes only `inspect`. The runner-surface preflight
now audits five compiled APIs. Public run, execute, initializer, seed, freeze, or writer methods
continue to fail closed.

## Current state

- Harness: `DISABLED`
- Official vector: absent
- Assignments: `0`
- Official seeds generated: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Outcome exposure: `0`
- Workflow/command entry points: `0`

The next gate may validate a disabled assignment schema using synthetic nonexperimental values only.
It must not generate or freeze a vector, accept official entropy, initialize a game, submit an action,
or write an execution artifact.
