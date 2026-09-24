# v0.9 Position 1 — Engine Coverage Batch AC Gate

Purpose: qualify Veteran Beastrider's Whisperer of the Wilds by composing the already-qualified
fixed green mana rail with a generic power-threshold activation restriction, without changing
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

## Why Whisperer of the Wilds is Batch AC

Whisperer of the Wilds is a small reusable-mechanic coverage gate among the remaining Veteran
blockers. Its two abilities require only primitives the engine already exposes:

- ordinary tap-for-green mana;
- a second mana ability producing two green;
- `ActivationRestriction.OnlyIfCondition`;
- `Conditions.YouControl(GameObjectFilter.Creature.powerAtLeast(4))`;
- projected-power reads already used by conditional mana-source qualification.

Current Oracle text:
"{T}: Add {G}.
Ferocious — {T}: Add {G}{G}. Activate only if you control a creature with power 4 or greater."

No Whisperer-specific executor or special Ferocious engine primitive is justified.

## Acceptance

1. Whisperer of the Wilds is `{1}{G}`, Creature — Human Shaman, 0/2.
2. Its first activated ability is a mana ability with tap cost and adds exactly `{G}`.
3. Its Ferocious activated ability is a mana ability with tap cost and adds exactly `{G}{G}`.
4. The Ferocious ability is not activatable unless its controller controls a creature whose
   projected power is 4 or greater.
5. When that condition becomes true, the generic ManaSolver can use Whisperer as a single source
   capable of paying `{G}{G}`.
6. Without the condition, the same source can still pay a single `{G}` through its base ability.
7. No new engine executor, decision type, mana primitive, or card-specific restriction is introduced.
8. Canonical FRF snapshot is reblessed through a fail-closed workflow.
9. Full golden card snapshots pass.
10. Expected post-implementation unresolved count is exactly 24.
11. Whisperer of the Wilds is absent from unresolved output and no Izzet identity becomes unresolved.
12. Official games/seeds/outcomes remain `0/0/0` and the exact v0.7 control remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
