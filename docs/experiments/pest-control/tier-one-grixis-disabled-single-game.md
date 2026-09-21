# Pest Control Tier-1 coverage — disabled single-game wiring

## Purpose

This gate composes the first accepted smoke cell with the qualified runner, private disabled
initializer proof, and canonical activation blocker. It is deliberately seedless: no vector,
assignment, entropy, environment, action, result, or artifact is accepted or produced.

The pinned composition SHA-256 is
`ec209b598a19e174b9fdae64c0a232c8cd364a4b9bbdaf6f902b00ab861fcd6e`. It binds:

- the accepted Pest Control versus Pasquale Grixis preboard protocol and smoke block;
- Game 1, Pest in seat zero, Grixis in seat one, Pest starting;
- qualified runner `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`;
- disabled-initializer construction proof
  `c5c4c64d9d6d7dcacefbce170be7ff6b7da518ab27f06f4d82609179dc5d26a3`;
- activation-blocker proof
  `64cd80b7d0e7e5a76c89fe98b7b67355ff21a9569cbb5720a89314474efaad14`;
- initializer enabled `false`; and
- zero submitted actions, official seeds, official games, and outcome exposure.

## Public surface

`PestControlTierOneGrixisDisabledSingleGame` exposes only `inspect`. The runner-surface preflight now
audits this fourth compiled API alongside the construction initializer, official boundary, and
execution contract. Any public run or execution method still fails closed.

## Current state

- Harness: `DISABLED`
- Official vector: absent
- Single-game assignment: absent
- Official seeds generated: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Outcome exposure: `0`
- Workflow/command entry points: `0`

The next gate may compose all four accepted cells as a disabled, seedless plan. It must continue to
exclude assignments, entropy, environments, actions, artifacts, and any official execution surface.
