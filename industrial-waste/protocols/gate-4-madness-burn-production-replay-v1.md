# Gate 4 Madness Burn production-profile replay v1

Status: planned; replay-only calibration.

## Question

Does replacing only the deficient Madness Burn v0 pilot with the already-proven
`PRODUCTION_CANDIDATE_EXPIRING` profile restore enough opposing pressure to make this exact matchup
harness credible for a later fresh test?

## Frozen inputs

- Industrial lists: immutable v1.0 Control and unpromoted Pactdoll-A.
- Industrial agent: unchanged `industrial-waste-policy-v2`.
- Opponent list: Davide Canevazzi's published 2026-09-12 Madness Burn 60.
- Opponent agent: `PRODUCTION_CANDIDATE_EXPIRING` only.
- Seeds: exact eight-seed `IW-G4-MADNESS-BURN-R1` vector, digest
  `011e5fdfb0d05340968c4df3fd4bde6628a1a5fc9364ebc045d1a62daafec7ef`.
- Frozen v0 result artifact SHA-256:
  `2e2db7a1177fab08872e23aa437fa70d219ac0a78da5bff4c200f38cb669c903`.

This run reuses spent seeds by design. It receives no new namespace, cannot be pooled with either Gate
4 sample, and is ineligible for deck promotion or win-rate estimation.

## Execution and gates

Replay all eight seeds for both Industrial lists and both seat rotations: 32 games total. The exact
vector was initially partitioned by original seed index across two disjoint 16-game CI shards. The
even-index shard completed cleanly; the odd-index shard wrote complete output but exceeded the test
timeout and is quarantined. Only that failed half is subdivided into two disjoint 8-game partitions
(original zero-based indices 1/5 and 3/7). Accept evidence only after the clean even shard and both
clean odd subshards are complete and merged, and only if all games complete without exceptions,
rejected actions, or unapproved draw reasons.

The opponent-policy calibration passes only if:

1. Madness Burn wins at least 4/16 against Control and at least 4/16 against Pactdoll-A, restoring the
   original two-sided pressure floor; and
2. at least one game result differs from the frozen v0 artifact, proving that the policy substitution
   is observable in the spent vector.

Passing authorizes only a decision on whether one fresh Burn namespace is worth its cost. Failure
retires Madness Burn from qualification work in the current harness. Neither outcome promotes or
rejects Pactdoll-A.
