# Pest Control V2 qualification seed-freeze gate

This gate creates the fresh 50-game qualification identity without initializing or executing a game.
It is manual, artifact-only, and fail-closed. The qualification runner remains `DISABLED`.

## Frozen inputs

- Readiness merge: `5cd65def599b1017dae29f988c8c9ff211cac258`
- Accepted calibration merge: `c4f02ff2c19bd256a356e1f97a18d762484ecb7d`
- Qualified runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`
- Complete exclusion set: 463 permanent-registry values, ten rejected first-candidate V2 values,
  ten accepted calibration values, and one nonexperimental V2 smoke value (484 unique values total)
- Deck identities and the 25/25 play-draw, 25/25 seat, 12/12/13/13 joint-cell design remain unchanged.

All source files are pinned by SHA-256 in the generator. Any change, duplicate, overlap, missing row,
or unexpected cardinality stops before entropy is requested.

## One-time operation

Pull requests run only deterministic fixture validation based on fixed nonexperimental bytes. The
fixture is visibly marked `NONEXPERIMENTAL_FIXTURE` and is never eligible for execution.

The production workflow requires an exact acknowledgement, `main`, workflow attempt 1, no prior
freeze artifact, and a successful entropy-free preflight. It then makes one 1,200-byte OS-random
request: 400 bytes become 50 signed 64-bit seeds in unchanged draw order and 800 bytes become 50
independent 128-bit within-shard assignment tags. The complete draw is fsynced to quarantine before
validation. An invalid draw is permanently retired; retry, replacement, and reroll are forbidden.

The output artifact contains the quarantined draw, ordered vector, assignment CSV, two-shard
allocation, manifest, and checksum inventory. Its initial disposition is `FROZEN_UNEXECUTED`.
Generation does not attach the freeze to the compiled readiness contract and cannot activate the
runner. A separate reviewed provenance change is required before any execution gate can exist.
