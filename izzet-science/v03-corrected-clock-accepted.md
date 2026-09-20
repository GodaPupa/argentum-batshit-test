# v0.3 Corrected Cumulative Goldfish Clock — Accepted Audit

Run: 35518608059
Artifact: 10607428257
Artifact SHA256: c60a2da4ae6cf85f5770f16ab296b700c6bfb912724bf2cd680cf249458373a1
Source: b90170a1a6490c1d5a23b399522d87f18e56df50
Samples: 100000
Seed: 0x1A22E7001
Horizon: T1-T10

Integrity: PASS
Corrected tutor policy: PASS
First-lethal absorption: PASS

Accepted cumulative deterministic lethal opportunity:
T1 0
T2 0
T3 0
T4 0
T5 0.130%
T6 0.856%
T7 2.034%
T8 3.325%
T9 4.694%
T10 6.180%

First-lethal increments:
T5 0.130%
T6 0.726%
T7 1.178%
T8 1.291%
T9 1.369%
T10 1.486%

Assembly:
T10 Spike+Ritual pair 10.242%
T10 pair + commander battlefield 9.520%
T10 lethal state 6.170%
T10 cumulative lethal 6.180%

Commander battlefield:
T2 39.996%
T3 54.995%
T4 67.411%
T5 77.700%
T6 84.056%
T10 94.670%

Tutor activity per turn:
T3 8.338%, T4 4.996%, T5 3.507%, T6 2.870%, T7 2.582%, T8 2.352%, T9 2.296%, T10 2.103%.
In this gate every tutor use is a direct primary-combo-component find.

Interpretation:
Earliest modeled deterministic lethal is T5.
By T10, primary pair acquisition (10.242%) is still the largest upstream limiter. Commander deployment is largely solved. Among pair+commander-ready states (9.520%), only 6.170% are launch-mana feasible on T10, so launch resources remain a second meaningful bottleneck.
The corrected tutor policy slightly improves the accepted T10 cumulative result over the rejected/provisional 6.074%, to 6.180%.

Scope:
This is an undisrupted solitaire opportunity curve, not a multiplayer win rate. It does not model opponent interaction, commander removal/tax, politics, or the need to kill multiple opponents. It also does not yet model all tutor/value lines beyond direct primary-pair completion.

Disposition:
V03_CORRECTED_CLOCK_ACCEPTED
EARLIEST_LETHAL_T5
LETHAL_BY_T10_6_180_PERCENT
NEXT_GATE_PRIMARY_PAIR_ACQUISITION_AND_LAUNCH_EFFICIENCY
