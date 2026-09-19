# Executable-Action Gate v1 — Protocol

Purpose:
Move beyond raw mana availability and measure whether the actual frozen hand can execute strategically relevant spells after the turn's land play and before optional infrastructure spending.

Compare:
- v0.1 control (20 Island / 6 Mountain)
- R0.5 (19 / 7)
- R1 (18 / 8)

Same 100000 trajectories and seed 0x1A22E7001.

Initial action classes to instrument from the actual 99:
1. Commander deployment: Izzet Guildmage {U/R}{U/R}; model as needing one U and one R conservatively.
2. UU interaction/value:
   - Counterspell UU
   - Deprive UU
   - Ideas Unbound UU
3. Single-blue interaction/selection readiness.
4. Single-red removal/engine readiness.
5. Combo-pair color readiness:
   - Desperate Ritual 1R plus Guildmage deployment state
   - Lava Spike R plus Guildmage deployment state
6. Hold-up states:
   - Guildmage deployable while retaining U
   - Guildmage deployable while retaining R
   - UU spell executable while retaining R
7. High Tide immediate productive state (existing metric).

Important:
This gate must inspect cards actually present in hand. It must not count a mana pattern as useful if no relevant spell requiring that pattern is available.

Scope:
This is still a mana/action executability model, not a full strategic gameplay agent. No opponent targets, stack decisions, card-selection lookahead, or combo-win claims are authorized yet.

Disposition: EXECUTABLE_ACTION_GATE_AUTHORIZED
