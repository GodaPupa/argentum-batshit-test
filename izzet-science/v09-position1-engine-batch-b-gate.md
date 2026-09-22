# v0.9 Position 1 — Engine Coverage Batch B Gate

Purpose: qualify Llanowar Visionary as a seed-free reusable-mechanics coverage addition after accepted Batch A (Boreal Druid).

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256 must remain `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Historical Batch A remains unchanged and retains its 55 -> 54 acceptance meaning.

## Batch B hypothesis

Registering Llanowar Visionary using already-supported ETB-draw and green mana-ability primitives reduces live unresolved real-engine card coverage from 54 to 53 without modifying either frozen deck identity or core game semantics.

## Acceptance

1. Llanowar Visionary resolves from the real CardRegistry.
2. Its 2/2 characteristic values, ETB trigger, and mana ability are present.
3. Live unresolved count is exactly 53.
4. Boreal Druid and Llanowar Visionary are absent from the unresolved output.
5. Official counters remain zero.
6. Full repository CI and the M21 golden snapshot must be reconciled separately before Batch B can be accepted.

<!-- post-rebless CI trigger: canonical M21 snapshot audited at 08c21cec63628179b9bc57233e95390c50c5f51d -->
