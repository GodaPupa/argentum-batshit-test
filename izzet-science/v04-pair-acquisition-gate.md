# v0.4 Challenger Gate — Pair Acquisition First

Parent: Izzet Science?! v0.3
Accepted control clock: 6.180% cumulative deterministic lethal by T10; earliest T5.
Control audit: izzet-science/v03-corrected-clock-accepted.md

Observed bottlenecks:
1. Spike + Ritual pair acquisition: 10.242% by T10.
2. Launch-mana feasibility after pair+commander assembly.

Experiment order:
Do not change mana infrastructure yet. First test whether a low-opportunity-cost card-selection upgrade improves pair acquisition without weakening interaction.

Candidate A — Strategic Planning challenger:
-1 Think Twice
+1 Strategic Planning

Hypothesis:
Strategic Planning sees three cards immediately for 1U and can place nonselected cards in the graveyard, increasing access to singleton combo components and synergizing with Archaeomancer/Mnemonic Wall/Izzet Chronarch/Treasure Cruise. Think Twice is resilient card advantage but sees only one new card on the front half and is slower for combo assembly.

Candidate B — Impulse-density alternative:
Identify another legal common 1-2 mana selection spell only after legality/card-text verification; do not materialize from memory alone.

Primary metrics:
- cumulative lethal by T5-T10
- first-lethal increments
- Spike+Ritual pair by turn
- tutor-assisted pair finds
- selection cards seen/drawn
- Guildmage deployment
- actual U/UU/R executable actions

Acceptance:
Paired same-seed 100k T1-T10 against frozen v0.3. A challenger must improve pair acquisition/lethal clock without a material ordinary-action regression. No adoption from theory alone.

Disposition: V04_PAIR_ACQUISITION_GATE_AUTHORIZED
