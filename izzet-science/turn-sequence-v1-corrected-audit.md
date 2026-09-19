# Corrected TURN_SEQUENCE_V1 Artifact Audit

Run ID: 35447657106
Artifact ID: 10586381086
Head SHA: dedcd26f3412ec6bba666cbd18a8e1c6ba85d265
Digest: sha256:5a82bb8d32c819ae92a92f46ffe17d142e35a139668a43db7b46f57125a46a70
Samples: 100000
Seed: 0x1A22E7001
Status: success

Opening buckets exactly reproduce the accepted baseline:
lands 0/1/2/3/4+: 3769/16392/29453/28753/21633
rocks 0/1/2/3+: 46126/39269/12500/2105

Corrected start-of-main telemetry:
T2 U 89.498%, R 10.203%, UU 0.000%, Guildmage start 0.000%
T3 U 94.428%, R 58.341%, UU 44.341%, Guildmage start 53.366%
T4 U 96.691%, R 69.124%, UU 77.405%, Guildmage start 66.179%
T5 U 97.860%, R 77.339%, UU 84.349%, Guildmage start 75.367%
T6 U 98.524%, R 82.931%, UU 88.645%, Guildmage start 81.550%

End-of-development residual telemetry:
T2 U 44.248%, R 23.675%, UU 15.602%, Guildmage end 17.914%
T3 U 78.732%, R 46.149%, UU 48.974%, Guildmage end 38.897%
T4 U 86.215%, R 59.935%, UU 63.887%, Guildmage end 53.566%
T5 U 92.022%, R 72.156%, UU 76.090%, Guildmage end 67.673%
T6 U 95.290%, R 80.075%, UU 84.197%, Guildmage end 77.453%

Reversal:
T4 neutral 1.251%, positive 0
T5 neutral 7.906%, positive 1.668%
T6 neutral 14.346%, positive 5.076%

Audit:
- Artifact digest matches GitHub metadata.
- Opening-hand baseline reproduces exactly.
- Same-seed replay preserved all previously expected residual and Reversal values while adding correctly named start-state telemetry.
- Start-state T1 is necessarily zero because telemetry is captured before the turn's land play; this is semantically correct but not a measure of T1 after-land color access.
- Guildmage start is likewise before the current turn land drop. Guildmage end is after development spending. Neither alone is 'maximum castability during turn'. A future actionable-readiness metric should measure after legal land play but before optional infrastructure spending.
- Reversal thresholds are based on end-of-development battlefield state and are internally consistent with the intended engine-development question, but still depend on the simplified development policy.

Disposition:
ARTIFACT_INTEGRITY_PASS
DETERMINISTIC_REPRODUCTION_PASS
CORRECTED_TELEMETRY_ACCEPTED_AS_STATE_METRICS
TURN_SEQUENCE_V1_ACCEPTED_WITH_SCOPE_LIMITATION
NEXT_GATE_ACTIONABLE_READINESS_OR_CONTROLLED_CHALLENGER
