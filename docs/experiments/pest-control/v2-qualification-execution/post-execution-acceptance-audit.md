# Pest Control V2 qualification — post-execution acceptance audit

## Disposition

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_QUALIFICATION_50`
- Frozen-vector SHA-256: `c38ff24c9b45e36036cab506475bb8283f4e14e211318a7a7e6ab99acef6f497`
- Execution source: `8d3895de38189ca16f18017eb84b5e5a3f060b3bc`
- Execution tree: `801f6eb99da2d2b7339b313a3fc27d9849bfc17d`
- Workflow run: `35536805887`, attempt 1, success
- Aggregate artifact: `10613973303`
- Aggregate archive SHA-256: `7e65e7feca23d9eb29754779d42a92f9aaed8388641f9d64a510f6d8c056d10c`
- Aggregate raw SHA-256: `8dd276453cccda391f27a9c3325184aaf7faec1689ac680aa276430cb3e924dc`
- Block-summary SHA-256: `066ad7ebd55b7310bc1427cf0db5528fc96423281fe254dedb868067bea8c7cb`
- Audit-input SHA-256: `26f3c28b99d18d40aed3d39d0081c69cd984ad649574b52abbccfae472a0de26`
- Final disposition: `ACCEPTED_MATCHUP_QUALIFICATION`

All 50 frozen seeds were consumed exactly once in their preassigned order. They are permanently
unavailable for replay, reassignment, replacement, pairing, or inclusion in another vector. This
acceptance does not rehabilitate or pool rejected Block A.

## Integrity reconciliation

Independent artifact readback verified:

- both 25-game shard jobs and aggregate reconciliation completed on attempt 1;
- 50/50 expected, attempted, recorded, and legitimate `LIFE_ZERO` terminal games;
- exact seed, order, play/draw, seat, deck, protocol, block, freeze, commit, and tree identities;
- exact concatenation of the two shard records into the aggregate;
- all 16,523 priority actions contiguous, accepted, and free of fallback or rejection;
- zero protocol defects, wedges, action guards, turn guards, or timeouts;
- every raw/compressed pair byte-identical after deterministic gzip decompression;
- 115 permanent targets, all controlled by the opponent, with zero self-permanent targets; and
- all 12 alternative-cost Fireblasts and 21 flashed-back Lava Darts paid the exact required distinct
  persistent Mountains, with zero invalid or reused sacrifices.

The predeclared methodological boundary therefore passes. Outcome count did not control this
decision.

## Performance readout

Pest Control defeated Mono Red Madness **32-18 (64%)**. The exact two-sided 95% binomial interval is
49.2%-77.1%; the two-sided test against 50% is `p = 0.0649`. This is encouraging matchup evidence,
but the interval remains wide and includes parity.

| Stratum | Pest record | Rate |
|---|---:|---:|
| Pest on play | 19-6 | 76% |
| Pest on draw | 13-12 | 52% |
| Pest seat zero | 12-13 | 48% |
| Pest seat one | 20-5 | 80% |

The seat split is material (`p = 0.0378`, two-sided Fisher exact) and is reported rather than hidden.
The trace audit found no seat/deck mapping, provenance, targeting, payment, terminal, or action defect.
Seat and play/draw were frozen and balanced before outcome exposure, including near-equal joint cells,
so the balanced 32-18 aggregate remains the controlling descriptive estimate. The stratum divergence
limits precision and is a reason not to overstate the point estimate; it is not a post hoc basis to
reject or rerun a clean frozen block.

## Acceptance boundary

This block qualifies evidence only for the exact frozen preboard SoterX Mono Red Madness matchup. It
does not establish Tier-1 status across the Pauper metagame and does not promote or change the frozen
Pest Control v1.0 deck. Any additional precision or seat-invariance study requires a new,
predeclared, non-overlapping vector. These 50 outcomes may not be used to tune such a study.
