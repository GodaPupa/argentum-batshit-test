# v0.9 Phase 23 — synthetic fixture action-generator gate

Date: 2026-09-21  
Status: qualification gate; no sampled pilot is authorized

## Purpose

Qualify the deterministic boundary from a concrete opponent battlefield state
to the accepted Phase-22 public action-candidate contract. This gate spends no
experimental seeds and makes no deck, matchup, or outcome claim.

## Frozen identity

`phase23-public-action-fixture-v1` is a deliberately synthetic public-state
fixture. It is not a Magic deck and is not representative of any opponent or
metagame. Its four visible sources exercise exactly the four accepted policy
classes:

| Source | Public cost | Consequence |
|---|---:|---|
| `fixture-pressure-source` | one red | 5 damage before the next response |
| `fixture-removal-source` | one black | remove Izzet Guildmage |
| `fixture-lock-source` | one blue | prohibit `cast_sorcery` through next main |
| `fixture-tempo-source` | one blue | tempo-only change |

The canonical executable identity is
`izzet-science/sim/capsize_fixture_action_generator.py`. Any edit creates a new
identity and requires requalification.

## Controls

- Accepted card control remains `v0.7-control.md`, SHA-256
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- The generator is seed-free, deterministic, and public-only.
- It derives availability from exact source presence, Guildmage presence, and
  colored mana. It does not receive either player's hidden zones.
- Unknown, duplicate, malformed, or out-of-domain state is contamination and
  rejects the whole input.
- Empty legal choice sets remain valid and cause the Phase-22 selector to pass.
- Candidate order is the frozen identity order, never container iteration order.

## Acceptance gate

Accept only if all 256 combinations of four source-presence bits, Guildmage
presence, and three colored-mana bits produce exactly the expected legal action
set; malformed inputs are rejected; and a full generated set passes unchanged
through the accepted opponent selector, concrete adapter, event ledger, and
Capsize response policy.

Passing authorizes selection and provenance-freezing of a real PDH opponent
identity plus a rules-sourced adapter. It does **not** authorize a sampled
matchup pilot.
