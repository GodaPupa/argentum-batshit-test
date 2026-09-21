# v0.9 Phase 31A — Completed Registry Static Qualification Accepted

Disposition: `V09_PHASE31_COMPLETED_REGISTRY_STATIC_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Provenance

- Phase-31 gate SHA256: `27b64e27b6c9528cd84a2d1dcf831e5f93010282d1d816db9872464604dd4733`
- Completed Izzet seed registry SHA256: `cc1d9777837dbb255d4bffc92b1da611ad2db52d259ab725918487639ad76565`
- Generator/freezer SHA256: `4c2870d7f01238dfe4317e057f0788c2ba7f434335e820140b62c3fb1442e54a`
- Static validator SHA256: `2fe72e3b27fe264234611606f92a171ae1fbf13aa16d6cbf8b56cb4b10f8f02f`
- Workflow run: `35575102528` — success
- Job: `106255061061` — success
- Artifact: `10628325024`, `izzet-v09-phase31-seed-tooling-static`
- Artifact ZIP SHA256: `15b9dab036200f190a249f8cf7502505356854efc5a5ee22b4eeff97fd22886c`
- Validation transcript SHA256: `871198bcc972be8efa8dc7a8ccf852a46d38aebec21090f964a08e37694e2fd2`

Independent artifact audit reproduced the GitHub ZIP digest exactly, found exactly
`manifest.txt` and `validation.txt`, and confirmed the exact terminal marker
`V09_PHASE31_SEED_TOOLING_STATIC_VALIDATION_PASS`.

The completed registry conservatively covers the full preserved Izzet experimental
namespace `0x1A22E7001` through `0x1A22E7013`. The prior incomplete-registry
generation attempt failed before generation and remains permanently preserved as a
fail-closed pre-generation failure.

## Decision

Accept the completed registry plus Phase-31 generator/freezer tooling for exactly
one later authorized generation event through a single-use authorization marker.

This acceptance itself generates no seeds and authorizes no gameplay.

Counters:
- experimental seeds generated: 0
- experimental seeds consumed: 0
- games initialized: 0
- outcome exposure: 0/12
- card changes: 0

The exact v0.7 control remains frozen.
