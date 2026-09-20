# Selection Goldfish #51 — C1 vs v0.2 Audit

Run: 35507012686
Artifact: 10604242309
Artifact SHA256: 685c4b86aef47ae2af74e26852c4c67b13652c74c8091e4b87992d38acc5824c
Seed: 0x1A22E7001
Samples: 100000 per configuration

Correct baseline for C1 comparison is R0.5-midpoint.txt, which is the 19 Island / 7 Mountain v0.2 mana identity.

C1 vs v0.2 (R0.5) key results:
T3 selection cast 7.555% vs 6.862%; avg selection seen .08661 vs .07077; drawn .11177 vs .10469.
T3 Guildmage actionable 62.033% vs 61.989% (+0.044 pp).
T3 UU executable 19.053% vs 18.682% (+0.371 pp).
T3 U executable 83.238% vs 82.881% (+0.357 pp).
T3 R executable 41.504% vs 41.459% (+0.045 pp).

T4 Guildmage actionable 73.495% vs 73.418% (+0.077 pp).
T4 UU executable 23.969% vs 23.691% (+0.278 pp).

T6 Guildmage actionable 86.369% vs 86.221% (+0.148 pp).
T6 UU executable 31.868% vs 31.478% (+0.390 pp).
T6 U executable 92.552% vs 92.407% (+0.145 pp).
T6 R executable 64.147% vs 64.026% (+0.121 pp).
T6 average lands 4.62210 vs 4.61842.
T6 average Islands 2.72523 vs 2.72248.

C1 Reversal cost:
T6 neutral 9.674% vs 14.302% (-4.628 pp).
T6 positive 2.732% vs 4.991% (-2.259 pp).

Interpretation:
With selection actually resolving, C1 does not regress ordinary development. It slightly improves selection volume and several executable-action metrics despite removing the two four-mana rocks. The cost is concentrated in Dramatic Reversal threshold frequency.

Lose Focus opponent-facing utility remains conservatively unvalued, so C1 receives essentially no simulated credit for that replacement beyond hand classification.

Decision:
C1 passes structural and selection-enabled goldfish gates. Given the design rule against distorting ordinary games for a secondary combo, promote C1 to the next construction baseline while preserving v0.2 as historical control. Dramatic Reversal remains in the deck as an opportunistic engine, but Sisay's Ring and Ur-Golem's Eye no longer receive protected slots solely to maximize it.

Disposition:
SELECTION_GOLDFISH_ACCEPTED
C1_PROMOTION_JUSTIFIED
