# v0.9 Position 1 — Engine Coverage Batch Y Gate

Purpose: qualify Veteran Beastrider's Destroy Evil by composing already-qualified modal targeting,
toughness filtering, enchantment targeting, and permanent-destruction rails without changing
Izzet Science v0.7 or either frozen deck identity.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through X remain unchanged.
- Batch X is accepted from Actions run **35958438388** and audited artifact
  `izzet-v09-position1-engine-batch-x` (SHA-256 digest
  `08e0b5b7c773ee51191463b5fd1e26de9f3092fa57e44d2f540d1a17b0b955e1`).
- Batch X accepted source SHA is
  `e5871e01ccaaa89d018cc0098fb4e9909515f144`;
  formal acceptance record HEAD is
  `fb6ebeee7c2477ea97d7d092973ca75eed257dbd`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Destroy Evil is Batch Y

Destroy Evil is the smallest correct reusable surface among the remaining Veteran Beastrider blockers.

The engine already has every required primitive:

- cast-time choose-one modal spells with independent per-mode target requirements;
- `TargetFilter.Creature.toughnessAtLeast(4)`, already used by Valorous Stance for the exact
  "destroy target creature with toughness 4 or greater" branch;
- generic enchantment targeting via `Targets.Enchantment`;
- generic destruction via `Effects.Destroy`.

Therefore Batch Y adds no new engine primitive and no card-specific executor. It qualifies a real
opponent card entirely by composing already-qualified reusable semantics.

## Acceptance

1. Destroy Evil is `{1}{W}`, Instant.
2. Its exact current Oracle text is:
   "Choose one —\n• Destroy target creature with toughness 4 or greater.\n• Destroy target enchantment."
3. Exactly one mode is chosen as the spell is cast.
4. Mode 0 targets exactly one creature with toughness 4 or greater and destroys it on legal resolution.
5. Mode 0 rejects a creature whose toughness is below 4.
6. Mode 1 targets exactly one enchantment and destroys it on legal resolution.
7. No new engine executor, targeting primitive, or card-specific routing path is introduced.
8. Canonical DMU snapshot is reblessed through a fail-closed workflow.
9. Full golden card snapshots pass.
10. Expected post-implementation unresolved count is exactly 28.
11. Destroy Evil is absent from unresolved output and no Izzet identity becomes unresolved.
12. Official games/seeds/outcomes remain `0/0/0` and the exact v0.7 control remains unchanged.


## Pre-qualification provenance

- Fail-closed Batch Y snapshot/rebless run **35959445056** succeeded.
- Canonical snapshot integration commit:
  `374bf6c342f5263d86aeb98a5e1b11d0f71269ea`.
- The integration commit changes exactly
  `mtg-sets/src/test/resources/snapshots/cards/DMU.json` (64 additions, 0 deletions).
- Destroy Evil semantic scenarios passed.
- Exact real-engine readiness emitted `V09_REAL_ENGINE_UNRESOLVED_COUNT=28`, with
  Destroy Evil absent and no unresolved Izzet identity.
- The fail-closed unrelated-snapshot guard passed.
- The exact frozen v0.7 SHA remained verified; no official seed/game/outcome was consumed or exposed.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
