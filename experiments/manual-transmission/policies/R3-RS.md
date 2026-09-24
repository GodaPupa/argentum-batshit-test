# R3-RS — RogSi Race overlay

Status: **ACCEPTED**

- Protocol: `MT_ROGSI_R3RS_QUAL_R1_2026_09_24`
- Frozen hardware: Manual Transmission v0.7
- Hardware SHA-256: `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`
- Exact qualification rows: **444**
- Candidate false stops / false-live / mana errors: **0 / 0 / 0**
- Reference false stops / false-live: **126 / 12**
- Independent audit errors: **0**
- Rows SHA-256: `ad73cf503ba1160e604c7cc1f5e9b24c49ab41c82bbd61bb2c7e1b1ca461cc66`
- Accepted evidence run: **36028662543**
- Accepted evidence source SHA: `641e605c44384744bd5c233055e8e557549e2149`
- Evidence artifact: `manual-transmission-active-evidence`, artifact ID **10820542784**
- Artifact ZIP SHA-256: `9b5ff0bc776a240016b96bcdfeb8246eca789e9f44de9502cfe0c7fa6db645f8`
- Independent artifact download reproduced that digest.
- Manifest audit: **31 / 31 files** matched recorded SHA-256 and byte size.
- Same-source broad CI run **36028672513**: **SUCCESS**.

## Qualified scope

R3-RS is a public-state Race timing/resource overlay. It distinguishes Oracle spell/ETB windows,
applies Defense Grid only to non-active players, models live Vexing Bauble trigger behavior,
counts actual casts for Mindbreak Trap, tracks Breach fuel without reuse, treats Phyrexian Tower
as colorless unless a creature is actually spent for black mana, and records Pact debt.

This promotion changes no cards and authorizes no cEDH win-rate claim.
