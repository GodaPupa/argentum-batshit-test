# Gate 10 Elves readiness v1

Status: seed-free readiness only. No Gate 10 gameplay namespace is authorized.

## Frozen opponent

Elves — Mogged
MTGO Pauper Challenge 16 #12854501
Top 4, 5-2
Event date: 2026-09-19
28 players

Frozen exact 75:
`industrial-waste/gauntlet/elves-mogged-2026-09-19.dck`

Deck-file SHA-256:
`01f63d291f90fdd6956a37b5ec9bc6b411bff4d23ea87e4ff96c69be89c19cf6`

## Selection rationale

Elves is a current, materially represented Pauper archetype and adds a strategic axis not covered by
the existing Industrial Waste gauntlet: explosive creature-mana scaling, go-wide development,
Timberwatch burst, initiative pressure, and creature-density refill.

Mogged's 5-2 Top 4 list is used as the representative exact 75 because it is recent, successful,
publicly sourced, and close to the current archetype core. Selection is a coverage/cost decision,
not a claim that this exact list is the strongest Elves build.

A current source-tree audit finds six absent distinct maindeck definitions:

1. Masked Vandal
2. Avenging Hunter
3. Land Grant
4. Winding Way
5. Lead the Stampede
6. Gingerbread Cabin

Nyxborn Hydra is already implemented and qualified from Gate 9. Do not redo its Bestow work.

## Admission requirements

No official Gate 10 gameplay seed namespace may be reserved, generated, exposed, or executed until
all requirements below pass.

1. Implement all six missing maindeck cards with faithful rules semantics and focused deterministic
   scenario tests.
2. Validate the exact Elves 60/15 identity and frozen deck SHA.
3. Confirm all 60 maindeck cards resolve through the actual registry with no placeholders or
   substitutions.
4. Build and qualify Elves-specific opponent-policy fixtures covering at minimum:
   - Quirion Ranger land-return/untap resource sequencing;
   - Priest of Titania mana scaling and mana-development decisions;
   - Winding Way and Lead the Stampede creature-refill choices;
   - Masked Vandal exile timing and its graveyard-card additional cost;
   - Timberwatch Elf attack/pump timing;
   - Avenging Hunter initiative/pressure development;
   - Land Grant reveal/search sequencing and low-land resource decisions;
   - Gingerbread Cabin tapped-versus-untapped entry condition.
5. Run one deterministic exact-deck readiness smoke requiring legal progression, zero exceptions,
   and zero illegal actions.
6. Only after every readiness requirement is green may a fresh non-overlapping Gate 10 pilot
   namespace be frozen.
7. Before generating that namespace, predeclare exactly which Industrial identities participate and
   preserve paired play/draw discipline.
8. No postboard work, promotion, deck modification, rerolls, seed replacement, or outcome-dependent
   protocol edits are authorized unless the frozen evidence gate explicitly justifies them.

## Experimental identities

The immutable Industrial Waste v1.0 Submitted Control remains the baseline.

Pactdoll-A remains the only surviving challenger and remains unpromoted. Gate 9 supplied no
replication trigger and no card-change authority.

Fail closed whenever a card capability, policy choice, deck identity, registry resolution, or
execution invariant is uncertain.
