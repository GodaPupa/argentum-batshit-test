# v0.9 Position 1 — Simultaneous Source-Damage Grouping Gate

Disposition: `PROSPECTIVE_SEED_FREE_QUALIFICATION`

This successor to accepted Batch AO addresses the explicit frozen-pair blocker for one damage
instruction that deals damage from the same source to more than one recipient. It does not change
Izzet Science?! v0.7, Veteran Beastrider, any pilot, or any official seed/game counter.

## Frozen boundaries

- Izzet Science?! v0.7 SHA-256 remains
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Veteran Beastrider remains
  `c3ab6ee8e37a78219e7bbb7c6a75c634d78c1eb4965ab0db3abd5d853398b0be`.
- Batch AO remains accepted for registry/capability scope only.
- The seven unresolved registry identities remain unchanged by this gate.
- Official gameplay remains 0/12 with zero outcome exposure. KEEP_V07 / NO CARD CHANGES.

## Qualified interaction

Ram Through's excess-damage self-replacement may split one source event into damage to the creature
and damage to its controller. The engine now marks only those actual DamageDealtEvent records with a
local ordered group. No random/global group identifier is introduced. Ordinary damage records retain
their prior serialized defaults.

Lifelink is deferred across the split and applied once to the total damage actually dealt, after
replacement/prevention. Unrestricted ATTACHED DealsDamageEvent triggers consume the marked source
event once and expose the combined damage amount. Recipient-side DamageReceivedEvent triggers and
recipient-filtered source triggers remain per recipient and are not coalesced.

The scope is deliberately narrow: it qualifies the exact Ram Through + lifelink and Ram Through +
Spirit Link/Armadillo Cloak event shape identified by Batch AO. It does not infer grouping for
unmarked damage, merge distinct sequential damage instructions, or alter ANY-bound batch observers.

## Required deterministic qualification

The exact Ram Through scenario must prove the 2+3 split remains two recipient records in one marked
source group, native lifelink emits one five-life gain event, and Spirit Link resolves once for five.
Existing Ram Through legality/response cases, Spirit Link shape, excess-damage rails, damage ledger,
real-engine readiness, and full CI remain required.

No official iterator, claim, seed, allocation, game, or outcome is touched. A green workflow is
capability evidence only; independent source/artifact review is required before the readiness guard
may be opened.
