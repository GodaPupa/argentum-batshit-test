# v0.9 Position 1 — Engine Coverage Batch O Gate

Purpose: qualify Ideas Unbound using the already-accepted draw, discard-selection,
and step-based delayed-trigger primitives, removing another Izzet engine blocker
without changing the frozen card control.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through N remain unchanged.

## Batch O hypothesis

Existing engine primitives are sufficient to model Ideas Unbound exactly: draw
three cards during spell resolution, then create a one-shot delayed trigger for
the next end step that makes the spell's controller discard three cards. The
trigger is not restricted to that player's turn, and the existing exact-discard
selection clamps to the number of cards actually available if fewer than three
remain in hand.

## Acceptance

1. Ideas Unbound resolves as a {U}{U} Sorcery — Arcane with current Oracle text.
2. Three cards are drawn immediately during spell resolution.
3. No discard occurs during the spell's initial resolution.
4. A one-shot discard-three trigger is scheduled for the next end step.
5. The delayed trigger is not restricted to the controller's turn.
6. At the next end step the controller chooses exactly three cards to discard when at least three are available.
7. If fewer than three cards remain, all remaining cards are discarded.
8. The delayed trigger is consumed after firing.
9. Canonical SOK snapshot is reblessed through a fail-closed workflow.
10. Full golden snapshots pass.
11. Live unresolved count is exactly 38.
12. Ideas Unbound is absent from unresolved output.
13. Official counters remain zero and the exact v0.7 100 remains unchanged.
