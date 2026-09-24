# Manual Transmission — Final Race Policy R1

Status: **ACCEPTED**

Protocol: `MT_FINAL_RACE_POLICY_R1_2026_09_24`

## Hardware invariant

- Hardware: **Manual Transmission v0.7**
- SHA-256: `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`
- Game Changers: **0**
- Current disposition: **KEEP_V07**
- This policy changes **zero cards**.

## Generic Race layers

Race begins from the already-accepted generic policy stack:

1. **R3 Balanced Mulligan**
2. **R3-P Priority Conservation**
3. **R3-K Kinnan Awareness**

Those generic layers own mulligan/resource discipline and non-axis-specific timing. An opponent
overlay may refine a public timing/resource classification; it never changes the hardware.

## Accepted opponent overlays

| Axis | Overlay |
|---|---|
| Hashaton | R3-HT |
| Magda / Clock | R3-M |
| Blue Farm | R3-BF |
| Shorikai | R3-SH |
| Sisay | R3-SY |
| RogSi | R3-RS |
| Kinnan / Basalt | R3-KB |

Every overlay above is formally accepted from an exact/replayable enumeration with an independent
audit. Their individual policy files remain the authority for axis-specific rules.

## Composition rules

- Apply an opponent overlay only when its public-state predicates are present.
- If more than one public axis is present, apply all non-conflicting timing/resource constraints;
  do not invent a new terminal classification merely because two overlays both recognize a threat.
- Once a spell, triggered ability, or activated ability is on the stack, source removal does not
  erase that object unless the rules or target legality actually make it fail.
- Costs already paid stay paid. A later counter does not refund Treasures, tapped permanents,
  discarded cards, graveyard fuel, or other resources.
- Preserve interaction colors and timing windows that the applicable overlay explicitly marks as
  live; do not reserve colors for hidden information.
- A fetched/tokenized/assembled engine piece is not automatically terminal. Continue through its
  actual triggers, activations, targets, and priority windows.
- Bounded enumerations are timing/policy qualification evidence, **not cEDH win rates**.

## Elasticity boundary

This consolidated Race policy is the high-gear policy used in the next same-hardware
Cruise/Sport/Race elasticity validation. Cruise and Sport remain their established lower-gear
pilot modes and do **not** inherit Race-only opponent overlays during that comparison.

A hardware promotion is not authorized by this document. The project remains KEEP_V07 unless
same-hardware elasticity or later deck-level evidence demonstrates that v0.7 cannot sustain the
intended gear separation.


## Formal acceptance

- Dedicated final-policy audit run **36031552516**: **SUCCESS**.
- Accepted source SHA: `9530ff2de311534e0f7f80137938aff20ed41cc4`.
- Same-source Manual qualification run **36031552871**: **SUCCESS**.
- The original failed audit was a provenance-fixture defect: it incorrectly treated the inherited
  v0.7 experiment SHA as a byte hash of the plaintext decklist, contrary to `PROVENANCE_IMPORT.md`.
- The corrected audit verifies the declared inherited identifier, the exact 100-card frozen list,
  all seven accepted overlays, zero card changes, and no win-rate claim.
- **Final Race Policy R1 is accepted.**
