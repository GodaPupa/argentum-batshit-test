# v0.9 Position 1 — Engine Coverage Batch L Gate

Purpose: qualify Shattering Pulse by reusing the already-accepted generic buyback
rail from Batch J while adding the card's artifact-only destruction semantics.
This removes another Izzet engine blocker without changing the frozen card control.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through K remain unchanged.

## Batch L hypothesis

The buyback cast-choice and successful-resolution destination semantics already
qualified by Capsize are generic and reusable. Shattering Pulse should therefore
require no new buyback engine primitive: it pays an optional additional {3},
destroys exactly one target artifact, returns to its owner's hand only after a
successful paid-buyback resolution, and otherwise goes to the graveyard.

## Acceptance

1. Shattering Pulse resolves as a {1}{R} instant with Buyback {3} and current Oracle text.
2. Its only legal target is an artifact permanent.
3. The legal-action surface labels the paid variant "Buyback" and reports {4}{R}.
4. A normal Shattering Pulse destroys its artifact target and goes to its owner's graveyard.
5. A paid-buyback Shattering Pulse destroys its artifact target and returns to its owner's hand.
6. A countered paid-buyback Shattering Pulse does not destroy the target and goes to its owner's graveyard.
7. Canonical EXO snapshot is reblessed through a fail-closed workflow.
8. Full golden snapshots pass.
9. Live unresolved count is exactly 41.
10. Shattering Pulse is absent from unresolved output.
11. Official counters remain zero and the exact v0.7 100 remains unchanged.
