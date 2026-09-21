# v0.9 Position 1 — Engine Gap Triage Gate

Disposition: `V09_POSITION1_ENGINE_GAP_TRIAGE_GATE_OPEN`

Authoritative unresolved inventory:
`izzet-science/position1-engine-gap-inventory.json`

## Research question

For each of the 55 unresolved frozen card identities, determine whether the missing
coverage is:
1. registry/card-definition only;
2. existing reusable engine mechanic plus card wiring;
3. new engine mechanic required;
4. multi-face/modal/special-cost support required.

Separately preserve the five PDH-specific gameplay blockers.

## Authorized work

- inspect existing card implementations and engine primitives;
- classify every unresolved identity with concrete implementation dependencies;
- identify reusable mechanics and minimal implementation batches;
- add no official-game execution path;
- consume no official seed;
- do not change either frozen deck.

## Exit criterion

Produce a complete machine-readable triage covering all 55 identities exactly once,
with no unresolved classification placeholders, and qualify it in CI.

The next gate may implement the smallest reusable mechanic/card batch justified by
that triage. Position 1 remains blocked until all cards reachable in the official
game and all PDH-specific semantics are qualified.
