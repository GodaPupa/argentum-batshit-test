# Spell Sequencing Engine v1 — Protocol

Parent comparison:
- v0.2 baseline
- C1 Lean Machine (-Sisay's Ring, -Ur-Golem's Eye, +Curate, +Lose Focus)

Purpose:
Begin valuing replacement spells by resolving card-selection effects rather than treating them only as names in hand.

Phase 1 scope:
Implement deterministic, information-limited selection for:
- Ponder
- Preordain
- Brainstorm
- Consider
- Opt
- Impulse
- Curate
- Faithless Looting
- Thrill of Possibility
- Frantic Search
- Think Twice where applicable

Initial policy objective hierarchy:
1. secure missing land drop / required color for current or next turn;
2. complete a known primary combo pair when the other component is already in hand;
3. find interaction when hand lacks any;
4. improve mana development when behind;
5. otherwise maximize card velocity while avoiding unnecessary dead combo pieces.

Constraints:
- no future-library omniscience beyond cards legally viewed by the spell;
- Brainstorm must put back actual cards from hand;
- Curate sees exactly top two, one to hand and one to graveyard;
- Consider/Opt obey their actual look/draw structure;
- Ponder/Preordain must preserve legal ordering/bottom/shuffle semantics as modeled;
- discard spells pay their real discard costs;
- Frantic Search untaps exactly up to three lands after resolution;
- no opponent modeling yet;
- permission receives no fabricated 'countered spell' value in Phase 1.

Metrics:
- land-drop rescue rate
- missing-color rescue rate
- combo-component find rate
- interaction find rate
- cards seen/drawn by turn
- dead-piece burden
- earliest primary-combo assembly opportunity
- Guildmage/actionable states after selection

No promotion of C1 until Phase 1 selection regressions and same-seed comparison are accepted.

Disposition: SPELL_SEQUENCE_V1_AUTHORIZED
