# R0 candidate construction and exact freeze

These are the three candidates admitted by `protocol-v2-r0.json`. They were
constructed before any v2 comparative ordering was executed. None is promoted.
The submitted v1.0 comparator and its completed conclusion remain immutable.

## COMPACT_LOOP

This family retains twelve Tron lands but concentrates the nonland slots on
finding and repeating the Retriever/Altar loop. Four Altars, four Retrievers,
two Kinsmiths and two Blood Fountains supply access and recovery. Four Ichor
Wellsprings and four Chromatic Stars turn sacrifice costs into replacement
cards. Two Pactdoll Terrors and two Golem Foundries diversify conversion while
reducing the four-mana payoff burden. Eighteen lands and four Maps keep the
Tron ceiling, with four Forests to support early green setup.

The risks to test are redundant loop pieces, black-mana access, and whether
the remaining Tron infrastructure still delays meaningful engine development.

## RECURSIVE_EGGS

This family removes the Tron and Map/Rotation package. Twenty lands include
twelve colored artifact lands; ordinary land development can feed sacrifice
draw and Pactdoll triggers. Four Fountains and three Dross Skullbombs support
recursion after milling or sacrificing creatures. The engine uses four
Wellsprings, four Stars, four Trails, four Insights and four Rumbles.

This is the strongest direct test of the engine-first hypothesis. Losing
Tron's mana ceiling may make repeated recursion or four-mana payoffs too slow;
artifact lands add vulnerability that a goldfish cannot measure. These are
prospective risks, not observed results.

## LEAN_TRON_HYBRID

This family retains twelve Tron lands and increases to twenty total lands.
Four Maps, four Rotations, four Rumbles and four Ancient Stirrings prioritize
assembly and access to colorless engine pieces. Four Stars and four Wellsprings
replace slower setup slots. Three Altars, four Retrievers, two Kinsmiths, two
Pactdolls and one Foundry are the retained conversion package.

The risks to test are colored setup costs, overinvestment in tutors and lands,
and failure to convert assembled mana into a completed loop.

## Common construction controls

- Each candidate is exactly 60/15 and differs from submitted v1.0 by at least
  eight maindeck slots. The exact count manifests and byte digests are in
  `r0-freeze.json`.
- All three sideboards reproduce the submitted fifteen cards. This controls
  the initial comparison; it does not claim they are optimized for the new
  mana bases. No sideboarding policy or postboard gameplay is admitted here.
- Current Pauper legality is audited against archived card responses in
  `sources/` and the current Wizards format and banned-list pages identified
  in `legality-source-audit.json`.
- Chromatic Star is selected because its graveyard trigger also rewards
  sacrifice to another card. Its missing implementation is a capability task,
  not a reason to replace it with a less suitable card to obtain a green audit.
- Source presence, including generated card definitions, is not behavioral
  qualification. `support-audit.json` states the remaining runtime gates.
- The historical Python structural model is not an authorized R1 executor.
  R1 requires real legal actions, payments, triggers, replay and event-derived
  telemetry under `protocol-v2-r1.json`.
