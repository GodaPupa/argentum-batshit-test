# v0.9 Position 1 — Engine Coverage Batch Q Gate

Purpose: qualify Star Compass by extending the already-accepted dynamic mana-color
selection rail with a basic-lands-you-control "could produce" source, removing another
Izzet engine blocker without changing the frozen card control.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through P remain unchanged.

## Batch Q hypothesis

The engine's existing `LandManaColorInspector` already answers the rules question
"what colors could these lands produce?" for Fellwar Stone and related cards. Star
Compass therefore needs only a narrow candidate-set variant: projected Basic Lands
controlled by the source's controller. The existing constrained-color mana ability then
handles selection and production without a bespoke Star Compass executor.

## Acceptance

1. Star Compass is a {2} artifact with current Oracle text.
2. Star Compass enters tapped.
3. Its tap ability is a mana ability.
4. A basic land you control contributes every color it could produce, even while tapped.
5. Multiple basic lands you control widen the available color set appropriately.
6. A nonbasic land you control does not contribute colors.
7. A basic land controlled by an opponent does not contribute colors.
8. Colorless production does not become a selectable color.
9. Canonical PLS snapshot is reblessed through a fail-closed workflow.
10. Full golden snapshots pass.
11. Live unresolved count is exactly 36.
12. Star Compass is absent from unresolved output.
13. Official counters remain zero and the exact v0.7 100 remains unchanged.
