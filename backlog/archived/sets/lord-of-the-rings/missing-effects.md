# LTR — Missing Effects

> ✅ **COMPLETE — 0 missing. All 291 LTR cards are implemented (100%).** Verified with
> `scripts/card-status --set LTR`. Every effect family below has LANDED; this triage is retained as
> a historical record of the gap analysis and is no longer open work.

Auto-generated triage of the (formerly) unimplemented LTR cards, **refreshed 2026-06-10**.
For each card we list every engine primitive / mechanic family it touches; the
leaderboard ranks each family by how many missing cards it appears on.

> ⚠️ The original generator (`scripts/ltr_categorize.py`) has been retired. This
> refresh was rebuilt from the prior per-card tags: the 9 cards implemented since
> the 2026-06-08 snapshot were dropped and counts recomputed. Card→family tags are
> still hand-curated; verify against `cards.md` + `ltr_set.json` when in doubt.

**Missing cards:** 0 of 291 (100% implemented). _The historical snapshot below recorded 82 missing._

## Foundational families now IMPLEMENTED (no longer blockers)

These primitives exist in the SDK/engine today (grep-confirmed) or need no engine
work, so a card tagged **only** with these is implementable now — it's waiting on
authoring, not on the engine. They are kept in the tables below (marked ✅) so the
historical tagging stays intact, but they do **not** count toward remaining work.

- ✅ Amass / Army tokens
- ✅ Basic lands (no engine work)
- ✅ Custom named counter types (verse / influence / burden)
- ✅ Food tokens
- ✅ Ring temptation mechanic
- ✅ Scry triggers + scry-driven effects
- ✅ Stun counters
- ✅ Treasure tokens
- ✅ trigger: opponent draws a card except first in their draw step

Cards implemented since the last snapshot (removed from the tables below): A-Orcish Bowmasters, Gríma Wormtongue, Lobelia Sackville-Baggins, Long List of the Ents, Nasty End, Orcish Bowmasters, Rise of the Witch-king, Samwise the Stouthearted, Scroll of Isildur, Shortcut to Mushrooms, There and Back Again.

## Effect-family leaderboard

Ranked by how many of the missing cards depend on each family. Families marked ✅
are already implemented (or need no engine work) and are **not** blockers — the
real remaining work is the unmarked rows.

| Cards | ✅ | Effect family |
|------:|:--:|---------------|
|   23 | ✅ | Ring temptation mechanic |
|    8 |  | Equipment-aware effects (find/attach/equip-cost mods) |
|    7 |  | Ring-bearer state & static abilities |
|    6 | ✅ | Scry triggers + scry-driven effects |
|    5 | ✅ | Basic lands (no engine work) |
|    5 |  | Blocking restrictions / requirements (per-turn or static) |
|    5 |  | Custom-cost Ward (discard / sacrifice etc.) |
|    5 |  | Reveal-from-top with conditional placement |
|    5 |  | Token creation (Soldier / Spirit / named-legendary / typed) |
|    4 | ✅ | Amass / Army tokens |
|    4 |  | Cast / pay without paying mana cost (alternative casts) |
|    4 | ✅ | Custom named counter types (verse / influence / burden) |
|    4 | ✅ | Food tokens |
|    4 |  | Modal choices (incl. 'choose one of N not chosen yet') |
|    4 |  | Replacement effects (draw/gain-life/untap/token-creation) |
|    4 |  | Self / activation cost reduction by dynamic count |
|    4 |  | Subtype anthems (other X you control have …) |
|    3 |  | Activated-from-graveyard abilities (Renew-style) |
|    3 |  | Copy spell with new targets |
|    3 |  | Legendary-restricted mana abilities |
|    2 |  | Blocks-or-becomes-blocked combat triggers |
|    2 |  | Draw N (dynamic-amount draws) |
|    2 |  | Dynamic amounts not yet supported |
|    2 |  | ETB-of-self-or-other-creature triggers |
|    2 |  | Mill effects (target / per-player) |
|    2 |  | Modified-copy tokens (clone but change types/abilities/P/T) |
|    2 |  | Protection from everything |
|    2 |  | Saga (chapter triggers + final-chapter sacrifice) |
|    2 |  | Temporary gain-control of opponent's permanent |
|    1 |  | Additional sacrifice costs (creature/land/etc.) |
|    1 |  | Base power/toughness overrides (set N/N or grants from activation) |
|    1 |  | Becomes-the-target-of-a-spell triggers |
|    1 |  | Beginning-of-combat triggers |
|    1 |  | Color-matching filters |
|    1 |  | Combat / turn-history filters (this-turn predicates) |
|    1 |  | Combat-damage-to-player triggers (with rider effects) |
|    1 |  | Combat-keyword grants by triggered ability |
|    1 |  | Conditional ETB-tapped |
|    1 |  | Conditional board wipes |
|    1 |  | Counter-spell effects (with riders) |
|    1 |  | Creature-type-shares filter |
|    1 |  | Cycling family (cycling / typecycling / landcycling) |
|    1 |  | Damage prevention / suppression for the turn |
|    1 |  | Damage-dealt-to-you triggers |
|    1 |  | Death triggers |
|    1 |  | Delayed triggers (across turns / end steps) |
|    1 |  | Discard-as-cost / discard-as-trigger-rider |
|    1 |  | Drain effects (scales with graveyard / per-creature etc.) |
|    1 |  | ETB -3/-3 with conditional rider (controller loses 3 life if creature is legendary) |
|    1 |  | ETB triggered aura effect (tap enchanted) |
|    1 |  | ETB: each opponent sacrifices a creature of their choice |
|    1 |  | Excess damage (Trample-like) on noncombat damage |
|    1 |  | Exile from opponent's graveyard |
|    1 |  | Fight / 'deal damage equal to power' between specified creatures |
|    1 |  | Flurry / nth-spell triggers |
|    1 |  | Grant protection from a chosen card type |
|    1 |  | Granted 'can't be countered' |
|    1 |  | Graveyard → battlefield (reanimate, with predicates) |
|    1 |  | Graveyard → hand |
|    1 |  | Graveyard-state-derived mana abilities |
|    1 |  | Landfall (with extra conditions) |
|    1 |  | Landwalk variants |
|    1 |  | Leaves-the-battlefield triggers |
|    1 |  | Library search with custom predicates |
|    1 |  | Look-at-top + reorder |
|    1 |  | Mana spend-restrictions (legendary-only etc.) |
|    1 |  | Move counters between permanents |
|    1 |  | Opponent reveals cards from their library / pile |
|    1 |  | Opponent-casts-a-spell triggers |
|    1 |  | Opponent-driven pile-choice (Fact or Fiction) |
|    1 |  | Opponent-guesses-then-reveal mechanic |
|    1 |  | Per-card mutable state (turn history / chosen-modes / noted types) |
|    1 |  | Per-turn trigger limits (once-per-turn) |
|    1 |  | Phasing |
|    1 |  | Power-comparison filters / dynamic |
|    1 |  | Prevent-damage replacements (per-turn / conditional) |
|    1 |  | Self-blink / owner-only blink |
|    1 |  | Spend any mana type to pay self's abilities |
|    1 |  | Static on a spell on the stack (e.g. can't be copied) |
|    1 |  | Take-an-extra-turn rider |
|    1 |  | Toughness-instead-of-power combat assignment |
|    1 |  | Triggers on spell that targets a creature you don't control |
|    1 |  | Vehicle / Crew |
|    1 |  | activated ability that mutates the creature's subtype / sets base P/T / grants keywords |
|    1 |  | activated impulse-draw (exile top, may play this turn) |
|    1 |  | activated: deal 1 damage to each opponent |
|    1 |  | activated: pay {X} + tap; put a +1/+1 counter on target creature with power X (sorcery speed) |
|    1 |  | activated: sacrifice self + a legendary artifact — choose up to two creatures, destroy the rest |
|    1 |  | at beginning of each combat — untap equipped creature |
|    1 |  | attack trigger: defending player sacrifices their lowest-power creature (forced choice among ties) |
|    1 |  | attack trigger: put a creature card with lesser power from hand onto battlefield tapped and attacking |
|    1 |  | conditional 'is also a creature' static (during your turn while you control an Army) |
|    1 |  | counter creature spell + conditional rider if legendary |
|    1 |  | deal X to target + 1 to each other creature with the same controller |
|    1 |  | destroy targeted creature |
|    1 |  | end-step trigger gated on 'a creature died under your control this turn' |
|    1 |  | instant -2/-2 until EOT |
|    1 |  | mana ability: tap for one mana of any color |
|    1 |  | per-turn nth-resolution gating ('second time this ability resolved') |
|    1 |  | return target spell OR nonland permanent to hand (cross-zone bounce) |
|    1 |  | rider: draw a card on counter-move |
|    1 |  | rider: when you play a card cast/played this way, deal 2 to each player |
|    1 |  | sacrifice-self activated grant (indestructible EOT) |
|    1 |  | static: gain copies of opponents' lands' activated abilities except mana abilities |
|    1 |  | static: opponents' lands' activated abilities can't be activated (except mana abilities) |
|    1 |  | trigger when this or another legendary creature you control enters |
|    1 |  | trigger: cast a spell that targets self — +1/+1 counter |
|    1 |  | trigger: whenever counters are put on self — mirror those counter kinds to another creature |

## What unblocks the most cards (remaining engine gaps only)

Implemented families filtered out. This is the actionable ranking: build the top
row and you touch the most still-missing cards. (A card needs *all* its remaining
families before it ships, so high count ≠ instant completions — see the per-card map.)

| Cards | Remaining engine gap |
|------:|----------------------|
|    8 | Equipment-aware effects (find/attach/equip-cost mods) |
|    7 | Ring-bearer state & static abilities |
|    5 | Blocking restrictions / requirements (per-turn or static) |
|    5 | Custom-cost Ward (discard / sacrifice etc.) |
|    5 | Reveal-from-top with conditional placement |
|    5 | Token creation (Soldier / Spirit / named-legendary / typed) |
|    4 | Cast / pay without paying mana cost (alternative casts) |
|    4 | Modal choices (incl. 'choose one of N not chosen yet') |
|    4 | Replacement effects (draw/gain-life/untap/token-creation) |
|    4 | Self / activation cost reduction by dynamic count |
|    4 | Subtype anthems (other X you control have …) |
|    3 | Activated-from-graveyard abilities (Renew-style) |
|    3 | Copy spell with new targets |
|    3 | Legendary-restricted mana abilities |
|    2 | Blocks-or-becomes-blocked combat triggers |
|    2 | Draw N (dynamic-amount draws) |
|    2 | Dynamic amounts not yet supported |
|    2 | ETB-of-self-or-other-creature triggers |
|    2 | Mill effects (target / per-player) |
|    2 | Modified-copy tokens (clone but change types/abilities/P/T) |
|    2 | Protection from everything |
|    2 | Saga (chapter triggers + final-chapter sacrifice) |
|    2 | Temporary gain-control of opponent's permanent |
|    1 | Additional sacrifice costs (creature/land/etc.) |
|    1 | Base power/toughness overrides (set N/N or grants from activation) |
|    1 | Becomes-the-target-of-a-spell triggers |
|    1 | Beginning-of-combat triggers |
|    1 | Color-matching filters |
|    1 | Combat / turn-history filters (this-turn predicates) |
|    1 | Combat-damage-to-player triggers (with rider effects) |
|    1 | Combat-keyword grants by triggered ability |
|    1 | Conditional ETB-tapped |
|    1 | Conditional board wipes |
|    1 | Counter-spell effects (with riders) |
|    1 | Creature-type-shares filter |
|    1 | Cycling family (cycling / typecycling / landcycling) |
|    1 | Damage prevention / suppression for the turn |
|    1 | Damage-dealt-to-you triggers |
|    1 | Death triggers |
|    1 | Delayed triggers (across turns / end steps) |
|    1 | Discard-as-cost / discard-as-trigger-rider |
|    1 | Drain effects (scales with graveyard / per-creature etc.) |
|    1 | ETB -3/-3 with conditional rider (controller loses 3 life if creature is legendary) |
|    1 | ETB triggered aura effect (tap enchanted) |
|    1 | ETB: each opponent sacrifices a creature of their choice |
|    1 | Excess damage (Trample-like) on noncombat damage |
|    1 | Exile from opponent's graveyard |
|    1 | Fight / 'deal damage equal to power' between specified creatures |
|    1 | Flurry / nth-spell triggers |
|    1 | Grant protection from a chosen card type |
|    1 | Granted 'can't be countered' |
|    1 | Graveyard → battlefield (reanimate, with predicates) |
|    1 | Graveyard → hand |
|    1 | Graveyard-state-derived mana abilities |
|    1 | Landfall (with extra conditions) |
|    1 | Landwalk variants |
|    1 | Leaves-the-battlefield triggers |
|    1 | Library search with custom predicates |
|    1 | Look-at-top + reorder |
|    1 | Mana spend-restrictions (legendary-only etc.) |
|    1 | Move counters between permanents |
|    1 | Opponent reveals cards from their library / pile |
|    1 | Opponent-casts-a-spell triggers |
|    1 | Opponent-driven pile-choice (Fact or Fiction) |
|    1 | Opponent-guesses-then-reveal mechanic |
|    1 | Per-card mutable state (turn history / chosen-modes / noted types) |
|    1 | Per-turn trigger limits (once-per-turn) |
|    1 | Phasing |
|    1 | Power-comparison filters / dynamic |
|    1 | Prevent-damage replacements (per-turn / conditional) |
|    1 | Self-blink / owner-only blink |
|    1 | Spend any mana type to pay self's abilities |
|    1 | Static on a spell on the stack (e.g. can't be copied) |
|    1 | Take-an-extra-turn rider |
|    1 | Toughness-instead-of-power combat assignment |
|    1 | Triggers on spell that targets a creature you don't control |
|    1 | Vehicle / Crew |
|    1 | activated ability that mutates the creature's subtype / sets base P/T / grants keywords |
|    1 | activated impulse-draw (exile top, may play this turn) |
|    1 | activated: deal 1 damage to each opponent |
|    1 | activated: pay {X} + tap; put a +1/+1 counter on target creature with power X (sorcery speed) |
|    1 | activated: sacrifice self + a legendary artifact — choose up to two creatures, destroy the rest |
|    1 | at beginning of each combat — untap equipped creature |
|    1 | attack trigger: defending player sacrifices their lowest-power creature (forced choice among ties) |
|    1 | attack trigger: put a creature card with lesser power from hand onto battlefield tapped and attacking |
|    1 | conditional 'is also a creature' static (during your turn while you control an Army) |
|    1 | counter creature spell + conditional rider if legendary |
|    1 | deal X to target + 1 to each other creature with the same controller |
|    1 | destroy targeted creature |
|    1 | end-step trigger gated on 'a creature died under your control this turn' |
|    1 | instant -2/-2 until EOT |
|    1 | mana ability: tap for one mana of any color |
|    1 | per-turn nth-resolution gating ('second time this ability resolved') |
|    1 | return target spell OR nonland permanent to hand (cross-zone bounce) |
|    1 | rider: draw a card on counter-move |
|    1 | rider: when you play a card cast/played this way, deal 2 to each player |
|    1 | sacrifice-self activated grant (indestructible EOT) |
|    1 | static: gain copies of opponents' lands' activated abilities except mana abilities |
|    1 | static: opponents' lands' activated abilities can't be activated (except mana abilities) |
|    1 | trigger when this or another legendary creature you control enters |
|    1 | trigger: cast a spell that targets self — +1/+1 counter |
|    1 | trigger: whenever counters are put on self — mirror those counter kinds to another creature |

## Cards with no remaining engine blocker (author-only)

Every family these touch is implemented — they need only authoring + a scenario test:

- Forest
- Island
- Mountain
- Plains
- Swamp

## Per-family cards

Each section lists the missing cards that touch that family (a card appears under
every family it touches). ✅ = already implemented.

### Ring temptation mechanic  (23) ✅ IMPLEMENTED

- Aragorn, Company Leader
- Boromir, Warden of the Tower
- Breaking of the Fellowship
- Call of the Ring
- Dreadful as the Storm
- Dúnedain Rangers
- Elrond, Lord of Rivendell
- Frodo Baggins
- Frodo, Sauron's Bane
- Galadriel of Lothlórien
- Glorious Gale
- Gollum's Bite
- Gollum, Patient Plotter
- One Ring to Rule Them All
- Rangers of Ithilien
- Ringsight
- Ringwraiths
- Sauron's Ransom
- Sauron, the Dark Lord
- Slip On the Ring
- Sméagol, Helpful Guide
- The Ring Goes South
- Witch-king of Angmar

### Equipment-aware effects (find/attach/equip-cost mods)  (8)

- Barrow-Blade
- Fires of Mount Doom
- Forge Anew
- Frodo, Determined Hero
- Glamdring
- Shagrat, Loot Bearer
- Sting, the Glinting Dagger
- Trailblazer's Boots

### Ring-bearer state & static abilities  (7)

- Aragorn, Company Leader
- Call of the Ring
- Dúnedain Rangers
- Frodo Baggins
- Galadriel of Lothlórien
- One Ring to Rule Them All
- Sauron, the Necromancer

### Scry triggers + scry-driven effects  (6) ✅ IMPLEMENTED

- Elrond, Lord of Rivendell
- Galadriel of Lothlórien
- Glorfindel, Dauntless Rescuer
- Lost Isle Calling
- Palantír of Orthanc
- The Grey Havens

### Basic lands (no engine work)  (5) ✅ IMPLEMENTED

- Forest
- Island
- Mountain
- Plains
- Swamp

### Blocking restrictions / requirements (per-turn or static)  (5)

- Frodo Baggins
- Glorfindel, Dauntless Rescuer
- Gollum, Scheming Guide
- The Balrog, Durin's Bane
- Troll of Khazad-dûm

### Custom-cost Ward (discard / sacrifice etc.)  (5)

- Gollum, Scheming Guide
- Saruman of Many Colors
- Sauron, the Dark Lord
- Shelob, Child of Ungoliant
- Witch-king of Angmar

### Reveal-from-top with conditional placement  (5)

- Galadriel of Lothlórien
- Hew the Entwood
- Sméagol, Helpful Guide
- The Ring Goes South
- Tom Bombadil

### Token creation (Soldier / Spirit / named-legendary / typed)  (5)

- Faramir, Prince of Ithilien
- King of the Oathbreakers
- Peregrin Took
- Riders of the Mark
- Sauron, the Necromancer

### Amass / Army tokens  (4) ✅ IMPLEMENTED

- Barad-dûr
- Grishnákh, Brash Instigator
- Sauron, the Dark Lord
- Shagrat, Loot Bearer

### Cast / pay without paying mana cost (alternative casts)  (4)

- Forge Anew
- Glamdring
- Press the Enemy
- Saruman of Many Colors

### Custom named counter types (verse / influence / burden)  (4) ✅ IMPLEMENTED

- A-The One Ring
- Lost Isle Calling
- Palantír of Orthanc
- The One Ring

### Food tokens  (4) ✅ IMPLEMENTED

- Bill the Pony
- Peregrin Took
- Shelob, Child of Ungoliant
- Voracious Fell Beast

### Modal choices (incl. 'choose one of N not chosen yet')  (4)

- Flame of Anor
- Gandalf the Grey
- Glorfindel, Dauntless Rescuer
- Éowyn, Lady of Rohan

### Replacement effects (draw/gain-life/untap/token-creation)  (4)

- Bewitching Leechcraft
- Fear, Fire, Foes!
- Peregrin Took
- Phial of Galadriel

### Self / activation cost reduction by dynamic count  (4)

- Gwaihir the Windlord
- Riders of the Mark
- The Balrog, Durin's Bane
- Éowyn, Lady of Rohan

### Subtype anthems (other X you control have …)  (4)

- Grishnákh, Brash Instigator
- Gwaihir the Windlord
- Shadowfax, Lord of Horses
- Shelob, Child of Ungoliant

### Activated-from-graveyard abilities (Renew-style)  (3)

- Gollum's Bite
- Gollum, Patient Plotter
- Lost Isle Calling

### Copy spell with new targets  (3)

- Display of Power
- Gandalf the Grey
- Saruman of Many Colors

### Legendary-restricted mana abilities  (3)

- Delighted Halfling
- Great Hall of the Citadel
- The Grey Havens

### Blocks-or-becomes-blocked combat triggers  (2)

- Barrow-Blade
- Battle-Scarred Goblin

### Draw N (dynamic-amount draws)  (2)

- A-The One Ring
- The One Ring

### Dynamic amounts not yet supported  (2)

- Gandalf's Sanction
- The Balrog, Durin's Bane

### ETB-of-self-or-other-creature triggers  (2)

- Elrond, Lord of Rivendell
- Radagast the Brown

### Mill effects (target / per-player)  (2)

- One Ring to Rule Them All
- Palantír of Orthanc

### Modified-copy tokens (clone but change types/abilities/P/T)  (2)

- Sauron, the Necromancer
- Shelob, Child of Ungoliant

### Protection from everything  (2)

- A-The One Ring
- The One Ring

### Saga (chapter triggers + final-chapter sacrifice)  (2)

- One Ring to Rule Them All
- Tom Bombadil

### Temporary gain-control of opponent's permanent  (2)

- Grishnákh, Brash Instigator
- Rangers of Ithilien

### Additional sacrifice costs (creature/land/etc.)  (1)

- Hew the Entwood

### Base power/toughness overrides (set N/N or grants from activation)  (1)

- Dreadful as the Storm

### Becomes-the-target-of-a-spell triggers  (1)

- King of the Oathbreakers

### Beginning-of-combat triggers  (1)

- Éowyn, Lady of Rohan

### Color-matching filters  (1)

- Ringsight

### Combat / turn-history filters (this-turn predicates)  (1)

- You Cannot Pass!

### Combat-damage-to-player triggers (with rider effects)  (1)

- Sauron, the Dark Lord

### Combat-keyword grants by triggered ability  (1)

- Éowyn, Lady of Rohan

### Conditional ETB-tapped  (1)

- Barad-dûr

### Conditional board wipes  (1)

- One Ring to Rule Them All

### Counter-spell effects (with riders)  (1)

- Boromir, Warden of the Tower

### Creature-type-shares filter  (1)

- Radagast the Brown

### Cycling family (cycling / typecycling / landcycling)  (1)

- Troll of Khazad-dûm

### Damage prevention / suppression for the turn  (1)

- Fear, Fire, Foes!

### Damage-dealt-to-you triggers  (1)

- Witch-king of Angmar

### Death triggers  (1)

- The Balrog, Durin's Bane

### Delayed triggers (across turns / end steps)  (1)

- Faramir, Prince of Ithilien

### Discard-as-cost / discard-as-trigger-rider  (1)

- Witch-king of Angmar

### Drain effects (scales with graveyard / per-creature etc.)  (1)

- One Ring to Rule Them All

### ETB -3/-3 with conditional rider (controller loses 3 life if creature is legendary)  (1)

- Ringwraiths

### ETB triggered aura effect (tap enchanted)  (1)

- Bewitching Leechcraft

### ETB: each opponent sacrifices a creature of their choice  (1)

- Voracious Fell Beast

### Excess damage (Trample-like) on noncombat damage  (1)

- Gandalf's Sanction

### Exile from opponent's graveyard  (1)

- Saruman of Many Colors

### Fight / 'deal damage equal to power' between specified creatures  (1)

- Breaking of the Fellowship

### Flurry / nth-spell triggers  (1)

- Saruman of Many Colors

### Grant protection from a chosen card type  (1)

- Pippin, Guard of the Citadel

### Granted 'can't be countered'  (1)

- Delighted Halfling

### Graveyard → battlefield (reanimate, with predicates)  (1)

- Forge Anew

### Graveyard → hand  (1)

- Ringwraiths

### Graveyard-state-derived mana abilities  (1)

- The Grey Havens

### Landfall (with extra conditions)  (1)

- Dúnedain Rangers

### Landwalk variants  (1)

- Trailblazer's Boots

### Leaves-the-battlefield triggers  (1)

- Gollum, Patient Plotter

### Library search with custom predicates  (1)

- Ringsight

### Look-at-top + reorder  (1)

- Gollum, Scheming Guide

### Mana spend-restrictions (legendary-only etc.)  (1)

- Delighted Halfling

### Move counters between permanents  (1)

- Goldberry, River-Daughter

### Opponent reveals cards from their library / pile  (1)

- Sméagol, Helpful Guide

### Opponent-casts-a-spell triggers  (1)

- Sauron, the Dark Lord

### Opponent-driven pile-choice (Fact or Fiction)  (1)

- Sauron's Ransom

### Opponent-guesses-then-reveal mechanic  (1)

- Gollum, Scheming Guide

### Per-card mutable state (turn history / chosen-modes / noted types)  (1)

- Faramir, Prince of Ithilien

### Per-turn trigger limits (once-per-turn)  (1)

- Tom Bombadil

### Phasing  (1)

- King of the Oathbreakers

### Power-comparison filters / dynamic  (1)

- Rangers of Ithilien

### Prevent-damage replacements (per-turn / conditional)  (1)

- Frodo, Determined Hero

### Self-blink / owner-only blink  (1)

- Slip On the Ring

### Spend any mana type to pay self's abilities  (1)

- Sharkey, Tyrant of the Shire

### Static on a spell on the stack (e.g. can't be copied)  (1)

- Display of Power

### Take-an-extra-turn rider  (1)

- Lost Isle Calling

### Toughness-instead-of-power combat assignment  (1)

- Bill the Pony

### Triggers on spell that targets a creature you don't control  (1)

- Legolas, Master Archer

### Vehicle / Crew  (1)

- Grond, the Gatebreaker

### activated ability that mutates the creature's subtype / sets base P/T / grants keywords  (1)

- Frodo, Sauron's Bane

### activated impulse-draw (exile top, may play this turn)  (1)

- Fires of Mount Doom

### activated: deal 1 damage to each opponent  (1)

- Mount Doom

### activated: pay {X} + tap; put a +1/+1 counter on target creature with power X (sorcery speed)  (1)

- Ent-Draught Basin

### activated: sacrifice self + a legendary artifact — choose up to two creatures, destroy the rest  (1)

- Mount Doom

### at beginning of each combat — untap equipped creature  (1)

- Sting, the Glinting Dagger

### attack trigger: defending player sacrifices their lowest-power creature (forced choice among ties)  (1)

- Witch-king, Bringer of Ruin

### attack trigger: put a creature card with lesser power from hand onto battlefield tapped and attacking  (1)

- Shadowfax, Lord of Horses

### conditional 'is also a creature' static (during your turn while you control an Army)  (1)

- Grond, the Gatebreaker

### counter creature spell + conditional rider if legendary  (1)

- Glorious Gale

### deal X to target + 1 to each other creature with the same controller  (1)

- Fear, Fire, Foes!

### destroy targeted creature  (1)

- You Cannot Pass!

### end-step trigger gated on 'a creature died under your control this turn'  (1)

- Sméagol, Helpful Guide

### instant -2/-2 until EOT  (1)

- Gollum's Bite

### mana ability: tap for one mana of any color  (1)

- Phial of Galadriel

### per-turn nth-resolution gating ('second time this ability resolved')  (1)

- Elrond, Lord of Rivendell

### return target spell OR nonland permanent to hand (cross-zone bounce)  (1)

- Press the Enemy

### rider: draw a card on counter-move  (1)

- Goldberry, River-Daughter

### rider: when you play a card cast/played this way, deal 2 to each player  (1)

- Fires of Mount Doom

### sacrifice-self activated grant (indestructible EOT)  (1)

- Boromir, Warden of the Tower

### static: gain copies of opponents' lands' activated abilities except mana abilities  (1)

- Sharkey, Tyrant of the Shire

### static: opponents' lands' activated abilities can't be activated (except mana abilities)  (1)

- Sharkey, Tyrant of the Shire

### trigger when this or another legendary creature you control enters  (1)

- Frodo Baggins

### trigger: cast a spell that targets self — +1/+1 counter  (1)

- Legolas, Master Archer

### trigger: whenever counters are put on self — mirror those counter kinds to another creature  (1)

- Aragorn, Company Leader

## Per-card effect map

Every missing card with its family list and its count of **remaining** (unimplemented)
families. Sort by the Remaining column to see which cards are closest to shippable.

| Card | Remaining | # families | Families |
|------|----------:|-----------:|----------|
| One Ring to Rule Them All | 5 | 6 | Conditional board wipes · Drain effects (scales with graveyard / per-creature etc.) · Mill effects (target / per-player) · Ring temptation mechanic ✅ · Ring-bearer state & static abilities · Saga (chapter triggers + final-chapter sacrifice) |
| Saruman of Many Colors | 5 | 5 | Cast / pay without paying mana cost (alternative casts) · Copy spell with new targets · Custom-cost Ward (discard / sacrifice etc.) · Exile from opponent's graveyard · Flurry / nth-spell triggers |
| Gollum, Scheming Guide | 4 | 4 | Blocking restrictions / requirements (per-turn or static) · Custom-cost Ward (discard / sacrifice etc.) · Look-at-top + reorder · Opponent-guesses-then-reveal mechanic |
| The Balrog, Durin's Bane | 4 | 4 | Blocking restrictions / requirements (per-turn or static) · Death triggers · Dynamic amounts not yet supported · Self / activation cost reduction by dynamic count |
| Éowyn, Lady of Rohan | 4 | 4 | Beginning-of-combat triggers · Combat-keyword grants by triggered ability · Modal choices (incl. 'choose one of N not chosen yet') · Self / activation cost reduction by dynamic count |
| Delighted Halfling | 3 | 3 | Granted 'can't be countered' · Legendary-restricted mana abilities · Mana spend-restrictions (legendary-only etc.) |
| Faramir, Prince of Ithilien | 3 | 3 | Delayed triggers (across turns / end steps) · Per-card mutable state (turn history / chosen-modes / noted types) · Token creation (Soldier / Spirit / named-legendary / typed) |
| Fear, Fire, Foes! | 3 | 3 | Damage prevention / suppression for the turn · Replacement effects (draw/gain-life/untap/token-creation) · deal X to target + 1 to each other creature with the same controller |
| Fires of Mount Doom | 3 | 3 | Equipment-aware effects (find/attach/equip-cost mods) · activated impulse-draw (exile top, may play this turn) · rider: when you play a card cast/played this way, deal 2 to each player |
| Forge Anew | 3 | 3 | Cast / pay without paying mana cost (alternative casts) · Equipment-aware effects (find/attach/equip-cost mods) · Graveyard → battlefield (reanimate, with predicates) |
| Frodo Baggins | 3 | 4 | Blocking restrictions / requirements (per-turn or static) · Ring temptation mechanic ✅ · Ring-bearer state & static abilities · trigger when this or another legendary creature you control enters |
| King of the Oathbreakers | 3 | 3 | Becomes-the-target-of-a-spell triggers · Phasing · Token creation (Soldier / Spirit / named-legendary / typed) |
| Sauron, the Dark Lord | 3 | 5 | Amass / Army tokens ✅ · Combat-damage-to-player triggers (with rider effects) · Custom-cost Ward (discard / sacrifice etc.) · Opponent-casts-a-spell triggers · Ring temptation mechanic ✅ |
| Sauron, the Necromancer | 3 | 3 | Modified-copy tokens (clone but change types/abilities/P/T) · Ring-bearer state & static abilities · Token creation (Soldier / Spirit / named-legendary / typed) |
| Sharkey, Tyrant of the Shire | 3 | 3 | Spend any mana type to pay self's abilities · static: gain copies of opponents' lands' activated abilities except mana abilities · static: opponents' lands' activated abilities can't be activated (except mana abilities) |
| Shelob, Child of Ungoliant | 3 | 4 | Custom-cost Ward (discard / sacrifice etc.) · Food tokens ✅ · Modified-copy tokens (clone but change types/abilities/P/T) · Subtype anthems (other X you control have …) |
| Sméagol, Helpful Guide | 3 | 4 | Opponent reveals cards from their library / pile · Reveal-from-top with conditional placement · Ring temptation mechanic ✅ · end-step trigger gated on 'a creature died under your control this turn' |
| Tom Bombadil | 3 | 3 | Per-turn trigger limits (once-per-turn) · Reveal-from-top with conditional placement · Saga (chapter triggers + final-chapter sacrifice) |
| Witch-king of Angmar | 3 | 4 | Custom-cost Ward (discard / sacrifice etc.) · Damage-dealt-to-you triggers · Discard-as-cost / discard-as-trigger-rider · Ring temptation mechanic ✅ |
| A-The One Ring | 2 | 3 | Custom named counter types (verse / influence / burden) ✅ · Draw N (dynamic-amount draws) · Protection from everything |
| Aragorn, Company Leader | 2 | 3 | Ring temptation mechanic ✅ · Ring-bearer state & static abilities · trigger: whenever counters are put on self — mirror those counter kinds to another creature |
| Barrow-Blade | 2 | 2 | Blocks-or-becomes-blocked combat triggers · Equipment-aware effects (find/attach/equip-cost mods) |
| Bewitching Leechcraft | 2 | 2 | ETB triggered aura effect (tap enchanted) · Replacement effects (draw/gain-life/untap/token-creation) |
| Boromir, Warden of the Tower | 2 | 3 | Counter-spell effects (with riders) · Ring temptation mechanic ✅ · sacrifice-self activated grant (indestructible EOT) |
| Display of Power | 2 | 2 | Copy spell with new targets · Static on a spell on the stack (e.g. can't be copied) |
| Dúnedain Rangers | 2 | 3 | Landfall (with extra conditions) · Ring temptation mechanic ✅ · Ring-bearer state & static abilities |
| Elrond, Lord of Rivendell | 2 | 4 | ETB-of-self-or-other-creature triggers · Ring temptation mechanic ✅ · Scry triggers + scry-driven effects ✅ · per-turn nth-resolution gating ('second time this ability resolved') |
| Frodo, Determined Hero | 2 | 2 | Equipment-aware effects (find/attach/equip-cost mods) · Prevent-damage replacements (per-turn / conditional) |
| Galadriel of Lothlórien | 2 | 4 | Reveal-from-top with conditional placement · Ring temptation mechanic ✅ · Ring-bearer state & static abilities · Scry triggers + scry-driven effects ✅ |
| Gandalf the Grey | 2 | 2 | Copy spell with new targets · Modal choices (incl. 'choose one of N not chosen yet') |
| Gandalf's Sanction | 2 | 2 | Dynamic amounts not yet supported · Excess damage (Trample-like) on noncombat damage |
| Glamdring | 2 | 2 | Cast / pay without paying mana cost (alternative casts) · Equipment-aware effects (find/attach/equip-cost mods) |
| Glorfindel, Dauntless Rescuer | 2 | 3 | Blocking restrictions / requirements (per-turn or static) · Modal choices (incl. 'choose one of N not chosen yet') · Scry triggers + scry-driven effects ✅ |
| Goldberry, River-Daughter | 2 | 2 | Move counters between permanents · rider: draw a card on counter-move |
| Gollum's Bite | 2 | 3 | Activated-from-graveyard abilities (Renew-style) · Ring temptation mechanic ✅ · instant -2/-2 until EOT |
| Gollum, Patient Plotter | 2 | 3 | Activated-from-graveyard abilities (Renew-style) · Leaves-the-battlefield triggers · Ring temptation mechanic ✅ |
| Grishnákh, Brash Instigator | 2 | 3 | Amass / Army tokens ✅ · Subtype anthems (other X you control have …) · Temporary gain-control of opponent's permanent |
| Grond, the Gatebreaker | 2 | 2 | Vehicle / Crew · conditional 'is also a creature' static (during your turn while you control an Army) |
| Gwaihir the Windlord | 2 | 2 | Self / activation cost reduction by dynamic count · Subtype anthems (other X you control have …) |
| Hew the Entwood | 2 | 2 | Additional sacrifice costs (creature/land/etc.) · Reveal-from-top with conditional placement |
| Legolas, Master Archer | 2 | 2 | Triggers on spell that targets a creature you don't control · trigger: cast a spell that targets self — +1/+1 counter |
| Lost Isle Calling | 2 | 4 | Activated-from-graveyard abilities (Renew-style) · Custom named counter types (verse / influence / burden) ✅ · Scry triggers + scry-driven effects ✅ · Take-an-extra-turn rider |
| Mount Doom | 2 | 2 | activated: deal 1 damage to each opponent · activated: sacrifice self + a legendary artifact — choose up to two creatures, destroy the rest |
| Peregrin Took | 2 | 3 | Food tokens ✅ · Replacement effects (draw/gain-life/untap/token-creation) · Token creation (Soldier / Spirit / named-legendary / typed) |
| Phial of Galadriel | 2 | 2 | Replacement effects (draw/gain-life/untap/token-creation) · mana ability: tap for one mana of any color |
| Press the Enemy | 2 | 2 | Cast / pay without paying mana cost (alternative casts) · return target spell OR nonland permanent to hand (cross-zone bounce) |
| Radagast the Brown | 2 | 2 | Creature-type-shares filter · ETB-of-self-or-other-creature triggers |
| Rangers of Ithilien | 2 | 3 | Power-comparison filters / dynamic · Ring temptation mechanic ✅ · Temporary gain-control of opponent's permanent |
| Riders of the Mark | 2 | 2 | Self / activation cost reduction by dynamic count · Token creation (Soldier / Spirit / named-legendary / typed) |
| Ringsight | 2 | 3 | Color-matching filters · Library search with custom predicates · Ring temptation mechanic ✅ |
| Ringwraiths | 2 | 3 | ETB -3/-3 with conditional rider (controller loses 3 life if creature is legendary) · Graveyard → hand · Ring temptation mechanic ✅ |
| Shadowfax, Lord of Horses | 2 | 2 | Subtype anthems (other X you control have …) · attack trigger: put a creature card with lesser power from hand onto battlefield tapped and attacking |
| Sting, the Glinting Dagger | 2 | 2 | Equipment-aware effects (find/attach/equip-cost mods) · at beginning of each combat — untap equipped creature |
| The Grey Havens | 2 | 3 | Graveyard-state-derived mana abilities · Legendary-restricted mana abilities · Scry triggers + scry-driven effects ✅ |
| The One Ring | 2 | 3 | Custom named counter types (verse / influence / burden) ✅ · Draw N (dynamic-amount draws) · Protection from everything |
| Trailblazer's Boots | 2 | 2 | Equipment-aware effects (find/attach/equip-cost mods) · Landwalk variants |
| Troll of Khazad-dûm | 2 | 2 | Blocking restrictions / requirements (per-turn or static) · Cycling family (cycling / typecycling / landcycling) |
| You Cannot Pass! | 2 | 2 | Combat / turn-history filters (this-turn predicates) · destroy targeted creature |
| Barad-dûr | 1 | 2 | Amass / Army tokens ✅ · Conditional ETB-tapped |
| Battle-Scarred Goblin | 1 | 1 | Blocks-or-becomes-blocked combat triggers |
| Bill the Pony | 1 | 2 | Food tokens ✅ · Toughness-instead-of-power combat assignment |
| Breaking of the Fellowship | 1 | 2 | Fight / 'deal damage equal to power' between specified creatures · Ring temptation mechanic ✅ |
| Call of the Ring | 1 | 2 | Ring temptation mechanic ✅ · Ring-bearer state & static abilities |
| Dreadful as the Storm | 1 | 2 | Base power/toughness overrides (set N/N or grants from activation) · Ring temptation mechanic ✅ |
| Ent-Draught Basin | 1 | 1 | activated: pay {X} + tap; put a +1/+1 counter on target creature with power X (sorcery speed) |
| Flame of Anor | 1 | 1 | Modal choices (incl. 'choose one of N not chosen yet') |
| Frodo, Sauron's Bane | 1 | 2 | Ring temptation mechanic ✅ · activated ability that mutates the creature's subtype / sets base P/T / grants keywords |
| Glorious Gale | 1 | 2 | Ring temptation mechanic ✅ · counter creature spell + conditional rider if legendary |
| Great Hall of the Citadel | 1 | 1 | Legendary-restricted mana abilities |
| Palantír of Orthanc | 1 | 3 | Custom named counter types (verse / influence / burden) ✅ · Mill effects (target / per-player) · Scry triggers + scry-driven effects ✅ |
| Pippin, Guard of the Citadel | 1 | 1 | Grant protection from a chosen card type |
| Sauron's Ransom | 1 | 2 | Opponent-driven pile-choice (Fact or Fiction) · Ring temptation mechanic ✅ |
| Shagrat, Loot Bearer | 1 | 2 | Amass / Army tokens ✅ · Equipment-aware effects (find/attach/equip-cost mods) |
| Slip On the Ring | 1 | 2 | Ring temptation mechanic ✅ · Self-blink / owner-only blink |
| The Ring Goes South | 1 | 2 | Reveal-from-top with conditional placement · Ring temptation mechanic ✅ |
| Voracious Fell Beast | 1 | 2 | ETB: each opponent sacrifices a creature of their choice · Food tokens ✅ |
| Witch-king, Bringer of Ruin | 1 | 1 | attack trigger: defending player sacrifices their lowest-power creature (forced choice among ties) |
| Forest | 0 | 1 | Basic lands (no engine work) ✅ |
| Island | 0 | 1 | Basic lands (no engine work) ✅ |
| Mountain | 0 | 1 | Basic lands (no engine work) ✅ |
| Plains | 0 | 1 | Basic lands (no engine work) ✅ |
| Swamp | 0 | 1 | Basic lands (no engine work) ✅ |

