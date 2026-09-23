# v0.9 Position 1 — Engine Coverage Batch M Gate

Purpose: qualify Turn Aside using the existing stack-object target-matching
predicate, removing another Izzet engine blocker without changing the frozen
card control or adding a bespoke counterspell path.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through L remain unchanged.

## Batch M hypothesis

The existing `CardPredicate.TargetsMatching` stack primitive is sufficient to
express Turn Aside exactly. Its target is a spell on the stack with at least one
current target that is a permanent controlled by Turn Aside's controller. The
target spell does not need to be controlled by an opponent.

## Acceptance

1. Turn Aside resolves as a {U} instant with current Oracle text.
2. Its target must be a spell on the stack.
3. The target spell must target at least one permanent controlled by Turn Aside's controller.
4. A spell targeting only an opponent-controlled permanent is not a legal target.
5. The target spell may be controlled by any player, including Turn Aside's controller.
6. A legal target spell is countered normally.
7. Canonical SOM snapshot is reblessed through a fail-closed workflow.
8. Full golden snapshots pass.
9. Live unresolved count is exactly 40.
10. Turn Aside is absent from unresolved output.
11. Official counters remain zero and the exact v0.7 100 remains unchanged.
