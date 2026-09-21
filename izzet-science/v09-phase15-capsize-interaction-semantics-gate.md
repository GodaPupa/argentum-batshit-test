# v0.9 Phase 15 — Capsize Interaction Semantics Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Disposition: `V09_PHASE15_SEED_FREE_GATE`

## Question

Can the accepted Capsize tutor policy enter opponent-aware testing without assigning
synthetic value to a card that the existing harness only recognizes as present and
mana-ready?

## Scope

This gate models one declared permanent and one Capsize spell. It validates:

- legal opponent and self targets, including hexproof and shroud restrictions;
- exact `1UU` casting and `4UU` buyback payments through the shared mana engine;
- Goblin Electromancer's generic-cost reduction;
- resolution to hand and buyback retention;
- failure atomicity when the spell cannot be cast;
- loss of buyback when Capsize is countered or its target becomes illegal;
- separate hand and command-zone outcomes for a commander; and
- Izzet Signet, Prismatic Lens, and Star Compass payment witnesses.

An opposing commander's owner chooses whether that commander goes to hand or the
command zone. The model records both branches and does not assume that Capsize
creates commander tax.

## Exclusions

No opponent deck, threat-arrival distribution, ward payment, recast policy,
political choice, tempo score, survival claim, backup-plan win, or match win rate is
modeled. Those values cannot be inferred from mana readiness alone.

## Gate

`validate_v09_capsize_interaction.py` must pass all deterministic fixtures together
with the adjacent payment, tutor-policy, and paired-contract validators. This gate
uses no random sampling, assigns no experimental seed, and consumes no seed.

On success, Phase 16 may preregister fixed-event paired estimands for:

1. one-shot response readiness to a legal hostile permanent;
2. response readiness with Capsize retained through buyback;
3. self-bounce rescue readiness for Izzet Guildmage;
4. separate opposing-commander hand and command-zone branches; and
5. explicit countered and target-illegal failure cells.

Every row must remain a fixed-event readiness measure. No row may be labeled as
tempo, survival, or win percentage without a separately frozen opponent policy.

No card changes are authorized. The accepted Phase-14 tutor policy remains active
for future stateful modeling, and v0.7 remains the exact card control.

