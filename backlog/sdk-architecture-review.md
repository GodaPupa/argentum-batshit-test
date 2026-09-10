# SDK Architecture Review — cross-cutting findings

_Review date: 2026-06-02. Scope: full `mtg-sdk` module (~38.6k LOC), read against
[`docs/architecture-principles.md`](../docs/architecture-principles.md) §1._

This is a **systemic** review — it looks for patterns that repeat across every subsystem rather
than cataloguing individual classes. The tactical class-by-class collapses already have homes:

- [sdk-reusability-consolidation.md](sdk-reusability-consolidation.md) — redundant primitives to delete (many ✅ done).
- [sdk-quality-audit.md](sdk-quality-audit.md) — per-type violations of the quality bar.
- [sdk-language-design.md](sdk-language-design.md) — how the authoring language is shaped.

What's **new here** and not tracked in those: (1) the four latent correctness bugs in §1, (2) the
cross-cutting framing in §2 that explains *why* the same kind of redundancy keeps reappearing, and
(3) a phased direction in §4 that orders the work so each phase unblocks the next.

## Headline

The SDK is fundamentally sound. "Data, not code" holds everywhere; the Gather→Select→Move pipeline
and the `GameObjectFilter` three-axis composition deliver on their promises; the recent
`ModifySpellCost`, `ModifyLifeGain/Loss`, condition-unification, and `requires: Set<Predicate>`
refactors are exactly the right direction. **The debt is a half-finished migration, not rot** — most
findings are "a general primitive landed but its specific predecessors were never retired," or "a
new corner was built without adopting the pattern the rest of the codebase already proved."

The biggest *correctness* risk is §1. The biggest *elegance* lever is restoring the facade boundary
(§2.3) so the consolidations in the companion docs become safe to land at scale.

---

## 1. Correctness bugs (verify, then fix) — HIGH

These are behavioral, not stylistic. All four were spot-checked against engine source.

### 1.1 `GameObjectFilter.matchAll` / `or` is only half-implemented

`matchAll` is documented as "true = AND, false = OR **all** predicates"
([`ObjectFilter.kt:48`](../mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/filters/unified/ObjectFilter.kt)),
but the evaluator only applies the OR branch to `cardPredicates`
([`PredicateEvaluator.kt:89`](../rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/PredicateEvaluator.kt)).
`controllerPredicate` (`:82–86`) and `statePredicates` (`:102`) are **always** AND-ed regardless of
the flag. The `or` infix also drops one side's controller predicate (`ObjectFilter.kt:556`,
`?: controllerPredicate`). So `filterA or filterB` silently mis-evaluates whenever either side
carries a state or controller predicate.

- **Direction:** this is the strongest argument for deleting the `matchAll` flag entirely and
  expressing OR via `CardPredicate.Or` (and giving `ControllerPredicate` combinators, see §2.1).
  A boolean mode spanning three lists it doesn't uniformly govern is a footgun. If kept, the
  evaluator must honor OR across all three axes. **Likely "latent" today** — probably no live card
  exercises `or` with a state/controller predicate — so confirm impact before prioritizing, but the
  fix is cheap either way.

### 1.2 `dynamicMaxCount` only works on triggered abilities, not cast-time spells

`TargetObject.dynamicMaxCount: DynamicAmount?` is meant to be a general dynamic target cap, but two
of its three consumers special-case the literal `DynamicAmount.XValue`:
[`TargetValidator.kt:66`](../rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/targeting/TargetValidator.kt)
and [
`TargetEnumerationUtils.kt:276`](../rules-engine/src/main/kotlin/com/wingedsheep/engine/legalactions/utils/TargetEnumerationUtils.kt)
both gate on `== DynamicAmount.XValue`, whereas
[`TriggerProcessor.kt:658`](../rules-engine/src/main/kotlin/com/wingedsheep/engine/event/TriggerProcessor.kt)
evaluates *any* `DynamicAmount`. A spell using e.g. `dynamicMaxCount = ContextProperty(...)` would
validate/enumerate against the static `count` fallback and silently mis-cap.

- **Direction:** make the validator and enumerator evaluate any `DynamicAmount` (mirroring
  `TriggerProcessor`). Then fold `count`/`minCount`/`optional`/`unlimited`/`dynamicMaxCount` into one
  `TargetCount` sealed type — see §2.2.

### 1.3 `StaticAbilityBuilder.buildFromEffect()` can silently produce a no-op ability

The effect→ability inference path ends in `else -> ModifyStats(0, 0, ...)`
([`CardBuilder.kt:1533`](../mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/dsl/CardBuilder.kt)) and is
self-flagged "This is a simplification." Any unrecognized effect yields a static ability that does
nothing. The real path is already the explicit `ability: StaticAbility?` field.

- **Direction:** delete `buildFromEffect`/`effect` from `StaticAbilityBuilder`; make `ability =` the
  only path. Removes a class of "my static ability does nothing" bugs.

### 1.4 `SuccessCriterion.Auto` fails open

The "if you do" auto-detection walks the action for a terminal `MoveCollectionEffect`
([`CompositeEffects.kt:351`](../mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/effects/CompositeEffects.kt))
and treats any other shape as success — a latent trap for new mechanics whose "did it happen" signal
isn't a zone move.

- **Direction:** require an explicit success criterion for non-zone-move actions, or make `Auto`
  conservative (fail-closed) and log unhandled shapes.

---

## 2. The three systemic patterns (root causes)

Almost every duplication in the companion docs is an instance of one of these three.

### 2.1 Parallel hierarchies for one concept

A general primitive is added; the specific predecessors are never deleted, so the same idea has 2–6
encodings, each with its own evaluator branch. The collapse recipe is always the same and **already
validated by the team's own prior refactors**: keep the general type, keep the named DSL helpers as
thin factories (per the `high use-count ≠ bloat` rule), delete the redundant *types* and their
engine dispatch arms.

| Concept                                                  | Redundant encodings (current)                                                                                                 | Canonical target                                                                                                                              | Notes / status                                                                                       |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Payable costs (Sacrifice/Discard/Exile/Tap/PayLife/Mana) | `AbilityCost` (28), `AdditionalCost` (17), `PayCost` (10) — ~70% overlap; `Forage`/`Blight` literally duplicated              | shared `Cost` core + 3 thin context wrappers for the genuinely-unique cases                                                                   | `ActivatedAbility.kt:101`, `AdditionalCost.kt:20`, `costs/PayCost.kt:18`. Largest single collapse.   |
| Object matching in events                                | `RecipientFilter` (15) + `SourceFilter` (11)                                                                                  | `GameObjectFilter` (+ tiny `PlayerFilter`, + `SourceCategory` enum for combat/spell/ability axis)                                             | `EventFilters.kt:27–173`; every case reachable via `.Matching(filter)`. Predates the unified filter. |
| Entity references                                        | `EntityReference` (11) ≈ entity subset of `EffectTarget`; two resolvers                                                       | one reference vocabulary behind the `DynamicAmounts` facade                                                                                   | `EntityReference.kt:8` admits the overlap.                                                           |
| Counting                                                 | `Count` = `AggregateBattlefield`/`AggregateZone` with `COUNT`; two copy-pasted eval blocks                                    | one `Aggregate(scope, player, filter, aggregation, property?)`                                                                                | `DynamicAmount.kt:478/537/632`.                                                                      |
| "Entity matches a filter" condition                      | `SourceMatches`, `EnchantedPermanentMatches`, `TargetMatchesFilter`, `TriggeringSpellMatchesFilter`, + 2 `Enchanted*` subsets | one `EntityMatches(reference, filter)`                                                                                                        | `BattlefieldConditions.kt:48–87` (doc admits subset relationship).                                   |
| Grant protection/hexproof (static side)                  | 5 bespoke `ProtectionStaticAbilities` that can't express the scopes                                                           | reuse the existing `ProtectionScope` (the `KeywordAbility.Protection` collapse is ✅ done; the **StaticAbility grant side** was not folded in) | `ProtectionStaticAbilities.kt:18–111` vs `KeywordAbility.kt:90`.                                     |
| Damage-number replacement                                | `PreventDamage`/`ModifyDamageAmount`/`CapDamage`/`DoubleDamage`                                                               | one `ModifyDamage(multiplier, modifier, cap)` — the `ModifyLifeGain/Loss` pattern already solved this                                         | `ReplacementEffect.kt:395–496` vs `:692`.                                                            |
| Combinators (And/Or/Not)                                 | declared 3× in `CardPredicate`, `StatePredicate`, `Condition`; `ControllerPredicate` has none (forces the §1.1 flag hack)     | one generic `Logic<T>` reused across predicate kinds                                                                                          | —                                                                                                    |

Also tracked in the reusability/quality docs and consistent with the above: spell-copy effect family
(`StackEffects.kt:472–622`, 5 near-siblings), the `IfYouDo`/optional-cost cluster (9 types,
`CompositeEffects.kt:300`), the trigger-doubling trio (`MiscStaticAbilities.kt:546–618`), `Count` vs
`YourLifeTotal` vs `LifeTotal(Player.You)`.

### 2.2 Flat data classes with mutually-exclusive fields that should be sealed unions

A discriminator field silently nullifies other fields, instead of the type making the states
unrepresentable.

- **Target count:** `count` + `minCount` + `optional` + `unlimited` + `dynamicMaxCount` are five
  interacting knobs (`TargetRequirement.kt:32–46`). Collapse to
  `sealed TargetCount { Single; Fixed(n); UpTo(n); Unlimited; Dynamic(amount, min) }`. Removes the
  `effectiveMin/MaxCount` helpers and the §1.2 special-casing in one move.
- **`GroupFilter.Scope`:** when `scope != Battlefield`, `baseFilter`/`excludeSelf` are silently
  ignored (`GroupFilter.kt:80`). Model as
  `sealed GroupTarget { Battlefield(filter, excludeSelf); Source; AttachedTo; Specific(...) }`.
- **DFC representation:** transforming DFCs use recursive `backFace: CardDefinition?`; MDFC/Adventure/
  Omen/Split use `cardFaces: List<CardFace>` + `layout`. Two incompatible "other side" models, with
  `isDoubleFaced` vs `isSplit`/`isAdventure` checked independently (`CardDefinition.kt:174–180`).
  Unify on `cardFaces` + `layout`, and enrich `CardFace` (it can't express auras/replacement effects
  today — `CardBuilder.kt:122–123`).
- **Boolean-flag creep that should be `requires: Set<Predicate>`:**
  `BecomesTargetEvent.byYou/byOpponent/firstTimeEachTurn`,
  `DealsDamageEvent.requireExcess`, `CountersPlacedEvent.firstTimeEachTurn`. The
  `SpellCastEvent`/`AttackEvent` `requires` pattern is the proven model — migrate the stragglers to it.
- **Identity-as-parameter:** `MoveToZoneEffect.byDestruction: Boolean` — destruction is a distinct
  game action (interacts with indestructible/regen/"dies"), and the collection side already models it
  correctly as `MoveType.Destroy` (`PipelineEffects.kt:760`). Align the single-target side.

### 2.3 The facade boundary is leaky and inconsistent — and it blocks §2.1

The `Effects.*`/`Costs.*` facades exist to be the anti-corruption layer that lets underlying types be
refactored without touching ~3,500 card files. Today that contract is not enforced, which is the
chicken-and-egg blocker for every collapse in §2.1:

- **596 card files construct raw effect types** directly (`MoveToZoneEffect`, `CompositeEffect`,
  `ForEachInGroupEffect`) — so refactoring those types touches hundreds of call sites.
- **`AdditionalCost` and `PayCost` have no facade at all** (only `AbilityCost` does, via
  `dsl/Costs.kt`); cards build them raw, violating the module's own "use the facade" rule.
- **`CardBuilder` itself leaks raw constructors** (`ActivatedAbility(...)`, fully-qualified inline
  types at `CardBuilder.kt:612,708`).
- **`EffectPatterns` is a 75-method pass-through** to 6 sub-objects — pure two-place-registration tax
  with no behavior.

**Direction:** treat the facade as a hard boundary. Add a detekt/ktlint rule (or a one-time codemod)
forbidding direct construction of the foundational effect/cost types in `mtg-sets`; add
`Costs.additional`/`Costs.pay` facades; collapse `EffectPatterns` onto its sub-objects (pick one
namespace). This is mechanical and low-risk, and it is the precondition that makes §2.1 safe.

---

## 3. Smaller systemic notes

- **`applyTextReplacement` identity boilerplate.** ~130 types implement `applyTextReplacement = this`
  by hand. A Kotlin interface-default on `TextReplaceable` deletes them all. (This is about runtime
  value substitution — e.g. "creatures of the chosen type" — and boilerplate reduction, **not** about
  how we generate English. We're English-only; the eager `description` vals are fine to leave as-is.)
- **SDK `GameEvent` name collision.** The SDK's `GameEvent` is a *pattern/matcher*; the engine has a
  runtime `GameEvent` (instances). The file's own doc has to disambiguate them
  (`GameEvent.kt:20–40`). Rename the SDK type to `EventPattern`/`EventSpec` with serial-name aliases.
- **`SelfAlternativeCost.manaCost` is a raw `String`** (`SelfAlternativeCost.kt:23`) while every other
  cost uses typed `ManaCost`. Make it typed.
- **`target`→`filter` doc drift.** ~40 `@property target` doc comments across the `*StaticAbilities.kt`
  files describe a field actually named `filter`. Mechanical fix.
- **`ObjectFilter.kt` package/type mismatch.** The file/type is `GameObjectFilter` in package
  `...scripting`, not `...scripting.filters.unified` — minor, but a discoverability smell.
- **Engine dispatch fan-out for static abilities.** Adding a `StaticAbility` touches a 38-case `when`
  in `StaticAbilityHandler` plus scattered branches in `CastPermissionUtils`/`TriggerDetector`/
  `ClientStateTransformer` — no single registration point, unlike the clean `EffectExecutorRegistry`.
  A per-ability applicator SPI (keyed by type) would make a new ability a one-file change. High effort,
  highest extensibility payoff; scope carefully.

## 3b. Capability gap (not a cleanup — a missing primitive)

The Gather→Select→Move pipeline cannot **branch on a gathered card's properties** mid-pipeline.
`EffectPatterns.kt:236` explicitly flags this: Explore, `DrawRevealDiscardUnless`,
`ExileFromTopRepeating`, etc. each became a bespoke `Effect` + executor *because* the pipeline AST
can't express "reveal, then do different things based on what you saw." A `Branch` /
`ForEachGathered(predicate → effect)` pipeline step would absorb a whole future class of
"reveal-and-react" cards and is the single biggest lever against steady effect-type growth.

---

## 4. Suggested direction (phased so each phase unblocks the next)

**Phase 0 — safe, mechanical, immediate (no behavior change):**
default `applyTextReplacement = this`; rename SDK `GameEvent` → `EventPattern`; type
`SelfAlternativeCost.manaCost`; fix the `target`→`filter` doc drift.

**Phase 1 — correctness:** fix §1.1–§1.4 (verify §1.1's live impact first; the other three are
clear-cut).

**Phase 2 — restore the facade boundary (§2.3):** lint/codemod cards onto facades; add the missing
cost facades; collapse `EffectPatterns`. *Precondition for Phase 3.*

**Phase 3 — collapse the parallel hierarchies (§2.1):** the substantive elegance win. Order by
blast radius: filters (`Recipient`/`Source` → `GameObjectFilter`) and `Count` → `Aggregate` first
(mechanical), then the cost-hierarchy unification (largest), then the condition/protection/damage
collapses. Each rides `add-feature` with full cross-layer tracing.

**Phase 4 — sealed-union conversions (§2.2):** `TargetCount`, `GroupTarget`, DFC unification, the
`requires`-predicate migrations. Larger and engine-touching.

**Phase 5 — capability:** the pipeline `Branch`/`ForEachGathered` primitive (§3b), and (optionally,
high-effort) the static-ability applicator SPI.

Phases 0–2 are low-risk and high-leverage; do them regardless. Phase 3 is where the SDK gets
materially smaller and more uniform, but only after Phase 2 makes it safe to touch the underlying
types.
