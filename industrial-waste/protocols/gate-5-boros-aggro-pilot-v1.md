# Gate 5 Boros Aggro preboard capability pilot v1

Status: completed; valid; sampling closed without replication or promotion.

## Inputs

- Namespace `IW-G5-BOROS-PILOT-V1`; exactly two fresh, nonzero, disjoint seeds.
- Vector SHA-256 `2ee9c8ae46f69164b181311f7fe0a83f125ce898c7ba5fe72877c41bc1ba580a`.
- Frozen v1.0 Control and exact Pactdoll-A versus the provenance-locked Marco Garrido Aboy Boros
  Aggro maindeck from 2026-09-12.
- Every seed is played once from each seat for each Industrial list: eight games total.
- Industrial uses policy v2. Boros uses the qualified `boros-gate-5-readiness` specialist.
- London mulligans; preboard 60s only; 16 turns per seat; 4,000 accepted actions.

The vector was derived deterministically from the new namespace, checked against every registered
Industrial Waste namespace, and has no overlap. No outcome was inspected while freezing it.

## Validity and decision rule

Reject the entire pilot for an exception, illegal action, unregistered card, seed/digest mismatch,
missing seat rotation, or draw other than a declared turn/action cap. This pilot is capability and
screening evidence only; it cannot promote a deck or authorize postboard work.

If valid, Boros must win at least one game to establish opponent pressure. Industrial Waste must win
at least one game across the two lists to establish fair-game capability. A failure of either gate
closes Boros sampling for diagnosis without allocating replacement seeds.

Only a Pactdoll-A advantage of at least one win over Control may authorize one fresh, predeclared
replication. A tie or Control lead closes this opponent without a challenger promotion. Absolute
results remain descriptive at this sample size and are not pooled with Burn evidence.

## Result

GitHub Actions run 35561796056 completed successfully and uploaded the canonical artifact. An
independent audit found all eight expected `(deck, seed, seat)` cells exactly once, with the frozen
vector digest, zero exceptions, zero illegal actions, and zero draws.

Control went 2-2 and Pactdoll-A went 1-3; Boros went 5-3 overall. Boros therefore cleared the
opponent-pressure floor, and the combined three Industrial wins cleared the fair-game-capability
floor. Control led Pactdoll-A by one win, so the challenger failed the only condition that could
authorize a fresh replication.

Supporting diagnostics do not reverse that decision. Each list assembled Tron by turn 5 once.
Pactdoll-A produced the pilot's only combo-ready game and won it on turn 7, while Control's two wins
were fair-game wins on turns 10 and 11. Mean colored-mana-failure turns were 2.50 for Control and
2.75 for Pactdoll-A; neither list mulliganed.

Decision: close Boros sampling. Do not allocate replication seeds, do not promote Pactdoll-A, and
do not authorize postboard work. The exact v1.0 Control remains frozen and unchanged.
