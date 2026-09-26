# Sphinx's Approach — Stage E card-support audit

Status: NARROW_ENGINE_FEATURE_REQUIRED
Evidence class: seedless readiness only
Official Stage-E games: 0
Official Stage-E seeds: 0

## Current registry

Present in the live engine:
- Tolarian Terror — canonical definition with instant/sorcery graveyard cost reduction and Ward {2}.
- Mental Note — mill two, then draw one.
- Thought Scour — target player mills two, then draw one.
- Spell Pierce — counter target noncreature spell unless its controller pays {2}.
- Counterspell and basic Island are part of the existing corpus.

Absent from the live engine:
- Sphinx's Approach.
- Goliath Sphinx.
- Snap (the Urza's Legacy instant).

The missing simple identities are not the main risk. Goliath Sphinx is a vanilla flying creature beyond
Flying; Snap is a bounce-plus-untap spell. Both still require normal canonical-printing verification and
their own card support before an official deck may instantiate.

## Verified Sphinx's Approach rules shape

Prospective research continues to use the verified FRA common printing:
- {1}{U}{U}, Instant.
- Draw two cards.
- Then the controller may exile the resolving spell and four cards named Sphinx's Approach from their
  graveyard.
- Only if that optional action succeeds does the controller search their library for a Sphinx creature
  card, put it onto the battlefield, then shuffle.
- The deck-construction exception permits any number of cards named Sphinx's Approach.

The common printing is prerelease/future-set research at this checkpoint; sanctioned legality is not
treated as established before the effective release/legality date.

## Existing reusable primitives

The current SDK already has:
- DrawCards.
- Gather/Move zone pipelines.
- filtered library search.
- SearchDestination.BATTLEFIELD.
- library shuffle and search events.
- GatedEffect / Gate.MayDecide and Gate.MayPay.
- exact graveyard-sensitive Tolarian Terror cost reduction.
- real stack resolution and countering.

## Narrow unresolved feature

No existing card definition or audited composition has yet demonstrated the exact Approach transaction:

1. the resolving spell remains on the stack while the mandatory draw-two occurs;
2. the optional action must simultaneously require the resolving spell plus exactly four OTHER cards
   named Sphinx's Approach in its controller's graveyard;
3. declining performs none of that optional action;
4. an impossible payment/action must not partially exile cards;
5. success moves the resolving spell from the stack and the four named graveyard cards to exile;
6. only success proceeds to a filtered Sphinx search -> battlefield -> shuffle;
7. there is no priority window between the draw and the optional action/search sequence.

This must be implemented through a reusable, rules-faithful primitive/composition and deterministic
scenario coverage. A Sphinx-specific hard-coded engine shortcut is not acceptable.

## Required deterministic fixtures

Before Stage-E deck freeze:
- three eligible graveyard copies: optional transaction unavailable;
- four eligible graveyard copies: available;
- resolving spell never counts as one of the four graveyard cards;
- mandatory draw happens before feasibility/decision;
- draw-two can remove the last Sphinx target without undoing the legal exile action;
- decline leaves the resolving spell to normal post-resolution destination and leaves graveyard copies;
- successful action exiles the resolving spell and exactly four named graveyard cards;
- no Sphinx in library: successful exile action still searches/shuffles and finds none;
- countered Approach performs neither draw nor optional action;
- graveyard interaction before resolution can make the optional action unavailable;
- searched creature enters the battlefield without being cast;
- summoning sickness / opponent-end-step deployment timing remains normal;
- zone-card conservation and event emission remain exact.

Until these pass, reconstructed v0.1 and hybrid are configuration records only, not qualified interactive decks.


## Composition-first correction — 2026-09-25

A deeper SDK audit found that a new engine primitive is **not yet justified**.

`CardSource.Self` is explicitly defined to gather the spell/ability's own source card regardless of its
current zone. The generic pipeline can therefore reference the resolving Approach while it is still on the
stack. `CardSource.FromZone(GRAVEYARD, ..., filter)`, `SelectFromCollection(ChooseExactly(4))`, and
`MoveCollection(EXILE)` already cover the four named graveyard cards and both exile moves. The library
pattern already covers filtered Sphinx search -> battlefield -> shuffle.

The SDK documentation also explicitly warns that `ChooseExactly(N)` clamps when fewer than N eligible
cards exist and instructs authors to gate all-or-nothing effects on an eligibility/count precondition before
moving anything. That warning matches Approach's requirement exactly.

Revised preferred implementation shape:

1. Draw two cards (mandatory).
2. Evaluate the optional action's feasibility *after* those draws.
3. If at least four OTHER cards named Sphinx's Approach are in the controller's graveyard, offer the may
   decision; otherwise skip the impossible decision.
4. On acceptance: gather eligible graveyard Approaches, choose exactly four, gather `CardSource.Self`,
   move the four and self to exile, then execute filtered Sphinx search -> battlefield -> shuffle.
5. On decline: do none of the optional pipeline; normal spell-resolution cleanup sends the resolving spell
   to its ordinary destination.
6. Countering the spell prevents the entire spell effect from resolving.

This composition must still be proven by deterministic engine scenarios, especially that
`MoveCollection` can move `CardSource.Self` from the resolving stack without the normal post-resolution
cleanup moving it again, and that the feasibility check can express a filtered graveyard count of four at
the correct post-draw resolution point.

Until those fixtures pass, this is a design finding rather than qualified card support.
