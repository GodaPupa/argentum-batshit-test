# v0.9 Position 1 — Engine Coverage Batch D Gate

Purpose: remove one additional Izzet-side real-engine blocker with a narrowly scoped,
seed-free reusable-mechanic qualification.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A, B, and C remain unchanged.

## Batch D hypothesis

Murmuring Mystic can be wired entirely from already-qualified instant/sorcery cast
trigger and token-creation primitives represented elsewhere in the engine.

## Acceptance

1. Murmuring Mystic resolves as a 1/5 Human Wizard.
2. Casting an instant or sorcery with it on the battlefield creates one 1/1 blue
   Bird Illusion creature token with flying.
3. Canonical GRN snapshot is reblessed through a fail-closed workflow.
4. Full golden snapshots pass.
5. Live unresolved count is exactly 49.
6. Murmuring Mystic is absent from unresolved output.
7. Official counters remain zero.
8. The exact v0.7 100 remains unchanged.
