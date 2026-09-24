# v0.9 Position 1 — Engine Coverage Batch AA Gate

Purpose: qualify Veteran Beastrider's Shrine Steward by composing the already-qualified optional
ETB library-search rail with the engine's existing OR-subtype card filter. This gate does not
change Izzet Science v0.7, Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through Z remain unchanged.
- Batch Z is accepted from Actions run **35997404054** and audited artifact
  `izzet-v09-position1-engine-batch-z` (artifact ID **10807111679**, SHA-256
  `87713eb4f3636054abf02b9611179f065673bc3f39cf128b34697c09885002e3`).
- Batch Z accepted source SHA is
  `e8fdb9afad0f42a2b40569f9ee744200fd535596`;
  formal acceptance record HEAD is
  `025c436a3ffbd5a6bb14567dc135031e46cee1d2`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Shrine Steward is Batch AA

Shrine Steward is the smallest remaining Veteran blocker that reuses the just-qualified search
surface without requiring any new executor or decision type.

The engine already has:
- optional ETB trigger gating;
- generic library search/reveal/move-to-hand/shuffle;
- `GameObjectFilter.withAnySubtype(...)`, which expresses Aura OR Shrine as one reusable filter.

Current Oracle text:
"When this creature enters, you may search your library for an Aura or Shrine card, reveal it,
put it into your hand, then shuffle."

## Acceptance

1. Shrine Steward is `{5}`, Artifact Creature — Construct, 3/2.
2. Its ETB search is optional and raises a yes/no decision before the search decision.
3. Choosing yes offers both Aura-subtype and Shrine-subtype cards.
4. Choosing yes excludes cards that are neither Aura nor Shrine.
5. A chosen qualifying card is revealed/moved to hand, removed from the library, and the library shuffles.
6. Choosing no leaves the relevant library/hand state unchanged.
7. No new engine executor, decision type, search primitive, or card-specific predicate is introduced.
8. Canonical NEO snapshot is reblessed through a fail-closed workflow.
9. Full golden card snapshots pass.
10. Expected post-implementation unresolved count is exactly 26.
11. Shrine Steward is absent from unresolved output and no Izzet identity becomes unresolved.
12. Official games/seeds/outcomes remain `0/0/0` and the exact v0.7 control remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
