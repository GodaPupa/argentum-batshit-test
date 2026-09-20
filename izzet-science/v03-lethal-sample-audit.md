# v0.3 Focused Lethal Sample — Audit

Run: 35510127272
Artifact: 10605470552
Artifact SHA256: 33b712243391c9c9542f564e200e344a9d5a443848946cd45cdbf91f0f799cbf
Samples: 100000
Seed: 0x1A22E7001
Horizon: T1-T10

Execution and artifact integrity: PASS.

Per-turn lethal opportunity (state on that turn, not cumulative first-kill):
T1 0
T2 0
T3 0
T4 0
T5 0.085%
T6 0.375%
T7 0.727%
T8 1.146%
T9 1.610%
T10 2.109%

Assembly context:
T5 pair 1.733%; pair+commander battlefield 1.149%; lethal 0.085%.
T6 pair 2.088%; commander-ready 1.581%; lethal 0.375%.
T8 pair 2.844%; commander-ready 2.468%; lethal 1.146%.
T10 pair 3.712%; commander-ready 3.414%; lethal 2.109%.

Commander battlefield:
T2 39.882%
T3 55.889%
T4 69.066%
T5 78.131%
T6 84.125%
T10 94.436%.

Interpretation:
Earliest observed modeled lethal opportunity is T5. The primary bottleneck is not commander deployment by midgame; it is finding/retaining the singleton Spike+Ritual pair and then reaching the six-mana launch threshold with sufficient red. By T10, 3.414% have pair+commander ready but only 2.109% satisfy launch mana, showing a remaining launch-resource bottleneck among assembled states.

Important scope:
combo_lethal is a per-turn opportunity frequency, not a cumulative first-lethal distribution. A trajectory that was lethal on an earlier turn may be counted again later if its state still satisfies the check, and the current pilot does not actually execute/remove the combo. Therefore do not call T10 2.109% 'killed by T10' and do not derive median kill turn from this sample.

Next justified gate:
Add first-lethal absorption telemetry (first turn a trajectory becomes lethal, then record it once) and tutor/transmute resolution for Muddle the Mixture / Merchant Scroll / Dizzy Spell where legally applicable. The current raw primary-pair find rate is low enough that tutor functionality is likely the highest-value missing model feature before judging the deck's true clock.

Disposition:
V03_LETHAL_STATE_SAMPLE_ACCEPTED
EARLIEST_MODELED_LETHAL_T5
CUMULATIVE_KILL_CURVE_NOT_YET_AUTHORIZED
