# v0.7-B Prismatic Lens — Frozen Challenger Gate

Control: `izzet-science/v0.6-control.md`
Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
Challenger: `izzet-science/challengers/v07B-prismatic-lens.md`
Change: `-1 Lose Focus, +1 Prismatic Lens`

Repository legality data identifies Prismatic Lens as Pauper Commander legal. The
model gives it its exact mana modes: it may tap for one colorless mana, or tap and
consume one mana from another source to produce one blue or red mana. Filtering
recolors mana and never increases the pool.

## Hypothesis

The accepted phase-1 pilot identified immediate commander recovery as the largest
measured T10 interaction-readiness gap. Replacing a conditional two-mana counter
with a two-mana untapped mana rock may increase the probability of paying one
commander-tax increment and launching the primary combo on the same turn. It also
adds a nonland source for Dramatic Reversal. The replacement is acceptable only if
the recovery gain pays for the conditional protection readiness it removes and the
frozen combo, color, and secondary-engine guardrails hold.

## Frozen execution

- Paired control and challenger trajectories
- Pilot samples: 10,000 per deck
- Pilot seed: `0x1A22E7004`
- Confirmation samples: 100,000 per deck, only after pilot pass
- Confirmation seed: `0x1A22E7005`
- Horizon: T1–T10
- One execution per stage; no rerolls or replacement seeds

The 10,000-game pilot cannot promote the challenger.

## Pilot pass criteria

At T10, all conditions must hold:

1. Immediate taxed commander recovery improves by at least 0.50 percentage points.
2. The recovery gain is at least as large as the loss in conditional stack
   protection readiness. These opportunity metrics may overlap and are not added.
3. Generic guaranteed stack protection does not decrease by more than 0.10
   percentage points.
4. Generic guaranteed targeted-removal protection does not decrease by more than
   0.10 percentage points.
5. Current-turn lethal state does not decrease by more than 0.10 percentage points.
6. Pair assembly does not decrease by more than 0.10 percentage points.
7. U and R execution guardrails do not decrease by more than 0.25 percentage points.
8. Dramatic Reversal positive-mana readiness does not decrease.
9. Deck identity, output completeness, source, seed, sample count, and artifact
   hashes all validate.

Failure of any criterion rejects v0.7-B and permanently ends this challenger
identity. A pass authorizes exactly one 100,000-game confirmation on the
preregistered seed; only that confirmation may earn promotion.

Disposition: `V07B_PILOT_REJECTED`

Official rejection evidence: `izzet-science/v07B-prismatic-lens-rejected.md`.
The 100,000-game confirmation was not authorized and was not run.
