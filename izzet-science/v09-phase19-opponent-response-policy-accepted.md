# v0.9 Phase 19 — Opponent Event and Response Policy Accepted

Disposition: `V09_PHASE19_SEED_FREE_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- Semantic source commit: `f1a8ae8c019ebd48bc748459c952011e6552a48b`
- Semantic source tree: `2fe567198d6b47dc0641af2453d123ec8c098e4f`
- Workflow runner commit: `11dd98561cbd2c7b682bbac2231005854774c404`
- Workflow runner tree: `8118fdc13ac854343efbb9eab9f68e4c84a1a7ac`
- Temporary workflow SHA256: `d5f35d582af5e7d7ab4691010eee297d392b995812f5bf9a6680323d9bb71ed6`
- Response policy SHA256: `65b605fd0c62c5dbf9973997f4ddb55c9d026814aeba7f3d7a87a679c0987197`
- Validator SHA256: `b6fb9d6a84a6ce84c875d2320d6e847cc9f3abbce81b4153f0c004e1d52cb82e`

## GitHub validation and artifact

- Workflow run: `35557828845` — success
- Job: `106204828351` — success
- Artifact: `10620444126`, `izzet-v09-phase19-response-policy`
- Artifact size: 2,184 bytes
- Artifact ZIP SHA256: `137224d973e93a4ece228ceb090c84bd2cbef64b54d87f56d9da84d088fd8681`
- Manifest SHA256: `67c7442bc380f80c8956b9940df09d86e14c20ddf298e378ea99d018d396c680`
- Phase-19 receipt SHA256: `64d5fed43fec20398ece0a4b371367796627d4aed3deac45f12ac0d6e10ff79c`

Exact checkout, all bound hashes, the Phase-15 through Phase-17 controls, 84
exhaustive Phase-19 policy states, eight malformed-input rejections, manifest
construction, and artifact upload passed. Independent download matched GitHub's ZIP
digest, contained exactly the five declared files, reproduced every receipt hash,
and confirmed zero assigned or consumed experimental seeds and zero outcome claims.

## Decision

Accept this response policy as frozen methodology for future opponent-aware work.
It is deliberately conservative: imminent loss outranks lethal-combo Guildmage
rescue, which outranks a declared next-main lock; tempo-only events never authorize
a Capsize cast. Equal-priority events use stable identity, and buyback is chosen only
when it is already affordable inside the single modeled response window.

This is not evidence that the priority order is strategically optimal, that any
event occurs at a particular rate, or that a response improves survival or win rate.
No deck, opponent, sampled event stream, recast value, or multiplayer politics are
modeled. No card or previously accepted behavior policy is promoted or changed.

Remove the temporary workflow and restore ordinary CI. The next eligible gate is a
seed-free event-ledger and opponent-policy contract that supplies and validates the
public-state event classifications consumed here. v0.7 remains the exact card
control.
