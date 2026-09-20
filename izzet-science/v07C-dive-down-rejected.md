# v0.7-C Dive Down — Pilot Rejection Audit

Disposition: `V07C_REJECTED_AT_PILOT`

The frozen challenger `-1 Skred, +1 Dive Down` completed its one authorized
10,000-game paired pilot. It did not clear every preregistered gate, so it is
rejected permanently and no 100,000-game confirmation is authorized.

## Provenance

- Accepted control: `izzet-science/v0.6-control.md`
- Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
- Challenger: `izzet-science/challengers/v07C-dive-down.md`
- Challenger SHA256: `6329279e87ae4922765609c5b8e6b71e28d82013ebea5ed2e95a13a5581bdf88`
- Frozen source commit: `727cf41de5ddb48a88da59e7d090548db8d695fc`
- Source validation run: `35535411407` (success)
- Official pilot run: `35535687806`, attempt 1 (success)
- Job: `izzet-dive-down-v07c`
- Seed: `0x1A22E7006`
- Samples: 10,000 per deck
- Artifact ID: `10612616846`
- Artifact ZIP SHA256: `e05b9bfa9ae98b67d825be17eae64d814fe570600e19a35671fe81f7b661e580`

## Artifact audit

| File | SHA256 |
|---|---|
| `v07C-control.txt` | `f6a1961eff7c7f10df4011bfeb935d205f744ebac2c4b9c654f4d2a8233b982a` |
| `v07C-dive-down.txt` | `27255c2d42947919bdfe83956af4d5ee23e51da32e5f12ac0039859950295761` |
| `v07C-dive-down.manifest` | `3b96c52b5c2620055cccd414ec246300bfddb45ce18293bb0c4bb1c9900931f7` |

The manifest binds the run ID, attempt, frozen source, control and challenger
hashes, seed, sample count, change, and both result-file hashes. Each result has
one header, two bucket lines, ten turn rows, and ten interaction rows.

## Frozen gate evaluation at T10

| Criterion | Control | Challenger | Delta | Result |
|---|---:|---:|---:|---|
| Guaranteed targeted-removal protection | 5.10% | 5.44% | +0.34 pp | Pass |
| Generic guaranteed stack protection | 4.62% | 4.62% | 0.00 pp | Pass |
| Conditional stack protection | 2.83% | 2.83% | 0.00 pp | Pass |
| Immediate taxed recovery | 3.20% | 3.20% | 0.00 pp | Pass |
| Current-turn lethal | 7.16% | 7.16% | 0.00 pp | Pass |
| Pair assembly | 10.19% | 10.19% | 0.00 pp | Pass |
| U execution | 95.87% | 96.66% | +0.79 pp | Pass |
| R execution | 84.28% | 81.39% | **-2.89 pp** | **Fail** |
| Reversal positive-mana readiness | 9.97% | 9.97% | 0.00 pp | Pass |
| Identity, completeness, and provenance | — | — | — | Pass |

Criterion 7 allowed at most a 0.25 percentage-point decrease in either U or R
execution. The 2.89-point R execution loss is binding even though the primary
protection metric improved. The result is not rerun, re-seeded, or reinterpreted.

## Decision

Reject v0.7-C. Preserve v0.6 unchanged as the accepted control. The confirmation
seed `0x1A22E7007` remains unused.

## Post-run factual correction

The preregistration described Skred as having a zero-damage ceiling because v0.6
supposedly had no snow permanents. Volatile Fjord is a snow land, so the correct
ceiling is one. This correction does not alter any sampled value, frozen threshold,
or the binding -2.89 percentage-point R-execution failure; v0.7-C remains rejected.
