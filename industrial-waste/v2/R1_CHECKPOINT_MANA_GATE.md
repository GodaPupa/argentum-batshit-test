# Industrial Waste v2 — R1 quiet-checkpoint mana classification gate

Status: **PROSPECTIVE SEED-FREE CAPABILITY; OFFICIAL R1 STILL BLOCKED**

This gate adds no candidate, comparator, ordering row, seed, allocation, action transcript or
comparative outcome. The three frozen structural candidates and immutable submitted control remain
unchanged.

The component classifies each hand copy only at a quiet acting-player precombat-main checkpoint. It
consumes the real engine LegalAction surface and ManaSolver. For fixed-cost cast shapes with legal
targets, it distinguishes:

- **INSUFFICIENT_TOTAL_MANA** when the real generic-equivalent available-mana count is below the
  printed fixed cost;
- **UNAVAILABLE_COLORED_PAYMENT** when that total is sufficient but every corresponding real legal
  cast shape remains unaffordable;
- **EXECUTABLE** when the engine exposes an affordable action.

Unfillable targets are reported separately. X costs, non-mana additional costs and other payment
shapes that cannot be attributed exactly by this narrow classifier remain unresolved and must fail
closed in the eventual official telemetry rather than being guessed. No filter is assumed free.

The fixed semantic qualification cases use no R1 ordering row and prove the distinguishing frozen-card
conditions with actual engine enumeration: two ordinary Urza's Mines provide enough total mana but
not green for Malevolent Rumble; one Mine is insufficient total mana for Myr Retriever; two Mines
make Myr Retriever executable. The existing event-metric replay and all previously accepted seed-free
fixtures remain required.

A green qualification is component evidence only. It does not complete deterministic conversion,
runner/replay, effective-rules provenance, durable claim/journal, runtime binding or execution
authorization. Official R1 remains **0/512 allocations initialized, zero actions submitted and zero
comparative outcomes exposed**.
