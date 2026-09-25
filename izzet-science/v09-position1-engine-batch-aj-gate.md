# v0.9 Position 1 — Engine Coverage Batch AJ Gate

Purpose: qualify Veteran Beastrider's **Spirit Link** by reusing the already-qualified attached
damage-trigger → controller life-gain rail. This gate does not change Izzet Science v0.7,
Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Batch AI is accepted from Actions run **36069680436**, source
  `08ffc961ecca4f8c6b0f118efddb0198735c4435`, artifact ID **10837449732**, artifact ZIP
  SHA-256 `ffcd76aa00657c757b8f3f6c07e41aff1f7fb7b3bbacd7d2366e352e40dfd1f3`.
- Batch AI formal acceptance commit is
  `536326136a014ab97d6641c9b35362812e653db1`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Spirit Link is Batch AJ

Oracle text:
"Enchant creature. Whenever enchanted creature deals damage, you gain that much life."

The engine already qualifies this exact semantic rail on Armadillo Cloak:
- creature Aura targeting;
- `TriggerBinding.ATTACHED` damage triggers;
- trigger damage amount captured from the emitted damage event;
- life gain by the Aura controller, not by the enchanted creature's controller;
- triggered (not lifelink/replacement) timing.

Batch AJ therefore adds only the Spirit Link card definition and a focused definition/rail
regression. It introduces no new executor, decision type, damage event, attachment primitive,
target primitive, or card-specific rule.

## Acceptance

1. Spirit Link is `{W}`, Enchantment — Aura.
2. It enchants a creature.
3. Its single triggered ability is bound to the attached creature dealing damage.
4. The life-gain amount is exactly the triggering damage amount.
5. The life gain belongs to the Aura controller; this is not the lifelink keyword.
6. Canonical 10E snapshot is reblessed through a fail-closed workflow.
7. Full golden card snapshots pass after rebless.
8. Expected post-implementation unresolved count is exactly **17**.
9. Spirit Link is absent from unresolved output and no Izzet identity becomes unresolved.
10. Official games/seeds/outcomes remain `0/0/0`.
11. The exact frozen v0.7 control SHA remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.

## Pre-qualification repair and canonical snapshot provenance

- Initial snapshot run **36078361222** failed during test compilation because
  SpiritLinkBatchAJScenarioTest referenced a builder-only card field and a nonexistent runtime
  target class. No rules assertion or official game ran.
- Repair commit **0363d76fc9ec8956a6e5a8ee7ea169d1f6762388** compares the compiled
  script's Aura target to the exact existing Targets.Creature facade. No assertion was removed,
  and the card definition and shared engine were unchanged.
- The snapshot workflow now checks out its triggering SHA and refuses to integrate if the remote
  branch moves. It no longer rebases an unqualified snapshot over concurrent source changes.
- Corrected snapshot run **36079075518**: **SUCCESS**. Spirit Link semantics, real registry
  readiness (17 unresolved Veteran identities, zero Izzet identities), full canonical snapshot
  regeneration, and the unrelated-snapshot guard all passed.
- Canonical snapshot commit **05f4cdb2047d0f9f810f978cf5471a8750bf7df6** changes exactly
  mtg-sets/src/test/resources/snapshots/cards/10E.json.
- Independent commit review confirms only Spirit Link's expected compiled tree was added.
- This snapshot stage uploads no artifact. Formal qualification with a complete manifest and
  transcript remains required before Batch AJ acceptance.
- Official seeds consumed, games initialized, and outcome exposure remain **0/0/0**.
