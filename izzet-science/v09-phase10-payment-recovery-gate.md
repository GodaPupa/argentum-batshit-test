# v0.9 Commander-Independent Readiness — Phase 10 Payment Recovery Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Purpose

Repair the selector/executor payment disagreement exposed by the inadmissible
Phase-9 paired pilot without sampling, assigning a seed, or changing the deck.

## Shared exact activation engine

Ready-mana feasibility and mutating colored payment now consume the same activation
state graph. Each reachable U/R/C pool carries a deterministic minimum-permanent
witness; execution taps that witness only after a payable pool is found. The graph
models leaving sources unused, land color choices, Boilerworks' fixed UR, ordinary
rocks and mana creatures, variable Chalice output, Lens mana or filtering, and
Signet activation.

Three deterministic end-to-end fixtures require Merchant Scroll to be selected,
paid, consumed, and resolved into Capsize through the formerly divergent source
classes:

- Island plus Izzet Signet;
- two Mountains plus Prismatic Lens filtering;
- Island plus Star Compass using its controlled-basic color.

Each fixture proves exact feasibility, selector choice, successful mutation, the
expected library/hand transition, and source tapping. Existing policy precedence,
failure atomicity, instrumentation, paired RNG, paired-contract, readiness, and
multiplayer-kill validators remain required adjacent controls.

The old Phase-9 runner now has an unconditional retirement guard before argument
parsing or iterator construction. It cannot consume the retired seed again; its
original bytes remain permanently available at the frozen source commit.

## Authorization

This is a seed-free repair only. It runs no paired iterator, assigns or consumes no
seed, exposes no outcome, and authorizes no pilot. A subsequent gate must freeze a
new runner/source/artifact identity and assign a fresh historically unused seed.

No card or policy earns promotion. v0.7 remains the accepted control.

Disposition: `V09_PHASE10_SEED_FREE_VALIDATED`
