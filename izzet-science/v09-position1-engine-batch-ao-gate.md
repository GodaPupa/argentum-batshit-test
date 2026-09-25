# v0.9 Position 1 — Engine Coverage Batch AO Gate

This prospective seed-free successor qualifies the two remaining damage/prevention identities
Ram Through and Prismatic Strands after the complete AN artifact is independently accepted. It is
one coherent capability batch, with shared engine changes separated from the two exact card commits.
The gate authorizes no sampled game, official seed retrieval, deck change or pilot promotion.

## Frozen boundaries

The v0.7 control remains SHA-256
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`; the Veteran opponent file remains
`c3ab6ee8e37a78219e7bbb7c6a75c634d78c1eb4965ab0db3abd5d853398b0be`.
Official counters remain **12 generated, 0 consumed, 0 initialized, 0 completed/12, 0 outcome
exposure/12**. Position 1 retains its conditional authorization; positions 2–12 are unauthorized.
KEEP_V07 / NO CARD CHANGES. PR 15 stays draft and unmerged.

## Primary rules and exact card identities

- [Ram Through, IKO #170](https://scryfall.com/card/iko/170/ram-through): {1}{G}, common instant;
  the controlled creature is the damage source, power and trample are read at resolution, the
  opposing creature is the other target, and an illegal target prevents all of this damage.
- [Prismatic Strands, JUD #18](https://scryfall.com/card/jud/18/prismatic-strands): {2}{W}, common
  instant; a resolution-time color choice prevents that color's sources from dealing damage this
  turn. Flashback taps one untapped white creature the caster controls, with no mana payment.

Oracle records, first-printing metadata and published rulings were retrieved before qualification.
The official Comprehensive Rules effective September 25, 2026 were checked through
[Wizards' rules page](https://magic.wizards.com/en/rules). The retrieved text digest is
`8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca`.
CR 120.4a determines an excess-damage split first, and CR 614.15 places a spell's self-replacement
before ordinary replacement/prevention. Prevention and flashback remain subject to their existing
rule paths; this qualification does not replace the program's eventual full rules/provenance gate.

## Shared capability scope

DamageUtils freezes the ordinary creature's raw lethal threshold and controller before other damage
replacement or prevention. The creature and controller portions each enter the existing damage path
once, with no state-based-action or trigger processing between them. The sum of emitted damage,
lifelink and source/recipient tracking reflects actual damage, without counting the controller
portion a second time as creature damage. Prior marked damage and deathtouch determine the raw
threshold; ordinary doubling and prevention then modify the already split portions.

The source-only `AllDamage` / `FromTarget` / `FromGroup` shape now stores a distinct all-damage
modifier with its chosen color. Damage-time evaluation uses current projected source characteristics,
with the existing base-card fallback for nonbattlefield spell sources. Matching is independent of
which player controls either source or recipient. Combat and noncombat damage consult the same
predicate, unpreventable damage retains its existing exception, and end-of-turn expiry stays on the
existing duration rail. The serialized modifier preserves the resolution-time choice after the
creating spell has left the stack. Both players can see the resulting public shield badge.

CombatOnly and legacy recipient-scoped FromGroup forms retain their existing representations. The
latter need a separate qualification; this batch does not turn a targeted shield into a global one.
The exact accepted `SerializationTestSupport.roundTrip` helper from Manual ancestor
`94b34d5c51cece560119e580c75d673286ce0fca` is reused without importing any gameplay evidence or adding a
dependency. Its integration is exercised again in the exact Prismatic Strands scenarios.

The frozen pair contains no planeswalker or battle permanent and no effect that turns a permanent
into either card type. Creature/planeswalker/battle overlap and its greatest-excess rule are therefore
outside this admitted card scope, not generally qualified by the ordinary-creature repair. The pair
also contains no source color-changing effect followed by damage from a departed object. Arbitrary
last-known color changes, optional replacement ordering outside these fixtures, and general
recipient-scoped source groups remain unqualified broader cases. Full-game readiness is not inferred
from registry resolution.

## Required deterministic qualification

- Eight excess-damage regressions cover ordinary, previously damaged, deathtouch and wither sources;
  event conservation, source and per-player recipient tracking, lifelink, the no-excess baseline,
  protection, a finite prevention shield, and source doubling after the split.
- Fourteen source-group regressions cover combat/noncombat damage to players, creatures,
  planeswalkers and battles; either controller, multicolor and spell sources, newly entering sources,
  current color replacing printed color, full-state serialization, explicit/global unpreventability,
  public shield badges and the unchanged combat-only representation.
- Seven exact Ram Through scenarios cover source attribution and total damage with/without trample,
  prior damage, response-time power/trample, either target becoming illegal and legal target groups.
- Eight exact Prismatic Strands scenarios cover actual spell damage to both players and creatures,
  unchosen colors, actual combat with/without prevention disabled, legal fresh-creature flashback,
  atomic rejection of illegal payments, persistence across the color choice, exile after resolution
  or countering, and expiry at the next turn.
- Existing floating-effect serialization, combat shield badges, Gandalf's Sanction, excess-trigger,
  recipient-shield, player-protection, damage-ledger and scoped PDH regressions must still pass.
- Expected post-implementation unresolved count is exactly **7**, all Veteran: Benevolent Blessing,
  Cho-Manno's Blessing, Forge of Heroes, Nyxborn Hydra, Opal Palace, Snake Umbra and Vines of Vastwood.
  All five existing full-game readiness guards stay closed, and a sixth explicit simultaneous-
  damage grouping guard is added. Zero unresolved Izzet registry identities is unchanged.
- Snapshot generation may add only Ram Through to IKO.json and Prismatic Strands to JUD.json; every
  existing record must remain identical. Every canonical snapshot is then checked with updating
  disabled.

## Artifact and guarded integration

The artifact contains exactly **19 files**: a manifest and eighteen transcripts (flashback counters,
Memory Lapse, Remand, excess, prevention,
serialization, badges, Ram, Strands, Gandalf, excess triggers, recipient shields, protection, damage
ledger, PDH, readiness, snapshot generation and snapshot verification). The manifest has **53
SHA-256 bindings** for thirty-five repository files and those eighteen transcripts, plus source SHA,
snapshot commit/tree, exact implemented names, unresolved counts, scoped capability and zero official
counters.

The workflow checks out the triggering SHA, verifies the preceding acceptance and frozen files,
executes all qualification, checks the exact snapshot delta, then freshly fetches the live branch and
requires it to equal the trigger SHA before a nonforce snapshot push. A concurrent change fails
closed. Independent archive/content/commit audit is required before accepting registry coverage **9 → 7**; a green
workflow alone is not acceptance or sampled gameplay. No earlier failed evidence is overwritten.

## Explicit unresolved simultaneous-damage interaction

Independent review identified a concrete frozen-pair interaction prerequisite before this source
was published. `TriggerDetector.detectTriggers` iterates per-recipient DamageDealtEvents;
`AttachmentTriggerDetector.detectAttachmentTriggers` creates a PendingTrigger for each matching
event. Spirit Link and Armadillo Cloak use ATTACHED DealsDamage triggers without batch grouping.
The split Ram Through instruction therefore currently supplies two trigger contexts (2 and 3 in
the distinguishing five-power-versus-2/2 case), rather than one simultaneous same-source total of 5.
The recursive noncombat lifelink path likewise emits separate life-gain events despite the correct
total. CR 603.2c, 119.9–10 and 702.15b/e govern the grouping requirement.

This is a source-observed defect, not an executed failing-game artifact, and is not reclassified as
successful card interaction. The thirty-seven new deterministic component scenarios do not qualify
this interaction. The readiness function adds the explicit blocking prerequisite
`Simultaneous same-source damage/lifelink and attached-trigger grouping not qualified`, preserving
all five earlier guards. The artifact records six closed guards, registry-only acceptance and
`simultaneous_damage_interaction_qualified=false`. AO can decrease the unresolved registry count
under the existing identity criterion after audit, but it does not accept complete Ram Through /
Spirit Link / Armadillo Cloak interaction or admit exact-pair gameplay. A prospective canonical
event-grouping implementation and distinguishing regressions are required before that guard opens.

## First validation diagnostic and fixture compilation repair

Run [36091364965](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36091364965), job
`107934310632`, failed at source `3535f2c391045b00eba3eaca9e892df65ba13955` during
`:rules-engine:compileTestKotlin`. The public-badge fixture called String.contains on the nullable
ClientPlayerEffect.description field. The fixture now explicitly requires that description to be
present, then retains both expected text checks. Production source, expected behavior, frozen files
and all six readiness guards are unchanged. No component scenarios, coverage scan, snapshot changes
or official games executed in the failed gate; downstream stages and artifact upload were skipped.
The failed job/log remains a diagnostic record. Complete successful validation and independent
archive audit are still required before accepting any AO registry reduction.

## Second validation diagnostic and exact Ram condition repair

Run [36092645417](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36092645417), job
`107938118410`, passed the excess-damage, source-group prevention, floating-effect serialization and
combat-badge qualification classes at source `42be09647a6a000b3a0615d25291a979c182f17c`.
The seven Ram Through scenarios then ran; three trample scenarios failed because all damage stayed
on the creature. The other four scenarios passed. Prismatic Strands and subsequent gates were
skipped, and no artifact was uploaded.

The exact card condition incorrectly supplied a named BoundVariable to EntityMatches. The existing
SDK and ConditionEvaluator support a positional ContextTarget for this condition and deliberately
reject unsupported entity roles. Ram Through now uses the existing TargetMatchesFilter facade at
index zero, its first declared controlled-creature target. Damage attribution retains the named
source binding, and power still reads that first target at resolution. No engine primitive, test
expectation, legality requirement or readiness guard changes. This is a card-definition repair,
not a waiver of the three failed scenarios; the full gate and archive audit remain mandatory.

## Third validation diagnostic and counter-target fixture repair

Run [36093845097](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36093845097), job
`107941756467`, passed the shared capability classes and all seven Ram Through scenarios at source
`8b5164727eb138d2ff6cb4983b6f8bde6aa02e84`. Seven of eight Prismatic Strands scenarios also passed.
The countering scenario stopped at its Counterspell cast assertion: GameTestDriver.castSpell
intentionally treats nonplayer convenience targets as permanents, so that helper supplied the wrong
ChosenTarget variant for a spell on the stack. The fixture now uses the existing castSpellWithTargets
helper with ChosenTarget.Spell(strands). Its successful counter, exile, absence-of-choice/shield and
subsequent unprevented-damage assertions are retained. No engine/card behavior or readiness guard
changes. Later stages and artifact upload were skipped; the failed job/log is retained. Acceptance
still requires the complete successful gate and independent artifact audit.


## Fourth validation diagnostic and paid-flashback counter repair

Run [36094670750](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36094670750), job
`107944297814`, passed the shared capability classes, all seven Ram Through scenarios and seven of
eight Prismatic Strands scenarios at source `9b60fdcf8a3111d658ef8525545a223c78c6a279`. The typed
Counterspell cast and resolution succeeded, exposing the preserved assertion that a countered
flashback spell must be exiled. The spell instead followed the ordinary graveyard counter path.
No successful artifact was produced; the failed log remains evidence of this engine defect.

The canonical repair is limited to StackResolver's graveyard, hand and library-top counter exits.
A shared destination helper reads the existing serialized stack provenance: graveyard origin and
explicitly paid AlternativeCostType.FLASHBACK. CR 702.34a then requires exile instead of those other
counter destinations. Merely having printed flashback or coming from a graveyard is insufficient.
The helper runs after the existing cannot-be-countered checks and retains the existing destination
riders and zone redirects for other casts. No casting API/schema, counter-to-exile behavior, normal
resolution/fizzle inference or general stack-exit behavior is qualified by this narrow repair.

Ten new deterministic counter scenarios cover the three destinations with actual paid flashback
casts and ordinary hand casts of the same printed-flashback card, uncounterability, serialized
state equivalence, and explicit null/ESCAPE counter-input negative probes. The latter exercise only
the counter's provenance decision and do not claim qualification of another graveyard-cast route.
Existing Memory Lapse and Remand scenarios join the required regression set. The original thirty-
seven AO component scenarios and all their assertions remain intact. The expanded artifact contract
above binds the added source, tests and three transcripts. All six full-game guards remain closed,
including simultaneous-damage grouping; a passing counter repair does not open exact-pair gameplay.
