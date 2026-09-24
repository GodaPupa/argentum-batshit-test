# Manual Transmission — opponent matrix status

Updated from live branch after Hashaton R3-HT and Magda R3-M promotion plus source freeze of all remaining provisional axes.

| Axis | Source/control status | Evidence status | Pilot overlay | Hardware |
|---|---|---|---|---|
| RogSi / turbo Oracle-Consult | inherited sourced checkpoint | ACCEPTED IMPORT; old raw GitHub provenance incomplete | R3 + R3-P context | KEEP v0.7 |
| Blue Farm / Oracle + Breach | **exact Sep 19 2026 winner frozen** | **PHASE B COMPLETE**; development screen green | none accepted | KEEP v0.7 |
| Kinnan activated mana | corrected sourced Sterling Sellards checkpoint | ACCEPTED IMPORT; corrected legal pre-loop windows | R3-K accepted | KEEP v0.7 |
| Shorikai / Scepter + Hullbreaker | **exact Sep 20 2026 contemporary control frozen** | **PHASE B COMPLETE**; development screen green | R3-S/R3-H not accepted | KEEP v0.7 |
| Sisay activated tutor | **exact Sep 23 2026 winner frozen** | **PHASE B COMPLETE**; development screen pending | R3-C/R3-F not accepted | KEEP v0.7 |
| Magda Treasure / Clock | exact Sep 19 2026 winner frozen | **QUALIFIED exact enumeration + independent audit** | **R3-M ACCEPTED** | KEEP v0.7 |
| Hashaton discard trigger | exact Sep 19 2026 winner frozen | **QUALIFIED exact enumeration + independent audit** | **R3-HT ACCEPTED** | KEEP v0.7 |

## Current hardware state

- Control: Manual Transmission v0.7
- SHA-256: `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`
- Game Changers: 0
- Disposition: `KEEP_V07`
- No evidence currently authorizes v0.8.

## Accepted new qualification overlays

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

## Remaining exact source freezes

### Blue Farm
- pilot: Nameless
- event: Commander Showdown XXXXV: Fall is Hot!
- date: 2026-09-19
- result: 1st / 59, 5-0-2
- 98 mainboard SHA-256: `1c3475ef24051797385c31d40f8b5c9c42d68b44492f2f7b6fbaf6b2f0c77b95`
- commanders+98 SHA-256: `164cef506c6069f1c69f46b1dfa5ff50449d3dae56df77a086ff04b7ab0e31e8`
- required Oracle/Consult, Breach/LED/Brain Freeze, Ranger, Voice, Silence/Chant and free-protection package all present.

### Shorikai
- pilot: Mark Domzil
- event: Cool City cEDH vol. 2
- date: 2026-09-20
- result: 6th / 19, 2-2-0
- 99 SHA-256: `8fc3d7904ba0dbf2dcc8283dbc892d28a47bc0c9fa0fc3f672a6c9969ee61589`
- commander+99 SHA-256: `9fedd6fdf069e09f34fb4619dedf63cb95db003524572e1b9dbe602329c7b380`
- exact control includes Isochron Scepter / Dramatic Reversal, Hullbreaker Horror, Humility, Ranger-Captain and Silence/Chant.

### Sisay
- pilot: Brad Corbin
- event: Forge & Fire Weekly cEDH
- date: 2026-09-23
- result: 1st / 24, 2-0-1
- 99 SHA-256: `61a0a1673338181eb3af5ad4048d79cb5a5759d6ce223acada19487861341faa`
- commander+99 SHA-256: `25d6794046a80dba919ec19a4491bb6ce8ef799b0d2de2afbaac377592b66580`
- exact control includes Marvin/Ioreth, Derevi/Emiel and Shang-Chi/Tyvar branches.

## Next justified work

1. **Complete:** all three new Phase-B fixture suites pass in GitHub.
2. Run DEVELOPMENT ONLY policy exploration for each passing axis; Blue Farm and Shorikai screens are green, Sisay remains pending.
3. Predeclare and execute exact/replayable qualification independently.
4. Consolidate final Race policy.
5. Reproduce or explicitly limit the inherited RogSi/Kinnan raw-replay gap.
6. Run same-hardware Cruise/Sport/Race elasticity validation and finalize KEEP_V07 versus evidence-supported hardware promotion.
