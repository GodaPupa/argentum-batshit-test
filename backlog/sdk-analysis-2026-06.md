# mtg-sdk Analysis — June 2026

_Analysis date: 2026-06-09, solutions detailed + mtgish-tooling alignment added 2026-06-10.
Scope: full `mtg-sdk` module (~40.5k LOC main, 166 files) read against
[`docs/architecture-principles.md`](../docs/architecture-principles.md) §1, plus the
`:mtgish-tooling` bridge/emitter as the SDK's most demanding downstream consumer._

**Relationship to prior reviews.** Four SDK reviews landed 2026-05-31..06-02
([architecture](sdk-architecture-review.md), [language design](sdk-language-design.md),
[quality audit](sdk-quality-audit.md), [reusability](sdk-reusability-consolidation.md)). This doc is a
fresh full-module pass: it re-verifies their headline items against today's source, records what has
since been fixed, and distills the remaining work into the points that matter most for the three goals
the SDK is judged by — **correct, elegant, easy to extend with new sets and cards**. Where a prior doc
already owns an item, this doc links rather than re-specifies.

---

## 0. Snapshot

### Hierarchy sizes (verified by grep against today's source)

| Sealed hierarchy | Subtypes | Note |
|---|---|---|
| `Effect` | **245** | across 37 files in `scripting/effects/` |
| `StaticAbility` | **109** | across 12 `*StaticAbilities.kt` files |
| `Condition` | **65** | `scripting/conditions/` |
| `CardPredicate` | 72 | includes 13 numeric P/T/MV variants |
| `ReplacementEffect` | 34 | one file |
| `DynamicAmount` | 31 | mostly compositional after `ContextProperty` collapse |
| `StatePredicate` | 29 | |
| `KeywordAbility` | 20 | post-flatten (was ~45) |
| `TargetRequirement` | 10 | + 5 interacting count fields |
| `ControllerPredicate` | 9 | **no combinators** |

### Authoring surface (DSL layer)

~566 public entry points: `Effects` 252 methods (2,820 lines), `CardBuilder` 73 (1,943 lines),
`Conditions` 57, `DynamicAmounts` 52, `Triggers` 32, `Costs` 22, pattern objects 68 across six
`Patterns.*` namespaces.

### Verified fixed since the June reviews

These were the prior reviews' headline correctness items; all confirmed resolved in today's source:

- **`GameObjectFilter` OR semantics** (arch review §1.1) — the half-implemented `matchAll` flag is
  gone, replaced by a principled design: homogeneous ORs flatten to `CardPredicate.Or` under the
  shared gate, heterogeneous ORs build a recursive `anyOf` union where each branch is matched as a
  complete filter ([`ObjectFilter.kt:620`](../mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/filters/unified/ObjectFilter.kt),
  evaluated at `PredicateEvaluator.kt:109`).
- **`dynamicMaxCount` evaluation** (arch review §1.2) — the enumerator now resolves *any*
  `DynamicAmount` at enumeration time via `resolveStaticDynamicMax` (`TargetEnumerationUtils.kt:277`),
  with `XValue` deliberately deferred to the client-side X clamp; the validator clamps per-req counts
  from the resolved value.
- **KeywordAbility flatten, static single-vs-group unification, `ModifySpellCost`, `TurnTracker`,
  `ContextProperty`, exhaustive `StatePredicate` evaluator** — the reusability doc's top items 1, 2,
  3, 5, 6, 10 are done; the quality audit's HIGH/MED items are essentially all done.

**Bottom line:** the corpus-facing debt identified in June is being paid down on schedule and the
"one general primitive, retire the specifics" playbook demonstrably works. What remains is (a) the
families that haven't had their collapse yet, (b) structural gaps that keep *generating* new one-offs,
and (c) build-time guarantees that don't exist yet — failures surface at runtime instead of compile/CI.

### What's healthy — keep doing it

- **"Data, not code" holds everywhere.** No lambdas or engine references in any SDK type; the module
  has zero deps beyond kotlinx-serialization. Grep for TODO/FIXME comes back empty.
- **The pipeline and filter cores deliver.** Gather→Select→Move expresses most zone mechanics with
  zero new executors; `GameObjectFilter`'s three-axis + `anyOf` design is genuinely good.
- **`Subtype` is the extensibility model to copy** — an inline value class over `String` with
  curated constants and a `fromName` fallback. New creature types cost *zero* code changes.
- **`Patterns` index + facade delegation** — the six pattern objects with a single index is clean and
  discoverable; `Effects` methods that delegate to patterns are correctly thin.
- **Serialization architecture** — sealed `@Serializable` hierarchies auto-register (no central
  discriminator list); `CompactJsonTransformer` is descriptor-driven and symmetric by construction.
- **The hygiene-test pattern exists and works** — rules-engine already has
  `SerializationPolymorphicRegistrationTest`, which walks `sealedSubclasses` via reflection to verify
  serializer registration. Several solutions below are "apply this exact pattern to another contract."

---

## 1. CORRECTNESS — move failures from runtime to build time

The SDK's individual primitives are largely correct; the systemic risk is that the *contracts between
SDK, engine, and content are enforced nowhere*. Every gap below is a class of bug that ships silently
and surfaces mid-game.

### 1.1 No coverage guarantee between SDK types and engine executors — [HIGH]

**Problem.** Adding an `Effect`/`StaticAbility`/`ReplacementEffect`/`Condition` subtype compiles
cleanly without an executor/evaluator/interception registration. The failure mode is a runtime error
(or worse, a silent no-op via a non-exhaustive `when` with an `else` branch — the exact bug class
behind the "static filter state-predicate fallthrough" and `GrantActivatedAbility` fallthrough
incidents). With 245 effect types and 109 statics, manual discipline doesn't scale.

**Solution direction.** A hygiene test suite in `rules-engine` next to the existing
`SerializationPolymorphicRegistrationTest` (which already walks sealed hierarchies via
`sealedSubclasses` — reuse its recursive walker):

1. **Effect coverage is trivial because the registry is a map.** `EffectExecutorRegistry` stores
   `executors[executor.effectType]` keyed by `KClass` and looks up `executors[effect::class]`
   (`EffectExecutorRegistry.kt:87,109`). The test: build the registry exactly as `ActionProcessor`
   does (all modules registered), walk `Effect::class.sealedSubclasses` recursively to leaf classes,
   and assert every concrete leaf is a key in the map. Failure message = the missing type name.
   Maintain a small explicit `KNOWN_UNEXECUTABLE` set (e.g. marker/pipeline-internal types) so
   intentional gaps are declared, not silent.
2. **`when`-based dispatchers can't be map-checked — make the compiler do it.** `StaticAbilityHandler`
   and the replacement-effect interception points dispatch via `when (ability)`. Remove the `else ->`
   branches so the `when` over a sealed type becomes exhaustiveness-checked: a new `StaticAbility`
   subtype then *fails compilation* in the engine until handled. Where a default genuinely applies
   ("this layer ignores ability kinds it doesn't project"), replace `else -> {}` with an explicit
   exhaustive listing of the ignored types, or route through a sealed sub-interface
   (`LayerAbility` / `NonLayerAbility`) so each dispatcher only sees the kinds it must handle.
3. **Condition coverage** — the condition evaluator is one dispatcher; same treatment as (2).
4. **Wire into `just test`** so it runs with the standard suite; it needs no card data and runs in
   milliseconds.

Effort: ~1 day. Payoff: permanent — "new SDK type, forgot the engine half" becomes a red CI line or
a compile error instead of a gameplay bug.

### 1.2 Card-level validation is shallow; structural errors ship to runtime — [HIGH]

**Problem.** `CardValidator` checks creature stats, target-index bounds, aura/equipment consistency,
planeswalker loyalty — and nothing else. Not validated: pipeline variable references
(`GatherCardsEffect(storeAs = "x")` … `MoveCollectionEffect(from = "y")` — a typo resolves to an
empty collection at runtime), `ChoiceSlot` references, `ContextTarget` indices inside *granted* or
nested abilities, `BoundVariable` names against declared target ids, kicker/mode target slicing. The
existing scenario tests catch what they cover; new cards' structural mistakes surface as silent
no-ops in playtesting.

**Solution direction.** Grow `CardValidator` into a **card linter**, in three concrete steps:

1. **Extract a generic effect-tree walker.** `CardValidator.collectIndicesRecursive`
   (`CardValidator.kt:169`) already hand-walks `CompositeEffect`/modal/gated shapes for one check.
   Generalize it: `fun walkEffects(script: CardScript, visit: (Effect, AbilityContext) -> Unit)`
   that recurses through composite children, modes, gates, granted triggered/activated abilities,
   class levels, saga chapters, kicker effects, and card faces. (Alternative implementation that
   can't go stale: serialize the definition with the existing machinery and walk the JSON tree —
   any field holding an `Effect` is visited automatically, no per-wrapper code. Slightly less typed,
   fully future-proof; the snapshot test already produces this tree.)
2. **Add dataflow checks on top of the walker:**
   - *Collections*: every `from`/`storeAs`-style collection name is defined by a prior pipeline
     stage in the same effect tree (plus the implicit `remainder`); flag orphan definitions too
     (defined, never consumed — usually a typo'd consumer).
   - *Targets*: `ContextTarget(i)` / `BoundVariable(name)` resolve against the *owning ability's*
     target requirements (today's check only covers the top-level spell); kicker effects check
     against `kickerTargetRequirements`.
   - *Choice slots*: every `CastChoice(slot)` / `ChoiceSlot` reference has a matching declaration.
   - *Stored entities*: `StoredEntityTarget(v)` has a plausible writer (`storeAs`/capture) somewhere
     on the card — warning-level, since cross-trigger flows are legal.
3. **Run it corpus-wide at build time.** `CardDefinitionSnapshotTest` already instantiates every
   registered card per set; add a `CardLintTest` beside it (or a lint pass inside it) asserting
   zero errors, with a per-card allowlist file for the (rare) intentional exceptions. New cards then
   get structural validation on every `just test`, and the `add-card` skill inherits the gate for
   free.

### 1.3 Fail-open defaults in merge/fallback paths — [MED]

**Problem.** A few quiet-override spots remain where invalid input degrades silently instead of
failing:
- `GameObjectFilter.and` resolves two conflicting controller predicates with
  `other.controllerPredicate ?: controllerPredicate` — the left branch's gate is silently discarded
  ([`ObjectFilter.kt:600`](../mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/filters/unified/ObjectFilter.kt)).
- `ControllerPredicate` has no `And/Or/Not`, so any composition need routes through workarounds.
- `SuccessCriterion.Auto` treats unrecognized effect shapes as success (arch review §1.4, still open
  — `CompositeEffects.kt:317`).

**Solution direction.**
1. **`and` with two different non-null controller predicates → `require` failure.** Combining
   "you control" with "opponent controls" is always an authoring bug; with the §1.2 linter running
   corpus-wide, the `require` fires at build time, not in a game. Same guard in any other merge
   helper that currently keeps one side (`statePredicates` concatenation is fine — AND of state
   predicates is well-defined).
2. **Give `ControllerPredicate` the same mini-combinator treatment `CardPredicate` has** (`And`,
   `Or`, `Not` wrappers + evaluator support in `PredicateEvaluator`). This is ~30 lines and removes
   the only reason heterogeneous-controller filters need the `anyOf` escape hatch.
3. **Make `SuccessCriterion.Auto` fail closed.** `Auto` walks known zone-move shapes; for any other
   shape it should throw at card-load ("`Auto` cannot infer success for `<EffectType>` — specify an
   explicit criterion") rather than report success. The §1.1 coverage mindset applies: unknown ≠ ok.

---

## 2. ELEGANCE — finish the collapses, and remove what *generates* sprawl

245 effects / 109 statics is not intrinsically wrong — MTG is big — but verified sampling shows
~25-35% are members of near-duplicate families differing only by a parameter the SDK already knows
how to model. More important than any single collapse: three structural features keep *minting* new
one-offs (2.4, 2.5, 3.3). Fix those and the hierarchies stop re-bloating after each cleanup.

> **Shared migration recipe.** Every collapse below follows the same five steps, so they're stated
> once: (1) introduce the parameterized type + its facade entries; (2) repoint the *existing facade
> signatures* at the new type so most card sources don't change at all — the facade boundary is
> exactly what makes this possible; (3) mechanically migrate the residue (deprecate old type with
> `ReplaceWith`, fix, delete); (4) re-bless the card snapshot golden
> (`./gradlew :mtg-sets:test --tests "*CardDefinitionSnapshotTest" -DupdateSnapshots=true`) and
> review the per-card JSON diff — that diff *is* the review artifact proving behavior-preservation;
> (5) update the mtgish bridge/emitter entries **in the same PR** and re-run `coverage-verify` +
> `EmitterGoldenTest` (see §4.1). Scenario tests stay untouched throughout — they assert behavior,
> not encoding.

### 2.1 Collapse the remaining near-duplicate effect families — [HIGH]

**Problem.** The proven playbook (Ward→`WardCost`, Protection→`ProtectionScope`, statics→`GroupFilter`)
has not yet reached the effect hierarchy's worst families:

| Family | Today | Target shape |
|---|---|---|
| Counters (~12 types: `AddCountersEffect`, `RemoveCountersEffect`, `MoveAllLastKnownCountersEffect`, `DoubleCountersEffect`, `DistributeCountersFromSelfEffect`, …) | one type per verb×scope | `CounterOp(op: Add/Remove/Move/Double, type, amount, target/group)` |
| Control change (5: `GainControlEffect`, `GainControlByActivePlayerEffect`, `GainControlByMostEffect`, `GiveControlToTargetPlayerEffect`, `ExchangeControlEffect`) | one type per "who gets it"; inconsistent `duration` support | `ChangeControl(newController: Player, target, duration)` (the `Player` AST already expresses ActivePlayer/TargetPlayer/…) |
| Evasion grants (~6: `GrantCantBeBlockedExceptByColorEffect`, `GrantCantBeBlockedByChosenColorEffect`, `CantBeBlockedEffect`, …) | fixed-vs-chosen color and "except by" baked into type names | `GrantEvasion(exception: GameObjectFilter?, duration)` — chosen-color is already expressible via `HasChosenColor` predicates |
| Sacrifice (3: `SacrificeEffect`, `SacrificeSelfEffect`, `SacrificeTargetEffect`) | scope baked into type; `any:` param exists on only one | one type over `EffectTarget`/filter |
| Numeric `CardPredicate`s (13: `PowerEquals/AtMost/AtLeast`, `Toughness*`, `ManaValue*`) | one class per property×operator | `NumericPredicate(property, op, value)` ([reusability §4](sdk-reusability-consolidation.md)) |
| Targeting OR-types (`TargetCreatureOrPlayer` etc., `TargetRequirement.kt:189`) | bespoke unions | delete; `TargetObject(filter = A or B)` ([reusability §7](sdk-reusability-consolidation.md)) |

**Solution direction.** One PR per family using the shared recipe above. Family-specific notes:

- **Counters first** — most members, least semantic risk, and the engine already routes counter
  mutation through one service (`resolveCounterType` precedent). Sketch:

  ```kotlin
  @Serializable
  data class CounterOp(
      val op: Op,                              // Add, Remove, RemoveAll, Move, Double
      val counterType: CounterType? = null,    // null = all kinds (Move/RemoveAll)
      val amount: DynamicAmount = Fixed(1),
      val target: EffectTarget = Self,
      val from: EffectTarget? = null,          // Move only
      val distribute: Boolean = false,         // distribute-among-targets variants
  ) : Effect
  ```

  `ProliferateEffect` stays — it's a named mechanic with its own rules text, not a parameter
  combination. `AddCountersToCollectionEffect` folds into the pipeline's `addCounterType` instead.
- **Control change** — the unified type takes `newController: Player` (the existing player AST);
  `ExchangeControlEffect` stays separate (genuinely different shape: two-way swap). Normalize
  `duration` support across all paths — `GainControlByMostEffect`'s implicit-permanent behavior
  becomes `duration = Permanent` explicitly.
- **Evasion** — "can't be blocked except by X" reduces to one type whose `exception` is a
  `GameObjectFilter`; "chosen color" routes through the existing `HasChosenColor` predicate so the
  chosen/fixed distinction stops being a type distinction. `CantAttack`/`CantBlock` group effects
  are a second, separate mini-family (restriction, not evasion) — same recipe, different PR.
- **Numeric predicates** — `NumericPredicate(property: EntityNumericProperty, op: ComparisonOp,
  value: DynamicAmount)`; `EntityNumericProperty` already exists. The 13 old names become facade
  functions (`Filters.powerAtMost(2)`) producing the new type, so card text stays readable.
- **Review rule, enforced in `add-feature`:** a new `Effect` subtype must include one sentence in
  the PR description proving it cannot be a parameter of an existing family. Cheap to write, makes
  the reviewer's job mechanical.

### 2.2 One zone-move encoding — [HIGH]

**Problem.** The single biggest semantic concept with multiple encodings is "move object(s) to a
zone": `MoveToZoneEffect` (single-target, with `byDestruction: Boolean`), `MoveCollectionEffect`
(pipeline, with `moveType: MoveType` enum — a *parallel* encoding of the same destroy/sacrifice/
discard semantics), plus ~10 specialized exile/return types (`ExileUntilLeavesEffect`,
`MarkExileOnDeathEffect`, `ExileAndGrantOwnerPlayPermissionEffect`, `ForceReturnOwnPermanentEffect`,
…) re-implementing slices of the same space. Two link-to-source mechanisms exist. Every new card that
moves cards unusually has to pick among encodings that don't compose. The duplication is contagious:
the mtgish emitter maintains *both* render paths today (`Effects.Move` at `ZoneHandlers.kt:36` vs raw
`MoveCollectionEffect` literals at `ZoneHandlers.kt:318`).

**Solution direction.** Don't merge the two top-level types — single-target and collection moves have
genuinely different targeting and the split is fine. Instead **extract the shared semantics into one
`MoveSpec` value** that both carry:

```kotlin
@Serializable
data class MoveSpec(
    val destination: CardDestination,        // reuse the pipeline's existing type
    val moveType: MoveType = Default,        // Default / Destroy / Sacrifice / Discard — the ONLY encoding
    val faceDown: Boolean = false,
    val revealed: Boolean = false,
    val linkToSource: Boolean = false,       // the ONE linking mechanism
)
```

Concrete steps:
1. `MoveToZoneEffect` and `MoveCollectionEffect` both gain a `spec: MoveSpec`; their old flat fields
   (including `byDestruction: Boolean`) become deprecated secondary constructors that build the
   spec, then get deleted once cards migrate (facade does most of this invisibly — step 2 of the
   shared recipe).
2. Engine-side, extract a single `ZoneMoveService` that takes `(entities, MoveSpec, context)` and is
   the *only* code path that emits `ZoneChangeEvent` and consults replacement effects. Both
   executors call it. This is the correctness payoff: replacement-effect interception (Rest in
   Peace, token cleanup, exile-instead) currently has to be right in N places; afterwards, one.
3. Re-express the specialized exile/return types as compositions over the unified move + the
   existing linked-exile (`CardSource.FromLinkedExile`) and delayed-trigger primitives. Triage each:
   `MarkExileOnDeathEffect` is a replacement-marker (keep, it's atomic); `ExileUntilLeavesEffect`
   is move+linked-return (compose); `ForceReturnOwnPermanentEffect` is select+move (compose). Keep
   what's genuinely atomic, delete what isn't.
4. The mtgish emitter's two render paths collapse into one handler family in the same change (§4.1).

### 2.3 One cost language — [HIGH]

**Problem.** Three parallel cost hierarchies — `AbilityCost`, `AdditionalCost` (591 lines), `PayCost`
— share ~70% of their constructors (sacrifice, discard, exile, tap, pay-life, mana). Each new payable
thing (Blight, waterbend fodder, …) gets implemented per-hierarchy or arbitrarily lands in one. The
engine-side `CostPaymentService` unification ([paycost doc](paycost-payment-unification.md)) already
proved the payment *execution* can be shared; the SDK-side declaration is the remaining duplication.

**Solution direction.**
1. **Extract `CostAtom`** — one sealed hierarchy holding the shared vocabulary, each variant
   parameterized the way the best current version is:

   ```kotlin
   @Serializable sealed interface CostAtom {
       data class Mana(val cost: ManaCost) : CostAtom
       data object Tap : CostAtom
       data class TapPermanents(val count: Int, val filter: GameObjectFilter) : CostAtom
       data class Sacrifice(val filter: GameObjectFilter, val count: DynamicAmount = Fixed(1)) : CostAtom
       data class Discard(val count: DynamicAmount, val filter: GameObjectFilter? = null, val random: Boolean = false) : CostAtom
       data class ExileFrom(val zone: Zone, val filter: GameObjectFilter, val count: DynamicAmount) : CostAtom
       data class PayLife(val amount: DynamicAmount) : CostAtom
       data class RemoveCounters(val type: CounterType, val amount: DynamicAmount, val from: EffectTarget = Self) : CostAtom
       // deliberately NOT: context-specific oddities (BlightVariable, Echo timing) — those stay on wrappers
   }
   ```
2. **Wrappers become thin contexts.** `AbilityCost`, `AdditionalCost`, `PayCost` each become (or
   gain) an `atoms: List<CostAtom>` plus their genuinely context-specific extras (`AdditionalCost`
   keeps optionality/kicker linkage; `AbilityCost` keeps activation-speed implications). Their old
   subtypes turn into deprecated aliases constructing atoms.
3. **Migration order: smallest first** — `PayCost` (~10 subtypes, fewest call sites, and
   `CostPaymentService` already executes it), then `AdditionalCost`, then `AbilityCost`. One wrapper
   per PR; `Costs.*` facade signatures stay identical throughout so cards are untouched until the
   final deprecation sweep.
4. **Engine:** `CostPaymentService` gets one `payAtom(atom, ...)` dispatcher; the three per-hierarchy
   payment paths delegate to it and shrink to their context-specific residue. The §1.1 coverage test
   extends to `CostAtom` automatically (it's one more sealed hierarchy to walk).
5. Done = a new payable thing (next set's "collect evidence"-alike) is **one** `CostAtom` variant +
   one payment branch, available in all three contexts on day one.

### 2.4 Retire the parallel event-filter vocabulary — [HIGH, structural]

**Problem.** `EventFilters.kt` defines **eight** mini-hierarchies (`RecipientFilter` 14 cases,
`SourceFilter` 11, `DamageType`, `AmountFilter`, `CounterTypeFilter`, `ControllerFilter`,
`SpellCastPredicate`, `AttackPredicate`) that restate what `GameObjectFilter` already expresses —
"creature you control" exists in at least three vocabularies. Both `RecipientFilter.Matching` and
`SourceFilter.Matching` already wrap `GameObjectFilter`, proving the bridge works. This is
*generative* sprawl: every new trigger/replacement pattern grows the parallel enums instead of
reusing the filter core, and the mtgish emitter has to learn each vocabulary separately.

**Solution direction.** Three steps, engine-first so behavior is locked before the SDK shape moves:
1. **Engine: one evaluation path.** Add `RecipientFilter.toGameObjectFilter()` /
   `SourceFilter.toGameObjectFilter()` (each enum case is a one-liner:
   `CreatureYouControl → GameObjectFilter.Creature.youControl()`; the player-recipient cases — `You`,
   `Opponent`, `AnyPlayer` — map to a small genuine `PlayerFilter` instead, since players aren't
   game-object-filterable). Rewrite the event evaluator to convert-then-delegate to
   `PredicateEvaluator`, deleting the hand-rolled per-case matching. Trigger behavior is pinned by
   the existing scenario suite; run it before and after.
2. **SDK: cases become constants.** Replace each enum case with a predefined value of the `Matching`
   shape (`val CreatureYouControl = Matching(GameObjectFilter.Creature.youControl())` in the
   companion). Card sources that say `RecipientFilter.CreatureYouControl` *do not change*. The JSON
   shape changes → snapshot re-bless is the review artifact.
3. **Deprecate-and-dissolve the wrappers.** Once everything is `Matching`, the wrapper adds nothing:
   `EventPattern` fields become `recipient: GameObjectFilter` / `recipientPlayer: PlayerFilter` /
   `source: GameObjectFilter` directly. `AmountFilter` and `DamageType` are small and genuinely
   event-specific — leave them. `CounterTypeFilter`/`ControllerFilter`/`SpellCastPredicate`/
   `AttackPredicate` get individually triaged with the same convert-or-keep test: "is this
   expressible as `GameObjectFilter`/`Condition` today?"

### 2.5 Sealed unions over interacting flat fields — [MED]

**Problem.** Several "pick one mode" concepts are encoded as N interacting nullable/boolean fields
whose invalid combinations are representable:
- `TargetRequirement`: `count` / `minCount` / `optional` / `unlimited` / `dynamicMaxCount` — five
  knobs, with rules like "unlimited implies count ignored" enforced only by doc comment
  (`TargetRequirement.kt:32-46`).
- `CardDefinition`: `backFace: CardDefinition?` **and** `cardFaces: List<CardFace>` **and** `layout`
  — two incompatible multi-face models; transform-DFCs use one, split/adventure/MDFC the other, and
  consumers must know which to read.
- Boolean predicate flags on event patterns (`byYou` / `byOpponent` / `firstTimeEachTurn`) predating
  the `requires: Set<Predicate>` pattern.

**Solution direction.**
1. **`TargetCount` sealed union.** The recent `dynamicMaxCount` fix removed the last semantic
   blocker; this is now a pure encoding refactor:

   ```kotlin
   @Serializable sealed interface TargetCount {
       data object One : TargetCount
       data class Exactly(val n: Int) : TargetCount
       data class UpTo(val n: Int) : TargetCount                      // optional=true today
       data class Between(val min: Int, val max: Int) : TargetCount
       data object AnyNumber : TargetCount                            // unlimited=true today
       data class UpToDynamic(val max: DynamicAmount) : TargetCount   // dynamicMaxCount today
   }
   ```

   Keep the old `count`/`minCount`/`optional`/`unlimited` properties as *derived* accessors during
   migration so engine call sites move incrementally; flip the storage, migrate consumers
   (`TargetValidator`, `TargetEnumerationUtils`, the client DTO mapping), then delete the
   accessors. Serialization: a custom serializer that still *reads* the legacy flat fields keeps old
   stored scenarios/goldens loadable for one release; snapshot re-bless converts the corpus.
2. **Face model: unify on `cardFaces` + `layout`.** `CardFace` gains the few fields only `backFace`
   carries today; TRANSFORM layout stores both faces in `cardFaces`; `backFace` becomes a derived
   `val` (`cardFaces.getOrNull(1)` when `layout == TRANSFORM`) so the many engine read sites keep
   compiling while storage unifies underneath. The `isDoubleFaced`/`isSplit`/`isAdventure` helpers
   stay as the public vocabulary.
3. **Boolean event flags → `requires`** opportunistically: no big-bang migration, but the linter
   (§1.2) warns on new uses of the deprecated flags so the set monotonically shrinks.

### 2.6 Condition hierarchy: stop the one-off accumulation — [MED]

**Problem.** 65 `Condition` subtypes and growing ~3-5 per set. Two anti-patterns: (a) four near-clones
of "entity X matches filter" (`SourceMatches`, `EnchantedPermanentMatches`, `TargetMatchesFilter`,
`TriggeringSpellMatchesFilter`) differing only in *which* entity; (b) hyper-specific trackers
(`IsFirstSpellPaidWithTreasureManaCastThisTurn`) that should be `Compare` over a tracked amount.

**Solution direction.**
1. **One `EntityMatches(entity: EffectTarget, filter: GameObjectFilter)`** — the `EffectTarget`
   vocabulary already names every entity role (Self, EnchantedPermanent, TriggeringEntity,
   ContextTarget…), and the engine's `resolveTarget` already resolves them. The four clones become
   facade constants (`Conditions.SourceMatches(f) = EntityMatches(Self, f)`), then get deleted.
   Evaluator: resolve entity → `PredicateEvaluator.matchesWithProjection` — one code path, projection
   handled once.
2. **Tracker-shaped conditions route through `Compare` + `TurnTracking`** — the LIFE_GAINED
   precedent. When the needed tracker doesn't exist, add the *tracker enum value* (data), not a
   condition class.
3. **Set-mechanic conditions are allowed but quarantined**: genuinely novel state (city's blessing,
   ring-bearer, Void) earns a type, but it lives in a mechanic-named file with the mechanic's other
   types, per the language-design doc — so the core `conditions/` directory stops being the dumping
   ground. The `add-feature` skill checklist gains this placement question explicitly.

---

## 3. EXTENSIBILITY — make "new set" and "new mechanic" cheap and safe

### 3.1 Get set-specific mechanics off the core `CardBuilder` — [HIGH]

**Problem.** `CardBuilder` has accumulated **12+ mechanic-specific methods** (`leyline()`, `flurry()`,
`mobilize()`, `firebending()`, `decayed()`, `vividEtb()`, `impending()`, `renew()`, `craft()`,
`station()`, …) directly on the core class (`CardBuilder.kt:311-860`). Every new set's keyword grows
the one file every card author reads; mechanics from six different sets interleave; CLAUDE.md's own
rule ("set-specific mechanics live in set-specific files") is violated by the SDK itself.

**Solution direction.** Kotlin extension functions make this nearly free:
1. **Create `dsl/mechanics/`, one file per mechanic**: `StationDsl.kt` holding
   `fun CardBuilder.station(...)`, `FirebendingDsl.kt`, etc. — body moves verbatim, since these
   methods only call public builder APIs (`triggeredAbility { }`, `keywordAbility(...)`). Where one
   touches a private builder field, widen that field to `internal` or route through an existing
   public block — inspect per-mechanic, most need nothing.
2. **Call sites don't change** (`station()` resolves identically as an extension); only imports do,
   and a single mechanical `goimports`-style sweep (or `*`-import of the mechanics package in set
   files) handles the corpus. Existing DSL tests (`StationDslTest`) move with their mechanic.
3. **Decision rule going forward**, applied in `add-feature` review: *evergreen or multi-set
   keyword with parameters* (rampage, kicker) may live on the core builder; *set mechanic* gets an
   extension file. A mechanic used by exactly one set may even host its extension in `mtg-sets`
   next to the set (extensions work across modules) — judgment call per mechanic, default is
   `dsl/mechanics/` so the mtgish emitter can keep importing from the SDK.
4. **End state:** `CardBuilder.kt` shrinks toward the genuinely universal blocks
   (spell/triggered/activated/static/loyalty/saga/class/faces/metadata), and "where do I add my
   mechanic's sugar" has a one-word answer.

### 3.2 Strengthen the facade boundary from spot-check to guarantee — [HIGH]

**Problem.** The facade is the load-bearing abstraction that makes every collapse in §2 safe (refactor
the data types, update only the facade). But enforcement is a regex test covering **five** patterns
(`CompositeEffect(`, `MoveToZoneEffect(`, `ForEachInGroupEffect(`, `AdditionalCost.`, `PayCost.`) —
the other ~240 effect constructors are constructible from cards today, and nothing verifies a new SDK
type even *gets* a facade entry, so gaps silently push authors back to raw construction.

**Solution direction.** Two tests, both cheap:
1. **Import-whitelist boundary** (replaces the regex blacklist in `FacadeBoundaryTest`). Card
   definition files may import `com.wingedsheep.sdk.dsl.*`, `core.*`, `model.*`; any
   `com.wingedsheep.sdk.scripting.*` import fails the test *unless* listed in a committed
   `facade-boundary-exceptions.txt` (initially seeded by running the test and dumping current
   violations — debt becomes a visible, burn-downable file instead of an invisible default).
   Implementation is ~20 lines: imports sit in a file's header, no AST needed, no regex
   false-negatives from aliasing or line-wrapping. Keep two of the old regexes (raw `CompositeEffect(`,
   constructor calls of types *re-exported* through dsl) as a belt-and-suspenders second pass.
2. **Facade-coverage test** (in mtg-sdk's own test suite): walk `Effect::class.sealedSubclasses`
   (and `Condition`, cost hierarchies) and assert each concrete type's simple name appears as a
   constructor call somewhere under `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/dsl/` — i.e. some
   facade can produce it — or is on an explicit `ENGINE_INTERNAL` list (pipeline plumbing the DSL
   composes for you). Source-scan rather than reflection-of-return-types: facades return the
   supertype, so the text scan is the honest check. This turns "added a type, forgot the facade"
   into a named CI failure and gives the facade the same completeness guarantee the snapshot test
   gives cards.
3. **Sequencing note:** seed both tests *before* the §2 collapses begin — they're what makes
   "repoint the facade, cards don't change" a verified claim instead of a hope.

### 3.3 Add the missing pipeline primitive: branch on gathered properties — [HIGH, structural]

**Problem.** The Gather→Select→Move pipeline is strictly linear. Any card that says "reveal/look,
**if it's a land** do X **otherwise** Y" (Explore, "draw and reveal, discard unless…", countless
others) cannot be expressed and forces a bespoke effect type (`DrawRevealDiscardUnlessEffect`, the
explore family, several `ExileFromTopRepeating` variants carry built-in predicates). This is the #1
*generator* of new one-off effects going forward — flagged in the arch review (§3b), still open.

**Solution direction.** One new step plus one gate, both pure data:
1. **`PartitionCollectionEffect(from, filter, storeMatching, storeRest)`** — splits a named
   collection by a `GameObjectFilter`, no player decision, no zone change. One executor, ~40 lines,
   no continuation needed (it's deterministic).
2. **Collection-gated branches.** `Gate.WhenCondition` + the existing
   `Conditions.CollectionContainsMatch` already express "run this only if `landPart` is non-empty" —
   verify the gate evaluator can see pipeline collections in `EffectContext` (if not, that's the one
   engine change: pass `storedCollections` into condition evaluation, which §1.2's dataflow view of
   collections also wants).
3. **Worked example — Explore** becomes pure composition:

   ```kotlin
   Effects.Composite(
       GatherCardsEffect(CardSource.TopOfLibrary(1), storeAs = "revealed"),
       PartitionCollectionEffect("revealed", Filters.Land, storeMatching = "land", storeRest = "nonland"),
       MoveCollectionEffect(from = "land", destination = ToZone(Zone.HAND)),
       Effects.AddCounters(PLUS_ONE_PLUS_ONE, 1, target = Self),       // unconditional per Explore
       GatedEffect(Gate.WhenCondition(CollectionContainsMatch("nonland")),
           SelectFromCollectionEffect("nonland", ChooseUpTo(1), storeSelected = "toGrave"),
           MoveCollectionEffect(from = "toGrave", destination = ToZone(Zone.GRAVEYARD)))
   )
   ```
4. **Then harvest:** grep the bespoke types whose only reason to exist was the missing branch
   (`DrawRevealDiscardUnlessEffect`, predicate-carrying exile-repeat variants), re-express, delete —
   each one is a small PR with a snapshot diff. New cards of this shape stop needing `add-feature`
   at all, which is also exactly the shape the mtgish emitter scaffolds on today (§4.5).

### 3.4 De-risk new-set registration — [MED]

**Problem.** Adding a set has three silent failure modes: (a) `MtgSetCatalog.all` is a hand-maintained
import + list — forget the list entry and the set silently doesn't exist; (b) `CardDiscovery.findIn`
on a mistyped package returns an **empty list silently**; (c) `setCode` stamping is centralized for
cards but per-set manual for basic lands (`.copy(setCode = code)`) — an inconsistency waiting to be
forgotten.

**Solution direction.**
1. **Discover sets the way cards are discovered.** `CardDiscovery` already uses ClassGraph; add
   `findSets()` scanning for `object : MtgSet` implementors under the definitions root, and make
   `MtgSetCatalog.all` a lazily-built view over it (keep the val so call sites don't change). The
   hand-maintained import block and list — the two-places-to-forget — disappear. If scan cost at
   startup matters for the server, cache the result; the scan already happens for cards.
2. **Zero cards = hard error.** In the set-loading path: `check(cards.isNotEmpty()) { "Set $code
   declared package $CARDS_PACKAGE but discovery found 0 cards — typo in package name?" }`. A
   legitimately empty set doesn't exist; an `incomplete` set still has *some* cards. This single
   `check` converts the worst silent failure (set ships hollow) into an immediate, named error.
3. **Stamp `setCode` centrally for basics too**, in the same registry-load pass that stamps cards;
   delete the per-set `.copy(setCode = code)` boilerplate from every `MtgSet` object.
4. Add a tiny `MtgSetCatalogTest`: all codes unique, all release dates parseable, every set's
   basics non-empty or `basicLandsFallback` set — the things currently only discovered when a draft
   fires up.

### 3.5 Keep enum-locked vocabularies honest — [LOW]

**Problem.** `Keyword`, `CounterType`, `AbilityFlag` are enums requiring SDK edits per new WotC
mechanic. This is mostly *fine* (they're stable, and enum exhaustiveness is valuable engine-side) —
but `CounterType` additionally drags a parallel `Counters` string-constant object that must be kept
in sync, and keyword display/parse logic is split between the enum and `KeywordAbility`.

**Solution direction.** Don't chase `Subtype`-style stringly-typing here — exhaustive matching over
keywords/counters is worth the edit cost. Instead:
1. **Delete the parallel `Counters` string object** (`CounterType.kt:57-135`); callers route through
   the enum plus the existing `resolveCounterType` for string input. One vocabulary, one sync point.
2. **Make the keyword checklist self-verifying:** a unit test asserting every `Keyword` enum entry
   either round-trips through `parseFromOracleText` or appears in an explicit `NOT_PARSED` set —
   so "add enum entry, forget the parser" stops being tribal knowledge.
3. The mtgish Registry already live-scans the `Keyword` enum and `Subtype` constants (§4), which is
   the right consumption pattern — no tooling change needed here.

---

## 4. MTGISH-TOOLING ALIGNMENT — co-evolve the dictionaries with the SDK

The `:mtgish-tooling` bridge/emitter is the SDK's most demanding consumer: it has an opinion about
*every* encoding choice, because it must render cards into the authoring language mechanically. Its
current state (157 bridge entries, ~64 emitter handlers, ~494 decline-to-scaffold points) makes the
coupling measurable — and it confirms this doc's central thesis: **every place the SDK has two
encodings for one concept, the tooling pays twice** (e.g. both zone-move render paths live in
`ZoneHandlers.kt`; the mana family needs three facades; prevention needs two). Conversely, the
tooling has validation asymmetries the SDK can help close.

### 4.1 Every §2 collapse must land with its bridge/emitter update — [process rule]

**Problem.** The bridge validates its entries against SDK `@SerialName`s via a live Registry scan
(`Bridge.kt:55-76`) — renames there surface as coverage gaps. But the **emitter renders SDK names as
plain strings** (`call("Effects.Move", ...)` ×8 in `ZoneHandlers.kt`; raw `MoveCollectionEffect(...)`
constructor literals at `ZoneHandlers.kt:318+`): an SDK facade rename or constructor-parameter change
emits broken or wrong drafts, caught only if a committed golden covers an affected card.

**Solution direction.** Make it a standing rule (this doc + the `add-feature` skill): an SDK PR that
renames/collapses a type the bridge or emitter references updates both dictionaries **in the same
PR**, with `EmitterGoldenTest` re-blessed and `just coverage-verify --set POR` green (POR must stay
0-mismatch — the established bar). The §2 collapses *reduce* tooling surface when done this way:
- §2.2 zone-move unification deletes the emitter's second render path outright.
- §2.3 cost atoms give the emitter one cost vocabulary to render instead of three.
- §2.4 event-filter collapse removes a whole parallel vocabulary the emitter currently must mirror.
- §2.1 family collapses replace per-variant handler dispatch with parameter mapping.

### 4.2 Validate the emitter's SDK-name strings mechanically — [HIGH, tooling-side, SDK-enabled]

**Problem.** ~75 hardcoded `Effects.X` / `Patterns.X` / raw-constructor strings across the handler
files are validated by nothing until a generated card happens to exercise them.

**Solution direction.** A hygiene test in `:mtgish-tooling` mirroring the bridge's Registry pattern:
scan the emitter source for the string-literal arguments of `call(...)` / `simple(...)` / `Lit(...)`
that look like SDK references (`Effects.`, `Patterns.`, `Conditions.`, `*Effect(`), and verify each
against the SDK — facade members via reflection over the `dsl` objects, type names via the same
`@SerialName`/class scan the bridge already does. The §3.2 facade-coverage machinery and this test
are two views of the same contract ("the facade surface is enumerable and stable"), so build them to
share the enumeration helper. After this, an SDK rename breaks the tooling build *immediately and by
name*, instead of at the next golden regen.

### 4.3 Emit facades only — make generated drafts boundary-clean — [MED]

**Problem.** The emitter mixes facade calls with raw constructors (`SacrificeSelfEffect`,
`RegenerateEffect`, `MoveCollectionEffect(from=..., destination=...)`). Raw constructor emission (a)
bypasses the rename-absorbing layer, so every collapse breaks it, and (b) produces drafts that would
violate the strengthened §3.2 import-whitelist the moment they're promoted into a set's `cards/`
package — the generator would be manufacturing boundary debt.

**Solution direction.** Audit the handlers once: each raw-constructor render either (a) switches to
the existing facade, (b) gets a facade added (the §3.2 coverage test will list exactly which types
lack one — the emitter is effectively a second consumer driving facade completeness), or (c) is
demoted to decline→SCAFFOLD if no facade exists *on purpose* (consistent with the established
"decline, don't widen" principle). End state: emitted drafts import only `dsl/`, identical to
human-authored cards, and SDK collapses stop touching emitter internals beyond argument mapping.

### 4.4 Export the "plumbing" set from the SDK — [LOW]

**Problem.** Fidelity scoring filters structural-only nodes via a **hardcoded** set
(`Fidelity.kt:22-25`: Composite, Gather, Select, …). A new SDK pipeline/structural effect won't be
auto-filtered → spurious capability deltas in fidelity reports.

**Solution direction.** Move the classification to the source of truth: either a marker interface
(`interface StructuralEffect : Effect` implemented by the pipeline/wrapper types) or a
`PLUMBING_EFFECT_TYPES: Set<String>` constant in `mtg-sdk`'s serialization package next to the
serializer config. The tooling imports it; §3.3's new `PartitionCollectionEffect` then lands
pre-classified instead of breaking fidelity scores.

### 4.5 The shared open design area: cast-time choices and inherited values — [HIGH, design]

**Problem.** The tooling's own CLAUDE.md flags the engine/SDK as "sloppy around extra costs and value
selection — choosing values at cast/activation time (X, creature type, color) and *inheriting* a
cast-time choice into later effects," and instructs the emitter to permanently scaffold these shapes.
This is simultaneously the emitter's biggest AUTOGEN blocker and a genuine SDK gap. The ingredients
exist but don't form one model: `ChoiceSlot`, `CastTimeCapture` (CR 601.2i flags), `CastChoice(slot)`,
`DynamicAmount.CastX` + durable `CastChoicesComponent` (PR #510), `xManaRestriction`,
`castTimeCreatureTypeChoice` — each added for one mechanic, each with its own read path.

**Solution direction.** Unify under one declared-choices model, generalizing the CastX precedent
(which already solved the hard part — durable storage + permanent→stack→LKI read fallback):
1. **One declaration:** `CardScript.castChoices: List<ChoiceSlot>` where a slot declares its kind
   (`Numeric(X)`, `Color`, `CreatureType`, `Mode`, …), when it's made (cast vs. resolution — per
   Scryfall rulings, the existing feedback rule), and its prompt.
2. **One storage:** `CastChoicesComponent` (already exists for X) holds *all* slot values, survives
   onto the permanent, and is LKI-readable — exactly CastX's semantics, widened from `Int` to a
   small value union.
3. **One read path:** `DynamicAmount.CastChoice(slot)` / `ChosenValue(slot)` predicates replace the
   per-mechanic readers (`CastX` stays as an alias for the X slot). `castTimeCreatureTypeChoice` and
   `xManaRestriction` become slot declarations.
4. **Payoff is triple:** the engine's per-mechanic choice plumbing converges; the §1.2 linter can
   validate slot references statically; and the emitter's standing scaffold-class becomes renderable
   ("declare slot, reference slot" is mechanical), converting a large mtgish-blocked tier to
   AUTOGEN. This is the right `add-feature`-sized project to schedule after the guarantees phase —
   it's the one item in this doc the tooling's creator-note explicitly asks for.

#### 4.5a Detailed mtgish-side design

**The key observation (verified against the IR): mtgish is already a declare-and-reference
language.** Every shape the creator's note describes arrives in the IR as an explicit *declaration*
node paired with explicit *symbolic reads* — the structure Forge's `declare` directive provides is
already in the data:

| Card (real IR) | Declaration node | Symbolic read, later in the card |
|---|---|---|
| Ivy Elemental | `{X}` in `ManaCost` | `EntersWithNumberCounters(_GameNumber: ValueX, PTCounter(1,1))` |
| Hydroid Krasis | `{X}` in `ManaCost` | cast trigger: `GainLife(HalfRoundedDown(Trigger_ValueXOfThatSpell))` + same for draw |
| Story Circle | `AsPermanentEnters → ChooseAColor` | activated ability: `ChooseADamageSource(IsColor(_Color: TheChosenColor))` |
| Phyrexian Processor | `AsPermanentEnters → PayAnyAmountOfLife` | activated ability: token `PTX(X, X, _GameNumber: TheLifePaid)` |
| (Imagecrafter et al.) | `ChooseACreatureType` | `_CreatureTypeVariable: TheChosenCreatureType` |

So the emitter doesn't scaffold these because parsing fails — it scaffolds because the SDK offers no
uniform translation target. Once §4.5's slot model exists, the tooling work is a mechanical mapping
in four pieces:

1. **A card-wide slot-resolution pass on `EmitCtx`.** `EmitCtx` is already per-card state threaded
   as the receiver of every handler — add `slots: Map<String, SlotInfo>` built by one IR scan
   *before* handlers run: `{X}` in the mana cost ⇒ implicit slot `x` (no DSL declaration emitted);
   `ChooseAColor` ⇒ slot `color` (emits a `castChoices { color(...) }` declaration);
   `ChooseACreatureType` in cast-time position ⇒ slot `creatureType`; `PayAnyAmountOfLife` ⇒ numeric
   slot `lifePaid` with a payment binding. This replaces today's *local* logic
   (`amountExpr`/`strictCardCount`, `EmitCtx.kt:113-140`) that can only resolve what's visible
   inside one action node.
2. **A reference-translation table** consulted wherever handlers currently bail:

   | IR read | Emitted DSL |
   |---|---|
   | `ValueX` (on-permanent context, e.g. ETB counters) | `DynamicAmount.CastX` |
   | `Trigger_ValueXOfThatSpell` (cast trigger) | `DynamicAmount.CastX` — the SDK read path already resolves permanent→stack→LKI |
   | `TheChosenColor` | `HasChosenColor` predicate / `ChosenValue("color")` |
   | `TheChosenCreatureType` | `HasChosenSubtype` (exists today) |
   | `TheLifePaid` | `DynamicAmount.CastChoice("lifePaid")` |

3. **Bridge entries that quantify the payoff up front.** `ChooseAColor`/`ChooseACreatureType` are
   currently UNIVERSAL *envelopes* (`Envelopes.kt:75-76`) — structurally free, with the gating
   happening implicitly when the reads fail. Promote them (plus `PayAnyAmountOfLife`,
   dynamic-`EntersWithNumberCounters`, `Trigger_ValueXOfThatSpell`) to real capability entries gated
   on the slot feature. Then `just coverage --set X` / the blocked-capability leaderboard reports
   exactly how many cards the slot feature unlocks per set **before any engine work starts** — the
   tooling's whole purpose, applied to its own biggest blocker.
4. **Sharper declines, not zero declines.** The decline→SCAFFOLD principle stays for what slots
   genuinely don't cover: cross-object reads ("the chosen color of **target** permanent" — another
   object's slot), two same-kind declarations on one card (ambiguous symbol binding), reads in
   abilities granted to *other* objects. The slot table makes these *detectable*: a read with no
   in-scope declaration declines with reason `unbound-choice-reference` — a crisp, leaderboard-able
   SCAFFOLD reason instead of today's blanket policy.

**Worked examples** (DSL names illustrative — the SDK feature fixes them; the mapping is 1:1
regardless):

*Ivy Elemental* — today: SCAFFOLD (no fixed integer recoverable in the ETB-replacement context).
After:

```kotlin
val IvyElemental = card("Ivy Elemental") {
    manaCost = "{X}{G}"
    typeLine = "Creature — Elemental"
    stats(0, 0)
    entersWithCounters(CounterType.PLUS_ONE_PLUS_ONE, amount = DynamicAmount.CastX)
}
```

*Hydroid Krasis* cast trigger — today: SCAFFOLD (`Trigger_ValueXOfThatSpell` unmapped). After:

```kotlin
triggeredAbility {
    trigger = Triggers.YouCastThis()
    effect = Effects.GainLife(DynamicAmounts.half(DynamicAmount.CastX))   // Divide exists
        .then(Effects.DrawCards(DynamicAmounts.half(DynamicAmount.CastX)))
}
```

*Story Circle* — today: SCAFFOLD (entry choice + `TheChosenColor` read). After:

```kotlin
val StoryCircle = card("Story Circle") {
    manaCost = "{1}{W}{W}"
    typeLine = "Enchantment"
    castChoices { color("color") }   // generalizes the existing EntersWithChoice/ChosenModeComponent precedent
    activatedAbility {
        cost = Costs.Mana("{W}")
        effect = Effects.PreventNextDamage(
            from = Filters.Source.hasChosenColor("color"),
            to = EffectTarget.Controller,
        )
    }
}
```

*Phyrexian Processor* — declaration is itself a payment; the read crosses into an activated ability:

```kotlin
castChoices { payLife("lifePaid") }
activatedAbility {
    cost = Costs.composite(Costs.Mana("{4}"), Costs.Tap)
    effect = Effects.CreateToken(
        power = DynamicAmount.CastChoice("lifePaid"),
        toughness = DynamicAmount.CastChoice("lifePaid"),
        colors = setOf(Color.BLACK), subtypes = listOf(Subtype.PHYREXIAN, Subtype.MINION),
    )
}
```

**Verification path** (per this module's own rules): add the newly-AUTO cards to the committed
fixture slice (`just coverage-fixtures`), keep calibrated sets 0-mismatch in `coverage-verify`, and
write a scenario test for the *first* card of each shape (X-counters, chosen-color read, declared
life payment) before trusting batch output — emitted correctness for a filter/amount is exactly the
thing `coverage-verify` cannot prove.

---

## 5. Suggested sequencing

The phases are ordered so each makes the next cheaper and safer; within a phase, items are independent.

1. **Guarantees first (§1.1, §1.2, §3.2, §4.2)** — executor-coverage test, card linter,
   import-whitelist facade boundary + facade-coverage test, emitter name-validation test. ~Days of
   work, no card migrations, and every subsequent collapse becomes dramatically safer because
   regressions turn into red CI instead of gameplay bugs. Highest ROI in the document.
2. **Structural de-generators (§3.3, §2.4, §3.1)** — pipeline partition step, event-filter collapse,
   mechanics off the builder. These stop the hierarchies from re-growing while the collapses proceed.
3. **The big three collapses (§2.1 families, §2.2 zone-move, §2.3 costs)** — one family per PR,
   snapshot-golden re-bless as the review artifact, bridge/emitter updated in the same PR (§4.1).
   Start with counters (most members, least semantic risk) and `MoveSpec`/`MoveType` (kills a whole
   parallel encoding in both the SDK and the emitter).
4. **The cast-choice unification (§4.5)** — a proper `add-feature` project; after phase 1 because
   the linter and coverage tests are what make a cross-cutting model change like this safe to land.
5. **Type-shape refits (§2.5, §2.6, §1.3)** — sealed `TargetCount`, face-model unification,
   `EntityMatches`, fail-closed merges. Serialization-migration care needed; do after the boundary
   tests exist.
6. **Registration hygiene (§3.4, §3.5, §4.4)** — anytime; ideal between-sets filler.

### What this buys, per goal

- **Correct:** the SDK↔engine, SDK↔content, and SDK↔tooling contracts become machine-checked
  (§1.1, §1.2, §3.2, §4.2); fail-open paths close (§1.3). New-card structural bugs die in CI.
- **Elegant:** one encoding per concept — costs, zone moves, counters, control, evasion, event
  matching, cast-time choices — with the proven facade/snapshot machinery making each collapse
  routine (§2.*, §4.5).
- **Extensible:** a new set touches set-local files only (§3.1, §3.4); a new mechanic is a
  composition + maybe one condition (§3.3, §2.6); when a genuinely new primitive *is* needed, the
  coverage tests enumerate exactly what to implement (§1.1, §3.2); and every encoding the SDK
  simplifies makes the auto-gen tooling render more of the next set for free (§4).
