# Pest Control v1.0 — Goldfish Sample #2 Take 4 Seed Freeze

Status: executed exactly once and formally rejected. The vector is permanently retired and
hard-disabled. This independent replication vector belongs only to Project Pest Control.

- Accepted observability implementation: `e316117ee71b30b170e241c8eb110bf8d4d20117` (CI #224 green)
- Final validated documentation head: `aeb0df41c25c762acd37f2abf301f612fa7c2c28` (CI #225 green)
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-2-take-4-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `9f53ae30fd233bc8a980163aff9a7df1d6b41095356072e520a2cad16f97a7ad`
- Derivation domain: `Project Pest Control v1.0 Goldfish Sample #2 Take 4 independent replication after remotely green head aeb0df41c25c762acd37f2abf301f612fa7c2c28 2026-09-13`
- Derivation: for counters beginning at 1, SHA-256 of `<domain>:<counter>`, take the first eight
  bytes as an unsigned big-endian integer, clear the sign bit, and reject zero, duplicates, and any
  value in the repository-wide exclusion set.

Before freezing, a read-only audit covered all 40 fetched local, remote, and pull-request refs,
collapsing identical ref tips to 24 distinct trees. It inspected 548 seed-category path instances,
103 unique seed-category blobs, and 73,693 seed/category-bearing lines, normalizing 1,692 positive
`Long` values. The resulting vector has zero overlap with that set and therefore zero overlap with
every known Pest Control, Batshit Economics, and Project X development, regression, smoke,
performance, and optimization seed available in the repository.

The validated Pest Control v1.0 deck, agent profile, engine, telemetry definitions, mulligan behavior,
horizon, metric set, observability schema, and acceptance standard remain unchanged. The complete
vector must run exactly once in CSV order. No rerolls, replacements, exclusions, seed substitutions,
deck, policy, telemetry, or mid-sample changes are permitted.

Before interpreting gameplay, the final machine-readable artifact must pass the mandatory audit-
completeness gate for every sequencing evaluation and friendly-removal evaluation or execution. Any
missing required field, or any clear rules/state, telemetry, observability, mana-provenance,
sequencing, or agent-policy defect, rejects the complete block. No game had been executed when this
freeze was recorded.

Sample #1 remains the sole accepted Pest Control performance/engine sample. The original rejected
Sample #2 and rejected Take 2 and Take 3 vectors remain permanently retired and hard-disabled. Games
13 and 14 of Take 3 remain historically unclassifiable, not policy failures. Pest Control v1.0 remains
exact, and the challenger remains audit-only and unconstructed.

## Post-execution disposition

The complete vector executed exactly once in frozen order from remote freeze head
`0e5966c5069f8b8b71f8557aceaeaecd0920c0c3`, which passed CI #226 before Game 1. Take 4 is formally
rejected for audit-observability defects: Games 8, 18, and 23 executed Bone Shards without a selected
friendly-removal valuation record, and the predeclared completeness checker falsely failed five
sequence-summary cross-checks in Games 1, 9, 16, and 23. Details are preserved in
`goldfish-sample-2-take-4-rejection-audit.md`.

The vector may never be replayed, rehabilitated, replaced, compared against, optimized against, or
reused. Its raw artifact and generated report are quarantined historical records, not performance
evidence.
