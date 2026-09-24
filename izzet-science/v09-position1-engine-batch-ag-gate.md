# v0.9 Position 1 — Engine Coverage Batch AG Gate

Purpose: qualify Veteran Beastrider's **Leafkin Druid** by composing the already-qualified
conditional mana-ability rail with the generic controlled-creature-count condition. This gate does
not change Izzet Science v0.7, Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Batch AF is accepted from Actions run **36052446097**, source
  `cdb88ab9b0a9d9e4555bf00c69bb242e08b61475`, artifact ID **10830783712**, artifact ZIP
  SHA-256 `7c9b285ea82f7bdacc44ab023bef88ba12631cee70253508524381635bb1fac6`.
- Batch AF formal acceptance record is the current accepted gate history.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Leafkin Druid is Batch AG

Leafkin Druid is the smallest remaining real-engine blocker that reuses generic rails already
qualified elsewhere:

- tap-cost mana abilities;
- `ConditionalEffect`;
- `Conditions.ControlCreaturesAtLeast(4)`;
- conditional mana-source discovery in the ManaSolver.

Oracle text:
"{T}: Add {G}. If you control four or more creatures, add {G}{G} instead."

The Leafkin itself counts toward the four controlled creatures. Batch AG therefore adds a card
definition and semantic regression only; it introduces no executor, decision type, mana primitive,
or card-specific engine condition.

## Acceptance

1. Leafkin Druid is `{1}{G}`, Creature — Elemental Druid, 0/3.
2. Its tap ability is a mana ability.
3. With fewer than four creatures controlled, one activation supplies exactly `{G}`.
4. With four or more creatures controlled, one activation supplies exactly `{G}{G}` instead.
5. The threshold is evaluated from the current projected battlefield.
6. ManaSolver can discover the correct current branch.
7. Canonical M20 snapshot is reblessed through a fail-closed workflow.
8. Full golden card snapshots pass after rebless.
9. Expected post-implementation unresolved count is exactly **20**.
10. Leafkin Druid is absent from unresolved output and no Izzet identity becomes unresolved.
11. Official games/seeds/outcomes remain `0/0/0`.
12. The exact frozen v0.7 control SHA remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.


## Formal qualification and acceptance

- Formal Batch AG qualification run **36057944951**: **SUCCESS**.
- Accepted source SHA:
  `690c6bdd733eaf83fe13943cc9e5dfa9c93b8ded`.
- Accepted artifact:
  `izzet-v09-position1-engine-batch-ag`, artifact ID **10833471323**.
- GitHub artifact ZIP SHA-256:
  `843b53e4e770d67956960be7a35048beaaba1ccfa76062863d91fc859deb9261`.
- Independent download reproduced that exact ZIP SHA-256 digest.
- The archive contains exactly `manifest.txt` and `test-output.txt`.
- The manifest binds:
  - source SHA `690c6bdd733eaf83fe13943cc9e5dfa9c93b8ded`;
  - frozen-control SHA-256
    `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`;
  - snapshot run **36053709223**;
  - snapshot commit `1277e6daa35e28eddc03153420ec45a1bd8520f6`;
  - unresolved reduction **21 -> 20**;
  - unresolved Izzet identities **0**;
  - official seeds/games/outcome exposure **0/0/0**.
- The downloaded test output independently emits
  `V09_REAL_ENGINE_UNRESOLVED_COUNT=20` and contains no unresolved Izzet identity.
- Formal qualification reverified Leafkin Druid semantics, canonical M20 snapshots, exact
  real-engine coverage, the frozen-control hash, and untouched official state.
- **Batch AG is accepted.**
