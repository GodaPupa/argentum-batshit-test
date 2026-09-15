# Pest Control Matchup Gate 1 — SoterX Mono Red Madness

## Administrative reconstruction notice

This record is a transparent reconstruction. The original uncommitted provenance record was lost
during transient workspace cleanup before commit.

- Original uncommitted provenance SHA-256:
  `da59435fdf92d514e233eeaf2e158f6b85c82e701ef5f78f5cb6308dd86c9cf9`
- Disposition: lost during transient workspace cleanup before commit
- Exact original bytes: unavailable
- Replacement status: reconstructed from the approved opponent identity, sourced list, and binding
  deck hashes
- This replacement does not claim to match the lost bytes and receives a new SHA-256 identity.

## Protocol identity

- Protocol ID: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Pilot: SoterX
- Finish: first place
- Event: 52-player MTGO Pauper Challenge 32 #12854069
- Snapshot date: September 11, 2026
- Authorized scope: preboard independent games using the exact 60-card maindeck only
- Tournament-list source:
  <https://mtgdecks.net/Pauper/mono-red-madness-decklist-by-soterx-3083867>
- Event source:
  <https://mtgdecks.net/Pauper/mtgo-pauper-challenge-32-12854069-tournament-269679>

The complete sourced 75 is the opponent identity. The sideboard is recorded but is not authorized for
instantiation, implementation, validation, or gameplay during Gates 1–2.

## Exact ordered maindeck — 60

```text
20 Mountain
4 Guttersnipe
3 Kessig Flamebreather
4 Sneaky Snacker
2 Faithless Looting
4 Fiery Temper
4 Fireblast
4 Grab the Prize
4 Highway Robbery
4 Lava Dart
4 Lightning Bolt
3 Melded Moxite
```

## Exact ordered sideboard — 15

```text
3 Campfire
2 Pyroblast
3 Red Elemental Blast
3 Relic of Progenitus
4 Smash to Smithereens
```

## Canonical hash contract

The maindeck and sideboard canonical byte streams contain one ordered `Name,count` row per line with
a trailing LF. The complete-75 byte stream is `MAIN` plus LF, the maindeck rows, `SIDEBOARD` plus LF,
and the sideboard rows.

- Maindeck SHA-256: `38c7850d1b9b070637502cedfffc6116d3504a525db8b51223505d7935134258`
- Sideboard SHA-256: `d0aab592e6c82ad019dba0eadc028db75ef5cf13c9b3e4e0531a04d513bfc77a`
- Complete-75 SHA-256: `e9ff7ecbdbc8f41ebe526fe8fee4f87f706e0630491f9d78121677922fbd647d`

Canonical maindeck bytes:

```text
Mountain,20
Guttersnipe,4
Kessig Flamebreather,3
Sneaky Snacker,4
Faithless Looting,2
Fiery Temper,4
Fireblast,4
Grab the Prize,4
Highway Robbery,4
Lava Dart,4
Lightning Bolt,4
Melded Moxite,3
```

Canonical sideboard bytes:

```text
Campfire,3
Pyroblast,2
Red Elemental Blast,3
Relic of Progenitus,3
Smash to Smithereens,4
```

Canonical complete-75 bytes prepend `MAIN` and insert `SIDEBOARD` between the two ordered sections.

## Gate boundary

This record approves the opponent identity only. It does not contain seeds, construct a gameplay
runner, execute a game, mutate either deck, authorize sideboarding, or authorize the proposed Block A.
Block A remains proposed as 50 independent preboard games split 25 Pest-on-play and 25 Pest-on-draw.
