# v0.7-A Pyroblast — Rejected Pilot

Run: 35533611783
Job: `izzet-pyroblast-v07a`
Artifact: 10612212420
Artifact ZIP SHA256: `fffe4f6ef9a152d666a881fa637cb03619db906fbc41c7527e05303d201d4095`
Control output SHA256: `c114b5bdd98740a22253b476b4e5f697780f6878d3faf981ded03ea3b2d94656`
Challenger output SHA256: `38518d9bd3409e09bee098c465edc6c86f16c6017fa66e08e9a1404307c6b908`
Manifest SHA256: `e89c7dbfb772b47a06d8905c5b00372c0d137bae7c8ba396484df32f42d8bd4f`
Source: `28c12b554232bf6c09bf2a8a48150577bf77f3bc`
Source CI: 35533225972 (success)
Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
Challenger SHA256: `8723d7b9f083ac7091cb252417a3cf5a7b4b6576e1e6cf7cbad0381dcd74806f`
Seed: `0x1A22E7002`
Samples: 10,000 per deck
Horizon: T1–T10
Change: `-1 Lose Focus, +1 Pyroblast`

## Integrity

- The source commit passed CI before experimental dispatch.
- The workflow verified both deck hashes before simulation.
- The job, provenance manifest, and artifact upload completed successfully.
- GitHub's artifact digest matches the downloaded ZIP.
- Both output digests match the manifest.
- Both outputs contain exactly ten standard and ten interaction rows.
- The paired run used the frozen seed, sample count, horizon, source, and one-slot
  challenger identity.
- A first UI dispatch attempt redirected to an unrelated concurrent workflow and
  created no run for the frozen source. Run 35533611783 is the sole Izzet execution
  associated with source `28c12b55`.
- No rerun, replacement seed, pooled estimate, or post-outcome threshold change occurred.

## T10 paired result

| Frozen criterion | Control | Challenger | Change | Result |
|---|---:|---:|---:|---|
| Blue-stack guaranteed protection | 4.80% | 5.09% | +0.29 pp | **Fail**; required +0.50 pp |
| Generic stack guaranteed protection | 4.80% | 4.80% | 0.00 pp | Pass |
| Generic targeted-removal protection | 5.39% | 5.39% | 0.00 pp | Pass |
| Conditional protection readiness | 2.96% | 2.13% | -0.83 pp | **Fail**; +0.29 pp blue gain does not cover loss |
| Current-turn lethal state | 7.66% | 7.66% | 0.00 pp | Pass |
| Pair assembly | 10.47% | 10.47% | 0.00 pp | Pass |
| U execution | 95.86% | 95.86% | 0.00 pp | Pass |
| R execution | 84.93% | 87.06% | +2.13 pp | Pass |
| Identity and artifact validation | — | — | — | Pass |

Pyroblast improved red execution and the explicitly blue protection state, but the
gain was below the preregistered minimum and materially smaller than the conditional
readiness lost with Lose Focus. The pilot therefore fails criteria 1 and 4.

## Disposition

`V07A_PILOT_REJECTED`

`V07A_CONFIRMATION_NOT_AUTHORIZED`

`V06_CONTROL_UNCHANGED`

`NO_CHALLENGER_PROMOTED`
