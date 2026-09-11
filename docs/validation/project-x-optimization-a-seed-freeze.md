# Project X Optimization Experiment A — paired seed freeze

## Disposition

This file freezes exactly 30 deterministic paired seeds before any Experiment A game is executed. Each seed must be used once for the frozen Project X v0.2 control and once for Variant A under identical opening/play conditions. No rerolls, replacements, exclusions, substitutions, deck changes, or policy changes are permitted after this freeze.

The control remains Project X v0.2 exactly. Variant A differs only by `-1 Falkenrath Noble`, `-1 Masked Vandal`, and `+2 Llanowar Elves`; all four Evolution Witness remain.

## Derivation

For pair index `NN` from `01` through `30`, the seed is the first 15 hexadecimal digits (60 bits) of SHA-256 over:

`Project X Optimization Experiment A|NN|bb93886c838f7fa54ce867bbfd49f1c3bb374aaf`

The final component is the validated seed-readiness branch tip. The complete ordered vector is stored in `gym/src/test/resources/project-x-optimization-a-seeds.csv`.

## Pre-execution collision audit

- Candidate count: 30
- Unique candidates: 30
- Current preserved seed rows checked: 492
- Reachable Project X/Batshit seed-bearing Git history checked: all local refs (13,550 commits)
- Candidate overlap with all checked Project X/Batshit prior-use sources: 0

The frozen control deck, declared variant, no-sideboard configuration, engine, Gym execution path, solitaire agent, telemetry definitions, mulligan policy, horizon, and report semantics are unchanged from the validated seed-readiness gate.
