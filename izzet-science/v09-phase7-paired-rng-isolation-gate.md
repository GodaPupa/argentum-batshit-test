# v0.9 Commander-Independent Readiness — Phase 7 Paired RNG Isolation Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Question

Can a future paired control-versus-Capsize-policy trial give both arms the same
initial shuffled deck for each game without allowing a policy-only search shuffle
to perturb later game pairs?

## Frozen derivation

Each game receives a counter-addressable child seed:

`first_u64_be(SHA256("izzet-v09-paired-game-v1\\0" || master_u64_be || index_u64_be))`

The master seed and zero-based game index must each be unsigned 64-bit integers;
Booleans are rejected. Control and policy receive separate RNG objects initialized
with the same child seed. Each later game reconstructs its RNGs directly from the
master and its index, never from a mutable batch-wide stream.

Child seeds are deterministic coordinates under one future registered master seed,
not rerolls or independently selectable experimental seeds.

## Frozen derivation vectors

Fixture master integer `1` is a format regression value, not an assigned or consumed
experimental seed:

- index 0: `0x5D971FF74224A405`;
- index 1: `0x592C610D76160D7D`;
- index 2: `0xE076DFA63A9DF208`.

## Deterministic isolation proof

- Control and policy RNGs produce identical initial 40-item shuffles for a game.
- An extra policy-only 31-item search shuffle separates the two RNG states inside
  that game, as real post-intervention play requires.
- Reconstructing the next game produces identical control and policy shuffles,
  unaffected by the prior policy-only random consumption.
- Reconstructing that game again produces the same shuffle.
- Six invalid seed-coordinate fixtures and four invalid sample-count fixtures fail
  closed.

The paired iterator exists for a later contract but was not consumed here. These are
RNG primitive regressions, not sampled games or outcome observations.

## Boundary

This gate resolves the Phase-6 cross-game coupling blocker. It does not yet freeze
paired comparison metrics, an output schema, an artifact auditor, a sample count, or
a master seed. Therefore it authorizes no pilot or workflow execution.

The next gate must define the paired estimands and fail-closed output contract before
any fresh seed can be assigned.

## Validation result

The dedicated validator passed control identity, the frozen derivation vectors,
same-game initial equality, within-game post-intervention divergence, next-game
isolation, reproducibility, and all invalid-input rejections. Phase-5 and Phase-6
deterministic fixtures remained green.

The validator reported `cross_game_rng_coupling=0`, `sampled_games=0`,
`seeds_consumed=0`, `pilot_authorized=0`, and `outcome_claims=0`.

Disposition: `V09_PHASE7_SEED_FREE_VALIDATED`
