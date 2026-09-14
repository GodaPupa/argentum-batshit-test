Warning: truncated output (original token count: 331990)
... 279383 bytes omitted ...

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
  filter is what enforces "you control". Its selection is recorded, so a resolv…242152 tokens truncated…cost; CR 712.11b calls it choosing a face). Because the back is a real `backFace`, transform and the
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
