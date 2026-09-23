# Pest Control Tier-1 coverage — Monster Tron admission

## Scope

This gate freezes the next uncovered preboard opponent identity after accepted Mono Red Madness,
Grixis Affinity and Mono-Blue Terror evidence. It performs identity validation and a read-only card
registry support audit only.

It does **not** generate seeds, initialize a game, submit an action, expose an outcome, sideboard,
tune Pest Control, or authorize gameplay.

## Opponent selection

Pilot: `mehanske`

Event: `MTGO Pauper Challenge 32 #12854527`

Date: `2026-09-21`

Field: `64 players`

Finish: `Top 8; 5-2`

Event source:
`https://mtgdecks.net/Pauper/mtgo-pauper-challenge-32-12854527-tournament-270818`

Decklist source:
`https://decksnipe.com/archetype/pauper/green-tron`

This identity was selected because it is a recent successful MTGO Challenge result in a large field
and preserves one concrete registered 75 rather than averaging competing Monster Tron branches.

Protocol:
`PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

## Frozen opponent maindeck — 60

```text
2 Rooftop Percher
2 Generous Ent
2 Boulderbranch Golem
4 Bramble Wurm
4 Maelstrom Colossus
4 Ancient Stirrings
3 Crop Rotation
2 Breath Weapon
2 Unfathomable Truths
1 Barrels of Blasting Jelly
2 Candy Trail
4 Expedition Map
4 Giant's Boulder
2 Bonder's Ornament
4 Pinnacle Kill-Ship
1 Bojuka Bog
2 Conduit Pylons
2 Forest
1 Haunted Fengraf
4 Urza's Mine
4 Urza's Power Plant
4 Urza's Tower
```

Canonical main SHA-256:
`79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f`

## Frozen opponent sideboard — 15

The sideboard is identity-only in this gate and is not instantiated.

```text
2 Blue Elemental Blast
2 Hydroblast
1 Kaervek's Torch
1 Pyroblast
4 Relic of Progenitus
2 Call Damage Control
1 Breath Weapon
2 Scour from Existence
```

Canonical sideboard SHA-256:
`af793fd3c1b39104f8d7f880cec010e8c0ab3233d68c552e60bb8f4455b67be5`

Canonical complete-75 SHA-256:
`f2ed1d8a0bc55e66a815ff0bb7c0219965089ab1f401a2cc14eee17105717e2a`

Hashes use the same ordered `Name,count` row convention and `MAIN` / `SIDEBOARD` complete-75
envelope already used by the accepted Pest Control Mono-Blue Terror readiness gate.

## Pest identity

Pest Control v1.0 remains the permanent frozen control.

Main SHA-256:
`7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`

Qualified production runner identity remains:
`9829ee98869343cd48dceaa9a27c56ed27c6b3bc`

No Pest card is changed by this gate.

## Admission state

Status: `IDENTITY_FROZEN_SUPPORT_AUDIT_PENDING`

The runner remains disabled. Official counters remain:

- games authorized: `0`;
- seeds generated: `0`;
- games initialized: `0`;
- actions submitted: `0`;
- outcomes exposed: `0`.

The dedicated pull-request workflow registers the live `main` card catalog and records exact
unresolved maindeck and sideboard identities in a support-audit artifact. Missing cards are evidence
for a later implementation/readiness gate; they are not substituted, approximated or silently
removed here.

## Next decision

If the exact maindeck is already fully supported, advance to seedless rules/policy readiness for this
specific 60.

If maindeck identities are unresolved, implement and validate only the missing preboard cards and
required general mechanics before any gameplay authorization.

Sideboard support does not block preboard readiness and remains out of execution scope.
