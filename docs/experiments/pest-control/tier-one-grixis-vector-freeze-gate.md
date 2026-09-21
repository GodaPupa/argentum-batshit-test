# Pest Control Tier-1 coverage — Grixis smoke-vector freeze gate

## Authorization and scope

The separate research decision following construction closure authorizes exactly one four-seed
vector-generation and freeze operation. It does not authorize game initialization, action
submission, execution, reporting, or outcome exposure. The runner and official initializer remain
`DISABLED`.

## Frozen inputs

- Construction merge: `fe8b2cfe796e307b2ed0deb29fd19d98e4286b3c`
- Construction proof: `dc981e02af9b687f4e999bfc13b35630299dfd9ad84cf5ae86ff49f752bdcecf`
- Qualified runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`
- Protocol: `PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4`
- Complete exclusion set: 534 unique Pest seed identities, including the frozen, unexecuted
  50-seed V2 qualification vector.

The generator pins every repository source by SHA-256 and independently reconstructs the frozen V2
qualification vector hash. Any source drift, cardinality error, duplicate, or cross-source collision
stops before entropy is requested.

## Fixed assignment mapping

| Game | Pest seat | Grixis seat | Starting deck |
|---:|---|---|---|
| 1 | zero | one | Pest Control |
| 2 | zero | one | Grixis Affinity |
| 3 | one | zero | Pest Control |
| 4 | one | zero | Grixis Affinity |

## One-time operation

Pull requests run deterministic validation from fixed nonexperimental bytes and write only a
`NONEXPERIMENTAL_FIXTURE` under the build directory. Production generation requires an exact manual
acknowledgement on `main`, workflow attempt one, absence of any prior named freeze artifact, and a
fresh entropy-free preflight.

The production step makes one `os.urandom(32)` request. The four signed big-endian 64-bit values are
preserved in draw order and fsynced to quarantine before validation. If the draw contains zero, a
duplicate, or any collision with the 534-value exclusion set, it is marked `INVALID_RETIRED` and the
operation stops permanently. There is no retry, replacement, or reroll path.

A valid artifact contains the quarantined vector, ordered vector, fixed assignment CSV, manifest,
and checksum inventory with disposition `FROZEN_UNEXECUTED`. It records zero initialized games,
submitted actions, outcome artifacts, and outcome exposure. A later reviewed provenance change is
required before any execution gate may be considered.

## Frozen result

The sole production dispatch, workflow run `35556631787` at source commit
`6465548adfa7039ff02edb8834e33318231903f6`, completed successfully on its first attempt. Artifact
`10620940806` passed its internal checksum inventory and independent audit: four unique nonzero seeds,
zero overlap with all 534 excluded Pest identities, and the exact four assignment cells above.

Immutable hashes and GitHub artifact metadata are recorded in
`tier-one-grixis-vector-freeze-provenance.json`. The production `workflow_dispatch` and freeze job are
removed by the same provenance change, permanently closing the repository's regeneration path. The
official vector is `FROZEN_UNEXECUTED`; games initialized, actions submitted, and outcome exposure
remain zero. Execution is not authorized.
