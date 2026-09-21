# v0.9 Phase 20 — Public Event Ledger and Opponent Policy Contract Accepted

Disposition: `V09_PHASE20_SEED_FREE_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- Semantic source commit: `0792a6f0b1e468b13f44d050dcd6d31019d002a6`
- Semantic source tree: `ba8084bc8ad3388c1408b1dfe281e8f5f35916e6`
- Workflow runner commit: `1ee5d79fabff00bbd6de6bf6c679b09ea57fdb6f`
- Workflow runner tree: `ce1d5db8f60803074a92f725b52638e4c2a2fc87`
- Temporary workflow SHA256: `42d7d241e0ff7168caf03b0fad72599b3a5d117a2b07fcbf5dcdba00d1858f49`
- Event-ledger module SHA256: `810082856bd9bcd1dd4e74bfa03255d8bac437a0f424a16b8a34cee82f207977`
- Validator SHA256: `ef08f454e96e340d3dd5fcad01cc8ee48b256b38d39ef996be1b514a483f295c`
- Gate SHA256: `956ecb4973d5b05a2d9947b22be668b588a9d51e51fdc81f5c41e15d7d99fc1f`

## GitHub validation and artifact

- Workflow run: `35558604980` — success
- Job: `106206992693` — success
- Artifact: `10620668632`, `izzet-v09-phase20-public-event-ledger`
- Artifact size: 2,696 bytes
- Artifact ZIP SHA256: `470438d8a9fd82b36f71239beb6793efd37a355e660986d8e1964b09ad7a0e4a`
- Manifest SHA256: `45cda3abf9e8256651eafc595a9dddbf193ce15f4860cd88eea5dbaaf59536e0`
- Phase-20 receipt SHA256: `7a8d8a8e21b44acf3744253f5e512b4ebbe93943a1b80d3414324bce44370870`

Exact checkout, all bound hashes, the Phase-15 through Phase-17 controls, the
accepted Phase-19 response policy, 96 exhaustive public-fact states, eighteen
malformed-input rejections, manifest construction, and artifact upload passed. An
independent download matched GitHub's ZIP digest and contained exactly the six
declared files. After removing the top-level path that GitHub's artifact packaging
strips, every validation hash reproduced. The artifact confirms zero assigned or
consumed experimental seeds and zero outcome claims.

## Decision

Accept the public-state event ledger and deterministic opponent-policy declaration
as frozen methodology. The ledger derives severity rather than accepting a caller-
supplied class, rejects contradictory public facts, binds stable provenance and
ordering, and retains but omits nonlethal Guildmage events that the frozen Phase-19
consumer cannot encode.

This acceptance does not prove any public-state witness, event frequency, opponent
choice, tempo, survival, or win value. No opponent deck or sampled event stream is
modeled. No card or accepted behavior policy is promoted or changed.

Remove the temporary workflow and restore ordinary CI. The next eligible gate is a
seed-free concrete opponent-adapter vocabulary and fixture suite that derives the
four ledger facts from frozen public game-state predicates. A sampled opponent pilot
remains unauthorized. v0.7 remains the exact card control.
