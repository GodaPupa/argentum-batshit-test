# v0.9 Position 1 — Simultaneous Source-Damage Grouping Gate

Disposition: `ACCEPTED_SEED_FREE_CAPABILITY`

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


## Accepted qualification

Run `36155027033` qualified exact source
`3269007734128082fe7e582b4c824a410b0540c8`. Artifact
`10873586790` contains the declared six files and has GitHub/downloaded ZIP SHA-256
`a3f9ff19a04fe254fde7f75c17abf36f0e73c41a4ad4b2e7bd44c899dee95b6c`.
Independent audit found no failed-build, failed-test, or nonzero-error marker in the five required
transcripts; Ram Through, Spirit Link, excess-damage, damage-ledger, and readiness transcripts all
completed successfully. The manifest binds the frozen control/opponent, Batch AO predecessor,
grouping sources/tests, exact source SHA, and zero official seed/game/outcome counters.

Disposition: **ACCEPTED FOR THE EXACT SIMULTANEOUS-SOURCE DAMAGE/LIFELINK AND UNRESTRICTED
ATTACHED DEALS-DAMAGE SCOPE ONLY**. This removes that one readiness blocker. The seven unresolved
Veteran Beastrider identities and the five PDH/full-game blockers remain. Official gameplay remains
**0/12**, outcome exposure remains zero, and **KEEP_V07 / NO CARD CHANGES** remains in force.
