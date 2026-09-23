# v0.9 Position 1 — Engine Coverage Batch H Gate

Purpose: qualify Echoing Truth using the already-accepted chosen-target, stored-name,
battlefield gather, same-name filter, and owner-routed battlefield-to-hand primitives.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through G remain unchanged.

## Batch H hypothesis

The existing pipeline can gather the chosen nonland permanent, store its name, gather every
battlefield permanent with that name, and move those permanents from the battlefield to hand.
The move executor already routes battlefield-to-hand moves to each card's owner, so no new generic
engine primitive should be required.

## Acceptance

1. Echoing Truth resolves as {1}{U} Instant with current Oracle text.
2. Its target requirement rejects lands.
3. The targeted nonland permanent and all other battlefield permanents with the same name are returned.
4. Same-named permanents owned by different players return to their respective owners' hands.
5. Differently named permanents remain on the battlefield.
6. Canonical DST snapshot is reblessed through a fail-closed workflow.
7. Full golden snapshots pass.
8. Live unresolved count is exactly 45.
9. Echoing Truth is absent from unresolved output.
10. Official counters remain zero and the exact v0.7 100 remains unchanged.
