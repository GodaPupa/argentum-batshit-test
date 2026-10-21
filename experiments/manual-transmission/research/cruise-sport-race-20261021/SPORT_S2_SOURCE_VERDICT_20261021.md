# Manual Transmission — SPORT S2 delayed trigger metadata qualification

**Disposition:** `S2_METADATA_SOURCE_QUALIFIED` · **NO MAIN MERGE** · **NO FULL-GAME RESULTS**.

Date: 2026-10-21. Scope: a **single metadata-expression change**, not a Pact payment, mana, timing, card-definition, AI policy, or deck change.

## Exact RED before GREEN

The preregistered four-test suite ran on the unchanged original `TriggerDetector.kt` (blob `4dab5a577dac7b93b49265f3582b60a0517ca187`), [GitHub Actions baseline 39673551868](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39673551868), artifact **12380349278**, SHA-256 `7dea8354dcab37d36563d878467d29b35f11e2ea7e211db41d1be6e5bd4d398a`. JUnit **3 PASS, 1 FAIL**: the actual UPKEEP metadata assertion failed; genuine END, wrong active player, and not-before-next-turn controls passed. This RED is an intended proof that the old descriptive field was wrong, *not* evidence of gameplay failure.

A one-line source patch was committed as `3ade1e75aef9b1fe9fa38ac20a2c154370400c61`:

```diff
- trigger = EventPattern.StepEvent(Step.END, Player.Each),
+ trigger = EventPattern.StepEvent(step, Player.Each),
```

This patch changes only the synthesized event metadata in `rules-engine/src/main/kotlin/com/wingedsheep/engine/event/TriggerDetector.kt`. The delayed-trigger matching checks already used the actual `delayed.fireAtStep == step`, the specified active player and the deferred turn restriction.

The independent [patched GitHub Actions run 39674502310](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39674502310) finished **SUCCESS**, artifact **12381771227** (`manual-sport-s2-delayed-metadata-39674502310`), SHA-256 **`3ec53a3c5eadbb09266b360db9c973d4cf3c2141fbbbed9b13b1c74e4bbea9c5`**.

Actual JUnit: **4/4 S2 metadata checks PASS**, plus **8/8 original R9 fixtures PASS** and **68/68 protected R5/Animar/Pact-debt/Ballista/Statue/Encore/payment/mulligan tests PASS**. **80/80 total, zero failures/errors/skips.** The preserved raw `r9-r5-protected.log` confirms that Pact debt tests include an actual five-mana payment during upkeep.

## Historical game observation

[SPORT S1 original-game inventory 39672563388](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39672563388) verified all ten archived full-game ZIP hashes, **33,440 accepted actions**, and **13/13 observed Pact debts paid at the appropriate first following caster upkeep**. All 13 original `AbilityTriggeredEvent` descriptions incorrectly displayed “End Step.” No unpaid-debt defect was demonstrated by those paid samples. Do not interpret their dependency as competitive matchup evidence or retroactively modify those original event logs.

## Qualification limits and next gate

- **No new games**: Precision games used **10/12**, **2 reserved**.
- `main`, CONTROL v0.7, seven-land Challenger A, original accepted R5, `AiProfile.CURRENT`, original qualified R9 and `ManaSolver` remain untouched.
- This correction is source-level qualified on an isolated S2 branch **only**; nothing has merged or been promoted. No new competitive benefit is claimed.
- The explicitly card-scoped `PactOfNegationScenarioTest` (counter plus paid and unpayable upkeep) and `PactOfNegationUnpaidScenarioTest` (declined payable debt loses) are **not covered by this S2 Actions job**. Execute those four exact card mechanic scenarios on the patched source as an integration prerequisite before any upstream merge. Do not equate the 68 AI-level regression checks with all card-mechanics coverage.
- After independent Pact mechanics integration, move to a separately isolated **SPORT S3 original opponent-priority/threat-window audit** before touching any Pact policy. Retain R9 as opt-in; retain `KEEP_V07` for the deck.

**Conclusion:** A formerly misleading UPKEEP trigger description was corrected with one source line; the validated original actual-payment timing was already correct. Retain this as an unmerged research repair.
