# v0.7-A Pyroblast — Frozen Challenger Gate

Control: `izzet-science/v0.6-control.md`
Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
Challenger: `izzet-science/challengers/v07A-pyroblast.md`
Change: `-1 Lose Focus, +1 Pyroblast`

Repository legality data identifies Pyroblast as Pauper Commander legal. It costs one
red mana and is credited as guaranteed protection only when the hostile spell is
explicitly blue. It receives no credit against the color-unknown stack-counter or
targeted-removal events.

## Hypothesis

Replacing a two-mana conditional counter with one-mana color-specific hard protection
will increase protected primary-combo launches against blue countermagic. Because both
cards occupy the interaction package, ordinary combo assembly and lethal timing should
remain effectively unchanged.

## Frozen execution

- Paired control and challenger trajectories
- Pilot samples: 10,000 per deck
- Pilot seed: `0x1A22E7002`
- Confirmation samples: 100,000 per deck, only after pilot pass
- Confirmation seed: `0x1A22E7003`
- Horizon: T1–T10
- One execution per stage; no rerolls or replacement seeds

The 10,000-game pilot cannot promote the challenger.

## Pilot pass criteria

At T10, all conditions must hold:

1. Guaranteed protection against a blue instant stack counter improves by at least
   0.50 percentage points.
2. Generic color-unknown stack protection does not decrease.
3. Generic targeted-removal protection does not decrease.
4. The blue guaranteed-protection gain is at least as large as the loss in conditional
   protection readiness.
5. Current-turn lethal state does not decrease by more than 0.10 percentage points.
6. Pair assembly does not decrease by more than 0.10 percentage points.
7. U and R execution guardrails do not decrease by more than 0.25 percentage points.
8. Deck identity, output completeness, source, seed, sample count, and artifact hashes
   all validate.

Failure of any criterion rejects v0.7-A and permanently ends this challenger identity.
A pass authorizes exactly one 100,000-game confirmation on the preregistered seed; only
that confirmation may earn promotion.

Disposition: `V07A_PILOT_REJECTED`

Official rejection evidence: `izzet-science/v07A-pyroblast-rejected.md`.
The 100,000-game confirmation was not authorized and was not run.
