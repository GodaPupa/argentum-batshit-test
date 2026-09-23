# v0.9 Position 1 — Engine Coverage Batch I Gate

Purpose: qualify Snap with rules-accurate resolution-time land selection, removing
one high-leverage Izzet engine blocker without changing the frozen card control.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through H remain unchanged.

## Batch I hypothesis

Existing pipeline primitives are sufficient to model Snap exactly:
bounce its single creature target, then gather all battlefield lands, let the
controller choose up to two at resolution, and untap only that chosen collection.
The lands are not targets and may be controlled by any player.

## Acceptance

1. Snap resolves as a {1}{U} instant with current Oracle text.
2. Its only target is the creature being returned.
3. The land choice occurs during resolution, not during casting.
4. Choosing zero lands is legal.
5. Choosing up to two lands is legal, including lands controlled by different players.
6. Only chosen lands untap.
7. Canonical ULG snapshot is reblessed through a fail-closed workflow.
8. Full golden snapshots pass.
9. Live unresolved count is exactly 44.
10. Snap is absent from unresolved output.
11. Official counters remain zero and the exact v0.7 100 remains unchanged.
