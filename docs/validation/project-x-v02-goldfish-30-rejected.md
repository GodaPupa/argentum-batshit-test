# Project X v0.2 — frozen 30-game deterministic goldfish block

> **Disposition: FORMALLY PRESERVED AND REJECTED.** Game 14 failed the rules/state audit with an illegal CastSpell action, and the block's Winding Way, Herald, terminal-state, and mana-bottleneck telemetry was not fit for validation. These exact seeds are retained only as a deterministic regression replay block. None of the aggregate engine, lethal, mulligan, functional, tutor, recursion, or bottleneck results below are admissible Project X performance evidence and none may be used for optimization.

Horizon: T12; Project X is on the play; opponent is a no-interaction 60-Plains goldfish.

## Summary

- Engine online by T4 / T5 / T6: 0/30, 0/30, 0/30
- Deterministic lethal by T4 / T5 / T6: 0/30, 0/30, 0/30
- Median engine turn: not reached
- Median deterministic lethal turn: not reached
- Infinite life: 0/30
- Huge Feeder before/no immediate lethal: 0/30
- Mulligan games / total mulligans: 11/30 / 14
- Functional without primary combo: 28/30
- At least one exactly-one-role-missing turn: 27/30
- Herald / Witness contribution: 0/30 / 7/30
- Birchlore / Nettle / Quirion contribution: 9/30 / 17/30 / 7/30
- Color / Khalni Garden / Haunted Mire bottleneck games: 28/30 / 7/30 / 6/30

## Per-game telemetry

### Game 1 — `0x8DAF02FC42EE3A2` (`638086385171882914`)

- Mulligans: 0; kept: [Carrion Feeder, Forest, Quirion Ranger, Wirewood Herald, Nettle Sentinel, Ivy Lane Denizen, Swamp]
- T1: [play Swamp, cast Carrion Feeder]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [T6:all-creatures:hand=[Masked Vandal, Wirewood Herald, Essence Warden]:grave=[]]
- Color stranded: [Quirion Ranger@T1, Nettle Sentinel@T1, Quirion Ranger@T2, Nettle Sentinel@T2, Quirion Ranger@T3, Nettle Sentinel@T3, Birchlore Rangers@T3, Quirion Ranger@T4, Birchlore Rangers@T4]; taplands: [none]
- Exactly one role missing: [1, 2, 5, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 235; audit: [clean]

### Game 2 — `0xE32AB5F951C1820` (`1023068492378413088`)

- Mulligans: 0; kept: [Carrion Feeder, Nettle Sentinel, Forest, Forest, Swamp, Nettle Sentinel, Nettle Sentinel]
- T1: [play Swamp, cast Carrion Feeder]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [none]
- Winding Way: [T3:all-creatures:hand=[Ivy Lane Denizen, Safehold Elite, Birchlore Rangers]:grave=[Haunted Mire], T5:all-creatures:hand=[Masked Vandal, Evolution Witness, Evolution Witness, Essence Warden]:grave=[]]
- Lead the Stampede: [none]
- Color stranded: [Nettle Sentinel@T1, Nettle Sentinel@T2]; taplands: [none]
- Exactly one role missing: [2, 3]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 250; audit: [clean]

### Game 3 — `0xDC3FCF0B428E3F7` (`991914453696570359`)

- Mulligans: 2; kept: [Swamp, Evolution Witness, Forest, Birchlore Rangers, Falkenrath Noble]
- T1: [play Swamp]; first meaningful: T2
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [T4:BLACK:[Birchlore Rangers, Evolution Witness], T5:BLACK:[Birchlore Rangers, Evolution Witness], T6:BLACK:[Birchlore Rangers, Evolution Witness], T7:BLACK:[Birchlore Rangers, Evolution Witness], T8:BLACK:[Birchlore Rangers, Evolution Witness], T9:BLACK:[Birchlore Rangers, Evolution Witness]]; Nettle: [T10:green-spell untap trigger]; Quirion: [none]
- Winding Way: [T9:all-creatures:hand=[Swamp, Swamp]:grave=[Ivy Lane Denizen, Winding Way]]
- Lead the Stampede: [none]
- Color stranded: [Birchlore Rangers@T1, Birchlore Rangers@T2, Nettle Sentinel@T5, Nettle Sentinel@T7, Safehold Elite@T7]; taplands: [Haunted Mire@T8:tapped]
- Exactly one role missing: []; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 354; audit: [clean]

### Game 4 — `0x9DE2E07F319464A` (`711056402849285706`)

- Mulligans: 1; kept: [Forest, Safehold Elite, Quirion Ranger, Haunted Mire, Carrion Feeder, Carrion Feeder]
- T1: [play Forest, cast Quirion Ranger]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [T2:BLACK:[Quirion Ranger, Birchlore Rangers]]; Nettle: [none]; Quirion: [T2:return=[Forest] target=[Permanent(entityId=e34)]]
- Winding Way: [none]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T1, Carrion Feeder@T2]; taplands: [Haunted Mire@T2:tapped]
- Exactly one role missing: [1, 2, 3, 4, 5]; functional classification: NONFUNCTIONAL_WITHOUT_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 208; audit: [clean]

### Game 5 — `0x6376A66781BFA83` (`447943676280961667`)

- Mulligans: 1; kept: [Lead the Stampede, Winding Way, Forest, Nettle Sentinel, Khalni Garden, Swamp]
- T1: [play Forest, cast Nettle Sentinel]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T2:green-spell untap trigger, T3:green-spell untap trigger, T4:green-spell untap trigger, T5:green-spell untap trigger, T6:green-spell untap trigger, T7:green-spell untap trigger]; Quirion: [none]
- Winding Way: [T2:all-creatures:hand=[Nettle Sentinel, Ivy Lane Denizen, Carrion Feeder]:grave=[Swamp]]
- Lead the Stampede: [T3:all-creatures:hand=[Evolution Witness, Wirewood Herald, Wirewood Herald, Evolution Witness, Safehold Elite]:grave=[]]
- Color stranded: [Wirewood Herald@T4, Safehold Elite@T4, Birchlore Rangers@T7, Nettle Sentinel@T7]; taplands: [none]
- Exactly one role missing: [2, 3, 4]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 300; audit: [clean]

### Game 6 — `0xFE32C7BFF6F1894` (`1144807641360308372`)

- Mulligans: 1; kept: [Forest, Falkenrath Noble, Lead the Stampede, Masked Vandal, Lead the Stampede, Haunted Mire]
- T1: [play Forest]; first meaningful: T3
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [T6:BLACK:[Nettle Sentinel, Birchlore Rangers]]; Nettle: [T4:green-spell untap trigger, T5:green-spell untap trigger, T6:green-spell untap trigger]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T2]; taplands: [Haunted Mire@T2:tapped, Khalni Garden@T7:tapped]
- Exactly one role missing: [4]; functional classification: NONFUNCTIONAL_WITHOUT_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 259; audit: [clean]

### Game 7 — `0xF1545B141BCEBEA` (`1086851512694270954`)

- Mulligans: 1; kept: [Evolution Witness, Nettle Sentinel, Evolution Witness, Forest, Forest, Nettle Sentinel]
- T1: [play Forest, cast Nettle Sentinel]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T2:green-spell untap trigger, T3:green-spell untap trigger, T3:green-spell untap trigger, T5:green-spell untap trigger, T5:green-spell untap trigger, T7:green-spell untap trigger, T7:green-spell untap trigger]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [T7:all-creatures:hand=[Evolution Witness, Quirion Ranger, Wirewood Herald]:grave=[]]
- Color stranded: [Carrion Feeder@T2, Carrion Feeder@T3, Carrion Feeder@T4, Carrion Feeder@T5, Carrion Feeder@T6, Carrion Feeder@T7]; taplands: [none]
- Exactly one role missing: [3, 4, 5, 6, 7]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 269; audit: [clean]

### Game 8 — `0xD0EE08D6D939280` (`940936270200410752`)

- Mulligans: 1; kept: [Forest, Swamp, Forest, Evolution Witness, Quirion Ranger, Nettle Sentinel]
- T1: [play Forest, cast Nettle Sentinel]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [Quirion Ranger@T7]
- Mana — Birchlore: [T3:BLACK:[Nettle Sentinel, Quirion Ranger], T3:BLACK:[Nettle Sentinel, Birchlore Rangers]]; Nettle: [T2:green-spell untap trigger, T2:green-spell untap trigger, T5:green-spell untap trigger, T6:green-spell untap trigger, T7:green-spell untap trigger]; Quirion: [T2:return=[Forest] target=[Permanent(entityId=e34)], T3:return=[Forest] target=[Permanent(entityId=e24)]]
- Winding Way: [none]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T3]; taplands: [none]
- Exactly one role missing: [6, 7]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 292; audit: [clean]

### Game 9 — `0xA7ACDC7B6ED365C` (`755142145189164636`)

- Mulligans: 1; kept: [Nettle Sentinel, Forest, Safehold Elite, Birchlore Rangers, Swamp, Lead the Stampede]
- T1: [play Forest, cast Nettle Sentinel]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [T4:BLACK:[Birchlore Rangers, Nettle Sentinel], T5:BLACK:[Birchlore Rangers, Safehold Elite], T5:BLACK:[Nettle Sentinel, Safehold Elite], T5:BLACK:[Quirion Ranger, Nettle Sentinel], T6:BLACK:[Quirion Ranger, Birchlore Rangers], T6:BLACK:[Nettle Sentinel, Safehold Elite], T6:BLACK:[Nettle Sentinel, Safehold Elite], T6:BLACK:[Nettle Sentinel, Safehold Elite]]; Nettle: [T2:green-spell untap trigger, T3:green-spell untap trigger, T4:green-spell untap trigger, T4:green-spell untap trigger, T5:green-spell untap trigger, T6:green-spell untap trigger, T7:green-spell untap trigger]; Quirion: [T5:return=[Forest] target=[Permanent(entityId=e24)], T5:return=[Forest] target=[Permanent(entityId=e24)], T6:return=[Forest] target=[Permanent(entityId=e24)], T6:return=[Forest] target=[Permanent(entityId=e24)]]
- Winding Way: [none]
- Lead the Stampede: [T3:all-creatures:hand=[Carrion Feeder]:grave=[]]
- Color stranded: [Safehold Elite@T6, Wirewood Herald@T7]; taplands: [none]
- Exactly one role missing: [3, 4, 5, 6, 7]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 328; audit: [clean]

### Game 10 — `0x7E7D9240EDEF832` (`569662626777200690`)

- Mulligans: 0; kept: [Forest, Wirewood Herald, Carrion Feeder, Swamp, Swamp, Forest, Lead the Stampede]
- T1: [play Swamp, cast Carrion Feeder]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T6:green-spell untap trigger]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [T5:all-creatures:hand=[Masked Vandal, Safehold Elite, Ivy Lane Denizen, Falkenrath Noble]:grave=[]]
- Color stranded: [none]; taplands: [none]
- Exactly one role missing: [4, 5, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 279; audit: [clean]

### Game 11 — `0xD8A42A96985829F` (`975665564666397343`)

- Mulligans: 0; kept: [Forest, Wirewood Herald, Forest, Khalni Garden, Safehold Elite, Safehold Elite, Winding Way]
- T1: [play Forest]; first meaningful: T2
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [T6:return=[Forest] target=[Permanent(entityId=e123)], T6:return=[Forest] target=[Permanent(entityId=e8)], T7:return=[Forest] target=[Permanent(entityId=e123)]]
- Winding Way: [T3:all-creatures:hand=[Ivy Lane Denizen, Ivy Lane Denizen]:grave=[Swamp, Forest]]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T6, Carrion Feeder@T7]; taplands: [Khalni Garden@T3:tapped]
- Exactly one role missing: [3, 4, 5, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 270; audit: [clean]

### Game 12 — `0x20E263BCD29AB1D` (`148097876036791069`)

- Mulligans: 0; kept: [Winding Way, Safehold Elite, Nettle Sentinel, Swamp, Essence Warden, Forest, Masked Vandal]
- T1: [play Forest, cast Essence Warden]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T7:green-spell untap trigger, T8:green-spell untap trigger]; Quirion: [none]
- Winding Way: [T3:all-creatures:hand=[Safehold Elite, Evolution Witness]:grave=[Forest, Forest], T4:all-creatures:hand=[Wirewood Herald, Birchlore Rangers, Carrion Feeder]:grave=[Swamp]]
- Lead the Stampede: [none]
- Color stranded: [Birchlore Rangers@T6]; taplands: [none]
- Exactly one role missing: [4, 5, 6, 7, 8]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 344; audit: [clean]

### Game 13 — `0x4FF420E80BFDFF` (`22504945329372671`)

- Mulligans: 2; kept: [Swamp, Quirion Ranger, Forest, Birchlore Rangers, Winding Way]
- T1: [play Forest, cast Quirion Ranger]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [T4:return=[Forest] target=[Permanent(entityId=e34)], T5:return=[Forest] target=[Permanent(entityId=e34)], T5:return=[Forest] target=[Permanent(entityId=e30)], T6:return=[Forest] target=[Permanent(entityId=e30)]]
- Winding Way: [T2:all-creatures:hand=[Carrion Feeder, Birchlore Rangers]:grave=[Haunted Mire, Forest]]
- Lead the Stampede: [none]
- Color stranded: [none]; taplands: [none]
- Exactly one role missing: []; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 240; audit: [clean]

### Game 14 — `0x2B7B990F635FEBD` (`195829141071068861`)

- Mulligans: 0; kept: [Forest, Quirion Ranger, Carrion Feeder, Khalni Garden, Forest, Carrion Feeder, Safehold Elite]
- T1: [play Forest, cast Quirion Ranger]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [T5:return=[Forest] target=[Permanent(entityId=e24)]]
- Winding Way: [none]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T1, Carrion Feeder@T2, Carrion Feeder@T3, Carrion Feeder@T4]; taplands: [Khalni Garden@T3:tapped]
- Exactly one role missing: [1, 2, 3, 4, 5]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ILLEGAL_ACTION; actions: 165; audit: [illegal action CastSpell: Not enough mana to auto-pay]

### Game 15 — `0xF4181E9519A47AE` (`1099302623151540142`)

- Mulligans: 0; kept: [Swamp, Forest, Winding Way, Ivy Lane Denizen, Nettle Sentinel, Carrion Feeder, Winding Way]
- T1: [play Swamp, cast Carrion Feeder]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T5:green-spell untap trigger, T6:green-spell untap trigger, T6:green-spell untap trigger]; Quirion: [none]
- Winding Way: [T2:all-creatures:hand=[Birchlore Rangers, Nettle Sentinel, Wirewood Herald, Nettle Sentinel]:grave=[], T5:all-creatures:hand=[Birchlore Rangers, Birchlore Rangers]:grave=[Forest, Forest]]
- Lead the Stampede: [none]
- Color stranded: [Nettle Sentinel@T1, Nettle Sentinel@T2, Birchlore Rangers@T4, Nettle Sentinel@T4, Birchlore Rangers@T5]; taplands: [none]
- Exactly one role missing: [1, 2, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 262; audit: [clean]

### Game 16 — `0x592CF05E0E6480D` (`401610940944369677`)

- Mulligans: 0; kept: [Forest, Wirewood Herald, Safehold Elite, Evolution Witness, Khalni Garden, Forest, Birchlore Rangers]
- T1: [play Forest, cast Birchlore Rangers]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [Safehold Elite@T7]
- Mana — Birchlore: [T3:BLACK:[Birchlore Rangers, Wirewood Herald], T4:BLACK:[Birchlore Rangers, Wirewood Herald], T4:BLACK:[Birchlore Rangers, Safehold Elite], T5:BLACK:[Birchlore Rangers, Birchlore Rangers], T5:BLACK:[Wirewood Herald, Safehold Elite], T6:BLACK:[Birchlore Rangers, Birchlore Rangers], T6:BLACK:[Wirewood Herald, Wirewood Herald], T6:BLACK:[Evolution Witness, Safehold Elite], T7:BLACK:[Birchlore Rangers, Birchlore Rangers], T7:BLACK:[Evolution Witness, Safehold Elite]]; Nettle: [none]; Quirion: [none]
- Winding Way: [T4:all-creatures:hand=[Birchlore Rangers, Wirewood Herald]:grave=[Forest, Winding Way]]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T5, Carrion Feeder@T6, Falkenrath Noble@T8]; taplands: [Khalni Garden@T3:tapped]
- Exactly one role missing: [5, 6, 7, 8]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 327; audit: [clean]

### Game 17 — `0x31E99250562D3C4` (`224785415698305988`)

- Mulligans: 0; kept: [Lead the Stampede, Wirewood Herald, Carrion Feeder, Winding Way, Forest, Wirewood Herald, Forest]
- T1: [play Forest]; first meaningful: T2
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [none]
- Winding Way: [T4:all-creatures:hand=[Forest, Swamp]:grave=[Safehold Elite, Winding Way], T7:all-creatures:hand=[Haunted Mire, Swamp, Forest]:grave=[Wirewood Herald]]
- Lead the Stampede: [T7:all-creatures:hand=[Safehold Elite, Birchlore Rangers, Ivy Lane Denizen]:grave=[]]
- Color stranded: [Carrion Feeder@T1, Carrion Feeder@T2, Carrion Feeder@T3, Carrion Feeder@T4, Carrion Feeder@T5, Carrion Feeder@T6, Carrion Feeder@T7, Birchlore Rangers@T8]; taplands: [none]
- Exactly one role missing: []; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 326; audit: [clean]

### Game 18 — `0xBB3AD7D16086DAE` (`843208308000583086`)

- Mulligans: 0; kept: [Lead the Stampede, Winding Way, Forest, Ivy Lane Denizen, Swamp, Forest, Swamp]
- T1: [play Forest]; first meaningful: T2
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [Safehold Elite@T6]
- Mana — Birchlore: [none]; Nettle: [T7:green-spell untap trigger]; Quirion: [none]
- Winding Way: [T2:all-creatures:hand=[Quirion Ranger, Carrion Feeder, Birchlore Rangers]:grave=[Khalni Garden], T5:all-creatures:hand=[Evolution Witness, Nettle Sentinel]:grave=[Lead the Stampede, Swamp]]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T3, Quirion Ranger@T3, Birchlore Rangers@T3, Quirion Ranger@T6, Birchlore Rangers@T6, Nettle Sentinel@T6]; taplands: [none]
- Exactly one role missing: [2, 3, 4, 6, 7]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 286; audit: [clean]

### Game 19 — `0xE7658226311FAC2` (`1042117268497103554`)

- Mulligans: 2; kept: [Lead the Stampede, Birchlore Rangers, Forest, Ivy Lane Denizen, Swamp]
- T1: [play Forest, cast Birchlore Rangers]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [Birchlore Rangers@T5]
- Mana — Birchlore: [T4:BLACK:[Birchlore Rangers, Evolution Witness]]; Nettle: [none]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [T3:all-creatures:hand=[Evolution Witness, Wirewood Herald, Evolution Witness, Quirion Ranger]:grave=[]]
- Color stranded: [Quirion Ranger@T5, Birchlore Rangers@T5, Quirion Ranger@T6, Birchlore Rangers@T6, Quirion Ranger@T7, Birchlore Rangers@T7]; taplands: [none]
- Exactly one role missing: [3, 4]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 274; audit: [clean]

### Game 20 — `0x6961682BB06432B` (`474591561473475371`)

- Mulligans: 0; kept: [Forest, Masked Vandal, Lead the Stampede, Khalni Garden, Forest, Nettle Sentinel, Wirewood Herald]
- T1: [play Forest, cast Nettle Sentinel]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T2:green-spell untap trigger, T3:green-spell untap trigger, T4:green-spell untap trigger, T4:green-spell untap trigger, T5:green-spell untap trigger, T5:green-spell untap trigger, T5:green-spell untap trigger, T5:green-spell untap trigger, T6:green-spell untap trigger, T6:green-spell untap trigger, T6:green-spell untap trigger]; Quirion: [none]
- Winding Way: [T3:all-creatures:hand=[Nettle Sentinel, Ivy Lane Denizen, Wirewood Herald]:grave=[Forest]]
- Lead the Stampede: [T5:all-creatures:hand=[Evolution Witness]:grave=[]]
- Color stranded: [Carrion Feeder@T6]; taplands: [Khalni Garden@T3:tapped]
- Exactly one role missing: [3, 4, 5, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 257; audit: [clean]

### Game 21 — `0xBFC356E9D051E82` (`863623977745325698`)

- Mulligans: 1; kept: [Carrion Feeder, Winding Way, Khalni Garden, Swamp, Wirewood Herald, Quirion Ranger]
- T1: [play Swamp, cast Carrion Feeder]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [Safehold Elite@T7]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [none]
- Winding Way: [T6:all-creatures:hand=[Falkenrath Noble]:grave=[Winding Way, Winding Way, Haunted Mire], T6:all-creatures:hand=[Swamp, Forest]:grave=[Lead the Stampede, Safehold Elite]]
- Lead the Stampede: [none]
- Color stranded: [Quirion Ranger@T1, Quirion Ranger@T2, Quirion Ranger@T3, Quirion Ranger@T4, Quirion Ranger@T5, Quirion Ranger@T6]; taplands: [Khalni Garden@T2:tapped]
- Exactly one role missing: [7]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 288; audit: [clean]

### Game 22 — `0xD717B57CB4A164C` (`968691011864368716`)

- Mulligans: 0; kept: [Masked Vandal, Forest, Ivy Lane Denizen, Swamp, Evolution Witness, Swamp, Forest]
- T1: [play Forest]; first meaningful: T2
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [Ivy Lane Denizen@T6]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [none]
- Winding Way: [T8:all-creatures:hand=[Safehold Elite, Safehold Elite]:grave=[Forest, Winding Way]]
- Lead the Stampede: [T3:all-creatures:hand=[Birchlore Rangers, Ivy Lane Denizen, Falkenrath Noble, Quirion Ranger]:grave=[], T7:all-creatures:hand=[Nettle Sentinel, Ivy Lane Denizen, Wirewood Herald, Nettle Sentinel]:grave=[]]
- Color stranded: [Birchlore Rangers@T4, Quirion Ranger@T4, Birchlore Rangers@T5, Quirion Ranger@T5, Safehold Elite@T8, Nettle Sentinel@T8]; taplands: [none]
- Exactly one role missing: [5, 6, 7, 8]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 300; audit: [clean]

### Game 23 — `0xEC4B5B583544BBF` (`1064175203163327423`)

- Mulligans: 0; kept: [Winding Way, Lead the Stampede, Winding Way, Carrion Feeder, Forest, Forest, Forest]
- T1: [play Forest]; first meaningful: T2
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [Carrion Feeder@T6]
- Mana — Birchlore: [none]; Nettle: [T4:green-spell untap trigger, T5:green-spell untap trigger, T6:green-spell untap trigger, T6:green-spell untap trigger, T7:green-spell untap trigger]; Quirion: [T6:return=[Forest] target=[Permanent(entityId=e24)], T7:return=[Forest] target=[Permanent(entityId=e24)], T7:return=[Forest] target=[Permanent(entityId=e24)]]
- Winding Way: [T3:all-creatures:hand=[Nettle Sentinel, Safehold Elite, Quirion Ranger]:grave=[Swamp]]
- Lead the Stampede: [T4:all-creatures:hand=[Quirion Ranger, Safehold Elite, Evolution Witness]:grave=[]]
- Color stranded: [Carrion Feeder@T1, Carrion Feeder@T2, Carrion Feeder@T3, Carrion Feeder@T4, Carrion Feeder@T6, Carrion Feeder@T7]; taplands: [none]
- Exactly one role missing: [3, 4, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 282; audit: [clean]

### Game 24 — `0x6F1EA49A2D6DE1E` (`500438635600338462`)

- Mulligans: 0; kept: [Lead the Stampede, Evolution Witness, Evolution Witness, Ivy Lane Denizen, Forest, Forest, Forest]
- T1: [play Forest]; first meaningful: T2
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T6:green-spell untap trigger, T7:green-spell untap trigger]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [T3:all-creatures:hand=[Falkenrath Noble, Carrion Feeder, Ivy Lane Denizen]:grave=[]]
- Color stranded: [Carrion Feeder@T4, Carrion Feeder@T5, Carrion Feeder@T6, Carrion Feeder@T7]; taplands: [none]
- Exactly one role missing: [2, 3]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 255; audit: [clean]

### Game 25 — `0xD785890B3F06FE8` (`970623098215755752`)

- Mulligans: 0; kept: [Birchlore Rangers, Carrion Feeder, Haunted Mire, Safehold Elite, Carrion Feeder, Birchlore Rangers, Forest]
- T1: [play Forest, cast Birchlore Rangers]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [T2:BLACK:[Birchlore Rangers, Birchlore Rangers]]; Nettle: [T5:green-spell untap trigger, T5:green-spell untap trigger]; Quirion: [none]
- Winding Way: [T3:all-creatures:hand=[Ivy Lane Denizen, Nettle Sentinel, Quirion Ranger]:grave=[Swamp], T4:all-creatures:hand=[Carrion Feeder, Essence Warden, Nettle Sentinel]:grave=[Swamp], T6:all-creatures:hand=[Forest, Swamp, Forest]:grave=[Falkenrath Noble]]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T1, Carrion Feeder@T2]; taplands: [Haunted Mire@T2:tapped]
- Exactly one role missing: [1, 2, 3]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 256; audit: [clean]

### Game 26 — `0x86BD41426043EDC` (`606811757813710556`)

- Mulligans: 0; kept: [Winding Way, Ivy Lane Denizen, Birchlore Rangers, Haunted Mire, Essence Warden, Forest, Swamp]
- T1: [play Forest, cast Essence Warden]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [none]
- Winding Way: [T2:all-creatures:hand=[Carrion Feeder, Wirewood Herald]:grave=[Swamp, Khalni Garden]]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T3, Birchlore Rangers@T3]; taplands: [Haunted Mire@T5:tapped]
- Exactly one role missing: [2, 3, 4, 5, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 247; audit: [clean]

### Game 27 — `0x4B6D8125B0420FC` (`339696395261059324`)

- Mulligans: 0; kept: [Forest, Nettle Sentinel, Safehold Elite, Haunted Mire, Safehold Elite, Safehold Elite, Carrion Feeder]
- T1: [play Forest, cast Nettle Sentinel]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T2:green-spell untap trigger, T3:green-spell untap trigger, T4:green-spell untap trigger, T5:green-spell untap trigger]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T1, Carrion Feeder@T2]; taplands: [Haunted Mire@T3:tapped]
- Exactly one role missing: [1, 2, 3, 5]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 186; audit: [clean]

### Game 28 — `0x2B68C852410DAC5` (`195498137300818629`)

- Mulligans: 0; kept: [Falkenrath Noble, Carrion Feeder, Forest, Wirewood Herald, Lead the Stampede, Swamp, Evolution Witness]
- T1: [play Swamp, cast Carrion Feeder]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [none]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [T3:all-creatures:hand=[Carrion Feeder, Quirion Ranger, Evolution Witness]:grave=[]]
- Color stranded: [Carrion Feeder@T4]; taplands: [none]
- Exactly one role missing: [2, 3, 4, 5, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 244; audit: [clean]

### Game 29 — `0x31E044C43A2FB0A` (`224621757013883658`)

- Mulligans: 0; kept: [Forest, Khalni Garden, Nettle Sentinel, Nettle Sentinel, Essence Warden, Evolution Witness, Ivy Lane Denizen]
- T1: [play Forest, cast Essence Warden]; first meaningful: T1
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [none]; Nettle: [T3:green-spell untap trigger, T4:green-spell untap trigger, T5:green-spell untap trigger, T5:green-spell untap trigger, T5:green-spell untap trigger, T5:green-spell untap trigger]; Quirion: [none]
- Winding Way: [none]
- Lead the Stampede: [T5:all-creatures:hand=[Evolution Witness, Carrion Feeder, Nettle Sentinel, Ivy Lane Denizen]:grave=[]]
- Color stranded: [Nettle Sentinel@T2, Carrion Feeder@T5]; taplands: [Khalni Garden@T4:tapped]
- Exactly one role missing: [5]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 210; audit: [clean]

### Game 30 — `0x533A60825713D4B` (`374825747920010571`)

- Mulligans: 1; kept: [Safehold Elite, Winding Way, Carrion Feeder, Forest, Evolution Witness, Forest]
- T1: [play Forest]; first meaningful: T2
- Engine / huge Feeder / infinite life / lethal: — / — / — / — (none)
- Herald: [none]; Witness: [none]
- Mana — Birchlore: [T3:BLACK:[Nettle Sentinel, Birchlore Rangers]]; Nettle: [T3:green-spell untap trigger, T4:green-spell untap trigger, T5:green-spell untap trigger, T6:green-spell untap trigger, T6:green-spell untap trigger]; Quirion: [none]
- Winding Way: [T2:all-creatures:hand=[Nettle Sentinel, Safehold Elite]:grave=[Lead the Stampede, Haunted Mire]]
- Lead the Stampede: [none]
- Color stranded: [Carrion Feeder@T1, Carrion Feeder@T2, Carrion Feeder@T3, Carrion Feeder@T4, Carrion Feeder@T5, Carrion Feeder@T6]; taplands: [none]
- Exactly one role missing: [1, 2, 3, 4, 5, 6]; functional classification: FUNCTIONAL_WITHOUT_PRIMARY_COMBO
- Secondary Witness loop: —; stop: ENGINE_GAME_OVER; actions: 243; audit: [clean]
