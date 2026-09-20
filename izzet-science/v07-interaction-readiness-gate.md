# v0.7 Interaction-Readiness Gate — Frozen Protocol

Control: `izzet-science/v0.6-control.md`
Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`

## Purpose

Measure whether a legal primary-combo opportunity can survive one defined hostile
event. This is a readiness gate, not a multiplayer win-rate model. It must not invent
an opponent metagame, threat frequency, or political behavior.

## Phase 0 — deterministic mechanics

Before sampling, regression fixtures must prove:

1. A first command-zone cast uses the frozen conservative `U+R` payment.
2. After Guildmage is removed and returned to the command zone, its next cast costs
   `2+U+R`; three mana fails and four appropriately colored mana succeeds.
3. An exact five-mana Ritual launch cannot also hold up a one-blue protection spell.
4. The same launch with one additional blue source can hold up `Dispel` against an
   instant spell.
5. Protection is counted only when its targeting restrictions cover the declared
   hostile event. Conditional soft permission is reported separately and never
   credited as guaranteed protection.
6. Seething Song and Goblin Electromancer launch routes preserve their distinct
   resource thresholds.

Phase 0 emits no performance percentage and cannot promote a card.

## Phase 1 — fixed-event readiness

Instrument the unchanged v0.6 trajectories for these separate events:

- instant spell counters the original combined Lava Spike;
- noncreature spell removes Guildmage in response to the first copy activation;
- targeted spell removes Guildmage before the combo turn, followed by command-zone
  replacement and one commander-tax increment.

Report by turn:

- unprotected legal lethal opportunity;
- opportunity while holding a guaranteed legal answer and its mana;
- conditional-answer opportunity (`Spell Pierce`, `Prohibit`, `Lose Focus`) kept
  separate by the explicit condition;
- recovery readiness after one resolved commander removal;
- opportunity loss relative to the frozen solitaire control.

Do not combine event rows into an overall survival or win percentage.

## Sampling ladder

1. Deterministic regressions only.
2. One 10,000-game instrumentation pilot on the frozen seed.
3. A 100,000-game confirmation only if the pilot is complete, internally consistent,
   and a specific challenger has a predeclared interaction hypothesis.

No rerolls, replacement seeds, or post-outcome threshold changes.

## Acceptance and promotion

- The v0.6 deck remains the control throughout this gate.
- A challenger must be paired against v0.6 on identical trajectories.
- Failed, incomplete, duplicate, or identity-mismatched runs are inadmissible.
- A challenger cannot earn promotion from goldfish speed alone; it must improve a
  predeclared interaction metric without breaching the frozen mana and combo guardrails.

Disposition: `V07_PHASE0_AUTHORIZED`

