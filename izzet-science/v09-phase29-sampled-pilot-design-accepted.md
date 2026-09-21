# v0.9 Phase 29 — Sampled Matchup Pilot Design Accepted

Disposition: `V09_PHASE29_SAMPLED_PILOT_DESIGN_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

## Provenance

- Gate: `izzet-science/v09-phase29-sampled-pilot-design-gate.md`
- Branch head under test: `09acca04dcc6131a2656e1ea291b0362c3d32425`
- Pull-request merge-ref source recorded by artifact: `03d80d19381ec268203c65e6177a74ec6fbfd544`
- Workflow run: `35571979941` — success
- Job: `106245325972` — success
- Artifact: `10626835043`, `izzet-v09-phase29-sampled-pilot-design`
- Artifact ZIP SHA256: `89777a65d6d4fd6d0b315112f2c3514f637403f120ba1845611d629be6363274`
- Manifest SHA256: `e393b3ea4fe126bd00e3996322e7c0a23906f4b11d4cd5d554c1362a6bf779f3`
- Validation transcript SHA256: `088f82ad25d3979b347f22eb59c88797e92be50c84f0936899756cc7e2cd1f2d`
- Gate SHA256: `f404cd9e4c916b569deb9786145e31d30ea4dbedabd355778f582c4b7ba9b3cb`
- Validator SHA256: `6b56da1af4861d23092c2d3772b4fad6d6bdd3eb750ee48df91ad82b63796ed0`
- General repository CI for branch head: success.

Independent artifact audit matched GitHub's ZIP digest exactly. The archive contained
exactly two nonempty files, `manifest.txt` and `validation.txt`, and the validation
transcript contained exactly `V09_PHASE29_SAMPLED_PILOT_DESIGN_VALIDATION_PASS`.

## Qualified frozen pilot design

- exactly 12 games;
- 6 Izzet play / 6 Izzet draw;
- fixed sample size, no adaptive widening;
- fresh unique nonzero signed 64-bit seeds from an OS cryptographic source, but only
  after later seed-generation authorization;
- complete seed-registry audit and frozen vector required before execution;
- no rerolls, regeneration, outcome-conditioned replacement, or reuse;
- descriptive analysis only;
- no card promotion/rejection or matchup-strength claim from this pilot alone;
- fail-closed invalidation for illegal action, hidden-information leak, seed
  mismatch/reuse, identity divergence, nondeterministic replay, missing ledger, or
  terminal-state accounting defect;
- canonical artifact is the sole outcome-exposure surface.

## Decision

Accept Phase 29 as methodology only. This acceptance does not authorize seed
generation or game execution.

Counters at acceptance:
- experimental seeds generated: 0
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

The exact v0.7 100 remains frozen.

## Next gate

Construct the 12-position runner and event-ledger schema under deterministic,
seed-free fixtures. Prove complete observability, replay determinism, play/draw
assignment handling, terminal accounting, and fail-closed invalidation before any
experimental seed is generated.
