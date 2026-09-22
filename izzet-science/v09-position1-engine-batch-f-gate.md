# v0.9 Position 1 — Engine Coverage Batch F Gate

Purpose: remove one additional Izzet-side blocker by qualifying a reusable
counter-to-top-of-library destination for Memory Lapse.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through E remain unchanged.

## Batch F hypothesis

Memory Lapse needs one reusable extension of the accepted counter destination model:
`CounterDestination.LibraryTop`. It must preserve genuine-counter semantics,
including can't-be-countered handling and counter-time exile riders such as flashback.

## Acceptance

1. Memory Lapse resolves as a {1}{U} instant with exact current Oracle text.
2. A countered spell is placed at index 0 of its owner's library.
3. An uncounterable spell is neither countered nor moved.
4. Counter-time exile riders remain higher priority than LibraryTop.
5. Canonical HML snapshot is reblessed through a fail-closed workflow.
6. Full golden snapshots pass.
7. Live unresolved count is exactly 47.
8. Memory Lapse is absent from unresolved output.
9. Official counters remain zero.
10. The exact v0.7 100 remains unchanged.
