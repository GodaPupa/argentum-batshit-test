# v0.9 Commander-Independent Readiness — Phase 5 Capsize Tutor Policy Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Question

Can the accepted control execute a deterministic Capsize acquisition policy with
its existing tutors while preserving the frozen primary-combo policy?

## Frozen policy arm

The Phase-5 arm is dormant: the default simulator does not call it. When explicitly
selected, it applies this order:

1. If the existing primary-combo tutor policy selects any payable search for a
   missing Lava Spike or Desperate Ritual, do not search for Capsize.
2. Capsize must still be in the library and the chosen tutor's exact cost must be
   payable from ready mana.
3. Prefer Merchant Scroll over Drift of Phantasms because its search costs two mana
   rather than three; use Drift only when Scroll is unavailable or unpayable.
4. Search only for Capsize. Dizzy Spell and Muddle the Mixture are never Capsize
   routes under the legal target model.

No commander-state trigger or projected win claim is inferred. This gate isolates
legal acquisition and primary-policy precedence before any sampled policy trial.

## Deterministic fixtures

- A payable Dizzy Spell search for missing Lava Spike blocks an otherwise payable
  Merchant Scroll search for Capsize.
- With both Capsize tutors in hand and three Islands ready, Merchant Scroll wins the
  deterministic tie-break; Drift is selected when Scroll is absent.
- Two Islands pay for Merchant Scroll but not Drift's three-mana transmute cost.
- Missing Capsize, an illegal declared target, or insufficient mana fails without
  hand, library, or mana mutation.
- Successful execution consumes exactly one tutor, taps the exact two Islands for
  Merchant Scroll, removes Capsize from the library, and puts it into hand.

## Boundary

This is executable semantics, not a sampled challenger. It changes no deck card,
does not alter default trajectories, authorizes no seed, and makes no access,
readiness, or win-rate claim. A later gate must freeze telemetry and an experimental
contract before this policy may run over sampled games.

## Validation result

The dedicated validator passed control identity, legal-target, exact-payment,
primary-precedence, deterministic tie-break, successful zone movement, and
transactional failure regressions. Adjacent deterministic validators remained
green.

The validator reported `default_trajectory_changes=0`, `sampled_games=0`,
`seeds_consumed=0`, and `outcome_claims=0`.

Disposition: `V09_PHASE5_SEED_FREE_VALIDATED`
