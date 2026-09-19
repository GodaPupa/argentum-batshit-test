# Pest Control matchup Block A — Gate 7 rejection

## Disposition

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_BLOCK_A`
- Freeze commit: `c009c4b05a16d13eeabc34db60edcb6af58f040a`
- Execution commit: `13c4680ddc952694c5d4738940b4abbe797563ab`
- Preservation commit: `3b42f71ea0403f071a970a17602e39d445483a5b`
- Final disposition: `REJECTED_OUTCOME_RELEVANT_TARGET_SELECTION_DEFECTS`

Block A is formally rejected in full. All 50 seeds are permanently retired and may never be
replayed, reused, rehabilitated, paired, reassigned, or included in another vector. The observed
35–15 result and every derived performance metric are inadmissible as accepted evidence. They may
be cited only as descriptive output from this rejected diagnostic block and must never be pooled
with an accepted sample or used to tune Pest Control.

The preserved artifacts, traces, hashes, and provenance remain authoritative historical evidence.
This rejection does not alter or regenerate any preserved execution artifact.

## Outcome-relevant defects

The Gate 7 trace audit found repeated destructive target/resource decisions:

| Game / sequence | Defect |
|---|---|
| 3 / 331 | Pest cast Cast Down on its own Fierce Witchstalker. |
| 23 / 444 | Pest cast Cast Down on its own Essence Warden. |
| 25 / 385 | Pest cast Cast Down on its own Blood Researcher. |
| 47 / 481 | Pest cast Cast Down on its own Fierce Witchstalker. |
| 42 / 499–500 | Mono Red flashbacked two Lava Darts into its own Kessig Flamebreather, sacrificing two Mountains. |
| 8 / 374; 49 / 437 | Mono Red spent Lava Dart and a Mountain on five-toughness Generous Ents without removing them. |

The four self-Cast Down actions resolved and destroyed Pest permanents. Some occurred in positions
whose recorded winner was already determined, but the shared selection defect is capable of
changing outcomes. The Lava Dart cases independently corroborate faulty target/resource valuation.

## Seedless diagnosis

The shared root cause is a strategist terminal-priority error: any simulated action leaf that ends
in a win is labeled action-created immediate lethal even when the pass baseline reaches the same
win. That terminal override bypasses the ordinary friendly-removal and land-sacrifice safeguards.
The authorized correction requires an action to create the winning terminal relative to passing;
an already-winning pass baseline is not evidence that the action is lethal.

Diagnosis and regression work is seedless. No retired seed was executed, no replacement seed or
Block B vector was generated, and every gameplay runner remains disabled.
