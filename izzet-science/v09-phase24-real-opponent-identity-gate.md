# v0.9 Phase 24 — real opponent identity gate

Date: 2026-09-21  
Status: identity qualification; no sampled pilot is authorized

## Purpose

Replace the synthetic Phase-23 qualification fixture with one provenance-frozen,
real, legal PDH opponent identity. This gate freezes a list only. It assigns no
behavior policy beyond the accepted Phase-22 selector, maps no card rules into
the simulator, spends no seeds, and makes no matchup claim.

## Selection

Selected identity: `veteran-beastrider-commander-clash-2025-v1`.

- Commander: Veteran Beastrider
- Player: Scarecrow1779
- Event: CPDH Commander Clash: 2025
- Event date: 2025-12-13
- Finish: first
- Recorded TopDeck record: 3-1-1
- Source: `https://topdeck.gg/deck/cpdh-commander-clash-2025/RF01KBbpm5h2JMkwqdzbDuLWKo12`

The cPDH Guide decklist library identifies later 2026 tournament lists, including
Ley Weaver // Lore Weaver winning Burn Book Brawl on 2026-08-22. That Moxfield
identity could not be captured completely because its public page and API denied
the laboratory client. It was therefore not reconstructed, inferred, or frozen.
Veteran Beastrider is the most recent event-winning identity in the cited library
whose complete list, quantities, result, and card identifiers were directly
available from a separate tournament record.

This selection is useful rather than convenient: it presents an aggressive,
creature-dense Selesnya opponent with mana acceleration, protection, fight/bite
interaction, removal, commander scaling, and commander-damage pressure. It does
not claim to be the unique or strongest 2026 deck.

## Frozen evidence

- Exact list: `izzet-science/opponents/veteran-beastrider-commander-clash-2025.txt`
- Identity and legality snapshot:
  `izzet-science/opponents/veteran-beastrider-commander-clash-2025.identity.json`
- Capture program: `izzet-science/sim/capture_v09_phase24_veteran_beastrider.py`
- Offline/replay validator:
  `izzet-science/sim/validate_v09_phase24_veteran_beastrider_identity.py`
- TopDeck page SHA256 at capture:
  `7c8fda3cd45775942cd6394429d3682d518004acf1ead3cb9cf4e56d9fd5f063`
- Capture timestamp: `2026-09-21T04:47:43+00:00`

The governing format source is PDH Home Base's current rules page. It requires an
uncommon creature, vehicle, or spacecraft as commander; 99 commons; 30 starting
life; 16 commander damage; and its stated ban list. The snapshot uses Scryfall's
`legalities.paupercommander` field for all mainboard cards, consistent with PDH
Home Base's documented legality integration.

## Controls and acceptance gate

- Accepted card control remains `v0.7-control.md`, SHA256
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- TopDeck must reproduce the exact commander and 99-card multiset.
- The result identity must remain event, date, player, first-place finish, and
  3-1-1 record.
- Scryfall must return all 85 unique card identities with no missing identifier.
- Veteran Beastrider must be an uncommon creature with green-white color identity.
- Every mainboard card must be legal in `paupercommander` and contained within
  green-white color identity.
- Only Forest and Plains may repeat.
- Any missing, substituted, illegal, off-color, duplicated, or malformed identity
  contaminates and rejects the freeze.

Passing authorizes a seed-free, rules-sourced mapping of the opponent's relevant
action surfaces into the Phase-23 generator boundary. It does not authorize deck
execution, a matchup pilot, seeds, outcomes, or any change to v0.7.
