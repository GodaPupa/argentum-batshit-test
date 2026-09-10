# Project X v0.2 — Goldfish Sample #2 seed freeze

## Disposition

This file freezes exactly 30 deterministic seeds for Project X Goldfish Sample #2 before any sample game is executed. No rerolls, replacements, exclusions, or substitutions are permitted after this freeze.

Project X Goldfish Sample #1 is formally accepted as performance evidence. Its 30 seeds, together with the corrected original regression block and every Project X or Batshit development, regression, smoke, performance, baseline, optimization, and matchup seed, are permanently excluded from this and all future performance vectors.

## Derivation

For game index `NN` from `01` through `30`, the seed is the first 15 hexadecimal digits (60 bits) of SHA-256 over:

`Project X v0.2 Goldfish Sample #2|NN|beb201cbbe1b10d13b11d28eace8d50783a9ba5f`

The final component is the accepted Sample #1 preservation tip. The complete ordered vector is stored in `gym/src/test/resources/project-x-goldfish-sample-2-seeds.csv`.

## Pre-execution collision audit

- Candidate count: 30
- Unique candidates: 30
- Current preserved seed rows checked: 464
- Reachable Project X/Batshit seed-bearing Git history checked: all local refs
- Candidate overlap with Sample #1: 0
- Candidate overlap with all checked Project X/Batshit prior-use sources: 0

The frozen Project X v0.2 deck, no-sideboard configuration, engine, Gym execution path, solitaire agent, telemetry definitions, mulligan policy, horizon, and report semantics remain unchanged from Sample #1. The only reporting extension is the explicitly requested missing-primary-role attribution trace; it does not alter game decisions or execution.
