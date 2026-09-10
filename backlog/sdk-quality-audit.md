# SDK Quality Audit — `mtg-sdk`

_Audit date: 2026-05-31. Scope: `mtg-sdk` module, measured against the quality bar in
[`docs/architecture-principles.md`](../docs/architecture-principles.md) §1 and the
[`add-feature`](../.claude/skills/add-feature/SKILL.md) skill._

## Quality bar applied

1. **Pure serializable data** — SDK types carry no lambdas, no engine references, no behavior.
2. **Name the mechanic, not the card** — a type named for one card is a smell.
3. **Parameterize** over filter / amount / duration / target / player — no baked-in constants,
   subtypes, or magic-number "any number" sentinels.
4. **Composition over monoliths** — a type that just 1:1 wraps existing atoms should be a
   `CompositeEffect` / `EffectPatterns` recipe instead.
5. **One condition, both contexts** — no separate `*ProjectionCondition` types.
6. **No single-use `EffectPatterns` helper** — inline until a second caller appears (exception:
   named MTG mechanics).

## Headline

The module is in **good shape**. Every type audited is pure serializable data (standard #1 clean —
no leaked lambdas/engine refs). The condition-unification refactor has **not** regressed (no
`*ProjectionCondition` types; `Player`-parametric forms intact). Most large effects
(`MoveToZoneEffect`, the Gather→Select→Move pipeline, `CompositeEffect`, `ModalEffect`,
`CreateTokenCopyOfTargetEffect`) are the intended foundational consolidations, correctly
parameterized. The violations below are localized — mostly delete-a-type-and-recompose.

User counts are caller _files_ in `mtg-sets/src/main` (worktree copies excluded).

---

## HIGH — clear violations worth fixing

### 1. `TapTargetCreaturesEffect` — magic-number `20` + hardcoded creature type ✅ DONE
- **Resolution:** `TapTargetCreaturesEffect` deleted from `TapEffects.kt` (file now holds only
  `TapUntapEffect`, `TapUntapCollectionEffect`, `PhaseOutEffect`). Icy Blast no longer passes
  `maxTargets = 20` — it composes `Effects.TapEachTarget().then(...)` and lets
  `TargetCreature(optional = true, dynamicMaxCount = XValue)` own the count. Regression-guarded by
  `TapEachTargetScenarioTest`.
- **Location:** `scripting/effects/TapEffects.kt:48`
- **Standards:** #3 (magic constant / unparameterized), #4 (monolith)
- **Why:** Bakes "creatures" into the name and carries a bare `maxTargets: Int`.
  **Icy Blast (`ktk/IcyBlast.kt:34`) passes `maxTargets = 20` as an "any number" sentinel** —
  exactly the anti-pattern the `unlimited=true` / `dynamicMaxCount` work was built to kill. The
  count is also duplicated against the spell's `TargetCreature` (dual source of truth). Other
  callers are plain tap-the-targets (ChokingTethers 4, TidalSurge 3, EddymurkCrab 2).
- **Users:** 4 (ChokingTethers, EddymurkCrab, IcyBlast, TidalSurge). No DSL facade.
- **Fix:** Replace with a tap-over-targets composition (`ForEachTarget(Tap(...))` /
  tap-collection recipe); let `TargetCreature` + `unlimited` / `dynamicMaxCount` own the count.
  Icy Blast drops the `20` sentinel. Delete `TapTargetCreaturesEffect`.

### 2. Three `CreateGlobalTriggeredAbility*` effects differing only by lifetime ✅ DONE
- **Resolution:** Collapsed to a single `CreateGlobalTriggeredAbilityEffect(ability, duration =
  Duration.Permanent, descriptionOverride)` at `PlayerEffects.kt:168`. The three lifetime-specific
  variants are deleted; `descriptionOverride` preserved. Facade passes through so card call sites
  are unchanged.
- **Location:** `scripting/effects/PlayerEffects.kt:161, 185, 206`
- **Standards:** #3 (parameterize over duration), #4 (3→1 collapse)
- **Why:** `…UntilEndOfTurnEffect`, `…PermanentEffect`, and `…WithDurationEffect(ability, duration)`
  all create a global `TriggeredAbility`; they differ only in lifetime.
  `CreateGlobalTriggeredAbilityWithDurationEffect` already takes a `Duration`, and `Duration` has
  both `EndOfTurn` and `Permanent` — so it fully subsumes the other two.
- **Users:** UntilEndOfTurn 3, Permanent 4, WithDuration 1.
- **Fix:** Keep the duration-parametric effect (rename to `CreateGlobalTriggeredAbilityEffect`);
  delete the other two. Add facade overloads passing `Duration.EndOfTurn` / `Duration.Permanent`
  so card call sites are unchanged. Preserve `CreatePermanentGlobalTriggeredAbilityEffect`'s
  `descriptionOverride` field.

### 3. `DynamicAmount.CreaturesSharingTypeWithEntity` — pure composition ✅ DONE
- **Resolution:** Deleted the variant + evaluator branch. Alpha Status now uses
  `AggregateBattlefield(Player.Each, GameObjectFilter.Creature.sharingCreatureTypeWith(EntityReference.AffectedEntity), excludeSelf = true)`.
  Two supporting generalizations: `EntityReference.AffectedEntity` now resolves inside predicate
  filters during projection (threaded through `PredicateContext`), and `AggregateBattlefield`'s
  `excludeSelf` excludes the affected entity (not just the source) so granted "for each OTHER …"
  effects exclude the right permanent.
- **Location:** `scripting/values/DynamicAmount.kt:738`
- **Standards:** #4, #5 (a `DynamicAmount` variant should only exist when it reads state no node can)
- **Why:** The SDK already has `CardPredicate.SharesCreatureTypeWith(entity)` (evaluator-backed)
  and `AggregateBattlefield(COUNT, excludeSelf=true)`. The bespoke evaluator loop
  (`DynamicAmountEvaluator.kt:329-351`) re-implements exactly that — no state the composition
  can't read.
- **Users:** 1 (Alpha Status, `scg/cards/AlphaStatus.kt`).
- **Fix:**
  ```kotlin
  DynamicAmount.AggregateBattlefield(
      player = Player.Each,
      filter = GameObjectFilter.Creature.and(CardPredicate.SharesCreatureTypeWith(EntityReference.AffectedEntity)),
      aggregation = Aggregation.COUNT,
      excludeSelf = true,
  )
  ```
  Delete the variant + its evaluator branch. (Bonus: gives the unused `SharesCreatureTypeWith`
  predicate its first real user.)

### 4. `IsFirstSpellOfTypeCastThisTurn` — duplicate of `PlayerCastSpellsThisTurn`, no facade ✅ RESOLVED
- **Location:** `scripting/conditions/TurnConditions.kt:136`
- **Standards:** #3, #4, plus the "use the facades" load-bearing rule (card builds the raw type)
- **Why:** Means "exactly one matching spell cast by you this turn." Expressible as
  `All(YouCastSpellsThisTurn(1, filter), Not(YouCastSpellsThisTurn(2, filter)))` over the existing
  `PlayerCastSpellsThisTurn` primitive. Has **no `Conditions.*` facade entry**.
- **Users:** 1 (`AlaniaDivergentStorm.kt`).
- **Resolution:** Type deleted. The count-only decomposition above was **rejected as buggy** — it
  drops the evaluator's guard that *the triggering spell itself matches the filter*, so casting a
  non-matching spell after one matching spell would wrongly fire (an instant cast earlier this turn
  would satisfy `YouCastSpellsThisTurn(1, Instant)` even while a creature is the spell being cast).
  Instead added a small, genuinely-general primitive `TriggeringSpellMatchesFilter(filter)` (facade
  `Conditions.TriggeringSpellMatches`) and a composing facade
  `Conditions.YouCastFirstSpellOfTypeThisTurn(filter)` =
  `All(TriggeringSpellMatches(filter), Not(YouCastSpellsThisTurn(2, filter)))` — reusing the
  `PlayerCastSpellsThisTurn` count instead of a bespoke loop. Alania now uses the facade. Covered by
  `AlaniaDivergentStormTest` (incl. the guard case). See `card-sdk-language-reference.md`.

---

## MEDIUM — generalize / decompose

### 5. `ChooseColorAndGrantProtectionTo{Group,Target}Effect` — the monolith the combinator replaces ✅ DONE
- **Resolution:** Both monoliths deleted. A `GrantProtectionFromChosenColorEffect` atom now exists
  (`ProtectionEffects.kt:63`) alongside the existing `GrantHexproofFromChosenColorEffect`, and
  protection is expressed via the `ChooseColorThenEffect` combinator — matching the
  hexproof / can't-be-blocked siblings.
- **Location:** `scripting/effects/ProtectionEffects.kt:26, 52`
- **Standards:** #4, #5
- **Why:** Exactly the monolith `ChooseColorThenEffect` (same file, line 76) was written to replace.
  The hexproof / can't-be-blocked siblings already follow the combinator pattern; protection
  doesn't.
- **Users:** Target variant 6 (ThornscapeMaster, StormscapeMaster, ArmoredGuardian,
  JarethLeonineTitan, FeatOfResistance, AvenLiberator). **Group variant 0.**
- **Fix:** Add a `GrantProtectionFromChosenColorEffect` atom; express both as
  `ChooseColorThen(GrantProtectionFromChosenColor(...))` (+ `ForEachInGroup` for the group case).
  Delete the zero-user Group variant first.

### 6. `LifeAuctionEffect` — named/shaped for one card ✅ DONE
- **Resolution:** Renamed to `OpenLifeBidEffect` (`AuctionEffects.kt:33`) and generalized over
  participants via `participant: Player` (defaults to `Player.Opponent`; Mages' Contest passes
  `Player.ControllerOf("target spell")`). `onWin` runs only when the caster is high bidder.
- **Location:** `scripting/effects/AuctionEffects.kt:28`
- **Standards:** #2, #4
- **Why:** Doc says "implements the Mages' Contest shape." Assumes exactly caster + one opponent;
  only `onCasterWins` is parameterized. No real MTG keyword "life auction."
- **Users:** 1 (`inv/MagesContest.kt`).
- **Fix:** The alternating-bid decision machinery may justify a bespoke _type_, but rename off the
  card (e.g. `OpenLifeBidEffect`) and generalize over participants (player filter).

### 7. `TargetSharesMostCommonColor` vs `ColorIsMostCommon` — duplicated tally logic ⚠️ PARTIAL
- **Resolution (partial):** The duplicated tally is gone — both conditions now route through a
  single shared `ConditionEvaluator.mostCommonColors(state, projected)` helper, so evaluators can't
  drift. The two conditions remain separate SDK types (not folded into one parametric
  `MostCommonColorCondition(subject)` nor redefined as an `Any(...)` composition), so the "name the
  mechanic" half of the fix is still open.
- **Location:** `scripting/conditions/BattlefieldConditions.kt:201, 225`
- **Standards:** #2/#3, drift risk
- **Why:** Copy-pasted "most common color across all permanents, ties included" evaluation; the
  target-shaped one bakes in "the target's colors."
- **Users:** TargetShares 2, ColorIsMostCommon 5.
- **Fix:** Keep `ColorIsMostCommon(color)` as primitive; redefine `TargetSharesMostCommonColor` as
  an `Any(...)` composition over it (or fold both into one parametric
  `MostCommonColorCondition(subject)`). At minimum share the tally so evaluators can't drift.

### 8. `CopyNextSpellCastEffect` / `CopyEachSpellCastEffect` — hardcoded "instant or sorcery" ✅ DONE
- **Resolution:** Both effects now carry `spellFilter: GameObjectFilter =
  GameObjectFilter.InstantOrSorcery` (descriptions read `${spellFilter.description} spell`), plumbed
  through `PendingSpellCopy.spellFilter` and the two executors. `CastSpellHandler` no longer gates on
  a global `isInstant || isSorcery` check — it matches each pending entry's filter against the spell
  just cast via `predicateEvaluator.matches(...)`, consuming only matched non-persistent entries and
  leaving non-matching ones waiting. Facades (`Effects.CopyNextSpellCast` /
  `CopyEachSpellCast`) take the optional `spellFilter`; existing callers (Howl of the Horde, Rimefire
  Torque, The Mirari Conjecture) are unchanged via the default. `ClientStateTransformer` badge text is
  filter-driven. Covered by `CopySpellCastFilterTest`. See `card-sdk-language-reference.md`.
- **Location:** `scripting/effects/StackEffects.kt:581, 606`
- **Standards:** #3 (baked-in filter)
- **Why:** Spell type hardcoded in description and behavior; can't express "copy the next creature
  spell." (The one-shot vs. end-of-turn distinction is genuine, so not a pure duplicate — MEDIUM.)
- **Users:** CopyNextSpellCast 2, CopyEachSpellCast 1.
- **Fix:** Add `spellFilter: GameObjectFilter = GameObjectFilter.InstantOrSorcery` to both.

### 9. Single-card helpers in `EffectPatterns` (should be inlined) ✅ DONE
- **Resolution:** All 15 helpers inlined into their sole callers and deleted from the SDK — both the
  `EffectPatterns.kt` facade entry and the delegate body (`putCreatureFromHandSharingTypeWithTapped`
  had only the inline facade body). Two helpers turned out to also have a single *rules-engine
  scenario-test* caller not counted in the `mtg-sets` sweep (`GoblinMachinistTest` →
  `revealUntilNonlandModifyStats`, `SupremeInquisitorTest` → `searchTargetLibraryExile`); both tests
  now inline the body too. 12 of the 15 were documented in `card-sdk-language-reference.md` — those
  entries were removed (correcting the original Fix note below, which wrongly assumed none were
  documented). Orphaned imports pruned from `EffectPatterns`/`HandPatterns`/`LibraryPatterns`/
  `CreatureTypePatterns`/`ExilePatterns`. The borderline five and the LOW dead-facade cleanup are
  **not** part of this change.
- **Standards:** #6 (no single-use patterns), #4 (each is a `CompositeEffect` 1:1 recipe — pure
  composition of atoms already in the facade, no new primitive earned).
- **Why:** 15 helpers are whole-card scripts lifted into the `EffectPatterns` facade and its delegate
  `*Patterns` objects, each with **exactly one caller** and **none a named MTG mechanic** (verified by
  grepping `mtg-sets/src/main`, worktree copies excluded). They inflate the shared SDK surface with
  card-specific scripts that read as reusable building blocks but never get a second user — the exact
  "name the card, not the mechanic" smell. (Contrast the legitimately-kept compositions in
  *Explicitly cleared*: scry/surveil/mill/loot/connive/factOrFiction/wheelEffect, each a real keyword
  or a multi-card shape.)
- **Two-layer structure (both must go):** Most helpers are a one-line facade entry in
  `EffectPatterns.kt` that delegates to a body in a `*Patterns` delegate object. Inlining each removes
  **both** the facade one-liner **and** the delegate body. The exception is
  `putCreatureFromHandSharingTypeWithTapped`, whose `CompositeEffect` body lives inline in
  `EffectPatterns.kt:387` (no delegate). The `_count`-style variable references and stored-collection
  names the bodies use (`opponentHand_count`, `tappedSubtypes`, …) move verbatim into the card.

  | Helper | Facade (`EffectPatterns.kt`) | Body (delegate) | Sole caller | Composes (inline recipe) |
  |---|---|---|---|---|
  | `headGames` | :206 | `HandPatterns.kt:436` | `ons/HeadGames` | Gather opp hand → move to their library top → Gather library → `ChooseUpTo(opponentHand_count)` → to hand → shuffle |
  | `patriarchsBidding` | :368 | `CreatureTypePatterns.kt:219` | `ons/PatriarchsBidding` | `EachPlayerChoosesCreatureType` → per-player Gather graveyard creatures of chosen type → to battlefield |
  | `putCreatureFromHandSharingTypeWithTapped` | :387 *(inline)* | — | `ons/CrypticGateway` | Gather `TappedAsCost` → `GatherSubtypes` → Gather hand creatures sharing a tapped subtype → `ChooseUpTo(1)` → to battlefield |
  | `revealUntilNonlandModifyStats` | :297 | `LibraryPatterns.kt:457` | `ons/GoblinMachinist` | `GatherUntilMatch(nonland)` → reveal → `ModifyStats` → move |
  | `revealUntilCreatureTypeToBattlefield` | :300 | `LibraryPatterns.kt:481` | `ons/RiptideShapeshifter` | `ChooseCreatureType` → `GatherUntilMatch(type)` → reveal → to battlefield → shuffle |
  | `searchTargetLibraryExile` | :288 | `LibraryPatterns.kt:388` | `ons/SupremeInquisitor` | Gather target's library → select → exile → shuffle |
  | `searchAndExileLinked` | :426 | `ExilePatterns.kt:55` | `scg/ParallelThoughts` | Gather library → select → exile (linked to source) → shuffle |
  | `eachPlayerRevealCreaturesCreateTokens` | :461 | `ExilePatterns.kt:190` | `ons/KamahlsSummons` | per-player Gather hand creatures → select → `CreateTokenEffect` copies |
  | `revealAndOpponentChooses` | :303 | `LibraryPatterns.kt:557` | `ons/AnimalMagnetism` | Gather top N → `Chooser.Opponent` selects → split move (kept vs rest) |
  | `chooseCreatureTypeMustAttack` | :365 | `CreatureTypePatterns.kt:203` | `ons/WalkingDesecration` | `ChooseOption(CREATURE_TYPE)` → `ForEachInGroup(MarkMustAttackThisTurn)` |
  | `chooseCreatureTypeShuffleGraveyardIntoLibrary` | :341 | `CreatureTypePatterns.kt:89` | `lgn/ElvishSoultiller` | `ChooseCreatureType` → Gather graveyard of type → shuffle into library |
  | `destroyAllExceptStoredSubtypes` | :371 | `CreatureTypePatterns.kt:242` | `ons/HarshMercy` | Gather creatures → `FilterCollection(exclude stored subtypes)` → destroy |
  | `searchLibraryNthFromTop` | :279 | `LibraryPatterns.kt:305` | `scg/LongTermPlans` | Gather library → select → reorder chosen to Nth-from-top → shuffle/move |
  | `lookAtTargetLibraryAndDiscard` | :285 | `LibraryPatterns.kt:359` | `por/CruelFate` | Gather top of target's library → select → split move |
  | `lookAtTopXAndPutOntoBattlefield` | :324 | `LibraryPatterns.kt:627` | `eoe/FamishedWorldsire` | Gather top X → select by filter → to battlefield + rest |

- **Users:** 1 each (all single-caller; counts re-verified, no helper has gained a second user).
- **Fix:** Inline each body into a `private val`/local `CompositeEffect` in its card definition (the
  recipes above are the exact moves), then delete the facade entry **and** the delegate body. Keep
  only atomic primitives + real mechanics in the facade. Do this **alongside the LOW dead-facade
  cleanup** (`readTheRunes`, `destroyAllSharingTypeWithSacrificed`, `takeFromLinkedExile`,
  `eachPlayerReturnsPermanentToHand`, `chooseCreatureTypeGainControl` — all 0-caller bodies in these
  same delegate objects): removing both sets empties `CreatureTypePatterns` / `ExilePatterns` of
  card-specific scripts, after which the surviving entries are only real mechanics. No
  `card-sdk-language-reference.md` change is needed (these are facade conveniences, not documented SDK
  building blocks). Spot-check: each card already has a scenario test, so inlining is behavior-neutral
  and regression is caught by the existing suite.
- **Borderline — keep for now (single-caller but defensible non-trivial shapes):**
  `searchMultipleZones` (`LibraryPatterns.kt:259`, `lgn/DarkSupplicant`),
  `eachOpponentMayPutFromHand` (`HandPatterns.kt:160`, `ons/TemptingWurm`),
  `chooseCreatureTypeUntap` (`CreatureTypePatterns.kt:142`, `ons/RiptideChronologist`),
  `eachPlayerSearchesLibrary` (`MiscPatterns.kt:152`, `ons/WeirdHarvest`),
  `shuffleAndExileTopPlayFree` (`ExilePatterns.kt:134`, `scg/MindsDesire` — the Storm-era
  "exile top, play free" shape likely to recur). These read as parameterized shapes a second card
  could plausibly reach; inline only if no second user materializes.

---

## LOW — opportunistic / cleanup

- **Dead facade entries — claim was stale; only `drain` was actually dead. ✅ DONE.** Re-checking
  callers showed the original "0 callers" list was wrong: `readTheRunes` (Read the Runes),
  `destroyAllSharingTypeWithSacrificed` (Endemic Plague), `takeFromLinkedExile` (Parallel Thoughts),
  `eachPlayerReturnsPermanentToHand` (Words of Wind), and `chooseCreatureTypeGainControl` (Peer
  Pressure) are all **live** — each reached by a real card through its `Effects.*` wrapper. Deleting
  them would break those cards, so they were kept. `EffectPatterns.connive` is likewise live
  (`Effects.Connive`); kept as a consistent sibling of the ~40 other `EffectPatterns` delegations
  rather than singled out. The only genuinely dead entry was **`drain`** (no `Effects.Drain` wrapper,
  zero callers) — `EffectPatterns.drain` + `MiscPatterns.drain` + its DSL-reference line deleted.
- **`Int` amounts that should be `DynamicAmount`** (convert when a 2nd user lands — deliberately left
  per the no-premature-generalization rule): `GrantDamageBonusEffect.bonusAmount`
  (`PlayerEffects.kt:389`, Flame of Keld); `BudgetModalEffect.budget` (`CompositeEffects.kt:1021`);
  `TakeExtraTurnEffect.loseAtEndStep` (`PlayerEffects.kt:116`) → generic `endOfExtraTurnEffect: Effect?`.
- **`AmassEffect.subtype` defaulted to `"Orc"` ✅ DONE.** Default removed from `AmassEffect` and both
  `Effects.Amass` overloads; the keyword's subtype is now required (Amass is CR 701.47 and the Army's
  type is printed on each card). All 19 LTR call sites updated to pass `"Orc"` explicitly.
- **`GainControlByActivePlayerEffect` vs `GiveControlToTargetPlayerEffect`** (`ControlEffects.kt:46, 120`)
  — converge on one player-parametric `GiveControl(permanent, newController, duration)`. **Deferred:**
  the merge needs a new `Player.ActivePlayer` reference (none exists today) whose addition ripples
  through every exhaustive `when (player)` in the SDK + engine and reworks the hardcoded "target
  opponent gains control" description — feature-sized blast radius, not opportunistic cleanup.
- **`ForceExileMultiZoneEffect`** (`RemovalEffects.kt:448`) — zone set hardcoded in name/behavior;
  1 user. Parameterize `zones: Set<Zone>` if a second multi-zone-exile card appears. (Deferred to 2nd user.)
- **Cosmetic:** `SourceAbilityResolvedNTimesThisTurn` ordinal produced "21th"/"22th" ✅ DONE — added an
  `ordinalSuffix` helper (21st/22nd/23rd/24th, with the 11–13 exceptions) in `TurnConditions.kt`.

---

## Explicitly cleared (not violations)

- **No `*ProjectionCondition` types** — standard #5 holds; unification refactor intact.
- **No `You*`/`Controller*` triples or `SourceIs*`/`SourceHas*` singletons regressed** —
  `SourceMatches(filter)` is the live primitive. Remaining standalone `Source*` conditions
  (`SourceIsModified`, `SourceIsRingBearer`, `SourceCastForImpending`, `SourceChosenModeIs`,
  `SourcePlottedOnPriorTurn`) each read state the filter machinery genuinely can't express.
- **Named mechanics kept rightly:** `GrantSuspendEffect`, `AmassEffect` (type), `ChainCopyEffect`
  (5 users, fully parameterized), `TransformEffect`, `ProvokeEffect`, `TheRingTemptsYouEffect`,
  `ManaRestriction` hierarchy, scry/surveil/mill/loot/connive/incubate/forage/blight/giftSpell/
  factOrFiction/wheelEffect.
- **`MoveToZoneEffect`, `CreateTokenCopyOfTargetEffect`, Gather→Select→Move pipeline,
  `CompositeEffect`, `ModalEffect`** — intended foundational consolidations, correctly parameterized.
- Most zero-user `DynamicAmount` / predicate variants are properly parameterized mechanic
  infrastructure (`HasGreatestPower`, `ManaSpentOnX`, `StartingLifeTotal`, etc.), not card-named.

---

## Suggested order of work

The four HIGH items are the cleanest wins — all delete-a-type-and-recompose, each with a single or
handful of users, and #1/#3/#4 contradict patterns already established elsewhere in the codebase.
Each touches shared SDK types with cross-layer wiring, so route through the **`add-feature`** flow
(executor + full trace + tests + `card-sdk-language-reference.md` update) rather than a quick edit.

1. #1 `TapTargetCreaturesEffect` (kills the `20` sentinel) ✅ done
2. #2 `CreateGlobalTriggeredAbility*` 3→1 collapse ✅ done
3. #3 `CreaturesSharingTypeWithEntity` delete ✅ done
4. #4 `IsFirstSpellOfTypeCastThisTurn` delete ✅ done
5. #5 protection combinator ✅ done · #6 `OpenLifeBidEffect` rename ✅ done · #7 shared tally ⚠️ partial (types not yet merged)
6. Remaining MEDIUM: #8 `CopyNextSpellCast` spellFilter ✅ done
7. #9 inline single-card `EffectPatterns` helpers ✅ done (all 15 inlined + deleted; borderline 5 kept)
8. LOW cleanup (not done; ordinal still emits "21th"/"22th")
7. #9 inline single-card `EffectPatterns` helpers (not done — all 15 still present)
8. LOW cleanup ✅ actionable items done — dead `drain` removed (the rest of the "dead facade" list
   was stale: all live), `AmassEffect.subtype` made required, ordinal fixed. Convergence + the two
   "wait for 2nd user" items deliberately deferred (see LOW section for rationale).
