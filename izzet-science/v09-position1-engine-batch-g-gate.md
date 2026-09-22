# v0.9 Position 1 — Engine Coverage Batch G Gate

Purpose: remove the commander-definition blocker by qualifying Izzet Guildmage
against the already-accepted spell-copy and retarget engine primitives.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through F remain unchanged.

## Batch G hypothesis

The existing `CopyTargetSpellEffect`, stack-target filtering, controller filtering,
mana-value filtering, and retarget continuation are sufficient to model Izzet
Guildmage exactly. No new generic engine primitive should be required.

## Acceptance

1. Izzet Guildmage resolves as a {U/R}{U/R} 2/2 Human Wizard with current Oracle text.
2. {2}{U} copies a target instant spell you control with mana value 2 or less.
3. The instant copy may choose new targets.
4. {2}{R} copies a target sorcery spell you control with mana value 2 or less.
5. Wrong spell type, mana value above 2, and opponent-controlled spells are rejected.
6. Canonical GPT snapshot is reblessed through a fail-closed workflow.
7. Full golden snapshots pass.
8. Live unresolved count is exactly 46.
9. Izzet Guildmage is absent from unresolved output.
10. Official counters remain zero and the exact v0.7 100 remains unchanged.
