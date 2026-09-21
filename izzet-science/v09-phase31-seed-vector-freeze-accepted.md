# v0.9 Phase 31 — Seed Vector Freeze Accepted

Disposition: `V09_PHASE31_SEED_VECTOR_FREEZE_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

## Official vector freeze

- Generation workflow run: `35575569439` — success
- Generation job: `106256546479` — success
- Frozen vector SHA256: `5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f`
- Assignment-vector SHA256: `b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3`
- Quarantine artifact: `10627816776`
- Quarantine artifact ZIP SHA256: `83c0c75363ff0d9030cd1ed6f3441a201514ddb8920562aa2791b04fc4651f6a`
- Quarantined vector file SHA256: `9c9089fbe256652ffcb30363258409fe8fff9df33414216386faedb4be2d745f`
- Public freeze artifact: `10628590437`
- Public freeze artifact ZIP SHA256: `2efb59ee5f1be34c0e989b84b20c800b9dc7a24cc54b3eea0d97fe32faf75b9a`
- Completed Izzet seed-registry SHA256: `cc1d9777837dbb255d4bffc92b1da611ad2db52d259ab725918487639ad76565`
- Runner SHA256: `864d46745c7ffd1c1e9c8eef48c5a0ad14b34938aa0a6066d94ab7a394516fc3`
- Phase-29 protocol identity: `f404cd9e4c916b569deb9786145e31d30ea4dbedabd355778f582c4b7ba9b3cb`

## Metadata repair audit

The original public manifest preserved one obsolete pre-completion registry digest
in metadata. The vector itself was generated using the completed registry file and
passed the repository-wide overlap audit before generation.

The vector was not regenerated.

The metadata-repair record superseded only that one registry-digest field and was
qualified independently:

- Repair record: `izzet-science/v09-phase31-seed-freeze-metadata-repair.md`
- Repair audit workflow run: `35607485691` — success
- Repair audit job: `106358048233` — success
- Repair audit artifact: `10642196516`
- Repair audit artifact ZIP SHA256: `1a93ce5a235017dd15b749764342230b6da3c7597c4f1006dca8e6c15107d296`
- Audit result: `V09_PHASE31_METADATA_REPAIR_AUDIT_PASS`

The audit verified the completed registry hash, frozen v0.7 hash, accepted runner
hash, frozen vector digest, assignment digest, both generation artifact digests,
12/0 generated/consumed counters, zero games initialized, zero outcome exposure,
the no-regeneration requirement, and absence of raw seed exposure.

## Decision

Accept the exact frozen 12-seed vector and 6/6 assignment binding.

The raw seed values remain quarantined. Do not print, replace, reroll, regenerate,
reassign, or reuse them.

Counters at acceptance:
- experimental seeds generated: 12
- experimental seeds consumed: 0
- games initialized: 0
- sampled games completed: 0/12
- outcome exposure: 0/12
- card changes: 0

The exact v0.7 100 remains frozen.

## Next gate

Open Phase 32 execution-readiness only. Build a loader that reads the exact
quarantined vector by artifact identity, validates its file/vector/assignment
digests without logging seed values, binds each position to the accepted Phase-30
runner, and proves durable attempt-before-initialization plus stop-on-first-invalid
semantics under synthetic or opaque fixtures.

No official game may initialize until Phase 32 is independently accepted.
