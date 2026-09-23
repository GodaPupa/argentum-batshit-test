# Pest Control Tier-1 coverage — Mono-Blue Terror disabled single-game wiring

## Purpose

This gate composes the first accepted smoke cell with the qualified runner, private disabled
initializer proof, and canonical activation blocker. It is deliberately seedless: no vector,
assignment, entropy, environment, action, result, or artifact is accepted or produced.

The pinned composition SHA-256 is
`d463d2796b7e6eba536652e27d7456f914120ad0c30a34c266744b36c690a1a9`. It binds:

- the accepted Pest Control versus Serpico_CC Mono-Blue Terror preboard protocol and smoke block;
- Game 1, Pest in seat zero, Terror in seat one, Pest starting;
- qualified runner `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`;
- disabled-initializer construction proof
  `f99fae927e7ca5a4530b315a00107dcedb845f78e7b45a5b680185f99f39533e`;
- activation-blocker proof
  `64cd80b7d0e7e5a76c89fe98b7b67355ff21a9569cbb5720a89314474efaad14`;
- initializer enabled `false`; and
- zero submitted actions, official seeds, official games, and outcome exposure.

## Public surface

`PestControlTierOneMonoBlueTerrorDisabledSingleGame` exposes only `inspect`. The runner-surface
preflight audits this fourth compiled API alongside the construction initializer, official boundary,
and execution contract. Any public run or execution method still fails closed.

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
