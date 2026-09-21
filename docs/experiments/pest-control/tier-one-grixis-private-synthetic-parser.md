# Pest Control Tier-1 coverage — private synthetic artifact parser

## Result

The private parser boundary validates vector, assignment CSV, and manifest parsing using only fixed
nonexperimental fixture bytes. Its canonical SHA-256 is
`e8d7c4d6cca0339e6b2752449f83d23376db8c5c7904281a99ee2878a9f1148c`, and its successful status is
`SYNTHETIC_PARSER_VALID_OFFICIAL_BYTES_NOT_LOADED_EXECUTION_NOT_AUTHORIZED`.

The canonical synthetic fixture bundle SHA-256 is
`0b0506b91202c8891d5dcc59d990ada4b1ac0d8308b83e593275f71f1e375897`. It is explicitly classified
`NONEXPERIMENTAL_FIXTURE`, uses four fixed synthetic values, and is excluded from every experimental
seed registry and result. It validates canonical LF encoding, exact headers and identities, numeric
and hexadecimal agreement, game order, seat separation, starter/play-draw agreement, vector/CSV
alignment, uniqueness, nonzero values, and manifest hashes.

Duplicate-value and manifest-hash fixture variants fail closed. The private byte builders and parsers
are unreachable from public callers; `PestControlTierOneGrixisPrivateSyntheticParser` exposes only
`inspect` and selects from fixed fixture variants.

## Official state

- Official artifact bytes loaded: `false`
- Official seed values visible: `0`
- Official assignments/seeds: `4/4`
- Initializer/runner enabled: `false/false`
- Official games authorized/initialized: `0/0`
- Actions submitted: `0`
- Outcome-bearing artifacts: `0`
- Outcome exposure: `0/4`

This gate does not authorize loading the official artifact or initializing a game. The next gate may
define a disabled official-byte loader boundary that remains private and unreachable from workflows
or commands; activating it requires a later explicit research decision.
