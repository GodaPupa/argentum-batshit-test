# TURN_SEQUENCE_V1 Artifact Audit

Run ID: 35445879766
Artifact ID: 10584424123
Artifact: izzet-science-turn-sequence-v1
Head SHA: 704ea44f66d9e246e37a1aa70601bde73060c67b
Artifact digest: sha256:c40cc21b165de1bcfb2e20dd8e6eb0bb513f3e8e608579afa196aba5b94487c1
Samples: 100000
Seed: 0x1A22E7001
Workflow status: success

Raw opening buckets reproduced:
lands 0/1/2/3/4+: 3769 / 16392 / 29453 / 28753 / 21633
rocks 0/1/2/3+: 46126 / 39269 / 12500 / 2105
These exactly match the previously accepted opening baseline.

Turn metrics:
T1 U .86206 R .08267 UU 0; GM pre 0 post 0; Rev neutral 0 positive 0; avg lands .97405; avg Islands .86592
T2 U .44248 R .23675 UU .15602; GM pre 0 post .17914; Rev 0/0; lands 1.87842; Islands 1.23465
T3 U .78732 R .46149 UU .48974; GM pre .53366 post .38897; Rev 0/0; lands 2.68503; Islands 1.78576
T4 U .86215 R .59935 UU .63887; GM pre .66179 post .53566; Rev neutral .01251 positive 0; lands 3.38128; Islands 2.18195
T5 U .92022 R .72156 UU .76090; GM pre .75367 post .67673; Rev neutral .07906 positive .01668; lands 3.97752; Islands 2.48693
T6 U .95290 R .80075 UU .84197; GM pre .81550 post .77453; Rev neutral .14346 positive .05076; lands 4.49309; Islands 2.74396

Audit finding:
The opening-hand reproduction is exact and the artifact digest matches GitHub metadata. However, T2 pre-development Guildmage readiness is 0 while post-development readiness is 17.914%. This is not intrinsically impossible because development can add a second colored source, but it demonstrates that 'pre' is measured before the turn's land play and 'post' after land/rock development. Therefore the labels are not directly comparable as alternative same-turn choices. They represent start-of-main-state vs end-of-development-state.

More importantly, U availability drops from 86.206% on T1 to 44.248% on T2. This is caused by the policy spending/tapping mana during development before color_flags() is recorded. Thus U/R/UU metrics are residual untapped mana after development, not whether colors were available during the turn. They must not be interpreted as colored-source availability.

Disposition:
ARTIFACT_INTEGRITY_PASS
OPENING_REPRODUCTION_PASS
TURN_SEQUENCE_METRIC_SEMANTICS_REJECTED_FOR_COLOR_AVAILABILITY
REVERSAL_THRESHOLDS_PROVISIONAL_PENDING_STATE_AUDIT

No deck tuning authorized from the rejected/mislabeled color metrics.
Next gate: record pre-development source availability and post-development residual mana as separate named metrics, then same-seed replay.
