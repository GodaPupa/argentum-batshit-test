# Manual Transmission — opponent matrix status

Updated from live branch after Hashaton R3-HT promotion.

| Axis | Source/control status | Evidence status | Pilot overlay | Hardware |
|---|---|---|---|---|
| RogSi / turbo Oracle-Consult | inherited sourced checkpoint | ACCEPTED IMPORT; old raw GitHub provenance incomplete | R3 + R3-P context | KEEP v0.7 |
| Blue Farm / Oracle + Breach | current tournament anchor located; exact 99 freeze pending | PROVISIONAL / NEEDS REPRODUCTION | none accepted | KEEP v0.7 |
| Kinnan activated mana | corrected sourced Sterling Sellards checkpoint | ACCEPTED IMPORT; corrected legal pre-loop windows | R3-K accepted | KEEP v0.7 |
| Shorikai / Scepter + Hullbreaker | current tournament anchors located; exact winning 99 freeze pending | PROVISIONAL / NEEDS REPRODUCTION | R3-S/R3-H not accepted | KEEP v0.7 |
| Sisay activated tutor | current tournament anchor located; exact 99 freeze pending | PROVISIONAL / NEEDS REPRODUCTION | R3-C/R3-F not accepted | KEEP v0.7 |
| Magda Treasure / Clock | **exact Sep 19 2026 winner frozen** | PHASE A COMPLETE; Phase B active | R3-M not accepted | KEEP v0.7 |
| Hashaton discard trigger | exact Sep 19 2026 winner frozen | **QUALIFIED exact enumeration + independent audit** | **R3-HT ACCEPTED** | KEEP v0.7 |

## Current hardware state

- Control: Manual Transmission v0.7
- SHA-256: `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`
- Game Changers: 0
- Disposition: `KEEP_V07`
- No evidence currently authorizes v0.8.

## Hashaton closure

- protocol: `MT_HASHATON_R3HT_QUAL_R1_2026_09_23`
- exact states: 2,950
- candidate false stops: 0
- candidate false-live states: 0
- independent audit errors: 0
- rows SHA-256: `d59d9f56e67d6003f2a474b07c919ca68c3e820866cf0b15e48fd030bb01c31d`
- accepted policy: `R3-HT`

## Magda source freeze

- pilot: Jace [NJcEDH]
- event: Top Deck Gauntlet #3 - September
- date: 2026-09-19
- result: 1st / 43, 4-0-1
- mainboard 99 SHA-256: `b9b5979807d7ad4a6dd1679c8c0459c29ad1e5bc28844916c3227ff86e274f82`
- commander+99 SHA-256: `328879aec8d8095eea6d38f43ddc3746e69209dd45726826eec8eb4c8976ed4a`
- exact list contains Clock of Omens and Torpor Orb, so the required ability-stack and ETB-suppression axes are present in the actual winning control.

## Next justified work

1. Magda deterministic legality fixtures.
2. Magda development and predeclared qualification only after fixtures pass.
3. Freeze exact Blue Farm, Shorikai and Sisay submitted 99s; do not substitute generic archetype lists.
4. Reproduce their required interaction gates.
5. Consolidate final Race policy and run same-hardware Cruise/Sport/Race elasticity validation.
