# v0.9 Commander-Independent Readiness — Phase 3 Tutor Opportunity Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Question

How often could an existing in-hand tutor legally and presently find a declared
backup card, without changing the frozen primary-combo tutor policy?

## Frozen observations

At the same post-land, pre-spend action window as the accepted readiness baseline,
each declared backup card receives three passive Boolean fields:

- `targetable`: the backup card is in the library and a compatible tutor is in hand;
- `payable`: at least one such tutor's full cost can be paid from exact ready mana;
- `uncontested`: at least one payable compatible tutor cannot also find a missing
  Lava Spike or Desperate Ritual.

The required ordering is `uncontested <= payable <= targetable`. Murmuring Mystic's
three fields must always be false because the accepted control has no compatible
tutor.

## Deterministic fixtures

- Three Islands make Dizzy Spell's Rolling Thunder and Kaervek's Torch searches
  payable, but they remain contested while a library Lava Spike is missing from hand.
- The same searches become uncontested once Lava Spike and Desperate Ritual are both
  in hand.
- Merchant Scroll can find Capsize with two Islands; Drift of Phantasms alone cannot
  transmute for Capsize until a third mana is ready.
- Removing an outlet from the library removes its targetable observation.
- Instrumented and uninstrumented deterministic trajectories have exactly identical
  legacy row data.

## Boundary

This instrumentation knows the simulated library only to determine whether a search
can legally succeed. It does not reveal hidden information to the policy, spend a
tutor, alter a library, select a backup line, or claim increased access or wins.
No pilot, seed, or sample is authorized until this seed-free gate passes and a fresh
pilot identity is separately frozen.

## Validation result

The dedicated validator passed the legal-target, exact-payment, primary-priority,
trajectory-equivalence, and aggregate subset regressions. Phase 2 connectivity,
Phase 1 readiness, and the multiplayer kill validators also remained green.

The validator reported `legacy_trajectory_changes=0`, `tutor_executions=0`,
`policy_changes=0`, `sampled_games=0`, `seeds_consumed=0`, and `outcome_claims=0`.

Disposition: `V09_PHASE3_SEED_FREE_VALIDATED`
