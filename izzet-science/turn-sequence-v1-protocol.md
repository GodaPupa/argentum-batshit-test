# Turn-Sequenced Mana Baseline v1 — Protocol

Control: frozen Izzet Science?! v0.1
Freeze: 19cb49f91e5f4aebeb470a40a07cb6b7bb31b756
Opening baseline: b87566b0491aafe724da5e07940a4b948edaf2e0

Purpose: measure actual early mana development before comparing land/rock challengers.

Model requirements:
- exact frozen 99-card library; commander starts in command zone
- deterministic sampling
- opening seven plus one draw per turn
- one land play per turn
- tapped lands enter tapped
- Ash Barrens may basic-landcycle when that improves near-term colored access
- Evolving Wilds / Terramorphic Expanse fetch basics and impose their real tempo cost
- Izzet Boilerworks requires a legal land return
- mana rocks may be cast only when their costs and colors are payable
- Guildmage may be deployed only when legal
- preserve interaction mana only in the later gameplay-policy phase; this phase optimizes mana development
- do not model cantrip selection yet, so results remain a conservative construction baseline

Metrics through turns 2–6:
- land drops made
- U, R, UU availability
- Guildmage castable
- Guildmage on battlefield plus one legal copy activation available
- dedicated nonland mana deployed
- Dramatic Reversal neutral threshold: at least 3 usable nonland mana including blue for activation cycle
- Dramatic Reversal positive threshold: more than 3 usable nonland mana including blue
- High Tide Island count and post-Tide gross land mana
- mana screw / flood indicators

No challenger may be sampled until control results are preserved.

Status: TURN_SEQUENCE_PROTOCOL_FROZEN
