# v0.9 Commander-Independent Readiness — Phase 8 Paired Output Contract Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Question

Can a future control-versus-Capsize-policy pilot preserve matched-pair direction,
report the intended benefits and costs, and fail closed on malformed output?

## Frozen estimands

At each turn T1–T10, six Boolean outcomes are classified into `both`,
`control_only`, `policy_only`, and `neither`:

- `capsize_present` — the access benefit;
- `capsize_buyback` — the modeled readiness benefit;
- `combo_pair` — primary-card assembly cost or benefit;
- `combo_lethal` — current deterministic-launch cost or benefit;
- `lethal_by_now` — cumulative primary-plan timing cost or benefit;
- `commander_battlefield` — deployment-tempo cost or benefit.

For every metric, `delta = policy_only - control_only`. Positive means more policy
trajectories have the outcome; interpretation still depends on whether the metric is
a benefit or opportunity cost. The four matched cells are retained so equal marginal
rates cannot conceal opposite paired movements.

The contract also reports cumulative Capsize tutor acquisitions, split into Merchant
Scroll and Drift of Phantasms. Each game may acquire Capsize through this policy at
most once.

## Frozen JSON contract

Schema identity is `izzet-v09-capsize-paired-v1`. The exact top-level fields bind:

- experimental source SHA;
- accepted-control SHA256;
- canonical 16-digit hexadecimal master seed;
- sample count and T1–T10 horizon;
- Phase-7 child-seed derivation identity;
- exactly ten ordered turn rows.

Each turn row binds its sample count, cumulative tutor totals, and the exact six
paired partitions. Counts must be non-Boolean integers in range. Every partition
must sum to `n`; every delta must recompute exactly; Scroll plus Drift must equal all
Capsize acquisitions; cumulative tutor and `lethal_by_now` counts cannot decrease;
and buyback readiness cannot exceed Capsize presence in either arm.

Strict JSON loading rejects duplicate keys and `NaN`/infinite constants. Unknown,
missing, or reordered paired-cell fields are rejected.

## Seed-free validation

A two-pair, ten-turn synthetic ledger exercised acquisition benefit, commander
deployment cost, and cumulative primary outcomes. It used Phase-7 fixture integer
`1`, not an experimental seed, and did not consume the paired game iterator.

The valid ledger passed aggregation and independent audit. Twelve adversarial cases
were rejected for missing identity, wrong source, incomplete horizon, Boolean count,
broken partition, false delta, broken tutor identity, decreasing tutor count,
readiness-subset violation, decreasing cumulative lethal, duplicate JSON key, or
non-finite JSON.

## Boundary

This gate freezes estimands and the output auditor. It does not assign a master seed,
set a pilot sample count, freeze an execution source, construct an artifact manifest,
or authorize a workflow. The next gate must freeze those execution and provenance
details before any paired game runs.

No card, control, access-rate, readiness-rate, or win-rate claim is made.

Disposition: `V09_PHASE8_SEED_FREE_VALIDATED`
