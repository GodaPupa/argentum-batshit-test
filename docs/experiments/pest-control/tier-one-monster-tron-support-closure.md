# Pest Control Tier-1 coverage — Monster Tron support closure

## Scope

This gate closes every preboard card-support gap from the accepted Monster Tron admission audit that
can be represented faithfully with already-supported engine primitives.

It does not add Prototype, generate seeds, initialize games, submit actions, expose outcomes,
sideboard, tune Pest Control, or authorize gameplay.

Accepted opponent identity remains:

- pilot: `mehanske`
- protocol: `PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`
- main SHA-256: `79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f`
- sideboard SHA-256: `af793fd3c1b39104f8d7f880cec010e8c0ab3233d68c552e60bb8f4455b67be5`
- 75 SHA-256: `f2ed1d8a0bc55e66a815ff0bb7c0219965089ab1f401a2cc14eee17105717e2a`

## Shared definitions added

The following exact maindeck identities are added to the shared catalog:

- Ancient Stirrings
- Crop Rotation
- Unfathomable Truths
- Bonder's Ornament
- Bojuka Bog
- Haunted Fengraf

Crop Rotation, Bonder's Ornament, and Bojuka Bog reuse accepted implementation shapes already
validated on the isolated `industrial-waste/lab` branch. Pest Control does not import the
Industrial Waste pilot, seeds, results, or deck-specific policy.

Ancient Stirrings uses the shared top-N filtered-selection pipeline, including lands as colorless
eligible cards and controller-chosen bottom ordering.

Unfathomable Truths uses the existing Devoid characteristic and predefined Eldrazi Spawn token.

Haunted Fengraf uses the engine's no-player-choice random collection selector after gathering
creature cards from its controller's graveyard.

## Remaining blocker

Status: `PREBOARD_SUPPORT_BLOCKED_ON_PROTOTYPE`

After this gate, the only unresolved preboard identity must be:

`Boulderbranch Golem:2`

Boulderbranch Golem cannot be represented faithfully yet because the shared SDK currently has no
Prototype keyword/ability model. Prototype changes mana cost, color, power/toughness and mana value
on the stack and battlefield when the alternate characteristics are used; a card-specific
alternative-cost approximation would not be accepted.

The dedicated test therefore requires both:

- exact unresolved maindeck = Boulderbranch Golem only; and
- no `PROTOTYPE` keyword in the shared SDK.

## Counters

- official games authorized: `0`
- official seeds generated: `0`
- games initialized: `0`
- actions submitted: `0`
- outcomes exposed: `0`

## Next justified gate

Implement Prototype as a shared engine/SDK mechanic with deterministic mechanic tests and a focused
Boulderbranch Golem scenario. Only after that gate is green may Monster Tron advance to seedless
rules/policy readiness.
