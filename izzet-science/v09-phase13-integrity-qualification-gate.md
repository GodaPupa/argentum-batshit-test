# v0.9 Commander-Independent Readiness — Phase 13 Integrity Qualification

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Purpose

Exercise the complete paired simulation and Phase-12 aggregation path cheaply before
assigning another experimental seed. This directly addresses two prior cases where
small deterministic fixtures passed but the sole official run exposed a reachable
contract defect.

## Qualification boundary

- 1,024 matched pairs / 2,048 trajectories;
- accepted v0.7 deck in both arms;
- fixed master coordinate `1`, already public in paired RNG regression vectors;
- T1–T10 complete control and Capsize-policy trajectories;
- Phase-10 shared exact payment engine;
- Phase-12 repeated-acquisition summary schema;
- both exposed runners required to be retired before iteration;
- no summary serialization, metric printing, artifact, effect estimate, or outcome
  interpretation.

The sweep exists only to prove that a nontrivial set of complete trajectories can
traverse generation, repeated tutor-event handling, matched-pair aggregation, and
strict summary validation without an exception. Reusing public regression coordinate
`1` avoids assigning or burning another candidate experimental seed.

## Authorization

This qualification is not admissible performance evidence. It emits no outcome
fields and cannot support promotion or rejection of the policy. It assigns and
consumes zero experimental seeds and authorizes no official pilot.

No card or policy earns promotion. v0.7 remains the accepted control.

Disposition: `V09_PHASE13_INVARIANT_SWEEP_VALIDATED`
