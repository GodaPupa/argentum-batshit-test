# v0.9 Position 1 — Engine Coverage Batch J Gate

Purpose: qualify Capsize with rules-accurate buyback semantics, removing one of
the highest-leverage remaining Izzet engine blockers without changing the frozen
card control.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through I remain unchanged.

## Batch J hypothesis

The existing optional-additional-cost rail can model buyback without a bespoke
casting path. A dedicated BUYBACK cast-choice slot records the payment, and the
normal successful-resolution destination step returns the spell to its owner's
hand. Countered or fizzled buyback spells never reach that successful-resolution
destination and therefore do not return to hand.

## Acceptance

1. Capsize resolves as a {1}{U}{U} instant with Buyback {3} and current Oracle text.
2. The legal-action surface labels the paid variant "Buyback", not "Kicked".
3. The paid variant costs {4}{U}{U} before external cost modifiers.
4. A normal Capsize returns the target permanent and goes to its owner's graveyard.
5. A Capsize whose buyback cost was paid returns to its owner's hand after successful resolution.
6. A countered Capsize does not return to hand even if its buyback cost was paid.
7. Canonical TMP snapshot is reblessed through a fail-closed workflow.
8. Full golden snapshots pass.
9. Live unresolved count is exactly 43.
10. Capsize is absent from unresolved output.
11. Official counters remain zero and the exact v0.7 100 remains unchanged.
