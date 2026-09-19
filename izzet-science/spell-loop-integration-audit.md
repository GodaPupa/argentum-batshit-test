# Spell-Loop Integration Audit

Date: 2026-09-19

Blocking architectural issue found before integration:
simulate_one() currently represents the library as a static list plus integer draw cursor (pos). SpellState selection effects mutate a separate library list. Integrating cast_one_selection() directly would desynchronize future draw order because simulate_one would continue drawing deck[pos] from the original unmodified deck.

Required refactor before selection-enabled trajectories:
- replace pos-based draws with a single mutable library object/list;
- opening seven removes cards from that library;
- normal draw step pops from the same library mutated by Ponder/Preordain/Brainstorm/Opt/Consider/Impulse/Curate;
- all selection effects operate on that single source of truth;
- add regressions proving a Ponder-selected top card is the next legal draw and Brainstorm put-backs can be drawn on later turns;
- only then integrate selection casting into T1-T6.

No selection-enabled results accepted before this refactor.

Disposition: MUTABLE_LIBRARY_REFACTOR_REQUIRED
