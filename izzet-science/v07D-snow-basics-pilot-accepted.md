# v0.7-D Snow-Basic Retrofit — Accepted Pilot

Disposition: `V07D_PILOT_ACCEPTED_CONFIRMATION_AUTHORIZED`

## Provenance

- Frozen source: `32f0355ad5a71ca5082c5dd18bea3ac4a6b42cf8`
- Source CI: `35536616583` / #548 (success)
- Pilot run: `35536773714`, attempt 1 (success)
- Job: `izzet-snow-basics-v07d`
- Artifact: `10613177718`
- Artifact ZIP SHA256: `8e73aed20b9043a64e060e9f1cc0cfb63c5cb2aa170b307fb47a3a4e8aea0029`
- Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
- Challenger SHA256: `29fa77acd45069b1374f10ae0569d4371c6cd5ac70d78cd07c8f5543fbb337da`
- Seed: `0x1A22E700A`
- Samples: 10,000 per deck

## Artifact audit

| File | SHA256 |
|---|---|
| `v07D-control.txt` | `4dcfd0df26244175aea07fa18435bf810486df52457c118a32058b79f704bb98` |
| `v07D-snow-basics.txt` | `a967fa69604ab9a5c47ec1651b340e7b90d6aca2f85576ae2e1f121621098ca5` |
| `v07D-comparison.txt` | `427dd94ceab8b842d3cf1548ef5074bf0b598d8023f4daee49e2eaba4626b2af` |
| `v07D-snow-basics.manifest` | `1f8ab45208d15a05f063d54dc4194c1b17bdc03da8d57db86e72227c80fea32b` |

The downloaded ZIP digest matches GitHub's artifact digest. The manifest binds the
source, run and attempt, frozen identities, seed, sample count, change, and all three
result hashes. Both outputs contain exactly ten standard and ten interaction rows.

## T10 result

| Frozen criterion | Control | Challenger | Delta | Result |
|---|---:|---:|---:|---|
| All pre-v0.7-D telemetry, T1–T10 | — | — | exactly equal | Pass |
| Castable Skred with 3+ snow | 0.00% | 17.64% | +17.64 pp | Pass |
| Live castable Skred | 3.23% | 18.64% | +15.41 pp | Pass |
| Average available Skred damage | 0.0323 | 0.9595 | +0.9272 | Pass |
| Identity, completeness, provenance | — | — | — | Pass |

The pilot cannot promote the challenger. It authorizes exactly one 100,000-game
confirmation on frozen seed `0x1A22E700B`; only that run may earn promotion.
