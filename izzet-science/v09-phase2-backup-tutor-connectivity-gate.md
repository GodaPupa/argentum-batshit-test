# v0.9 Commander-Independent Readiness — Phase 2 Tutor Connectivity Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Question

Which declared commander-independent cards can the accepted control's existing
tutors legally find, before any tutor-use policy or sampled experiment changes?

## Frozen semantics

Transmute matches the target card's mana value. An `X` in a library card's mana
cost contributes zero, so Rolling Thunder and Kaervek's Torch each have mana value
one there. Merchant Scroll can find a blue instant. The resulting exact map is:

| Existing tutor | Declared backup targets |
|---|---|
| Muddle the Mixture | none |
| Dizzy Spell | Rolling Thunder; Kaervek's Torch |
| Drift of Phantasms | Capsize |
| Merchant Scroll | Capsize |

No existing tutor in the control can find Murmuring Mystic.

## Required deterministic regressions

- Each tutor returns exactly the connectivity above when all four backup cards are
  in the library.
- Dizzy Spell still selects a missing Lava Spike ahead of either legal backup
  target.
- With both primary combo pieces already present, the current policy does not spend
  Dizzy Spell even though a backup target is legal.
- Accepted-control identity and hash are exact, and all eight audited cards are
  present in the deck.

## Boundary

This is passive legal connectivity only. It does not execute a tutor for a backup
card, alter action ordering, measure effective access, or claim a win. Any future
policy experiment must first freeze when the opportunity cost of spending a tutor
on a backup card is acceptable and must preserve a primary-pair control arm.

## Validation result

The dedicated validator passed the exact connectivity, primary-pair precedence, and
no-spend policy regressions. It also rechecked tutor execution, the 99-card control
identity, and the frozen control hash. The adjacent Phase-1 readiness and multiplayer
kill validators remained green.

The validator reported `policy_changes=0`, `sampled_games=0`, `seeds_consumed=0`,
and `outcome_claims=0`.

Disposition: `V09_PHASE2_SEED_FREE_VALIDATED`
