# Pest Control v1.0 — Goldfish Sample #2 Take 2 Seed Freeze

Status: frozen before execution. This independent replication vector belongs only to Project Pest
Control.

- Remote-green correction gate: CI #217 at `9e5adc4416c1d5c2684befa9ea699be28fe6c2f0`
- Game 16/Game 28 implementation: `dd0572ab61c6a2dd3d2e76a578999c2fa822b2c0`
- Implementation status commit: `c50c7dfb0f93356adbfb6a9bc1d6f189e5f09042`
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-2-take-2-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `1db3fb1fcf4b969d9229bb2a060491a556ff5e1c8c80d327caf37698ca1c3cb7`
- Derivation domain: `Project Pest Control v1.0 Goldfish Sample #2 Take 2 independent replication after remote-green head 9e5adc4416c1d5c2684befa9ea699be28fe6c2f0 2026-09-13`
- Derivation: for counters beginning at 1, SHA-256 of `<domain>:<counter>`, take the first eight
  bytes as an unsigned big-endian integer, clear the sign bit, and reject zero, duplicates, and any
  value in the repository-wide exclusion set.

Before freezing, a read-only audit covered all 40 fetched local, remote, and pull-request refs,
collapsing identical states to 24 distinct trees. It inspected 12,721 seed-bearing ref/path instances
and 652 unique blobs across seed/vector, development, regression, smoke, performance, optimization,
goldfish, and sample material. Hexadecimal and decimal positive `Long` values were normalized into an
11,856-value exclusion set. The resulting vector has zero overlap with that set and therefore zero
overlap with every known Pest Control, Batshit Economics, and Project X seed available in the
repository.

The validated Sample #1 deck, agent, engine, telemetry definitions, mulligan behavior, horizon, and
acceptance criteria remain unchanged, with the remotely validated Game 16/Game 28 sequencing
corrections included. The vector must run exactly once in CSV order. No rerolls, replacements,
exclusions, seed substitutions, deck, policy, telemetry, or mid-sample changes are permitted. Any
clear rules/state, telemetry, mana-provenance, sequencing, or agent-policy defect rejects the entire
block. No game had been executed when this freeze was recorded.

CI #215 is stranded/non-validation because it created zero jobs. It is neither successful nor failed
validation and must never be cited as either.
