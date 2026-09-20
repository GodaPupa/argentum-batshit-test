# Gate 1 — deterministic structural screen v1

Status: authorized diagnostic screen; no official matchup seeds and no gameplay
outcomes.

## Question

Which isolated two-for-two challenger to the exact submitted 60 provides the
strongest structural reason to spend credits on executable card support and
gameplay?

The three candidates isolate Turbo Tron, Pactdoll artifact velocity, and
recursive resilience. The exact submitted 60 is also simulated as the control.
All four lists use the same sideboard, are paired on the same seeds, and remain
separate throughout the screen.

## Evidence boundary

This gate uses a transparent draw/selection/mana model. It measures deck shape
and deterministic policy outcomes. It does **not** claim rules-engine game wins,
opponent interaction, sideboard quality, or tournament win rate. In particular,
fair-game win capability and recovery are proxies until an executable harness is
green.

## Diagnostic seeds

- Namespace: `IW-G1-V1`
- Seeds: SHA-256-derived signed 64-bit values from namespace plus indices 1–2000
- Same 2,000 seeds for each candidate (paired screen)
- These seeds are diagnostic and permanently ineligible for later matchup or
  promotion evidence.
- The validator fails on zero, duplicates, or a literal collision with any
  integer seed recorded in repository text.

Two thousand local iterations are intentionally inexpensive: large enough to
reject gross structural failures, too small and too abstract to justify
promotion.

## Recorded metrics

- London mulligan rate and mean final hand size
- Tron assembled by turns 3, 4, and 5
- green- and black-mana access failures
- first raw combo-ready turn (Altar + payoff + two Retrievers visible)
- first modeled lethal loop turn
- Myr Retriever pair visibility/availability
- redundant payoff cards stranded in hand
- non-infinite Pactdoll triggers through turn 8
- recursion access and a forced-interaction recovery proxy
- fair-game material proxy through turn 8

## Gate decision

A candidate may advance only if it has a coherent advantage on its declared
axis without a material loss to Tron assembly, colored access, mulligans, or
combo timing. A signal must be replicated on fresh, non-overlapping diagnostic
seeds before any deck promotion. If no candidate clears that bar, all three are
preserved as rejected screens and a new hypothesis is declared.
