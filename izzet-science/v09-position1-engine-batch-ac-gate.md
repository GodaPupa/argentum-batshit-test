# v0.9 Position 1 — Engine Coverage Batch AC Gate

Purpose: qualify Veteran Beastrider's Ilysian Caryatid by composing the already-qualified
any-one-color mana rail with the engine's state-conditional mana-ability rail, without changing
Izzet Science v0.7, Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through AB remain unchanged.
- Batch AB is accepted from Actions run **36024552017** and audited artifact
  `izzet-v09-position1-engine-batch-ab` (artifact ID **10818158912**, SHA-256
  `45ac51935c94db6742efdd68d5c1286b21a4af46ea028ad834c913ebde3a9fbc`).
- Batch AB accepted source SHA is
  `5c052dc022e502e6561d0334a67b8fc4c1fa0387`;
  latest accepted-lineage record before Batch AC is
  `e88841e108b03a83234c1f8388ddbbcb43fe93c1`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Ilysian Caryatid is Batch AC

Ilysian Caryatid is the smallest remaining Veteran blocker that reuses existing generic mana
semantics without requiring a new executor or card-specific rule.

The engine already has:
- `Effects.AddAnyColorMana(amount)`, which adds N mana of one chosen color;
- `ConditionalEffect` over projected battlefield conditions;
- `Conditions.YouControl(GameObjectFilter.Creature.powerAtLeast(4))`;
- qualified conditional mana-source discovery/auto-tap behavior from the Raucous Audience rail.

Current Oracle text:
"{T}: Add one mana of any color. If you control a creature with power 4 or greater, add two mana
of any one color instead."

## Acceptance

1. Ilysian Caryatid is `{1}{G}`, Creature — Plant, 1/1.
2. Its activated ability is a mana ability with tap cost and does not use the stack.
3. Without a creature you control with power 4 or greater, it adds exactly one mana of the chosen color.
4. With a creature you control with power 4 or greater, it adds exactly two mana of one chosen color instead.
5. The threshold reads projected power through the generic creature filter.
6. The chosen color is shared by all mana produced by the activation; it is not "any combination".
7. Conditional mana-source discovery remains compatible with the generic ManaSolver/auto-tap rail.
8. No new engine executor, decision type, mana primitive, or card-specific condition is introduced.
9. Canonical THB snapshot is reblessed through a fail-closed workflow.
10. Full golden card snapshots pass.
11. Expected post-implementation unresolved count is exactly 24.
12. Ilysian Caryatid is absent from unresolved output and no Izzet identity becomes unresolved.
13. Official games/seeds/outcomes remain `0/0/0` and the exact v0.7 control remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.


## Pre-qualification provenance

- Fail-closed Batch AC snapshot/rebless run **36026261710**: **SUCCESS**.
- Snapshot workflow source SHA:
  `30e3cc0c9663aabe1632089eb9aa9843b8f40990`.
- Canonical snapshot integration commit:
  `a4260a4cf83eb71369eb3c93c28d1a421751d91e`.
- The integration commit changes exactly
  `mtg-sets/src/test/resources/snapshots/cards/THB.json`.
- Ilysian Caryatid conditional any-color mana semantics passed.
- Exact real-engine readiness emitted `V09_REAL_ENGINE_UNRESOLVED_COUNT=24`, with Ilysian Caryatid
  absent and no unresolved Izzet identity.
- The unrelated-snapshot guard passed and the exact frozen v0.7 SHA remained verified.
- No official seed/game/outcome was consumed or exposed.


## Formal qualification and acceptance

- Formal Batch AC qualification run **36027210971**: **SUCCESS**.
- Accepted source SHA:
  `622d7486d8da53be6f33ed0381938ab6eb2055e2`.
- Accepted artifact:
  `izzet-v09-position1-engine-batch-ac`, artifact ID **10820891185**.
- GitHub artifact ZIP SHA-256:
  `74d5a4399844b209f816cbe55bc91b9a25c60cb413d7caec033817eda0086abf`.
- Independent download reproduced that exact ZIP SHA-256 digest.
- The downloaded manifest binds:
  - source SHA `622d7486d8da53be6f33ed0381938ab6eb2055e2`;
  - frozen-control SHA-256
    `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`;
  - snapshot run **36026261710**;
  - snapshot commit `a4260a4cf83eb71369eb3c93c28d1a421751d91e`;
  - unresolved reduction **25 -> 24**;
  - unresolved Izzet identities **0**;
  - official seeds/games/outcome exposure **0/0/0**.
- Formal qualification reverified Ilysian Caryatid conditional any-color mana semantics, exact
  real-engine coverage, full card snapshots, the frozen v0.7 control hash, and untouched official state.
- **Batch AC is accepted.**
