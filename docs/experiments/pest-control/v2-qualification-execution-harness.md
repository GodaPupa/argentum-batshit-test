# Pest Control V2 qualification execution harness — validation boundary

This gate validates the exact frozen 50-game input artifact without initializing a game. It binds the
archive, ordered vector, assignments, freeze manifest, shard allocation, qualified runner, deck
hashes, protocol, block, and balance constraints already accepted in frozen readiness.

The harness has no execution mode. Its workflow exposes only validation, and its Kotlin entry point
only reads bytes, builds the sealed two-shard plan, proves that the runner guard rejects activation,
and emits `VALIDATED_UNEXECUTED` evidence. The compiled qualification runner remains `DISABLED`;
outcome exposure remains 0/50.

Validation requires:

- archive SHA-256 `bf7456303c355202587484f208da20a1ca37a2ba326d081e8a3fb7c3a31be8f0`;
- artifact ID `10612245984` from freeze run `35532647383`, attempt 1;
- all internal artifact checksums;
- 50 unique, nonzero seeds in exact vector/CSV order;
- exact protocol, block, runner, deck, manifest, and shard identities;
- 25/25 play-draw and seat allocation, global joint cells 12/12/13/13; and
- contiguous games 1-25 and 26-50 with per-shard joint cells 6/6/6/7.

The sealed plan reuses the accepted deterministic shard artifact and aggregation contract. That
contract binds every attempt before initialization, rejects retries and substitutions, requires clean
terminal records with no rejected or fallback actions, and retires the complete vector if either
shard is missing, failed, cancelled, timed out, duplicated, reordered, or incomplete.

Any mismatch rejects validation. Authorization and execution require a later, separately reviewed gate
that pins a green harness commit and preserves the no-retry, no-replacement, whole-vector rejection
boundary.
