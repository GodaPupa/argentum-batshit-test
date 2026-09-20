# Primary Combo Assembly Gate v1 — Protocol

Baseline: Izzet Science?! v0.3 — Lean Machine
Baseline commit: 7b639cbcb85e0adee5861aa6c42d138b121bf7ca

Primary deterministic package:
- Commander: Izzet Guildmage
- Lava Spike
- Desperate Ritual

Goal:
Measure earliest legal assembly/opportunity timing under the selection-enabled, information-limited goldfish model. Do not claim a win merely because the three names are present.

Required state distinctions:
1. Spike in hand.
2. Ritual in hand.
3. Both Spike + Ritual in hand.
4. Guildmage castable this turn.
5. Guildmage deployable while preserving the mana required for the combo line on a later turn.
6. Guildmage treated as available from the command zone; commander tax begins at zero in solitaire.
7. Primary package assembled in hand/command-zone terms.
8. Primary line mana-feasible under modeled costs.
9. Earliest deterministic lethal opportunity only after the actual copy/ritual loop cost and sequencing are encoded.

Metrics T2-T10:
- Spike presence
- Ritual presence
- pair presence
- pair + Guildmage actionable
- pair + Guildmage already deployed (once battlefield commander state exists)
- tutor/selection-assisted pair finds
- earliest assembly opportunity distribution
- median assembly opportunity
- stranded component turns

Scope discipline:
This first gate may accept assembly metrics before lethal-loop execution is implemented, but must label them ASSEMBLY, not KILL/LETHAL.

No opponent interaction, removal, commander tax after removal, or mulliplayer disruption is modeled in this gate.

Disposition: PRIMARY_COMBO_ASSEMBLY_V1_AUTHORIZED
