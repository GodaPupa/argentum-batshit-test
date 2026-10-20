# ARGENTUM — R12 GameEndedEvent reason forensic closeout (October 19, 2026)

## Disposition: NO_DEFECT

**Read-only source/trace reconciliation complete. No code correction, gameplay rerun, or policy/deck change is justified.** The apparent inconsistency in the two R11 seed-`202610191613` games is due to the existing, documented semantics of an *aggregate game-end event reason*, not a misapplication of the empty-library loss rule.

### Exact historical provenance

- Frozen R11 game execution commit: `79d3c0609eeb09931080bd4caffad702b703b7c0`.
- R11 four-game completed/success workflow: [39357888559](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39357888559).
- R11 source-bound report head: `a1cafbe2d3bb3ad33db2803a3f7b1257fe52d29e`, `gameplay/manual-r11/HELDOUT-FINDINGS-20261019.md`.
- Original R11 seed-1613 R5 artifact `12236591581`, ZIP SHA-256 `76eabaf1dd02ddcc9ec31738a42d7403acf9db6f655bfa21ea4746fff1dbe63f`.
- Original R11 seed-1613 R7 artifact `12236521360`, ZIP SHA-256 `acdf66ada6e187cb7203078e646a2ebaf18f59944d389a4c420f3306487fb98f`.
- Rules-engine source `rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/sba/game/GameEndCheck.kt` blob **`dd98057c7051f6ec5d2d6f7cd6790532a1f0b2a5`**, identical in the pinned R11 code and current `main`. The relevant implementation and explanatory comment are at lines 47–66.

### Reported observed sequence

According to the preserved R11 action and final-state forensics, Seats 1 and 2 had already lost with `PlayerLostComponent.reason=LIFE_ZERO`. Later, Seat 4 (`manual-mirror-3`) failed to draw from an empty library; the engine emitted `DrawFailedEvent` and the correct per-player `PlayerLostComponent.reason=EMPTY_LIBRARY`. Its elimination left Seat 3 (`manual-mirror-2`) the winner.

The aggregate terminal `GameEndedEvent(winnerId=manual-mirror-2, reason=LIFE_ZERO)` did **not** claim that the last player lost from damage. It reported the **first meaningful loss cause in `turnOrder`**:

```kotlin
val lostReasons = state.turnOrder
    .mapNotNull { state.getEntity(it)?.get<PlayerLostComponent>()?.reason }
val reason = lostReasons.firstOrNull { it != LossReason.TEAM_DEFEATED }
    ?: lostReasons.firstOrNull()
```

The source comment states this behavior explicitly: the game-end reason is drawn from a defeated player, preferring a meaningful cause over propagated `TEAM_DEFEATED`. Since `LIFE_ZERO` was already associated with an earlier seat, `losingReason` selected it before reaching Seat 4's `EMPTY_LIBRARY`. The enum mapping itself is correct:

```kotlin
LossReason.LIFE_ZERO -> GameEndReason.LIFE_ZERO
LossReason.EMPTY_LIBRARY -> GameEndReason.DECK_EMPTY
```

### Exact meaning and limitations

- **Authoritative per-player reason:** `PlayerLostComponent.reason` correctly identifies the empty-library loss. Use it for exact elimination-cause analysis.
- **Aggregate terminal event:** `GameEndedEvent.reason` selects a meaningful defeated-player reason in turn order; it is **not** defined as the last or causally decisive elimination.
- **Gameplay validity:** Win conditions and empty-library loss handling were correct. R11's terminal winner, life totals, Ballista analysis, and `R7_INCONCLUSIVE` competitive verdict remain authentic and unchanged.
- **Future enrichment:** Recording the last decisive loss, a causal elimination identifier, or monotonic loss sequence could improve terminal telemetry, but would be an **optional, separately scoped design change**, not a proven rules-engine bug.

### Scope and next step

No code patch was applied; **no new Kotlin tests or workflow were dispatched** because no behavioral defect was demonstrated. Existing R11 source, original artifacts and conclusions are unmodified. Frozen Manual Transmission v0.7, `AiProfile.CURRENT`, `main`, R7 opt-in policy, Wan v0.8 independent challenger and official C4/D2 gates remain unchanged.

**R12 status: `NO_DEFECT`; forensic assignment closed. No next action is required within R12.** Any subsequent gameplay performance investigation must be independently justified and authorized; do not automatically launch games or merge policies.
