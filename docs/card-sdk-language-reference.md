Warning: truncated output (original token count: 331931)
... 279146 bytes omitted ...

# Card SDK Language Reference

A complete catalog of every building block available to card authors in the Argentum
Engine `mtg-sdk`, with a one-line description for each. Designed to be scanned and
searched. For step-by-step authoring workflow see [`api-guide.md`](api-guide.md) (and use the
`add-card` skill); for hard cases see
[`managing-complex-and-rare-abilities.md`](managing-complex-and-rare-abilities.md).

**Maintenance rule:** this document is the canonical SDK catalog. **Every change to the
SDK — new effect, trigger, condition, filter, cost, keyword, dynamic amount, modal
shape, replacement effect, etc. — must update the matching section here in the same
change.** If the entry doesn't fit cleanly in an existing section, add or rename a
section; do not let SDK additions land without a corresponding doc update.

---

## 0. Game formats

`Format` is the runtime rules configuration, separate from deck-construction `DeckFormat`.

- `Format.Commander` — enables per-player commanders, command zones, commander damage, command-zone
  casting tax, and the command-zone replacement choice. Its configuration includes
  `commanderDamageThreshold`, `deckSize`, `startingLife`, `startingHandSize`, and
  `alwaysDivertToCommand`. Nothing in it is per-seat-count: commander damage is tallied per
  *(commander, defending player)* pair, so the same instance runs a 1v1 match and an N-player pod.
- `CommanderPreset` — the life / commander-damage / deck-size tunings a limited Commander lobby
  converts to a `Format.Commander` at match start: `BRAWL` (60/25/16) and `COMMANDER` (60/30/21) pace
  a 1v1 match, `POD` (60/40/21) is paper multiplayer Commander and is what any multiplayer table
  plays at. `toFormat()` does the conversion.
- `Format.TeamVsTeam` — team membership with individual turns and life totals. By default it is a
  normal 20-life format with no commanders. Supplying `commanderDamageThreshold` and `deckSize`
  opts it into the same Commander rules while retaining Team-vs-Team seating and win conditions.
- `Format.TwoHeadedGiant` — shared life, turns, combat, and team loss. It deliberately does not expose
  Commander configuration because Two-Headed Giant and Commander have conflicting starting-life rules.
- `Format.usesCommanders` — capability flag derived from a non-null `commanderDamageThreshold`; engine
  systems use this instead of checking for one concrete format subtype.
- `GameRules` — the *lobby-facing* rules selection (`STANDARD` / `COMMANDER`) that resolves to a
  `Format` at match start, with `usesCommanders` mirroring the engine's flag. It is its own axis:
  orthogonal to `DeckFormat` (what may go in a deck), to where the cards came from (a brought deck,
  a sealed pool or any draft shape — the Commander-Legends 20-card pack is a pool property, not a
  rules one), and to the table. `GameRules.inferred(commanderPackShape, deckFormat)` is the single
  back-compat derivation for lobbies created before the axis existed. Oathbreaker and Pauper
  Commander become values here rather than new draft shapes.

## 1. Top-level card DSL

**Entry points**

- `card("Name") { ... }` — open the builder for a standard card.
- `basicLand("Plains" | "Island" | "Swamp" | "Mountain" | "Forest" | "Wastes")` — shortcut for basic lands (sets
  type line, intrinsic mana ability, supertype). The five colored types get a subtype after the dash and tap for
  their color; `"Wastes"` is the colorless basic — type line `Basic Land` with no subtype, intrinsic `{T}: Add {C}`.
  Supports `collectorNumber`, `artist`, `flavorText`, `imageUri`, `rarity`, and `inBooster` (set `false` to keep an
  art variant defined but exclude it from the draft/sealed deck-building basic pool).
  A set declares one `basicLand(...)` per art variant. Limited deck building hands out exactly **one** printing per
  land type — the set's *standard* art, i.e. its lowest-numbered `inBooster` variant (`BasicLandArt.standardFirst`),
  since paper numbers the plain booster arts inside the main set numbering and appends full-art / extended /
  borderless treatments above the set's card count. So `collectorNumber` is what decides which art a drafted deck
  is played with: give a special treatment a number below the regular art and limited will use the treatment.

**Card builder properties**

- `manaCost: String` — mana cost in `{X}{R}{U}` syntax. Supported pip forms: generic (`{2}`),
  colored (`{R}`), colorless (`{C}`), variable (`{X}`), hybrid (`{W/U}` — either colour),
  Phyrexian (`{W/P}` — colour or 2 life), and monocolored hybrid / "twobrid" (`{2/B}` — two
  generic **or** one mana of the colour; mana value counts the generic side per CR 202.3f).
  Gurmag Nightwatch's `{2/B}{2/G}{2/U}` is the canonical twobrid example.
- `typeLine: String` — full type line including supertypes and subtypes. A `Legendary Instant` /
  `Legendary Sorcery` automatically gets the CR 205.4e casting restriction (can be cast only while
  its controller controls a legendary creature or legendary planeswalker) — the engine enforces this
  from the type line in both legal-action enumeration and the cast handler; no per-card opt-in needed.
- `oracleText: String` — rules text; auto-generated from abilities if omitted.
- `power: Int?`, `toughness: Int?` — base P/T for creatures.
- `dynamicPower`, `dynamicToughness` — characteristic-defining P/T (e.g. `*/*` Tarmogoyf), as
  `CharacteristicValue` properties. Prefer the builder helpers below over assigning them directly.
- `dynamicPower(source, offset?)` / `dynamicToughness(source, offset?)` — set one
  characteristic-defining stat from a `DynamicAmount` with an optional `±` delta. Use these when
  only one stat is dynamic (Duelist of the Mind's `*`/3) or when the two read *different* sources
  (Yavimaya Kavu: power = red creatures, toughness = green creatures).
- `dynamicStats(source, powerOffset?, toughnessOffset?)` — the `*`/`*` cycle: composes
  `dynamicPower` + `dynamicToughness` over one shared source, with optional `±` deltas
  (Tarmogoyf's `toughnessOffset = 1`).
- `startingLoyalty: Int?` — starting loyalty for planeswalkers. Placed as loyalty counters by the
  engine's intrinsic enters-with replacement (CR 306.5b) on *every* battlefield entry — resolving from
  the stack, reanimation, an "exile until this leaves" return, any other put-onto-the-battlefield
  effect, and token copies (loyalty is a copiable value, CR 707.2). Card definitions never place them.
- `startingDefense: Int?` — printed defense for **battles** (`typeLine = "Battle — Siege"`), the number
  in the card's lower right corner (CR 310.4a). Placed as defense counters by the same intrinsic
  enters-with replacement as `startingLoyalty` (CR 310.4b), on every battlefield entry. A battle's
  defense on the battlefield *is* its defense-counter count (CR 310.4c), so nothing reads this field
  once it is in play. See the **Battles** section below; `CardValidator` rejects a battle without it.
- `colorIdentity: String?` — override (normally auto-detected). Treated as authoritative in this repo.
- `colorIndicator: String?` — explicit color indicator (CR 204), e.g. `"B"`. `null` (default) = no
  indicator; the card's color is its mana-cost colors alone. Set it on a face printed with a color
  indicator instead of colored mana symbols — most often a transforming DFC back face with an empty
  mana cost (e.g. The Grim Captain's black back face reads as black despite `manaCost = ""`). The
  indicated colors combine with any mana-cost colors (CR 202.2) and fold into color identity (CR 903.4).
  Prefer this over the older `colorIdentity`-only approximation, which left such faces colourless.
- `auraTarget: TargetRequirement?` — what this Aura enchants. Usually a permanent (`Targets.Creature`),
  but `Targets.Player` makes it an **"enchant player"** Aura: it attaches to a player via
  `AttachedToComponent` (players are entities too), survives state-based actions while that player is in
  the game, and exposes the player through `Player.EnchantedPlayer` / `EventPattern.LifeGainEvent(EnchantedPlayer)`
  and a `takesDamage(binding = ATTACHED)` "whenever enchanted player is dealt damage" trigger. (Grievous Wound.)
  The filter is **not** just a cast-time target check: per CR 303.4c it restricts what the Aura may stay
  attached to, so the engine re-evaluates it against the projected host after every state change and sends
  the Aura to its owner's graveyard once the host stops matching (CR 704.5m). Write the filter to cover
  every type the Aura's *own* effects can turn its host into, exactly as the printed card does — Imprisoned
  in the Moon makes its host a land and so enchants "creature, land, or planeswalker"; an Aura that turns a
  creature into a land while enchanting only `Targets.Creature` would destroy itself on resolution.
- `morph: String?` — morph mana cost (cast face-down).
- `morphCost: PayCost?` — non-mana morph cost.
- `morphFaceUpEffect: Effect?` — effect that fires when this morph turns face up.
- `disguise: String?` — disguise mana cost (CR 702.168): cast face down for `{3}` as a 2/2 **with
  ward {2}**, flip for this cost.
- `disguiseCost: PayCost?` — non-mana disguise cost.
- **`{X}` in a turn-up cost** — `Disguise {X}{3}{W}` (Aurelia's Vindicator) is legal, and the chosen X
  is readable by the card's `Triggers.TurnedFaceUp` ability as **`DynamicAmount.XValue`** — carried
  `TurnFaceUp.xValue` → `TurnFaceUpEvent.xValue` → `TriggerContext.xValue` → the trigger's stack
  object → `EffectContext.xValue`. It is **not `DynamicAmount.CastX`**, which is the X paid to cast a
  *spell*: a disguised card was cast face down for `{3}`, with no X anywhere in that cost, so `CastX`
  reads 0 here. The same `XValue` feeds a `dynamicMaxCount` target cap ("exile up to X other target
  creatures"), snapshotted when the trigger goes on the stack. Turn-up X applies to morph costs
  identically; disguise is simply where it is printed.
- `disguiseFaceUpEffect: Effect?` — the disguise-side sibling of `morphFaceUpEffect`, for the
  "As this creature is turned face up, …" replacement clause (Bubble Smuggler = "put four +1/+1
  counters on it" → `Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 4, EffectTarget.Self)`). It is
  applied **as part of the turn-up special action**, so it doesn't use the stack and can't be
  responded to — that is what separates it from a `Triggers.TurnedFaceUp` ability ("When this
  creature is turned face up, …", Granite Witness / Exit Specialist), which does use the stack.
  Like `morphFaceUpEffect` it rides the *turn-up procedure* (CR 702.37b's megamorph treatment), so
  a card put face down by cloak or manifest and flipped for its mana cost instead of its disguise
  cost does not get it.
- `disguiseCostReduction: CostReductionSource?` — "Disguise {5}{R}. This cost is reduced by {1} for
  each instant and sorcery card in your graveyard" (Fugitive Codebreaker). A **self**-scoped generic
  reduction on this card's own disguise cost, carried on `KeywordAbility.Disguise.costReduction` and
  travelling with the card into the face-down permanent's turn-up procedure — as distinct from
  `SpellCostTarget.MorphActivation`, which is the battlefield-scanned modifier that prices *every*
  player's turn-up (Exiled Doomsayer). Takes the same `CostReductionSource` values a
  `ModifySpellCost` static does, and obeys the same rules: increases first, then reductions
  (CR 601.2f), generic mana only, so a disguise cost's colored pips are a floor. Re-read at every
  price check, so a card that hits the graveyard after the permanent came down moves the price. The
  enumerated turn-up action quotes the reduced cost, so the button matches what is charged.
- `warp: String?` — Warp alt-cost; exiles at end of turn.
- `dash: String?` — Dash alt-cost (CR 702.109); gains haste and returns to owner's hand at the
  beginning of the next end step.
- `evoke: String?` — Evoke alt-cost; sacrifices on ETB.
- `selfAlternativeCost: SelfAlternativeCost?` — generic alternative-cost slot.
- `castTimeCreatureTypeChoice: CastTimeCreatureTypeSource?` — forces a creature-type choice at cast time.
- `cantBeCountered: Boolean` — spell is uncounterable.
- `cantBeCopied: Boolean` — spell can't be copied (CR 707.10); copy effects that name it create no copy (Display of Power).
- `conditionalFlash: Condition?` — gains flash while condition holds.
- `layout: CardLayout` — physical layout shape (see §2).
- `meldResult: Boolean` — this card is the permanent a **meld pair** combines into (CR 701.42) — Chittering Host,
  Brisela, Voice of Nightmares, Hanweir, the Writhing Township, Ragnarok, Divine Deliverance. Set it on the *result*,
  never on the meld parts (those are ordinary cards). A meld result is physically the two parts' back halves, so it is
  never opened, drafted, or put in a deck: the flag drops it from the booster/sealed pool (`BoosterGenerator`), the
  constructed pool (`FormatCardPool`), and random AI decks. Scryfall can't be the source — it reports meld results as
  `booster: true` and format-legal, because the card they're printed on is. The result is still authored as a normal
  card so the corpus carries its characteristics (meld itself is not modelled; the parts' meld triggers are unwired),
  and scenario tests can still put it directly onto the battlefield.

**Ability blocks inside `card { ... }`**

- `triggeredAbility { ... }` — "when/whenever/at" abilities.
- `staticAbility { ... }` — continuous effects.
- `activatedAbility { ... }` — `cost: effect` abilities.
- `loyaltyAbility(±N) { ... }` — planeswalker loyalty abilities.
- `replacementEffect { ... }` — "instead/if … would" replacement.
- `keywords(...)` / `keywordAbility(...)` / `keywordAbilities(...)` — add keyword abilities.
- `spell { ... }` — define the spell payload for instants/sorceries and Adventure / Omen faces.
- `mayBeginGameOnBattlefield()` — the "If this card is in your opening hand, you may begin the game with it on
  the battlefield" ability (CR 103.6a). Reusable across the Leyline enchantment cycles and non-"Leyline of X"
  cards alike (e.g. Leyline Axe). Sets `CardScript.mayStartOnBattlefield = true`. After all mulligans and
  bottoming resolve, the engine walks each player in turn order from the active player and presents a yes/no
  decision per such card in their opening hand; a "yes" routes the card to the battlefield through the standard
  zone-change pipeline before the first turn begins, a "no" leaves it in hand.

### Battles (CR 310)

A **battle** is a permanent that gets *attacked* rather than one that attacks. It is a card type
(`CardType.BATTLE`), not a keyword, and everything about it is intrinsic — a battle card declares only
its type line and its printed defense:

```kotlin
val invasionOfSomewhere = card("Invasion of Somewhere") {
    manaCost = "{2}{B}{B}"
    typeLine = "Battle — Siege"
    startingDefense = 5
    // triggered/activated abilities as usual
}
```

The engine supplies the rest; **do not** write any of it onto the card:

- **Defense is counters.** The battle enters with `startingDefense` defense counters (CR 310.4b) via the
  same intrinsic enters-with replacement that places planeswalker loyalty, so it applies to every entry
  path. On the battlefield its defense *is* that count (CR 310.4c), and damage dealt to it removes that
  many counters (CR 120.3h) rather than being marked. A battle at 0 defense is put into its owner's
  graveyard as a state-based action (CR 704.5v for a Siege, CR 704.5w for any other battle — only a
  Siege gets the reprieve that keeps it alive while its own defeat trigger is on the stack).
- **A protector defends it.** Every battle has a player designated as its protector (CR 310.9), stored in
  `ProtectorComponent` and assigned by the CR 704.5x / 704.5y state-based actions — silently when only one
  player is eligible (every two-player game), otherwise by prompting the battle's controller. A **Siege**'s
  protector must be an opponent of its controller (CR 310.12a); a battle with no battle types is protected
  by its own controller (CR 310.9a). If no player qualifies, the battle is put into its owner's graveyard.
- **Its protector, not its controller, is the defending player** for every rule and effect while it is
  being attacked (CR 310.9d). That asymmetry is the point of a Siege: you cast it, an opponent protects it,
  and *you* attack it. Its protector can never attack it (CR 310.9b) and is the only player who may block
  creatures attacking it (CR 310.9c).

- **A Siege is defeated, not destroyed.** Every Siege has the intrinsic trigger "when the last defense
  counter is removed from this permanent, exile it, then you may cast it transformed without paying its
  mana cost" (CR 310.12b), supplied as `com.wingedsheep.sdk.scripting.Sieges.defeatAbility` and granted by
  `TriggerAbilityResolver` to any permanent whose *projected* types make it a Siege. It is a
  `countersRemovedFrom(counterType = Counters.DEFENSE, lastRemoved = true, binding = SELF)` trigger over a
  `GatherCards(Self) → MoveCollection(→ exile) → MayEffect(CastFromCollectionWithoutPayingCost(castTransformed = true))`
  pipeline. **A Siege card therefore only needs its `startingDefense` and its back face** — write the front
  face's own abilities and nothing else. Two consequences worth knowing: a Siege that never had a defense
  counter (a permanent that became a copy of one) can't have its "last" removed, so CR 704.5v bins it and
  nothing is exiled or cast; and a Siege with no transforming back face is exiled and simply stays there.

Engine-side helpers all live on `com.wingedsheep.engine.mechanics.battle.Battles` (`protectorOf`,
`defenseOf`, `eligibleProtectors`, `canBeAttackedBy`); `ProjectedState.isBattle(entityId)` is the type
check. The client receives a battle's defense in the ordinary `counters` map and its protector as
`ClientCard.protectorId`. A battle is a legal choice for "any target" (CR 115.4) alongside creatures,
players and planeswalkers.

Battles are printed **landscape** — the image is a portrait file holding a sideways card, as a
Room's is. **`CardDefinition.isLandscapePrint` is the single place that decides what counts as
printed sideways** (split layouts including Rooms, plus battles); a future landscape card type is
one clause there and nothing else. It reaches the client as `ClientCard.isLandscapeFace` (in-game:
battlefield, stack, hover preview) and `SealedCardInfo.isLandscape` (sealed / draft / deckbuilder /
cube previews, via the one `landscapeImageRotateDeg` helper). Renderers read the flag rather than
re-deriving orientation from `isRoom` / `cardFaces` / type lines — doing that in three different
ways is exactly how battles ended up rendering sideways. The flag is per *face*: a Siege reports
true, the portrait back face it becomes when defeated reports false, and `backFaceIsLandscape`
carries the other side for the hover preview's flip toggle.

---

## 2. Card faces, layouts, printings, set metadata

**`CardLayout`**

- `NORMAL` — standard single face (default).
- `SPLIT` — two or more halves on one card; combined characteristics apply off-battlefield (CR 709.4c). Used for Rooms,
  Fuse, Aftermath, and the classic Invasion split cards (Pain // Suffering, Stand // Deliver, Wax // Wane). Each half is
  cast independently via `CastSpell.faceIndex`; only the chosen half goes on the stack (CR 709.4). A non-permanent half
  carries its effect in a `face("Name") { spell { … } }` block (with its own `target(...)` requirements); a permanent
  half (Room) carries triggered/activated/static abilities instead.
  - **Room face abilities are door-gated (CR 709.5).** A Room face's abilities function only while that door is
    unlocked. Triggered abilities are scoped to the unlocked face in `TriggerDetector`; **static** abilities —
    continuous effects, `GrantActivatedAbility` (incl. granted mana abilities), and `NoMaximumHandSize` — are folded
    in by the engine helper `RoomFaceStatics.activeStaticAbilities(container, cardDef)`, the single source of truth
    every battlefield static-ability scan reads (continuous-effect projection, clickable granted abilities, the mana
    auto-payer, no-maximum-hand-size). So a static printed on a Room face (e.g. Greenhouse's "Lands you control have
    '{T}: Add one mana of any color.'") works exactly like the same static on a normal permanent, but only once its
    door is unlocked. The baked continuous-effect component is refreshed when a door unlocks (via `RoomDoorUnlocker`),
    the same way a transform re-bakes it. (Replacement effects on a Room face are not yet door-gated — no current card
    needs one.)
- `ADVENTURE` — primary face is a permanent (usually a creature; **may also be a land** — FIN Towns), `cardFaces[0]`
  is an instant/sorcery Adventure (CR 715). Resolving the Adventure exiles the card and grants permission to play
  the primary face from exile — *cast* the creature, or *play* the land for a **land // spell** Adventure
  (`Land — Town // Sorcery — Adventure`, e.g. Ishgard, the Holy See // Faith & Grief). The generic may-play
  permission covers both; from hand a land-primary Adventure offers *play the land* (PlayLandEnumerator) **and**
  *cast the Adventure spell* (`CastSpell.faceIndex = 0`). An `{X}` in the *face's* own mana cost is supported —
  the face's cast action carries `hasXCost`/`maxAffordableX`, so the client opens its X picker and
  `DynamicAmount.XValue` in the face's `spell { }` reads the declared X (An Unexpected Party // At the Door,
  `{X}{2}{W}` "Create X 2/2 red Dwarf creature tokens").
- `OMEN` — primary face is a permanent (creature), `cardFaces[0]` is an instant/sorcery Omen (Tarkir: Dragonstorm).
  Casts exactly like an Adventure (creature face, or Omen via `CastSpell.faceIndex = 0`), but resolving the Omen
  **shuffles the card into its owner's library** instead of exiling it — no cast-from-exile linkage. DSL:
  `card { omen("Name") { spell { … } } }`.
- `MODAL_DFC` — primary characteristics are the front face; the caster picks one face before the card goes on the
  stack and only that face is evaluated (CR 712.11b/712.11c). Two shapes, by what the back face *is*:
  - **Spell back** — `cardFaces[0]`, cast via `CastSpell.faceIndex = 0`. No exile-then-recast linkage: it resolves
    as an ordinary spell (graveyard, or exile when its script sets `selfExileOnResolve` via `spell { selfExile() }`).
    DSL: `card { modalBack("Name") { spell { … } } }`. Flamescroll Celebrant // Revel in Silence.
  - **Permanent back** — a full `CardDefinition` in `backFace`, because it needs P/T, keywords and battlefield
    abilities. Built with `CardDefinition.modalDoubleFacedPermanent(front, back)`, cast via
    `CastSpell(useAlternativeCost = true, alternativeCostType = MODAL_BACK_FACE)` for the **back face's own printed
    mana cost**, and put on the stack *transformed* — the same engine path as disturb. CR 712.3 lets such a card
    also transform, so the front's `{cost}: Transform …` ability reaches the same back face. The back keeps its
    printed mana cost and takes **no** color indicator: per CR 712.8f a modal back face has its own mana value
    (unlike CR 712.8e for nonmodal DFCs, where it stays the front's). The Marvel Super Heroes hero cycle —
    Jennifer Walters // The Sensational She-Hulk, Bruce Banner // The Incredible Hulk, King T'Challa, Tony Stark,
    Monica Rambeau.
  - **Land back** — a full `CardDefinition` in `backFace`, built with
    `CardDefinition.modalDoubleFacedLand(front, back)`. Neither face is ever *cast*: CR 712.12 makes this a
    **play-a-land** choice — *"A player playing a modal double-faced card as a land chooses one of its faces
    that's a land before putting it onto the battlefield. It enters the battlefield with that face up."* So the
    card shows up as **two land plays** in hand (`PlayLandEnumerator` emits one per land face, each named for the
    face it plays) and `PlayLand.asBackFace` says which was taken. Neither face carries a mana cost or a color
    indicator. Once down the permanent has only the played face's characteristics (CR 712.8f) and can never turn
    over (CR 712.9 excludes modal DFCs from transforming); off the battlefield the card is its front face again
    (CR 712.8a). The ten-card Pathway cycle, split across Zendikar Rising (six) and Kaldheim (four) —
    Riverglide Pathway // Lavaglide Pathway, Hengegate Pathway // Mistgate Pathway, and the rest.
- `PREPARE` — primary characteristics are the creature face, `cardFaces[0]` is the **prepare spell** (an
  instant/sorcery) (Secrets of Strixhaven). The card is only ever cast as the creature; the prepare spell is never
  cast from hand. A creature that carries `Keyword.PREPARED` ("This creature enters prepared") becomes prepared on
  enter; one without the keyword (e.g. Leech Collector) only becomes prepared via an effect — `Effects.BecomePrepared(target)`.
  When it becomes prepared, the engine creates a **copy of the prepare spell in exile** that the controller may cast for
  the face's cost — surfaced by the cast-from-exile enumerator as `CastSpell(..., faceIndex = 0)` from `EXILE`.
  Casting the copy unprepares the creature; the copy ceases to exist on resolution. The exile copy persists in
  exile (exempt from the 707.10a phantom-copy SBA) until the source leaves the battlefield or stops being prepared,
  at which point it is cleaned up. A creature already prepared does not re-prepare. DSL: `card { prepare("Name") { spell { … } } }`.

**`CardFace` (SPLIT / ADVENTURE / OMEN / MODAL_DFC / PREPARE)**

- `name` — face name.
- `manaCost` — face mana cost.
- `typeLine` — face type line.
- `script { ... }` — that face's abilities; for instant/sorcery SPLIT halves, Adventures, and modal DFC spell
  faces this includes a `spell { effect = …; target(...) }` block holding the face's effect and target
  requirements (plus `selfExile()` for faces that exile themselves on resolution).
- `keywords` — face-local keywords.
- `imageUri` — face art when it differs from the front (MODAL_DFC backs have their own Scryfall image).

**`metadata { ... }`**

- `rarity: Rarity` — `COMMON | UNCOMMON | RARE | MYTHIC | SPECIAL | BONUS`.
- `collectorNumber: String` — Scryfall collector number.
- `artist: String` — illustrator credit.
- `flavorText: String` — italicized flavor.
- `imageUri: String?` — art URL; auto-fetched from Scryfall if omitted.
- `imageUriByCreatureSubtype: Map<String, String>` — optional display-only alternate art selected
  from a battlefield permanent's projected creature subtypes. Because the server evaluates the
  projected subtype, the art appears and reverts with continuous type-changing effects; the client
  only renders the selected URI.
- `imageRotation: Int` — clockwise degrees to rotate the art when rendered (default `0`). Set `180` for
  flip-layout tokens whose only Scryfall image shows the other face upright — the WOE Role tokens are printed
  two-to-a-card (`Wicked // Cursed`, `Monster // Sorcerer`), so the bottom face (`Cursed`, `Sorcerer`) reads
  upside-down on the single image. Purely cosmetic: flows SDK → `ClientCard.imageRotation` → client CSS transform;
  the engine never reads it.
- `scryfallId: String?` — Scryfall UUID.
- `releaseDate: String?` — `YYYY-MM-DD`.
- `inBooster: Boolean` — part of the draft/sealed product (default `true`; `false` for Special Guests / starter
  exclusives). Gates both the booster pool and the basic-land variants offered during limited deck building.
  It mirrors Scryfall's product-level `booster` flag, so it is *not* the lever for meld results (Scryfall marks
  those `true`) — use the card-level `meldResult` flag in §1 for those.
- `oracleTextOverride: String?` — bypass auto-generated oracle text.

**Reprints** — add a `Printing` row in the new set's `Reprints.kt` and wire it into `MtgSet.printings`. Never duplicate
the `CardDefinition`.

**`Printing`** — a presentation-only row for one printing of a card (oracle identity stays on the `CardDefinition`).
Carries `setCode`, `collectorNumber`, `scryfallId`, `artist`, `imageUri`, `backFaceImageUri`, `releaseDate`, `rarity`,
plus the frame fields:

- `isFullArt: Boolean` — Scryfall full-art treatment.
- `frameEffects: List<String>` — Scryfall `frame_effects` (e.g. `["showcase"]`, `["inverted"]`).
- `borderColor: String?` — Scryfall `border_color` (`"black" | "white" | "borderless"`).
- `isAlternateFrame: Boolean` (derived) — true when the printing is a **showcase** frame
  (`"showcase" in frameEffects`) or **borderless** (`borderColor == "borderless"`). This is the predicate the booster
  variant slot selects on; plain full-art / promo treatments are not counted.

`CardDefinition.withPrinting(printing)` returns a copy presenting that printing — it overlays only presentation
metadata (set code, collector number, art, artist, Scryfall id, and the back-face art for genuine DFCs) and leaves the
card's oracle identity untouched.

**Showcase / borderless in boosters** — a set advertises a per-card variant rate via `MtgSet.boosterVariantChance`
(default `0.0`). When non-zero, `BoosterGenerator` rolls each generated card independently and, on a hit, re-skins it
with one of its `isAlternateFrame` `printings` of the same name (via `applyVariantPrintings` →
`CardDefinition.withPrinting`). The swap is presentation-only: it changes the art shown in the draft/sealed pool, not
the card's rules or its in-game (name-resolved) art. Lorwyn Eclipsed sets `boosterVariantChance = 0.15` and contributes
its showcase/borderless rows via `LorwynEclipsedVariantPrintings` — the play-booster treatments only, with the
collector-only ones (reversible shocklands, Japanese Showcase, Fracture Foil, serialized/headliner chase cards)
excluded.

---

## 3. Costs (`Costs.*`)

Spell mana costs containing Phyrexian symbols (for example `{B/P}`) are paid per pip with either
one mana of that color or 2 life. Manual payment records the chosen life-paid pip colors as a
multiset on `PaymentStrategy.Explicit.phyrexianLifePayments`; the engine validates that those pips
exist in the cost and charges the life through the shared life-payment service.

> **One cost vocabulary (`CostAtom`).** The payable things shared across cost *contexts* — mana, life,
> sacrifice, discard, exile-from-zone, tap, return-to-hand, reveal — are defined **once** in the
> `CostAtom` sealed hierarchy (`scripting/costs/CostAtom.kt`). All three context wrappers carry them via
> an `Atom(atom)` member: `PayCost.Atom`, `AdditionalCost.Atom`, and `AbilityCost.Atom` each hold one
> `CostAtom`, leaving only their genuinely context-specific members on the wrapper
> (`PayCost.OwnManaCost` / `PayCost.Choice`; `AdditionalCost`'s Behold / Blight / Forage / ChooseEntity
> / per-target life / variable exile; `AbilityCost`'s `Free`, `Tap`/`Untap`, the X-variable costs
> (`PayXLife`, `ExileXFromGraveyard`, `TapXPermanents`), the self-referential `SacrificeSelf` /
> `ExileSelf` / `ReturnSelfToHand` / `ExileGrantingPermanent`, counter-removal, `Loyalty`, `Composite`, and named mechanics
> `Forage` / `Blight` / `Craft`). The `Costs.*` facades below are unchanged — they construct the right
> `…Atom(CostAtom.X(…))` for you, so card authoring is identical. A *new* payable thing is one
> `CostAtom` variant + one engine payment branch, available in every context.

- `Costs.Free` — costs nothing (`{0}`).
- `Costs.Tap` — `{T}`; tap this permanent.
- `Costs.Untap` — `{Q}`; untap this permanent.
- `Costs.Exert` — exert this permanent (CR 701.43a, `AbilityCost.Exert`): it won't untap during its
  controller's next untap step. Always payable regardless of tapped/exerted state (701.43b) —
  `canPayAbilityCost` returns `true` unconditionally, and re-exerting before the next untap step is
  a no-op (doesn't stack multiple skips). Backed by a new per-object marker component
  (`ExertedComponent`), not a continuous static ability like `AbilityFlag.DOESNT_UNTAP` — the
  untap step (`BeginningPhaseManager`) both skips untapping an exerted permanent and clears the
  marker *unconditionally* every untap step for that permanent's controller, whether or not it
  actually prevented an untap (2024-06-07 ruling), unlike a stun counter which is only consumed
  when it does. Exposed client-side as `ClientCard.isExerted`. Distinct from the "you may exert
  [this] as it attacks" attack-cost template (701.43d) — that's a separate optional-cost-to-attack
  shape, not an ability cost; only the cost-component shape is implemented so far. First user: Arena
  of Glory (MH3) — `Costs.Composite(Costs.Mana("{R}"), Costs.Tap, Costs.Exert)`.
- `Costs.Mana("{2}{U}")` — pay the given mana cost (string or `ManaCost`).
- `Costs.PayLife(amount)` — pay N life.
- `Costs.PayXLife` — pay X life, where X is the value chosen for the ability's `{X}` mana cost
  (e.g. "{X}{B}, {T}, Pay X life: …" on Krumar Initiate). The X-linked counterpart to
  `Costs.PayLife`; `calculateMaxAffordableX` caps X at the controller's life total — X may go as
  high as their current life, paying down to exactly 0 (legal per CR 119.4; they then lose to a
  state-based action).
- `Costs.Sacrifice(filter)` — sacrifice a permanent matching the filter (may include self).
- `Costs.SacrificeAnother(filter)` — sacrifice a *different* permanent matching the filter.
- `Costs.SacrificeMultiple(count, filter = Any, distinctNames = false)` — sacrifice `count` matching permanents. With `distinctNames = true` the chosen permanents must all have **different names** ("sacrifice three artifact tokens with different names" — Transmutation Font); the cost is only payable when ≥ `count` distinctly-named candidates exist, and the activation always pauses for the selection (it's a real choice even when candidates == count).
- `Costs.SacrificeSelf` — sacrifice this permanent (the ability's source).
- `Costs.SacrificeGrantingPermanent` — sacrifice the permanent that *granted* this activated ability, resolved from the static-grant lookup at activation time (no filter, no prompt). The self-sacrifice sibling of `Costs.ExileGrantingPermanent`: use for an Equipment/Aura whose granted activated ability says "Sacrifice [this permanent]" — e.g. Deconstruction Hammer's "{3}, {T}, Sacrifice Deconstruction Hammer: ...". Per CR 201.5a the name refers only to the specific granting permanent, so this sacrifices exactly that one even with another same-named permanent on the battlefield.
- `Costs.DiscardCard` — discard a card you choose (any card).
- `Costs.Discard(filter, count = 1, atRandom = false)` — discard `count` cards matching the filter.
  When `atRandom` is true the engine picks the cards (no player selection); otherwise the player
  chooses which cards to discard.
- `Costs.DiscardAtRandom(count, filter)` — discard `count` cards chosen at random (Meteor Storm:
  "Discard two cards at random").
- `Costs.DiscardHand` — discard your entire hand.
- `Costs.DiscardSelf` — discard this card (cycling-style).
- `Costs.DiscardLastDrawnThisTurn` — discard the specific card you drew most recently this turn
  (Jandor's Ring: "{2}, {T}, Discard the last card you drew this turn: Draw a card."). The engine
  tracks the per-player most-recently-drawn entity on `GameState.lastCardDrawnThisTurnByPlayer`
  (updated at every `CardsDrawnEvent` emit site during a turn; the last id of a multi-card draw
  wins; cleared at every turn boundary) and discards it automatically — no player selection. The
  cost is unpayable when the controller has not drawn a card this turn or the tracked card has
  since left their hand (matches the Scryfall ruling: "If you do not have the card still in your
  hand, you can't pay the cost").
- `Costs.MillCard` / `Costs.Mill(count)` — mill a card / `count` cards as a cost ("{T}, Mill a card:
  Add {C}" — Deranged Assistant). No player selection: the milled cards are the top of the library.
  Per **CR 701.17b** a player *can't pay a cost that includes milling more cards than their library
  holds*, so — unlike the mill *effect*, which mills as many as possible — the cost is **unpayable**
  on a short library and gates legal-action enumeration (including mana-ability enumeration). A
  `ModifyMillAmount` replacement (Bruvac) still applies to the announced count when the cost is
  actually paid, and the library→graveyard moves go through `ZoneTransitionService`, so mill triggers
  fire exactly as they do for an effect's mill.
- `Costs.ExileTopOfLibrary(count)` — exile the top `count` cards of your library as a cost
  ("{R}, Exile the top ten cards of your library" — Arc-Slogger). The exile twin of `Costs.Mill`:
  no player selection (the cards are the top of the library), and per **CR 118.3** — a player can't
  pay a cost without the resources to pay it fully — the cost is **unpayable** on a short library and
  gates legal-action enumeration, rather than exiling as many as possible the way the exile *effect*
  would. Unlike mill, no `ModifyMillAmount` replacement applies: exiling from the top is not milling
  (CR 701.17a), so the announced count is the paid count. Distinct from the `ExileFromGraveyard`-style
  *chosen*-card costs, which mean "choose N", not "the top N".
- `Costs.ExileSelf` — exile this permanent (or graveyard card, for graveyard-activated abilities).
- `Costs.ReturnSelfToHand` — return this permanent to its owner's hand (Maze's End: "{3}, {T},
  Return this land to its owner's hand: …"). The bounce-to-hand sibling of `Costs.SacrificeSelf` /
  `Costs.ExileSelf`: deterministic, so there is no player selection and no `additionalCostInfo` is
  surfaced to the client — the engine pays it during activation, before the ability goes on the
  stack (CR 601.2h), and the ability still resolves with its source gone. Contrast
  `Costs.ReturnToHand(filter, count, youControl = true)`, the choose-a-permanent bounce cost, which
  deliberately excludes the source and is scoped to permanents you control unless `youControl` is
  turned off. Like the other self-removing costs it snapshots the source's
  counters first, so the resolving effect can still read them via
  `DynamicAmounts.lastKnownSourceCounters(...)`.
- `Costs.ExileFromGraveyard(count, filter)` — exile N matching cards from your graveyard.
- `Costs.ExileXFromGraveyard(filter)` — **variable-count** "exile X cards from your graveyard".
  X *is* the size of the graveyard selection, so activating raises a single `SelectCardsDecision`
  over the matching graveyard cards and the count the player picks becomes the ability's X — read it
  back with `DynamicAmount.XValue`. A `{X}` in the mana cost is therefore optional: **Winter, Cursed
  Rider** ("{2}{U}{B}, {T}, Exile X artifact cards from your graveyard: Each other nonartifact
  creature gets -X/-X") has none and the selection is free (0..matching cards); **Necropolis Fiend**
  ("{X}, {T}, Exile X cards from your graveyard") pays X in mana too, so the mana-X picker fixes the
  count first and the selection is pinned to exactly that many. Selecting nothing is legal and
  settles as X = 0.
- `Costs.ExilePermanentsFixed(count = 1, filter = Any)` — **fixed-count** "exile N permanents you
  control matching `filter`" activated-ability cost (City of Shadows: "{T}, Exile a creature you
  control:"). The counted sibling of the variable-count `Costs.ExilePermanents` below — reach for
  this whenever the card names a specific number and nothing downstream reads an X. Pass a
  controller-scoped filter (`.youControl()`): the battlefield zone map is keyed by **owner**, so the
  filter is what enforces "you control". Its selection is recorded, so a resolving effect can name
  the exiled cards via `CardSource.ExiledAsCost`.
- `Costs.ExilePermanents(filter = Any, minCount = 1, excludeSelf = true, xMeasure = TOTAL_MANA_VALUE, minMeasure = 0)`
  / `Costs.SacrificePermanents(filter = Any, minCount = 1, excludeSelf = false, xMeasure = COUNT, minMeasure = 0)`
  / `Costs.TapPermanentsVariable(filter = Creature, minCount = 1, excludeSelf = false, xMeasure = COUNT, minMeasure = 0)` —
  **variable-count** "exile/sacrifice/tap one or more permanents you control matching `filter`"
  activated-ability cost (CR 601.2b — the player chooses how many, at least `minCount`, as the
  ability is activated). One atom, `CostAtom.VariablePermanents`, with three orthogonal axes; the two
  facades are the named entry points to it. With `excludeSelf` the ability's own source is excluded
  ("one or more *other* …"); leave it false when the source may pay for itself.
  - **`action`** (set by which facade you call) — `EXILE` moves the permanents via the normal
    battlefield→exile transition; `SACRIFICE` puts them in their owners' graveyards through the same
    path a fixed-count sacrifice cost uses, so "whenever you sacrifice" triggers and Food tracking
    fire. Either way Auras fall off, tokens cease to exist, and leaves-the-battlefield triggers fire.
    `TAP` taps them in place, leaving them on the battlefield — the Teamwork N shape, reached through
    `Costs.additional.TapForTotalPower(n)` as a spell's additional cost and through
    `Costs.TapPermanentsVariable(...)` as an activated-ability cost (Mossbridge Troll: "Tap any number
    of untapped creatures you control other than this creature with total power 10 or greater:"). Only untapped permanents are candidates (CR 701.26a) and
    summoning sickness never applies (CR 302.6 is about the `{T}` symbol, not a tap paid as a cost).
  - **`xMeasure`** — how the choice is measured, both as the ability's **X** (read with
    `DynamicAmount.XValue`) and as the quantity a `minMeasure` floor is compared against.
    `TOTAL_MANA_VALUE` sums the chosen permanents' mana values, for "…with total mana value X" cards
    whose target is bounded by `GameObjectFilter.manaValueAtMostX()`; `COUNT` is simply how many were
    chosen, for "…for each permanent sacrificed this way"; `TOTAL_POWER` sums their **projected**
    power, for "…with total power N or more". Either value is fixed at activation and
    stored on the stack, so an X-bounded target is re-validated against it at resolution (CR 608.2b)
    and a resolution-time `XValue` read can't be changed by removal in response.
  - **`minMeasure`** — a floor on the *measure* rather than on the count (0 = none): "any number …
    with total power N or more". Pair it with `minCount = 0` for the free-count shapes; the engine
    marks the whole cost unpayable when every candidate together falls short (CR 601.2h).

  This atom is also a **spell additional cost**, not only an activated-ability cost: teamwork
  (CR 702.194a) rides it through `AdditionalCost.Atom`, paying from
  `AdditionalCostPayment.variableCostPermanents`.

  Backs **Fabrication Foundry** ("{2}{W}, {T}, Exile one or more other artifacts you control with
  total mana value X: Return target artifact card with mana value X or less from your graveyard to
  the battlefield") and **Radiant Lotus** ("{T}, Sacrifice one or more artifacts: Choose a color.
  Target player adds three mana of the chosen color for each artifact sacrificed this way" —
  `Costs.SacrificePermanents(Artifact, excludeSelf = false)` plus
  `AddManaOfChoice(amount = Multiply(XValue, 3), recipient = <the target>)`). The engine drives the
  activation in order: it pauses for the on-battlefield selection (min = `minCount`, max = all
  eligible), computes X, then pauses again for the ability's target — so an over-X target can never
  be chosen. Both pauses precede cost payment, so cancelling either is side-effect-free. Pair with
  `TimingRule.SorcerySpeed` where the card says "Activate only as a sorcery."
- `Costs.Forage()` (ability cost) / `Costs.additional.Forage` (additional cost) — Forage (CR
  701.59a): "exile three cards from your graveyard **or** sacrifice a Food." A *choice* between two
  sub-costs that belongs to the player. All cost-shaped forage payment is unified in the engine's
  `ForageCostResolver`: the enumerators surface the available modes as separate legal actions (the
  same multi-action pattern the "OrPay" costs use — `ExileFromGraveyard` and `SacrificePermanent`
  cost-info, so the client's existing pickers let the player choose the mode *and* which cards/Food),
  and payment honors that choice, only auto-paying a legal mode when none was supplied (AI /
  engine-direct). Used as an activated/mana-ability cost (Camellia, Thornvault Forager), a modal
  additional cost (Feed the Cycle), and the graveyard-cast permission (Osteomancer Adept, where the
  card being cast is excluded from the exile pool). For a "you may forage" *effect* (not a cost) use
  `Patterns.Mechanic.forage(afterEffect?)` instead. Every one of these paths emits the foraged event
  that fires `Triggers.WheneverYouForage` — the cost forms from `ForageCostResolver.pay`, the effect
  form from a marker inside each of its modes — so no context can forage without the payoffs seeing
  it. A forage that was declined, or one no mode was feasible for, emits nothing: forage has no
  "even if you can't" clause.
- `Costs.RevealNotedCreatureType` (ability cost) — "Reveal the creature type you chose" (MKM — A Killer Among Us). Publishes the secret creature type this permanent's controller noted with `Effects.SecretlyChooseCreatureType(...)` (§ effects) and hands it to the ability's own effect as `chosenValues["chosenCreatureType"]` — the key `CardPredicate.HasSubtypeFromVariable` reads, so "if target attacking creature token is the chosen type" is an ordinary `Conditions.TargetMatchesFilter(Filters.creature.withSubtypeFromVariable("chosenCreatureType"))` test rather than new vocabulary. Two rules make it more than a formality. **Only the player who made the note can pay it**: for anyone else the cost is unpayable, so a permanent whose control changed hands stops offering the ability at all (the card's own ruling; CR 702.106d's linkage). And the type is **captured at activation, not at resolution** (CR 113.7a) — the same cost usually sacrifices the source, so by the time the ability resolves the permanent and its note are gone. Activated-ability-only: a spell has no source permanent to carry a note, and every other cost context reports it unpayable rather than half-paying it.
- `Costs.Unattach` (ability cost) — "**Unattach this Equipment**" (RAV — Sunforger). Detaches the
  ability's source from the permanent it is attached to, without moving it between zones (CR 701.3d).
  The cost twin of `Effects.UnattachEquipment` (§ effects): the *effect* has existed since Stolen
  Uniform's rider, the *cost* had not, and it is not a lookalike of any other atom — a sacrifice moves
  zones, a tap can be restored, this does neither. Its affordability gate is the card's own ruling
  ("You can't pay the cost of unattaching Sunforger unless Sunforger is attached to a creature"), so
  the ability is offered as unaffordable while the Equipment sits loose. Payment runs through the same
  `ZoneMovementUtils.unattachEmittingEvent` chokepoint as the effect, so a `Triggers.becomesUnattached`
  trigger cannot tell the two apart. Activated-ability-only: a spell on the stack is attached to
  nothing, so every other cost context reports it unpayable rather than half-paying it. Sunforger is
  `Costs.Composite(Costs.Mana("{R}{W}"), Costs.Unattach)`.
- `Costs.CollectEvidence(n)` (ability cost) / `Costs.additional.CollectEvidence(n)` (mandatory
  additional cost) / `card { collectEvidence(n) }` (the optional **linked** cast cost) — Collect
  evidence N (CR 701.59a): "exile any number of cards from your graveyard with total mana value N or
  greater." Backed by one shared `CostAtom.CollectEvidence`, so the same payable thing serves an
  activated-ability cost (Cryptex, Forensic Researcher, Polygraph Orb), a cast-time additional cost
  (Extract a Confession, Vitu-Ghazi Inspector), and a `PayCost`. All of them route through the
  engine's `CollectEvidenceResolver` — one reachability gate, one legality rule, one exile, one
  `EvidenceCollectedEvent`.

  **The threshold is a floor on total mana value, not a card count.** Exiling *more* than N is legal,
  and mana-value-0 cards (lands) are legal selections contributing nothing — so "enough cards" never
  implies "enough evidence". The picker is therefore a variable-size selection with a sum gate:
  `AdditionalCostData.exileMinTotalWeight` (with the per-card `exileCardWeights` the client sums and
  the `exileWeightUnit` it labels the tally with — the same payload the filtered
  `ExileFromGraveyardForTotal` uses, so there is one sum-gated picker rather than one per cost) and
  `SelectCardsDecision.minTotalManaValue` (the mirror of the existing `maxTotalManaValue` cap) carry
  the floor, and the client shows a running total and keeps Confirm disabled until it is met.

  Per **CR 701.59b** a player who cannot reach N *can't choose to collect evidence*: every
  affordability check fails closed on the summed mana value, so the option is never payable and an
  under-total submission is rejected rather than trimmed. For collect evidence as an *effect* rather
  than a cost, use `Effects.CollectEvidence(n)` (§ effects).

  `Costs.additional.CollectEvidenceForTargetsTotalManaValue` is the one shape whose threshold isn't
  printed — **Urgent Necropsy**'s "collect evidence X, where X is the total mana value of the
  permanents this spell targets". `CostAtom.CollectEvidence.amount` is a `DynamicAmount` for it, and
  accepts exactly the three shapes a cost can be priced from before it is paid: a literal, the
  cast's `XValue`, and `ContextPropertyKey.TARGETS_TOTAL_MANA_VALUE` (an `init` guard rejects the
  rest, so a cost can never carry an amount the cost-time evaluator has no context to read). This is
  the opposite call from `Effects.CollectEvidenceChosenAmount` (§ effects), which stayed a separate
  effect precisely because a player-*chosen* X isn't a `DynamicAmount` at all — a derived one is.

  Two consequences worth knowing, both from the card's own rulings. **X is locked in after targets,
  before payment** (CR 601.2c → 601.2f → 601.2h): the engine prices it from `CastSpell.targets`, so
  it counts what the caster actually chose, not what they could have. And a graveyard that can't
  reach it makes the cast **illegal, not cheaper** (CR 601.2e) — the reachability gate has nowhere to
  fail closed at enumeration time, since the price doesn't exist yet, so the check moves to cast-time
  validation. Client-side that is why the evidence picker runs **after** the targeting step for this
  cost: the enumerator ships `AdditionalCostData.exileWeightPerTarget` (what each legal target would
  add), whose presence is both the per-target price list and the instruction to defer — the same
  deferral `manaCostPerExtraTarget` already does for mana-source selection. With no targets chosen X
  is 0, which collects evidence 0: legal, exiles nothing, and still counts as having collected
  evidence per the 2024-02-02 ruling.

  `Costs.CollectEvidence(n, linkToSource = true)` tethers the cards this payment exiles to the
  *source permanent's* `LinkedExileComponent`, so a later ability on that same permanent can name
  them — "cards exiled **with it**". That is the only thing the flag does: it grants no permission
  and changes no legality, it just leaves a handle for `CardSource.FromLinkedExile()` to gather.
  **Kylox's Voltstrider** ("Collect evidence 6: This Vehicle becomes an artifact creature until end
  of turn" + "Whenever this Vehicle attacks, you may cast an instant or sorcery spell from among
  cards exiled with it") is the whole reason it exists. Off by default, because an ordinary
  collection exiles the cards and forgets them, and an unread pile is state the client would
  otherwise tether to the permanent for no reason. The pile is cumulative across activations and
  prunes itself: a card leaving exile is dropped from every linked-exile pile
  (`ZoneMovementUtils.unlinkFromAllLinkedExiles`), so a spell already cast off the pile is gone from
  it without the card doing any bookkeeping.

  It also serves as the non-mana half of an **alternative** casting cost — Conspiracy Unraveler's
  "You may collect evidence 10 rather than pay the mana cost for spells you cast", i.e.
  `GrantAlternativeCastingCost("{0}", listOf(Costs.additional.CollectEvidence(10)))` (§ casting
  permissions). That path stamps no `ChoiceSlot`, so unlike the linked `card { collectEvidence(n) }`
  form it does **not** make `Conditions.WasEvidenceCollected` read true on the spell being cast.
- `Costs.ExileFromGraveyardForTotal(minTotal, measure, filter = Any)` /
  `Costs.ExileFromGraveyardForColoredSymbols(minSymbols, vararg colors)` — the **unnamed, filtered
  generalization of collect evidence**: "exile any number of `<filter>` cards from your graveyard
  whose summed `<measure>` is `minTotal` or more". Backed by `CostAtom.ExileFromGraveyardForTotal`
  and by the *same* engine implementation collect evidence uses — `GraveyardTotalExileResolver`,
  which `CollectEvidenceResolver` now delegates to, so the two can never drift apart on
  reachability, legality, auto-selection or the exile itself.

  Two axes distinguish it from `Costs.CollectEvidence(n)`, which is otherwise the identical mechanic:
  the **filter** (collect evidence spends *any* graveyard card, CR 701.59a; here non-matching cards
  are never offered), and the **measure** — the per-card quantity that is summed, a `CardMeasure`:
  - `CardMeasure.ManaValue` — mana value (CR 202.3); what collect evidence uses;
  - `CardMeasure.ColoredManaSymbols(colors)` — how many mana symbols of those colours appear in the
    card's **printed** mana cost, counted by `ManaCost.coloredSymbolCount` — the single counting rule
    also behind `CardPredicate.ColoredManaSymbolsAtLeast` and
    `EntityNumericProperty.ColoredManaSymbolCount`, so a group total and a per-card read can never
    disagree (hybrid/Phyrexian pips count for their colour(s), CR 107.4e/f; generic, `{C}` and `{X}`
    count for none).

  `ExileFromGraveyardForColoredSymbols(15, Color.BLACK)` is **Baron Helmut Zemo**'s boast cost,
  "exile any number of black cards from your graveyard with fifteen or more black mana symbols among
  their mana costs" — it derives the colour filter and the pip measure from one list of colours so
  they can't drift. The colour filter and the pip count are *not* redundant: colour is a
  characteristic, the count reads printed pips, so the filter is what keeps a black card with no
  black pip on the right side of the printed wording.

  Same three consequences as collect evidence, for the same reason: **the threshold is a floor on the
  measure, never on the card count** (overpaying is legal, and a matching card whose measure is 0 is
  a legal selection contributing nothing), and the cost **fails closed** — a graveyard that can't
  reach the floor makes the ability not offered at all rather than offered and refused. Measures read
  the **base** card (mana value and printed cost are intrinsic, and a graveyard card has no
  battlefield projection); the *filter* evaluates against projected state like every other cost
  filter.

  Client-side it *is* the collect-evidence picker — one branch, not a parallel one. Both costs ship
  `AdditionalCostData.exileMinTotalWeight` + `exileCardWeights` + `exileWeightUnit` (the unit label
  comes from `CardMeasure.unitLabel`, so the measure names itself and the client never has to know
  which cost it is looking at); only `costType` differs. The weights are server-computed for both,
  because a pip total is a reading of the printed cost the client can't do — and sending mana values
  it *could* have computed is what buys the single code path. The server re-validates the submitted
  selection regardless — a submitted selection that doesn't pay is **rejected**, never silently
  replaced with the engine's own pick.

  Activated-ability cost only today: it is deliberately reported unpayable as a spell's additional
  cost and as a `PayCost`, since no printed card wants either and an offered-then-unpayable cost is
  worse than an absent one.
- `Costs.Craft(filter, minCount = 1, maxCount = null)` — Craft material cost (CR 702.167a): exile
  this permanent **and** exile at least `minCount` (and, when `maxCount` is set, at most `maxCount`)
  cards matching `filter` selected from the combined pool of
  permanents you control and cards in your graveyard. Exact-count crafts ("Craft with artifact" =
  exactly one, "Craft with two creatures" = exactly two) set `maxCount == minCount`; "... or more"
  wordings leave `maxCount = null`. Atomic because CR 702.167a pairs the
  self-exile with the materials-exile in one clause. Records the chosen materials on the source's
  `CraftedFromExiledComponent` so the back face's CDA can read them after the source returns
  transformed. Always combined with `Mana(...)` and used with the
  `Effects.ReturnSelfFromExileTransformed` resolution effect (the `card { craft(filter, cost) }`
  helper wires the whole pattern).
  - **Heterogeneous per-slot craft** — `card { craft(slots = listOf(f1, f2, ...), cost, materialDescription?) }`
    for crafts that name one material of *each* of several kinds ("Craft with a Dinosaur, a Merfolk, a
    Pirate, and a Vampire" — Throne of the Grim Captain). Each slot is filled by exactly **one distinct**
    material, so validating a chosen set is a bipartite perfect-matching problem, not a per-subtype count
    (a single Merfolk Pirate fills only one slot; four Vampires cannot cover four different subtypes). The
    built `AbilityCost.Craft` carries the per-slot filters in `slots` plus a union `filter` (`anyOf` of the
    slots) with `minCount == maxCount == slots.size`, so the flat BF+GY candidate gathering, `canPay`, the
    legal-action enumerator, and the client material overlay work unchanged; the engine layers the
    matching check (`CraftSlotMatching`, Kuhn's augmenting-path — same routine as `BlockPhaseManager`) on
    top in `canPay`, enumeration, and payment. The legal action still ships one flat material list
    (min = max = slot count); an illegal set that can't fill every slot is rejected at payment time
    (no per-slot selection UI).
- `Costs.PutCounterOnSelf(counterType, count = 1)` — "Put a [kind] counter on this permanent" as
  part of the activation cost (Mazemind Tome: "{T}, Put a page counter on this artifact: Scry 1").
  The *accruing* mirror of `Costs.RemoveCounterFromSelf`, and the only cost that adds something
  rather than spending it: it is **always payable**, which is exactly what lets Mazemind Tome reach
  the fourth page counter that exiles it. Paid at activation (so the counter lands before the
  ability resolves, and stays even if the ability is countered), and routed through the normal
  counter-placement chokepoint — the "can't have counters put on it" gate and the placement
  replacements (Hardened Scales, Doubling Season) all apply. Activated-ability scoped: there is no
  additional-cost or `PayCost` form, since a spell on the stack has no permanent to accrue them on.
- `Costs.TapGrantingPermanent` — tap the permanent whose static ability *granted* this activated
  ability, the third member of the granter-cost family alongside `Costs.ExileGrantingPermanent` and
  `Costs.SacrificeGrantingPermanent`. Use for an Equipment/Aura whose granted ability names the
  Equipment itself: Fishing Pole's "Equipped creature has '{1}, {T}, **Tap Fishing Pole**: …'",
  where `Costs.Tap` taps the *host creature* and this taps the *Equipment* — compose both in a
  `Costs.Composite`. Per CR 201.5a the name refers only to the granting permanent, so an
  already-tapped (or departed) granter makes the ability unactivatable even with another same-named
  Equipment untapped elsewhere; the enumerator and `ActivateAbilityHandler` both gate on it.
- `Costs.Composite(c1, c2, ...)` — multiple costs paid together.
- `Costs.RemoveCounters(count = 1, counterType = null, filter = Any)` — remove `count` counters
  from among permanents matching `filter` you control. When `counterType` is set (e.g. `"+1/+1"`),
  only counters of that type are removed; when `null`, counters of any type may be removed in any
  combination (Tayam, Luminous Enigma).
- `Costs.RemoveXCounters(counterType = "+1/+1", filter = Permanent, self = false)` — remove X
  counters, where X is the activated ability's chosen variable-cost value. Use
  `Costs.RemoveXCounters()` (the default) to remove X counters of any type. By default the removal
  is spread across permanents matching `filter` — the player is asked to distribute it, the
  Retribution of the Ancients shape. Pass **`self = true`** for "remove any number of counters from
  ~" (The Astonishing Ant-Man), where the counters come off the ability's own source: that takes
  the direct payment path and caps X by the source's own counters. The filter-based form is not
  merely imprecise for a self-scoped cost, it is *unpayable* — nothing is ever distributed, so
  payment fails with a total of 0. Don't reach for `GameObjectFilter.Permanent.sourceItself()`.

**Spell-level alternatives**

- `selfAlternativeCost` — generic "cast instead for" alt-cost. Optional `condition` gates whether
  the alternative is available at all — the "…rather than pay this spell's mana cost **if**
  <condition>" clause (Blasphemous Edict: `condition = Conditions.CompareAmounts(
  DynamicAmount.AggregateBattlefield(Player.Each, GameObjectFilter.Creature), GTE,
  DynamicAmount.Fixed(13))`). Evaluated with no target/trigger context at two mirrored sites —
  `CastSpellEnumerator` (so the action isn't offered) and `CastSpellHandler.validate` (so an
  authorization can't outlive the enumeration that offered it). Omit for an unconditional
  alternative (Zahid, Djinn of the Lamp).
- `evoke` — pay evoke cost; creature is sacrificed at ETB.
- `morph` — cast face-down for `{3}`-ish.
- `disguise` — cast face-down for `{3}` as a 2/2 with ward {2} (CR 702.168a); same sorcery-speed
  timing and the same `MorphCastEnumerator` as morph.
- `warp` — cast from anywhere; exiled at end of turn.
- `dash` — cast from hand for the dash cost (CR 702.109); gains haste, returned to owner's hand at
  the beginning of the next end step (not exiled — unlike warp, dash has no later recast).
- `conditionalFlash` — flash while condition holds.
- `cantBeCountered` — spell is uncounterable.
- `cantBeCopied` — spell can't be copied (CR 707.10).
- `xManaRestriction = setOf(Color.BLACK, Color.RED)` — "spend only [colors] on X." Restricts which
  mana may pay the `{X}` portion of the cost (the fixed colored/generic portion is unaffected).
  Available in both `spell { }` and `activatedAbility { }` blocks; honored by the mana solver and the
  payment path. Per-color amount spent on X is then readable via `DynamicAmount.ManaSpentOnX(color)`.
  Soul Burn (`spell { xManaRestriction = setOf(Color.BLACK, Color.RED) }`) and Atalya, Samite Master
  (`activatedAbility { xManaRestriction = setOf(Color.WHITE) }`) are the first users.

**`Costs.additional.*`** (wraps `AdditionalCost`) — extra costs paid alongside the mana cost. Card
definitions construct these through the facade, e.g. `Costs.additional.SacrificePermanent(Filters.Creature)`.

- `Costs.additional.ReturnToHand(filter = Filters.Any, count = 1, youControl = true)` — "as an additional cost to cast
  this spell, return [count] permanent(s) you control to its owner's hand" (Fear of Isolation). Paid
  as the spell is cast (CR 601.2f) via `additionalCostPayment.bouncedPermanents`; the enumerator
  surfaces the returnable permanents (a `costType = "ReturnToHand"` cost) and the client picks them
  on the battlefield. The bounce goes through `ZoneTransitionService.moveToZone(…, Zone.HAND)`, so
  attached Auras fall off and tokens cease to exist. Mirrors the sacrifice/tap additional-cost path.
- `Costs.additional.TapForTotalPower(totalPower, filter = GameObjectFilter.Creature)` — "tap any number of
  creatures you control with total power N or more" (Teamwork N, CR 702.194a). A
  `CostAtom.VariablePermanents` with `action = TAP`, `xMeasure = TOTAL_POWER`, `minMeasure = N` and
  `minCount = 0`: the count is free, the power floor is the constraint, and the sum is read from
  **projected** state. The engine advertises the candidates as a `costType = "TapForTotalPower"` cost
  carrying `tapForPowerCreatures` / `tapForPowerRequired` (the same payload crew and saddle use) and
  the client returns the picks in `additionalCostPayment.variableCostPermanents`. Reach for this
  through the `teamwork(n)` DSL helper rather than by hand.
- `Costs.additional.BlightVariable` — "as you cast, you may pay X life" (Blight X); X exposed via
  `DynamicAmount.AdditionalCostBlightAmount`.
- `Costs.additional.PayXLife(minCount = 0)` — "as an additional cost to cast this spell, pay X life."
  The caster declares X at cast time (capped at their current life total) and X is fed to the spell's
  effects through the resolution **X value** — i.e. read it with `DynamicAmount.XValue` and filter with
  `CardPredicate.ManaValueAtMostX` / `manaValueAtMostX()` (Vicious Rivalry: "pay X life; destroy all
  artifacts and creatures with mana value X or less"). A card using this cost must **not** also have an
  `{X}` in its mana cost — both write the same X slot. The client shows a numeric X picker (no target
  step); the AI declares X = 0 by default.
- `Costs.additional.PayLifePerTarget(amountPerTarget)` — "this spell costs N life more to cast for
  each target." Pair with an unbounded `TargetCreature(unlimited = true)` etc.; the engine
  auto-pays `amountPerTarget × action.targets.size` at cast resolution (Phyrexian Purge).
- `Costs.additional.PayLifeEqualToManaValueOfSpell` — auto-pays life equal to the cast spell's own
  mana value. The substitute cost for "pay life equal to its mana value rather than pay its mana cost"
  (Valgavoth, Terror Eater; Bolas's Citadel-style effects). Pair it with a play-from-exile grant whose
  mana cost is waived — `GrantMayCastFromLinkedExile(withoutPayingManaCost = true, additionalCost =
  Costs.additional.PayLifeEqualToManaValueOfSpell)` — so the only cost paid is the life. The amount is
  read from the cast card's mana value, checked at cast time (CR 119.4 — must have at least that much life).
- `Costs.additional.OrPay(cost, alternativeManaCost)` — **cost-vs-mana**: "as an additional cost
  to cast this spell, \<pay this cost\> **or** pay {mana}". One `AdditionalCost.OrPay` covers the
  whole "do X or pay {N}" family, parameterized by the `AdditionalCost` on the non-mana leg; the
  named shapes below are its printed wordings and are one-line facades over it. The enumerator
  offers up to two cast paths: the **leg path** (base cost + the leg cost's ordinary selection
  prompt — the same `costType` that cost emits standing alone, so no new client UI) and the **pay
  path** (base cost + `alternativeManaCost` folded in). The leg path is offered only when the board
  affords the leg's selection, so with nothing to pay it only the pay path is castable — but the
  cost as a whole is always payable. Which leg was taken is recovered at payment time from **which
  `additionalCostPayment` field the client populated**, so `cost` must be a selection-carrying cost
  — a `Behold`, or an atom cost over `Sacrifice` / `Discard` / `ExileFrom` / `TapPermanents` /
  `ReturnToHand` / `RevealFromHand` — and must not share its field with another additional cost on
  the same card.
  `CastSpellHandler.reduceCostAlternatives` then rewrites the whole cost to that plain leg cost (leg
  paid) or drops it (pay path), so validation, payment, LKI snapshots, behold's pipeline storage and
  discard tracking (CR 701.8) reuse the leg's own paths verbatim. `BlightOrPay` stays a separate
  type only because there is no standalone blight `AdditionalCost` for `OrPay` to wrap.
- `Costs.additional.BeholdOrPay(filter = Filters.Any, alternativeManaCost, storeAs = "beheld")` —
  "behold a [filter] or pay {mana}" (Lys Alana Dignitary). `OrPay(AdditionalCost.Behold(filter,
  storeAs = storeAs), …)`; the behold path surfaces as a `costType = "Behold"` cost over one
  candidate pool spanning battlefield *and* hand, and stores the chosen cards under `storeAs` for
  downstream costs/effects exactly as a plain `Behold` does.
- `Costs.additional.RevealFromHand(filter = Filters.Any, count = 1)` — "as an additional cost to
  cast this spell, reveal a [filter] card from your hand". `Atom(CostAtom.RevealFromHand(filter,
  count))`; surfaces as a `costType = "RevealCard"` cost over `validRevealTargets` (the caster's
  hand, minus the spell being cast) and the picks come back as
  `additionalCostPayment.revealedCards`. **Paying moves nothing** — CR 701.20b: revealing a card
  doesn't cause it to leave the zone it's in — so the revealed card is still in hand and still
  castable afterwards; the payment is a `CardsRevealedEvent` and nothing else. As a *mandatory*
  cost it fails closed: with no matching card in hand the spell isn't castable at all.
- `Costs.additional.RevealFromHandOrPay(filter = Filters.Any, alternativeManaCost, count = 1)` —
  "reveal a [filter] card from your hand or pay {mana}" (Lorwyn's tribal cycle: Wren's Run
  Vanquisher, Silvergill Adept, Goldmeadow Stalwart, Squeaking Pie Sneak, Flamekin Bladewhirl).
  `OrPay(RevealFromHand(filter, count), …)`. **Not `BeholdOrPay`**: CR 701.4a defines "behold a
  [quality]" as "reveal a [quality] card from your hand **or** choose a [quality] permanent you
  control", so behold's candidate pool also spans the battlefield and would wrongly let a permanent
  pay a hand-only reveal. That width is also why the reveal has its own `revealedCards` payment
  field rather than sharing `beheldCards` — on an `OrPay` leg the populated field is the only thing
  telling the engine which leg the caster took.
- `Costs.additional.ExileFromGraveyardOrPay(exileCount, alternativeManaCost, filter = Filters.Any)`
  — "exile N cards from your graveyard or pay {mana}" (Soaring Stoneglider: "exile two cards from
  your graveyard or pay {1}{W}"). `OrPay(Atom(CostAtom.ExileFrom(GRAVEYARD, filter, exileCount)), …)`;
  the exile path surfaces as a `costType = "ExileFromGraveyard"` cost and is offered only when the
  graveyard holds at least `exileCount` matching cards. That `costType` pins the client's picker to
  the graveyard, so an `ExileFrom` leg on any other zone is declined (pay path only).
- `Costs.additional.SacrificeOrPay(filter = Filters.Any, alternativeManaCost, count = 1)` —
  "sacrifice a [filter] or pay {mana}" (Louisoix's Sacrifice: "sacrifice a legendary creature or pay
  {2}"). `OrPay(Atom(CostAtom.Sacrifice(filter, count)), …)`; the sacrifice path surfaces as a
  `costType = "SacrificePermanent"` cost (the on-battlefield picker Natural Order uses) and is
  offered only when you control at least `count` matching permanents.
- `Costs.additional.DiscardOrPay(alternativeManaCost, filter = Filters.Any, count = 1)` — "discard a
  [filter] or pay {mana}" (Pumpkin Bombardment: "discard a card or pay {2}").
  `OrPay(Atom(CostAtom.Discard(count, filter)), …)`; the discard path surfaces as a `costType =
  "DiscardCard"` cost (the hand picker Force of Will uses), excludes the spell being cast, and is
  offered only when you hold at least `count` other matching cards. The discard-as-cost still feeds
  the turn's discard tracking (CR 701.8), so it counts toward
  `DynamicAmounts.cardsDiscardedThisTurn()` / `Conditions.YouDiscardedACardThisTurn` /
  `Conditions.YouDiscardedThisCardThisTurn` (Mayhem).
- `Costs.additional.Choice(vararg options)` — **cost-vs-cost**: "as an additional cost to cast this
  spell, pay exactly one of `options`" (Souls of the Lost: *"discard a card **or** sacrifice a
  permanent"*). The general, parameterized form of `Forage` — each option is itself an
  `AdditionalCost` (compose the `Sacrifice` / `Discard` / `ExileFrom` atoms). Distinct from the
  `OrPay` family (`OrPay` and its `SacrificeOrPay` / `DiscardOrPay` / `ExileFromGraveyardOrPay` /
  `BeholdOrPay` wordings, plus `BlightOrPay`): those
  fold a **mana** alternative into the spell's cost, whereas `Choice` is for options that are each
  independently payable **non-mana** costs (no mana-cost change). The enumerator emits **one cast
  action per payable option** (`CastSpellEnumerator.expandChoiceAdditionalCosts` +
  `ChoiceCostResolver`), each carrying that option's existing picker (`SacrificePermanent` /
  `DiscardCard` / `ExileFromGraveyard`) — so the caster picks the sub-cost by choosing which action to
  play, with **no new client UI**. The plain (un-expanded) base action is dropped, since a mandatory
  choice cost can't be skipped. At payment time `CastSpellHandler.reduceCostAlternatives` collapses the
  `Choice` to the single option the caller populated (or, for a server-initiated free/AI cast with no
  payment, the first payable option — mirroring `ForageCostResolver`'s engine-direct fallback), so
  validation, application, and the free-cast selection pause all handle it as a plain atom. Keep the
  options on **distinct** payment fields (sacrifice vs. discard vs. exile) — two options consuming the
  same field can't be told apart by the payment alone.
- `Costs.additional.RemoveCounters(count, counterType = null, filter = Any)` — "as an additional
  cost to cast this spell, remove `count` counters from among permanents matching `filter` you
  control." When `counterType` is set (e.g. `"+1/+1"`), only counters of that type are removed;
  when `null`, counters of any type may be removed in any combination (Eladamri, Korève Domain).

**`Costs.pay.*`** (wraps `PayCost`) — payable costs used by [`PayOrSufferEffect`](#15-replacement-effects) ("do X
unless you Y") and by `morphCost` (non-mana face-up cost). Distinct from `AbilityCost` / `Costs.*`
which model an ability's activation cost; `PayCost` models a single cost the engine prompts the
player to pay against an alternative consequence.

**`PayOrSufferEffect` prompts unconditionally** — it does not check whether its `suffer` effect would
actually do anything. A branch whose suffer reads a collection that may be empty must therefore be
wrapped in a `ConditionalOnCollectionEffect`, or the player is asked to pay for a consequence that
would be a no-op (Wand of Ith splits the revealed card into a land pile and a nonland pile and gates
each ransom on its own pile). The resolving pipeline's collections *are* carried across the
pay-or-decline pause, so a suffer effect can name them on either answer.

`PayOrSufferEffect(cost, suffer, player = EffectTarget.Controller)` defaults to charging the ability's
controller, but **`player` may route the decision *and* the payment to any other player** — most
usefully `EffectTarget.PlayerRef(Player.TriggeringPlayer)` on a death trigger, which resolves to the
dying permanent's last-known controller. The `suffer` consequence still resolves under the **ability's
controller** (not the payer), so `EffectTarget.Controller` inside it means *you*: Meathook Massacre II's
"whenever a creature an opponent controls dies, *they* may pay 3 life. If they don't, return that card
under *your* control" is `PayOrSufferEffect(player = PlayerRef(TriggeringPlayer), cost = Costs.pay.PayLife(3),
suffer = Composite(Move(TriggeringEntity → battlefield, controllerOverride = Controller), AddCounters(finality)))`.

**`consequenceDescription`** is the prompt's words for what happens if the cost isn't paid ("Pay {2}
or **…**?"), and every `Costs.pay` variant's prompt renders it. Null generates the clause from
`suffer`, which is right while the payer is the ability's controller. It stops being right the moment
`player` routes the question elsewhere: an effect description is an imperative fragment addressed to
*the controller*, so `GainControlEffect`'s "gain control of target" asked of an opponent offers them
the theft they are the subject of — Scarwood Bandits asked its victim "Pay {2} or gain control of
target for as long as this creature remains on the battlefield?". The unresolved `target` and `this
creature` are the same fragment's other half: placeholders that read as the card's text rather than
as this game's board. **Write it out whenever `player` isn't the controller**, or whenever the
generated clause would name a placeholder the player can't resolve. (Same defect and same remedy as
an optional trigger's `description` becoming its "may" prompt, above.)

Non-mana `morphCost` payment is routed through the shared engine `CostPaymentService`, so **every
`Costs.pay` variant below works as a morph cost** (including `Tap` / `Choice` / `OwnManaCost`): turning
the creature face up pauses for the cost-specific decision and only flips once the cost is paid.
(Mana morph costs keep their own up-front payment — explicit mana-source selection, X, auto-tap
preview — in the turn-face-up handler.)

- `Costs.pay.Atom(CostAtom)` — the generic lift of any shared payable thing into this context, the
  mirror of `AbilityCost.Atom` / `AdditionalCost.Atom`. Reach for a named factory below where one
  exists; this is what a caller holding a `CostAtom` it did not build itself needs, and it is what
  makes the variable-count costs above payable ("…sacrifice this creature unless you sacrifice any
  number of creatures with total power 12 or greater" — Phyrexian Dreadnought).
- `Costs.pay.Mana(ManaCost)` — pay mana (auto-taps lands via the solver). "...unless you pay {U}{U}"
  (Vaporous Djinn).
- `Costs.pay.OwnManaCost` — pay the mana cost of the permanent the cost applies to (its *own* mana
  cost, read from `CardComponent.manaCost` at payment time). Use for granted abilities like
  Essence Leak ("...sacrifice this permanent unless you pay its mana cost"), where the affected
  permanent — not a fixed cost — owns the mana cost. The engine resolves it into a concrete
  `Costs.pay.Mana` against that permanent before prompting.
- `AbilityCost.AttachedPermanentManaCost` — pay the mana cost of the permanent this Aura/Equipment is
  **attached to**: Merseine (FEM), "Pay enchanted creature's mana cost: Remove a net counter from this
  Aura." The activated-ability sibling of `Costs.pay.OwnManaCost`, and lowered the same way — into a plain
  mana `Atom` against the attached permanent's printed cost before anything prices or pays it, so every
  downstream path (affordability, enumeration, the mana solver, the prompt) sees a uniform shape. An
  unattached source, or one attached to a permanent with no mana cost, prices as {0}.
- `Costs.pay.PayLife(amount)` — pay N life; offered only when the player's life total is at least N
- `Costs.pay.PayDynamicLife(amount: DynamicAmount)` — "pay life equal to **&lt;rule&gt;**", where the
  card names a rule rather than a number (**Wand of Ith**: "…unless they pay life equal to its mana
  value"). Lowered to a concrete `PayLife` inside `PayOrSufferExecutor`, the one place holding the
  `EffectContext` the amount may need — a pipeline-scoped amount such as `ManaValueSumOfCollection`
  is unreadable anywhere else. Consequently it is **PayOrSuffer-only**: used as a spell or ability
  cost it reports unaffordable, because affordability there has to be known before any context
  exists. Same idea as `PayCost.OwnManaCost`, which is likewise resolved at payment time.
  (CR 119.4). "...unless you pay 3 life."
- `Costs.pay.Discard(filter = Any, count = 1, random = false)` — discard cards matching `filter`.
  Random variant prompts a yes/no and the engine picks the discards (Pillaging Horde).
- `Costs.pay.DiscardHand` — discard your **entire** hand. Nothing is selected (every card goes), so
  it prompts a yes/no, and it is **always affordable**: an empty hand discards nothing, and a cost
  of nothing is a cost you can pay (CR 118.3). "Counter target spell unless its controller discards
  their hand" (Perplex) is `PayOrSufferEffect(cost = Costs.pay.DiscardHand, suffer =
  Effects.CounterSpell(), player = EffectTarget.TargetController)`. The shared-vocabulary twin of
  `Costs.DiscardHand`, the activated-ability spelling of the same payable thing.
- `Costs.pay.Sacrifice(filter = Any, count = 1)` — sacrifice permanents you control matching
  `filter`. **The source is included when it matches** — "sacrifice it unless you sacrifice an
  artifact" on an artifact creature may name that creature. "...unless you sacrifice three Forests"
  (Primeval Force).
- `Costs.pay.SacrificeAnother(filter = Any, count = 1)` — the printed-"another" variant; same cost
  with the source excluded.
- `Costs.pay.Exile(filter = Any, zone = HAND, count = 1)` — exile cards from `zone` matching
  `filter`. "...unless you exile a blue card from your hand."
- `Costs.pay.Tap(filter = Any, count = 1)` — tap untapped permanents you control matching `filter`.
  **The source is included when it matches and is untapped** — Public Thoroughfare's and Command
  Bridge's rulings both allow tapping the land itself when something untapped it in response.
  Tapping each emits a `TappedEvent` so "becomes tapped" triggers fire.
  "...unless you tap an untapped permanent you control" (Command Bridge).
- `Costs.pay.TapAnother(filter = Any, count = 1)` — the printed-"another" variant; same cost with
  the source excluded.
- `Costs.pay.Atom(CostAtom.Mill(count))` — mill from the top of your own library. No named factory:
  it arrives through `Costs.pay.Atom`. "...sacrifice this creature unless you mill two cards"
  (Deep Spawn, the only printed instance). Milling from the top selects nothing, so the prompt is a
  plain yes/no. **CR 701.17b** — a player can't pay a cost that mills more cards than their library
  holds, so a library shallower than `count` makes this unpayable and the `suffer` half happens
  with no prompt at all. Mill *replacement* effects apply when the payment is made, not to the
  announced count.

The word **"another"** is the only thing that decides self-exclusion, and it lives on the cost atom
(`excludeSelf`). Every path that asks "which objects could pay this?" reads that flag and nothing
else — `PayOrSufferExecutor`, `AnyPlayerMayPayExecutor` (and the continuation that asks the next
player), `CostHandler`, both enumerators, and `CostPaymentService.selectionCandidates`, the single
candidate-domain helper behind the last one's affordability *and* prompt. None of them applies a
blanket exclusion: a self-inclusive cost stays payable on a board holding only the source, and a
self-exclusive one is never offered the source in the first place.
- `Costs.pay.Choice(options)` — present several `PayCost`s; player picks one (or the suffer effect).
  Unaffordable options are hidden. "...unless they sacrifice a nonland permanent or discard a card."
- `Costs.pay.ReturnToHand(filter, count = 1, youControl = true)` — return permanents to their
  owner's hand. Wired into `PayOrSufferEffect` (Drake Familiar: "sacrifice it unless you return an
  enchantment to its owner's hand") as well as `morphCost`. Set `youControl = false` for the cards
  whose ruling is control-agnostic — Drake Familiar's says *any* enchantment on the battlefield
  qualifies, an opponent's included, and that an untargetable one does too because the ability never
  targets. Selecting nothing is a decline, so the suffer half runs; with no legal permanent at all
  there is no prompt. The source is always out of the pool.
- `Costs.pay.RevealCard(filter, count = 1)` — reveal a card from hand matching `filter`. Currently
  only consumed by `morphCost`; not yet wired into `PayOrSufferEffect`.
- `Costs.pay.RemoveCounters(count, counterType = null, filter = Any)` — remove `count` counters
  from among permanents matching `filter` you control. When `counterType` is set (e.g. `"+1/+1"`),
  only counters of that type are removed; when `null`, counters of any type may be removed in any
  combination.
- `Costs.pay.PutCountersOnPermanent(counterType, count = 1, filter = Permanent)` — put counters on a
  permanent **the payer controls**; the selected-permanent sibling of `PutCountersOnSelf`. Unlike that
  one — always payable, because it needs no selection — this is **unpayable when the payer controls no
  matching permanent**, which is the whole point of the punisher clause it models: Tourach's Chant (FEM)
  deals 3 damage to a player "unless the player puts a -1/-1 counter on a creature they control", and a
  player with an empty board simply takes the damage. The payer picks which of their permanents takes it.
- `Costs.ExileFromSingleGraveyard(count, filter)` is the "from a single graveyard" wording. The two
  flags it sets live on the underlying `CostAtom.ExileFrom`, **not** on `Costs.pay.Exile` — that
  facade is `(filter, zone = HAND, count)` and offers no way to reach them, so a `PayOrSuffer` cost
  cannot express this shape today. `anyPlayersZone = true` widens the pool from the payer's own zone to **every** player's (each
  card leaves from, and is exiled by, its own owner's zone; the payer only chooses). `singleZone = true`
  then adds the other half: all `count` cards must come out of **one** player's zone. Both together are
  Night Soil (FEM), "{1}, Exile two creature cards from a single graveyard" — so a board with one
  creature card in each of two graveyards pays nothing, and `CostHandler` rejects a payment whose cards
  span owners. A graveyard holding fewer than `count` matches is not offered at all.

---

## 4. Effects (`Effects.*`)

Atomic effect factories. For library/zone manipulation, prefer the pipelines in §5.

### Damage

- `DealDamage(amount, target)` — deal fixed/dynamic damage.
- `DealDamageExcessToController(amount, target)` — deal damage to a creature; any amount beyond
  lethal (CR 120.4a) is dealt to that creature's controller instead (the creature is marked only with
  the lethal portion). Backed by `DealDamageEffect.excessToController`. Used by Gandalf's Sanction.
- `DealXDamage(target)` — deal X damage (spell's X).
- `AmplifyNoncombatDamageThisTurn(bonus)` — install an until-end-of-turn replacement (CR 616): every
  source you control deals `bonus` *additional* noncombat damage to any permanent or player this turn.
  Combat damage is unaffected; no opponent restriction. `bonus` (a `DynamicAmount`) is resolved once at
  resolution and baked in (typically `DynamicAmount.XValue` from an `{X}` cost); multiple installs stack
  additively. Read at damage time by the engine's static-amplification path, then cleaned up at end of
  turn. Distinct from the opponent-only, permanent-tied `NoncombatDamageBonus` static. Taii Wakeen,
  Perfect Shot: `{X}, {T}: … it deals that much damage plus X instead.`
- `DoubleDamageToPlayer(target, duration = UntilYourNextTurn)` — install a duration-bounded replacement
  (CR 616) that *doubles* all damage — any source, combat or noncombat — dealt to `target` (a player,
  e.g. `EffectTarget.PlayerRef(Player.TriggeringPlayer)`) and to any permanent that player controls. The
  player is resolved once at resolution and baked into a floating effect scoped to that player, so the
  doubling outlives the source that created it (CR 611.2) and lasts the whole `duration`. Read at damage
  time by the engine's static-amplification path — combat damage is doubled per already-assigned recipient
  (assignment/division happens before doubling) and stays attributed to the original source. Two installs
  on the same player each double once (⇒ ×4). Backs the "Stagger" ability word — Lightning, Army of One:
  "Whenever Lightning deals combat damage to a player, until your next turn, if a source would deal damage
  to that player or a permanent that player controls, it deals double that damage instead." Distinct from
  the permanent-hosted, "you"/"opponent"-relative `DoubleDamage` replacement (Furnace of Rath).
- `Fight(target1, target2, excessDamageVariable?)` — two creatures each deal damage equal to their power
  to each other (CR 701.14). When `excessDamageVariable` is set, the excess damage (CR 120.4a, deathtouch-
  and marked-damage-aware) that `target1` deals **to `target2`** is stored into that pipeline number
  variable for a following effect to read via `DynamicAmount.VariableReference` — e.g. The Last Agni Kai:
  `Fight(yours, theirs, "excess") then AddMana(RED, VariableReference("excess"))`.
- `Effects.DividedDamage(total, minTargets, maxTargets, dynamicTotal?)` — "N damage divided as you
  choose among target ..." The targets come from the ability's target requirement; pair with
  `TargetCreature(count, minCount)` (Forked Lightning, Skirk Volcanist) or, for "any number of target",
  a `TargetObject(unlimited = true, dynamicMaxCount = ..., filter = ...)`. Set `dynamicTotal` (a
  `DynamicAmount`) for totals computed when the ability resolves/goes on the stack — Ureni, the Song
  Unending: `dynamicTotal = DynamicAmounts.landsYouControl()`. Works for creatures and planeswalkers
  (`GameObjectFilter.CreatureOrPlaneswalker`); zero chosen targets ⇒ no-op.

  **Always cap the target count at the total.** Each chosen target must be assigned at least 1 damage
  (CR 601.2d), so a requirement that lets the player pick more targets than there is damage leaves them
  with no legal division to submit. Pass `dynamicMaxCount` alongside `unlimited` — a
  `DynamicAmount.Fixed(total)` for a fixed total (Chandra, Flameshaper: 8 damage ⇒ at most 8 targets),
  or the same `DynamicAmount` that drives `dynamicTotal` when the total is board-derived (Ureni). The
  cap and `unlimited` compose: the client is offered `min(legal targets, cap)`.

  **The division is chosen at announcement, never at resolution** (CR 601.2d). It rides on the action
  (`CastSpell.damageDistribution` / `ActivateAbility.damageDistribution`), is locked onto the stack
  object, and is honored verbatim when the effect resolves — so a target removed in response costs that
  target's share and the survivors keep exactly what they were assigned (the total is *not* re-divided).
  The engine surfaces `requiresDamageDistribution` / `totalDamageToDistribute` / `minDamagePerTarget` on
  the legal action for both spells and activated abilities, and the client collects the division right
  after targeting. When no division is supplied (a single target, or a non-interactive controller such
  as the built-in AI) the executor deals the whole total to a lone target, or asks for the division at
  resolution via a `DistributeDecision`.
- `DamageCantBePreventedThisTurn()` — "Damage can't be prevented this turn." Turn-scoped one-shot that
  sets a `GameState` flag (cleared at the next turn boundary), shutting off all damage prevention for
  the rest of the turn — prevention shields, prevention/replacement-of-damage effects, and protection's
  prevention clause are ignored (CR 615.6). The static, permanent-hosted equivalent is the
  `DamageCantBePrevented` replacement effect (Sunspine Lynx); use this effect when a spell/ability needs
  the shutoff without a permanent on the battlefield (Fear, Fire, Foes!).
- `DamageCantBePrevented(appliesTo = EventPattern.DamageEvent(...))` — the static, permanent-hosted
  replacement, **scoped by its `appliesTo` pattern**. Left at the default (`source` and `recipient` both
  `Any`) it is the printed global "Damage can't be prevented" (Sunspine Lynx, Leyline of Punishment).
  Narrow the pattern for a card that names one end of the damage instance: **Excruciator**'s "damage that
  would be dealt by this creature can't be prevented" is
  `DamageCantBePrevented(EventPattern.DamageEvent(source = SourceFilter.Self))`, which leaves every other
  source's damage preventable. `DamageUtils.isDamagePreventionDisabled(state, recipientId, sourceId)`
  matches the pattern against the concrete damage instance through the same `damageSourceMatches` /
  `damageRecipientMatches` pair every other damage replacement uses, so a filter supported by one is
  supported by all. Callers that don't know both ends of the instance get the *unscoped* answer only —
  a scoped effect never blanks a shield it might not cover.
- `DamageToTargetCantBePreventedThisTurnEffect(target)` — the **per-recipient** form: "Damage that
  would be dealt to that creature this turn can't be prevented **or dealt instead to another
  permanent or player**" (Whippoorwill). Stamps a turn-scoped marker on the recipient, cleared at
  cleanup. One marker covers both halves of the clause: `DamageUtils.isDamagePreventionDisabled(state,
  recipientId)` consults it wherever prevention is applied (shields, prevention replacements, and —
  checked per assignment, not as an early-out — protection's prevention clause), and the redirection
  check is skipped for a marked recipient.
  - Reach for this rather than the global `DamageCantBePreventedThisTurn()` whenever the card names a
    creature: the global form blanks every prevention effect in the game for the turn, which is a
    very different card.
  - It is a marker rather than a replacement effect on purpose — "can't be prevented" is a rules
    modification (CR 615.9), not itself a replacement, so it cannot compete in the replacement-effect
    gather and has to be read where prevention is *applied*.

### Life

- `GainLife(amount, target?)` — target gains life (default: controller).
- `PayDynamicLife(amount: DynamicAmount, payer?)` — pay life equal to a `DynamicAmount` (e.g.
  "pay life equal to its power" via `EntityProperty(Triggering, Power)`), evaluated at resolution.
  The dynamic, payer-parametric twin of the fixed `PayLifeEffect`; use it as the `cost` of an
  `OptionalCostEffect` (`Gate.MayPay`) so the same amount can also feed the `ifPaid` effect. A
  non-positive evaluated amount pays nothing and still counts as paid (CR 119.4).
- `LoseLife(amount, target)` — target loses life.
- `DrainLife(amount, from = EachOpponent, to = Controller)` — each player in `from` loses `amount`
  life, then `to` gains life equal to the total *actually* lost (each loss honors `ModifyLifeLoss`
  replacements) as a single life-gain event — "Each opponent loses X life. You gain life equal to
  the life lost this way." (Exsanguinate). Prefer this over `LoseLife + GainLife` whenever the gain
  is worded "equal to the life lost this way".
- `SetLifeTotal(amount, target)` — set target's life total to N.
- `ExchangeLifeAndStat(target, stat, player)` — swap a player's life total with a creature's power or
  toughness (CR 701.12g). `stat` is `CreatureStat.POWER` (default, Evra, Halcyon Witness) or
  `CreatureStat.TOUGHNESS` (Tree of Perdition); `player` defaults to the controller, pass a
  `ContextTarget` for "target opponent's life total". The creature's *projected* stat is what the
  player receives, while the creature's **base** stat is set at Layer 7b — so counters, Auras, and
  Equipment apply on top of the new value. No-op if the creature has left the battlefield.
- `ExchangeLifeTotals(target, drawEqualToLifeLost)` — swap the controller's life total with `target`
  player's (CR 701.12c): each player gains/loses the life needed to reach the other's former total,
  applied through the shared gain/lose-life primitives so gain prevention/replacements and loss
  modification apply and gain/loss triggers fire. With `drawEqualToLifeLost = true`, the controller
  then draws a card for each point of life they **actually lost** in the swap (Mister Negative). Wrap
  the whole thing in `MayEffect` for "you may exchange".
- `LoseHalfLife(roundUp, target, lifePlayer?)` — lose half of life total (round up/down).
- `LockLifeGain(target?, duration?)` — "target player can't gain life" for `duration` (default
  `Duration.Permanent` = rest of the game; `EndOfTurn` / `UntilYourNextTurn` also honored). A one-shot
  effect that tags the player with `CantGainLifeComponent`, so the lock is independent of any source —
  unlike the `PreventLifeGain` *replacement* (§11), which ends when its permanent leaves play.
  Non-player targets are a no-op, so it composes after a "deal damage to any target" rider (Screaming
  Nemesis). Checked by `DamageUtils.isLifeGainPrevented`.
- `LoseGame(target, message?)` — target loses the game.
- `RemoveMaximumHandSize(target?)` — "target has no maximum hand size for the rest of the game"
  (default target: controller). One-shot resolution effect that confers a permanent, player-scoped
  property via `PlayerNoMaximumHandSizeComponent` — unlike the battlefield-only `NoMaximumHandSize`
  *static ability* (§9, Reliquary Tower / Thought Vessel), it survives the source leaving any zone
  (e.g. Wisdom of Ages exiles itself on resolution). Idempotent. `CleanupPhaseManager` checks both
  this component and the static ability when discarding to hand size.
- `ReduceMaximumHandSize(amount, target?)` — "target's maximum hand size is reduced by `amount` for
  the rest of the game" (Inspired Idea; default target: controller). `amount` is an `Int` (fixed)
  or `DynamicAmount` overload, evaluated once at resolution and *accumulated* into
  `PlayerMaximumHandSizeReductionComponent` — repeat applications stack (two Inspired Ideas → −6).
  A permanent, player-scoped reduction that survives the source leaving the stack, distinct from the
  battlefield-only `SetMaximumHandSize` static (§9). `MaximumHandSize.effective` subtracts the
  accumulated total after the `SetMaximumHandSize` statics pick the most restrictive base, floored
  at 0; a player with no maximum hand size has nothing to reduce (the reduction is inert while that
  holds).
- `WinGame(target, message?)` — target wins the game.
- `TakeExtraTurn(target, loseAtEndStep?, powerUpAbilitiesCantBeActivated?)` — target takes an extra turn after this
  one (Time Walk, Lost Isle Calling). Set `loseAtEndStep = true` for "...you lose the game at the beginning of that
  turn's end step" (Last Chance, Final Fortune). Set `powerUpAbilitiesCantBeActivated = true` for "During that turn,
  power-up abilities can't be activated" (Kang the Conqueror) — a global lockout on every player's power-up abilities
  (§ Power-up, CR 702.193) for the extra turn only, on every permanent, that outlives its source. Both riders are
  scoped to the extra turn *this* effect creates, so neither applies when the `PreventExtraTurns` replacement
  (Ugin's Nexus) stops the extra turn from happening; that is why they are parameters here rather than separate
  effects sequenced after it in a `Composite`.
- `EndTheTurn` — end the current turn (CR 720): Ultima ("Destroy all artifacts and creatures. End the turn."),
  Time Stop, Sundial of the Infinite, Discontinuity. When it resolves the whole stack is exiled (including the
  source and any triggered abilities the resolution queued — even ones that can't be countered — so those never
  reach the stack, CR 720.1c), creatures are removed from combat, and the game skips straight to the cleanup step
  (discard to maximum hand size, marked damage wears off, "this turn" / "until end of turn" effects end) before the
  next turn begins. Takes no target — it always ends the active player's turn. Modeled as a two-step effect: the
  executor records an `EndTheTurnRequestedComponent` on the active player, and `PassPriorityHandler` runs the
  sequence via `TurnManager.performEndTheTurn` once t…212152 tokens truncated…er, sacrificeFilter, variant)` so the rules text renders. The
  `variant` parameter is a textual tag only — `""` for plain Devour, `"land"` for the EOE
  "Devour land N" wording. **Scope today:** only the stack-spell entry path is wired; reanimation and
  token entries skip Devour (which is fine for printed cards — Devour creatures all cost real mana to
  cast).
- `EntersWithExileCounters(filter, sourceZone, maxCards, counterType, countersPerCard)` — as the
  permanent resolves from the stack, its controller may select up to the dynamic `maxCards` matching
  cards from their `sourceZone` (graveyard by default). The selected cards move to exile linked to the
  entering permanent's `LinkedExileComponent`, and the permanent enters with `countersPerCard` counters
  for each card that actually reached exile. `maxCards = DynamicAmount.XValue` models X-bounded entry
  choices; a zero maximum or no matching cards skips the prompt. **Mimeoplasm, Revered One** uses this
  with creature cards, three +1/+1 counters per card, and later targets the linked exile pile.
- `ModifyCounterPlacement(modifier, appliesTo, placedByYou?)` / `DoubleCounterPlacement(placedByYou?, appliesTo)` —
  **static** counter-placement modifiers living on a battlefield permanent for as long as it remains
  (Hardened Scales `+1`, Winding Constrictor `+1`, Doubling Season doubles). `appliesTo` is an
  `EventPattern.CounterPlacementEvent(counterType, recipient)`; `recipient = CreatureYouControl` is
  resolved relative to the *source permanent's* controller. **`placedByYou`** (both types, default
  `false`) is the *placer* axis, and it is what separates the two printed wordings: leave it `false`
  for "**if counters would be put** on …" (Hardened Scales, Winding Constrictor, Doubling Season),
  where an opponent's proliferate feeds the effect too and the recipient filter is the only
  "you control" gate; set it `true` for "**if you would put** one or more counters on …" (Doc
  Samson, Super Psychiatrist; Innkeeper's Talent), which applies only when the effect's own
  controller is the one placing them. For the **activated/spell-granted,
  duration-scoped** version of this (Prairie Dog), use the effect
  `Effects.GrantCounterPlacementModifier(...)` (§4 Counters) instead — it records a controller-scoped
  modifier in a turn-scoped game-state store consulted from the same counter-placement chokepoint,
  and expires at end of turn.
- `MultiplyTokenCreation(factor = 2, appliesTo)` / `ModifyTokenCount(modifier, appliesTo)` —
  **static** token-count replacements living on a battlefield permanent. `MultiplyTokenCreation`
  multiplies the number of tokens created by `factor` (Doubling Season / Anointed Procession /
  Exalted Sunborn — `factor = 2`; **Ojer Taq, Deepest Foundation** — `factor = 3`); several stack
  multiplicatively. `ModifyTokenCount` shifts the count by a fixed amount. `appliesTo` defaults to
  `EventPattern.TokenCreationEvent(controller = You)`; the multiplier runs in `CreateTokenExecutor`,
  the executor for **creature** tokens (it always builds a "… Creature" type line — Treasure/Clue/Map
  and other predefined tokens go through a separate executor that isn't multiplied), so an
  unfiltered `MultiplyTokenCreation` scopes to creature tokens in practice. `tokenFilter` on the
  event is not yet honored by the count path (filtered events are skipped), so express "creature
  tokens" via the default rather than `tokenFilter = Creature`.
- `ReplaceTokenCreationWithAttachedCopy(optional, oncePerTurn, attachmentVerb, appliesTo)` —
  "the first time you would create one or more tokens each turn, you may instead create that
  many tokens that are copies of [attached] permanent." Works for both Equipment and Auras —
  the engine reads the source's `AttachedToComponent` to find the permanent to copy.
  `optional = true` surfaces a yes/no during resolution; `oncePerTurn = true` adds
  `TokenReplacementOfferedThisTurnComponent` after the first offer (cleared at end of turn).
  `attachmentVerb` is a display-only label ("equipped", "enchanted", "fortified") — the
  attachment-type validation already happens at cast/attach time via `equipmentTarget` /
  `auraTarget`. Token copies are summoning-sick only when the copy is a creature (CR 302.6).
  Mirrormind Crown: `attachmentVerb = "equipped"`; Moonlit Meditation: `attachmentVerb = "enchanted"`.
- `CreateAdditionalToken(additionalTokenType, additionalTokenCount = 1, inheritTapped = false, appliesTo, restrictions = [])` —
  token-creation replacement that keeps the original tokens and appends one or more predefined tokens of
  another type. `appliesTo = EventPattern.TokenCreationEvent(controller, tokenFilter)` gates the original
  creation event, and the extra tokens are added once per qualifying event, not once per token. The added
  tokens bypass the same replacement pass so a Map added for an artifact-token event does not recursively
  trigger itself. Used by Worldwalker Helm (`TokenCreationEvent(You, Artifact)`, add `Map`, `inheritTapped = true`)
  and Peregrin Took (`additionalTokenType = "Food"`, "those tokens plus an additional Food token are created instead")
  and Quina, Qu Gourmet (`additionalTokenType = "Frog"`, default `appliesTo` = any token you create, adds a 1/1 green Frog).
  `restrictions` are extra `Condition` gates, evaluated by `TokenCreationReplacementHelper` with the
  *creating* player as the controller and the rider's own permanent as the source — so a source-relative
  gate resolves. That is how a **Solved replacement effect** carries its gate: a "Solved —" static
  ability written as a replacement effect can't go through `solvedStaticAbility { }` (replacement effects
  are declared outside the static-ability builder), so it takes `restrictions = listOf(Conditions.SourceIsSolved)`
  instead — Case of the Pilfered Proof's "Solved — If one or more tokens would be created under your
  control, those tokens plus a Clue token are created instead".
- `EntersAsCopy(optional, copyFilter, copyFromZone, filterByTotalManaSpent, additionalSubtypes, additionalKeywords, nameOverride, powerOverride, toughnessOverride, exileCopiedCard, tappedIfCopied, additionalCounters)` —
  "enter as a copy of …". As the permanent enters, the controller picks an object matching
  `copyFilter` and the permanent enters as a copy (Rule 707 copiable values), with any overrides
  applied. `copyFromZone` selects the candidate pool: `Zone.BATTLEFIELD` (default — Clone, Clever
  Impersonator, Mockingbird) copies a permanent in play; `Zone.GRAVEYARD` copies a *card*
  from any graveyard (Superior Spider-Man; Echoing Deeps copies a land card) via the modal card-list
  overlay. `additionalSubtypes` /
  `additionalKeywords` are added "in addition to its other types"; `nameOverride` keeps a fixed name;
  `powerOverride` / `toughnessOverride` force base P/T; `exileCopiedCard` exiles the copied card after
  the copy ("When you do, exile that card"). `filterByTotalManaSpent` restricts copy targets to mana
  value ≤ total mana spent (Mockingbird). `tappedIfCopied` makes the permanent enter **tapped** only
  when it actually enters as a copy — the "enter tapped as a copy" rider on the land-copy cycle
  (Echoing Deeps; Vesuva / Thespian's Stage copying a land on the battlefield); declining the copy
  enters it untapped as its printed self. `additionalCounters: DynamicAmount?` is the sibling rider
  "except it enters with N additional +1/+1 counters on it" — `DynamicAmount.XValue` for Altered
  Ego, `Fixed(1)` for a Spark Double shape. It lives on the copy effect rather than on a separate
  `EntersWithCounters`, because copying replaces the permanent's own copiable text: a self-targeted
  enters-with-counters replacement would be gone by the time the copy applies. It follows the same
  "only if a copy was actually made" rule as `tappedIfCopied`, which is the printed ruling
  ("You can choose not to copy anything. … It won't have +1/+1 counters placed on it by its
  ability."), and routes through `EntersWithReplacements.placeEntryCounters` so Hardened Scales-style
  placement modifiers apply exactly as for printed enters-with counters. The copy snapshots a
  `CopyOfComponent` so it reverts to its
  printed identity when it leaves the battlefield (CR 400.7 / 707.2). Works both when the source is
  cast as a spell (resolved off the stack) **and** when it enters the battlefield directly — a land
  played (Echoing Deeps) pauses via `PermanentEntryReplacements.pauseForEntersAsCopy`, its resumer
  `CloneEntersOnBattlefieldContinuation` copying onto the already-placed permanent in place.
- `ModifyDrawAmount(modifier, multiplier, restrictions, appliesTo)` — modify the number of cards a draw
  instruction announces to `(count * multiplier) + modifier`, clamped to ≥ 0, optionally gated by extra
  `restrictions: List<Condition>`
  evaluated against the drawing player as controller. Applied **once** per draw instruction at the
  announcement site — `DrawCardsExecutor.execute` for spell/ability draws and
  `DrawPhaseManager.performDrawStep` for the draw step (CR 121.2a: "An instruction to draw multiple
  cards can be modified by replacement effects that refer to the number of cards drawn. This
  modification occurs before considering any of the individual card draws.") — so a paused-and-
  resumed per-card loop doesn't double-modify. CR 616.1g is what makes the two-level split legal:
  the announced draw *contains* the individual draws, and an effect applying to a contained event
  can't be chosen until the containing one has been. `appliesTo` is typed as
  `EventPattern.DrawCardsEvent`, not the general `EventPattern`, so pointing one at the per-card
  `DrawEvent` is a **compile error** rather than a hang — a count modification that draws no card
  leaves the game state unchanged, so the per-card loop would re-match and re-apply it forever.
  Reach for `ReplaceDrawWithEffect` when you genuinely need a per-card replacement. Note that "you"
  in restriction text reads as the drawing player, not the source's controller; for
  `DrawCardsEvent(player = Player.You)` they coincide, but `DrawCardsEvent(player = Player.EachOpponent)`
  cards needing "you" = source controller would have to use a source-relative condition instead. Use
  `modifier` for the additive wording — "if you would draw one or more cards, you draw that many
  cards plus N instead" (Quantum Riddler:
  `ModifyDrawAmount(modifier = 1, restrictions = listOf(Conditions.CardsInHandAtMost(1)), appliesTo = DrawCardsEvent(player = Player.You))`)
  — and `multiplier` for a doubling that genuinely refers to the announced quantity. **Pick by the
  oracle wording, not by the outcome.** "If you would draw *one or more cards*, …" refers to the
  number drawn and belongs here; "If you would draw *a card*, draw two cards instead" (Vnwxt,
  Verbose Host) does not, and belongs in a per-card `ReplaceDrawWithEffect(DrawCardsEffect(2))` on
  `EventPattern.DrawEvent`. Both make Harmonize draw six on their own, so the difference only shows
  when the two levels meet: CR 616.1g orders the containing event before the contained one, so
  Quantum Riddler plus Vnwxt is `(3 + 1)` announced and then each of the four draws doubled = 8.
  Modelling Vnwxt here instead would drop both into one CR 616.1e pool and let the player choose an
  order giving 7. Several applicable announcement effects are cumulative — two doublers quadruple
  the draw. `restrictions` is also the seam a "Max speed —" gate folds into — declare the replacement
  inside `maxSpeed { replacementEffect(…) }` and the builder fills the slot.
- `ModifyMillAmount(modifier, restrictions, appliesTo)` — modify the number of cards a *mill* announces
  by a fixed amount (the mill twin of `ModifyDrawAmount`): a player who would mill N instead mills
  `N + modifier`, clamped to ≥ 0. `appliesTo` is an `EventPattern.MillEvent` whose `player` filter
  (`Player.You` / `Player.EachOpponent` / `Player.Each`) gates which players' mills are affected,
  relative to the source's controller. `restrictions` (a `List<Condition>`, ALL must hold, evaluated
  against the milling player as controller) gates *when* it applies. Applied **once** per mill
  instruction at the announcement site (`GatherCardsExecutor`'s `CardSource.TopOfLibrary(isMill = true)`
  branch, which only the `Patterns.Library.mill(...)` pipeline sets — scry / surveil / exile-top /
  look-at-top gathers leave `isMill = false` and are never affected), so a paused-and-resumed mill
  never double-modifies. A base mill of 0 is left untouched ("would mill one or more cards"). Multiple
  instances sum. Use for "if an opponent would mill one or more cards, they mill that many cards plus
  four instead" (The Water Crystal:
  `ModifyMillAmount(modifier = 4, appliesTo = EventPattern.MillEvent(player = Player.EachOpponent))`).
- `ModifyKeywordAction(prefixEffect, appliesTo)` — insert an extra effect *in front of* a keyword
  action (CR 614): replaces "[a matching permanent] <acts>" with "[prefixEffect], then that permanent
  <acts>". One type across keyword actions rather than one per action — `appliesTo` carries which
  action (and its subject filter), `prefixEffect` carries the rest. Supported patterns:
  `EventPattern.ExploredEvent` (CR 701.44) and `EventPattern.ConnivedEvent` (CR 701.50); any other
  pattern never matches. The pattern's `filter` scopes which actions are modified, matched against
  the acting permanent with the **source's controller** as "you" (so
  `ConnivedEvent(GameObjectFilter.Creature.youControl())` = "if a creature you control would
  connive"); `ExploredEvent.revealedType` is irrelevant (the replacement runs before the reveal).
  Like `ReplaceDrawWithEffect`, neither action is a generic replaceable event —
  `ExploreEffectExecutor` / `ConniveEffectExecutor` consult printed `ModifyKeywordAction` on the
  battlefield directly (via `KeywordActionReplacements`) and, on a match, re-issue the action as
  `Composite(prefixEffect, <action>(sameCreature, replacementsApplied = true))` through the registry
  recursion, so a pausing prefix (Scry's top/bottom decision) and the action's own decision
  (connive's discard) sequence in the printed order. The `replacementsApplied` guard on the inner
  action stops the same replacement applying twice (CR 614.5). Multiple applicable sources chain
  their prefixes in battlefield order (a faithful APNAP order per CR 616 is unmodeled — no printed
  card stacks two modifiers on one action). Note the prefix runs in the *replaced action's* context,
  so an opponent's effect making your creature act would run the prefix as the opponent; no printed
  card distinguishes this today.
  - Twists and Turns:
    `ModifyKeywordAction(Effects.Scry(1), EventPattern.ExploredEvent(GameObjectFilter.Creature.youControl()))`
    ("If a creature you control would explore, instead you scry 1, then that creature explores").
  - Leader, Super-Genius:
    `ModifyKeywordAction(Effects.DrawCards(1), EventPattern.ConnivedEvent(GameObjectFilter.Creature.youControl()))`
    ("If a creature you control would connive, instead you draw a card, then that creature
    connives") — the extra card is in hand *before* the discard is chosen, which a
    "whenever … connives, draw a card" trigger could not do.
- `ModifyLifeGain(multiplier, modifier, appliesTo, restrictions)` — modify life gain by a multiplicative *and/or*
  additive factor: `gained = (original * multiplier) + modifier`, clamped to ≥ 0. `appliesTo` is a `LifeGainEvent`
  whose `player` filter (default `Player.Each`) gates which players the replacement applies to. `restrictions`
  (a `List<Condition>`, ALL must hold, evaluated against the gaining player as controller) gates *when* it applies
  — e.g. Phial of Galadriel `restrictions = listOf(Conditions.LifeAtMost(5))` ("while you have 5 or less life").
  Used by Alhammarret's Archive (`multiplier = 2`), Leyline of Hope (`multiplier = 1, modifier = 1, player =
  Player.You`). Multiple instances stack (×s multiply, +s sum) — two Leylines of Hope add 2 to every life-gain event.
- `ModifyLifeLoss(multiplier, modifier, restrictions, appliesTo)` — same shape as `ModifyLifeGain` for life loss
  events (`LifeLossEvent`), plus a `restrictions: List<Condition>` list that further gates the replacement.
- `LifeLossFloor(floor, restrictions, appliesTo)` — cap damage-induced life loss so the resulting life total
  is ≥ `floor`. `appliesTo` is a `LifeLossEvent` whose `player` filter gates who is protected (default
  `Player.Each`); `restrictions: List<Condition>` (evaluated against the source's controller) further
  gates the floor — same shape as `ModifyLifeLoss.restrictions`. **Scope:** damage-as-life-loss only
  (CR 120.3a); `LoseLifeExecutor` deliberately skips this step so pay-life costs and direct life-loss
  effects bypass the floor (matching the Ali from Cairo ruling "does not apply to effects which reduce
  your life without doing damage"). The damage event still fires at the original amount, so lifelink
  and damage-dealt triggers see the full damage. Multiple instances pick the strictest floor. Used by
  Ali from Cairo (`LifeLossFloor(floor = 1, appliesTo = LifeLossEvent(Player.You))`); Worship adds a
  `restrictions = listOf(YouControlACreature)` gate.
- `ReplaceLifePaymentWithLibraryExile(appliesTo)` — a life **payment** becomes an exile of that many cards
  off the top of the payer's library, when the library is at least that deep (Ashiok, Wicked Manipulator).
  `appliesTo` is a `LifePaymentEvent` whose `player` filter picks whose payments are replaced (default
  `Player.You`). **Scope:** payments only (CR 118.8) — life *loss* from damage or a "you lose N life"
  effect keeps its own path, which is exactly the printed reminder text "Damage and unpayable costs still
  cause you to lose life". Mandatory and unsplittable, and a library shallower than the payment simply
  falls through to paying life normally. It does not raise what you're allowed to pay: CR 118.5 still
  requires a life total at least equal to the payment, so cost legality is unchanged.
  Applied by `LifePaymentService`, the engine choke point every life payment funnels through — cost
  atoms, additional casting costs, ward and Phyrexian-style payments, pain-cost mana abilities and the
  `PayLife` / `PayDynamicLife` resolution effects alike.
- `PreventLifeGain(appliesTo)` — life gain matching the event is fully prevented (Sulfuric Vortex, Erebos).
  The `LifeGainEvent.player` scope can be `You` / `EachOpponent` / `Each` (resolved relative to the
  source's controller) or `EnchantedPlayer` for an "enchant player" Aura whose locked player is its
  attachment target (Grievous Wound). For a *source-independent, rest-of-game* lock on a specific player
  instead, use the one-shot effect `Effects.LockLifeGain` (§4).
- Custom — implement the `ReplacementEffect` interface directly.

Amount-modifying replacements expose **both** `multiplier` (×) and `modifier` (±) on the same type — do not split into
`DoubleX` + `ModifyXAmount`.

---

## 16. Counters

String-keyed counter types — resolve via the central `resolveCounterType` helper rather than per-executor character
substitution.

- `+1/+1`, `-1/-1` — power/toughness counters.
- `loyalty` — planeswalker loyalty.
- `charge`, `time`, `level`, `quest`, `fade`, `vanishing`, `experience`, `age`, `velocity`, `awakening`,
  `blood`, `cage`, `doom`, `storage`, `divinity` (`Counters.DIVINITY`, a passive counter used by the Myojin
  cycle), `charm`, `music`, `crumble`, `corpse`, `germ`, `ink`, `growth`,
  `hour`, `energy`, `scry`, `aura`, `chapter`, `citation`, `rune`, `scar`, `crux`, `omen`, `secret`, `feather`,
  `hourglass`, `hope`, `verse`, `influence`, `burden`, `loot`, `soul`, `bait` — assorted printed counter kinds. (`hourglass`: Temporal Distortion
  — a permanent with one doesn't untap during its controller's untap step; model the restriction with
  `GrantKeyword(AbilityFlag.DOESNT_UNTAP.name, GroupFilter(... .withCounter(Counters.HOURGLASS)))` so it stays
  projection-scoped.) (`hope` / `verse` / `influence` / `burden`: LTR — Dawn of a New Age / Lost Isle Calling /
  Palantír of Orthanc / The One Ring. `loot`: OTJ — Bandit's Haul. `wind`: ARN — Cyclone (accrued one-per-upkeep,
  scales a pay-or-sacrifice cost + damage). `nest` (`Counters.NEST`): DSK — Twitching Doll,
  whose mana ability accumulates one per activation and whose sacrifice ability reads the count to scale a token
  payoff. `page` (`Counters.PAGE`): SOS — Diary of Dreams, whose cast-an-instant-or-sorcery trigger accumulates one
  and whose `{5},{T}: draw` ability reads the count via `genericCostReduction` to cost `{1}` less per counter.
  `hoofprint` (`Counters.HOOFPRINT`): LRW — Hoofprints of the Stag, whose "whenever you draw a card, you **may**"
  trigger accumulates one and whose `{2}{W}, Remove four hoofprint counters` ability
  (`Costs.RemoveCounterFromSelf(Counters.HOOFPRINT, 4)`) spends them for a 4/4 flying Elemental.
  `mannequin` (`Counters.MANNEQUIN`): LRW — Makeshift Mannequin, a pure marker that exists only so the reanimated
  creature's granted "when this becomes the target of a spell or ability, sacrifice it" ability has something to be
  keyed to (`Duration.WhileAffectedHasCounter(Counters.MANNEQUIN)`); remove the counter and the drawback goes too.
  `doom`: ATQ — Armageddon Clock (accrued one-per-upkeep, scales the damage dealt to each player in the draw step;
  a {4} ability removes one). `omen` (`Counters.OMEN`): VOW — Soulcipher Board, a *countdown* counter — the artifact
  enters with three and a per-card "whenever a creature card is put into your graveyard from anywhere" trigger removes
  one, transforming the artifact once a `Compare(countersOnSelf(Named(OMEN)), EQ, 0)` intervening check passes.
  `suspect` (`Counters.SUSPECT`): VOW — Investigator's Journal, a passive store — the artifact enters with one per
  creature the most-creatured player controls (`EntersWithDynamicCounters` over
  `DynamicAmounts.greatestControlledBySinglePlayer(...)`) and a `{2}, {T}, Remove a suspect counter` ability spends
  them one at a time. Unrelated to the *suspected* keyword action (CR 701.58), which places no counter at all.
  Pure passive counters with no inherent rule; the cards that use them accumulate/spend them via their own
  abilities and read the count via `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.X))` — or, when a
  self-sacrifice/exile cost wipes them first, `DynamicAmounts.lastKnownSourceCounters(...)` (CR 113.7a; see §13).
  `rev` (`Counters.REV`): DSK — Chainsaw, whose "whenever one or more creatures die" batched trigger accumulates one
  per death batch and whose `+X/+0` static reads the count via `DynamicAmounts.countersOnSelf(...)` applied to the
  equipped creature — another pure passive counter with no inherent rule.
  `bloodstain` (`Counters.BLOODSTAIN`): MKM — Blood Spatter Analysis, whose "whenever one or more creatures die"
  batched trigger accumulates one per death batch and then, *in the same resolution*, tests
  `Conditions.SourceCounterCountAtLeast(Counters.BLOODSTAIN, 5)` to decide whether to sacrifice itself. The threshold
  deliberately lives inside the trigger rather than in a state trigger/SBA: per the card's ruling, a fifth counter
  arriving by any other route (proliferate, a doubler) does *not* sacrifice it — another pure passive counter with no
  inherent rule.
  `blood` (`Counters.BLOOD`): RAV — Bloodletter Quill, whose `{2},{T}, put a blood counter on this artifact: draw a
  card` ability accrues one per activation as part of the *cost* (`Costs.PutCounterOnSelf`, always payable) and then
  reads the running count on resolution via `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.BLOOD))`
  to size the life lost, while a second `{U}{B}` ability removes one as its *effect*. Note it is a counter, entirely
  unrelated to the MID Blood *token* — another pure passive counter with no inherent rule.
  `soul` (`Counters.SOUL`): FDN — Ravenous Amulet, whose `{1},{T}, sacrifice a creature: draw` ability accumulates
  one per activation and whose `{4},{T}, sacrifice this: each opponent loses life` ability reads the count via
  `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.SOUL))` — another pure passive counter with no
  inherent rule.
  `possession` (`Counters.POSSESSION`): DSK — Unwilling Vessel, whose Eerie triggers (an enchantment you control
  entering / fully unlocking a Room) each accumulate one and whose dies trigger reads the total counter count via
  `DynamicAmount.ContextProperty(ContextPropertyKey.LAST_KNOWN_TOTAL_COUNTER_COUNT)` to size the X/X Spirit token it
  leaves behind — another pure passive counter with no inherent rule.)
  `fire` (`Counters.FIRE`): TLA — War Balloon (a `{1}` ability accumulates one; a `ConditionalStaticAbility`
  gated on `Conditions.SourceCounterCountAtLeast(Counters.FIRE, 3)` grants `GrantCardType("CREATURE")` so the
  Vehicle is an artifact creature at 3+); reused by later Fated/Fated-Firepower cards — another pure passive
  counter with no inherent rule.
  `conqueror` (`Counters.CONQUEROR`): TLA — Zhao, the Moon Slayer (a `{7}` ability accumulates one; a
  `ConditionalStaticAbility` gated on `Conditions.SourceCounterCountAtLeast(Counters.CONQUEROR, 1)` switches on a
  `SetLandTypesForGroup` making all nonbasic lands Mountains) — another pure passive counter with no inherent rule.
  `net` (`Counters.NET`): LCI — Braided Net (enters with three via an `EntersWithCounters` replacement; its tap
  ability spends them via `Costs.RemoveCounterFromSelf(Counters.NET, 1)`) — another pure passive counter with no
  inherent rule.
  `incubation` (`Counters.INCUBATION`): FDN — Drake Hatcher (a `DealsCombatDamageToPlayer` trigger accumulates
  "that many" via `AddDynamicCounters(Counters.INCUBATION, DynamicAmount.ContextProperty(TRIGGER_DAMAGE_AMOUNT), Self)`;
  an activated ability spends three via `Costs.RemoveCounterFromSelf(Counters.INCUBATION, 3)` to hatch a Drake token) —
  a pure passive resource counter with no inherent rule. Not MTG's Incubate/incubator-token mechanic.
  `bait` (`Counters.BAIT`): FDN — Fishing Pole (the Equipment's *granted* ability accrues one via
  `Costs.PutCounterOnSelf(Counters.BAIT)` + `AddCountersEffect(..., EffectTarget.GrantingSource)`;
  its "equipped creature becomes untapped" trigger spends one through an `IfYouDoEffect` gated on
  `SuccessCriterion.CountersRemoved` to make a Fish token) — another pure passive resource counter
  with no inherent rule.
  `fellowship` (`Counters.FELLOWSHIP`): FDN — Banner of Kinship (enters with one per creature you control of
  the as-enters chosen type via `EntersWithDynamicCounters(CounterTypeFilter.Named(Counters.FELLOWSHIP),
  DynamicAmount.AggregateBattlefield(Player.You, GameObjectFilter.Creature.withChosenSubtype()))`; a
  `GrantDynamicStatsEffect` sized by `DynamicAmounts.countersOnSelf(...)` reads the count back) — a pure
  passive resource counter with no inherent rule.
  `ingenuity` (`Counters.INGENUITY`): SPM — Lady Octopus, Inspired Inventor (two `Triggers.NthCardDrawn`
  triggers — first and second draw each turn — each add one via `AddCounters(Counters.INGENUITY, 1, EffectTarget.Self)`;
  her `{T}` ability reads the count via `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.INGENUITY))`
  inside a `CollectionFilter.ManaValueAtMost` to gate which hand artifact she can free-cast) — a pure passive
  resource counter with no inherent rule.
  `film` (`Counters.FILM`): SPM — Peter Parker's Camera (enters with three via an `EntersWithCounters(
  CounterTypeFilter.Named(Counters.FILM), count = 3, selfOnly = true)` replacement; each activation of its
  `{2}, {T}` copy ability spends one via `Costs.RemoveCounterFromSelf(Counters.FILM, 1)`). A pure "uses left"
  counter with no inherent rule — when it hits zero the activation cost is simply unpayable.
  `wish` (`Counters.WISH`): ELD — Wishclaw Talisman (enters with three via an `EntersWithCounters(
  CounterTypeFilter.Named(Counters.WISH), count = 3, selfOnly = true)` replacement; each activation of its
  tutor ability spends one via `Costs.RemoveCounterFromSelf(Counters.WISH, 1)`). A pure "uses left" counter
  with no inherent rule — when it hits zero the activation cost is simply unpayable, which is exactly the
  printed ruling that the Talisman then sits inert on the battlefield.
  `skewer` (`Counters.SKEWER`): WOE — Rotisserie Elemental (its combat-damage trigger adds one via
  `AddCounters(Counters.SKEWER, 1, EffectTarget.Self)`, and the optional self-sacrifice cashes the tally in
  for an impulse-exile sized by `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.SKEWER))`).
  A pure tally counter with no inherent rule.
  `ice` (`Counters.ICE`): SOI — Thing in the Ice (enters with four via an `EntersWithCounters(
  CounterTypeFilter.Named(Counters.ICE), count = 4, selfOnly = true)` replacement; its
  `Triggers.YouCastInstantOrSorcery` trigger removes one and then flips the permanent through a
  `ConditionalEffect(Conditions.SourceCounterCountAtMost(Counters.ICE, 0), TransformEffect(Self))`).
  A "countdown to zero" counter with no inherent rule — the inverse of the `wish`/`film` "uses left" shape:
  read down rather than spent as a cost. Gating the flip on the live count *inside the ability's resolution*
  is what makes the printed ruling hold — removing the last counter any other way never transforms it.
- `stun` — CR 122.1d, a built-in replacement: "If a permanent with a stun counter on it would become untapped,
  instead remove a stun counter from it." Engine-wired through `untapOrConsumeStun` (`rules-engine/core/UntapHelpers.kt`),
  which is invoked from the untap step (`BeginningPhaseManager`), from `TapUntapExecutor`'s untap branch, and from the
  sacrifice/pay continuation resumer. Adding stun counters is done by `AddCounters(Counters.STUN, n, target)`.
- `shield` — CR 122.1c, a built-in replacement **and** prevention effect: "If this permanent would be destroyed
  as the result of an effect, instead remove a shield counter from it" and "If damage would be dealt to this
  permanent, prevent that damage and remove a shield counter from it." One or more counters create a *single*
  effect of each kind, so exactly **one** counter is consumed per damage or destruction event however many are on
  the permanent and however large the damage. Add via `AddCounters(Counters.SHIELD, n, target)` or an
  `EntersWithCounters` replacement (Captain America, Super-Soldier); read the presence back with
  `Conditions.SourceHasCounter` / `.withCounter(Counters.SHIELD)`.
  Engine-wired at the four chokepoints in `rules-engine/core/ShieldCounterHelpers.kt`'s KDoc:
  `DamageUtils.dealDamageToTarget` and `CombatDamageManager` (prevention; combat damage applies it once for the
  whole simultaneous batch per CR 510.2, so a creature blocked by three creatures still spends one counter), and
  `ZoneMovementUtils.destroyPermanent` + `MoveCollectionExecutor`'s destroy branch (replacement).
  What it deliberately does **not** stop, per the official rulings: sacrifice; the lethal-damage/deathtouch
  state-based action (CR 122.1c replaces destruction "as the result of an **effect**"); 0-toughness death. It is
  not regeneration (no tap, no removal from combat, marked damage untouched) and it is not a keyword counter, so
  losing all abilities doesn't switch it off. Unpreventable damage (Leyline of Punishment) is still dealt — but
  still removes a counter. An indestructible permanent never "would be destroyed", so its counter stays unspent.
- `storage` — a passive counter with no inherent rule, like `loot` and `nest`: the card that places
  them is the only thing that reads them. City of Shadows exiles a creature to add one
  (`AddCounters(Counters.STORAGE, 1, Self)`) and taps to add {C} for each
  (`AddColorlessMana(EntityProperty(Source, CounterCount(Named(Counters.STORAGE))))`).
- `hunger` — a pure bookkeeping counter, same no-inherent-rule shape as `storage`: the card counts
  its own pile and acts on the total. Fasting adds one each upkeep
  (`AddCounters(Counters.HUNGER, 1, Self)`) and destroys itself at five, reading the count back
  through `Conditions.SourceCounterCountAtLeast(Counters.HUNGER, 5)`.
- `javelin`, `credit`, `cube`, `tide` — the Fallen Empires named counters, all in the no-inherent-rule
  family above. `javelin` (Icatian Javelineers) is a one-shot resource: the creature enters with one and
  removing it is part of the cost of its ping. `credit` (Icatian Moneychanger) accrues one per upkeep and is
  cashed in for life when the creature sacrifices itself. `cube` (Delif's Cube) is charged by the artifact's
  first ability and spent by its second — the same store-and-spend shape as `storage`. `tide` (Homarid,
  Tidal Influence) is the odd one out: its *exact* count is what matters, not a threshold, because the
  permanent's static effect switches on at exactly one and again at exactly three and sheds all of them on
  reaching four — so read it with an equality condition, not `SourceCounterCountAtLeast`.
- `hone` — CR 122.1j, a built-in Layer 7c pump aimed at a *different* object: "A hone counter on an Equipment
  gives +1/+0 to any creature that Equipment is attached to." Add via `AddCounters(Counters.HONE, n, target)`
  or `AddDynamicCounters(Counters.HONE, amount, target)` — and that is **all** a hone card does; the bonus is
  never a `ModifyStats`/`GrantDynamicStats` on the Equipment. Like `shield` and `stun` the behavior belongs to
  the counter, which is what makes Dwalin, Weaponmaster ("put a hone counter on each Equipment you control")
  work: a Mirrodin Bonesplitter that has never heard of hone still pumps its equipped creature. Engine-wired in
  `StateProjector.collectContinuousEffects`, which synthesizes one `Modification.ModifyPowerToughness(n, 0)` per
  honed Equipment scoped to `AffectsFilter.AttachedPermanent` — so it stacks additively with the Equipment's own
  printed bonus (CR 613.4c covers "effects **and** counters that modify power and/or toughness"), contributes
  nothing while the Equipment is unattached, and is inert on a non-Equipment permanent. Not a keyword counter,
  so it stays out of `KEYWORD_COUNTER_MAP`. Used by Sting, Bilbo's Sword and Dwalin, Weaponmaster (HOB).
- **Keyword counters** (Rule 122.1b) — `flying`, `first strike`, `double strike`, `vigilance`, `lifelink`,
  `indestructible`, `deathtouch`, `trample`, `hexproof`, `reach`, `haste`, `menace`. `StateProjector` grants the matching `Keyword`
  to any permanent carrying one (mapped in `KEYWORD_COUNTER_MAP`, re-applied after Layer 6 so "loses all abilities"
  can't wipe a counter-granted keyword). Add via `AddCounters(Counters.DEATHTOUCH, ...)` etc.; no static ability needed.
  (`reach`: Sagu Pummeler's renew payoff puts a reach counter on a creature. `vigilance`: Aragorn, Company Leader.
  `double strike`: Mai, Jaded Edge's exhaust ability. `haste` / `menace`: Super-Adaptoid, which copies keywords
  off another creature as counters.)
- **Ability counters beyond single keywords** — `decayed` (`Counters.DECAYED`, CR 702.147a, Tarkir: Dragonstorm) grants
  the whole **Decayed** ability (a "can't block" static **and** an attack-triggered end-of-combat sacrifice) to any
  creature that bears one. `StateProjector` projects the `DECAYED` keyword + `cantBlock = true` (initial pass and the
  post-Layer-6 re-apply), and `TriggerDetector.detectDecayedCounterAttackTriggers` schedules the self-sacrifice when a
  decayed-countered creature attacks. Add via `AddCounters(Counters.DECAYED, n, target)` (Rot-Curse Rakshasa's Renew).

Counter effects live in §4 (`AddCounters`, `RemoveCounters`, `Proliferate`, `MoveAllLastKnownCounters`, etc.).

---

## 17. Zones & movement

**Zones** — `BATTLEFIELD`, `HAND`, `LIBRARY`, `GRAVEYARD`, `EXILE`, `STACK`.

**Primitives**

- `MoveToZoneEffect(target, zone, faceDown?, byDestruction?, linked?)` — single-target move. Card
  definitions construct it via the facade `Effects.Move(target, destination, …)` (or the named
  shortcuts `Effects.Destroy/Exile/ReturnToHand/PutOnTopOfLibrary/ShuffleIntoLibrary/…`).
- `MoveTrackedBattlefieldObjectEffect(target, destination, enteredBattlefieldTimestamp?)` — moves
  only the battlefield object identified by both entity ID and entry timestamp. When nested in a
  delayed trigger, target resolution snapshots the timestamp automatically. Use for delayed moves
  that must ignore a permanent that left and returned as a new object (CR 603.7c / 400.7).
- `MoveCollectionEffect(collectionName, zone, faceDown?, linkToSource?, asOwner?, likelyPosition?)` — pipeline move of a
  stored collection.
- `faceDown` (on both move effects) is a nullable **`FaceDownMode`** — `null` = enter face up;
  `MORPH` = face-down with the card's morph cost as its turn-up cost; `MANIFEST` = face-down with
  the card's mana cost as its turn-up cost (only if it's a creature card, CR 701.40b);
  `DISGUISE` = morph plus ward {2} (CR 702.168); `CLOAK` = manifest plus ward {2} (CR 701.58a);
  `HIDDEN` = face down with no turn-up (e.g. exiled face down for Hideaway). The engine derives the
  turn-up data at entry, so a manifested, cloaked or disguised creature reuses the whole morph
  turn-up machinery (special action, payment, flip).
  - **The ward is data on the mode** (`FaceDownMode.faceDownWard`), not an ability of the card
    underneath. Per CR 708.2 a face-down permanent has only the characteristics the rules that made
    it face down list, and disguise/cloak list ward {2} among theirs — so `StateProjector` puts
    `WARD` in the face-down keyword set and `TriggerAbilityResolver` builds the ward trigger from the
    mode instead of from `cardDef.keywordAbilities`. It ends the instant the permanent is turned face
    up. A face-down permanent contributes no *other* triggered ability of its own; ward granted from
    outside (a `GrantWard` static elsewhere) still applies to it.
  - **Turn-up procedures.** `FaceDownTurnUp` is the single place that maps (card, mode) →
    `MorphDataComponent.procedures`. Manifest and cloak contribute both their own "pay the card's
    mana cost" procedure *and* any morph/disguise procedure the card prints, because CR 701.40c/d and
    701.58c/d let the controller pick either — that permanent then offers two `TurnFaceUp` legal
    actions, selected by `TurnFaceUp.procedureIndex`. Megamorph's `faceUpEffect` rides its own
    procedure, so a cloaked megamorph creature flipped for its mana cost correctly gets no counter
    (CR 702.37b).
  - **A cloaked/manifested instant or sorcery that would turn face up** is revealed and left face
    down, firing no turned-face-up trigger (CR 701.40g / 701.58g) — handled in `TurnFaceUpExecutor`.
  - `FaceDownModeComponent(mode)` carries the mode on the permanent while it is face down. It is
    public information (CR 708.6) and reaches the client as `ClientCard.faceDownMode`, which picks
    the face-down helper-card art (morph token / manifest token / "A Mysterious Creature" for both
    disguise and cloak, matching paper).
- `GatherCardsEffect(source, filter, into)` — pipeline gather from a zone into a named collection. `CardSource`
  variants include zones (`FromZone`, `FromMultipleZones`), battlefield queries (`BattlefieldMatching`,
  `ControlledPermanents`), linked exile (`FromLinkedExile`), tapped-as-cost (`TappedAsCost`), and the resolved
  spell/ability targets (`ChosenTargets`). The zone/library sources (`FromZone`, `FromMultipleZones`,
  `TopOfLibrary`) accept a multi-player `player` reference (`Player.Each`, `Player.ActivePlayerFirst`,
  `Player.EachOpponent`) and fan out across every relevant player's copy of the zone in a single gather —
  e.g. "all creature cards in each player's graveyard" (Bringer of the Last Gift). Pair with
  `MoveCollectionEffect(underOwnersControl = true)` to return each card to its owner.
  `revealed = true` makes a public reveal (every player sees the cards while they stay in a hidden
  zone, persisted via `RevealedToComponent` and emitting a reveal event). For a non-public library
  *look* (`revealed = false`), `lookAudience` chooses who privately sees the cards:
  `LookAudience.Controller` (default — Scry / Surveil / look-at-top-N), `LookAudience.Opponent`
  ("an opponent looks at the top N of your library"), or `LookAudience.None` (no one is auto-shown;
  a downstream decision is the only window — used by **Sauron's Ransom**, where the opponent who
  partitions sees the cards through their own `SelectFromCollection` decision but the caster does
  not). `lookAudience` is ignored when `revealed = true` or for non-library sources. To then turn a
  single pile face up for everyone — including the caster, before a `ChoosePileEffect` — re-gather
  that pile via `GatherCards(FromVariable("pile"), revealed = true)`; any pile never revealed
  renders to the caster as opaque card backs (Sauron's Ransom's concealed face-down pile).
- `CaptureControllersEffect(from, storeAs)` — snapshot each entity's current controller into a parallel
  `List<EntityId>` under `storedCollections[storeAs]`. Required when a later step needs "who controlled
  this card before it left the battlefield" — `ControllerComponent` is stripped on move-out.
  Also captures a spell's stack controller before countering it, even when its caster is not its owner
  (Broken Ambitions). Battlefield permanents use projected control. Pair `captureControllers` with
  `forEachCaptured` over the original collection when the rider applies regardless of whether a move
  succeeded; retain the snapshot through intervening decisions such as counter payments and clashes.
- `ForEachCapturedControllerEffect(collection, originalCollection, controllerSnapshot, countVariable?, effects)` —
  cross-references a post-move `collection` against an `originalCollection` + parallel `controllerSnapshot` to
  build per-controller tallies, then runs `effects` once per controller (turn order from the active player). Each
  iteration sets `context.controllerId` to the controller (so `Player.You` / `EffectTarget.Controller` resolve to
  them) and writes the tally into `storedNumbers[countVariable]` (default `"iterationCount"`) for
  `DynamicAmount.VariableReference` to read. Outer `storedCollections` are preserved (unlike
  `ForEachPlayerEffect`). Used by Builder's Bane via the
  `GatherCards(ChosenTargets) → CaptureControllers → MoveCollection(Destroy, storeMovedAs) → ForEachCapturedController`
  shape.
- `ForEachInCollectionEffect(collection, effect)` — run `effect` once per entity in a named pipeline collection
  (snapshotted at resolution), with `pipeline.iterationTarget` set to that entity. Lowers to
  `ForEachEffect(IterationSpace.Collection(...))` — see the unified ForEach entry under "Sequencing &
  conditional". Collection-based sibling of
  `ForEachInGroupEffect` (which iterates a battlefield filter): use it to apply a per-entity effect to a *chosen*
  set rather than a re-evaluated filter. Pair with a single-target effect on `EffectTarget.Self` — e.g.
  `ForEachInCollection(nonChosenPile, Effects.CantAttack(EffectTarget.Self))` gives each creature in a chosen pile
  its own snapshot can't-attack floating effect (Fight or Flight / Stand or Fall; creatures entering after the
  split are unaffected).
- `SelectFromCollectionEffect(from, into, selectCount?, allowZero?, alwaysPrompt?, restrictions?)` — let a player pick
  from a collection. `restrictions` (`List<SelectionRestriction>`) cap and trim the picks server-side: `OnePerCardType`,
  `OnePerColor(matchControllerPermanentColors?)`, `OnePerCardName`, `OnePerPower`, `TotalManaValueAtMost(max)` /
  `TotalManaValueAtMost(maxAmount = <DynamicAmount>)` (the dynamic overload caps the sum at a resolved amount — e.g.
  `DynamicAmount.XValue` for "with total mana value X or less"; the executor resolves it to a fixed cap up front so every
  downstream consumer sees an integer — The Rise of Sozin // Fire Lord Sozin),
  `TotalPowerAtMost(max)`, `OnePerBasicLandType`, `ReducedMinimumIfMatches(reducedMinimum, filter, requiredMatches?)`, and
  `MaxAffordablePayment(manaPerSelected, payer?)`. `TotalPowerAtMost(max)` caps the sum of selected creatures'
  **projected** power at `max` (a creature with undefined power contributes 0); it is the power analogue of
  `TotalManaValueAtMost` and surfaces `maxTotalPower` on `SelectCardsDecision` so the UI shows a running "Total power: X / N"
  and disables over-cap picks while the server trims oversubmits in response order — used for "choose any number of
  creatures you control with total power N or less, then sacrifice the rest" (Destined Confrontation). `OnePerPower` keeps at most one card of each *printed* power
  (a card with no fixed power — no printed P/T, or a characteristic-defining `*` — can't be kept and bottoms out,
  like a typeless land under `OnePerBasicLandType`); pair it with the `CreatureOrVehicle` filter for "any number of
  creature and/or Vehicle cards with different powers" (Rip, Spawn Hunter). `OnePerBasicLandType` keeps at most one
  land of each basic land type (a kept land claims
  *every* basic type it has) and — unlike `OnePerColor`, where a colourless card is unconstrained — a land with no
  basic land type can't be kept at all (Global Ruin: "chooses a land of each basic land type, then sacrifices the
  rest"). Each restriction also exposes a boolean flag on `SelectCardsDecision` (`onePerBasicLandType`, …) so the UI
  can disable redundant picks. `ReducedMinimumIfMatches` exposes `conditionalMinimums` on `SelectCardsDecision` so the
  UI and server can accept one matching card for "discard two unless you discard a creature card" while rejecting one
  nonmatching card. `MaxAffordablePayment` caps the selection at
  `floor(payer's available mana / manaPerSelected)` (floating + untapped sources) — pair it with a downstream
  `Gate.MayPay` over `PayDynamicMana` at the same rate so a player can never select a set whose total cost is
  unpayable and silently forfeit the payoff; a cap of zero (under `ChooseAnyNumber`) skips the selection prompt
  entirely (Magnetic Mountain: "choose any number … and pay {4} for each creature chosen this way").
  - `chooser` (`Chooser`, default `Controller`) — who makes the selection: `Controller`, `Opponent`, `TargetPlayer`
    (`context.targets[0]` treated as the player), `TriggeringPlayer`, `SourceController` (the source's controller,
    ignoring per-iteration swaps), `ControllerOfSelection` (the controller of the cards in `from` — resolved from the
    first card's projected controller), `DefendingPlayer` (the player the source is attacking, CR 508.1), or
    `ControllerOfTarget` (the controller of the targeted *permanent*,
    `context.targets[0]`, falling back to its owner once it has left the battlefield). Use `ControllerOfSelection` for
    "their controller chooses…" where the deciding player is whoever controls the gathered cards and may be you or an
    opponent (Barrin's Spite: gather the two targeted creatures, their controller sacrifices one, the other is returned
    to hand). Use `ControllerOfTarget` for "destroy target permanent. Its controller searches/chooses…" where the
    targeted permanent's controller performs a follow-up (Magmatic Hellkite: destroy target nonbasic land, *its
    controller* searches for a basic). Use `DefendingPlayer` for an attack trigger whose payoff is the defending
    player's own choice — "defending player discards three cards" (Mindstab Thrull) is picked from *their* hand, not
    the attacker's; it reads combat off the ability's source and keeps answering after a self-sacrifice has taken that
    source off the battlefield (CR 508.1 / 608.2h), the same last-known leg `Player.DefendingPlayer` uses. The same
    `chooser` set is accepted by `ChoosePileEffect`.
  - **`Chooser.Opponent` in multiplayer.** "An opponent" is *one* opponent, and the controller of the spell or
    ability picks which one (CR 601.7a / 602.3a for cast/activation-time choices; resolution-time choices follow the
    same principle and cards say so in their rulings — Curator of Destinies: "You decide which opponent chooses the
    pile"). The engine handles that for you: with several opponents the step first pauses on a `ChooseOptionDecision`
    for the controller listing the opponents by name, then re-runs itself and presents the real choice to the named
    opponent. With a sole opponent the choice is forced and nothing extra is prompted, so two-player games are
    unaffected. Each "an opponent chooses" step in one resolution gets its own pick — the choice is
    resolution-scoped, not recorded on the source. All of this lives in the engine's `ChooserResolution`, so any
    effect carrying a `Chooser` inherits it; card definitions just say `Chooser.Opponent`. (For the durable,
    cast-time "you may promise **an opponent** a gift"-style recipient choice, use
    `Effects.ChooseOpponentForSource` + `Player.ChosenOpponent` instead — that one is stored on the source and
    persists past the resolution.)
  - **`Chooser.ChosenOpponent`** is the decision-side twin of `Player.ChosenOpponent`: the opponent a
    preceding `Effects.ChooseOpponentForSource` already named for this source makes the choice. Use it,
    not `Chooser.Opponent`, whenever the *same* opponent has to appear in two steps of one mechanic —
    `Chooser.Opponent` re-picks per step, so in a multiplayer game a mechanic that reads one opponent's
    library and then asks that opponent to decide could split across two different players. Clash
    (CR 701.30b) is the case in hand. Unresolvable if no choice has been made, so a card using it must
    run `Effects.ChooseOpponentForSource` first.

**Linked exile**

- `Effects.ExileGroupAndLink(filter, storeAs?)` — exile matching permanents linked to source.
- `Effects.ReturnLinkedExile` — return all to controller.
- `ReturnLinkedExileUnderOwnersControl` — return to owners.
- `ReturnLinkedExileToHand` — return to hand.
- `ReturnLinkedExileToZoneExiledFrom` — return each card to the zone it was exiled from (CR 610.3).
- `ReturnOneFromLinkedExile` — return one chosen card.
- `CardSource.FromLinkedExile()` — play permission targeting linked-exile pile.
- `CardSource.FromExile(name)` — play permission for a named exile zone.

**Face-down**

- `PutOntoBattlefieldFaceDown(count, target?)` — enter face-down (morph shape).
- `Triggers.TurnedFaceUp` — fires when source flips face-up.
- UI label: `"Turn Face-Up"` (used by E2E `selectAction("Turn Face-Up")`).

---

## 18. Components (set indirectly by effects)

### Permanent

- `ChosenModeComponent` — chosen entry mode (Sieges, modal permanents).
- `TypeLineOverrideComponent` — temporary type-line edits.
- `CountersComponent` — all counters on the permanent.
- `EnchantedCreatureComponent` — reference to attached creature (Auras).
- `EquippedCreatureComponent` — reference to equipped creature.
- `LinkedExileComponent` — linked exile pile attached to source.
- `ExileOnLeaveComponent` — replace next zone change with exile.
- `MayPlayFromExileComponent` — owner may play this from exile.
- `TappedStateComponent` — tap state.
- `FaceDownComponent` — face-down state.
- `ControllerComponent` — current controller.
- `ProtectionComponent` — protection from colors/types.
- `CantAttackComponent` / `CantBlockComponent` — combat restrictions.

### Player

- `PlayerCitysBlessingComponent` — you have City's Blessing.
- `TheRingComponent` — you have the Ring emblem; `temptCount` gates its four abilities (CR 701.54).
- `RingBearerComponent` — designates a creature as a player's Ring-bearer (on the creature, not the player).
- `SpellsCantBeCounteredComponent` — your matching spells can't be countered.
- `LifeGainedAmountThisTurnComponent` — accumulator for life gained.
- `LifeLostThisTurnComponent` — marker that you've lost life this turn.
- `PlayerAttackedThisTurnComponent` — marker that you've attacked this turn.
- `PlayerAttackersThisTurnComponent` — list of attackers declared this turn.
- `PlayerAttackedPlayersThisTurnComponent` — set of defending players you "attacked" this turn (CR
  508.6); read by `PlayerAttackedPlayerThisTurn`.
- `LandDropsComponent` — lands played this turn.
- `FoodSacrificeThisTurnComponent` — marker that you sacrificed a Food this turn.
- `SpellsCastThisTurnByPlayer` — count of spells you cast this turn.

Card authors rarely reference these directly; they are created/updated by the matching effect or trigger.

---

## 19. Named-mechanic composites

- **Cycling / Typecycling / Basic landcycling** — `KeywordAbility.Cycling(cost)`, `Typecycling(type, cost)`,
  `BasicLandcycling(cost)`; unified via `TypecyclingVariant(cost, searchFilter, description)` in `TypecycleCardHandler`.
  A plain cycling cost may contain `{X}`; `CycleCardHandler` announces it (CR 107.3a), resolves the cost via
  `ManaCost.withXAs(x)` so the ordinary payment path sees no X, and stamps it on `CardCycledEvent.xValue` for the
  cycling trigger. See the `Cycling(cost)` entry in §Keyword abilities for the full flow.
- **Cases (CR 719)** — `typeLine = "Enchantment — Case"` (`Subtype.CASE`, `TypeLine.isCase`) plus the
  four DSL helpers in `dsl/mechanics/CaseDsl.kt`. No new ability kind: a Case's two special lines lower
  onto vocabulary that already existed.
  - `toSolve(condition)` — the **"To solve — [condition]"** ability (CR 719.3a) = "At the beginning of
    your end step, if [condition] and this Case is not solved, this Case becomes solved". Emits a
    `Triggers.YourEndStep` triggered ability whose `interveningIf` is
    `All(condition, Not(SourceIsSolved))` and whose effect is `Effects.BecomeSolved()`. Both halves are
    re-checked on resolution (CR 603.4), so a Case whose condition is undone in response stays unsolved,
    and the `not solved` half is what stops a solved Case re-triggering every turn.
  - `solvedStaticAbility { }` / `solvedTriggeredAbility { }` / `solvedActivatedAbility { }` — the
    **"Solved — [ability]"** keyword (CR 702.169) in its three shapes. Each is the ordinary builder with
    the gate pre-applied, matching the rule exactly: a static ability gets `condition = SourceIsSolved`
    ("as long as this Case is solved", 702.169b), a triggered ability gets
    `triggerRestriction = SourceIsSolved` ("triggers only if this Case is solved", 702.169c — a trigger
    restriction, *not* an intervening-if, so an ability that triggered while solved still resolves), and
    an activated ability gets `ActivationRestriction.OnlyIfCondition(SourceIsSolved)` ("activate only if
    this Case is solved", 702.169d). Each helper ANDs its gate with whatever condition/restriction the
    block sets itself, so a Solved ability that is also sorcery-speed-only keeps both.
  - The **solved designation** itself (CR 719.3b) is `SolvedComponent` — engine state, neither an ability
    nor a copiable value. `Effects.BecomeSolved` stamps it, `Conditions.SourceIsSolved` / `.solved()` /
    `StatePredicate.IsSolved` read it, `ClientCard.isSolved` surfaces it as a card badge, and
    `ZoneMovementUtils.stripBattlefieldComponents` drops it when the Case leaves the battlefield.
  - The **moment** it flips is `Triggers.WheneverYouSolveACase` (§ Cases (CR 719) under triggers) —
    "when this creature enters **and whenever you solve a Case**" (Case File Auditor). That is the
    event, not the standing state; `SourceIsSolved` is the state.
  - An **unsolved** Case shows how close its criterion is as a `current/required` badge — Case of the
    Burning Masks counts 0/3 up to 3/3 as sources deal damage — and drops it once solved. That falls
    out of the generic intervening-if badge (§ Triggered abilities) reading inside the
    `All(condition, Not(SourceIsSolved))` composite that `toSolve` emits; no Case-specific plumbing.
  - A Case's remaining lines — the "When this Case enters" ability, or an always-on static like Case of
    the Ransacked Lab's cost reduction — are plain `triggeredAbility { }` / `staticAbility { }` blocks;
    they function whether or not the Case is solved.
- **Plot (CR 718)** — `KeywordAbility.plot(cost)`. Engine wires a sorcery-speed `PlotEnumerator` + `PlotCardHandler`
  that pays the plot cost, exiles the card face-up from hand, stamps `PlottedComponent(controllerId, turnPlotted)` +
  `PlayWithoutPayingCostComponent`, and adds a permanent `MayPlayPermission` gated by `SourcePlottedOnPriorTurn`.
  The cast-from-exile path is the standard `MayPlayPermission` flow in `CastFromZoneEnumerator` — `permanent = true`
  keeps the grant alive across end-of-turn cleanup. Emits `CardPlottedEvent` / `ClientEvent.CardPlotted`.
- **Adventure (CR 715)** — `layout = ADVENTURE` + `cardFaces[0]` Adventure spell; DSL:
  `card { adventure("Name") { spell { … } } }`. The primary face may be a **land** (`Land — Town`) instead of a
  creature — FIN's "Town land // spell" DFCs (Ishgard, the Holy See // Faith & Grief, …). No new layout: resolving
  the Adventure exiles the card with the same generic `MayPlayPermission`, which `CastFromZoneEnumerator` /
  `PlayLandHandler` already honor as a *play-the-land-from-exile* permission. The only seam beyond the creature case
  is `CastSpellEnumerator` — it now enumerates the Adventure spell face for a land-primary card (the land itself is
  played via `PlayLandEnumerator`). First land users: Ishgard, the Holy See; Jidoor, Aristocratic Capital; Lindblum,
  Industrial Regency; Midgar, City of Mako; Zanarkand, Ancient Metropolis.
- **Omen (Tarkir: Dragonstorm)** — `layout = OMEN` + `cardFaces[0]` Omen spell; DSL:
  `card { omen("Name") { spell { … } } }`. Reuses the Adventure cast/enumeration path (`enumerateSecondaryFace`,
  cast via `CastSpell.faceIndex = 0`), but `StackResolver` routes the resolving Omen to `Zone.LIBRARY` and shuffles
  the owner's library (`shuffleOwnerLibrary` + `LibraryShuffledEvent`) instead of exiling with a `MayPlayPermission`.
  No new effect/component — the layout enum drives the resolution fork. First user: Dirgur Island Dragon //
  Skimming Strike.
- **Modal DFC, spell back (CR 712)** — `layout = MODAL_DFC` + `cardFaces[0]` back face; DSL:
  `card { modalBack("Name") { imageUri = …; spell { selfExile(); … } } }`. Cast either face from hand (back via
  `CastSpell.faceIndex = 0`); reuses the Adventure cast/enumeration path (`enumerateSecondaryFace`) but with no
  exile-then-recast linkage at resolution. `StackResolver` reads the cast face's `selfExileOnResolve`, and the back
  art rides on `CardFace.imageUri` → `CardComponent.backFaceImageUri`. First user: Flamescroll Celebrant.
- **Modal DFC, permanent back (CR 712.3)** — `layout = MODAL_DFC` + a full `backFace`, via
  `CardDefinition.modalDoubleFacedPermanent(front, back)`. Reuses the **disturb** path rather than the Adventure
  one, because the card goes on the stack transformed: `ModalDfcCasts.castFace` is the single
  can-I-and-as-which-face policy (mirroring `DisturbCasts`), `CastZoneResolver.modalBackCastFace` is its hand-side
  permission check, `CastSpellEnumerator.enumerateModalBackFace` surfaces the offer, and `CastSpellHandler` reads
  every characteristic off that face (`transformedFace`) and passes `castTransformed = true` to `StackResolver`.
  Cost is the back's own mana cost (`AlternativeCostType.MODAL_BACK_FACE` — the enum entry is plumbing, not a real
  alternative cost; CR 712.11b calls it choosing a face). Because the back is a real `backFace`, transform and the
  client's flip preview work with no extra wiring. The one place the shared disturb path is *not* shareable is
  **mana value**, which is the only characteristic the CR treats differently for the two layouts — see the entry
  below. Timing comes off the face being cast, not the front (CR 712.11c), so a permanent back is sorcery-speed
  unless *it* has flash. First users: the MSH hero cycle.
- **Mana value across a transform (CR 712.8c / 712.8e / 712.8f)** — the one characteristic where nonmodal and modal
  DFCs diverge, so it is the one thing the shared face-swap machinery has to fork on. A **nonmodal** DFC computes
  its mana value from the **front** face's mana cost while the back is up — on the stack (CR 712.8c, a disturb
  cast) and on the battlefield (CR 712.8e) — which matters because a transform back prints no mana cost at all, so
  reading the face directly would make a transformed Delver of Secrets mana value 0. A **modal** DFC has no such
  exception (CR 712.8f): its back keeps its own printed cost. `dfcBackFaceManaValue(frontDef, frontManaValue)` is
  the single place that decides, and it feeds `CardComponent.manaValueOverride` (which `CardComponent.manaValue`
  prefers over `manaCost.cmc`) through `buildCardComponentForDfcFace` — so all three flip routes
  (`flipDfcInPlace`, `returnDfcFace`, and `StackResolver`'s cast-transformed swap) agree, and every reader of
  `manaValue` (predicates, `EntityNumericProperty.ManaValue`, emerge) sees the right number with no per-call-site
  handling. `StackResolver` reuses the same value for `SpellCastEvent.manaValue` (hence
  `ContextPropertyKey.TRIGGERING_SPELL_MANA_VALUE`) and `CastSpellHandler` mirrors it for its `CastSpellRecord`.
  Not modelled: CR 712.8e's other clause, that a permanent *copying* a nonmodal back face has mana value 0
  (CR 202.3b) — that is a property of the copy, not of the flip. Pinned by `DfcManaValueTest` and `DisturbKeywordTest`.
- **Prepare / Prepared (Secrets of Strixhaven)** — `layout = PREPARE` + `cardFaces[0]` prepare spell; DSL:
  `card { prepare("Name") { spell { … } } }`. The creature is only cast as itself. A creature that carries
  `Keyword.PREPARED` ("This creature enters prepared") becomes prepared on enter
  (`StackResolver.enterPermanentOnBattlefield`, gated on the keyword). A PREPARE-layout creature *without* the
  keyword (Leech Collector, Joined Researchers) only becomes prepared via `Effects.BecomePrepared(target)`
  (`BecomePreparedExecutor`). Both paths call the shared `PreparationLogic.makePrepared`, which
  creates a stack-style copy of the prepare spell in the controller's exile carrying
  `PreparedSpellCopyComponent(sourceId)`, stamps `PreparedComponent(exileCopyId)` on the creature, and grants a
  permanent `MayPlayPermission` for the copy. `CastFromZoneEnumerator` recognizes the copy and offers it as
  `CastSpell(..., faceIndex = 0)` from `EXILE` using the prepare face's cost/targets. Casting the copy
  (`StackResolver.castSpell`) strips the source's `PreparedComponent` and consumes the permission; the copy resolves
  via the face script and ceases to exist (`CopyOfComponent`). The exiled copy is exempt from the 707.10a
  phantom-copy SBA (`PhantomCardCopiesCheck`) while linked, and that same check removes it once the source leaves
  the battlefield or stops being prepared. First users: Adventurous Eater // Have a Bite, Landscape Painter //
  Vibrant Idea; becomes-prepared-via-trigger: Leech Collector // Bloodletting, Joined Researchers // Secret
  Rendezvous (end-step trigger gated on `Conditions.OpponentHasMoreCardsInHand`).
- **Hideaway N** — `KeywordAbility.hideaway(n)` (display, "Hideaway N") + `MoveCollectionEffect(faceDown = FaceDownMode.HIDDEN,
  linkToSource = true)` + `CardSource.FromLinkedExile()`; no special engine plumbing needed.
- **Ascend / City's Blessing** (CR 702.131) — on a **permanent**, `keywords(Keyword.ASCEND)` is the whole
  implementation: ascend there is a *static* ability (702.131b, "**any time** you control ten or more
  permanents…"), and the engine's `AscendCitysBlessingCheck` state-based action grants the designation to
  any player controlling an ascend permanent once they control ten permanents. Do **not** write it as an
  enters-the-battlefield trigger — that samples the count once, on the turn a cheap creature is least
  likely to meet it, and never looks again. On an **instant or sorcery** ascend is a spell ability
  (702.131a), so those cards spell it out with `Effects.GainCitysBlessing()` in the spell's effect, as does
  any card that just says "you get the city's blessing".
  Read it back with `Conditions.YouHaveCitysBlessing` / `SourceProjectionCondition.ControllerHasCitysBlessing`;
  the marker is `PlayerCitysBlessingComponent` and is never removed (702.131b/c, "for the rest of the game").
  Both the read and the two writers go through `CitysBlessingService`, which also evaluates the ascend
  condition *live* — that is what makes "create a token, then if you have the city's blessing…" come out
  right when the token itself is your tenth permanent (Ocelot Pride), since state-based actions aren't
  polled mid-resolution.
- **Storied / Enduring Story** (The Hobbit, CR 702.195) — the same shape as ascend-on-a-permanent, with a
  different threshold, and authored the same way: `storied()` adds `Keyword.STORIED` and nothing else,
  because 702.195a is a *static* ability ("**any time** you control three or more permanents that are
  artifacts, Sagas, and/or legendary…") handled by the engine's `StoriedEnduringStoryCheck` state-based
  action. There is no spell-ability form — storied only ever appears on permanents — so unlike the city's
  blessing there is no grant *effect*, only the SBA.

  ```kotlin
  storied()
  staticAbility {                                       // "As long as you have an enduring story, …"
      ability = ConditionalStaticAbility(
          ability = GrantKeyword(Keyword.VIGILANCE, GroupFilter.source()),
          condition = Conditions.YouHaveEnduringStory
      )
  }
  ```

  Read it back with `Conditions.YouHaveEnduringStory` (backed by `PlayerHasEnduringStory(Player.You)`);
  the marker is `PlayerEnduringStoryComponent` and is never removed (702.195a, "for the rest of the
  game"). Read and write both go through `EnduringStoryService`, which evaluates the storied condition
  *live* for the same mid-resolution reason as `CitysBlessingService`. Two things the count gets right and
  a hand-rolled one usually doesn't: the three categories are a **union over permanents**, so a legendary
  artifact is one qualifying permanent rather than two; and the whole scan reads *projected* state, so a
  granted storied, an animated artifact, or a stolen permanent all count for the right player. First
  users: Ori, Keeper of Songs; Óin the Brave; Thorin Oakenshield.
- **Speed / Start your engines! / Max speed** (Aetherdrift, CR 702.178–702.179) — a player's speed is
  an `Int` 0–4 (`Speed.NONE` / `Speed.STARTING` / `Speed.MAX` in `core/Speed.kt`) held by
  `PlayerSpeedComponent`. It only ever rises, is clamped at 4, and is never removed — like the city's
  blessing. Author it with two `CardBuilder` helpers and add nothing else:

  ```kotlin
  startYourEngines()                                     // CR 702.179a — just the keyword
  maxSpeed { keywords(Keyword.DOUBLE_STRIKE) }            // "Max speed — This creature has double strike."
  maxSpeed {                                              // any ability kind works inside the block
      activatedAbility { cost = Costs.Tap; effect = Effects.AddMana("{R}{R}"); manaAbility = true }
      triggeredAbility { trigger = Triggers.BeginningOfYourEndStep; effect = … }
      staticAbility { ability = ModifyStats(1, 2, GroupFilter.source()) }
  }
  ```

  - `startYourEngines()` adds only `Keyword.START_YOUR_ENGINES`. Setting a controller's speed to 1 is a
    *state-based action* (CR 704.5aa, `StartYourEnginesCheck`), not a trigger, so nothing is authored on
    the card. Because the check reads projected keywords and projected controllers, gaining control of
    a permanent with the keyword or having the keyword granted to one both start speed for free.
  - `maxSpeed { }` adds display-only `Keyword.MAX_SPEED` and gates each ability it declares on
    `Conditions.YouHaveMaxSpeed` using that ability kind's existing vocabulary — statics via
    `ConditionalStaticAbility`, activated via `ActivationRestriction.OnlyIfCondition`, triggered via
    `interveningIf` (CR 603.4). No new ability type; activated/triggered labels get the printed
    "Max speed — " prefix. Several abilities may share one block (Tsagan, Raider Warlord).
    Two static kinds are exceptions to the wrapper, both for the same reason — their read site scans
    the *raw* static list with `filterIsInstance` and never unwraps a conditional, so a
    `ConditionalStaticAbility` would hide them entirely. Both carry their own condition slot, and the
    builder folds the gate into it. Authoring is unchanged — declare them in the block and the builder
    picks the right seam:
    - `ModifySpellCost` → its `CostGating.OnlyIf` slot (cost calculation; Racers' Scoreboard,
      "Max speed — Spells you cast cost {1} less to cast").
    - `MayCastSelfFromZones` → its `condition` slot (`CastFromZoneEnumerator.enumerateIntrinsicZoneCast`
      / `CastZoneResolver.findMayCastSelfFromZoneAbility`; Lightwheel Enhancements, "Max speed — You
      may cast this card from your graveyard"). The condition is evaluated in the *casting player's*
      context at both read sites, which is what lets a max-speed ability function from a zone where
      the card isn't a permanent at all — per the CR ruling that *"if the granted ability functions in
      a zone other than the battlefield, the max speed ability does too."*
  - Raising speed is `Effects.IncreaseSpeed(amount, target)`. The inherent CR 702.179d trigger
    ("Whenever one or more opponents lose life during your turn, if your speed is less than 4, your
    speed increases by 1. This ability triggers only once each turn.") is synthesized per player by
    `SpeedAbilities.inherentSpeedIncrease` and handed out by `TriggerDetector.detectInherentSpeedTriggers`
    with the *player entity* as its source — which is what makes the generic `oncePerTurn` tracker and
    its cleanup reset enforce the once-each-turn clause with no new machinery.
  - Read speed with `DynamicAmounts.speed(player)` ("where X is your speed"); a player with no speed
    reads as 0 (CR 702.179f), so no has-speed guard is ever needed. `SpeedService` is the single writer,
    holding the clamp and the CR 702.179c "no speed + N ⇒ N" rule.
  - Client: `ClientPlayer.speed` (public info, unmasked) plus `ClientEvent.SpeedChanged`; the UI renders
    a four-bar `SpeedGauge` that redlines at max speed.
  - **Replacement effects** use a third seam, `maxSpeed { replacementEffect(…) }`, for the same reason
    as the two static exceptions: they're read straight off `ReplacementEffectSourceComponent` at ~20
    independent interception sites, so a `ConditionalStaticAbility`-style wrapper would be invisible to
    every one of them. The builder folds the gate into the effect's own `restrictions` list, which only
    some replacement types have — anything else throws rather than silently emitting an ungated effect.
    Vnwxt, Verbose Host ("Max speed — If you would draw a card, draw two cards instead") is a
    `ReplaceDrawWithEffect`; Far Fortune, End Boss's damage rider is a `ModifyDamageAmount`. Know which
    player the restrictions read before reaching for it: the damage family evaluates them against the
    *source's controller* (so Far Fortune taxes opponents while gating on your speed), while the draw /
    life-total ones read the *affected* player — fine for a `Player.You` pattern like Vnwxt's own draws,
    wrong for a `Player.EachOpponent` one.
- **Siege (named-mode entry)** — `EntersWithChoice(ChoiceType.MODE, modeOptions = ...)` + `SourceChosenModeIs("id")`.
- **Morph** — `morph = "{2}{U}"` (top-level) + `morphFaceUpEffect` for "as it turns face up".
- **Disguise** (CR 702.168) — `disguise = "{1}{W}"` (top-level), or `disguiseCost` for a non-mana
  cost. Morph plus ward {2}, and that is the whole difference: the same sorcery-speed `{3}`
  face-down cast (`MorphCastEnumerator`), the same turn-face-up special action, and the ward carried
  as a face-down characteristic by `FaceDownMode.DISGUISE` rather than as an ability of the card
  (see the `FaceDownMode` notes under the move effects). Pair with `Triggers.TurnedFaceUp` for the
  common "when this creature is turned face up, …" payoff, or
  `Triggers.or(Triggers.EntersBattlefield, Triggers.TurnedFaceUp)` for the "enters **or** is turned
  face up" wording (Rakish Scoundrel) — one ability with two conditions, which must fire once on
  either route, not twice. For the **replacement** wording "As this creature is turned face up, …"
  (Bubble Smuggler) reach for `disguiseFaceUpEffect` instead: it applies inside the special action,
  so it can't be responded to, where the `Triggers.TurnedFaceUp` form goes on the stack first.
- **Cloak** (CR 701.58) — no keyword to author: it is `FaceDownMode.CLOAK` on whichever move puts
  the card onto the battlefield, exactly as manifest is `FaceDownMode.MANIFEST`. For the common
  "look at the top N, cloak M" shape use
  `Patterns.Library.lookAtTopAndKeep(keepDestination = ToZone(BATTLEFIELD), keepFaceDown = CLOAK)`
  (Hide in Plain Sight).
- **Warp** — `warp = "{1}{R}"`; alt-cost that exiles end of turn. Like morph and cycle, a warp card
  always surfaces *both* cast options — its normal cost and its warp cost — in the action window, even
  when only one (or neither) is payable; the unpayable side appears grayed out (CR 118.9a, the caster
  chooses which cost to use). The warp action is enumerated by `CastFromZoneEnumerator`, which also
  emits the grayed-out normal-cast placeholder when the normal cost is unaffordable (mirroring
  `MorphCastEnumerator`). The end-step exile is a delayed trigger whose `WarpExileEffect` snapshots
  the permanent's battlefield-entry timestamp (`enteredBattlefieldTimestamp`); at resolution it only
  exiles the *same object* — a warped permanent that left the battlefield and returned before the
  end step (blink, e.g. Daydream) is a new object (CR 603.7c / 400.7) and stays permanently.
- **Dash** — `dash = "{1}{R}"`; hand-only alt-cost (CR 702.109). Mirrors warp's cast-window and
  timestamp-guarded delayed-trigger shape, but simpler: no graveyard variant, no recast-from-exile
  permission. The cast is tracked with a `DashedComponent` marker (not a floating continuous
  effect — an EntityId can be reused across a later fresh cast of the same card, so a
  `Duration.Permanent` effect keyed to the id could misfire) that `StateProjector` reads live to
  grant haste, and a delayed trigger fires `MoveTrackedBattlefieldObjectEffect(..., HAND)` at
  `Step.END`, snapshotting
  `enteredBattlefieldTimestamp` the same way warp's exile does — a dashed permanent blinked before
  the end step is a new object and stays on the battlefield; one that already left (died, was
  bounced) is left where it is, per the official ruling.
- **Evoke** — `evoke = "{U}"`; pay alt cost, sacrifice on ETB.
- **Sneak** — `sneak("{1}{U}")`; declare-blockers-step alt cost (pay mana + return an unblocked attacker you control to hand); a resolving permanent enters tapped and attacking the same defender. `Conditions.SneakCostWasPaid` reads the rider flag.
- **Ninjutsu** — `ninjutsu("{1}{U}{B}")`; the canonical CR 702.49 keyword that **Sneak** reflavors. Same declare-blockers alt cost and tapped-and-attacking entry, shared via `KeywordAbility.ninjutsuStyleCost`. *Kaito, Bane of Nightmares* (DSK).
- **Splice** — `splice("{2}{R}{R}")` (CR 702.47); reveal from hand as you cast an Arcane spell, pay the splice cost as an *additional* cost, and that spell gains this card's rules text — the card itself stays in hand. The spell keeps its own characteristics (702.47c); the spliced text resolves after the main spell's (702.47b) with its own targets. *Through the Breach* (CHK / INR).
- **Earthbend** — `Effects.Earthbend(amount, target)` composes AnimateLand + GrantKeyword + AddCounters + granted
  self-triggers (no fake keyword). `amount` is an `Int` for "Earthbend N" (Earthbending Lesson) or a `DynamicAmount`
  for "Earthbend X, where X is …" (Rockalanche — X = the number of Forests you control), which counts X at resolution
  via `AddDynamicCounters`.
- **Airbend** (Avatar: The Last Airbender) — `Effects.Airbend(cost = {2})` / `Effects.AirbendAll(filter, excludeSelf, excludeChosenTargets, cost = {2})`.
  *"Airbend target permanent"* = "Exile it. While it's exiled, its owner may cast it for {2} rather than its mana
  cost." Composes a pipeline (no fake keyword): `GatherCards(ChosenTargets)` → `MoveCollection(→ EXILE, storeMovedAs)`
  → `GrantMayPlayFromExile(ownerControls = true, expiry = Permanent, fixedAlternativeManaCost = {2})`. **Target-agnostic
  by design:** the *card* declares the targeting shape via its `TargetRequirement` ("up to one", "any number of",
  "another", "you control", "target nonland permanent"), and `Effects.Airbend()` airbends whatever was chosen — so one
  effect serves every airbend card. `AirbendAll(filter)` swaps the gather to `CardSource.BattlefieldMatching` for "airbend
  all other creatures" — pass `excludeChosenTargets = true` (and `excludeSelf = false` for a sorcery) so the spared "other"
  is the spell's chosen target, backing **Avatar's Wrath** ("Choose up to one target creature, then airbend all other
  creatures."); `CardSource.BattlefieldMatching.excludeChosenTargets` drops `EffectContext.targets` from the gather, the
  chosen-target sibling of `excludeSelf`/`excludeTriggering`. The new piece is **`fixedAlternativeManaCost`** on `GrantMayPlayFromExile`: it
  stamps `PlayWithFixedAlternativeManaCostComponent(controllerId, fixedCost)` on each exiled card, which the legal-action
  enumerator (`CastFromZoneEnumerator`) and the cast handler (`CastSpellHandler`) read to *replace* the printed mana cost
  entirely (a 6-drop and a 2-drop both become {2}) — unlike `GrantPlayWithCostIncrease`, which adds on top. The component
  is stripped when the card leaves exile (`StackResolver`), so a recast Airbended permanent doesn't carry a stale cost.
- **Airbend a spell** (the stack branch — Aang, Swift Savior: "airbend up to one other target creature **or spell**").
  The single target is a cross-zone union — `TargetFilter.anyOf(TargetFilter.Creature, TargetFilter.SpellOnStack)` (the
  same union machinery as Sorceress's Schemes). Branch on whether the chosen target is a spell with
  `Conditions.TargetIsSpellOnStack(0)`: the spell branch is `Effects.AirbendSpell(cost = {2})` — airbend's reminder
  says "**exile it**", not "counter it", so it reuses the Aven Interrupter `exileSpell` primitive: it removes the spell
  from the stack to its *owner's* exile **even if the spell can't be countered**, fires **no** `SpellCounteredEvent`, and
  grants the **owner** the same fixed-{2} may-play (reusing `PlayWithFixedAlternativeManaCostComponent`). The permanent
  branch is the normal `Effects.Airbend()`. Both branches fire the "whenever you airbend" trigger below once an object is
  actually exiled (CR 701.65b). (`Effects.AirbendSpell` is `Effects.ExileTargetSpell` with `emitAirbend = true`; use the
  plain `ExileTargetSpell` — no bend — for a non-airbend exile like Aven Interrupter.)
- **"Whenever you waterbend, earthbend, firebend, or airbend" (the four-bend event) + "all four this turn"** —
  `Triggers.YouBend(types = BendType.ALL)` fires once per bend of any element in `types` the controller performs
  (Avatar Aang uses all four; pass a subset like `setOf(BendType.EARTH)` for a single-element variant). Backed by a
  `BendPerformedEvent(playerId, bendType)` emitted at each of the four keyword actions, per CR 701.65b / 701.66b /
  701.67c / 702.189b:
  - **earthbend** and **airbend** compose `Effects.EmitBend(BendType.EARTH/AIR)` into their pipelines
    (`Effects.Earthbend`, `Effects.Airbend`/`AirbendAll`); airbend emits only when ≥1 object was exiled (gated on the
    `airbendExiled` collection, CR 701.65b). Airbending a **spell** (`Effects.AirbendSpell`, the stack branch) emits the
    same `BendType.AIR` from `ExileTargetSpellExecutor` once the spell is exiled.
  - **firebending** emits `BendType.FIRE` when its attack trigger resolves (folded into `firebendingAttackTrigger`), so
    both printed `firebending(n)` and `Effects.GrantFirebending` fire it.
  - **waterbend** emits `BendType.WATER` engine-side when the waterbend cost is *paid* — in `CastSpellHandler` /
    `ActivateAbilityHandler`, ungated on how it was paid (CR 701.67c), so paying entirely with mana still fires it.
  Each emit also folds the element into the player's `BendsThisTurnComponent` (a `Set<BendType>`, reset for every player
  at the start of each turn). Read the count of *distinct* bends this turn via
  `DynamicAmount.TurnTracking(Player.You, TurnTracker.DISTINCT_BENDS)` (0–4); "if you've done all four this turn" is
  `Conditions.CompareAmounts(TurnTracking(You, DISTINCT_BENDS), ComparisonOperator.GTE, DynamicAmount.Fixed(4))`.
  `Effects.EmitBend(bendType)` is the internal marker effect (executor: `EmitBendEventExecutor`); card authors reach a
  bend through the keyword-action facades above, not this effect. `BendPerformedEvent` is internal (dropped from the
  client log).
- **Endure N** — `Effects.Endure(amount, target = EffectTarget.Self)` composes a `ModalEffect.chooseOne` of
  AddDynamicCounters (N +1/+1 counters on the enduring permanent) and a single N/N white Spirit `CreateTokenEffect`
  (no fake keyword — endure is always the effect of a triggered/activated ability, resolved at resolution time). `amount`
  is `DynamicAmount.Fixed` for "endure 2" or any dynamic value for "endure X" (e.g. Warden of the Grove reads
  `EntityProperty(Source, CounterCount(...))`); `target` defaults to `Self` ("it endures") but takes
  `EffectTarget.TriggeringEntity` when a card endures the creature that triggered it.
- **Forage** — effect form is `Patterns.Mechanic.forage` (`ChooseActionEffect`). All *cost* forms
  (`Costs.Forage()`, `Costs.additional.Forage`, and the cast-from-graveyard permission) route their
  payment, candidate-finding, and per-mode legal-action cost-info through the single
  `ForageCostResolver`, so the player chooses exile-vs-sacrifice and which cards/Food everywhere
  (CR 701.59a). The payoff is `Triggers.WheneverYouForage` (`EventPattern.ForagedEvent`), emitted
  from `ForageCostResolver.pay` for the cost forms and from the `Effects.Foraged()` marker inside
  each effect-form mode — the same cost/effect split waterbend uses. The foraging player is whoever
  *paid*, which need not be the source's controller.
- **Blight X** — `Costs.additional.BlightVariable` + `DynamicAmount.AdditionalCostBlightAmount` +
  `Conditions.BlightWasPaid(n)`.
- **Divvy (Fact-or-Fiction)** — `Patterns.Library.factOrFiction(...)`; `SplitPilesDecision` stays dormant until N > 2.
- **Astral Slide / delayed return** — `ExileUntilEndStepEffect` + `DelayedTriggeredAbility`.
- **Lord effects** — multiple `staticAbility { }` blocks + `ModifyStatsForCreatureGroup` /
  `AffectsFilter.OtherCreaturesWithSubtype`.
- **Player-scoped uncounterable grant** — `Effects.GrantSpellsCantBeCountered(target, filter, duration)` +
  `SpellsCantBeCounteredComponent`.
- **Static emblems** — `Effects.CreatePermanentEmblem(...)` for planeswalker emblems with static abilities.
- **The Ring / the Ring tempts you (CR 701.54)** — `Effects.TheRingTemptsYou(target = Controller)`: the player gets
  the Ring emblem (`TheRingComponent`, tempt-count tracked) and chooses a creature they control to become their
  Ring-bearer (`RingBearerComponent` designation). The emblem's four cumulative abilities are resolved by the engine,
  not card data: the bearer is made legendary in `StateProjector` and can't be blocked by greater power via
  `RingBearerCantBeBlockedByGreaterPowerRule`; the ≥2/≥3/≥4 triggered abilities are appended to the bearer by
  `TriggerAbilityResolver` (see `TheRingAbilities`). For card triggers/checks use `Triggers.RingTemptsYou`
  ("Whenever the Ring tempts you"), `Conditions.SourceIsRingBearer` ("if this is your Ring-bearer"), and
  `Conditions.YouChoseOtherCreatureAsRingBearer` ("if you chose a creature other than this as your
  Ring-bearer" — pairs with `Triggers.RingTemptsYou` for the Aragorn/Faramir/Gandalf/Galadriel cycle).
  CR 701.54a: the designation ends permanently when another player gains control of the bearer —
  every control-change executor strips `RingBearerComponent` via `clearRingBearerOnControlChange`, so a
  temporary steal (Threaten) does not silently restore the designation when control reverts.
- **Amass [subtype] N (CR 701.47)** — `Effects.Amass(count, subtype)` (fixed) or
  `Effects.Amass(amount, subtype)` (a `DynamicAmount`, for "amass Orcs X"). `subtype` is required (no default) —
  the amassed Army's type is printed on each card (Orcs for the LTR cards). If the controller controls no Army
  creature, a 0/0 black `[subtype]` Army token is created first (composing `CreateTokenEffect`); then they put N
  +1/+1 counters on an Army they control (a `SelectCardsDecision` resolved by `AmassContinuation` picks which one
  when they control several) and that Army becomes the subtype if it isn't already. The counter/subtype back half
  lives in `AmassResolution`; counters route through `AddCountersEffect`, so placement replacements still apply.

## 20. Miscellaneous author-facing knobs

- `triggeredAbility { controlledByTriggeringEntityController = true }` — the triggered ability is controlled by the
  triggering entity's controller (not source's). Useful for ETB-on-creature triggers and Death Match-style shapes.
- `metadata.oracleTextOverride` — bypass auto-generated oracle text when needed.
- `metadata.inBooster = false` — Special Guests, starter exclusives, bonus sheets.
- `colorIdentity` override is authoritative — never run `:mtg-sets:syncColorIdentityFromDump`.
- Layer dependencies (CR 613.8) — same-layer effects sort by dependency (trial application) before falling back to
  timestamp.
- Server is authoritative; never compute legal actions in the client. Every state change emits a `GameEvent` so triggers
  and animations can react.

### Ability identity (engine-internal, not authored)

`AbilityIdentity(cardDefinitionId, abilityId)` (`mtg-sdk` `scripting/AbilityIdentity.kt`) is the stable, **definition-scoped**
identity of a *kind* of ability — independent of the stack object or source entity instance. Two permanents printed from the
same card (and every future instance) share one identity for a given ability, because both halves are definition-scoped.
Cards never author it. Activated-ability lookup records whether the concrete ability came from the current card definition:
printed abilities and generated Class level-up abilities receive the corresponding identity, while runtime-, static-, and
emblem-granted abilities and intrinsic subtype abilities retain their concrete `ActivatedAbility` without claiming definition
ownership. The activation snapshot travels through stack copies and resolution; its `activatedAbilityId` is derived from the
snapshot, so retaining “this ability” never depends on a grant still existing when the effect resolves. The engine threads proven
identities onto `ActivatedAbilityOnStackComponent` and `DecisionContext`, so persistent yields can remember a per-ability
answer across all copies. The triggered-ability path still derives a provisional key from the current source card definition;
typed ownership provenance for granted and synthesized triggers remains an explicit follow-up in
`backlog/stack-collapse-and-batch-decisions.md` §4. See that backlog's §C.2 for the original identity contract.

### Batched may-question (engine-internal, not authored)

When a run of structurally identical **optional, targeted** triggers ("Whenever …, you may … *target* …") fires off one
event, the engine asks the controller a single `BatchYesNoDecision` instead of one `YesNoDecision` per trigger — Magic
Online's "auto-stack identical triggers" affordance (`backlog/stack-collapse-and-batch-decisions.md` §B). Cards author
nothing: `TriggerProcessor` groups contiguous `liveTriggers` sharing one (controller, `AbilityIdentity`) key (and that would
actually raise the may-question rather than fizzle for lack of targets) into one decision carrying a `count`. The reply,
`BatchYesNoResponse(choice, applyToAll)`, is fanned back out by `BatchMayTriggerContinuation`:

- `applyToAll = true` resolves the whole run (`no` drops it; `yes` unwraps each may-gate and routes every instance through
  ordinary per-trigger target selection — only the yes/no is shared, never the target).
- `applyToAll = false` peels one instance off (answered with `choice`) and re-raises the batch for the remainder.

Only same-controller, same-identity, targeted-may triggers batch; targetless "may" triggers still decide at resolution, and a
lone trigger uses the plain per-trigger yes/no. The guard guarantees the engine never makes a meaningful target/ordering
choice on the player's behalf.

## 21. Structural lint (`CardLinter`)

Every registered card is structurally validated at build time: `CardValidator.validate` runs
`CardLinter` (mtg-sdk `serialization/CardLinter.kt`), and the corpus-wide gate is
`CardLintTest` in mtg-sets (beside `CardDefinitionSnapshotTest`). The linter walks the card's
serialized JSON tree, so every container — composites, gates, modes, granted abilities, class
levels, saga chapters, faces — is covered automatically. What it checks:

- **Pipeline dataflow** — every read of a named pipeline variable (`MoveCollection.from`,
  `CardSource.FromVariable`, `VariableReference`, `CollectionContainsMatch`, `chosenSubtypeKey`,
  …) must have a writer (`storeAs` / `storeSelected` / `storeMatching` / `StoreNumber` /
  `ChooseOption` / a cast-time additional cost, …) in the same resolution scope. A read written
  *nowhere* on the card is an **error** (typo → silent no-op); read-before-write and
  cross-resolution reads are warnings, as are stores nothing reads. A collection write `x`
  also satisfies the numeric read `x_count`. Macro effects that the engine expands into a
  pipeline count as writers of the collections that expansion seeds: `Scry` writes `toTop` /
  `toBottom` and `Surveil` writes `toTop` / `toGraveyard`, so a sibling effect may read the
  kept-on-top cards (e.g. Starving Revenant's `DistinctEntitiesInCollections("toTop")`).
- **Target bindings per owning ability** — `ContextTarget(i)` must fit the owning ability's
  flattened target slots (a `count = 2` requirement spans two indices); `BoundVariable(name)`
  must match a requirement `id` (indexed form `id[i]` allowed). Modes inherit the card-level
  requirements unless they declare their own; `ReflexiveTriggerEffect.reflexiveEffect` resolves
  against `reflexiveTargetRequirements`; `CreateDelayedTriggerEffect.effect` against its
  `targetRequirement`; granted/token abilities against their own requirements only.
- **Choice slots** — a `ChoiceSlot` read (`CastChoiceMade`, `DynamicAmount.CastChoice`,
  `HasChosenColor`, `SourceChosenModeIs`, …) needs a declarer on the card (`EntersWithChoice`,
  kicker, blight, sneak, `ChooseColorThen`/`ChooseColorForTarget`, or `ChooseNumberForSource`,
  which declares the slot named in its `slot` field); `SourceChosenModeIs` ids must match a
  declared `modeOptions` id.
- **Registry hygiene** — a string field whose name follows the dataflow conventions (`store*`,
  `from`, `collectionName`, `variableName`, …) on a node type the linter doesn't know is itself
  an error: **when you add an SDK type that reads or writes a named pipeline variable, classify
  it in `CardLinter.dataflowFields` in the same change** (and name the field conventionally so
  the hygiene net sees it).
- **`EntityMatches` entity roles** — the condition's `entity` must be a role the
  `ConditionEvaluator` dispatches (`Self`, `EnchantedPermanent`, `EnchantedCreature`,
  `EquippedCreature`, `ContextTarget`, `TriggeringEntity`); any other `EffectTarget` would be a
  silent constant `false` and is an **error**. Extending the evaluator to a new role must extend
  `CardLinter.supportedEntityMatchesRoles` in the same change.
- **Mana-ability classification (CR 605.1a)** — the `isManaAbility` flag is a *consequence* of the
  ability, not an authoring choice, so it is checked in **both** directions and each mismatch is an
  **error**. An activated ability that could add mana (`AddMana`, `AddColorlessMana`,
  `AddManaOfChoice`, `AddDynamicMana`, …) *is* a mana ability unless it requires a target, is a
  loyalty ability, or its **cost or effect** moves a card to or from a library — `UnflaggedManaAbility`
  fires when such an ability isn't flagged (the shape that shipped Cryptolith Rite, Joiner Adept and
  Citanul Hierophants unflagged, because a raw `ActivatedAbility(...)` inside a `GrantActivatedAbility`
  has no builder to derive the flag), and `MisflaggedManaAbility` fires when a disqualified ability
  *is* flagged. The library clause entered 605.1a in the August 7, 2026 update, which is why the
  second direction exists: Chromatic Sphere, the five Odyssey Eggs and Deranged Assistant were all
  correctly flagged when written and became ordinary activated abilities on that date. A library
  *reorder* is not a disqualifier — it moves cards **within** a library, not to or from one — so
  `Scry`, the pipeline it expands to, and `Patterns.Library.lookAtTopAndReorder` all leave a mana
  ability a mana ability (Path of Ancestry). What the check reads is whether a card crosses the
  library boundary: gathered from a library and put anywhere else, or put into a library without
  having come from one. `Surveil` does cross it (library → graveyard) and disqualifies.
- **Attach-scope on a card that can't be attached** — a **printed** static ability
  (`script.staticAbilities` or a `classLevels` entry, on any face) whose `GroupFilter` carries
  `Scope.AttachedTo` ("enchanted/equipped creature"), on a card that is not an Aura, Equipment, or
  Fortification and carries no `auraTarget` / `equipCost`, is an **error**. The engine resolves
  attach-scope only by walking a host's attachments, so with nothing attached the filter matches no
  permanent and the ability does nothing. This is the shape that shipped Harmonious Grovestrider
  with no ward and Myojin of Night's Reach with no indestructible: attach scope is the *default*
  filter on the `Grant*` static abilities, so simply omitting the filter argument on a creature
  produces it — and because it's a default it appears in neither the card source nor the snapshot.
  (The check therefore re-encodes each ability with defaults materialized.) Use
  `keywordAbility(KeywordAbility.ward("{N}"))` for a card's own printed keyword, or pass an explicit
  filter — `Filters.Self` for "this permanent", a battlefield-scoped `GroupFilter` for a lord-style
  grant. Attach scope *inside an effect* is not flagged: an effect can change what the card is
  first (The Irencrag becomes an Equipment and then grants "equipped creature gets +3/+3"), and an
  effect can hand abilities to another object entirely (an Aura token). Attachability is likewise
  judged across the whole physical card, so a creature that transforms into an Aura isn't flagged
  for its other face.

Intentional exceptions go in `mtg-sets/src/test/resources/lint-allowlist.txt`
(`ErrorType|Card Name`, stale entries fail). Inside `ForEachInGroup` / `ForEachInCollection`,
address the iterated entity with `EffectTarget.Self` — `ContextTarget(0)` reads the cast-time
target list, which is unrelated to the iteration (this exact bug shipped on a real card before
the linter).

---

## Authoritative source files

| Area               | Path                                                            |
|--------------------|-----------------------------------------------------------------|
| Card DSL           | `mtg-sdk/src/main/kotlin/.../dsl/CardBuilder.kt`                |
| Effects            | `mtg-sdk/src/main/kotlin/.../dsl/Effects.kt`                    |
| Effect patterns    | `mtg-sdk/src/main/kotlin/.../dsl/{Library,Hand,Group,Exile,CreatureType,Misc}Patterns.kt` |
| Inline pipelines   | `mtg-sdk/src/main/kotlin/.../dsl/PipelineBuilder.kt`            |
| Triggers           | `mtg-sdk/src/main/kotlin/.../dsl/Triggers.kt`                   |
| Costs              | `mtg-sdk/src/main/kotlin/.../dsl/Costs.kt`                      |
| Conditions         | `mtg-sdk/src/main/kotlin/.../dsl/Conditions.kt`                 |
| Filters            | `mtg-sdk/src/main/kotlin/.../dsl/Filters.kt`                    |
| Targets            | `mtg-sdk/src/main/kotlin/.../dsl/Targets.kt`                    |
| Keywords           | `mtg-sdk/src/main/kotlin/.../core/Keyword.kt`                   |
| Card model         | `mtg-sdk/src/main/kotlin/.../model/CardDefinition.kt`           |
| Dynamic amounts    | `mtg-sdk/src/main/kotlin/.../scripting/values/DynamicAmount.kt` |
| Real card examples | `just where BLB` → `mtg-sets/<era>/.../definitions/blb/cards/`   |

For step-by-step authoring workflow see [`api-guide.md`](api-guide.md) (and use the `add-card` skill);
for hard cases see [`managing-complex-and-rare-abilities.md`](managing-complex-and-rare-abilities.md).

### Transmute

`transmute("{1}{U}{U}")` composes a hand-zone activated ability: pay the mana,
discard the source as a cost, and search for one card with the same mana value.
The chosen card is revealed and put into hand, then the library is shuffled; the
search may find nothing. Activation is restricted to sorcery timing. It uses
`Costs.DiscardSelf` and the existing library search pipeline. Declare `manaCost`
before `transmute`: the helper compiles the card's hand-zone mana value into the
search filter, so reanimating and copying the discarded card in response does not
change the search. Mana paid and later characteristics of a new incarnation are
irrelevant. Callers: Drift of Phantasms and Dimir Infiltrator.

`Effects.Regenerate(target)` creates the existing regeneration shield through the
DSL facade. Dimir House Guard composes it with a creature sacrifice cost. The
default target is `ContextTarget(0)`; self-regeneration passes `EffectTarget.Self`.

### Dredge

`keywordAbility(KeywordAbility.dredge(N))` declares dredge N. The optional replacement
functions only in the card owner's graveyard and only when that player has at least
N cards in their library. It replaces one draw with milling N cards and returning
the source to hand. Each draw of a multi-card instruction rechecks the graveyard,
so cards milled by the first replacement may be available for the next draw.

The engine keeps the intrinsic amounts in `DredgeComponent` and supplies matching
graveyard sources to the ordinary draw-replacement processor with `CardZoneIdentity`.
The effect recipe composes library milling and return-to-hand; no new decision or
resolution executor is introduced. The existing Yes/No decision belongs to the
drawing player, and its source identifies the public graveyard card. The client
keyword label is `DREDGE`. The mtgish emitter preserves the numeric argument through
`KeywordAbility.dredge(N)`; unsupported numeric shapes remain scaffolded.
