# v0.9 Phase 31 — Seed Freeze Metadata Repair

Disposition: `V09_PHASE31_FREEZE_METADATA_REPAIRED_VECTOR_UNCHANGED`

No seed was regenerated, replaced, rerolled, consumed, or exposed by this repair.

## Frozen generation event

- Workflow run: `35575569439` — success
- Job: `106256546479` — success
- Source merge-ref SHA: `41d12082b35cd4bf065c92fac63f644962fe1d34`
- Quarantine artifact: `10627816776`
- Quarantine artifact ZIP SHA256: `83c0c75363ff0d9030cd1ed6f3441a201514ddb8920562aa2791b04fc4651f6a`
- Quarantined vector file SHA256: `9c9089fbe256652ffcb30363258409fe8fff9df33414216386faedb4be2d745f`
- Public freeze artifact: `10628590437`
- Public freeze artifact ZIP SHA256: `2efb59ee5f1be34c0e989b84b20c800b9dc7a24cc54b3eea0d97fe32faf75b9a`
- Frozen vector SHA256: `5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f`
- Assignment-vector SHA256: `b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3`
- Count: 12
- Izzet play/draw: 6/6
- Seeds consumed: 0
- Games initialized: 0
- Outcome exposure: 0/12

## Provenance defect

The successful run verified the completed registry file itself before generation and
also passed the repository-wide Izzet seed-literal overlap audit. The generator then
read that completed registry file for the actual overlap check.

However, the command-line metadata field supplied to the public manifest retained
the obsolete pre-completion registry digest
`c235f9cd89206490ec5c9105255c187a0f3d845cfb5877fbe4110c926e3dfb2f`.

That value was metadata only. It did not select or alter the registry used for the
actual overlap check. The completed registry that was verified and read by the run
has SHA256:

`cc1d9777837dbb255d4bffc92b1da611ad2db52d259ab725918487639ad76565`

Therefore the original public manifest's registry-digest field is superseded by this
repair record. All other frozen identities remain unchanged.

## Corrected immutable public freeze binding

- vector SHA256: `5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f`
- runner version: `izzet-v09-phase30-runner-v1`
- runner SHA256: `864d46745c7ffd1c1e9c8eef48c5a0ad14b34938aa0a6066d94ab7a394516fc3`
- v0.7 control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`
- opponent identity: `veteran-beastrider-commander-clash-2025-v1`
- Phase-29 protocol identity: `f404cd9e4c916b569deb9786145e31d30ea4dbedabd355778f582c4b7ba9b3cb`
- assignment-vector SHA256: `b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3`
- completed Izzet seed-registry SHA256: `cc1d9777837dbb255d4bffc92b1da611ad2db52d259ab725918487639ad76565`
- exact count: 12
- play/draw: 6/6
- generated/consumed: 12/0
- games initialized: 0
- outcome exposure: 0/12

The raw seed values remain quarantined and are intentionally omitted.

## Decision

Preserve the generated vector exactly. Do not regenerate it. Treat the original
public manifest as historically preserved but superseded only for its
`izzet_seed_registry_sha256` metadata field by this repair record.

The generation workflow was disarmed immediately after the successful official
generation event. Phase 31 is not yet closed until a fresh seed-free audit validates
this repair record against the completed registry, public artifact digests, and
frozen identities.
