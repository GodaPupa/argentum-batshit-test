# Gate 8 Jund Wildfire readiness v1

Status: frozen selection and seed-free readiness gate; no gameplay namespace reserved.

## Opponent identity

- Jund Wildfire — manohito, Top 4 (4-3), MTGO Pauper Challenge 16 #12854110.
- Event date: 2026-09-15; 27 players.
- Exact 60/15 preserved in `gauntlet/jund-wildfire-manohito-2026-09-15.dck`.
- Deck-file SHA-256: `b3c5722946b3e32adc0c716a7b08a0688f2ab62b0f6bb87d9d0faec12c09cc29`.
- Sources:
  - https://decksnipe.com/archetype/pauper/jund-wildfire
  - https://mtgdecks.net/Pauper/mtgo-pauper-challenge-16-12854110-tournament-270190

## Why this opponent is next

Gate 7 closed Mono-Blue sampling with no replication or promotion. Jund Wildfire remains a current,
meaningful metagame deck and supplies a new resource-denial/artifact-value/sacrifice axis. The exact
representative 60 is also cheaper to qualify than the audited current Monster Tron representative.

Current branch maindeck implementation gap:

1. Writhing Chrysalis
2. Cleansing Wildfire
3. Twisted Landscape

All other maindeck names are already represented by current branch card definitions/basic lands.

## Admission gate

No official Gate 8 seed may be reserved or exposed until all of the following pass seed-free:

1. exact 60/15 count and deck-file SHA validation;
2. the three missing maindeck cards are implemented with focused deterministic scenarios;
3. every maindeck card resolves through the registry with no placeholder or substitute;
4. a Jund-specific opponent-policy fixture set passes, covering at minimum:
   - Cleansing Wildfire target/use sequencing;
   - sacrifice-value sequencing around Familiar/Wellspring/Lembas and draw spells;
   - Writhing Chrysalis pressure/token utilization;
   - interaction target selection for Cast Down/Galvanic Blast/Munitions;
5. one deterministic exact-deck readiness smoke completes with legal progression, zero exceptions,
   and zero illegal actions.

Only after those requirements pass may a fresh, non-overlapping pilot namespace be frozen. The
frozen Industrial Waste v1.0 Control remains unchanged. Pactdoll-A remains unpromoted; any inclusion
in a Gate 8 pilot must be predeclared before seed generation.
