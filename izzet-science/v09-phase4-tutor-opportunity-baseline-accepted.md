# v0.9 Phase 4 Tutor Opportunity — Accepted Baseline

Disposition: `V09_PHASE4_PILOT_ACCEPTED`

## Provenance

- Control: `izzet-science/v0.7-control.md`
- Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`
- Experimental source: `3a4ca81f9d3476e6f4f5495b729d44fae4b271f8`
- Workflow runner: `2163ab35ad74436ed0c14aa99ffd6a1235fbfd08`
- GitHub Actions run: `35546197942` / #184, attempt 1
- Job: `106172438269`
- Seed: `0x1A22E700F`, consumed
- Samples: 10,000
- Horizon: T1–T10
- Artifact: `10615728723`, 4,036 bytes
- Artifact ZIP SHA256: `cf9de83c4d2bd5c21be96dbef8c135fc2e64541b5f93ae23fe34a8ed3f24c1b5`
- Pilot output SHA256: `d78ccf552de98a2617a4fe351c47a867598badd79ef67e6b34c980a646b4f897`
- Audit output SHA256: `9eb62e6480117158a169edd90738aec5e29d8d079312abeb74ef99d1cbc00e07`
- Manifest SHA256: `672ed0560a2c99dca207ae843f9c124e3978cc83061d5f4ac1b16bb78180d2f8`

The frozen-source checkout, control identity, seed-free preflight, auditor self-test,
sampled output audit, provenance manifest, and nonempty artifact upload all passed.
The downloaded ZIP digest matched GitHub's reported digest, all three internal file
hashes were independently recomputed, and the pilot output passed the auditor again.

## T10 tutor opportunities

| Backup card | Targetable | Payable | Uncontested |
|---|---:|---:|---:|
| Murmuring Mystic | 0.00% | 0.00% | 0.00% |
| Rolling Thunder | 3.17% | 3.05% | 1.89% |
| Kaervek's Torch | 3.03% | 2.88% | 1.81% |
| Capsize | 24.62% | 24.34% | 24.34% |

Because each backup card is a singleton, direct presence and being a legal library
target are disjoint at this observation window. The derived present-or-payable-tutor
opportunity was therefore 16.89% for Mystic, 19.98% for Rolling Thunder, 19.53% for
Kaervek's Torch, and 40.88% for Capsize. The analogous present-or-uncontested-tutor
figures were 16.89%, 18.82%, 18.46%, and 40.88% respectively.

## Interpretation

Existing tutors create a large modeled acquisition opportunity for Capsize: its
present-or-payable potential is roughly 2.47 times direct presence, and none of that
tutor opportunity competes with a missing primary combo piece. The X-spell routes
add only about three percentage points each, with roughly one percentage point of
payable opportunity contested by a missing Lava Spike. No current tutor improves
Mystic access.

This is passive opportunity evidence, not executed acquisition. Tutors were not
spent, mana was not consumed, libraries were not changed, and no backup spell was
cast. The result therefore supports modeling an explicit Capsize tutor policy before
testing a decklist challenger; it does not authorize a promotion or win-rate claim.

## Control decision

Preserve v0.7 unchanged. Consume and retire seed `0x1A22E700F`; do not rerun or pool
the pilot. Restore the ordinary workflow and use this baseline only to preregister a
stateful tutor-policy gate.
