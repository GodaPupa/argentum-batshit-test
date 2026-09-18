# Turn-Sequenced Mana Baseline v1 — Execution Record

Control: Izzet Science?! v0.1
Protocol: 6f536a52c35425176a722f279643fd400fb40a5f

Execution status: NOT YET ACCEPTED

During implementation review, the protocol exposed a required modeling dependency before numerical results can be trusted: several mana permanents are conditional rather than fixed-output sources.

Required explicit semantics before execution:
- Fellwar Stone: opponent-color production is table-dependent; multiplayer opponent mana bases must be modeled or Stone must receive a declared conservative assumption.
- Star Compass: color production depends on controlled basic lands.
- Network Terminal: fixed mana ability plus its separate draw activation must not be conflated.
- Everflowing Chalice: output depends on multikicker amount actually paid.
- Izzet Signet: requires one mana input and produces UR; gross output cannot be counted as net +2.
- Sky Diamond / Fire Diamond enter tapped.
- Ur-Golem's Eye / Sisay's Ring produce two colorless each and cannot themselves satisfy the blue component of Guildmage's instant-copy activation.
- creature mana sources in the utility module (Ornithopter of Paradise, Silver Myr, Iron Myr) are not part of the frozen 10 dedicated infrastructure count but do affect actual Reversal board output and have summoning-sickness timing.

Protocol safeguard:
No fabricated turn-sequence percentages will be recorded until these semantics are fixed. Opening-baseline evidence remains accepted and unchanged.

Next action:
Freeze a mana-source semantics table, then execute the deterministic turn-sequence baseline from the same frozen v0.1 deck.

Disposition: EXECUTION_HELD_FOR_MANA_SEMANTICS
