# SDK Language Design

Steps to make the card-authoring DSL more elegant, modular, and extensible.

**Companion docs:**

- [sdk-reusability-consolidation.md](sdk-reusability-consolidation.md) — tactical class-level
  collapses (Ward variants, GrantKeyword vs GrantKeywordToGroup, AddMana cluster, …).
- [archived/sdk-composability-gaps.md](archived/sdk-composability-gaps.md) — missing primitives
  that forced authors into one-off classes.

This doc operates one level up: **how the language is shaped**, not which specific classes to
delete. The premise is that even after every consolidation listed in the reusability doc lands,
several language-design choices will still pull the SDK toward sprawl as new sets arrive.

---

## Guiding principles

1. **One concept, one encoding.** Every authoring concept lives in exactly one namespace with
   one casing convention. If two surfaces exist for the same idea, one of them is wrong.
2. **Composition over enumeration.** A new mechanic should be expressible as
   `Composite + existing atoms + a new condition or filter`, not a new executor. New executors
   are an admission that primitives were missing.
3. **Projection-safety is a property of a predicate, not a separate predicate language.** The
   engine decides when to evaluate; authors describe the question once.
4. **Author surface and engine internals are different documents.** Components, executors, and
   continuations should not appear in the language reference an author reads to write a card.
5. **Set-specific mechanics live in set-specific files.** The core DSL should not grow a new
   trigger or condition every time WOTC ships a new keyword.

---

## 1. Unify the predicate languages — [HIGH]

**Problem.** Five overlapping "predicate" surfaces exist:

| Surface                     | Where used                               | Why it's separate                                                 |
|-----------------------------|------------------------------------------|-------------------------------------------------------------------|
| `Condition`                 | Triggers, activated/static gates, spells | Evaluated at resolution                                           |
| `SourceProjectionCondition` | `ConditionalStaticAbility`               | Must evaluate during state projection                             |
| `GameObjectFilter`          | Searches, group effects, costs           | Card+state predicate over an object                               |
| `GroupFilter`               | Static-ability scope                     | Same as above plus a scope (Battlefield/Self/AttachedTo/Specific) |
| `StatePredicate`            | Combat/state checks                      | Battlefield-only state queries                                    |

Authors must know that `YouAttackedWithCreaturesThisTurn` (Condition) and
`ControllerAttackedWithCreaturesThisTurn` (SourceProjectionCondition) are the same question in
different lighting. Every "did X this turn" / "controls Y" / "is in state Z" question risks
needing two implementations.

**Proposal.** Collapse to two surfaces:

- `Predicate<Target>` — pure, declares its `evaluationMode: Resolution | Projection | Either`.
  The engine routes by mode rather than authors picking a sibling.
- `Filter` — `Predicate<Entity>` over a card+state shape; `GroupFilter` becomes
  `Filter + Scope`; `GameObjectFilter` becomes the bare filter.

`StatePredicate` folds into `Filter` (it's already a card+state predicate over battlefield
entities). The `SourceProjectionCondition` / `Condition` split disappears entirely; instead,
`Conditions.YouAttackedWithCreaturesThisTurn(filter, n)` is *one* condition that declares
itself projection-safe. The engine's `ConditionalStaticAbility` accepts any
`Predicate.evaluationMode != Resolution`.

**Win:** kills a class of "I added a condition but it doesn't work in static abilities" bugs.
Halves the surface area an author has to learn.

**Dependencies.** Touches `StaticAbilityHandler`, `ConditionEvaluator`,
`StateProjector.evaluateProjectionCondition`, every predicate-using site. Mostly mechanical
once `evaluationMode` is added.

---

## 2. Convention: one namespace, one casing — [MEDIUM]

**Problem.** `Effects.*` (PascalCase, class-style) and `EffectPatterns.*` (camelCase,
function-style) feel like two different libraries. The boundary leaks:
`Effects.Proliferate`, `Effects.ReadTheRunes`, `Effects.EachPlayerReturnPermanentToHand` are
multi-step compositions wearing atom clothing; `EffectPatterns.wheelEffect`,
`EffectPatterns.factOrFiction` are likewise full mechanics.

**Proposal.** Pick one of:

- **(a) Single namespace.** Merge into `Effects.*` with `is composite` or `category` tagging
  for documentation; authors search one place.
- **(b) Tighten the boundary.** An entry belongs in `Effects.*` iff it maps 1:1 to a single
  executor in `rules-engine/handlers/effects/`. Anything composing ≥2 executors lives in
  `EffectPatterns.*`. Move violators in both directions.

(b) is a smaller change and preserves the "atoms vs recipes" mental model. Either way,
publish the rule on the §4 authoring-rule line in the reference doc so future PRs can be
held to it.

**Win:** authors find effects on the first try; reviewers have a clear test for "does this
belong here?".

---

## 3. Token creators → archetype registry — [MEDIUM]

**Problem.** Each predefined-token shape becomes a new effect: `CreateTreasure`, `CreateFood`,
`CreateLander`, `CreateMutavault`, `CreateMap`, `CreateDrone`, `CreateRoleToken`. Adding a
new archetype (Clue, Blood, Gold, Powerstone, Junk, Incubator, Walker…) means an executor.

**Proposal.**

```kotlin
object TokenArchetypes {
    val Treasure = TokenArchetype(
        "Treasure", types = [Artifact], subtypes = ["Treasure"],
        activatedAbilities = [/* {T}, Sacrifice: add one mana */]
    )
    val Food = TokenArchetype("Food", ...)
    val Clue = TokenArchetype("Clue", ...)
}

Effects.CreateToken(TokenArchetypes.Treasure, count = 2)
```

Custom tokens (creature tokens with stats) still use the existing `CreateToken(name, p, t, …)`
factory; the archetype shape is a wrapper over the same `TokenDefinition`. Set files can
register set-specific archetypes (e.g. Incubator from ONE) without touching `mtg-sdk` if the
abilities exist as primitives.

**Win:** new predefined tokens are data, not code. One executor instead of N.

---

## 4. Alternative costs and spell modifiers → first-class lists — [MEDIUM]

**Problem.** `CardBuilder` has a growing list of top-level alt-cost / spell-modifier
properties: `morph`, `morphCost`, `morphFaceUpEffect`, `warp`, `evoke`, `selfAlternativeCost`,
`conditionalFlash`, `cantBeCountered`. Kicker, Buyback, Madness, Flashback, Suspend, Spectacle,
Foretell, Dash, Surge, Casualty, Plot, Disturb don't exist yet — when they land, this section
bloats further.

**Proposal.**

```kotlin
sealed interface AlternativeCost {
    data class Morph(val cost: ManaCost, val faceUpEffect: Effect? = null) : AlternativeCost
    data class Warp(val cost: ManaCost) : AlternativeCost
    data class Evoke(val cost: ManaCost) : AlternativeCost
    data class Flashback(val cost: ManaCost) : AlternativeCost
    data class Madness(val cost: ManaCost) : AlternativeCost
    data class Kicker(val cost: ManaCost, val effect: Effect) : AlternativeCost  // additive
    data class Generic(val name: String, val cost: PayCost, val effect: Effect) : AlternativeCost
}

sealed interface SpellModifier {
    object CantBeCountered : SpellModifier
    data class FlashWhile(val condition: Condition) : SpellModifier
    data class GainsKeywordWhile(val keyword: Keyword, val condition: Condition) : SpellModifier
}

card("Name") {
    alternativeCosts = listOf(
        AlternativeCost.Morph("{3}"),
        AlternativeCost.Kicker("{2}{R}", Effects.DealDamage(2, EffectTarget.AnyTarget)),
    )
    spellModifiers = listOf(SpellModifier.CantBeCountered)
}
```

**Win:** new alt-cost mechanics (Madness, Flashback, Foretell, Plot…) become one case class,
not a top-level builder property. `CardBuilder` gets ~8 properties lighter today and stops
growing per set.

**Dependencies.** `CastSpellHandler.validate` already branches per alt-cost; centralise via
`AlternativeCost.canCastFrom(zone)` + `AlternativeCost.applyOnResolution`.

---

## 5. Choice and flow-control primitives → one family — [MEDIUM]

**Problem.** "Ask the player a question, branch on the answer" is currently expressed by:

- `MayEffect` (in code, referenced in memory)
- `OptionalCostEffect(cost, effect)`
- `IfYouDoEffect(action, reflexive, optional)`
- `ReflexiveTriggerEffect(action, reflexive, optional)`
- `ConditionalEffect(condition, ifTrue, ifFalse?)` / `Branch(...)`
- `ChooseActionEffect(choices)`
- `ModalEffect.chooseOne` / `chooseN`
- `mayPay(cost, effect)` / `mayPayOrElse(cost, ifPaid, ifNotPaid)`

Each takes a slightly different shape of "the chosen option". Continuation handling has to
know all of them.

**Proposal.** One `Choice` primitive with composable branches:

```kotlin
sealed interface Choice {
    data class YesNo(val prompt: String, val ifYes: Effect, val ifNo: Effect = Effects.Noop) : Choice
    data class PayCost(val cost: PayCost, val ifPaid: Effect, val ifNotPaid: Effect = Effects.Noop) : Choice
    data class PickOne(val options: List<Option>) : Choice
    data class PickN(val n: Int, val options: List<Option>) : Choice
    data class IfYouDo(val action: Effect, val reflexive: Effect, val optional: Boolean) : Choice
}

data class Option(val label: String, val effect: Effect, val requires: TargetRequirement? = null)
```

`MayEffect`, `OptionalCostEffect`, `IfYouDoEffect`, `mayPay`, `mayPayOrElse`,
`ChooseActionEffect`, `ModalEffect.*` all become factory functions returning `Choice`. The
engine has one continuation shape (`ChoiceContinuation`) instead of one per variant.

Reflexive triggers stay distinct only insofar as they go on the stack; that's a property of
the resulting effect (`Effects.ReflexiveTrigger(...)` wraps `Choice.IfYouDo`).

**Win:** kills a recurring class of bugs ([bug_triggers_lost_on_mid_resolution_pause],
[bug_modal_targets_flat_only]) where each branch had to be patched independently. New
question shapes are one enum case.

---

## 6. Trigger shape parameterization — [MEDIUM]

**Problem.** §8 in the language reference lists ~80 triggers. Many are
`(actor, filter, source-zone?)` triples with a frozen actor or filter:

- `Attacks` / `AttacksAlone` / `AnyAttacks` / `CreatureYouControlAttacks` /
  `NontokenCreatureYouControlAttacks` / `YouAttack` / `YouAttackWithFilter`
- ~12 `YouCast*` variants
- ~10 zone-change variants with various filter/actor combinations

Set-specific shapes (`YouCommitCrime`, `Valiant`, `RoomFullyUnlocked`, `OnDoorUnlocked`) compound
the problem — each new keyword set adds new triggers to the core DSL.

**Proposal.** The `TriggerSpec` data layer is already general (per the consolidation doc);
push the DSL down to ~6 base builders parameterized by `(actor, filter, fromZone?, toZone?,
extraCondition?)`:

- `Triggers.attacks(actor, filter)` — covers Attacks/AnyAttacks/CreatureYouControlAttacks/…
- `Triggers.casts(actor, filter, fromZone?)` — covers every `YouCast*`
- `Triggers.zoneChange(filter, from, to, binding)` — covers Dies/LeavesBattlefield/ETB families
- `Triggers.dealsDamage(actor, recipient, isCombat?)`
- `Triggers.phaseStep(phase, step, actor)`
- `Triggers.stateChange(filter, predicate)` — TurnedFaceUp, BecomesTapped, etc.

Set-specific triggers live in `mtg-sets/.../setdsl/SetTriggers.kt`:

```kotlin
// mtg-sets/.../mkm/SetTriggers.kt
val YouCommitCrime = ...  // composed from primitives
```

The core `Triggers.*` namespace stops growing per set.

**Win:** new sets add zero classes to the SDK. Authors who know the 6 builders can read any
trigger without consulting the catalog.

---

## 7. Layout coverage and explicit gaps — [LOW]

**Problem.** §2 lists `NORMAL`, `SPLIT`, `ADVENTURE`. Missing: `TRANSFORM` (DFCs), `MELD`,
`MODAL_DFC`, `FLIP`, `BATTLE`, `SAGA`, `CLASS`, `CASE`. Either the engine supports some of
these and the doc is incomplete, or it doesn't and there's no roadmap entry.

**Proposal.** Audit which layouts are implemented (Saga is, based on the docs index entry).
Either document them in §2 or list them under a "not yet implemented" subsection with a
backlog link.

For unimplemented ones, design once with a shared `CardFace` shape and a `LayoutBehavior`
sealed interface (`ResolutionBehavior`, `TransformTriggerHandling`, `EligibleCastZones`)
rather than adding ad-hoc fields per layout. The existing `ADVENTURE` implementation is a
good template.

**Win:** sets the next 2–3 years of layout work up to be data rather than per-layout code.

---

## 8. Explicit player parameter, deprecate "implicit controller" — [LOW]

**Problem.** Many effects implicitly assume "you / controller" without a player parameter:

- `DistributeCountersFromSelf` — among "creatures you control"
- `CreatePermanentEmblem` — yours
- Most counter / draw / scry shapes default to controller without exposing the slot

When a card wants "target opponent does X to their creatures" (Mindslaver-shapes, donate
effects), authors hit a wall and need a new effect.

**Proposal.** Every effect taking a player gets an explicit `actor: PlayerRef = Controller`
slot. `EffectTarget.PlayerRef` already exists; this is just plumbing. Default stays
`Controller`, so existing card definitions don't change.

**Win:** Mindslaver-style cards become data; "give opponent a Treasure" stops being a feature
request.

---

## 9. Separate author-facing reference from engine internals — [LOW]

**Problem.** `card-sdk-language-reference.md` §18 (Components) is engine-implementation
detail. Authors don't need to know `ChosenModeComponent` or `LinkedExileComponent` exists.
Mixing them in the same doc widens what an author has to learn.

**Proposal.** Two docs:

- `card-sdk-language-reference.md` — author-facing: DSL entries, no components, no
  continuations, no executors.
- `engine-internals.md` (or extend `architecture-principles.md`) — components, executors,
  continuations, resolution flow.

Cross-link the two. Section §19 (named-mechanic composites) stays in the author doc.

**Win:** the author-facing reference shrinks and stops looking intimidating to new
contributors.

---

## 10. Counter type → sealed enum — [LOW]

**Problem.** §16 lists 30+ counter types as strings. The doc tells you to "route through the
central helper." [bug_counter_type_resolver] is exactly the bug a type would prevent (silent
fallback to PLUS_ONE_PLUS_ONE for `-1/-1`).

**Proposal.**

```kotlin
sealed interface CounterType {
    val displayName: String

    data object PlusOnePlusOne : CounterType {
        val displayName = "+1/+1"
    }
    data object MinusOneMinusOne : CounterType {
        val displayName = "-1/-1"
    }
    data object Loyalty : CounterType { ... }

    // ... known ones
    data class Other(val displayName: String) : CounterType  // escape hatch for printed-but-mechanic-less counters
}
```

Every `AddCounters(type, count, target)` etc. takes `CounterType` instead of `String`. The
central resolver becomes the constructor.

**Win:** typos surface at compile time, autocomplete works, the bug class is closed by
construction. The consolidation doc already gestures at this under "CounterTypeFilter".

---

## Recommended ordering

By leverage and risk:

1. **Predicate unification (§1)** — highest leverage; collapses 5 surfaces into 2 and closes a
   bug class. Higher risk because every predicate site touches it.
2. **Counter type enum (§10)** — small change, closes a known bug class, prerequisite for some
   simplifications in the consolidation doc.
3. **Choice/flow-control unification (§5)** — second-highest leverage; depends on §1 landing
   first to share evaluation modes cleanly.
4. **Convention split (§2)** — pure ergonomics, low risk; do once the rule is settled.
5. **Alternative costs as a list (§4)** — moderate risk; needs `CastSpellHandler` refactor.
   Worth doing before the next big set with new alt-cost keywords lands.
6. **Trigger shape parameterization (§6)** — large mechanical refactor, low semantic risk if
   `TriggerSpec` already general; coordinate with the trigger-DSL split in the consolidation
   doc.
7. **Token archetype registry (§3)** — straightforward; one PR.
8. **Explicit player parameter (§8)** — opportunistic; expand effect signatures as touched.
9. **Doc split (§9)** — anytime; no code changes.
10. **Layout coverage audit (§7)** — design exercise; precedes the next layout-bearing set.

Each item is independently shippable. None requires a coordinated multi-file rewrite beyond
the predicate unification.
