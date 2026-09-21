# v0.9 Phase 20 — Public Event Ledger and Opponent Policy Contract Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Disposition: `V09_PHASE20_SEED_FREE_GATE`

## Question

Can the accepted Phase-19 Capsize policy receive deterministic event classes from
an auditable public-state ledger without hidden information, random event sampling,
or caller-supplied severity labels?

## Contract

Each ledger entry binds stable event, policy, response-window, source, and target
identities; positive turn and sequence numbers; target legality and class; and a
tuple drawn only from four public facts:

1. loss before the next response opportunity;
2. the event targets the player's Izzet Guildmage;
3. the primary combo is currently lethal; and
4. the event prevents the currently available next-main plan.

Classification is derived in the accepted priority order: loss, qualified lethal-
combo Guildmage removal, next-main lock, then tempo-only. The ledger rejects
unknown or contradictory facts, mixed windows, mixed turns, mixed policies,
duplicate identities, and nonmonotonic sequences. Its opponent-policy declaration
must be deterministic, public-only, hidden-information-free, and nonsampling.

Phase 19 cannot encode a nonlethal Guildmage target as `tempo_only`. Phase 20 keeps
that entry in the audit ledger but omits it from the response-policy feed. It also
rejects loss and next-main-lock labels on an own-Guildmage target instead of
silently translating an event the frozen consumer cannot represent.

## Gate and exclusions

The validator exhausts all 96 combinations of target class, target legality, and
public-fact subset; replays a mixed window; integrates the compiled events with the
accepted Phase-19 selector; and rejects eighteen malformed or contradictory inputs.
It consumes no paired iterator and assigns no experimental seed.

This contract does not prove the public facts. A later opponent adapter must derive
them from a frozen game-state vocabulary and preserve its own evidence. This phase
defines no opponent deck, action frequency, threat arrival, recast policy, tempo,
survival, politics, or win rate. Success authorizes only a seed-free concrete
opponent-adapter vocabulary and fixture suite. It authorizes no sampled pilot, card
change, or behavior-policy promotion. v0.7 remains the exact card control.
