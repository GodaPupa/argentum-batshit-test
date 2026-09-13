# Pest Control v1.0 — Goldfish Sample #1 fresh performance seed freeze

These 30 seeds are frozen before execution for the authorized Pest Control v1.0 Goldfish Sample #1
fresh performance/engine sample. The entire vector and its order become immutable before the first
game: no rerolls, replacements, exclusions, substitutions, deck changes, policy changes, telemetry
changes, or mid-sample corrections are permitted.

- Accepted Weather regression head: `8a13f2d8f9322b85ba6c52de571c591fea966a31`
- Shared Argentum change at the accepted gate: **yes**
- Deck: permanent frozen Pest Control v1.0 main deck, no sideboard
- Agent: currently validated generic Argentum/Pest Control solitaire policy
- Telemetry: accepted actionable-mana, sacrifice-mana provenance, Weather, and Pest engine telemetry,
  plus pre-execution descriptive counterfactual-Warden aggregation
- Count: 30 unique deterministic seeds
- Derivation: first 63 bits of SHA-256 over
  `Project Pest Control v1.0 Goldfish Sample #1 fresh performance sample 2026-09-13 authorized after Weather regression head 8a13f2d8 seed N`,
  incrementing `N` from 1 through 30
- Vector SHA-256: `c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513`
- Overlap audit: zero normalized numeric matches after inspecting 28 local/remote refs, 135,029
  seed-related lines/file contents, 1,019 seed-path instances, and 2,495 distinct available numeric
  values across all fetched Pest Control, Batshit/Affinity, Project X, development, regression,
  smoke, performance, and optimization material
- Execution status: executed exactly once from green preflight head
  `9f3f3c1cc8270c3bbd55b8017c0935dbbee7771f`; entire sample rejected by manual agent-sanity audit

The authoritative vector is
`gym/src/test/resources/pest-control-v10-goldfish-sample-1-performance-seeds.csv`.

This sample is development/engine goldfish evidence, not matchup evidence. A clear rules/state,
telemetry, mana-provenance, mana-legality, terminal-reporting, Weather/Follow sequencing,
payoff-accounting, Thrall/Scion lifecycle, Ent-decision, actionable-bottleneck, or solitaire-agent
defect rejects the entire sample without removing or replacing any game.

Both earlier Pest vectors remain permanently retired. This new vector has a separate identity and
must not rehabilitate either rejected execution or its regression replay as performance evidence.

The execution produced all 30 games and passed automated invariants, but Game 15 exposed a clear
Weather/Follow policy defect. The sample is rejected in full and preserved in
`goldfish-sample-1-performance-{rejected,audit-rejected}.{json,md}` as applicable. It must not be used
as performance evidence. No replay or corrective change is authorized automatically.
