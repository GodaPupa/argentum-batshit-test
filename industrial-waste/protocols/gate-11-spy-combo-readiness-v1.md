# Gate 11 Spy Combo readiness v1

Status: seed-free readiness only. No Gate 11 gameplay namespace is authorized.

## Frozen opponent

Spy Combo — Drinkme
MTGO Pauper Challenge 32 #12854119
Top 16, 4-2
Event date: 2026-09-16
47 players

Frozen exact 75:
`industrial-waste/gauntlet/spy-combo-drinkme-2026-09-16.dck`

Deck-file SHA-256:
`d01a41caed140d361fb5bb1a8d5b224f27161a7e4732efc202636029a7a6de2f`

Public sources:
- https://mtgtop8.com/event?d=890539&e=90924&f=PAU
- https://mtgdecks.net/Pauper/mtgo-pauper-challenge-32-12854119-tournament-270270

## Selection rationale

Gate 10 closed Elves sampling without replication or promotion. The current gauntlet already covers
burn/madness pressure, Boros aggro, Affinity, Mono-Blue Terror, Jund Wildfire, Monster Tron, and
Elves. It does not yet contain a true fast-combo opponent whose primary plan is to convert mana
development into a one-turn graveyard kill rather than win through normal combat.

Spy Combo is materially represented in the current Pauper field and recent public results. Drinkme's
4-2 MTGO Challenge list is used because it is recent, successful, fully published, and preserves the
current four-land Balustrade Spy core. Selection is a strategic-coverage decision, not a claim that
this exact 75 is the strongest possible Spy build.

The frozen maindeck's characteristic pressure is:
- defender-based burst mana from Overgrown Battlement, Wall of Roots, and Saruli Caretaker;
- low-land resource management through Land Grant, landcycling, Gatecreeper Vine, and Quirion Ranger;
- creature-density refill from Winding Way and Lead the Stampede;
- Balustrade Spy self-mill after lands are removed from the library;
- Dread Return flashback into Lotleth Giant as the graveyard conversion kill;
- Mesmeric Fiend as proactive hand disruption.

## Source-tree implementation audit

At Gate 11 selection, six distinct maindeck definitions are absent from the live lab tree:

1. Balustrade Spy
2. Gatecreeper Vine
3. Lotleth Giant
4. Mesmeric Fiend
5. Overgrown Battlement
6. Wall of Roots

The following important Spy cards are already present and must not be reimplemented merely for this
gate: Dread Return, Land Grant, Lead the Stampede, Winding Way, Masked Vandal, Nyxborn Hydra,
Quirion Ranger, Saruli Caretaker, Generous Ent, Sagu Wildling, Troll of Khazad-dûm, and Elves of
Deep Shadow.

## Admission requirements

No official Gate 11 gameplay seed namespace may be reserved, generated, exposed, or executed until
all requirements below pass.

1. Implement all six missing maindeck cards with faithful rules semantics and focused deterministic
   scenario tests.
2. Validate the exact Spy Combo 60/15 identity and frozen deck SHA.
3. Confirm every maindeck card resolves through the actual registry with no placeholder or
   substitution.
4. Re-qualify the already-present combo-critical capabilities that Gate 11 depends on rather than
   assuming filename presence is sufficient, especially:
   - Dread Return's flashback sacrifice-three-creatures cost;
   - Land Grant's landless-hand alternative cost and full-hand reveal;
   - Winding Way and Lead the Stampede refill behavior;
   - Nyxborn Hydra's bestow/X behavior where relevant to resource development.
5. Build and qualify Spy-specific opponent-policy fixtures covering at minimum:
   - Land Grant, landcycling, Gatecreeper Vine, and Quirion Ranger low-land sequencing;
   - Wall of Roots once-per-turn counter-for-mana timing;
   - Overgrown Battlement defender-count mana scaling;
   - Saruli Caretaker mana development;
   - Winding Way / Lead the Stampede refill choices;
   - Mesmeric Fiend hand-disruption targeting and linked return;
   - Balustrade Spy self-target timing and mill-to-land behavior;
   - Dread Return flashback sacrifice selection;
   - Lotleth Giant graveyard-count lethal sequencing.
6. Run one deterministic exact-deck readiness smoke requiring legal progression, zero exceptions,
   and zero illegal actions.
7. Only after every readiness requirement is green may a fresh non-overlapping Gate 11 pilot
   namespace be frozen.
8. Before generating that namespace, predeclare exactly which Industrial identities participate and
   preserve paired play/draw discipline. Expected identities remain frozen Control + Pactdoll-A
   unless existing experimental rules formally justify something else.
9. No postboard work, promotion, deck modification, rerolls, seed replacement, or outcome-dependent
   protocol edits are authorized unless a frozen evidence gate explicitly permits them.

## Experimental identities

Industrial Waste v1.0 Submitted Control remains immutable.

Pactdoll-A remains the only surviving challenger and remains unpromoted. Gate 10 supplied no
replication trigger and no card-change authority.

Fail closed whenever a card capability, policy choice, deck identity, registry resolution, or
execution invariant is uncertain.
