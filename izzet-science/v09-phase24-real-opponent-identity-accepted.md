# v0.9 Phase 24 — Real Opponent Identity Accepted

Disposition: `V09_PHASE24_IDENTITY_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- Opponent identity: `veteran-beastrider-commander-clash-2025-v1`
- Semantic source commit: `bd7699847a662899e8024ed55f55f2b861eda531`
- Semantic source tree: `95bccc0a25c96ec2dd6ac86a3e454f6e1db4d905`
- Workflow runner commit: `c66136ca239b770896eefe4963f65188e6139b91`
- Workflow runner tree: `5ec57639f901cd3db22ed4f9e7ceb37b6cab9d67`
- Temporary workflow SHA256: `50616f1d1bfe2f1e924de721da5d0fb396941d002d09ff422e53e4e59266ce66`
- Exact list SHA256: `c3ab6ee8e37a78219e7bbb7c6a75c634d78c1eb4965ab0db3abd5d853398b0be`
- Identity snapshot SHA256: `ecde45fcafc4f09ce7e69398ab0787979a6e0d84b4dba83f067a2153176dc0ff`
- Capture program SHA256: `673b0c07959937a549823b8a410cdc764649e40e6f8237060b3eca12c4c08261`
- Validator SHA256: `a4d768af9da73420b805065b34a66247b45f96388359563449cdf8bd670bcd22`
- Gate SHA256: `5bef023ec3c24d7cb08afeaec20424ab77fd71a0c209f83a7e2fb2022115bbd5`

## GitHub validation and artifact

- Workflow run: `35562466013` — success
- Job: `106217814194` — success
- Artifact: `10622364890`, `izzet-v09-phase24-real-opponent-identity`
- Artifact size: 14,445 bytes
- Artifact ZIP SHA256: `cba4d576cc42174a9b3c4ee60fcb0c3ca8f894ad7eb2bee8092cd40d7bbabaaa`
- Manifest SHA256: `2f9870d8f0ae5fe4da855b7e73cef7ee74c80cae242a7335c7ee88a58068e1a1`
- Live replay SHA256: `ae1b4af4e37bc858d51f3ee9f776796cab888c9d5ab199e16ba07d81d06b9deb`
- Phase-24 online receipt SHA256: `40b416caabaa2d33c7371b64e409420b590d605760f67a534bdc1c2c63835d50`

Exact checkout, all bound hashes, the accepted Phase-23 fixture boundary, the
frozen opponent identity, eight contaminated-identity rejections, a fresh TopDeck
and Scryfall replay, manifest construction, and artifact upload passed. The replay
reproduced the first-place event identity and all 85 unique card identities, with
all 99 mainboard slots currently PDH-legal and no color-identity violation.

An independent download matched GitHub's ZIP digest, contained exactly the eight
declared files, and reproduced every payload hash after accounting for GitHub's
stripped upload-directory prefix.

## Decision

Accept Veteran Beastrider / Scarecrow1779 / CPDH Commander Clash 2025 as the first
real frozen opponent identity. The exact 100 cards, quantities, tournament result,
TopDeck source digest, Scryfall oracle identities, and legality observations are
now immutable for this experimental identity.

This acceptance freezes identity, not behavior or matchup strength. It does not
claim that Veteran Beastrider is the strongest current deck, does not infer card
rules from deck membership, and does not authorize opponent execution. Zero games
ran, zero experimental seeds were assigned or consumed, and no outcome claim,
card change, or accepted-control promotion occurred.

Remove the temporary workflow and restore ordinary CI. The next eligible gate is
a seed-free, rules-sourced action-surface map for this exact opponent, beginning
with its public mana development, removal/protection, commander scaling, and
commander-damage pressure. A sampled matchup pilot remains unauthorized. v0.7
remains the exact card control.
