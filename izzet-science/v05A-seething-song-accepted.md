# v0.5-A Seething Song — Accepted Promotion Audit

Run: 35522120849
Artifact: 10608312284
Artifact ZIP SHA256: 078886bcf49d2ac4008b4966ebdbfaed840dcc552824d3caccb1ebd3b5b1b6c2
Control output SHA256: b7d7b79c250b2098d925eddc0e93559ac1058e76273c3bdce5586d72af656c21
Challenger output SHA256: 9b1c0c8140b0f2582eb9b91d459e2593fd00c2810e8e53994cecad53f9aa6bb5
Source: 849c3d00780a736cd9ce8ac69fff0e69f3181d71
Samples: 100000 per deck
Seed: 0x1A22E7001
Horizon: T1-T10

Change:
-1 Hidden Strings
+1 Seething Song

Integrity:
- Workflow and repository CI: green
- Exact v0.4 control reran to the previously accepted output hash
- Artifact present and non-empty
- Reported artifact digest matches downloaded ZIP SHA256
- One slot changed; mana base, tutors, selection, interaction, and commander unchanged
- No reroll, replacement seed, or post-outcome protocol change

Cumulative deterministic lethal:
- T3: 0.000% -> 0.017% (+0.017 pp)
- T4: 0.018% -> 0.085% (+0.067 pp)
- T5: 0.380% -> 0.560% (+0.180 pp)
- T6: 1.293% -> 1.572% (+0.279 pp)
- T7: 2.552% -> 2.893% (+0.341 pp)
- T8: 3.855% -> 4.246% (+0.391 pp)
- T9: 5.252% -> 5.697% (+0.445 pp)
- T10: 6.825% -> 7.288% (+0.463 pp; +6.78% relative)

T10 assembly and conversion:
- Spike + Ritual pair: 10.362% -> 10.362%
- Pair + commander ready: 9.638% -> 9.638%
- Lethal state: 6.816% -> 7.279% (+0.463 pp)
- Cumulative lethal: 6.825% -> 7.288% (+0.463 pp)
- Cumulative lethal / pair+commander-ready: 70.81% -> 75.62%

Guardrails:
- Commander deployment: unchanged
- U, R, and UU execution: unchanged at every reported turn
- High Tide castability/productivity: unchanged
- Dramatic Reversal neutral/positive thresholds: unchanged
- Land and Island development: unchanged

Interpretation:
Seething Song improves only launch conversion in this paired model; it receives no
credit for ordinary acceleration. The gain is therefore not caused by a mana-base,
selection, or development trade. It also creates a legal T3 deterministic line while
retaining standalone ritual utility. The +0.463 percentage-point T10 gain is larger
than the prior Strategic Planning promotion and clears the v0.5 launch-efficiency gate.

Disposition:
V05A_PASS
SEETHING_SONG_PROMOTED
V05_CONTROL_ACCEPTED
HIDDEN_STRINGS_REMOVED
