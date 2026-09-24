# v0.9 Position 1 — Engine Coverage Batch AH Gate

Purpose: qualify Veteran Beastrider's **Stave Off** by reusing the already-qualified
resolution-time choose-color plus temporary protection rail. This gate does not change Izzet
Science v0.7, Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Batch AG is accepted from Actions run **36057944951**, source
  `690c6bdd733eaf83fe13943cc9e5dfa9c93b8ded`, artifact ID **10833471323**, artifact ZIP
  SHA-256 `843b53e4e770d67956960be7a35048beaaba1ccfa76062863d91fc859deb9261`.
- Batch AG formal acceptance commit is
  `e75fd589fbc9b1a6ecc826ad9f7d0479c22b7f74`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Stave Off is Batch AH

Oracle text:
"Target creature gains protection from the color of your choice until end of turn."

The engine already qualifies:
- creature targeting;
- resolution-time `ChooseColorThen`;
- `GrantProtectionFromChosenColor`;
- until-end-of-turn floating keyword expiry.

Feat of Resistance and Razor Barrier already exercise the same color-choice/protection rail.
Batch AH therefore adds only a card definition plus semantic regression.

## Acceptance

1. Stave Off is `{W}`, Instant.
2. It targets exactly one creature.
3. The color choice occurs on resolution rather than during cast target selection.
4. The target gains protection from exactly the chosen color.
5. The protection grant expires at end of turn.
6. No new executor, decision type, protection primitive, or card-specific rule is introduced.
7. Canonical M12 snapshot is reblessed through a fail-closed workflow.
8. Full golden card snapshots pass after rebless.
9. Expected post-implementation unresolved count is exactly **19**.
10. Stave Off is absent from unresolved output and no Izzet identity becomes unresolved.
11. Official games/seeds/outcomes remain `0/0/0`.
12. The exact frozen v0.7 control SHA remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
