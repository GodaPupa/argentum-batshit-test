# v0.9 Position 1 — Engine Coverage Batch P Gate

Purpose: qualify Skred with a generic snow-supertype predicate and the already-qualified
dynamic battlefield-count damage pattern, removing another Izzet engine blocker without
changing the frozen card control.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through O remain unchanged.

## Batch P hypothesis

The engine already evaluates projected supertypes and already supports dynamic battlefield
counts at resolution. Adding a generic Snow card predicate should therefore allow Skred to
reuse the same resolution-time count-and-damage semantics already exercised by cards such as
Spitting Earth and Spire Barrage, with no bespoke Skred executor.

## Acceptance

1. Skred resolves as a {R} instant with current Oracle text.
2. Its only legal target is a creature.
3. A snow permanent counts regardless of permanent type.
4. A non-snow permanent does not count.
5. The number of snow permanents is evaluated at resolution, not locked at cast time.
6. Skred deals damage equal to exactly that resolution-time count.
7. The Snow predicate reads projected/live supertype state through the generic predicate evaluator.
8. Canonical CSP snapshot is reblessed through a fail-closed workflow.
9. Full golden snapshots pass.
10. Live unresolved count is exactly 37.
11. Skred is absent from unresolved output.
12. Official counters remain zero and the exact v0.7 100 remains unchanged.
