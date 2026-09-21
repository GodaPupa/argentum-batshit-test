# v0.9 Phase 22 — Deterministic Opponent Action Policy Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Disposition: `V09_PHASE22_SEED_FREE_GATE`

## Question

Can a future opponent model select one legal action deterministically from the
accepted Phase-21 public vocabulary without consulting the Izzet player's hand,
opponent hidden zones, random order, or future outcomes?

## Frozen public heuristic

The selector ranks already-legal candidate actions by:

1. a known public consequence that loses the game before the next response;
2. removal targeting the battlefield Izzet Guildmage;
3. a restriction intersecting the frozen next-main object or action requirements;
4. every other tempo-only action.

Equal-priority candidates use stable event identity. No-candidate windows pass.
Every candidate must share policy, response-window, turn, and decision-sequence
identity. An illegal candidate, hidden-information marker, duplicate identity,
mixed decision window, or malformed Phase-21 event contaminates the whole choice
set and fails closed.

Guildmage removal is prioritized from its public battlefield identity alone. The
opponent selector never receives the responder's hand or `primary_combo_lethal_now`
predicate. This is a conservative reproducible heuristic, not evidence that the
priority order is strategically optimal.

## Gate and exclusions

The validator covers every priority, stable tempo ties, empty-window pass behavior,
all 24 permutations of a four-action window, 96 exhaustive public threshold/action-
availability states, an end-to-end handoff through Phase 21, Phase 20, and Phase 19,
and twelve malformed or contaminated inputs. It assigns no experimental seed and
consumes no paired iterator.

This phase does not generate legal actions, define an opponent deck, assign event
frequency, model future choices, or claim tempo, survival, politics, or win value.
Success authorizes only a seed-free concrete opponent action generator and fixture
suite for a separately frozen opponent identity. It authorizes no sampled pilot,
card change, or behavior-policy promotion. v0.7 remains the exact card control.
