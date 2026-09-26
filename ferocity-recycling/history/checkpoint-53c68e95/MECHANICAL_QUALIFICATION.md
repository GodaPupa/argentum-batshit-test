# Mechanical qualification

Status: **baseline executed; gameplay admission blocked by three reproducible failures**.

## Accepted baseline execution

| Stable-source class | Executed | Passed | Failed | Errors / skips | Receipt directory |
|---|---:|---:|---:|---:|---|
| Ferocity of the Hunt | 24 | 22 | 2 | 0 / 0 | `evidence/build/ferocity-stable-reproduction-01/` |
| Crypt Rats | 8 | 8 | 0 | 0 / 0 | `evidence/build/rats-baseline-01/` |
| Toxin Analysis | 9 | 8 | 1 | 0 / 0 | `evidence/build/toxin-baseline-01/` |
| **Distinct baseline scenarios** | **41** | **38** | **3** | **0 / 0** | |

Every accepted baseline invocation recorded unchanged compiled inputs. The earlier 24-case
Ferocity bootstrap changed while fixtures were still being authored; its source guard rejected
qualification and its full record is retained separately. There were 65 actual case executions,
not 65 distinct scenarios or independent gameplay experiments.

Both Ferocity failures incorrectly leave a Craw Wurm alive after queued damage from the departed
deathtouch Shaman, including when graveyard hate prevents its return. Toxin's failure gives its
controller 23 rather than the expected 24 life after queued damage from a departed lifelink
Shaman. The live-source gain/loss-of-deathtouch fixtures passed. These observations require a
departure-aware source-identity repair; an activation-time keyword snapshot would be incorrect.

The repair is isolated on `engine/ferocity-damage-source-lki`, with no shared-runtime change in
this baseline research checkpoint. Correct assertions remain unchanged. A repair must pass the
preserved failures and relevant generic regressions before this gate can be reconsidered.

## Scope and provenance

The project uses the real `ScenarioTestBase` / `ActionProcessor`, the existing SDK, and the card
registry from the source version recorded by the project build receipt. Ferocity and Crypt Rats
are registered only inside their test classes. Neither fixture admits a prerelease card to
`MtgSetCatalog`, alters a frozen project input, or creates a sanctioned Pauper result.

Ferocity's complete text comes from `sources/ferocity-of-the-hunt-canonical.json`, retrieved
2026-09-26. Its archived record gives common rarity, FRA #134, release 2026-10-02, and
`pauper: not_legal` at retrieval. The card has flash, costs `{1}{B/G}`, enchants a creature,
grants +1/+0 and deathtouch, and has an **Aura-owned** trigger returning the dead card tapped
under its owner's control. The fixture uses the existing attached-death trigger and
`PutOntoBattlefieldFromGraveyard(..., tapped = true)` facades. No new engine vocabulary or
shared-engine repair is included in this checkpoint.

Crypt Rats' text comes from `sources/crypt-rats-canonical.json`; common-printing eligibility
is separately audited in the rules source record. The fixture costs `{2}{B}`, is a 1/1 Rat,
and uses existing `{X}` activation plus `xManaRestriction = setOf(Color.BLACK)`, damage to
all creatures, and damage to each player.

The artificial zero-mana removal, prevention, and ability-suppression spells are clearly
named **Fixture** cards. They isolate timing and rules behavior and are not proposed deck
cards. Existing cards such as Shaman, Toxin Analysis, Village Rites, Visionary, and Rest in
Peace execute their actual registered definitions. Rest in Peace is a rules stress fixture,
not a Pauper deck-selection claim.

## Authored executable cases

`FerocityOfTheHuntScenarioTest` contains **24 deterministic cases** (20 declarations, four
two-case parameter loops). `CryptRatsScenarioTest` contains **8 deterministic cases**.
Both set a fixed fixture RNG state, execute no randomized matchup allocation, and consume
no development, evaluation, or confirmation game identifiers.

| Requirement | Fixture coverage | Execution status |
|---|---|---|
| Hybrid payment | Black and green payment succeed; red-only payment fails atomically | Passed |
| Flash and Aura targeting | Opponent-combat cast; missing target rejects; removed target fizzles; counterspell stops Aura | Passed |
| Attachment and continuous effects | Correct host, +1/+0, deathtouch; no triggered-ability grant on host | Passed |
| Trigger source and return | Stack source is Aura; creature returns tapped to owner, including borrowed creature | Passed |
| New object | Object reference changes; no haste, retained Aura, retained deathtouch, or retained power bonus; summoning sickness is present | Passed |
| Death and nondeath | Exile, bounce, token departure, and Rest in Peace replacement; Aura has its proper destination | Passed |
| Graveyard interaction | Creature exiled between death and trigger resolution cannot return | Passed |
| Ability ownership | Removing host abilities preserves Aura trigger; removing Aura abilities prevents it | Passed |
| Cost and trigger order | Village Rites sacrifice occurs atomically; return trigger resolves before draw; returned Visionary's entry trigger resolves before underlying Rites | Passed |
| Shaman symmetry | Own and opposing nonfliers take damage; fliers and player life totals are unaffected | Passed |
| Live damage source | Aura added after activation supplies deathtouch; Aura removed before damage removes deathtouch | Passed |
| Queued damage and source identity | Older activation kills returned creature; old source deathtouch persists after return or prevented return | **Two departed-source failures**; returned-object death check passed |
| Rats payment and damage | Only black pays X; creatures with flying, own creatures, and both players are damaged | Passed |
| Zero, prevention, indestructible | Zero damage does not apply deathtouch; prevented damage cannot kill by deathtouch; indestructible survives lethal damage | Passed |
| Ferocity life pressure | One-point Rats pulse reduces both players' life by one and grants no lifegain | Passed |
| Toxin comparison | One damage to three creatures and two players gives five lifegain; controller ends at 24, opponent at 19; Clue remains | Passed |
| Tapped returned creature | Tapped, summoning-sick Rats may legally activate its ability again because it has no tap symbol; new costs are paid | Passed |

Toxin Analysis has a separate one-card scenario file maintained by the comparison-package
reviewer. Its own baseline receipt contains eight passes and the one departed-source lifelink failure, so the complete package is not admitted. Tests of
Rats plus Toxin here qualify only that specific combined damage-and-lifelink sequence.

## Execution and replay

Run through the repository's machine-global build lock:

```bash
just test-class FerocityOfTheHuntScenarioTest
just test-class CryptRatsScenarioTest
just test-class ToxinAnalysisScenarioTest
```

The project build receipt records the source revision, JDK/Gradle identity, complete commands,
exit status, and preserved logs. A compiler error, dependency failure, unexpected decision,
nonempty stack, or action error is a failure. Fixtures assert errors and terminal stack state;
they do not silently interpret a stalled scenario as successful.

Each scenario is replayable by its stable test name. Replays used for debugging remain
deterministic debugging evidence; they are never fresh randomized games.

## Source-audit risks requiring executable resolution

Inspection found that the damage path reads live projected source keywords and spell-keyword
grants. `DealDamageExecutor` forwards the source entity ID, while `DamageUtils`' deathtouch
and lifelink reads do not visibly carry the ability's originating object reference. The
pending-source scenarios therefore explicitly check damage from a departed source and from
an older incarnation after the same entity ID returns. This was a source-inspection concern before execution and is now a **confirmed compatibility
defect** in the stable-source baseline above. The paired live-source tests
also prevent a simplistic activation-time snapshot from being accepted as a repair: a
still-present source must use its characteristics when it deals damage.

No card-specific workaround, frozen keyword shortcut, or changed expected result is permitted
to make those scenarios pass. A confirmed result-affecting failure must block interactive
admission, be preserved, and receive a scoped shared-engine repair plus regression evidence.

### Repair seam identified before baseline execution

**This baseline checkpoint contains no shared-engine repair.** The following source-inspection
notes preceded execution and explain the now-confirmed failing path:

1. `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/ZoneTransitionService.kt`
   creates the departure `EntitySnapshot` before battlefield cleanup. The snapshot contains
   projected keywords, controller, type information, and battlefield-entry timestamp. The
   service already stamps the first departure snapshot onto `pendingSpellCopies`; it does
   not visibly stamp it onto ordinary pending activated abilities. Its
   `LastKnownPermanentComponent` is removed on a later zone move, so that component alone
   cannot provide the original source after graveyard exile or a return to the battlefield.
2. `rules-engine/src/main/kotlin/com/wingedsheep/engine/state/components/stack/StackComponents.kt`
   already gives `ActivatedAbilityOnStackComponent` the fields `lastKnownSourceSnapshot`,
   `sourceBattlefieldTimestamp`, and `objectReferences`. In
   `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/ability/ActivateAbilityHandler.kt`,
   the snapshot is populated for a source sacrificed or exiled as its own activation cost.
   An ordinary Rats or Shaman activation does not pay that kind of cost.
3. A candidate departure fix could populate the existing snapshot on pending activated
   stack objects whose `objectReferences.origin` names the **exact departing object**,
   preserving the first such snapshot. Matching only an entity ID would permit the returned
   object's later death to overwrite the original source. Capturing only at activation
   would be wrong when a live source gains or loses Ferocity before dealing damage.
4. `rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt`
   already propagates these activated-ability fields to `EffectContext`.
   `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/ObjectReferenceEnvironment.kt`
   preserves `origin` separately from references that may follow authorized zone moves.
   The source view for damage must use the original object's live characteristics while
   that object remains present, and its departure snapshot after it leaves. It must never
   borrow the characteristics of a new object sharing the same entity ID.
5. `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/damage/DealDamageExecutor.kt`
   currently supplies `sourceId` to `DamageUtils.dealDamageToTarget` without an originating
   object view. Both Rats and Shaman use this executor: the SDK's `DealXDamage` facade
   produces an ordinary `DealDamageEffect` with `DynamicAmount.XValue`.
6. `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/DamageUtils.kt`
   has separate source reads for deathtouch, lifelink/controller, damage-source tracking,
   and prevention/replacement processing. Any repair must carry the same justified source
   view through those reads and through redirected damage. A keyword-only patch would not
   by itself establish protection, source-color, or source-controller fidelity.
   `rules-engine/src/main/kotlin/com/wingedsheep/engine/state/components/stack/EntitySnapshot.kt`
   currently has keywords/controller/type fields but no general projected-colors field;
   that is an explicit further design concern if the supported interaction needs it.

The preferred direction is a narrowly scoped, generic source-identity repair using the
existing context and snapshot path. Temporarily applying old keywords to the returned
permanent, globally weakening new-object guards, registering card-specific exceptions, or
changing a correct fixture expectation would create false gameplay and is not an acceptable
repair. Existing triggered-source, combat-damage, and continuation pathways also require
scope assessment before claiming a general damage-source solution.

## Remaining qualification boundaries

Even green results for these scenarios would not qualify full candidate gameplay. Further
requirements include exact selected support-package coverage; changing artifact count and
Familiar's actual affinity costs; sacrifice-draw extra costs and Map decisions; any selected
recursion/Aura-recovery cards; all benchmark cards and legal actions; multiple simultaneous
death-trigger ordering; actual combat and protection edge cases; and hidden-information-safe,
competently developed pilots for both candidates and opponents.

Fixed scenarios measure neither win rate nor pilot strength. There are **0 accepted
development games, 0 frozen-evaluation games, and 0 independent-confirmation games** in
this mechanical record.
