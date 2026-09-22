# v0.9 Position 1 — Engine Coverage Batch C Gate

Purpose: qualify Owlbear as a seed-free reusable-mechanics coverage addition after
accepted Boreal Druid and Llanowar Visionary coverage.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256 must remain
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- The deck-level audit disposition remains `V07_DECK_AUDIT_NO_CHANGE`.
- Historical Batch A and Batch B evidence remains unchanged.

## Batch C hypothesis

Registering Owlbear with already-supported creature characteristics, trample, and
ETB-draw primitives reduces live unresolved real-engine card coverage from 53 to 52
without modifying either frozen deck identity or introducing a new mechanic family.

## Acceptance

1. Owlbear resolves from the real CardRegistry.
2. It is a 4/4 with trample and one ETB draw trigger.
3. Live unresolved count is exactly 52.
4. Boreal Druid, Llanowar Visionary, and Owlbear are absent from unresolved output.
5. Full card-definition snapshot validation passes after a fail-closed AFR rebless.
6. Official counters remain zero.
7. No deck card changes occur.
