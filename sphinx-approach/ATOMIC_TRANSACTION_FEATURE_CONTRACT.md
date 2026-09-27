# Sphinx's Approach — atomic transaction feature contract

Status: DESIGN_FROZEN_FOR_IMPLEMENTATION
Scope: reusable engine vocabulary; seedless
Official Stage-E games/seeds/outcomes: 0 / 0 / 0

## Why a new primitive is justified

The current pipeline vocabulary already exposes `CardSource.Self`, filtered `CardSource.FromZone`,
collection selection/movement, and `Gate.DoAction` success gating. However, composing those existing
steps naively would allow a partial zone move before discovering that the whole printed action cannot
be completed. Sphinx's Approach requires one indivisible optional action spanning two source zones:
the resolving spell on the stack and exactly four other named cards in the graveyard.

The engine also already distinguishes ordinary after-resolution replacement destinations from effects
that move a resolving spell during its own resolution. Stamping a generic after-resolution EXILE rider
would be incorrect: declining Approach must allow the spell to go to the graveyard normally, while
accepting the action moves it to exile immediately as part of the printed action.

## Reusable vocabulary

Add a generic effect tentatively named `MoveSourceAndExactCardsEffect` (final repository naming may
follow local conventions) with data-only parameters:

- sourceRequiredZone
- sourceDestination
- additionalSourceZone
- additionalFilter
- exactAdditionalCount
- additionalDestination
- storeMovedAs (optional)

Semantics:

1. Resolve the ability source and verify it is still in `sourceRequiredZone`.
2. Enumerate legal additional cards in the requested zone for the effect controller using projected/
   zone-correct card data.
3. If fewer than `exactAdditionalCount` exist, the action is infeasible and moves nothing.
4. If more than the exact count exist, prompt the controller to choose exactly that many; no cards move
   until the choice is complete.
5. Commit the source move and all chosen additional moves as one action-resolution transaction.
6. Report action success only when the source and exact additional count all moved successfully.
7. Emit ordinary zone-change events for every moved card; do not create a Sphinx-specific event.
8. A `Gate.DoAction` around this primitive may use its explicit success criterion to run a downstream
   payoff only on success.

This primitive is intentionally general enough for future "exile this spell/card and N cards matching X"
resolution actions. It must not know the name Sphinx's Approach or the Sphinx creature type.

## Approach composition after the primitive exists

Spell effect:
1. Draw two cards.
2. `GatedEffect(Gate.MayDecide(...), then = GatedEffect(Gate.DoAction(atomic move), then = search))`
   or the equivalent existing facade that preserves one optional prompt and action-success semantics.
3. Search uses the existing library pipeline filtered to Sphinx creature cards,
   `SearchDestination.BATTLEFIELD`, count 1, shuffle true.

The feasibility check should suppress/disable an impossible optional action rather than offer a choice
that can only partially execute.

## Deterministic acceptance fixtures

The feature itself:
- insufficient additional cards -> no move;
- exact count -> all requested objects move;
- excess candidates -> exactly N chosen and moved;
- source absent from required zone -> no move;
- interrupted/invalid selection -> no partial move;
- zone-change event/card conservation across both source zones.

Approach:
- mandatory draw precedes optional feasibility;
- three graveyard Approaches after draw -> no transaction;
- four after draw -> transaction available;
- resolving Approach never satisfies one of the four;
- decline -> source resolves to graveyard normally and four remain;
- accept -> source + exactly four graveyard Approaches exile;
- search only after successful transaction;
- zero legal Sphinx targets -> exile still happened, search finds none, library shuffles;
- draw removes final Sphinx target -> same behavior;
- countered spell -> no draw/action/search;
- graveyard interaction before resolution can remove feasibility;
- found Sphinx enters rather than being cast;
- normal summoning sickness and opponent-end-step timing;
- Terror cost immediately recalculates after the four graveyard cards leave.

No Stage-E vector may be generated until these pass.
