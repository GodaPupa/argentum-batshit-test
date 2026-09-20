# Face Value preboard Stage 1 gameplay audit disposition

Run: https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35523068822  
Evidence commit: `3fda43813ad98ee83d78b484beac54e9a770aa52`  
Workflow artifact SHA-256: `54cfcfb2443976799208e56c868f949c10dd89d025ece5ae9c9ef0b73c4c402a`  
Raw-log archive SHA-256: `8b3efb79c2d06199c1d71bad3c22be40d7d5ab22ffc7fa8d4b36e2351444e054`  
Summary SHA-256: `9e98d25a8b21f749e218eb012339b18402374b2869f8d5b1dabf1d626971c0da`

## Global disposition

`MIXED_BLOCK_DISPOSITION_NO_POOLED_RESULT`

All 256 assignments executed once. All 256 seeds are permanently consumed and may never be replayed, reassigned, rehabilitated, or used in another vector. The frozen control and opponent files remain preserved exactly as executed.

Seven matchup blocks are quarantined for material simulator or AI-execution defects. Their scores are descriptive rejected-run metadata only and are inadmissible for pooling, tuning, challenger design, promotion, or Tier 1 qualification.

Monster Tron is the only block accepted as Stage 1 simulator evidence. Acceptance means only that this 32-game preboard screen passed the declared automated and gameplay-quality audits; it does not establish Tier 1 coverage or authorize a challenger.

## Block dispositions

| Matchup | Recorded score | Disposition | Material basis |
|---|---:|---|---|
| Mono-Red Madness | 27-5 | `QUARANTINED_AI_CARD_COVERAGE` | Faithless Looting is tagged `AI:RemoveDeck:All` in pinned Forge and was never cast; Highway Robbery was never cast. The discard/draw engine was not represented faithfully. |
| Mono-Blue Terror | 26-6 | `QUARANTINED_INVALID_CARD_IDENTITY` | The downloaded list used unaccented `Lorien Revealed`; pinned Forge defines `Lórien Revealed`. It had zero log presence, so the representative 60 was not faithfully loaded/executed. |
| Grixis Affinity | 22-10 | `QUARANTINED_AI_CARD_COVERAGE` | Krark-Clan Shaman is tagged `AI:RemoveDeck:All` and was never cast; Reckoner's Bargain had zero log presence. Both are material to the matchup. |
| Jund Wildfire | 22-10 | `QUARANTINED_AI_CARD_COVERAGE` | Krark-Clan Shaman is tagged `AI:RemoveDeck:All` and was never cast. Its sweeper role is especially material against Face Value's token engine. |
| Elves | 20-12 | `QUARANTINED_AI_CARD_COVERAGE` | Quirion Ranger and Winding Way are tagged `AI:RemoveDeck:All`; Ranger had zero log presence and Winding Way was never cast. The core mana/card-flow engine was not represented. |
| Mono-Red Rally | 21-11 | `QUARANTINED_AI_SEQUENCING_DEFECT` | Outcome-independent sampled traces showed Bushwhacker cast before Rally at the Hornburg, excluding newly created tokens from the payoff. This materially understates the opponent's defining sequence. |
| Mono-Blue Faeries | 24-8 | `QUARANTINED_SLOW_MATCH_CONTRADICTORY_TERMINAL` | Assignment 256 hit the 120-second slow-match cutoff on turn 58, raised `InterruptedException`, and logged contradictory game-outcome bookkeeping. The entire block is quarantined. |
| Monster Tron | 20-12 | `ACCEPTED_STAGE1_SIMULATOR_SCREEN` | All 32 games completed without flags; every nonland maindeck card had observed execution; both seats finished 10-6; outcome-independent sampled traces showed functional mulligans, mana assembly, removal, threats, attacks, and blocks without a material defect. |

## Outcome-independent gameplay sample

Before trace inspection, two clean games per matchup/seat were selected by the lowest SHA-256 rank of `face-value-stage1-gameplay-audit-v1|<seed>`. For the accepted Monster Tron block the selected run indices were 87, 231, 184, and 8. Selection did not depend on winner or score.

The Monster Tron result is 20-12 overall, 10-6 with Face Value in seat 1 and 10-6 in seat 2. The two-sided 90% Wilson interval for the observed Face Value game-win proportion is approximately 48.0% to 75.1%. This is a screening estimate, not human tournament evidence.

## Required next gate

Do not rerun this vector. Before any replacement qualification block, add a fail-closed opponent capability audit that checks exact Unicode card identities, pinned-Forge `AI:RemoveDeck` annotations, and observed execution coverage for role-critical cards. A replacement block requires a new identity and fresh non-overlapping seeds only after its opponent pilot is demonstrably representative.

No quarantined result may be used to alter the permanent control or design a challenger.
