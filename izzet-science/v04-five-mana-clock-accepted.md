# v0.4 Corrected Five-Mana Clock — Accepted Audit

Run: 35521517813
Artifact: 10608601992
Artifact ZIP SHA256: 6e5cbbb26476566e05d4e153c74407c47330273dd42580937f01e5592e716b30
Control output SHA256: b7d7b79c250b2098d925eddc0e93559ac1058e76273c3bdce5586d72af656c21
Source: 8be6fb7d94418352fe84248e6894b9c8c08b57a2
Samples: 100000
Seed: 0x1A22E7001
Horizon: T1-T10

Integrity:
- Workflow: green
- Artifact present and non-empty
- Reported artifact digest matches downloaded ZIP SHA256
- v0.4 identity unchanged
- Corrected five-mana Ritual route regressions passed
- Izzet Signet and Izzet Boilerworks launch-payment regressions passed

Accepted cumulative deterministic lethal opportunity:
- T3 0.000%
- T4 0.018%
- T5 0.380%
- T6 1.293%
- T7 2.552%
- T8 3.855%
- T9 5.252%
- T10 6.825%

First-lethal increments:
- T4 0.018%
- T5 0.362%
- T6 0.913%
- T7 1.259%
- T8 1.303%
- T9 1.397%
- T10 1.573%

T10 assembly:
- Spike + Ritual pair: 10.362%
- Pair + commander ready: 9.638%
- Lethal state: 6.816%
- Cumulative lethal: 6.825%

Interpretation:
The previously accepted 6.302% T10 clock understated the legal launch rate because
it omitted the line that casts Desperate Ritual while the spliced Lava Spike remains
on the stack. The corrected v0.4 clock supersedes that performance number without
changing the accepted 99-card control. The remaining gap between pair+commander-ready
and lethal state still justifies the frozen v0.5 launch-efficiency gate.

Disposition:
V04_FIVE_MANA_CLOCK_ACCEPTED
PRIOR_V04_CLOCK_SUPERSEDED
CONTROL_IDENTITY_UNCHANGED
