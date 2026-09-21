# v0.9 Phase 26 — Public-State Action Compiler Gate

Disposition: `V09_PHASE26_PUBLIC_STATE_COMPILER_GATE_OPEN`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

Parent acceptances:
- Phase 24 real opponent identity accepted.
- Phase 25 rules-sourced action-surface map accepted.
- Zero matchup games run.
- Zero experimental seeds assigned or consumed.
- Hidden-hand generation remains unauthorized.

## Research question

Can the accepted Phase-25 action-surface map be compiled deterministically into legal public-state opponent actions without leaking hidden information or waiving printed restrictions?

## Authorized scope

Build a seed-free compiler contract that consumes only:
- public battlefield permanents and counters;
- public graveyards/exile where rules permit;
- observed/revealed opponent cards;
- current phase/step and priority-relevant timing;
- tapped/untapped state;
- summoning-sickness state;
- commander zone/battlefield status and commander-damage ledger;
- explicit target candidates already legal from public information.

The compiler may emit only actions whose legality is derivable from those inputs.

## Required fail-closed checks

1. Mana
- exact color/generic payment;
- tapped sources cannot be reused;
- summoning-sick creatures cannot activate tap abilities;
- conditional/public mana must respect printed restrictions.

2. Timing
- sorcery-speed surfaces only at legal times;
- instant/flash surfaces only when priority/timing permits;
- attack-only and combat-triggered surfaces only in correct steps.

3. Targets
- target count and type restrictions;
- protection/hexproof/shroud/public ward constraints where observable;
- self/opponent/permanent/player distinctions.

4. Commander pressure
- 16-damage PDH commander threshold frozen from Phase 25;
- commander entry/power/trample/untap/Aura scaling only from mapped public effects;
- no hidden pump/protection assumptions.

5. Information boundary
- no private hand inspection;
- no hidden land-access inference beyond observed/revealed information;
- no future draw or seed access;
- no action may be emitted solely because an unobserved card exists in the frozen 99.

6. Determinism
- same public state + same observed-card set => byte-identical compiled action set;
- canonical stable ordering independent of hash-map/set iteration order.

## Qualification fixtures

At minimum:
- legal public mana development;
- tapped-source rejection;
- summoning-sick tap-ability rejection;
- legal and illegal target cases;
- timing-bound removal/protection examples;
- commander scaling and commander-damage pressure examples;
- hidden-hand contamination rejection;
- deterministic replay equality.

## Explicitly unauthorized

- opponent hidden-hand generation;
- mulligan policy;
- random action selection;
- sampled matchup positions;
- experimental seed generation or consumption;
- outcome exposure;
- deck changes;
- v0.7 promotion/replacement.

## Exit criterion

Phase 26 may be accepted only after a fresh seed-free CI qualification proves the compiler contract and all fail-closed fixtures. Acceptance authorizes later observed-action behavior work only; it does not authorize a sampled matchup pilot.

Next gate after acceptance: deterministic observed-action response behavior over concrete public states, still seed-free.

