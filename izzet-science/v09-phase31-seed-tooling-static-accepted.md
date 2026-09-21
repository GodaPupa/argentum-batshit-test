# v0.9 Phase 31A — Seed Tooling Static Qualification Accepted

Disposition: `V09_PHASE31_SEED_TOOLING_STATIC_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Provenance

- Phase-31 gate SHA256: `27b64e27b6c9528cd84a2d1dcf831e5f93010282d1d816db9872464604dd4733`
- Seed registry SHA256: `c235f9cd89206490ec5c9105255c187a0f3d845cfb5877fbe4110c926e3dfb2f`
- Generator/freezer SHA256: `4c2870d7f01238dfe4317e057f0788c2ba7f434335e820140b62c3fb1442e54a`
- Static validator SHA256: `2fe72e3b27fe264234611606f92a171ae1fbf13aa16d6cbf8b56cb4b10f8f02f`
- Workflow run: `35573216533` — success
- Job: `106249154804` — success
- Artifact: `10626482844`, `izzet-v09-phase31-seed-tooling-static`
- Artifact ZIP SHA256: `7b4cd39d912d466ba5cb9b0303add5d3a83442224220cf481650422f9a1c2e07`
- Artifact manifest SHA256: `1c8a3da1b9602510594ec7edc6034a012a64d1b4f9a56d18973f8787e1d8f9de`
- Validation transcript SHA256: `871198bcc972be8efa8dc7a8ccf852a46d38aebec21090f964a08e37694e2fd2`

Independent artifact audit matched GitHub's reported ZIP digest exactly. The archive
contained exactly two nonempty files, `manifest.txt` and `validation.txt`. The
validation transcript contained exactly
`V09_PHASE31_SEED_TOOLING_STATIC_VALIDATION_PASS`.

The static qualification generated no experimental seed, initialized no sampled
game, and exposed no outcome.

## Decision

Accept the Phase-31 generation/freezing tooling for exactly one later authorized
generation attempt. This acceptance does not itself generate or consume a seed and
does not authorize game execution.

Counters:
- experimental seeds generated: 0
- experimental seeds consumed: 0
- games initialized: 0
- outcome exposure: 0/12
- card changes: 0

The exact v0.7 control remains frozen.
