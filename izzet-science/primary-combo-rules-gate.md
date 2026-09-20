# Primary Combo Rules Gate — Spike / Ritual / Guildmage

Purpose: freeze the exact rules model required before any assembly state may be labeled lethal.

Required sequence:
1. Izzet Guildmage is already on battlefield.
2. Cast Lava Spike for R.
3. While casting Lava Spike, splice Desperate Ritual by paying its splice cost 1R; Ritual remains in hand.
4. The resulting Lava Spike spell has both Lava Spike's effect and Desperate Ritual's mana-producing text.
5. With that combined instant/sorcery spell on the stack, activate Izzet Guildmage's red copy ability for 2R, targeting the spell.
6. A copy resolves and deals 3 damage while adding RRR.
7. The RRR produced by that copy can fund the next 2R Guildmage activation, allowing the process to repeat while the original combined spell remains on the stack.
8. Each repeated copy is mana-neutral after the first activation and deals 3 damage.
9. The original spell eventually resolves after the desired number of copies, dealing another 3 and adding RRR.

Goldfish feasibility boundary:
- Guildmage already battlefield.
- Lava Spike and Desperate Ritual in hand.
- Sufficient mana to cast Lava Spike with Ritual spliced: total 2R (three mana, two of it red across Spike R + splice 1R).
- After that payment, sufficient additional 2R to activate Guildmage once before any copy has resolved.
- Therefore initial turn resource requirement is 6 total mana with at least 4 red payments across the cast/splice/first-copy sequence, subject to exact source sequencing.
- Do not count the RRR from the original spell before the first copy activation; the original is still on stack.
- Once the first copied combined spell resolves, its RRR sustains subsequent 2R activations.

Damage:
- Against a 30-life PDH opponent at full life, enough copies must resolve to reach at least 30 total damage, including eventual original Lava Spike resolution.
- Multiplayer table-kill logic is out of scope; this gate is single-opponent deterministic goldfish lethal only.

Required deterministic regressions:
- assembled cards + commander but insufficient first-copy mana => not lethal;
- exactly feasible initial mana => lethal loop available;
- Ritual must remain in hand after splice;
- original spell must remain on stack while copied;
- copied combined spell includes both damage and RRR;
- mana cannot be generated from the unresolved original to pay the first Guildmage activation.

Disposition: RULES_GATE_FROZEN_PENDING_IMPLEMENTATION
