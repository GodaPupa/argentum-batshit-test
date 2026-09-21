# v0.9 Phase 25 — real opponent action-surface gate

Date: 2026-09-21
Status: seed-free semantic qualification; no opponent execution or sampled pilot is authorized

## Purpose

Bind the accepted Phase-24 Veteran Beastrider identity to current oracle rules and
classify the finite card surfaces needed before a real opponent action generator
can be designed. This gate maps capabilities only. It does not decide that a
hidden card is available, waive costs or timing, generate actions, select actions,
spend seeds, simulate games, or make matchup claims.

## Frozen evidence

- Accepted opponent identity:
  `izzet-science/opponents/veteran-beastrider-commander-clash-2025.identity.json`
  (`ecde45fcafc4f09ce7e69398ab0787979a6e0d84b4dba83f067a2153176dc0ff`)
- Oracle rules snapshot:
  `izzet-science/opponents/veteran-beastrider-commander-clash-2025.rules.json`
  (`481d255e817b34e76a12cd87fbe5acdfcac8c35eb20f1e233f2f43d1bae9c61c`)
- Action-surface map:
  `izzet-science/opponents/veteran-beastrider-commander-clash-2025.action-surfaces.json`
  (`bd33aa63ab246454fb5be34198605035f50b9016862d24575cd08bc0b4b7f219`)
- Capture program:
  `izzet-science/sim/capture_v09_phase25_veteran_beastrider_rules.py`
  (`f09fcdfb5b9aba27a30b11b84c9142c2295d7483df7b798d298e19306a132bed`)
- Offline/replay validator:
  `izzet-science/sim/validate_v09_phase25_veteran_beastrider_action_surfaces.py`
  (`19aeaeeedcf0727c9d3a929e8496fc7db539009b5cfb7459ec60a449c49032ea`)
- Oracle capture timestamp: `2026-09-21T05:07:40+00:00`
- Oracle source: `https://api.scryfall.com/cards/collection`, keyed by the 85
  oracle IDs frozen in Phase 24
- Format rule source: `https://pdhhomebase.com/rules/`

The oracle snapshot preserves names, oracle IDs, mana costs, type lines, oracle
text, power/toughness, and keywords for every unique card in the exact deck. A
live replay may differ only in capture timestamp.

## Classification

The map contains 12 explicitly bounded surface classes and covers every one of
the 85 unique cards exactly as either mapped or deferred. Seventy-seven unique
cards touch at least one relevant surface; eight value or standalone-threat cards
are explicitly deferred rather than silently omitted.

The initial surface includes:

- public battlefield mana sources and separately labeled hidden land-access actions;
- targeted creature and noncreature control, including conditional damage, fight,
  destruction, exile, and damage suppression;
- targeting protection, damage prevention, and destruction replacement;
- commander entry counters, power scaling, trample, and end-step untapping;
- public commander-damage pressure under PDH's 16-damage loss threshold; and
- Aura access that can find mapped commander scaling or protection.

Classification does not imply legality in a concrete state. For example,
`Destroy Evil` still requires toughness 4 or greater, fight and bite effects still
require legal source creatures, `Wose Pathfinder` still targets another creature,
and tapped or summoning-sick mana creatures do not produce mana. Those conditions
must be represented by the next compiler gate before any action can be emitted.

## Information control

Only `battlefield_mana_source` is classified as public battlefield information.
Land access, hand-cast interaction, and hand-cast protection remain private until
the opponent action is observed. The map therefore cannot place private cards in
the accepted Phase-22 public candidate set. Searches reveal only as their oracle
actions resolve. No hand inference, deck-order inference, or sampled pilot policy
is introduced.

## Acceptance gate

- The accepted card control must remain byte-identical at SHA256
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- The Phase-24 opponent identity must remain byte-identical.
- All 85 oracle identities must be present exactly once and reproduce on a live
  Scryfall replay, apart from the capture timestamp.
- The 12 classification sets must match the frozen map and be sorted, exhaustive,
  and disjoint from the eight-card deferred set.
- The commander-damage threshold must remain 16.
- Twelve contaminated artifacts must be rejected, including incomplete rules,
  changed identities, blank rules, surface drift, hidden-information relabeling,
  changed commander damage, incomplete classification, execution authorization,
  and seed/game contamination.
- Exact source validation and the ordinary repository test suite must pass on
  GitHub, and the uploaded evidence must reproduce independently.

Passing authorizes only a deterministic compiler contract for concrete public
battlefield mana and observed opponent actions. It does not authorize hidden-hand
generation, opponent piloting, randomized execution, a matchup pilot, outcomes,
or a card-control change.
