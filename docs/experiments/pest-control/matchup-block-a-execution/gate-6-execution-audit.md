# Pest Control matchup Block A — Gate 6 execution audit

## Disposition

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_BLOCK_A`
- Freeze commit: `c009c4b05a16d13eeabc34db60edcb6af58f040a`
- Execution commit: `13c4680ddc952694c5d4738940b4abbe797563ab`
- Dispatch commit: `32ce1fd6f15dc00342baf7ee03c9891672f44796`
- Workflow run: `34930029981`, attempt 1, success
- Artifact ID: `10391659486`
- Artifact ZIP SHA-256: `3d4b525ee9dc91290a11398e598053eb2de5dea85d28a7cebd44cb1a8d84a706`
- Artifact ZIP size: `11269752` bytes
- Disposition: `PENDING_GATE_7_REVIEW`

The block has not been accepted, pooled, or used for tuning. All 50 seeds are permanently
non-replayable. No Block B seeds were generated.

## Reconciliation

The sole execution consumed the frozen CSV sequentially. Independent readback verified 50 contiguous
game numbers, 50 unique assignment seeds, 50 unique attempted seeds, and 50 unique game-record seeds,
all identical position by position. Pest play/draw was 25/25, Pest engine seat was 25/25, and the
joint cells were 13/12/12/13.

Every game instantiated the two frozen 60-card maindecks and zero sideboard cards while retaining all
six 60/15/75 hashes in provenance. Every game is marked `FROZEN_EXPERIMENTAL_VECTOR`, with
`fixtureIsNonexperimental=false` and no fixture seed-registry exclusion.

All 50 records reached legitimate `LIFE_ZERO` terminals. There were zero protocol defects, rejected
actions, illegal-action fallbacks, wedges, action guards, turn guards, or timeouts. Priority-action
sequence numbers are contiguous within every game; every action carries its legal-action hash and
before/after audit digest.

The raw result is Pest Control 35 wins and Mono Red Madness 15 wins. Pest was 19-6 on the play and
16-9 on the draw; it was 18-7 in seat zero and 17-8 in seat one. These are descriptive raw values,
not a Gate 7 acceptance decision.

## Preserved artifact identities

- Raw JSON SHA-256: `4a13c4491423705b4732fce44d7fc0a3069476d68be331206a8e4af32ff16705`
- Deterministic gzip SHA-256: `2987034cf1ee39d781e4f0a052025f378f60e2400d4a34e102a00514ec4cbf30`
- Human report SHA-256: `1cb6b1ceae97e9f22fa9e25e48003047d68f7050e6362c4d66c8d281585b2c6c`
- Execution manifest SHA-256: `f2054e5cef2db1032eac0000cc6236e4be203b7ad65b35eb1200a1f83ecb0c4c`
- Execution log SHA-256: `d4cd230153f29ad8758ac3df8753a2a5a44e2cb46ea8d700fb7e17cd236ba43f`
- Acceptance-audit input SHA-256: `f2e265a2e334fd5a6c1769c9befd1dbb855c002bf267942c7c0976a7f1819c1b`
- Hash inventory SHA-256: `b07696d3f3edf79e0e037540d40afeffa0e0e643e483e1445fbd06aca8cefd03`

The gzip header is canonical (`mtime=0`, `xfl=0`, `os=255`) and decompresses byte-for-byte to the raw
JSON identity above. GitHub Actions artifact `10391659486` preserves both exact raw forms uploaded by
the run. The repository preserves the manifest, report, execution log, acceptance-audit input, hash
inventory, and this audit; the downloaded raw files are intentionally not rewritten or regenerated.

## Runner state

The dedicated Block A runner was activated only for the exact authorized execution and was returned
to `DISABLED` after artifact preservation. All 13 preexisting Pest gameplay runners remained disabled
and unchanged throughout execution.
