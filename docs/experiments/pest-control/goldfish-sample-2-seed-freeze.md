# Pest Control v1.0 — Goldfish Sample #2 Seed Freeze

Status: frozen before execution. This independent replication vector belongs only to Project Pest
Control.

- Accepted Sample #1 head: `4ccd4f097ade866a8eb3eff42e897229cd34e29f`
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-2-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `1e4247fceaa9a7d2f438ab8fa29733782f624cbcde383ec858153de1367e522d`
- Derivation domain: `Project Pest Control v1.0 Goldfish Sample #2 independent replication after 4ccd4f097ade866a8eb3eff42e897229cd34e29f 2026-09-13`

Before freezing, a read-only audit covered all 28 local/remote refs currently available. It scanned
seed/vector/smoke/regression/performance/optimization paths and seed-bearing source text, normalized
hexadecimal and decimal values, and excluded 1,500 distinct numeric values. The resulting vector has
zero overlap with that exclusion set or any prior Pest Control vector.

The validated Sample #1 deck, agent profile, engine, telemetry definitions, mulligan behavior,
horizon, and acceptance criteria are unchanged. The vector must run exactly once in CSV order. No
rerolls, replacements, exclusions, seed substitutions, deck, policy, telemetry, or mid-sample
changes are permitted. A clear defect rejects the entire block.
