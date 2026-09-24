# v0.9 Position 1 — Engine Coverage Batch X Gate

Purpose: qualify the first remaining Veteran Beastrider identity, Generous Gift, by composing
already-qualified generic permanent-destruction and target-controller token rails without changing
Izzet Science v0.7 or either frozen deck identity.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through W remain unchanged.
- Batch W is accepted from Actions run **35931662488** and audited artifact
  `izzet-v09-position1-engine-batch-w` (SHA-256 digest
  `2b3bcaf3f8ae0e6c326bcaa539ba1b80a6d30ad6b643039e85fa73d6129560c7`).
- Batch W accepted source SHA is
  `ad5fbf406fab720cbc75916ae16c173ca21ea262`;
  formal acceptance record HEAD is
  `94b98edb16e0a2360990df09cfde162732d5c920`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Generous Gift is Batch X

After Batch W, every Izzet Science identity is resolved. The first official position remains
execution-blocked because Veteran Beastrider still has 30 unresolved identities plus the five
separately frozen PDH gameplay semantics.

Generous Gift is the smallest correct reusable surface among the remaining opponent blockers:

- the engine already has generic target-permanent destruction;
- the engine already has `EffectTarget.TargetController`;
- `CreateTokenEffect` already resolves token ownership/control to the targeted permanent's
  controller;
- the existing Beast Within definition composes the exact same rules shape:
  destroy target permanent, then that permanent's controller creates a 3/3 token.

Therefore Batch X adds no new engine primitive and no card-specific executor. It qualifies a real
opponent card using already-existing reusable semantics.

## Acceptance

1. Generous Gift is `{2}{W}`, Instant.
2. Its exact current Oracle text is:
   "Destroy target permanent. Its controller creates a 3/3 green Elephant creature token."
3. The target requirement is exactly one permanent.
4. On legal resolution, the target is destroyed using the generic destroy rail.
5. The destroyed permanent's controller, not necessarily the spell's controller, creates exactly
   one 3/3 green Elephant creature token.
6. If the sole target is illegal at resolution, the spell does not resolve and no Elephant is
   created.
7. No new engine executor or card-specific target-routing path is introduced.
8. Canonical MH1 snapshot is reblessed through a fail-closed workflow.
9. Full golden card snapshots pass.
10. Expected post-implementation unresolved count is exactly 29.
11. Generous Gift is absent from unresolved output and no Izzet identity becomes unresolved.
12. Official games/seeds/outcomes remain `0/0/0` and the exact v0.7 control remains unchanged.


## Pre-qualification provenance

- Fail-closed Batch X snapshot/rebless run **35947000559** succeeded.
- Canonical snapshot integration commit:
  `856485bc2db1e34cd0c05889edc709a6c4515e86`.
- The integration commit changes exactly
  `mtg-sets/src/test/resources/snapshots/cards/MH1.json` (53 additions, 0 deletions).
- Generous Gift semantic scenarios passed.
- Exact real-engine readiness emitted `V09_REAL_ENGINE_UNRESOLVED_COUNT=29`, with
  Generous Gift absent and no unresolved Izzet identity.
- The fail-closed unrelated-snapshot guard passed.
- The exact frozen v0.7 SHA remained verified; no official seed/game/outcome was consumed or exposed.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
