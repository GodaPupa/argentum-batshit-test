# Pest Control Tier-1 coverage — automatic Mono-Blue Terror replication freeze

## Scope

This gate authorizes exactly one production freeze of a fresh 12-game Mono-Blue Terror replication
vector. It authorizes entropy and artifact construction only; gameplay remains disabled.

Authorization record:
`tier-one-mono-blue-terror-replication-auto-freeze-authorization.json`

Authorization SHA-256:
`e2762dfd323470c0567eb12a3e9d4ef524881b17a753f9f701a0ec736f85116b`

The record binds the accepted replication construction gate merge `bc32108205e6e9daa11336125718891b541a85dc`,
its tree `21608e530d6f568d9f703f514145669694b781e9`, the accepted 4-0 smoke result, the exact
smoke freeze artifact, and the complete 554-identity collision exclusion universe.

## Production discipline

There is no manual dispatch. The production freeze is triggered only by the merge that first adds
the immutable authorization record to `main`.

Production may make exactly one `os.urandom(96)` call. The full draw is quarantined before
validation. If any value is zero, duplicated, or collides with the 554 retired identities, the
entire draw is `INVALID_RETIRED`. There is no reroll, replacement, salvage, or regeneration.

Replay barriers require:

- `main` push;
- workflow attempt `1`;
- exact authorization bytes;
- accepted freeze-gate ancestry;
- no prior production push run for this workflow; and
- no prior replication-freeze artifact.

A valid artifact must be `FROZEN_UNEXECUTED`, contain exactly 12 fresh seeds, preserve exact
3× coverage of each seat × starting-deck cell, and report zero games/actions/outcomes.

No manual workflow action is required.
