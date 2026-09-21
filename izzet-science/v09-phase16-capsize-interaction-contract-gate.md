# v0.9 Phase 16 — Paired Capsize Interaction Contract Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Disposition: `V09_PHASE16_SEED_FREE_GATE`

## Question

Can a future control-versus-accepted-policy pilot measure fixed-event Capsize
readiness without turning mana availability into an unsupported claim about tempo,
survival, politics, or match wins?

## Paired estimands

At each turn T1–T10, after the deterministic turn policy completes selection,
tutoring, commander deployment, and mana development, preserve the full matched-pair partition (`both`,
`control_only`, `policy_only`, `neither`, and signed policy-minus-control delta) for:

1. Capsize present in that residual response window;
2. one-shot response to one legal hostile permanent with no ward or response;
3. the same response with Capsize retained through buyback;
4. self-bounce rescue readiness for Izzet Guildmage;
5. an opposing commander whose owner chooses hand;
6. an opposing commander whose owner chooses the command zone;
7. primary-combo lethal readiness;
8. cumulative primary lethal; and
9. commander battlefield presence.

The two opposing-commander branches must be pairwise identical at this stage. The
destination choice changes downstream replay rules, not whether Capsize can legally
be cast. No commander-tax benefit may be inferred from the hand branch.

## Fixed failures

The contract binds two explicit failure branches. If Capsize is countered, or if its
only target is illegal at resolution, the spell does not resolve and buyback does
not retain it. These are mechanics controls, not sampled opponent frequencies.

## Fail-closed invariants

- Exact accepted-control identity and ten-turn horizon.
- Exact source, master seed, sample count, child-seed derivation, and pair order.
- Complete Boolean telemetry in both arms; policy events are forbidden in control.
- Tutor event identity and monotone cumulative event ledgers.
- Every paired partition sums to the sample count and every delta preserves direction.
- One-shot readiness is a subset of Capsize presence.
- Buyback readiness is a subset of one-shot readiness.
- Self-rescue readiness is a subset of both one-shot readiness and commander presence.
- Commander destination branches remain exactly identical.
- Cumulative lethal never decreases in either arm.
- Duplicate JSON keys, non-finite values, malformed schemas, and inconsistent
  cross-metric identities fail closed.

## Authorization

The seed-free validator must pass deterministic semantics, trajectory-equivalence,
synthetic paired aggregation, strict JSON parsing, and seventeen adversarial
rejections. It assigns and consumes no experimental seed and exposes no sampled
outcome.

Success authorizes only a subsequent cost-controlled invariant qualification of the
complete paired path. It does not authorize an official pilot, a new seed, a card
change, or a matchup/win-rate claim. The accepted Phase-14 tutor policy remains the
only behavior-policy promotion, and v0.7 remains the exact card control.
