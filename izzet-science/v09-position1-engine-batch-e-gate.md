# v0.9 Position 1 — Engine Coverage Batch E Gate

Purpose: remove one additional Izzet-side real-engine blocker using an already-qualified
static spell-cost reduction primitive.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through D remain unchanged.

## Batch E hypothesis

Goblin Electromancer can be wired entirely from the already-qualified
`ModifySpellCost(YouCast(InstantOrSorcery), ReduceGeneric(1))` primitive used by
Stormcatch Mentor and Haughty Djinn.

No new rules-engine mechanic is required.

## Acceptance

1. Goblin Electromancer resolves as a 2/2 Goblin Wizard.
2. Its static ability reduces the generic portion of instant and sorcery spell costs by exactly {1}.
3. The reduction never removes a colored mana symbol.
4. Canonical RTR snapshot is reblessed through a fail-closed workflow.
5. Full golden snapshots pass.
6. Live unresolved count is exactly 48.
7. Goblin Electromancer is absent from unresolved output.
8. Official counters remain zero.
9. The exact v0.7 100 remains unchanged.
