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
