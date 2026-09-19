# R1 Red-Access Challenger Audit

Run: 35451375360
Source: bd7994a9a257a9acc9365cd4ae7e30e289abd619
Seed: 0x1A22E7001
Samples: 100000
Result SHA256: c38c608d95983381742123ae5eb0b0d51b60ca369caaa1dcfc5b437b1063537f

Change vs control: -2 Island +2 Mountain only.

Key actionable deltas (R1 minus control):
T2 U -2.773 pp; R +9.506 pp; UU -8.548 pp; Guildmage +6.733 pp
T3 U -1.869 pp; R +8.932 pp; UU -6.305 pp; Guildmage +7.061 pp
T4 U -1.125 pp; R +7.612 pp; UU -5.385 pp; Guildmage +6.487 pp
T5 U -0.727 pp; R +6.101 pp; UU -4.147 pp; Guildmage +5.374 pp
T6 U -0.532 pp; R +4.800 pp; UU -3.147 pp; Guildmage +4.268 pp

Island count:
T6 control 2.74396; R1 2.49338; delta -0.25058.

Reversal:
T6 neutral control 14.346%; R1 14.330% (-0.016 pp)
T6 positive control 5.076%; R1 5.069% (-0.007 pp)
Effect is negligible in this model.

Interpretation:
R1 materially improves red and Guildmage actionable readiness at a modest cost to blue and a larger cost to UU/Island density. Because High Tide performance has not yet been modeled as an actual spell-sequence payoff, the Island/UU cost cannot yet be valued correctly.

Disposition:
R1_MANA_ACCESS_CHALLENGER_PASSES_PRIMARY_RED_GUILDMAGE_HYPOTHESIS
ADOPTION_HELD_FOR_HIGH_TIDE_UU_COST_EVALUATION

Next justified gate:
Test a midpoint R0.5 (-1 Island +1 Mountain; 19/7 basics) on the same seed, and/or add a High Tide payoff metric before choosing between control, midpoint, and R1.
