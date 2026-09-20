# Pest Control V2 official block — post-execution acceptance audit

## Disposition

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10`
- Frozen-vector SHA-256: `c88d45352f531a08a484004f35ffbcc44ef08331f2c7844e5c11449e02c2d104`
- Pinned harness: `cafa38d496479c863480a4f5db019d1ec58d9cbb`
- Official run: `35525262024`, attempt 1, success
- Official artifact: `10610310479`
- Artifact archive SHA-256: `577ecf33bbbd6fa2b9967867228b6eabb81b6b2c5590f2c0670f5d3536f80a2b`
- Audit input SHA-256: `5f8326c012c7b62184686b7269d45fe9f80b23124d50543aa7e417ca22cd0254`
- Final disposition: `ACCEPTED_CALIBRATION`

All ten V2 seeds were consumed exactly once in frozen order and are permanently unavailable for replay, reassignment, replacement, or inclusion in another vector. This acceptance does not rehabilitate or pool the rejected Block A evidence.

## Reconciliation

Independent readback verified all ten raw hashes, the block-summary hash, and every deterministic gzip against its raw JSON. The trace audit then reconciled:

- 10/10 expected, attempted, recorded, and terminal games;
- all 3,160 priority actions contiguous, accepted, and free of fallback or rejection;
- zero protocol defects, wedges, action guards, turn guards, or timeouts;
- unchanged frozen deck, opponent, protocol, sideboard, seat, play/draw, and seed identities;
- 24 permanent-targeting spells: 23 opposing targets, all removed as selected, and one reviewed terminal self-target;
- all four Fireblasts paid exactly two Mountains and the one flashed-back Lava Dart paid exactly one Mountain; every sacrifice persisted in the graveyard.

The descriptive result is Pest Control 6-4 Mono Red Madness. Pest was 2-3 when starting and 4-1 when drawing. These ten independent preboard games validate the V2 calibration runner; they are not a precise matchup estimate or Tier-1 qualification sample.

## Reviewed game-8 terminal target

Game 8 action 217 flashed back Lava Dart targeting Mono Red's own Kessig Flamebreather. This is visually dominated by targeting Pest directly, so it was isolated for full stack review rather than silently accepted.

Immediately before the action, Pest was at 6 life with an empty hand. Mono Red already had Fireblast and its first Flamebreather trigger on the stack. Flashing back Lava Dart supplied the additional Flamebreather trigger needed to make the line exactly lethal: the two triggers reduced Pest from 6 to 4, then Fireblast dealt 4. Lava Dart's one damage to the 1/3 Flamebreather neither destroyed it nor changed the terminal; targeting Pest would have produced the same winner with excess damage.

The action therefore created a legitimate, unanswerable lethal relative to passing and is not the rejected Block A pass-equivalent terminal-priority defect. The target is strategically inelegant but outcome-equivalent inside an already committed exact-lethal stack. No nonterminal friendly-burn target occurred anywhere in the block.

## Acceptance boundary

This block is accepted only as V2 calibration evidence. It establishes that the corrected runner and production-candidate pilots can execute the frozen preboard protocol without an outcome-relevant defect in this block. The next justified gate is a separately frozen, fresh, larger matchup-qualification block with predeclared acceptance criteria. No V2 official seed may be reused for that gate.
