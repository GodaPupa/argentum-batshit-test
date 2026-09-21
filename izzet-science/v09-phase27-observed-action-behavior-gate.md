# v0.9 Phase 27 — Observed-Action Behavior Gate

Disposition: `V09_PHASE27_OBSERVED_ACTION_BEHAVIOR_GATE_OPEN`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

Parent acceptance:
- Phase 26 public-state action compiler accepted.
- Compiler legality boundary is frozen for this gate.
- Experimental seeds consumed: 0.
- Sampled games: 0.
- Outcome exposure: 0.

## Research question

Given only legal actions emitted by the accepted Phase-26 public-state compiler,
can the Veteran Beastrider behavior layer choose one deterministic observed action
from a concrete public state without hidden information, randomness, or future
knowledge?

## Authorized scope

The behavior layer may consume:
- the canonical ordered set of legal `CompiledAction` values emitted by Phase 26;
- public Izzet battlefield/object identities;
- current public life and commander-damage totals;
- a frozen public next-main plan consisting only of object/action tags already
  derivable from visible Izzet state.

It may return exactly one selected action or pass.

## Frozen priority contract

Priority is descriptive of the simulator policy only, not a claim about optimal
human play.

1. Publicly terminal/imminent commander-pressure action.
2. Legal action that removes or disables Izzet Guildmage when Guildmage is public.
3. Legal action that blocks an explicitly required next-main object/action.
4. Other targeted control action.
5. Commander power/trample development.
6. Mana development / aura access.
7. Other legal tempo action.
8. Stable canonical tie-break by action ID, target ID, then payment-source IDs.

## Fail-closed requirements

- reject any action not emitted by the Phase-26 compiler;
- reject duplicate action identity tuples;
- reject hidden/private fields;
- reject noncanonical public-plan inputs;
- no randomness, seed, deck, hand, library, future draw, or opponent-private zone;
- repeated identical inputs must return byte-identical decisions;
- candidate input ordering must not change the selected result.

## Qualification fixtures

At minimum:
- Guildmage-removal precedence;
- next-main lock precedence;
- generic control versus development;
- canonical tie-break;
- pass with no legal action;
- duplicate contamination rejection;
- candidate-order invariance;
- deterministic replay equality;
- public terminal commander-pressure precedence.

## Explicitly unauthorized

Hidden-hand generation, mulligan behavior, draw simulation, sampled matchup states,
experimental seeds, game execution, outcome exposure, deck changes, or card-control
promotion.

## Exit criterion

A fresh seed-free CI run must pass the behavior fixtures, frozen v0.7 identity
check, Phase-26 parent-presence check, and no-experimental-execution-surface audit.

Acceptance authorizes only the next seed-free integration gate between compiler and
behavior. It does not authorize sampled matchup execution.
