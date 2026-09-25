# v0.9 Position 1 — Engine Coverage Batch AK Gate

Purpose: qualify three independent, simple remaining Veteran Beastrider identities in one
seed-free batch. The current finite queue follows accepted Batch AJ, with 17 unresolved Veteran
identities and no unresolved Izzet identity. Neither frozen deck changes.

## Frozen boundaries

- Izzet v0.7 byte SHA-256 remains
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Opponent remains `veteran-beastrider-commander-clash-2025-v1`.
- Batch AJ must be formally accepted before this workflow can qualify.
- No official vector is read, seed revealed or consumed, game initialized, or outcome exposed.
- Position 1 remains blocked pending exact full-engine readiness; positions 2–12 remain unauthorized.
- This gate implements no SDK, engine, pilot, or event-ledger capability.

## Scope and composition

| Card | Canonical printing | Existing qualified composition |
| --- | --- | --- |
| Brightwood Tracker | M20 #166 | Tap/mana cost; optional look-four creature selection; reveal to hand; random-order bottom remainder |
| Deepwood Denizen | MH2 #155 | Vigilance; tap/mana draw; generic activation discount using the sum of +1/+1 counters on creatures its controller controls |
| Shardless Outlander | J25 #28 | Artifact creature; trample; basic landcycling for two generic mana |

Current Oracle, printing details, and earliest-printing lists were retrieved from Scryfall before
implementation. The cards have no published rulings in those retrieved records. J25 receives a
minimal incomplete set scaffold so Shardless Outlander resides in its actual first printing.
No complete-set implementation is claimed.

Sources: [Brightwood Tracker](https://scryfall.com/card/m20/166/brightwood-tracker),
[Deepwood Denizen](https://scryfall.com/card/mh2/155/deepwood-denizen),
[Shardless Outlander](https://scryfall.com/card/j25/28/shardless-outlander).

Cosmic Hunger was considered and excluded before implementation: the current branch lacks an exact
fixed battle-type target predicate. It remains unresolved; this batch must not approximate its
creature, planeswalker, or battle target as only the first two types.

## Required behavioral and snapshot evidence

1. Brightwood Tracker offers only creatures from the top four, permits decline, moves a selected
   creature to hand, and places precisely the unselected cards on the library bottom. Its ordinary
   library pipeline keeps the look private and reveals only the selected card.
2. Deepwood Denizen sums counter quantities across its controller's creatures. Opponent counters
   and noncreature counters do not contribute. Four counters do not suffice with only green mana;
   five or more remove the generic component while never removing the green pip. A paid activation
   draws exactly one card.
3. Shardless Outlander has trample and printed 6/5 statistics. Basic landcycling fails atomically
   without mana, pays two generic mana, discards the card, allows only basic lands, moves the chosen
   card to hand, and uses the existing reveal/shuffle rail.
4. Each card has its own scenario-test file; all fixtures run in the same workflow.
5. Canonical snapshot regeneration may change exactly M20.json, MH2.json, and J25.json. A semantic
   comparison must preserve every preexisting card and add exactly the three named cards.
6. The full canonical snapshot suite must pass again with snapshot updating disabled.
7. Expected post-implementation unresolved count is exactly **14**, all Veteran identities.
8. Frozen-control and untouched official-execution guards must pass.
9. A complete success artifact binds source commit, canonical snapshot commit, file hashes,
   scenario/snapshot/readiness transcripts, and official counters of **0/0/0**.

## Integration and acceptance

The workflow checks out the triggering commit. It commits only the three qualified snapshot files,
checks that the live remote branch still equals the triggering commit, and pushes without force.
If another worker advances the branch, integration fails closed; no automatic rebase is permitted.

Snapshot generation, full snapshot verification, behavioral tests, and manifest capture form one
coherent batch rather than one administrative cycle per card. Formal acceptance still requires an
independent download and audit of the complete artifact. A successful workflow alone neither accepts
the batch nor demonstrates a sampled game or deck-strength result.
