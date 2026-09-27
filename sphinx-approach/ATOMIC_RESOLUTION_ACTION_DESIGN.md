# Sphinx's Approach — atomic resolution-action design

Status: DESIGN_FROZEN_BEFORE_IMPLEMENTATION
Evidence class: seedless engine readiness
Official Stage-E seeds/games/outcomes: 0 / 0 / 0

## Decision

Do not add a Sphinx-specific executor.

The current SDK already exposes:
- `CardSource.Self`, which can gather the resolving source card while it is on the stack;
- `CardSource.FromZone` with a card filter;
- collection selection and zone moves;
- `GatedEffect` / `Gate.DoAction`, whose success is evaluated after an action drains;
- resolution-time graveyard-payment precedent in `CollectEvidenceEffect`;
- filtered library search to `SearchDestination.BATTLEFIELD` plus shuffle.

The missing vocabulary is therefore a small reusable **atomic zone-card action/payment**: choose exactly N
cards matching a filter from a specified zone, optionally include the resolving source card as an additional
required object, and move the complete required set to one destination. If the full requirement cannot be
met or the chooser declines, move nothing. The action must expose success/failure to ordinary
`Gate.DoAction` / success-criterion machinery.

## Required semantics

For the initial use:
- controller chooses exactly 4 cards;
- source zone = controller graveyard;
- filter = cards named "Sphinx's Approach";
- required extra object = source card on the stack;
- destination = exile;
- action is feasible only when all five objects can legally move;
- success is all-or-nothing;
- the resolving source is not part of the four-card graveyard count;
- no priority window is introduced by the decision/payment;
- on success, downstream effect composition performs the Sphinx search;
- on failure/decline, downstream search does not run and normal spell cleanup applies.

## Reusability bar

The primitive must not contain the words Sphinx, Approach, Goliath, or a hard-coded count of four in its
engine implementation. Parameters must carry zone, player, filter, exact count, source inclusion, and
destination.

## Regression boundary

Add engine-level tests for:
1. exact-N feasibility;
2. N-1 fails without moving anything;
3. source is counted separately from selected zone cards;
4. decline leaves every card in place;
5. exact selection moves all required cards atomically;
6. a selected card becoming unavailable before completion cannot produce a partial payment;
7. action-success gate runs payoff only after complete success;
8. source already absent from its required zone fails closed;
9. card conservation and emitted zone-change events.

Then add the card-level Approach scenarios from `STAGE_E_CARD_SUPPORT_AUDIT.md`.

This design is frozen before implementation tests/outcomes. If implementation proves the abstraction cannot
faithfully satisfy these semantics, stop and revise the design explicitly rather than weakening a fixture.
