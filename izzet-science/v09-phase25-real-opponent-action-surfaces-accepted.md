# v0.9 Phase 25 — Real Opponent Action Surfaces Accepted

Disposition: `V09_PHASE25_ACTION_SURFACES_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- Opponent identity: `veteran-beastrider-commander-clash-2025-v1`
- Semantic source commit: `60c1d94290ae557c31ec5f0c7979ffb9497b1950`
- Semantic source tree: `81d41a452a49a35b1d017bf7d23d978671633347`
- Workflow runner commit: `3f43def582c813df3f99bac23fd07bf3cd3ac87a`
- Workflow runner tree: `bc0c7adbc539366f1dd5f1f1faaae8f7cce17c7e`
- Temporary workflow SHA256: `c2451be8398d2c80b668308b9de658b71c793dcfd73bfd0083232efa63372814`
- Identity snapshot SHA256: `ecde45fcafc4f09ce7e69398ab0787979a6e0d84b4dba83f067a2153176dc0ff`
- Oracle rules snapshot SHA256: `481d255e817b34e76a12cd87fbe5acdfcac8c35eb20f1e233f2f43d1bae9c61c`
- Action-surface map SHA256: `bd33aa63ab246454fb5be34198605035f50b9016862d24575cd08bc0b4b7f219`
- Capture program SHA256: `f09fcdfb5b9aba27a30b11b84c9142c2295d7483df7b798d298e19306a132bed`
- Validator SHA256: `19aeaeeedcf0727c9d3a929e8496fc7db539009b5cfb7459ec60a449c49032ea`
- Gate SHA256: `4c6a4b70fb261b21538709e2c09608ed8d64e7779d060126d4b667e6099ca24f`

## GitHub validation and artifact

- Workflow run: `35564010877` — success
- Job: `106222142819` — success
- Artifact: `10623300820`, `izzet-v09-phase25-real-opponent-action-surfaces`
- Artifact size: 23,251 bytes
- Artifact ZIP SHA256: `7dedbb8f22c9d1ec086e64c2c538c3b0a0a82aa4cb650bcaf205effea6d79db2`
- Manifest SHA256: `1bc84782ebf4a26f005e4828ef976fe319f5d10e8f595b58eafcd7d677b9e5da`
- Live replay SHA256: `7f74baca7206cfce131a3d38e09ac5c28ddd810320604a18675c4d961b38d0c1`
- Phase-25 online receipt SHA256: `09e16c2e7ad892bf8cf3e18aed07e4226c92d79c430d6172780b9d054db887cb`

Exact checkout, all bound hashes, the accepted Phase-23 generator boundary, the
accepted Phase-24 identity, the frozen 85-card oracle snapshot, the 12-class
surface map, twelve contaminated-artifact rejections, a fresh Scryfall replay,
manifest construction, and artifact upload passed.

An independent download matched GitHub's ZIP digest, contained exactly the nine
declared files, reproduced all eight payload hashes after accounting for GitHub's
stripped upload-directory prefix, matched the three frozen repository payloads,
and passed the replay validator again.

## Decision

Accept the rules-sourced Veteran Beastrider action-surface map. It classifies all
85 unique card identities: 77 touch at least one of 12 relevant surfaces and eight
are explicitly deferred. Public battlefield mana is separated from hidden land
access; hidden interaction and protection remain private until observed. The map
also freezes the current 16-damage PDH commander threshold and the cards capable
of commander entry scaling, power scaling, trample, untapping, and Aura access.

This acceptance freezes capabilities, not concrete-state legality or behavior.
It does not expose a private hand, generate or select opponent actions, waive any
printed cost or restriction, or claim matchup strength. Zero games ran, zero
experimental seeds were assigned or consumed, and no outcome claim, card change,
or accepted-control promotion occurred.

Remove the temporary workflow and restore ordinary CI. The next eligible gate is
a deterministic, seed-free compiler contract for public battlefield mana and
observed opponent actions, with explicit timing, target, tapping, summoning-sick,
and information-boundary checks. Hidden-hand generation and a sampled matchup
pilot remain unauthorized. v0.7 remains the exact card control.
