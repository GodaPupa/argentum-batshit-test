# v0.9 Phase 27 — Observed-Action Behavior Accepted

Disposition: `V09_PHASE27_OBSERVED_ACTION_BEHAVIOR_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

## Provenance

- Gate: `izzet-science/v09-phase27-observed-action-behavior-gate.md`
- Branch head under test: `29dcbc4c0166db3f0551bffaabcf93c9096bbb45`
- Pull-request merge ref checkout: `de4a785afbb02e481f7dd60575bf5c36ec8dc2cc`
- Behavior SHA256: `82df64d6d0dcc8da367f2c3a7f6d298900f97e8b4973c68a3f94172158370f2b`
- Validator SHA256: `6db79423cde02f529567a0676c0f8f46e00380885a4579306616a35043cf4d8c`
- Gate SHA256: `fb6ea97188db2ddb18b5374be3e635c0e7baac8a36cdcd3772750c357c3d039d`
- Workflow run: `35568964589` — success
- Job: `106236353055` — success
- Artifact: `10624728523`, `izzet-v09-phase27-observed-action-behavior`
- Artifact ZIP SHA256: `758d37edbc9d6bc1874f02ae6df63e8a458e2bbcf94551fc7f7b5a6a9a6d195e`
- Manifest SHA256: `0a0c70cb67b55e80511a9d4bc3a4c9004420a9149cdae636107058302895681a`
- Validation transcript SHA256: `f9b55aa0cb86e98d1850e68eac0769cc2c9a956ec23787f33be073c540999e9f`

The downloaded artifact contained exactly two nonempty files, `manifest.txt` and
`validation.txt`. The locally computed ZIP digest matched GitHub's reported
artifact digest exactly. The validation transcript contained exactly
`V09_PHASE27_OBSERVED_ACTION_BEHAVIOR_VALIDATION_PASS`.

## Qualified behavior

The seed-free behavior layer passed fixtures for:
- public Guildmage-removal precedence;
- public next-main lock precedence;
- generic control over development;
- canonical tie-breaking;
- pass with no legal action;
- duplicate-action rejection;
- candidate-order invariance;
- deterministic replay equality; and
- public terminal commander-pressure precedence.

The workflow also passed the frozen-v0.7 identity check, Phase-26 parent-acceptance
check, no-experimental-execution-surface audit, manifest generation, and artifact
upload.

## Decision

Accept the deterministic Phase-27 observed-action behavior policy over legal actions
emitted by the accepted Phase-26 compiler.

This acceptance freezes simulator behavior only. It is not a claim that the policy is
optimal human play or that Veteran Beastrider has any particular matchup strength.

Counters at acceptance:
- experimental seeds consumed: 0
- sampled games: 0
- outcome exposure: 0
- card changes: 0

The exact v0.7 100 remains frozen.

## Next gate

Build a seed-free integration contract that composes:
Phase-25 rules surfaces -> Phase-26 legal-action compiler -> Phase-27 deterministic
behavior selection over concrete public fixtures.

The integration gate must prove identity preservation, legality preservation,
information-boundary preservation, and deterministic end-to-end replay. Hidden-hand
generation and sampled matchup execution remain unauthorized until this integration
passes independently.
