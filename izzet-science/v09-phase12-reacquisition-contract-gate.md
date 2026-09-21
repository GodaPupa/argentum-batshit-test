# v0.9 Commander-Independent Readiness — Phase 12 Reacquisition Contract Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Purpose

Replace the invalid one-acquisition assumption exposed by inadmissible run
`35549404361` without sampling, assigning a seed, or changing the deck.

## Reacquisition semantics

Summary schema `izzet-v09-capsize-paired-v2` separates two estimands:

- `capsize_tutor_events_by_now` counts every successful acquisition event and may
  exceed the number of trajectories;
- `capsize_ever_tutored_by_now` counts trajectories with at least one acquisition
  and is bounded by the sample count.

Merchant Scroll and Drift event counts remain separate and must sum exactly to all
Capsize tutor events. All four cumulative fields must be nondecreasing; event counts
are bounded by one event per trajectory-turn, and `ever tutored` cannot exceed either
the event count or the number of trajectories.

## Deterministic causal fixture

A seed-free zone-transition fixture now executes this full path:

1. Merchant Scroll acquires Capsize;
2. Brainstorm returns Drift of Phantasms and Capsize to the library in deterministic
   order;
3. the next draw returns Drift to hand while Capsize remains in the library;
4. Drift transmute acquires Capsize a second time.

The synthetic paired ledger contains two successful events in one trajectory, split
one per tutor, while its `ever tutored` contribution remains one. Thirteen malformed
summary/JSON mutations are rejected. Paired partitions, readiness subsets, monotonic
lethal counts, seed coordinates, and strict JSON rules remain unchanged.

## Authorization

Both failed runners are retired and both exposed seeds remain consumed. This gate
runs no paired iterator, assigns or consumes no seed, and authorizes no pilot. Any
future execution requires a new runner, fresh seed audit, frozen source, and separate
manual workflow.

No card or policy earns promotion. v0.7 remains the accepted control.

Disposition: `V09_PHASE12_SEED_FREE_VALIDATED`
