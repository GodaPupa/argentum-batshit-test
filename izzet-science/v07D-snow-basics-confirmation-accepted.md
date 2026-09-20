# v0.7-D Snow-Basic Retrofit — Confirmation Acceptance

Disposition: `V07D_CONFIRMATION_ACCEPTED_PROMOTED_TO_V07`

## Provenance

- Frozen confirmation source: `121427cd68cbfa5c85a138b6089cf97f3b80875b`
- Source CI: `35536972387` / #550 (success)
- Confirmation run: `35537163085`, attempt 1 (success)
- Job: `izzet-snow-basics-v07d-confirmation`
- Artifact: `10613114086`
- Artifact ZIP SHA256: `eea78236d305ffb8b9dcc54bc9aabb24c765fa98f65791ec2a78c2b537bdae65`
- Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
- Challenger SHA256: `29fa77acd45069b1374f10ae0569d4371c6cd5ac70d78cd07c8f5543fbb337da`
- Confirmation seed: `0x1A22E700B`
- Samples: 100,000 per deck

## Artifact audit

| File | SHA256 |
|---|---|
| `v07D-confirm-control.txt` | `ad8dcf9198c29d1f65e86e42d9d0cf5cd7cd81b77a0af77c11f8ea6be47d1c5b` |
| `v07D-confirm-snow-basics.txt` | `2e78922cdb3722ce08c6c0a14cb4ef06b694b02b46c8f568b7a5dfb71c82c279` |
| `v07D-confirm-comparison.txt` | `55d2dedf33c78abb03baceabdd813d0837eb475836668133b7546ba19dddeead` |
| `v07D-snow-basics-confirmation.manifest` | `11b84cd95a8ed1da6250fee39c1426e6fdb2e761f5bdefdd38fefba111c47b14` |

The ZIP digest matches GitHub's artifact digest. The manifest binds the exact source,
run and attempt, frozen deck hashes, seed, sample count, change, and result hashes.
Both outputs contain ten standard and ten interaction rows, and an independent local
rerun of the frozen comparator passed.

## Confirmed T10 result

| Frozen criterion | Control | Challenger | Delta | Result |
|---|---:|---:|---:|---|
| All pre-v0.7-D telemetry, T1–T10 | — | — | exactly equal | Pass |
| Castable Skred with 3+ snow | 0.000% | 17.457% | +17.457 pp | Pass |
| Live castable Skred | 3.182% | 18.427% | +15.245 pp | Pass |
| Average available Skred damage | 0.03182 | 0.94246 | +0.91064 | Pass |
| Pair assembly | 10.503% | 10.503% | 0.000 pp | Pass |
| Current-turn lethal | 7.339% | 7.339% | 0.000 pp | Pass |
| U execution | 95.905% | 95.905% | 0.000 pp | Pass |
| R execution | 84.583% | 84.583% | 0.000 pp | Pass |
| Identity, completeness, provenance | — | — | — | Pass |

## Promotion

Promote the frozen challenger as `izzet-science/v0.7-control.md`, SHA256
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
The accepted deck changes only the 26 ordinary basics to their Snow-Covered
counterparts. v0.6 remains preserved as historical control evidence.
