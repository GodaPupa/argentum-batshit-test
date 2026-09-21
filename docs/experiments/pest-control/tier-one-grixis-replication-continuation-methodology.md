# Pest Control Tier-1 Grixis replication — continuation methodology after Game 1 infrastructure failure

## Decision

Games 2-12 may be executed exactly once as a **salvage continuation block**, but the resulting eleven
completed games must not be described as the originally planned 12-game replication.

Game 1 remains permanently consumed/incomplete. Its seed may never be replayed, replaced, regenerated,
or scored as a win/loss.

## Why continuation is methodologically admissible

The failure in official run `35654021529` occurred before Game 1 produced a terminal raw artifact and
before any matchup outcome was exposed. The failure was caused by a deterministic identity guard in
the execution infrastructure: the reused initializer required the old four-game smoke hashes.

The incident therefore revealed no win/loss information about Game 1 and no information about Games
2-12. The identities, seeds, assignment order, decks, pilot policy, protocol, and matchup were frozen
before the failure and remain unchanged.

Executing Games 2-12 does not condition continuation on observed matchup performance. It is a
pre-specified untouched suffix after a non-outcome infrastructure failure.

## Required continuation boundary

A continuation runner must:

1. load and verify the exact original 12-game frozen artifact;
2. preserve the original game numbers and assignments;
3. require Game 1 to be absent from its executable assignment list;
4. execute only Games 2 through 12, once each, in original order;
5. persist a durable attempt marker before every initialization;
6. stop permanently on the first failure;
7. prohibit rerolls, replacements, seed regeneration, Game-1 replay, and outcome-conditioned restart;
8. preserve the original decks, pilot, production driver, and protocol;
9. upload all available evidence even on failure.

No new seed vector may be generated.

## Inference status

The eleven-game continuation is **SALVAGE_CONTINUATION_11**, not a clean 12-game replication.

Primary reporting must state:

- planned replication: 12 frozen assignments;
- Game 1: consumed/incomplete due infrastructure identity-guard failure, no outcome;
- completed salvage sample: Games 2-12 only;
- missingness mechanism: pre-outcome deterministic infrastructure failure.

The original 4-game smoke remains separate from the salvage continuation for primary inference.
A combined 15 completed-game descriptive view (4 smoke + 11 continuation) may be reported only as
secondary context after the continuation is independently accepted.

Because the 11-game suffix is no longer perfectly balanced, play/draw and seat-stratified rates must
report their actual denominators rather than being presented as the planned 6/6 design.

## Current authorization

This document authorizes construction and deterministic validation of a Games-2-12 continuation
harness only. It does not authorize official gameplay.
