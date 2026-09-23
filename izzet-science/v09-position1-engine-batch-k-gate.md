# v0.9 Position 1 — Engine Coverage Batch K Gate

Purpose: qualify Frantic Search with its exact in-resolution draw/discard sequence
and non-targeted land untap choice, removing another high-leverage Izzet engine
blocker without changing the frozen card control.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through J remain unchanged.

## Batch K hypothesis

Existing engine primitives are sufficient to model Frantic Search exactly:
draw two, pause during resolution for a mandatory two-card discard, then gather
all battlefield lands and let the controller choose up to three to untap.
The land choices are not targets and may be controlled by any player.

## Acceptance

1. Frantic Search resolves as a {2}{U} instant with current Oracle text.
2. Two cards are drawn before the mandatory two-card discard choice.
3. Draw and discard occur within the same spell resolution.
4. The land choice occurs after the discard and during resolution.
5. Choosing zero through three lands is legal.
6. Chosen lands may be controlled by different players.
7. Only chosen lands untap.
8. Canonical ULG snapshot is reblessed through a fail-closed workflow.
9. Full golden snapshots pass.
10. Live unresolved count is exactly 42.
11. Frantic Search is absent from unresolved output.
12. Official counters remain zero and the exact v0.7 100 remains unchanged.
