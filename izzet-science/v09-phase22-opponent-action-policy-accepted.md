# v0.9 Phase 22 — Deterministic Opponent Action Policy Accepted

Disposition: `V09_PHASE22_SEED_FREE_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- Semantic source commit: `26c15c897e4ca667f24486dd85b8d8f7dd2ad161`
- Semantic source tree: `7d9d461309e32d2275a035e3fb84e0d483e1ba60`
- Workflow runner commit: `0fa5c46a2a57d3c2e5c2aee2621166cb69604d15`
- Workflow runner tree: `6cf7446a2212e0a98396d3b19a86105ab24d49a9`
- Temporary workflow SHA256: `9e816c8dc518ade393743b1cb937e9c82d82b60dcc17999a78f922133c353f8c`
- Action-policy module SHA256: `ae98059481dde833a2777093c83e28cb67e5c7f23d9876cab6227b7fd05cdc6a`
- Validator SHA256: `edf91bc77d9eeb02a3d6f0f80170e2a43edcaf6261102931c2e0db9faf6da38a`
- Gate SHA256: `d429b2e197ac74a1ce97d00b24cddace77e3647bc41d01972ad6d8f9d3160a16`

## GitHub validation and artifact

- Workflow run: `35560189696` — success
- Job: `106211492874` — success
- Artifact: `10621951293`, `izzet-v09-phase22-opponent-action-policy`
- Artifact size: 3,748 bytes
- Artifact ZIP SHA256: `7e0d5cb98e11500961faf4b23de9f778edf36a6580b836d532192215fbffbec1`
- Manifest SHA256: `6c4b3fa1d70979809eeca2a78a667a902d9bff8608295a27d013552282e608cd`
- Phase-22 receipt SHA256: `cc5e7fa6d32a40189cea83311cbab530108d219f06635c07e83654bb7f917303`

Exact checkout, all bound hashes, the complete adjacent semantic chain, all 24
four-action permutations, 96 exhaustive public action states, twelve contaminated-
input rejections, manifest construction, and artifact upload passed. An independent
download matched GitHub's ZIP digest, contained exactly the eight declared files,
and reproduced every validation hash after accounting for GitHub's stripped upload-
directory prefix.

## Decision

Accept the deterministic opponent action selector as frozen methodology. It chooses
known imminent loss, public Guildmage removal, frozen-plan lock, then tempo-only,
with stable event identity for ties and pass for an empty choice set. Illegal,
hidden-information-dependent, duplicate, or mixed-window candidates fail closed.

This policy does not inspect the Izzet hand and does not establish strategic
optimality. It generates no actions, specifies no opponent deck, and assigns no
event frequency. Zero games ran, zero experimental seeds were assigned or consumed,
and no outcome claim, card change, or behavior-policy promotion occurred.

Remove the temporary workflow and restore ordinary CI. The next eligible gate is a
seed-free concrete opponent action generator and fixture suite for a separately
frozen opponent identity. A sampled opponent pilot remains unauthorized. v0.7
remains the exact card control.
