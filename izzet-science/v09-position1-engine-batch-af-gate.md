# v0.9 Position 1 — Engine Coverage Batch AF Gate

Purpose: qualify Veteran Beastrider's **Master's Rebuke** by reusing the already-qualified
Bite Down / source-power damage rail, without changing Izzet Science v0.7, Veteran Beastrider's
frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Batch AE is accepted from Actions run **36037864352**, source
  `d7432270e8f1eff63bb6e3e9adb5c94728c5c3eb`, artifact ID **10825790086**, ZIP SHA-256
  `9f8950458e32dcc4530dda3f79f01d5c0bc96118dc7b18eb0b8461996f8a61ac`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Master's Rebuke is Batch AF

Its rules text is mechanically identical to the already-implemented Bite Down rail:

"Target creature you control deals damage equal to its power to target creature or planeswalker
you don't control."

The existing engine already provides:

- source-relative target binding;
- projected source power via `DynamicAmounts.targetPower`;
- creature-or-planeswalker opponent target filtering;
- damage sourced from the selected creature rather than the spell.

Batch AF therefore adds one card definition and one semantic regression only. It introduces no new
executor, decision type, target-routing primitive, damage primitive, or opponent-specific policy.

## Acceptance

1. Master's Rebuke is `{1}{G}`, Instant.
2. It requires a creature you control as the damage source.
3. It requires a creature or planeswalker you don't control as the recipient.
4. On legal resolution, the source creature deals damage equal to its power.
5. The source does not receive reciprocal fight damage.
6. No new engine primitive is introduced.
7. Canonical NEO snapshot is reblessed through a fail-closed workflow.
8. Full golden card snapshots pass after rebless.
9. Expected post-implementation unresolved count is exactly **21**.
10. Master's Rebuke is absent from unresolved output and no Izzet identity becomes unresolved.
11. Official games/seeds/outcomes remain `0/0/0`.
12. Frozen v0.7 SHA remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
