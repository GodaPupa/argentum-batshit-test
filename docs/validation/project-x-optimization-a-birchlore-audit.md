# Project X Optimization Experiment A — Birchlore and Noble audit

## Scope and disposition

This is an existing-artifact-only audit of Experiment A's Birchlore Rangers mana-event difference and Falkenrath Noble availability. No game was replayed, no seed was generated, and no deck, agent, engine, Gym, telemetry, or workflow behavior was changed.

Experiment A remains formally preserved as promising but not promoted. Replication is blocked pending a decision on the demonstrated general mana-activation policy defect.

## Birchlore event localization

The raw report records 45 control Birchlore activation events and 13 Variant A events. The entire net difference of 32 events is localized as follows:

| Pair | Control | Variant A | Delta | Relevant state |
|---:|---:|---:|---:|---|
| 3 | 19 | 0 | -19 | Control opened Noble; variant did not. Variant cast Llanowar T2. |
| 14 | 4 | 4 | 0 | Same Birchlore pattern. |
| 20 | 13 | 0 | -13 | Control opened Noble; variant did not. Neither treatment cast Llanowar. |
| 21 | 6 | 6 | 0 | Same Birchlore pattern. |
| 23 | 3 | 2 | -1 | Small sequencing divergence. |
| 27 | 0 | 1 | +1 | Variant cast Llanowar T3 and used Birchlore once on T5. |

Pairs 3 and 20 alone account for all 32 net events. Pair 20 contains no Llanowar cast or activation, so Llanowar replacing Birchlore mana demand cannot explain the aggregate gap.

## Battlefield availability and legal opportunity

The large gap is not explained by reduced Birchlore battlefield availability:

- Pair 3 control cast Birchlore on T2 and T6; Variant A cast it on T4 and T6. Control cast Nettle on T4; variant cast it on T3. Both developed ample Elves.
- Pair 20 cast Birchlore on T3 and T4 in both treatments. Control recorded 12 `BIRCHLORE_MANA_AVAILABLE` constraint observations, while variant recorded 23, yet variant activated Birchlore zero times.
- Across the complete block, control recorded 121 Birchlore-available constraint observations and variant recorded 146. Variant therefore had at least as much reported legal availability despite far fewer activations.

The difference is not evidence of fewer legally useful Birchlore opportunities in Variant A.

## Sequencing and policy diagnosis

`ProjectXSolitaireAgent.priority` gives Birchlore a fixed priority of 7,000 whenever `needsBlackMana` is true. `needsBlackMana` becomes true whenever Carrion Feeder or Falkenrath Noble is in hand and no black mana is currently floating. This path bypasses the simulator-backed reusable-mana check that otherwise requires an activation to unlock an executable relevant cast.

Consequently, the control repeatedly tapped Elf pairs for black merely because Noble remained in hand:

- Pair 3 activated Birchlore on T2–T8. No black spell was cast until Noble on T8. At most the T8 activations funded that spell; the preceding activations were not productive cast funding.
- Pair 20 activated Birchlore on T3–T10. The only black spell cast during those turns was Carrion Feeder on T9; most recorded events did not fund a spell. Noble remained uncast when the game ended on T11.

The report field named `birchloreManaContribution` records every successful activation, not mana consumption or a spell demonstrably funded by that activation. It therefore overstates meaningful mana contribution and is sensitive to this policy behavior.

### Classification

- Llanowar replacing Birchlore demand: minor/local at most; disproven as the aggregate explanation by pair 20.
- Different Nettle/Birchlore battlefield availability: not the primary cause.
- Fewer legal Birchlore opportunities: not supported; the variant had more availability observations.
- Agent sequencing: primary cause. The black-mana shortcut activates without checking whether the mana unlocks an executable cast or immediate deterministic line.
- Telemetry: secondary semantic issue. Activation count is labeled contribution without tracking whether the mana was spent productively.

This is a clear general reusable-mana sequencing defect within the Project X solitaire policy, not a card-rules or engine defect.

## Noble availability audit

Noble availability fell from 15 control games to 7 variant games, consistent with the deliberate two-copy-to-one-copy reduction:

- Every one of the seven variant Noble-available pairs also had Noble available in control.
- Eight pairs were control-only for Noble availability; zero were variant-only.
- Control cast Noble in 5/15 availability games (33.3%); Variant A cast it in 2/7 (28.6%). The similar conversion rate does not indicate a separate sequencing suppression.
- In the shared pair 16, both treatments drew Noble on T3 and cast it on T4.
- In pair 15, Variant A correctly retained and cast Noble on T4, then converted the T6 primary loop into deterministic drain.
- No observed control engine with an available Noble kill was lost in its paired variant.

The lower Noble availability is attributable to the intentional copy reduction, not an unrelated discovery or deployment bug.

## Required gate before replication

Replication should not proceed unchanged because pooling a corrected policy run with Experiment A would mix two decision policies, while repeating the demonstrated defect would preserve a misleading Birchlore metric and potentially distort fair-board sequencing.

A clean follow-up requires an approved general correction that:

1. gives a reusable mana activation elevated priority only when the post-activation state unlocks an executor-valid relevant cast or an immediate validated combo line;
2. preserves explicit Birchlore color planning and Nettle/Quirion sequencing;
3. separates raw Birchlore activations from spells actually funded and mana deliberately floated for a documented line;
4. adds deterministic regressions for a stranded Noble that cannot yet be cast and for a Birchlore activation that genuinely enables a black spell.

After that correction is validated, Experiment A can remain promising exploratory evidence, but the replication must be treated as a new-policy paired experiment rather than pooled directly with the original policy block.
