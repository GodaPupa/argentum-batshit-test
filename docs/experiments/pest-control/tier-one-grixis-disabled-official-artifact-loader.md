# Pest Control Tier-1 coverage — disabled official-artifact loader

## Authorization boundary

The separate research decision following synthetic parser qualification authorizes one private,
read-only validation of the already frozen artifact bytes. It does not authorize seed regeneration,
game initialization, action submission, execution, result creation, or outcome exposure.

The loader is disabled by default. Its disabled proof SHA-256 is
`0c5dfd0d9d09754f50f29f4be42eb874433fa4ef9a881e6072e4543c7f9adfb6`. A guarded manual validation
may download only artifact `10620940806`, verify the archive and internal checksums, and privately
parse the vector, assignments, manifest, quarantine, and checksum inventory. A successful validation
has proof SHA-256 `8f3634a183b40b71329c900eed4e9a649cb46b76f6f5051462f9098f4eafbee1`
and status `OFFICIAL_ARTIFACT_VERIFIED_UNCONSUMED_EXECUTION_NOT_AUTHORIZED`.

## Disclosure and execution controls

The validation result contains only hashes, counts, and zero counters. It never serializes a seed,
assignment row, hand, game state, action, event, result, or outcome. The parsed values remain private
and transient inside the validation process.

- Official artifact bytes loaded by default: `false`
- Official seed values exposed: `0`
- Official seeds consumed: `0`
- Initializer enabled: `false`
- Runner enabled: `false`
- Official games authorized: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Outcome-bearing artifacts: `0`
- Outcome exposure: `0/4`

The one authorized validation completed successfully in workflow run `35559222714` at source commit
`eb15b1b9a580214f5f6a5766f3c7bb9ab46abe7c`. The digest-only result is recorded in
`tier-one-grixis-official-loader-validation-provenance.json`. The manual trigger and official-artifact
job have been removed; pull requests can exercise only the disabled state. Execution remains
separately unauthorized.
