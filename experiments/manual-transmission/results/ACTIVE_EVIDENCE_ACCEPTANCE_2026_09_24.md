# Manual Transmission — active evidence acceptance

Date: 2026-09-24

This record accepts the three contemporary Phase-B opponent overlays qualified by the serialized
active-evidence runner. It does not change the frozen Manual Transmission v0.7 hardware and does not
claim cEDH game-win percentages.

## Frozen hardware

- Manual Transmission v0.7 SHA-256:
  `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`
- Game Changers: 0
- disposition: `KEEP_V07`

## Qualification provenance

- Actions run: **36026769912** — **SUCCESS**
- broad CI run: **36026775814** — **SUCCESS**
- source SHA: `1c321af73f6e04845778f58f20ff0c96907e4382`
- artifact: `manual-transmission-active-evidence`
- artifact ID: **10820441368**
- GitHub artifact ZIP SHA-256:
  `6f4ebed33ee94efec59142eeb6275327380c0bf43420155c6a32a26db143a848`
- independent download reproduced the exact ZIP SHA-256
- manifest schema: `manual-transmission-ci-evidence-manifest-v1`
- manifest head SHA matches the qualification source SHA

## Accepted overlays

### R3-BF — Blue Farm
- protocol: `MT_BLUE_FARM_R3BF_QUAL_R1_2026_09_24`
- exact states: 144
- reference false stops: 19
- candidate false stops / false-live: 0 / 0
- independent audit errors: 0
- rows SHA-256:
  `59f611bdd6421963ab6c929e078cbb00429ec14680aef1c621fb9cef6cbe690e`

### R3-SH — Shorikai
- protocol: `MT_SHORIKAI_R3SH_QUAL_R1_2026_09_24`
- exact states: 120
- reference false stops: 55
- candidate false stops / false-live: 0 / 0
- independent audit errors: 0
- rows SHA-256:
  `290f205ca23669e3d0c4acc551ee95cdaed941001a24e4c35c0a4c4fb2403482`

### R3-SY — Sisay
- protocol: `MT_SISAY_R3SY_QUAL_R1_2026_09_24`
- exact states: 220,972
- reference false stops: 12,007
- candidate false stops / false-live: 0 / 0
- target-classification errors: 0
- LKI changed-ceiling errors: 0
- independent audit errors: 0
- rows SHA-256:
  `08132877c8c0ce6669596af4b524310fed3326ecef1a3d98627c249482e76ea3`

These are bounded deterministic policy/timing qualifications. They are not cEDH matchup win rates.

## Remaining Race qualification work

RogSi and Kinnan/Basalt now have contemporary frozen controls, Phase-B fixtures, and green
DEVELOPMENT-ONLY screens. They remain unaccepted until separately predeclared formal qualification
and independent audits pass. After those axes close, consolidate the final Race policy and run the
same-hardware Cruise/Sport/Race elasticity validation before any hardware promotion decision.
