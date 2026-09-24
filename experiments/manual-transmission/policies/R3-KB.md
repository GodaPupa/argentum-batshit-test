# R3-KB — Kinnan/Basalt Race overlay

Status: **ACCEPTED**

- Protocol: `MT_KINNAN_BASALT_R3KB_QUAL_R1_2026_09_24`
- Frozen hardware: Manual Transmission v0.7
- Hardware SHA-256: `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`
- Exact qualification rows: **74**
- Candidate false stops / false-live: **0 / 0**
- Candidate illegal windows / terminal errors: **0 / 0**
- Reference false stops / illegal windows / false terminals: **7 / 3 / 20**
- Independent audit errors: **0**
- Rows SHA-256: `683129ac1a066bd2df5c9013af2bafaae6a4de5f78b80a6f904c12f5891628a8`
- Accepted evidence run: **36028662543**
- Accepted evidence source SHA: `641e605c44384744bd5c233055e8e557549e2149`
- Evidence artifact: `manual-transmission-active-evidence`, artifact ID **10820542784**
- Artifact ZIP SHA-256: `9b5ff0bc776a240016b96bcdfeb8246eca789e9f44de9502cfe0c7fa6db645f8`
- Independent artifact download reproduced that digest.
- Manifest audit: **31 / 31 files** matched recorded SHA-256 and byte size.
- Same-source broad CI run **36028672513**: **SUCCESS**.

## Qualified scope

R3-KB is a public-state Race timing/resource overlay. It does not invent windows inside Basalt or
Kinnan mana abilities, preserves mana already produced, treats paid Basalt untaps as real stack
windows, handles Tidebinder only at live activation windows, respects artifact/creature targeting,
and refuses to score arbitrary colorless mana as terminal while colored outlet costs remain.

This promotion changes no cards and authorizes no cEDH win-rate claim.
