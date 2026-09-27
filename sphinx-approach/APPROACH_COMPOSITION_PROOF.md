# Sphinx's Approach — composition proof and implementation decision

Status: EXISTING_ENGINE_COMPOSITION_SELECTED
Supersedes: provisional new-primitive recommendation in ATOMIC_TRANSACTION_FEATURE_CONTRACT.md
Evidence class: seedless implementation analysis
Official Stage-E games/seeds/outcomes: 0 / 0 / 0

## Result

A new atomic zone executor is **not currently justified**.

The live engine already has every required generic operation, and resolution semantics remove the apparent
partial-payment race that motivated the provisional design:

- `CardSource.Self` gathers the resolving spell/ability source regardless of its current zone.
- `CardSource.FromZone(GRAVEYARD, ..., GameObjectFilter.Any.named("Sphinx's Approach"))` gathers only
  the other eligible copies.
- `FeasibilityCheck.HasCardsInZone(..., count = 4)` can gate the optional decision before any move.
- `SelectionMode.ChooseExactly(4)` can choose the four copies when more than four qualify.
- `MoveCollectionEffect` uses the canonical zone-transition path and emits normal zone-change events.
- `LibraryPatterns.searchLibrary(..., SearchDestination.BATTLEFIELD, shuffleAfter = true)` supplies
  the payoff search.
- No player receives priority during the resolution of these pipeline steps. Once feasibility has passed
  and the exact-four choice has been made, an opponent cannot remove one of those cards between the
  graveyard move and the source move.

## Important executor finding

`SelectFromCollectionExecutor` intentionally clamps `ChooseExactly(N)` to the number of eligible
cards. Therefore **ChooseExactly(4) alone is not sufficient**: with only three eligible cards it would
select all three.

For Approach, the outer optional gate MUST use
`FeasibilityCheck.HasCardsInZone(Zone.GRAVEYARD, namedApproachFilter, count = 4)`.
That feasibility requirement is load-bearing and receives a deterministic three-vs-four regression.

## Proposed rules-faithful pipeline

After the mandatory DrawCards(2):

1. Optional `MayEffect` with the exact-four graveyard feasibility check.
2. Gather all cards named Sphinx's Approach from the controller's graveyard as `eligibleApproaches`.
3. Select exactly four as `approachesToExile`.
4. Gather `CardSource.Self` as `resolvingApproach`.
5. Move `approachesToExile` to exile.
6. Move `resolvingApproach` to exile.
7. Search the controller's library for up to one Sphinx creature card, put it onto the battlefield,
   then shuffle.

The source move occurs before normal spell-resolution cleanup. The normal resolver must therefore see
that the source card has already left the stack and must not move it to the graveyard a second time.
That behavior is a mandatory deterministic fixture before qualification.

## Why this is preferable

- no card-name knowledge in the engine;
- no new serialized effect type;
- no new executor or continuation;
- no new pause/resume protocol;
- uses the same zone-transition/event machinery as the rest of the corpus;
- the only card-specific behavior remains in the card definition where it belongs.

## Qualification requirements

The previously frozen Approach fixture matrix remains in force, with these additional composition-specific
checks:

- three graveyard copies after Draw 2: May gate is infeasible and no exile occurs;
- four copies: gate is feasible;
- five or more: player chooses exactly four, never all by clamping;
- declining the May gate leaves all graveyard copies and lets normal resolution put the source in graveyard;
- accepting moves exactly four graveyard copies plus the source to exile;
- normal spell cleanup does not double-move an already-exiled source;
- all five zone changes use ordinary zone-change events;
- Terror's generic cost is recomputed immediately after the four graveyard instants leave.

If any of those fail under the real engine, return to the generic primitive design rather than patching
the card with a Sphinx-specific shortcut.
