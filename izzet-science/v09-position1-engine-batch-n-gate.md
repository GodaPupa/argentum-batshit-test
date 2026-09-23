# v0.9 Position 1 — Engine Coverage Batch N Gate

Purpose: qualify Pieces of the Puzzle using the already-accepted reveal/gather,
filtered selection, and collection-move pipeline, removing another high-leverage
Izzet engine blocker without changing the frozen card control.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through M remain unchanged.

## Batch N hypothesis

Existing library-pipeline primitives are sufficient to model Pieces of the Puzzle
exactly: reveal the top five cards, expose only instant and/or sorcery cards as
selectable, allow zero through two of those cards to be kept, move the selected
cards to hand, and move every unselected card into the graveyard.

## Acceptance

1. Pieces of the Puzzle resolves as a {2}{U} sorcery with current Oracle text.
2. Exactly the top five available cards are gathered and revealed.
3. Only instant and/or sorcery cards among those cards are selectable.
4. Choosing zero, one, or two eligible cards is legal.
5. Selected cards move to the controller's hand.
6. Every unselected revealed card moves to that controller's graveyard.
7. Canonical SOI snapshot is reblessed through a fail-closed workflow.
8. Full golden snapshots pass.
9. Live unresolved count is exactly 39.
10. Pieces of the Puzzle is absent from unresolved output.
11. Official counters remain zero and the exact v0.7 100 remains unchanged.
