# v0.9 Position 1 — Engine Coverage Batch AC Gate

Purpose: qualify Veteran Beastrider's Ilysian Caryatid by composing the already-qualified
any-color mana rail with a generic projected-power condition and conditional mana amount, without
changing Izzet Science v0.7, Veteran Beastrider's frozen identity, or any gameplay engine primitive.

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

Ilysian Caryatid is a small reusable-mechanic coverage gate among the remaining Veteran blockers.
Its single mana ability requires only primitives the engine already exposes:

- ordinary tap-cost mana abilities;
- the existing any-color mana choice rail;
- `Conditions.YouControl(GameObjectFilter.Creature.powerAtLeast(4))`;
- projected-power reads;
- `ConditionalEffect` choosing between one and two mana of the chosen color.

Current Oracle text:
"{T}: Add one mana of any color. If you control a creature with power 4 or greater, add two mana
of any one color instead."

No Caryatid-specific executor, decision type, Ferocious keyword primitive, or card-specific mana
solver rule is justified.

## Acceptance

1. Ilysian Caryatid is `{1}{G}`, Creature — Plant, 1/1.
2. It has one tap-cost activated mana ability.
3. Without a controlled creature of projected power 4 or greater, that source produces exactly one
   mana of a chosen color.
4. With a controlled creature of projected power 4 or greater, that same source produces exactly
   two mana of one chosen color instead.
5. The generic ManaSolver can use Caryatid as one source to pay a single colored pip when the
   threshold is false.
6. The generic ManaSolver can use Caryatid as one source to pay two same-color pips when the
   threshold is true, and cannot do so when the threshold is false.
7. No new engine executor, decision type, mana primitive, or card-specific restriction is introduced.
8. Canonical THB snapshot is reblessed through a fail-closed workflow.
9. Full golden card snapshots pass.
10. Expected post-implementation unresolved count is exactly 24.
11. Ilysian Caryatid is absent from unresolved output and no Izzet identity becomes unresolved.
12. Official games/seeds/outcomes remain `0/0/0` and the exact v0.7 control remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
