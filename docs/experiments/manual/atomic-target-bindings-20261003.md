# Atomic spell target bindings

TargetingEvents now updates existing modal and splice slices and divided
allocations in the same immutable state update as the flat targets. Slice
positions come from the original durable slices, never current target legality.
Mismatched slices and ambiguous target-keyed allocation merges/splits fail
without mutation. Existing newly-targeted event emission remains shared.

This does not register Spellskite. Target-group cardinality still must be
announced and retained, including omitted optional groups; the original
five-case diagnostic is intentionally retained to demonstrate that boundary
after these production repairs. Activated/triggered ability slice orchestration
and a legality-aware fixed-destination one-slot operation are not qualified.
No registry or gameplay admission.
