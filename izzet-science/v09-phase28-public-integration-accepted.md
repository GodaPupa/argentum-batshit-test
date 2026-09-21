# v0.9 Phase 28 — End-to-End Public Integration Accepted

Disposition: `V09_PHASE28_PUBLIC_INTEGRATION_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

## Provenance

- Gate: `izzet-science/v09-phase28-public-integration-gate.md`
- Branch head: `7910bc0899227e8d0daf9b5351aabfd79342ed31`
- Pull-request merge ref checkout: `6ac1d673dc3e6d1186a68ae97b9a0464d1cc77d0`
- Workflow run: `35569339437` — success
- Job: `106237469545` — success
- Artifact: `10624709117`, `izzet-v09-phase28-public-integration`
- Artifact ZIP SHA256: `5d41a8bed92e75b5067d3314797ba5db6879f17d27e4be2a15553946d3d4827e`
- Integration SHA256: `3cc0d8b14b5f3f3b0cb57e859b8875abe2347a4e432ae06b5b3e6562196ebf4a`
- Validator SHA256: `d86af7d9226712bf33a1482988e4c6185e69041c91f43d0326afdce162701430`
- Gate SHA256: `b419923e9d285032e1331c6ea94fab98959b066eabce80aba7cdb92e803565c5`
- Validation transcript SHA256: `8d9869035f379d931e11e14e370969d2c19934b1c61553403e8b2e652beeeea6`

Independent artifact audit confirmed the ZIP digest, exactly two nonempty files
(`manifest.txt`, `validation.txt`), and the terminal marker
`V09_PHASE28_PUBLIC_INTEGRATION_VALIDATION_PASS`.

## Decision

Accept the seed-free end-to-end public pipeline:
Phase 25 surfaces -> Phase 26 legality compiler -> Phase 27 deterministic behavior.

The qualification passed identity preservation, legality preservation, information
boundary preservation, invalid source/surface rejection, timing rejection,
tapped/summoning-sick rejection, order invariance, deterministic replay, and pass
behavior.

Counters at acceptance:
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

The exact v0.7 100 remains frozen.

## Next gate

Open a sampled-matchup pilot design gate only. Freeze pilot objectives, observables,
sample size, play/draw balance, seed policy, stopping rules, invalidation criteria,
and acceptance semantics before generating any experimental seed or executing any
game. No seed generation or gameplay is authorized by Phase 28 alone.
