# Contemporary gauntlet provenance

Research date: 2026-09-20.

The first pilot opponent is Davide Canevazzi's Madness Burn list from the 99-player 43rd Super
Ingenio in Barcelona on 2026-09-12. It finished in the Top 8. The exact published 60/15 is preserved
in `madness-burn-canevazzi-2026-09-12.dck` from:

- https://www.mtgtop8.com/event?d=889937&e=90850&f=PAU
- Event index and field size: https://www.mtgtop8.com/event?e=90850&f=PAU

This list is selected for the minimal pilot because it is recent, successful, represents a fast
clock, and its complete maindeck is already implemented in Argentum. Selection is an infrastructure
and pressure-coverage decision, not a claim that it is the format's single best deck.

The same event's runner-up Mono-Blue Terror and Top 8 Grixis Affinity are reserved candidates for
the interaction and artifact axes. They are not yet admitted because each requires several missing
card implementations; spending that work before the one-opponent capability pilot would violate the
project's staged cost gate.

The post-pilot feasibility audit counted seven currently absent unique maindeck cards in Joan
Rubies's runner-up Mono-Blue Terror list: Murmuring Mystic, Artful Dodge, Deem Inferior, Deep
Analysis, Force Spike, Sleep of the Dead, and Thought Scour. That makes it strategically attractive
but not the next cost-efficient executable opponent. No substitute list has been admitted.

## Second opponent selection

Research updated 2026-09-21 after the production-profile Burn screen and replication closed Burn
sampling. Marco Garrido Aboy's Top 8 Boros Aggro list from the same event is preserved exactly in
`boros-aggro-garrido-aboy-2026-09-12.dck` from:

- https://www.mtgtop8.com/event?d=889938&e=90850&f=PAU
- Event index and field size: https://www.mtgtop8.com/event?e=90850&f=PAU

An exact maindeck corpus audit found four absent distinct cards, fewer than every other non-Burn
candidate in the event's published Top 16. Boros also adds a distinct recursive go-wide and
prevention axis. It is therefore selected for the next readiness gate, not admitted to the executable
gauntlet yet. `protocols/gate-5-boros-aggro-readiness-v1.md` records the comparison and the no-seed
admission criteria.

## Third opponent selection

Research updated 2026-09-21 after the valid Boros capability pilot closed without replication.
Carlos Dc's Top 8 Grixis Affinity list from the same event is preserved exactly in
`grixis-affinity-carlos-dc-2026-09-12.dck` from:

- https://www.mtgtop8.com/event?d=889936&e=90850&f=PAU
- Event index and field size: https://www.mtgtop8.com/event?e=90850&f=PAU

Grixis Affinity, UR Control, and Mono-Black Aggro tied at six absent distinct maindeck cards in the
Gate 5 audit. Grixis wins the cost/coverage tie because its missing package maps more narrowly to
existing primitives, it has the stronger finish than Mono-Black Aggro, and its artifact-affinity,
sacrifice, discard, reach, and graveyard-interaction plan adds more direct coverage of Industrial
Waste's resilience than another speed opponent. It is selected for readiness only; no experimental
seed or gameplay run is authorized.


## Fourth opponent selection

Research updated 2026-09-22 after Gate 7 Mono-Blue Terror closed validly without replication.
Current Pauper metagame evidence keeps Jund Wildfire among the major competitive archetypes: recent
MTGO data places it at roughly five percent of the field, while DeckSnipe's recent published-list
window shows a 4.3% share and a 51.9% match win rate.

The representative list is manohito's Top 4 Jund Wildfire finish from MTGO Pauper Challenge 16
#12854110 on 2026-09-15 (27 players), preserved exactly in
`jund-wildfire-manohito-2026-09-15.dck`.

Sources:
- https://decksnipe.com/archetype/pauper/jund-wildfire
- https://mtgdecks.net/Pauper/mtgo-pauper-challenge-16-12854110-tournament-270190

This opponent is selected ahead of Monster Tron for the next readiness gate because it adds a
distinct Cleansing-Wildfire/resource-denial, artifact-value, sacrifice, and midrange axis while
requiring only three absent maindeck definitions on the current Industrial Waste branch:
Writhing Chrysalis, Cleansing Wildfire, and Twisted Landscape. The audited representative Monster
Tron list requires five absent maindeck definitions. Selection is therefore a cost/coverage decision,
not a claim that Jund Wildfire is stronger than Monster Tron.

No Gate 8 gameplay seed namespace is authorized until exact 60/15 identity, all maindeck card
implementations, opponent-policy fixtures, and a deterministic exact-deck readiness smoke pass.


## Fifth opponent selection

Research updated 2026-09-22 after Gate 8 Jund Wildfire closed validly without replication.
Monster Tron remains an active contemporary Pauper archetype, and PinoIo_Cosmico's 7-1 runner-up
finish in the 27-player MTGO Pauper Challenge 16 #12854110 on 2026-09-15 provides a recent,
high-finish exact 75 with public list provenance.

The representative list is frozen exactly in
`monster-tron-pinoio-cosmico-2026-09-15.dck`.

Sources:
- https://decksnipe.com/player/pinoio_cosmico
- https://mtgdecks.net/Pauper/mtgo-pauper-challenge-16-12854110-tournament-270190

The branch audit now finds only four absent maindeck definitions: Nyxborn Hydra, Pulse of Murasa,
Bonder's Ornament, and Bojuka Bog. The earlier five-card estimate included Writhing Chrysalis, which
was implemented and qualified during Gate 8. Monster Tron therefore becomes the next cost-efficient
coverage gate, adding a large-mana mirror/resource-race axis distinct from the Burn, Boros, Grixis,
Mono-Blue, and Jund opponents already sampled.

No Gate 9 gameplay seed namespace is authorized until exact 60/15 identity, all four maindeck card
implementations, Monster-Tron-specific opponent-policy fixtures, and a deterministic exact-deck
readiness smoke all pass.


## Sixth opponent selection

Research updated 2026-09-23 after Gate 9 Monster Tron closed validly without replication.

Elves remains a materially represented contemporary Pauper archetype. Current MTGO metagame
tracking places it around the mid-single-digit share range, and recent Challenge results continue to
put Elves into Top 8s. The matchup adds a strategic axis not yet covered by the Industrial Waste
gauntlet: creature-mana scaling, go-wide development, Timberwatch burst, initiative pressure, and
high-density creature refill.

The representative exact 75 is Mogged's 5-2 Top 4 finish from MTGO Pauper Challenge 16 #12854501
on 2026-09-19 (28 players), preserved in `elves-mogged-2026-09-19.dck`.

Sources:
- https://decksnipe.com/archetype/pauper/mono-green-elves?lists=40
- https://mtgdecks.net/Pauper/mtgo-pauper-challenge-16-12854501-tournament-270509

Deck-file SHA-256:
`01f63d291f90fdd6956a37b5ec9bc6b411bff4d23ea87e4ff96c69be89c19cf6`.

The current branch source-tree audit identifies six absent distinct maindeck definitions: Masked
Vandal, Avenging Hunter, Land Grant, Winding Way, Lead the Stampede, and Gingerbread Cabin.
Nyxborn Hydra is already qualified from Gate 9.

Elves is selected for Gate 10 readiness because it adds the first dedicated creature-swarm/mana-engine
coverage axis while keeping the implementation gap bounded. No Gate 10 gameplay seed namespace is
authorized until exact identity, all six card capabilities, Elves-specific policy fixtures, registry
completeness, and a deterministic exact-deck readiness smoke all pass.
