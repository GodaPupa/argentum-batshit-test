# Pest Control: Spy support batch B

## Accepted input and scope

PR #145 accepted batch A at source `d0705a90f30241f7b6509669ab9e952fd558c051`, merged as
`fc975805ef1373ebe6832e1acd312ef7e8d47f72`. Its independently audited artifact **10841209800**
from run **36081122745** has ZIP SHA-256
`84c8169f318805acd73cd0daa594375beb6a0bd0dfde44bc02d681d2afb02421` and exactly five unresolved
main-deck identities: Nyxborn Hydra, Mesmeric Fiend, Wall of Roots, Land Grant and Winding Way.
Its five per-card scenarios and strict affected-set snapshots passed CI **36081122753**. Those
accepted artifact bytes remain historical evidence; this batch does not rewrite them.

Batch B addresses four of those remaining identities in one coherent seed-free gate: **Land Grant,
Winding Way, Mesmeric Fiend and Wall of Roots**. Their definitions and reusable primitives come
from accepted Industrial source `abfd806f6332c0da311e40b01c9d85eeb0962165`, with independent Pest
qualification. No Industrial matchup results, pilots, old control, CI waiver or blanket source
snapshot is imported.

Nyxborn Hydra is deliberately excluded before this batch's results. Inspection found that the
current Pest SDK and engine lack the complete Bestow capability; its source definition cannot be
admitted merely because it imports known counter and stat primitives. Full Bestow construction,
X-cost preservation, Aura targeting, protection, illegal-target resolution and detachment behavior
require a separate canonical capability batch. No substitute behavior is accepted.

## Mechanics and fault isolation

Land Grant receives its exact optional alternate cost: the player must have no land card in their
own hand and reveal that hand rather than pay the mana cost. The hand revelation is an actual cost
with an event record, not a pilot shortcut. Its Forest search keeps the existing search/reveal/shuffle
composition. Winding Way receives the selected-card-type filter pipeline so the chosen creature or
land type determines which revealed cards enter hand and which go to graveyard.

A separate three-case predicate suite verifies library choices, invalid/unbound names, and real
Layer.TYPE battlefield changes through projected characteristics. This closes a generic printed-type
assumption discovered in the inherited helper.

The canonical shared backport is restricted to these missing conditions, cost/payment/legal-action
paths and chosen-type filtering. Unrelated Industrial changes, especially reversions to accepted
Pest Cascade or Prototype support, are excluded. Each card has a dedicated scenario class. Mesmeric
Fiend verifies linked exile/return and the leaves-before-enter-trigger ordering; Wall of Roots
verifies the real toughness cost, once-per-turn restriction, and resetting on the opponent's turn.

Current Oracle text, first-printing metadata and Pauper legalities are independently sourced.
Snapshots add only these four new card blocks. Existing card snapshots remain unchanged. The
standard CI snapshot comparison runs without update or blessing flags.

## Required inventory after acceptance

The frozen Spy 60/15 identities remain governed by `tier-one-spy-combo-source-freeze.md`. The new
strict queue test requires exactly **Nyxborn Hydra ×2** unresolved in the main deck. Remaining
sideboard identities are Jack-o'-Lantern ×1, Nyxborn Hydra ×1, Flaring Pain ×1, Faerie Macabre ×2,
Acorn Harvest ×1 and Nylea's Disciple ×4.

Batch A's current-code regression now enforces monotonic coverage: all five A cards must still
resolve and no unresolved identity outside its historically accepted remaining five may appear.
The new B test enforces the exact one-identity main queue. This prospectively accepts additional
support without treating a historical gap count as a permanent requirement.

The validation workflow checks the exact PR source head, runs all four separate card suites plus
both support gates and frozen admission, and preserves each stage's XML before a later Gradle
invocation can replace it. A green registry inventory does not qualify the exact opponent pilot,
initialize games, freeze seeds, or authorize Spy gameplay. The only subsequent main-card capability
is Nyxborn Hydra/Bestow; policy, interaction, runner and postboard qualification remain distinct
required stages of the existing bounded protocol. Monster Tron keeps its independent frozen source
and evidence.

## Qualification repair before acceptance

The first CI at `f32c6e563d4a3c68df3bb2814b683cde29e2f1d9` correctly rejected an omitted
`RevealHand` representative in the exhaustive cost serialization test. The representative now
round-trips through every existing wrapper; the exhaustive assertion is unchanged. The new Fiend
ordering fixture also assumed that the only legal opponent always produces a target prompt. It
now accepts the engine's automatic sole-target selection and additionally requires the enter
trigger to remain on the stack, with the victim still in hand, before the intervening removal.
The actual linked-trigger ordering and exile/return assertions remain required. The dedicated
workflow preserves the cost serialization suite alongside the other eight stages. No official
evidence or accepted result was affected.
