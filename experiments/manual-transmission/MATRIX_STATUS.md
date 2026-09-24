# Manual Transmission — opponent matrix status

Updated from live branch after the serialized evidence run at `1c321af73f6e04845778f58f20ff0c96907e4382`.

| Axis | Source/control status | Evidence status | Pilot overlay | Hardware |
|---|---|---|---|---|
| RogSi / turbo Oracle-Consult | **contemporary exact control frozen** | **QUALIFIED exact enumeration + independent audit** | **R3-RS ACCEPTED** | KEEP v0.7 |
| Blue Farm / Oracle + Breach | **exact Sep 19 2026 winner frozen** | **QUALIFIED exact enumeration + independent audit** | **R3-BF ACCEPTED** | KEEP v0.7 |
| Kinnan / Basalt activated mana | **contemporary exact control frozen** | **QUALIFIED exact enumeration + independent audit** | **R3-KB ACCEPTED** | KEEP v0.7 |
| Shorikai / Scepter + Hullbreaker | **exact Sep 20 2026 contemporary control frozen** | **QUALIFIED exact enumeration + independent audit** | **R3-SH ACCEPTED** | KEEP v0.7 |
| Sisay activated tutor | **exact Sep 23 2026 winner frozen** | **QUALIFIED exact enumeration + independent audit** | **R3-SY ACCEPTED** | KEEP v0.7 |
| Magda Treasure / Clock | exact Sep 19 2026 winner frozen | **QUALIFIED exact enumeration + independent audit** | **R3-M ACCEPTED** | KEEP v0.7 |
| Hashaton discard trigger | exact Sep 19 2026 winner frozen | **QUALIFIED exact enumeration + independent audit** | **R3-HT ACCEPTED** | KEEP v0.7 |

## Current hardware state

- Control: Manual Transmission v0.7
- SHA-256: `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`
- Game Changers: 0
- Disposition: `KEEP_V07`
- No current evidence authorizes v0.8.

## Accepted qualification overlays

### Hashaton — R3-HT
- protocol: `MT_HASHATON_R3HT_QUAL_R1_2026_09_23`
- exact states: 2,950
- candidate false stops / false-live: 0 / 0
- independent audit errors: 0
- rows SHA-256: `d59d9f56e67d6003f2a474b07c919ca68c3e820866cf0b15e48fd030bb01c31d`

### Magda — R3-M
- protocol: `MT_MAGDA_R3M_QUAL_R1_2026_09_24`
- exact states: 960
- generic-reference false stops: 444
- candidate false stops / false-live: 0 / 0
- independent audit errors: 0
- rows SHA-256: `00c3c9cf65d5d59b9be396ec3c615468be4a3fa122d0f0df17cc22d2d05305c9`


### Blue Farm — R3-BF
- protocol: `MT_BLUE_FARM_R3BF_QUAL_R1_2026_09_24`
- exact states: 144
- generic-reference false stops: 19
- candidate false stops / false-live: 0 / 0
- independent audit errors: 0
- rows SHA-256: `59f611bdd6421963ab6c929e078cbb00429ec14680aef1c621fb9cef6cbe690e`

### Shorikai — R3-SH
- protocol: `MT_SHORIKAI_R3SH_QUAL_R1_2026_09_24`
- exact states: 120
- generic-reference false stops: 55
- candidate false stops / false-live: 0 / 0
- independent audit errors: 0
- rows SHA-256: `290f205ca23669e3d0c4acc551ee95cdaed941001a24e4c35c0a4c4fb2403482`

### Sisay — R3-SY
- protocol: `MT_SISAY_R3SY_QUAL_R1_2026_09_24`
- exact states: 220,972
- generic-reference false stops: 12,007
- candidate false stops / false-live: 0 / 0
- candidate target-classification errors: 0
- LKI changed-ceiling errors: 0
- independent audit errors: 0
- rows SHA-256: `08132877c8c0ce6669596af4b524310fed3326ecef1a3d98627c249482e76ea3`

### Active-evidence provenance
- Actions run: **36026769912** — **SUCCESS**.
- Source SHA: `1c321af73f6e04845778f58f20ff0c96907e4382`.
- Artifact: `manual-transmission-active-evidence`, ID **10820441368**.
- Artifact ZIP SHA-256:
  `6f4ebed33ee94efec59142eeb6275327380c0bf43420155c6a32a26db143a848`.
- Independent artifact download reproduced that exact ZIP digest.
- Artifact manifest schema: `manual-transmission-ci-evidence-manifest-v1`;
  manifest head SHA equals the qualification source SHA.
- Broad CI run **36026775814** also completed **SUCCESS** at the same source SHA.

### Blue Farm — R3-BF
- protocol: `MT_BLUE_FARM_R3BF_QUAL_R1_2026_09_24`
- exact states: 144
- reference false stops: 19
- candidate false stops / false-live: 0 / 0
- independent audit errors: 0
- rows SHA-256: `59f611bdd6421963ab6c929e078cbb00429ec14680aef1c621fb9cef6cbe690e`

### Shorikai — R3-SH
- protocol: `MT_SHORIKAI_R3SH_QUAL_R1_2026_09_24`
- exact states: 120
- reference false stops: 55
- candidate false stops / false-live: 0 / 0
- independent audit errors: 0
- rows SHA-256: `290f205ca23669e3d0c4acc551ee95cdaed941001a24e4c35c0a4c4fb2403482`

### Sisay — R3-SY
- protocol: `MT_SISAY_R3SY_QUAL_R1_2026_09_24`
- exact states: 220,972
- reference false stops: 12,007
- candidate false stops / false-live: 0 / 0
- candidate target-classification errors: 0
- LKI ceiling errors: 0
- independent audit errors: 0
- rows SHA-256: `08132877c8c0ce6669596af4b524310fed3326ecef1a3d98627c249482e76ea3`

The three newly promoted overlays are bound to serialized evidence run **36026769912**,
source `1c321af73f6e04845778f58f20ff0c96907e4382`, artifact
`manual-transmission-active-evidence` ID **10820441368**, ZIP SHA-256
`6f4ebed33ee94efec59142eeb6275327380c0bf43420155c6a32a26db143a848`.
An independent download reproduced that digest.

## Contemporary provenance repair

### RogSi
- timing fixture protocol: `MT_ROGSI_TIMING_FIXTURES_R1_2026_09_24`
- fixtures: **18 / 18 PASS**
- development protocol: `MT_ROGSI_POLICY_DEVELOPMENT_R1_2026_09_24`
- development cells: **444**
- candidate false stop / false-live / mana errors: **0 / 0 / 0**
- random seeds / qualification outcomes exposed: **0 / 0**
- disposition: contemporary raw timing/policy surface reproduced; candidate is not yet promoted.

### Kinnan / Basalt
- timing fixture protocol: `MT_KINNAN_BASALT_TIMING_FIXTURES_R1_2026_09_24`
- fixtures: **16 / 16 PASS**
- development protocol: `MT_KINNAN_BASALT_POLICY_DEVELOPMENT_R1_2026_09_24`
- candidate false stop / false-live / illegal windows / terminal errors: **0 / 0 / 0 / 0**
- random seeds / qualification outcomes exposed: **0 / 0**
- disposition: contemporary raw timing/policy surface reproduced; candidate is not yet promoted.

## Newly accepted contemporary overlays

### RogSi — R3-RS
- protocol: `MT_ROGSI_R3RS_QUAL_R1_2026_09_24`
- exact states: **444**
- candidate false stops / false-live / mana errors: **0 / 0 / 0**
- independent audit errors: **0**
- rows SHA-256: `ad73cf503ba1160e604c7cc1f5e9b24c49ab41c82bbd61bb2c7e1b1ca461cc66`

### Kinnan / Basalt — R3-KB
- protocol: `MT_KINNAN_BASALT_R3KB_QUAL_R1_2026_09_24`
- exact states: **74**
- candidate false stops / false-live / illegal windows / terminal errors: **0 / 0 / 0 / 0**
- independent audit errors: **0**
- rows SHA-256: `683129ac1a066bd2df5c9013af2bafaae6a4de5f78b80a6f904c12f5891628a8`

Both promotions are bound to Actions run **36028662543**, source
`641e605c44384744bd5c233055e8e557549e2149`, artifact
`manual-transmission-active-evidence` ID **10820542784**, ZIP SHA-256
`9b5ff0bc776a240016b96bcdfeb8246eca789e9f44de9502cfe0c7fa6db645f8`.
An independent download reproduced that digest; every one of the 31 manifest entries independently
matched its recorded SHA-256 and byte size. Same-source broad CI run **36028672513** succeeded.

## Final Race policy

- Protocol: `MT_FINAL_RACE_POLICY_R1_2026_09_24`
- Status: **ACCEPTED**
- Dedicated audit run **36031552516**: **SUCCESS**
- Accepted source: `9530ff2de311534e0f7f80137938aff20ed41cc4`
- Card changes authorized: **0**
- Hardware remains **Manual Transmission v0.7 / KEEP_V07**

## Next justified work

1. Run same-hardware Cruise / Sport / Race elasticity validation.
2. Finish with `KEEP_V07` unless the elasticity evidence itself supports a hardware change.

Exact policy enumerations are timing/classification evidence, not cEDH matchup win rates.
