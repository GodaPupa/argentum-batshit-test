# Pest Control v1.0 — fresh Goldfish Sample #1 seed freeze

These 30 seeds are frozen before execution for the authorized fresh Pest Control v1.0 Goldfish
Sample #1 performance/engine baseline. Once execution begins, the vector and its order are immutable:
no rerolls, replacements, exclusions, substitutions, deck changes, policy changes, or telemetry
changes are permitted.

- Accepted regression gate: `27b82421d34cf2e61b7eb106b59ed2809bb50013`
- Deck: permanent frozen Pest Control v1.0 main deck, no sideboard
- Agent: currently validated generic Argentum/Pest Control solitaire policy
- Telemetry: corrected actionable-bottleneck and sacrifice-mana provenance telemetry
- Count: 30 unique deterministic seeds
- Derivation: first 63 bits of SHA-256 over
  `Project Pest Control v1.0 Goldfish Sample #1 fresh performance baseline 2026-09-13 seed N`,
  incrementing `N` from 1
- Vector SHA-256: `50d831076ff08c5df70aaa21e6edf7deaf9c269eeb74f0b5f9981b8be51caad8`
- Overlap audit: zero numeric matches after inspecting 27 fetched remote refs, 50,553
  seed-related lines/files, and 1,711 distinct available numeric seed values across Pest Control,
  Batshit/Affinity, Project X, development, regression, smoke, performance, and optimization material
- Execution status: not started

The authoritative vector is
`gym/src/test/resources/pest-control-v10-goldfish-sample-1-fresh-seeds.csv`.

This is development/engine goldfish evidence, not matchup evidence. A clear rules/state, telemetry,
mana-provenance, mana-legality, terminal-reporting, Weather/Follow sequencing, payoff-accounting,
Thrall/Scion lifecycle, Ent-decision, actionable-bottleneck, or solitaire-agent defect rejects the
entire sample without removing affected games.
