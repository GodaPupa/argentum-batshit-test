# Pest Control v1.0 — Goldfish Sample #2 Take 3 Seed Freeze

Status: executed exactly once and formally rejected. The vector is permanently retired and
hard-disabled. This independent replication vector belongs only to Project Pest Control.

- Accepted correction implementation: `14bea9a1f0c67be74aae8f48704186e2277de1b1` (CI #220 green)
- Final validated documentation head: `99be2946c20c3a10f17b9e2a9d1c0c86809a877f` (CI #221 green)
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-2-take-3-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `341fc7936a8a415d19d198662d1f6f2b2120ecb70d055a3a7a6fb76dd1ec8c47`
- Derivation domain: `Project Pest Control v1.0 Goldfish Sample #2 Take 3 independent replication after remotely green head 99be2946c20c3a10f17b9e2a9d1c0c86809a877f 2026-09-13`
- Derivation: for counters beginning at 1, SHA-256 of `<domain>:<counter>`, take the first eight
  bytes as an unsigned big-endian integer, clear the sign bit, and reject zero, duplicates, and any
  value in the repository-wide exclusion set.

Before freezing, a read-only audit covered all 40 fetched local, remote, and pull-request refs,
collapsing identical ref tips to 25 distinct trees. It inspected 1,694 seed-category path instances,
171 unique seed-category blobs, and 216,475 seed/category-bearing lines, normalizing 1,704 positive
`Long` values. The resulting vector has zero overlap with that set and therefore zero overlap with
every known Pest Control, Batshit Economics, and Project X development, regression, smoke,
performance, and optimization seed available in the repository.

The validated Pest Control v1.0 deck, agent profile, engine, telemetry definitions, mulligan behavior,
horizon, metric set, and acceptance standard remain unchanged. The vector must run exactly once in
CSV order. No rerolls, replacements, exclusions, seed substitutions, deck, policy, telemetry, or
mid-sample changes are permitted. Any clear rules/state, telemetry, mana-provenance, sequencing, or
agent-policy defect rejects the entire block. No game had been executed when this freeze was recorded.

Sample #1 remains the sole accepted Pest Control performance/engine sample. The original rejected
Sample #2 and rejected Take 2 vectors remain permanently retired and hard-disabled. Pest Control v1.0
remains exact, and the challenger remains audit-only and unconstructed.

## Post-execution disposition

The 30 seeds executed once in frozen order from remotely green freeze head
`d9bddfe67f78232e12b09003a239a0890c4f65d8` (CI #222). The sample is rejected because the preserved
Weather record omitted two required sequencing-classification fields and removal actions omitted
their targets and additional-cost choices. The complete audit is recorded in
`goldfish-sample-2-take-3-rejection-audit.md`. The vector may never be replayed, rehabilitated,
replaced, compared, optimized against, or reused.
