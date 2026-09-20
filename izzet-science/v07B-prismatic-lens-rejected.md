# v0.7-B Prismatic Lens — Rejected Pilot

Run: 35534598056
Job: `izzet-prismatic-lens-v07b`
Artifact: 10612511561
Artifact ZIP SHA256: `fd321f1ee8e1b6d7eec7744af04048571504f84e7dfd736c8ad9b3cb4b62f47f`
Control output SHA256: `7b32f15988e23943411f30cda5298d6cb584fa979b33bee15ae06c5790a46c25`
Challenger output SHA256: `82e4b89c6be8dd91a8c71a5d6777b882923fd6847928e1e8b3a4d62b83f12605`
Manifest SHA256: `5974ef4310cd63a3769f7fcc157356530e0668876921050d2d4cad7a8f154b87`
Source: `5664537b7f8e5e67fdb1620559252b0bd988616b`
Source CI: 35534493450 (success)
Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
Challenger SHA256: `7c0e16f35518b0f9a1c73b583537a39311b81b2a809501ec95fd5ff1b54255b8`
Seed: `0x1A22E7004`
Samples: 10,000 per deck
Horizon: T1–T10
Change: `-1 Lose Focus, +1 Prismatic Lens`

## Integrity

- The source commit passed CI before experimental dispatch.
- The workflow verified both deck hashes before simulation.
- The job, provenance manifest, and artifact upload completed successfully.
- GitHub's artifact digest matches the downloaded ZIP.
- Both output digests match the manifest.
- Both outputs contain exactly ten standard and ten interaction rows.
- The paired run used the frozen seed, sample count, horizon, source, and one-slot
  challenger identity.
- Run 35534598056 is the sole Izzet execution associated with source `5664537b`.
- No rerun, replacement seed, pooled estimate, or post-outcome threshold change occurred.

## T10 paired result

| Frozen criterion | Control | Challenger | Change | Result |
|---|---:|---:|---:|---|
| Immediate taxed commander recovery | 3.36% | 3.57% | +0.21 pp | **Fail**; required +0.50 pp |
| Conditional stack protection | 3.10% | 2.28% | -0.82 pp | **Fail**; +0.21 pp recovery gain does not cover loss |
| Generic guaranteed stack protection | 4.71% | 5.06% | +0.35 pp | Pass |
| Generic guaranteed targeted-removal protection | 5.20% | 5.51% | +0.31 pp | Pass |
| Current-turn lethal state | 7.34% | 7.63% | +0.29 pp | Pass |
| Pair assembly | 10.85% | 10.90% | +0.05 pp | Pass |
| U execution | 96.05% | 96.00% | -0.05 pp | Pass |
| R execution | 84.74% | 84.83% | +0.09 pp | Pass |
| Dramatic Reversal positive readiness | 9.58% | 11.58% | +2.00 pp | Pass |
| Identity and artifact validation | — | — | — | Pass |

Prismatic Lens improved every targeted mana-readiness metric except U execution,
whose small decline remained within the guardrail. However, its +0.21 pp recovery
gain was below the preregistered minimum and materially smaller than the conditional
protection readiness lost with Lose Focus. The pilot therefore fails criteria 1 and 2.

## Disposition

`V07B_PILOT_REJECTED`

`V07B_CONFIRMATION_NOT_AUTHORIZED`

`V06_CONTROL_UNCHANGED`

`NO_CHALLENGER_PROMOTED`
