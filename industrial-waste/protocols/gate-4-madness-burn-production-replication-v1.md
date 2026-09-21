# Gate 4 Madness Burn production-profile replication v1

Status: frozen; unexecuted.

## Question

Does Pactdoll-A's +2-win preboard advantage over immutable v1.0 Control against production-profile
Madness Burn reproduce on a fresh, disjoint vector?

## Frozen inputs

- Namespace: `IW-G4-MADNESS-BURN-PROD-R1`.
- Eight deterministic namespace-derived signed 64-bit seeds, unique, nonzero, and disjoint from all
  registered Industrial Waste seeds.
- Vector SHA-256: `4f12f51b6535464629de73e2dc72260426cfb2277ad52e8ff4287d6a5f35bab7`.
- Frozen v1.0 Control and exact unpromoted Pactdoll-A.
- Unchanged `industrial-waste-policy-v2` versus `PRODUCTION_CANDIDATE_EXPIRING`.
- Exact sourced Canevazzi Madness Burn 60; preboard only; London mulligans enabled.
- 16 turns per seat and 4,000 accepted actions.

Each seed is played with both Industrial lists from both seats: 32 games. Eight one-seed CI jobs
avoid the calibrated test-timeout boundary. Every seed is used once; no rerolls or replacements.

## Validity and decision gates

Reject the entire replication for any failed CI job, digest mismatch, missing or duplicate
`(deck, pair, seat)` key, exception, rejected action, unapproved draw, or seed overlap.

Madness Burn must win at least 4/16 against each list. The Pactdoll-A direction is confirmed only if:

1. Pactdoll-A wins at least one more replication game than Control; and
2. across the frozen screen plus this replication, Pactdoll-A leads Control by at least three wins.

This requires independent same-direction movement while allowing normal small-sample variation from
the screen's exact +2 threshold. If either condition fails, the Burn-specific advantage is not
replicated and no further Burn sampling is allowed. If both pass, record a replicated Burn-specific
advantage and move to the next implementation-feasible preboard gauntlet opponent. Neither result
can promote Pactdoll-A or authorize postboard work by itself.

Tron by turn 5, combo-ready incidence, mulligans, colored-mana failures, and paired win discordance
remain supporting diagnostics and cannot override the win gates.
