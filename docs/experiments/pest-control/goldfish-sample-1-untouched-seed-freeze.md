# Pest Control v1.0 — Goldfish Sample #1 Untouched Seed Freeze

Status: frozen before execution. This vector is owned only by Project Pest Control.

- Accepted policy head: `aff4a349390d3c8ac4dfcbd3479fe69d56c481da`
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-1-untouched-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `f7012b5807621453692699055c90037bcf44526b4bc59edb37205c221aff9365`
- Derivation domain: `Project Pest Control v1.0 Goldfish Sample #1 untouched performance vector after aff4a349390d3c8ac4dfcbd3479fe69d56c481da 2026-09-13`

Before freezing, a read-only audit covered all 28 local/remote refs currently available in the
repository. It scanned seed/vector/smoke/regression/performance/optimization paths and seed-bearing
source text, normalized hexadecimal and decimal values, and excluded 1,469 distinct numeric values.
The resulting vector has zero overlap with that exclusion set and zero overlap with all three prior
Pest Control vectors. All prior Pest execution entry points remain hard-disabled.

The vector must run exactly once, in CSV order, with no rerolls, replacements, exclusions, seed,
deck, policy, or telemetry changes after execution starts. A defect rejects the whole block.
