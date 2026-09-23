# v0.9 Position 1 — Engine Coverage Batch R Gate

Purpose: qualify Arcane Denial by reusing the accepted counter and delayed-trigger rails,
adding only the narrow target-controller capture needed for its deferred draw choice.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through Q remain unchanged.
- Batch Q is accepted from Actions run 35878978167 and artifact
  `izzet-v09-position1-engine-batch-q` (SHA-256 digest
  `aa10ab551c16bd6c13c98b46055f13a8e0a3a678d28c4e072d1409d70cda4f02`).
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Batch R hypothesis

Arcane Denial does not need a bespoke counter or delayed-trigger executor. The engine
already qualifies ordinary countering, one-shot delayed step triggers, NEXT_TURN timing,
DrawCards, and DrawUpTo. The only missing rail is preserving "its controller" for a
DrawUpTo effect after the target spell leaves the stack.

The smallest reusable extension is therefore to bake a delayed DrawUpTo whose target is
`EffectTarget.TargetController` into a concrete player entity at scheduling time.
Arcane Denial schedules both delayed abilities before the counter instruction, following
the already-qualified Mana Sculpt capture-before-counter pattern; no player receives
priority during spell resolution, so this does not change observable resolution choices.

## Acceptance

1. Arcane Denial is {1}{U}, Instant, with current Oracle text.
2. It targets a spell and uses the existing generic counter rail.
3. It creates two independent one-shot delayed triggers.
4. Neither delayed trigger may fire during the turn Arcane Denial resolves.
5. Both become eligible at the very next turn's upkeep, regardless of whose turn that is.
6. The target spell's controller is captured while the target is still on the stack.
7. That player chooses 0, 1, or 2 only when the delayed ability resolves.
8. Arcane Denial's controller draws exactly one card from the separate delayed ability.
9. An uncounterable legal target is not countered, but both delayed draw abilities are still created.
10. Canonical ALL snapshot is reblessed through a fail-closed workflow.
11. Full golden card snapshots pass.
12. Live unresolved count is exactly 35.
13. Arcane Denial is absent from unresolved output.
14. Official games/seeds/outcomes remain 0/0/0 and the exact v0.7 control remains unchanged.
