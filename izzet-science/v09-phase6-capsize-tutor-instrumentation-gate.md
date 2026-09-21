# v0.9 Commander-Independent Readiness — Phase 6 Capsize Tutor Instrumentation Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Question

Can the validated Phase-5 Capsize tutor policy be activated explicitly inside a
trajectory and emit fail-closed acquisition telemetry without changing the default
control path?

## Activation point

The optional arm runs after the existing selection and primary-tutor window and
before commander deployment or optional mana-rock development. It runs only when
the frozen primary tutor policy selected no action. The default is off.

An enabled row reports four Boolean fields:

- `capsize_tutor_used`;
- `capsize_tutor_found`;
- `capsize_scroll_used`;
- `capsize_drift_used`.

Every row must satisfy:

`used == found == scroll + drift`

Merchant Scroll and Drift are mutually exclusive, and a primary tutor and Capsize
tutor may not execute in the same action window.

## Deterministic fixtures

- Calling the simulator normally and calling it with the policy explicitly disabled
  produce exactly identical rows and no Phase-6 fields.
- A fixed, nonrandom ordered-deck fixture keeps Capsize in the library and makes
  Merchant Scroll payable on turn two. The enabled arm executes exactly one search,
  acquires Capsize, and emits exactly one mutually consistent event.
- Four adversarial aggregates are rejected for event-identity mismatch, tutor-sum
  mismatch, out-of-range counts, or Boolean values masquerading as integer counts.
- Phase-5 legal-target, exact-payment, primary-precedence, tie-break, zone-movement,
  and transactional-failure fixtures remain green.

The forced fixture is a regression test, not a sampled game or outcome observation.

## Sampling boundary

No pilot is authorized by this gate. The current batch sampler advances one shared
random stream across games. A policy search legitimately shuffles its current
library, but that additional random consumption could also change the initial deck
orders of later games in the batch. That cross-game coupling would contaminate a
paired control-versus-policy comparison.

The next gate must isolate randomness per game and prove that paired arms begin each
game from the same shuffled deck while allowing only post-intervention within-game
divergence. A seed and sampled contract may be considered only after that proof.

## Validation result

The dedicated validator passed control identity, default-path equivalence, explicit
activation, single-event acquisition, mutual exclusion, and telemetry invariants.
All four adversarial aggregate fixtures were rejected, and adjacent deterministic
policy fixtures remained green.

The validator reported `default_trajectory_changes=0`, `sampled_games=0`,
`seeds_consumed=0`, `paired_sampling_authorized=0`, and `outcome_claims=0`.

Disposition: `V09_PHASE6_SEED_FREE_VALIDATED`
