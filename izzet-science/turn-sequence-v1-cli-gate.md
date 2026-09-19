# Turn-Sequence v1 — Execution Gate

Date: 2026-09-18
Harness head: 8304b362dcd8ee58e4c5985381ffca7783218dbf

A final code audit before execution found that the harness now has stateful primitives and regressions, but its CLI main routine still invokes only the opening_baseline sampler. It does not yet iterate full games through turns 2–6 or aggregate the frozen turn-sequence metrics.

Therefore the harness is NOT yet an executable implementation of TURN_SEQUENCE_V1 despite the primitives being present.

Required before execution:
1. add simulate_game / simulate_sample loop using opening seven plus one draw per turn;
2. invoke development_turn each turn with untap ordering handled exactly once;
3. aggregate per-turn U/R/UU, Guildmage castability/activation readiness, deployed nonland mana, Reversal neutral/positive, Island count/High Tide output;
4. emit machine-readable deterministic results;
5. add regressions that prevent double untap and verify draw/turn ordering.

No turn-sequence percentages accepted.

Disposition: CLI_EXECUTION_LOOP_MISSING
