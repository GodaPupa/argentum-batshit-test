# Manual Transmission — CRUISE R2 original-game forensics (2026-10-21)

**Disposition:** `FORENSICS_ONLY; NO_NEW_GAMES; NO_POLICY_OR_HARDWARE_PROMOTION`.

## Immutable sample and limits

- The original R1/R6 Precision pilots produced ten complete, four-seat games, five fixed seeds matched between frozen CONTROL v0.7 and seven-land Challenger A.
- Locally recomputed against all ten exact original ZIP SHA-256 digests: **33,440 accepted actions**, **zero rejected actions**, **7,827 priority decisions reconciled to actual submitted actions**.
- Across 40 seat-games, every seat had at least one AI-scored Animar cast option. Animar was actually cast in 35 seat-games; median first scored offer was personal turn 4, median first actual cast among casters was personal turn 7. Among those who cast Animar, 31 delayed at least one personal turn and median recorded delay was two turns.
- A scored option is **not** automatically a confirmed affordable command-zone opportunity or a strategic error. The original games used seven-card keep hands, **skipped mulligans**, and provide no independent opponent-response counterfactual.
- This sample, with dependent seats and already-examined traces, is **not** sufficient to conclude Challenger A has a competitive win-rate advantage.
- Official gameplay cap remains **10/12 used, 2 reserved**, and this research has run **zero** new games. No production, source, deck, or control promotions.

## Resolved: 249 scored Animar options vs. 233 command-zone legal offers

The historical AI-scoring stream and on-turn legal-offer metric differ **by definition**. Exact, read-only zone attribution over every original `ZoneChangeEvent` showed:

| Scored Cast Animar option source | Count |
|---|---:|
| Animar in command zone | **233** |
| Animar in player's hand | **16** |
| Total scored | **249** |

Every one of the ten original arms matches its recorded **command-zone** legal-offer count after removing the hand-origin scored options.

The only hand-origin observations are seed `202610202141`, CONTROL, Seat 3 (`manual-mirror-3`, commander entity `e300`):
1. **Action 751**: the commander was actually cast from COMMAND, and resolved onto BATTLEFIELD at 755.
2. **Action 2956**: Animar was returned from BATTLEFIELD to HAND. The original game requested whether to move it to COMMAND instead.
3. **Action 2957**: the actual `YesNoResponse` was **false** (leave in HAND).
4. Later personal-turn scored options offered casting Animar from HAND, accounting for **16** of the 19 Seat 3 scored choices. The original COMMAND-only counter recorded the other **three**, all before the bounce.

**Conclusion:** no proven phantom Animar action from this 249/233 discrepancy. The original counters measured different zones. This does **not** independently prove every hand-offer was strategically optimal or revalidate every possible mana payment; it reconciles the original zone-specific counts.

## Current recommendation

**HOLD any generic early-Animar score boost or Challenger A promotion.** First perform a bounded, predeclared, archived-state legality and opportunity-quality audit on identified high-deferment checkpoints, using the original R5 comparator, preserved opponent priority (not auto-passed in the proof), actual mana/source constraints and the exact accepted action indices. Compare only one independently attributable candidate policy variable, retaining Pact/Ballista/Statue/Encore/payment protections.

Do not count simulator quiet-state opponent autopasses as interactive victories. Do not change `main`, `AiProfile.CURRENT`, CONTROL v0.7, Challenger A, or accepted R5. Do not expend the two reserved games without a distinct authorized full-game gate.

The source-pinned ten-archive manifest, source-only zone verifier and GREEN/RED CI artifact constitute the durable R2 audit. This is a **retrospective observation**, not a blinded heldout or competitive qualification.
