# v0.9 Position 1 — Engine Coverage Batch AE Gate

Purpose: qualify Veteran Beastrider's **Bonder's Ornament** by importing the exact production
definition already merged on current main and reusing qualified any-color mana, per-player
iteration, name-filter, and conditional draw rails. This does not change Izzet Science v0.7,
Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Batch AD is accepted from Actions run **36029536192**, source
  `a006f7f153364a6bedaa82299915f5f28e337709`, artifact ID **10822235063**, ZIP SHA-256
  `a898568a706dda6f8e69039f6e0a9794685c653ee44e4fab239dbfb4c119b8d2`.
- Latest Batch AD acceptance record HEAD before AE is
  `9ce656ec25ec23b25471756db41b00441db8be9f`.
- The imported production definition originated on current main from commit
  `8abcf611152b2700ec3dbd51da0068f92ad667d1`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Bonder's Ornament is Batch AE

Bonder's Ornament is a remaining real-engine identity whose exact implementation already exists in
reviewed production history. AE reuses it rather than re-authoring the card.

Oracle text:
"{T}: Add one mana of any color.
{4}, {T}: Each player who controls a permanent named Bonder's Ornament draws a card."

The required rails already exist:
- tap mana abilities with an explicit color choice;
- `Effects.ForEachPlayer`;
- `Conditions.YouControl(GameObjectFilter.Any.named(...))`;
- ordinary card draw.

No new executor, decision type, event type, mana primitive, or opponent-specific heuristic is
introduced.

## Acceptance

1. Bonder's Ornament is `{3}`, Artifact.
2. Its tap mana ability is a mana ability and can produce the chosen color.
3. Its `{4}, {T}` ability draws exactly one card for each player who controls a permanent named
   Bonder's Ornament.
4. A player without an Ornament does not draw from that ability.
5. The definition is byte-equivalent in semantics to the reviewed production definition from
   commit `8abcf611152b2700ec3dbd51da0068f92ad667d1`.
6. Canonical C20 snapshot is reblessed through a fail-closed workflow.
7. Full golden card snapshots pass after rebless.
8. Expected post-implementation unresolved count is exactly **22**.
9. Bonder's Ornament is absent from unresolved output and no Izzet identity becomes unresolved.
10. Official games/seeds/outcomes remain `0/0/0`.
11. Frozen v0.7 SHA remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.

## Pre-qualification provenance

- Fail-closed Batch AE snapshot/rebless run **36036706908**: **SUCCESS**.
- Snapshot workflow source SHA:
  `4cb26917a742da000587c845d04d063bc6e62660`.
- Canonical snapshot integration commit:
  `1d0226a274e7e2dcf643eee023d6d469e109f986`.
- That integration commit adds exactly
  `mtg-sets/src/test/resources/snapshots/cards/C20.json`.
- Bonder's Ornament mana/draw semantics passed before snapshot integration.
- Exact real-engine readiness emitted `V09_REAL_ENGINE_UNRESOLVED_COUNT=22`, with Bonder's
  Ornament absent and no unresolved Izzet identity.
- The corrected unrelated-snapshot guard admitted exactly the previously absent C20 golden and
  rejected every other tracked or untracked path.
- The exact frozen v0.7 SHA remained verified; no official seed/game/outcome was consumed or exposed.

